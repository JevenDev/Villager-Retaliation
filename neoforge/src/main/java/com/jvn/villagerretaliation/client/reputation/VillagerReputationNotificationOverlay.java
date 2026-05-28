package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.config.ReputationChangeDisplayMode;
import com.jvn.villagerretaliation.config.ReputationChangeHudPosition;
import com.jvn.villagerretaliation.config.ReputationChangeNotificationStyle;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
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
    private static final int EXPERIMENTAL_ENTRY_HEIGHT = 18;
    private static final int EXPERIMENTAL_TEXT_PADDING_X = 12;
    private static final int EXPERIMENTAL_TEXT_PADDING_Y = 5;
    private static final int EXPERIMENTAL_EXTRA_WIDTH = 20;
    private static final int TEXT_PADDING_X = 8;
    private static final int TEXT_PADDING_Y = 3;
    private static final int BACKGROUND_COLOR = 0xA0101010;
    private static final int STRIPE_COLOR = 0xCCECECEC;
    private static final int TEXT_COLOR = 0xFFF3F3F3;
    private static final int MAP_DISCOVERY_TEXT_COLOR = 0xFF55AAFF;
    private static final int RECEIVED_ITEM_TEXT_COLOR = 0xFF55FFFF;
    private static final int GIFT_LIKED_TEXT_COLOR = 0xFF55FF55;
    private static final int GIFT_NEUTRAL_TEXT_COLOR = 0xFFAAAAAA;
    private static final int GIFT_DISLIKED_TEXT_COLOR = 0xFFFF5555;
    private static final int VILLAGER_FOLLOWING_TEXT_COLOR = 0xFF55FFAA;
    private static final int VILLAGER_DISMISSED_TEXT_COLOR = 0xFFFFD166;
    private static final int VILLAGER_HIRED_TEXT_COLOR = 0xFF55AAFF;
    private static final int VILLAGER_FIRED_TEXT_COLOR = 0xFFFFAA55;
    private static final int VILLAGER_DEATH_TEXT_COLOR = 0xFFFF5555;
    private static final int SHADOW_COLOR = 0xB0000000;
    private static final int ENTRY_LIFETIME_TICKS = 82;
    private static final int FADE_IN_TICKS = 8;
    private static final int FADE_OUT_TICKS = 14;
    private static final int SLIDE_DISTANCE = 6;
    private static final ArrayDeque<NotificationEntry> ACTIVE_ENTRIES = new ArrayDeque<>();
    private static final ArrayDeque<PendingNotification> PENDING_NOTIFICATIONS = new ArrayDeque<>();

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
                    chatMessage(payload),
                    false
            );
            return;
        }
        if (!displayMode.showsHudNotification()) {
            return;
        }

        PENDING_NOTIFICATIONS.addLast(new PendingNotification(
                payload.text(),
                payload.kind(),
                payload.textColor(),
                payload.chatColor()
        ));
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
        ReputationChangeNotificationStyle style = VillagerRetaliationConfig.REPUTATION_CHANGE_NOTIFICATION_STYLE.get();
        int entryHeight = entryHeight(style);
        for (NotificationEntry entry : ACTIVE_ENTRIES) {
            float alpha = entry.alpha(partialTick);
            if (alpha <= 0.01F) {
                index++;
                continue;
            }

            String text = entry.text;
            int textWidth = font.width(text);
            int width = entryWidth(style, textWidth);
            Anchor anchor = anchor(position, screenWidth, screenHeight, width, entryHeight);
            int horizontalSlide = Math.round((1.0F - alpha) * SLIDE_DISTANCE);
            int x = switch (position) {
                case TOP_LEFT, MID_LEFT -> anchor.x() - horizontalSlide;
                case TOP_RIGHT, MID_RIGHT -> anchor.x() + horizontalSlide;
                case MID_TOP -> anchor.x();
            };
            int y = anchor.y() + index * (entryHeight + ENTRY_GAP);
            if (style.experimental()) {
                renderExperimentalEntry(graphics, font, entry, x, y, width, entryHeight, alpha, entry.age + partialTick, position);
            } else {
                renderEntry(graphics, font, entry, x, y, width, alpha);
            }
            index++;
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        ACTIVE_ENTRIES.clear();
        PENDING_NOTIFICATIONS.clear();
    }

    private static void promotePendingEntries() {
        while (ACTIVE_ENTRIES.size() < MAX_ENTRIES && !PENDING_NOTIFICATIONS.isEmpty()) {
            PendingNotification pending = PENDING_NOTIFICATIONS.removeFirst();
            ACTIVE_ENTRIES.addLast(new NotificationEntry(
                    pending.text(),
                    pending.kind(),
                    pending.textColor(),
                    pending.chatColor()
            ));
        }
    }

    private static int entryHeight(ReputationChangeNotificationStyle style) {
        return style.experimental() ? EXPERIMENTAL_ENTRY_HEIGHT : ENTRY_HEIGHT;
    }

    private static int entryWidth(ReputationChangeNotificationStyle style, int textWidth) {
        if (style.experimental()) {
            return textWidth + EXPERIMENTAL_TEXT_PADDING_X * 2 + EXPERIMENTAL_EXTRA_WIDTH;
        }
        return textWidth + TEXT_PADDING_X * 2 + 4;
    }

    private static Anchor anchor(ReputationChangeHudPosition position, int screenWidth, int screenHeight, int width, int entryHeight) {
        int totalHeight = MAX_ENTRIES * entryHeight + (MAX_ENTRIES - 1) * ENTRY_GAP;
        return switch (position) {
            case TOP_LEFT -> new Anchor(PADDING_X, PADDING_Y);
            case MID_LEFT -> new Anchor(PADDING_X, Math.max(PADDING_Y, (screenHeight - totalHeight) / 2));
            case MID_TOP -> new Anchor((screenWidth - width) / 2, PADDING_Y);
            case TOP_RIGHT -> new Anchor(screenWidth - width - PADDING_X, PADDING_Y);
            case MID_RIGHT -> new Anchor(screenWidth - width - PADDING_X, Math.max(PADDING_Y, (screenHeight - totalHeight) / 2));
        };
    }

    private static void renderExperimentalEntry(
            GuiGraphics graphics,
            Font font,
            NotificationEntry entry,
            int x,
            int y,
            int width,
            int height,
            float alpha,
            float elapsedTicks,
            ReputationChangeHudPosition position) {
        int accentColor = textColor(entry);
        float direction = switch (position) {
            case TOP_RIGHT, MID_RIGHT -> -1.0F;
            default -> 1.0F;
        };
        boolean rendered = VillagerInteractionScreenShaderRenderer.renderExperimentalNotification(
                graphics,
                x,
                y,
                x + width,
                y + height,
                accentColor,
                alpha,
                elapsedTicks,
                direction);
        if (!rendered) {
            renderEntry(graphics, font, entry, x, y, width, alpha);
            return;
        }

        int textColor = VillagerClientUiUtil.withAlphaRound(textColor(entry), alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.drawString(font, entry.text, x + EXPERIMENTAL_TEXT_PADDING_X, y + EXPERIMENTAL_TEXT_PADDING_Y, textColor, true);
        graphics.pose().popPose();
    }

    private static void renderEntry(GuiGraphics graphics, Font font, NotificationEntry entry, int x, int y, int width, float alpha) {
        int background = VillagerClientUiUtil.withAlphaRound(BACKGROUND_COLOR, alpha);
        int stripe = VillagerClientUiUtil.withAlphaRound(STRIPE_COLOR, alpha);
        int shadow = VillagerClientUiUtil.withAlphaRound(SHADOW_COLOR, alpha);
        int textColor = VillagerClientUiUtil.withAlphaRound(textColor(entry), alpha);

        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT, background);
        graphics.fill(x, y, x + 2, y + ENTRY_HEIGHT, stripe);
        graphics.fill(x, y + ENTRY_HEIGHT, x + width, y + ENTRY_HEIGHT + 1, shadow);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.drawString(font, entry.text, x + TEXT_PADDING_X, y + TEXT_PADDING_Y, textColor, true);
        graphics.pose().popPose();
    }

    private static int textColor(NotificationEntry entry) {
        if (entry.textColor != Integer.MIN_VALUE) {
            return entry.textColor;
        }
        return switch (entry.kind) {
            case MAP_DISCOVERY -> MAP_DISCOVERY_TEXT_COLOR;
            case RECEIVED_ITEM -> RECEIVED_ITEM_TEXT_COLOR;
            case GIFT_LIKED -> GIFT_LIKED_TEXT_COLOR;
            case GIFT_NEUTRAL -> GIFT_NEUTRAL_TEXT_COLOR;
            case GIFT_DISLIKED -> GIFT_DISLIKED_TEXT_COLOR;
            case VILLAGER_FOLLOWING -> VILLAGER_FOLLOWING_TEXT_COLOR;
            case VILLAGER_DISMISSED -> VILLAGER_DISMISSED_TEXT_COLOR;
            case VILLAGER_HIRED -> VILLAGER_HIRED_TEXT_COLOR;
            case VILLAGER_FIRED -> VILLAGER_FIRED_TEXT_COLOR;
            case VILLAGER_DEATH -> VILLAGER_DEATH_TEXT_COLOR;
            default -> TEXT_COLOR;
        };
    }

    private static ChatFormatting chatColor(VillagerReputationNoticeKind kind) {
        return switch (kind) {
            case MAP_DISCOVERY -> ChatFormatting.AQUA;
            case RECEIVED_ITEM -> ChatFormatting.AQUA;
            case GIFT_LIKED -> ChatFormatting.GREEN;
            case GIFT_NEUTRAL -> ChatFormatting.GRAY;
            case GIFT_DISLIKED -> ChatFormatting.RED;
            case VILLAGER_FOLLOWING -> ChatFormatting.GREEN;
            case VILLAGER_DISMISSED -> ChatFormatting.YELLOW;
            case VILLAGER_HIRED -> ChatFormatting.AQUA;
            case VILLAGER_FIRED -> ChatFormatting.GOLD;
            case VILLAGER_DEATH -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }

    private static Component chatMessage(VillagerReputationTierNoticePayload payload) {
        if (payload.chatColor() != Integer.MIN_VALUE) {
            return Component.literal(payload.text()).withStyle(style -> style
                    .withColor(payload.chatColor() & 0x00FFFFFF)
                    .withItalic(true));
        }
        if (payload.kind() == VillagerReputationNoticeKind.RECEIVED_ITEM) {
            String prefix = "Received item: ";
            String text = payload.text();
            if (text.startsWith(prefix) && text.length() > prefix.length()) {
                return Component.literal(prefix)
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                        .append(Component.literal(text.substring(prefix.length()))
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
            }
        }
        return Component.literal(payload.text()).withStyle(chatColor(payload.kind()), ChatFormatting.ITALIC);
    }

    private record Anchor(int x, int y) {
    }

    private record PendingNotification(String text, VillagerReputationNoticeKind kind, int textColor, int chatColor) {
    }

    private static final class NotificationEntry {
        private final String text;
        private final VillagerReputationNoticeKind kind;
        private final int textColor;
        private final int chatColor;
        private int age;

        private NotificationEntry(String text, VillagerReputationNoticeKind kind, int textColor, int chatColor) {
            this.text = text;
            this.kind = kind;
            this.textColor = textColor;
            this.chatColor = chatColor;
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
