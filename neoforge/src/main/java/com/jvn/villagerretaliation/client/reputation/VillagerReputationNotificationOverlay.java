package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.config.ReputationChangeDisplayMode;
import com.jvn.villagerretaliation.config.ReputationChangeHudPosition;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationTierNoticePayload;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class VillagerReputationNotificationOverlay {
    private static final int MAX_ENTRIES = 5;
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 10;
    private static final int ENTRY_HEIGHT = 14;
    private static final int ENTRY_GAP = 4;
    private static final int TEXT_PADDING_X = 8;
    private static final int TEXT_PADDING_Y = 3;
    private static final int BACKGROUND_COLOR = 0xA0101010;
    private static final int STRIPE_COLOR = 0xCCECECEC;
    private static final int TEXT_COLOR = 0xFFF3F3F3;
    private static final int SHADOW_COLOR = 0xB0000000;
    private static final int ENTRY_LIFETIME_TICKS = 82;
    private static final int FADE_IN_TICKS = 8;
    private static final int FADE_OUT_TICKS = 14;
    private static final int SLIDE_DISTANCE = 6;
    private static final ArrayDeque<NotificationEntry> ACTIVE_ENTRIES = new ArrayDeque<>();
    private static final ArrayDeque<String> PENDING_TEXT = new ArrayDeque<>();

    private VillagerReputationNotificationOverlay() {
    }

    public static void accept(VillagerReputationTierNoticePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || payload.text() == null || payload.text().isBlank()) {
            return;
        }

        ReputationChangeDisplayMode displayMode = VillagerRetaliationConfig.REPUTATION_CHANGE_DISPLAY_MODE.get();
        if (displayMode.showsChatMessage()) {
            minecraft.player.displayClientMessage(
                    Component.literal(payload.text()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                    false
            );
            return;
        }
        if (!displayMode.showsHudNotification()) {
            return;
        }

        PENDING_TEXT.addLast(payload.text());
        promotePendingEntries();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }

        Iterator<NotificationEntry> iterator = ACTIVE_ENTRIES.iterator();
        while (iterator.hasNext()) {
            NotificationEntry entry = iterator.next();
            entry.age++;
            if (entry.age >= ENTRY_LIFETIME_TICKS) {
                iterator.remove();
            }
        }
        promotePendingEntries();
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || ACTIVE_ENTRIES.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        float partialTick = minecraft.isPaused() ? 0.0F : event.getPartialTick().getGameTimeDeltaPartialTick(true);
        int index = 0;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        ReputationChangeHudPosition position = VillagerRetaliationConfig.REPUTATION_CHANGE_HUD_POSITION.get();
        for (NotificationEntry entry : ACTIVE_ENTRIES) {
            float alpha = entry.alpha(partialTick);
            if (alpha <= 0.01F) {
                index++;
                continue;
            }

            String text = entry.text;
            int textWidth = font.width(text);
            int width = textWidth + TEXT_PADDING_X * 2 + 4;
            Anchor anchor = anchor(position, screenWidth, screenHeight, width);
            int horizontalSlide = Math.round((1.0F - alpha) * SLIDE_DISTANCE);
            int x = switch (position) {
                case TOP_LEFT, MID_LEFT -> anchor.x() - horizontalSlide;
                case TOP_RIGHT, MID_RIGHT -> anchor.x() + horizontalSlide;
                case MID_TOP -> anchor.x();
            };
            int y = anchor.y() + index * (ENTRY_HEIGHT + ENTRY_GAP);
            renderEntry(graphics, font, text, x, y, width, alpha);
            index++;
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        ACTIVE_ENTRIES.clear();
        PENDING_TEXT.clear();
    }

    private static void promotePendingEntries() {
        while (ACTIVE_ENTRIES.size() < MAX_ENTRIES && !PENDING_TEXT.isEmpty()) {
            ACTIVE_ENTRIES.addLast(new NotificationEntry(PENDING_TEXT.removeFirst()));
        }
    }

    private static Anchor anchor(ReputationChangeHudPosition position, int screenWidth, int screenHeight, int width) {
        int totalHeight = MAX_ENTRIES * ENTRY_HEIGHT + (MAX_ENTRIES - 1) * ENTRY_GAP;
        return switch (position) {
            case TOP_LEFT -> new Anchor(PADDING_X, PADDING_Y);
            case MID_LEFT -> new Anchor(PADDING_X, Math.max(PADDING_Y, (screenHeight - totalHeight) / 2));
            case MID_TOP -> new Anchor((screenWidth - width) / 2, PADDING_Y);
            case TOP_RIGHT -> new Anchor(screenWidth - width - PADDING_X, PADDING_Y);
            case MID_RIGHT -> new Anchor(screenWidth - width - PADDING_X, Math.max(PADDING_Y, (screenHeight - totalHeight) / 2));
        };
    }

    private static void renderEntry(GuiGraphics graphics, Font font, String text, int x, int y, int width, float alpha) {
        int background = withAlpha(BACKGROUND_COLOR, alpha);
        int stripe = withAlpha(STRIPE_COLOR, alpha);
        int shadow = withAlpha(SHADOW_COLOR, alpha);
        int textColor = withAlpha(TEXT_COLOR, alpha);

        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT, background);
        graphics.fill(x, y, x + 2, y + ENTRY_HEIGHT, stripe);
        graphics.fill(x, y + ENTRY_HEIGHT, x + width, y + ENTRY_HEIGHT + 1, shadow);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.drawString(font, text, x + TEXT_PADDING_X, y + TEXT_PADDING_Y, textColor, true);
        graphics.pose().popPose();
    }

    private static int withAlpha(int color, float alphaFactor) {
        int alphaChannel = color >>> 24;
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round(alphaChannel * alphaFactor)));
        return adjustedAlpha << 24 | (color & 0x00FFFFFF);
    }

    private record Anchor(int x, int y) {
    }

    private static final class NotificationEntry {
        private final String text;
        private int age;

        private NotificationEntry(String text) {
            this.text = text;
        }

        private float alpha(float partialTick) {
            float progress = this.age + partialTick;
            if (progress < FADE_IN_TICKS) {
                return progress / FADE_IN_TICKS;
            }
            float fadeOutStart = ENTRY_LIFETIME_TICKS - FADE_OUT_TICKS;
            if (progress > fadeOutStart) {
                return Math.max(0.0F, (ENTRY_LIFETIME_TICKS - progress) / FADE_OUT_TICKS);
            }
            return 1.0F;
        }
    }
}
