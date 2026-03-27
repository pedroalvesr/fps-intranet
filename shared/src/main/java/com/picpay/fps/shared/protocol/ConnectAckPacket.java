package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

public class ConnectAckPacket extends Packet {
    private int playerId;
    private byte team; // 0 = red, 1 = blue

    public ConnectAckPacket() {
        super(PacketType.CONNECT_ACK);
    }

    public ConnectAckPacket(int playerId, byte team) {
        super(PacketType.CONNECT_ACK);
        this.playerId = playerId;
        this.team = team;
    }

    public int getPlayerId() { return playerId; }
    public byte getTeam() { return team; }

    @Override
    protected byte[] serializePayload() {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.putInt(playerId);
        buf.put(team);
        return buf.array();
    }

    public static ConnectAckPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, 3, data.length - 3);
        int playerId = buf.getInt();
        byte team = buf.get();
        ConnectAckPacket pkt = new ConnectAckPacket(playerId, team);
        pkt.setSequence(Packet.peekSequence(data));
        return pkt;
    }
}
