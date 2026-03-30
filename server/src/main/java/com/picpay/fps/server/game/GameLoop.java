package com.picpay.fps.server.game;

import com.picpay.fps.server.physics.PhysicsEngine;
import com.picpay.fps.shared.constants.GameConfig;
import com.picpay.fps.shared.protocol.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Authoritative game loop running at 64 ticks/second.
 * Manages phases: LOBBY, COUNTDOWN, PLAYING.
 * Snapshots broadcast at configurable rate (default 20Hz).
 */
public class GameLoop implements Runnable {
    private static final Logger LOG = Logger.getLogger(GameLoop.class.getName());

    public enum Phase { LOBBY, COUNTDOWN, PLAYING }

    private final Map<Integer, ServerPlayer> players = new ConcurrentHashMap<>();
    private final GameMap map = new GameMap();
    private final PhysicsEngine physics = new PhysicsEngine(map);
    private final AtomicInteger nextPlayerId = new AtomicInteger(1);
    private final AtomicInteger teamCounter = new AtomicInteger(0);
    private volatile boolean running = true;
    private int tick = 0;

    // Phase management
    private volatile Phase phase = Phase.LOBBY;
    private float countdownTimer = 0;
    private float lobbyBroadcastTimer = 0;

    // Snapshot rate control
    private float snapshotTimer = 0;

    // Heartbeat timeout check timer (runs every second, not every tick)
    private float heartbeatCheckTimer = 0;
    private static final float HEARTBEAT_CHECK_INTERVAL = 1.0f;

    // Input rate limiting: track inputs processed per tick per player
    private final Map<Integer, Integer> inputsThisTick = new ConcurrentHashMap<>();

    // Callback to send packets to clients
    private BiConsumer<ServerPlayer, byte[]> sendToPlayer;
    private Consumer<byte[]> broadcastAll;

    // Callback to remove player sessions (set by ServerNetworkManager)
    private Consumer<ServerPlayer> removePlayerSession;

    public void setSendToPlayer(BiConsumer<ServerPlayer, byte[]> fn) { this.sendToPlayer = fn; }
    public void setBroadcastAll(Consumer<byte[]> fn) { this.broadcastAll = fn; }
    public void setRemovePlayerSession(Consumer<ServerPlayer> fn) { this.removePlayerSession = fn; }

    public Phase getPhase() { return phase; }
    public int getTick() { return tick; }

    public ServerPlayer addPlayer(String name, java.net.InetSocketAddress address) {
        int id = nextPlayerId.getAndIncrement();
        byte team = (byte) (teamCounter.getAndIncrement() % 2);
        ServerPlayer player = new ServerPlayer(id, name, team);
        player.setAddress(address);

        // In lobby, don't spawn yet — just register
        if (phase == Phase.PLAYING) {
            float[] spawn = map.getSpawnPoint(team);
            player.respawn(spawn[0], spawn[1], spawn[2]);
        }

        players.put(id, player);
        LOG.info("Player joined: " + name + " (id=" + id + ", team=" + (team == 0 ? "RED" : "BLUE") + ") [" + phase + "]");
        return player;
    }

    public void removePlayer(int playerId) {
        ServerPlayer removed = players.remove(playerId);
        if (removed != null) {
            LOG.info("Player removed: " + removed.getName());
            if (removePlayerSession != null) removePlayerSession.accept(removed);
            // If in countdown and not enough players/ready anymore, cancel
            if (phase == Phase.COUNTDOWN) {
                checkCountdownConditions();
            }
        }
    }

    public void setPlayerReady(int playerId, boolean ready) {
        ServerPlayer player = players.get(playerId);
        if (player == null || phase == Phase.PLAYING) return;
        player.setReady(ready);
        LOG.info("Player " + player.getName() + " is " + (ready ? "READY" : "NOT READY"));
        checkCountdownConditions();
    }

    private void checkCountdownConditions() {
        if (phase == Phase.PLAYING) return;

        int totalPlayers = (int) players.values().stream().filter(p -> !p.isDisconnected()).count();
        long readyCount = players.values().stream().filter(p -> !p.isDisconnected() && p.isReady()).count();
        boolean enoughPlayers = totalPlayers >= GameConfig.MIN_PLAYERS_TO_START;
        boolean allReady = readyCount == totalPlayers && totalPlayers > 0;

        if (enoughPlayers && allReady && phase != Phase.COUNTDOWN) {
            phase = Phase.COUNTDOWN;
            countdownTimer = GameConfig.LOBBY_COUNTDOWN_SECONDS;
            LOG.info("All players ready! Countdown started: " + GameConfig.LOBBY_COUNTDOWN_SECONDS + "s");
        } else if (phase == Phase.COUNTDOWN && (!enoughPlayers || !allReady)) {
            phase = Phase.LOBBY;
            countdownTimer = 0;
            LOG.info("Countdown cancelled — not all players ready");
        }
    }

    private void startGame() {
        phase = Phase.PLAYING;
        LOG.info("=== GAME STARTED with " + players.size() + " players ===");

        // Spawn all players
        for (ServerPlayer player : players.values()) {
            if (player.isDisconnected()) continue;
            float[] spawn = map.getSpawnPoint(player.getTeam());
            player.respawn(spawn[0], spawn[1], spawn[2]);
        }

        // Broadcast game start
        GameStartPacket startPkt = new GameStartPacket();
        if (broadcastAll != null) broadcastAll.accept(startPkt.serialize());

        // Broadcast spawns
        for (ServerPlayer player : players.values()) {
            if (player.isDisconnected()) continue;
            SpawnPacket spawnPkt = new SpawnPacket(player.getId(),
                player.getPosition().x, player.getPosition().y, player.getPosition().z);
            if (broadcastAll != null) broadcastAll.accept(spawnPkt.serialize());
        }
    }

