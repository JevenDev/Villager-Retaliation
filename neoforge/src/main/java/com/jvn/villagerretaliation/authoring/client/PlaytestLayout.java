package com.jvn.villagerretaliation.authoring.client;

public record PlaytestLayout(int width, int height, int scale, int screenWidth, int screenHeight) {
    public static PlaytestLayout calculate(int width, int height, int screenWidth, int screenHeight,
            float fraction, int requestedScale, boolean unicode) {
        int gameWidth = Math.max(1, Math.round(width * fraction));
        int scale = 1;
        while (scale != requestedScale && gameWidth / (scale + 1) >= 320 && height / (scale + 1) >= 240) scale++;
        if (unicode && scale % 2 != 0) scale++;
        return new PlaytestLayout(gameWidth, Math.max(1, height), scale,
                Math.max(1, Math.round(screenWidth * (float) gameWidth / Math.max(1, width))), Math.max(1, screenHeight));
    }

    public int guiWidth() { return (int) Math.ceil((double) width / scale); }
    public int guiHeight() { return (int) Math.ceil((double) height / scale); }

    public static float clampFraction(float fraction, float width, float textScale) {
        if (!Float.isFinite(fraction)) fraction = 2F / 3;
        float minimum = Math.min(.5F, 320 / Math.max(1, width));
        float maximum = Math.max(minimum, 1 - 360 * textScale / Math.max(1, width));
        return Math.clamp(fraction, minimum, maximum);
    }
}
