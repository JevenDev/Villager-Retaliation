package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
        float[] headings = {0, 90, 180, -90, 45, 22.5F, 137.25F, 360};
        int[][] reference = new int[headings.length][];
        try {
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
        } finally {
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShaderTexture(0, texture0);
            RenderSystem.setShaderTexture(1, texture1);
            RenderSystem.setShader(() -> shader);
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
        }
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
