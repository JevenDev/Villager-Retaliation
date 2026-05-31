package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class VillagerQuestItemHighlightClient {
    private static final int HIGHLIGHT_FILL = 0x18F6C453;
    private static final int HIGHLIGHT_EDGE = 0xFFF6C453;
    public static final int QUEST_OUTLINE_RED = 246;
    public static final int QUEST_OUTLINE_GREEN = 196;
    public static final int QUEST_OUTLINE_BLUE = 83;
    public static final int QUEST_OUTLINE_ALPHA = 255;
    public static final int QUEST_OUTLINE_RGB = 0xF6C453;

    private VillagerQuestItemHighlightClient() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty()) {
            return;
        }
        Optional<QuestTrackerSyncPayload.Entry> questEntry = questEntryForStack(event.getItemStack());
        if (questEntry.isEmpty()) {
            return;
        }

        Component marker = Component.literal("Quest item: " + questEntry.get().title())
                .withStyle(ChatFormatting.GOLD);
        if (!event.getToolTip().contains(marker)) {
            event.getToolTip().add(marker);
        }
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        List<QuestTrackerSyncPayload.Entry> activeQuestItemEntries = activeQuestItemEntries();
        if (activeQuestItemEntries.isEmpty()) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int left = screenWidth / 2 - 88;
        int top = screenHeight - 19;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (questEntryForStack(stack, activeQuestItemEntries).isPresent()) {
                renderSlotHighlight(event.getGuiGraphics(), left + slot * 20, top);
            }
        }
    }

    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        List<QuestTrackerSyncPayload.Entry> activeQuestItemEntries = activeQuestItemEntries();
        if (activeQuestItemEntries.isEmpty()
                || !(event.getContainerScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (slot.hasItem() && questEntryForStack(slot.getItem(), activeQuestItemEntries).isPresent()) {
                renderSlotHighlight(event.getGuiGraphics(), slot.x, slot.y);
            }
        }
    }

    public static boolean matchesActiveQuestItem(ItemStack stack) {
        return questEntryForStack(stack).isPresent();
    }

    public static boolean shouldRenderHeldQuestGlow(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext) {
        Minecraft minecraft = Minecraft.getInstance();
        return questItemShaderHighlightsEnabled()
                && entity == minecraft.player
                && isHeldDisplayContext(displayContext)
                && matchesActiveQuestItem(stack);
    }

    public static boolean shouldOutlineDroppedQuestItem(ItemEntity itemEntity) {
        Minecraft minecraft = Minecraft.getInstance();
        return questItemShaderHighlightsEnabled()
                && minecraft.player != null
                && minecraft.level != null
                && itemEntity.isAlive()
                && matchesActiveQuestItem(itemEntity.getItem());
    }

    private static boolean questItemShaderHighlightsEnabled() {
        return VillagerRetaliationConfig.ENABLE_QUEST_ITEM_SHADER_HIGHLIGHTS.get();
    }

    private static boolean matchesTrackedQuestItem(ItemStack stack, QuestTrackerSyncPayload.Entry entry) {
        if (stack.isEmpty() || entry.questItems().isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return false;
        }
        String serialized = itemId.toString();
        for (QuestTrackerSyncPayload.QuestItem questItem : entry.questItems()) {
            if (serialized.equals(questItem.itemId())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<QuestTrackerSyncPayload.Entry> activeTrackedEntry() {
        Optional<QuestTrackerSyncPayload.Entry> tracked = VillagerQuestTrackerOverlay.trackedEntry();
        return tracked.filter(VillagerQuestItemHighlightClient::isActiveTrackedEntry);
    }

    private static Optional<QuestTrackerSyncPayload.Entry> questEntryForStack(ItemStack stack) {
        return questEntryForStack(stack, activeQuestItemEntries());
    }

    private static Optional<QuestTrackerSyncPayload.Entry> questEntryForStack(
            ItemStack stack,
            List<QuestTrackerSyncPayload.Entry> activeQuestItemEntries) {
        Optional<QuestTrackerSyncPayload.Entry> tracked = activeTrackedEntry()
                .filter(entry -> matchesTrackedQuestItem(stack, entry));
        if (tracked.isPresent()) {
            return tracked;
        }
        return activeQuestItemEntries.stream()
                .filter(entry -> matchesTrackedQuestItem(stack, entry))
                .findFirst();
    }

    private static List<QuestTrackerSyncPayload.Entry> activeQuestItemEntries() {
        return VillagerQuestTrackerOverlay.entries().stream()
                .filter(VillagerQuestItemHighlightClient::isActiveTrackedEntry)
                .filter(entry -> !entry.questItems().isEmpty())
                .toList();
    }

    private static boolean isActiveTrackedEntry(QuestTrackerSyncPayload.Entry entry) {
        return "active".equalsIgnoreCase(entry.state());
    }

    private static boolean isHeldDisplayContext(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static void renderSlotHighlight(GuiGraphics graphics, int left, int top) {
        int right = left + 16;
        int bottom = top + 16;
        graphics.fill(left, top, right, bottom, HIGHLIGHT_FILL);
        graphics.fill(left, top, right, top + 1, HIGHLIGHT_EDGE);
        graphics.fill(left, bottom - 1, right, bottom, HIGHLIGHT_EDGE);
        graphics.fill(left, top, left + 1, bottom, HIGHLIGHT_EDGE);
        graphics.fill(right - 1, top, right, bottom, HIGHLIGHT_EDGE);
    }
}
