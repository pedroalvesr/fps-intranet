package com.picpay.fps.client.game;

import com.picpay.fps.client.engine.*;
import com.picpay.fps.client.network.ClientNetworkManager;
import com.picpay.fps.shared.constants.GameConfig;
import com.picpay.fps.shared.protocol.*;

import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Main game client — ties together rendering, networking, and input.
 * Manages NAME_ENTRY, LOBBY, and PLAYING states.
 * Online features: interpolation, client-side prediction with reconciliation.
 */
public class GameClient {
    private static final Logger LOG = Logger.getLogger(GameClient.class.getName());

    private enum State { NAME_ENTRY, LOBBY, PLAYING }

    private Window window;
    private VulkanRenderer renderer;
    private Camera camera;
    private SceneBuilder sceneBuilder;
    private GeometricFont font;
    private LobbyRenderer lobbyRenderer;
    private ClientNetworkManager network;

    // Remote players state (interpolated)
    private final Map<Integer, RemotePlayer> remotePlayers = new ConcurrentHashMap<>();

    // Client state
    private State state = State.NAME_ENTRY;
    private String serverHost = "127.0.0.1";
    private String playerName = "Player";
    private boolean running = true;
    private boolean localReady = false;
    private boolean readyKeyWasPressed = false;

    // Lobby state from server
    private volatile LobbyStatePacket.LobbyPlayer[] lobbyPlayers = new LobbyStatePacket.LobbyPlayer[0];
    private volatile byte lobbyPhase = 0;
    private volatile byte lobbyCountdown = 0;

    // Lobby camera
    private float lobbyCameraAngle = 0;

    // Name entry
    private boolean skipNameEntry = false;

    // Client-side prediction: buffer of unacknowledged inputs
    private final Deque<PredictedInput> pendingInputs = new ArrayDeque<>();
    private static final int MAX_PENDING_INPUTS = 128;

    public static void main(String[] args) {
        GameClient client = new GameClient();

        if (args.length >= 1) client.serverHost = args[0];
        if (args.length >= 2) {
            client.playerName = args[1];
            client.skipNameEntry = true;
        }

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

        window = new Window();
        window.init();

        renderer = new VulkanRenderer();
        renderer.init(window);

        camera = new Camera();
        camera.updateProjection(renderer.getWidth(), renderer.getHeight());

        sceneBuilder = new SceneBuilder();
        font = new GeometricFont(sceneBuilder);
        lobbyRenderer = new LobbyRenderer(sceneBuilder, font);

        window.setMouseGrabbed(false);

        if (skipNameEntry) {
            // Name provided via CLI — skip straight to connecting
            connectToServer();
        } else {
            // Show name entry screen
            state = State.NAME_ENTRY;
            window.setTextInputActive(true);
            window.setTextInputBuffer("");
            LOG.info("Enter your name to join...");
        }
    }

