package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.WorkBlocker;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.CookingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredProcessingRecipeFilter;
import com.jvn.villagerretaliation.interaction.work.SmeltingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;

final class SettlementBackgroundSmelting {
    private SettlementBackgroundSmelting() {
    }

    static boolean tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        if (town.productionQueues().containsKey(worker.area())) {
            return SettlementBackgroundProductionFurnace.tick(level, town, worker, elapsed, multiplier);
        }
        return tickLegacy(level, town, worker, elapsed, multiplier, false);
    }

    static boolean finishExisting(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        return tickLegacy(level, town, worker, elapsed, multiplier, true);
    }

    private static boolean tickLegacy(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier, boolean finishExisting) {
        List<SettlementDepot> depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        if ((!worker.settings().contains("AutoDepositOutputs") || worker.settings().getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) {
            return true;
        }
        var construction = new SettlementConstructionMachines(level, town, null);
        var area = town.areas().get(worker.area());
        var machines = town.furnaces().values().stream().filter(state -> SettlementFurnaceService.serves(area, state))
                .filter(state -> SettlementWorksiteService.links(town, worker.area()).permitsStation(state.position()))
                .sorted(Comparator.comparingDouble(state -> state.position().distSqr(worker.position()))).toList();
        var carried = worker.items(level.registryAccess());
        ItemStack filter = carried.get(HiredJobInventory.FILTER_SLOT);
        boolean processing = false;
        for (SettlementFurnaceState machine : machines) {
            if (!SettlementFurnaceService.available(level, machine, construction)
                    || !com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations.get(level).available(level, machine.position())) {
                continue;
            }
            var items = machine.items(level.registryAccess());
            if (finishExisting && items.getFirst().isEmpty() && items.get(2).isEmpty() && !items.get(1).is(Items.BUCKET)) continue;
            if (!items.getFirst().isEmpty() && !inputAllowed(level, worker, machine, filter, items.getFirst())
                    || !items.get(2).isEmpty() && !outputAllowed(level, worker, filter, items.get(2))
                    || !items.get(1).isEmpty() && !items.get(1).is(Items.BUCKET) && items.get(1).getBurnTime(machine.recipeType()) <= 0) {
                continue;
            }
            processing |= !items.getFirst().isEmpty() && (machine.burnTime() > 0 || !items.get(1).isEmpty());
            int collectSlot = !items.get(2).isEmpty() ? 2 : items.get(1).is(Items.BUCKET) ? 1 : -1;
            if (collectSlot >= 0) {
                if (!SettlementProductionInventory.insertOutputs(worker, carried, List.of(items.get(collectSlot)))) {
                    worker.status("output_full");
                    return true;
                }
                worker.work(elapsed, multiplier);
                if (!worker.spend(40.0D + 16.0D * Math.sqrt(worker.position().distSqr(machine.position())))) {
                    worker.status("collecting");
                    return true;
                }
                ItemStack collected = items.get(collectSlot);
                items.set(collectSlot, ItemStack.EMPTY);
                machine.updateItems(level, items);
                worker.updateInventory(level.registryAccess(), carried);
                SettlementFurnaceService.applyLoaded(level, machine);
                if (collectSlot == 2) {
                    boolean cooking = worker.role() == HiredVillagerRole.COOK;
                    worker.practice(level, HiredWorkPractice.batch(cooking ? VillagerSkill.COOKING : VillagerSkill.SMITHING,
                            cooking ? "hired:cooking:batch" : "hired:smelting:batch", collected.getCount(), collected.getItem().hashCode()));
                }
                worker.status("collected");
                return true;
            }
            boolean needsInput = items.getFirst().isEmpty();
            boolean needsFuel = items.get(1).isEmpty();
            if (!needsInput && !needsFuel) {
                continue;
            }
            var supplies = SettlementProductionInventory.supplies(level, town, worker, carried, depots);
            List<Integer> consumed = new ArrayList<>(java.util.Collections.nCopies(supplies.size(), 0));
            for (int index = 0; needsInput && index < supplies.size() && items.getFirst().getCount() < 16; index++) {
                ItemStack candidate = supplies.get(index).available();
                if (!inputAllowed(level, worker, machine, filter, candidate)) {
                    continue;
                }
                ItemStack input = items.getFirst();
                if (!input.isEmpty() && !ItemStack.isSameItemSameComponents(input, candidate)) {
                    continue;
                }
                int count = Math.min(candidate.getCount(), Math.min(16, candidate.getMaxStackSize()) - input.getCount());
                if (count > 0) {
                    consumed.set(index, count);
                    if (SettlementProductionInventory.allowed(level, supplies, consumed)) {
                        items.set(0, candidate.copyWithCount(input.getCount() + count));
                    } else {
                        consumed.set(index, 0);
                    }
                }
            }
            if (!items.getFirst().isEmpty() && needsFuel) {
                for (int index = 0; index < supplies.size() && items.get(1).getCount() < 8; index++) {
                    ItemStack candidate = supplies.get(index).available();
                    ItemStack fuel = items.get(1);
                    if (candidate.getBurnTime(machine.recipeType()) <= 0
                            || !fuel.isEmpty() && !ItemStack.isSameItemSameComponents(fuel, candidate)) {
                        continue;
                    }
                    int previous = consumed.get(index);
                    int count = Math.min(candidate.getCount() - previous, Math.min(8, candidate.getMaxStackSize()) - fuel.getCount());
                    if (count > 0) {
                        consumed.set(index, previous + count);
                        if (SettlementProductionInventory.allowed(level, supplies, consumed)) {
                            items.set(1, candidate.copyWithCount(fuel.getCount() + count));
                        } else {
                            consumed.set(index, previous);
                        }
                    }
                }
            }
            if (consumed.stream().mapToInt(Integer::intValue).sum() == 0) {
                continue;
            }
            double cost = 40.0D + 16.0D * Math.sqrt(worker.position().distSqr(machine.position()));
            var visited = new HashSet<UUID>();
            for (int index = 0; index < supplies.size(); index++) {
                var depot = supplies.get(index).depot();
                if (consumed.get(index) > 0 && depot != null && visited.add(depot.id())) {
                    cost += 16.0D * Math.sqrt(machine.position().distSqr(depot.position()));
                }
            }
            worker.work(elapsed, multiplier);
            if (!worker.spend(cost)) {
                worker.status("supplying_furnace");
                return true;
            }
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
            machine.updateItems(level, items);
            worker.updateInventory(level.registryAccess(), carried);
            SettlementFurnaceService.applyLoaded(level, machine);
            worker.status("supplied_furnace");
            return true;
        }
        worker.status(machines.isEmpty() ? "no_furnace" : "waiting_for_furnace",
                machines.isEmpty() ? WorkBlocker.MISSING_FURNACE : WorkBlocker.NONE);
        return finishExisting ? processing : worker.role() == HiredVillagerRole.SMELTER;
    }

    private static boolean inputAllowed(ServerLevel level, SettlementWorkerState worker, SettlementFurnaceState machine,
                                        ItemStack filter, ItemStack input) {
        if (worker.role() == HiredVillagerRole.SMELTER) {
            return SmeltingWorker.isSmeltableOre(level, input, machine.recipeType(), filter);
        }
        return machine.recipe(level, input).filter(recipe -> {
            ItemStack result = recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess());
            return CookingWorker.isFood(result) && HiredProcessingRecipeFilter.allows(level, filter, recipe, input, result);
        }).isPresent();
    }

    private static boolean outputAllowed(ServerLevel level, SettlementWorkerState worker, ItemStack filter, ItemStack output) {
        return worker.role() == HiredVillagerRole.SMELTER ? SmeltingWorker.isDesiredSmeltedOutput(level, filter, output)
                : CookingWorker.isFood(output) && (filter.isEmpty() || VillagerFilterMatcher.matches(level, filter, output));
    }
}
