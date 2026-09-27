package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

final class SettlementItemDropService {
    private static final java.util.Map<SettlementRecord, Integer> CURSORS = new java.util.WeakHashMap<>();

    private SettlementItemDropService() {
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        for (var drop : List.copyOf(town.itemDrops().values())) {
            if (delivered(level, town, drop) || !drop.advance(level, now)) town.removeItemDrop(drop.id());
            town.changed();
        }
        var drops = List.copyOf(town.itemDrops().values());
        if (drops.isEmpty()) return;
        int cursor = Math.floorMod(CURSORS.getOrDefault(town, 0), drops.size());
        int count = Math.min(64, drops.size());
        for (int index = 0; index < count; index++) materialize(level, town, drops.get((cursor + index) % drops.size()));
        CURSORS.put(town, (cursor + count) % drops.size());
    }

    static boolean materialize(ServerLevel level, SettlementRecord town, SettlementItemDrop drop) {
        if (!town.dimension().equals(level.dimension().location()) || level.isOutsideBuildHeight(drop.position())
                || !level.getWorldBorder().isWithinBounds(drop.position())) return false;
        var pos = new ChunkPos(drop.position());
        var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null || !level.areEntitiesLoaded(pos.toLong())) return false;
        if (delivered(level, town, drop)) {
            town.removeItemDrop(drop.id());
            return true;
        }
        var existing = SettlementAnimalBirthService.existing(level, drop.id());
        if (existing != null) {
            if (!(existing instanceof ItemEntity item) || !ItemStack.isSameItemSameComponents(item.getItem(), drop.item(level.registryAccess()))) return false;
        } else if (!level.addFreshEntity(drop.context(level))) {
            return false;
        }
        SettlementChunkReceipts.applied(chunk, receiptKey(town, drop), drop.id(), 1L);
        town.removeItemDrop(drop.id());
        var area = town.areas().get(drop.area());
        if (area != null && level.getEntity(drop.id()) instanceof ItemEntity item) {
            SettlementGroundItemService.observe(level, town, area, item);
        }
        return true;
    }

    private static boolean delivered(ServerLevel level, SettlementRecord town, SettlementItemDrop drop) {
        if (!town.dimension().equals(level.dimension().location())) return false;
        var pos = new ChunkPos(drop.position());
        var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        return chunk != null && SettlementChunkReceipts.version(chunk, receiptKey(town, drop), drop.id()) >= 1L;
    }

    private static String receiptKey(SettlementRecord town, SettlementItemDrop drop) {
        return "ItemDrop:" + town.id().value() + ":" + drop.id();
    }

    static boolean collect(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, Villager owner, HiredJobInventory inventory,
                           SettlementWorkBudget budget, Predicate<ItemStack> filter) {
        var area = town.areas().get(worker.area());
        if (area == null || !area.contains(worker.position())) return false;
        boolean unreachable = false;
        for (var drop : town.itemDrops().values().stream().filter(drop -> drop.area().equals(area.id()) && area.contains(drop.position()))
                .sorted(Comparator.comparingDouble(drop -> drop.position().distSqr(worker.position()))).toList()) {
            if (delivered(level, town, drop)) {
                town.removeItemDrop(drop.id());
                continue;
            }
            if (SettlementAnimalBirthService.existing(level, drop.id()) != null) continue;
            var stack = drop.item(level.registryAccess());
            if (!filter.test(stack) || HiredPathMemory.isAvoided(level, owner, drop.position())) continue;
            int steps = drop.steps(worker);
            if (steps < 0) steps = SettlementLivestockService.observedSteps(level, town, area, worker, drop.context(level));
            if (steps < 0) {
                unreachable = true;
                continue;
            }
            if (!ready(worker, inventory, budget, stack, steps)) return true;
            var remainder = inventory.insertOutput(stack.copy());
            owner.onItemPickup(drop.context(level));
            if (remainder.isEmpty()) town.removeItemDrop(drop.id());
            else {
                drop.remaining(remainder, level.registryAccess());
                town.changed();
            }
            worker.status("collected_animal_drops");
            return true;
        }
        if (SettlementGroundItemService.collect(level, town, worker, owner, inventory, budget, filter)) return true;
        if (unreachable) worker.status("animal_drop_unreachable");
        return unreachable;
    }

    static boolean ready(SettlementWorkerState worker, HiredJobInventory inventory, SettlementWorkBudget budget,
                                 ItemStack stack, int steps) {
        if (!inventory.canStoreOutputs(List.of(stack.copyWithCount(1)))) {
            worker.status("output_full");
            return false;
        }
        if (!budget.spend(20.0D + steps * 16.0D)) {
            worker.status("collecting_animal_drops");
            return false;
        }
        return true;
    }
}
