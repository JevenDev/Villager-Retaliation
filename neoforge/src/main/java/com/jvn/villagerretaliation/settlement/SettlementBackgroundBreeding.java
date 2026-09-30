package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.AnimalBreedingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

final class SettlementBackgroundBreeding {
    private SettlementBackgroundBreeding() {
    }

    static Result feed(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredJobInventory inventory,
                       List<SettlementDepot> depots, SettlementWorkBudget budget) {
        if (!com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions.breed(worker.settings())) return Result.NONE;
        var area = town.areas().get(worker.area());
        var selected = HiredAnimalBreedingTargets.selectedTargetIds(worker.settings());
        var animals = new ArrayList<Candidate>();
        for (var state : town.livestock().values()) {
            if (!state.area().equals(worker.area()) || !area.contains(state.position()) || state.unsafe()
                    || !SettlementLivestockBreeding.supported(state.type())) continue;
            var animal = state.context(level);
            if (animal != null && animal.isAlive() && animal.getAge() == 0 && animal.canFallInLove()
                    && HiredAnimalBreedingTargets.matches(animal, selected)
                    && !AnimalBreedingWorker.isHandledAnimalOnCooldown(worker.settings(), animal, worker.checkedAt())) {
                animals.add(new Candidate(state, animal));
            }
        }
        animals.sort(Comparator.comparingDouble(candidate -> candidate.state().position().distSqr(worker.position())));
        boolean missingFood = false;
        Candidate first = null;
        Candidate second = null;
        Predicate<ItemStack> food = null;
        double best = Double.MAX_VALUE;
        var supplies = SettlementProductionInventory.supplies(level, town, worker, SettlementBackgroundTools.items(inventory), depots);
        for (int a = 0; a < animals.size(); a++) {
            for (int b = a + 1; b < animals.size(); b++) {
                var one = animals.get(a);
                var two = animals.get(b);
                if (one.animal().getType() != two.animal().getType() || one.animal().distanceToSqr(two.animal()) > 9.0D) continue;
                Predicate<ItemStack> match = stack -> one.animal().isFood(stack) && two.animal().isFood(stack);
                boolean stocked = count(inventory, match) >= 2;
                for (int index = 0; !stocked && index < supplies.size(); index++) {
                    stocked = supplies.get(index).depot() != null && match.test(supplies.get(index).available())
                            && SettlementBackgroundTools.allowed(level, supplies, index);
                }
                if (!stocked) {
                    missingFood = true;
                    continue;
                }
                double score = one.state().position().distSqr(worker.position()) + two.state().position().distSqr(worker.position())
                        + one.animal().distanceToSqr(two.animal()) * 0.25D;
                if (score < best) {
                    best = score;
                    first = one;
                    second = two;
                    food = match;
                }
            }
        }
        if (first == null) return missingFood ? Result.NO_FOOD : Result.NONE;
        int firstSteps = first.state().steps(worker);
        int secondSteps = second.state().steps(worker);
        if (firstSteps < 0 || secondSteps < 0) {
            worker.status("animal_unreachable");
            return Result.ACTED;
        }
        int missing = 2 - count(inventory, food);
        if (missing > 0) {
            fetch(level, worker, inventory, supplies, food, missing, budget);
            return Result.ACTED;
        }
        if (!budget.spend(SettlementBackgroundAnimalHandling.productTicks(worker) + Math.max(firstSteps, secondSteps) * 16.0D)) {
            worker.status("feeding_animals");
            return Result.ACTED;
        }
        if (!inventory.consumeSupplyExactly(food, 2)) {
            worker.status("missing_breeding_food");
            return Result.ACTED;
        }
        first.state().feed();
        second.state().feed();
        AnimalBreedingWorker.rememberHandledAnimals(worker.settings(), worker.checkedAt(), first.animal(), second.animal());
        SettlementLivestockService.applyLoaded(level, first.state());
        SettlementLivestockService.applyLoaded(level, second.state());
        worker.practice(level, HiredWorkPractice.animal("breed", 1.2D, first.state().type().hashCode()));
        worker.status("fed_animals");
        town.changed();
        return Result.ACTED;
    }

    private static int count(HiredJobInventory inventory, Predicate<ItemStack> food) {
        return inventory.supplySlots().stream().map(inventory::getItem).filter(food).mapToInt(ItemStack::getCount).sum();
    }

    private static void fetch(ServerLevel level, SettlementWorkerState worker, HiredJobInventory inventory,
                              List<SettlementProductionInventory.Supply> supplies, Predicate<ItemStack> food,
                              int missing, SettlementWorkBudget budget) {
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (source.depot() == null || !food.test(source.available())) continue;
            int count = Math.min(missing, source.available().getCount());
            var consumed = new ArrayList<>(java.util.Collections.nCopies(supplies.size(), 0));
            consumed.set(index, count);
            if (!SettlementProductionInventory.allowed(level, supplies, consumed)) continue;
            var items = SettlementBackgroundTools.items(inventory);
            if (!SettlementProductionInventory.insertSupplies(worker, items, List.of(source.available().copyWithCount(count)))) {
                worker.status("supply_full");
                return;
            }
            if (!budget.spend(40.0D + 16.0D * Math.sqrt(worker.position().distSqr(source.depot().position())))) {
                worker.status("fetching_breeding_food");
                return;
            }
            source.depot().removeItem(source.slot(), count);
            SettlementBackgroundTools.replaceItems(level, inventory, items);
            worker.status("fetched_breeding_food");
            return;
        }
        worker.status("missing_breeding_food");
    }

    enum Result {
        NONE, NO_FOOD, ACTED
    }

    private record Candidate(SettlementLivestock state, Animal animal) {
    }
}
