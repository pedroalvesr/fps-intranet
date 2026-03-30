package com.picpay.fps.shared.protocol;

/**
 * Client → Server: graceful disconnect.
 * Server responds with DISCONNECT_ACK.
 */
public class DisconnectPacket extends Packet {

    public DisconnectPacket() {
        super(PacketType.DISCONNECT);
    }

    @Override
    protected byte[] serializePayload() {
        return new byte[0];
    }

    public static DisconnectPacket deserialize(byte[] data) {
        DisconnectPacket pkt = new DisconnectPacket();
        pkt.setSequence(Packet.peekSequence(data));
        pkt.setSessionHash(Packet.peekSessionHash(data));
        return pkt;
    }
}
