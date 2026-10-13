package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.ConnectedContainerResolver;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;

/** Checked write-ahead transfers between the settlement ledger and independently saved container chunks. */
final class SettlementConstructionTransfers {
    private static final Map<UUID, Checkpoint> CHECKPOINTS = new HashMap<>();
    private SettlementConstructionTransfers() { }

    static Container container(ServerLevel level, SettlementRecord town, SettlementStorageLinks.Link link) {
        if (!SettlementStorageService.permitted(level, town, HiredVillagerRole.BUILDER, link)
                || AssignedStorageService.containerPositions(level, link.position()).size() != 1) return null;
        // Only direct vanilla inventories participate: no opaque item handlers or split-chunk double chests.
        var block = level.getBlockEntity(link.position());
        if (!(block instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)
                && !(block instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity)) return null;
        return ConnectedContainerResolver.resolve(level, link.position());
    }

    static CompoundTag debit(ServerLevel level, BlockPos position, Container container, Map<Integer, Integer> slots, UUID id) {
        var operation = operation(level, position, id);
        var changes = new ListTag();
        for (var entry : slots.entrySet()) {
            var before = container.getItem(entry.getKey());
            var after = before.copyWithCount(before.getCount() - entry.getValue());
            changes.add(change(level, entry.getKey(), before, after));
        }
        operation.put("Changes", changes);
        return operation;
    }

    private static CompoundTag operation(ServerLevel level, BlockPos position, UUID id) {
        var operation = new CompoundTag();
        operation.putUUID("Id", id);
        operation.putLong("Position", position.asLong());
        operation.putString("Block", BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString());
        operation.put("Receipt", SettlementReceipt.issue(level, "construction_transfer", position).save());
        return operation;
    }

    private static CompoundTag change(ServerLevel level, int slot, ItemStack before, ItemStack after) {
        var tag = new CompoundTag();
        tag.putInt("Slot", slot);
        tag.put("Before", before.isEmpty() ? new CompoundTag() : before.save(level.registryAccess()));
        tag.put("After", after.isEmpty() ? new CompoundTag() : after.save(level.registryAccess()));
        return tag;
    }

