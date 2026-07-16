package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
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

        ItemStack foodFilter = VillagerItemFilterService.assignedFilter(villager);
        CraftingAssessment crafting = assessCraftingTargets(level, villager, context, foodFilter);
        if (crafting.selection() != null) {
            return tickCraftingFood(level, villager, context, crafting.selection());
        }

        BlockPos station = nearestCookingStation(level, villager, context);
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
                return WorkResult.idle(status);
            }
            HiredWorkerBrain.setFailure(context, "no_cooking_station", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.cooking.no_station");
        }
        if (!(level.getBlockEntity(station) instanceof AbstractFurnaceBlockEntity furnace)) {
            clearCachedStation(context.state());
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "no_cooking_station", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.cooking.no_station");
        }

        RecipeType<AbstractCookingRecipe> recipeType = recipeType(furnace);
        Predicate<ItemStack> desiredFood = desiredFoodPredicate(foodFilter);
        ItemStack output = furnace.getItem(RESULT_SLOT);
        if (!output.isEmpty() && (!isFood(output) || !desiredFood.test(output))) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "cooking_wrong_output", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, station);
            return WorkResult.idle("interaction.work.cooking.wrong_output");
        }
        ItemStack input = furnace.getItem(INPUT_SLOT);
        if (!input.isEmpty() && !isCookableFood(level, input, recipeType, desiredFood)) {
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
                    desiredFood);
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
        return workCookingStation(level, villager, context, furnace, station, recipeType, desiredFood);
    }

    private WorkResult workCookingStation(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractFurnaceBlockEntity furnace,
            BlockPos station,
            RecipeType<AbstractCookingRecipe> recipeType,
            Predicate<ItemStack> desiredFood) {
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
                    stack -> isCookableFood(level, stack, recipeType, desiredFood));
            if (carriedFood.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_cooking_raw_food", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.cooking.missing_raw_food");
            }
            int count = Math.min(carriedFood.getCount(), Math.min(MAX_INPUT_PULL, furnace.getMaxStackSize(carriedFood)));
            ItemStack loaded = consumeCarriedSupply(context, carriedFood, count);
            if (loaded.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_cooking_raw_food", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.cooking.missing_raw_food");
            }
            furnace.setItem(INPUT_SLOT, loaded);
            updateFurnace(level, furnace, station);
            swingWorkTool(villager);
            return WorkResult.progressed("interaction.work.cooking.loaded_input", itemReplacements(loaded));
        }

        if (fuel.isEmpty()) {
            ItemStack carriedFuel = context.inventory().findSupply(stack -> isFuel(stack, recipeType));
            if (carriedFuel.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_cooking_fuel", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.cooking.missing_fuel");
            }
            int count = Math.min(carriedFuel.getCount(), Math.min(MAX_FUEL_PULL, furnace.getMaxStackSize(carriedFuel)));
            ItemStack loaded = consumeCarriedSupply(context, carriedFuel, count);
            if (loaded.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_cooking_fuel", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.cooking.missing_fuel");
            }
            furnace.setItem(FUEL_SLOT, loaded);
            updateFurnace(level, furnace, station);
            swingWorkTool(villager);
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
        ItemStack planned = stack.copy();
        if (!context.canStoreOutputs(List.of(planned))) {
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
        ItemStack removed = furnace.removeItem(slot, planned.getCount());
        if (removed.isEmpty()) {
            return WorkResult.progressed("interaction.work.cooking.waiting_station");
        }
        ItemStack remainder = context.storeOutputAfterDepositIfFull(villager, removed.copy());
        if (!remainder.isEmpty()) {
            restoreFurnaceStack(furnace, slot, remainder);
        }
        updateFurnace(level, furnace, station);
        swingWorkTool(villager);
        return completed ? WorkResult.completed(status, itemReplacements(removed)) : WorkResult.progressed(status, itemReplacements(removed));
    }

    private WorkResult gatherCookingMaterials(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos station,
            AbstractFurnaceBlockEntity furnace,
            RecipeType<AbstractCookingRecipe> recipeType,
            Predicate<ItemStack> desiredFood) {
        List<MaterialNeed> needs = materialNeeds(level, villager, context, furnace, recipeType, desiredFood);
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
        for (MaterialNeed need : needs) {
            movedTotal += AssignedStorageService.transferItemsAtAssignedStorageIgnoringFilter(
                    villager,
                    storage,
                    need.predicate(),
                    need.count(),
                    context.inventory()::insertSupplyFromStorage);
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
            Predicate<ItemStack> desiredFood) {
        List<MaterialNeed> needs = new ArrayList<>();
        Predicate<ItemStack> rawFoodPredicate = stack -> isCookableFood(level, stack, recipeType, desiredFood);
        Predicate<ItemStack> fuelPredicate = stack -> isFuel(stack, recipeType);
        ItemStack input = furnace.getItem(INPUT_SLOT);
        if (input.isEmpty() && HiredSupplyCrafting.countCarried(context, rawFoodPredicate) <= 0) {
            needs.add(new MaterialNeed(
                    rawFoodPredicate,
                    MAX_INPUT_PULL,
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
                    MAX_FUEL_PULL,
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
        Predicate<ItemStack> desiredFood = desiredFoodPredicate(filter);
        List<RecipeHolder<CraftingRecipe>> candidates =
                level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        boolean hasCraftingTable = HiredSupplyCrafting.hasCraftingTable(level, context);
        boolean hasRecipe = false;
        boolean missingCraftingTable = false;

        List<ItemStack> configuredEntries = VillagerItemFilterData.entries(filter);
        if (VillagerItemFilterData.mode(filter) == VillagerItemFilterData.Mode.ALLOWLIST) {
            for (ItemStack entry : configuredEntries) {
                if (entry.isEmpty()) {
                    continue;
                }
                CraftingAssessment assessment = assessCraftingItem(
                        level,
                        villager,
                        context,
                        candidates,
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
                    || !desiredFood.test(result)) {
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
            if (planner.planRecipe(recipe, result.getCount(), materials)) {
                return new CraftingAssessment(
                        new CraftingSelection(recipe, result.copy(), Map.copyOf(materials)),
                        true,
                        false);
            }
        }
        return new CraftingAssessment(null, hasRecipe, missingCraftingTable);
    }

    private WorkResult tickCraftingFood(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            CraftingSelection selection) {
        BlockPos craftingTable = HiredSupplyCrafting.requiresCraftingTable(selection.recipe())
                ? nearestCraftingTable(level, villager, context)
                : null;
        if (HiredSupplyCrafting.requiresCraftingTable(selection.recipe()) && craftingTable == null) {
            HiredWorkerBrain.setFailure(context, "no_cooking_crafting_table", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.cooking.no_crafting_table");
        }

        WorkResult gathering = gatherCraftingMaterials(level, villager, context, selection, craftingTable);
        if (gathering != null) {
            context.setProgressTicks(0);
            return gathering;
        }

        if (!context.canStoreOutputs(List.of(selection.result()))) {
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

        if (!HiredSupplyCrafting.craftCarriedSupplyItemWithStations(level, context, selection.result().getItem())) {
            HiredWorkerBrain.setFailure(context, "missing_cooking_crafting_materials", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, craftingTable);
            return WorkResult.idle("interaction.work.cooking.missing_crafting_materials");
        }
        int craftedCount = context.inventory().consumeSupply(
                sameStackPredicate(selection.result()),
                selection.result().getCount());
        if (craftedCount <= 0) {
            HiredWorkerBrain.setFailure(context, "cooking_craft_failed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, craftingTable);
            return WorkResult.idle("interaction.work.cooking.craft_failed");
        }
        ItemStack crafted = selection.result().copyWithCount(craftedCount);
        ItemStack remainder = context.storeOutputAfterDepositIfFull(villager, crafted.copy());
        if (!remainder.isEmpty()) {
            context.inventory().returnSupplyOrDrop(remainder);
        }
        HiredWorkerBrain.clearFailure(context);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        swingWorkTool(villager);
        return WorkResult.completed("interaction.work.cooking.crafted_food", itemReplacements(crafted));
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
        for (MaterialNeed need : needs) {
            movedTotal += AssignedStorageService.transferItemsAtAssignedStorageIgnoringFilter(
                    villager,
                    storage,
                    need.predicate(),
                    need.count(),
                    context.inventory()::insertSupplyFromStorage);
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
            if (!context.isLoaded(level, pos) || !level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                continue;
            }
            HiredPathTarget target = bestWorkTarget(level, villager, context, pos);
            if (target == null) {
                continue;
            }
            double distance = villager.distanceToSqr(target.approachPos().getCenter());
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

    private BlockPos nearestCookingStation(ServerLevel level, Villager villager, HiredWorkContext context) {
        CompoundTag state = context.state();
        BlockPos cached = cachedPos(state);
        if (isValidCookingStation(level, context, cached)
                && !HiredPathMemory.isAvoided(level, villager, cached)) {
            return cached;
        }
        clearCachedStation(state);
        if (level.getGameTime() < state.getLong(NEXT_STATION_SCAN_GAME_TIME_TAG)) {
            return null;
        }

        List<FacilityCandidate> candidates = new ArrayList<>();
        for (BlockPos raw : context.workAreaPositions()) {
            BlockPos pos = raw.immutable();
            if (isValidCookingStation(level, context, pos)
                    && !HiredPathMemory.isAvoided(level, villager, pos)) {
                candidates.add(new FacilityCandidate(pos, villager.distanceToSqr(pos.getCenter())));
            }
        }
        candidates.sort(Comparator.comparingDouble(FacilityCandidate::score));
        BlockPos fallback = candidates.isEmpty() ? null : candidates.getFirst().pos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int attempts = 0;
        for (FacilityCandidate candidate : candidates) {
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

    private static boolean isValidCookingStation(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        return pos != null
                && context.isInsideWorkArea(pos)
                && context.isLoaded(level, pos)
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
            Predicate<ItemStack> desiredFood) {
        if (stack == null || stack.isEmpty() || !isFood(stack)) {
            return false;
        }
        return cookingRecipe(level, stack, recipeType)
                .map(recipe -> recipe.value().assemble(new SingleRecipeInput(stack), level.registryAccess()))
                .filter(CookingWorker::isFood)
                .filter(desiredFood)
                .isPresent();
    }

    static boolean isCookableFoodForFilter(
            ServerLevel level,
            ItemStack stack,
            RecipeType<AbstractCookingRecipe> recipeType,
            ItemStack filter) {
        return isCookableFood(level, stack, recipeType, desiredFoodPredicate(filter));
    }

    private static Predicate<ItemStack> desiredFoodPredicate(ItemStack filter) {
        if (filter == null || filter.isEmpty()) {
            return CookingWorker::isFood;
        }
        return stack -> isFood(stack) && VillagerItemFilterData.matches(filter, stack);
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

    private static boolean isFood(ItemStack stack) {
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

    record CraftingSelection(CraftingRecipe recipe, ItemStack result, Map<Item, Integer> materials) {
    }

    record CraftingAssessment(CraftingSelection selection, boolean hasRecipe, boolean missingCraftingTable) {
        private static final CraftingAssessment NONE = new CraftingAssessment(null, false, false);
    }

    private record FacilityCandidate(BlockPos pos, double score) {
    }
}
