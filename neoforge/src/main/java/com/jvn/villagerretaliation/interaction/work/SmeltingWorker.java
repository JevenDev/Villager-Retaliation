package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SmeltingWorker extends AbstractBlockWorker {
    private static final String CACHED_STATION_POS_TAG = "SmeltingCachedStationPos";
    private static final String NEXT_STATION_SCAN_GAME_TIME_TAG = "NextSmeltingStationScanGameTime";
    private static final long FACILITY_SCAN_COOLDOWN_TICKS = 100L;
    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int RESULT_SLOT = 2;
    private static final int MAX_STATION_PATH_ATTEMPTS = 12;
    private static final int MAX_INPUT_PULL = 16;
    private static final int MAX_FUEL_PULL = 8;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.SMELTER;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }
        if (AssignedStorageService.hasAssignedStorage(level, villager)) {
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (depositResult == DepositResult.MOVING) {
                return WorkResult.progressed("interaction.work.smelting.depositing_outputs");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
        }

        BlockPos station = nearestSmeltingStation(level, villager, context);
        if (station == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "no_smelting_station", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.smelting.no_station");
        }
        if (!(level.getBlockEntity(station) instanceof AbstractFurnaceBlockEntity furnace)) {
            clearCachedStation(context.state());
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "no_smelting_station", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.smelting.no_station");
        }

        RecipeType<AbstractCookingRecipe> recipeType = recipeType(furnace);
        ItemStack output = furnace.getItem(RESULT_SLOT);
        if (!output.isEmpty() && !isSmeltedOre(output)) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "smelting_wrong_output", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, station);
            return WorkResult.idle("interaction.work.smelting.wrong_output");
        }
        ItemStack input = furnace.getItem(INPUT_SLOT);
        if (!input.isEmpty() && !isSmeltableOre(level, input, recipeType)) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "smelting_wrong_input", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, station);
            return WorkResult.idle("interaction.work.smelting.wrong_input");
        }
        ItemStack fuel = furnace.getItem(FUEL_SLOT);
        boolean fuelRemainder = isFuelRemainder(fuel);
        if (!fuel.isEmpty() && !fuelRemainder && !isFuel(fuel, recipeType)) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "smelting_wrong_fuel", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, station);
            return WorkResult.idle("interaction.work.smelting.wrong_fuel");
        }

        if (output.isEmpty() && !fuelRemainder) {
            WorkResult gatheredMaterials = gatherSmeltingMaterials(level, villager, context, station, furnace, recipeType);
            if (gatheredMaterials != null) {
                context.setProgressTicks(0);
                return gatheredMaterials;
            }
        }

        HiredPathTarget target = bestWorkTarget(level, villager, context, station);
        if (target == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "smelting_station_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, station);
            return WorkResult.idle("interaction.work.smelting.station_unreachable");
        }
        prepareBreakingTarget(level, context, villager, target);
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, station);
            if (!moveToTarget(level, villager, context, target, 0.45D)) {
                if (recordWorkPathFailure(level, villager, station)) {
                    HiredWorkerBrain.setFailure(context, "smelting_station_path_failed", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, station);
                    return WorkResult.idle("interaction.work.smelting.station_blocked");
                }
                return WorkResult.progressed("interaction.work.smelting.repositioning_station");
            }
            return WorkResult.progressed("interaction.work.smelting.moving_to_station");
        }

        clearWorkPathFailure(villager, station);
        HiredWorkerBrain.clearFailure(context);
        holdWorkPosition(villager, target);
        setTaskState(context, HiredWorkerTaskState.WORKING, station);
        context.setProgressTicks(0);
        return workSmeltingStation(level, villager, context, furnace, station, recipeType);
    }

    private WorkResult workSmeltingStation(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractFurnaceBlockEntity furnace,
            BlockPos station,
            RecipeType<AbstractCookingRecipe> recipeType) {
        ItemStack output = furnace.getItem(RESULT_SLOT);
        if (!output.isEmpty()) {
            return collectOutput(level, villager, context, furnace, station, RESULT_SLOT, output, "interaction.work.smelting.collected_output", true);
        }

        ItemStack fuel = furnace.getItem(FUEL_SLOT);
        if (isFuelRemainder(fuel)) {
            return collectOutput(level, villager, context, furnace, station, FUEL_SLOT, fuel, "interaction.work.smelting.collected_fuel_remainder", false);
        }

        ItemStack input = furnace.getItem(INPUT_SLOT);
        if (input.isEmpty()) {
            ItemStack carriedOre = context.inventory().findSupply(stack -> isSmeltableOre(level, stack, recipeType));
            if (carriedOre.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_smelting_raw_ore", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.smelting.missing_raw_ore");
            }
            int count = Math.min(carriedOre.getCount(), Math.min(context.transferLimit(MAX_INPUT_PULL), furnace.getMaxStackSize(carriedOre)));
            ItemStack loaded = consumeCarriedSupply(context, carriedOre, count);
            if (loaded.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_smelting_raw_ore", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.smelting.missing_raw_ore");
            }
            furnace.setItem(INPUT_SLOT, loaded);
            updateFurnace(level, furnace, station);
            useWorkItem(level, villager, loaded);
            return WorkResult.progressed("interaction.work.smelting.loaded_input", itemReplacements(loaded));
        }

        if (fuel.isEmpty()) {
            ItemStack carriedFuel = context.inventory().findSupply(stack -> isFuel(stack, recipeType));
            if (carriedFuel.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_smelting_fuel", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.smelting.missing_fuel");
            }
            int count = Math.min(carriedFuel.getCount(), Math.min(context.transferLimit(MAX_FUEL_PULL), furnace.getMaxStackSize(carriedFuel)));
            ItemStack loaded = consumeCarriedSupply(context, carriedFuel, count);
            if (loaded.isEmpty()) {
                HiredWorkerBrain.setFailure(context, "missing_smelting_fuel", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, station);
                return WorkResult.idle("interaction.work.smelting.missing_fuel");
            }
            furnace.setItem(FUEL_SLOT, loaded);
            updateFurnace(level, furnace, station);
            useWorkItem(level, villager, loaded);
            return WorkResult.progressed("interaction.work.smelting.loaded_fuel", itemReplacements(loaded));
        }

        return WorkResult.progressed("interaction.work.smelting.waiting_station");
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
        ItemStack planned = stack.copyWithCount(Math.min(stack.getCount(), context.transferLimit(MAX_INPUT_PULL)));
        if (!context.canStoreOutputs(List.of(planned))) {
            OutputFullHandling handling = handleOutputFullInventory(
                    level,
                    context,
                    villager,
                    0.45D,
                    station,
                    "interaction.work.smelting.depositing_outputs",
                    "interaction.work.smelting.output_full");
            if (handling.handled()) {
                return handling.result();
            }
            return WorkResult.idle("interaction.work.smelting.output_full");
        }
        ItemStack removed = furnace.removeItem(slot, planned.getCount());
        if (removed.isEmpty()) {
            return WorkResult.progressed("interaction.work.smelting.waiting_station");
        }
        ItemStack remainder = context.storeOutputAfterDepositIfFull(villager, removed.copy());
        if (!remainder.isEmpty()) {
            restoreFurnaceStack(furnace, slot, remainder);
        }
        updateFurnace(level, furnace, station);
        useWorkItem(level, villager, removed);
        var practice = HiredWorkPractice.batch(
                VillagerSkill.SMITHING, "hired:smelting:batch", removed.getCount(), removed.getItem().hashCode());
        return completed
                ? WorkResult.completedWithPractice(status, itemReplacements(removed), practice)
                : WorkResult.progressedWithPractice(status, itemReplacements(removed), practice);
    }

    private WorkResult gatherSmeltingMaterials(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos station,
            AbstractFurnaceBlockEntity furnace,
            RecipeType<AbstractCookingRecipe> recipeType) {
        List<MaterialNeed> needs = materialNeeds(level, villager, context, furnace, recipeType);
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
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContaining(level, villager, storageNeedFilter);
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
            return WorkResult.progressed("interaction.work.smelting.collecting_materials");
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            BlockPos failedStorage = storage;
            for (BlockPos alternateStorage : AssignedStorageService.assignedStoragePositionsContaining(
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
                    return WorkResult.progressed("interaction.work.smelting.collecting_materials");
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
                HiredWorkerBrain.setFailure(context, "smelting_storage_path_failed", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, failedStorage);
                return WorkResult.idle("interaction.work.smelting.materials_unreachable");
            }
        }

        faceBlock(villager, storage);
        int movedTotal = 0;
        int remainingTripCapacity = context.transferLimit(MAX_INPUT_PULL);
        for (MaterialNeed need : needs) {
            if (remainingTripCapacity <= 0) {
                break;
            }
            int moved = AssignedStorageService.transferItemsAtAssignedStorage(
                    villager,
                    storage,
                    need.predicate(),
                    Math.min(need.count(), remainingTripCapacity),
                    context.inventory()::insertSupplyFromStorage);
            movedTotal += moved;
            remainingTripCapacity -= moved;
        }
        if (movedTotal <= 0) {
            HiredWorkerBrain.setFailure(context, "smelting_material_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return WorkResult.idle("interaction.work.smelting.material_inventory_full");
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.clearFailure(context);
        stopWorkNavigation(villager);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, station);
        return WorkResult.progressed("interaction.work.smelting.gathered_materials");
    }

    private List<MaterialNeed> materialNeeds(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractFurnaceBlockEntity furnace,
            RecipeType<AbstractCookingRecipe> recipeType) {
        List<MaterialNeed> needs = new ArrayList<>();
        Predicate<ItemStack> rawOrePredicate = stack -> isSmeltableOre(level, stack, recipeType);
        Predicate<ItemStack> fuelPredicate = stack -> isFuel(stack, recipeType);
        ItemStack input = furnace.getItem(INPUT_SLOT);
        if (input.isEmpty() && HiredSupplyCrafting.countCarried(context, rawOrePredicate) <= 0) {
            needs.add(new MaterialNeed(
                    rawOrePredicate,
                    context.transferLimit(MAX_INPUT_PULL),
                    "missing_smelting_raw_ore",
                    "interaction.work.smelting.missing_raw_ore"));
        }
        boolean hasAvailableInput = !input.isEmpty() || HiredSupplyCrafting.countAvailable(villager, context, rawOrePredicate) > 0;
        if (furnace.getItem(FUEL_SLOT).isEmpty()
                && hasAvailableInput
                && HiredSupplyCrafting.countCarried(context, fuelPredicate) <= 0) {
            needs.add(new MaterialNeed(
                    fuelPredicate,
                    context.transferLimit(MAX_FUEL_PULL),
                    "missing_smelting_fuel",
                    "interaction.work.smelting.missing_fuel"));
        }
        return needs;
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

    private BlockPos nearestSmeltingStation(ServerLevel level, Villager villager, HiredWorkContext context) {
        CompoundTag state = context.state();
        BlockPos cached = cachedPos(state);
        if (isValidSmeltingStation(level, context, cached)
                && !HiredPathMemory.isAvoided(level, villager, cached)
                && stationNeedsAttention(level, cached)) {
            return cached;
        }
        clearCachedStation(state);
        if (level.getGameTime() < state.getLong(NEXT_STATION_SCAN_GAME_TIME_TAG)) {
            return null;
        }

        List<FacilityCandidate> candidates = new ArrayList<>();
        for (BlockPos raw : context.workAreaPositions()) {
            BlockPos pos = raw.immutable();
            if (isValidSmeltingStation(level, context, pos)
                    && !HiredPathMemory.isAvoided(level, villager, pos)) {
                candidates.add(new FacilityCandidate(pos, villager.distanceToSqr(pos.getCenter())));
            }
        }
        candidates.sort(Comparator.comparingDouble(FacilityCandidate::score));
        boolean hasAvailableStation = candidates.stream()
                .anyMatch(candidate -> stationNeedsAttention(level, candidate.pos()));
        BlockPos fallback = candidates.stream()
                .filter(candidate -> !hasAvailableStation || stationNeedsAttention(level, candidate.pos()))
                .map(FacilityCandidate::pos)
                .findFirst()
                .orElse(null);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int attempts = 0;
        for (FacilityCandidate candidate : candidates) {
            if (hasAvailableStation && !stationNeedsAttention(level, candidate.pos())) {
                continue;
            }
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

    private static boolean stationNeedsAttention(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return false;
        }
        return !furnace.getItem(RESULT_SLOT).isEmpty()
                || isFuelRemainder(furnace.getItem(FUEL_SLOT))
                || furnace.getItem(INPUT_SLOT).isEmpty()
                || furnace.getItem(FUEL_SLOT).isEmpty();
    }

    private static boolean isValidSmeltingStation(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        return pos != null
                && context.isInsideWorkArea(pos)
                && context.isLoaded(level, pos)
                && (level.getBlockEntity(pos) instanceof FurnaceBlockEntity
                || level.getBlockEntity(pos) instanceof BlastFurnaceBlockEntity);
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
        if (furnace instanceof BlastFurnaceBlockEntity) {
            return (RecipeType<AbstractCookingRecipe>) (RecipeType<?>) RecipeType.BLASTING;
        }
        return (RecipeType<AbstractCookingRecipe>) (RecipeType<?>) RecipeType.SMELTING;
    }

    private static boolean isSmeltableOre(ServerLevel level, ItemStack stack, RecipeType<AbstractCookingRecipe> recipeType) {
        return isRawOre(stack) && smeltingRecipe(level, stack, recipeType).isPresent();
    }

    private static boolean isRawOre(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(Items.RAW_COPPER)
                || stack.is(Items.RAW_GOLD)
                || stack.is(Items.RAW_IRON));
    }

    private static boolean isSmeltedOre(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(Items.COPPER_INGOT)
                || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.IRON_INGOT));
    }

    private static Optional<RecipeHolder<AbstractCookingRecipe>> smeltingRecipe(
            ServerLevel level,
            ItemStack stack,
            RecipeType<AbstractCookingRecipe> recipeType) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(recipeType, new SingleRecipeInput(stack), level);
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

    private record FacilityCandidate(BlockPos pos, double score) {
    }
}
