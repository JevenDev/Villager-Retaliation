package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class MapTabsClientChecks {
    private MapTabsClientChecks() { }

    static void verify(ExploredMapScreen screen) {
        var minecraft = Minecraft.getInstance();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        graphics.flush();
        var shader = RenderSystem.getShader();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        var tabs = new MapTabs();
        tabs.load();
        var sprites = new NativeImage[3];
        var selectedSprites = new NativeImage[3];
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            for (int index = 0; index < sprites.length; index++) {
                var texture = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_right_tab_" + (index + 1) + ".png");
                try (var input = minecraft.getResourceManager().getResourceOrThrow(texture).open()) { sprites[index] = NativeImage.read(input); }
                var selected = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_right_tab_" + (index + 1) + "_selected.png");
                try (var input = minecraft.getResourceManager().getResourceOrThrow(selected).open()) { selectedSprites[index] = NativeImage.read(input); }
                require(sprites[index].getWidth() == 100 && sprites[index].getHeight() == 155
                        && selectedSprites[index].getWidth() == 100 && selectedSprites[index].getHeight() == 155, "map tabs retain their native dimensions");
            }
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 640, 360, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int left = 640 - MapTabs.WIDTH + MapTabs.OVERHANG, top = 0;
            var output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
            for (int scale = 1; scale <= 2; scale++) {
                var target = new TextureTarget(640 * scale, 360 * scale, false, Minecraft.ON_OSX);
                try {
                    try (var idle = render(graphics, target, tabs, -1, -1, 0, 0, 0)) {
                        require(tabs.at(left, top + 10) == -1 && tabs.at(640, top + 30) == -1,
                                "transparent padding and offscreen pixels are not hover targets");
                        require(tabs.at(left + 30, top + 60) == 0, "the first tab starts in front where sprites overlap");
                        for (int y = 0; y < MapTabs.HEIGHT; y++) for (int x = 0; x < 44; x++) {
                            int color = 0;
                            for (var sprite : sprites) if (sprite.getPixelRGBA(x, y) >>> 24 != 0) { color = sprite.getPixelRGBA(x, y); break; }
                            if (color >>> 24 == 255 || color == 0)
                                require(pixel(idle, left + x, top + y, scale) == color, "idle tabs preserve alpha, colors and the 56-pixel overhang");
                        }
                        if (scale == 1) idle.writeToFile(output.resolve("map-right-tabs-idle.png"));
                    }
                    int[] hoverY = {30, 85, 135};
                    for (int index = 0; index < sprites.length; index++) {
                        try (var ignored = render(graphics, target, tabs, -1, -1, 0, 0, 0)) { }
                        try (var hovered = render(graphics, target, tabs, left + 30, top + hoverY[index], 0, 0, 0)) {
                            require(tabs.at(left + 30, top + hoverY[index]) == index, "each exposed tab can be hovered");
                            int clear = 0, opaque = 0;
                            for (int y = -1; y <= MapTabs.HEIGHT; y++) for (int x = -1; x < 54; x++) {
                                int sx = left - 10 + x, sy = top + y;
                                if (sx < 0 || sy < 0 || sx >= 640 || sy >= 360) continue;
                                if (alpha(selectedSprites[index], x, y)) {
                                    int color = selectedSprites[index].getPixelRGBA(x, y);
                                    if (color >>> 24 == 255) {
                                        require(pixel(hovered, sx, sy, scale) == color, "the hovered tab swaps to its selected sprite, slides inward and renders above its neighbors");
                                        opaque++;
                                    }
                                } else {
                                    boolean covered = false;
                                    for (int other = 0; other < sprites.length; other++)
                                        if (other != index && alpha(sprites[other], x - 10, y)) covered = true;
                                    if (!covered) {
                                        require(pixel(hovered, sx, sy, scale) >>> 24 == 0, "hover preserves the selected sprite's transparent padding");
                                        clear++;
                                    }
                                }
                            }
                            require(clear > 30 && opaque > 1000, "alpha and stacking checks cover each tab's visible silhouette");
                            if (scale == 1) hovered.writeToFile(output.resolve("map-right-tab-" + (index + 1) + "-hover.png"));
                        }
                    }
                    try (var fractional = render(graphics, target, tabs, -1, -1, 1f / scale, 1f / scale, 0)) {
                        for (int index = 0; index < sprites.length; index++) {
                            int motionDepth = 4 - index;
                            for (int y = hoverY[index]; y <= hoverY[index] + 1; y++) for (int x = 20; x < 26; x++)
                                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++)
                                    require(fractional.getPixelRGBA((left + x) * scale - motionDepth + dx, (top + y) * scale - motionDepth + dy)
                                            == sprites[index].getPixelRGBA(x, y), "tab parallax moves in physical pixels instead of snapping to whole GUI pixels");
                            require(tabs.at(left + 25 - (double) motionDepth / scale, top + hoverY[index] - (double) motionDepth / scale) == index,
                                    "tab hover masks track fractional positions");
                        }
                    }
                    try (var moving = render(graphics, target, tabs, -1, -1, 1, -1, 1)) {
                        for (int index = 0; index < sprites.length; index++) {
                            int shiftedTop = top + 4 - index;
                            require(tabs.at(left + 30, shiftedTop + hoverY[index]) == index, "hover follows independent mouse and zoom parallax");
                        }
                    }
                } finally { target.destroyBuffers(); }
            }
            var original = screen.view();
            int x = screen.width - 15, y = 30;
            screen.mouseClicked(x, y, 1);
            screen.mouseDragged(x + 10, y, 0, 10, 0);
            screen.mouseReleased(x + 10, y, 0);
            screen.mouseScrolled(x, y, 0, 1);
            require(minecraft.screen == screen && screen.view().equals(original), "right-clicking tabs does not open screens or pass dragging and scrolling through to the map");
            System.out.println("Map tab checks passed: original alpha, 56-pixel overhang, hover masks, inward motion, foreground stacking, selected sprites, transparent edges, GUI scales, parallax and input occlusion");
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
        finally {
            for (var sprite : sprites) if (sprite != null) sprite.close();
            for (var sprite : selectedSprites) if (sprite != null) sprite.close();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShader(() -> shader);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        }
    }

    private static boolean alpha(NativeImage image, int x, int y) {
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && image.getPixelRGBA(x, y) >>> 24 != 0;
    }

    private static int pixel(NativeImage image, int x, int y, int scale) {
        int color = image.getPixelRGBA(x * scale, y * scale);
        for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++)
            require(image.getPixelRGBA(x * scale + dx, y * scale + dy) == color, "tab pixels stay aligned to GUI pixels");
        return color;
    }

    private static NativeImage render(GuiGraphics graphics, TextureTarget target, MapTabs tabs, int x, int y,
                                      float mouseX, float mouseY, float zoom) {
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        tabs.render(graphics, 640, 360, x, y, mouseX, mouseY, zoom, 1, 1, 1);
        graphics.flush();
        var image = new NativeImage(target.width, target.height, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY();
        return image;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
