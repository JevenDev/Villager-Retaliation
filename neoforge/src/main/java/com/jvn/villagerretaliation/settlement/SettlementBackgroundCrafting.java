package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.CraftsmanWorker;
import com.jvn.villagerretaliation.interaction.work.CookingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredProcessingRecipeFilter;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
        BlockPos table = survey == null ? null : survey.fixtures().entrySet().stream()
                .filter(entry -> CRAFTING_TABLE.equals(entry.getValue())).map(Map.Entry::getKey)
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
        List<Supply> supplies = supplies(level, worker, items, depots);
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
                var plan = SettlementCraftingPlan.prepare(level, recipe, supplies.stream().map(Supply::available).toList(),
                        HiredProcessingRecipeFilter.narrowedCraftingIngredients(level, filter, recipe));
                if (plan == null || !allowed(level, supplies, plan)) {
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
                if (!insertOutputs(worker, updated, plan.outputs())) {
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
                    Supply supply = supplies.get(index);
                    int count = plan.consumed().get(index);
                    if (supply.depot() != null && count > 0) {
                        supply.depot().removeItem(supply.slot(), count);
                    }
                }
                worker.updateInventory(level.registryAccess(), updated);
                if (worker.role() == HiredVillagerRole.CRAFTSMAN) {
                    CraftsmanWorker.advanceCursor(worker.settings(), targets, target.slot());
                }
                worker.status("crafted");
                return;
            }
        }
        worker.status("missing_materials");
    }

    private static List<Supply> supplies(ServerLevel level, SettlementWorkerState worker,
                                        NonNullList<ItemStack> items, List<SettlementDepot> depots) {
        List<Supply> result = new ArrayList<>();
        for (int slot : worker.supplySlots()) {
            if (!items.get(slot).isEmpty()) {
                result.add(new Supply(null, slot, items.get(slot).copy()));
            }
        }
        if (worker.settings().contains("UseAssignedStorageForSupplies")
                && !worker.settings().getBoolean("UseAssignedStorageForSupplies")) {
            return result;
        }
        for (SettlementDepot depot : depots) {
            if (!depot.purpose().providesSupplies()) {
                continue;
            }
            int depotStart = result.size();
            for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                ItemStack stored = depot.getItem(slot);
                if (stored.isEmpty()) {
                    continue;
                }
                int alreadyListed = result.subList(depotStart, result.size()).stream()
                        .filter(supply -> ItemStack.isSameItemSameComponents(supply.available(), stored))
                        .mapToInt(supply -> supply.available().getCount()).sum();
                int unclaimed = depot.count(stack -> ItemStack.isSameItemSameComponents(stack, stored))
                        - AssignedStorageService.reservedItemCount(level, depot.position(), null,
                                VillagerFilterPolicy.TransferOperation.PROVIDE, stored) - alreadyListed;
                int count = Math.min(stored.getCount(), Math.min(unclaimed,
                        SettlementLogisticsService.framedAllowance(level, depot, stored,
                                VillagerFilterPolicy.TransferOperation.PROVIDE, true)));
                if (count > 0) {
                    result.add(new Supply(depot, slot, stored.copyWithCount(count)));
                }
            }
        }
        return result;
    }

    private static boolean allowed(ServerLevel level, List<Supply> supplies, SettlementCraftingPlan.Result plan) {
        Map<SettlementDepot, List<ItemStack>> reservations = new HashMap<>();
        for (int index = 0; index < supplies.size(); index++) {
            Supply supply = supplies.get(index);
            int count = plan.consumed().get(index);
            if (count <= 0 || supply.depot() == null) {
                continue;
            }
            var depot = supply.depot();
            var reserved = reservations.computeIfAbsent(depot, ignored -> new ArrayList<>());
            if (AssignedStorageService.observedTransferAllowance(level, depot.position(), depot, depot.observedRules(),
                    supply.available(), VillagerFilterPolicy.TransferOperation.PROVIDE, reserved) < count) {
                return false;
            }
            reserved.add(supply.available().copyWithCount(count));
        }
        return true;
    }

    private static boolean insertOutputs(SettlementWorkerState worker, NonNullList<ItemStack> items, List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            ItemStack remaining = output.copy();
            for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                for (int slot : worker.outputSlots()) {
                    ItemStack existing = items.get(slot);
                    if (pass == 0 ? existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining) : !existing.isEmpty()) {
                        continue;
                    }
                    int count = Math.min(remaining.getCount(), Math.min(64, remaining.getMaxStackSize()) - existing.getCount());
                    if (count > 0) {
                        items.set(slot, remaining.copyWithCount(existing.getCount() + count));
                        remaining.shrink(count);
                    }
                    if (remaining.isEmpty()) {
                        break;
                    }
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private record Supply(SettlementDepot depot, int slot, ItemStack available) {
    }
}
