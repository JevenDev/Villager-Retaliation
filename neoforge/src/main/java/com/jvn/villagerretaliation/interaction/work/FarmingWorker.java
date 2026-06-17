package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.Path;

public final class FarmingWorker extends AbstractBlockWorker {
    private static final String NEXT_CROP_SCAN_GAME_TIME_TAG = "NextFarmingCropScanGameTime";
    private static final String CROP_SCAN_CURSOR_TAG = "FarmingCropScanCursor";
    private static final String NEXT_CROP_PRESENCE_SCAN_GAME_TIME_TAG = "NextFarmingCropPresenceScanGameTime";
    private static final String CROP_PRESENCE_SCAN_CURSOR_TAG = "FarmingCropPresenceScanCursor";
    private static final String CROP_PRESENT_TAG = "FarmingCropPresent";
    private static final int MAX_CROP_SCAN_POSITIONS_PER_WORK_TICK = 1536;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;
    private static final int MAX_PLANNED_CROP_TARGETS = 24;
    private static final HiredTargetSearch.Messages CROP_SEARCH_MESSAGES = new HiredTargetSearch.Messages(
            "active_crop_target",
            "planned_crop_target",
            "crop_scan_cooldown",
            "crop_scan_full_no_reachable_targets",
            "crop_scan_partial_",
            "crop_target_found",
            NO_TARGET_SCAN_COOLDOWN_TICKS);

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.FARMING;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }

        WorkResult hoeResult = ensureFarmingHoe(level, villager, context);
        if (hoeResult != null) {
            return hoeResult;
        }

        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        FarmTarget farmTarget = findFarmTarget(level, villager, context);
        if (farmTarget == null) {
            clearActiveBreakingTarget(level, context, villager);
            if (isCropScanInProgress(context)) {
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("interaction.work.farming.searching_scan");
            }
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.farming.no_target_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            CropPresence cropPresence = farmCropPresence(level, context);
            if (cropPresence != CropPresence.ABSENT) {
                HiredWorkerBrain.clearFailure(context);
                if (cropPresence == CropPresence.SCANNING) {
                    HiredWorkerBrain.setLastTargetScanResult(context, "crop_presence_scan_partial");
                    setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                    return WorkResult.progressed("interaction.work.farming.searching_scan");
                }
                HiredWorkerBrain.setLastTargetScanResult(context, "waiting_for_growth");
                if (roamInsideWorkArea(level, villager, context, 0.35D)) {
                    return WorkResult.progressed("interaction.work.farming.roaming");
                }
                setTaskState(context, HiredWorkerTaskState.IDLE);
                return WorkResult.idle("interaction.work.farming.waiting_for_growth");
            }
            if (roamInsideWorkArea(level, villager, context, 0.35D)) {
                return WorkResult.progressed("interaction.work.farming.roaming");
            }
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.farming.no_targets");
        }

        BlockPos targetPos = farmTarget.pos();
        BlockState targetState = level.getBlockState(targetPos);
        FarmTargetType targetType = farmTargetType(level, targetPos, targetState);
        if (targetType == FarmTargetType.NONE) {
            HiredWorkPlan.removeTarget(context, targetPos);
            clearActiveBreakingTarget(level, context, villager);
            HiredWorkerBrain.setFailure(context, "target_changed", level.getGameTime() + 40L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, targetPos);
            return WorkResult.idle("interaction.work.farming.target_changed");
        }

        if (targetType == FarmTargetType.CROP && targetState.getBlock() instanceof CropBlock crop) {
            clearActiveBreakingTarget(level, context, villager);
            if (!canWorkCropFromCurrentPosition(villager, context, targetPos)) {
                context.setProgressTicks(0);
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, targetPos);
                if (!moveToCropTarget(level, villager, context, targetPos, 0.5D)) {
                    if (recordWorkPathFailure(level, villager, targetPos)) {
                        HiredWorkPlan.removeTarget(context, targetPos);
                        HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 20L * 30L);
                        setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, targetPos);
                        return WorkResult.idle("interaction.work.farming.crop_blocked");
                    }
                    return WorkResult.progressed("interaction.work.farming.crop_repositioning");
                }
                return WorkResult.progressed("interaction.work.farming.moving_to_crop");
            }
            clearWorkPathFailure(villager, targetPos);
            stopAtCropTarget(villager, targetPos);
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.WORKING, targetPos);
            return harvestCropNow(level, villager, context, targetPos, crop);
        }

        if (!canWorkFarmOutputFromCurrentPosition(villager, context, targetPos)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, targetPos);
            if (!moveToFarmOutputTarget(level, villager, context, targetPos, 0.45D)) {
                if (recordWorkPathFailure(level, villager, targetPos)) {
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, targetPos);
                    return WorkResult.idle("interaction.work.farming.output_blocked");
                }
                return WorkResult.progressed("interaction.work.farming.output_repositioning");
            }
            return WorkResult.progressed("interaction.work.farming.moving_to_output");
        }
        clearWorkPathFailure(villager, targetPos);
        stopAtFarmOutputTarget(villager, targetPos);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, targetPos);

        int needed = Math.max(1, 5 - Math.max(0, context.efficiency() - 75) / 30);
        int progress = context.progressTicks() + 1;
        if (progress < needed) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            showBreakProgress(level, villager, targetPos, progress, needed);
            return WorkResult.progressed("interaction.work.farming.harvesting_output");
        }

        context.setProgressTicks(0);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, targetPos);
        ItemStack tool = context.inventory().findTool(FarmerHoeRequirement::isHoe);
        FarmHarvestResult harvestResult = storeFarmOutputDrops(level, context, villager, targetPos, tool);
        if (harvestResult == FarmHarvestResult.OUTPUT_FULL) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.45D);
            if (depositResult == DepositResult.DEPOSITED) {
                harvestResult = storeFarmOutputDrops(level, context, villager, targetPos, tool);
            }
            if (harvestResult == FarmHarvestResult.COMPLETED) {
                return completedFarmTarget(
                        level,
                        villager,
                        context,
                        targetPos,
                        "interaction.work.farming.completed_output");
            }
            if (harvestResult == FarmHarvestResult.TARGET_CHANGED) {
                return targetChanged(level, villager, context, targetPos);
            }
            if (depositResult == DepositResult.DEPOSITED && harvestResult == FarmHarvestResult.OUTPUT_FULL) {
                return WorkResult.progressed("interaction.work.farming.output_full_depositing");
            }
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.farming.output_full_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("interaction.work.farming.output_full_blocked");
        }
        if (harvestResult == FarmHarvestResult.TARGET_CHANGED) {
            return targetChanged(level, villager, context, targetPos);
        }
        return completedFarmTarget(
                level,
                villager,
                context,
                targetPos,
                "interaction.work.farming.completed_output");
    }

    private WorkResult harvestCropNow(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target,
            CropBlock crop) {
        context.setProgressTicks(0);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, target);
        ItemStack tool = context.inventory().findTool(FarmerHoeRequirement::isHoe);
        FarmHarvestResult harvestResult = storeCropDrops(level, context, villager, target, tool);
        if (harvestResult == FarmHarvestResult.OUTPUT_FULL) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.45D);
            if (depositResult == DepositResult.DEPOSITED) {
                harvestResult = storeCropDrops(level, context, villager, target, tool);
            }
            if (harvestResult == FarmHarvestResult.COMPLETED) {
                return completedFarmTarget(
                        level,
                        villager,
                        context,
                        target,
                        "interaction.work.farming.completed_crop");
            }
            if (harvestResult == FarmHarvestResult.MISSING_PLANTING_ITEM) {
                HiredWorkerBrain.setFailure(context, "missing_planting_item", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, target);
                return WorkResult.idle("interaction.work.farming.missing_planting_item");
            }
            if (harvestResult == FarmHarvestResult.TARGET_CHANGED) {
                return targetChanged(level, villager, context, target);
            }
            if (depositResult == DepositResult.DEPOSITED && harvestResult == FarmHarvestResult.OUTPUT_FULL) {
                return WorkResult.progressed("interaction.work.farming.output_full_depositing");
            }
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.farming.output_full_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("interaction.work.farming.output_full_blocked");
        }
        if (harvestResult == FarmHarvestResult.MISSING_PLANTING_ITEM) {
            HiredWorkerBrain.setFailure(context, "missing_planting_item", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, target);
            return WorkResult.idle("interaction.work.farming.missing_planting_item");
        }
        if (harvestResult == FarmHarvestResult.TARGET_CHANGED) {
            return targetChanged(level, villager, context, target);
        }
        return completedFarmTarget(
                level,
                villager,
                context,
                target,
                "interaction.work.farming.completed_crop");
    }

    private WorkResult completedFarmTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target,
            String statusKey) {
        HiredWorkPlan.removeTarget(context, target);
        clearActiveBreakingTarget(level, context, villager);
        if (HiredWorkPlan.size(context) > 0) {
            setTaskState(context, HiredWorkerTaskState.FINDING_CHAIN_TARGET, target);
            return WorkResult.skilledProgress(statusKey);
        }
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return WorkResult.completed(statusKey);
    }

    private WorkResult targetChanged(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos target) {
        HiredWorkPlan.removeTarget(context, target);
        clearActiveBreakingTarget(level, context, villager);
        HiredWorkerBrain.setFailure(context, "target_changed", level.getGameTime() + 40L);
        setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target);
        return WorkResult.idle("interaction.work.farming.target_changed");
    }

    private WorkResult ensureFarmingHoe(ServerLevel level, Villager villager, HiredWorkContext context) {
        ItemStack hoe = context.inventory().equipBestTool(FarmerHoeRequirement::isHoe, FarmerHoeRequirement::hoeScore);
        if (!hoe.isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            return null;
        }
        if (!context.useAssignedStorageForSupplies()) {
            HiredWorkerBrain.setFailure(context, "missing_hoe", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return WorkResult.idle("interaction.work.farming.missing_hoe");
        }
        BlockPos storage = AssignedStorageService.nearestAssignedToolStoragePosContaining(level, villager, FarmerHoeRequirement::isHoe);
        if (storage == null) {
            HiredWorkerBrain.setFailure(context, "missing_hoe", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return WorkResult.idle("interaction.work.farming.missing_hoe");
        }
        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result result = HiredStorageNavigationGoal.moveToStorageTarget(level, context, villager, storage, 0.45D);
        if (result == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("interaction.work.farming.collecting_hoe");
        }
        if (result == HiredStorageNavigationGoal.Result.FAILED) {
            AssignedStorageService.rememberToolStorageFailure(level, villager, storage, "farming_hoe_storage_unreachable");
            HiredWorkerBrain.setFailure(context, "farming_hoe_storage_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return WorkResult.idle("interaction.work.farming.hoe_unreachable");
        }
        int moved = AssignedStorageService.transferToolAtAssignedStorage(
                villager,
                storage,
                FarmerHoeRequirement::isHoe,
                context.inventory()::insertTool);
        hoe = context.inventory().equipBestTool(FarmerHoeRequirement::isHoe, FarmerHoeRequirement::hoeScore);
        if (moved <= 0 || hoe.isEmpty()) {
            HiredWorkerBrain.setFailure(context, "farming_hoe_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return WorkResult.idle("interaction.work.farming.hoe_inventory_full");
        }
        AssignedStorageService.clearStorageFailure(level, villager, storage);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
        return WorkResult.progressed("interaction.work.farming.collected_hoe");
    }

    private FarmHarvestResult storeCropDrops(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos target,
            ItemStack tool) {
        if (!context.isInsideWorkArea(target)
                || !context.isLoaded(level, target)
                || !canWorkCropFromCurrentPosition(villager, context, target)) {
            return FarmHarvestResult.TARGET_CHANGED;
        }
        BlockState state = level.getBlockState(target);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return FarmHarvestResult.TARGET_CHANGED;
        }
        boolean replant = shouldReplantCrops(context);
        ItemStack seed = replant ? seedForCrop(level, target, crop) : ItemStack.EMPTY;
        List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), villager, tool);
        List<ItemStack> storedDrops = copyDrops(drops);
        boolean reservedPlantingItem = replant && reservePlantingItemFromDrops(storedDrops, seed);
        if (replant && !reservedPlantingItem && !hasPlantingItemAvailable(villager, context, seed)) {
            return FarmHarvestResult.MISSING_PLANTING_ITEM;
        }
        if (!context.canStoreOutputs(storedDrops)) {
            return FarmHarvestResult.OUTPUT_FULL;
        }
        if (replant && !reservedPlantingItem && !consumePlantingItem(villager, context, seed)) {
            return FarmHarvestResult.MISSING_PLANTING_ITEM;
        }
        for (ItemStack drop : storedDrops) {
            if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                return FarmHarvestResult.OUTPUT_FULL;
            }
        }
        faceBlock(villager, target);
        swingWorkTool(villager);
        if (replant) {
            level.setBlock(target, crop.getStateForAge(0), 3);
        } else {
            level.destroyBlock(target, false, villager);
        }
        HiredPathMemory.rememberRecent(level, target);
        return FarmHarvestResult.COMPLETED;
    }

    private boolean canWorkFarmOutputFromCurrentPosition(Villager villager, HiredWorkContext context, BlockPos target) {
        return context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(target)
                && villager.position().distanceToSqr(target.getCenter()) <= HiredMoveToBlockFaceJob.MAX_REACH_SQR;
    }

    private boolean moveToFarmOutputTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target,
            double speed) {
        if (!context.isInsideWorkArea(target)
                || !context.isLoaded(level, target)
                || !context.isInsideWorkArea(villager.blockPosition())) {
            return false;
        }
        if (canWorkFarmOutputFromCurrentPosition(villager, context, target)) {
            stopAtFarmOutputTarget(villager, target);
            return true;
        }

        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null && !HiredMoveToBlockFaceJob.pathStaysInsideFilter(currentPath, pos -> canUseFarmMovementPosition(context, pos))) {
            stopCropNavigation(villager);
            return false;
        }

        BlockPos approach = nearestFarmApproach(level, villager, context, target);
        if (approach == null) {
            HiredPathMemory.clearNavigationProgress(villager);
            return false;
        }

        setCropWalkTarget(villager, approach, speed);
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && approach.equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    approach,
                    villager.distanceToSqr(approach.getCenter()))) {
                stopCropNavigation(villager);
                return false;
            }
            return true;
        }

        Path path = villager.getNavigation().createPath(approach, 0);
        if (path != null
                && path.canReach()
                && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, pos -> canUseFarmMovementPosition(context, pos))) {
            boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(villager, path, approach, speed, 1);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(level, villager, approach, villager.distanceToSqr(approach.getCenter()));
            } else {
                HiredPathMemory.clearNavigationProgress(villager);
            }
            return moved;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private BlockPos nearestFarmApproach(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos raw : BlockPos.betweenClosed(target.offset(-2, -1, -2), target.offset(2, 1, 2))) {
            BlockPos candidate = raw.immutable();
            if (!context.isInsideWorkArea(candidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)
                    || candidate.getCenter().distanceToSqr(target.getCenter()) > HiredMoveToBlockFaceJob.MAX_REACH_SQR) {
                continue;
            }
            Path path = villager.getNavigation().createPath(candidate, 0);
            if (path == null || !path.canReach() || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, pos -> canUseFarmMovementPosition(context, pos))) {
                continue;
            }
            double score = villager.distanceToSqr(candidate.getCenter())
                    + HiredMoveToBlockFaceJob.pathTraversalCost(level, path)
                    + HiredMoveToBlockFaceJob.terrainCost(level, candidate)
                    + HiredPathMemory.recentCost(villager, target);
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private void stopAtFarmOutputTarget(Villager villager, BlockPos target) {
        stopCropNavigation(villager);
        faceBlock(villager, target);
    }

    private FarmHarvestResult storeFarmOutputDrops(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos target,
            ItemStack tool) {
        if (!context.isInsideWorkArea(target)
                || !context.isLoaded(level, target)
                || !canWorkFarmOutputFromCurrentPosition(villager, context, target)) {
            return FarmHarvestResult.TARGET_CHANGED;
        }
        BlockState state = level.getBlockState(target);
        if (farmTargetType(level, target, state) != FarmTargetType.BLOCK_OUTPUT) {
            return FarmHarvestResult.TARGET_CHANGED;
        }
        List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), villager, tool);
        if (!context.canStoreOutputs(drops)) {
            return FarmHarvestResult.OUTPUT_FULL;
        }
        for (ItemStack drop : drops) {
            if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                return FarmHarvestResult.OUTPUT_FULL;
            }
        }
        faceBlock(villager, target);
        swingWorkTool(villager);
        level.destroyBlock(target, false, villager);
        clearBreakProgress(level, villager, target);
        HiredPathMemory.rememberRecent(level, target);
        return FarmHarvestResult.COMPLETED;
    }

    private boolean canWorkCropFromCurrentPosition(Villager villager, HiredWorkContext context, BlockPos target) {
        return context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(target)
                && villager.position().distanceToSqr(target.getCenter()) <= HiredMoveToBlockFaceJob.MAX_REACH_SQR;
    }

    private boolean canUseFarmMovementPosition(HiredWorkContext context, BlockPos pos) {
        return context.hasWorkArea()
                && pos != null
                && pos.getX() >= context.workMin().getX() - 1
                && pos.getX() <= context.workMax().getX() + 1
                && pos.getY() >= context.workMin().getY()
                && pos.getY() <= context.workMax().getY() + 1
                && pos.getZ() >= context.workMin().getZ() - 1
                && pos.getZ() <= context.workMax().getZ() + 1;
    }

    private boolean moveToCropTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target,
            double speed) {
        if (!context.isInsideWorkArea(target)
                || !context.isLoaded(level, target)
                || !context.isInsideWorkArea(villager.blockPosition())) {
            return false;
        }
        if (canWorkCropFromCurrentPosition(villager, context, target)) {
            stopAtCropTarget(villager, target);
            return true;
        }

        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null && !HiredMoveToBlockFaceJob.pathStaysInsideFilter(currentPath, pos -> canUseFarmMovementPosition(context, pos))) {
            stopCropNavigation(villager);
            return false;
        }

        BlockPos approach = nearestFarmApproach(level, villager, context, target);
        if (approach == null) {
            HiredPathMemory.clearNavigationProgress(villager);
            return false;
        }

        setCropWalkTarget(villager, approach, speed);
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && approach.equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    approach,
                    villager.distanceToSqr(approach.getCenter()))) {
                stopCropNavigation(villager);
                return false;
            }
            return true;
        }

        Path path = villager.getNavigation().createPath(approach, 0);
        if (path != null
                && path.canReach()
                && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, pos -> canUseFarmMovementPosition(context, pos))) {
            boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(villager, path, approach, speed, 1);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(level, villager, approach, villager.distanceToSqr(approach.getCenter()));
            } else {
                HiredPathMemory.clearNavigationProgress(villager);
            }
            return moved;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private void stopAtCropTarget(Villager villager, BlockPos target) {
        stopCropNavigation(villager);
        faceBlock(villager, target);
    }

    private void stopCropNavigation(Villager villager) {
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        HiredPathMemory.clearNavigationProgress(villager);
    }

    private static void setCropWalkTarget(Villager villager, BlockPos target, double speed) {
        BlockPosTracker tracker = new BlockPosTracker(target);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, tracker);
        VillagerTaskNavigationUtil.setHiredWalkTarget(villager, target, speed, 1);
    }

    private FarmTarget findFarmTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        Predicate<BlockPos> validator = farmTargetValidator(level, villager, context);
        return HiredTargetSearch.find(
                level,
                context,
                () -> activeFarmTarget(level, villager, context),
                target -> validator.test(target.pos()),
                filter -> plannedFarmTarget(level, villager, context, filter, MAX_PLANNED_CROP_TARGETS),
                validator,
                NEXT_CROP_SCAN_GAME_TIME_TAG,
                CROP_SCAN_CURSOR_TAG,
                MAX_CROP_SCAN_POSITIONS_PER_WORK_TICK,
                candidates -> rebuildFarmObjective(level, villager, context, candidates),
                CROP_SEARCH_MESSAGES);
    }

    private FarmTarget activeFarmTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        if (active == null) {
            return null;
        }
        FarmTargetType type = farmTargetType(level, active.blockPos(), level.getBlockState(active.blockPos()));
        return type == FarmTargetType.BLOCK_OUTPUT ? new FarmTarget(active.blockPos(), type) : null;
    }

    private Predicate<BlockPos> farmTargetValidator(ServerLevel level, Villager villager, HiredWorkContext context) {
        return pos -> context.isInsideWorkArea(pos)
                && context.isLoaded(level, pos)
                && isHarvestableFarmTarget(level, pos)
                && !isTemporarilyAvoidedTarget(level, villager, pos);
    }

    private static boolean isCropScanInProgress(HiredWorkContext context) {
        return HiredWorkAreaScan.isInProgress(context, CROP_SCAN_CURSOR_TAG);
    }

    private static boolean isHarvestableFarmTarget(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return farmTargetType(level, pos, state) != FarmTargetType.NONE;
    }

    private static CropPresence farmCropPresence(ServerLevel level, HiredWorkContext context) {
        if (!HiredWorkAreaScan.isInProgress(context, CROP_PRESENCE_SCAN_CURSOR_TAG)
                && level.getGameTime() < context.state().getLong(NEXT_CROP_PRESENCE_SCAN_GAME_TIME_TAG)) {
            return context.state().getBoolean(CROP_PRESENT_TAG) ? CropPresence.PRESENT : CropPresence.ABSENT;
        }

        HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                context,
                CROP_PRESENCE_SCAN_CURSOR_TAG,
                MAX_CROP_SCAN_POSITIONS_PER_WORK_TICK,
                pos -> context.isLoaded(level, pos) && isFarmCropBlock(level.getBlockState(pos)));
        if (!scan.candidates().isEmpty()) {
            HiredWorkAreaScan.clearCursor(context, CROP_PRESENCE_SCAN_CURSOR_TAG);
            context.state().putBoolean(CROP_PRESENT_TAG, true);
            context.state().putLong(NEXT_CROP_PRESENCE_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
            return CropPresence.PRESENT;
        }
        if (scan.completedFullPass()) {
            context.state().putBoolean(CROP_PRESENT_TAG, false);
            context.state().putLong(NEXT_CROP_PRESENCE_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
            return CropPresence.ABSENT;
        }
        return CropPresence.SCANNING;
    }

    private static boolean isFarmCropBlock(BlockState state) {
        return state.getBlock() instanceof CropBlock
                || state.getBlock() instanceof CocoaBlock
                || state.getBlock() instanceof StemBlock
                || state.getBlock() instanceof AttachedStemBlock
                || state.is(BlockTags.CROPS);
    }

    private static FarmTargetType farmTargetType(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state) ? FarmTargetType.CROP : FarmTargetType.NONE;
        }
        if (state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock) {
            return FarmTargetType.NONE;
        }
        if (state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE) {
            return FarmTargetType.BLOCK_OUTPUT;
        }
        if ((state.is(BlockTags.CROPS) && !hasImmatureAgeProperty(state))
                || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.MELON)) {
            return FarmTargetType.BLOCK_OUTPUT;
        }
        return FarmTargetType.NONE;
    }

    private static boolean hasImmatureAgeProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty ageProperty && "age".equals(property.getName())) {
                int currentAge = state.getValue(ageProperty);
                int maxAge = ageProperty.getPossibleValues().stream()
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(currentAge);
                return currentAge < maxAge;
            }
        }
        return false;
    }

    private FarmTarget plannedFarmTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Predicate<BlockPos> validator,
            int maxPlanTargets) {
        Predicate<BlockPos> safeValidator = validator == null ? ignored -> true : validator;
        HiredWorkPlan.retainMatching(
                context,
                safeValidator,
                maxPlanTargets);
        for (BlockPos planned : HiredWorkPlan.targets(context)) {
            if (!safeValidator.test(planned)) {
                continue;
            }
            FarmTargetType type = farmTargetType(level, planned, level.getBlockState(planned));
            if (type == FarmTargetType.CROP) {
                return new FarmTarget(planned, type);
            }
            if (type == FarmTargetType.BLOCK_OUTPUT) {
                return new FarmTarget(planned, type);
            }
        }
        HiredWorkPlan.clear(context);
        return null;
    }

    private FarmTarget rebuildFarmObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates) {
        List<BlockPos> row = bestCropRow(level, villager, candidates);
        if (row.size() >= 2) {
            HiredWorkPlan.replaceWithObjective(context, "row", row.getFirst(), row, MAX_PLANNED_CROP_TARGETS);
            FarmTarget target = plannedFarmTarget(
                    level,
                    villager,
                    context,
                    farmTargetValidator(level, villager, context),
                    MAX_PLANNED_CROP_TARGETS);
            if (target != null) {
                return target;
            }
        }

        List<BlockPos> ordered = HiredWorkPlan.routeOrder(villager.blockPosition(), candidates, MAX_PLANNED_CROP_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() >= 2 ? "patch" : "single_crop",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_CROP_TARGETS);
        return plannedFarmTarget(
                level,
                villager,
                context,
                farmTargetValidator(level, villager, context),
                MAX_PLANNED_CROP_TARGETS);
    }

    private static List<BlockPos> bestCropRow(ServerLevel level, Villager villager, List<BlockPos> candidates) {
        Set<Long> candidateSet = new HashSet<>();
        for (BlockPos candidate : candidates) {
            candidateSet.add(candidate.asLong());
        }

        List<BlockPos> best = List.of();
        double bestScore = Double.NEGATIVE_INFINITY;
        for (BlockPos seed : candidates) {
            BlockState seedState = level.getBlockState(seed);
            if (!(seedState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(seedState)) {
                continue;
            }
            List<BlockPos> alongX = contiguousCropLine(level, seed, candidateSet, true);
            List<BlockPos> alongZ = contiguousCropLine(level, seed, candidateSet, false);
            List<BlockPos> row = alongX.size() >= alongZ.size() ? alongX : alongZ;
            double score = row.size() * 1000.0D - villager.distanceToSqr(seed.getCenter());
            if (row.size() >= 2 && score > bestScore) {
                best = row;
                bestScore = score;
            }
        }
        return best;
    }

    private static List<BlockPos> contiguousCropLine(
            ServerLevel level,
            BlockPos seed,
            Set<Long> candidateSet,
            boolean alongX) {
        BlockState seedState = level.getBlockState(seed);
        if (!(seedState.getBlock() instanceof CropBlock seedCrop)) {
            return List.of();
        }

        int fixedY = seed.getY();
        int fixedAxis = alongX ? seed.getZ() : seed.getX();
        List<BlockPos> negatives = new ArrayList<>();
        List<BlockPos> positives = new ArrayList<>();

        for (int step = 1; step <= MAX_PLANNED_CROP_TARGETS; step++) {
            BlockPos candidate = alongX
                    ? seed.offset(-step, 0, 0)
                    : seed.offset(0, 0, -step);
            if (!candidateSet.contains(candidate.asLong()) || !matchesCrop(level, candidate, seedCrop, fixedY, fixedAxis, alongX)) {
                break;
            }
            negatives.addFirst(candidate.immutable());
        }
        for (int step = 1; step <= MAX_PLANNED_CROP_TARGETS; step++) {
            BlockPos candidate = alongX
                    ? seed.offset(step, 0, 0)
                    : seed.offset(0, 0, step);
            if (!candidateSet.contains(candidate.asLong()) || !matchesCrop(level, candidate, seedCrop, fixedY, fixedAxis, alongX)) {
                break;
            }
            positives.add(candidate.immutable());
        }

        List<BlockPos> row = new ArrayList<>(negatives.size() + positives.size() + 1);
        row.addAll(negatives);
        row.add(seed.immutable());
        row.addAll(positives);
        return row;
    }

    private static boolean matchesCrop(
            ServerLevel level,
            BlockPos candidate,
            CropBlock seedCrop,
            int fixedY,
            int fixedAxis,
            boolean alongX) {
        if (candidate.getY() != fixedY) {
            return false;
        }
        if (alongX ? candidate.getZ() != fixedAxis : candidate.getX() != fixedAxis) {
            return false;
        }
        BlockState state = level.getBlockState(candidate);
        return state.getBlock() == seedCrop && seedCrop.isMaxAge(state);
    }

    private static boolean shouldReplantCrops(HiredWorkContext context) {
        return "harvest_replant".equals(context.state().getString("CropMode"));
    }

    private static List<ItemStack> copyDrops(List<ItemStack> drops) {
        List<ItemStack> copied = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                copied.add(drop.copy());
            }
        }
        return copied;
    }

    private static boolean reservePlantingItemFromDrops(List<ItemStack> drops, ItemStack seed) {
        if (seed.isEmpty()) {
            return false;
        }
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (!matchesPlantingItem(drop, seed)) {
                continue;
            }
            drop.shrink(1);
            if (drop.isEmpty()) {
                drops.remove(i);
            }
            return true;
        }
        return false;
    }

    private static boolean hasPlantingItemAvailable(Villager villager, HiredWorkContext context, ItemStack seed) {
        if (seed.isEmpty()) {
            return false;
        }
        if (!context.inventory().findSupply(stack -> matchesPlantingItem(stack, seed)).isEmpty()) {
            return true;
        }
        if (context.inventory().hasOutput(stack -> matchesPlantingItem(stack, seed))) {
            return true;
        }
        return context.useAssignedStorageForSupplies()
                && AssignedStorageService.countItems(villager, stack -> matchesPlantingItem(stack, seed)) > 0;
    }

    private static boolean consumePlantingItem(Villager villager, HiredWorkContext context, ItemStack seed) {
        if (context.consumeSupply(villager, stack -> matchesPlantingItem(stack, seed), 1) > 0) {
            return true;
        }
        return !context.inventory().consumeOutput(stack -> matchesPlantingItem(stack, seed), 1).isEmpty();
    }

    private static boolean matchesPlantingItem(ItemStack stack, ItemStack seed) {
        return !stack.isEmpty()
                && !seed.isEmpty()
                && (ItemStack.isSameItemSameComponents(stack, seed) || stack.is(seed.getItem()));
    }

    private static ItemStack seedForCrop(ServerLevel level, BlockPos pos, CropBlock crop) {
        if (crop == Blocks.WHEAT) {
            return new ItemStack(Items.WHEAT_SEEDS);
        }
        if (crop == Blocks.CARROTS) {
            return new ItemStack(Items.CARROT);
        }
        if (crop == Blocks.POTATOES) {
            return new ItemStack(Items.POTATO);
        }
        if (crop == Blocks.BEETROOTS) {
            return new ItemStack(Items.BEETROOT_SEEDS);
        }
        return crop.getCloneItemStack(level, pos, crop.defaultBlockState());
    }

    private enum FarmTargetType {
        NONE,
        CROP,
        BLOCK_OUTPUT
    }

    private enum FarmHarvestResult {
        COMPLETED,
        OUTPUT_FULL,
        TARGET_CHANGED,
        MISSING_PLANTING_ITEM
    }

    private enum CropPresence {
        PRESENT,
        ABSENT,
        SCANNING
    }

    private record FarmTarget(BlockPos pos, FarmTargetType type) {
    }
}
