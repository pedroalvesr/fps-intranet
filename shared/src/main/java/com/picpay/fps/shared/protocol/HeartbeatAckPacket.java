package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Server → Client: heartbeat acknowledgment echoing the client's timestamp.
 */
public class HeartbeatAckPacket extends Packet {
    private long clientTimestamp;
    private int serverTick;

    public HeartbeatAckPacket() {
        super(PacketType.HEARTBEAT_ACK);
    }

    public HeartbeatAckPacket(long clientTimestamp, int serverTick) {
        super(PacketType.HEARTBEAT_ACK);
        this.clientTimestamp = clientTimestamp;
        this.serverTick = serverTick;
    }

    public long getClientTimestamp() { return clientTimestamp; }
    public int getServerTick() { return serverTick; }

    @Override
    protected byte[] serializePayload() {
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.putLong(clientTimestamp);
        buf.putInt(serverTick);
        return buf.array();
    }

    public static HeartbeatAckPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, Packet.HEADER_SIZE, data.length - Packet.HEADER_SIZE);
        long ts = buf.getLong();
        int tick = buf.getInt();
        HeartbeatAckPacket pkt = new HeartbeatAckPacket(ts, tick);
        pkt.setSequence(Packet.peekSequence(data));
        pkt.setSessionHash(Packet.peekSessionHash(data));
        return pkt;
    }
}
