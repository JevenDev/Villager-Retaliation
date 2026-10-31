package com.jvn.villagerretaliation.client.ui;

import com.jvn.toucanlib.client.ui.ToucanNineSlice;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public record VillagerNineSlice(
        ResourceLocation texture,
        int textureWidth,
        int textureHeight,
        int sliceLeft,
        int sliceRight,
        int sliceTop,
        int sliceBottom) {

    public void render(GuiGraphics graphics, int left, int top, int width, int height) {
        render(graphics, left, top, width, height, 1.0F);
    }

    public void render(GuiGraphics graphics, int left, int top, int width, int height, float alpha) {
        new ToucanNineSlice(texture, textureWidth, textureHeight, sliceLeft, sliceRight, sliceTop, sliceBottom)
                .render(graphics, left, top, width, height, alpha, VillagerAdaptiveGuiScale::unit);
    }

    public void renderAtTextureScale(GuiGraphics graphics, int left, int top, int width, int height) {
        new ToucanNineSlice(texture, textureWidth, textureHeight, sliceLeft, sliceRight, sliceTop, sliceBottom)
                .renderAtTextureScale(graphics, left, top, width, height);
    }

}
