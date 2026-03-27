package com.picpay.fps.client.engine;

import com.picpay.fps.shared.constants.GameConfig;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * First-person camera with mouse look.
 */
public class Camera {
    private final Vector3f position = new Vector3f(0, 1.6f, 0);
    private float yaw = 0;    // horizontal rotation (radians)
    private float pitch = 0;  // vertical rotation (radians)

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projMatrix = new Matrix4f();
    private final Matrix4f vpMatrix = new Matrix4f();

    public void updateProjection(int width, int height) {
        float aspect = (float) width / height;
        float fovRad = (float) Math.toRadians(GameConfig.FOV);
        // Vulkan uses top-to-bottom Y, so negate the [1][1] element
        projMatrix.identity()
            .perspective(fovRad, aspect, GameConfig.NEAR_PLANE, GameConfig.FAR_PLANE, true);
        // Flip Y for Vulkan coordinate system
        projMatrix.m11(projMatrix.m11() * -1);
    }

    public void rotate(double deltaX, double deltaY) {
        yaw += (float) (deltaX * GameConfig.MOUSE_SENSITIVITY);
        pitch += (float) (deltaY * GameConfig.MOUSE_SENSITIVITY);

        // Clamp pitch
        float maxPitch = (float) Math.toRadians(89);
        if (pitch > maxPitch) pitch = maxPitch;
        if (pitch < -maxPitch) pitch = -maxPitch;
    }

    public Matrix4f getViewProjectionMatrix() {
        // Look direction
        float cosP = (float) Math.cos(pitch);
        float lookX = (float) Math.sin(yaw) * cosP;
        float lookY = (float) -Math.sin(pitch);
        float lookZ = (float) -Math.cos(yaw) * cosP;

        viewMatrix.identity().lookAt(
            position.x, position.y, position.z,
            position.x + lookX, position.y + lookY, position.z + lookZ,
            0, 1, 0
        );

        vpMatrix.set(projMatrix).mul(viewMatrix);
        return vpMatrix;
    }

    public Vector3f getPosition() { return position; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }
}
