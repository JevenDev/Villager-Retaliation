package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
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

public final class VillagerGiftReturnTracker {
    private static final String GIFT_LEDGER_TAG = "VillagerRetaliationGiftLedger";
    private static final String GIFT_ITEM_TAG = "VillagerRetaliationGift";
    private static final int RETURNED_GIFT_EXTRA_PENALTY = 10;
    private static final int STOLEN_GIFT_EXTRA_PENALTY = 25;
    private static final TrackedVillagerItemLedger LEDGER =
            new TrackedVillagerItemLedger(GIFT_LEDGER_TAG, GIFT_ITEM_TAG, true);

    private VillagerGiftReturnTracker() {
    }

    public static void recordStoredGift(ServerLevel level, Villager villager, ServerPlayer player, ItemStack stack, int reputationValue) {
        if (stack.isEmpty() || reputationValue <= 0) {
            return;
        }

        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(level, villager);
        entries.add(new TrackedVillagerItemLedger.LedgerEntry(
                player.getUUID(),
                player.getGameProfile().getName(),
                LEDGER.strippedStack(stack),
                stack.getCount(),
                reputationValue));
        saveEntries(level, villager, entries);
    }

    public static ItemStack markStoredGift(ItemStack stack, ServerPlayer player) {
        return LEDGER.mark(stack, player);
    }

    public static ItemStack markStoredGift(ItemStack stack, ServerPlayer player, Villager owner) {
        return LEDGER.mark(stack, player, owner);
    }

    public static Optional<String> giftedBy(ItemStack stack) {
        return LEDGER.trackedBy(stack);
    }

    static boolean isStoredGiftFrom(ItemStack stack, UUID playerId) {
        return LEDGER.isFrom(stack, playerId);
    }

    public static void stripGiftTracking(ItemStack stack) {
        LEDGER.stripTracking(stack);
    }

    private static void stripGiftTracking(ItemStack stack, String sourceKind) {
        LEDGER.stripTracking(stack, sourceKind);
    }

    static boolean isSameTrackedGiftItem(ItemStack stack, ItemStack target) {
        return LEDGER.isSameTrackedItem(stack, target);
    }

    static GiftSnapshot capture(ServerPlayer player, Villager villager) {
        pruneMissingGifts(player.serverLevel(), villager);

        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(player.serverLevel(), villager);
        List<TrackedVillagerItemLedger.StackCount> counts = new ArrayList<>();
        for (TrackedVillagerItemLedger.LedgerEntry entry : entries) {
            if (LEDGER.containsMatchingStack(counts, entry.playerId(), entry.stack())) {
                continue;
            }
            counts.add(new TrackedVillagerItemLedger.StackCount(
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

        List<TrackedVillagerItemLedger.StackDecrease> decreases = new ArrayList<>();
        for (TrackedVillagerItemLedger.StackCount count : snapshot.counts()) {
            int currentCount = VillagerInventoryContainer.countStoredGiftItem(villager, count.playerId(), count.stack());
            int playerReceivedCount =
                    countTrackedGiftInPlayerInventory(player, count.playerId(), count.stack());
            int removedCount = Math.min(count.count() - currentCount, playerReceivedCount);
            if (removedCount > 0) {
                decreases.add(new TrackedVillagerItemLedger.StackDecrease(count.playerId(), count.stack(), removedCount));
            }
        }
        applyDecreases(player, villager, decreases);
    }

    private static void pruneMissingGifts(ServerLevel level, Villager villager) {
        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(level, villager);
        List<TrackedVillagerItemLedger.StackCount> ledgerCounts = new ArrayList<>();
        for (TrackedVillagerItemLedger.LedgerEntry entry : entries) {
            TrackedVillagerItemLedger.StackCount count = LEDGER.findMatchingStackCount(ledgerCounts, entry.playerId(), entry.stack());
            if (count == null) {
                ledgerCounts.add(new TrackedVillagerItemLedger.StackCount(entry.playerId(), entry.stack().copy(), entry.count()));
            } else {
                count.add(entry.count());
            }
        }

        List<TrackedVillagerItemLedger.StackDecrease> missingGifts = new ArrayList<>();
        for (TrackedVillagerItemLedger.StackCount count : ledgerCounts) {
            int currentCount = VillagerInventoryContainer.countStoredGiftItem(villager, count.playerId(), count.stack());
            int missingCount = count.count() - currentCount;
            if (missingCount > 0) {
                missingGifts.add(new TrackedVillagerItemLedger.StackDecrease(count.playerId(), count.stack(), missingCount));
            }
        }
        if (missingGifts.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (TrackedVillagerItemLedger.LedgerEntry entry : entries) {
            TrackedVillagerItemLedger.StackDecrease missing =
                    LEDGER.findMatchingDecrease(missingGifts, entry.playerId(), entry.stack());
            if (missing == null || missing.count() <= 0) {
                continue;
            }

            int removedCount = Math.min(entry.count(), missing.count());
            entry.remove(removedCount, revokedReputation(entry, removedCount));
            missing.remove(removedCount);
            changed = true;
        }
        if (changed) {
            entries.removeIf(entry -> entry.count() <= 0 || entry.reputation() <= 0 || entry.stack().isEmpty());
            saveEntries(level, villager, entries);
        }
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
        boolean stolenGift = false;
        for (TrackedVillagerItemLedger.LedgerEntry entry : entries) {
            if (entry.count() <= 0 || entry.reputation() <= 0) {
                continue;
            }

            TrackedVillagerItemLedger.StackDecrease decrease = LEDGER.findMatchingDecrease(decreases, entry.playerId(), entry.stack());
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

    private static int revokedReputation(TrackedVillagerItemLedger.LedgerEntry entry, int removedCount) {
        if (removedCount >= entry.count()) {
            return entry.reputation();
        }
        return Math.max(1, entry.reputation() * removedCount / entry.count());
    }

    private static void stripGiftTrackingFromPlayerInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            stripGiftTracking(inventory.getItem(slot), "gift");
        }
        stripGiftTracking(player.containerMenu.getCarried(), "gift");
        inventory.setChanged();
    }

    private static void saveEntries(ServerLevel level, Villager villager, List<TrackedVillagerItemLedger.LedgerEntry> entries) {
        LEDGER.saveEntries(level, villager, entries);
    }

    private static int countTrackedGiftInPlayerInventory(
            ServerPlayer player, UUID giftGiverId, ItemStack target) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            count += matchingTrackedGiftCount(inventory.getItem(slot), giftGiverId, target);
        }
        count += matchingTrackedGiftCount(player.containerMenu.getCarried(), giftGiverId, target);
        return count;
    }

    private static int matchingTrackedGiftCount(ItemStack stack, UUID giftGiverId, ItemStack target) {
        return isStoredGiftFrom(stack, giftGiverId) && isSameTrackedGiftItem(stack, target)
                ? stack.getCount()
                : 0;
    }

    record GiftSnapshot(List<TrackedVillagerItemLedger.StackCount> counts) {
    }
}
