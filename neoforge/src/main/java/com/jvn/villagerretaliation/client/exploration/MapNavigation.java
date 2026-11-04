package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapView;
import org.lwjgl.glfw.GLFW;

final class MapNavigation {
    static final double DEFAULT_SCALE = 2.5;
    private static final double WORLD_LIMIT = 29_999_000;
    private MapView view = new MapView(1, 1, 0, 0, DEFAULT_SCALE);
    private boolean dragging;
    private int heldKeys;
    private long lastFrame;
    private MapView resetFrom, resetTarget;
    private long resetStart;

    MapView view() { return this.view; }
    boolean resetting() { return this.resetTarget != null; }

    void resetTo(double x, double z) {
        clear();
        this.resetFrom = this.view;
        this.resetTarget = new MapView(this.view.width(), this.view.height(),
                Math.clamp(x, -WORLD_LIMIT, WORLD_LIMIT), Math.clamp(z, -WORLD_LIMIT, WORLD_LIMIT),
                Math.clamp(DEFAULT_SCALE, minimumZoom(this.view.width(), this.view.height()), 24));
        this.resetStart = -1;
    }

    void resize(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        set(new MapView(width, height, this.view.x(), this.view.z(),
                Math.clamp(this.view.scale(), minimumZoom(width, height), 24)));
        clear();
    }

    void focus(double x, double z) {
        cancelReset();
        x = Math.clamp(x, -WORLD_LIMIT, WORLD_LIMIT);
        z = Math.clamp(z, -WORLD_LIMIT, WORLD_LIMIT);
        if (x != this.view.x() || z != this.view.z())
            this.view = new MapView(this.view.width(), this.view.height(), x, z, this.view.scale());
    }

    void zoomAt(double x, double y, double target) {
        cancelReset();
        var before = this.view;
        double scale = Math.clamp(target, minimumZoom(before.width(), before.height()), 24);
        if (scale == before.scale()) return;
        this.view = new MapView(before.width(), before.height(), before.x(), before.z(), scale);
        focus(before.x() + before.worldX(x) - this.view.worldX(x), before.z() + before.worldZ(y) - this.view.worldZ(y));
    }

    void update(long now, boolean active) {
        if (!active) { clear(); return; }
        double elapsed = this.lastFrame == 0 ? 0 : Math.clamp((now - this.lastFrame) / 1e9, 0, .05);
        this.lastFrame = now;
        if (resetting()) {
            if (this.resetStart < 0) this.resetStart = now;
            double progress = Math.clamp((now - this.resetStart) / 450_000_000.0, 0, 1);
            if (progress == 1) {
                set(this.resetTarget);
                cancelReset();
            } else {
                double eased = MapScreenTransition.ease((float) progress);
                set(new MapView(this.view.width(), this.view.height(),
                        this.resetFrom.x() + (this.resetTarget.x() - this.resetFrom.x()) * eased,
                        this.resetFrom.z() + (this.resetTarget.z() - this.resetFrom.z()) * eased,
                        this.resetFrom.scale() * Math.pow(this.resetTarget.scale() / this.resetFrom.scale(), eased)));
            }
            return;
        }
        double dx = (((this.heldKeys & 2) != 0 ? 1 : 0) - ((this.heldKeys & 1) != 0 ? 1 : 0)) * 200 * elapsed;
        double dz = (((this.heldKeys & 8) != 0 ? 1 : 0) - ((this.heldKeys & 4) != 0 ? 1 : 0)) * 200 * elapsed;
        panPixels(dx, dz);
    }

    void beginDrag() { cancelReset(); this.dragging = true; }
    void endDrag() { this.dragging = false; }

    boolean drag(double dx, double dy) {
        if (!this.dragging) return false;
        panPixels(-dx, -dy);
        return true;
    }

    boolean press(int key) {
        int arrow = arrow(key);
        if (arrow != 0) cancelReset();
        this.heldKeys |= arrow;
        return arrow != 0;
    }

    boolean release(int key) {
        int arrow = arrow(key);
        this.heldKeys &= ~arrow;
        return arrow != 0;
    }

    void clear() {
        cancelReset();
        this.dragging = false;
        this.heldKeys = 0;
        this.lastFrame = 0;
    }

    private void cancelReset() {
        this.resetFrom = null;
        this.resetTarget = null;
    }

    private void panPixels(double dx, double dz) {
        if (dx != 0 || dz != 0) focus(this.view.x() + dx / this.view.scale(), this.view.z() + dz / this.view.scale());
    }

    private void set(MapView view) {
        if (!this.view.equals(view)) this.view = view;
    }

    private static double minimumZoom(int width, int height) {
        return Math.min(24, Math.max(width, height) / 4000.0);
    }

    private static int arrow(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT -> 1;
            case GLFW.GLFW_KEY_RIGHT -> 2;
            case GLFW.GLFW_KEY_UP -> 4;
            case GLFW.GLFW_KEY_DOWN -> 8;
            default -> 0;
        };
    }
}
