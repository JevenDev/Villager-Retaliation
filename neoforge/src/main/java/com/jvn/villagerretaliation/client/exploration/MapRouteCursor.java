package com.jvn.villagerretaliation.client.exploration;

import org.lwjgl.glfw.GLFW;

final class MapRouteCursor implements AutoCloseable {
    private long crosshair, hand, window;
    private int current;
    void set(long window, int shape) {
        this.window = window;
        if (shape == current) return;
        if (shape == GLFW.GLFW_CROSSHAIR_CURSOR && crosshair == 0) crosshair = GLFW.glfwCreateStandardCursor(shape);
        if (shape == GLFW.GLFW_HAND_CURSOR && hand == 0) hand = GLFW.glfwCreateStandardCursor(shape);
        GLFW.glfwSetCursor(window, shape == GLFW.GLFW_CROSSHAIR_CURSOR ? crosshair : shape == GLFW.GLFW_HAND_CURSOR ? hand : 0);
        current = shape;
    }
    @Override public void close() {
        if (window != 0) GLFW.glfwSetCursor(window, 0);
        if (crosshair != 0) GLFW.glfwDestroyCursor(crosshair);
        if (hand != 0) GLFW.glfwDestroyCursor(hand);
        crosshair = hand = window = 0; current = 0;
    }
}