    public ServerPlayer getPlayer(int id) { return players.get(id); }
    public Map<Integer, ServerPlayer> getPlayers() { return players; }

    public void processInput(int playerId, PlayerInputPacket input) {
        // Only process game input during PLAYING phase
        if (phase != Phase.PLAYING) return;

        ServerPlayer player = players.get(playerId);
        if (player == null || player.isDisconnected()) return;

        // Rate limit: max 1 input per tick per player
        int count = inputsThisTick.merge(playerId, 1, Integer::sum);
        if (count > 1) return; // discard excess

        float dt = (float) GameConfig.TICK_DURATION;

        physics.processMovement(player,
            input.isForward(), input.isBack(),
            input.isLeft(), input.isRight(),
            input.isSprint(),
            input.getYaw(), input.getPitch(), dt);

        // Speed validation (anti-cheat)
        float distSq = player.getPosition().distanceSquared(player.getLastValidatedPosition());
        float maxDist = GameConfig.MAX_SPEED_PER_TICK;
        if (distSq > maxDist * maxDist * 4) { // generous tolerance (2x)
            // Snap back — possible speed hack
            player.getPosition().set(player.getLastValidatedPosition());
            LOG.warning("Speed violation from player " + player.getName() + " (id=" + playerId + ")");
        } else {
            player.updateValidatedPosition();
        }

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
        LOG.info("Snapshot rate: " + GameConfig.SNAPSHOT_RATE + " Hz");
        LOG.info("Waiting for players in LOBBY...");
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

        // Clear per-tick rate limit counters
        inputsThisTick.clear();

        // Check heartbeat timeouts periodically
        heartbeatCheckTimer -= dt;
        if (heartbeatCheckTimer <= 0) {
            checkHeartbeatTimeouts();
            heartbeatCheckTimer = HEARTBEAT_CHECK_INTERVAL;
        }

        // Update disconnect grace period timers
        updateDisconnectTimers(dt);

        switch (phase) {
            case LOBBY -> updateLobby(dt);
            case COUNTDOWN -> updateCountdown(dt);
            case PLAYING -> updatePlaying(dt);
        }
    }

    private void checkHeartbeatTimeouts() {
        long now = System.currentTimeMillis();
        long timeoutMs = (long) (GameConfig.HEARTBEAT_TIMEOUT * 1000);

        for (ServerPlayer player : players.values()) {
            if (player.isDisconnected()) continue;
            if (now - player.getLastHeartbeatTime() > timeoutMs) {
                LOG.info("Player " + player.getName() + " timed out (no heartbeat for " +
                    GameConfig.HEARTBEAT_TIMEOUT + "s)");
                player.setDisconnected(true);
                player.setDisconnectTimer(0);
            }
        }
    }

    private void updateDisconnectTimers(float dt) {
        List<Integer> toRemove = new ArrayList<>();
        for (ServerPlayer player : players.values()) {
            if (player.isDisconnected()) {
                player.setDisconnectTimer(player.getDisconnectTimer() + dt);
                if (player.getDisconnectTimer() >= GameConfig.RECONNECT_GRACE_PERIOD) {
                    toRemove.add(player.getId());
                }
            }
        }
        for (int id : toRemove) {
            LOG.info("Removing player " + id + " after reconnect grace period expired");
            removePlayer(id);
        }
    }

    private void updateLobby(float dt) {
        lobbyBroadcastTimer -= dt;
        if (lobbyBroadcastTimer <= 0) {
            broadcastLobbyState();
            lobbyBroadcastTimer = GameConfig.LOBBY_BROADCAST_RATE;
        }
    }

    private void updateCountdown(float dt) {
        countdownTimer -= dt;

        lobbyBroadcastTimer -= dt;
        if (lobbyBroadcastTimer <= 0) {
            broadcastLobbyState();
            lobbyBroadcastTimer = GameConfig.LOBBY_BROADCAST_RATE;
        }

        if (countdownTimer <= 0) {
            startGame();
        }
    }

    private void updatePlaying(float dt) {
        // Update cooldowns and respawns
        for (ServerPlayer player : players.values()) {
            if (player.isDisconnected()) continue;
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

        // Broadcast world snapshot at reduced rate
        snapshotTimer -= dt;
        if (snapshotTimer <= 0) {
            broadcastWorldSnapshot();
            snapshotTimer = (float) GameConfig.SNAPSHOT_INTERVAL;
        }
    }

    private void broadcastLobbyState() {
        if (broadcastAll == null) return;

        LobbyStatePacket.LobbyPlayer[] lobbyPlayers = players.values().stream()
            .filter(p -> !p.isDisconnected())
            .map(p -> new LobbyStatePacket.LobbyPlayer(p.getId(), p.getName(), p.getTeam(), p.isReady()))
            .toArray(LobbyStatePacket.LobbyPlayer[]::new);

        byte phaseId = switch (phase) {
            case LOBBY -> (byte) 0;
            case COUNTDOWN -> (byte) 1;
            case PLAYING -> (byte) 2;
        };

        LobbyStatePacket pkt = new LobbyStatePacket(phaseId, (byte) Math.max(0, Math.ceil(countdownTimer)), lobbyPlayers);
        broadcastAll.accept(pkt.serialize());
    }

    private void broadcastWorldSnapshot() {
        if (broadcastAll == null || players.isEmpty()) return;

        // Only include active (non-disconnected) players
        List<ServerPlayer> activePlayers = players.values().stream()
            .filter(p -> !p.isDisconnected())
            .toList();

        int count = activePlayers.size();
        if (count == 0) return;

        ByteBuffer statesBuf = ByteBuffer.allocate(count * 31);

        for (ServerPlayer p : activePlayers) {
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
