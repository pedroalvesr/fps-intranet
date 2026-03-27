package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Server → Client: state of a single player (broadcast each tick).
 */
public class PlayerStatePacket extends Packet {
    private int playerId;
    private float x, y, z;
    private float yaw, pitch;
    private byte hp;
    private byte weapon; // 0=pistol, 1=rifle
    private byte team;
    private int lastInputSequence; // for client reconciliation

    public PlayerStatePacket() {
        super(PacketType.PLAYER_STATE);
    }

    public PlayerStatePacket(int playerId, float x, float y, float z,
                             float yaw, float pitch, byte hp, byte weapon,
                             byte team, int lastInputSequence) {
        super(PacketType.PLAYER_STATE);
        this.playerId = playerId;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
        this.hp = hp; this.weapon = weapon;
        this.team = team;
        this.lastInputSequence = lastInputSequence;
    }

    public int getPlayerId() { return playerId; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public byte getHp() { return hp; }
    public byte getWeapon() { return weapon; }
    public byte getTeam() { return team; }
    public int getLastInputSequence() { return lastInputSequence; }

    @Override
    protected byte[] serializePayload() {
        ByteBuffer buf = ByteBuffer.allocate(31);
        buf.putInt(playerId);
        buf.putFloat(x);
        buf.putFloat(y);
        buf.putFloat(z);
        buf.putFloat(yaw);
        buf.putFloat(pitch);
        buf.put(hp);
        buf.put(weapon);
        buf.put(team);
        buf.putInt(lastInputSequence);
        return buf.array();
    }

    public static PlayerStatePacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, 3, data.length - 3);
        int id = buf.getInt();
        float x = buf.getFloat(), y = buf.getFloat(), z = buf.getFloat();
        float yaw = buf.getFloat(), pitch = buf.getFloat();
        byte hp = buf.get(), weapon = buf.get(), team = buf.get();
        int seq = buf.getInt();
        PlayerStatePacket pkt = new PlayerStatePacket(id, x, y, z, yaw, pitch, hp, weapon, team, seq);
        pkt.setSequence(Packet.peekSequence(data));
        return pkt;
    }
}
