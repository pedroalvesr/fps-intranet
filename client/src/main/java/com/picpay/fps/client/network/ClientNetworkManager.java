package com.picpay.fps.client.network;

import com.picpay.fps.shared.constants.GameConfig;
import com.picpay.fps.shared.protocol.*;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class ClientNetworkManager {
    private static final Logger LOG = Logger.getLogger(ClientNetworkManager.class.getName());

    private Channel channel;
    private EventLoopGroup group;
    private InetSocketAddress serverAddress;

    // Connection state
    private volatile int localPlayerId = -1;
    private volatile byte localTeam = -1;
    private volatile boolean connected = false;
    private final AtomicInteger sequenceCounter = new AtomicInteger(0);

    // Session
    private volatile UUID sessionToken;
    private volatile int sessionHash = 0;

    // RTT measurement
    private volatile long lastRttMs = 0;
    private volatile long smoothedRttMs = 0;

    // Heartbeat
    private long lastHeartbeatSent = 0;

    // Incoming packets queue (polled by game loop)
    private final ConcurrentLinkedQueue<byte[]> incomingPackets = new ConcurrentLinkedQueue<>();

    // Reconnect
    private volatile boolean disconnectedByServer = false;
    private String playerName;
    private String host;

    public void connect(String host, String playerName) throws InterruptedException {
        this.host = host;
        this.playerName = playerName;
        serverAddress = new InetSocketAddress(host, GameConfig.SERVER_PORT);
        group = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioDatagramChannel.class)
            .handler(new ChannelInitializer<NioDatagramChannel>() {
                @Override
                protected void initChannel(NioDatagramChannel ch) {
                    ch.pipeline().addLast(new InboundHandler());
                }
            });

        channel = bootstrap.bind(0).sync().channel();
        LOG.info("Connecting to " + host + ":" + GameConfig.SERVER_PORT);

        // Send connect packet (sessionHash=0, no session yet)
        ConnectPacket pkt = new ConnectPacket(playerName);
        send(pkt.serialize());
    }

    public void send(byte[] data) {
        if (channel != null && serverAddress != null) {
            ByteBuf buf = Unpooled.wrappedBuffer(data);
            channel.writeAndFlush(new DatagramPacket(buf, serverAddress));
        }
    }

    /** Send with session hash stamped into the header. */
    private void sendWithSession(Packet pkt) {
        pkt.setSessionHash(sessionHash);
        send(pkt.serialize());
    }

    public void sendInput(byte keys, float yaw, float pitch) {
        int seq = sequenceCounter.getAndIncrement();
        PlayerInputPacket pkt = new PlayerInputPacket(keys, yaw, pitch, seq);
        pkt.setSessionHash(sessionHash);
        send(pkt.serialize());
    }

    /**
     * Called every frame from the game loop. Handles heartbeat timing.
     */
    public void tick() {
        if (!connected) return;

        long now = System.currentTimeMillis();
        long heartbeatIntervalMs = (long) (GameConfig.HEARTBEAT_INTERVAL * 1000);

        if (now - lastHeartbeatSent >= heartbeatIntervalMs) {
            HeartbeatPacket hb = new HeartbeatPacket(now);
            sendWithSession(hb);
            lastHeartbeatSent = now;
        }
    }

    public byte[] pollPacket() {
        return incomingPackets.poll();
    }

    public boolean isConnected() { return connected; }
    public int getLocalPlayerId() { return localPlayerId; }
    public byte getLocalTeam() { return localTeam; }
    public long getRttMs() { return smoothedRttMs; }
    public boolean isDisconnectedByServer() { return disconnectedByServer; }

    public void sendReady(boolean ready) {
        PlayerReadyPacket pkt = new PlayerReadyPacket(ready);
        sendWithSession(pkt);
    }

    public void disconnect() {
        if (channel != null && connected) {
            DisconnectPacket pkt = new DisconnectPacket();
            sendWithSession(pkt);
        }
        if (group != null) group.shutdownGracefully();
        connected = false;
    }

    /**
     * Attempt to reconnect after a disconnect.
     */
    public void attemptReconnect() throws InterruptedException {
        if (group != null) group.shutdownGracefully();
        sessionHash = 0;
        connected = false;
        disconnectedByServer = false;
        connect(host, playerName);
    }

    private class InboundHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
            ByteBuf content = msg.content();
            byte[] data = new byte[content.readableBytes()];
            content.readBytes(data);

            if (data.length < Packet.HEADER_SIZE) return;

            // Protocol version check
            if (Packet.peekVersion(data) != Packet.PROTOCOL_VERSION) return;

            PacketType type = Packet.peekType(data);
            if (type == null) return;

            switch (type) {
                case CONNECT_ACK -> handleConnectAck(data);
                case HEARTBEAT_ACK -> handleHeartbeatAck(data);
                default -> incomingPackets.add(data);
            }
        }

        private void handleConnectAck(byte[] data) {
            ConnectAckPacket ack = ConnectAckPacket.deserialize(data);
            localPlayerId = ack.getPlayerId();
            localTeam = ack.getTeam();
            sessionToken = ack.getSessionToken();
            sessionHash = ConnectAckPacket.computeSessionHash(sessionToken);
            connected = true;
            disconnectedByServer = false;
            LOG.info("Connected! Player ID: " + localPlayerId +
                ", Team: " + (localTeam == 0 ? "RED" : "BLUE") +
                ", Session: " + sessionToken);
        }

        private void handleHeartbeatAck(byte[] data) {
            HeartbeatAckPacket ack = HeartbeatAckPacket.deserialize(data);
            long now = System.currentTimeMillis();
            lastRttMs = now - ack.getClientTimestamp();
            // Exponential moving average for smooth RTT
            if (smoothedRttMs == 0) {
                smoothedRttMs = lastRttMs;
            } else {
                smoothedRttMs = (smoothedRttMs * 7 + lastRttMs) / 8;
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.warning("Client network error: " + cause.getMessage());
        }
    }
}
