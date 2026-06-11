package com.jvn.villagerretaliation.client.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

public final class VillagerAdaptiveGuiScale {
    private static final double BASE_GUI_SCALE = 3.0D;
    private static final int REFERENCE_GUI_WIDTH = 640;
    private static final int REFERENCE_GUI_HEIGHT = 360;
    private static final float MIN_SCALE = 0.35F;

    private VillagerAdaptiveGuiScale() {
    }

    public static float scaleFactor() {
        Window window = Minecraft.getInstance().getWindow();
        if (window == null) {
            return 1.0F;
        }
        return scaleFactor(window.getGuiScaledWidth(), window.getGuiScaledHeight(), window.getGuiScale());
    }

    public static float scaleFactor(int guiWidth, int guiHeight) {
        Window window = Minecraft.getInstance().getWindow();
        double guiScale = window == null ? BASE_GUI_SCALE : window.getGuiScale();
        return scaleFactor(guiWidth, guiHeight, guiScale);
    }

    public static float scaleFactor(int guiWidth, int guiHeight, double guiScale) {
        if (guiWidth <= 0 || guiHeight <= 0) {
            return 1.0F;
        }
        float baseScale = guiScale <= 0.0D ? 1.0F : (float) (BASE_GUI_SCALE / guiScale);
        float widthScale = guiWidth / (float) REFERENCE_GUI_WIDTH;
        float heightScale = guiHeight / (float) REFERENCE_GUI_HEIGHT;
        float fitScale = Math.min(widthScale, heightScale);
        return Math.max(MIN_SCALE, Math.min(baseScale, fitScale));
    }

    public static int unit(int guiScaleThreeValue) {
        return Math.round(guiScaleThreeValue * scaleFactor());
    }

    public static int unitAtLeast(int guiScaleThreeValue, int minimum) {
        return Math.max(minimum, unit(guiScaleThreeValue));
    }

}
