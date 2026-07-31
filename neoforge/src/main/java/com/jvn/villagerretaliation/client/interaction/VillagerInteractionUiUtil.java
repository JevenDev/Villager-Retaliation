package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanColors;
import com.jvn.toucanlib.client.ToucanGuiText;
import com.jvn.toucanlib.client.ToucanScrollbarThumb;
import com.jvn.toucanlib.client.ToucanScrollbars;
import com.jvn.toucanlib.client.tooltip.ToucanTooltips;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;

final class VillagerInteractionUiUtil {
    private VillagerInteractionUiUtil() {
    }

    static float edgeFadeAlpha(
            float currentScroll,
            float maxScroll,
            float elementTop,
            float elementBottom,
            int viewportTop,
            int viewportBottom,
            float fadeBand) {
        return ToucanScrollbars.edgeFadeAlpha(
                currentScroll,
                maxScroll,
                elementTop,
                elementBottom,
                viewportTop,
                viewportBottom,
                fadeBand);
    }

    static ToucanScrollbarThumb buildScrollbarThumb(
            int viewportTop,
            int viewportHeight,
            int scrollbarLeft,
            int scrollbarWidth,
            int scrollbarHitWidth,
            int minimumThumbHeight,
            float currentScroll,
            float maxScroll,
            float contentHeight) {
        return ToucanScrollbars.buildThumb(
                viewportTop,
                viewportHeight,
                scrollbarLeft,
                scrollbarWidth,
                scrollbarHitWidth,
                minimumThumbHeight,
                currentScroll,
                maxScroll,
                contentHeight);
    }

    static int withAlpha(int color, float alphaFactor) {
        return ToucanColors.multiplyAlpha(color, alphaFactor);
    }

    static int scaledWrapWidth(int width, float scale) {
        return ToucanGuiText.scaledWrapWidth(width, scale);
    }

    static int scaledLineStep(Font font, float scale) {
        return ToucanGuiText.scaledLineStep(font, scale);
    }

    static void drawScaledString(GuiGraphics graphics, Font font, String text, int left, int top, int color, float scale) {
        ToucanGuiText.drawScaledString(graphics, font, text, left, top, color, scale);
    }


    static void renderScaledComponentTooltip(GuiGraphics graphics, Font font, List<Component> tooltip, int mouseX, int mouseY, float scale) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        renderBoundedComponentTooltipInCurrentPose(
                graphics,
                font,
                tooltip,
                Mth.floor(mouseX / Math.max(scale, 0.001F)),
                Mth.floor(mouseY / Math.max(scale, 0.001F)),
                scale,
                0.0F,
                0.0F);
        graphics.pose().popPose();
    }

    static void renderBoundedComponentTooltipInCurrentPose(
            GuiGraphics graphics,
            Font font,
            List<Component> tooltip,
            int mouseX,
            int mouseY,
            float scale,
            float originX,
            float originY) {
        ToucanTooltips.renderBounded(graphics, font, tooltip, mouseX, mouseY, scale, originX, originY);
    }



}
