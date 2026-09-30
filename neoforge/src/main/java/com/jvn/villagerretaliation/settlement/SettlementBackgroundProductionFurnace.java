package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.HiredProcessingRecipeFilter;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;

final class SettlementBackgroundProductionFurnace {
    private SettlementBackgroundProductionFurnace() {
    }

    static boolean tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var queue = town.productionQueues().get(worker.area());
        var stations = HiredProductionStations.get(level);
        if (queue == null || stations.readOnly()) {
            worker.status("production_station_recovery_required");
            return true;
        }
        AssignedStorageService.releaseProductionInputs(worker.id());
        var depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        if ((!worker.settings().contains("AutoDepositOutputs") || worker.settings().getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) return true;
        var area = town.areas().get(worker.area());
        var machines = town.furnaces().values().stream().filter(machine -> SettlementFurnaceService.serves(area, machine))
                .filter(machine -> SettlementWorksiteService.links(town, worker.area()).permitsStation(machine.position()))
                .sorted(Comparator.comparingDouble(machine -> machine.position().distSqr(worker.position()))).toList();
        var construction = new SettlementConstructionMachines(level, town, null);
        var allocation = queue.allocation(worker.id());
        if (allocation == null) {
            for (var orphan : queue.allocations()) {
                if (orphan.started() && orphan.worker() == null && machines.stream().anyMatch(machine -> machine.position().equals(orphan.station()))
                        && queue.resume(orphan.id(), worker.id())) {
                    allocation = queue.allocation(worker.id());
                    break;
                }
            }
        }
        if (allocation != null && allocation.started()) {
            var active = allocation;
            var machine = machines.stream().filter(candidate -> candidate.position().equals(active.station())).findFirst().orElse(null);
            if (machine == null || !SettlementFurnaceService.available(level, machine, construction)) {
                var claim = allocation.station() == null ? null : stations.claim(level, allocation.station());
                if (allocation.station() != null && level.hasChunkAt(allocation.station())
                        && (!(level.getBlockEntity(allocation.station()) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)
                            || claim != null && !claim.machine().equals(SettlementFurnaceState.identity(furnace)))) {
                    stations.release(allocation.id());
                    queue.lost(allocation.id());
                    worker.resetWork();
                    queue.blocker(allocation.order(), "station_removed");
                    worker.status("station_removed");
                    return true;
                }
                worker.status("station_unavailable");
                queue.blocker(allocation.order(), "station_unavailable");
                return true;
            }
            return serviceBatch(level, town, worker, queue, allocation, machine, depots, elapsed, multiplier);
        }
        if (allocation == null && SettlementBackgroundSmelting.finishExisting(level, town, worker, elapsed, multiplier)) return true;
        var carried = worker.items(level.registryAccess());
        var supplies = SettlementProductionInventory.supplies(level, town, worker, carried, depots);
        for (var order : queue.executionOrders()) {
            if (!order.recipe().supports(level, worker.role()) || !ItemStack.matches(order.output(), order.recipe().output(level))) {
                queue.blocker(order.id(), "recipe_unavailable");
                continue;
            }
            long stock = SettlementProductionStock.count(level, town, queue, order);
            int own = allocation != null && allocation.order().equals(order.id()) ? allocation.remaining() : 0;
            if (stock < 0L || queue.available(order.id(), stock) + own <= 0L) {
                queue.blocker(order.id(), stock < 0L ? "output_storage_unavailable" : "quantity_reserved_or_stock_met");
                continue;
            }
            boolean carrying = worker.outputSlots().stream().anyMatch(slot -> !carried.get(slot).isEmpty());
            if (carrying && !Objects.equals(HiredProductionStock.destination(worker.settings()), order.storage())) {
                queue.blocker(order.id(), "awaiting_output_delivery");
                continue;
            }
            if (worker.role() == HiredVillagerRole.COOK
                    && level.getRecipeManager().byKey(order.recipe().recipe()).orElseThrow().value() instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
                if (SettlementBackgroundCrafting.tickOrder(level, town, worker, elapsed, multiplier, order)) return true;
                allocation = queue.allocation(worker.id());
                continue;
            }
            for (var machine : machines) {
                if (!SettlementFurnaceService.available(level, machine, construction)) continue;
                var claim = stations.reconcile(level, machine.position(), machine.id());
                if (claim != null && (allocation == null || !claim.allocation().equals(allocation.id()))) continue;
                var items = machine.items(level.registryAccess());
                if (!items.getFirst().isEmpty() || !items.get(2).isEmpty()) continue;
                var source = supplies.stream().filter(supply -> allowedInput(level, machine, order, supply.available())).findFirst().orElse(null);
                if (source == null) continue;
                int batches = Math.min(16, Math.min(source.available().getCount(),
                        (int) ((queue.available(order.id(), stock) + own + order.output().getCount() - 1L) / order.output().getCount())));
                if (batches <= 0) continue;
                var consumed = new ArrayList<Integer>(java.util.Collections.nCopies(supplies.size(), 0));
                consumed.set(supplies.indexOf(source), batches);
                items.set(0, source.available().copyWithCount(batches));
                if (items.get(1).isEmpty() && machine.burnTime() <= 0 && !planFuel(machine, items, supplies, consumed)) continue;
                if (!SettlementProductionInventory.allowed(level, supplies, consumed, order.id())) continue;
                var preview = copy(carried);
                consumeCarried(preview, supplies, consumed);
                if (!SettlementProductionInventory.insertOutputs(worker, preview, List.of(order.output()))) {
                    queue.blocker(order.id(), "output_full");
                    continue;
                }
                if (allocation != null && (!allocation.order().equals(order.id()) || !machine.position().equals(allocation.station()))) {
                    stations.release(allocation.id());
                    queue.release(allocation.id());
                    allocation = null;
                }
                if (allocation == null) allocation = queue.reserve(worker.id(), order.id(), order.output().getCount(), batches, stock, machine.position());
                if (allocation == null || !stations.reserve(level, queue, allocation, machine.id())) continue;
                int required = allocation.remaining() / order.output().getCount();
                if (required != batches) {
                    releasePlanned(level, queue, worker);
                    allocation = null;
                    continue;
                }
                if (!worker.settings().hasUUID("FurnaceProductionAllocation")
                        || !worker.settings().getUUID("FurnaceProductionAllocation").equals(allocation.id())) {
                    worker.resetWork();
                    worker.settings().putUUID("FurnaceProductionAllocation", allocation.id());
                }
                HiredProductionStock.prepareDestination(worker.settings(), carrying, order);
                queue.blocker(order.id(), "");
                worker.work(elapsed, multiplier);
                if (!worker.spend(cost(worker, machine, supplies, consumed))) {
                    worker.status("supplying_furnace");
                    return true;
                }
                if (!queue.start(allocation.id())) {
                    releasePlanned(level, queue, worker);
                    return false;
                }
                commit(level, worker, machine, items, carried, supplies, consumed);
                stations.reserve(level, queue, queue.allocation(worker.id()), machine.id());
                worker.status("supplied_furnace");
                return true;
            }
            if (!queue.order(order.id()).blocker().equals("output_full")) queue.blocker(order.id(), "no_available_station_or_materials");
        }
        releasePlanned(level, queue, worker);
        worker.status("waiting_for_furnace");
        return true;
    }

