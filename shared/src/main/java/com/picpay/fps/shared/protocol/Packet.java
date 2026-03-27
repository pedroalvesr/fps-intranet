package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Base packet format:
 * [type: 1 byte][sequence: 2 bytes][payload: variable]
 */
public abstract class Packet {
    protected PacketType type;
    protected short sequence;

    protected Packet(PacketType type) {
        this.type = type;
    }

    public PacketType getType() { return type; }
    public short getSequence() { return sequence; }
    public void setSequence(short sequence) { this.sequence = sequence; }

    public byte[] serialize() {
        byte[] payload = serializePayload();
        ByteBuffer buffer = ByteBuffer.allocate(3 + payload.length);
        buffer.put(type.getId());
        buffer.putShort(sequence);
        buffer.put(payload);
        return buffer.array();
    }

    protected abstract byte[] serializePayload();

    public static PacketType peekType(byte[] data) {
        if (data == null || data.length < 1) return null;
        return PacketType.fromId(data[0]);
    }

    public static short peekSequence(byte[] data) {
        if (data == null || data.length < 3) return 0;
        return ByteBuffer.wrap(data, 1, 2).getShort();
    }
}
