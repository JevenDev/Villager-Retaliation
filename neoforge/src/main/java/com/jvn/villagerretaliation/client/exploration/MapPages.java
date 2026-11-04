package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class MapPages {
    enum Control {
        ROUTES, VILLAGES, SEARCH, RELATIONS, SETTINGS;

        List<Component> tooltip() {
            return switch (this) {
                case ROUTES -> List.of(MapLayers.text("routes"), MapLayers.text("routes_hint"));
                case VILLAGES -> List.of(com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen.text("villages"), MapLayers.text("villages_hint"));
                case SEARCH -> List.of(MapLocationsClient.text("title"), MapLayers.text("search_shortcut"));
                case RELATIONS -> List.of(com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen.text("title"), MapLayers.text("relations_hint"));
                case SETTINGS -> List.of(MapLayers.text("settings"), MapLayers.text("settings_hint"));
            };
        }
    }

    private static final int WIDTH = 208, HEIGHT = 217;
    static final int CORNER_SIZE = 119, CORNER_OFFSET = 15;
    private static final MapHudSprite WOOD = new MapHudSprite("map_wood_corner", CORNER_SIZE, CORNER_SIZE);
    private static final MapHudSprite PAGE_1 = new MapHudSprite("map_left_page_1", WIDTH, HEIGHT);
    private static final MapHudSprite PAGE_1_SELECTED = new MapHudSprite("map_left_page_1_selected", WIDTH, HEIGHT);
    private static final MapHudSprite PAGE_2 = new MapHudSprite("map_left_page_2", WIDTH, HEIGHT);
    private final MapTabs tabs = new MapTabs();
    private final MapBottomPage bottom = new MapBottomPage();
    private float mouseX, mouseY, zoom;
    private float entrance = 1;
    private long lastFrame;
    private boolean pageHovered;
    private float pageX, pageY, pagePeek;
    private int screenWidth, screenHeight;

    private boolean overPage(double x, double y) {
        return x >= 0 && y >= 0 && x < screenWidth && y < screenHeight
                && (pageHovered ? PAGE_1_SELECTED : PAGE_1).contains(x - pageX, y - pageY);
    }
    MapBottomPage bottom() { return bottom; }
    Control controlAt(double x, double y) {
        var tab = switch (tabs.at(x, y)) {
            case 0 -> Control.ROUTES;
            case 1 -> Control.VILLAGES;
            case 2 -> Control.SEARCH;
            default -> null;
        };
        if (tab != null) return tab;
        if (overPage(x, y)) return Control.RELATIONS;
        return bottom.at(x, y) ? Control.SETTINGS : null;
    }
    float compassX() { return -mouseX * 1.5f + zoom * .5f + cornerSlide(); }
    float compassY() { return -mouseY * 1.5f + zoom * .5f + cornerSlide(); }

    private float cornerSlide() { return (1 - entrance) * CORNER_SIZE; }

    void renderCorner(GuiGraphics graphics, int width, int height) {
        float left = width - CORNER_SIZE + CORNER_OFFSET + (zoom - mouseX) * 3 + cornerSlide();
        float top = height - CORNER_SIZE + CORNER_OFFSET + (zoom - mouseY) * 3 + cornerSlide();
        WOOD.render(graphics, left, top);
    }

    void render(GuiGraphics graphics, int width, int height, int x, int y, double scale, float opacity, float entrance) {
        this.entrance = Math.clamp(entrance, 0, 1);
        screenWidth = width;
        screenHeight = height;
        long now = System.nanoTime();
        double elapsed = lastFrame == 0 ? 0 : Math.clamp((now - lastFrame) / 1e9, 0, .05);
        lastFrame = now;
        float smoothing = (float) (1 - Math.exp(-10 * elapsed));
        float targetX = Math.clamp(2f * x / Math.max(1, width) - 1, -1, 1);
        float targetY = Math.clamp(2f * y / Math.max(1, height) - 1, -1, 1);
        if (!bottom.raised()) {
            mouseX += (targetX - mouseX) * smoothing;
            mouseY += (targetY - mouseY) * smoothing;
        }
        float targetZoom = (float) Math.clamp(Math.log(scale / MapNavigation.DEFAULT_SCALE) / Math.log(2), -4, 4);
        if (!bottom.raised()) zoom += (targetZoom - zoom) * smoothing;
        pageX = -96 - mouseX * 4 - zoom * 4 - (1 - this.entrance) * WIDTH + pagePeek * 10;
        pageY = -68 - mouseY * 4 - zoom * 4 - (1 - this.entrance) * HEIGHT + pagePeek * 10;
        pageHovered = overPage(x, y);
        float previousPeek = pagePeek;
        pagePeek += ((pageHovered ? 1 : 0) - pagePeek) * smoothing;
        pageX += (pagePeek - previousPeek) * 10;
        pageY += (pagePeek - previousPeek) * 10;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1, 1, 1, opacity);
        bottom.render(graphics, width, height, x, y, mouseX, mouseY, zoom, smoothing, this.entrance);
        PAGE_2.render(graphics, -84 - mouseX * 2 - zoom * 2 - (1 - this.entrance) * WIDTH, -68 - mouseY * 2 - zoom * 2 - (1 - this.entrance) * HEIGHT);
        (pageHovered ? PAGE_1_SELECTED : PAGE_1).render(graphics, pageX, pageY);
        renderCorner(graphics, width, height);
        graphics.setColor(1, 1, 1, 1);
        tabs.render(graphics, width, height, x, y, mouseX, mouseY, zoom, smoothing, opacity, this.entrance);
    }
}
