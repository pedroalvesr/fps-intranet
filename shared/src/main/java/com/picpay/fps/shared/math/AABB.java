package com.picpay.fps.shared.math;

/**
 * Axis-Aligned Bounding Box for collision detection.
 */
public class AABB {
    public float minX, minY, minZ;
    public float maxX, maxY, maxZ;

    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public static AABB fromCenter(float cx, float cy, float cz, float halfW, float halfH, float halfD) {
        return new AABB(cx - halfW, cy - halfH, cz - halfD, cx + halfW, cy + halfH, cz + halfD);
    }

    public boolean intersects(AABB other) {
        return this.maxX > other.minX && this.minX < other.maxX
            && this.maxY > other.minY && this.minY < other.maxY
            && this.maxZ > other.minZ && this.minZ < other.maxZ;
    }

    /**
     * Ray-AABB intersection test (slab method).
     * Returns distance to hit or -1 if no hit.
     */
    public float rayIntersect(float ox, float oy, float oz, float dx, float dy, float dz) {
        float tMin = Float.NEGATIVE_INFINITY;
        float tMax = Float.POSITIVE_INFINITY;

        if (dx != 0) {
            float t1 = (minX - ox) / dx;
            float t2 = (maxX - ox) / dx;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        } else if (ox < minX || ox > maxX) {
            return -1;
        }

        if (dy != 0) {
            float t1 = (minY - oy) / dy;
            float t2 = (maxY - oy) / dy;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        } else if (oy < minY || oy > maxY) {
            return -1;
        }

        if (dz != 0) {
            float t1 = (minZ - oz) / dz;
            float t2 = (maxZ - oz) / dz;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        } else if (oz < minZ || oz > maxZ) {
            return -1;
        }

        if (tMax >= tMin && tMax > 0) {
            return tMin > 0 ? tMin : tMax;
        }
        return -1;
    }

    public AABB move(float dx, float dy, float dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public float centerX() { return (minX + maxX) / 2; }
    public float centerY() { return (minY + maxY) / 2; }
    public float centerZ() { return (minZ + maxZ) / 2; }
}
