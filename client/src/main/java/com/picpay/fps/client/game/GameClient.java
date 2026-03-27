package com.picpay.fps.client.game;

import com.picpay.fps.client.engine.*;
import com.picpay.fps.client.network.ClientNetworkManager;
import com.picpay.fps.shared.constants.GameConfig;
import com.picpay.fps.shared.protocol.*;

import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Main game client — ties together rendering, networking, and input.
 */
public class GameClient {
    private static final Logger LOG = Logger.getLogger(GameClient.class.getName());

    private Window window;
    private VulkanRenderer renderer;
    private Camera camera;
    private SceneBuilder sceneBuilder;
    private ClientNetworkManager network;

    // Remote players state (interpolated)
    private final Map<Integer, RemotePlayer> remotePlayers = new ConcurrentHashMap<>();

    private String serverHost = "127.0.0.1";
    private String playerName = "Player";
    private boolean running = true;

    public static void main(String[] args) {
        GameClient client = new GameClient();

        if (args.length >= 1) client.serverHost = args[0];
        if (args.length >= 2) client.playerName = args[1];

        client.run();
    }

    public void run() {
        try {
            init();
            gameLoop();
        } catch (Exception e) {
            LOG.severe("Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void init() throws Exception {
        LOG.info("=== FPS Intranet Client ===");
        LOG.info("Server: " + serverHost);
        LOG.info("Player: " + playerName);

        window = new Window();
        window.init();

        renderer = new VulkanRenderer();
        renderer.init(window);

        camera = new Camera();
        camera.updateProjection(renderer.getWidth(), renderer.getHeight());

        sceneBuilder = new SceneBuilder();

        network = new ClientNetworkManager();
        network.connect(serverHost, playerName);

        // Wait for connection (up to 5 seconds)
        long deadline = System.currentTimeMillis() + 5000;
        while (!network.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        if (!network.isConnected()) {
            throw new RuntimeException("Could not connect to server at " + serverHost);
        }

        LOG.info("Connected to server!");
    }

    private void gameLoop() {
        long lastTime = System.nanoTime();
        double accumulator = 0;
        double tickDuration = GameConfig.TICK_DURATION;

        while (!window.shouldClose() && running) {
            long now = System.nanoTime();
            double frameTime = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            // Cap frame time to avoid spiral of death
            if (frameTime > 0.25) frameTime = 0.25;
            accumulator += frameTime;

            // Handle window events
            window.pollEvents();

            // Fixed timestep for input/networking
            while (accumulator >= tickDuration) {
                processInput();
                processNetworkPackets();
                accumulator -= tickDuration;
            }

            // Handle resize
            if (window.isResized()) {
                renderer.recreateSwapchain();
                camera.updateProjection(renderer.getWidth(), renderer.getHeight());
            }

            // Render
            render();
        }
    }

    private void processInput() {
        if (!window.isMouseGrabbed()) return;

        // Mouse look
        camera.rotate(window.getMouseDeltaX(), window.getMouseDeltaY());

        // Build input bitfield
        byte keys = 0;
        if (window.isKeyDown(GLFW_KEY_W)) keys |= 0x01;
        if (window.isKeyDown(GLFW_KEY_S)) keys |= 0x02;
        if (window.isKeyDown(GLFW_KEY_A)) keys |= 0x04;
        if (window.isKeyDown(GLFW_KEY_D)) keys |= 0x08;
        if (window.isKeyDown(GLFW_KEY_SPACE)) keys |= 0x10;
        if (window.isKeyDown(GLFW_KEY_LEFT_SHIFT)) keys |= 0x20;
        if (window.isLeftMousePressed()) keys |= 0x40;

        // Send input to server
        network.sendInput(keys, camera.getYaw(), camera.getPitch());

        // Client-side prediction (move locally too)
        float dt = (float) GameConfig.TICK_DURATION;
        float speed = window.isKeyDown(GLFW_KEY_LEFT_SHIFT) ? GameConfig.PLAYER_SPRINT_SPEED : GameConfig.PLAYER_SPEED;

        float dx = 0, dz = 0;
        float sinYaw = (float) Math.sin(camera.getYaw());
        float cosYaw = (float) Math.cos(camera.getYaw());

        if (window.isKeyDown(GLFW_KEY_W)) { dx += sinYaw; dz -= cosYaw; }
        if (window.isKeyDown(GLFW_KEY_S)) { dx -= sinYaw; dz += cosYaw; }
        if (window.isKeyDown(GLFW_KEY_A)) { dx -= cosYaw; dz -= sinYaw; }
        if (window.isKeyDown(GLFW_KEY_D)) { dx += cosYaw; dz += sinYaw; }

        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            camera.getPosition().x += (dx / len) * speed * dt;
            camera.getPosition().z += (dz / len) * speed * dt;
        }
    }

    private void processNetworkPackets() {
        byte[] data;
        while ((data = network.pollPacket()) != null) {
            PacketType type = Packet.peekType(data);
            if (type == null) continue;

            switch (type) {
                case WORLD_SNAPSHOT -> handleWorldSnapshot(data);
                case SPAWN -> handleSpawn(data);
                case KILL_EVENT -> handleKillEvent(data);
                default -> {}
            }
        }
    }

    private void handleWorldSnapshot(byte[] data) {
        WorldSnapshotPacket snapshot = WorldSnapshotPacket.deserialize(data);
        ByteBuffer buf = ByteBuffer.wrap(snapshot.getPlayerStatesData());

        for (int i = 0; i < snapshot.getPlayerCount(); i++) {
            if (buf.remaining() < 31) break;

            int id = buf.getInt();
            float x = buf.getFloat(), y = buf.getFloat(), z = buf.getFloat();
            float yaw = buf.getFloat(), pitch = buf.getFloat();
            byte hp = buf.get(), weapon = buf.get(), team = buf.get();
            int lastInputSeq = buf.getInt();

            if (id == network.getLocalPlayerId()) {
                // Server reconciliation: correct local position from server authority
                // For now, just snap to server position (basic implementation)
                // A full implementation would replay unprocessed inputs
                camera.getPosition().set(x, y + 1.6f, z);
            } else {
                RemotePlayer rp = remotePlayers.computeIfAbsent(id, k -> new RemotePlayer());
                rp.x = x; rp.y = y; rp.z = z;
                rp.yaw = yaw; rp.pitch = pitch;
                rp.hp = hp; rp.weapon = weapon; rp.team = team;
                rp.alive = hp > 0;
                rp.lastUpdate = System.currentTimeMillis();
            }
        }

        // Remove stale players (disconnected)
        long now = System.currentTimeMillis();
        remotePlayers.entrySet().removeIf(e -> now - e.getValue().lastUpdate > 5000);
    }

    private void handleSpawn(byte[] data) {
        SpawnPacket pkt = SpawnPacket.deserialize(data);
        if (pkt.getPlayerId() == network.getLocalPlayerId()) {
            camera.getPosition().set(pkt.getX(), pkt.getY() + 1.6f, pkt.getZ());
            LOG.info("Respawned!");
        }
    }

    private void handleKillEvent(byte[] data) {
        KillEventPacket pkt = KillEventPacket.deserialize(data);
        String killerName = pkt.getKillerId() == network.getLocalPlayerId() ? "You" : "Player " + pkt.getKillerId();
        String victimName = pkt.getVictimId() == network.getLocalPlayerId() ? "You" : "Player " + pkt.getVictimId();
        LOG.info(killerName + " killed " + victimName);
    }

    private void render() {
        sceneBuilder.clear();

        // Build scene
        sceneBuilder.addMap();

        // Add remote players
        for (RemotePlayer rp : remotePlayers.values()) {
            sceneBuilder.addPlayer(rp.x, rp.y, rp.z, rp.yaw, rp.team, rp.alive);
        }

        // Add crosshair
        sceneBuilder.addCrosshair(
            camera.getPosition().x, camera.getPosition().y, camera.getPosition().z,
            camera.getYaw(), camera.getPitch()
        );

        float[] vertices = sceneBuilder.build();
        renderer.uploadVertices(vertices);

        Matrix4f mvp = camera.getViewProjectionMatrix();
        renderer.renderFrame(mvp);
    }

    private void cleanup() {
        LOG.info("Shutting down...");
        if (network != null) network.disconnect();
        if (renderer != null) renderer.cleanup();
        if (window != null) window.cleanup();
    }

    /**
     * Simple container for remote player state.
     */
    static class RemotePlayer {
        float x, y, z;
        float yaw, pitch;
        byte hp, weapon, team;
        boolean alive = true;
        long lastUpdate;
    }
}
