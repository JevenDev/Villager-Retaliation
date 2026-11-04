package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class MapBottomPage {
    static final int WIDTH = 348, HEIGHT = 431, VISIBLE = 25;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_bottom_page.png");
    private static final ResourceLocation SELECTED = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_bottom_page_selected.png");
    private boolean[] pixels, selectedPixels;
    private boolean hovered, raised;
    private float left, top, peek;
    private int screenWidth, screenHeight;

    void load() {
        pixels = loadPixels(TEXTURE);
        selectedPixels = loadPixels(SELECTED);
    }

    float left() { return left; }
    float top() { return top; }
    boolean raised() { return raised; }
    void raised(boolean value) { raised = value; }

    boolean at(double x, double y) {
        if (pixels == null || x < 0 || y < 0 || x >= screenWidth || y >= screenHeight) return false;
        int px = (int) Math.floor(x - left), py = (int) Math.floor(y - top);
        return px >= 0 && py >= 0 && px < WIDTH && py < HEIGHT && (hovered ? selectedPixels : pixels)[px + py * WIDTH];
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(selected ? SELECTED : TEXTURE, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        var font = Minecraft.getInstance().font;
        var title = MapLayers.text("settings");
        graphics.drawString(font, title, (WIDTH - font.width(title)) / 2, 10, 0xff625d53, false);
        graphics.hLine(20, WIDTH - 20, 29, 0xff000000);
    }

    private static boolean[] loadPixels(ResourceLocation texture) {
        var pixels = new boolean[WIDTH * HEIGHT];
        try (var input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture).open();
             var image = NativeImage.read(input)) {
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
                pixels[x + y * WIDTH] = image.getPixelRGBA(x * image.getWidth() / WIDTH, y * image.getHeight() / HEIGHT) >>> 24 != 0;
        } catch (IOException exception) { throw new UncheckedIOException("Failed to load bottom map page " + texture, exception); }
        return pixels;
    }
}
