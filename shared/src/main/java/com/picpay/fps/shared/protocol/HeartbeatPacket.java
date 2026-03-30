package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Bidirectional heartbeat. Client sends periodically, server echoes back as HeartbeatAckPacket.
 * Payload carries a timestamp so the client can measure RTT.
 */
public class HeartbeatPacket extends Packet {
    private long clientTimestamp;

    public HeartbeatPacket() {
        super(PacketType.HEARTBEAT);
    }

    public HeartbeatPacket(long clientTimestamp) {
        super(PacketType.HEARTBEAT);
        this.clientTimestamp = clientTimestamp;
    }

    public long getClientTimestamp() { return clientTimestamp; }

    @Override
    protected byte[] serializePayload() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putLong(clientTimestamp);
        return buf.array();
    }

    public static HeartbeatPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, Packet.HEADER_SIZE, data.length - Packet.HEADER_SIZE);
        long ts = buf.getLong();
        HeartbeatPacket pkt = new HeartbeatPacket(ts);
        pkt.setSequence(Packet.peekSequence(data));
        pkt.setSessionHash(Packet.peekSessionHash(data));
        return pkt;
    }
}
