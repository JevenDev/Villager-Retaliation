package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class VillagerTradePaymentTracker {
    private static final String TRADE_LEDGER_TAG = "VillagerRetaliationTradePaymentLedger";
    private static final String TRADE_ITEM_TAG = "VillagerRetaliationTradePayment";
    private static final String PLAYER_TAG = "Player";
    private static final String PLAYER_NAME_TAG = "PlayerName";
    private static final String STACK_TAG = "Stack";
    private static final String COUNT_TAG = "Count";
    private static final int RETURNED_TRADE_PAYMENT_BASE_PENALTY = 25;
    private static final int RETURNED_TRADE_PAYMENT_PER_ITEM_PENALTY = 5;
    private static final int RETURNED_TRADE_PAYMENT_MAX_PENALTY = 120;
    private static final int STOLEN_TRADE_PAYMENT_BASE_PENALTY = 45;
    private static final int STOLEN_TRADE_PAYMENT_PER_ITEM_PENALTY = 7;
    private static final int STOLEN_TRADE_PAYMENT_MAX_PENALTY = 180;

    private VillagerTradePaymentTracker() {
    }

    public static void storeTradePayment(ServerLevel level, Villager villager, ServerPlayer player, ItemStack payment) {
        if (!isStorableTradePayment(payment)) {
            return;
        }

        ItemStack storedPayment = markTradePayment(payment.copy(), player);
        ItemStack remainder = VillagerInventoryContainer.addItem(villager, storedPayment);
        int storedCount = payment.getCount() - remainder.getCount();
        if (storedCount <= 0) {
            return;
        }

        List<TradeLedgerEntry> entries = loadEntries(level, villager);
        entries.add(new TradeLedgerEntry(player.getUUID(), player.getGameProfile().getName(), strippedTradePaymentStack(payment), storedCount));
        saveEntries(villager, entries);
    }

    public static boolean isInvalidStoredTradePayment(ItemStack stack) {
        return tradedBy(stack).isPresent() && !isStorableTradePayment(stack);
    }

    public static Optional<String> tradedBy(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        CompoundTag tradeTag = tradeTag(stack);
        if (tradeTag.isEmpty() || !tradeTag.contains(PLAYER_NAME_TAG)) {
            return Optional.empty();
        }
        return Optional.of(tradeTag.getString(PLAYER_NAME_TAG));
    }

    static boolean isStoredTradePaymentFrom(ItemStack stack, UUID playerId) {
        CompoundTag tradeTag = tradeTag(stack);
        return !tradeTag.isEmpty() && tradeTag.hasUUID(PLAYER_TAG) && tradeTag.getUUID(PLAYER_TAG).equals(playerId);
    }

    static boolean isSameTrackedTradePayment(ItemStack stack, ItemStack target) {
        if (stack.isDamageableItem() || target.isDamageableItem()) {
            return ItemStack.isSameItem(stack, target);
        }
        return ItemStack.isSameItemSameComponents(strippedTradePaymentStack(stack), strippedTradePaymentStack(target));
    }

    static TradePaymentSnapshot capture(ServerPlayer player, Villager villager) {
        reconcileMissingTradePayments(player, villager);

        List<TradeLedgerEntry> entries = loadEntries(player.serverLevel(), villager);
        List<TradePaymentStackCount> counts = new ArrayList<>();
        for (TradeLedgerEntry entry : entries) {
            if (containsMatchingStack(counts, entry.playerId(), entry.stack())) {
                continue;
            }
            counts.add(new TradePaymentStackCount(
                    entry.playerId(),
                    entry.stack().copy(),
                    VillagerInventoryContainer.countStoredTradePayment(villager, entry.playerId(), entry.stack())
            ));
        }
        return new TradePaymentSnapshot(counts);
    }

    static void applyTakenTradePaymentPenalties(ServerPlayer player, Villager villager, TradePaymentSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        List<TradePaymentDecrease> decreases = new ArrayList<>();
        for (TradePaymentStackCount count : snapshot.counts()) {
            int currentCount = VillagerInventoryContainer.countStoredTradePayment(villager, count.playerId(), count.stack());
            int removedCount = count.count() - currentCount;
            if (removedCount > 0) {
                decreases.add(new TradePaymentDecrease(count.playerId(), count.stack(), removedCount));
            }
        }
        applyDecreases(player, villager, decreases);
    }

    public static void stripTradePaymentTrackingFromPlayerInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            stripTradePaymentTracking(inventory.getItem(slot));
        }
        stripTradePaymentTracking(player.containerMenu.getCarried());
        inventory.setChanged();
    }

    private static void reconcileMissingTradePayments(ServerPlayer player, Villager villager) {
        List<TradeLedgerEntry> entries = loadEntries(player.serverLevel(), villager);
        List<TradePaymentStackCount> ledgerCounts = new ArrayList<>();
        for (TradeLedgerEntry entry : entries) {
            TradePaymentStackCount count = findMatchingStackCount(ledgerCounts, entry.playerId(), entry.stack());
            if (count == null) {
                ledgerCounts.add(new TradePaymentStackCount(entry.playerId(), entry.stack().copy(), entry.count()));
            } else {
                count.add(entry.count());
            }
        }

        List<TradePaymentDecrease> decreases = new ArrayList<>();
        for (TradePaymentStackCount count : ledgerCounts) {
            int currentCount = VillagerInventoryContainer.countStoredTradePayment(villager, count.playerId(), count.stack());
            int missingCount = count.count() - currentCount;
            if (missingCount > 0) {
                decreases.add(new TradePaymentDecrease(count.playerId(), count.stack(), missingCount));
            }
        }
        applyDecreases(player, villager, decreases);
    }

    private static void applyDecreases(ServerPlayer player, Villager villager, List<TradePaymentDecrease> decreases) {
        if (decreases.isEmpty()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        List<TradeLedgerEntry> entries = loadEntries(level, villager);
        boolean changed = false;
        ItemStack firstReturnedStack = ItemStack.EMPTY;
        int returnedCount = 0;
        boolean stolenPayment = false;
        for (TradeLedgerEntry entry : entries) {
            if (entry.count() <= 0) {
                continue;
            }

            TradePaymentDecrease decrease = findMatchingDecrease(decreases, entry.playerId(), entry.stack());
            if (decrease == null || decrease.count() <= 0) {
                continue;
            }

            int removedCount = Math.min(entry.count(), decrease.count());
            entry.remove(removedCount);
            decrease.remove(removedCount);
            boolean returnedByTrader = entry.playerId().equals(player.getUUID());
            int penalty = tradePaymentPenalty(returnedByTrader, removedCount);
            VillagerReputationManager.addTradePaymentReturnReputation(level, villager, player, -penalty);
            if (firstReturnedStack.isEmpty()) {
                firstReturnedStack = entry.stack().copy();
                returnedCount = removedCount;
            } else if (isSameTrackedTradePayment(firstReturnedStack, entry.stack())) {
                returnedCount += removedCount;
            }
            stolenPayment |= !returnedByTrader;
            changed = true;
        }

        if (changed) {
            entries.removeIf(entry -> entry.count() <= 0 || entry.stack().isEmpty());
            saveEntries(villager, entries);
            stripTradePaymentTrackingFromPlayerInventory(player);
            VillagerInteractionService.sendTradePaymentTakenBackDialogue(player, villager, firstReturnedStack, returnedCount, stolenPayment);
        }
    }

    private static int tradePaymentPenalty(boolean returnedByTrader, int removedCount) {
        if (returnedByTrader) {
            return Math.min(
                    RETURNED_TRADE_PAYMENT_MAX_PENALTY,
                    RETURNED_TRADE_PAYMENT_BASE_PENALTY + RETURNED_TRADE_PAYMENT_PER_ITEM_PENALTY * removedCount
            );
        }
        return Math.min(
                STOLEN_TRADE_PAYMENT_MAX_PENALTY,
                STOLEN_TRADE_PAYMENT_BASE_PENALTY + STOLEN_TRADE_PAYMENT_PER_ITEM_PENALTY * removedCount
        );
    }

    private static boolean containsMatchingStack(List<TradePaymentStackCount> counts, UUID playerId, ItemStack stack) {
        return findMatchingStackCount(counts, playerId, stack) != null;
    }

    private static TradePaymentStackCount findMatchingStackCount(List<TradePaymentStackCount> counts, UUID playerId, ItemStack stack) {
        for (TradePaymentStackCount count : counts) {
            if (count.playerId().equals(playerId) && isSameTrackedTradePayment(count.stack(), stack)) {
                return count;
            }
        }
        return null;
    }

    private static TradePaymentDecrease findMatchingDecrease(List<TradePaymentDecrease> decreases, UUID playerId, ItemStack stack) {
        for (TradePaymentDecrease decrease : decreases) {
            if (decrease.playerId().equals(playerId) && isSameTrackedTradePayment(decrease.stack(), stack)) {
                return decrease;
            }
        }
        return null;
    }

    private static List<TradeLedgerEntry> loadEntries(ServerLevel level, Villager villager) {
        List<TradeLedgerEntry> entries = new ArrayList<>();
        ListTag tag = villager.getPersistentData().getList(TRADE_LEDGER_TAG, CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < tag.size(); index++) {
            CompoundTag entryTag = tag.getCompound(index);
            if (!entryTag.hasUUID(PLAYER_TAG)
                    || !entryTag.contains(PLAYER_NAME_TAG)
                    || !entryTag.contains(COUNT_TAG)) {
                continue;
            }

            ItemStack stack = entryTag.contains(STACK_TAG, CompoundTag.TAG_COMPOUND)
                    ? ItemStack.parseOptional(level.registryAccess(), entryTag.getCompound(STACK_TAG))
                    : ItemStack.EMPTY;
            int count = entryTag.getInt(COUNT_TAG);
            if (!stack.isEmpty() && count > 0) {
                entries.add(new TradeLedgerEntry(
                        entryTag.getUUID(PLAYER_TAG),
                        entryTag.getString(PLAYER_NAME_TAG),
                        strippedTradePaymentStack(stack),
                        count
                ));
            }
        }
        return entries;
    }

    private static void saveEntries(Villager villager, List<TradeLedgerEntry> entries) {
        if (entries.isEmpty()) {
            villager.getPersistentData().remove(TRADE_LEDGER_TAG);
            return;
        }

        ListTag tag = new ListTag();
        for (TradeLedgerEntry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(PLAYER_TAG, entry.playerId());
            entryTag.putString(PLAYER_NAME_TAG, entry.playerName());
            entryTag.put(STACK_TAG, entry.stack().saveOptional(villager.level().registryAccess()));
            entryTag.putInt(COUNT_TAG, entry.count());
            tag.add(entryTag);
        }
        villager.getPersistentData().put(TRADE_LEDGER_TAG, tag);
    }

    private static ItemStack markTradePayment(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) {
            return stack;
        }

        CompoundTag tradeTag = new CompoundTag();
        tradeTag.putUUID(PLAYER_TAG, player.getUUID());
        tradeTag.putString(PLAYER_NAME_TAG, player.getGameProfile().getName());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TRADE_ITEM_TAG, tradeTag));
        return stack;
    }

    private static boolean isStorableTradePayment(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.EMERALD);
    }

    private static void stripTradePaymentTracking(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TRADE_ITEM_TAG)) {
            return;
        }

        CompoundTag tag = customData.copyTag();
        tag.remove(TRADE_ITEM_TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static ItemStack strippedTradePaymentStack(ItemStack stack) {
        ItemStack strippedStack = stack.copy();
        stripTradePaymentTracking(strippedStack);
        return strippedStack;
    }

    private static CompoundTag tradeTag(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TRADE_ITEM_TAG)) {
            return new CompoundTag();
        }
        return customData.copyTag().getCompound(TRADE_ITEM_TAG);
    }

    record TradePaymentSnapshot(List<TradePaymentStackCount> counts) {
    }

    private record TradePaymentStackCount(UUID playerId, ItemStack stack, Counter counter) {
        private TradePaymentStackCount(UUID playerId, ItemStack stack, int count) {
            this(playerId, stack, new Counter(count));
        }

        private int count() {
            return this.counter.value();
        }

        private void add(int count) {
            this.counter.add(count);
        }
    }

    private record TradePaymentDecrease(UUID playerId, ItemStack stack, Counter counter) {
        private TradePaymentDecrease(UUID playerId, ItemStack stack, int count) {
            this(playerId, stack.copy(), new Counter(count));
        }

        private int count() {
            return this.counter.value();
        }

        private void remove(int count) {
            this.counter.add(-count);
        }
    }

    private record TradeLedgerEntry(UUID playerId, String playerName, ItemStack stack, Counter countCounter) {
        private TradeLedgerEntry(UUID playerId, String playerName, ItemStack stack, int count) {
            this(playerId, playerName, stack.copy(), new Counter(count));
        }

        private int count() {
            return this.countCounter.value();
        }

        private void remove(int count) {
            this.countCounter.add(-count);
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
