package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.interaction.VillagerQuestJournalScreen;
import com.jvn.villagerretaliation.client.ui.VillagerAdaptiveGuiScale;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.network.QuestTrackerRequestPayload;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.quest.tracking.QuestJournalData;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private static final int HUD_MARGIN = 5;

    private static final QuestTrackerModel MODEL = new QuestTrackerModel();
    private static int flashTicks;
    private static int notificationAge;
    private static int age;
    private static float notificationAlpha;
    private static float trackerAlpha;
    private static boolean trackerVisible;
    private static int ignoredJournalToggleTicks;
    private static int pendingJournalOpenTicks;

    private VillagerQuestTrackerOverlay() {
    }

    public static void accept(QuestTrackerSyncPayload payload) {
        Map<String, UUID> indicators = new LinkedHashMap<>();
        for (var marker : payload.questMarkers()) {
            if (marker.valid() && !marker.questId().isBlank()) indicators.put(marker.questId(), marker.villagerId());
        }
        MODEL.accept(payload.entries(), payload.trackedQuestIds(), payload.questlineNodes(), indicators);
        if (payload.flash() && trackedEntry().isPresent()) {
            flashTicks = FLASH_LIFETIME_TICKS;
            notificationAge = 0;
        } else if (trackedEntry().isEmpty()) {
            flashTicks = 0;
            notificationAge = 0;
            notificationAlpha = 0.0F;
        }
        if (MODEL.entries().isEmpty()) {
            flashTicks = 0;
            notificationAge = 0;
            if (pendingJournalOpenTicks > 0 && Minecraft.getInstance().screen == null) {
                pendingJournalOpenTicks = 0;
                Minecraft.getInstance().setScreen(new com.jvn.villagerretaliation.client.interaction.CampaignJournalScreen(null));
            } else if (Minecraft.getInstance().screen instanceof VillagerQuestJournalScreen) {
                Minecraft.getInstance().setScreen(null);
            }
        } else if (pendingJournalOpenTicks > 0 && Minecraft.getInstance().screen == null) {
            pendingJournalOpenTicks = 0;
            Minecraft.getInstance().setScreen(new VillagerQuestJournalScreen());
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
            boolean journalHidingTracker = minecraft.screen instanceof VillagerQuestJournalScreen journal
                    && !journal.isClosingWithAnimation();
            boolean hasTrackedEntry = trackedEntry().isPresent();
            boolean targetTrackerVisible = hasTrackedEntry && trackerVisible && !journalHidingTracker;
            boolean targetNotificationVisible = hasTrackedEntry && flashTicks > 0 && !targetTrackerVisible && !journalOpen;
            trackerAlpha = approach(trackerAlpha, targetTrackerVisible);
            notificationAlpha = approach(notificationAlpha, targetNotificationVisible);
        }
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.SAVING_INDICATOR.equals(event.getName())
                || MODEL.entries().isEmpty()
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

        VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.hudLayerZ());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Optional<QuestJournalData.Entry> trackedEntry = hudEntries().stream().findFirst();
        if (notificationAlpha > 0.01F && trackedEntry.isPresent()) {
            renderNotification(graphics, font, trackedEntry.get(), screenWidth, screenHeight);
        }
        if (trackerAlpha <= 0.01F) {
            VillagerClientUiUtil.popGuiLayer(graphics);
            return;
        }

        renderTrackerLayer(graphics, font, screenWidth, screenHeight, trackerAlpha, true, age);
        VillagerClientUiUtil.popGuiLayer(graphics);
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        MODEL.reset();
        flashTicks = 0;
        notificationAge = 0;
        age = 0;
        notificationAlpha = 0.0F;
        trackerAlpha = 0.0F;
        trackerVisible = false;
        ignoredJournalToggleTicks = 0;
        pendingJournalOpenTicks = 0;
    }

    public static List<QuestJournalData.Entry> journalEntries() {
        if (!com.jvn.villagerretaliation.authoring.client.EditorPlaytestViewport.active()) return MODEL.shared().entries();
        return entries().stream().map(VrQuestJournalData::shared).toList();
    }

    public static List<QuestJournalData.QuestlineNode> journalQuestlineNodes() { return MODEL.shared().questlineNodes(); }

    private static List<QuestJournalData.Entry> hudEntries() {
        if (!com.jvn.villagerretaliation.authoring.client.EditorPlaytestViewport.active()) return MODEL.shared().trackedEntries();
        return trackedEntries().stream().map(VrQuestJournalData::shared).toList();
    }

    public static List<QuestTrackerSyncPayload.Entry> entries() {
        if (!com.jvn.villagerretaliation.authoring.client.EditorPlaytestViewport.active()) return MODEL.entries();
        return MODEL.entries().stream().map(com.jvn.villagerretaliation.authoring.client.EditorPlaytestPresentation::entry).toList();
    }

    public static List<QuestTrackerSyncPayload.QuestlineNode> questlineNodes() {
        return MODEL.questlineNodes();
    }

    public static void acknowledgeQuestUpdate(QuestTrackerSyncPayload.Entry entry) {
        MODEL.acknowledge(entry);
    }

    public static Optional<QuestTrackerSyncPayload.Entry> trackedEntry() {
        return trackedEntries().stream().findFirst();
    }

    public static boolean hasQuestIndicator(UUID villagerId) {
        return MODEL.hasIndicator(villagerId);
    }

    public static List<QuestTrackerSyncPayload.Entry> trackedEntries() {
        return MODEL.trackedEntries().stream()
                .map(com.jvn.villagerretaliation.authoring.client.EditorPlaytestPresentation::entry).toList();
    }

    public static boolean isTracked(QuestTrackerSyncPayload.Entry entry) {
        return MODEL.trackedIndex(entry) >= 0;
    }

    public static int trackedIndex(QuestTrackerSyncPayload.Entry entry) {
        return MODEL.trackedIndex(entry);
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
        if (MODEL.entries().isEmpty() || alpha <= 0.01F) {
            return;
        }
        List<QuestJournalData.Entry> trackerEntries = trackerEntries(showRecentQuests);
        if (trackerEntries.isEmpty()) {
            return;
        }
        int width = VrQuestPresentation.HUD.trackerWidth(screenWidth);
        int count = VrQuestPresentation.HUD.visibleTrackerEntryCount(showRecentQuests, trackerEntries.size());
        int totalHeight = VrQuestPresentation.HUD.trackerHeight(font, trackerEntries, count, width);
        int x = hudMargin();
        int y = Math.max(VillagerAdaptiveGuiScale.unit(10), (screenHeight - totalHeight) / 2);
        for (int index = 0; index < count; index++) {
            QuestJournalData.Entry entry = trackerEntries.get(index);
            boolean primary = index == 0;
            int height = primary ? VrQuestPresentation.HUD.primaryHeight() : VrQuestPresentation.HUD.secondaryHeight(font, entry, width);
            float entryAlpha = alpha * (primary ? 1.0F : 0.76F);
            int slide = Math.round((1.0F - entryAlpha) * VrQuestPresentation.HUD.slideDistance());
            VrQuestPresentation.HUD.renderEntry(graphics, font, entry, x - slide, y, width, height, entryAlpha, primary, renderAge + index * 13);
            y += height + VrQuestPresentation.HUD.panelGap();
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
        if (pendingJournalOpenTicks > 0) {
            pendingJournalOpenTicks--;
        }
    }

    private static void toggleJournal() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerQuestJournalScreen journal) {
            journal.onClose();
            return;
        }
        if (!MODEL.entries().isEmpty()) {
            minecraft.setScreen(new VillagerQuestJournalScreen());
            return;
        }
        pendingJournalOpenTicks = 40;
        PacketDistributor.sendToServer(new QuestTrackerRequestPayload("", QuestTrackerRequestPayload.Action.REFRESH));
    }

    private static List<QuestJournalData.Entry> trackerEntries(boolean showRecentQuests) {
        List<QuestJournalData.Entry> tracked = hudEntries();
        if (tracked.isEmpty()) {
            return List.of();
        }
        if (!showRecentQuests) {
            return List.of(tracked.getFirst());
        }
        return tracked;
    }

    private static float approach(float value, boolean visible) {
        float delta = visible ? 0.16F : -0.16F;
        return Mth.clamp(value + delta, 0.0F, 1.0F);
    }

    private static void renderNotification(
            GuiGraphics graphics,
            Font font,
            QuestJournalData.Entry entry,
            int screenWidth,
            int screenHeight) {
        int width = VrQuestPresentation.HUD.notificationWidth(font, entry, screenWidth);
        int height = VrQuestPresentation.HUD.notificationHeight(font, entry, width, screenHeight);
        int margin = hudMargin();
        int x = margin - Math.round((1.0F - notificationAlpha) * VrQuestPresentation.HUD.slideDistance());
        x = Math.min(x, screenWidth - width - margin);
        int y = Math.max(VillagerAdaptiveGuiScale.unit(10), screenHeight / 2 - height / 2);
        float alpha = notificationAlpha;

        VrQuestPresentation.HUD.renderNotification(graphics, font, entry, x, y, width, height, alpha, notificationAge);
    }

    private static int hudMargin() {
        return VillagerAdaptiveGuiScale.unit(HUD_MARGIN);
    }
}
