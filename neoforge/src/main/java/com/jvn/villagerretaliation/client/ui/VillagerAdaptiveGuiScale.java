package com.jvn.villagerretaliation.client.ui;

import com.jvn.toucanlib.client.ToucanGuiScale;

public final class VillagerAdaptiveGuiScale {
    private static final double BASE_GUI_SCALE = 3.0D;

    private VillagerAdaptiveGuiScale() {
    }

    public static float scaleFactor() {
        return ToucanGuiScale.scaleFactor(BASE_GUI_SCALE);
    }

    public static int unit(int guiScaleThreeValue) {
        return ToucanGuiScale.unit(guiScaleThreeValue, BASE_GUI_SCALE);
    }

    public static int unitAtLeast(int guiScaleThreeValue, int minimum) {
        return ToucanGuiScale.unitAtLeast(guiScaleThreeValue, minimum, BASE_GUI_SCALE);
    }

}
