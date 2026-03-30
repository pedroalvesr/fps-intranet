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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ServerNetworkManager {
    private static final Logger LOG = Logger.getLogger(ServerNetworkManager.class.getName());

    private final GameLoop gameLoop;

    // Player mapping by session hash (replaces InetSocketAddress mapping for NAT safety)
    private final Map<Integer, ServerPlayer> sessionToPlayer = new ConcurrentHashMap<>();
    // Fallback: address → sessionHash (used only during connect before session is established)
    private final Map<InetSocketAddress, Integer> addressToSession = new ConcurrentHashMap<>();

    // Rate limiting: connection attempts per IP
    private final Map<String, long[]> connectAttempts = new ConcurrentHashMap<>();

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
            if (player.getAddress() != null && !player.isDisconnected()) {
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

            // Minimum packet size check
            if (data.length < Packet.HEADER_SIZE) return;

            // Protocol version check
            byte version = Packet.peekVersion(data);
            if (version != Packet.PROTOCOL_VERSION) return;

            PacketType type = Packet.peekType(data);
            if (type == null) return;

            // Connect is the only packet that doesn't require a valid session
            if (type == PacketType.CONNECT) {
                handleConnect(sender, data);
                return;
            }

            // All other packets: validate session hash
            int sessionHash = Packet.peekSessionHash(data);
            ServerPlayer player = sessionToPlayer.get(sessionHash);
            if (player == null) return; // unknown session — drop

            // Update player address (NAT rebinding support)
            if (!sender.equals(player.getAddress())) {
                player.setAddress(sender);
                addressToSession.put(sender, sessionHash);
            }

            switch (type) {
                case PLAYER_INPUT -> handleInput(player, data);
                case PLAYER_READY -> handleReady(player, data);
                case DISCONNECT -> handleDisconnect(player);
                case HEARTBEAT -> handleHeartbeat(player, sender, data);
                default -> {}
            }
        }

        private void handleConnect(InetSocketAddress sender, byte[] data) {
            // Rate limiting: max N connect attempts per minute per IP
            String ip = sender.getAddress().getHostAddress();
            if (isRateLimited(ip)) {
                LOG.warning("Rate limited connection attempt from " + ip);
                return;
            }
            recordConnectAttempt(ip);

            ConnectPacket pkt = ConnectPacket.deserialize(data);

            // Protocol version mismatch
            if (pkt.getProtocolVersion() != Packet.PROTOCOL_VERSION) {
                LOG.warning("Protocol mismatch from " + sender + ": v" + pkt.getProtocolVersion());
                return;
            }

            // Check if this address already has a session (duplicate connect)
            Integer existingSession = addressToSession.get(sender);
            if (existingSession != null) {
                ServerPlayer existing = sessionToPlayer.get(existingSession);
                if (existing != null && !existing.isDisconnected()) {
                    // Already connected — resend the ack
                    ConnectAckPacket ack = new ConnectAckPacket(existing.getId(), existing.getTeam(), existing.getSessionToken());
                    sendToAddress(sender, ack.serialize());
                    return;
                }
            }

            // Check for reconnect: look for a disconnected player with same name
            ServerPlayer reconnecting = findReconnectCandidate(pkt.getPlayerName());
            if (reconnecting != null) {
                reconnecting.reconnect(sender);
                sessionToPlayer.put(reconnecting.getSessionHash(), reconnecting);
                addressToSession.put(sender, reconnecting.getSessionHash());

                ConnectAckPacket ack = new ConnectAckPacket(reconnecting.getId(), reconnecting.getTeam(), reconnecting.getSessionToken());
                sendToAddress(sender, ack.serialize());
                LOG.info("Player reconnected: " + reconnecting.getName() + " (id=" + reconnecting.getId() + ")");
                return;
            }

            // New connection
            if (gameLoop.getPlayers().size() >= GameConfig.MAX_PLAYERS) return;

            ServerPlayer player = gameLoop.addPlayer(pkt.getPlayerName(), sender);
            sessionToPlayer.put(player.getSessionHash(), player);
            addressToSession.put(sender, player.getSessionHash());

            ConnectAckPacket ack = new ConnectAckPacket(player.getId(), player.getTeam(), player.getSessionToken());
            sendToAddress(sender, ack.serialize());

            // Only send spawn if game is already playing (late join)
            if (gameLoop.getPhase() == GameLoop.Phase.PLAYING) {
                SpawnPacket spawnPkt = new SpawnPacket(player.getId(),
                    player.getPosition().x, player.getPosition().y, player.getPosition().z);
                broadcastToAll(spawnPkt.serialize());
            }
        }

        private void handleInput(ServerPlayer player, byte[] data) {
            if (player.isDisconnected()) return;

            PlayerInputPacket input = PlayerInputPacket.deserialize(data);

            // Validate floats
            if (Float.isNaN(input.getYaw()) || Float.isInfinite(input.getYaw()) ||
                Float.isNaN(input.getPitch()) || Float.isInfinite(input.getPitch())) {
                LOG.warning("Invalid input floats from player " + player.getId());
                return;
            }

            // Validate pitch range (-PI/2 to PI/2)
            if (input.getPitch() < -Math.PI / 2 - 0.01f || input.getPitch() > Math.PI / 2 + 0.01f) {
                return;
            }

            gameLoop.processInput(player.getId(), input);
        }

        private void handleReady(ServerPlayer player, byte[] data) {
            if (player.isDisconnected()) return;
            PlayerReadyPacket pkt = PlayerReadyPacket.deserialize(data);
            gameLoop.setPlayerReady(player.getId(), pkt.isReady());
        }

        private void handleDisconnect(ServerPlayer player) {
            LOG.info("Player disconnecting gracefully: " + player.getName());
            // Soft-disconnect: keep player in grace period for reconnect
            player.setDisconnected(true);
            player.setDisconnectTimer(0);
            addressToSession.remove(player.getAddress());
        }

        private void handleHeartbeat(ServerPlayer player, InetSocketAddress sender, byte[] data) {
            player.updateHeartbeat();
            HeartbeatPacket hb = HeartbeatPacket.deserialize(data);

            HeartbeatAckPacket ack = new HeartbeatAckPacket(hb.getClientTimestamp(), gameLoop.getTick());
            ack.setSessionHash(player.getSessionHash());
            sendToAddress(sender, ack.serialize());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.warning("Network error: " + cause.getMessage());
        }
    }

    /** Called by GameLoop to remove timed-out players fully. */
    public void removePlayerSession(ServerPlayer player) {
        sessionToPlayer.remove(player.getSessionHash());
        if (player.getAddress() != null) {
            addressToSession.remove(player.getAddress());
        }
    }

    private ServerPlayer findReconnectCandidate(String name) {
        for (ServerPlayer p : gameLoop.getPlayers().values()) {
            if (p.isDisconnected() && p.getName().equals(name)
                && p.getDisconnectTimer() < GameConfig.RECONNECT_GRACE_PERIOD) {
                return p;
            }
        }
        return null;
    }

    // --- Rate limiting ---

    private boolean isRateLimited(String ip) {
        long[] attempts = connectAttempts.get(ip);
        if (attempts == null) return false;
        long now = System.currentTimeMillis();
        int count = 0;
        for (long ts : attempts) {
            if (now - ts < 60_000) count++;
        }
        return count >= GameConfig.MAX_CONNECT_ATTEMPTS_PER_MINUTE;
    }

    private void recordConnectAttempt(String ip) {
        long now = System.currentTimeMillis();
        connectAttempts.compute(ip, (k, existing) -> {
            if (existing == null) return new long[]{ now };
            // Keep only last N entries, compact old ones
            long[] filtered = new long[Math.min(existing.length + 1, GameConfig.MAX_CONNECT_ATTEMPTS_PER_MINUTE + 2)];
            int idx = 0;
            for (long ts : existing) {
                if (now - ts < 60_000 && idx < filtered.length - 1) {
                    filtered[idx++] = ts;
                }
            }
            filtered[idx] = now;
            // Trim to actual size
            long[] result = new long[idx + 1];
            System.arraycopy(filtered, 0, result, 0, idx + 1);
            return result;
        });
    }
}
