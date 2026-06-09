package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class VillagerConfiscatedStolenItemTracker {
    private static final String LEDGER_TAG = "VillagerRetaliationConfiscatedStolenItemLedger";
    private static final String ITEM_TAG = "VillagerRetaliationConfiscatedStolenItem";
    private static final String OFFENSES_TAG = "VillagerRetaliationConfiscatedStolenItemOffenses";
    private static final String OFFENSE_PLAYER_TAG = "Player";
    private static final String OFFENSE_COUNT_TAG = "Count";
    private static final int FIRST_OFFENSE_REPUTATION_LOSS = 10;
    private static final int SECOND_OFFENSE_REPUTATION_LOSS = 18;
    private static final int AGGRO_OFFENSE_REPUTATION_LOSS = 25;
    private static final TrackedVillagerItemLedger LEDGER = new TrackedVillagerItemLedger(LEDGER_TAG, ITEM_TAG, false);
    private static final List<DialogueOptionDefinition> WARNING_OPTIONS = List.of(
            DialogueOptionDefinition.simple("leave", "Leave", DialogueRequestType.QUESTION, 0)
    );

    private VillagerConfiscatedStolenItemTracker() {
    }

    public static ItemStack markConfiscatedStolenItem(ItemStack stack, ServerPlayer player) {
        return LEDGER.mark(stack, player);
    }

    public static ItemStack markConfiscatedStolenItem(ItemStack stack, ServerPlayer player, Villager owner) {
        return LEDGER.mark(stack, player, owner);
    }

    public static void recordConfiscatedStolenItem(ServerLevel level, Villager villager, ServerPlayer player, ItemStack stack, int count) {
        if (stack.isEmpty() || count <= 0) {
            return;
        }

        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(level, villager);
        entries.add(new TrackedVillagerItemLedger.LedgerEntry(
                player.getUUID(),
                player.getGameProfile().getName(),
                LEDGER.strippedStack(stack),
                count,
                0));
        LEDGER.saveEntries(level, villager, entries);
    }

    public static Optional<String> stolenItemBy(ItemStack stack) {
        return LEDGER.trackedBy(stack);
    }

    static boolean isConfiscatedStolenItemFrom(ItemStack stack, UUID playerId) {
        return LEDGER.isFrom(stack, playerId);
    }

    static boolean isSameConfiscatedStolenItem(ItemStack stack, ItemStack target) {
        return LEDGER.isSameTrackedItem(stack, target);
    }

    static StolenItemSnapshot capture(ServerPlayer player, Villager villager) {
        reconcileMissingStolenItems(player, villager);

        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(player.serverLevel(), villager);
        List<TrackedVillagerItemLedger.StackCount> counts = new ArrayList<>();
        for (TrackedVillagerItemLedger.LedgerEntry entry : entries) {
            if (LEDGER.containsMatchingStack(counts, entry.playerId(), entry.stack())) {
                continue;
            }
            counts.add(new TrackedVillagerItemLedger.StackCount(
                    entry.playerId(),
                    entry.stack().copy(),
                    VillagerInventoryContainer.countConfiscatedStolenItem(villager, entry.playerId(), entry.stack())
            ));
        }
        return new StolenItemSnapshot(counts);
    }

    static void applyTakenStolenItemPenalties(ServerPlayer player, Villager villager, StolenItemSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        List<TrackedVillagerItemLedger.StackDecrease> decreases = new ArrayList<>();
        for (TrackedVillagerItemLedger.StackCount count : snapshot.counts()) {
            int currentCount = VillagerInventoryContainer.countConfiscatedStolenItem(villager, count.playerId(), count.stack());
            int removedCount = count.count() - currentCount;
            if (removedCount > 0) {
                decreases.add(new TrackedVillagerItemLedger.StackDecrease(count.playerId(), count.stack(), removedCount));
            }
        }
        applyDecreases(player, villager, decreases);
    }

    private static void reconcileMissingStolenItems(ServerPlayer player, Villager villager) {
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
            int currentCount = VillagerInventoryContainer.countConfiscatedStolenItem(villager, count.playerId(), count.stack());
            int missingCount = count.count() - currentCount;
            if (missingCount > 0) {
                decreases.add(new TrackedVillagerItemLedger.StackDecrease(count.playerId(), count.stack(), missingCount));
            }
        }
        applyLedgerOnlyDecreases(player.serverLevel(), villager, decreases);
    }

    private static void applyDecreases(ServerPlayer player, Villager villager, List<TrackedVillagerItemLedger.StackDecrease> decreases) {
        if (decreases.isEmpty()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(level, villager);
        boolean changed = false;
        ItemStack firstTakenStack = ItemStack.EMPTY;
        int takenCount = 0;
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
            if (firstTakenStack.isEmpty()) {
                firstTakenStack = entry.stack().copy();
                takenCount = removedCount;
            } else if (isSameConfiscatedStolenItem(firstTakenStack, entry.stack())) {
                takenCount += removedCount;
            }
            changed = true;
        }

        if (!changed) {
            return;
        }

        entries.removeIf(entry -> entry.count() <= 0 || entry.stack().isEmpty());
        LEDGER.saveEntries(level, villager, entries);
        stripStolenItemTrackingFromPlayerInventory(player);
        punishStolenItemTaken(player, villager, firstTakenStack, takenCount);
    }

    private static void applyLedgerOnlyDecreases(
            ServerLevel level,
            Villager villager,
            List<TrackedVillagerItemLedger.StackDecrease> decreases) {
        if (decreases.isEmpty()) {
            return;
        }

        List<TrackedVillagerItemLedger.LedgerEntry> entries = LEDGER.loadEntries(level, villager);
        boolean changed = false;
        for (TrackedVillagerItemLedger.LedgerEntry entry : entries) {
            TrackedVillagerItemLedger.StackDecrease decrease = LEDGER.findMatchingDecrease(decreases, entry.playerId(), entry.stack());
            if (decrease == null || decrease.count() <= 0) {
                continue;
            }

            int removedCount = Math.min(entry.count(), decrease.count());
            entry.remove(removedCount);
            decrease.remove(removedCount);
            changed = true;
        }
        if (changed) {
            entries.removeIf(entry -> entry.count() <= 0 || entry.stack().isEmpty());
            LEDGER.saveEntries(level, villager, entries);
        }
    }

    private static void punishStolenItemTaken(ServerPlayer player, Villager villager, ItemStack stack, int count) {
        ServerLevel level = player.serverLevel();
        int offenseCount = incrementOffenseCount(villager, player.getUUID());
        int reputationLoss = offenseCount >= 3
                ? AGGRO_OFFENSE_REPUTATION_LOSS
                : offenseCount == 2 ? SECOND_OFFENSE_REPUTATION_LOSS : FIRST_OFFENSE_REPUTATION_LOSS;
        if (VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addDialogueReputation(level, villager, player, -reputationLoss);
        }

        String line = warningLine(level, player, villager, offenseCount, stack.copyWithCount(Math.max(1, count)));
        if (offenseCount >= 3) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    level,
                    villager,
                    line,
                    VillagerInteractionService.villagerSpeakerLabel(villager)
            );
            VillagerRetaliationHandler.forceAnger(villager, player);
            return;
        }

        level.getServer().execute(() -> openWarningDialogue(player, villager, line));
    }

    private static void openWarningDialogue(ServerPlayer player, Villager villager, String line) {
        if (!player.isAlive()
                || player.isSpectator()
                || !villager.isAlive()
                || !VillagerInteractionService.canUseForcedInteractionSystem(player, villager)) {
            return;
        }
        if (!VillagerInteractionService.openForcedDialogue(player, villager, line, WARNING_OPTIONS, true)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
        }
    }

    private static String warningLine(ServerLevel level, ServerPlayer player, Villager villager, int offenseCount, ItemStack stack) {
        return VillagerDialogueResources.message(
                VillagerInteractionService.createDialogueContext(level, player, villager),
                warningMessageKey(offenseCount),
                Map.of("item_stack", itemName(stack))
        ).orElse("");
    }

    private static String warningMessageKey(int offenseCount) {
        if (offenseCount >= 3) {
            return "interaction.confiscated_stolen_item.aggro";
        }
        if (offenseCount == 2) {
            return "interaction.confiscated_stolen_item.second_offense";
        }
        return "interaction.confiscated_stolen_item.first_offense";
    }

    private static int incrementOffenseCount(Villager villager, UUID playerId) {
        ListTag offenses = villager.getPersistentData().getList(OFFENSES_TAG, CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < offenses.size(); index++) {
            CompoundTag offense = offenses.getCompound(index);
            if (!offense.hasUUID(OFFENSE_PLAYER_TAG) || !offense.getUUID(OFFENSE_PLAYER_TAG).equals(playerId)) {
                continue;
            }

            int count = offense.getInt(OFFENSE_COUNT_TAG) + 1;
            offense.putInt(OFFENSE_COUNT_TAG, count);
            villager.getPersistentData().put(OFFENSES_TAG, offenses);
            return count;
        }

        CompoundTag offense = new CompoundTag();
        offense.putUUID(OFFENSE_PLAYER_TAG, playerId);
        offense.putInt(OFFENSE_COUNT_TAG, 1);
        offenses.add(offense);
        villager.getPersistentData().put(OFFENSES_TAG, offenses);
        return 1;
    }

    private static void stripStolenItemTrackingFromPlayerInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            stripStolenItemTracking(inventory.getItem(slot), "confiscated_stolen_item");
        }
        stripStolenItemTracking(player.containerMenu.getCarried(), "confiscated_stolen_item");
        inventory.setChanged();
    }

    private static void stripStolenItemTracking(ItemStack stack) {
        LEDGER.stripTracking(stack);
    }

    private static void stripStolenItemTracking(ItemStack stack, String sourceKind) {
        LEDGER.stripTracking(stack, sourceKind);
    }

    private static String itemName(ItemStack stack) {
        String name = stack.getHoverName().getString();
        return stack.getCount() > 1 ? stack.getCount() + "x " + name : name;
    }

    record StolenItemSnapshot(List<TrackedVillagerItemLedger.StackCount> counts) {
    }
}
