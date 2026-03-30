package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Server → Client: current lobby state (broadcast periodically while in lobby).
 * Contains list of connected players with name, team, and ready status.
 */
public class LobbyStatePacket extends Packet {
    private byte phase;          // 0=LOBBY, 1=COUNTDOWN, 2=PLAYING
    private byte countdown;      // seconds remaining (0 if not counting)
    private LobbyPlayer[] players;

    public LobbyStatePacket() {
        super(PacketType.LOBBY_STATE);
    }

    public LobbyStatePacket(byte phase, byte countdown, LobbyPlayer[] players) {
        super(PacketType.LOBBY_STATE);
        this.phase = phase;
        this.countdown = countdown;
        this.players = players;
    }

    public byte getPhase() { return phase; }
    public byte getCountdown() { return countdown; }
    public LobbyPlayer[] getPlayers() { return players; }

    @Override
    protected byte[] serializePayload() {
        // Calculate size: phase(1) + countdown(1) + playerCount(1) + players(variable)
        int size = 3;
        for (LobbyPlayer p : players) {
            size += 4 + 1 + 1 + 1 + p.name.getBytes(StandardCharsets.UTF_8).length; // id(4) + team(1) + ready(1) + nameLen(1) + name
        }

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(phase);
        buf.put(countdown);
        buf.put((byte) players.length);

        for (LobbyPlayer p : players) {
            buf.putInt(p.id);
            buf.put(p.team);
            buf.put(p.ready ? (byte) 1 : (byte) 0);
            byte[] nameBytes = p.name.getBytes(StandardCharsets.UTF_8);
            buf.put((byte) nameBytes.length);
            buf.put(nameBytes);
        }
        return buf.array();
    }

    public static LobbyStatePacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, Packet.HEADER_SIZE, data.length - Packet.HEADER_SIZE);
        byte phase = buf.get();
        byte countdown = buf.get();
        int count = buf.get() & 0xFF;

        LobbyPlayer[] players = new LobbyPlayer[count];
        for (int i = 0; i < count; i++) {
            int id = buf.getInt();
            byte team = buf.get();
            boolean ready = buf.get() == 1;
            int nameLen = buf.get() & 0xFF;
            byte[] nameBytes = new byte[Math.min(nameLen, buf.remaining())];
            buf.get(nameBytes);
            players[i] = new LobbyPlayer(id, new String(nameBytes, StandardCharsets.UTF_8), team, ready);
        }

        LobbyStatePacket pkt = new LobbyStatePacket(phase, countdown, players);
        pkt.setSequence(Packet.peekSequence(data));
        pkt.setSessionHash(Packet.peekSessionHash(data));
        return pkt;
    }

    public record LobbyPlayer(int id, String name, byte team, boolean ready) {}
}
