package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import net.minecraft.util.Mth;

final class VillagerInteractionExperimentalLayout {
    private static final float OPTIONS_LEFT_RATIO = 0.7F;
    private static final float OPTIONS_TOP_RATIO = 0.9F;
    private static final float PAGE_LEFT_RATIO = 0.7F;
    private static final float INFO_RIGHT_RATIO = 0.60F;
    private static final int SCROLLBAR_RIGHT_MARGIN = 36;
    private static final int SCREEN_EDGE_MARGIN = 8;
    private static final int MIN_SCREEN_MARGIN = 20;
    private static final int INFO_TO_OPTIONS_GAP = 28;
    private static final int PAGE_RIGHT_MARGIN = 20;

    private VillagerInteractionExperimentalLayout() {
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
        int maxLeft = Math.max(MIN_SCREEN_MARGIN, screenWidth - optionWidth - unit(SCREEN_EDGE_MARGIN));
        return Mth.clamp(preferredLeft, MIN_SCREEN_MARGIN, maxLeft);
    }

    static int optionsTop(int screenHeight, int viewportHeight) {
        int top = Mth.floor(screenHeight * OPTIONS_TOP_RATIO);
        return Mth.clamp(top, 24, Math.max(24, screenHeight - viewportHeight - 18));
    }

    static int pageLeft(int screenWidth, int optionWidth) {
        int preferredLeft = Mth.floor(screenWidth * PAGE_LEFT_RATIO);
        int maxLeft = Math.max(MIN_SCREEN_MARGIN, screenWidth - optionWidth - unit(PAGE_RIGHT_MARGIN));
        return Mth.clamp(preferredLeft, MIN_SCREEN_MARGIN, maxLeft);
    }

    static int infoRight(int screenWidth, int optionsLeft) {
        int preferredRight = Mth.floor(screenWidth * INFO_RIGHT_RATIO);
        int maxRight = Math.max(MIN_SCREEN_MARGIN, optionsLeft - INFO_TO_OPTIONS_GAP);
        return Mth.clamp(preferredRight, MIN_SCREEN_MARGIN, maxRight);
    }

    static int scrollbarLeft(int screenWidth, int optionsLeft, int optionWidth, int scrollbarOffset, int scrollbarWidth) {
        int maxLeft = Math.max(MIN_SCREEN_MARGIN, screenWidth - scrollbarWidth - unit(SCREEN_EDGE_MARGIN));
        int minLeft = Math.min(maxLeft, optionsLeft + optionWidth + scrollbarOffset);
        int preferredLeft = screenWidth - unit(SCROLLBAR_RIGHT_MARGIN);
        return Mth.clamp(preferredLeft, minLeft, maxLeft);
    }
}
