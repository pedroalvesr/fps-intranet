package com.picpay.fps.server.game;

import com.picpay.fps.server.physics.PhysicsEngine;
import com.picpay.fps.shared.constants.GameConfig;
import com.picpay.fps.shared.protocol.*;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Authoritative game loop running at 64 ticks/second.
 */
public class GameLoop implements Runnable {
    private static final Logger LOG = Logger.getLogger(GameLoop.class.getName());

    private final Map<Integer, ServerPlayer> players = new ConcurrentHashMap<>();
    private final GameMap map = new GameMap();
    private final PhysicsEngine physics = new PhysicsEngine(map);
    private final AtomicInteger nextPlayerId = new AtomicInteger(1);
    private final AtomicInteger teamCounter = new AtomicInteger(0);
    private volatile boolean running = true;
    private int tick = 0;

    // Callback to send packets to clients
    private BiConsumer<ServerPlayer, byte[]> sendToPlayer;
    private java.util.function.Consumer<byte[]> broadcastAll;

    public void setSendToPlayer(BiConsumer<ServerPlayer, byte[]> fn) { this.sendToPlayer = fn; }
    public void setBroadcastAll(java.util.function.Consumer<byte[]> fn) { this.broadcastAll = fn; }

    public ServerPlayer addPlayer(String name, java.net.InetSocketAddress address) {
        int id = nextPlayerId.getAndIncrement();
        byte team = (byte) (teamCounter.getAndIncrement() % 2);
        ServerPlayer player = new ServerPlayer(id, name, team);
        player.setAddress(address);

        float[] spawn = map.getSpawnPoint(team);
        player.respawn(spawn[0], spawn[1], spawn[2]);

        players.put(id, player);
        LOG.info("Player joined: " + name + " (id=" + id + ", team=" + (team == 0 ? "RED" : "BLUE") + ")");
        return player;
    }

    public void removePlayer(int playerId) {
        ServerPlayer removed = players.remove(playerId);
        if (removed != null) {
            LOG.info("Player left: " + removed.getName());
        }
    }

    public ServerPlayer getPlayer(int id) { return players.get(id); }
    public Map<Integer, ServerPlayer> getPlayers() { return players; }

    public void processInput(int playerId, PlayerInputPacket input) {
        ServerPlayer player = players.get(playerId);
        if (player == null) return;

        float dt = (float) GameConfig.TICK_DURATION;

        physics.processMovement(player,
            input.isForward(), input.isBack(),
            input.isLeft(), input.isRight(),
            input.isSprint(),
            input.getYaw(), input.getPitch(), dt);

        player.setLastProcessedInput(input.getInputSequence());

        // Handle shooting
        if (input.isShoot() && player.getShootCooldown() <= 0 && player.isAlive()) {
            float fireRate = player.getWeapon() == 0 ? GameConfig.PISTOL_FIRE_RATE : GameConfig.RIFLE_FIRE_RATE;
            player.setShootCooldown(fireRate);

            PhysicsEngine.HitResult hit = physics.hitscan(player, players.values());
            if (hit != null) {
                int damage = player.getWeapon() == 0 ? GameConfig.PISTOL_DAMAGE : GameConfig.RIFLE_DAMAGE;
                hit.target().takeDamage(damage);

                if (!hit.target().isAlive()) {
                    player.addKill();
                    hit.target().setRespawnTimer(GameConfig.RESPAWN_TIME);
                    KillEventPacket killPkt = new KillEventPacket(player.getId(), hit.target().getId());
                    if (broadcastAll != null) broadcastAll.accept(killPkt.serialize());
                }
            }
        }
    }

    @Override
    public void run() {
        LOG.info("Game loop started at " + GameConfig.TICK_RATE + " ticks/s");
        long tickNanos = (long) (GameConfig.TICK_DURATION * 1_000_000_000);

        while (running) {
            long start = System.nanoTime();

            update();

            long elapsed = System.nanoTime() - start;
            long sleepNanos = tickNanos - elapsed;
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        LOG.info("Game loop stopped");
    }

    private void update() {
        tick++;
        float dt = (float) GameConfig.TICK_DURATION;

        // Update cooldowns and respawns
        for (ServerPlayer player : players.values()) {
            if (player.getShootCooldown() > 0) {
                player.setShootCooldown(player.getShootCooldown() - dt);
            }
            if (!player.isAlive()) {
                player.setRespawnTimer(player.getRespawnTimer() - dt);
                if (player.getRespawnTimer() <= 0) {
                    float[] spawn = map.getSpawnPoint(player.getTeam());
                    player.respawn(spawn[0], spawn[1], spawn[2]);
                    SpawnPacket spawnPkt = new SpawnPacket(player.getId(), spawn[0], spawn[1], spawn[2]);
                    if (broadcastAll != null) broadcastAll.accept(spawnPkt.serialize());
                }
            }
        }

        // Broadcast world snapshot
        broadcastWorldSnapshot();
    }

    private void broadcastWorldSnapshot() {
        if (broadcastAll == null || players.isEmpty()) return;

        // Serialize all player states into one snapshot
        int count = players.size();
        ByteBuffer statesBuf = ByteBuffer.allocate(count * 31); // 31 bytes per player state

        for (ServerPlayer p : players.values()) {
            statesBuf.putInt(p.getId());
            statesBuf.putFloat(p.getPosition().x);
            statesBuf.putFloat(p.getPosition().y);
            statesBuf.putFloat(p.getPosition().z);
            statesBuf.putFloat(p.getYaw());
            statesBuf.putFloat(p.getPitch());
            statesBuf.put((byte) p.getHp());
            statesBuf.put(p.getWeapon());
            statesBuf.put(p.getTeam());
            statesBuf.putInt(p.getLastProcessedInput());
        }

        WorldSnapshotPacket snapshot = new WorldSnapshotPacket(tick, count, statesBuf.array());
        broadcastAll.accept(snapshot.serialize());
    }

    public void stop() {
        running = false;
    }

    public GameMap getMap() { return map; }
}
