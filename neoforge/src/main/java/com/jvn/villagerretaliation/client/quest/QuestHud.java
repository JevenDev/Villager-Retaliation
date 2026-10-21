package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.client.ui.VillagerNineSlice;
import com.jvn.villagerretaliation.quest.tracking.QuestJournalData;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class QuestHud {
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
    private final QuestPresentation presentation;
    private final VillagerNineSlice OPTIONS_LIST_BACKGROUND_NINE_SLICE;

    public QuestHud(QuestPresentation presentation) {
        this.presentation = java.util.Objects.requireNonNull(presentation);
        this.OPTIONS_LIST_BACKGROUND_NINE_SLICE =
            new VillagerNineSlice(
                    this.presentation.texture(QuestPresentation.Texture.HUD_PANEL),
                    49,
                    23,
                    8,
                    8,
                    8,
                    8);
    }

    public int trackerWidth(int screenWidth) {
        return Math.max(
                VillagerAdaptiveGuiScale.unit(176),
                Math.min(
                        Math.max(VillagerAdaptiveGuiScale.unit(212), screenWidth / 4),
                        screenWidth - VillagerAdaptiveGuiScale.unit(24)));
    }

    public int visibleTrackerEntryCount(boolean showRecentQuests, int entryCount) {
        return showRecentQuests ? Math.min(this.presentation.maxHudEntries(), entryCount) : 1;
    }

    public int trackerHeight(Font font, List<QuestJournalData.Entry> entries, int entryCount, int width) {
        int height = primaryHeight();
        for (int index = 1; index < entryCount; index++) {
            height += secondaryHeight(font, entries.get(index), width) + panelGap();
        }
        return height;
    }

    public int notificationWidth(Font font, QuestJournalData.Entry entry, int screenWidth) {
        return trackerWidth(screenWidth);
    }

    public int panelGap() {
        return VillagerAdaptiveGuiScale.unitAtLeast(PANEL_GAP, 1);
    }

    public int notificationHeight(Font font, QuestJournalData.Entry entry, int width, int screenHeight) {
        return Math.min(primaryHeight(), Math.max(VillagerAdaptiveGuiScale.unit(NOTIFICATION_MIN_HEIGHT), screenHeight - VillagerAdaptiveGuiScale.unit(20)));
    }

    public int primaryHeight() {
        return VillagerAdaptiveGuiScale.unit(PRIMARY_HEIGHT);
    }

    public int secondaryHeight(Font font, QuestJournalData.Entry entry, int width) {
        int height = VillagerAdaptiveGuiScale.unit(SECONDARY_HEIGHT);
        if (!entry.showProgress() || entry.objective().isBlank()) {
            return height;
        }
        int contentWidth = width - paddingX() * 2;
        int wrapWidth = VillagerClientUiUtil.scaledWrapWidth(contentWidth, textScale());
        int objectiveLines = Math.min(
                2,
                font.split(Component.literal(entry.objective()), wrapWidth).size());
        return height + Math.max(0, objectiveLines - 1) * lineStep(font);
    }

    public int slideDistance() {
        return VillagerAdaptiveGuiScale.unit(SLIDE_DISTANCE);
    }

    public void renderNotification(
            GuiGraphics graphics,
            Font font,
            QuestJournalData.Entry entry,
            int x,
            int y,
            int width,
            int height,
            float alpha,
            int age) {
        renderEntry(graphics, font, entry, x, y, width, height, alpha, true, age);
    }

    private int addNotificationLines(
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

    private int notificationContentHeight(List<NotificationLine> lines, int lineStep) {
        if (lines.isEmpty()) {
            return 0;
        }
        NotificationLine lastLine = lines.get(lines.size() - 1);
        return lastLine.top() + lineStep;
    }

    private int trackerScroll(int age, int overflow) {
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

    private float smoothProgress(float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private String questItemsLine(QuestJournalData.Entry entry) {
        List<String> names = new ArrayList<>();
        for (QuestJournalData.QuestItem item : entry.questItems()) {
            names.add(questItemProgressLine(item));
        }
        return this.presentation.text("quest_item_marker", String.join(", ", names)).getString();
    }

    private String questItemProgressLine(QuestJournalData.QuestItem item) {
        int current = Math.max(0, Math.min(item.count(), item.currentCount()));
        return item.label() + " " + current + "/" + item.count();
    }

    public void renderEntry(
            GuiGraphics graphics,
            Font font,
            QuestJournalData.Entry entry,
            int x,
            int y,
            int width,
            int height,
            float alpha,
            boolean primary,
            int age) {
        int titleColor = VillagerClientUiUtil.withAlphaRound(primary ? this.presentation.color(QuestPresentation.Color.HUD_TITLE_COLOR) : this.presentation.color(QuestPresentation.Color.HUD_TEXT_COLOR), alpha);
        int textColor = VillagerClientUiUtil.withAlphaRound(primary ? this.presentation.color(QuestPresentation.Color.HUD_TEXT_COLOR) : this.presentation.color(QuestPresentation.Color.HUD_MUTED_TEXT_COLOR), alpha);
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
            renderProgressBar(graphics, barLeft, progressBarTop(y, height), barRight, progressBarHeight(), entry.progress(), alpha, true);
        }
    }

    private void renderPrimaryEntryContent(
            GuiGraphics graphics,
            Font font,
            QuestJournalData.Entry entry,
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
        int titleColor = VillagerClientUiUtil.withAlphaRound(this.presentation.color(QuestPresentation.Color.HUD_TITLE_COLOR), alpha);
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
            renderProgressBar(graphics, barLeft, barTop, barRight, progressBarHeight(), entry.progress(), alpha, true);
        }
    }

    private List<NotificationLine> buildTrackerBodyLines(
            Font font,
            QuestJournalData.Entry entry,
            int wrapWidth,
            int lineStep) {
        List<NotificationLine> lines = new ArrayList<>();
        int y = addNotificationLines(lines, font, entry.objective(), wrapWidth, this.presentation.color(QuestPresentation.Color.HUD_TEXT_COLOR), false, 0, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(5, 3));
        if (!entry.metadata().isBlank()) {
            for (String part : visibleMetadataParts(entry)) {
                y = addNotificationLines(lines, font, part, wrapWidth, this.presentation.color(QuestPresentation.Color.HUD_MUTED_TEXT_COLOR), false, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(2, 1));
            }
        }
        String runtime = runtimeJournalLine(entry);
        if (!runtime.isBlank()) {
            y = addNotificationLines(lines, font, runtime, wrapWidth, this.presentation.color(QuestPresentation.Color.HUD_MUTED_TEXT_COLOR), false, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(2, 1));
        }
        String waypoint = waypointLine(entry);
        if (!waypoint.isBlank()) {
            y = addNotificationLines(lines, font, waypoint, wrapWidth, this.presentation.color(QuestPresentation.Color.HUD_MUTED_TEXT_COLOR), false, y, lineStep, VillagerAdaptiveGuiScale.unitAtLeast(2, 1));
        }
        if (!entry.questItems().isEmpty()) {
            addNotificationLines(lines, font, questItemsLine(entry), wrapWidth, this.presentation.color(QuestPresentation.Color.HUD_MUTED_TEXT_COLOR), false, y, lineStep, 0);
        }
        return lines;
    }

    private String runtimeJournalLine(QuestJournalData.Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        long expiresAt = entry.journal().expiresAtGameTime();
        if (expiresAt <= 0L || !"active".equalsIgnoreCase(entry.state())) {
            return "";
        }
        return this.presentation.text("quest_journal.expires", formatDuration(Math.max(0L, expiresAt - now))).getString();
    }

    private String waypointLine(QuestJournalData.Entry entry) {
        QuestJournalData.Waypoint waypoint = entry.journal().waypoint();
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
        return this.presentation.text("quest_journal.waypoint",
                waypoint.x(), waypoint.y(), waypoint.z(), waypoint.dimension(), distance).getString();
    }

    private String formatDuration(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + remainder + "s";
        return remainder + "s";
    }

    private List<String> visibleMetadataParts(QuestJournalData.Entry entry) {
        if (entry.metadata().isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(entry.metadata().split("\\s+\\|\\s+"))
                .filter(part -> !"active".equalsIgnoreCase(part.trim()))
                .toList();
    }

    private void renderPanelBackground(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            float alpha) {
        OPTIONS_LIST_BACKGROUND_NINE_SLICE.render(graphics, x, y, width, height, alpha);
    }

    private void renderWrappedText(
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

    private void renderSingleLine(
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

    private int paddingX() {
        return VillagerAdaptiveGuiScale.unit(PADDING_X);
    }

    private int paddingY() {
        return VillagerAdaptiveGuiScale.unit(PADDING_Y);
    }

    private int progressBarTop(int y, int height) {
        return y + height - paddingY() - progressBarHeight();
    }

    private int progressBarHeight() {
        return VillagerAdaptiveGuiScale.unitAtLeast(2, 1);
    }

    private int lineStep(Font font) {
        return Math.max(VillagerAdaptiveGuiScale.unitAtLeast(LINE_STEP, 1), VillagerClientUiUtil.scaledLineStep(font, textScale()));
    }

    private float textScale() {
        return VillagerAdaptiveGuiScale.scaleFactor();
    }

    private record NotificationLine(FormattedCharSequence text, int baseColor, boolean shadow, int top) {
    }
    private void renderProgressBar(
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
        graphics.fill(left, top, right, bottom, VillagerClientUiUtil.withAlphaRound(this.presentation.color(QuestPresentation.Color.HUD_BAR_BACKGROUND_COLOR), alpha));
        int fillRight = left + Math.round((right - left) * Mth.clamp(progress, 0.0F, 1.0F));
        if (fillRight > left) {
            graphics.fill(left, top, fillRight, bottom, VillagerClientUiUtil.withAlphaRound(this.presentation.color(QuestPresentation.Color.HUD_ACCENT_COLOR), alpha));
        }
        if (highlight) {
            int highlightHeight = VillagerAdaptiveGuiScale.unitAtLeast(1, 1);
            graphics.fill(left, top - highlightHeight, right, top, VillagerClientUiUtil.withAlphaRound(this.presentation.color(QuestPresentation.Color.HUD_EDGE_HIGHLIGHT_COLOR), alpha * 0.35F));
        }
    }
}
