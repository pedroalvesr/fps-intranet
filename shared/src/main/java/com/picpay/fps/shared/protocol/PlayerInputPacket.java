package com.picpay.fps.shared.protocol;

import java.nio.ByteBuffer;

/**
 * Client → Server: player input state each tick.
 * Bitfield keys: [forward][back][left][right][jump][sprint][shoot]
 */
public class PlayerInputPacket extends Packet {
    private byte keys;       // bitfield
    private float yaw;       // horizontal look
    private float pitch;     // vertical look
    private int inputSequence; // for server reconciliation

    public PlayerInputPacket() {
        super(PacketType.PLAYER_INPUT);
    }

    public PlayerInputPacket(byte keys, float yaw, float pitch, int inputSequence) {
        super(PacketType.PLAYER_INPUT);
        this.keys = keys;
        this.yaw = yaw;
        this.pitch = pitch;
        this.inputSequence = inputSequence;
    }

    public byte getKeys() { return keys; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public int getInputSequence() { return inputSequence; }

    // Key bit helpers
    public boolean isForward()  { return (keys & 0x01) != 0; }
    public boolean isBack()     { return (keys & 0x02) != 0; }
    public boolean isLeft()     { return (keys & 0x04) != 0; }
    public boolean isRight()    { return (keys & 0x08) != 0; }
    public boolean isJump()     { return (keys & 0x10) != 0; }
    public boolean isSprint()   { return (keys & 0x20) != 0; }
    public boolean isShoot()    { return (keys & 0x40) != 0; }

    @Override
    protected byte[] serializePayload() {
        ByteBuffer buf = ByteBuffer.allocate(13);
        buf.put(keys);
        buf.putFloat(yaw);
        buf.putFloat(pitch);
        buf.putInt(inputSequence);
        return buf.array();
    }

    public static PlayerInputPacket deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, 3, data.length - 3);
        byte keys = buf.get();
        float yaw = buf.getFloat();
        float pitch = buf.getFloat();
        int seq = buf.getInt();
        PlayerInputPacket pkt = new PlayerInputPacket(keys, yaw, pitch, seq);
        pkt.setSequence(Packet.peekSequence(data));
        return pkt;
    }
}
