package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.*;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ExploredMapScreen extends Screen {
    private static final ExploredMapRenderer TILES = new ExploredMapRenderer();
    private final Screen parent;
    private final VillageRelationsMap relations = new VillageRelationsMap(this);
    private double centerX, centerZ, zoom = 2.5;
    private boolean centered, dragging;
    private int heldKeys;
    private long lastFrame, renderedFrames, openedAt;
    public ExploredMapScreen() { this(null); }
    public ExploredMapScreen(Screen parent) { super(Component.translatable("map.villagerretaliation.title")); this.parent = parent; }
    public void focusRelations(java.util.UUID perspective, java.util.UUID village, com.jvn.villagerretaliation.network.VillageRelationsPayload.Entry entry) {
        centerX = entry.position().getX(); centerZ = entry.position().getZ(); centered = true;
        relations.focus(perspective, village);
    }
    public void acceptRelations(com.jvn.villagerretaliation.network.VillageRelationsPayload payload) { relations.accept(payload); }
    public void acceptCouriers(com.jvn.villagerretaliation.network.SettlementCourierMapPayload payload) { relations.acceptCouriers(payload); }
    @Override public void tick() { relations.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    static void resetTiles() { TILES.close(); }
    long renderedFrames() { return renderedFrames; }
    @Override protected void init() {
        if (openedAt == 0) openedAt = System.nanoTime();
        if (!centered && minecraft.player != null) { center(); centered = true; }
        addRenderableWidget(Button.builder(com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen.text("title"),
                b -> relations.toggle()).bounds(10, 8, 80, 20).build());
        addRenderableWidget(Button.builder(com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen.text("villages"),
                b -> minecraft.setScreen(new com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen(this, null, null, null)))
                .bounds(width - 90, 8, 80, 20).build());
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
        float opening = openingProgress();
        if (opening < .4f) {
            TILES.prepare(view());
            MapFogRenderer.renderOpening(graphics, width, height, lighting, opening);
            return;
        }
        if (repository != null) {
            var view = view();
            TILES.render(graphics, view, lighting);
            graphics.enableScissor(0, 0, width, height);
            relations.renderTerrain(graphics, view);
            for (var marker : TILES.markers()) {
                if (relations.contains(marker.id())) continue;
                int x = (int)view.screenX(marker.x()), y = (int)view.screenY(marker.z());
                if (x < -16 || x > width + 16 || y < -16 || y > height + 16) continue;
                MapVillageIcon.render(graphics, x, y);
                graphics.drawCenteredString(font, marker.name().isBlank() ? Component.translatable("map.villagerretaliation.village")
                        : Component.literal(marker.name()), x, y + 10, 0xffe8c0);
            }
            relations.render(graphics, view, font);
            QuestMapOverlay.render(graphics, view, font);
            if (minecraft.player != null) MapPlayerIcon.render(graphics, (float)view.screenX(minecraft.player.getX()),
                    (float)view.screenY(minecraft.player.getZ()), minecraft.player.getViewYRot(partial));
            graphics.disableScissor(); renderedFrames++;
            if (!repository.error().isEmpty()) graphics.drawString(font, Component.translatable("map.villagerretaliation.storage_error"), 160, height - 20, 0xff9999);
        } else {
            graphics.fill(0, 0, width, height, lighting.background());
            graphics.drawCenteredString(font, Component.translatable("map.villagerretaliation.connecting"), width / 2, height / 2, 0xddd7cc);
        }
        if (opening < 1) {
            MapFogRenderer.renderOpening(graphics, width, height, lighting, opening);
            return;
        }
        int titleColor = repository != null && lighting.red() + lighting.green() + lighting.blue() > 2.1f ? 0x40464f : 0xffffff;
        graphics.drawCenteredString(font, title, width / 2, 12, titleColor);
        super.render(graphics, mouseX, mouseY, partial);
        relations.renderPopup(graphics, font, mouseX, mouseY, partial);
    }
    float openingProgress() {
        return openedAt < 0 || !VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.get()
                ? 1 : (float)Math.clamp((System.nanoTime() - openedAt) / 1_250_000_000.0, 0, 1);
    }
    public boolean coversWorld() { return openingProgress() >= .4f; }
    float openingPitch(float pitch) {
        float progress = openingProgress();
        if (progress >= .4f) return pitch;
        float turn = Math.clamp(progress / .33f, 0, 1);
        turn = turn * turn * (3 - 2 * turn);
        return pitch + (Math.min(pitch, -65) - pitch) * turn;
    }
    private void finishOpening() { openedAt = -1; }
    MapView view() { return new MapView(width, height, centerX, centerZ, zoom); }
    private void zoomAt(double x, double y, double target) {
        finishOpening();
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
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        finishOpening();
        if (relations.scroll(x, y, vertical)) return true;
        zoomAt(x, y, zoom * Math.pow(1.15, vertical)); return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (ExploredMapClient.OPEN.matchesMouse(button)) { onClose(); return true; }
        finishOpening();
        if (relations.click(font, view(), TILES.markers(), x, y, button)) return true;
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
        finishOpening();
        if (relations.key(key)) return true;
        if (ExploredMapClient.OPEN.matches(key, scan)) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { center(); return true; }
        if (arrow(key) != 0) { heldKeys |= arrow(key); return true; } return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        if (arrow(key) != 0) { heldKeys &= ~arrow(key); return true; } return super.keyReleased(key, scan, modifiers);
    }
    @Override public void removed() { dragging = false; heldKeys = 0; lastFrame = 0; openedAt = 0; }
}
