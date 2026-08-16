package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class VillagerTradePaymentTracker {
    private static final String TRADE_LEDGER_TAG = "VillagerRetaliationTradePaymentLedger";
    private static final String TRADE_ITEM_TAG = "VillagerRetaliationTradePayment";
    private static final int RETURNED_TRADE_PAYMENT_BASE_PENALTY = 25;
    private static final int RETURNED_TRADE_PAYMENT_PER_ITEM_PENALTY = 5;
    private static final int RETURNED_TRADE_PAYMENT_MAX_PENALTY = 120;
    private static final int STOLEN_TRADE_PAYMENT_BASE_PENALTY = 45;
    private static final int STOLEN_TRADE_PAYMENT_PER_ITEM_PENALTY = 7;
    private static final int STOLEN_TRADE_PAYMENT_MAX_PENALTY = 180;
    private static final TrackedVillagerItemLedger LEDGER =
            new TrackedVillagerItemLedger(TRADE_LEDGER_TAG, TRADE_ITEM_TAG, false);

    private VillagerTradePaymentTracker() {
    }

    public static void storeTradePayment(ServerLevel level, Villager villager, ServerPlayer player, ItemStack payment) {
        if (!isStorableTradePayment(level, payment)) {
            return;
        }

        ItemStack storedPayment = markTradePayment(payment.copy(), player, villager);
        ItemStack remainder = VillagerInventoryContainer.addItem(villager, storedPayment);
        int storedCount = payment.getCount() - remainder.getCount();
        if (storedCount <= 0) {
            return;
        }

        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(level, villager);
        entries.add(new TrackedVillagerItemLedger.LedgerEntry(
                player.getUUID(),
                player.getGameProfile().getName(),
                LEDGER.strippedStack(payment),
                storedCount,
                0));
        saveEntries(level, villager, entries);
    }

    public static boolean isInvalidStoredTradePayment(ItemStack stack) {
        return false;
    }

    public static Optional<String> tradedBy(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        return LEDGER.trackedBy(stack);
    }

    static boolean isStoredTradePaymentFrom(ItemStack stack, UUID playerId) {
        return LEDGER.isFrom(stack, playerId);
    }

    static boolean isSameTrackedTradePayment(ItemStack stack, ItemStack target) {
        return LEDGER.isSameTrackedItem(stack, target);
    }

    static TradePaymentSnapshot capture(ServerPlayer player, Villager villager) {
        reconcileMissingTradePayments(player, villager);

        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(player.serverLevel(), villager);
        List<TrackedVillagerItemLedger.StackCount> counts = new ArrayList<>();
        for (TrackedVillagerItemLedger.LedgerEntry entry : entries) {
            if (LEDGER.containsMatchingStack(counts, entry.playerId(), entry.stack())) {
                continue;
            }
            counts.add(new TrackedVillagerItemLedger.StackCount(
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

        List<TrackedVillagerItemLedger.StackDecrease> decreases = new ArrayList<>();
        for (TrackedVillagerItemLedger.StackCount count : snapshot.counts()) {
            int currentCount = VillagerInventoryContainer.countStoredTradePayment(villager, count.playerId(), count.stack());
            int removedCount = count.count() - currentCount;
            if (removedCount > 0) {
                decreases.add(new TrackedVillagerItemLedger.StackDecrease(count.playerId(), count.stack(), removedCount));
            }
        }
        applyDecreases(player, villager, decreases);
    }

    public static void stripTradePaymentTrackingFromPlayerInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            stripTradePaymentTracking(inventory.getItem(slot), "trade_payment");
        }
        stripTradePaymentTracking(player.containerMenu.getCarried(), "trade_payment");
        inventory.setChanged();
    }

    private static void reconcileMissingTradePayments(ServerPlayer player, Villager villager) {
        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(player.serverLevel(), villager);
        List<TrackedVillagerItemLedger.StackCount> ledgerCounts = new ArrayList<>();
        for (TrackedVillagerItemLedger.LedgerEntry entry : entries) {
            TrackedVillagerItemLedger.StackCount count = LEDGER.findMatchingStackCount(ledgerCounts, entry.playerId(), entry.stack());
            if (count == null) {
                ledgerCounts.add(new TrackedVillagerItemLedger.StackCount(entry.playerId(), entry.stack().copy(), entry.count()));
            } else {
                count.add(entry.count());
            }
        }

        List<TrackedVillagerItemLedger.StackDecrease> decreases = new ArrayList<>();
        for (TrackedVillagerItemLedger.StackCount count : ledgerCounts) {
            int currentCount = VillagerInventoryContainer.countStoredTradePayment(villager, count.playerId(), count.stack());
            int missingCount = count.count() - currentCount;
            if (missingCount > 0) {
                decreases.add(new TrackedVillagerItemLedger.StackDecrease(count.playerId(), count.stack(), missingCount));
            }
        }
        applyDecreases(player, villager, decreases);
    }

    private static void applyDecreases(ServerPlayer player, Villager villager, List<TrackedVillagerItemLedger.StackDecrease> decreases) {
        if (decreases.isEmpty()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(level, villager);
        boolean changed = false;
        ItemStack firstReturnedStack = ItemStack.EMPTY;
        int returnedCount = 0;
        boolean stolenPayment = false;
        for (TrackedVillagerItemLedger.LedgerEntry entry : entries) {
            if (entry.count() <= 0) {
                continue;
            }

            TrackedVillagerItemLedger.StackDecrease decrease = LEDGER.findMatchingDecrease(decreases, entry.playerId(), entry.stack());
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
            saveEntries(level, villager, entries);
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

    private static void saveEntries(ServerLevel level, Villager villager, List<TrackedVillagerItemLedger.LedgerEntry> entries) {
        LEDGER.saveEntries(level, villager, entries);
    }

    private static ItemStack markTradePayment(ItemStack stack, ServerPlayer player) {
        return LEDGER.mark(stack, player);
    }

    private static ItemStack markTradePayment(ItemStack stack, ServerPlayer player, Villager owner) {
        return LEDGER.mark(stack, player, owner);
    }

    private static boolean isStorableTradePayment(ServerLevel level, ItemStack stack) {
        return VillagerCurrencyResources.isCurrency(level.getServer(), stack);
    }

    private static void stripTradePaymentTracking(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        LEDGER.stripTracking(stack);
    }

    private static void stripTradePaymentTracking(ItemStack stack, String sourceKind) {
        if (stack.isEmpty()) {
            return;
        }

        LEDGER.stripTracking(stack, sourceKind);
    }

    record TradePaymentSnapshot(List<TrackedVillagerItemLedger.StackCount> counts) {
    }
}
