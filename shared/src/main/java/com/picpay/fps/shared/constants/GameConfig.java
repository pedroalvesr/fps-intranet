package com.picpay.fps.shared.constants;

public final class GameConfig {
    // Networking
    public static final int SERVER_PORT = 27015;
    public static final int TICK_RATE = 64;
    public static final double TICK_DURATION = 1.0 / TICK_RATE;
    public static final int MAX_PLAYERS = 20;
    public static final int INTERPOLATION_DELAY_MS = 100;

    // Online networking
    public static final int SNAPSHOT_RATE = 20; // snapshots per second (was implicitly 64)
    public static final double SNAPSHOT_INTERVAL = 1.0 / SNAPSHOT_RATE;
    public static final float HEARTBEAT_INTERVAL = 1.0f; // client sends heartbeat every 1s
    public static final float HEARTBEAT_TIMEOUT = 10.0f; // disconnect after 10s without heartbeat
    public static final float RECONNECT_GRACE_PERIOD = 30.0f; // seconds to allow reconnect
    public static final int MAX_CONNECT_ATTEMPTS_PER_MINUTE = 5;
    public static final float MAX_SPEED_PER_TICK = 8.0f * (float) TICK_DURATION * 1.5f; // sprint speed * dt * tolerance

    // Reliability
    public static final int RELIABLE_RETRANSMIT_MS = 200; // retry reliable packet every 200ms
    public static final int RELIABLE_MAX_RETRIES = 10; // give up after 10 retries (2s)

    // Gameplay
    public static final float PLAYER_SPEED = 5.0f;
    public static final float PLAYER_SPRINT_SPEED = 7.5f;
    public static final int PLAYER_MAX_HP = 100;
    public static final float PLAYER_HEIGHT = 1.8f;
    public static final float PLAYER_RADIUS = 0.4f;
    public static final float RESPAWN_TIME = 3.0f;
    public static final float GRAVITY = 9.81f;
    public static final float JUMP_VELOCITY = 5.0f;

    // Lobby
    public static final int MIN_PLAYERS_TO_START = 2;
    public static final int LOBBY_COUNTDOWN_SECONDS = 10;
    public static final float LOBBY_BROADCAST_RATE = 0.5f; // seconds between lobby state broadcasts

    // Weapons
    public static final int PISTOL_DAMAGE = 25;
    public static final float PISTOL_FIRE_RATE = 0.3f;
    public static final int PISTOL_AMMO = 12;
    public static final int RIFLE_DAMAGE = 15;
    public static final float RIFLE_FIRE_RATE = 0.1f;
    public static final int RIFLE_AMMO = 30;

    // Map
    public static final float MAP_SIZE = 40.0f;
    public static final float WALL_HEIGHT = 4.0f;

    // Rendering
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final float FOV = 90.0f;
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 200.0f;
    public static final float MOUSE_SENSITIVITY = 0.003f;

    private GameConfig() {}
}