    private void connectToServer() throws Exception {
        LOG.info("Player: " + playerName);
        network = new ClientNetworkManager();
        network.connect(serverHost, playerName);

        long deadline = System.currentTimeMillis() + 5000;
        while (!network.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        if (!network.isConnected()) {
            throw new RuntimeException("Could not connect to server at " + serverHost);
        }

        LOG.info("Connected to server! Waiting in lobby...");
        state = State.LOBBY;
    }

    private void gameLoop() {
        long lastTime = System.nanoTime();
        double accumulator = 0;
        double tickDuration = GameConfig.TICK_DURATION;

        while (!window.shouldClose() && running) {
            long now = System.nanoTime();
            double frameTime = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            if (frameTime > 0.25) frameTime = 0.25;
            accumulator += frameTime;

            window.pollEvents();

            while (accumulator >= tickDuration) {
                if (state != State.NAME_ENTRY) {
                    processNetworkPackets();
                    // Tick the network manager (heartbeat, etc.)
                    if (network != null) network.tick();
                }

                switch (state) {
                    case NAME_ENTRY -> processNameEntryInput();
                    case LOBBY -> processLobbyInput();
                    case PLAYING -> processGameInput();
                }
                accumulator -= tickDuration;
            }

            if (window.isResized()) {
                renderer.recreateSwapchain();
                camera.updateProjection(renderer.getWidth(), renderer.getHeight());
            }

            switch (state) {
                case NAME_ENTRY -> renderNameEntry();
                case LOBBY -> renderLobby(frameTime);
                case PLAYING -> renderGame();
            }
        }
    }

    // ─── NAME ENTRY ────────────────────────────────────────────────────

    private void processNameEntryInput() {
        if (window.consumeEnterPressed()) {
            String name = window.getTextInputBuffer().trim();
            if (!name.isEmpty()) {
                playerName = name;
                window.setTextInputActive(false);
                try {
                    connectToServer();
                } catch (Exception e) {
                    LOG.severe("Connection failed: " + e.getMessage());
                    // Go back to name entry
                    state = State.NAME_ENTRY;
                    window.setTextInputActive(true);
                }
            }
        }
    }

    private void renderNameEntry() {
        sceneBuilder.clear();

        // Dark platform floor
        sceneBuilder.addColoredQuad(
            -10, 0, -10,  10, 0, -10,
            10, 0, 10,  -10, 0, 10,
            0.08f, 0.08f, 0.12f
        );

        // Title: "FPS INTRANET"
        font.renderText("FPS INTRANET", 0, 6.0f, -5.0f, 1.0f, 0.2f, 0.9f, 0.3f, true);

        // Subtitle: "ENTER YOUR NAME"
        font.renderText("ENTER YOUR NAME", 0, 4.0f, -5.0f, 0.5f, 0.7f, 0.7f, 0.7f, true);

        // Input field background bar
        sceneBuilder.addWallBox(-4.5f, 2.0f, -5.3f, 4.5f, 3.2f, -5.1f, 0.12f, 0.12f, 0.18f);

        // Current typed text (z=-5.0, in front of background box)
        String typed = window.getTextInputBuffer();
        if (!typed.isEmpty()) {
            font.renderText(typed, 0, 2.2f, -4.9f, 0.7f, 1.0f, 1.0f, 1.0f, true);
        }

        // Blinking cursor
        boolean cursorVisible = (System.currentTimeMillis() / 500) % 2 == 0;
        if (cursorVisible) {
            float cursorX = typed.isEmpty() ? 0 : getTextEndX(typed, 0, 0.7f);
            font.renderText("_", cursorX, 2.2f, -4.9f, 0.7f, 0.2f, 0.9f, 0.3f, false);
        }

        // Hint: "PRESS ENTER TO JOIN"
        font.renderText("PRESS ENTER", 0, 0.8f, -5.0f, 0.35f, 0.5f, 0.5f, 0.5f, true);

        // Fixed camera for name entry
        Matrix4f view = new Matrix4f().lookAt(
            0, 5, 8,
            0, 3, -5,
            0, 1, 0
        );

        float aspect = (float) renderer.getWidth() / renderer.getHeight();
        Matrix4f proj = new Matrix4f().perspective(
            (float) Math.toRadians(60), aspect,
            GameConfig.NEAR_PLANE, GameConfig.FAR_PLANE, true
        );
        proj.m11(proj.m11() * -1); // Vulkan Y flip

        Matrix4f mvp = new Matrix4f();
        proj.mul(view, mvp);

        float[] vertices = sceneBuilder.build();
        renderer.uploadVertices(vertices);
        renderer.renderFrame(mvp);
    }

    /** Calculate the end X position of centered text (for cursor placement). */
    private float getTextEndX(String text, float centerX, float charHeight) {
        float charWidth = charHeight * 0.6f;
        float spacing = charHeight * 0.15f;
        float totalWidth = text.length() * (charWidth + spacing) - spacing;
        return centerX + totalWidth / 2.0f + spacing;
    }

    // ─── LOBBY ───────────────────────────────────────────────────────────

    private void processLobbyInput() {
        // R key toggles ready (with debounce)
        boolean rPressed = window.isKeyDown(GLFW_KEY_R);
        if (rPressed && !readyKeyWasPressed) {
            localReady = !localReady;
            network.sendReady(localReady);
            LOG.info("Ready: " + localReady);
        }
        readyKeyWasPressed = rPressed;
    }

    private void renderLobby(double dt) {
        // Slowly rotating camera looking at the center of the map from above
        lobbyCameraAngle += (float) (dt * 0.3);
        float camDist = 20.0f;
        float camHeight = 15.0f;
        float camX = (float) Math.sin(lobbyCameraAngle) * camDist;
        float camZ = (float) Math.cos(lobbyCameraAngle) * camDist;

        camera.getPosition().set(camX, camHeight, camZ);

        // Look at center
        Matrix4f view = new Matrix4f().lookAt(
            camX, camHeight, camZ,
            0, 2, 0,
            0, 1, 0
        );

        float aspect = (float) renderer.getWidth() / renderer.getHeight();
        Matrix4f proj = new Matrix4f().perspective(
            (float) Math.toRadians(60), aspect,
            GameConfig.NEAR_PLANE, GameConfig.FAR_PLANE, true
        );
        proj.m11(proj.m11() * -1); // Vulkan Y flip

        Matrix4f mvp = new Matrix4f();
        proj.mul(view, mvp);

        lobbyRenderer.render(lobbyPlayers, lobbyPhase, lobbyCountdown,
            network.getLocalPlayerId(), localReady);

        float[] vertices = sceneBuilder.build();
        renderer.uploadVertices(vertices);
        renderer.renderFrame(mvp);
    }

    private void transitionToPlaying() {
        state = State.PLAYING;
        LOG.info("=== GAME STARTING! ===");
        // Grab mouse for FPS controls
        window.setMouseGrabbed(true);
        pendingInputs.clear();
    }

    // ─── PLAYING ─────────────────────────────────────────────────────────

    private void processGameInput() {
        if (!window.isMouseGrabbed()) return;

        camera.rotate(window.getMouseDeltaX(), window.getMouseDeltaY());

        byte keys = 0;
        if (window.isKeyDown(GLFW_KEY_W)) keys |= 0x01;
        if (window.isKeyDown(GLFW_KEY_S)) keys |= 0x02;
        if (window.isKeyDown(GLFW_KEY_A)) keys |= 0x04;
        if (window.isKeyDown(GLFW_KEY_D)) keys |= 0x08;
        if (window.isKeyDown(GLFW_KEY_SPACE)) keys |= 0x10;
        if (window.isKeyDown(GLFW_KEY_LEFT_SHIFT)) keys |= 0x20;
        if (window.isLeftMousePressed()) keys |= 0x40;

        network.sendInput(keys, camera.getYaw(), camera.getPitch());

        // Client-side prediction: apply locally
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
            float moveX = (dx / len) * speed * dt;
            float moveZ = (dz / len) * speed * dt;
            camera.getPosition().x += moveX;
            camera.getPosition().z += moveZ;
        }

        // Store predicted input for reconciliation
        if (pendingInputs.size() >= MAX_PENDING_INPUTS) {
            pendingInputs.pollFirst();
        }
        pendingInputs.addLast(new PredictedInput(
            network.getLocalPlayerId(), // will be current seq-1 since sendInput increments
            keys, camera.getYaw(), camera.getPitch(),
            camera.getPosition().x, camera.getPosition().y - 1.6f, camera.getPosition().z
        ));
    }

