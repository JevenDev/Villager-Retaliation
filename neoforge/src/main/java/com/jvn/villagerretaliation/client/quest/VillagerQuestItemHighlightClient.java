package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class VillagerQuestItemHighlightClient {
    private static final int HIGHLIGHT_FILL = 0x44F6C453;
    private static final int HIGHLIGHT_EDGE = 0xFFF6C453;

    private VillagerQuestItemHighlightClient() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty()) {
            return;
        }
        Optional<QuestTrackerSyncPayload.Entry> tracked = VillagerQuestTrackerOverlay.trackedEntry();
        if (tracked.isEmpty() || !matchesTrackedQuestItem(event.getItemStack(), tracked.get())) {
            return;
        }

        Component marker = Component.literal("Quest item: " + tracked.get().title())
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
        Optional<QuestTrackerSyncPayload.Entry> tracked = VillagerQuestTrackerOverlay.trackedEntry();
        if (tracked.isEmpty() || tracked.get().questItems().isEmpty()) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int left = screenWidth / 2 - 91;
        int top = screenHeight - 22;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (matchesTrackedQuestItem(stack, tracked.get())) {
                renderSlotHighlight(event.getGuiGraphics(), left + slot * 20, top);
            }
        }
    }

    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        Optional<QuestTrackerSyncPayload.Entry> tracked = VillagerQuestTrackerOverlay.trackedEntry();
        if (tracked.isEmpty()
                || tracked.get().questItems().isEmpty()
                || !(event.getContainerScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (slot.hasItem() && matchesTrackedQuestItem(slot.getItem(), tracked.get())) {
                renderSlotHighlight(event.getGuiGraphics(), slot.x, slot.y);
            }
        }
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
