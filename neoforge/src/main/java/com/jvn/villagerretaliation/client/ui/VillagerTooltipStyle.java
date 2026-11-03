package com.jvn.villagerretaliation.client.ui;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import net.minecraft.client.gui.GuiGraphics;

public record VillagerTooltipStyle(VillagerNineSlice background, VillagerNineSlice border, int padding) {
    public static final VillagerTooltipStyle DEFAULT = new VillagerTooltipStyle(
            new VillagerNineSlice(VillagerRetaliationClientAssets.TOOLTIP_BACKGROUND_TEXTURE, 84, 84, 2, 2, 2, 2),
            new VillagerNineSlice(VillagerRetaliationClientAssets.TOOLTIP_BORDER_TEXTURE, 84, 84, 2, 2, 2, 2), 4);

    public VillagerTooltipStyle {
        java.util.Objects.requireNonNull(background);
        java.util.Objects.requireNonNull(border);
        if (padding < 0) throw new IllegalArgumentException("Tooltip padding must not be negative");
    }

    public void renderBackground(GuiGraphics graphics, int x, int y, int width, int height, int z) {
        if (width <= 0 || height <= 0) return;
        int left = x - this.padding;
        int top = y - this.padding;
        int outerWidth = width + this.padding * 2;
        int outerHeight = height + this.padding * 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, z);
        try {
            this.background.renderAtTextureScale(graphics, left, top, outerWidth, outerHeight);
            this.border.renderAtTextureScale(graphics, left, top, outerWidth, outerHeight);
        } finally {
            graphics.pose().popPose();
        }
    }
}
