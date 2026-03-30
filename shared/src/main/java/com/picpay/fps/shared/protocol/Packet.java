package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Base packet format (v2 — online-ready):
 * [version: 1 byte][type: 1 byte][sequence: 2 bytes][sessionHash: 4 bytes][payload: variable]
 *
 * Header = 8 bytes total.
 * ConnectPacket sends sessionHash=0 (no session yet).
 * All other packets must include a valid session hash.
 */
public abstract class Packet {
    public static final byte PROTOCOL_VERSION = 1;
    public static final int HEADER_SIZE = 8;

    protected PacketType type;
    protected short sequence;
    protected int sessionHash;

    protected Packet(PacketType type) {
        this.type = type;
    }

    public PacketType getType() { return type; }
    public short getSequence() { return sequence; }
    public void setSequence(short sequence) { this.sequence = sequence; }
    public int getSessionHash() { return sessionHash; }
    public void setSessionHash(int sessionHash) { this.sessionHash = sessionHash; }

    public byte[] serialize() {
        byte[] payload = serializePayload();
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        buffer.put(PROTOCOL_VERSION);
        buffer.put(type.getId());
        buffer.putShort(sequence);
        buffer.putInt(sessionHash);
        buffer.put(payload);
        return buffer.array();
    }

    protected abstract byte[] serializePayload();

    public static byte peekVersion(byte[] data) {
        if (data == null || data.length < 1) return -1;
        return data[0];
    }

    public static PacketType peekType(byte[] data) {
        if (data == null || data.length < 2) return null;
        return PacketType.fromId(data[1]);
    }

    public static short peekSequence(byte[] data) {
        if (data == null || data.length < 4) return 0;
        return ByteBuffer.wrap(data, 2, 2).getShort();
    }

    public static int peekSessionHash(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) return 0;
        return ByteBuffer.wrap(data, 4, 4).getInt();
    }
}
