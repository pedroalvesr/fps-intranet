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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    // Incoming packets queue (polled by game loop)
    private final ConcurrentLinkedQueue<byte[]> incomingPackets = new ConcurrentLinkedQueue<>();

    public void connect(String host, String playerName) throws InterruptedException {
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

        // Send connect packet
        ConnectPacket pkt = new ConnectPacket(playerName);
        send(pkt.serialize());
    }

    public void send(byte[] data) {
        if (channel != null && serverAddress != null) {
            ByteBuf buf = Unpooled.wrappedBuffer(data);
            channel.writeAndFlush(new DatagramPacket(buf, serverAddress));
        }
    }

    public void sendInput(byte keys, float yaw, float pitch) {
        int seq = sequenceCounter.getAndIncrement();
        PlayerInputPacket pkt = new PlayerInputPacket(keys, yaw, pitch, seq);
        send(pkt.serialize());
    }

    public byte[] pollPacket() {
        return incomingPackets.poll();
    }

    public boolean isConnected() { return connected; }
    public int getLocalPlayerId() { return localPlayerId; }
    public byte getLocalTeam() { return localTeam; }

    public void disconnect() {
        if (channel != null) {
            Packet disconnectPkt = new Packet(PacketType.DISCONNECT) {
                @Override
                protected byte[] serializePayload() { return new byte[0]; }
            };
            send(disconnectPkt.serialize());
        }
        if (group != null) group.shutdownGracefully();
    }

    private class InboundHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
            ByteBuf content = msg.content();
            byte[] data = new byte[content.readableBytes()];
            content.readBytes(data);

            PacketType type = Packet.peekType(data);
            if (type == null) return;

            if (type == PacketType.CONNECT_ACK) {
                ConnectAckPacket ack = ConnectAckPacket.deserialize(data);
                localPlayerId = ack.getPlayerId();
                localTeam = ack.getTeam();
                connected = true;
                LOG.info("Connected! Player ID: " + localPlayerId + ", Team: " + (localTeam == 0 ? "RED" : "BLUE"));
            } else {
                incomingPackets.add(data);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.warning("Client network error: " + cause.getMessage());
        }
    }
}
