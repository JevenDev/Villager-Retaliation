package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

final class SettlementBackgroundBrewing {
    private SettlementBackgroundBrewing() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        List<SettlementDepot> depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        if ((!worker.settings().contains("AutoDepositOutputs") || worker.settings().getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) {
            return;
        }
        var queue = town.productionQueues().get(worker.area());
        if (queue != null) {
            tickProduction(level, town, worker, queue, elapsed, multiplier);
            return;
        }
        tickOrder(level, town, worker, SettlementBrewingOrder.read(worker.settings()), null, null, elapsed, multiplier);
    }

    private static boolean tickOrder(ServerLevel level, SettlementRecord town, SettlementWorkerState worker,
            SettlementBrewingOrder order, HiredProductionQueue queue, HiredProductionQueue.Allocation allocation, int elapsed, double multiplier) {
        List<SettlementDepot> depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        var route = order == null ? null : HiredBrewingRecipeCatalog.find(level, order.item(), order.potion()).orElse(null);
        if (route == null) {
            worker.status("choose_brewing_order");
            return false;
        }
        var construction = new SettlementConstructionMachines(level, town, null);
        var machines = town.brewingStands().values().stream().filter(state -> state.area().equals(worker.area()))
                .filter(state -> allocation == null || state.position().equals(allocation.station()))
                .filter(state -> SettlementWorksiteService.links(town, worker.area()).permitsStation(state.position()))
                .sorted(Comparator.comparingDouble(state -> state.position().distSqr(worker.position()))).toList();
        for (var machine : machines) {
            if (!SettlementBrewingStandService.available(level, machine, construction)
                    || allocation == null && !HiredProductionStations.get(level).available(level, machine.position())) {
                continue;
            }
            var items = machine.items(level.registryAccess());
            int step = currentStep(level, items, route);
            ItemStack ingredient = items.get(3);
            if (step < 0 || !items.get(4).isEmpty() && !items.get(4).is(Items.BLAZE_POWDER)
                    || !ingredient.isEmpty() && !route.ingredients().contains(ingredient.getItem()) && !remainder(route, ingredient)) {
                continue;
            }
            int removeIngredient = remainder(route, ingredient) ? ingredient.getCount()
                    : !ingredient.isEmpty() && step < route.ingredients().size() && ingredient.is(route.ingredients().get(step))
                            ? Math.max(0, ingredient.getCount() - 1)
                            : machine.brewTime() == 0 ? ingredient.getCount() : 0;
            if (removeIngredient > 0) {
                var carried = worker.items(level.registryAccess());
                if (!SettlementProductionInventory.insertOutputs(worker, carried, List.of(ingredient.copyWithCount(removeIngredient)))) {
                    worker.status("output_full");
                    return false;
                }
                if (!spend(worker, elapsed, multiplier, travel(worker, machine), "collecting")) {
                    return true;
                }
                ingredient.shrink(removeIngredient);
                commit(level, worker, machine, carried, items);
                worker.status("collected");
                return true;
            }
            if (step == route.ingredients().size()) {
                var carried = worker.items(level.registryAccess());
                int remaining = order.continuous() ? 3 : order.amount();
                int collected = 0;
                List<ItemStack> outputs = new ArrayList<>();
                for (int slot = 0; slot < 3 && collected < remaining; slot++) {
                    ItemStack bottle = items.get(slot);
                    int count = Math.min(bottle.getCount(), remaining - collected);
                    if (count > 0) {
                        outputs.add(bottle.copyWithCount(count));
                        bottle.shrink(count);
                        collected += count;
                    }
                }
                if (collected == 0) return false;
                if (!SettlementProductionInventory.insertOutputs(worker, carried, outputs)) {
                    worker.status("output_full");
                    return false;
                }
                if (!spend(worker, elapsed, multiplier, travel(worker, machine), "collecting")) {
                    return true;
                }
                commit(level, worker, machine, carried, items);
                if (queue != null) {
                    if (!queue.complete(allocation.id(), route.output().copyWithCount(collected))) throw new IllegalStateException("Brewed output did not match its production allocation");
                    if (queue.allocation(worker.id()) == null) {
                        HiredProductionStations.get(level).release(allocation.id());
                        worker.settings().remove("BrewingProductionAllocation");
                        worker.resetWork();
                    }
                } else if (!order.continuous()) {
                    worker.brewingOrder(collected >= order.amount() ? null
                            : new SettlementBrewingOrder(order.item(), order.potion(), order.amount() - collected, false));
                }
                worker.practice(level, HiredWorkPractice.batch(VillagerSkill.MEDICINE, "hired:brewing:batch",
                        collected, route.output().getItem().hashCode()));
                worker.status("brewed");
                return true;
            }
            if (machine.brewTime() > 0) {
                continue;
            }
            var carried = worker.items(level.registryAccess());
            var supplies = SettlementProductionInventory.supplies(level, worker, carried, depots);
            List<Integer> consumed = new ArrayList<>(java.util.Collections.nCopies(supplies.size(), 0));
            BlockPos water = null;
            int bottles = 0;
            for (int slot = 0; slot < 3; slot++) {
                bottles += items.get(slot).isEmpty() ? 0 : 1;
            }
            if (step == 0 && ingredient.isEmpty()) {
                int target = order.continuous() ? 3 : Math.min(3, order.amount());
                ItemStack base = PotionContents.createItemStack(Items.POTION, Potions.WATER);
                for (int slot = 0; slot < 3 && bottles < target; slot++) {
                    if (!items.get(slot).isEmpty()) {
                        continue;
                    }
                    ItemStack bottle = take(level, supplies, consumed, stack -> ItemStack.isSameItemSameComponents(stack, base));
                    if (bottle.isEmpty()) {
                        BlockPos source = waterSource(level, town, worker, machine.position());
                        if (source != null && !take(level, supplies, consumed, stack -> stack.is(Items.GLASS_BOTTLE)).isEmpty()) {
                            bottle = base.copy();
                            water = source;
                        }
                    }
                    if (!bottle.isEmpty()) {
                        items.set(slot, bottle);
                        bottles++;
                    }
                }
            }
            if (bottles > 0) {
                if (ingredient.isEmpty()) {
                    items.set(3, take(level, supplies, consumed, stack -> stack.is(route.ingredients().get(step))));
                }
                if (!items.get(3).isEmpty() && machine.fuel() == 0 && items.get(4).isEmpty()) {
                    items.set(4, take(level, supplies, consumed, stack -> stack.is(Items.BLAZE_POWDER)));
                }
            }
            if (consumed.stream().mapToInt(Integer::intValue).sum() == 0
                    || allocation != null && !allocation.started() && bottles != allocation.remaining()) {
                continue;
            }
            double cost = travel(worker, machine);
            var visited = new HashSet<UUID>();
            for (int index = 0; index < supplies.size(); index++) {
                var depot = supplies.get(index).depot();
                if (consumed.get(index) > 0 && depot != null && visited.add(depot.id())) {
                    cost += 16.0D * Math.sqrt(machine.position().distSqr(depot.position()));
                }
            }
            if (water != null) {
                cost += 16.0D * Math.sqrt(machine.position().distSqr(water));
            }
            if (!spend(worker, elapsed, multiplier, cost, "supplying_brewing_stand")) {
                return true;
            }
            if (queue != null && !queue.start(allocation.id())) return false;
            for (int index = 0; index < supplies.size(); index++) {
                var source = supplies.get(index);
                int count = consumed.get(index);
                if (count > 0) {
                    if (source.depot() == null) {
                        carried.get(source.slot()).shrink(count);
                    } else {
                        source.depot().removeItem(source.slot(), count);
                    }
                }
            }
            commit(level, worker, machine, carried, items);
            if (queue != null) HiredProductionStations.get(level).reserve(level, queue, queue.allocation(worker.id()), machine.id());
            worker.status("supplied_brewing_stand");
            return true;
        }
        worker.status(machines.isEmpty() ? "no_brewing_stand" : "waiting_for_brewing_stand");
        return allocation != null && allocation.started();
    }

