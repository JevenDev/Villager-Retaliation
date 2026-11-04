package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.jvn.villagerretaliation.client.ui.VillagerTooltips;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

final class MapLayersScreen extends Screen implements VillagerTooltips.Provider {
    private static final ResourceLocation CHECKBOX = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/checbox_black.png");
    private static final ResourceLocation CHECKED = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/checkbox_black_filled.png");
    private final ExploredMapScreen parent;
    private final LongSupplier clock;
    private float originLeft, originTop, from;
    private float left, top;
    private long started;
    private boolean minimap, closing;

    MapLayersScreen(ExploredMapScreen parent) { this(parent, System::nanoTime); }
    MapLayersScreen(ExploredMapScreen parent, LongSupplier clock) {
        super(MapLayers.text("settings"));
        this.parent = parent;
        this.clock = clock;
        originLeft = parent.settingsPage().left();
        originTop = parent.settingsPage().top();
        left = originLeft; top = originTop;
        started = clock.getAsLong();
    }

    @Override protected void init() {
        parent.settingsPage().raised(true);
        addRenderableWidget(new PaperButton(20, 40, 150, MapLayers.text("full_map"), null,
                b -> { minimap = false; rebuildWidgets(); })).active = minimap;
        addRenderableWidget(new PaperButton(178, 40, 150, MapLayers.text("minimap"), null,
                b -> { minimap = true; rebuildWidgets(); })).active = !minimap;
        var layers = new MapLayers(minimap);
        int y = 75, spacing = Math.min(24, Math.max(16, (height - 95) / 5));
        for (var layer : MapLayers.Layer.values()) {
            var button = addRenderableWidget(new PaperButton(24, y, 300, layer.title(), () -> layers.visible(layer), b -> {
                layers.toggle(layer);
                b.setMessage(MapLayers.text(layers.visible(layer) ? "layer_on" : "layer_off", layer.title()));
            }));
            button.setMessage(MapLayers.text(layers.visible(layer) ? "layer_on" : "layer_off", layer.title()));
            button.setHeight(Math.min(20, spacing));
            y += spacing;
        }
    }

    float progress() {
        float time = (float) Math.clamp((clock.getAsLong() - started) / 400_000_000.0, 0, 1);
        return from + ((closing ? 0 : 1) - from) * MapScreenTransition.ease(time);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        if (closing) return;
        from = progress();
        if (from == 1) {
            originLeft = parent.settingsPage().left();
            originTop = parent.settingsPage().top();
        }
        started = clock.getAsLong();
        closing = true;
    }
    @Override public void tick() { if (closing && progress() == 0) minecraft.setScreen(parent); }
    @Override public void removed() { parent.settingsPage().raised(false); }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        parent.render(graphics, -1, -1, partial);
        float progress = progress();
        float targetLeft = Math.clamp((width - MapPages.CORNER_SIZE + MapPages.CORNER_OFFSET - MapBottomPage.WIDTH) / 2f,
                10, Math.max(10, width - MapBottomPage.WIDTH - 10));
        left = originLeft + (targetLeft - originLeft) * progress;
        top = originTop + (Math.max(10, (height - MapBottomPage.HEIGHT) / 2f) - originTop) * progress;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(0, 0, width, height, (int) (80 * progress) << 24);
        graphics.pose().translate(left, top, 0);
        MapBottomPage.renderPaper(graphics, true);
        super.render(graphics, (int) (x - left), (int) (y - top), partial);
        graphics.pose().popPose();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (closing) return true;
        double localX = x - left, localY = y - top;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (localX < 0 || localX >= MapBottomPage.WIDTH
                || localY < 0 || localY >= MapBottomPage.HEIGHT || localY < 34)) {
            onClose(); return true;
        }
        if (progress() == 1) super.mouseClicked(localX, localY, button);
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(x - left, y - top, button); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return true; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || ExploredMapClient.OPEN.matches(key, scan)) { onClose(); return true; }
        if (closing || progress() < 1) return true;
        return super.keyPressed(key, scan, modifiers);
    }

    private static final class PaperButton extends Button {
        private final Component label;
        private final BooleanSupplier checked;
        PaperButton(int x, int y, int width, Component label, BooleanSupplier checked, OnPress press) {
            super(x, y, width, 20, label, press, DEFAULT_NARRATION);
            this.label = label;
            this.checked = checked;
        }
        @Override protected void renderWidget(GuiGraphics graphics, int x, int y, float partial) {
            var font = Minecraft.getInstance().font;
            int textX = checked == null ? getX() + (getWidth() - font.width(label)) / 2 : getX() + 16;
            int textY = getY() + 6;
            if (checked != null) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                graphics.blit(checked.getAsBoolean() ? CHECKED : CHECKBOX, getX(), textY - 1, 0, 0, 9, 9, 9, 9);
            }
            graphics.drawString(font, label, textX, textY, 0xff000000, false);
            if (isHoveredOrFocused() || !active) graphics.hLine(textX, textX + font.width(label), textY + font.lineHeight + 1, 0xff000000);
        }
    }
}
