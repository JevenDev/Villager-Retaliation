package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
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
        var order = SettlementBrewingOrder.read(worker.settings());
        var route = order == null ? null : HiredBrewingRecipeCatalog.find(level, order.item(), order.potion()).orElse(null);
        if (route == null) {
            worker.status("choose_brewing_order");
            return;
        }
        var machines = town.brewingStands().values().stream().filter(state -> state.area().equals(worker.area()))
                .sorted(Comparator.comparingDouble(state -> state.position().distSqr(worker.position()))).toList();
        for (var machine : machines) {
            if (!SettlementBrewingStandService.available(level, machine)) {
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
                    return;
                }
                if (!spend(worker, elapsed, multiplier, travel(worker, machine), "collecting")) {
                    return;
                }
                ingredient.shrink(removeIngredient);
                commit(level, worker, machine, carried, items);
                worker.status("collected");
                return;
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
                if (!SettlementProductionInventory.insertOutputs(worker, carried, outputs)) {
                    worker.status("output_full");
                    return;
                }
                if (!spend(worker, elapsed, multiplier, travel(worker, machine), "collecting")) {
                    return;
                }
                commit(level, worker, machine, carried, items);
                if (!order.continuous()) {
                    worker.brewingOrder(collected >= order.amount() ? null
                            : new SettlementBrewingOrder(order.item(), order.potion(), order.amount() - collected, false));
                }
                worker.practice(level, HiredWorkPractice.batch(VillagerSkill.MEDICINE, "hired:brewing:batch",
                        collected, route.output().getItem().hashCode()));
                worker.status("brewed");
                return;
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
            if (consumed.stream().mapToInt(Integer::intValue).sum() == 0) {
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
                return;
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
            commit(level, worker, machine, carried, items);
            worker.status("supplied_brewing_stand");
            return;
        }
        worker.status(machines.isEmpty() ? "no_brewing_stand" : "waiting_for_brewing_stand");
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

    private static int currentStep(ServerLevel level, NonNullList<ItemStack> items, HiredBrewingRecipeCatalog.BrewingRoute route) {
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

    private static boolean remainder(HiredBrewingRecipeCatalog.BrewingRoute route, ItemStack stack) {
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
