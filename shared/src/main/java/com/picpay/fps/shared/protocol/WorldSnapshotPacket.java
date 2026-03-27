package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Server → Client: full snapshot of all players (sent periodically).
 */
public class WorldSnapshotPacket extends Packet {
    private int tick;
    private int playerCount;
    private byte[] playerStatesData; // raw serialized PlayerState entries

    public WorldSnapshotPacket() {
        super(PacketType.WORLD_SNAPSHOT);
    }

    public WorldSnapshotPacket(int tick, int playerCount, byte[] playerStatesData) {
        super(PacketType.WORLD_SNAPSHOT);
        this.tick = tick;
        this.playerCount = playerCount;
        this.playerStatesData = playerStatesData;
    }

    public int getTick() { return tick; }
    public int getPlayerCount() { return playerCount; }
    public byte[] getPlayerStatesData() { return playerStatesData; }

    @Override
    protected byte[] serializePayload() {
        ByteBuffer buf = ByteBuffer.allocate(5 + playerStatesData.length);
        buf.putInt(tick);
        buf.put((byte) playerCount);
        buf.put(playerStatesData);
        return buf.array();
    }

    public static WorldSnapshotPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, 3, data.length - 3);
        int tick = buf.getInt();
        int count = buf.get() & 0xFF;
        byte[] states = new byte[buf.remaining()];
        buf.get(states);
        WorldSnapshotPacket pkt = new WorldSnapshotPacket(tick, count, states);
        pkt.setSequence(Packet.peekSequence(data));
        return pkt;
    }
}
