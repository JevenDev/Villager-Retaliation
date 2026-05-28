package com.jvn.villagerretaliation.client.ui;

import com.jvn.toucanlib.client.ToucanColors;
import com.jvn.toucanlib.client.ToucanScreenRects;
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
        return ToucanColors.multiplyAlpha(color, alphaFactor);
    }

    public static boolean containsInclusive(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public static boolean containsExclusive(double x, double y, int left, int top, int right, int bottom) {
        return ToucanScreenRects.contains(x, y, left, top, right - left, bottom - top);
    }
}
