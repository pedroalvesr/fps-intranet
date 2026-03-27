package com.picpay.fps.server.game;

import com.picpay.fps.shared.constants.GameConfig;
import com.picpay.fps.shared.math.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side map representation. Box arena with some cover walls.
 */
public class GameMap {
    private final List<AABB> walls = new ArrayList<>();
    private final float[][] redSpawns;
    private final float[][] blueSpawns;
    private int redSpawnIndex = 0;
    private int blueSpawnIndex = 0;

    public GameMap() {
        float S = GameConfig.MAP_SIZE;
        float H = GameConfig.WALL_HEIGHT;
        float T = 0.5f; // wall thickness

        // Floor (for reference, rendered client-side)
        // walls.add(new AABB(-S, -T, -S, S, 0, S)); // not used for player collision

        // Outer walls
        walls.add(new AABB(-S, 0, -S, S, H, -S + T));   // North
        walls.add(new AABB(-S, 0, S - T, S, H, S));      // South
        walls.add(new AABB(-S, 0, -S, -S + T, H, S));    // West
        walls.add(new AABB(S - T, 0, -S, S, H, S));      // East

        // Cover walls in the middle (creates lanes like a CS map)
        // Center cross
        walls.add(new AABB(-1, 0, -12, 1, H, -4));       // Center vertical top
        walls.add(new AABB(-1, 0, 4, 1, H, 12));         // Center vertical bottom
        walls.add(new AABB(-8, 0, -1, -2, H, 1));        // Center horizontal left
        walls.add(new AABB(2, 0, -1, 8, H, 1));          // Center horizontal right

        // Corner boxes (cover)
        walls.add(new AABB(-16, 0, -16, -12, H * 0.6f, -12));
        walls.add(new AABB(12, 0, -16, 16, H * 0.6f, -12));
        walls.add(new AABB(-16, 0, 12, -12, H * 0.6f, 16));
        walls.add(new AABB(12, 0, 12, 16, H * 0.6f, 16));

        // Side barriers
        walls.add(new AABB(-20, 0, -6, -18, H * 0.5f, 6));
        walls.add(new AABB(18, 0, -6, 20, H * 0.5f, 6));

        // Red team spawns (negative Z side)
        redSpawns = new float[][] {
            {-30, 0, -30}, {-28, 0, -30}, {-26, 0, -30}, {-24, 0, -30}, {-22, 0, -30},
            {-30, 0, -28}, {-28, 0, -28}, {-26, 0, -28}, {-24, 0, -28}, {-22, 0, -28}
        };

        // Blue team spawns (positive Z side)
        blueSpawns = new float[][] {
            {30, 0, 30}, {28, 0, 30}, {26, 0, 30}, {24, 0, 30}, {22, 0, 30},
            {30, 0, 28}, {28, 0, 28}, {26, 0, 28}, {24, 0, 28}, {22, 0, 28}
        };
    }

    public List<AABB> getWalls() { return walls; }

    public float[] getSpawnPoint(byte team) {
        if (team == 0) {
            float[] spawn = redSpawns[redSpawnIndex % redSpawns.length];
            redSpawnIndex++;
            return spawn;
        } else {
            float[] spawn = blueSpawns[blueSpawnIndex % blueSpawns.length];
            blueSpawnIndex++;
            return spawn;
        }
    }

    /**
     * Resolve player movement against walls. Returns corrected position.
     */
    public void resolveCollision(ServerPlayer player, float newX, float newY, float newZ) {
        float r = GameConfig.PLAYER_RADIUS;
        float h = GameConfig.PLAYER_HEIGHT;

        // Try X movement
        AABB testBox = AABB.fromCenter(newX, newY + h / 2, player.getPosition().z, r, h / 2, r);
        boolean blockedX = false;
        for (AABB wall : walls) {
            if (testBox.intersects(wall)) {
                blockedX = true;
                break;
            }
        }

        // Try Z movement
        float px = blockedX ? player.getPosition().x : newX;
        testBox = AABB.fromCenter(px, newY + h / 2, newZ, r, h / 2, r);
        boolean blockedZ = false;
        for (AABB wall : walls) {
            if (testBox.intersects(wall)) {
                blockedZ = true;
                break;
            }
        }

        player.getPosition().x = blockedX ? player.getPosition().x : newX;
        player.getPosition().y = newY; // simplified: flat ground
        player.getPosition().z = blockedZ ? player.getPosition().z : newZ;
    }
}
