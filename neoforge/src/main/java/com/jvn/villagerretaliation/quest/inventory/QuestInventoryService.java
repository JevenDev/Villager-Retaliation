package com.jvn.villagerretaliation.quest.inventory;

import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.party.PartySharedQuestRecord;
import com.jvn.villagerretaliation.quest.PartyQuestService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.debug.QuestDebugFormatter;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRequirement;
import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class QuestInventoryService {
    private static final long INVENTORY_ITEM_COUNT_CACHE_TICKS = 5L;
    private static final Map<UUID, InventoryItemCountCache> INVENTORY_ITEM_COUNT_CACHES = new HashMap<>();

    private QuestInventoryService() { }

    public static void clearRuntimeState() {
        INVENTORY_ITEM_COUNT_CACHES.clear();
    }

    public static void clearRuntimeState(ServerPlayer player) {
        if (player != null) {
            INVENTORY_ITEM_COUNT_CACHES.remove(player.getUUID());
        }
    }

    public static int itemCount(ServerPlayer player, ResourceLocation itemId) {
        if (itemId == null) {
            return 0;
        }
        InventoryItemCountCache cache = cachedInventoryCache(player);
        cache.recordSimpleLookup();
        return cache.counts().getOrDefault(itemId, 0);
    }

    public static int itemCount(
            ServerPlayer player,
            ResourceLocation itemId,
            ItemStackPredicate stackPredicate) {
        ItemStackPredicate predicate = stackPredicate == null ? ItemStackPredicate.ANY : stackPredicate;
        if (predicate.isAny()) {
            return itemCount(player, itemId);
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : removablePlayerStacks(player)) {
            if (!stack.isEmpty() && stack.is(item.get()) && predicate.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int itemCount(
            ServerPlayer player,
            ResourceLocation itemId,
            QuestDefinition.ItemRequirements requirements) {
        if (itemId == null) {
            return 0;
        }
        if (hasSimpleItemRequirements(requirements)) {
            return itemCount(player, itemId);
        }
        InventoryItemCountCache cache = cachedInventoryCache(player);
        cache.recordExactLookup();
        InventoryObjectiveCountKey cacheKey = new InventoryObjectiveCountKey(itemId, requirements);
        Integer cached = cache.objectiveCounts().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty()) {
            cache.objectiveCounts().put(cacheKey, 0);
            cache.recordExactCacheMiss(0);
            return 0;
        }
        int total = 0;
        int scannedSlots = 0;
        for (ItemStack stack : player.getInventory().items) {
            scannedSlots++;
            if (matchesItem(stack, requirements, item.get())) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            scannedSlots++;
            if (matchesItem(stack, requirements, item.get())) {
                total += stack.getCount();
            }
        }
        cache.objectiveCounts().put(cacheKey, total);
        cache.recordExactCacheMiss(scannedSlots);
        return total;
    }

    private static InventoryItemCountCache cachedInventoryCache(ServerPlayer player) {
        int changeCount = player.getInventory().getTimesChanged();
        long gameTime = player.level().getGameTime();
        InventoryItemCountCache cache = INVENTORY_ITEM_COUNT_CACHES.get(player.getUUID());
        if (cache != null
                && cache.changeCount() == changeCount
                && gameTime - cache.rebuiltGameTime() <= INVENTORY_ITEM_COUNT_CACHE_TICKS) {
            return cache;
        }

        Map<ResourceLocation, Integer> counts = new HashMap<>();
        int scannedSlots = 0;
        scannedSlots += addInventoryItemCounts(counts, player.getInventory().items);
        scannedSlots += addInventoryItemCounts(counts, player.getInventory().offhand);
        Map<ResourceLocation, Integer> immutableCounts = Map.copyOf(counts);
        InventoryItemCountCache updated = new InventoryItemCountCache(
                changeCount,
                immutableCounts,
                new HashMap<>(),
                gameTime,
                scannedSlots);
        INVENTORY_ITEM_COUNT_CACHES.put(player.getUUID(), updated);
        return updated;
    }

    private static int addInventoryItemCounts(Map<ResourceLocation, Integer> counts, Iterable<ItemStack> stacks) {
        int scannedSlots = 0;
        for (ItemStack stack : stacks) {
            scannedSlots++;
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null) {
                counts.merge(itemId, stack.getCount(), Integer::sum);
            }
        }
        return scannedSlots;
    }

    private static boolean hasSimpleItemRequirements(QuestDefinition.ItemRequirements requirements) {
        return requirements == null
                || (requirements.enchantments().isEmpty()
                && requirements.minDurability().isEmpty()
                && requirements.maxDurability().isEmpty()
                && requirements.minDurabilityPercent().isEmpty()
                && requirements.maxDurabilityPercent().isEmpty()
                && !requirements.hasCustomData()
                && requirements.stackPredicate().isAny());
    }

    public static ItemHandInResult handInRequiredObjectiveItems(
            ServerPlayer player,
            Villager issuer,
            List<QuestDefinition.Objective> requiredItemHandIns,
            Function<QuestDefinition.Objective, ResourceLocation> resolvedItem,
            PartySharedQuestRecord shared) {
        QuestItemReceiver receiver = issuer == null ? null : new QuestItemReceiver() {
            @Override public boolean canReceive(List<ItemStack> stacks) { return VillagerInventoryAccess.canAddItems(issuer, stacks); }
            @Override public ItemStack receive(ItemStack stack) { return VillagerInventoryAccess.addItem(issuer, stack); }
        };
        return handInToReceiver(player, receiver, requiredItemHandIns, resolvedItem, shared);
    }

    public static ItemHandInResult handInToReceiver(ServerPlayer player, QuestItemReceiver receiver,
            List<QuestDefinition.Objective> requiredItemHandIns,
            Function<QuestDefinition.Objective, ResourceLocation> resolvedItem, PartySharedQuestRecord shared) {
        if (requiredItemHandIns.isEmpty()) {
            return ItemHandInResult.SUCCESS;
        }
        if (shared != null) {
            Optional<PartyQuestService.SubmissionPlan> planned = PartyQuestService.planSharedItemSubmission(
                    player,
                    shared,
                    requiredItemHandIns,
                    (objective, stack) -> matchesObjectiveItemStack(objective, stack, resolvedItem.apply(objective)));
            if (planned.isEmpty()) {
                return ItemHandInResult.MISSING_ITEMS;
            }
            PartyQuestService.SubmissionPlan plan = planned.get();
            if (receiver != null && !receiver.canReceive(plan.submittedStacks())) {
                return ItemHandInResult.NO_ROOM;
            }
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Quest contribution: " + String.join(", ", plan.preview())));
            if (!plan.remove()) {
                return ItemHandInResult.MISSING_ITEMS;
            }
            for (ItemStack stack : receiver == null ? List.<ItemStack>of() : plan.submittedStacks()) {
                ItemStack remainder = receiver.receive(stack.copy());
                if (!remainder.isEmpty()) {
                    player.addItem(remainder);
                }
            }
            return ItemHandInResult.SUCCESS;
        }
        List<ItemStack> handInStacks = previewObjectiveItemStacks(player, requiredItemHandIns, resolvedItem)
                .orElse(null);
        if (handInStacks == null) {
            return ItemHandInResult.MISSING_ITEMS;
        }
        if (receiver != null && !receiver.canReceive(handInStacks)) {
            return ItemHandInResult.NO_ROOM;
        }
        if (!removeSpecificPlayerStacks(player, handInStacks)) {
            return ItemHandInResult.MISSING_ITEMS;
        }
        for (ItemStack stack : receiver == null ? List.<ItemStack>of() : handInStacks) {
            ItemStack remainder = receiver.receive(stack.copy());
            if (!remainder.isEmpty()) {
                player.addItem(remainder);
            }
        }
        return ItemHandInResult.SUCCESS;
    }

    public static Optional<List<ItemStack>> previewObjectiveItemStacks(
            ServerPlayer player,
            List<QuestDefinition.Objective> requiredObjectives,
            Function<QuestDefinition.Objective, ResourceLocation> resolvedItem) {
        List<ItemStack> handInStacks = new ArrayList<>();
        List<ItemStack> availableStacks = removablePlayerStacks(player).stream()
                .map(ItemStack::copy)
                .toList();
        for (QuestDefinition.Objective objective : requiredObjectives) {
            ResourceLocation itemId = resolvedItem.apply(objective);
            if (itemId == null) {
                return Optional.empty();
            }
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
            if (item.isEmpty()) {
                return Optional.empty();
            }
            int remaining = objective.count();
            for (ItemStack stack : availableStacks) {
                if (remaining <= 0) {
                    break;
                }
                if (!matchesItem(stack, objective.itemRequirements(), item.get())) {
                    continue;
                }
                int removed = Math.min(remaining, stack.getCount());
                handInStacks.add(stack.copyWithCount(removed));
                stack.shrink(removed);
                remaining -= removed;
            }
            if (remaining > 0) {
                return Optional.empty();
            }
        }
        return Optional.of(List.copyOf(handInStacks));
    }

    public static boolean matchesObjectiveItemStack(
            QuestDefinition.Objective objective,
            ItemStack stack,
            ResourceLocation itemId) {
        if (itemId == null || stack == null || stack.isEmpty()) {
            return false;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
        return item.isPresent() && matchesItem(stack, objective.itemRequirements(), item.get());
    }

    private static boolean matchesItem(
            ItemStack stack,
            QuestDefinition.ItemRequirements requirements,
            Item item) {
        if (stack.isEmpty() || !stack.is(item)) {
            return false;
        }
        if (!requirements.stackPredicate().matches(stack)) {
            return false;
        }
        if (!requirements.enchantments().isEmpty()
                && requirements.enchantments().stream().anyMatch(requirement -> !matchesEnchantment(stack, requirement))) {
            return false;
        }
        if ((requirements.minDurability().isPresent()
                || requirements.maxDurability().isPresent()
                || requirements.minDurabilityPercent().isPresent()
                || requirements.maxDurabilityPercent().isPresent())
                && !matchesDurability(stack, requirements)) {
            return false;
        }
        if (requirements.hasCustomData() && !matchesCustomData(stack, requirements.customData())) {
            return false;
        }
        return true;
    }

    private static boolean matchesEnchantment(ItemStack stack, QuestDefinition.EnchantmentRequirement requirement) {
        int level = Math.max(
                enchantmentLevel(stack.getEnchantments(), requirement.id()),
                enchantmentLevel(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY), requirement.id()));
        return level > 0
                && requirement.minLevel().stream().allMatch(min -> level >= min)
                && requirement.maxLevel().stream().allMatch(max -> level <= max);
    }

    private static int enchantmentLevel(ItemEnchantments enchantments, ResourceLocation id) {
        int level = 0;
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().map(key -> key.location().equals(id)).orElse(false)) {
                level = Math.max(level, entry.getIntValue());
            }
        }
        return level;
    }

    private static boolean matchesDurability(ItemStack stack, QuestDefinition.ItemRequirements requirements) {
        int maximum = stack.isDamageableItem() ? Math.max(0, stack.getMaxDamage()) : 0;
        int remaining = stack.isDamageableItem() ? Math.max(0, maximum - stack.getDamageValue()) : 0;
        int percent = maximum <= 0 ? 0 : Math.round(remaining * 100.0F / maximum);
        return requirements.minDurability().stream().allMatch(min -> remaining >= min)
                && requirements.maxDurability().stream().allMatch(max -> remaining <= max)
                && requirements.minDurabilityPercent().stream().allMatch(min -> percent >= min)
                && requirements.maxDurabilityPercent().stream().allMatch(max -> percent <= max);
    }

    private static boolean matchesCustomData(ItemStack stack, CompoundTag requiredData) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return containsTagSubset(customData.copyTag(), requiredData);
    }

    private static boolean containsTagSubset(CompoundTag actual, CompoundTag required) {
        for (String key : required.getAllKeys()) {
            Tag requiredChild = required.get(key);
            Tag actualChild = actual.get(key);
            if (requiredChild == null || actualChild == null) {
                return false;
            }
            if (requiredChild instanceof CompoundTag requiredCompound) {
                if (!(actualChild instanceof CompoundTag actualCompound)
                        || !containsTagSubset(actualCompound, requiredCompound)) {
                    return false;
                }
                continue;
            }
            if (requiredChild instanceof NumericTag requiredNumber && actualChild instanceof NumericTag actualNumber) {
                if (Double.compare(requiredNumber.getAsDouble(), actualNumber.getAsDouble()) != 0) {
                    return false;
                }
                continue;
            }
            if (!requiredChild.equals(actualChild)) {
                return false;
            }
        }
        return true;
    }

    private static boolean removeSpecificPlayerStacks(ServerPlayer player, List<ItemStack> handInStacks) {
        if (!canRemoveSpecificPlayerStacks(player, handInStacks)) {
            return false;
        }
        for (ItemStack handInStack : handInStacks) {
            int remaining = handInStack.getCount();
            for (ItemStack stack : removablePlayerStacks(player)) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, handInStack)) {
                    continue;
                }
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        player.getInventory().setChanged();
        return true;
    }

    private static boolean canRemoveSpecificPlayerStacks(ServerPlayer player, List<ItemStack> handInStacks) {
        List<ItemStack> availableStacks = removablePlayerStacks(player).stream()
                .map(ItemStack::copy)
                .toList();
        for (ItemStack handInStack : handInStacks) {
            int remaining = handInStack.getCount();
            for (ItemStack stack : availableStacks) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, handInStack)) {
                    continue;
                }
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> removablePlayerStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    public static String debugInventoryCacheLine(ServerPlayer player, QuestDefinition definition) {
        int itemObjectives = 0;
        int exactItemObjectives = 0;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (!QuestObjectiveRegistry.requirements(objective).contains(QuestObjectiveRequirement.INVENTORY)) {
                continue;
            }
            itemObjectives++;
            if (!hasSimpleItemRequirements(objective.itemRequirements())) {
                exactItemObjectives++;
            }
        }
        boolean proofItem = definition.target().hasProofItem();
        if (itemObjectives == 0 && !proofItem) {
            return QuestDebugFormatter.inventoryCacheLine(new QuestDebugFormatter.InventoryCacheLine(
                    false,
                    false,
                    0,
                    0,
                    0,
                    false,
                    0,
                    0,
                    0L,
                    0,
                    0,
                    0,
                    0,
                    0));
        }

        int changeCount = player.getInventory().getTimesChanged();
        InventoryItemCountCache cache = INVENTORY_ITEM_COUNT_CACHES.get(player.getUUID());
        boolean warm = cache != null && cache.changeCount() == changeCount;
        long gameTime = player.level().getGameTime();
        return QuestDebugFormatter.inventoryCacheLine(new QuestDebugFormatter.InventoryCacheLine(
                true,
                warm,
                changeCount,
                itemObjectives,
                exactItemObjectives,
                proofItem,
                warm ? cache.counts().size() : 0,
                warm ? cache.objectiveCounts().size() : 0,
                warm ? Math.max(0L, gameTime - cache.rebuiltGameTime()) : 0L,
                warm ? cache.simpleScanSlots() : 0,
                warm ? cache.simpleLookups() : 0,
                warm ? cache.exactLookups() : 0,
                warm ? cache.exactCacheMisses() : 0,
                warm ? cache.exactScanSlots() : 0));
    }

    private record InventoryObjectiveCountKey(
            ResourceLocation itemId,
            QuestDefinition.ItemRequirements requirements) {
    }

    private static final class InventoryItemCountCache {
        private final int changeCount;
        private final Map<ResourceLocation, Integer> counts;
        private final Map<InventoryObjectiveCountKey, Integer> objectiveCounts;
        private final long rebuiltGameTime;
        private final int simpleScanSlots;
        private int simpleLookups;
        private int exactLookups;
        private int exactCacheMisses;
        private int exactScanSlots;

        private InventoryItemCountCache(
                int changeCount,
                Map<ResourceLocation, Integer> counts,
                Map<InventoryObjectiveCountKey, Integer> objectiveCounts,
                long rebuiltGameTime,
                int simpleScanSlots) {
            this.changeCount = changeCount;
            this.counts = counts;
            this.objectiveCounts = objectiveCounts;
            this.rebuiltGameTime = rebuiltGameTime;
            this.simpleScanSlots = simpleScanSlots;
        }

        private int changeCount() {
            return this.changeCount;
        }

        private Map<ResourceLocation, Integer> counts() {
            return this.counts;
        }

        private Map<InventoryObjectiveCountKey, Integer> objectiveCounts() {
            return this.objectiveCounts;
        }

        private long rebuiltGameTime() {
            return this.rebuiltGameTime;
        }

        private int simpleScanSlots() {
            return this.simpleScanSlots;
        }

        private int simpleLookups() {
            return this.simpleLookups;
        }

        private int exactLookups() {
            return this.exactLookups;
        }

        private int exactCacheMisses() {
            return this.exactCacheMisses;
        }

        private int exactScanSlots() {
            return this.exactScanSlots;
        }

        private void recordSimpleLookup() {
            this.simpleLookups++;
        }

        private void recordExactLookup() {
            this.exactLookups++;
        }

        private void recordExactCacheMiss(int scannedSlots) {
            this.exactCacheMisses++;
            this.exactScanSlots += scannedSlots;
        }
    }

    public enum ItemHandInResult {
        SUCCESS("completed", "", ""),
        MISSING_ITEMS("missing_objectives", "There is still more to do before this is ready.", "quest.dialogue.missing_objectives"),
        NO_ROOM("inventory_full", "I do not have room in my inventory for that.", "quest.dialogue.inventory_full");

        private final String status;
        private final String message;
        private final String messageKey;

        ItemHandInResult(String status, String message, String messageKey) {
            this.status = status;
            this.message = message;
            this.messageKey = messageKey;
        }

        public String status() {
            return this.status;
        }

        public String message() {
            return this.message;
        }

        public String messageKey() {
            return this.messageKey;
        }
    }
}
