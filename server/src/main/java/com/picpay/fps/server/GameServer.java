package com.picpay.fps.server;

import com.picpay.fps.server.game.GameLoop;
import com.picpay.fps.server.network.ServerNetworkManager;
import com.picpay.fps.shared.constants.GameConfig;

import java.util.logging.Logger;

/**
 * FPS Intranet Server — entry point.
 * Starts the authoritative game loop and UDP network listener.
 */
public class GameServer {
    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

    public static void main(String[] args) throws Exception {
        LOG.info("=== FPS Intranet Server ===");
        LOG.info("Max players: " + GameConfig.MAX_PLAYERS);
        LOG.info("Tick rate: " + GameConfig.TICK_RATE + " Hz");
        LOG.info("Port: " + GameConfig.SERVER_PORT);

        GameLoop gameLoop = new GameLoop();
        ServerNetworkManager network = new ServerNetworkManager(gameLoop);

        // Start network (binds UDP port)
        network.start();

        // Start game loop in dedicated thread
        Thread gameThread = new Thread(gameLoop, "GameLoop");
        gameThread.setDaemon(false);
        gameThread.start();

        LOG.info("Server running. Press Ctrl+C to stop.");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down...");
            gameLoop.stop();
            network.stop();
        }));

        gameThread.join();
    }
}