    private static boolean serviceBatch(ServerLevel level, SettlementRecord town, SettlementWorkerState worker,
            HiredProductionQueue queue, HiredProductionQueue.Allocation allocation, SettlementFurnaceState machine,
            List<SettlementDepot> depots, int elapsed, double multiplier) {
        var order = queue.order(allocation.order());
        var stations = HiredProductionStations.get(level);
        if (!stations.reserve(level, queue, allocation, machine.id())) return blocked(worker, queue, order, "station_claim_changed");
        var items = machine.items(level.registryAccess());
        var carried = worker.items(level.registryAccess());
        boolean carrying = worker.outputSlots().stream().anyMatch(slot -> !carried.get(slot).isEmpty());
        if (!HiredProductionStock.prepareDestination(worker.settings(), carrying, order)) return blocked(worker, queue, order, "awaiting_output_delivery");
        if (!items.get(0).isEmpty() && !allowedInput(level, machine, order, items.get(0))) return blocked(worker, queue, order, "station_input_changed");
        if (!items.get(2).isEmpty() && !HiredProductionStock.matches(order.output(), items.get(2))) return blocked(worker, queue, order, "station_output_changed");
        int collect = !items.get(2).isEmpty() ? 2
                : !items.get(1).isEmpty() && items.get(1).getBurnTime(machine.recipeType()) <= 0 ? 1 : -1;
        if (collect >= 0) {
            var receipt = items.get(collect).copyWithCount(Math.min(16, Math.min(items.get(collect).getCount(), collect == 2 ? allocation.remaining() : 64)));
            if (!SettlementProductionInventory.insertOutputs(worker, carried, List.of(receipt))) return blocked(worker, queue, order, "output_full");
            worker.work(elapsed, multiplier);
            if (!worker.spend(40.0D + 16.0D * Math.sqrt(worker.position().distSqr(machine.position())))) {
                worker.status("collecting");
                return true;
            }
            items.get(collect).shrink(receipt.getCount());
            machine.updateItems(level, items);
            worker.updateInventory(level.registryAccess(), carried);
            SettlementFurnaceService.applyLoaded(level, machine);
            if (collect == 2) {
                if (!queue.complete(allocation.id(), HiredProductionStock.product(receipt))) throw new IllegalStateException("Furnace output did not match its production allocation");
                if (queue.allocation(worker.id()) == null) {
                    stations.release(allocation.id());
                    worker.settings().remove("FurnaceProductionAllocation");
                    worker.resetWork();
                }
                boolean cooking = worker.role() == HiredVillagerRole.COOK;
                worker.practice(level, HiredWorkPractice.batch(cooking ? VillagerSkill.COOKING : VillagerSkill.SMITHING,
                        cooking ? "hired:cooking:batch" : "hired:smelting:batch", receipt.getCount(), receipt.getItem().hashCode()));
            }
            queue.blocker(order.id(), "");
            worker.status("collected");
            return true;
        }
        if (items.getFirst().isEmpty()) {
            stations.release(allocation.id());
            queue.lost(allocation.id());
            return blocked(worker, queue, order, "station_batch_missing");
        }
        if (items.get(1).isEmpty() && machine.burnTime() <= 0) {
            var supplies = SettlementProductionInventory.supplies(level, town, worker, carried, depots);
            var consumed = new ArrayList<Integer>(java.util.Collections.nCopies(supplies.size(), 0));
            if (!planFuel(machine, items, supplies, consumed) || !SettlementProductionInventory.allowed(level, supplies, consumed, order.id())) {
                return blocked(worker, queue, order, "missing_fuel");
            }
            worker.work(elapsed, multiplier);
            if (!worker.spend(cost(worker, machine, supplies, consumed))) {
                worker.status("supplying_furnace");
                return true;
            }
            commit(level, worker, machine, items, carried, supplies, consumed);
        }
        queue.blocker(order.id(), "");
        worker.status("waiting_for_furnace");
        return true;
    }

