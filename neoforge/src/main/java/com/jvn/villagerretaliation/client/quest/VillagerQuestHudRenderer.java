package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer.ExperimentalNotificationPanel;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer.ShaderRect;
import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class VillagerQuestHudRenderer {
    private static final int PANEL_GAP = 4;
    private static final int NOTIFICATION_HEIGHT = 26;
    private static final int PRIMARY_HEIGHT = 76;
    private static final int SECONDARY_HEIGHT = 44;
    private static final int SLIDE_DISTANCE = 8;

    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 7;
    private static final int LINE_STEP = 10;
    private static final float QUEST_PANEL_SLANT = 0.0F;
    private static final int BACKGROUND_COLOR = 0xC00B0D12;
    private static final int TEXT_Z = 210;

    private VillagerQuestHudRenderer() {
    }

    static int trackerWidth(int screenWidth) {
        return Math.max(
                VillagerAdaptiveGuiScale.unit(176),
                Math.min(
                        Math.max(VillagerAdaptiveGuiScale.unit(204), screenWidth / 4),
                        screenWidth - VillagerAdaptiveGuiScale.unit(24)));
    }

    static int visibleTrackerEntryCount(boolean showRecentQuests, int entryCount) {
        return showRecentQuests ? Math.min(QuestTrackerSyncPayload.MAX_TRACKER_ENTRIES, entryCount) : 1;
    }

    static int trackerHeight(int entryCount) {
        return primaryHeight() + Math.max(0, entryCount - 1) * (secondaryHeight() + panelGap());
    }

    static int notificationWidth(Font font, QuestTrackerSyncPayload.Entry entry) {
        int textWidth = VillagerClientUiUtil.scaledTextWidth(font, entry.title(), textScale());
        return Math.max(
                VillagerAdaptiveGuiScale.unit(112),
                Math.min(VillagerAdaptiveGuiScale.unit(204), textWidth + paddingX() * 2 + VillagerAdaptiveGuiScale.unit(4)));
    }

    static int panelGap() {
        return VillagerAdaptiveGuiScale.unitAtLeast(PANEL_GAP, 1);
    }

    static int notificationHeight() {
        return VillagerAdaptiveGuiScale.unit(NOTIFICATION_HEIGHT);
    }

    static int primaryHeight() {
        return VillagerAdaptiveGuiScale.unit(PRIMARY_HEIGHT);
    }

    static int secondaryHeight() {
        return VillagerAdaptiveGuiScale.unit(SECONDARY_HEIGHT);
    }

    static int slideDistance() {
        return VillagerAdaptiveGuiScale.unit(SLIDE_DISTANCE);
    }

    static void renderNotification(
            GuiGraphics graphics,
            Font font,
            QuestTrackerSyncPayload.Entry entry,
            int x,
            int y,
            int width,
            float alpha) {
        int height = notificationHeight();
        graphics.fill(x, y, x + width, y + height, VillagerClientUiUtil.withAlphaRound(BACKGROUND_COLOR, alpha * 0.82F));
        graphics.fill(x, y, x + VillagerAdaptiveGuiScale.unitAtLeast(4, 1), y + height, VillagerClientUiUtil.withAlphaRound(VillagerQuestUi.ACCENT_COLOR, alpha));
        graphics.fill(x + VillagerAdaptiveGuiScale.unitAtLeast(4, 1), y, x + width, y + VillagerAdaptiveGuiScale.unitAtLeast(1, 1), VillagerClientUiUtil.withAlphaRound(VillagerQuestUi.EDGE_HIGHLIGHT_COLOR, alpha * 0.72F));

        int contentLeft = x + paddingX();
        int titleColor = VillagerClientUiUtil.withAlphaRound(VillagerQuestUi.TEXT_COLOR, alpha);
        renderSingleLine(graphics, font, entry.title(), contentLeft, y + VillagerAdaptiveGuiScale.unit(6), width - paddingX() * 2, titleColor, true);
        if (entry.showProgress()) {
            int barLeft = contentLeft;
            int barRight = x + width - paddingX();
            int barTop = y + height - VillagerAdaptiveGuiScale.unit(4);
            VillagerQuestUi.renderProgressBar(graphics, barLeft, barTop, barRight, VillagerAdaptiveGuiScale.unitAtLeast(2, 1), entry.progress(), alpha, 0, false, false);
        }
    }

    static void renderEntry(
            GuiGraphics graphics,
            Font font,
            QuestTrackerSyncPayload.Entry entry,
            int x,
            int y,
            int width,
            int height,
            float alpha,
            boolean primary,
            int age) {
        int titleColor = VillagerClientUiUtil.withAlphaRound(primary ? VillagerQuestUi.TITLE_COLOR : VillagerQuestUi.TEXT_COLOR, alpha);
        int textColor = VillagerClientUiUtil.withAlphaRound(primary ? VillagerQuestUi.TEXT_COLOR : VillagerQuestUi.MUTED_TEXT_COLOR, alpha);
        int metadataColor = VillagerClientUiUtil.withAlphaRound(VillagerQuestUi.MUTED_TEXT_COLOR, alpha * 0.88F);
        renderPanelChrome(graphics, x, y, width, height, alpha, primary, age);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int contentLeft = x + paddingX();
        int contentWidth = width - paddingX() * 2;
        int cursorY = y + paddingY();
        renderSingleLine(graphics, font, entry.title(), contentLeft, cursorY, contentWidth, titleColor, true);
        cursorY += VillagerAdaptiveGuiScale.unit(13);

        int objectiveLines = primary ? 3 : 2;
        renderWrappedText(graphics, font, entry.objective(), contentLeft, cursorY, contentWidth, objectiveLines, textColor);

        if (primary && !entry.metadata().isBlank()) {
            renderSingleLine(graphics, font, entry.metadata(), contentLeft, y + height - VillagerAdaptiveGuiScale.unit(25), contentWidth, metadataColor, false);
        }
        if (entry.showProgress()) {
            int barLeft = contentLeft;
            int barRight = x + width - paddingX();
            int barTop = y + height - VillagerAdaptiveGuiScale.unit(6);
            VillagerQuestUi.renderProgressBar(graphics, barLeft, barTop, barRight, VillagerAdaptiveGuiScale.unitAtLeast(2, 1), entry.progress(), alpha, age, false, true);
        }
    }

    private static void renderPanelChrome(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            float alpha,
            boolean primary,
            int age) {
        VillagerInteractionScreenShaderRenderer.renderExperimentalNotification(
                graphics,
                new ExperimentalNotificationPanel(
                        new ShaderRect(x, y, x + width, y + height),
                        VillagerQuestUi.ACCENT_COLOR,
                        alpha * (primary ? 0.98F : 0.72F),
                        age,
                        1.0F,
                        QUEST_PANEL_SLANT));
    }

    private static void renderWrappedText(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int maxLines,
            int color) {
        VillagerClientUiUtil.drawWrappedScaledLinesAtZ(graphics, font, text, x, y, width, maxLines, lineStep(font), color, false, textScale(), TEXT_Z);
    }

    private static void renderSingleLine(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int color,
            boolean shadow) {
        VillagerClientUiUtil.drawClippedScaledStringAtZ(graphics, font, text, x, y, width, lineStep(font), color, shadow, textScale(), TEXT_Z);
    }

    private static int paddingX() {
        return VillagerAdaptiveGuiScale.unit(PADDING_X);
    }

    private static int paddingY() {
        return VillagerAdaptiveGuiScale.unit(PADDING_Y);
    }

    private static int lineStep(Font font) {
        return Math.max(VillagerAdaptiveGuiScale.unitAtLeast(LINE_STEP, 1), VillagerClientUiUtil.scaledLineStep(font, textScale()));
    }

    private static float textScale() {
        return VillagerAdaptiveGuiScale.scaleFactor();
    }
}
