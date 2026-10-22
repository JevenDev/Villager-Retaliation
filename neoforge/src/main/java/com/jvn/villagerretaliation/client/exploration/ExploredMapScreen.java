package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ExploredMapScreen extends Screen {
    private static final ExploredMapRenderer TILES = new ExploredMapRenderer();
    private double centerX, centerZ, zoom = 2.5;
    private boolean centered, dragging;
    private int heldKeys;
    private long lastFrame, renderedFrames;
    public ExploredMapScreen() { super(Component.translatable("map.villagerretaliation.title")); }
    static void resetTiles() { TILES.close(); }
    long renderedFrames() { return renderedFrames; }
    @Override protected void init() {
        if (!centered && minecraft.player != null) { center(); centered = true; }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width - 76, height - 26, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("map.villagerretaliation.center"), b -> center()).bounds(10, height - 26, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u2212"), b -> zoomAt(width / 2.0, height / 2.0, zoom / 1.25)).bounds(96, height - 26, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomAt(width / 2.0, height / 2.0, zoom * 1.25)).bounds(124, height - 26, 24, 20).build());
    }
    private void center() { centerX = minecraft.player.getX(); centerZ = minecraft.player.getZ(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        moveWithKeys(); zoom = Math.clamp(zoom, Math.max(width, height) / 2800.0, 24);
        var repository = ExploredMapClient.repository();
        var lighting = minecraft.level == null ? MapLighting.DAY : MapLighting.at(minecraft.level.getTimeOfDay(partial), minecraft.level.dimensionType().hasSkyLight());
        if (repository != null) {
            var view = view();
            TILES.render(graphics, view, lighting);
            graphics.enableScissor(0, 0, width, height);
            for (var marker : TILES.markers()) {
                int x = (int)view.screenX(marker.x()), y = (int)view.screenY(marker.z());
                if (x < -16 || x > width + 16 || y < -16 || y > height + 16) continue;
                MapVillageIcon.render(graphics, x, y);
                graphics.drawCenteredString(font, marker.name().isBlank() ? Component.translatable("map.villagerretaliation.village")
                        : Component.literal(marker.name()), x, y + 10, 0xffe8c0);
            }
            if (minecraft.player != null) MapPlayerIcon.render(graphics, (float)view.screenX(minecraft.player.getX()),
                    (float)view.screenY(minecraft.player.getZ()), minecraft.player.getViewYRot(partial));
            graphics.disableScissor(); renderedFrames++;
            if (!repository.error().isEmpty()) graphics.drawString(font, Component.translatable("map.villagerretaliation.storage_error"), 160, height - 20, 0xff9999);
        } else {
            graphics.fill(0, 0, width, height, lighting.background());
            graphics.drawCenteredString(font, Component.translatable("map.villagerretaliation.connecting"), width / 2, height / 2, 0xddd7cc);
        }
        int titleColor = repository != null && lighting.red() + lighting.green() + lighting.blue() > 2.1f ? 0x40464f : 0xffffff;
        graphics.drawCenteredString(font, title, width / 2, 12, titleColor);
        super.render(graphics, mouseX, mouseY, partial);
    }
    MapView view() { return new MapView(width, height, centerX, centerZ, zoom); }
    private void zoomAt(double x, double y, double target) {
        var before = view(); zoom = Math.clamp(target, Math.max(width, height) / 2800.0, 24); var after = view();
        pan(before.worldX(x) - after.worldX(x), before.worldZ(y) - after.worldZ(y));
    }
    private void moveWithKeys() {
        long now = System.nanoTime();
        double elapsed = lastFrame == 0 ? 0 : Math.min(.05, (now - lastFrame) / 1e9); lastFrame = now;
        pan((((heldKeys & 2) != 0 ? 1 : 0) - ((heldKeys & 1) != 0 ? 1 : 0)) * 200 * elapsed / zoom,
                (((heldKeys & 8) != 0 ? 1 : 0) - ((heldKeys & 4) != 0 ? 1 : 0)) * 200 * elapsed / zoom);
    }
    private void pan(double dx, double dz) {
        centerX = Math.clamp(centerX + dx, -29_999_000, 29_999_000); centerZ = Math.clamp(centerZ + dz, -29_999_000, 29_999_000);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { zoomAt(x, y, zoom * Math.pow(1.15, vertical)); return true; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (ExploredMapClient.OPEN.matchesMouse(button)) { onClose(); return true; }
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0) { dragging = true; return true; } return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) dragging = false; return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging) { pan(-dx / zoom, -dy / zoom); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    private static int arrow(int key) {
        return switch (key) { case GLFW.GLFW_KEY_LEFT -> 1; case GLFW.GLFW_KEY_RIGHT -> 2;
            case GLFW.GLFW_KEY_UP -> 4; case GLFW.GLFW_KEY_DOWN -> 8; default -> 0; };
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (ExploredMapClient.OPEN.matches(key, scan)) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { center(); return true; }
        if (arrow(key) != 0) { heldKeys |= arrow(key); return true; } return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        if (arrow(key) != 0) { heldKeys &= ~arrow(key); return true; } return super.keyReleased(key, scan, modifiers);
    }
    @Override public void removed() { dragging = false; heldKeys = 0; }
}
