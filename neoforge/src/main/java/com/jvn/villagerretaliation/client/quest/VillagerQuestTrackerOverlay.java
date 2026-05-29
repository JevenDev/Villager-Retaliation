package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer;
import com.jvn.villagerretaliation.client.interaction.VillagerQuestJournalScreen;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class VillagerQuestTrackerOverlay {
    private static final int FLASH_LIFETIME_TICKS = 96;
    private static final int PANEL_GAP = 4;
    private static final int NOTIFICATION_HEIGHT = 26;
    private static final int PRIMARY_HEIGHT = 76;
    private static final int SECONDARY_HEIGHT = 44;
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 7;
    private static final int LINE_STEP = 10;
    private static final int SLIDE_DISTANCE = 8;
    private static final float QUEST_PANEL_SLANT = 0.0F;
    private static final int BACKGROUND_COLOR = 0xC00B0D12;
    private static final int SECONDARY_BACKGROUND_COLOR = 0x9A0B0D12;
    private static final int ACCENT_COLOR = 0xFFFFD166;
    private static final int TITLE_COLOR = 0xFFFFF0C8;
    private static final int TEXT_COLOR = 0xFFE9EEF5;
    private static final int MUTED_TEXT_COLOR = 0xFFB8C3D0;
    private static final int BAR_BACKGROUND_COLOR = 0x80373A42;
    private static final int PANEL_SHADOW_COLOR = 0x8A000000;
    private static final int EDGE_HIGHLIGHT_COLOR = 0x2CFFFFFF;

    private static List<QuestTrackerSyncPayload.Entry> entries = List.of();
    private static int flashTicks;
    private static int age;
    private static float notificationAlpha;
    private static float trackerAlpha;
    private static boolean trackerVisible;

    private VillagerQuestTrackerOverlay() {
    }

    public static void accept(QuestTrackerSyncPayload payload) {
        entries = payload.entries();
        if (payload.flash() && !entries.isEmpty()) {
            flashTicks = FLASH_LIFETIME_TICKS;
        }
        if (entries.isEmpty()) {
            flashTicks = 0;
            if (Minecraft.getInstance().screen instanceof VillagerQuestJournalScreen) {
                Minecraft.getInstance().setScreen(null);
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            reset();
            return;
        }
        if (!minecraft.isPaused()) {
            updateKeyState();
            age++;
            if (flashTicks > 0) {
                flashTicks--;
            }
            boolean journalOpen = minecraft.screen instanceof VillagerQuestJournalScreen;
            boolean targetTrackerVisible = !entries.isEmpty() && (trackerVisible || journalOpen || flashTicks > 0);
            boolean targetNotificationVisible = !entries.isEmpty() && flashTicks > 0 && !targetTrackerVisible;
            trackerAlpha = approach(trackerAlpha, targetTrackerVisible);
            notificationAlpha = approach(notificationAlpha, targetNotificationVisible);
        }
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())
                || entries.isEmpty()
                || (notificationAlpha <= 0.01F && trackerAlpha <= 0.01F)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (notificationAlpha > 0.01F) {
            renderNotification(graphics, font, entries.get(0), screenHeight);
        }
        if (trackerAlpha <= 0.01F || minecraft.screen instanceof VillagerQuestJournalScreen) {
            return;
        }

        renderTrackerLayer(graphics, font, screenWidth, screenHeight, trackerAlpha, false, age);
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        entries = List.of();
        flashTicks = 0;
        age = 0;
        notificationAlpha = 0.0F;
        trackerAlpha = 0.0F;
        trackerVisible = false;
    }

    public static List<QuestTrackerSyncPayload.Entry> entries() {
        return entries;
    }

    public static void dismissJournalFlash() {
        flashTicks = 0;
        notificationAlpha = 0.0F;
        if (!trackerVisible) {
            trackerAlpha = 0.0F;
        }
    }

    public static void renderTrackerLayer(
            GuiGraphics graphics,
            Font font,
            int screenWidth,
            int screenHeight,
            float alpha,
            boolean showRecentQuests,
            int renderAge) {
        if (entries.isEmpty() || alpha <= 0.01F) {
            return;
        }
        int width = Math.max(176, Math.min(Math.max(204, screenWidth / 4), screenWidth - 24));
        int count = showRecentQuests ? Math.min(QuestTrackerSyncPayload.MAX_TRACKER_ENTRIES, entries.size()) : 1;
        int totalHeight = PRIMARY_HEIGHT + (count - 1) * (SECONDARY_HEIGHT + PANEL_GAP);
        int x = 12;
        int y = Math.max(10, (screenHeight - totalHeight) / 2);
        for (int index = 0; index < count; index++) {
            QuestTrackerSyncPayload.Entry entry = entries.get(index);
            boolean primary = index == 0;
            int height = primary ? PRIMARY_HEIGHT : SECONDARY_HEIGHT;
            float entryAlpha = alpha * (primary ? 1.0F : 0.76F);
            int slide = Math.round((1.0F - entryAlpha) * SLIDE_DISTANCE);
            renderEntry(graphics, font, entry, x - slide, y, width, height, entryAlpha, primary, renderAge + index * 13);
            y += height + PANEL_GAP;
        }
    }

    private static void updateKeyState() {
        while (VillagerQuestKeyMappings.OPEN_JOURNAL.consumeClick()) {
            openJournal();
        }
        while (VillagerQuestKeyMappings.TOGGLE_TRACKER.consumeClick()) {
            trackerVisible = !trackerVisible;
        }
    }

    private static void openJournal() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerQuestJournalScreen) {
            return;
        }
        if (!entries.isEmpty()) {
            minecraft.setScreen(new VillagerQuestJournalScreen());
        }
    }

    private static float approach(float value, boolean visible) {
        float delta = visible ? 0.16F : -0.16F;
        return Mth.clamp(value + delta, 0.0F, 1.0F);
    }

    private static void renderNotification(
            GuiGraphics graphics,
            Font font,
            QuestTrackerSyncPayload.Entry entry,
            int screenHeight) {
        int textWidth = font.width(entry.title());
        int width = Math.max(112, Math.min(204, textWidth + PADDING_X * 2 + 4));
        int x = 12 - Math.round((1.0F - notificationAlpha) * SLIDE_DISTANCE);
        int y = Math.max(10, screenHeight / 2 - NOTIFICATION_HEIGHT / 2);
        float alpha = notificationAlpha;

        graphics.fill(x, y, x + width, y + NOTIFICATION_HEIGHT, VillagerClientUiUtil.withAlphaRound(BACKGROUND_COLOR, alpha * 0.82F));
        graphics.fill(x, y, x + 4, y + NOTIFICATION_HEIGHT, VillagerClientUiUtil.withAlphaRound(ACCENT_COLOR, alpha));
        graphics.fill(x + 4, y, x + width, y + 1, VillagerClientUiUtil.withAlphaRound(EDGE_HIGHLIGHT_COLOR, alpha * 0.72F));

        int contentLeft = x + PADDING_X;
        int titleColor = VillagerClientUiUtil.withAlphaRound(TEXT_COLOR, alpha);
        renderSingleLine(graphics, font, entry.title(), contentLeft, y + 6, width - PADDING_X * 2, titleColor, true);
        if (entry.showProgress()) {
            int barLeft = contentLeft;
            int barRight = x + width - PADDING_X;
            int barTop = y + NOTIFICATION_HEIGHT - 4;
            graphics.fill(barLeft, barTop, barRight, barTop + 2, VillagerClientUiUtil.withAlphaRound(BAR_BACKGROUND_COLOR, alpha));
            graphics.fill(barLeft, barTop, barLeft + Math.round((barRight - barLeft) * entry.progress()), barTop + 2,
                    VillagerClientUiUtil.withAlphaRound(ACCENT_COLOR, alpha));
        }
    }

    private static void renderEntry(
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
        int accent = VillagerClientUiUtil.withAlphaRound(ACCENT_COLOR, alpha);
        int titleColor = VillagerClientUiUtil.withAlphaRound(primary ? TITLE_COLOR : TEXT_COLOR, alpha);
        int textColor = VillagerClientUiUtil.withAlphaRound(primary ? TEXT_COLOR : MUTED_TEXT_COLOR, alpha);
        int metadataColor = VillagerClientUiUtil.withAlphaRound(MUTED_TEXT_COLOR, alpha * 0.88F);
        renderPanelChrome(graphics, x, y, width, height, alpha, primary, age);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int contentLeft = x + PADDING_X;
        int contentWidth = width - PADDING_X * 2;
        int cursorY = y + PADDING_Y;
        renderSingleLine(graphics, font, entry.title(), contentLeft, cursorY, contentWidth, titleColor, true);
        cursorY += 13;

        int objectiveLines = primary ? 3 : 2;
        renderWrappedText(graphics, font, entry.objective(), contentLeft, cursorY, contentWidth, objectiveLines, textColor);

        if (primary && !entry.metadata().isBlank()) {
            renderSingleLine(graphics, font, entry.metadata(), contentLeft, y + height - 25, contentWidth, metadataColor, false);
        }
        if (entry.showProgress()) {
            int barLeft = contentLeft;
            int barRight = x + width - PADDING_X;
            int barTop = y + height - 6;
            graphics.fill(barLeft, barTop, barRight, barTop + 2, VillagerClientUiUtil.withAlphaRound(BAR_BACKGROUND_COLOR, alpha));
            graphics.fill(barLeft, barTop, barLeft + Math.round((barRight - barLeft) * entry.progress()), barTop + 2, accent);
            graphics.fill(barLeft, barTop - 1, barRight, barTop, VillagerClientUiUtil.withAlphaRound(EDGE_HIGHLIGHT_COLOR, alpha * 0.35F));
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
        boolean rendered = VillagerInteractionScreenShaderRenderer.renderExperimentalNotification(
                graphics,
                x,
                y,
                x + width,
                y + height,
                ACCENT_COLOR,
                alpha * (primary ? 0.98F : 0.72F),
                age,
                1.0F,
                QUEST_PANEL_SLANT);
        if (rendered) {
            return;
        }

        int background = VillagerClientUiUtil.withAlphaRound(primary ? BACKGROUND_COLOR : SECONDARY_BACKGROUND_COLOR, alpha);
        int accent = VillagerClientUiUtil.withAlphaRound(ACCENT_COLOR, alpha);
        graphics.fill(x, y, x + width, y + height, background);
        graphics.fill(x, y, x + 7, y + height, VillagerClientUiUtil.withAlphaRound(ACCENT_COLOR, alpha * 0.82F));
        graphics.fill(x + 7, y, x + width, y + 1, VillagerClientUiUtil.withAlphaRound(EDGE_HIGHLIGHT_COLOR, alpha));
        graphics.fill(x, y + height, x + width, y + height + 1, VillagerClientUiUtil.withAlphaRound(PANEL_SHADOW_COLOR, alpha));
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
        if (text == null || text.isBlank() || width <= 0 || maxLines <= 0) {
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 210.0F);
        int visibleLines = Math.min(maxLines, lines.size());
        for (int index = 0; index < visibleLines; index++) {
            graphics.drawString(font, lines.get(index), x, y + index * LINE_STEP, color, false);
        }
        graphics.pose().popPose();
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
        if (text == null || text.isBlank() || width <= 0) {
            return;
        }
        graphics.enableScissor(x, y, x + width, y + LINE_STEP);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 210.0F);
        graphics.drawString(font, text, x, y, color, shadow);
        graphics.pose().popPose();
        graphics.disableScissor();
    }
}
