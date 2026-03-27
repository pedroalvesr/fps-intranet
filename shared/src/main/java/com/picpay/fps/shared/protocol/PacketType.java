package com.picpay.fps.shared.protocol;

public enum PacketType {
    // Client → Server
    CONNECT((byte) 0x01),
    DISCONNECT((byte) 0x02),
    PLAYER_INPUT((byte) 0x03),
    SHOOT((byte) 0x04),
    CHAT((byte) 0x05),

    // Server → Client
    CONNECT_ACK((byte) 0x10),
    PLAYER_STATE((byte) 0x11),
    GAME_STATE((byte) 0x12),
    PLAYER_JOINED((byte) 0x13),
    PLAYER_LEFT((byte) 0x14),
    HIT_CONFIRM((byte) 0x15),
    KILL_EVENT((byte) 0x16),
    SPAWN((byte) 0x17),
    WORLD_SNAPSHOT((byte) 0x18);

    private final byte id;

    PacketType(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }

    public static PacketType fromId(byte id) {
        for (PacketType type : values()) {
            if (type.id == id) return type;
        }
        return null;
    }
}
