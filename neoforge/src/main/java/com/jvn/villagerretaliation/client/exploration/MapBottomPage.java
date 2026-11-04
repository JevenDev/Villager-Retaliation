package com.jvn.villagerretaliation.client.exploration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class MapBottomPage {
    static final int WIDTH = 348, HEIGHT = 431, VISIBLE = 25;
    private static final MapHudSprite TEXTURE = new MapHudSprite("map_bottom_page", WIDTH, HEIGHT);
    private static final MapHudSprite SELECTED = new MapHudSprite("map_bottom_page_selected", WIDTH, HEIGHT);
    private boolean hovered, raised;
    private float left, top, peek;
    private int screenWidth, screenHeight;

    float left() { return left; }
    float top() { return top; }
    boolean raised() { return raised; }
    void raised(boolean value) { raised = value; }

    boolean at(double x, double y) {
        return x >= 0 && y >= 0 && x < screenWidth && y < screenHeight
                && (hovered ? SELECTED : TEXTURE).contains(x - left, y - top);
    }

    void render(GuiGraphics graphics, int width, int height, int x, int y,
                float mouseX, float mouseY, float zoom, float smoothing, float entrance) {
        screenWidth = width;
        screenHeight = height;
        if (raised) return;
        left = (width - MapPages.CORNER_SIZE + MapPages.CORNER_OFFSET - WIDTH) / 2f - mouseX * 3;
        top = height - (VISIBLE + mouseY * 3 - zoom * 3 + peek * 10) * entrance;
        hovered = at(x, y);
        float before = peek;
        peek += ((hovered ? 1 : 0) - peek) * smoothing;
        top -= (peek - before) * 10 * entrance;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        renderPaper(graphics, hovered);
        graphics.pose().popPose();
    }

    static void renderPaper(GuiGraphics graphics, boolean selected) {
        (selected ? SELECTED : TEXTURE).render(graphics, 0, 0);
        var font = Minecraft.getInstance().font;
        var title = MapLayers.text("settings");
        graphics.drawString(font, title, (WIDTH - font.width(title)) / 2, 10, 0xff625d53, false);
        graphics.hLine(20, WIDTH - 20, 29, 0xff000000);
    }
}
