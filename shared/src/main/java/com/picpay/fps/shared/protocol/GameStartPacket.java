package com.picpay.fps.shared.protocol;

/**
 * Server → Client: game is starting (transition from lobby to playing).
 */
public class GameStartPacket extends Packet {

    public GameStartPacket() {
        super(PacketType.GAME_START);
    }

    @Override
    protected byte[] serializePayload() {
        return new byte[0];
    }

    public static GameStartPacket deserialize(byte[] data) {
        GameStartPacket pkt = new GameStartPacket();
        pkt.setSequence(Packet.peekSequence(data));
        pkt.setSessionHash(Packet.peekSessionHash(data));
        return pkt;
    }
}
