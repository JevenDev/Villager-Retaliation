package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

final class SettlementViewNavigation {
    private static final double MOVE_RESPONSE = 12.0D;
    private static final double ZOOM_RESPONSE = 14.0D;
    private final Set<Integer> heldKeys = new HashSet<>();
    private final SettlementViewCamera camera = new SettlementViewCamera();
    private double x;
    private double z;
    private float height;
    private double cameraHeight;
    private double velocityX;
    private double velocityZ;

    SettlementViewNavigation(SettlementViewWindow window, float height, float yaw, int renderDistance) {
        this.x = window.x();
        this.z = window.z();
        this.height = Math.clamp(height, SettlementViewWindow.MIN_HEIGHT, maximumHeight(renderDistance));
        this.cameraHeight = this.height;
        rotate(yaw - this.camera.yaw());
    }

    double x() { return this.x; }
    double z() { return this.z; }
    float height() { return this.height; }
    double cameraHeight() { return this.cameraHeight; }
    float yaw() { return this.camera.yaw(); }

    void accept(SettlementViewWindow window) {
        this.x = window.x();
        this.z = window.z();
        this.height = window.height();
        snapHeight();
    }

    void restore(SettlementViewWindow window, SettlementViewPreferences.Bookmark bookmark, int renderDistance) {
        clear();
        pan(window, bookmark.x() - this.x, bookmark.z() - this.z);
        this.height = Math.clamp(bookmark.height(), SettlementViewWindow.MIN_HEIGHT, maximumHeight(renderDistance));
        this.cameraHeight = this.height;
        rotate(bookmark.yaw() - yaw());
    }

    void rotate(double degrees) {
        this.camera.rotate(degrees / 90.0D, 1.0D);
    }

    boolean held(int key) { return this.heldKeys.contains(key); }
    void press(int key) { this.heldKeys.add(key); }
    void release(int key) { this.heldKeys.remove(key); }

    static boolean handles(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
                    GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
                    GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
                    GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E,
                    GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT -> true;
            default -> false;
        };
    }

    Vec3 movement(double elapsed, boolean fast) {
        rotate(((held(GLFW.GLFW_KEY_E) ? 1 : 0) - (held(GLFW.GLFW_KEY_Q) ? 1 : 0)) * 90.0D * elapsed);
        double forward = (held(GLFW.GLFW_KEY_W) || held(GLFW.GLFW_KEY_UP) ? 1 : 0)
                - (held(GLFW.GLFW_KEY_S) || held(GLFW.GLFW_KEY_DOWN) ? 1 : 0);
        double right = (held(GLFW.GLFW_KEY_D) || held(GLFW.GLFW_KEY_RIGHT) ? 1 : 0)
                - (held(GLFW.GLFW_KEY_A) || held(GLFW.GLFW_KEY_LEFT) ? 1 : 0);
        double speed = this.height * (fast ? 2.5D : 1.0D) / Math.max(1.0D, Math.hypot(forward, right));
        Vec3 movement = SettlementViewCamera.movement(yaw(), right, forward);
        double response = 1.0D - Math.exp(-MOVE_RESPONSE * elapsed);
        double targetX = movement.x * speed, targetZ = movement.z * speed;
        // integrate the changing velocity so travel distance does not depend on frame rate
        double dx = targetX * elapsed + (this.velocityX - targetX) * response / MOVE_RESPONSE;
        double dz = targetZ * elapsed + (this.velocityZ - targetZ) * response / MOVE_RESPONSE;
        this.velocityX += (targetX - this.velocityX) * response;
        this.velocityZ += (targetZ - this.velocityZ) * response;
        this.cameraHeight += (this.height - this.cameraHeight) * (1.0D - Math.exp(-ZOOM_RESPONSE * elapsed));
        snapHeight();
        return new Vec3(dx, 0, dz);
    }

    Vec3 drag(double dx, double dy, int screenHeight, double fov) {
        stopPan();
        double scale = 2.0D * this.height * Math.tan(Math.toRadians(fov / 2.0D)) / Math.max(1, screenHeight);
        return SettlementViewCamera.movement(yaw(), dx, dy).scale(scale);
    }

    boolean pan(SettlementViewWindow window, double dx, double dz) {
        var target = window.focus(this.x + dx, this.z + dz);
        boolean changed = target.x != this.x || target.z != this.z;
        this.x = target.x;
        this.z = target.z;
        return changed;
    }

    boolean zoom(double direction, int renderDistance) {
        float next = (float) Math.clamp(this.height * Math.pow(1.15D, direction),
                SettlementViewWindow.MIN_HEIGHT, maximumHeight(renderDistance));
        boolean changed = next != this.height;
        this.height = next;
        return changed;
    }

    SettlementViewCamera.Pose position(SettlementViewWindow window, double visibleDistance,
                                       SettlementViewCamera.Heights terrain, double elapsed) {
        return this.camera.position(window, this.x, this.z, this.cameraHeight, visibleDistance, terrain, elapsed);
    }

    void clear() {
        this.heldKeys.clear();
        stopPan();
    }

    void stopPan() {
        this.velocityX = 0.0D;
        this.velocityZ = 0.0D;
    }

    private void snapHeight() {
        if (Math.abs(this.height - this.cameraHeight) < 0.01D) this.cameraHeight = this.height;
    }

    private static float maximumHeight(int renderDistance) {
        return Math.min(SettlementViewWindow.MAX_HEIGHT, Math.max(SettlementViewWindow.MIN_HEIGHT, renderDistance * 12.0F));
    }
}
