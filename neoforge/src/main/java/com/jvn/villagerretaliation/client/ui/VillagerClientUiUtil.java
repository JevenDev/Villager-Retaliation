package com.jvn.villagerretaliation.client.ui;

import net.minecraft.util.Mth;

public final class VillagerClientUiUtil {
    private VillagerClientUiUtil() {
    }

    public static int withAlphaFloor(int color, float alphaFactor) {
        int alpha = color >>> 24;
        int adjustedAlpha = Mth.clamp(Mth.floor(alpha * alphaFactor), 0, 255);
        return adjustedAlpha << 24 | color & 0x00FFFFFF;
    }

    public static int withAlphaRound(int color, float alphaFactor) {
        int alpha = color >>> 24;
        int adjustedAlpha = Mth.clamp(Math.round(alpha * alphaFactor), 0, 255);
        return adjustedAlpha << 24 | color & 0x00FFFFFF;
    }

    public static boolean containsInclusive(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public static boolean containsExclusive(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }
}
