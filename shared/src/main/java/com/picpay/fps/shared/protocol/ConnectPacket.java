package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Client → Server: connection request.
 * Payload: [protocolVersion: 1B][nameLen: 1B][name: variable]
 * Session hash is 0 (no session yet).
 */
public class ConnectPacket extends Packet {
    private byte protocolVersion;
    private String playerName;

    public ConnectPacket() {
        super(PacketType.CONNECT);
    }

    public ConnectPacket(String playerName) {
        super(PacketType.CONNECT);
        this.protocolVersion = Packet.PROTOCOL_VERSION;
        this.playerName = sanitizeName(playerName);
        this.sessionHash = 0; // no session yet
    }

    public byte getProtocolVersion() { return protocolVersion; }
    public String getPlayerName() { return playerName; }

    @Override
    protected byte[] serializePayload() {
        byte[] nameBytes = playerName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(2 + nameBytes.length);
        buf.put(protocolVersion);
        buf.put((byte) nameBytes.length);
        buf.put(nameBytes);
        return buf.array();
    }

    public static ConnectPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, Packet.HEADER_SIZE, data.length - Packet.HEADER_SIZE);
        byte version = buf.get();
        int nameLen = buf.get() & 0xFF;
        byte[] nameBytes = new byte[Math.min(nameLen, buf.remaining())];
        buf.get(nameBytes);
        ConnectPacket pkt = new ConnectPacket(new String(nameBytes, StandardCharsets.UTF_8));
        pkt.protocolVersion = version;
        pkt.setSequence(Packet.peekSequence(data));
        pkt.setSessionHash(Packet.peekSessionHash(data));
        return pkt;
    }

    /** Sanitize player name: max 16 chars, ASCII printable only. */
    static String sanitizeName(String name) {
        if (name == null || name.isEmpty()) return "Player";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length() && sb.length() < 16; i++) {
            char c = name.charAt(i);
            if (c >= 0x20 && c <= 0x7E) {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "Player" : sb.toString();
    }
}