    private void renderGame() {
        sceneBuilder.clear();
        sceneBuilder.addMap();

        for (RemotePlayer rp : remotePlayers.values()) {
            // Interpolate between previous and current position
            float t = rp.interpolationT();
            float ix = rp.prevX + (rp.x - rp.prevX) * t;
            float iy = rp.prevY + (rp.y - rp.prevY) * t;
            float iz = rp.prevZ + (rp.z - rp.prevZ) * t;
            float iyaw = lerpAngle(rp.prevYaw, rp.yaw, t);

            sceneBuilder.addPlayer(ix, iy, iz, iyaw, rp.team, rp.alive);
        }

        sceneBuilder.addCrosshair(
            camera.getPosition().x, camera.getPosition().y, camera.getPosition().z,
            camera.getYaw(), camera.getPitch()
        );

        float[] vertices = sceneBuilder.build();
        renderer.uploadVertices(vertices);

        Matrix4f mvp = camera.getViewProjectionMatrix();
        renderer.renderFrame(mvp);
    }

    // ─── NETWORKING ──────────────────────────────────────────────────────

    private void processNetworkPackets() {
        byte[] data;
        while ((data = network.pollPacket()) != null) {
            PacketType type = Packet.peekType(data);
            if (type == null) continue;

            switch (type) {
                case LOBBY_STATE -> handleLobbyState(data);
                case GAME_START -> transitionToPlaying();
                case WORLD_SNAPSHOT -> handleWorldSnapshot(data);
                case SPAWN -> handleSpawn(data);
                case KILL_EVENT -> handleKillEvent(data);
                default -> {}
            }
        }
    }

    private void handleLobbyState(byte[] data) {
        LobbyStatePacket pkt = LobbyStatePacket.deserialize(data);
        lobbyPlayers = pkt.getPlayers();
        lobbyPhase = pkt.getPhase();
        lobbyCountdown = pkt.getCountdown();
    }

