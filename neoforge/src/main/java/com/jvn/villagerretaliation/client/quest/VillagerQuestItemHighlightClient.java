package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class VillagerQuestItemHighlightClient {
    private static final int HIGHLIGHT_FILL = 0x18F6C453;
    private static final int HIGHLIGHT_EDGE = 0xFFF6C453;
    private static final double WORLD_GLOW_RADIUS = 64.0D;
    private static final double WORLD_GLOW_RADIUS_SQUARED = WORLD_GLOW_RADIUS * WORLD_GLOW_RADIUS;
    private static final Map<Integer, Boolean> LOCALLY_GLOWING_ITEM_ENTITIES = new HashMap<>();

    private VillagerQuestItemHighlightClient() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clearLocalItemGlows(minecraft);
            return;
        }

        List<QuestTrackerSyncPayload.Entry> activeQuestItemEntries = activeQuestItemEntries();
        if (activeQuestItemEntries.isEmpty()) {
            clearLocalItemGlows(minecraft);
            return;
        }

        Map<Integer, Boolean> stillGlowing = new HashMap<>();
        for (ItemEntity itemEntity : minecraft.level.getEntities(
                net.minecraft.world.entity.EntityType.ITEM,
                minecraft.player.getBoundingBox().inflate(WORLD_GLOW_RADIUS),
                entity -> entity.isAlive() && entity.distanceToSqr(minecraft.player) <= WORLD_GLOW_RADIUS_SQUARED)) {
            if (questEntryForStack(itemEntity.getItem(), activeQuestItemEntries).isEmpty()) {
                continue;
            }
            int entityId = itemEntity.getId();
            if (!itemEntity.hasGlowingTag()) {
                LOCALLY_GLOWING_ITEM_ENTITIES.putIfAbsent(entityId, false);
                itemEntity.setGlowingTag(true);
            }
            if (LOCALLY_GLOWING_ITEM_ENTITIES.containsKey(entityId)) {
                stillGlowing.put(entityId, LOCALLY_GLOWING_ITEM_ENTITIES.get(entityId));
            }
        }

        LOCALLY_GLOWING_ITEM_ENTITIES.keySet().removeIf(entityId -> {
            if (stillGlowing.containsKey(entityId)) {
                return false;
            }
            restoreItemGlowState(minecraft, entityId);
            return true;
        });
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
        return entity == minecraft.player
                && isHeldDisplayContext(displayContext)
                && matchesActiveQuestItem(stack);
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
        return displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static void clearLocalItemGlows(Minecraft minecraft) {
        LOCALLY_GLOWING_ITEM_ENTITIES.keySet().removeIf(entityId -> {
            restoreItemGlowState(minecraft, entityId);
            return true;
        });
    }

    private static void restoreItemGlowState(Minecraft minecraft, int entityId) {
        Boolean previous = LOCALLY_GLOWING_ITEM_ENTITIES.get(entityId);
        if (minecraft.level == null) {
            return;
        }
        net.minecraft.world.entity.Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof ItemEntity itemEntity && previous != null) {
            itemEntity.setGlowingTag(previous);
        }
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
