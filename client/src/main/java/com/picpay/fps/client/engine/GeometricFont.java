package com.picpay.fps.client.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders text as 7-segment display style characters in 3D world space.
 * Each character is composed of thin colored quads (segments) built via SceneBuilder.
 * LCD/retro aesthetic — fits the low-poly game style.
 */
public class GeometricFont {
    private final SceneBuilder sceneBuilder;

    // Segment thickness relative to char height
    private static final float SEG_THICKNESS_RATIO = 0.08f;

    // 7-segment definitions: [top, topRight, bottomRight, bottom, bottomLeft, topLeft, middle]
    private static final Map<Character, boolean[]> CHAR_MAP = new HashMap<>();

    static {
        // Digits
        CHAR_MAP.put('0', new boolean[]{true,  true,  true,  true,  true,  true,  false});
        CHAR_MAP.put('1', new boolean[]{false, true,  true,  false, false, false, false});
        CHAR_MAP.put('2', new boolean[]{true,  true,  false, true,  true,  false, true});
        CHAR_MAP.put('3', new boolean[]{true,  true,  true,  true,  false, false, true});
        CHAR_MAP.put('4', new boolean[]{false, true,  true,  false, false, true,  true});
        CHAR_MAP.put('5', new boolean[]{true,  false, true,  true,  false, true,  true});
        CHAR_MAP.put('6', new boolean[]{true,  false, true,  true,  true,  true,  true});
        CHAR_MAP.put('7', new boolean[]{true,  true,  true,  false, false, false, false});
        CHAR_MAP.put('8', new boolean[]{true,  true,  true,  true,  true,  true,  true});
        CHAR_MAP.put('9', new boolean[]{true,  true,  true,  true,  false, true,  true});

        // Letters (7-segment approximations)
        CHAR_MAP.put('A', new boolean[]{true,  true,  true,  false, true,  true,  true});
        CHAR_MAP.put('B', new boolean[]{false, false, true,  true,  true,  true,  true});
        CHAR_MAP.put('C', new boolean[]{true,  false, false, true,  true,  true,  false});
        CHAR_MAP.put('D', new boolean[]{false, true,  true,  true,  true,  false, true});
        CHAR_MAP.put('E', new boolean[]{true,  false, false, true,  true,  true,  true});
        CHAR_MAP.put('F', new boolean[]{true,  false, false, false, true,  true,  true});
        CHAR_MAP.put('G', new boolean[]{true,  false, true,  true,  true,  true,  false});
        CHAR_MAP.put('H', new boolean[]{false, true,  true,  false, true,  true,  true});
        CHAR_MAP.put('I', new boolean[]{false, false, false, false, true,  true,  false});
        CHAR_MAP.put('J', new boolean[]{false, true,  true,  true,  false, false, false});
        CHAR_MAP.put('K', new boolean[]{false, false, false, false, true,  true,  true});
        CHAR_MAP.put('L', new boolean[]{false, false, false, true,  true,  true,  false});
        CHAR_MAP.put('M', new boolean[]{true,  true,  true,  false, true,  true,  false});
        CHAR_MAP.put('N', new boolean[]{false, false, true,  false, true,  false, true});
        CHAR_MAP.put('O', new boolean[]{true,  true,  true,  true,  true,  true,  false});
        CHAR_MAP.put('P', new boolean[]{true,  true,  false, false, true,  true,  true});
        CHAR_MAP.put('Q', new boolean[]{true,  true,  true,  false, false, true,  true});
        CHAR_MAP.put('R', new boolean[]{false, false, false, false, true,  false, true});
        CHAR_MAP.put('S', new boolean[]{true,  false, true,  true,  false, true,  true});
        CHAR_MAP.put('T', new boolean[]{false, false, false, true,  true,  true,  true});
        CHAR_MAP.put('U', new boolean[]{false, true,  true,  true,  true,  true,  false});
        CHAR_MAP.put('V', new boolean[]{false, true,  true,  true,  true,  true,  false});
        CHAR_MAP.put('W', new boolean[]{false, true,  true,  true,  true,  true,  false});
        CHAR_MAP.put('X', new boolean[]{false, true,  true,  false, true,  true,  true});
        CHAR_MAP.put('Y', new boolean[]{false, true,  true,  true,  false, true,  true});
        CHAR_MAP.put('Z', new boolean[]{true,  true,  false, true,  true,  false, true});

        // Punctuation
        CHAR_MAP.put(' ', new boolean[]{false, false, false, false, false, false, false});
        CHAR_MAP.put('-', new boolean[]{false, false, false, false, false, false, true});
        CHAR_MAP.put('_', new boolean[]{false, false, false, true,  false, false, false});
        CHAR_MAP.put('.', new boolean[]{false, false, false, false, false, false, false}); // dot handled specially
        CHAR_MAP.put(':', new boolean[]{false, false, false, false, false, false, false}); // colon handled specially
    }

    public GeometricFont(SceneBuilder sceneBuilder) {
        this.sceneBuilder = sceneBuilder;
    }

