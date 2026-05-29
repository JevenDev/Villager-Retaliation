package com.jvn.villagerretaliation.client.quest;

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
    private static final int FLASH_LIFETIME_TICKS = 160;
    private static final int PANEL_GAP = 5;
    private static final int PRIMARY_HEIGHT = 58;
    private static final int SECONDARY_HEIGHT = 38;
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 6;
    private static final int LINE_STEP = 10;
    private static final int BACKGROUND_COLOR = 0xB0101218;
    private static final int SECONDARY_BACKGROUND_COLOR = 0x8C101218;
    private static final int ACCENT_COLOR = 0xFFFFD166;
    private static final int TITLE_COLOR = 0xFFF6E9B8;
    private static final int TEXT_COLOR = 0xFFE7EBF0;
    private static final int MUTED_TEXT_COLOR = 0xFFB6C0CC;
    private static final int BAR_BACKGROUND_COLOR = 0x80373A42;

    private static List<QuestTrackerSyncPayload.Entry> entries = List.of();
    private static int flashTicks;
    private static int age;
    private static float alpha;
    private static boolean manualVisible;

    private VillagerQuestTrackerOverlay() {
    }

    public static void accept(QuestTrackerSyncPayload payload) {
        entries = payload.entries();
        if (payload.flash() && !entries.isEmpty()) {
            flashTicks = FLASH_LIFETIME_TICKS;
        }
        if (entries.isEmpty()) {
            flashTicks = 0;
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            reset();
            return;
        }
        while (VillagerQuestKeyMappings.TOGGLE_TRACKER.consumeClick()) {
            manualVisible = !manualVisible;
        }
        if (!minecraft.isPaused()) {
            age++;
            if (flashTicks > 0) {
                flashTicks--;
            }
            boolean targetVisible = !entries.isEmpty() && (manualVisible || flashTicks > 0);
            float delta = targetVisible ? 0.12F : -0.12F;
            alpha = Mth.clamp(alpha + delta, 0.0F, 1.0F);
        }
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName()) || entries.isEmpty() || alpha <= 0.01F) {
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
        int width = Math.max(116, Math.min(Math.max(150, screenWidth / 4), screenWidth - 24));
        int count = Math.min(QuestTrackerSyncPayload.MAX_ENTRIES, entries.size());
        int totalHeight = PRIMARY_HEIGHT + (count - 1) * (SECONDARY_HEIGHT + PANEL_GAP);
        int x = 12;
        int y = Math.max(10, (screenHeight - totalHeight) / 2);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int index = 0; index < count; index++) {
            QuestTrackerSyncPayload.Entry entry = entries.get(index);
            boolean primary = index == 0;
            int height = primary ? PRIMARY_HEIGHT : SECONDARY_HEIGHT;
            float entryAlpha = alpha * (primary ? 1.0F : 0.72F);
            renderEntry(graphics, font, entry, x, y, width, height, entryAlpha, primary, age + index * 13);
            y += height + PANEL_GAP;
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        entries = List.of();
        flashTicks = 0;
        age = 0;
        alpha = 0.0F;
        manualVisible = false;
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
        int background = VillagerClientUiUtil.withAlphaRound(primary ? BACKGROUND_COLOR : SECONDARY_BACKGROUND_COLOR, alpha);
        int accent = VillagerClientUiUtil.withAlphaRound(ACCENT_COLOR, alpha);
        int titleColor = VillagerClientUiUtil.withAlphaRound(primary ? TITLE_COLOR : TEXT_COLOR, alpha);
        int textColor = VillagerClientUiUtil.withAlphaRound(primary ? TEXT_COLOR : MUTED_TEXT_COLOR, alpha);
        int metadataColor = VillagerClientUiUtil.withAlphaRound(MUTED_TEXT_COLOR, alpha * 0.88F);

        graphics.fill(x, y, x + width, y + height, background);
        graphics.fill(x, y, x + 2, y + height, accent);
        graphics.fill(x + 2, y, x + width, y + 1, VillagerClientUiUtil.withAlphaRound(0x28FFFFFF, alpha));

        int contentLeft = x + PADDING_X;
        int contentWidth = width - PADDING_X * 2;
        int cursorY = y + PADDING_Y;
        int titleViewport = primary ? 12 : 10;
        renderScrollingText(graphics, font, entry.title(), contentLeft, cursorY, contentWidth, titleViewport, titleColor, age, true);
        cursorY += primary ? 15 : 12;

        int objectiveViewport = primary ? 20 : 12;
        renderScrollingText(graphics, font, entry.objective(), contentLeft, cursorY, contentWidth, objectiveViewport, textColor, age + 23, false);

        if (primary && !entry.metadata().isBlank()) {
            renderScrollingText(graphics, font, entry.metadata(), contentLeft, y + height - 18, contentWidth, 9, metadataColor, age + 41, false);
        }
        if (entry.showProgress()) {
            int barLeft = contentLeft;
            int barRight = x + width - PADDING_X;
            int barTop = y + height - 6;
            graphics.fill(barLeft, barTop, barRight, barTop + 2, VillagerClientUiUtil.withAlphaRound(BAR_BACKGROUND_COLOR, alpha));
            graphics.fill(barLeft, barTop, barLeft + Math.round((barRight - barLeft) * entry.progress()), barTop + 2, accent);
        }
    }

    private static void renderScrollingText(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int viewportHeight,
            int color,
            int age,
            boolean shadow) {
        if (text == null || text.isBlank() || width <= 0 || viewportHeight <= 0) {
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        int contentHeight = lines.size() * LINE_STEP;
        float offset = scrollingOffset(contentHeight, viewportHeight, age);

        graphics.enableScissor(x, y, x + width, y + viewportHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 210.0F);
        for (int index = 0; index < lines.size(); index++) {
            int lineY = Math.round(y + index * LINE_STEP - offset);
            if (lineY > y - LINE_STEP && lineY < y + viewportHeight) {
                graphics.drawString(font, lines.get(index), x, lineY, color, shadow);
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private static float scrollingOffset(int contentHeight, int viewportHeight, int age) {
        int maxOffset = Math.max(0, contentHeight - viewportHeight);
        if (maxOffset <= 0) {
            return 0.0F;
        }
        int pauseTicks = 32;
        int travelTicks = Math.max(32, maxOffset * 5);
        int cycleTicks = pauseTicks * 2 + travelTicks * 2;
        int tick = Math.floorMod(age, cycleTicks);
        if (tick < pauseTicks) {
            return 0.0F;
        }
        tick -= pauseTicks;
        if (tick < travelTicks) {
            return maxOffset * smooth((float) tick / travelTicks);
        }
        tick -= travelTicks;
        if (tick < pauseTicks) {
            return maxOffset;
        }
        tick -= pauseTicks;
        return maxOffset * (1.0F - smooth((float) tick / travelTicks));
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
