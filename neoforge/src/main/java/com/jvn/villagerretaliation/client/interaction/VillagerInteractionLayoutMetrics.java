package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.skill.VillagerSkill;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;

final class VillagerInteractionLayoutMetrics {
    private static final int OPTION_WIDTH = 180;
    private static final int OPTION_HEIGHT = 18;
    private static final int OPTION_GAP = 0;
    private static final int OPTION_VIEWPORT_ROWS = 5;
    private static final int INFO_PANEL_ROWS = 7;
    private static final int OPTION_TEXT_INSET = 10;
    private static final int OPTION_SCROLLBAR_OFFSET = 2;
    private static final int OPTION_SCROLLBAR_WIDTH = 2;
    private static final int OPTION_SCROLLBAR_HIT_WIDTH = 10;
    private static final int TOP_BACK_BUTTON_GAP = 8;
    private static final int SCREEN_BOTTOM_MARGIN = 48;
    private static final int SKILLS_EDGE_MARGIN = 10;
    private static final int SKILLS_CONTAINER_PADDING_X = 8;
    private static final int SKILLS_CONTAINER_PADDING_Y = 6;
    private static final int PROFILE_SKILL_ROW_HEIGHT = 16;
    private static final int PROFILE_SKILL_ROW_GAP = 2;
    private static final int PROFILE_SKILL_COLUMNS = 2;

    private VillagerInteractionLayoutMetrics() {
    }

    static int focusCenterY(int screenHeight) {
        return Math.max(72, screenHeight - SCREEN_BOTTOM_MARGIN);
    }

    static int optionWidth(boolean experimentalUi) {
        return experimentalUi ? VillagerInteractionExperimentalLayout.unit(OPTION_WIDTH) : OPTION_WIDTH;
    }

    static int optionHeight(boolean experimentalUi) {
        return experimentalUi ? VillagerInteractionExperimentalLayout.unit(OPTION_HEIGHT) : OPTION_HEIGHT;
    }

    static int optionTextInset(boolean experimentalUi) {
        return experimentalUi ? VillagerInteractionExperimentalLayout.unit(OPTION_TEXT_INSET) : OPTION_TEXT_INSET;
    }

    static float optionTextYOffset(int optionHeight) {
        return optionHeight * (5.0F / 18.0F);
    }

    static int optionScrollbarOffset(boolean experimentalUi) {
        return experimentalUi ? VillagerInteractionExperimentalLayout.unit(OPTION_SCROLLBAR_OFFSET) : OPTION_SCROLLBAR_OFFSET;
    }

    static int optionScrollbarWidth(boolean experimentalUi) {
        return experimentalUi ? VillagerInteractionExperimentalLayout.unitAtLeast(OPTION_SCROLLBAR_WIDTH, 1) : OPTION_SCROLLBAR_WIDTH;
    }

    static int optionScrollbarHitWidth(boolean experimentalUi) {
        return experimentalUi ? VillagerInteractionExperimentalLayout.unitAtLeast(OPTION_SCROLLBAR_HIT_WIDTH, 1) : OPTION_SCROLLBAR_HIT_WIDTH;
    }

    static int topBackButtonGap(boolean experimentalUi) {
        return experimentalUi ? VillagerInteractionExperimentalLayout.unit(TOP_BACK_BUTTON_GAP) : TOP_BACK_BUTTON_GAP;
    }

    static int optionViewportHeight(boolean experimentalUi, int optionCount) {
        int visibleRows = Math.min(OPTION_VIEWPORT_ROWS, Math.max(1, optionCount));
        return visibleRows * optionHeight(experimentalUi) + Math.max(0, visibleRows - 1) * OPTION_GAP;
    }

    static int fullOptionViewportHeight(boolean experimentalUi) {
        return OPTION_VIEWPORT_ROWS * optionHeight(experimentalUi) + Math.max(0, OPTION_VIEWPORT_ROWS - 1) * OPTION_GAP;
    }

    static int rootOptionViewportHeight(boolean experimentalUi) {
        return INFO_PANEL_ROWS * optionHeight(experimentalUi) + Math.max(0, INFO_PANEL_ROWS - 1) * OPTION_GAP;
    }

    static float optionContentHeight(boolean experimentalUi, int optionCount) {
        if (optionCount <= 0) {
            return 0.0F;
        }
        return optionCount * optionHeight(experimentalUi) + Math.max(0, optionCount - 1) * OPTION_GAP;
    }

    static int optionStride(boolean experimentalUi) {
        return optionHeight(experimentalUi) + OPTION_GAP;
    }

    static float maxOptionScroll(float optionContentHeight, int optionViewportHeight) {
        return Math.max(0.0F, optionContentHeight - optionViewportHeight);
    }

    static int skillsPanelHeight(Font font) {
        int rows = (VillagerSkill.values().length + PROFILE_SKILL_COLUMNS - 1) / PROFILE_SKILL_COLUMNS;
        return font.lineHeight + 4
                + rows * PROFILE_SKILL_ROW_HEIGHT
                + Math.max(0, rows - 1) * PROFILE_SKILL_ROW_GAP;
    }

    static int skillsContainerHeight(int skillsPanelHeight) {
        return skillsPanelHeight + SKILLS_CONTAINER_PADDING_Y * 2;
    }

    static int skillsPanelTop(int screenHeight, int skillsContainerHeight) {
        return Mth.clamp(
                SKILLS_EDGE_MARGIN,
                SKILLS_EDGE_MARGIN,
                Math.max(SKILLS_EDGE_MARGIN, screenHeight - skillsContainerHeight - SKILLS_EDGE_MARGIN));
    }

    static int skillsPanelLeft(int screenWidth, int panelWidth, int targetLeft) {
        return Mth.clamp(
                targetLeft,
                SKILLS_EDGE_MARGIN + SKILLS_CONTAINER_PADDING_X,
                Math.max(SKILLS_EDGE_MARGIN + SKILLS_CONTAINER_PADDING_X, screenWidth - panelWidth - SKILLS_EDGE_MARGIN));
    }
}
