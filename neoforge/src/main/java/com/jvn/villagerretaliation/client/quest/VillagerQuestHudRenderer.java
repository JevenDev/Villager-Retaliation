package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer.ExperimentalNotificationPanel;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer.ShaderRect;
import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

final class VillagerQuestHudRenderer {
    private static final int PANEL_GAP = 4;
    private static final int NOTIFICATION_HEIGHT = 50;
    private static final int NOTIFICATION_SCROLL_TOP_HOLD_TICKS = 14;
    private static final int NOTIFICATION_SCROLL_TICKS = 48;
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
        int textWidth = Math.max(
                VillagerClientUiUtil.scaledTextWidth(font, entry.title(), textScale()),
                Math.max(
                        VillagerClientUiUtil.scaledTextWidth(font, entry.objective(), textScale()),
                        VillagerClientUiUtil.scaledTextWidth(font, entry.status(), textScale())));
        return Math.max(
                VillagerAdaptiveGuiScale.unit(144),
                Math.min(VillagerAdaptiveGuiScale.unit(244), textWidth + paddingX() * 2 + VillagerAdaptiveGuiScale.unit(4)));
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
            float alpha,
            int age) {
        int height = notificationHeight();
        graphics.fill(x, y, x + width, y + height, VillagerClientUiUtil.withAlphaRound(BACKGROUND_COLOR, alpha * 0.82F));
        graphics.fill(x, y, x + VillagerAdaptiveGuiScale.unitAtLeast(4, 1), y + height, VillagerClientUiUtil.withAlphaRound(VillagerQuestUi.ACCENT_COLOR, alpha));
        graphics.fill(x + VillagerAdaptiveGuiScale.unitAtLeast(4, 1), y, x + width, y + VillagerAdaptiveGuiScale.unitAtLeast(1, 1), VillagerClientUiUtil.withAlphaRound(VillagerQuestUi.EDGE_HIGHLIGHT_COLOR, alpha * 0.72F));

        int contentLeft = x + paddingX();
        int contentWidth = width - paddingX() * 2;
        int lineStep = lineStep(font);
        int progressReservedHeight = entry.showProgress() ? VillagerAdaptiveGuiScale.unitAtLeast(7, 4) : 0;
        int viewportTop = y + paddingY();
        int viewportBottom = Math.max(viewportTop + lineStep, y + height - paddingY() - progressReservedHeight);
        int viewportHeight = viewportBottom - viewportTop;
        List<NotificationLine> lines = buildNotificationLines(font, entry, contentWidth, lineStep);
        int contentHeight = notificationContentHeight(lines, lineStep);
        int overflow = Math.max(0, contentHeight - viewportHeight);
        int scroll = notificationScroll(age, overflow);

        graphics.enableScissor(contentLeft, viewportTop, x + width - paddingX(), viewportBottom);
        for (NotificationLine line : lines) {
            int lineTop = viewportTop + line.top() - scroll;
            int lineBottom = lineTop + lineStep;
            if (lineBottom < viewportTop || lineTop > viewportBottom) {
                continue;
            }
            float fadeFactor = 1.0F;
            if (overflow > 0) {
                fadeFactor = Mth.clamp((lineBottom - viewportTop) / (float) Math.max(1, lineStep), 0.0F, 1.0F)
                        * Mth.clamp((viewportBottom - lineTop) / (float) Math.max(1, lineStep), 0.0F, 1.0F);
            }
            int color = VillagerClientUiUtil.withAlphaRound(line.baseColor(), alpha * fadeFactor);
            VillagerClientUiUtil.drawScaledStringAtZ(graphics, font, line.text(), contentLeft, lineTop, color, line.shadow(), textScale(), TEXT_Z);
        }
        graphics.disableScissor();
        if (entry.showProgress()) {
            int barLeft = contentLeft;
            int barRight = x + width - paddingX();
            int barTop = y + height - VillagerAdaptiveGuiScale.unit(4);
            VillagerQuestUi.renderProgressBar(graphics, barLeft, barTop, barRight, VillagerAdaptiveGuiScale.unitAtLeast(2, 1), entry.progress(), alpha, 0, false, false);
        }
    }

    private static List<NotificationLine> buildNotificationLines(
            Font font,
            QuestTrackerSyncPayload.Entry entry,
            int contentWidth,
            int lineStep) {
        List<NotificationLine> lines = new ArrayList<>();
        int wrapWidth = VillagerClientUiUtil.scaledWrapWidth(contentWidth, textScale());
        int y = 0;
        y = addNotificationLines(lines, font, entry.title(), wrapWidth, VillagerQuestUi.TITLE_COLOR, true, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(3, 1));
        y = addNotificationLines(lines, font, entry.objective(), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, false, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(2, 1));
        if (!entry.status().isBlank()) {
            y = addNotificationLines(lines, font, entry.status(), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, false, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(2, 1));
        }
        if (!entry.questItems().isEmpty()) {
            y = addNotificationLines(lines, font, questItemsLine(entry), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, false, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(2, 1));
        }
        if (!entry.metadata().isBlank()) {
            addNotificationLines(lines, font, entry.metadata(), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, false, y, lineStep, 0);
        }
        return lines;
    }

    private static int addNotificationLines(
            List<NotificationLine> lines,
            Font font,
            String text,
            int wrapWidth,
            int baseColor,
            boolean shadow,
            int top,
            int lineStep,
            int gapAfter) {
        if (text == null || text.isBlank() || wrapWidth <= 0) {
            return top;
        }
        int y = top;
        for (FormattedCharSequence line : font.split(Component.literal(text), wrapWidth)) {
            lines.add(new NotificationLine(line, baseColor, shadow, y));
            y += lineStep;
        }
        return y + gapAfter;
    }

    private static int notificationContentHeight(List<NotificationLine> lines, int lineStep) {
        if (lines.isEmpty()) {
            return 0;
        }
        NotificationLine lastLine = lines.get(lines.size() - 1);
        return lastLine.top() + lineStep;
    }

    private static int notificationScroll(int age, int overflow) {
        if (overflow <= 0) {
            return 0;
        }
        float progress = Mth.clamp(
                (age - NOTIFICATION_SCROLL_TOP_HOLD_TICKS) / (float) NOTIFICATION_SCROLL_TICKS,
                0.0F,
                1.0F);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        return Math.round(overflow * eased);
    }

    private static String questItemsLine(QuestTrackerSyncPayload.Entry entry) {
        List<String> names = new ArrayList<>();
        for (QuestTrackerSyncPayload.QuestItem item : entry.questItems()) {
            names.add(item.count() > 1 ? item.label() + " x" + item.count() : item.label());
        }
        return "Quest item: " + String.join(", ", names);
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

    private record NotificationLine(FormattedCharSequence text, int baseColor, boolean shadow, int top) {
    }
}
