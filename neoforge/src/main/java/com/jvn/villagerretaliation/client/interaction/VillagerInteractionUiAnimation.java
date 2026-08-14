package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import net.minecraft.Util;
import net.minecraft.util.Mth;

final class VillagerInteractionUiAnimation {
    private static final float TEXT_FADE_IN_DURATION_MILLIS = 220.0F;
    private static final float CONTENT_FADE_IN_DURATION_MILLIS = 180.0F;
    private static final float TEXT_ALPHA_DRAW_THRESHOLD = 0.04F;

    private static long animationStartMillis = -1L;
    private static long contentAnimationStartMillis = -1L;

    private VillagerInteractionUiAnimation() {
    }

    static void resetAnimation() {
        long now = Util.getMillis();
        animationStartMillis = now;
        contentAnimationStartMillis = now;
    }

    static void resetContentAnimation() {
        contentAnimationStartMillis = Util.getMillis();
    }

    static void completeAnimation() {
        long now = Util.getMillis();
        animationStartMillis = now - (long) TEXT_FADE_IN_DURATION_MILLIS;
        contentAnimationStartMillis = now - (long) CONTENT_FADE_IN_DURATION_MILLIS;
    }

    private static float normalizedProgress(float elapsedMillis, float delayMillis, float durationMillis) {
        return Mth.clamp((elapsedMillis - delayMillis) / durationMillis, 0.0F, 1.0F);
    }

    static float textEntranceProgress(float delayMillis, float durationMillis) {
        long now = Util.getMillis();
        if (animationStartMillis < 0L) {
            animationStartMillis = now;
        }
        return normalizedProgress(now - animationStartMillis, delayMillis, durationMillis);
    }

    static float textFadeInAlpha() {
        return VillagerClientUiUtil.smoothstep(textEntranceProgress(0.0F, TEXT_FADE_IN_DURATION_MILLIS));
    }

    static float uiAlpha() {
        return textFadeInAlpha() * contentFadeInAlpha();
    }

    private static float contentFadeInAlpha() {
        long now = Util.getMillis();
        if (contentAnimationStartMillis < 0L) {
            contentAnimationStartMillis = now;
        }
        float progress = normalizedProgress(
                now - contentAnimationStartMillis,
                0.0F,
                CONTENT_FADE_IN_DURATION_MILLIS);
        return VillagerClientUiUtil.smoothstep(progress);
    }

    static boolean shouldDrawText(float alpha) {
        return alpha > TEXT_ALPHA_DRAW_THRESHOLD;
    }
}