    static boolean advanceDebits(ServerLevel level, SettlementRecord town, SettlementProject project, boolean cancelling) {
        boolean ready = true;
        var ledger = project.materialEscrow();
        for (var raw : ledger.getList("Debits", Tag.TAG_COMPOUND)) {
            var operation = (CompoundTag) raw;
            if (operation.getBoolean("Done")) continue;
            var position = BlockPos.of(operation.getLong("Position"));
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk == null) { ready = false; continue; }
            if (cancelling && !applied(chunk, operation)) {
                // The durable source snapshot never removed these items. Release the promise,
                // never refund a stack that remains in its original container.
                for (var value : ledger.getList("Lots", Tag.TAG_COMPOUND)) {
                    var lot = (CompoundTag) value;
                    if (!lot.getBoolean("Ordinary") || !operation.getUUID("Id").equals(lot.getUUID("Source"))) continue;
                    var line = ledger.getList("Quote", Tag.TAG_COMPOUND).getCompound(lot.getInt("Row"));
                    line.putInt("Refunded", line.getInt("Refunded") + lot.getInt("Remaining"));
                    lot.putInt("Remaining", 0);
                }
                operation.putBoolean("Done", true);
                town.changed();
            } else if (apply(level, town, project, operation)) {
                operation.putBoolean("Done", true);
                CHECKPOINTS.remove(operation.getUUID("Id"));
                town.changed();
            } else ready = false;
        }
        return ready;
    }

    private static boolean applied(LevelChunk chunk, CompoundTag operation) {
        return SettlementChunkReceipts.version(chunk, "ConstructionTransfer:" + operation.getUUID("Id"), operation.getUUID("Id"),
                SettlementReceipt.load(operation.getCompound("Receipt"))) >= 1L;
    }

    private static boolean apply(ServerLevel level, SettlementRecord town, SettlementProject project, CompoundTag operation) {
        if (SettlementSavedData.get(level).get(town.id()).orElse(null) != town || town.projects().get(project.id()) != project) return false;
        var position = BlockPos.of(operation.getLong("Position"));
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return false;
        UUID id = operation.getUUID("Id");
        if (!applied(chunk, operation)) {
            var link = new SettlementStorageLinks.Link(position, operation.getBoolean("Refund")
                    ? SettlementStorageLinks.Purpose.GENERAL : SettlementStorageLinks.Purpose.SUPPLY, 0);
            var container = container(level, town, link);
            if (container == null || !BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString().equals(operation.getString("Block"))) {
                project.materialEscrow().putString("Blocker", "material_transfer_source_unavailable");
                return false;
            }
            var rules = AssignedStorageService.observedContainerRules(level, position).orElse(null);
            if (rules == null) return false;
            var promised = new java.util.ArrayList<ItemStack>();
            for (var raw : operation.getList("Changes", Tag.TAG_COMPOUND)) {
                var change = (CompoundTag) raw;
                int slot = change.getInt("Slot");
                var before = ItemStack.parseOptional(level.registryAccess(), change.getCompound("Before"));
                var after = ItemStack.parseOptional(level.registryAccess(), change.getCompound("After"));
                if (slot < 0 || slot >= container.getContainerSize() || !ItemStack.matches(container.getItem(slot), before)) {
                    project.materialEscrow().putString("Blocker", "material_transfer_conflict");
                    return false;
                }
                var sample = operation.getBoolean("Refund") ? after : before;
                int count = Math.abs(after.getCount() - before.getCount());
                if (AssignedStorageService.observedTransferAllowance(level, position, container, rules, sample,
                        operation.getBoolean("Refund") ? VillagerFilterPolicy.TransferOperation.RECEIVE : VillagerFilterPolicy.TransferOperation.PROVIDE,
                        promised) < count) {
                    project.materialEscrow().putString("Blocker", "material_transfer_filter");
                    return false;
                }
                promised.add(sample.copyWithCount(count));
            }
            if (!SettlementConstructionJournal.persist(level)) {
                project.materialEscrow().putString("Blocker", "construction_checkpoint_failed");
                return false;
            }
            for (var raw : operation.getList("Changes", Tag.TAG_COMPOUND)) {
                var change = (CompoundTag) raw;
                container.setItem(change.getInt("Slot"), ItemStack.parseOptional(level.registryAccess(), change.getCompound("After")));
            }
            container.setChanged();
            SettlementChunkReceipts.applied(chunk, "ConstructionTransfer:" + id, id, 1L,
                    SettlementReceipt.load(operation.getCompound("Receipt")));
        }
        var checkpoint = CHECKPOINTS.get(id);
        if (checkpoint == null || checkpoint.chunk.get() != chunk) {
            checkpoint = new Checkpoint(new java.lang.ref.WeakReference<>(chunk), SettlementChunkCheckpoint.revision(chunk));
            CHECKPOINTS.put(id, checkpoint);
        }
        return SettlementChunkCheckpoint.durable(level, chunk, checkpoint.revision);
    }

    static void advanceRefunds(ServerLevel level, SettlementRecord town, SettlementProject project) {
        var ledger = project.materialEscrow();
        for (var raw : ledger.getList("Refunds", Tag.TAG_COMPOUND)) {
            var operation = (CompoundTag) raw;
            if (operation.getBoolean("Done")) continue;
            if (!apply(level, town, project, operation)) {
                var position = BlockPos.of(operation.getLong("Position"));
                var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                if (chunk != null && !applied(chunk, operation)) {
                    // The loaded chunk proves this intent has never delivered anything. Retire
                    // its stale slot plan while retaining the entire lot for source/fallback retry.
                    operation.putBoolean("Done", true);
                    operation.putBoolean("Abandoned", true);
                    CHECKPOINTS.remove(operation.getUUID("Id"));
                    town.changed();
                }
                continue;
            }
            var lot = ledger.getList("Lots", Tag.TAG_COMPOUND).getCompound(operation.getInt("Lot"));
            var line = ledger.getList("Quote", Tag.TAG_COMPOUND).getCompound(lot.getInt("Row"));
            int count = operation.getInt("Amount");
            if (count > lot.getInt("Remaining")) throw new IllegalStateException("Construction refund exceeds retained escrow");
            lot.putInt("Remaining", lot.getInt("Remaining") - count);
            line.putInt("Refunded", line.getInt("Refunded") + count);
            operation.putBoolean("Done", true);
            CHECKPOINTS.remove(operation.getUUID("Id"));
            town.changed();
        }
    }

    static boolean refundInFlight(SettlementProject project, int lot) {
        return project.materialEscrow().getList("Refunds", Tag.TAG_COMPOUND).stream().anyMatch(raw -> {
            var operation = (CompoundTag) raw;
            return !operation.getBoolean("Done") && operation.getInt("Lot") == lot;
        });
    }

    static boolean refund(ServerLevel level, SettlementRecord town, SettlementProject project, int lot,
                          SettlementStorageLinks.Link link, ItemStack offered) {
        var container = container(level, town, link);
        var rules = AssignedStorageService.observedContainerRules(level, link.position()).orElse(null);
        if (container == null || rules == null) return false;
        int allowed = AssignedStorageService.observedTransferAllowance(level, link.position(), container, rules, offered,
                VillagerFilterPolicy.TransferOperation.RECEIVE);
        int remaining = Math.min(offered.getCount(), allowed);
        if (remaining <= 0) return false;
        var changes = new ListTag();
        int accepted = 0;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            var before = container.getItem(slot);
            if (!container.canPlaceItem(slot, offered) || (pass == 0 ? before.isEmpty() || !ItemStack.isSameItemSameComponents(before, offered) : !before.isEmpty())) continue;
            int count = Math.min(remaining, Math.min(container.getMaxStackSize(), offered.getMaxStackSize()) - before.getCount());
            if (count <= 0) continue;
            changes.add(change(level, slot, before, offered.copyWithCount(before.getCount() + count)));
            accepted += count;
            remaining -= count;
        }
        if (accepted == 0) return false;
        var operation = operation(level, link.position(), UUID.randomUUID());
        operation.putBoolean("Refund", true);
        operation.putInt("Lot", lot);
        operation.putInt("Amount", accepted);
        operation.put("Changes", changes);
        var ledger = project.materialEscrow();
        if (!ledger.contains("Refunds", Tag.TAG_LIST)) ledger.put("Refunds", new ListTag());
        ledger.getList("Refunds", Tag.TAG_COMPOUND).add(operation);
        town.changed();
        advanceRefunds(level, town, project);
        return true;
    }

    static void validate(CompoundTag ledger) {
        var ids = new java.util.HashSet<UUID>();
        for (String name : List.of("Debits", "Refunds")) for (var raw : ledger.getList(name, Tag.TAG_COMPOUND)) {
            var operation = (CompoundTag) raw;
            if (!operation.hasUUID("Id") || !operation.contains("Position", Tag.TAG_LONG)
                    || !operation.contains("Changes", Tag.TAG_LIST) || operation.getList("Changes", Tag.TAG_COMPOUND).isEmpty()
                    || operation.getList("Changes", Tag.TAG_COMPOUND).size() > 54 || !ids.add(operation.getUUID("Id"))
                    || net.minecraft.resources.ResourceLocation.tryParse(operation.getString("Block")) == null)
                throw new IllegalArgumentException("Invalid construction storage transfer");
            SettlementReceipt.load(operation.getCompound("Receipt"));
            if (name.equals("Refunds") && (operation.getInt("Lot") < 0 || operation.getInt("Lot") >= ledger.getList("Lots", Tag.TAG_COMPOUND).size()
                    || operation.getInt("Amount") < 1 || operation.getInt("Amount") > 99)) throw new IllegalArgumentException("Invalid construction refund transfer");
            var slots = new java.util.HashSet<Integer>();
            for (var value : operation.getList("Changes", Tag.TAG_COMPOUND)) {
                var change = (CompoundTag) value;
                if (!change.contains("Before", Tag.TAG_COMPOUND) || !change.contains("After", Tag.TAG_COMPOUND)
                        || change.getInt("Slot") < 0 || change.getInt("Slot") >= 54 || !slots.add(change.getInt("Slot")))
                    throw new IllegalArgumentException("Invalid construction transfer slots");
            }
        }
    }

    private record Checkpoint(java.lang.ref.WeakReference<LevelChunk> chunk, long revision) { }
}
