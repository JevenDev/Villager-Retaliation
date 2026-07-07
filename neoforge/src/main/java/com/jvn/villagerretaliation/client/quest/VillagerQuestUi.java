package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class VillagerQuestUi {
    public static final int ACCENT_COLOR = 0xFFFFD166;
    public static final int TITLE_COLOR = 0xFFFFF0C8;
    public static final int TEXT_COLOR = 0xFFE9EEF5;
    public static final int MUTED_TEXT_COLOR = 0xFFB8C3D0;
    public static final int BAR_BACKGROUND_COLOR = 0x80373A42;
    public static final int EDGE_HIGHLIGHT_COLOR = 0x2CFFFFFF;

    private VillagerQuestUi() {
    }

    public static void renderProgressBar(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int height,
            float progress,
            float alpha,
            boolean highlight) {
        if (right <= left || height <= 0) {
            return;
        }

        int bottom = top + height;
        graphics.fill(left, top, right, bottom, VillagerClientUiUtil.withAlphaRound(BAR_BACKGROUND_COLOR, alpha));
        int fillRight = left + Math.round((right - left) * Mth.clamp(progress, 0.0F, 1.0F));
        if (fillRight > left) {
            graphics.fill(left, top, fillRight, bottom, VillagerClientUiUtil.withAlphaRound(ACCENT_COLOR, alpha));
        }
        if (highlight) {
            int highlightHeight = VillagerAdaptiveGuiScale.unitAtLeast(1, 1);
            graphics.fill(left, top - highlightHeight, right, top, VillagerClientUiUtil.withAlphaRound(EDGE_HIGHLIGHT_COLOR, alpha * 0.35F));
        }
    }
}
