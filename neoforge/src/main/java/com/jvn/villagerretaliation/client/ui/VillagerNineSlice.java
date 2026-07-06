package com.jvn.villagerretaliation.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
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
        if (width <= 0 || height <= 0 || alpha <= 0.0F) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        try {
            renderSliced(
                    graphics,
                    left,
                    top,
                    width,
                    height,
                    VillagerAdaptiveGuiScale.unit(this.sliceLeft),
                    VillagerAdaptiveGuiScale.unit(this.sliceRight),
                    VillagerAdaptiveGuiScale.unit(this.sliceTop),
                    VillagerAdaptiveGuiScale.unit(this.sliceBottom)
            );
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderSliced(
            GuiGraphics graphics,
            int left,
            int top,
            int width,
            int height,
            int destSliceLeft,
            int destSliceRight,
            int destSliceTop,
            int destSliceBottom) {
        int centerSourceWidth = this.textureWidth - this.sliceLeft - this.sliceRight;
        int centerSourceHeight = this.textureHeight - this.sliceTop - this.sliceBottom;
        int centerWidth = Math.max(0, width - destSliceLeft - destSliceRight);
        int centerHeight = Math.max(0, height - destSliceTop - destSliceBottom);

        blit(graphics, left, top, destSliceLeft, destSliceTop, 0, 0, this.sliceLeft, this.sliceTop);
        blit(graphics, left + destSliceLeft, top, centerWidth, destSliceTop, this.sliceLeft, 0, centerSourceWidth, this.sliceTop);
        blit(graphics, left + width - destSliceRight, top, destSliceRight, destSliceTop, this.textureWidth - this.sliceRight, 0, this.sliceRight, this.sliceTop);

        blit(graphics, left, top + destSliceTop, destSliceLeft, centerHeight, 0, this.sliceTop, this.sliceLeft, centerSourceHeight);
        blit(graphics, left + destSliceLeft, top + destSliceTop, centerWidth, centerHeight, this.sliceLeft, this.sliceTop, centerSourceWidth, centerSourceHeight);
        blit(graphics, left + width - destSliceRight, top + destSliceTop, destSliceRight, centerHeight, this.textureWidth - this.sliceRight, this.sliceTop, this.sliceRight, centerSourceHeight);

        blit(graphics, left, top + height - destSliceBottom, destSliceLeft, destSliceBottom, 0, this.textureHeight - this.sliceBottom, this.sliceLeft, this.sliceBottom);
        blit(graphics, left + destSliceLeft, top + height - destSliceBottom, centerWidth, destSliceBottom, this.sliceLeft, this.textureHeight - this.sliceBottom, centerSourceWidth, this.sliceBottom);
        blit(graphics, left + width - destSliceRight, top + height - destSliceBottom, destSliceRight, destSliceBottom, this.textureWidth - this.sliceRight, this.textureHeight - this.sliceBottom, this.sliceRight, this.sliceBottom);
    }

    private void blit(
            GuiGraphics graphics,
            int destLeft,
            int destTop,
            int destWidth,
            int destHeight,
            int sourceLeft,
            int sourceTop,
            int sourceWidth,
            int sourceHeight) {
        if (destWidth <= 0 || destHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        graphics.blit(
                this.texture,
                destLeft,
                destTop,
                destWidth,
                destHeight,
                (float) sourceLeft,
                (float) sourceTop,
                sourceWidth,
                sourceHeight,
                this.textureWidth,
                this.textureHeight
        );
    }
}
