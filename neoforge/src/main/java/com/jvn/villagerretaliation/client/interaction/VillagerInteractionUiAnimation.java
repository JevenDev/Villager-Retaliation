package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanEasing;
import net.minecraft.Util;
import net.minecraft.util.Mth;

final class VillagerInteractionUiAnimation {
    private static final float TEXT_FADE_IN_DURATION_MILLIS = 320.0F;
    private static final float TEXT_ALPHA_DRAW_THRESHOLD = 0.04F;

    private static long animationStartMillis = -1L;

    private VillagerInteractionUiAnimation() {
    }

    static void resetAnimation() {
        animationStartMillis = Util.getMillis();
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
        return ToucanEasing.smoothstep(textEntranceProgress(0.0F, TEXT_FADE_IN_DURATION_MILLIS));
    }

    static float uiAlpha() {
        return textFadeInAlpha();
    }

    static boolean shouldDrawText(float alpha) {
        return alpha > TEXT_ALPHA_DRAW_THRESHOLD;
    }
}
