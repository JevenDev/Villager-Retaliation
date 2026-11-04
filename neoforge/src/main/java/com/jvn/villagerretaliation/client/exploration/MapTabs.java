package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class MapTabs {
    static final int WIDTH = 100, HEIGHT = 155, OVERHANG = 56;
    private final Tab[] tabs = new Tab[3];
    private final float[] peek = new float[3];
    private final float[] left = new float[3], top = new float[3];
    private int hovered = -1, screenWidth, screenHeight;

    void load() {
        for (int index = 0; index < tabs.length; index++) {
            tabs[index] = Tab.load(ResourceLocation.fromNamespaceAndPath("villagerretaliation",
                    "textures/gui/map_right_tab_" + (index + 1) + ".png"));
        }
    }

    void render(GuiGraphics graphics, int width, int height, int x, int y,
                float mouseX, float mouseY, float zoom, float smoothing, float opacity, float entrance) {
        screenWidth = width;
        screenHeight = height;
        for (int index = 0; index < tabs.length; index++) {
            float depth = 4 - index;
            left[index] = width - WIDTH + OVERHANG - mouseX * depth + zoom * depth - peek[index] * 10 + (1 - entrance) * WIDTH;
            top[index] = -mouseY * depth;
        }
        hovered = at(x, y);
        for (int index = 0; index < tabs.length; index++) {
            float before = peek[index];
            peek[index] += ((hovered == index ? 1 : 0) - peek[index]) * smoothing;
            left[index] += (before - peek[index]) * 10;
        }
        graphics.setColor(1, 1, 1, opacity);
        for (int index = tabs.length - 1; index >= 0; index--)
            if (index != hovered) renderTab(graphics, index);
        if (hovered >= 0) renderTab(graphics, hovered);
        graphics.setColor(1, 1, 1, 1);
    }

    static List<Component> tooltip(int tab) {
        return switch (tab) {
            case 0 -> List.of(MapLayers.text("routes"), MapLayers.text("routes_hint"));
            case 1 -> List.of(com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen.text("villages"), MapLayers.text("villages_hint"));
            case 2 -> List.of(MapLocationsClient.text("title"), MapLayers.text("search_shortcut"));
            default -> List.of();
        };
    }

    int at(double x, double y) {
        if (x < 0 || y < 0 || x >= screenWidth || y >= screenHeight) return -1;
        if (hovered >= 0 && hits(hovered, x, y)) return hovered;
        for (int index = 0; index < tabs.length; index++)
            if (hits(index, x, y)) return index;
        return -1;
    }

    private boolean hits(int index, double x, double y) {
        return tabs[index] != null && tabs[index].opaque((int) Math.floor(x - left[index]), (int) Math.floor(y - top[index]), hovered == index);
    }

    private void renderTab(GuiGraphics graphics, int index) {
        graphics.pose().pushPose();
        graphics.pose().translate(left[index], top[index], 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(index == hovered ? tabs[index].selected() : tabs[index].texture(), 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        graphics.pose().popPose();
    }

    private record Tab(ResourceLocation texture, ResourceLocation selected, boolean[] pixels, boolean[] selectedPixels) {
        boolean opaque(int x, int y, boolean hovered) {
            return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && (hovered ? selectedPixels : pixels)[x + y * WIDTH];
        }

        static Tab load(ResourceLocation texture) {
            var selected = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), texture.getPath().replace(".png", "_selected.png"));
            return new Tab(texture, selected, loadPixels(texture), loadPixels(selected));
        }

        private static boolean[] loadPixels(ResourceLocation texture) {
            var pixels = new boolean[WIDTH * HEIGHT];
            try (var input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture).open();
                 var image = NativeImage.read(input)) {
                for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
                    pixels[x + y * WIDTH] = image.getPixelRGBA(x * image.getWidth() / WIDTH, y * image.getHeight() / HEIGHT) >>> 24 != 0;
            } catch (IOException exception) { throw new UncheckedIOException("Failed to load map tab " + texture, exception); }
            return pixels;
        }
    }
}