    private static void tickProduction(ServerLevel level, SettlementRecord town, SettlementWorkerState worker,
            HiredProductionQueue queue, int elapsed, double multiplier) {
        var stations = HiredProductionStations.get(level);
        com.jvn.villagerretaliation.inventory.AssignedStorageService.releaseProductionInputs(worker.id());
        if (stations.readOnly()) {
            worker.status("production_station_recovery_required");
            return;
        }
        var machines = town.brewingStands().values().stream().filter(machine -> machine.area().equals(worker.area()))
                .filter(machine -> SettlementWorksiteService.links(town, worker.area()).permitsStation(machine.position()))
                .sorted(Comparator.comparingDouble(machine -> machine.position().distSqr(worker.position()))).toList();
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
            productionBatch(level, town, worker, queue, allocation, elapsed, multiplier);
            return;
        }
        var construction = new SettlementConstructionMachines(level, town, null);
        var carried = worker.items(level.registryAccess());
        var depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        var supplies = SettlementProductionInventory.supplies(level, worker, carried, depots);
        for (var order : queue.orders()) {
            if (!order.runnable()) continue;
            var route = HiredBrewingRecipeCatalog.find(level, order.recipe().brewingItem(), order.recipe().potion()).orElse(null);
            if (route == null || !ItemStack.matches(order.output(), route.output())) {
                queue.blocker(order.id(), "recipe_unavailable");
                continue;
            }
            long stock = SettlementProductionStock.count(level, town, queue, order);
            long own = allocation != null && allocation.order().equals(order.id()) ? allocation.remaining() : 0L;
            if (stock < 0L || queue.available(order.id(), stock) + own <= 0L) {
                queue.blocker(order.id(), stock < 0L ? "output_storage_unavailable" : "quantity_reserved_or_stock_met");
                continue;
            }
            boolean carrying = worker.outputSlots().stream().anyMatch(slot -> !carried.get(slot).isEmpty());
            if (carrying && !java.util.Objects.equals(HiredProductionStock.destination(worker.settings()), order.storage())) {
                queue.blocker(order.id(), "awaiting_output_delivery");
                continue;
            }
            int batchSize = (int) Math.min(3L, queue.available(order.id(), stock) + own);
            queue.blocker(order.id(), "no_available_brewing_stand");
            for (var machine : machines) {
                if (!SettlementBrewingStandService.available(level, machine, construction)) continue;
                var claim = stations.reconcile(level, machine.position(), machine.id());
                if (claim != null && (allocation == null || !claim.allocation().equals(allocation.id()))) continue;
                var items = machine.items(level.registryAccess());
                if (items.subList(0, 4).stream().anyMatch(stack -> !stack.isEmpty())
                        || !items.get(4).isEmpty() && !items.get(4).is(Items.BLAZE_POWDER)) continue;
                var consumed = new ArrayList<Integer>(java.util.Collections.nCopies(supplies.size(), 0));
                boolean ready = true;
                for (int bottle = 0; bottle < batchSize; bottle++) {
                    if (take(level, supplies, consumed, HiredBrewingRecipeCatalog::isWaterPotion).isEmpty()
                            && (waterSource(level, town, worker, machine.position()) == null
                                || take(level, supplies, consumed, stack -> stack.is(Items.GLASS_BOTTLE)).isEmpty())) ready = false;
                }
                for (var ingredient : route.ingredients()) {
                    if (take(level, supplies, consumed, stack -> stack.is(ingredient)).isEmpty()) ready = false;
                }
                if (machine.fuel() == 0 && items.get(4).isEmpty()
                        && take(level, supplies, consumed, stack -> stack.is(Items.BLAZE_POWDER)).isEmpty()) ready = false;
                if (!ready) {
                    queue.blocker(order.id(), "missing_materials");
                    continue;
                }
                var preview = NonNullList.withSize(carried.size(), ItemStack.EMPTY);
                for (int slot = 0; slot < carried.size(); slot++) preview.set(slot, carried.get(slot).copy());
                for (int index = 0; index < supplies.size(); index++) {
                    if (supplies.get(index).depot() == null) preview.get(supplies.get(index).slot()).shrink(consumed.get(index));
                }
                if (!SettlementProductionInventory.insertOutputs(worker, preview, List.of(route.output().copyWithCount(batchSize)))) {
                    queue.blocker(order.id(), "output_full");
                    continue;
                }
                if (allocation != null && (!allocation.order().equals(order.id()) || !allocation.station().equals(machine.position()))) {
                    stations.release(allocation.id());
                    queue.release(allocation.id());
                    allocation = null;
                }
                if (allocation == null) allocation = queue.reserve(worker.id(), order.id(), batchSize, 1, stock, machine.position());
                if (allocation == null || !stations.reserve(level, queue, allocation, machine.id())) continue;
                if (!worker.settings().hasUUID("BrewingProductionAllocation")
                        || !worker.settings().getUUID("BrewingProductionAllocation").equals(allocation.id())) {
                    worker.resetWork();
                    worker.settings().putUUID("BrewingProductionAllocation", allocation.id());
                }
                HiredProductionStock.prepareDestination(worker.settings(), carrying, order);
                if (productionBatch(level, town, worker, queue, allocation, elapsed, multiplier)) return;
                releaseProduction(level, worker, queue);
                allocation = null;
            }
        }
        releaseProduction(level, worker, queue);
        worker.status("no_runnable_production");
    }

    private static boolean productionBatch(ServerLevel level, SettlementRecord town, SettlementWorkerState worker,
            HiredProductionQueue queue, HiredProductionQueue.Allocation allocation, int elapsed, double multiplier) {
        var stations = HiredProductionStations.get(level);
        var order = queue.order(allocation.order());
        var machine = town.brewingStands().get(allocation.station());
        if (machine == null || !machine.area().equals(worker.area())
                || !SettlementWorksiteService.links(town, worker.area()).permitsStation(machine.position())
                || !SettlementBrewingStandService.available(level, machine, new SettlementConstructionMachines(level, town, null))) {
            var claim = allocation.station() == null ? null : stations.claim(level, allocation.station());
            if (allocation.station() != null && level.hasChunkAt(allocation.station())
                    && (!(level.getBlockEntity(allocation.station()) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand)
                        || claim != null && !claim.machine().equals(SettlementBrewingStandState.identity(stand)))) {
                stations.release(allocation.id());
                queue.lost(allocation.id());
                worker.resetWork();
                queue.blocker(order.id(), "station_removed");
            } else queue.blocker(order.id(), "station_unavailable");
            worker.status(queue.order(order.id()).blocker());
            return allocation.started();
        }
        if (!stations.reserve(level, queue, allocation, machine.id())) {
            queue.blocker(order.id(), "station_claim_changed");
            worker.status("station_claim_changed");
            return allocation.started();
        }
        var items = machine.items(level.registryAccess());
        if (allocation.started() && items.subList(0, 3).stream().allMatch(ItemStack::isEmpty)) {
            stations.release(allocation.id());
            queue.lost(allocation.id());
            queue.blocker(order.id(), "station_batch_missing");
            worker.status("station_batch_missing");
            return true;
        }
        if (!allocation.started() && items.subList(0, 4).stream().anyMatch(stack -> !stack.isEmpty())) {
            queue.blocker(order.id(), "station_contents_changed");
            return false;
        }
        var carried = worker.items(level.registryAccess());
        if (!HiredProductionStock.prepareDestination(worker.settings(), worker.outputSlots().stream().anyMatch(slot -> !carried.get(slot).isEmpty()), order)) {
            queue.blocker(order.id(), "awaiting_output_delivery");
            return allocation.started();
        }
        var route = HiredBrewingRecipeCatalog.find(level, order.recipe().brewingItem(), order.recipe().potion()).orElse(null);
        if (route == null || !ItemStack.matches(order.output(), route.output())) {
            queue.blocker(order.id(), "recipe_unavailable");
            return allocation.started();
        }
        int step = currentStep(level, items, route);
        if (step < 0 || !items.get(4).isEmpty() && !items.get(4).is(Items.BLAZE_POWDER)
                || !items.get(3).isEmpty() && !route.ingredients().contains(items.get(3).getItem()) && !remainder(route, items.get(3))) {
            queue.blocker(order.id(), "station_contents_changed");
            worker.status("station_contents_changed");
            return allocation.started();
        }
        queue.blocker(order.id(), "");
        boolean active = tickOrder(level, town, worker, new SettlementBrewingOrder(order.recipe().brewingItem(), order.recipe().potion(), allocation.remaining(), false),
                queue, allocation, elapsed, multiplier);
        if (!active || worker.status().equals("output_full")) queue.blocker(order.id(), worker.status());
        else if (worker.status().equals("waiting_for_brewing_stand") && machine.brewTime() == 0
                && (items.get(3).isEmpty() || machine.fuel() == 0 && items.get(4).isEmpty())) queue.blocker(order.id(), "missing_materials");
        return active || allocation.started();
    }

    private static void releaseProduction(ServerLevel level, SettlementWorkerState worker, HiredProductionQueue queue) {
        var allocation = queue.allocation(worker.id());
        if (allocation != null && !allocation.started()) {
            HiredProductionStations.get(level).release(allocation.id());
            queue.release(allocation.id());
        }
        worker.settings().remove("BrewingProductionAllocation");
        worker.resetWork();
    }

    private static ItemStack take(ServerLevel level, List<SettlementProductionInventory.Supply> supplies,
                                  List<Integer> consumed, Predicate<ItemStack> matches) {
        for (int index = 0; index < supplies.size(); index++) {
            ItemStack available = supplies.get(index).available();
            int previous = consumed.get(index);
            if (available.getCount() > previous && matches.test(available)) {
                consumed.set(index, previous + 1);
                if (SettlementProductionInventory.allowed(level, supplies, consumed)) {
                    return available.copyWithCount(1);
                }
                consumed.set(index, previous);
            }
        }
        return ItemStack.EMPTY;
    }

    static int currentStep(ServerLevel level, NonNullList<ItemStack> items, HiredBrewingRecipeCatalog.BrewingRoute route) {
        ItemStack expected = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        for (int step = 0; step <= route.ingredients().size(); step++) {
            boolean matches = true;
            for (int slot = 0; slot < 3; slot++) {
                matches &= items.get(slot).isEmpty() || ItemStack.isSameItemSameComponents(items.get(slot), expected);
            }
            if (matches) {
                return step;
            }
            if (step < route.ingredients().size()) {
                expected = level.potionBrewing().mix(new ItemStack(route.ingredients().get(step)), expected);
            }
        }
        return -1;
    }

    static boolean remainder(HiredBrewingRecipeCatalog.BrewingRoute route, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (var ingredient : route.uniqueIngredients()) {
            ItemStack sample = new ItemStack(ingredient);
            if (sample.hasCraftingRemainingItem() && ItemStack.isSameItemSameComponents(stack, sample.getCraftingRemainingItem())) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos waterSource(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, BlockPos stand) {
        var survey = town.surveys().get(worker.area());
        if (survey == null) {
            return null;
        }
        return survey.waterSources().stream().filter(position -> {
            var chunk = level.getChunkSource().getChunkNow(Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
            return chunk == null || SettlementBuildingSurvey.waterSource(chunk.getBlockState(position));
        }).min(Comparator.comparingDouble(position -> position.distSqr(stand))).orElse(null);
    }

    private static double travel(SettlementWorkerState worker, SettlementBrewingStandState machine) {
        return 40.0D + 16.0D * Math.sqrt(worker.position().distSqr(machine.position()));
    }

    private static boolean spend(SettlementWorkerState worker, int elapsed, double multiplier, double cost, String status) {
        worker.work(elapsed, multiplier);
        if (worker.spend(cost)) {
            return true;
        }
        worker.status(status);
        return false;
    }

    private static void commit(ServerLevel level, SettlementWorkerState worker, SettlementBrewingStandState machine,
                               NonNullList<ItemStack> carried, NonNullList<ItemStack> items) {
        machine.updateItems(level, items);
        worker.updateInventory(level.registryAccess(), carried);
        SettlementBrewingStandService.applyLoaded(level, machine);
    }
}
