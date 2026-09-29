package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.CraftsmanWorker;
import com.jvn.villagerretaliation.interaction.work.CookingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredProcessingRecipeFilter;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
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
import net.minecraft.world.item.crafting.RecipeType;

final class SettlementBackgroundCrafting {
    private static final ResourceLocation CRAFTING_TABLE = ResourceLocation.withDefaultNamespace("crafting_table");

    private SettlementBackgroundCrafting() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var queue = town.productionQueues().get(worker.area());
        if (queue != null) AssignedStorageService.releaseProductionInputs(worker.id());
        List<SettlementDepot> depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        if ((!worker.settings().contains("AutoDepositOutputs") || worker.settings().getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) {
            return;
        }
        var survey = town.surveys().get(worker.area());
        var blocks = new SettlementBuildingSurvey.ObservedBlocks(level, null, town);
        BlockPos table = survey == null ? null : survey.fixtures().entrySet().stream()
                .filter(entry -> SettlementWorksiteService.links(town, worker.area()).permitsStation(entry.getKey())
                        && CRAFTING_TABLE.equals(entry.getValue())
                        && SettlementBuildingSurvey.fixtureAvailable(blocks, entry.getKey(), entry.getValue())).map(Map.Entry::getKey)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(worker.position()))).orElse(null);
        if (table == null) {
            releaseProduction(queue, worker);
            if (queue != null) for (var order : queue.orders()) if (order.runnable()) queue.blocker(order.id(), "no_crafting_table");
            worker.status("no_crafting_table");
            return;
        }
        var items = worker.items(level.registryAccess());
        ItemStack filter = items.get(HiredJobInventory.FILTER_SLOT);
        var targets = queue == null ? CraftsmanWorker.targets(level, filter)
                : java.util.stream.IntStream.range(0, queue.orders().size())
                    .filter(index -> queue.orders().get(index).runnable())
                    .mapToObj(index -> new CraftsmanWorker.Target(index, queue.orders().get(index).output())).toList();
        if (worker.role() == HiredVillagerRole.COOK) {
            targets = targets.stream().filter(target -> CookingWorker.isFood(target.stack())).toList();
        }
        if (targets.isEmpty()) {
            releaseProduction(queue, worker);
            worker.status("no_filter");
            return;
        }
        List<SettlementProductionInventory.Supply> supplies = SettlementProductionInventory.supplies(level, worker, items, depots);
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
                var existing = queue.allocation(worker.id());
                long pending = existing != null && existing.order().equals(order.id()) ? existing.remaining() : 0L;
                if (stock < 0L || queue.available(order.id(), stock) + pending <= 0L) {
                    queue.blocker(order.id(), stock < 0L ? "output_storage_unavailable" : "quantity_reserved_or_stock_met");
                    continue;
                }
                boolean carrying = worker.outputSlots().stream().anyMatch(slot -> !items.get(slot).isEmpty());
                if (carrying && !java.util.Objects.equals(HiredProductionStock.destination(worker.settings()), order.storage())) {
                    queue.blocker(order.id(), "awaiting_output_delivery");
                    continue;
                }
                if (!order.recipe().supports(level, worker.role()) || !ItemStack.matches(order.output(), order.recipe().output(level))) {
                    queue.blocker(order.id(), "recipe_unavailable");
                    continue;
                }
                filter = order.recipe().filter(level);
            }
            for (var recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                ItemStack output = recipe.value().getResultItem(level.registryAccess());
                if (output.isEmpty() || !output.is(target.stack().getItem())
                        || !HiredProcessingRecipeFilter.allowsCraftingRecipe(level, filter, recipe, output)) {
                    continue;
                }
                var plan = SettlementCraftingPlan.prepare(level, recipe, supplies.stream().map(SettlementProductionInventory.Supply::available).toList(),
                        HiredProcessingRecipeFilter.narrowedCraftingIngredients(level, filter, recipe));
                if (plan == null || !SettlementProductionInventory.allowed(level, supplies, plan.consumed())) {
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
                    return;
                }
                double cost = plan.crafts() * 20.0D + 16.0D * Math.sqrt(worker.position().distSqr(table));
                var visited = new java.util.HashSet<java.util.UUID>();
                for (int index = 0; index < supplies.size(); index++) {
                    var depot = supplies.get(index).depot();
                    if (plan.consumed().get(index) > 0 && depot != null && visited.add(depot.id())) {
                        cost += 16.0D * Math.sqrt(table.distSqr(depot.position()));
                    }
                }
                HiredProductionQueue.Allocation allocation = null;
                if (order != null) {
                    allocation = queue.allocation(worker.id());
                    if (allocation != null && !allocation.order().equals(order.id())) {
                        queue.release(allocation.id());
                        allocation = null;
                    }
                    if (allocation == null) allocation = queue.reserve(worker.id(), order.id(), output.getCount(), 1, stock, null);
                    if (allocation == null) {
                        queue.blocker(order.id(), "quantity_reserved");
                        continue;
                    }
                    if (!worker.settings().hasUUID("CraftsmanProductionAllocation")
                            || !worker.settings().getUUID("CraftsmanProductionAllocation").equals(allocation.id())) {
                        worker.resetWork();
                        worker.settings().putUUID("CraftsmanProductionAllocation", allocation.id());
                    }
                    HiredProductionStock.prepareDestination(worker.settings(), false, order);
                    queue.blocker(order.id(), "");
                }
                worker.work(elapsed, multiplier);
                if (!worker.spend(cost)) {
                    worker.status("crafting");
                    return;
                }
                if (allocation != null && !queue.start(allocation.id())) {
                    releaseProduction(queue, worker);
                    worker.status("production_changed");
                    return;
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
                    releaseProduction(queue, worker);
                } else if (worker.role() == HiredVillagerRole.CRAFTSMAN) {
                    CraftsmanWorker.advanceCursor(worker.settings(), targets, target.slot());
                }
                boolean cooking = worker.role() == HiredVillagerRole.COOK;
                worker.practice(level, HiredWorkPractice.batch(cooking ? VillagerSkill.COOKING : VillagerSkill.CRAFTING,
                        cooking ? "hired:cooking:craft" : "hired:craftsman:recipe", output.getCount(), output.getItem().hashCode()));
                worker.status("crafted");
                return;
            }
            if (order != null && !queue.order(order.id()).blocker().equals("output_full")) queue.blocker(order.id(), "missing_materials");
        }
        releaseProduction(queue, worker);
        worker.status("missing_materials");
    }

    private static void releaseProduction(HiredProductionQueue queue, SettlementWorkerState worker) {
        if (queue == null) return;
        var allocation = queue.allocation(worker.id());
        if (allocation != null) queue.release(allocation.id());
        worker.settings().remove("CraftsmanProductionAllocation");
        worker.resetWork();
    }
}
