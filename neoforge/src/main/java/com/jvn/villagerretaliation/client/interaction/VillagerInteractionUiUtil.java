package com.jvn.villagerretaliation.client.interaction;

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
        int alpha = color >>> 24;
        int adjustedAlpha = Mth.clamp(Mth.floor(alpha * alphaFactor), 0, 255);
        return adjustedAlpha << 24 | color & 0x00FFFFFF;
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
            return mouseX >= this.hitLeft
                    && mouseX <= this.hitRight
                    && mouseY >= this.top
                    && mouseY <= this.bottom;
        }
    }
}
