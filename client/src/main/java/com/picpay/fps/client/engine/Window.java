package com.picpay.fps.client.engine;

import com.picpay.fps.shared.constants.GameConfig;
import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * GLFW window wrapper with input state tracking.
 */
public class Window {
    private long handle;
    private int width, height;
    private boolean resized;

    // Input state
    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private double mouseX, mouseY;
    private double mouseDeltaX, mouseDeltaY;
    private boolean mouseGrabbed = true;
    private boolean leftMousePressed;

    public Window() {
        this.width = GameConfig.WINDOW_WIDTH;
        this.height = GameConfig.WINDOW_HEIGHT;
    }

    public void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new RuntimeException("Failed to init GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API); // Vulkan — no OpenGL context
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        handle = glfwCreateWindow(width, height, "FPS Intranet - PicPay Team Building", NULL, NULL);
        if (handle == NULL) {
            throw new RuntimeException("Failed to create window");
        }

        // Key callback
        glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
            if (key >= 0 && key <= GLFW_KEY_LAST) {
                keys[key] = (action != GLFW_RELEASE);
            }
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                mouseGrabbed = !mouseGrabbed;
                glfwSetInputMode(handle, GLFW_CURSOR,
                    mouseGrabbed ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
            }
        });

        // Mouse position callback
        glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
            mouseDeltaX = xpos - mouseX;
            mouseDeltaY = ypos - mouseY;
            mouseX = xpos;
            mouseY = ypos;
        });

        // Mouse button callback
        glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                leftMousePressed = (action == GLFW_PRESS);
            }
        });

        // Framebuffer resize callback
        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> {
            width = w;
            height = h;
            resized = true;
        });

        // Grab mouse
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Center window
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            glfwSetWindowPos(handle, (vidMode.width() - width) / 2, (vidMode.height() - height) / 2);
        }
    }

    public void pollEvents() {
        mouseDeltaX = 0;
        mouseDeltaY = 0;
        glfwPollEvents();
    }

    public boolean shouldClose() { return glfwWindowShouldClose(handle); }
    public long getHandle() { return handle; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public boolean isResized() {
        boolean r = resized;
        resized = false;
        return r;
    }

    public boolean isKeyDown(int key) { return keys[key]; }
    public double getMouseDeltaX() { return mouseDeltaX; }
    public double getMouseDeltaY() { return mouseDeltaY; }
    public boolean isMouseGrabbed() { return mouseGrabbed; }
    public boolean isLeftMousePressed() { return leftMousePressed; }

    public void cleanup() {
        glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
