package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.interaction.VillagerQuestJournalScreen;
import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import com.jvn.villagerretaliation.network.QuestTrackerRequestPayload;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerQuestTrackerOverlay {
    private static final int FLASH_LIFETIME_TICKS = 180;

    private static List<QuestTrackerSyncPayload.Entry> entries = List.of();
    private static int flashTicks;
    private static int notificationAge;
    private static int age;
    private static float notificationAlpha;
    private static float trackerAlpha;
    private static boolean trackerVisible;
    private static int ignoredJournalToggleTicks;
    private static String trackedQuestId = "";

    private VillagerQuestTrackerOverlay() {
    }

    public static void accept(QuestTrackerSyncPayload payload) {
        entries = payload.entries();
        trackedQuestId = payload.trackedQuestId();
        if (payload.flash() && trackedEntry().isPresent()) {
            flashTicks = FLASH_LIFETIME_TICKS;
            notificationAge = 0;
        } else if (trackedEntry().isEmpty()) {
            flashTicks = 0;
            notificationAge = 0;
            notificationAlpha = 0.0F;
        }
        if (entries.isEmpty()) {
            flashTicks = 0;
            notificationAge = 0;
            trackedQuestId = "";
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
                notificationAge++;
            } else {
                notificationAge = 0;
            }
            boolean journalOpen = minecraft.screen instanceof VillagerQuestJournalScreen;
            boolean hasTrackedEntry = trackedEntry().isPresent();
            boolean targetTrackerVisible = hasTrackedEntry && (trackerVisible || journalOpen);
            boolean targetNotificationVisible = hasTrackedEntry && flashTicks > 0 && !targetTrackerVisible;
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
        Optional<QuestTrackerSyncPayload.Entry> trackedEntry = trackedEntry();
        if (notificationAlpha > 0.01F && trackedEntry.isPresent()) {
            renderNotification(graphics, font, trackedEntry.get(), screenWidth, screenHeight);
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
        notificationAge = 0;
        age = 0;
        notificationAlpha = 0.0F;
        trackerAlpha = 0.0F;
        trackerVisible = false;
        ignoredJournalToggleTicks = 0;
        trackedQuestId = "";
    }

    public static List<QuestTrackerSyncPayload.Entry> entries() {
        return entries;
    }

    public static Optional<QuestTrackerSyncPayload.Entry> trackedEntry() {
        if (trackedQuestId.isBlank()) {
            return Optional.empty();
        }
        return entries.stream()
                .filter(entry -> entry.trackable() && trackedQuestId.equals(entry.questId()))
                .findFirst();
    }

    public static boolean isTracked(QuestTrackerSyncPayload.Entry entry) {
        return entry != null && entry.trackable() && entry.questId().equals(trackedQuestId);
    }

    public static void toggleTracking(QuestTrackerSyncPayload.Entry entry) {
        if (entry == null || !entry.trackable()) {
            return;
        }
        if (!isTracked(entry)) {
            trackerVisible = true;
        }
        PacketDistributor.sendToServer(new QuestTrackerRequestPayload(entry.questId(), QuestTrackerRequestPayload.Action.TOGGLE));
    }

    public static void dismissJournalFlash() {
        flashTicks = 0;
        notificationAge = 0;
        notificationAlpha = 0.0F;
        if (!trackerVisible) {
            trackerAlpha = 0.0F;
        }
    }

    public static void ignorePendingJournalToggle() {
        ignoredJournalToggleTicks = 2;
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
        List<QuestTrackerSyncPayload.Entry> trackerEntries = trackerEntries(showRecentQuests);
        if (trackerEntries.isEmpty()) {
            return;
        }
        int width = VillagerQuestHudRenderer.trackerWidth(screenWidth);
        int count = VillagerQuestHudRenderer.visibleTrackerEntryCount(showRecentQuests, trackerEntries.size());
        int totalHeight = VillagerQuestHudRenderer.trackerHeight(count);
        int x = VillagerAdaptiveGuiScale.unit(12);
        int y = Math.max(VillagerAdaptiveGuiScale.unit(10), (screenHeight - totalHeight) / 2);
        for (int index = 0; index < count; index++) {
            QuestTrackerSyncPayload.Entry entry = trackerEntries.get(index);
            boolean primary = index == 0;
            int height = primary ? VillagerQuestHudRenderer.primaryHeight() : VillagerQuestHudRenderer.secondaryHeight();
            float entryAlpha = alpha * (primary ? 1.0F : 0.76F);
            int slide = Math.round((1.0F - entryAlpha) * VillagerQuestHudRenderer.slideDistance());
            VillagerQuestHudRenderer.renderEntry(graphics, font, entry, x - slide, y, width, height, entryAlpha, primary, renderAge + index * 13);
            y += height + VillagerQuestHudRenderer.panelGap();
        }
    }

    private static void updateKeyState() {
        while (VillagerQuestKeyMappings.OPEN_JOURNAL.consumeClick()) {
            if (ignoredJournalToggleTicks > 0) {
                continue;
            }
            toggleJournal();
        }
        while (VillagerQuestKeyMappings.TOGGLE_TRACKER.consumeClick()) {
            trackerVisible = !trackerVisible;
        }
        if (ignoredJournalToggleTicks > 0) {
            ignoredJournalToggleTicks--;
        }
    }

    private static void toggleJournal() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerQuestJournalScreen journal) {
            journal.onClose();
            return;
        }
        if (!entries.isEmpty()) {
            minecraft.setScreen(new VillagerQuestJournalScreen());
        }
    }

    private static List<QuestTrackerSyncPayload.Entry> trackerEntries(boolean showRecentQuests) {
        Optional<QuestTrackerSyncPayload.Entry> tracked = trackedEntry();
        if (tracked.isEmpty()) {
            return List.of();
        }
        if (!showRecentQuests) {
            return List.of(tracked.get());
        }
        List<QuestTrackerSyncPayload.Entry> ordered = new ArrayList<>();
        ordered.add(tracked.get());
        for (QuestTrackerSyncPayload.Entry entry : entries) {
            if (entry.trackable() && !entry.questId().equals(tracked.get().questId())) {
                ordered.add(entry);
            }
        }
        return List.copyOf(ordered);
    }

    private static float approach(float value, boolean visible) {
        float delta = visible ? 0.16F : -0.16F;
        return Mth.clamp(value + delta, 0.0F, 1.0F);
    }

    private static void renderNotification(
            GuiGraphics graphics,
            Font font,
            QuestTrackerSyncPayload.Entry entry,
            int screenWidth,
            int screenHeight) {
        int width = VillagerQuestHudRenderer.notificationWidth(font, entry, screenWidth);
        int height = VillagerQuestHudRenderer.notificationHeight(font, entry, width, screenHeight);
        int margin = VillagerAdaptiveGuiScale.unit(12);
        int x = margin - Math.round((1.0F - notificationAlpha) * VillagerQuestHudRenderer.slideDistance());
        x = Math.min(x, screenWidth - width - margin);
        int y = Math.max(VillagerAdaptiveGuiScale.unit(10), screenHeight / 2 - height / 2);
        float alpha = notificationAlpha;

        VillagerQuestHudRenderer.renderNotification(graphics, font, entry, x, y, width, height, alpha, notificationAge);
    }
}
