package com.picpay.fps.client.engine;

import com.picpay.fps.shared.constants.GameConfig;
import com.picpay.fps.shared.math.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the vertex data for the game world (map geometry + player models).
 * All geometry is simple colored triangles — low-poly style.
 */
public class SceneBuilder {
    private final List<float[]> triangles = new ArrayList<>();

    public void clear() {
        triangles.clear();
    }

    /**
     * Add the arena map geometry.
     */
    public void addMap() {
        float S = GameConfig.MAP_SIZE;

        // Floor — dark gray grid
        addQuad(-S, 0, -S, S, 0, -S, S, 0, S, -S, 0, S,
            0.15f, 0.15f, 0.18f);

        // Outer walls
        float H = GameConfig.WALL_HEIGHT;
        float T = 0.5f;

        // North wall
        addWallBox(-S, 0, -S, S, H, -S + T, 0.3f, 0.3f, 0.35f);
        // South wall
        addWallBox(-S, 0, S - T, S, H, S, 0.3f, 0.3f, 0.35f);
        // West wall
        addWallBox(-S, 0, -S, -S + T, H, S, 0.3f, 0.3f, 0.35f);
        // East wall
        addWallBox(S - T, 0, -S, S, H, S, 0.3f, 0.3f, 0.35f);

        // Cover walls (same as server GameMap)
        addWallBox(-1, 0, -12, 1, H, -4, 0.4f, 0.35f, 0.3f);
        addWallBox(-1, 0, 4, 1, H, 12, 0.4f, 0.35f, 0.3f);
        addWallBox(-8, 0, -1, -2, H, 1, 0.4f, 0.35f, 0.3f);
        addWallBox(2, 0, -1, 8, H, 1, 0.4f, 0.35f, 0.3f);

        // Corner boxes
        float ch = H * 0.6f;
        addWallBox(-16, 0, -16, -12, ch, -12, 0.35f, 0.4f, 0.35f);
        addWallBox(12, 0, -16, 16, ch, -12, 0.35f, 0.4f, 0.35f);
        addWallBox(-16, 0, 12, -12, ch, 16, 0.35f, 0.4f, 0.35f);
        addWallBox(12, 0, 12, 16, ch, 16, 0.35f, 0.4f, 0.35f);

        // Side barriers
        float sh = H * 0.5f;
        addWallBox(-20, 0, -6, -18, sh, 6, 0.35f, 0.35f, 0.4f);
        addWallBox(18, 0, -6, 20, sh, 6, 0.35f, 0.35f, 0.4f);
    }

    /**
     * Add a box-shaped wall with colored faces.
     */
    public void addWallBox(float x1, float y1, float z1, float x2, float y2, float z2,
                           float r, float g, float b) {
        float dr = r * 0.8f, dg = g * 0.8f, db = b * 0.8f; // darker for sides

        // Top face
        addQuad(x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b);

        // Front face (positive Z)
        addQuad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, dr, dg, db);

        // Back face (negative Z)
        addQuad(x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, dr, dg, db);

        // Left face (negative X)
        addQuad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r * 0.7f, g * 0.7f, b * 0.7f);

        // Right face (positive X)
        addQuad(x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, r * 0.7f, g * 0.7f, b * 0.7f);
    }

    /**
     * Add a player model (simple capsule/box shape).
     */
    public void addPlayer(float px, float py, float pz, float yaw, byte team, boolean isAlive) {
        if (!isAlive) return;

        float r, g, b;
        if (team == 0) { r = 0.9f; g = 0.2f; b = 0.2f; } // Red team
        else { r = 0.2f; g = 0.3f; b = 0.9f; } // Blue team

        // Body (box)
        float hw = 0.3f, hd = 0.2f;
        addWallBox(px - hw, py, pz - hd, px + hw, py + 1.4f, pz + hd, r, g, b);

        // Head (smaller box)
        float hh = 0.18f;
        addWallBox(px - hh, py + 1.4f, pz - hh, px + hh, py + 1.8f, pz + hh,
            r * 1.2f, g * 1.2f, b * 1.2f);
    }

    /**
     * Add a crosshair overlay (rendered in world space at a fixed distance from camera).
     */
    public void addCrosshair(float cx, float cy, float cz, float yaw, float pitch) {
        // Crosshair is drawn as 2 thin quads in front of the camera
        float dist = 0.5f;
        float cosP = (float) Math.cos(pitch);
        float fx = cx + (float) Math.sin(yaw) * cosP * dist;
        float fy = cy - (float) Math.sin(pitch) * dist;
        float fz = cz - (float) Math.cos(yaw) * cosP * dist;

        float s = 0.005f; // crosshair size
        // Horizontal line
        triangles.add(new float[]{
            fx - s * 3, fy, fz, 0.0f, 1.0f, 0.0f,
            fx + s * 3, fy, fz, 0.0f, 1.0f, 0.0f,
            fx, fy + s * 0.5f, fz, 0.0f, 1.0f, 0.0f,
        });
        triangles.add(new float[]{
            fx - s * 3, fy, fz, 0.0f, 1.0f, 0.0f,
            fx + s * 3, fy, fz, 0.0f, 1.0f, 0.0f,
            fx, fy - s * 0.5f, fz, 0.0f, 1.0f, 0.0f,
        });
        // Vertical line
        triangles.add(new float[]{
            fx, fy - s * 3, fz, 0.0f, 1.0f, 0.0f,
            fx, fy + s * 3, fz, 0.0f, 1.0f, 0.0f,
            fx + s * 0.5f, fy, fz, 0.0f, 1.0f, 0.0f,
        });
        triangles.add(new float[]{
            fx, fy - s * 3, fz, 0.0f, 1.0f, 0.0f,
            fx, fy + s * 3, fz, 0.0f, 1.0f, 0.0f,
            fx - s * 0.5f, fy, fz, 0.0f, 1.0f, 0.0f,
        });
    }

    private void addQuad(float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float x4, float y4, float z4,
                         float r, float g, float b) {
        // Triangle 1
        triangles.add(new float[]{
            x1, y1, z1, r, g, b,
            x2, y2, z2, r, g, b,
            x3, y3, z3, r, g, b
        });
        // Triangle 2
        triangles.add(new float[]{
            x1, y1, z1, r, g, b,
            x3, y3, z3, r, g, b,
            x4, y4, z4, r, g, b
        });
    }

    /**
     * Build final vertex array for rendering.
     */
    public float[] build() {
        float[] result = new float[triangles.size() * 18]; // 3 verts * 6 floats
        int idx = 0;
        for (float[] tri : triangles) {
            System.arraycopy(tri, 0, result, idx, tri.length);
            idx += tri.length;
        }
        return result;
    }
}
