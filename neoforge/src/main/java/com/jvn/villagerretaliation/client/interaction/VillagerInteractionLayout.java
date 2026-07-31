package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import net.minecraft.util.Mth;

final class VillagerInteractionLayout {
    private static final float OPTIONS_LEFT_RATIO = 0.7F;
    private static final float OPTIONS_TOP_RATIO = 0.9F;
    private static final float PAGE_LEFT_RATIO = 0.7F;
    private static final int SCROLLBAR_RIGHT_MARGIN = 36;
    private static final int SCREEN_EDGE_MARGIN = 8;
    private static final int MIN_SCREEN_MARGIN = 20;
    private static final int PAGE_RIGHT_MARGIN = 20;

    private VillagerInteractionLayout() {
    }

    static float scaleFactor() {
        return VillagerAdaptiveGuiScale.scaleFactor();
    }

    static int unit(int guiScaleThreeValue) {
        return VillagerAdaptiveGuiScale.unit(guiScaleThreeValue);
    }

    static int unitAtLeast(int guiScaleThreeValue, int minimum) {
        return VillagerAdaptiveGuiScale.unitAtLeast(guiScaleThreeValue, minimum);
    }

    static int optionsLeft(int screenWidth, int optionWidth) {
        int preferredLeft = Mth.floor(screenWidth * OPTIONS_LEFT_RATIO);
        int minLeft = unit(MIN_SCREEN_MARGIN);
        int maxLeft = Math.max(minLeft, screenWidth - optionWidth - unit(SCREEN_EDGE_MARGIN));
        return Mth.clamp(preferredLeft, minLeft, maxLeft);
    }

    static int optionsTop(int screenHeight, int viewportHeight) {
        int top = Mth.floor(screenHeight * OPTIONS_TOP_RATIO);
        int minTop = unit(24);
        int bottomMargin = unit(18);
        return Mth.clamp(top, minTop, Math.max(minTop, screenHeight - viewportHeight - bottomMargin));
    }

    static int pageLeft(int screenWidth, int optionWidth) {
        int preferredLeft = Mth.floor(screenWidth * PAGE_LEFT_RATIO);
        int minLeft = unit(MIN_SCREEN_MARGIN);
        int maxLeft = Math.max(minLeft, screenWidth - optionWidth - unit(PAGE_RIGHT_MARGIN));
        return Mth.clamp(preferredLeft, minLeft, maxLeft);
    }


    static int scrollbarLeft(int screenWidth, int optionsLeft, int optionWidth, int scrollbarOffset, int scrollbarWidth) {
        int minLeftMargin = unit(MIN_SCREEN_MARGIN);
        int maxLeft = Math.max(minLeftMargin, screenWidth - scrollbarWidth - unit(SCREEN_EDGE_MARGIN));
        int minLeft = Math.min(maxLeft, optionsLeft + optionWidth + scrollbarOffset);
        int preferredLeft = screenWidth - unit(SCROLLBAR_RIGHT_MARGIN);
        return Mth.clamp(preferredLeft, minLeft, maxLeft);
    }
}
