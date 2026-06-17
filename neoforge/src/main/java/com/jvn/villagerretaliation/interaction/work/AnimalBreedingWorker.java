package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AnimalBreedingWorker extends AbstractBlockWorker {
    private static final double BREEDING_REACH_SQR = 9.0D;
    private static final double HANDLING_REACH_SQR = 9.0D;
    private static final double EGG_PICKUP_REACH_SQR = 2.25D;
    private static final int BREEDING_WORK_TICKS = 3;
    private static final int PRODUCT_WORK_TICKS = 3;
    private static final int VANILLA_PARENT_BREEDING_COOLDOWN_TICKS = 6000;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;
    private static final String NEXT_ANIMAL_SCAN_GAME_TIME_TAG = "NextAnimalBreedingScanGameTime";
    private static final String RECENTLY_HANDLED_ANIMALS_TAG = "RecentlyHandledAnimalBreeding";
    private static final String ANIMAL_ID_TAG = "Animal";
    private static final String COOLDOWN_UNTIL_TAG = "CooldownUntil";
    private static final HiredItemPickup.Messages GROUND_EGG_PICKUP_MESSAGES = new HiredItemPickup.Messages(
            "interaction.work.animal_breeding.output_full_depositing",
            "interaction.work.animal_breeding.output_full_blocked",
            "egg_unreachable",
            "interaction.work.animal_breeding.egg_unreachable",
            "interaction.work.animal_breeding.egg_repositioning",
            "interaction.work.animal_breeding.moving_to_egg",
            "interaction.work.animal_breeding.collected_eggs",
            true,
            false);

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.ANIMAL_HANDLING;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }

        WorkResult eggResult = collectGroundEggs(level, villager, context);
        if (eggResult != null) {
            return eggResult;
        }

        BreedingSearch search = findBreedingPair(level, villager, context);
        if (search.pair() == null) {
            AnimalProductTarget productTarget = findAnimalProductTarget(level, villager, context);
            if (productTarget != null) {
                WorkResult productResult = handleAnimalProduct(level, villager, context, productTarget);
                if (productResult != null) {
                    return productResult;
                }
            }
            context.setProgressTicks(0);
            HiredWorkerBrain.setLastTargetScanResult(context, search.scannedRecently() ? "animal_scan_cooldown" : "no_breedable_pairs");
            if (search.hasPairWithoutFood()) {
                HiredWorkerBrain.setFailure(context, "missing_breeding_food", level.getGameTime() + 100L);
                HiredWorkerBrain.setLastTargetScanResult(context, "missing_breeding_food");
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
                return WorkResult.idle("interaction.work.animal_breeding.missing_food");
            }
            if (roamInsideWorkArea(level, villager, context, 0.35D)) {
                return WorkResult.progressed("interaction.work.animal_breeding.roaming");
            }
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.animal_breeding.no_targets");
        }

        BreedingPair pair = search.pair();
        WorkResult gatheredFood = gatherBreedingFood(level, villager, context, pair);
        if (gatheredFood != null) {
            return gatheredFood;
        }

        if (!canBreedFromCurrentPosition(villager, context, pair)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, pair.first().blockPosition());
            if (!moveToAnimal(level, villager, context, pair.first(), 0.45D)) {
                HiredWorkerBrain.setFailure(context, "animal_target_unreachable", level.getGameTime() + 20L * 30L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, pair.first().blockPosition());
                return WorkResult.idle("interaction.work.animal_breeding.pair_unreachable");
            }
            return WorkResult.progressed("interaction.work.animal_breeding.moving_to_pair");
        }

        stopWorkNavigation(villager);
        faceAnimal(villager, pair.first());
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setLastTargetScanResult(context, "breedable_pair_found");
        setTaskState(context, HiredWorkerTaskState.WORKING, pair.first().blockPosition());

        int progress = context.progressTicks() + 1;
        if (progress < BREEDING_WORK_TICKS) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            return WorkResult.progressed("interaction.work.animal_breeding.feeding");
        }

        context.setProgressTicks(0);
        if (!canAttemptPair(pair.first(), pair.second())) {
            HiredWorkerBrain.setFailure(context, "animal_pair_changed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, pair.first().blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.pair_changed");
        }
        if (context.inventory().consumeSupply(pair.foodPredicate(), 2) < 2) {
            HiredWorkerBrain.setFailure(context, "missing_breeding_food", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, pair.first().blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.missing_food");
        }

        pair.first().setInLove(hirer);
        pair.second().setInLove(hirer);
        rememberHandledAnimals(context, level.getGameTime(), pair.first(), pair.second());
        setTaskState(context, HiredWorkerTaskState.IDLE, pair.first().blockPosition());
        swingWorkTool(villager);
        return WorkResult.completed(
                "interaction.work.animal_breeding.completed",
                java.util.Map.of("target", HiredAnimalBreedingTargets.label(pair.typeId())));
    }

    private BreedingSearch findBreedingPair(ServerLevel level, Villager villager, HiredWorkContext context) {
        long gameTime = level.getGameTime();
        boolean scanCooldown = gameTime < context.state().getLong(NEXT_ANIMAL_SCAN_GAME_TIME_TAG);
        if (scanCooldown) {
            return new BreedingSearch(null, false, true);
        }

        pruneHandledAnimalCooldowns(context, gameTime);
        Set<ResourceLocation> selectedTargets = HiredAnimalBreedingTargets.selectedTargetIds(context.state());
        AABB bounds = workAreaBounds(context);
        List<Animal> animals = new ArrayList<>(level.getEntitiesOfClass(
                Animal.class,
                bounds,
                animal -> isEligibleAnimal(level, context, animal, selectedTargets)));
        animals.sort(Comparator.comparingDouble(villager::distanceToSqr));

        boolean hasPairWithoutFood = false;
        BreedingPair best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < animals.size(); i++) {
            Animal first = animals.get(i);
            for (int j = i + 1; j < animals.size(); j++) {
                Animal second = animals.get(j);
                if (!canAttemptPair(first, second)) {
                    continue;
                }
                Predicate<ItemStack> food = stack -> first.isFood(stack) && second.isFood(stack);
                int carried = HiredSupplyCrafting.countCarried(context, food);
                if (carried < 2 && AssignedStorageService.countItems(villager, food) <= 0) {
                    hasPairWithoutFood = true;
                    continue;
                }
                double score = villager.distanceToSqr(first) + first.distanceToSqr(second) * 0.25D;
                if (score < bestScore) {
                    bestScore = score;
                    best = new BreedingPair(first, second, food, typeId(first));
                }
            }
        }

        if (best == null && !hasPairWithoutFood) {
            context.state().putLong(NEXT_ANIMAL_SCAN_GAME_TIME_TAG, gameTime + NO_TARGET_SCAN_COOLDOWN_TICKS);
        } else {
            context.state().remove(NEXT_ANIMAL_SCAN_GAME_TIME_TAG);
        }
        return new BreedingSearch(best, hasPairWithoutFood, false);
    }

    private WorkResult gatherBreedingFood(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BreedingPair pair) {
        int missing = Math.max(0, 2 - HiredSupplyCrafting.countCarried(context, pair.foodPredicate()));
        if (missing <= 0) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            return null;
        }
        if (!AssignedStorageService.hasAssignedStorage(level, villager)) {
            HiredWorkerBrain.setFailure(context, "missing_breeding_food", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, pair.first().blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.missing_food");
        }
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContaining(level, villager, pair.foodPredicate());
        if (storage == null) {
            HiredWorkerBrain.setFailure(context, "missing_breeding_food", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, pair.first().blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.missing_food");
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                0.45D);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("interaction.work.animal_breeding.collecting_food");
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredWorkerBrain.setFailure(context, "animal_food_storage_path_failed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return WorkResult.idle("interaction.work.animal_breeding.food_unreachable");
        }

        int moved = AssignedStorageService.transferFirstMatchingStackAtAssignedStorage(
                villager,
                storage,
                pair.foodPredicate(),
                context.inventory()::insertSupply);
        if (moved <= 0) {
            HiredWorkerBrain.setFailure(context, "animal_food_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return WorkResult.idle("interaction.work.animal_breeding.food_inventory_full");
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
        return WorkResult.progressed("interaction.work.animal_breeding.gathered_food");
    }

    private WorkResult handleAnimalProduct(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AnimalProductTarget target) {
        if (!canStoreProductOutputs(context, target)) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.45D);
            if (depositResult == DepositResult.DEPOSITED) {
                return WorkResult.progressed("interaction.work.animal_breeding.output_full_depositing");
            }
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.animal_breeding.output_full_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, target.animal().blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.output_full_blocked");
        }

        WorkResult gatheredSupply = gatherAnimalHandlingSupply(level, villager, context, target);
        if (gatheredSupply != null) {
            return gatheredSupply;
        }

        if (!canHandleProductFromCurrentPosition(villager, context, target.animal())) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.animal().blockPosition());
            if (!moveToAnimal(level, villager, context, target.animal(), 0.45D)) {
                HiredWorkerBrain.setFailure(context, "animal_product_target_unreachable", level.getGameTime() + 20L * 30L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.animal().blockPosition());
                return WorkResult.idle("interaction.work.animal_breeding.product_unreachable");
            }
            return WorkResult.progressed(target.movingMessageKey());
        }

        stopWorkNavigation(villager);
        faceAnimal(villager, target.animal());
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setLastTargetScanResult(context, target.scanResult());
        setTaskState(context, HiredWorkerTaskState.WORKING, target.animal().blockPosition());

        int progress = context.progressTicks() + 1;
        if (progress < PRODUCT_WORK_TICKS) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            return WorkResult.progressed(target.workingMessageKey());
        }

        context.setProgressTicks(0);
        return switch (target.kind()) {
            case SHEAR -> shearSheep(level, villager, context, target.sheep());
            case MILK -> milkAnimal(level, villager, context, target.animal());
        };
    }

    private WorkResult gatherAnimalHandlingSupply(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AnimalProductTarget target) {
        if (HiredSupplyCrafting.countCarried(context, target.supplyPredicate()) > 0) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            return null;
        }
        if (!AssignedStorageService.hasAssignedStorage(level, villager)) {
            HiredWorkerBrain.setFailure(context, target.missingSupplyFailure(), level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, target.animal().blockPosition());
            return WorkResult.idle(target.missingSupplyMessageKey());
        }
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContaining(level, villager, target.supplyPredicate());
        if (storage == null) {
            HiredWorkerBrain.setFailure(context, target.missingSupplyFailure(), level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, target.animal().blockPosition());
            return WorkResult.idle(target.missingSupplyMessageKey());
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                0.45D);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed(target.collectingSupplyMessageKey());
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredWorkerBrain.setFailure(context, "animal_product_storage_path_failed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return WorkResult.idle("interaction.work.animal_breeding.product_supply_unreachable");
        }

        int moved = AssignedStorageService.transferFirstMatchingStackAtAssignedStorage(
                villager,
                storage,
                target.supplyPredicate(),
                context.inventory()::insertSupply);
        if (moved <= 0) {
            HiredWorkerBrain.setFailure(context, "animal_product_supply_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return WorkResult.idle("interaction.work.animal_breeding.product_supply_inventory_full");
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
        return WorkResult.progressed(target.gatheredSupplyMessageKey());
    }

    private WorkResult shearSheep(ServerLevel level, Villager villager, HiredWorkContext context, Sheep sheep) {
        if (sheep == null || !sheep.readyForShearing()) {
            HiredWorkerBrain.setFailure(context, "animal_product_target_changed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, sheep == null ? villager.blockPosition() : sheep.blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.product_changed");
        }
        int shearsSlot = firstSupplySlot(context, stack -> stack.is(Items.SHEARS));
        if (shearsSlot < 0) {
            HiredWorkerBrain.setFailure(context, "missing_shears", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, sheep.blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.missing_shears");
        }

        int woolCount = 1 + level.random.nextInt(3);
        ItemStack wool = new ItemStack(woolItem(sheep.getColor()), woolCount);
        if (!context.canStoreOutputs(List.of(wool))) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.45D);
            if (depositResult == DepositResult.DEPOSITED || depositResult == DepositResult.MOVING) {
                return WorkResult.progressed("interaction.work.animal_breeding.output_full_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, sheep.blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.output_full_blocked");
        }
        ItemStack remainder = context.storeOutputAfterDepositIfFull(villager, wool);
        if (!remainder.isEmpty()) {
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, sheep.blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.output_full_blocked");
        }
        sheep.setSheared(true);
        level.playSound(null, sheep, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
        damageSupplyItem(context, shearsSlot, villager);
        swingWorkTool(villager);
        setTaskState(context, HiredWorkerTaskState.IDLE, sheep.blockPosition());
        return WorkResult.completed(
                "interaction.work.animal_breeding.sheared_sheep",
                java.util.Map.of("count", Integer.toString(wool.getCount())));
    }

    private WorkResult milkAnimal(ServerLevel level, Villager villager, HiredWorkContext context, Animal animal) {
        if (!isMilkable(animal)) {
            HiredWorkerBrain.setFailure(context, "animal_product_target_changed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, animal == null ? villager.blockPosition() : animal.blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.product_changed");
        }
        if (context.inventory().consumeSupply(stack -> stack.is(Items.BUCKET), 1) <= 0) {
            HiredWorkerBrain.setFailure(context, "missing_bucket", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, animal.blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.missing_bucket");
        }

        ItemStack milk = new ItemStack(Items.MILK_BUCKET);
        if (!context.canStoreOutputs(List.of(milk))) {
            context.inventory().insertSupply(new ItemStack(Items.BUCKET));
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, animal.blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.output_full_blocked");
        }
        ItemStack remainder = context.storeOutputAfterDepositIfFull(villager, milk);
        if (!remainder.isEmpty()) {
            context.inventory().insertSupply(new ItemStack(Items.BUCKET));
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, animal.blockPosition());
            return WorkResult.idle("interaction.work.animal_breeding.output_full_blocked");
        }
        level.playSound(null, animal, milkingSound(animal), SoundSource.NEUTRAL, 1.0F, 1.0F);
        swingWorkTool(villager);
        setTaskState(context, HiredWorkerTaskState.IDLE, animal.blockPosition());
        return WorkResult.completed(
                "interaction.work.animal_breeding.milked_animal",
                java.util.Map.of("target", HiredAnimalBreedingTargets.label(typeId(animal))));
    }

    private WorkResult collectGroundEggs(ServerLevel level, Villager villager, HiredWorkContext context) {
        return HiredItemPickup.collectNearestOutputItem(
                level,
                villager,
                context,
                this,
                stack -> stack.is(Items.EGG),
                EGG_PICKUP_REACH_SQR,
                0.45D,
                GROUND_EGG_PICKUP_MESSAGES);
    }

    private AnimalProductTarget findAnimalProductTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        Set<ResourceLocation> selectedTargets = HiredAnimalBreedingTargets.selectedTargetIds(context.state());
        AABB bounds = workAreaBounds(context);
        List<Animal> animals = new ArrayList<>(level.getEntitiesOfClass(
                Animal.class,
                bounds,
                animal -> isEligibleProductAnimal(level, context, villager, animal, selectedTargets)));
        animals.sort(Comparator.comparingDouble(villager::distanceToSqr));
        for (Animal animal : animals) {
            if (animal instanceof Sheep sheep && sheep.readyForShearing()) {
                return AnimalProductTarget.shearing(sheep);
            }
            if (isMilkable(animal)) {
                return AnimalProductTarget.milking(animal);
            }
        }
        return null;
    }

    private boolean canStoreProductOutputs(HiredWorkContext context, AnimalProductTarget target) {
        return switch (target.kind()) {
            case SHEAR -> context.hasOutputSpace();
            case MILK -> context.canStoreOutputs(List.of(new ItemStack(Items.MILK_BUCKET)));
        };
    }

    private static boolean isEligibleProductAnimal(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            Animal animal,
            Set<ResourceLocation> selectedTargets) {
        return animal.isAlive()
                && !animal.isBaby()
                && context.isInsideWorkArea(animal.blockPosition())
                && context.isLoaded(level, animal.blockPosition())
                && !HiredPathMemory.isAvoided(level, villager, animal.blockPosition())
                && HiredAnimalBreedingTargets.matches(animal, selectedTargets)
                && (animal instanceof Sheep sheep && sheep.readyForShearing() || isMilkable(animal));
    }

    private static void rememberHandledAnimals(HiredWorkContext context, long gameTime, Animal first, Animal second) {
        long cooldownUntil = gameTime + VANILLA_PARENT_BREEDING_COOLDOWN_TICKS;
        UUID firstId = first.getUUID();
        UUID secondId = second.getUUID();
        ListTag existing = context.state().getList(RECENTLY_HANDLED_ANIMALS_TAG, Tag.TAG_COMPOUND);
        ListTag updated = new ListTag();
        for (int i = 0; i < existing.size(); i++) {
            CompoundTag entry = existing.getCompound(i);
            if (!entry.hasUUID(ANIMAL_ID_TAG) || entry.getLong(COOLDOWN_UNTIL_TAG) <= gameTime) {
                continue;
            }
            UUID animalId = entry.getUUID(ANIMAL_ID_TAG);
            if (!animalId.equals(firstId) && !animalId.equals(secondId)) {
                updated.add(entry.copy());
            }
        }
        appendCooldown(updated, firstId, cooldownUntil);
        appendCooldown(updated, secondId, cooldownUntil);
        context.state().put(RECENTLY_HANDLED_ANIMALS_TAG, updated);
    }

    private static boolean isHandledAnimalOnCooldown(HiredWorkContext context, Animal animal, long gameTime) {
        UUID animalId = animal.getUUID();
        ListTag handledAnimals = context.state().getList(RECENTLY_HANDLED_ANIMALS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < handledAnimals.size(); i++) {
            CompoundTag entry = handledAnimals.getCompound(i);
            if (entry.hasUUID(ANIMAL_ID_TAG)
                    && entry.getUUID(ANIMAL_ID_TAG).equals(animalId)
                    && entry.getLong(COOLDOWN_UNTIL_TAG) > gameTime) {
                return true;
            }
        }
        return false;
    }

    private static void pruneHandledAnimalCooldowns(HiredWorkContext context, long gameTime) {
        ListTag existing = context.state().getList(RECENTLY_HANDLED_ANIMALS_TAG, Tag.TAG_COMPOUND);
        if (existing.isEmpty()) {
            return;
        }
        ListTag updated = new ListTag();
        for (int i = 0; i < existing.size(); i++) {
            CompoundTag entry = existing.getCompound(i);
            if (entry.hasUUID(ANIMAL_ID_TAG) && entry.getLong(COOLDOWN_UNTIL_TAG) > gameTime) {
                updated.add(entry.copy());
            }
        }
        if (updated.isEmpty()) {
            context.state().remove(RECENTLY_HANDLED_ANIMALS_TAG);
        } else {
            context.state().put(RECENTLY_HANDLED_ANIMALS_TAG, updated);
        }
    }

    private static void appendCooldown(ListTag handledAnimals, UUID animalId, long cooldownUntil) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID(ANIMAL_ID_TAG, animalId);
        entry.putLong(COOLDOWN_UNTIL_TAG, cooldownUntil);
        handledAnimals.add(entry);
    }

    private static boolean isEligibleAnimal(
            ServerLevel level,
            HiredWorkContext context,
            Animal animal,
            Set<ResourceLocation> selectedTargets) {
        return animal.isAlive()
                && !animal.isBaby()
                && animal.canFallInLove()
                && !isHandledAnimalOnCooldown(context, animal, level.getGameTime())
                && context.isInsideWorkArea(animal.blockPosition())
                && context.isLoaded(level, animal.blockPosition())
                && HiredAnimalBreedingTargets.matches(animal, selectedTargets);
    }

    private static boolean canAttemptPair(Animal first, Animal second) {
        return first != second
                && first.getType() == second.getType()
                && first.isAlive()
                && second.isAlive()
                && !first.isBaby()
                && !second.isBaby()
                && first.canFallInLove()
                && second.canFallInLove();
    }

    private boolean canBreedFromCurrentPosition(Villager villager, HiredWorkContext context, BreedingPair pair) {
        return context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(pair.first().blockPosition())
                && context.isInsideWorkArea(pair.second().blockPosition())
                && villager.distanceToSqr(pair.first()) <= BREEDING_REACH_SQR;
    }

    private boolean canHandleProductFromCurrentPosition(Villager villager, HiredWorkContext context, Animal animal) {
        return animal != null
                && context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(animal.blockPosition())
                && villager.distanceToSqr(animal) <= HANDLING_REACH_SQR;
    }

    private boolean moveToAnimal(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Animal target,
            double speed) {
        if (!context.isInsideWorkArea(villager.blockPosition())
                || !context.isInsideWorkArea(target.blockPosition())) {
            stopWorkNavigation(villager);
            return false;
        }
        if (villager.distanceToSqr(target) <= BREEDING_REACH_SQR) {
            stopWorkNavigation(villager);
            faceAnimal(villager, target);
            return true;
        }

        BlockPos targetPos = target.blockPosition();
        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null && !HiredMoveToBlockFaceJob.pathStaysInsideFilter(currentPath, context::isInsideWorkArea)) {
            stopWorkNavigation(villager);
            return false;
        }
        Path path = villager.getNavigation().createPath(targetPos, 0);
        if (path != null && path.canReach() && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(targetPos));
            boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(villager, path, targetPos, speed, 0);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(level, villager, targetPos, villager.distanceToSqr(targetPos.getCenter()));
            }
            return moved;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private static AABB workAreaBounds(HiredWorkContext context) {
        return new AABB(
                context.workMin().getX(),
                context.workMin().getY(),
                context.workMin().getZ(),
                context.workMax().getX() + 1.0D,
                context.workMax().getY() + 1.0D,
                context.workMax().getZ() + 1.0D);
    }

    private void faceAnimal(Villager villager, Animal animal) {
        Vec3 center = animal.position().add(0.0D, animal.getBbHeight() * 0.5D, 0.0D);
        faceBlock(villager, center);
    }

    private static ResourceLocation typeId(Animal animal) {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
    }

    private static boolean isMilkable(Animal animal) {
        return animal instanceof Cow || animal instanceof Goat;
    }

    private static net.minecraft.sounds.SoundEvent milkingSound(Animal animal) {
        return animal instanceof Goat ? SoundEvents.GOAT_MILK : SoundEvents.COW_MILK;
    }

    private static int firstSupplySlot(HiredWorkContext context, Predicate<ItemStack> predicate) {
        for (int slot : context.inventory().supplySlots()) {
            ItemStack stack = context.inventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static void damageSupplyItem(HiredWorkContext context, int slot, Villager villager) {
        ItemStack stack = context.inventory().getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        stack.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
        if (stack.isEmpty()) {
            context.inventory().setItem(slot, ItemStack.EMPTY);
        } else {
            context.inventory().setChanged();
        }
    }

    private static Item woolItem(DyeColor color) {
        return switch (color) {
            case WHITE -> Items.WHITE_WOOL;
            case ORANGE -> Items.ORANGE_WOOL;
            case MAGENTA -> Items.MAGENTA_WOOL;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
            case YELLOW -> Items.YELLOW_WOOL;
            case LIME -> Items.LIME_WOOL;
            case PINK -> Items.PINK_WOOL;
            case GRAY -> Items.GRAY_WOOL;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case CYAN -> Items.CYAN_WOOL;
            case PURPLE -> Items.PURPLE_WOOL;
            case BLUE -> Items.BLUE_WOOL;
            case BROWN -> Items.BROWN_WOOL;
            case GREEN -> Items.GREEN_WOOL;
            case RED -> Items.RED_WOOL;
            case BLACK -> Items.BLACK_WOOL;
        };
    }

    private record BreedingSearch(BreedingPair pair, boolean hasPairWithoutFood, boolean scannedRecently) {
    }

    private record BreedingPair(Animal first, Animal second, Predicate<ItemStack> foodPredicate, ResourceLocation typeId) {
    }

    private enum AnimalProductKind {
        SHEAR,
        MILK
    }

    private record AnimalProductTarget(Animal animal, AnimalProductKind kind) {
        static AnimalProductTarget shearing(Sheep sheep) {
            return new AnimalProductTarget(sheep, AnimalProductKind.SHEAR);
        }

        static AnimalProductTarget milking(Animal animal) {
            return new AnimalProductTarget(animal, AnimalProductKind.MILK);
        }

        Sheep sheep() {
            return this.animal instanceof Sheep sheep ? sheep : null;
        }

        Predicate<ItemStack> supplyPredicate() {
            return switch (this.kind) {
                case SHEAR -> stack -> stack.is(Items.SHEARS);
                case MILK -> stack -> stack.is(Items.BUCKET);
            };
        }

        String scanResult() {
            return switch (this.kind) {
                case SHEAR -> "shearable_sheep_found";
                case MILK -> "milkable_animal_found";
            };
        }

        String missingSupplyFailure() {
            return switch (this.kind) {
                case SHEAR -> "missing_shears";
                case MILK -> "missing_bucket";
            };
        }

        String missingSupplyMessageKey() {
            return switch (this.kind) {
                case SHEAR -> "interaction.work.animal_breeding.missing_shears";
                case MILK -> "interaction.work.animal_breeding.missing_bucket";
            };
        }

        String collectingSupplyMessageKey() {
            return switch (this.kind) {
                case SHEAR -> "interaction.work.animal_breeding.collecting_shears";
                case MILK -> "interaction.work.animal_breeding.collecting_bucket";
            };
        }

        String gatheredSupplyMessageKey() {
            return switch (this.kind) {
                case SHEAR -> "interaction.work.animal_breeding.gathered_shears";
                case MILK -> "interaction.work.animal_breeding.gathered_bucket";
            };
        }

        String movingMessageKey() {
            return switch (this.kind) {
                case SHEAR -> "interaction.work.animal_breeding.moving_to_sheep";
                case MILK -> "interaction.work.animal_breeding.moving_to_milk";
            };
        }

        String workingMessageKey() {
            return switch (this.kind) {
                case SHEAR -> "interaction.work.animal_breeding.shearing";
                case MILK -> "interaction.work.animal_breeding.milking";
            };
        }
    }
}
