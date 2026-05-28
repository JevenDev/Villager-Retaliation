package com.jvn.villagerretaliation.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;

final class TrackedVillagerItemLedger {
    private static final String PLAYER_TAG = "Player";
    private static final String PLAYER_NAME_TAG = "PlayerName";
    private static final String OWNER_VILLAGER_TAG = "OwnerVillager";
    private static final String OWNER_VILLAGER_NAME_TAG = "OwnerVillagerName";
    private static final String STACK_TAG = "Stack";
    private static final String COUNT_TAG = "Count";
    private static final String REPUTATION_TAG = "Reputation";

    private final String ledgerTag;
    private final String itemTag;
    private final boolean storesReputation;

    TrackedVillagerItemLedger(String ledgerTag, String itemTag, boolean storesReputation) {
        this.ledgerTag = ledgerTag;
        this.itemTag = itemTag;
        this.storesReputation = storesReputation;
    }

    ItemStack mark(ItemStack stack, ServerPlayer player) {
        return mark(stack, player, null);
    }

    ItemStack mark(ItemStack stack, ServerPlayer player, Villager owner) {
        if (stack.isEmpty()) {
            return stack;
        }

        CompoundTag trackingTag = new CompoundTag();
        trackingTag.putUUID(PLAYER_TAG, player.getUUID());
        trackingTag.putString(PLAYER_NAME_TAG, player.getGameProfile().getName());
        if (owner != null) {
            trackingTag.putUUID(OWNER_VILLAGER_TAG, owner.getUUID());
            trackingTag.putString(OWNER_VILLAGER_NAME_TAG, VillagerPresetNameRegistry.resolveDisplayName(owner).getString());
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(this.itemTag, trackingTag));
        return stack;
    }

    Optional<String> trackedBy(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        CompoundTag trackingTag = trackingTag(stack);
        if (trackingTag.isEmpty() || !trackingTag.contains(PLAYER_NAME_TAG)) {
            return Optional.empty();
        }
        return Optional.of(trackingTag.getString(PLAYER_NAME_TAG));
    }

    boolean isFrom(ItemStack stack, UUID playerId) {
        CompoundTag trackingTag = trackingTag(stack);
        return !trackingTag.isEmpty() && trackingTag.hasUUID(PLAYER_TAG) && trackingTag.getUUID(PLAYER_TAG).equals(playerId);
    }

    void stripTracking(ItemStack stack) {
        stripTracking(stack, "");
    }

    void stripTracking(ItemStack stack, String sourceKind) {
        if (stack.isEmpty()) {
            return;
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(this.itemTag)) {
            return;
        }

        CompoundTag tag = customData.copyTag();
        if (sourceKind != null && !sourceKind.isBlank()) {
            VillagerTakenItemTracker.markTakenFromVillager(stack, tag.getCompound(this.itemTag), sourceKind);
            customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            tag = customData.copyTag();
        }
        tag.remove(this.itemTag);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    boolean isSameTrackedItem(ItemStack stack, ItemStack target) {
        if (stack.isDamageableItem() || target.isDamageableItem()) {
            return ItemStack.isSameItem(stack, target);
        }
        return ItemStack.isSameItemSameComponents(strippedStack(stack), strippedStack(target));
    }

    ItemStack strippedStack(ItemStack stack) {
        ItemStack strippedStack = stack.copy();
        stripTracking(strippedStack);
        return strippedStack;
    }

    List<LedgerEntry> loadEntries(ServerLevel level, Villager villager) {
        List<LedgerEntry> entries = new ArrayList<>();
        ListTag tag = villager.getPersistentData().getList(this.ledgerTag, CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < tag.size(); index++) {
            CompoundTag entryTag = tag.getCompound(index);
            if (!entryTag.hasUUID(PLAYER_TAG)
                    || !entryTag.contains(PLAYER_NAME_TAG)
                    || !entryTag.contains(COUNT_TAG)
                    || (this.storesReputation && !entryTag.contains(REPUTATION_TAG))) {
                continue;
            }

            ItemStack stack = entryTag.contains(STACK_TAG, CompoundTag.TAG_COMPOUND)
                    ? ItemStack.parseOptional(level.registryAccess(), entryTag.getCompound(STACK_TAG))
                    : ItemStack.EMPTY;
            int count = entryTag.getInt(COUNT_TAG);
            int reputation = this.storesReputation ? entryTag.getInt(REPUTATION_TAG) : 0;
            if (!stack.isEmpty() && count > 0 && (!this.storesReputation || reputation > 0)) {
                entries.add(new LedgerEntry(
                        entryTag.getUUID(PLAYER_TAG),
                        entryTag.getString(PLAYER_NAME_TAG),
                        strippedStack(stack),
                        count,
                        reputation
                ));
            }
        }
        return entries;
    }

    void saveEntries(ServerLevel level, Villager villager, List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            villager.getPersistentData().remove(this.ledgerTag);
            return;
        }

        ListTag tag = new ListTag();
        for (LedgerEntry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(PLAYER_TAG, entry.playerId());
            entryTag.putString(PLAYER_NAME_TAG, entry.playerName());
            entryTag.put(STACK_TAG, entry.stack().saveOptional(level.registryAccess()));
            entryTag.putInt(COUNT_TAG, entry.count());
            if (this.storesReputation) {
                entryTag.putInt(REPUTATION_TAG, entry.reputation());
            }
            tag.add(entryTag);
        }
        villager.getPersistentData().put(this.ledgerTag, tag);
    }

