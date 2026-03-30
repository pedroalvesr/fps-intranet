package com.picpay.fps.client.engine;

import com.picpay.fps.shared.protocol.LobbyStatePacket;

/**
 * Renders lobby screen: overhead view of the map with players standing in spawn,
 * ready indicators, player names, and countdown display using geometric shapes.
 */
public class LobbyRenderer {
    private final SceneBuilder sceneBuilder;
    private final GeometricFont font;

    public LobbyRenderer(SceneBuilder sceneBuilder, GeometricFont font) {
        this.sceneBuilder = sceneBuilder;
        this.font = font;
    }

    /**
     * Build the lobby scene.
     */
    public void render(LobbyStatePacket.LobbyPlayer[] players, byte phase, byte countdown,
                       int localPlayerId, boolean localReady) {
        sceneBuilder.clear();

        // Add map (same arena)
        sceneBuilder.addMap();

        // Render players standing in a line at center of map
        float startX = -(players.length - 1) * 2.0f / 2.0f;
        for (int i = 0; i < players.length; i++) {
            LobbyStatePacket.LobbyPlayer p = players[i];
            float px = startX + i * 2.0f;
            float py = 0;
            float pz = 0;

            // Player model
            sceneBuilder.addPlayer(px, py, pz, 0, p.team(), true);

            // Ready indicator: green diamond above head if ready, red if not
            float indicatorY = py + 2.2f;
            if (p.ready()) {
                addDiamond(px, indicatorY, pz, 0.2f, 0.0f, 1.0f, 0.0f); // green
            } else {
                addDiamond(px, indicatorY, pz, 0.2f, 1.0f, 0.2f, 0.2f); // red
            }

            // Player name above ready indicator
            String name = p.name();
            if (name != null && !name.isEmpty()) {
                float nameY = indicatorY + 0.6f;
                font.renderText(name, px, nameY, pz - 0.1f, 0.3f, 1.0f, 1.0f, 1.0f, true);
            }

            // Highlight local player with a ring on the ground
            if (p.id() == localPlayerId) {
                addGroundRing(px, 0.01f, pz, 0.6f, 1.0f, 1.0f, 0.0f); // yellow ring
            }
        }

        // Countdown indicator: big floating cubes showing the number
        if (phase == 1 && countdown > 0) {
            addCountdownDisplay(0, 6.0f, -8.0f, countdown);
        }

        // "Press R when ready" text indicator
        if (phase == 0) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0));
            if (localReady) {
                font.renderText("READY", 0, 5.0f, -8.0f, 0.8f,
                    0.0f, 0.8f * pulse + 0.2f, 0.0f, true);
            } else {
                font.renderText("PRESS R", 0, 5.0f, -8.0f, 0.8f,
                    0.8f * pulse + 0.2f, 0.8f * pulse + 0.2f, 0.0f, true);
            }
        }
    }

    private void addDiamond(float cx, float cy, float cz, float size, float r, float g, float b) {
        // Diamond = 2 pyramids (top + bottom), approximated with 8 triangles
        float s = size;
        float h = size * 1.5f;

        // Top half (4 triangles)
        sceneBuilder.addTriangle(cx, cy + h, cz, cx + s, cy, cz, cx, cy, cz + s, r, g, b);
        sceneBuilder.addTriangle(cx, cy + h, cz, cx, cy, cz + s, cx - s, cy, cz, r * 0.8f, g * 0.8f, b * 0.8f);
        sceneBuilder.addTriangle(cx, cy + h, cz, cx - s, cy, cz, cx, cy, cz - s, r * 0.7f, g * 0.7f, b * 0.7f);
        sceneBuilder.addTriangle(cx, cy + h, cz, cx, cy, cz - s, cx + s, cy, cz, r * 0.9f, g * 0.9f, b * 0.9f);

        // Bottom half (4 triangles)
        sceneBuilder.addTriangle(cx, cy - h, cz, cx, cy, cz + s, cx + s, cy, cz, r * 0.6f, g * 0.6f, b * 0.6f);
        sceneBuilder.addTriangle(cx, cy - h, cz, cx - s, cy, cz, cx, cy, cz + s, r * 0.5f, g * 0.5f, b * 0.5f);
        sceneBuilder.addTriangle(cx, cy - h, cz, cx, cy, cz - s, cx - s, cy, cz, r * 0.5f, g * 0.5f, b * 0.5f);
        sceneBuilder.addTriangle(cx, cy - h, cz, cx + s, cy, cz, cx, cy, cz - s, r * 0.6f, g * 0.6f, b * 0.6f);
    }

    private void addGroundRing(float cx, float cy, float cz, float radius, float r, float g, float b) {
        // Ring approximated with small quads around a circle
        int segments = 16;
        float thickness = 0.08f;
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (2 * Math.PI * i / segments);
            float a2 = (float) (2 * Math.PI * (i + 1) / segments);
            float innerR = radius - thickness;

            float x1o = cx + (float) Math.cos(a1) * radius;
            float z1o = cz + (float) Math.sin(a1) * radius;
            float x2o = cx + (float) Math.cos(a2) * radius;
            float z2o = cz + (float) Math.sin(a2) * radius;
            float x1i = cx + (float) Math.cos(a1) * innerR;
            float z1i = cz + (float) Math.sin(a1) * innerR;
            float x2i = cx + (float) Math.cos(a2) * innerR;
            float z2i = cz + (float) Math.sin(a2) * innerR;

            sceneBuilder.addTriangle(x1o, cy, z1o, x2o, cy, z2o, x2i, cy, z2i, r, g, b);
            sceneBuilder.addTriangle(x1o, cy, z1o, x2i, cy, z2i, x1i, cy, z1i, r, g, b);
        }
    }

    private void addCountdownDisplay(float cx, float cy, float cz, int number) {
        // Show countdown number as 7-segment text
        font.renderText(String.valueOf(number), cx, cy, cz, 2.0f, 1.0f, 0.9f, 0.1f, true);
    }
}
