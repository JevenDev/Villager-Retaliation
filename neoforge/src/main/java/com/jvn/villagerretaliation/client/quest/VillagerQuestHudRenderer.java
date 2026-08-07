package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.client.ui.VillagerNineSlice;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

final class VillagerQuestHudRenderer {
    private static final int PANEL_GAP = 4;
    private static final int NOTIFICATION_MIN_HEIGHT = 70;
    private static final int TRACKER_SCROLL_HOLD_TICKS = 54;
    private static final int TRACKER_SCROLL_TICKS = 96;
    private static final int PRIMARY_HEIGHT = 90;
    private static final int SECONDARY_HEIGHT = 44;
    private static final int SLIDE_DISTANCE = 6;

    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 8;
    private static final int LINE_STEP = 10;
    private static final int TEXT_Z = 210;
    private static final VillagerNineSlice OPTIONS_LIST_BACKGROUND_NINE_SLICE =
            new VillagerNineSlice(
                    VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_TEXTURE,
                    49,
                    23,
                    8,
                    8,
                    8,
                    8);

    private VillagerQuestHudRenderer() {
    }

    static int trackerWidth(int screenWidth) {
        return Math.max(
                VillagerAdaptiveGuiScale.unit(176),
                Math.min(
                        Math.max(VillagerAdaptiveGuiScale.unit(212), screenWidth / 4),
                        screenWidth - VillagerAdaptiveGuiScale.unit(24)));
    }

    static int visibleTrackerEntryCount(boolean showRecentQuests, int entryCount) {
        return showRecentQuests ? Math.min(QuestTrackerSyncPayload.MAX_TRACKER_ENTRIES, entryCount) : 1;
    }

    static int trackerHeight(int entryCount) {
        return primaryHeight() + Math.max(0, entryCount - 1) * (secondaryHeight() + panelGap());
    }

    static int notificationWidth(Font font, QuestTrackerSyncPayload.Entry entry, int screenWidth) {
        return trackerWidth(screenWidth);
    }

    static int panelGap() {
        return VillagerAdaptiveGuiScale.unitAtLeast(PANEL_GAP, 1);
    }

