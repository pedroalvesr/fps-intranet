package com.picpay.fps.server.network;

import com.picpay.fps.server.game.GameLoop;
import com.picpay.fps.server.game.ServerPlayer;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ServerNetworkManager {
    private static final Logger LOG = Logger.getLogger(ServerNetworkManager.class.getName());

    private final GameLoop gameLoop;
    private final Map<InetSocketAddress, Integer> addressToPlayerId = new ConcurrentHashMap<>();
    private Channel channel;
    private EventLoopGroup group;

    public ServerNetworkManager(GameLoop gameLoop) {
        this.gameLoop = gameLoop;
    }

    public void start() throws InterruptedException {
        group = new NioEventLoopGroup();

        gameLoop.setSendToPlayer((player, data) -> sendToAddress(player.getAddress(), data));
        gameLoop.setBroadcastAll(this::broadcastToAll);

        Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioDatagramChannel.class)
            .option(ChannelOption.SO_BROADCAST, true)
            .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
            .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
            .handler(new ChannelInitializer<NioDatagramChannel>() {
                @Override
                protected void initChannel(NioDatagramChannel ch) {
                    ch.pipeline().addLast(new PacketHandler());
                }
            });

        channel = bootstrap.bind(GameConfig.SERVER_PORT).sync().channel();
        LOG.info("Server listening on UDP port " + GameConfig.SERVER_PORT);
    }

    public void stop() {
        if (group != null) group.shutdownGracefully();
    }

    private void sendToAddress(InetSocketAddress address, byte[] data) {
        if (channel != null && address != null) {
            ByteBuf buf = Unpooled.wrappedBuffer(data);
            channel.writeAndFlush(new DatagramPacket(buf, address));
        }
    }

    private void broadcastToAll(byte[] data) {
        for (ServerPlayer player : gameLoop.getPlayers().values()) {
            if (player.getAddress() != null) {
                sendToAddress(player.getAddress(), data);
            }
        }
    }

    private class PacketHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
            InetSocketAddress sender = msg.sender();
            ByteBuf content = msg.content();
            byte[] data = new byte[content.readableBytes()];
            content.readBytes(data);

            PacketType type = Packet.peekType(data);
            if (type == null) return;

            switch (type) {
                case CONNECT -> handleConnect(sender, data);
                case PLAYER_INPUT -> handleInput(sender, data);
                case DISCONNECT -> handleDisconnect(sender);
                default -> {}
            }
        }

        private void handleConnect(InetSocketAddress sender, byte[] data) {
            if (addressToPlayerId.containsKey(sender)) return; // already connected
            if (gameLoop.getPlayers().size() >= GameConfig.MAX_PLAYERS) return;

            ConnectPacket pkt = ConnectPacket.deserialize(data);
            ServerPlayer player = gameLoop.addPlayer(pkt.getPlayerName(), sender);
            addressToPlayerId.put(sender, player.getId());

            ConnectAckPacket ack = new ConnectAckPacket(player.getId(), player.getTeam());
            sendToAddress(sender, ack.serialize());

            // Notify others
            SpawnPacket spawnPkt = new SpawnPacket(player.getId(),
                player.getPosition().x, player.getPosition().y, player.getPosition().z);
            broadcastToAll(spawnPkt.serialize());
        }

        private void handleInput(InetSocketAddress sender, byte[] data) {
            Integer playerId = addressToPlayerId.get(sender);
            if (playerId == null) return;

            PlayerInputPacket input = PlayerInputPacket.deserialize(data);
            gameLoop.processInput(playerId, input);
        }

        private void handleDisconnect(InetSocketAddress sender) {
            Integer playerId = addressToPlayerId.remove(sender);
            if (playerId != null) {
                gameLoop.removePlayer(playerId);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.warning("Network error: " + cause.getMessage());
        }
    }
}
