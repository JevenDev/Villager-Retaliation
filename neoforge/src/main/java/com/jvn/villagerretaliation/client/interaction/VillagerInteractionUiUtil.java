package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

final class VillagerInteractionUiUtil {
    private static final float SCROLL_VISIBILITY_THRESHOLD = 0.75F;

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
        if (maxScroll <= 0.0F) {
            return 1.0F;
        }

        boolean canScrollUp = currentScroll > SCROLL_VISIBILITY_THRESHOLD;
        boolean canScrollDown = currentScroll < maxScroll - SCROLL_VISIBILITY_THRESHOLD;
        float topFade = canScrollUp ? Mth.clamp((elementBottom - viewportTop) / fadeBand, 0.0F, 1.0F) : 1.0F;
        float bottomFade = canScrollDown ? Mth.clamp((viewportBottom - elementTop) / fadeBand, 0.0F, 1.0F) : 1.0F;
        return Math.min(topFade, bottomFade);
    }

    static ScrollbarThumb buildScrollbarThumb(
            int viewportTop,
            int viewportHeight,
            int scrollbarLeft,
            int scrollbarWidth,
            int scrollbarHitWidth,
            int minimumThumbHeight,
            float currentScroll,
            float maxScroll,
            float contentHeight) {
        if (maxScroll <= 0.0F || contentHeight <= 0.0F) {
            return null;
        }

        int scrollbarRight = scrollbarLeft + scrollbarWidth;
        int thumbHeight = Math.max(minimumThumbHeight, Mth.floor(viewportHeight * (viewportHeight / contentHeight)));
        float trackTravel = Math.max(0.0F, viewportHeight - thumbHeight);
        float scrollRatio = currentScroll / maxScroll;
        int thumbTop = viewportTop + Mth.floor(trackTravel * scrollRatio);
        int hitLeft = scrollbarLeft - (scrollbarHitWidth - scrollbarWidth) / 2;
        int hitRight = hitLeft + scrollbarHitWidth;
        return new ScrollbarThumb(
                scrollbarLeft,
                scrollbarRight,
                hitLeft,
                hitRight,
                thumbTop,
                thumbTop + thumbHeight,
                viewportTop,
                trackTravel
        );
    }

    static int withAlpha(int color, float alphaFactor) {
        return VillagerClientUiUtil.withAlphaFloor(color, alphaFactor);
    }

    static int scaledWrapWidth(int width, float scale) {
        return Math.max(1, Math.round(width / scale));
    }

    static int scaledLineStep(Font font, float scale) {
        return Math.round((font.lineHeight + 2) * scale);
    }

    static void drawScaledString(GuiGraphics graphics, Font font, String text, int left, int top, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    static void drawScaledString(GuiGraphics graphics, Font font, FormattedCharSequence text, int left, int top, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    record ScrollbarThumb(
            int left,
            int right,
            int hitLeft,
            int hitRight,
            int top,
            int bottom,
            int viewportTop,
            float trackTravel) {
        int height() {
            return this.bottom - this.top;
        }

        boolean contains(double mouseX, double mouseY) {
            return VillagerClientUiUtil.containsInclusive(mouseX, mouseY, this.hitLeft, this.top, this.hitRight, this.bottom);
        }
    }
}
