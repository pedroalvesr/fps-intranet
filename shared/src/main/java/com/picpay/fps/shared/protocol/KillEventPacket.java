package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Server → Client: someone was killed.
 */
public class KillEventPacket extends Packet {
    private int killerId;
    private int victimId;

    public KillEventPacket() {
        super(PacketType.KILL_EVENT);
    }

    public KillEventPacket(int killerId, int victimId) {
        super(PacketType.KILL_EVENT);
        this.killerId = killerId;
        this.victimId = victimId;
    }

    public int getKillerId() { return killerId; }
    public int getVictimId() { return victimId; }

    @Override
    protected byte[] serializePayload() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putInt(killerId);
        buf.putInt(victimId);
        return buf.array();
    }

    public static KillEventPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, 3, data.length - 3);
        int killer = buf.getInt();
        int victim = buf.getInt();
        KillEventPacket pkt = new KillEventPacket(killer, victim);
        pkt.setSequence(Packet.peekSequence(data));
        return pkt;
    }
}
