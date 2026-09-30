package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.work.AnimalBreedingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.util.VillagerEquipmentDurability;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class SettlementBackgroundAnimalHandling {
    private SettlementBackgroundAnimalHandling() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        if (SettlementGatheringPolicy.hold(level, town, worker, elapsed, multiplier)) return;
        var before = SettlementGatheringPolicy.before(level, town, worker);
        try {
            workTick(level, town, worker, elapsed, multiplier);
        } finally {
            SettlementGatheringPolicy.received(level, town, worker, before);
        }
    }

    private static void workTick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var area = town.areas().get(worker.area());
        if (area == null || !area.enabled()) return;
        var depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        if ((!worker.settings().contains("AutoDepositOutputs") || worker.settings().getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) return;
        var owner = worker.context(level, town);
        if (owner == null) {
            worker.status("awaiting_worker_observation");
            return;
        }
        var inventory = HiredJobInventory.getJobInventory(owner);
        var budget = new SettlementWorkBudget(worker, elapsed, multiplier);
        try {
            if (HiredAnimalHandlingOptions.collect(worker.settings())
                    && SettlementItemDropService.collect(level, town, worker, owner, inventory, budget,
                    stack -> stack.is(Items.EGG) || AnimalBreedingWorker.isAnimalDrop(stack))) return;
            if (SettlementBackgroundCulling.tick(level, town, worker, owner, inventory, depots, budget)) return;
            var feeding = SettlementBackgroundBreeding.feed(level, town, worker, inventory, depots, budget);
            if (feeding == SettlementBackgroundBreeding.Result.ACTED) return;
            for (int action = 0; action < 4; action++) {
                if (!SettlementGatheringPolicy.action(level, town, worker, inventory)) return;
                var candidates = town.livestock().values().stream()
                        .filter(animal -> animal.area().equals(area.id()) && area.contains(animal.position()) && !animal.unsafe())
                        .sorted(Comparator.comparingDouble(animal -> animal.position().distSqr(worker.position()))).toList();
                var selected = HiredAnimalBreedingTargets.selectedTargetIds(worker.settings());
                SettlementLivestock target = null;
                Animal animal = null;
                for (var candidate : candidates) {
                    var current = candidate.context(level);
                    if (current != null && current.isAlive() && !current.isBaby() && HiredAnimalBreedingTargets.matches(current, selected)
                            && (current instanceof Sheep sheep && HiredAnimalHandlingOptions.shearSheep(worker.settings())
                                    && sheep.readyForShearing() || HiredAnimalHandlingOptions.milk(worker.settings()) && AnimalBreedingWorker.isMilkable(current))) {
                        target = candidate;
                        animal = current;
                        break;
                    }
                }
                if (target == null) {
                    worker.status(feeding == SettlementBackgroundBreeding.Result.NO_FOOD ? "missing_breeding_food"
                            : candidates.isEmpty() ? "no_observed_livestock" : "no_animal_products");
                    return;
                }
                int steps = target.steps(worker);
                if (steps < 0) {
                    worker.status("animal_unreachable");
                    return;
                }
                boolean shear = animal instanceof Sheep;
                ItemStack tool = ItemStack.EMPTY;
                if (shear) {
                    tool = inventory.equipBestTool(stack -> stack.is(Items.SHEARS),
                            stack -> stack.getMaxDamage() - stack.getDamageValue());
                    if (tool.isEmpty()) {
                        SettlementBackgroundTools.fetchTool(level, town, worker, inventory, depots, stack -> stack.is(Items.SHEARS),
                                stack -> stack.getMaxDamage() - stack.getDamageValue(), "shears", budget);
                        return;
                    }
                } else if (inventory.findSupply(stack -> stack.is(Items.BUCKET)).isEmpty()) {
                    fetchBucket(level, town, worker, inventory, depots, budget);
                    return;
                }
                double cost = productTicks(worker) + steps * 16.0D;
                if (budget.available() < cost) {
                    budget.spend(cost);
                    worker.status(shear ? "shearing" : "milking");
                    return;
                }
                ItemStack product = shear ? new ItemStack(AnimalBreedingWorker.woolItem(((Sheep) animal).getColor()),
                        1 + level.random.nextInt(3)) : new ItemStack(Items.MILK_BUCKET);
                Map<net.minecraft.world.item.Item, Integer> inputs = shear ? Map.of() : Map.of(Items.BUCKET, 1);
                if (!(shear ? inventory.canStoreOutputs(List.of(product))
                        : inventory.canTransformSuppliesToOutputs(inputs, List.of(product)))) {
                    worker.status("output_full");
                    return;
                }
                if (!budget.spend(cost)) return;
                boolean stored;
                if (shear) {
                    var before = inventory.captureSnapshot();
                    stored = inventory.insertOutput(product).isEmpty();
                    if (!stored) inventory.restoreSnapshot(before);
                } else {
                    stored = inventory.tryTransformSuppliesToOutputs(inputs, List.of(product));
                }
                if (!stored) {
                    worker.status("output_full");
                    return;
                }
                if (shear) {
                    target.shear();
                    VillagerEquipmentDurability.hurtTool(tool, owner, EquipmentSlot.MAINHAND);
                    inventory.syncMainHandEquipment();
                    inventory.setChanged();
                }
                worker.practice(level, HiredWorkPractice.animal(shear ? "shear" : "milk",
                        shear ? 0.35D * product.getCount() : 0.5D, target.type().hashCode()));
                SettlementLivestockService.applyLoaded(level, target);
                worker.status(shear ? "sheared_sheep" : "milked_animal");
                town.changed();
            }
        } finally {
            var snapshot = inventory.captureSnapshot();
            if (!snapshot.equals(worker.inventory())) worker.changed();
            worker.inventorySnapshot(snapshot);
        }
    }

    static int productTicks(SettlementWorkerState worker) {
        int efficiency = HiredVillagerWorkService.calculateEfficiencyPercent(
                VillagerRetaliationConfig.HIRED_WORK_BASE_EFFICIENCY_PERCENT.get(),
                HiredVillagerRoles.roleCadencePercent(worker.role(), worker.aptitude()), 0, false,
                VillagerRetaliationConfig.HIRED_WORK_MINIMUM_EFFICIENCY_PERCENT.get(),
                VillagerRetaliationConfig.HIRED_WORK_MAXIMUM_EFFICIENCY_PERCENT.get());
        return HiredVillagerWorkService.effectiveWorkTickInterval(efficiency) * 3
                + HiredVillagerWorkService.completedTaskCooldownTicks(efficiency);
    }

    private static void fetchBucket(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredJobInventory inventory,
                                    List<SettlementDepot> depots, SettlementWorkBudget budget) {
        var items = SettlementBackgroundTools.items(inventory);
        var supplies = SettlementProductionInventory.supplies(level, town, worker, items, depots);
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (!source.available().is(Items.BUCKET) || !SettlementBackgroundTools.allowed(level, supplies, index)) continue;
            if (source.depot() == null) items.get(source.slot()).shrink(1);
            if (!SettlementProductionInventory.insertSupplies(worker, items, List.of(source.available().copyWithCount(1)))) {
                worker.status("supply_full");
                return;
            }
            double cost = 40.0D + (source.depot() == null ? 0.0D
                    : 16.0D * Math.sqrt(worker.position().distSqr(source.depot().position())));
            if (!budget.spend(cost)) {
                worker.status("fetching_bucket");
                return;
            }
            if (source.depot() != null) source.depot().removeItem(source.slot(), 1);
            SettlementBackgroundTools.replaceItems(level, inventory, items);
            worker.status("fetched_bucket");
            return;
        }
        worker.status("missing_bucket");
    }
}
