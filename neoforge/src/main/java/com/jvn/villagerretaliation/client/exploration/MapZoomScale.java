package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class MapZoomScale extends AbstractWidget {
    private final DoubleSupplier scale;
    private final DoubleConsumer zoom;

    MapZoomScale(int screenHeight, DoubleSupplier scale, DoubleConsumer zoom) {
        super(MapCompass.PADDING, Math.max(MapCompass.PADDING, screenHeight - MapCompass.PADDING - 124),
                96, Math.min(124, Math.max(52, screenHeight - MapCompass.PADDING * 2)), Component.empty());
        this.scale = scale;
        this.zoom = zoom;
        setTooltip(Tooltip.create(MapLayers.text("zoom_hint")));
    }

    int segments() { return 4; }
    int rulerPixels() { return getHeight() - 24; }
    double blocks(int index) { return index * (double) rulerPixels() / segments() / scale.getAsDouble(); }
    private String distance() {
        double blocks = blocks(segments());
        long rounded = Math.round(blocks);
        return (Math.abs(blocks - rounded) < .05 ? "" : "~") + rounded;
    }
    private String percentage() { return Math.round(scale.getAsDouble() / MapNavigation.DEFAULT_SCALE * 100) + "%"; }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        var font = Minecraft.getInstance().font;
        int segments = segments(), pixels = rulerPixels();
        int x = getX() + 3, bottom = getBottom() - font.lineHeight / 2 - 1, top = bottom - pixels;
        int ink = color(0xff000000);
        var zoomLabel = MapLayers.text("zoom", percentage());
        graphics.drawString(font, zoomLabel, getX(), top - 17, ink, false);
        if (isFocused()) graphics.hLine(getX(), getX() + font.width(zoomLabel), top - 6, ink);
        graphics.vLine(x, top, bottom, ink);
        for (int index = 0; index <= segments; index++) {
            int y = bottom - (int) Math.round(index * (double) pixels / segments);
            int length = index == 0 || index == segments ? 3 : 2;
            graphics.hLine(x - length, x + length, y, ink);
        }
        graphics.drawString(font, MapLayers.text("scale", distance()), x + 9, top - font.lineHeight / 2, ink, false);
        graphics.drawString(font, "0", x + 9, bottom - font.lineHeight / 2, ink, false);
        if (isHovered() && mouseY >= top && mouseY <= bottom)
            graphics.hLine(x - 3, x + 5, mouseY, ink);
    }

    private int color(int value) { return VillagerClientUiUtil.withAlphaRound(value, alpha); }

    @Override protected void onDrag(double x, double y, double dx, double dy) {
        if (visible && active) zoom.accept(scale.getAsDouble() * Math.pow(1.02, -dy));
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!visible || !active || !isMouseOver(x, y)) return false;
        zoom.accept(scale.getAsDouble() * Math.pow(1.15, vertical));
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!visible || !active || !isFocused()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_RIGHT -> zoom.accept(scale.getAsDouble() * 1.15);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT -> zoom.accept(scale.getAsDouble() / 1.15);
            case GLFW.GLFW_KEY_PAGE_UP -> zoom.accept(scale.getAsDouble() * 1.5);
            case GLFW.GLFW_KEY_PAGE_DOWN -> zoom.accept(scale.getAsDouble() / 1.5);
            case GLFW.GLFW_KEY_HOME -> zoom.accept(MapNavigation.DEFAULT_SCALE);
            default -> { return false; }
        }
        return true;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, MapLayers.text("zoom", percentage()));
        output.add(NarratedElementType.USAGE, MapLayers.text("zoom_hint"));
    }
}
