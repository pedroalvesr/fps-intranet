package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Server → Client: player respawned at position.
 */
public class SpawnPacket extends Packet {
    private int playerId;
    private float x, y, z;

    public SpawnPacket() {
        super(PacketType.SPAWN);
    }

    public SpawnPacket(int playerId, float x, float y, float z) {
        super(PacketType.SPAWN);
        this.playerId = playerId;
        this.x = x; this.y = y; this.z = z;
    }

    public int getPlayerId() { return playerId; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }

    @Override
    protected byte[] serializePayload() {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putInt(playerId);
        buf.putFloat(x);
        buf.putFloat(y);
        buf.putFloat(z);
        return buf.array();
    }

    public static SpawnPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, Packet.HEADER_SIZE, data.length - Packet.HEADER_SIZE);
        int id = buf.getInt();
        float x = buf.getFloat(), y = buf.getFloat(), z = buf.getFloat();
        SpawnPacket pkt = new SpawnPacket(id, x, y, z);
        pkt.setSequence(Packet.peekSequence(data));
        pkt.setSessionHash(Packet.peekSessionHash(data));
        return pkt;
    }
}
