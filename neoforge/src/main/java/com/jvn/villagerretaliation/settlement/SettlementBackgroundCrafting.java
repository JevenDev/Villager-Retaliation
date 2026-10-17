package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.WorkBlocker;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.CraftsmanWorker;
import com.jvn.villagerretaliation.interaction.work.WorkCraftingPlan;
import com.jvn.villagerretaliation.interaction.work.CookingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredProcessingRecipeFilter;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionEligibility;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionCrafting;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementBackgroundCrafting {
    private static final ResourceLocation CRAFTING_TABLE = ResourceLocation.withDefaultNamespace("crafting_table");

    private SettlementBackgroundCrafting() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        tickOrder(level, town, worker, elapsed, multiplier, null);
    }

    static boolean tickOrder(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier, HiredProductionOrder selected) {
        var queue = town.productionQueues().get(worker.area());
        if (queue != null) AssignedStorageService.releaseProductionInputs(worker.id());
        List<SettlementDepot> depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        if ((!worker.settings().contains("AutoDepositOutputs") || worker.settings().getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) {
            return true;
        }
        var survey = town.surveys().get(worker.area());
        var blocks = new SettlementBuildingSurvey.ObservedBlocks(level, null, town);
        BlockPos table = survey == null ? null : survey.fixtures().entrySet().stream()
                .filter(entry -> SettlementWorksiteService.links(town, worker.area()).permitsStation(entry.getKey())
                        && CRAFTING_TABLE.equals(entry.getValue())
                        && SettlementBuildingSurvey.fixtureAvailable(blocks, entry.getKey(), entry.getValue())).map(Map.Entry::getKey)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(worker.position()))).orElse(null);
        if (table == null && worker.role() != HiredVillagerRole.COOK) {
            releaseProduction(level, queue, worker, selected);
            if (queue != null) for (var order : queue.executionOrders()) queue.blocker(order.id(), "no_crafting_table");
            worker.status("no_crafting_table", WorkBlocker.MISSING_CRAFTING_TABLE);
            return false;
        }
        BlockPos craftingPosition = table == null ? worker.position() : table;
        var items = worker.items(level.registryAccess());
        ItemStack filter = items.get(HiredJobInventory.FILTER_SLOT);
        var targets = queue == null ? CraftsmanWorker.targets(level, filter)
                : queue.executionOrders().stream().filter(order -> selected == null || selected.id().equals(order.id()))
                    .map(order -> new CraftsmanWorker.Target(queue.orders().indexOf(order), order.output())).toList();
        if (worker.role() == HiredVillagerRole.COOK) {
            targets = targets.stream().filter(target -> CookingWorker.isFood(target.stack())).toList();
        }
        if (targets.isEmpty()) {
            releaseProduction(level, queue, worker, selected);
            worker.status("no_filter", WorkBlocker.MISSING_FILTER);
            return false;
        }
        List<SettlementProductionInventory.Supply> supplies = SettlementProductionInventory.supplies(level, town, worker, items, depots);
        var mode = queue != null || worker.role() == HiredVillagerRole.COOK ? CraftsmanWorker.Mode.PREFER_FIRST : CraftsmanWorker.mode(worker.settings());
        int start = mode == CraftsmanWorker.Mode.PREFER_FIRST ? 0
                : CraftsmanWorker.indexAtOrAfter(targets, worker.settings().getInt("CraftsmanCursor"));
        int attempts = mode == CraftsmanWorker.Mode.FORCED_ROUND_ROBIN ? 1 : targets.size();
        for (int offset = 0; offset < attempts; offset++) {
            var target = targets.get((start + offset) % targets.size());
            var order = queue == null ? null : queue.orders().get(target.slot());
            long stock = 0L;
            if (order != null) {
                stock = SettlementProductionStock.count(level, town, queue, order);
                boolean carrying = worker.outputSlots().stream().anyMatch(slot -> !items.get(slot).isEmpty());
                String blocker = HiredProductionEligibility.blocker(level, worker.role(), queue, order, worker.id(),
                        stock, carrying, HiredProductionStock.destination(worker.settings()));
                if (!blocker.isEmpty()) {
                    queue.blocker(order.id(), blocker);
                    continue;
                }
                filter = order.recipe().filter(level);
            }
            for (var recipe : com.jvn.villagerretaliation.interaction.work.WorkCraftingRecipes.producing(level, target.stack().getItem())) {
                ItemStack output = recipe.value().getResultItem(level.registryAccess());
                if (output.isEmpty() || !output.is(target.stack().getItem())
                        || !HiredProcessingRecipeFilter.allowsCraftingRecipe(level, filter, recipe, output)) {
                    continue;
                }
                if (table == null && com.jvn.villagerretaliation.interaction.work.HiredSupplyCrafting.requiresCraftingTable(recipe.value())) {
                    if (order != null) queue.blocker(order.id(), "no_crafting_table");
                    continue;
                }
                var plan = WorkCraftingPlan.prepare(level, recipe.value(), supplies.stream().map(SettlementProductionInventory.Supply::available).toList(),
                        HiredProcessingRecipeFilter.narrowedCraftingIngredients(level, filter, recipe), table != null);
                if (plan == null || !SettlementProductionInventory.allowed(level, supplies, plan.consumed(), order == null ? null : order.id())) {
                    continue;
                }
                output = plan.result();
                if (order != null && (!order.matches(output) || output.getCount() != order.output().getCount())) {
                    queue.blocker(order.id(), "recipe_unavailable");
                    continue;
                }
                NonNullList<ItemStack> updated = NonNullList.withSize(items.size(), ItemStack.EMPTY);
                for (int slot = 0; slot < items.size(); slot++) {
                    updated.set(slot, items.get(slot).copy());
                }
                for (int index = 0; index < supplies.size(); index++) {
                    if (supplies.get(index).depot() == null) {
                        updated.get(supplies.get(index).slot()).shrink(plan.consumed().get(index));
                    }
                }
                if (!SettlementProductionInventory.insertOutputs(worker, updated, plan.outputs())) {
                    if (order != null) {
                        queue.blocker(order.id(), "output_full");
                        continue;
                    }
                    worker.status("output_full");
                    return false;
                }
                double cost = plan.crafts() * 20.0D + 16.0D * Math.sqrt(worker.position().distSqr(craftingPosition));
                var visited = new java.util.HashSet<java.util.UUID>();
                for (int index = 0; index < supplies.size(); index++) {
                    var depot = supplies.get(index).depot();
                    if (plan.consumed().get(index) > 0 && depot != null && visited.add(depot.id())) {
                        cost += 16.0D * Math.sqrt(craftingPosition.distSqr(depot.position()));
                    }
                }
                HiredProductionQueue.Allocation allocation = null;
                if (order != null) {
                    allocation = HiredProductionCrafting.reserve(level, queue, worker.id(), order,
                            output.getCount(), stock, worker::resetWork);
                    if (allocation == null) {
                        queue.blocker(order.id(), "quantity_reserved");
                        continue;
                    }
                    HiredProductionCrafting.trackProgress(worker.settings(), allocation, worker::resetWork);
                    HiredProductionStock.prepareDestination(worker.settings(), false, order);
                    queue.blocker(order.id(), "");
                }
                worker.work(elapsed, multiplier);
                if (!worker.spend(cost)) {
                    worker.status("crafting");
                    return true;
                }
                if (allocation != null && !queue.start(allocation.id())) {
                    releaseProduction(level, queue, worker, selected);
                    worker.status("production_changed");
                    return false;
                }
                for (int index = 0; index < supplies.size(); index++) {
                    SettlementProductionInventory.Supply supply = supplies.get(index);
                    int count = plan.consumed().get(index);
                    if (supply.depot() != null && count > 0) {
                        supply.depot().removeItem(supply.slot(), count);
                    }
                }
                worker.updateInventory(level.registryAccess(), updated);
                if (allocation != null) {
                    if (!queue.complete(allocation.id(), output)) throw new IllegalStateException("Crafted output did not match its production allocation");
                    releaseProduction(level, queue, worker, selected);
                } else if (worker.role() == HiredVillagerRole.CRAFTSMAN) {
                    CraftsmanWorker.advanceCursor(worker.settings(), targets, target.slot());
                }
                boolean cooking = worker.role() == HiredVillagerRole.COOK;
                worker.practice(level, HiredWorkPractice.batch(cooking ? VillagerSkill.COOKING : VillagerSkill.CRAFTING,
                        cooking ? "hired:cooking:craft" : "hired:craftsman:recipe", output.getCount(), output.getItem().hashCode()));
                worker.status("crafted");
                return true;
            }
            if (order != null && !java.util.Set.of("output_full", "no_crafting_table").contains(queue.order(order.id()).blocker())) queue.blocker(order.id(), "missing_materials");
        }
        releaseProduction(level, queue, worker, selected);
        worker.status("missing_materials");
        return false;
    }

    private static void releaseProduction(ServerLevel level, HiredProductionQueue queue, SettlementWorkerState worker, HiredProductionOrder selected) {
        if (queue == null) return;
        var allocation = queue.allocation(worker.id());
        if (allocation != null && selected != null && !allocation.order().equals(selected.id())) return;
        HiredProductionCrafting.releasePlanned(level, queue, allocation);
        HiredProductionCrafting.clearProgress(worker.settings(), worker::resetWork);
    }
}
