package com.picpay.fps.server.physics;

import com.picpay.fps.server.game.GameMap;
import com.picpay.fps.server.game.ServerPlayer;
import com.picpay.fps.shared.math.AABB;
import org.joml.Vector3f;

import java.util.Collection;

/**
 * Server-side physics: movement, hitscan raycasting.
 */
public class PhysicsEngine {
    private final GameMap map;

    public PhysicsEngine(GameMap map) {
        this.map = map;
    }

    /**
     * Process player movement from input.
     */
    public void processMovement(ServerPlayer player, boolean forward, boolean back,
                                 boolean left, boolean right, boolean sprint,
                                 float yaw, float pitch, float dt) {
        if (!player.isAlive()) return;

        player.setYaw(yaw);
        player.setPitch(pitch);

        float speed = sprint ? 7.5f : 5.0f;
        float dx = 0, dz = 0;

        float sinYaw = (float) Math.sin(yaw);
        float cosYaw = (float) Math.cos(yaw);

        if (forward) { dx += sinYaw; dz -= cosYaw; }
        if (back)    { dx -= sinYaw; dz += cosYaw; }
        if (left)    { dx -= cosYaw; dz -= sinYaw; }
        if (right)   { dx += cosYaw; dz += sinYaw; }

        // Normalize diagonal movement
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            dx = (dx / len) * speed * dt;
            dz = (dz / len) * speed * dt;
        }

        float newX = player.getPosition().x + dx;
        float newZ = player.getPosition().z + dz;

        map.resolveCollision(player, newX, 0, newZ);
    }

    /**
     * Hitscan raycast: find the closest player hit.
     * Returns the hit player or null.
     */
    public HitResult hitscan(ServerPlayer shooter, Collection<ServerPlayer> allPlayers) {
        Vector3f origin = shooter.getEyePosition();
        Vector3f dir = shooter.getLookDirection();

        float closestDist = Float.MAX_VALUE;
        ServerPlayer closestHit = null;

        for (ServerPlayer target : allPlayers) {
            if (target.getId() == shooter.getId()) continue;
            if (!target.isAlive()) continue;
            if (target.getTeam() == shooter.getTeam()) continue; // no friendly fire

            AABB hitbox = target.getBoundingBox();
            float dist = hitbox.rayIntersect(origin.x, origin.y, origin.z, dir.x, dir.y, dir.z);
            if (dist > 0 && dist < closestDist) {
                // Check if wall blocks the shot
                if (!isBlockedByWall(origin, dir, dist)) {
                    closestDist = dist;
                    closestHit = target;
                }
            }
        }

        return closestHit != null ? new HitResult(closestHit, closestDist) : null;
    }

    private boolean isBlockedByWall(Vector3f origin, Vector3f dir, float maxDist) {
        for (AABB wall : map.getWalls()) {
            float dist = wall.rayIntersect(origin.x, origin.y, origin.z, dir.x, dir.y, dir.z);
            if (dist > 0 && dist < maxDist) {
                return true;
            }
        }
        return false;
    }

    public record HitResult(ServerPlayer target, float distance) {}
}
