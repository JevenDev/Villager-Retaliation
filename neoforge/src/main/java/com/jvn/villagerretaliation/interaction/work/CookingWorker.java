package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.quest.tutorial.*;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.interaction.work.production.HiredQueuedFurnaceWorker;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CookingWorker extends AbstractBlockWorker {
    private static final String CACHED_STATION_POS_TAG = "CookingCachedStationPos";
    private static final String NEXT_STATION_SCAN_GAME_TIME_TAG = "NextCookingStationScanGameTime";
    private static final long FACILITY_SCAN_COOLDOWN_TICKS = 100L;
    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int RESULT_SLOT = 2;
    private static final int MAX_STATION_PATH_ATTEMPTS = 12;
    private static final int MAX_INPUT_PULL = 16;
    private static final int MAX_FUEL_PULL = 8;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.COOK;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (TutorialWorkService.isTrial(villager) && !TutorialWorkService.canTick(villager, role())) return WorkResult.idle("interaction.work.tutorial.paused");
        if (context.services(villager).production(role()).managed()) {
            return new HiredQueuedFurnaceWorker(role()).tick(level, villager, hirer, context);
        }
        return tickLegacy(level, villager, hirer, context, false);
    }

    public WorkResult finishExisting(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        return tickLegacy(level, villager, hirer, context, true);
    }

    private WorkResult tickLegacy(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context, boolean finishExisting) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }
        if (AssignedStorageService.hasAssignedStorage(level, villager)) {
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (depositResult == DepositResult.MOVING) {
                return WorkResult.progressed("interaction.work.cooking.depositing_outputs");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
        }

        var tutorial = TutorialWorkService.run(villager).orElse(null);
        if (TutorialWorkService.isTrial(villager) && (tutorial == null || tutorial.committedUnits("process") >= 2 && (TutorialFurnacePlan.station(level, villager) == null
                || !(level.getBlockEntity(TutorialFurnacePlan.station(level, villager)) instanceof AbstractFurnaceBlockEntity boundFurnace) || boundFurnace.getItem(2).isEmpty()))) return WorkResult.idle("interaction.work.tutorial.paused");
        ItemStack foodFilter = VillagerItemFilterService.assignedFilter(villager);
        Predicate<ItemStack> desiredFood = desiredFoodPredicate(level, foodFilter);
        BlockPos station = nearestCookingStation(level, villager, context, foodFilter, desiredFood, finishExisting);
        if (station == null && finishExisting) return null;
        CraftingAssessment crafting = CraftingAssessment.NONE;
        if (!TutorialWorkService.isTrial(villager) && !finishExisting && !stationHasCollectibleOutput(level, station, desiredFood)) {
            crafting = assessCraftingTargets(level, villager, context, foodFilter);
            if (crafting.selection() != null) {
                return tickCraftingFood(level, villager, context, crafting.selection(), null, null);
            }
        }

        if (station == null) {
            context.setProgressTicks(0);
            if (crafting.hasRecipe()) {
                String reason = crafting.missingCraftingTable()
                        ? "no_cooking_crafting_table"
                        : "missing_cooking_crafting_materials";
                String status = crafting.missingCraftingTable()
                        ? "interaction.work.cooking.no_crafting_table"
                        : "interaction.work.cooking.missing_crafting_materials";
                HiredWorkerBrain.setFailure(context, reason, level.getGameTime() + 100L);
                setTaskState(context, crafting.missingCraftingTable()
                        ? HiredWorkerTaskState.SELECTING_TARGET
                        : HiredWorkerTaskState.WAITING_FOR_MATERIALS);
                return WorkResult.idle(status).withBlocker(crafting.missingCraftingTable()
                        ? WorkBlocker.MISSING_CRAFTING_TABLE : WorkBlocker.NONE);
            }
            HiredWorkerBrain.setFailure(context, "no_cooking_station", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.cooking.no_station").withBlocker(WorkBlocker.MISSING_FURNACE);
        }
        if (!(level.getBlockEntity(station) instanceof AbstractFurnaceBlockEntity furnace)) {
            clearCachedStation(context.state());
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "no_cooking_station", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.cooking.no_station").withBlocker(WorkBlocker.MISSING_FURNACE);
        }

        RecipeType<AbstractCookingRecipe> recipeType = recipeType(furnace);
        ItemStack output = furnace.getItem(RESULT_SLOT);
        if (!output.isEmpty() && (!isFood(output) || !desiredFood.test(output))) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "cooking_wrong_output", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, station);
            return WorkResult.idle("interaction.work.cooking.wrong_output");
        }
        ItemStack input = furnace.getItem(INPUT_SLOT);
        if (!input.isEmpty() && !isCookableFood(level, input, recipeType, foodFilter)) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "cooking_wrong_input", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, station);
            return WorkResult.idle("interaction.work.cooking.wrong_input");
        }
        ItemStack fuel = furnace.getItem(FUEL_SLOT);
        boolean fuelRemainder = isFuelRemainder(fuel);
        if (!fuel.isEmpty() && !fuelRemainder && !isFuel(fuel, recipeType)) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "cooking_wrong_fuel", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, station);
            return WorkResult.idle("interaction.work.cooking.wrong_fuel");
        }

        if (output.isEmpty() && !fuelRemainder) {
            WorkResult gatheredMaterials = gatherCookingMaterials(
                    level,
                    villager,
                    context,
                    station,
                    furnace,
                    recipeType,
                    foodFilter);
            if (gatheredMaterials != null) {
                context.setProgressTicks(0);
                return gatheredMaterials;
            }
        }

        HiredPathTarget target = bestWorkTarget(level, villager, context, station);
        if (target == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "cooking_station_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, station);
            return WorkResult.idle("interaction.work.cooking.station_unreachable");
        }
        prepareBreakingTarget(level, context, villager, target);
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, station);
            if (!moveToTarget(level, villager, context, target, 0.45D)) {
                if (recordWorkPathFailure(level, villager, station)) {
                    HiredWorkerBrain.setFailure(context, "cooking_station_path_failed", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, station);
                    return WorkResult.idle("interaction.work.cooking.station_blocked");
                }
                return WorkResult.progressed("interaction.work.cooking.repositioning_station");
            }
            return WorkResult.progressed("interaction.work.cooking.moving_to_station");
        }

        clearWorkPathFailure(villager, station);
        HiredWorkerBrain.clearFailure(context);
        holdWorkPosition(villager, target);
        setTaskState(context, HiredWorkerTaskState.WORKING, station);
        context.setProgressTicks(0);
        return workCookingStation(level, villager, context, furnace, station, recipeType, foodFilter);
    }

    private WorkResult workCookingStation(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractFurnaceBlockEntity furnace,
            BlockPos station,
            RecipeType<AbstractCookingRecipe> recipeType,
            ItemStack foodFilter) {
        ItemStack output = furnace.getItem(RESULT_SLOT);
        if (!output.isEmpty()) {
            return collectOutput(level, villager, context, furnace, station, RESULT_SLOT, output, "interaction.work.cooking.collected_output", true);
        }

        ItemStack fuel = furnace.getItem(FUEL_SLOT);
        if (isFuelRemainder(fuel)) {
            return collectOutput(level, villager, context, furnace, station, FUEL_SLOT, fuel, "interaction.work.cooking.collected_fuel_remainder", false);
        }

        ItemStack input = furnace.getItem(INPUT_SLOT);
        if (input.isEmpty()) {
            ItemStack carriedFood = context.inventory().findSupply(
                    stack -> isCookableFood(level, stack, recipeType, foodFilter));
            if (carriedFood.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_cooking_raw_food", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.cooking.missing_raw_food");
            }
            int count = Math.min(carriedFood.getCount(), Math.min(context.transferLimit(MAX_INPUT_PULL), furnace.getMaxStackSize(carriedFood)));
            TutorialRun.Permit loadPermit = null;
            if (TutorialWorkService.isTrial(villager)) {
                var run = TutorialWorkService.run(villager).orElseThrow();
                if (!carriedFood.is(TutorialFurnacePlan.input(role()))) return WorkResult.idle("interaction.work.tutorial.paused");
                count = Math.min(count, 2 - run.committedUnits("load"));
                loadPermit = TutorialFurnaceService.reserveLoad(villager, furnace, count, false);
                if (loadPermit == null) return WorkResult.idle("interaction.work.tutorial.paused");
            }
            ItemStack loaded = consumeCarriedSupply(context, carriedFood, count);
            if (loaded.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_cooking_raw_food", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.cooking.missing_raw_food");
            }
            furnace.setItem(INPUT_SLOT, loaded);
            CookBonusService.loaded(level, villager, furnace);
            if (loadPermit != null) TutorialWorkService.committed(villager, loadPermit, "", 0, "");
            updateFurnace(level, furnace, station);
            useWorkItem(level, villager, loaded);
            return WorkResult.progressed("interaction.work.cooking.loaded_input", itemReplacements(loaded));
        }

        if (fuel.isEmpty()) {
            ItemStack carriedFuel = context.inventory().findSupply(stack -> isFuel(stack, recipeType));
            if (carriedFuel.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_cooking_fuel", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.cooking.missing_fuel");
            }
            int count = Math.min(carriedFuel.getCount(), Math.min(context.transferLimit(MAX_FUEL_PULL), furnace.getMaxStackSize(carriedFuel)));
            TutorialRun.Permit fuelPermit = null;
            if (TutorialWorkService.isTrial(villager)) {
                if (!carriedFuel.is(Items.COAL)) return WorkResult.idle("interaction.work.tutorial.paused");
                count = 1;
                fuelPermit = TutorialFurnaceService.reserveLoad(villager, furnace, count, true);
                if (fuelPermit == null) return WorkResult.idle("interaction.work.tutorial.paused");
            }
            ItemStack loaded = consumeCarriedSupply(context, carriedFuel, count);
            if (loaded.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_cooking_fuel", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.cooking.missing_fuel");
            }
            furnace.setItem(FUEL_SLOT, loaded);
            if (fuelPermit != null) TutorialWorkService.committed(villager, fuelPermit, "", 0, "");
            updateFurnace(level, furnace, station);
            useWorkItem(level, villager, loaded);
            return WorkResult.progressed("interaction.work.cooking.loaded_fuel", itemReplacements(loaded));
        }

        return WorkResult.progressed("interaction.work.cooking.waiting_station");
    }

    private WorkResult collectOutput(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractFurnaceBlockEntity furnace,
            BlockPos station,
            int slot,
            ItemStack stack,
            String status,
            boolean completed) {
        if (TutorialWorkService.isTrial(villager) && (!completed || !TutorialOutputService.attributable(villager, stack)))
            return WorkResult.idle("interaction.work.tutorial.paused");
        ItemStack planned = stack.copyWithCount(Math.min(stack.getCount(), context.transferLimit(MAX_INPUT_PULL)));
        int bonus = completed ? CookBonusService.bonus(level, villager, furnace, planned.getCount()) : 0;
        ItemStack combined = planned.copyWithCount(planned.getCount() + bonus);
        if (!context.canStoreOutputs(List.of(combined))) {
            OutputFullHandling handling = handleOutputFullInventory(
                    level,
                    context,
                    villager,
                    0.45D,
                    station,
                    "interaction.work.cooking.depositing_outputs",
                    "interaction.work.cooking.output_full");
            if (handling.handled()) {
                return handling.result();
            }
            return WorkResult.idle("interaction.work.cooking.output_full");
        }
        var snapshot = context.inventory().captureSnapshot();
        ItemStack removed = furnace.removeItem(slot, planned.getCount());
        if (removed.isEmpty()) return WorkResult.progressed("interaction.work.cooking.waiting_station");
        ItemStack remainder = context.inventory().insertOutput(removed.copyWithCount(removed.getCount() + bonus));
        if (!remainder.isEmpty()) {
            context.inventory().restoreSnapshot(snapshot);
            restoreFurnaceStack(furnace, slot, removed);
            return WorkResult.idle("interaction.work.cooking.output_full");
        }
        if (completed) CookBonusService.collected(furnace, removed.getCount(), bonus);
        updateFurnace(level, furnace, station);
        useWorkItem(level, villager, removed);
        if (!completed) {
            return WorkResult.progressed(status, itemReplacements(removed));
        }
        return WorkResult.completedWithPractice(
                status,
                itemReplacements(removed.copyWithCount(removed.getCount() + bonus)),
                HiredWorkPractice.batch(
                        VillagerSkill.COOKING, "hired:cooking:batch",
                        removed.getCount(), removed.getItem().hashCode()));
    }

    private WorkResult gatherCookingMaterials(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos station,
            AbstractFurnaceBlockEntity furnace,
            RecipeType<AbstractCookingRecipe> recipeType,
            ItemStack foodFilter) {
        List<MaterialNeed> needs = materialNeeds(level, villager, context, furnace, recipeType, foodFilter);
        if (needs.isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            return null;
        }
        MaterialNeed primaryNeed = needs.getFirst();
        if (!context.useAssignedStorageForSupplies() || !AssignedStorageService.hasAssignedStorage(level, villager)) {
            HiredWorkerBrain.setFailure(context, primaryNeed.failureReason(), level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
            return WorkResult.idle(primaryNeed.missingStatus());
        }

        Predicate<ItemStack> storageNeedFilter = stack -> matchesAnyNeed(needs, stack);
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContainingIgnoringFilter(
                level,
                villager,
                storageNeedFilter);
        if (storage == null) {
            HiredWorkerBrain.setFailure(context, primaryNeed.failureReason(), level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
            return WorkResult.idle(primaryNeed.missingStatus());
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
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, storage);
            return WorkResult.progressed("interaction.work.cooking.collecting_materials");
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            BlockPos failedStorage = storage;
            for (BlockPos alternateStorage : AssignedStorageService.assignedStoragePositionsContainingIgnoringFilter(
                    level,
                    villager,
                    storageNeedFilter)) {
                if (failedStorage.equals(alternateStorage)) {
                    continue;
                }
                HiredWorkerBrain.setStorageTarget(context, alternateStorage);
                HiredStorageNavigationGoal.Result alternateMoveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                        level,
                        context,
                        villager,
                        alternateStorage,
                        0.45D);
                if (alternateMoveResult == HiredStorageNavigationGoal.Result.MOVING) {
                    HiredWorkerBrain.clearFailure(context);
                    setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, alternateStorage);
                    return WorkResult.progressed("interaction.work.cooking.collecting_materials");
                }
                if (alternateMoveResult == HiredStorageNavigationGoal.Result.ARRIVED) {
                    storage = alternateStorage;
                    HiredWorkerBrain.setStorageTarget(context, storage);
                    moveResult = HiredStorageNavigationGoal.Result.ARRIVED;
                    break;
                }
            }
            if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
                HiredWorkerBrain.setStorageTarget(context, failedStorage);
                HiredWorkerBrain.setFailure(context, "cooking_storage_path_failed", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, failedStorage);
                return WorkResult.idle("interaction.work.cooking.materials_unreachable");
            }
        }

        faceBlock(villager, storage);
        int movedTotal = 0;
        int remainingTripCapacity = context.transferLimit(MAX_INPUT_PULL);
        for (MaterialNeed need : needs) {
            if (remainingTripCapacity <= 0) {
                break;
            }
            int moved = AssignedStorageService.transferItemsAtAssignedStorageIgnoringFilter(
                    villager,
                    storage,
                    need.predicate(),
                    Math.min(need.count(), remainingTripCapacity),
                    context.inventory()::insertSupplyFromStorage);
            movedTotal += moved;
            remainingTripCapacity -= moved;
        }
        if (movedTotal <= 0) {
            HiredWorkerBrain.setFailure(context, "cooking_material_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return WorkResult.idle("interaction.work.cooking.material_inventory_full");
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.clearFailure(context);
        stopWorkNavigation(villager);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, station);
        return WorkResult.progressed("interaction.work.cooking.gathered_materials");
    }

    private List<MaterialNeed> materialNeeds(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractFurnaceBlockEntity furnace,
            RecipeType<AbstractCookingRecipe> recipeType,
            ItemStack foodFilter) {
        List<MaterialNeed> needs = new ArrayList<>();
        Predicate<ItemStack> rawFoodPredicate = stack -> isCookableFood(level, stack, recipeType, foodFilter);
        if (TutorialWorkService.isTrial(villager)) rawFoodPredicate = stack -> stack.is(TutorialFurnacePlan.input(role()));
        Predicate<ItemStack> fuelPredicate = stack -> isFuel(stack, recipeType) && (!TutorialWorkService.isTrial(villager) || stack.is(Items.COAL));
        ItemStack input = furnace.getItem(INPUT_SLOT);
        if (input.isEmpty() && HiredSupplyCrafting.countCarried(context, rawFoodPredicate) <= 0) {
            needs.add(new MaterialNeed(
                    rawFoodPredicate,
                    TutorialWorkService.isTrial(villager) ? 2 : context.transferLimit(MAX_INPUT_PULL),
                    "missing_cooking_raw_food",
                    "interaction.work.cooking.missing_raw_food"));
        }
        boolean hasAvailableInput = !input.isEmpty()
                || countAvailableIgnoringFilter(villager, context, rawFoodPredicate) > 0;
        if (furnace.getItem(FUEL_SLOT).isEmpty()
                && hasAvailableInput
                && HiredSupplyCrafting.countCarried(context, fuelPredicate) <= 0) {
            needs.add(new MaterialNeed(
                    fuelPredicate,
                    TutorialWorkService.isTrial(villager) ? 1 : context.transferLimit(MAX_FUEL_PULL),
                    "missing_cooking_fuel",
                    "interaction.work.cooking.missing_fuel"));
        }
        return needs;
    }

    static CraftingAssessment assessCraftingTargets(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            ItemStack filter) {
        if (filter == null || filter.isEmpty()) {
            return CraftingAssessment.NONE;
        }
        Predicate<ItemStack> desiredFood = desiredFoodPredicate(level, filter);
        List<RecipeHolder<CraftingRecipe>> candidates =
                level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        boolean hasCraftingTable = HiredSupplyCrafting.hasCraftingTable(level, context);
        boolean hasRecipe = false;
        boolean missingCraftingTable = false;

        List<ItemStack> configuredEntries = VillagerItemFilterData.entries(filter).stream()
                .filter(entry -> !VillagerRetaliationItems.isFilter(entry))
                .toList();
        if (VillagerRetaliationItems.isItemFilter(filter)
                && VillagerItemFilterData.mode(filter) == VillagerItemFilterData.Mode.ALLOWLIST
                && !configuredEntries.isEmpty()) {
            for (ItemStack entry : configuredEntries) {
                CraftingAssessment assessment = assessCraftingItem(
                        level,
                        villager,
                        context,
                        candidates,
                        filter,
                        desiredFood,
                        entry.getItem(),
                        hasCraftingTable);
                if (assessment.selection() != null) {
                    return assessment;
                }
                hasRecipe |= assessment.hasRecipe();
                missingCraftingTable |= assessment.missingCraftingTable();
            }
        } else {
            CraftingAssessment assessment = assessCraftingItem(
                    level,
                    villager,
                    context,
                    candidates,
                    filter,
                    desiredFood,
                    null,
                    hasCraftingTable);
            if (assessment.selection() != null) {
                return assessment;
            }
            hasRecipe = assessment.hasRecipe();
            missingCraftingTable = assessment.missingCraftingTable();
        }
        return new CraftingAssessment(null, hasRecipe, missingCraftingTable);
    }

    private static CraftingAssessment assessCraftingItem(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<RecipeHolder<CraftingRecipe>> candidates,
            ItemStack filter,
            Predicate<ItemStack> desiredFood,
            Item requestedItem,
            boolean hasCraftingTable) {
        boolean hasRecipe = false;
        boolean missingCraftingTable = false;
        for (RecipeHolder<CraftingRecipe> holder : candidates) {
            CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial()) {
                continue;
            }
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()
                    || requestedItem != null && !result.is(requestedItem)
                    || !desiredFood.test(result)
                    || !HiredProcessingRecipeFilter.allowsCraftingRecipe(level, filter, holder, result)) {
                continue;
            }
            hasRecipe = true;
            if (HiredSupplyCrafting.requiresCraftingTable(recipe)
                    && !hasCraftingTable) {
                missingCraftingTable = true;
                continue;
            }
            HiredSupplyCrafting.MaterialPlanner planner =
                    new HiredSupplyCrafting.MaterialPlanner(level, villager, context, false, true);
            Map<Item, Integer> materials = new LinkedHashMap<>();
            Map<Integer, Item> narrowed =
                    HiredProcessingRecipeFilter.narrowedCraftingIngredients(level, filter, holder);
            if (planner.planRecipe(recipe, result.getCount(), materials, narrowed)) {
                return new CraftingAssessment(
                        new CraftingSelection(recipe, result.copy(), Map.copyOf(materials), narrowed),
                        true,
                        false);
            }
        }
        return new CraftingAssessment(null, hasRecipe, missingCraftingTable);
    }

    public WorkResult tickProductionCraft(ServerLevel level, Villager villager, HiredWorkContext context,
            HiredProductionQueue queue, HiredProductionOrder order, long stock) {
        var assessment = assessCraftingTargets(level, villager, context, order.recipe().filter(level));
        var selection = assessment.selection();
        if (selection == null) {
            queue.blocker(order.id(), assessment.missingCraftingTable() ? "no_crafting_table" : "missing_materials");
            return WorkResult.idle("interaction.work.cooking.missing_crafting_materials");
        }
        var missing = new LinkedHashMap<Item, Integer>();
        selection.materials().forEach((item, count) -> {
            int needed = count - HiredSupplyCrafting.countCarried(context, item);
            if (needed > 0) missing.put(item, needed);
        });
        if (missing.isEmpty() && !context.inventory().canTransformSuppliesToOutputs(selection.materials(), List.of(order.output()))) {
            queue.blocker(order.id(), "output_full");
            return WorkResult.idle("interaction.work.cooking.output_full");
        }
        if (!AssignedStorageService.reserveProductionInputs(villager, missing, order.id())) {
            queue.blocker(order.id(), "materials_reserved");
            return WorkResult.idle("interaction.work.cooking.missing_crafting_materials");
        }
        var allocation = queue.allocation(villager.getUUID());
        if (allocation != null && !allocation.order().equals(order.id())) {
            HiredProductionStations.get(level).release(allocation.id());
            queue.release(allocation.id());
            allocation = null;
            context.setProgressTicks(0);
        }
        if (allocation == null) allocation = queue.reserve(villager.getUUID(), order.id(), order.output().getCount(), 1, stock, null);
        if (allocation == null) {
            AssignedStorageService.releaseProductionInputs(villager);
            queue.blocker(order.id(), "quantity_reserved");
            return WorkResult.idle("interaction.work.cooking.missing_crafting_materials");
        }
        HiredProductionStock.prepareDestination(context.state(), context.inventory().hasOutput(ignored -> true), order);
        queue.blocker(order.id(), "");
        var result = tickCraftingFood(level, villager, context, selection, queue, allocation);
        if (!result.progressed() && queue.order(order.id()).blocker().isEmpty()) queue.blocker(order.id(), result.status());
        return result;
    }

    private WorkResult tickCraftingFood(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            CraftingSelection selection, HiredProductionQueue queue, HiredProductionQueue.Allocation allocation) {
        BlockPos craftingTable = HiredSupplyCrafting.requiresCraftingTable(selection.recipe())
                ? nearestCraftingTable(level, villager, context)
                : null;
        if (HiredSupplyCrafting.requiresCraftingTable(selection.recipe()) && craftingTable == null) {
            HiredWorkerBrain.setFailure(context, "no_cooking_crafting_table", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.cooking.no_crafting_table").withBlocker(WorkBlocker.MISSING_CRAFTING_TABLE);
        }

        WorkResult gathering = gatherCraftingMaterials(level, villager, context, selection, craftingTable);
        if (gathering != null) {
            context.setProgressTicks(0);
            return gathering;
        }

        if (!context.canStoreOutputs(List.of(selection.result()))
                && (queue == null || !context.inventory().canTransformSuppliesToOutputs(selection.materials(), List.of(selection.result())))) {
            OutputFullHandling handling = handleOutputFullInventory(
                    level,
                    context,
                    villager,
                    0.45D,
                    craftingTable,
                    "interaction.work.cooking.depositing_outputs",
                    "interaction.work.cooking.output_full");
            return handling.handled() ? handling.result() : WorkResult.idle("interaction.work.cooking.output_full");
        }

        if (craftingTable != null) {
            HiredPathTarget target = bestWorkTarget(level, villager, context, craftingTable);
            if (target == null) {
                HiredWorkerBrain.setFailure(context, "cooking_crafting_table_unreachable", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, craftingTable);
                return WorkResult.idle("interaction.work.cooking.crafting_table_unreachable");
            }
            prepareBreakingTarget(level, context, villager, target);
            if (!canWorkFromCurrentPosition(level, villager, context, target)) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, craftingTable);
                if (!moveToTarget(level, villager, context, target, 0.45D)) {
                    HiredWorkerBrain.setFailure(context, "cooking_crafting_table_unreachable", level.getGameTime() + 100L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, craftingTable);
                    return WorkResult.idle("interaction.work.cooking.crafting_table_unreachable");
                }
                return WorkResult.progressed("interaction.work.cooking.moving_to_crafting_table");
            }
            holdWorkPosition(villager, target);
            faceBlock(villager, craftingTable);
            setTaskState(context, HiredWorkerTaskState.WORKING, craftingTable);
        } else {
            stopWorkNavigation(villager);
            setTaskState(context, HiredWorkerTaskState.WORKING);
        }

        var plan = HiredSupplyCrafting.prepareCarriedRecipe(level, context, selection.recipe(), selection.narrowedIngredients());
        if (plan == null || queue != null && (!queue.order(allocation.order()).matches(plan.result())
                || plan.result().getCount() > allocation.remaining())) {
            if (queue != null) {
                queue.release(allocation.id());
                queue.blocker(allocation.order(), "recipe_unavailable");
                AssignedStorageService.releaseProductionInputs(villager);
            }
            return WorkResult.idle("interaction.work.cooking.missing_crafting_materials");
        }
        ItemStack crafted = plan.result();
        boolean eligibleBonus = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.HIRED_COOK_BONUS_CHANCE_PERCENT.get() > 0
                && !TutorialWorkService.isTrial(villager)
                && (queue == null || queue.order(allocation.order()).mode() == HiredProductionOrder.Mode.CONTINUOUS)
                && CookBonusService.eligibleBread(level, selection.recipe(), plan);
        List<ItemStack> products = new ArrayList<>(plan.products());
        if (eligibleBonus) {
            products.add(crafted.copyWithCount(1));
            if (!context.inventory().transformSupplyStacks(plan.consumedStacks(), plan.surplus(), products, false))
                return WorkResult.idle("interaction.work.cooking.output_full");
            if (!CookBonusService.roll(villager)) products.removeLast();
        }
        ItemStack displayed = crafted.copyWithCount(crafted.getCount() + (products.size() > plan.products().size() ? 1 : 0));
        if (queue != null) {
            if (!queue.start(allocation.id())) return WorkResult.idle("interaction.work.cooking.missing_crafting_materials");
            if (!context.inventory().transformSupplyStacks(plan.consumedStacks(), plan.surplus(), products, true)) {
                queue.lost(allocation.id());
                queue.blocker(allocation.order(), "output_full");
                AssignedStorageService.releaseProductionInputs(villager);
                return WorkResult.idle("interaction.work.cooking.output_full");
            }
            if (!queue.complete(allocation.id(), crafted)) throw new IllegalStateException("Cooked output did not match its production allocation");
            AssignedStorageService.releaseProductionInputs(villager);
            HiredWorkerBrain.clearFailure(context);
            HiredStorageNavigationGoal.clearStorageTarget(context);
            useWorkItem(level, villager, crafted);
            return WorkResult.completedWithPractice("interaction.work.cooking.crafted_food", itemReplacements(displayed),
                    HiredWorkPractice.batch(VillagerSkill.COOKING, "hired:cooking:craft", crafted.getCount(), crafted.getItem().hashCode()));
        }
        List<ItemStack> supplies = new ArrayList<>(plan.surplus());
        supplies.addAll(plan.products().subList(0, plan.products().size() - 1));
        if (!context.inventory().transformSupplyStacks(plan.consumedStacks(), supplies, products.size() > plan.products().size() ? List.of(crafted, crafted.copyWithCount(1)) : List.of(crafted), true)) {
            HiredWorkerBrain.setFailure(context, "missing_cooking_crafting_materials", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, craftingTable);
            return WorkResult.idle("interaction.work.cooking.missing_crafting_materials");
        }
        HiredWorkerBrain.clearFailure(context);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        useWorkItem(level, villager, crafted);
        return WorkResult.completedWithPractice(
                "interaction.work.cooking.crafted_food",
                itemReplacements(displayed),
                HiredWorkPractice.batch(
                        VillagerSkill.COOKING, "hired:cooking:craft", crafted.getCount(), crafted.getItem().hashCode()));
    }

    private WorkResult gatherCraftingMaterials(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            CraftingSelection selection,
            BlockPos returnTarget) {
        List<MaterialNeed> needs = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : selection.materials().entrySet()) {
            int missing = Math.max(0, entry.getValue() - HiredSupplyCrafting.countCarried(context, entry.getKey()));
            if (missing > 0) {
                Item item = entry.getKey();
                needs.add(new MaterialNeed(
                        stack -> stack.is(item),
                        missing,
                        "missing_cooking_crafting_materials",
                        "interaction.work.cooking.missing_crafting_materials"));
            }
        }
        if (needs.isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            return null;
        }
        if (!context.useAssignedStorageForSupplies() || !AssignedStorageService.hasAssignedStorage(level, villager)) {
            HiredWorkerBrain.setFailure(context, "missing_cooking_crafting_materials", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, returnTarget);
            return WorkResult.idle("interaction.work.cooking.missing_crafting_materials");
        }
        Predicate<ItemStack> storageNeedFilter = stack -> matchesAnyNeed(needs, stack);
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContainingIgnoringFilter(
                level,
                villager,
                storageNeedFilter);
        if (storage == null) {
            HiredWorkerBrain.setFailure(context, "missing_cooking_crafting_materials", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, returnTarget);
            return WorkResult.idle("interaction.work.cooking.missing_crafting_materials");
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
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, storage);
            return WorkResult.progressed("interaction.work.cooking.collecting_crafting_materials");
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredWorkerBrain.setFailure(context, "cooking_storage_path_failed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return WorkResult.idle("interaction.work.cooking.materials_unreachable");
        }

        faceBlock(villager, storage);
        int movedTotal = 0;
        int remainingTripCapacity = context.transferLimit(MAX_INPUT_PULL);
        for (MaterialNeed need : needs) {
            if (remainingTripCapacity <= 0) {
                break;
            }
            int moved = AssignedStorageService.transferItemsAtAssignedStorageIgnoringFilter(
                    villager,
                    storage,
                    need.predicate(),
                    Math.min(need.count(), remainingTripCapacity),
                    context.inventory()::insertSupplyFromStorage);
            movedTotal += moved;
            remainingTripCapacity -= moved;
        }
        if (movedTotal <= 0) {
            HiredWorkerBrain.setFailure(context, "cooking_material_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return WorkResult.idle("interaction.work.cooking.material_inventory_full");
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.clearFailure(context);
        stopWorkNavigation(villager);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnTarget);
        return WorkResult.progressed("interaction.work.cooking.gathered_crafting_materials");
    }

    private BlockPos nearestCraftingTable(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos raw : context.workAreaPositions()) {
            BlockPos pos = raw.immutable();
            if (!context.permitsStation(pos) || !context.isLoaded(level, pos) || !level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                continue;
            }
            boolean outside = !context.isInsideWorkArea(villager.blockPosition());
            HiredPathTarget target = outside ? null : bestWorkTarget(level, villager, context, pos);
            if (!outside && target == null) {
                continue;
            }
            double distance = villager.distanceToSqr(outside ? pos.getCenter() : target.approachPos().getCenter());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    private static int countAvailableIgnoringFilter(
            Villager villager,
            HiredWorkContext context,
            Predicate<ItemStack> predicate) {
        int count = HiredSupplyCrafting.countCarried(context, predicate);
        if (context.useAssignedStorageForSupplies()) {
            count += AssignedStorageService.countItemsIgnoringFilter(villager, predicate);
        }
        return count;
    }

    private static boolean matchesAnyNeed(List<MaterialNeed> needs, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (MaterialNeed need : needs) {
            if (need.predicate().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private BlockPos nearestCookingStation(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            ItemStack foodFilter,
            Predicate<ItemStack> desiredFood, boolean finishExisting) {
        if (TutorialWorkService.isTrial(villager)) {
            var bound = TutorialFurnacePlan.station(level, villager);
            return bound != null && context.isInsideWorkArea(bound) && level.hasChunkAt(bound) ? bound : null;
        }
        CompoundTag state = context.state();
        BlockPos cached = cachedPos(state);
        if (isValidCookingStation(level, context, cached)
                && (!finishExisting || hasExistingBatch(level, cached))
                && !HiredPathMemory.isAvoided(level, villager, cached)
                && stationContentsCompatible(level, cached, foodFilter, desiredFood)
                && stationHasCollectibleOutput(level, cached, desiredFood)) {
            return cached;
        }
        clearCachedStation(state);
        if (level.getGameTime() < state.getLong(NEXT_STATION_SCAN_GAME_TIME_TAG)) {
            return null;
        }

        List<FacilityCandidate> candidates = new ArrayList<>();
        for (BlockPos raw : context.workAreaPositions()) {
            BlockPos pos = raw.immutable();
            if (!isValidCookingStation(level, context, pos)
                    || finishExisting && (!hasExistingBatch(level, pos) || !stationContentsCompatible(level, pos, foodFilter, desiredFood))
                    || HiredPathMemory.isAvoided(level, villager, pos)) {
                continue;
            }
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
            if (HiredProcessingRecipeFilter.supportsRecipeType(level, foodFilter, recipeType(furnace))) {
                candidates.add(new FacilityCandidate(pos, villager.distanceToSqr(pos.getCenter())));
            }
        }
        candidates.sort(Comparator.comparingDouble(FacilityCandidate::score));
        boolean hasCompatibleStation = candidates.stream()
                .anyMatch(candidate -> stationContentsCompatible(level, candidate.pos(), foodFilter, desiredFood));
        if (hasCompatibleStation) {
            candidates = candidates.stream()
                    .filter(candidate -> stationContentsCompatible(level, candidate.pos(), foodFilter, desiredFood))
                    .toList();
        }
        boolean hasCollectibleStation = candidates.stream()
                .anyMatch(candidate -> stationHasCollectibleOutput(level, candidate.pos(), desiredFood));
        boolean hasAttentionStation = candidates.stream()
                .anyMatch(candidate -> stationNeedsAttention(level, candidate.pos()));
        List<FacilityCandidate> selectable = candidates.stream()
                .filter(candidate -> hasCollectibleStation
                        ? stationHasCollectibleOutput(level, candidate.pos(), desiredFood)
                        : !hasAttentionStation || stationNeedsAttention(level, candidate.pos()))
                .toList();
        BlockPos fallback = selectable.stream()
                .map(FacilityCandidate::pos)
                .findFirst()
                .orElse(null);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int attempts = 0;
        for (FacilityCandidate candidate : selectable) {
            HiredPathTarget target = bestWorkTarget(level, villager, context, candidate.pos());
            if (target == null) {
                continue;
            }
            double score = villager.distanceToSqr(target.approachPos().getCenter());
            if (score < bestScore) {
                bestScore = score;
                best = candidate.pos();
            }
            if (++attempts >= MAX_STATION_PATH_ATTEMPTS) {
                break;
            }
        }
        if (best == null) {
            if (fallback != null) {
                state.putLong(CACHED_STATION_POS_TAG, fallback.asLong());
                state.remove(NEXT_STATION_SCAN_GAME_TIME_TAG);
                return fallback;
            }
            state.putLong(NEXT_STATION_SCAN_GAME_TIME_TAG, level.getGameTime() + FACILITY_SCAN_COOLDOWN_TICKS);
            return null;
        }
        state.putLong(CACHED_STATION_POS_TAG, best.asLong());
        state.remove(NEXT_STATION_SCAN_GAME_TIME_TAG);
        return best;
    }

    private static boolean hasExistingBatch(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace
                && (!furnace.getItem(INPUT_SLOT).isEmpty() || !furnace.getItem(RESULT_SLOT).isEmpty() || isFuelRemainder(furnace.getItem(FUEL_SLOT)));
    }

    private static boolean stationNeedsAttention(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return false;
        }
        return !furnace.getItem(RESULT_SLOT).isEmpty()
                || isFuelRemainder(furnace.getItem(FUEL_SLOT))
                || furnace.getItem(INPUT_SLOT).isEmpty()
                || furnace.getItem(FUEL_SLOT).isEmpty();
    }

    private static boolean stationHasCollectibleOutput(
            ServerLevel level,
            BlockPos pos,
            Predicate<ItemStack> desiredFood) {
        if (pos == null) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return false;
        }
        ItemStack output = furnace.getItem(RESULT_SLOT);
        return !output.isEmpty() && isFood(output) && desiredFood.test(output)
                || isFuelRemainder(furnace.getItem(FUEL_SLOT));
    }

    private static boolean stationContentsCompatible(
            ServerLevel level,
            BlockPos pos,
            ItemStack foodFilter,
            Predicate<ItemStack> desiredFood) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return false;
        }
        RecipeType<AbstractCookingRecipe> recipeType = recipeType(furnace);
        ItemStack output = furnace.getItem(RESULT_SLOT);
        ItemStack input = furnace.getItem(INPUT_SLOT);
        ItemStack fuel = furnace.getItem(FUEL_SLOT);
        return (output.isEmpty() || isFood(output) && desiredFood.test(output))
                && (input.isEmpty() || isCookableFood(level, input, recipeType, foodFilter))
                && (fuel.isEmpty() || isFuelRemainder(fuel) || isFuel(fuel, recipeType));
    }

    private static boolean isValidCookingStation(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        return pos != null
                && context.isInsideWorkArea(pos)
                && context.permitsStation(pos)
                && context.isLoaded(level, pos)
                && com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations.get(level).available(level, pos)
                && (level.getBlockEntity(pos) instanceof FurnaceBlockEntity
                || level.getBlockEntity(pos) instanceof SmokerBlockEntity);
    }

    private static BlockPos cachedPos(CompoundTag state) {
        if (!state.contains(CACHED_STATION_POS_TAG)) {
            return null;
        }
        return BlockPos.of(state.getLong(CACHED_STATION_POS_TAG));
    }

    private static void clearCachedStation(CompoundTag state) {
        state.remove(CACHED_STATION_POS_TAG);
    }

    @SuppressWarnings("unchecked")
    private static RecipeType<AbstractCookingRecipe> recipeType(AbstractFurnaceBlockEntity furnace) {
        if (furnace instanceof SmokerBlockEntity) {
            return (RecipeType<AbstractCookingRecipe>) (RecipeType<?>) RecipeType.SMOKING;
        }
        return (RecipeType<AbstractCookingRecipe>) (RecipeType<?>) RecipeType.SMELTING;
    }

    private static boolean isCookableFood(
            ServerLevel level,
            ItemStack stack,
            RecipeType<AbstractCookingRecipe> recipeType,
            ItemStack foodFilter) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return cookingRecipe(level, stack, recipeType)
                .filter(recipe -> {
                    ItemStack result = recipe.value().assemble(
                            new SingleRecipeInput(stack), level.registryAccess());
                    return isFood(result)
                            && HiredProcessingRecipeFilter.allows(
                                    level, foodFilter, recipe, stack, result);
                })
                .isPresent();
    }

    static boolean isCookableFoodForFilter(
            ServerLevel level,
            ItemStack stack,
            RecipeType<AbstractCookingRecipe> recipeType,
            ItemStack filter) {
        return isCookableFood(level, stack, recipeType, filter);
    }

    private static Predicate<ItemStack> desiredFoodPredicate(ServerLevel level, ItemStack filter) {
        if (filter == null || filter.isEmpty()) {
            return CookingWorker::isFood;
        }
        return stack -> isFood(stack)
                && com.jvn.villagerretaliation.item.VillagerFilterMatcher.matches(level, filter, stack);
    }

    private static Optional<RecipeHolder<AbstractCookingRecipe>> cookingRecipe(
            ServerLevel level,
            ItemStack stack,
            RecipeType<AbstractCookingRecipe> recipeType) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(recipeType, new SingleRecipeInput(stack), level);
    }

    public static boolean isFood(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.get(DataComponents.FOOD) != null || stack.is(Items.CAKE));
    }

    private static boolean isFuel(ItemStack stack, RecipeType<AbstractCookingRecipe> recipeType) {
        return stack != null && !stack.isEmpty() && stack.getBurnTime(recipeType) > 0;
    }

    private static boolean isFuelRemainder(ItemStack stack) {
        return stack != null && stack.is(Items.BUCKET);
    }

    private static ItemStack consumeCarriedSupply(HiredWorkContext context, ItemStack sample, int count) {
        if (sample.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack selected = sample.copyWithCount(Math.min(sample.getCount(), count));
        int consumed = context.inventory().consumeSupply(sameStackPredicate(sample), selected.getCount());
        return consumed <= 0 ? ItemStack.EMPTY : selected.copyWithCount(consumed);
    }

    private static Predicate<ItemStack> sameStackPredicate(ItemStack sample) {
        ItemStack normalized = sample.copyWithCount(1);
        return stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack.copyWithCount(1), normalized);
    }

    private static void restoreFurnaceStack(AbstractFurnaceBlockEntity furnace, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack existing = furnace.getItem(slot);
        if (existing.isEmpty()) {
            furnace.setItem(slot, stack.copy());
        } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
            existing.grow(stack.getCount());
        }
    }

    private static void updateFurnace(ServerLevel level, AbstractFurnaceBlockEntity furnace, BlockPos station) {
        furnace.setChanged();
        BlockState state = level.getBlockState(station);
        level.sendBlockUpdated(station, state, state, 3);
    }

    private static Map<String, String> itemReplacements(ItemStack stack) {
        return Map.of(
                "item", stack.getHoverName().getString(),
                "count", Integer.toString(stack.getCount()));
    }

    private record MaterialNeed(Predicate<ItemStack> predicate, int count, String failureReason, String missingStatus) {
    }

    record CraftingSelection(
            CraftingRecipe recipe,
            ItemStack result,
            Map<Item, Integer> materials,
            Map<Integer, Item> narrowedIngredients) {
    }

    record CraftingAssessment(CraftingSelection selection, boolean hasRecipe, boolean missingCraftingTable) {
        private static final CraftingAssessment NONE = new CraftingAssessment(null, false, false);
    }

    private record FacilityCandidate(BlockPos pos, double score) {
    }
}
