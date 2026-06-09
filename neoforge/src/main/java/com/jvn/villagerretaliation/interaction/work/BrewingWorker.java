package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.mixin.BrewingStandBlockEntityAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class BrewingWorker extends AbstractBlockWorker {
    private static final String TARGET_ITEM_TAG = "BrewingTargetItem";
    private static final String TARGET_POTION_TAG = "BrewingTargetPotion";
    private static final String REMAINING_TAG = "BrewingRemaining";
    private static final String CONTINUOUS_TAG = "BrewingContinuous";
    private static final int FUEL_USES_PER_BLAZE_POWDER = 20;
    private static final int FIRST_BOTTLE_SLOT = 0;
    private static final int BOTTLE_SLOT_COUNT = 3;
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.BREWING;
    }

    public static void setOrder(CompoundTag state, ResourceLocation itemId, ResourceLocation potionId, int amount, boolean continuous) {
        state.putString(TARGET_ITEM_TAG, itemId.toString());
        state.putString(TARGET_POTION_TAG, potionId.toString());
        state.putInt(REMAINING_TAG, Math.max(0, amount));
        state.putBoolean(CONTINUOUS_TAG, continuous);
        state.remove("NextWorkGameTime");
    }

    public static boolean hasOrder(CompoundTag state) {
        return state.contains(TARGET_ITEM_TAG, Tag.TAG_STRING)
                && state.contains(TARGET_POTION_TAG, Tag.TAG_STRING)
                && (state.getBoolean(CONTINUOUS_TAG) || state.getInt(REMAINING_TAG) > 0);
    }

    public static String orderSummary(ServerLevel level, CompoundTag state) {
        return targetRoute(level, state)
                .map(route -> {
                    String amount = state.getBoolean(CONTINUOUS_TAG) ? "continuously" : Integer.toString(state.getInt(REMAINING_TAG));
                    return "Brewing " + amount + " x " + route.output().getHoverName().getString() + ".";
                })
                .orElse("No brewing order selected.");
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }
        boolean hasAssignedStorage = AssignedStorageService.hasAssignedStorage(level, villager);
        if (hasAssignedStorage) {
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (depositResult == DepositResult.MOVING) {
                return WorkResult.progressed("I am putting finished potions away before brewing more.");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
        }
        if (!hasOrder(context.state())) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("Choose a potion and amount before I start brewing.");
        }

        HiredBrewingRecipeCatalog.BrewingRoute route = targetRoute(level, context.state()).orElse(null);
        if (route == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "unknown_brewing_target", 0L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("That potion recipe is no longer available.");
        }

        BlockPos stand = nearestBrewingStand(level, villager, context);
        if (stand == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "no_brewing_stand", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("I need a brewing stand inside the assigned job site.");
        }

        int batchSize = nextBatchSize(context.state());
        if (batchSize <= 0) {
            clearOrder(context.state());
            return WorkResult.completed("The brewing order is complete.");
        }
        BlockPos water = nearestWaterSource(level, villager, context, stand);
        boolean waterSource = water != null;

        BrewingStandPlan standPlan = BrewingStandPlan.create(level, stand, route, batchSize);
        if (standPlan.hasWrongBottles()) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "brewing_stand_blocked", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("The brewing stand has different bottles in it.");
        }
        if (!standPlan.hasFinishedOutput(route) && standPlan.hasWrongIngredient()) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "brewing_stand_wrong_ingredient", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("The brewing stand has the wrong ingredient in it.");
        }
        if (!standPlan.hasFinishedOutput(route) && standPlan.hasWrongFuel()) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "brewing_stand_wrong_fuel", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("The brewing stand has the wrong fuel in it.");
        }

        MaterialPlan materials = MaterialPlan.create(level, villager, context, route, waterSource, standPlan);
        if (!materials.hasEverything()) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, materials.missingStatus(), level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle(materials.missingStatus());
        }
        WorkResult gatheredMaterials = gatherBrewingMaterials(level, villager, context, materials);
        if (gatheredMaterials != null) {
            context.setProgressTicks(0);
            return gatheredMaterials;
        }
        WorkResult preparedInputs = prepareBrewingInputs(level, context, route, standPlan);
        if (preparedInputs != null) {
            context.setProgressTicks(0);
            swingWorkTool(villager);
            return preparedInputs;
        }
        if (waterSource && materials.missingCarriedWaterBottles(context) > 0) {
            WorkResult waterResult = moveToWaterSourceAndFill(level, villager, context, water, materials.waterBottleCount());
            if (waterResult != null) {
                context.setProgressTicks(0);
                return waterResult;
            }
        }
        if (materials.missingCarriedWaterBottles(context) > 0 && countJobWaterBottles(context) <= 0) {
            HiredWorkerBrain.setFailure(context, "missing_brewing_water_bottles", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("Missing materials");
        }
        HiredPathTarget target = bestWorkTarget(level, villager, context, stand);
        if (target == null) {
            HiredWorkerBrain.setFailure(context, "brewing_stand_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, stand);
            return WorkResult.idle("I cannot reach the assigned brewing stand.");
        }
        prepareBreakingTarget(level, context, villager, target);
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, stand);
            if (!moveToTarget(level, villager, context, target, 0.45D)
                    && !moveNearBrewingStand(level, villager, context, stand, 0.45D)) {
                if (recordWorkPathFailure(level, villager, stand)) {
                    HiredWorkerBrain.setFailure(context, "brewing_stand_path_failed", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, stand);
                    return WorkResult.idle("The brewing stand is blocked off, so I am waiting for a clear path.");
                }
                return WorkResult.progressed("I am moving into position at the brewing stand.");
            }
            return WorkResult.progressed("I am heading to the brewing stand.");
        }
        clearWorkPathFailure(villager, stand);
        holdWorkPosition(villager, target);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, stand);

        context.setProgressTicks(0);
        return workBrewingStand(level, villager, context, route, standPlan.targetBottleCount(), standPlan.collectLimit(context.state()), stand);
    }

    private static boolean moveNearBrewingStand(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos stand,
            double speed) {
        Path path = villager.getNavigation().createPath(stand, 2);
        return path != null
                && path.canReach()
                && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)
                && villager.getNavigation().moveTo(path, speed);
    }

    @Override
    public String status(ServerLevel level, Villager villager, HiredWorkContext context) {
        return orderSummary(level, context.state()) + " " + context.status();
    }

    private static java.util.Optional<HiredBrewingRecipeCatalog.BrewingRoute> targetRoute(ServerLevel level, CompoundTag state) {
        ResourceLocation itemId = ResourceLocation.tryParse(state.getString(TARGET_ITEM_TAG));
        ResourceLocation potionId = ResourceLocation.tryParse(state.getString(TARGET_POTION_TAG));
        return HiredBrewingRecipeCatalog.find(level, itemId, potionId);
    }

    private static BlockPos nearestBrewingStand(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos raw : context.workAreaPositions()) {
            BlockPos pos = raw.immutable();
            if (!context.isLoaded(level, pos) || !level.getBlockState(pos).is(Blocks.BREWING_STAND)) {
                continue;
            }
            double distance = villager.distanceToSqr(pos.getCenter());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    private static BlockPos nearestWaterSource(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos stand) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos raw : context.workAreaPositions()) {
            BlockPos pos = raw.immutable();
            if (!context.isLoaded(level, pos)) {
                continue;
            }
            FluidState fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
                double distance = stand.distSqr(pos) + villager.distanceToSqr(pos.getCenter()) * 0.25D;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos;
                }
            }
        }
        return best;
    }

    private static int standFuelUses(ServerLevel level, BlockPos stand) {
        if (level.getBlockEntity(stand) instanceof BrewingStandBlockEntity blockEntity) {
            return Math.max(0, ((BrewingStandBlockEntityAccessor) blockEntity).villagerretaliation$getFuel());
        }
        return 0;
    }

    private static int nextBatchSize(CompoundTag state) {
        if (state.getBoolean(CONTINUOUS_TAG)) {
            return 3;
        }
        return Math.min(3, Math.max(0, state.getInt(REMAINING_TAG)));
    }

    private static void decrementOrder(CompoundTag state, int brewed) {
        if (state.getBoolean(CONTINUOUS_TAG)) {
            return;
        }
        int remaining = Math.max(0, state.getInt(REMAINING_TAG) - brewed);
        state.putInt(REMAINING_TAG, remaining);
        if (remaining <= 0) {
            clearOrder(state);
        }
    }

    public static void clearOrder(CompoundTag state) {
        state.remove(TARGET_ITEM_TAG);
        state.remove(TARGET_POTION_TAG);
        state.remove(REMAINING_TAG);
        state.remove(CONTINUOUS_TAG);
    }

    private WorkResult moveToWaterSourceAndFill(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos water,
            int requiredWaterBottles) {
        int missingWaterBottles = Math.max(0, requiredWaterBottles - countJobWaterBottles(context));
        if (missingWaterBottles <= 0) {
            return null;
        }
        HiredPathTarget target = bestWaterTarget(level, villager, context, water);
        if (target == null) {
            HiredWorkerBrain.setFailure(context, "brewing_water_source_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, water);
            return WorkResult.idle("I cannot reach the assigned water source.");
        }
        if (!canUseWaterFromCurrentPosition(villager, water)) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, water);
            if (!moveToWaterTarget(level, villager, context, target, 0.45D)) {
                if (recordWorkPathFailure(level, villager, water)) {
                    HiredWorkerBrain.setFailure(context, "brewing_water_source_path_failed", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, water);
                    return WorkResult.idle("The water source is blocked off, so I am waiting for a clear path.");
                }
                return WorkResult.progressed("I am moving into position at the water source.");
            }
            return WorkResult.progressed("I am heading to the water source.");
        }
        clearWorkPathFailure(villager, water);
        faceBlock(villager, water);
        villager.setDeltaMovement(villager.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
        int filled = fillWaterBottles(context, missingWaterBottles);
        if (filled <= 0) {
            HiredWorkerBrain.setFailure(context, "brewing_water_bottle_space", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, water);
            return WorkResult.idle("I need room in my job inventory to fill water bottles.");
        }
        swingWorkTool(villager);
        level.playSound(null, water, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        stopWorkNavigation(villager);
        HiredWorkerBrain.clearTarget(context);
        return null;
    }

    private static HiredPathTarget bestWaterTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos water) {
        if (canUseWaterFromCurrentPosition(villager, water)
                && context.isInsideWorkArea(villager.blockPosition())) {
            return new HiredPathTarget(water.immutable(), villager.blockPosition().immutable(), Vec3.atCenterOf(water));
        }
        HiredPathTarget best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos raw : BlockPos.betweenClosed(water.offset(-1, -1, -1), water.offset(1, 1, 1))) {
            BlockPos candidate = raw.immutable();
            if (!context.isInsideWorkArea(candidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)
                    || candidate.distSqr(water) > 4) {
                continue;
            }
            Path path = villager.getNavigation().createPath(candidate, 0);
            if (path == null || !path.canReach()) {
                continue;
            }
            double score = villager.distanceToSqr(candidate.getCenter()) + path.getNodeCount() * 1.5D;
            if (score < bestScore) {
                bestScore = score;
                best = new HiredPathTarget(water.immutable(), candidate, Vec3.atCenterOf(water));
            }
        }
        return best;
    }

    private static boolean canUseWaterFromCurrentPosition(Villager villager, BlockPos water) {
        return villager.getEyePosition().distanceToSqr(water.getCenter()) <= HiredMoveToBlockFaceJob.MAX_REACH_SQR
                && villager.position().distanceToSqr(water.getCenter()) <= 16.0D;
    }

    private static boolean moveToWaterTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            double speed) {
        if (!context.isInsideWorkArea(target.blockPos())
                || !context.isInsideWorkArea(target.approachPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())) {
            return false;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            return true;
        }
        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
        return path != null && path.canReach() && villager.getNavigation().moveTo(path, speed);
    }

    private WorkResult prepareBrewingInputs(
            ServerLevel level,
            HiredWorkContext context,
            HiredBrewingRecipeCatalog.BrewingRoute route,
            BrewingStandPlan standPlan) {
        for (int i = standPlan.nextIngredientIndex(); i < route.ingredients().size(); i++) {
            Item ingredient = route.ingredients().get(i);
            if (countJobItem(context, ingredient) > 0) {
                continue;
            }
            if (!HiredSupplyCrafting.craftCarriedSupplyItem(level, context, ingredient)) {
                HiredWorkerBrain.setFailure(context, "missing_brewing_ingredient", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
                return WorkResult.idle("Missing materials");
            }
            return WorkResult.progressed("I crafted brewing ingredients.");
        }
        if (standPlan.needsFuelForBrewing(route) && countJobItem(context, Items.BLAZE_POWDER) <= 0) {
            if (!HiredSupplyCrafting.craftCarriedSupplyItem(level, context, Items.BLAZE_POWDER)) {
                HiredWorkerBrain.setFailure(context, "missing_brewing_fuel", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
                return WorkResult.idle("Missing materials");
            }
            return WorkResult.progressed("I crafted blaze powder for the brewing stand.");
        }
        return null;
    }

    private WorkResult workBrewingStand(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredBrewingRecipeCatalog.BrewingRoute route,
            int batchSize,
            int collectLimit,
            BlockPos stand) {
        if (!(level.getBlockEntity(stand) instanceof BrewingStandBlockEntity blockEntity)) {
            HiredWorkerBrain.setFailure(context, "no_brewing_stand", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("I need a brewing stand inside the assigned job site.");
        }

        int collected = collectFinishedPotions(level, villager, context, route, blockEntity, stand, collectLimit);
        if (collected > 0) {
            boolean completesOrder = completesOrder(context.state(), collected);
            decrementOrder(context.state(), collected);
            level.playSound(null, stand, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
            setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, stand);
            String status = "I collected " + collected + " brewed " + route.output().getHoverName().getString() + ".";
            return completesOrder ? WorkResult.completed(status) : WorkResult.skilledProgress(status);
        }

        int currentStep = currentBrewingStep(level, blockEntity, route);
        if (currentStep < 0) {
            HiredWorkerBrain.setFailure(context, "brewing_stand_blocked", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("The brewing stand has different bottles in it.");
        }
        int loadedBottles = loadedBottleCount(blockEntity);
        if (loadedBottles > batchSize) {
            if (currentStep == 0 && blockEntity.getItem(INGREDIENT_SLOT).isEmpty()) {
                int unloaded = unloadExtraWaterBottlesFromStand(level, context, blockEntity, stand, loadedBottles - batchSize);
                if (unloaded <= 0) {
                    HiredWorkerBrain.setFailure(context, "brewing_water_bottle_space", level.getGameTime() + 100L);
                    setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, stand);
                    return WorkResult.idle("I need room in my job inventory before I can clear extra bottles from the stand.");
                }
                swingWorkTool(villager);
                return WorkResult.progressed("I cleared extra water bottles from the brewing stand before starting this order.");
            }
            return WorkResult.progressed("I am finishing the larger batch already in the brewing stand.");
        }
        if (loadedBottles < batchSize) {
            if (loadedBottles > 0 && (currentStep > 0 || !blockEntity.getItem(INGREDIENT_SLOT).isEmpty())) {
                return WorkResult.progressed("I am waiting for the current brewing batch to finish.");
            }
            int loaded = loadWaterBottlesIntoStand(level, context, blockEntity, stand, batchSize - loadedBottles);
            if (loaded <= 0) {
                HiredWorkerBrain.setFailure(context, "missing_brewing_water_bottles", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
                return WorkResult.idle("Missing materials");
            }
            swingWorkTool(villager);
            return WorkResult.progressed("I loaded water bottles into the brewing stand.");
        }

        if (currentStep >= route.ingredients().size()) {
            return WorkResult.progressed("I am ready to collect the finished potions.");
        }

        Item nextIngredient = route.ingredients().get(currentStep);
        ItemStack ingredientSlot = blockEntity.getItem(INGREDIENT_SLOT);
        if (!ingredientSlot.isEmpty()) {
            if (ingredientSlot.is(nextIngredient)) {
                return WorkResult.progressed("I am waiting for the brewing stand to finish.");
            }
            HiredWorkerBrain.setFailure(context, "brewing_stand_wrong_ingredient", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("The brewing stand has the wrong ingredient in it.");
        }

        WorkResult fuelResult = ensureStandFuel(level, villager, context, blockEntity, stand);
        if (fuelResult != null) {
            return fuelResult;
        }

        int consumed = context.inventory().consumeSupply(stack -> stack.is(nextIngredient), 1);
        if (consumed <= 0) {
            HiredWorkerBrain.setFailure(context, "missing_brewing_ingredient", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("Missing materials");
        }
        blockEntity.setItem(INGREDIENT_SLOT, new ItemStack(nextIngredient));
        updateBrewingStand(level, blockEntity, stand);
        swingWorkTool(villager);
        return WorkResult.progressed("I loaded the next brewing ingredient.");
    }

    private static boolean completesOrder(CompoundTag state, int brewed) {
        return !state.getBoolean(CONTINUOUS_TAG)
                && Math.max(0, state.getInt(REMAINING_TAG) - brewed) <= 0;
    }

    private WorkResult ensureStandFuel(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BrewingStandBlockEntity blockEntity,
            BlockPos stand) {
        if (standFuelUses(level, stand) > 0) {
            return null;
        }
        ItemStack fuelSlot = blockEntity.getItem(FUEL_SLOT);
        if (!fuelSlot.isEmpty()) {
            if (fuelSlot.is(Items.BLAZE_POWDER)) {
                return null;
            }
            HiredWorkerBrain.setFailure(context, "brewing_stand_wrong_fuel", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("The brewing stand has the wrong fuel in it.");
        }
        int consumed = context.inventory().consumeSupply(stack -> stack.is(Items.BLAZE_POWDER), 1);
        if (consumed <= 0) {
            HiredWorkerBrain.setFailure(context, "missing_brewing_fuel", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("Missing materials");
        }
        blockEntity.setItem(FUEL_SLOT, new ItemStack(Items.BLAZE_POWDER));
        updateBrewingStand(level, blockEntity, stand);
        swingWorkTool(villager);
        return WorkResult.progressed("I loaded blaze powder into the brewing stand.");
    }

    private static int loadWaterBottlesIntoStand(
            ServerLevel level,
            HiredWorkContext context,
            BrewingStandBlockEntity blockEntity,
            BlockPos stand,
            int count) {
        int loaded = 0;
        for (int slot = FIRST_BOTTLE_SLOT; slot < FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT && loaded < count; slot++) {
            if (!blockEntity.getItem(slot).isEmpty()) {
                continue;
            }
            if (context.inventory().consumeSupply(HiredBrewingRecipeCatalog::isWaterPotion, 1) <= 0) {
                break;
            }
            blockEntity.setItem(slot, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            loaded++;
        }
        if (loaded > 0) {
            updateBrewingStand(level, blockEntity, stand);
        }
        return loaded;
    }

    private static int unloadExtraWaterBottlesFromStand(
            ServerLevel level,
            HiredWorkContext context,
            BrewingStandBlockEntity blockEntity,
            BlockPos stand,
            int count) {
        int unloaded = 0;
        for (int slot = FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT - 1; slot >= FIRST_BOTTLE_SLOT && unloaded < count; slot--) {
            ItemStack stack = blockEntity.getItem(slot);
            if (!HiredBrewingRecipeCatalog.isWaterPotion(stack)) {
                continue;
            }
            ItemStack bottle = stack.copyWithCount(1);
            if (!context.inventory().insertSupply(bottle).isEmpty()) {
                break;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                blockEntity.setItem(slot, ItemStack.EMPTY);
            }
            unloaded++;
        }
        if (unloaded > 0) {
            updateBrewingStand(level, blockEntity, stand);
        }
        return unloaded;
    }

    private int collectFinishedPotions(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredBrewingRecipeCatalog.BrewingRoute route,
            BrewingStandBlockEntity blockEntity,
            BlockPos stand,
            int maxCount) {
        int collected = 0;
        int remaining = Math.max(0, maxCount);
        for (int slot = FIRST_BOTTLE_SLOT; slot < FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT && remaining > 0; slot++) {
            ItemStack stack = blockEntity.getItem(slot);
            if (stack.isEmpty() || !samePotionStack(stack, route.output())) {
                continue;
            }
            ItemStack output = stack.copyWithCount(1);
            if (!context.storeOutputAfterDepositIfFull(villager, output).isEmpty()) {
                HiredWorkerBrain.setFailure(context, "brewing_output_full_after_brew", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, stand);
                break;
            }
            stack.shrink(1);
            remaining--;
            if (stack.isEmpty()) {
                blockEntity.setItem(slot, ItemStack.EMPTY);
            }
            collected++;
        }
        if (collected > 0) {
            updateBrewingStand(level, blockEntity, stand);
        }
        return collected;
    }

    private static int currentBrewingStep(
            ServerLevel level,
            BrewingStandBlockEntity blockEntity,
            HiredBrewingRecipeCatalog.BrewingRoute route) {
        int loaded = loadedBottleCount(blockEntity);
        if (loaded <= 0) {
            return 0;
        }
        for (int step = 0; step <= route.ingredients().size(); step++) {
            ItemStack expected = routeStackAt(level, route, step);
            boolean matches = true;
            for (int slot = FIRST_BOTTLE_SLOT; slot < FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT; slot++) {
                ItemStack stack = blockEntity.getItem(slot);
                if (!stack.isEmpty() && !samePotionStack(stack, expected)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return step;
            }
        }
        return -1;
    }

    private static int loadedBottleCount(BrewingStandBlockEntity blockEntity) {
        int count = 0;
        for (int slot = FIRST_BOTTLE_SLOT; slot < FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT; slot++) {
            if (!blockEntity.getItem(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static ItemStack routeStackAt(
            ServerLevel level,
            HiredBrewingRecipeCatalog.BrewingRoute route,
            int step) {
        ItemStack stack = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        int clampedStep = Math.min(Math.max(0, step), route.ingredients().size());
        for (int i = 0; i < clampedStep; i++) {
            stack = level.potionBrewing().mix(new ItemStack(route.ingredients().get(i)), stack);
        }
        return stack.copyWithCount(1);
    }

    private static boolean samePotionStack(ItemStack first, ItemStack second) {
        return !first.isEmpty()
                && !second.isEmpty()
                && ItemStack.isSameItemSameComponents(first.copyWithCount(1), second.copyWithCount(1));
    }

    private static void updateBrewingStand(ServerLevel level, BrewingStandBlockEntity blockEntity, BlockPos stand) {
        blockEntity.setChanged();
        BlockState state = level.getBlockState(stand);
        level.sendBlockUpdated(stand, state, state, 3);
    }

    private static int fillWaterBottles(HiredWorkContext context, int count) {
        int filled = 0;
        for (int i = 0; i < count; i++) {
            if (countJobItem(context, Items.GLASS_BOTTLE) <= 0) {
                break;
            }
            ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            if (!HiredSupplyCrafting.canInsertSupply(context, waterBottle)
                    && !HiredSupplyCrafting.willConsumeOnlyCarriedSupplyStack(context, Items.GLASS_BOTTLE)) {
                break;
            }
            if (context.inventory().consumeSupply(stack -> stack.is(Items.GLASS_BOTTLE), 1) <= 0) {
                break;
            }
            ItemStack remainder = context.inventory().insertSupply(waterBottle);
            if (!remainder.isEmpty()) {
                context.inventory().insertSupply(new ItemStack(Items.GLASS_BOTTLE));
                break;
            }
            filled++;
        }
        return filled;
    }

    private WorkResult gatherBrewingMaterials(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            MaterialPlan materials) {
        StorageNeed need = materials.firstStorageNeed(context);
        if (need == null) {
            HiredWorkerBrain.clearStorageTarget(context);
            HiredStorageNavigationGoal.clearStorageNavigationState(context);
            return null;
        }
        if (!AssignedStorageService.hasAssignedStorage(level, villager)) {
            HiredWorkerBrain.setFailure(context, "missing_brewing_materials", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("Missing materials");
        }
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContaining(level, villager, need.predicate());
        if (storage == null) {
            HiredWorkerBrain.setFailure(context, "missing_brewing_materials", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("Missing materials");
        }
        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                0.45D);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("I am collecting brewing materials from storage.");
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredWorkerBrain.setFailure(context, "brewing_storage_path_failed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return WorkResult.idle("I cannot reach the assigned brewing materials.");
        }
        int moved = AssignedStorageService.transferItemsAtAssignedStorage(
                villager,
                storage,
                need.predicate(),
                need.count(),
                context.inventory()::insertSupply);
        if (moved <= 0) {
            HiredWorkerBrain.setFailure(context, "brewing_material_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return WorkResult.idle("I need room in my job inventory to carry brewing materials.");
        }
        HiredWorkerBrain.clearStorageTarget(context);
        HiredStorageNavigationGoal.clearStorageNavigationState(context);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
        return WorkResult.progressed("I gathered brewing materials from storage.");
    }

    private record BrewingStandPlan(
            int currentStep,
            int loadedBottleCount,
            int targetBottleCount,
            int waterBottleCount,
            int nextIngredientIndex,
            boolean hasFuelAvailable,
            boolean hasWrongBottles,
            boolean hasWrongIngredient,
            boolean hasWrongFuel) {
        static BrewingStandPlan create(
                ServerLevel level,
                BlockPos stand,
                HiredBrewingRecipeCatalog.BrewingRoute route,
                int requestedBatchSize) {
            if (!(level.getBlockEntity(stand) instanceof BrewingStandBlockEntity blockEntity)) {
                return new BrewingStandPlan(0, 0, Math.max(0, requestedBatchSize), Math.max(0, requestedBatchSize), 0, false, false, false, false);
            }

            int currentStep = currentBrewingStep(level, blockEntity, route);
            int loadedBottles = BrewingWorker.loadedBottleCount(blockEntity);
            boolean hasWrongBottles = currentStep < 0;
            ItemStack ingredientSlot = blockEntity.getItem(INGREDIENT_SLOT);
            boolean currentIngredientLoaded = false;
            boolean hasWrongIngredient = false;
            if (!ingredientSlot.isEmpty()) {
                if (!hasWrongBottles
                        && currentStep < route.ingredients().size()
                        && ingredientSlot.is(route.ingredients().get(currentStep))) {
                    currentIngredientLoaded = true;
                } else {
                    hasWrongIngredient = true;
                }
            }

            ItemStack fuelSlot = blockEntity.getItem(FUEL_SLOT);
            boolean hasWrongFuel = !fuelSlot.isEmpty() && !fuelSlot.is(Items.BLAZE_POWDER);
            boolean hasFuelAvailable = standFuelUses(level, stand) > 0 || fuelSlot.is(Items.BLAZE_POWDER);
            boolean canAddBaseBottles = !hasWrongBottles && currentStep == 0 && !hasWrongIngredient && loadedBottles < BOTTLE_SLOT_COUNT;
            int requested = Math.clamp(requestedBatchSize, 0, BOTTLE_SLOT_COUNT);
            int targetBottleCount = canAddBaseBottles
                    ? requested
                    : (loadedBottles > 0 ? loadedBottles : requested);
            int waterBottleCount = Math.max(0, targetBottleCount - loadedBottles);
            int nextIngredientIndex = hasWrongBottles || hasWrongIngredient
                    ? 0
                    : Math.min(route.ingredients().size(), currentStep + (currentIngredientLoaded ? 1 : 0));
            return new BrewingStandPlan(
                    currentStep,
                    loadedBottles,
                    targetBottleCount,
                    waterBottleCount,
                    nextIngredientIndex,
                    hasFuelAvailable,
                    hasWrongBottles,
                    hasWrongIngredient,
                    hasWrongFuel);
        }

        private boolean needsFuelForBrewing(HiredBrewingRecipeCatalog.BrewingRoute route) {
            return !this.hasFuelAvailable
                    && !this.hasWrongBottles
                    && !this.hasWrongIngredient
                    && this.currentStep < route.ingredients().size()
                    && this.targetBottleCount > 0;
        }

        private boolean hasFinishedOutput(HiredBrewingRecipeCatalog.BrewingRoute route) {
            return !this.hasWrongBottles
                    && this.loadedBottleCount > 0
                    && this.currentStep >= route.ingredients().size();
        }

        private int outputCount(CompoundTag state) {
            return Math.max(1, Math.min(this.targetBottleCount, collectLimit(state)));
        }

        private int collectLimit(CompoundTag state) {
            if (state.getBoolean(CONTINUOUS_TAG)) {
                return BOTTLE_SLOT_COUNT;
            }
            return Math.clamp(state.getInt(REMAINING_TAG), 0, BOTTLE_SLOT_COUNT);
        }
    }

    private record MaterialPlan(
            Map<Item, Integer> items,
            int waterBottleCount,
            boolean waterSource,
            boolean useWaterBottles,
            boolean hasEverything,
            String missingStatus) {
        static MaterialPlan create(
                ServerLevel level,
                Villager villager,
                HiredWorkContext context,
                HiredBrewingRecipeCatalog.BrewingRoute route,
                boolean waterSource,
                BrewingStandPlan standPlan) {
            HiredSupplyCrafting.MaterialPlanner planner = new HiredSupplyCrafting.MaterialPlanner(level, villager, context);
            Map<Item, Integer> items = new LinkedHashMap<>();
            for (int i = standPlan.nextIngredientIndex(); i < route.ingredients().size(); i++) {
                Item ingredient = route.ingredients().get(i);
                if (!planner.plan(ingredient, 1, items)) {
                    return missing(items, standPlan.waterBottleCount(), waterSource, false);
                }
            }
            if (standPlan.needsFuelForBrewing(route) && planFuel(planner, items) <= 0) {
                return missing(items, standPlan.waterBottleCount(), waterSource, false);
            }
            boolean useWaterBottles = standPlan.waterBottleCount() > 0;
            int availableWaterBottles = countWaterBottles(villager, context);
            if (waterSource && availableWaterBottles < standPlan.waterBottleCount()) {
                int missingWaterBottles = standPlan.waterBottleCount() - availableWaterBottles;
                Map<Item, Integer> withGlassBottles = new LinkedHashMap<>(items);
                if (planner.plan(Items.GLASS_BOTTLE, missingWaterBottles, withGlassBottles)) {
                    items = withGlassBottles;
                } else {
                    return missing(items, standPlan.waterBottleCount(), waterSource, true);
                }
            } else if (!waterSource && availableWaterBottles < standPlan.waterBottleCount()) {
                return missing(items, standPlan.waterBottleCount(), waterSource, true);
            }
            return new MaterialPlan(items, standPlan.waterBottleCount(), waterSource, useWaterBottles, true, "");
        }

        private static MaterialPlan missing(
                Map<Item, Integer> items,
                int waterBottleCount,
                boolean waterSource,
                boolean useWaterBottles) {
            return new MaterialPlan(items, waterBottleCount, waterSource, useWaterBottles, false, "Missing materials");
        }

        private static int planFuel(HiredSupplyCrafting.MaterialPlanner planner, Map<Item, Integer> items) {
            if (planner.directAvailable(Items.BLAZE_POWDER, items) > 0) {
                items.merge(Items.BLAZE_POWDER, 1, Integer::sum);
                return FUEL_USES_PER_BLAZE_POWDER;
            }
            Map<Item, Integer> withPowder = new LinkedHashMap<>(items);
            if (planner.surplusAvailable(Items.BLAZE_POWDER) > 0 && planner.plan(Items.BLAZE_POWDER, 1, withPowder)) {
                items.clear();
                items.putAll(withPowder);
                return FUEL_USES_PER_BLAZE_POWDER;
            }
            withPowder = new LinkedHashMap<>(items);
            if (planner.plan(Items.BLAZE_POWDER, 1, withPowder)) {
                items.clear();
                items.putAll(withPowder);
                return FUEL_USES_PER_BLAZE_POWDER;
            }
            return 0;
        }

        public boolean hasEverything() {
            return this.hasEverything;
        }

        public String missingStatus() {
            return this.missingStatus.isBlank() ? "Missing materials" : this.missingStatus;
        }

        private StorageNeed firstStorageNeed(HiredWorkContext context) {
            for (Map.Entry<Item, Integer> entry : this.items.entrySet()) {
                int carried = countJobItem(context, entry.getKey());
                int missing = Math.max(0, entry.getValue() - carried);
                if (missing > 0) {
                    Item item = entry.getKey();
                    return new StorageNeed(stack -> stack.is(item), missing);
                }
            }
            if (this.useWaterBottles) {
                int carried = countJobWaterBottles(context);
                int missing = Math.max(0, this.waterBottleCount - carried);
                if (missing > 0) {
                    int plannedGlassBottles = this.items.getOrDefault(Items.GLASS_BOTTLE, 0);
                    if (this.waterSource && plannedGlassBottles > 0 && countJobItem(context, Items.GLASS_BOTTLE) >= plannedGlassBottles) {
                        return null;
                    }
                    return new StorageNeed(HiredBrewingRecipeCatalog::isWaterPotion, missing);
                }
            }
            return null;
        }

        private int missingCarriedWaterBottles(HiredWorkContext context) {
            return Math.max(0, this.waterBottleCount - countJobWaterBottles(context));
        }

        private static int countWaterBottles(Villager villager, HiredWorkContext context) {
            return HiredSupplyCrafting.countAvailable(villager, context, HiredBrewingRecipeCatalog::isWaterPotion);
        }

        private static int countJobItem(HiredWorkContext context, Item item) {
            return HiredSupplyCrafting.countCarried(context, item);
        }

        private static int countJobWaterBottles(HiredWorkContext context) {
            return HiredSupplyCrafting.countCarried(context, HiredBrewingRecipeCatalog::isWaterPotion);
        }
    }

    private record StorageNeed(Predicate<ItemStack> predicate, int count) {
    }

    private static int countJobItem(HiredWorkContext context, Item item) {
        return MaterialPlan.countJobItem(context, item);
    }

    private static int countJobWaterBottles(HiredWorkContext context) {
        return MaterialPlan.countJobWaterBottles(context);
    }
}
