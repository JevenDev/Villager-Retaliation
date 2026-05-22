package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class VillagerGiftReturnTracker {
    private static final String GIFT_LEDGER_TAG = "VillagerRetaliationGiftLedger";
    private static final String GIFT_ITEM_TAG = "VillagerRetaliationGift";
    private static final String PLAYER_TAG = "Player";
    private static final String PLAYER_NAME_TAG = "PlayerName";
    private static final String STACK_TAG = "Stack";
    private static final String COUNT_TAG = "Count";
    private static final String REPUTATION_TAG = "Reputation";
    private static final int RETURNED_GIFT_EXTRA_PENALTY = 10;
    private static final int STOLEN_GIFT_EXTRA_PENALTY = 25;

    private VillagerGiftReturnTracker() {
    }

    public static void recordStoredGift(ServerLevel level, Villager villager, ServerPlayer player, ItemStack stack, int reputationValue) {
        if (stack.isEmpty() || reputationValue <= 0) {
            return;
        }

        List<GiftLedgerEntry> entries = loadEntries(level, villager);
        entries.add(new GiftLedgerEntry(player.getUUID(), player.getGameProfile().getName(), strippedGiftStack(stack), stack.getCount(), reputationValue));
        saveEntries(level, villager, entries);
    }

    public static ItemStack markStoredGift(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) {
            return stack;
        }

        CompoundTag giftTag = new CompoundTag();
        giftTag.putUUID(PLAYER_TAG, player.getUUID());
        giftTag.putString(PLAYER_NAME_TAG, player.getGameProfile().getName());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(GIFT_ITEM_TAG, giftTag));
        return stack;
    }

    public static Optional<String> giftedBy(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        CompoundTag giftTag = giftTag(stack);
        if (giftTag.isEmpty() || !giftTag.contains(PLAYER_NAME_TAG)) {
            return Optional.empty();
        }
        return Optional.of(giftTag.getString(PLAYER_NAME_TAG));
    }

    static boolean isStoredGiftFrom(ItemStack stack, UUID playerId) {
        CompoundTag giftTag = giftTag(stack);
        return !giftTag.isEmpty() && giftTag.hasUUID(PLAYER_TAG) && giftTag.getUUID(PLAYER_TAG).equals(playerId);
    }

    public static void stripGiftTracking(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(GIFT_ITEM_TAG)) {
            return;
        }

        CompoundTag tag = customData.copyTag();
        tag.remove(GIFT_ITEM_TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    static boolean isSameTrackedGiftItem(ItemStack stack, ItemStack target) {
        if (stack.isDamageableItem() || target.isDamageableItem()) {
            return ItemStack.isSameItem(stack, target);
        }
        return ItemStack.isSameItemSameComponents(strippedGiftStack(stack), strippedGiftStack(target));
    }

    static GiftSnapshot capture(ServerPlayer player, Villager villager) {
        reconcileMissingGifts(player, villager);

        List<GiftLedgerEntry> entries = loadEntries(player.serverLevel(), villager);
        List<GiftStackCount> counts = new ArrayList<>();
        for (GiftLedgerEntry entry : entries) {
            if (containsMatchingStack(counts, entry.playerId(), entry.stack())) {
                continue;
            }
            counts.add(new GiftStackCount(
                    entry.playerId(),
                    entry.stack().copy(),
                    VillagerInventoryContainer.countStoredGiftItem(villager, entry.playerId(), entry.stack())
            ));
        }
        return new GiftSnapshot(counts);
    }

    static void applyTakenGiftPenalties(ServerPlayer player, Villager villager, GiftSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        List<GiftStackDecrease> decreases = new ArrayList<>();
        for (GiftStackCount count : snapshot.counts()) {
            int currentCount = VillagerInventoryContainer.countStoredGiftItem(villager, count.playerId(), count.stack());
            int removedCount = count.count() - currentCount;
            if (removedCount > 0) {
                decreases.add(new GiftStackDecrease(count.playerId(), count.stack(), removedCount));
            }
        }
        applyDecreases(player, villager, decreases);
    }

    private static void reconcileMissingGifts(ServerPlayer player, Villager villager) {
        List<GiftLedgerEntry> entries = loadEntries(player.serverLevel(), villager);
        List<GiftStackCount> ledgerCounts = new ArrayList<>();
        for (GiftLedgerEntry entry : entries) {
            if (!entry.playerId().equals(player.getUUID())) {
                continue;
            }

            GiftStackCount count = findMatchingStackCount(ledgerCounts, entry.playerId(), entry.stack());
            if (count == null) {
                ledgerCounts.add(new GiftStackCount(entry.playerId(), entry.stack().copy(), entry.count()));
            } else {
                count.add(entry.count());
            }
        }

        List<GiftStackDecrease> decreases = new ArrayList<>();
        for (GiftStackCount count : ledgerCounts) {
            int currentCount = VillagerInventoryContainer.countStoredGiftItem(villager, count.playerId(), count.stack());
            int missingCount = count.count() - currentCount;
            if (missingCount > 0) {
                decreases.add(new GiftStackDecrease(count.playerId(), count.stack(), missingCount));
            }
        }
        applyDecreases(player, villager, decreases);
    }

    private static void applyDecreases(ServerPlayer player, Villager villager, List<GiftStackDecrease> decreases) {
        if (decreases.isEmpty()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        List<GiftLedgerEntry> entries = loadEntries(level, villager);
        boolean changed = false;
        ItemStack firstReturnedStack = ItemStack.EMPTY;
        int returnedCount = 0;
        boolean stolenGift = false;
        for (GiftLedgerEntry entry : entries) {
            if (entry.count() <= 0 || entry.reputation() <= 0) {
                continue;
            }

            GiftStackDecrease decrease = findMatchingDecrease(decreases, entry.playerId(), entry.stack());
            if (decrease == null || decrease.count() <= 0) {
                continue;
            }

            int removedCount = Math.min(entry.count(), decrease.count());
            int revokedReputation = revokedReputation(entry, removedCount);
            entry.remove(removedCount, revokedReputation);
            decrease.remove(removedCount);
            boolean returnedByGiver = entry.playerId().equals(player.getUUID());
            int extraPenalty = returnedByGiver
                    ? RETURNED_GIFT_EXTRA_PENALTY
                    : STOLEN_GIFT_EXTRA_PENALTY;
            VillagerReputationManager.addGiftReputation(
                    level,
                    villager,
                    player,
                    -(revokedReputation + extraPenalty)
            );
            VillagerReputationAdvancements.onGiftTakenBack(player);
            if (firstReturnedStack.isEmpty()) {
                firstReturnedStack = entry.stack().copy();
                returnedCount = removedCount;
            } else if (isSameTrackedGiftItem(firstReturnedStack, entry.stack())) {
                returnedCount += removedCount;
            }
            stolenGift |= !returnedByGiver;
            changed = true;
        }

        if (changed) {
            entries.removeIf(entry -> entry.count() <= 0 || entry.reputation() <= 0 || entry.stack().isEmpty());
            saveEntries(level, villager, entries);
            stripGiftTrackingFromPlayerInventory(player);
            VillagerInteractionService.sendGiftTakenBackDialogue(player, villager, firstReturnedStack, returnedCount, stolenGift);
        }
    }

    private static int revokedReputation(GiftLedgerEntry entry, int removedCount) {
        if (removedCount >= entry.count()) {
            return entry.reputation();
        }
        return Math.max(1, entry.reputation() * removedCount / entry.count());
    }

    private static boolean containsMatchingStack(List<GiftStackCount> counts, UUID playerId, ItemStack stack) {
        return findMatchingStackCount(counts, playerId, stack) != null;
    }

    private static GiftStackCount findMatchingStackCount(List<GiftStackCount> counts, UUID playerId, ItemStack stack) {
        for (GiftStackCount count : counts) {
            if (count.playerId().equals(playerId) && isSameTrackedGiftItem(count.stack(), stack)) {
                return count;
            }
        }
        return null;
    }

    private static GiftStackDecrease findMatchingDecrease(List<GiftStackDecrease> decreases, UUID playerId, ItemStack stack) {
        for (GiftStackDecrease decrease : decreases) {
            if (decrease.playerId().equals(playerId) && isSameTrackedGiftItem(decrease.stack(), stack)) {
                return decrease;
            }
        }
        return null;
    }

    private static List<GiftLedgerEntry> loadEntries(ServerLevel level, Villager villager) {
        List<GiftLedgerEntry> entries = new ArrayList<>();
        ListTag tag = villager.getPersistentData().getList(GIFT_LEDGER_TAG, CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < tag.size(); index++) {
            CompoundTag entryTag = tag.getCompound(index);
            if (!entryTag.hasUUID(PLAYER_TAG)
                    || !entryTag.contains(PLAYER_NAME_TAG)
                    || !entryTag.contains(COUNT_TAG)
                    || !entryTag.contains(REPUTATION_TAG)) {
                continue;
            }

            ItemStack stack = entryTag.contains(STACK_TAG, CompoundTag.TAG_COMPOUND)
                    ? ItemStack.parseOptional(level.registryAccess(), entryTag.getCompound(STACK_TAG))
                    : ItemStack.EMPTY;
            int count = entryTag.getInt(COUNT_TAG);
            int reputation = entryTag.getInt(REPUTATION_TAG);
            if (!stack.isEmpty() && count > 0 && reputation > 0) {
                entries.add(new GiftLedgerEntry(
                        entryTag.getUUID(PLAYER_TAG),
                        entryTag.getString(PLAYER_NAME_TAG),
                        strippedGiftStack(stack),
                        count,
                        reputation
                ));
            }
        }
        return entries;
    }

    private static void saveEntries(ServerLevel level, Villager villager, List<GiftLedgerEntry> entries) {
        if (entries.isEmpty()) {
            villager.getPersistentData().remove(GIFT_LEDGER_TAG);
            return;
        }

        ListTag tag = new ListTag();
        for (GiftLedgerEntry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(PLAYER_TAG, entry.playerId());
            entryTag.putString(PLAYER_NAME_TAG, entry.playerName());
            entryTag.put(STACK_TAG, entry.stack().saveOptional(level.registryAccess()));
            entryTag.putInt(COUNT_TAG, entry.count());
            entryTag.putInt(REPUTATION_TAG, entry.reputation());
            tag.add(entryTag);
        }
        villager.getPersistentData().put(GIFT_LEDGER_TAG, tag);
    }

    private static void stripGiftTrackingFromPlayerInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            stripGiftTracking(inventory.getItem(slot));
        }
        stripGiftTracking(player.containerMenu.getCarried());
        inventory.setChanged();
    }

    private static ItemStack strippedGiftStack(ItemStack stack) {
        ItemStack strippedStack = stack.copy();
        stripGiftTracking(strippedStack);
        return strippedStack;
    }

    private static CompoundTag giftTag(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(GIFT_ITEM_TAG)) {
            return new CompoundTag();
        }
        return customData.copyTag().getCompound(GIFT_ITEM_TAG);
    }

    record GiftSnapshot(List<GiftStackCount> counts) {
    }

    private record GiftStackCount(UUID playerId, ItemStack stack, Counter counter) {
        private GiftStackCount(UUID playerId, ItemStack stack, int count) {
            this(playerId, stack, new Counter(count));
        }

        private int count() {
            return this.counter.value();
        }

        private void add(int count) {
            this.counter.add(count);
        }
    }

    private record GiftStackDecrease(UUID playerId, ItemStack stack, Counter counter) {
        private GiftStackDecrease(UUID playerId, ItemStack stack, int count) {
            this(playerId, stack.copy(), new Counter(count));
        }

        private int count() {
            return this.counter.value();
        }

        private void remove(int count) {
            this.counter.add(-count);
        }
    }

    private record GiftLedgerEntry(UUID playerId, String playerName, ItemStack stack, Counter countCounter, Counter reputationCounter) {
        private GiftLedgerEntry(UUID playerId, String playerName, ItemStack stack, int count, int reputation) {
            this(playerId, playerName, stack.copy(), new Counter(count), new Counter(reputation));
        }

        private int count() {
            return this.countCounter.value();
        }

        private int reputation() {
            return this.reputationCounter.value();
        }

        private void remove(int count, int reputation) {
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
