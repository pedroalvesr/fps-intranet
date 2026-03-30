package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Server → Client: connection acknowledged.
 * Payload: [playerId: 4B][team: 1B][sessionTokenHigh: 8B][sessionTokenLow: 8B]
 *
 * The client must compute a 4-byte hash of the UUID and include it
 * in the sessionHash header field of all subsequent packets.
 */
public class ConnectAckPacket extends Packet {
    private int playerId;
    private byte team;
    private UUID sessionToken;

    public ConnectAckPacket() {
        super(PacketType.CONNECT_ACK);
    }

    public ConnectAckPacket(int playerId, byte team, UUID sessionToken) {
        super(PacketType.CONNECT_ACK);
        this.playerId = playerId;
        this.team = team;
        this.sessionToken = sessionToken;
    }

    public int getPlayerId() { return playerId; }
    public byte getTeam() { return team; }
    public UUID getSessionToken() { return sessionToken; }

    @Override
    protected byte[] serializePayload() {
        ByteBuffer buf = ByteBuffer.allocate(21);
        buf.putInt(playerId);
        buf.put(team);
        buf.putLong(sessionToken.getMostSignificantBits());
        buf.putLong(sessionToken.getLeastSignificantBits());
        return buf.array();
    }

    public static ConnectAckPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, Packet.HEADER_SIZE, data.length - Packet.HEADER_SIZE);
        int playerId = buf.getInt();
        byte team = buf.get();
        long msb = buf.getLong();
        long lsb = buf.getLong();
        UUID token = new UUID(msb, lsb);
        ConnectAckPacket pkt = new ConnectAckPacket(playerId, team, token);
        pkt.setSequence(Packet.peekSequence(data));
        pkt.setSessionHash(Packet.peekSessionHash(data));
        return pkt;
    }

    /**
     * Compute a 4-byte hash of a UUID for the packet header.
     * XORs the high and low 32-bit halves for distribution.
     */
    public static int computeSessionHash(UUID token) {
        long bits = token.getMostSignificantBits() ^ token.getLeastSignificantBits();
        return (int) (bits ^ (bits >>> 32));
    }
}
