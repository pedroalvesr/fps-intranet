package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ConnectPacket extends Packet {
    private String playerName;

    public ConnectPacket() {
        super(PacketType.CONNECT);
    }

    public ConnectPacket(String playerName) {
        super(PacketType.CONNECT);
        this.playerName = playerName;
    }

    public String getPlayerName() { return playerName; }

    @Override
    protected byte[] serializePayload() {
        byte[] nameBytes = playerName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(1 + nameBytes.length);
        buf.put((byte) nameBytes.length);
        buf.put(nameBytes);
        return buf.array();
    }

    public static ConnectPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, 3, data.length - 3);
        int nameLen = buf.get() & 0xFF;
        byte[] nameBytes = new byte[nameLen];
        buf.get(nameBytes);
        ConnectPacket pkt = new ConnectPacket(new String(nameBytes, StandardCharsets.UTF_8));
        pkt.setSequence(Packet.peekSequence(data));
        return pkt;
    }
}
