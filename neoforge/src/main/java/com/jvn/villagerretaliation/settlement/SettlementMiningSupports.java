package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningExcavationShaft;
import com.jvn.villagerretaliation.interaction.work.mining.MiningSupportManager;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorkerState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

final class SettlementMiningSupports {
    private SettlementMiningSupports() {
    }

    static Result tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, SettlementMine mine, SettlementMiningView view,
                       Villager owner, HiredWorkContext context, BlockPos origin, Integer layer,
                       List<SettlementDepot> depots, SettlementWorkBudget budget, SettlementMiningRoutes.Budget routeBudget) {
        int current = layer == null ? context.workMin().getY() : layer;
        if (!MiningExcavationShaft.requiresLadder(view, context, current)) return Result.NONE;
        var storage = depots.stream().min(Comparator.comparingDouble(depot -> origin.distSqr(depot.position())))
                .map(SettlementDepot::position).orElse(null);
        var shaft = MiningExcavationShaft.select(view, context, storage, current);
        if (shaft == null) {
            worker.status("no_excavation_shaft");
            return Result.WAITING;
        }
        if (!MiningExcavationShaft.hasCompleteRouteToLayer(view, context, current)) {
            if (find(context, worker.supplySlots(), stack -> stack.is(Items.LADDER)) < 0) {
                return fetch(level, town, worker, context, origin, depots, budget, stack -> stack.is(Items.LADDER), "ladders", true);
            }
            int floor = MiningExcavationShaft.supportFloorY(view, context, current);
            for (int y = MiningExcavationShaft.topY(view, context, shaft); y >= floor; y--) {
                var position = shaft.at(y);
                if (mine.getBlockState(position).is(Blocks.LADDER)) continue;
                if (!mine.getBlockState(position).isAir()) break;
                return place(level, town, worker, mine, view, owner, context, origin, position, shaft.ladderState(),
                        Items.LADDER, depots, budget, routeBudget, true);
            }
        }
        for (int y = context.workMax().getY(); y >= current; y--) {
            if (!MiningSupportManager.isTorchLayer(context, y)) continue;
            for (var placement : MiningSupportManager.torchPlacements(context, y)) {
                if (shaft.contains(placement.pos()) || !mine.getBlockState(placement.pos()).isAir()) continue;
                var state = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, placement.facing());
                if (!MiningSupportManager.canEventuallyPlace(view, context, placement.pos(), state)) continue;
                if (find(context, worker.supplySlots(), stack -> stack.is(Items.TORCH)) < 0) {
                    return fetch(level, town, worker, context, origin, depots, budget, stack -> stack.is(Items.TORCH), "torches", false);
                }
                var result = place(level, town, worker, mine, view, owner, context, origin, placement.pos(), state,
                        Items.TORCH, depots, budget, routeBudget, false);
                if (result != Result.NONE) return result;
            }
        }
        return Result.NONE;
    }

    private static Result place(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, SettlementMine mine, SettlementMiningView view,
                                Villager owner, HiredWorkContext context, BlockPos origin, BlockPos position, BlockState state,
                                Item item, List<SettlementDepot> depots, SettlementWorkBudget budget,
                                SettlementMiningRoutes.Budget routeBudget, boolean required) {
        var routes = SettlementMiningRoutes.forSupport(worker, mine, origin);
        var target = routes.target(owner, position, routeBudget);
        if (target == null) {
            if (!required && !routes.pending(position)) return Result.NONE;
            worker.status(routes.pending(position) ? "searching_support_route" : "mining_support_unreachable");
            return Result.WAITING;
        }
        var destination = position;
        int slot;
        boolean backing = !MiningSupportManager.canPlace(view, position, state);
        if (backing) {
            var need = MiningSupportManager.backingNeed(view, context, position, state);
            if (need == null) {
                if (!required) return Result.NONE;
                worker.status("mining_support_blocked");
                return Result.WAITING;
            }
            Predicate<ItemStack> suitable = stack -> MiningSupportManager.canUseBacking(view, need.backingPos(), need.supportFace(), stack);
            var slots = new ArrayList<>(worker.outputSlots());
            slots.addAll(worker.supplySlots());
            slot = find(context, slots, suitable);
            if (slot < 0) return fetch(level, town, worker, context, origin, depots, budget, suitable, "backing", required);
            state = ((BlockItem) context.inventory().getItem(slot).getItem()).getBlock().defaultBlockState();
            destination = need.backingPos();
        } else {
            slot = find(context, worker.supplySlots(), stack -> stack.is(item));
            if (slot < 0) return Result.NONE;
        }
        double cost = 20.0D + 16.0D * target.steps();
        if (!SettlementBackgroundMining.ready(worker, target, budget, cost, "approaching_support", "placing_support")) return Result.WAITING;
        if (!mine.changeSupport(destination, state)) {
            worker.status("mine_conflict");
            return Result.WAITING;
        }
        budget.spend(cost);
        var updated = SettlementBackgroundTools.items(context.inventory());
        updated.get(slot).shrink(1);
        SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
        HiredWorkPlan.clear(context);
        AbstractBlockWorker.clearStoredTarget(context.state());
        MiningWorkerState.clearExcavationLayerCache(context);
        context.state().putLong(SettlementBackgroundMining.POSITION, target.stance().asLong());
        worker.status(backing ? "placed_mining_backing" : item == Items.LADDER ? "placed_ladder" : "placed_torch");
        return Result.PROGRESSED;
    }

    private static int find(HiredWorkContext context, List<Integer> slots, Predicate<ItemStack> predicate) {
        for (int slot : slots) {
            var stack = context.inventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return slot;
        }
        return -1;
    }

    private static Result fetch(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredWorkContext context, BlockPos origin,
                                List<SettlementDepot> depots, SettlementWorkBudget budget, Predicate<ItemStack> predicate,
                                String label, boolean required) {
        var updated = SettlementBackgroundTools.items(context.inventory());
        var supplies = SettlementProductionInventory.supplies(level, town, worker, updated, depots);
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (source.depot() == null || !predicate.test(source.available()) || !SettlementBackgroundTools.allowed(level, supplies, index)) continue;
            if (!SettlementProductionInventory.insertSupplies(worker, updated, List.of(source.available().copyWithCount(1)))) {
                if (!required) return Result.NONE;
                worker.status("support_inventory_full");
                return Result.WAITING;
            }
            if (!budget.spend(40.0D + 16.0D * Math.sqrt(origin.distSqr(source.depot().position())))) {
                worker.status("fetching_" + label);
                return Result.WAITING;
            }
            source.depot().removeItem(source.slot(), 1);
            SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
            worker.status("fetched_" + label);
            return Result.PROGRESSED;
        }
        if (!required) return Result.NONE;
        worker.status("missing_" + label);
        return Result.WAITING;
    }

    enum Result {
        NONE,
        PROGRESSED,
        WAITING
    }
}
