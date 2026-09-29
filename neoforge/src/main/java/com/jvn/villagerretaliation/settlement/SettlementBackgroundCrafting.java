package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.CraftsmanWorker;
import com.jvn.villagerretaliation.interaction.work.CookingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredProcessingRecipeFilter;
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
            worker.status("no_crafting_table");
            return;
        }
        var items = worker.items(level.registryAccess());
        ItemStack filter = items.get(HiredJobInventory.FILTER_SLOT);
        var targets = CraftsmanWorker.targets(level, filter);
        if (worker.role() == HiredVillagerRole.COOK) {
            targets = targets.stream().filter(target -> CookingWorker.isFood(target.stack())).toList();
        }
        if (targets.isEmpty()) {
            worker.status("no_filter");
            return;
        }
        List<SettlementProductionInventory.Supply> supplies = SettlementProductionInventory.supplies(level, worker, items, depots);
        var mode = worker.role() == HiredVillagerRole.COOK ? CraftsmanWorker.Mode.PREFER_FIRST : CraftsmanWorker.mode(worker.settings());
        int start = mode == CraftsmanWorker.Mode.PREFER_FIRST ? 0
                : CraftsmanWorker.indexAtOrAfter(targets, worker.settings().getInt("CraftsmanCursor"));
        int attempts = mode == CraftsmanWorker.Mode.FORCED_ROUND_ROBIN ? 1 : targets.size();
        for (int offset = 0; offset < attempts; offset++) {
            var target = targets.get((start + offset) % targets.size());
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
                worker.work(elapsed, multiplier);
                if (!worker.spend(cost)) {
                    worker.status("crafting");
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
                if (worker.role() == HiredVillagerRole.CRAFTSMAN) {
                    CraftsmanWorker.advanceCursor(worker.settings(), targets, target.slot());
                }
                boolean cooking = worker.role() == HiredVillagerRole.COOK;
                worker.practice(level, HiredWorkPractice.batch(cooking ? VillagerSkill.COOKING : VillagerSkill.CRAFTING,
                        cooking ? "hired:cooking:craft" : "hired:craftsman:recipe", output.getCount(), output.getItem().hashCode()));
                worker.status("crafted");
                return;
            }
        }
        worker.status("missing_materials");
    }
}
