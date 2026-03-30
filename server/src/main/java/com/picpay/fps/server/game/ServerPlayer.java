package com.picpay.fps.server.game;

import com.picpay.fps.shared.math.AABB;
import org.joml.Vector3f;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Server-side player state. All authoritative game logic runs here.
 */
public class ServerPlayer {
    private final int id;
    private String name;
    private byte team; // 0=red, 1=blue
    private final Vector3f position = new Vector3f();
    private float yaw, pitch;
    private int hp;
    private byte weapon; // 0=pistol, 1=rifle
    private boolean alive;
    private float respawnTimer;
    private int kills, deaths;
    private int lastProcessedInput;
    private float shootCooldown;
    private boolean ready;
    private InetSocketAddress address;

    // Online session management
    private UUID sessionToken;
    private int sessionHash;
    private long lastHeartbeatTime;
    private boolean disconnected; // soft-disconnect for reconnect grace period
    private float disconnectTimer; // time since disconnect (for grace period)

    // Anti-cheat: track last position for speed validation
    private final Vector3f lastValidatedPosition = new Vector3f();

    public ServerPlayer(int id, String name, byte team) {
        this.id = id;
        this.name = name;
        this.team = team;
        this.hp = 100;
        this.weapon = 0;
        this.alive = true;
        this.respawnTimer = 0;
        this.shootCooldown = 0;
        this.lastHeartbeatTime = System.currentTimeMillis();

        // Generate session token
        this.sessionToken = UUID.randomUUID();
        this.sessionHash = com.picpay.fps.shared.protocol.ConnectAckPacket.computeSessionHash(sessionToken);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public byte getTeam() { return team; }
    public Vector3f getPosition() { return position; }
    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
    public byte getWeapon() { return weapon; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public float getRespawnTimer() { return respawnTimer; }
    public void setRespawnTimer(float t) { this.respawnTimer = t; }
    public int getKills() { return kills; }
    public void addKill() { this.kills++; }
    public int getDeaths() { return deaths; }
    public void addDeath() { this.deaths++; }
    public int getLastProcessedInput() { return lastProcessedInput; }
    public void setLastProcessedInput(int seq) { this.lastProcessedInput = seq; }
    public float getShootCooldown() { return shootCooldown; }
    public void setShootCooldown(float cd) { this.shootCooldown = cd; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public InetSocketAddress getAddress() { return address; }
    public void setAddress(InetSocketAddress address) { this.address = address; }

    // Session management
    public UUID getSessionToken() { return sessionToken; }
    public int getSessionHash() { return sessionHash; }
    public long getLastHeartbeatTime() { return lastHeartbeatTime; }
    public void updateHeartbeat() { this.lastHeartbeatTime = System.currentTimeMillis(); }
    public boolean isDisconnected() { return disconnected; }
    public void setDisconnected(boolean disconnected) { this.disconnected = disconnected; }
    public float getDisconnectTimer() { return disconnectTimer; }
    public void setDisconnectTimer(float t) { this.disconnectTimer = t; }

    // Anti-cheat
    public Vector3f getLastValidatedPosition() { return lastValidatedPosition; }
    public void updateValidatedPosition() { lastValidatedPosition.set(position); }

    public AABB getBoundingBox() {
        return AABB.fromCenter(position.x, position.y + 0.9f, position.z, 0.4f, 0.9f, 0.4f);
    }

    /**
     * Get the eye position (camera height).
     */
    public Vector3f getEyePosition() {
        return new Vector3f(position.x, position.y + 1.6f, position.z);
    }

    /**
     * Get the look direction from yaw/pitch.
     */
    public Vector3f getLookDirection() {
        float cosP = (float) Math.cos(pitch);
        return new Vector3f(
            (float) (Math.sin(yaw) * cosP),
            (float) -Math.sin(pitch),
            (float) (-Math.cos(yaw) * cosP)
        );
    }

    public void takeDamage(int damage) {
        if (!alive) return;
        hp = Math.max(0, hp - damage);
        if (hp <= 0) {
            alive = false;
            deaths++;
        }
    }

    public void respawn(float x, float y, float z) {
        position.set(x, y, z);
        lastValidatedPosition.set(x, y, z);
        hp = 100;
        alive = true;
        respawnTimer = 0;
        shootCooldown = 0;
    }

    /**
     * Reconnect: update address, reset disconnect state, refresh heartbeat.
     */
    public void reconnect(InetSocketAddress newAddress) {
        this.address = newAddress;
        this.disconnected = false;
        this.disconnectTimer = 0;
        this.lastHeartbeatTime = System.currentTimeMillis();
    }
}
