package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class MapCompassClientChecks {
    private MapCompassClientChecks() { }

    static void verify(ExploredMapScreen screen) {
        var minecraft = Minecraft.getInstance();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        graphics.flush();
        var shader = RenderSystem.getShader();
        int texture0 = RenderSystem.getShaderTexture(0), texture1 = RenderSystem.getShaderTexture(1);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int left = screen.width - MapCompass.SIZE - MapCompass.PADDING;
        int top = screen.height - MapCompass.SIZE - MapCompass.PADDING;
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            require(widget.getRight() <= left || widget.getX() >= left + MapCompass.SIZE
                    || widget.getBottom() <= top || widget.getY() >= top + MapCompass.SIZE,
                    "map controls do not cover the compass");
        }
        String[] directions = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        for (int index = 0; index < directions.length; index++) {
            require(MapCompass.direction(index * 45).equals(directions[index]), "compass labels cover every cardinal and diagonal direction");
            require(MapCompass.direction(index * 45 - 720).equals(directions[index])
                    && MapCompass.direction(index * 45 + 360).equals(directions[index]), "facing labels wrap consistently for negative and repeated rotations");
        }
        require(MapCompass.direction(22.49f).equals("S") && MapCompass.direction(22.5f).equals("SW")
                && MapCompass.direction(-22.5f).equals("S") && MapCompass.direction(-22.51f).equals("SE"),
                "facing labels change at the nearest eight-way heading boundary");
        require(MapCompass.hovered(screen.width, screen.height, left + 32, top + 32)
                && !MapCompass.hovered(screen.width, screen.height, left, top)
                && !MapCompass.hovered(screen.width, screen.height, -1, -1), "compass hover follows the circular dial rather than transparent corners");
        require(MapCompass.hovered(screen.width, screen.height, left + 1, top + 32, 0, 0)
                && !MapCompass.hovered(screen.width, screen.height, left + 1, top + 32, 2, 0),
                "the tooltip hover boundary follows compass parallax");
        require(MapCompass.hovered(screen.width, screen.height, left + .25, top + 32, 0, 0)
                && !MapCompass.hovered(screen.width, screen.height, left + .25, top + 32, .5f, 0),
                "compass hover follows fractional GUI-pixel movement");
        var tooltip = MapCompass.tooltip(minecraft.player, -45);
        require(tooltip.size() == 3 && tooltip.get(0).getString().equals(minecraft.player.getName().getString())
                && tooltip.get(1).getString().equals("X: " + minecraft.player.getBlockX() + ", Y: " + minecraft.player.getBlockY() + ", Z: " + minecraft.player.getBlockZ())
                && tooltip.get(2).getString().equals("Facing SE"), "compass tooltip contains the player name, block coordinates and facing direction");
        float[] headings = {0, 90, 180, -90, 45, 22.5F, 137.25F, 360};
        int[][] reference = new int[headings.length][];
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, screen.width, screen.height, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            for (int scale = 1; scale <= 3; scale++) {
                var target = new TextureTarget(screen.width * scale, screen.height * scale, false, Minecraft.ON_OSX);
                try {
                    target.setClearColor(0, 0, 0, 0);
                    for (int index = 0; index < headings.length; index++) {
                        target.clear(Minecraft.ON_OSX);
                        target.bindWrite(true);
                        MapCompass.render(graphics, screen.width, screen.height, headings[index], 1);
                        try (var image = new NativeImage(target.width, target.height, false)) {
                            RenderSystem.bindTexture(target.getColorTextureId());
                            image.downloadTexture(0, false);
                            image.flipY();
                            int[] pixels = new int[MapCompass.SIZE * MapCompass.SIZE];
                            for (int y = 0; y < MapCompass.SIZE; y++) for (int x = 0; x < MapCompass.SIZE; x++) {
                                int color = image.getPixelRGBA((left + x) * scale, (top + y) * scale);
                                pixels[y * MapCompass.SIZE + x] = color;
                                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++) {
                                    require(image.getPixelRGBA((left + x) * scale + dx, (top + y) * scale + dy) == color,
                                            "rotated needle pixels stay on the dial's texel grid");
                                }
                            }
                            require(image.getPixelRGBA((left - 1) * scale, (top + 32) * scale) == 0
                                    && image.getPixelRGBA((screen.width - MapCompass.PADDING) * scale, (top + 32) * scale) == 0
                                    && image.getPixelRGBA((left + 32) * scale, (top - 1) * scale) == 0
                                    && image.getPixelRGBA((left + 32) * scale, (screen.height - MapCompass.PADDING) * scale) == 0,
                                    "the compass stays inside its ten-pixel corner inset");
                            if (scale == 1) reference[index] = pixels;
                            else require(Arrays.equals(reference[index], pixels), "heading and pixel art are independent of GUI scale");
                        }
                    }
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    MapCompass.render(graphics, screen.width, screen.height, headings[4], 1, 2, -1);
                    try (var image = new NativeImage(target.width, target.height, false)) {
                        RenderSystem.bindTexture(target.getColorTextureId());
                        image.downloadTexture(0, false);
                        image.flipY();
                        for (int y = 0; y < MapCompass.SIZE; y++) for (int x = 0; x < MapCompass.SIZE; x++)
                            for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++)
                                require(image.getPixelRGBA((left + 2 + x) * scale + dx, (top - 1 + y) * scale + dy) == reference[4][y * MapCompass.SIZE + x],
                                        "compass parallax preserves the needle and dial's pixel grid at every GUI scale");
                    }
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    MapCompass.render(graphics, screen.width, screen.height, headings[4], 1, 1f / scale, -1f / scale);
                    try (var image = new NativeImage(target.width, target.height, false)) {
                        RenderSystem.bindTexture(target.getColorTextureId());
                        image.downloadTexture(0, false);
                        image.flipY();
                        for (int y = 0; y < MapCompass.SIZE; y++) for (int x = 0; x < MapCompass.SIZE; x++)
                            for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++)
                                require(image.getPixelRGBA((left + x) * scale + 1 + dx, (top + y) * scale - 1 + dy) == reference[4][y * MapCompass.SIZE + x],
                                        "compass parallax can move one physical pixel without jumping a whole GUI pixel");
                    }
                    verifyWood(graphics, target, screen.width, screen.height, scale);
                } finally {
                    target.destroyBuffers();
                    minecraft.getMainRenderTarget().bindWrite(true);
                }
            }
            require(reference[0][32 * MapCompass.SIZE + 12] >>> 24 != 0, "the supplied dial renders beneath the needle");
            heading(reference[0], 0, 1);
            heading(reference[1], -1, 0);
            heading(reference[2], 0, -1);
            heading(reference[3], 1, 0);
            heading(reference[4], -1, 1);
            require(Arrays.equals(reference[0], reference[7]), "heading wraps through a full turn without jumping");
            require(!Arrays.equals(reference[5], reference[6]), "the needle responds to intermediate headings");
            System.out.println("Map corner checks passed: original wood alpha and colors, 15-pixel overhang, compass layering, parallax pixel alignment and moving tooltip bounds");
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShaderTexture(0, texture0);
            RenderSystem.setShaderTexture(1, texture1);
            RenderSystem.setShader(() -> shader);
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
        }
    }

    private static void verifyWood(GuiGraphics graphics, TextureTarget target, int width, int height, int scale) {
        var texture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_wood_corner.png");
        try (var input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture).open();
             var source = NativeImage.read(input);
             var image = new NativeImage(target.width, target.height, false)) {
            require(source.getWidth() == 119 && source.getHeight() == 119, "wood retains its native dimensions");
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            new MapPages().renderCorner(graphics, width, height);
            graphics.flush();
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            int visible = MapPages.CORNER_SIZE - MapPages.CORNER_OFFSET;
            for (int y = 0; y < visible; y++) for (int x = 0; x < visible; x++) {
                int color = source.getPixelRGBA(x, y);
                if (color >>> 24 == 0) color = 0;
                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++)
                    require(image.getPixelRGBA((width - visible + x) * scale + dx, (height - visible + y) * scale + dy) == color,
                            "wood preserves its alpha and colors with exactly 15 pixels offscreen on both edges");
            }
            MapCompass.render(graphics, width, height, 0, 1);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            int hubX = width - MapCompass.PADDING - MapCompass.SIZE / 2;
            int hubY = height - MapCompass.PADDING - MapCompass.SIZE / 2;
            require(image.getPixelRGBA(hubX * scale, (hubY + 12) * scale) != source.getPixelRGBA(hubX - width + visible, hubY + 12 - height + visible),
                    "the compass needle renders above the wood backing");
            if (scale == 1) image.writeToFile(java.nio.file.Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-wood-compass.png"));
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }

    private static void heading(int[] pixels, int dx, int dy) {
        double x = 0, y = 0;
        int count = 0;
        for (int index = 0; index < pixels.length; index++) {
            int color = pixels[index];
            if ((color & 255) < 150 || (color >>> 8 & 255) > 60 || (color >>> 16 & 255) > 60) continue;
            x += index % MapCompass.SIZE + 0.5 - MapCompass.SIZE / 2.0;
            y += index / MapCompass.SIZE + 0.5 - MapCompass.SIZE / 2.0;
            count++;
        }
        require(count > 40, "the supplied red needle renders");
        x /= count;
        y /= count;
        require(dx == 0 ? Math.abs(x) < 2 : x * dx > 4, "the needle follows the player's east-west heading");
        require(dy == 0 ? Math.abs(y) < 2 : y * dy > 4, "the needle follows the player's north-south heading");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