    StackCount findMatchingStackCount(List<StackCount> counts, UUID playerId, ItemStack stack) {
        for (StackCount count : counts) {
            if (count.playerId().equals(playerId) && isSameTrackedItem(count.stack(), stack)) {
                return count;
            }
        }
        return null;
    }

    StackDecrease findMatchingDecrease(List<StackDecrease> decreases, UUID playerId, ItemStack stack) {
        for (StackDecrease decrease : decreases) {
            if (decrease.playerId().equals(playerId) && isSameTrackedItem(decrease.stack(), stack)) {
                return decrease;
            }
        }
        return null;
    }

    boolean containsMatchingStack(List<StackCount> counts, UUID playerId, ItemStack stack) {
        return findMatchingStackCount(counts, playerId, stack) != null;
    }

    private CompoundTag trackingTag(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(this.itemTag)) {
            return new CompoundTag();
        }
        return customData.copyTag().getCompound(this.itemTag);
    }

    record Snapshot(List<StackCount> counts) {
    }

    record StackCount(UUID playerId, ItemStack stack, Counter counter) {
        StackCount(UUID playerId, ItemStack stack, int count) {
            this(playerId, stack, new Counter(count));
        }

        int count() {
            return this.counter.value();
        }

        void add(int count) {
            this.counter.add(count);
        }
    }

    record StackDecrease(UUID playerId, ItemStack stack, Counter counter) {
        StackDecrease(UUID playerId, ItemStack stack, int count) {
            this(playerId, stack.copy(), new Counter(count));
        }

        int count() {
            return this.counter.value();
        }

        void remove(int count) {
            this.counter.add(-count);
        }
    }

    record LedgerEntry(UUID playerId, String playerName, ItemStack stack, Counter countCounter, Counter reputationCounter) {
        LedgerEntry(UUID playerId, String playerName, ItemStack stack, int count, int reputation) {
            this(playerId, playerName, stack.copy(), new Counter(count), new Counter(reputation));
        }

        int count() {
            return this.countCounter.value();
        }

        int reputation() {
            return this.reputationCounter.value();
        }

        void remove(int count) {
            this.countCounter.add(-count);
        }

        void remove(int count, int reputation) {
            this.countCounter.add(-count);
            this.reputationCounter.add(-reputation);
        }
    }

    private static final class Counter {
        private int value;

        private Counter(int value) {
            this.value = value;
        }

        private int value() {
            return this.value;
        }

        private void add(int amount) {
            this.value += amount;
        }
    }
}
