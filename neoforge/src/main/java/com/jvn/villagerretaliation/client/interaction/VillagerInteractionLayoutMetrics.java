package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanScrollState;
import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
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
    private static final int SKILLS_EDGE_MARGIN = 4;
    private static final int SKILLS_BOTTOM_MARGIN = 5;
    private static final int SKILLS_CONTAINER_WIDTH = 431;
    private static final int SKILLS_CONTAINER_HEIGHT = 139;
    private static final int SKILLS_CONTAINER_PADDING_X = 8;
    private static final int SKILLS_CONTAINER_PADDING_Y = 6;
    private static final int PROFILE_SKILL_ROW_HEIGHT = 16;
    private static final int PROFILE_SKILL_ROW_GAP = 2;
    private static final int PROFILE_SKILL_BAR_HEIGHT = 4;
    private static final int PROFILE_SKILL_COLUMN_GAP = 8;
    private static final int PROFILE_SKILL_COLUMNS = 2;

    private VillagerInteractionLayoutMetrics() {
    }

    static int focusCenterY(int screenHeight) {
        return Math.max(72, screenHeight - SCREEN_BOTTOM_MARGIN);
    }

    static int optionWidth() {
        return VillagerInteractionExperimentalLayout.unit(OPTION_WIDTH);
    }

    static int optionHeight() {
        return VillagerInteractionExperimentalLayout.unit(OPTION_HEIGHT);
    }

    static int optionTextInset() {
        return VillagerInteractionExperimentalLayout.unit(OPTION_TEXT_INSET);
    }

    static float optionTextYOffset(int optionHeight) {
        return optionHeight * (5.0F / 18.0F);
    }

    static int optionScrollbarOffset() {
        return VillagerInteractionExperimentalLayout.unit(OPTION_SCROLLBAR_OFFSET);
    }

    static int optionScrollbarWidth() {
        return VillagerInteractionExperimentalLayout.unitAtLeast(OPTION_SCROLLBAR_WIDTH, 1);
    }

    static int optionScrollbarHitWidth() {
        return VillagerInteractionExperimentalLayout.unitAtLeast(OPTION_SCROLLBAR_HIT_WIDTH, 1);
    }

    static int topBackButtonGap() {
        return VillagerInteractionExperimentalLayout.unit(TOP_BACK_BUTTON_GAP);
    }

    static int optionViewportHeight(int optionCount) {
        return fullOptionViewportHeight();
    }

    static int fullOptionViewportHeight() {
        return OPTION_VIEWPORT_ROWS * optionHeight() + Math.max(0, OPTION_VIEWPORT_ROWS - 1) * OPTION_GAP;
    }

    static int rootOptionViewportHeight() {
        return INFO_PANEL_ROWS * optionHeight() + Math.max(0, INFO_PANEL_ROWS - 1) * OPTION_GAP;
    }

    static int optionStride() {
        return optionHeight() + OPTION_GAP;
    }

    static float maxOptionScroll(float optionContentHeight, int optionViewportHeight) {
        return ToucanScrollState.maxScroll(optionContentHeight, optionViewportHeight);
    }

    static int skillsContainerWidth() {
        return SKILLS_CONTAINER_WIDTH;
    }

    static int skillsPanelHeight(Font font) {
        return skillsContainerHeight();
    }

    static int skillsContainerPaddingX() {
        return VillagerAdaptiveGuiScale.unit(SKILLS_CONTAINER_PADDING_X);
    }

    static int skillsContainerPaddingY() {
        return VillagerAdaptiveGuiScale.unit(SKILLS_CONTAINER_PADDING_Y);
    }

    static int skillsContainerHeight(int skillsPanelHeight) {
        return skillsContainerHeight();
    }

    static int skillsContainerHeight() {
        return SKILLS_CONTAINER_HEIGHT;
    }

    static int skillsPanelTop(int screenHeight, int skillsContainerHeight) {
        int edgeMargin = SKILLS_EDGE_MARGIN;
        int bottomMargin = SKILLS_BOTTOM_MARGIN;
        return Mth.clamp(
                screenHeight - skillsContainerHeight - bottomMargin,
                edgeMargin,
                Math.max(edgeMargin, screenHeight - skillsContainerHeight - edgeMargin));
    }

    static int skillsPanelLeft(int screenWidth, int panelWidth, int targetLeft) {
        return skillsPanelLeft(screenWidth, panelWidth);
    }

    static int skillsPanelLeft(int screenWidth, int panelWidth) {
        int edgeMargin = SKILLS_EDGE_MARGIN;
        return Mth.clamp(
                (screenWidth - panelWidth) / 2,
                edgeMargin,
                Math.max(edgeMargin, screenWidth - panelWidth - edgeMargin));
    }

    static int profileSkillRowHeight() {
        return VillagerAdaptiveGuiScale.unit(PROFILE_SKILL_ROW_HEIGHT);
    }

    static int profileSkillRowGap() {
        return VillagerAdaptiveGuiScale.unitAtLeast(PROFILE_SKILL_ROW_GAP, 1) + 1;
    }

    static int profileSkillBarHeight() {
        return VillagerAdaptiveGuiScale.unitAtLeast(PROFILE_SKILL_BAR_HEIGHT, 1);
    }

    static int profileSkillColumnGap() {
        return VillagerAdaptiveGuiScale.unit(PROFILE_SKILL_COLUMN_GAP);
    }
}