    /**
     * Render text in 3D world space on the XZ plane (horizontal, readable from above).
     * Text faces positive Y (upward).
     */
    public void renderTextXZ(String text, float x, float y, float z,
                             float charHeight, float r, float g, float b, boolean centered) {
        String upper = text.toUpperCase();
        float charWidth = charHeight * 0.6f;
        float spacing = charHeight * 0.15f;
        float totalWidth = upper.length() * (charWidth + spacing) - spacing;
        float startX = centered ? x - totalWidth / 2.0f : x;

        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            float cx = startX + i * (charWidth + spacing);
            renderCharXZ(ch, cx, y, z, charWidth, charHeight, r, g, b);
        }
    }

    /**
     * Render text in 3D world space on the XY plane (vertical, like a billboard).
     * Double-sided so it's visible from any camera angle.
     */
    public void renderText(String text, float x, float y, float z,
                           float charHeight, float r, float g, float b, boolean centered) {
        String upper = text.toUpperCase();
        float charWidth = charHeight * 0.6f;
        float spacing = charHeight * 0.15f;
        float totalWidth = upper.length() * (charWidth + spacing) - spacing;
        float startX = centered ? x - totalWidth / 2.0f : x;

        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            float cx = startX + i * (charWidth + spacing);
            renderCharXY(ch, cx, y, z, charWidth, charHeight, r, g, b);
        }
    }

    // ─── XY plane (vertical billboard) ───────────────────────────────────

    private void renderCharXY(char ch, float x, float y, float z,
                              float w, float h, float r, float g, float b) {
        boolean[] segs = CHAR_MAP.get(ch);
        if (segs == null) return;

        float t = SEG_THICKNESS_RATIO * h;
        float halfH = h / 2.0f;

        // Segments on XY plane at fixed Z
        if (segs[0]) addHSegXY(x, y + h - t, z, w, t, r, g, b);           // top
        if (segs[1]) addVSegXY(x + w - t, y + halfH, z, halfH, t, r, g, b); // top-right
        if (segs[2]) addVSegXY(x + w - t, y, z, halfH, t, r, g, b);        // bottom-right
        if (segs[3]) addHSegXY(x, y, z, w, t, r, g, b);                     // bottom
        if (segs[4]) addVSegXY(x, y, z, halfH, t, r, g, b);                // bottom-left
        if (segs[5]) addVSegXY(x, y + halfH, z, halfH, t, r, g, b);        // top-left
        if (segs[6]) addHSegXY(x, y + halfH - t / 2, z, w, t, r, g, b);   // middle

        if (ch == '.') addDotXY(x + w / 2, y + t, z, t * 1.5f, r, g, b);
        if (ch == ':') {
            addDotXY(x + w / 2, y + h * 0.3f, z, t * 1.5f, r, g, b);
            addDotXY(x + w / 2, y + h * 0.7f, z, t * 1.5f, r, g, b);
        }
    }

    private void addHSegXY(float x, float y, float z, float width, float thickness,
                           float r, float g, float b) {
        // Front face (visible from +Z)
        sceneBuilder.addColoredQuad(
            x, y + thickness, z,  x + width, y + thickness, z,
            x + width, y, z,  x, y, z,
            r, g, b
        );
        // Back face (visible from -Z)
        sceneBuilder.addColoredQuad(
            x, y, z,  x + width, y, z,
            x + width, y + thickness, z,  x, y + thickness, z,
            r, g, b
        );
    }

    private void addVSegXY(float x, float y, float z, float height, float thickness,
                           float r, float g, float b) {
        sceneBuilder.addColoredQuad(
            x, y + height, z,  x + thickness, y + height, z,
            x + thickness, y, z,  x, y, z,
            r, g, b
        );
        sceneBuilder.addColoredQuad(
            x, y, z,  x + thickness, y, z,
            x + thickness, y + height, z,  x, y + height, z,
            r, g, b
        );
    }

    private void addDotXY(float x, float y, float z, float size, float r, float g, float b) {
        float half = size / 2;
        sceneBuilder.addColoredQuad(
            x - half, y + half, z,  x + half, y + half, z,
            x + half, y - half, z,  x - half, y - half, z,
            r, g, b
        );
        sceneBuilder.addColoredQuad(
            x - half, y - half, z,  x + half, y - half, z,
            x + half, y + half, z,  x - half, y + half, z,
            r, g, b
        );
    }

    // ─── XZ plane (horizontal, readable from above) ─────────────────────

    private void renderCharXZ(char ch, float x, float y, float z,
                              float w, float h, float r, float g, float b) {
        boolean[] segs = CHAR_MAP.get(ch);
        if (segs == null) return;

        float t = SEG_THICKNESS_RATIO * h;
        float halfH = h / 2.0f;

        // Map char Y-axis to world Z-axis (negative Z = "up" in char)
        if (segs[0]) addHSegXZ(x, y, z - h + t, w, t, r, g, b);             // top
        if (segs[1]) addVSegXZ(x + w - t, y, z - h, halfH, t, r, g, b);     // top-right
        if (segs[2]) addVSegXZ(x + w - t, y, z - halfH, halfH, t, r, g, b); // bottom-right
        if (segs[3]) addHSegXZ(x, y, z, w, t, r, g, b);                      // bottom
        if (segs[4]) addVSegXZ(x, y, z - halfH, halfH, t, r, g, b);         // bottom-left
        if (segs[5]) addVSegXZ(x, y, z - h, halfH, t, r, g, b);             // top-left
        if (segs[6]) addHSegXZ(x, y, z - halfH + t / 2, w, t, r, g, b);    // middle
    }

    private void addHSegXZ(float x, float y, float z, float width, float thickness,
                           float r, float g, float b) {
        sceneBuilder.addColoredQuad(
            x, y, z,  x + width, y, z,
            x + width, y, z - thickness,  x, y, z - thickness,
            r, g, b
        );
    }

    private void addVSegXZ(float x, float y, float z, float height, float thickness,
                           float r, float g, float b) {
        sceneBuilder.addColoredQuad(
            x, y, z,  x + thickness, y, z,
            x + thickness, y, z - height,  x, y, z - height,
            r, g, b
        );
    }
}
