package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

record MapHudSprite(ResourceLocation texture, int width, int height) {
    private static final Map<MapHudSprite, boolean[]> MASKS = new HashMap<>();

    MapHudSprite(String name, int width, int height) {
        this(VillagerRetaliation.id("textures/gui/" + name + ".png"), width, height);
    }

    static void clearMasks() { MASKS.clear(); }

    boolean contains(double x, double y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return false;
        return MASKS.computeIfAbsent(this, MapHudSprite::loadMask)[(int) x + (int) y * width];
    }

    void render(GuiGraphics graphics, float x, float y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, 0, 0, 0, 0, width, height, width, height);
        graphics.pose().popPose();
    }

    private static boolean[] loadMask(MapHudSprite sprite) {
        var mask = new boolean[sprite.width * sprite.height];
        try (var input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(sprite.texture).open();
             var image = NativeImage.read(input)) {
            for (int y = 0; y < sprite.height; y++) for (int x = 0; x < sprite.width; x++)
                mask[x + y * sprite.width] = image.getPixelRGBA(x * image.getWidth() / sprite.width,
                        y * image.getHeight() / sprite.height) >>> 24 != 0;
        } catch (IOException exception) { throw new UncheckedIOException("Failed to load map HUD sprite " + sprite.texture, exception); }
        return mask;
    }
}
