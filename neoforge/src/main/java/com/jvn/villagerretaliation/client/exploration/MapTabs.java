package com.jvn.villagerretaliation.client.exploration;

import net.minecraft.client.gui.GuiGraphics;

final class MapTabs {
    static final int WIDTH = 100, HEIGHT = 155, OVERHANG = 56;
    private static final Tab[] TABS = {
            new Tab("map_right_tab_1"), new Tab("map_right_tab_2"), new Tab("map_right_tab_3")
    };
    private final float[] peek = new float[3];
    private final float[] left = new float[3], top = new float[3];
    private int hovered = -1, screenWidth, screenHeight;

    void render(GuiGraphics graphics, int width, int height, int x, int y,
                float mouseX, float mouseY, float zoom, float smoothing, float opacity, float entrance) {
        screenWidth = width;
        screenHeight = height;
        for (int index = 0; index < TABS.length; index++) {
            float depth = 4 - index;
            left[index] = width - WIDTH + OVERHANG - mouseX * depth + zoom * depth - peek[index] * 10 + (1 - entrance) * WIDTH;
            top[index] = -mouseY * depth;
        }
        hovered = at(x, y);
        for (int index = 0; index < TABS.length; index++) {
            float before = peek[index];
            peek[index] += ((hovered == index ? 1 : 0) - peek[index]) * smoothing;
            left[index] += (before - peek[index]) * 10;
        }
        graphics.setColor(1, 1, 1, opacity);
        for (int index = TABS.length - 1; index >= 0; index--)
            if (index != hovered) renderTab(graphics, index);
        if (hovered >= 0) renderTab(graphics, hovered);
        graphics.setColor(1, 1, 1, 1);
    }

    int at(double x, double y) {
        if (x < 0 || y < 0 || x >= screenWidth || y >= screenHeight) return -1;
        if (hovered >= 0 && hits(hovered, x, y)) return hovered;
        for (int index = 0; index < TABS.length; index++)
            if (hits(index, x, y)) return index;
        return -1;
    }

    private boolean hits(int index, double x, double y) {
        return TABS[index].sprite(hovered == index).contains(x - left[index], y - top[index]);
    }

    private void renderTab(GuiGraphics graphics, int index) {
        TABS[index].sprite(index == hovered).render(graphics, left[index], top[index]);
    }

    private record Tab(MapHudSprite normal, MapHudSprite selected) {
        Tab(String name) { this(new MapHudSprite(name, WIDTH, HEIGHT), new MapHudSprite(name + "_selected", WIDTH, HEIGHT)); }
        MapHudSprite sprite(boolean hovered) { return hovered ? selected : normal; }
    }
}