    private void handleWorldSnapshot(byte[] data) {
        if (state != State.PLAYING) return;

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
                // Server reconciliation: discard acknowledged inputs
                while (!pendingInputs.isEmpty()) {
                    PredictedInput pi = pendingInputs.peekFirst();
                    if (pi.sequence <= lastInputSeq) {
                        pendingInputs.pollFirst();
                    } else {
                        break;
                    }
                }

                // Check divergence — if server position differs significantly, correct
                float camWorldX = camera.getPosition().x;
                float camWorldZ = camera.getPosition().z;
                float distSq = (x - camWorldX) * (x - camWorldX) + (z - camWorldZ) * (z - camWorldZ);

                // If divergence > threshold, snap to server position and replay pending inputs
                if (distSq > 4.0f) { // ~2 units divergence
                    camera.getPosition().set(x, y + 1.6f, z);
                    // Replay pending inputs on top of corrected position
                    for (PredictedInput pi : pendingInputs) {
                        applyPredictedMovement(pi);
                    }
                }
                // Small divergences: trust client prediction (feels smoother)
            } else {
                // Remote player: update with interpolation support
                RemotePlayer rp = remotePlayers.computeIfAbsent(id, k -> new RemotePlayer());
                rp.prevX = rp.x; rp.prevY = rp.y; rp.prevZ = rp.z;
                rp.prevYaw = rp.yaw;
                rp.x = x; rp.y = y; rp.z = z;
                rp.yaw = yaw; rp.pitch = pitch;
                rp.hp = hp; rp.weapon = weapon; rp.team = team;
                rp.alive = hp > 0;
                rp.lastUpdate = System.currentTimeMillis();
                rp.snapshotReceiveTime = System.currentTimeMillis();
            }
        }

        long now = System.currentTimeMillis();
        remotePlayers.entrySet().removeIf(e -> now - e.getValue().lastUpdate > 5000);
    }

    private void applyPredictedMovement(PredictedInput input) {
        float dt = (float) GameConfig.TICK_DURATION;
        boolean sprint = (input.keys & 0x20) != 0;
        float speed = sprint ? GameConfig.PLAYER_SPRINT_SPEED : GameConfig.PLAYER_SPEED;

        float dx = 0, dz = 0;
        float sinYaw = (float) Math.sin(input.yaw);
        float cosYaw = (float) Math.cos(input.yaw);

        if ((input.keys & 0x01) != 0) { dx += sinYaw; dz -= cosYaw; }
        if ((input.keys & 0x02) != 0) { dx -= sinYaw; dz += cosYaw; }
        if ((input.keys & 0x04) != 0) { dx -= cosYaw; dz -= sinYaw; }
        if ((input.keys & 0x08) != 0) { dx += cosYaw; dz += sinYaw; }

        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            camera.getPosition().x += (dx / len) * speed * dt;
            camera.getPosition().z += (dz / len) * speed * dt;
        }
    }

    private void handleSpawn(byte[] data) {
        SpawnPacket pkt = SpawnPacket.deserialize(data);
        if (pkt.getPlayerId() == network.getLocalPlayerId()) {
            camera.getPosition().set(pkt.getX(), pkt.getY() + 1.6f, pkt.getZ());
            pendingInputs.clear();
            LOG.info("Respawned!");
        }
    }

    private void handleKillEvent(byte[] data) {
        KillEventPacket pkt = KillEventPacket.deserialize(data);
        String killerName = pkt.getKillerId() == network.getLocalPlayerId() ? "You" : "Player " + pkt.getKillerId();
        String victimName = pkt.getVictimId() == network.getLocalPlayerId() ? "You" : "Player " + pkt.getVictimId();
        LOG.info(killerName + " killed " + victimName);
    }

    // ─── UTILITIES ───────────────────────────────────────────────────────

    /** Lerp angles handling wraparound. */
    private float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        return a + diff * t;
    }

    // ─── CLEANUP ─────────────────────────────────────────────────────────

    private void cleanup() {
        LOG.info("Shutting down...");
        if (network != null) network.disconnect();
        if (renderer != null) renderer.cleanup();
        if (window != null) window.cleanup();
    }

    // ─── INNER CLASSES ───────────────────────────────────────────────────

    static class RemotePlayer {
        float x, y, z;
        float yaw, pitch;
        float prevX, prevY, prevZ, prevYaw;
        byte hp, weapon, team;
        boolean alive = true;
        long lastUpdate;
        long snapshotReceiveTime;

        /** Returns interpolation factor [0..1] based on time since last snapshot. */
        float interpolationT() {
            long elapsed = System.currentTimeMillis() - snapshotReceiveTime;
            float snapshotInterval = 1000.0f / GameConfig.SNAPSHOT_RATE;
            return Math.min(1.0f, elapsed / snapshotInterval);
        }
    }

    /** Stores a predicted input for server reconciliation. */
    record PredictedInput(int sequence, byte keys, float yaw, float pitch, float posX, float posY, float posZ) {}
}