    private static boolean planFuel(SettlementFurnaceState machine, NonNullList<ItemStack> items,
            List<SettlementProductionInventory.Supply> supplies, List<Integer> consumed) {
        for (int index = 0; index < supplies.size(); index++) {
            var stack = supplies.get(index).available();
            if (stack.getCount() > consumed.get(index) && stack.getBurnTime(machine.recipeType()) > 0) {
                consumed.set(index, consumed.get(index) + 1);
                items.set(1, stack.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    private static boolean allowedInput(ServerLevel level, SettlementFurnaceState machine, HiredProductionOrder order, ItemStack input) {
        return machine.recipe(level, input).filter(recipe -> recipe.id().equals(order.recipe().recipe())
                && HiredProcessingRecipeFilter.allows(level, order.recipe().filter(level), recipe, input,
                    recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess()))).isPresent();
    }

    private static NonNullList<ItemStack> copy(NonNullList<ItemStack> items) {
        var result = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) result.set(slot, items.get(slot).copy());
        return result;
    }

    private static void consumeCarried(NonNullList<ItemStack> carried, List<SettlementProductionInventory.Supply> supplies, List<Integer> consumed) {
        for (int index = 0; index < supplies.size(); index++) {
            if (supplies.get(index).depot() == null) carried.get(supplies.get(index).slot()).shrink(consumed.get(index));
        }
    }

    private static void commit(ServerLevel level, SettlementWorkerState worker, SettlementFurnaceState machine,
            NonNullList<ItemStack> items, NonNullList<ItemStack> carried, List<SettlementProductionInventory.Supply> supplies, List<Integer> consumed) {
        consumeCarried(carried, supplies, consumed);
        for (int index = 0; index < supplies.size(); index++) {
            if (supplies.get(index).depot() != null && consumed.get(index) > 0) supplies.get(index).depot().removeItem(supplies.get(index).slot(), consumed.get(index));
        }
        machine.updateItems(level, items);
        worker.updateInventory(level.registryAccess(), carried);
        SettlementFurnaceService.applyLoaded(level, machine);
    }

    private static double cost(SettlementWorkerState worker, SettlementFurnaceState machine,
            List<SettlementProductionInventory.Supply> supplies, List<Integer> consumed) {
        double cost = 40.0D + 16.0D * Math.sqrt(worker.position().distSqr(machine.position()));
        var visited = new java.util.HashSet<java.util.UUID>();
        for (int index = 0; index < supplies.size(); index++) {
            var depot = supplies.get(index).depot();
            if (depot != null && consumed.get(index) > 0 && visited.add(depot.id())) cost += 16.0D * Math.sqrt(machine.position().distSqr(depot.position()));
        }
        return cost;
    }

    private static void releasePlanned(ServerLevel level, HiredProductionQueue queue, SettlementWorkerState worker) {
        var allocation = queue.allocation(worker.id());
        if (allocation != null && !allocation.started()) {
            HiredProductionStations.get(level).release(allocation.id());
            queue.release(allocation.id());
        }
        worker.settings().remove("FurnaceProductionAllocation");
        worker.resetWork();
    }

    private static boolean blocked(SettlementWorkerState worker, HiredProductionQueue queue, HiredProductionOrder order, String reason) {
        queue.blocker(order.id(), reason);
        worker.status(reason);
        return true;
    }
}
