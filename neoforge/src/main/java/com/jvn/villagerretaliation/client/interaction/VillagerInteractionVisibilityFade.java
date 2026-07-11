package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.Util;
import net.minecraft.util.Mth;

public final class VillagerInteractionVisibilityFade {
    private static final long DURATION_MILLIS = 260L;
    private static long transitionStartMillis = -1L;
    private static float settledAlpha = 1.0F;
    private static float startAlpha = 1.0F;
    private static float targetAlpha = 1.0F;

    private VillagerInteractionVisibilityFade() {
    }

    public static void fadeOut() {
        begin(false);
    }

    public static void fadeIn() {
        begin(true);
    }

    public static void reset() {
        transitionStartMillis = -1L;
        settledAlpha = 1.0F;
        startAlpha = 1.0F;
        targetAlpha = 1.0F;
    }

    public static float alpha() {
        if (transitionStartMillis < 0L) {
            return settledAlpha;
        }
        float progress = Mth.clamp((float) (Util.getMillis() - transitionStartMillis) / DURATION_MILLIS, 0.0F, 1.0F);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        if (progress >= 1.0F) {
            transitionStartMillis = -1L;
            settledAlpha = targetAlpha;
            return settledAlpha;
        }
        return Mth.lerp(eased, startAlpha, targetAlpha);
    }

    private static void begin(boolean targetVisible) {
        float currentAlpha = alpha();
        float nextTarget = targetVisible ? 1.0F : 0.0F;
        if (Math.abs(currentAlpha - nextTarget) <= 0.001F) {
            settledAlpha = nextTarget;
            transitionStartMillis = -1L;
            return;
        }
        startAlpha = currentAlpha;
        targetAlpha = nextTarget;
        transitionStartMillis = Util.getMillis();
    }
}
