package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Client → Server: player toggling ready status.
 */
public class PlayerReadyPacket extends Packet {
    private boolean ready;

    public PlayerReadyPacket() {
        super(PacketType.PLAYER_READY);
    }

    public PlayerReadyPacket(boolean ready) {
        super(PacketType.PLAYER_READY);
        this.ready = ready;
    }

    public boolean isReady() { return ready; }

    @Override
    protected byte[] serializePayload() {
        return new byte[]{ (byte) (ready ? 1 : 0) };
    }

    public static PlayerReadyPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, Packet.HEADER_SIZE, data.length - Packet.HEADER_SIZE);
        boolean ready = buf.get() == 1;
        PlayerReadyPacket pkt = new PlayerReadyPacket(ready);
        pkt.setSequence(Packet.peekSequence(data));
        pkt.setSessionHash(Packet.peekSessionHash(data));
        return pkt;
    }
}