    static int notificationHeight(Font font, QuestTrackerSyncPayload.Entry entry, int width, int screenHeight) {
        return Math.min(primaryHeight(), Math.max(VillagerAdaptiveGuiScale.unit(NOTIFICATION_MIN_HEIGHT), screenHeight - VillagerAdaptiveGuiScale.unit(20)));
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
            int height,
            float alpha,
            int age) {
        renderEntry(graphics, font, entry, x, y, width, height, alpha, true, age);
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

    private static int trackerScroll(int age, int overflow) {
        if (overflow <= 0) {
            return 0;
        }
        int cycle = TRACKER_SCROLL_HOLD_TICKS * 2 + TRACKER_SCROLL_TICKS * 2;
        int phase = Math.floorMod(age, cycle);
        if (phase < TRACKER_SCROLL_HOLD_TICKS) {
            return 0;
        }
        phase -= TRACKER_SCROLL_HOLD_TICKS;
        if (phase < TRACKER_SCROLL_TICKS) {
            return Math.round(overflow * smoothProgress(phase / (float) TRACKER_SCROLL_TICKS));
        }
        phase -= TRACKER_SCROLL_TICKS;
        if (phase < TRACKER_SCROLL_HOLD_TICKS) {
            return overflow;
        }
        phase -= TRACKER_SCROLL_HOLD_TICKS;
        return Math.round(overflow * (1.0F - smoothProgress(phase / (float) TRACKER_SCROLL_TICKS)));
    }

    private static float smoothProgress(float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static String questItemsLine(QuestTrackerSyncPayload.Entry entry) {
        List<String> names = new ArrayList<>();
        for (QuestTrackerSyncPayload.QuestItem item : entry.questItems()) {
            names.add(questItemProgressLine(item));
        }
        return Component.translatable("villagerretaliation.gui.quest_item_marker", String.join(", ", names)).getString();
    }

    private static String questItemProgressLine(QuestTrackerSyncPayload.QuestItem item) {
        int current = Math.max(0, Math.min(item.count(), item.currentCount()));
        return item.label() + " " + current + "/" + item.count();
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
        renderPanelBackground(graphics, x, y, width, height, alpha * (primary ? 1.0F : 0.72F));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (primary) {
            renderPrimaryEntryContent(graphics, font, entry, x, y, width, height, alpha, age);
            return;
        }

        int contentLeft = x + paddingX();
        int contentWidth = width - paddingX() * 2;
        int cursorY = y + paddingY();
        renderSingleLine(graphics, font, entry.title(), contentLeft, cursorY, contentWidth, titleColor, true);
        cursorY += VillagerAdaptiveGuiScale.unit(13);

        renderWrappedText(graphics, font, entry.objective(), contentLeft, cursorY, contentWidth, 2, textColor);
        if (entry.showProgress()) {
            int barLeft = contentLeft;
            int barRight = x + width - paddingX();
            VillagerQuestUi.renderProgressBar(graphics, barLeft, progressBarTop(y, height), barRight, progressBarHeight(), entry.progress(), alpha, true);
        }
    }

    private static void renderPrimaryEntryContent(
            GuiGraphics graphics,
            Font font,
            QuestTrackerSyncPayload.Entry entry,
            int x,
            int y,
            int width,
            int height,
            float alpha,
            int age) {
        int contentLeft = x + paddingX();
        int contentWidth = width - paddingX() * 2;
        int lineStep = lineStep(font);
        int barTop = entry.showProgress()
                ? progressBarTop(y, height)
                : y + height - paddingY();
        int titleTop = y + paddingY();
        int titleColor = VillagerClientUiUtil.withAlphaRound(VillagerQuestUi.TITLE_COLOR, alpha);
        renderSingleLine(graphics, font, entry.title(), contentLeft, titleTop, contentWidth, titleColor, true);

        int wrapWidth = VillagerClientUiUtil.scaledWrapWidth(contentWidth, textScale());
        int viewportTop = titleTop + lineStep + VillagerAdaptiveGuiScale.unitAtLeast(4, 2);
        int viewportBottom = Math.max(viewportTop + lineStep, barTop - VillagerAdaptiveGuiScale.unitAtLeast(7, 4));
        int viewportHeight = viewportBottom - viewportTop;
        List<NotificationLine> lines = buildTrackerBodyLines(font, entry, wrapWidth, lineStep);
        int contentHeight = notificationContentHeight(lines, lineStep);
        int overflow = Math.max(0, contentHeight - viewportHeight);
        int contentTopOffset = overflow > 0 ? 0 : Math.max(0, Math.min(lineStep, (viewportHeight - contentHeight) / 2));
        int scroll = trackerScroll(age, overflow);

        graphics.enableScissor(contentLeft, viewportTop, x + width - paddingX(), viewportBottom);
        for (NotificationLine line : lines) {
            int lineTop = viewportTop + contentTopOffset + line.top() - scroll;
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
            VillagerQuestUi.renderProgressBar(graphics, barLeft, barTop, barRight, progressBarHeight(), entry.progress(), alpha, true);
        }
    }

    private static List<NotificationLine> buildTrackerBodyLines(
            Font font,
            QuestTrackerSyncPayload.Entry entry,
            int wrapWidth,
            int lineStep) {
        List<NotificationLine> lines = new ArrayList<>();
        int y = addNotificationLines(lines, font, entry.objective(), wrapWidth, VillagerQuestUi.TEXT_COLOR, false, 0, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(5, 3));
        if (!entry.metadata().isBlank()) {
            for (String part : visibleMetadataParts(entry)) {
                y = addNotificationLines(lines, font, part, wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, false, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(2, 1));
            }
        }
        String runtime = runtimeJournalLine(entry);
        if (!runtime.isBlank()) {
            y = addNotificationLines(lines, font, runtime, wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, false, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(2, 1));
        }
        String waypoint = waypointLine(entry);
        if (!waypoint.isBlank()) {
            y = addNotificationLines(lines, font, waypoint, wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, false, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(2, 1));
        }
        if (!entry.questItems().isEmpty()) {
            addNotificationLines(lines, font, questItemsLine(entry), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, false, y, lineStep, 0);
        }
        return lines;
    }

    private static String runtimeJournalLine(QuestTrackerSyncPayload.Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        long expiresAt = entry.journal().expiresAtGameTime();
        if (expiresAt <= 0L || !"active".equalsIgnoreCase(entry.state())) {
            return "";
        }
        return Component.translatable("villagerretaliation.gui.quest_journal.expires", formatDuration(Math.max(0L, expiresAt - now))).getString();
    }

    private static String waypointLine(QuestTrackerSyncPayload.Entry entry) {
        QuestTrackerSyncPayload.Waypoint waypoint = entry.journal().waypoint();
        if (!waypoint.present()) {
            return "";
        }
        Minecraft minecraft = Minecraft.getInstance();
        String distance = "";
        if (minecraft.player != null && minecraft.level != null
                && minecraft.level.dimension().location().toString().equals(waypoint.dimension())) {
            int dx = waypoint.x() - minecraft.player.blockPosition().getX();
            int dz = waypoint.z() - minecraft.player.blockPosition().getZ();
            distance = " • " + Math.round(Math.sqrt((double) dx * dx + (double) dz * dz)) + " blocks";
        }
        return Component.translatable("villagerretaliation.gui.quest_journal.waypoint",
                waypoint.x(), waypoint.y(), waypoint.z(), waypoint.dimension(), distance).getString();
    }

    private static String formatDuration(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + remainder + "s";
        return remainder + "s";
    }

    private static List<String> visibleMetadataParts(QuestTrackerSyncPayload.Entry entry) {
        if (entry.metadata().isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(entry.metadata().split("\\s+\\|\\s+"))
                .filter(part -> !"active".equalsIgnoreCase(part.trim()))
                .toList();
    }

    private static void renderPanelBackground(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            float alpha) {
        OPTIONS_LIST_BACKGROUND_NINE_SLICE.render(graphics, x, y, width, height, alpha);
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

    private static int progressBarTop(int y, int height) {
        return y + height - paddingY() - progressBarHeight();
    }

    private static int progressBarHeight() {
        return VillagerAdaptiveGuiScale.unitAtLeast(2, 1);
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
