package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.client.ui.VillagerNineSlice;
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
    private static final int PADDING_X = 5;
    private static final int PADDING_Y = 5;
    private static final int ENTRY_HEIGHT = 23;
    private static final int ENTRY_GAP = 1;
    private static final int EXPERIMENTAL_ENTRY_HEIGHT = 23;
    private static final int TEXT_COLOR = 0xFFF3F3F3;
    private static final int TEXT_OUTLINE_COLOR = 0xFF000000;
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
    private static final int QUEST_TEXT_COLOR = 0xFFFFD166;
    private static final int OPTIONS_LIST_TEXT_INSET = 8;
    private static final int OPTIONS_LIST_TEXT_TOP = 8;
    private static final int OPTIONS_LIST_TEXT_RIGHT_PADDING = OPTIONS_LIST_TEXT_INSET;
    private static final int ENTRY_LIFETIME_TICKS = 82;
    private static final int FADE_IN_TICKS = 8;
    private static final int FADE_OUT_TICKS = 14;
    private static final int SLIDE_DISTANCE = 6;
    private static final VillagerNineSlice OPTIONS_LIST_BACKGROUND_NINE_SLICE =
            new VillagerNineSlice(
                    VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_TEXTURE,
                    49,
                    23,
                    8,
                    8,
                    8,
                    8);
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
        if (!VanillaGuiLayers.SAVING_INDICATOR.equals(event.getName())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || ACTIVE_ENTRIES.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        float partialTick = minecraft.isPaused() ? 0.0F : event.getPartialTick().getGameTimeDeltaPartialTick(true);
        renderEntries(graphics, minecraft, partialTick);
    }

    public static void renderAboveInteractionMenu(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || ACTIVE_ENTRIES.isEmpty()) {
            return;
        }
        renderEntries(graphics, minecraft, minecraft.isPaused() ? 0.0F : partialTick);
    }

    private static void renderEntries(GuiGraphics graphics, Minecraft minecraft, float partialTick) {
        Font font = minecraft.font;
        int index = 0;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        ReputationChangeHudPosition position = VillagerRetaliationConfig.REPUTATION_CHANGE_HUD_POSITION.get();
        ReputationChangeNotificationStyle style = VillagerRetaliationConfig.REPUTATION_CHANGE_NOTIFICATION_STYLE.get();
        int entryHeight = entryHeight(style, font);
        VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.hudLayerZ());
        for (NotificationEntry entry : ACTIVE_ENTRIES) {
            float alpha = entry.alpha(partialTick);
            if (alpha <= 0.01F) {
                index++;
                continue;
            }

            String text = entry.text;
            int width = entryWidth(style, font, text);
            Anchor anchor = anchor(position, screenWidth, screenHeight, width, entryHeight);
            int horizontalSlide = Math.round((1.0F - alpha) * slideDistance());
            int x = switch (position) {
                case TOP_LEFT, MID_LEFT -> anchor.x() - horizontalSlide;
                case TOP_RIGHT, MID_RIGHT -> anchor.x() + horizontalSlide;
                case MID_TOP -> anchor.x();
            };
            int y = anchor.y() + index * (entryHeight + entryGap());
            if (style.experimental()) {
                renderExperimentalEntry(graphics, font, entry, x, y, width, entryHeight, alpha, entry.age + partialTick, position);
            } else {
                renderEntry(graphics, font, entry, x, y, width, entryHeight, alpha);
            }
            index++;
        }
        VillagerClientUiUtil.popGuiLayer(graphics);
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

    private static int entryHeight(ReputationChangeNotificationStyle style, Font font) {
        int baseHeight = style.experimental() ? EXPERIMENTAL_ENTRY_HEIGHT : ENTRY_HEIGHT;
        return Math.max(
                VillagerAdaptiveGuiScale.unit(baseHeight),
                optionsListTextTop()
                        + VillagerClientUiUtil.scaledLineStep(font, textScale())
                        + optionsListTextBottomPadding(font));
    }

    private static int entryWidth(ReputationChangeNotificationStyle style, Font font, String text) {
        int textWidth = VillagerClientUiUtil.scaledTextWidth(font, text, textScale());
        int minimumWidth = VillagerAdaptiveGuiScale.unit(OPTIONS_LIST_BACKGROUND_NINE_SLICE.textureWidth());
        return Math.max(minimumWidth, optionsListTextInset() + textWidth + optionsListTextRightPadding());
    }

    private static Anchor anchor(ReputationChangeHudPosition position, int screenWidth, int screenHeight, int width, int entryHeight) {
        int totalHeight = MAX_ENTRIES * entryHeight + (MAX_ENTRIES - 1) * entryGap();
        return switch (position) {
            case TOP_LEFT -> new Anchor(paddingX(), paddingY());
            case MID_LEFT -> new Anchor(paddingX(), Math.max(paddingY(), (screenHeight - totalHeight) / 2));
            case MID_TOP -> new Anchor((screenWidth - width) / 2, paddingY());
            case TOP_RIGHT -> new Anchor(screenWidth - width - paddingX(), paddingY());
            case MID_RIGHT -> new Anchor(screenWidth - width - paddingX(), Math.max(paddingY(), (screenHeight - totalHeight) / 2));
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
        renderOptionsListBackground(graphics, x, y, width, height, alpha);
        int textColor = VillagerClientUiUtil.withAlphaRound(textColor(entry), alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawOptionsListText(
                graphics,
                font,
                entry.text,
                x + optionsListTextInset(),
                y + optionsListTextTop(),
                textColor,
                alpha);
    }

    private static void renderEntry(GuiGraphics graphics, Font font, NotificationEntry entry, int x, int y, int width, int height, float alpha) {
        renderOptionsListBackground(graphics, x, y, width, height, alpha);
        int textColor = VillagerClientUiUtil.withAlphaRound(textColor(entry), alpha);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawOptionsListText(
                graphics,
                font,
                entry.text,
                x + optionsListTextInset(),
                y + optionsListTextTop(),
                textColor,
                alpha);
    }

    private static void renderOptionsListBackground(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        OPTIONS_LIST_BACKGROUND_NINE_SLICE.render(graphics, x, y, width, height, alpha);
    }

    private static int paddingX() {
        return VillagerAdaptiveGuiScale.unit(PADDING_X);
    }

    private static int paddingY() {
        return VillagerAdaptiveGuiScale.unit(PADDING_Y);
    }

    private static int entryGap() {
        return ENTRY_GAP;
    }

    private static int optionsListTextInset() {
        return VillagerAdaptiveGuiScale.unit(OPTIONS_LIST_TEXT_INSET);
    }

    private static int optionsListTextTop() {
        return VillagerAdaptiveGuiScale.unit(OPTIONS_LIST_TEXT_TOP);
    }

    private static int optionsListTextRightPadding() {
        return VillagerAdaptiveGuiScale.unit(OPTIONS_LIST_TEXT_RIGHT_PADDING);
    }

    private static int optionsListTextBottomPadding(Font font) {
        int textureHeight = VillagerAdaptiveGuiScale.unit(OPTIONS_LIST_BACKGROUND_NINE_SLICE.textureHeight());
        int lineHeight = VillagerClientUiUtil.scaledLineStep(font, textScale());
        return Math.max(0, textureHeight - optionsListTextTop() - lineHeight);
    }

    private static void drawOptionsListText(GuiGraphics graphics, Font font, String text, int x, int y, int color, float alpha) {
        if (text == null || text.isBlank()) {
            return;
        }
        int outlineColor = VillagerClientUiUtil.withAlphaRound(TEXT_OUTLINE_COLOR, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 200.0F);
        graphics.pose().scale(textScale(), textScale(), 1.0F);
        graphics.drawString(font, text, -1, 0, outlineColor, false);
        graphics.drawString(font, text, 1, 0, outlineColor, false);
        graphics.drawString(font, text, 0, -1, outlineColor, false);
        graphics.drawString(font, text, 0, 1, outlineColor, false);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static int slideDistance() {
        return VillagerAdaptiveGuiScale.unit(SLIDE_DISTANCE);
    }

    private static float textScale() {
        return VillagerAdaptiveGuiScale.scaleFactor();
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
            case QUEST -> QUEST_TEXT_COLOR;
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
            case QUEST -> ChatFormatting.GOLD;
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
