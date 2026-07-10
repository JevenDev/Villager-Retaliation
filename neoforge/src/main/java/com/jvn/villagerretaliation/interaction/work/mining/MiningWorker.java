package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.work.HiredTargetSearch;
import com.jvn.villagerretaliation.interaction.work.HiredPathTarget;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class MiningWorker extends AbstractBlockWorker {
    private static final int MAX_PLANNED_MINING_TARGETS = 20;
    private static final int MAX_EXCAVATION_SCAN_POSITIONS = 768;
    private static final int MAX_WORK_AREA_RETURN_PATH_ATTEMPTS = 24;
    private static final HiredTargetSearch.Messages EXCAVATION_SEARCH_MESSAGES = new HiredTargetSearch.Messages(
            "active_excavation_target",
            "planned_excavation_target",
            "excavation_scan_cooldown",
            "no_targets",
            "excavation_scan_partial_",
            "excavation_targets_found",
            MiningWorkerState.noTargetScanCooldownTicks());

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.MINING;
    }

    public static BlockPos excavationEntryTarget(ServerLevel level, HiredWorkContext context) {
        return MiningExcavationSupport.entryTarget(level, context);
    }

    public static BlockPos excavationReturnTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        return MiningExcavationSupport.returnTarget(level, villager, context);
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }

        HiredMiningMode mode = HiredMiningMode.fromState(context.state());
        if (mode.excavatesArea()) {
            WorkResult supplyResult = MiningExcavationSupport.gatherSupplies(level, villager, context);
            if (supplyResult != null) {
                return supplyResult;
            }
        }
        WorkResult returnResult = returnToMiningWorkAreaIfOutside(level, villager, context, mode, 0.55D);
        if (returnResult != null) {
            return returnResult;
        }
        WorkResult fullInventoryResult = depositFullInventoryBeforeMining(level, villager, context);
        if (fullInventoryResult != null) {
            return fullInventoryResult;
        }
        if (mode.excavatesArea()) {
            WorkResult supportResult = MiningExcavationSupport.maintain(level, villager, context, this);
            if (supportResult != null) {
                return supportResult;
            }
            WorkResult ladderRequirement = MiningExcavationSupport.requireLadder(level, villager, context);
            if (ladderRequirement != null) {
                return ladderRequirement;
            }
            MiningWorkerState.set(context, MiningWorkerState.Phase.ASSESS_HAZARDS);
            WorkResult hazardResult = MiningHazardManager.tick(level, villager, context);
            if (hazardResult != null) {
                clearActiveBreakingTarget(level, context, villager);
                HiredWorkPlan.clear(context);
                return hazardResult;
            }
        }
        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        HiredPathTarget target = resolveTarget(level, villager, context, mode);
        if (target == null) {
            clearActiveBreakingTarget(level, context, villager);
            if (mode.excavatesArea() && settleIntoClearedExcavationFloor(level, context, villager, 0.55D)) {
                context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
                MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET);
                HiredWorkerBrain.setLastTargetScanResult(context, "repositioning_cleared_excavation_floor");
                return WorkResult.progressed(miningStatusKey(mode, "repositioning"));
            }
            if (MiningWorkerState.isExcavationScanInProgress(context, mode)) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("interaction.work.mining.scan_excavation");
            }
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
            MiningWorkerState.set(context, depositResult == DepositResult.DEPOSITED || depositResult == DepositResult.MOVING
                    ? MiningWorkerState.Phase.DEPOSIT_OUTPUT
                    : MiningWorkerState.Phase.WAITING_NO_TARGETS);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed(miningStatusKey(mode, "no_target_deposit"));
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            if (depositResult != DepositResult.UNAVAILABLE && isExcavationComplete(level, context, mode)) {
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
                return WorkResult.completed(excavationResultStatusKey(depositResult, "completed_excavation"));
            }
            if (roamInsideWorkArea(level, villager, context, 0.4D)) {
                return WorkResult.progressed(miningStatusKey(mode, "searching"));
            }
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            MiningWorkerState.ensureNoTargetScanCooldown(level, context);
            return WorkResult.idle(depositResult == DepositResult.DEPOSITED
                    ? miningStatusKey(mode, "deposited_waiting")
                    : miningStatusKey(mode, "no_targets"));
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        String toolLabel = MiningBlockRules.requiredMiningToolLabel(mode, targetState);
        ToolStorageResult toolResult = equipBestToolOrCollectFromStorage(
                level,
                villager,
                context,
                stack -> MiningBlockRules.isUsableMiningTool(mode, stack, targetState),
                stack -> effectiveDestroySpeed(stack, targetState),
                0.55D);
        if (toolResult.status() != ToolStorageStatus.READY && toolResult.status() != ToolStorageStatus.COLLECTED) {
            if (toolResult.status() == ToolStorageStatus.MOVING) {
                return WorkResult.progressed("interaction.work.status.collecting_tool");
            }
            HiredWorkPlan.clear(context);
            clearActiveBreakingTarget(level, context, villager);
            if (toolResult.status() == ToolStorageStatus.UNREACHABLE) {
                HiredWorkerBrain.setFailure(context, "tool_storage_unreachable_" + toolLabel, level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, toolResult.storagePos());
                return WorkResult.idle("interaction.work.status.tool_storage_unreachable", Map.of("tool", toolLabel));
            }
            if (toolResult.status() == ToolStorageStatus.INVENTORY_FULL) {
                HiredWorkerBrain.setFailure(context, "tool_inventory_full_" + toolLabel, level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, toolResult.storagePos());
                return WorkResult.idle("interaction.work.status.tool_inventory_full", Map.of("tool", toolLabel));
            }
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_TOOL);
            HiredWorkerBrain.setFailure(context, "missing_" + toolLabel, 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return WorkResult.idle(miningStatusKey(mode, "missing_tool"), Map.of("tool", toolLabel));
        }
        ItemStack tool = toolResult.tool();
        if (toolResult.status() == ToolStorageStatus.COLLECTED && !context.isInsideWorkArea(villager.blockPosition())) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, miningReturnTarget(level, villager, context, mode));
            return WorkResult.progressed("interaction.work.status.returning_bounds");
        }
        WorkResult outputCapacityResult = depositBeforeMiningIfTargetDropsDoNotFit(
                level,
                villager,
                context,
                target,
                targetState,
                tool,
                mode);
        if (outputCapacityResult != null) {
            return outputCapacityResult;
        }

        prepareBreakingTarget(level, context, villager, target);
        if (!canStartMining(level, villager, context, target, mode)) {
            context.setProgressTicks(0);
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            boolean closeEnough = isCloseEnough(villager, target);
            boolean hasLineOfSight = hasLineOfSightToTarget(level, villager, target);
            if (!moveToMiningTarget(level, villager, context, target, mode, 0.55D)) {
                if (mode.excavatesArea() && settleNearExcavationTarget(level, context, villager, target, 0.55D)) {
                    clearWorkPathFailure(villager, target.blockPos());
                    return WorkResult.progressed(miningStatusKey(mode, "repositioning"));
                }
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
                    return WorkResult.idle(miningStatusKey(mode, "blocked_target"));
                }
                return WorkResult.progressed(miningStatusKey(mode, "repositioning"));
            }
            return WorkResult.progressed(closeEnough && !hasLineOfSight
                    ? miningStatusKey(mode, "blocked_swing")
                    : miningStatusKey(mode, "moving_to_target"));
        }

        clearWorkPathFailure(villager, target.blockPos());
        holdWorkPosition(villager, target);
        MiningWorkerState.set(context, MiningWorkerState.Phase.MINE_TARGET);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, target.blockPos());

        int needed = actualBreakProgressGoal(level, target.blockPos(), tool);
        int progress = context.progressTicks() + MiningWorkerState.elapsedBreakProgressTicks(level, context);
        if (progress < needed) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            showBreakProgress(level, villager, target.blockPos(), progress, needed);
            return WorkResult.progressed(miningStatusKey(mode, "working_target"));
        }

        List<ItemStack> drops = Block.getDrops(
                targetState,
                level,
                target.blockPos(),
                level.getBlockEntity(target.blockPos()),
                villager,
                tool);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, target.blockPos());
        if (!context.canStoreOutputs(drops)) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.MOVING) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.DEPOSIT_OUTPUT);
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.mining.output_full_depositing");
            }
            if (depositResult == DepositResult.DEPOSITED && !context.canStoreOutputs(drops)) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.DEPOSIT_OUTPUT);
                return WorkResult.progressed("interaction.work.mining.deposited_checking_packs");
            }
        }
        if (!context.canStoreOutputs(drops)) {
            context.setProgressTicks(0);
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_OUTPUT_FULL);
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("interaction.work.mining.output_full_blocked");
        }

        context.setProgressTicks(0);
        if (!storeMinedDrops(level, context, villager, target, tool, mode)) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_OUTPUT_FULL);
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("interaction.work.mining.output_full_blocked");
        }
        if (mode.excavatesArea()) {
            MiningWorkerState.clearExcavationLayerCache(context);
        }
        HiredOreBlockTracker.onBlockBroken(level, target.blockPos());
        HiredWorkPlan.removeTarget(context, target.blockPos());

        MiningWorkerState.rememberLastMined(context, target.blockPos());
        if (!mode.excavatesArea()) {
            MiningWorkerState.rememberMiningAnchor(level, context, target.blockPos());
        }
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.FINDING_CHAIN_TARGET, target.blockPos());
        HiredPathTarget nextTarget;
        if (mode.excavatesArea()) {
            nextTarget = plannedExcavationTarget(
                    level,
                    villager,
                    context,
                    pos -> isValidExcavationTarget(level, villager, context, pos),
                    MAX_PLANNED_MINING_TARGETS);
            if (nextTarget == null) {
                nextTarget = findNearestExcavationTarget(level, villager, context);
            }
        } else {
            nextTarget = findAdjacentMineable(level, villager, context, target.blockPos(), mode);
            if (nextTarget == null) {
                nextTarget = findMineableInCurrentPocket(level, villager, context);
            }
        }
        if (nextTarget != null) {
            if (!mode.excavatesArea()) {
                MiningWorkerState.rememberMiningAnchor(level, context, nextTarget.blockPos());
                HiredWorkPlan.prioritize(context, nextTarget.blockPos(), MAX_PLANNED_MINING_TARGETS);
            }
            prepareBreakingTarget(level, context, villager, nextTarget);
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, nextTarget.blockPos());
            return WorkResult.skilledProgress(miningStatusKey(mode, "next_target"));
        }

        MiningWorkerState.clearMiningAnchor(context);
        if (MiningWorkerState.isExcavationScanInProgress(context, mode)) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.skilledProgress("interaction.work.mining.post_clear_scan");
        }
        DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
        MiningWorkerState.set(context, depositResult == DepositResult.DEPOSITED || depositResult == DepositResult.MOVING
                ? MiningWorkerState.Phase.DEPOSIT_OUTPUT
                : MiningWorkerState.Phase.WAITING_NO_TARGETS);
        if (depositResult == DepositResult.MOVING) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.skilledProgress("interaction.work.mining.post_clear_depositing");
        }
        if (depositResult == DepositResult.STORAGE_FULL) {
            return WorkResult.idle(storageFullStatus(context));
        }
        if (depositResult != DepositResult.UNAVAILABLE && isExcavationComplete(level, context, mode)) {
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            MiningWorkerState.ensureNoTargetScanCooldown(level, context);
            return WorkResult.completed(excavationResultStatusKey(depositResult, "completed_excavation"));
        }
        if (roamInsideWorkArea(level, villager, context, 0.4D)) {
            return WorkResult.skilledProgress(miningStatusKey(mode, "completed_searching"));
        }
        if (mode.excavatesArea()) {
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.skilledProgress(excavationResultStatusKey(depositResult, "continuing_excavation"));
        }
        setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
        MiningWorkerState.ensureNoTargetScanCooldown(level, context);
        return WorkResult.completed(depositResult == DepositResult.DEPOSITED
                ? miningStatusKey(mode, "completed_deposited")
                : miningStatusKey(mode, "completed_no_next"));
    }

    private HiredPathTarget resolveTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredMiningMode mode) {
        if (mode.excavatesArea()) {
            return resolveExcavationTarget(level, villager, context);
        }
        return resolveOreTarget(level, villager, context);
    }

    private WorkResult depositFullInventoryBeforeMining(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        boolean storageTrip = worker.storageTargetPos() != null
                && (worker.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE
                || worker.taskState() == HiredWorkerTaskState.DEPOSITING
                || worker.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL);
        if (!storageTrip && (context.hasOutputSpace() || !context.hasOutputToDeposit())) {
            return null;
        }
        return depositBeforeMining(level, villager, context);
    }

    private WorkResult depositBeforeMiningIfTargetDropsDoNotFit(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            BlockState targetState,
            ItemStack tool,
            HiredMiningMode mode) {
        List<ItemStack> drops = Block.getDrops(
                targetState,
                level,
                target.blockPos(),
                level.getBlockEntity(target.blockPos()),
                villager,
                tool);
        List<ItemStack> requiredCapacity = drops;
        if (mode.excavatesArea()
                && MiningExcavationSupport.needsLadderRouteOutputReserve(level, context, target.blockPos())) {
            requiredCapacity = new ArrayList<>(drops);
            requiredCapacity.add(new ItemStack(Items.DIRT));
            requiredCapacity.add(new ItemStack(Items.COBBLESTONE));
            requiredCapacity.add(new ItemStack(Items.RAW_IRON));
        }
        if (context.canStoreOutputs(requiredCapacity)) {
            return null;
        }
        return depositBeforeMining(level, villager, context);
    }

    private WorkResult depositBeforeMining(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        clearActiveBreakingTarget(level, context, villager);
        DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
        if (depositResult == DepositResult.DEPOSITED) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.DEPOSIT_OUTPUT);
            return WorkResult.progressed("interaction.work.mining.deposited_checking_packs");
        }
        if (depositResult == DepositResult.MOVING) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.DEPOSIT_OUTPUT);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("interaction.work.mining.output_full_depositing");
        }
        if (depositResult == DepositResult.STORAGE_FULL) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_OUTPUT_FULL);
            return WorkResult.idle(storageFullStatus(context));
        }
        MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_OUTPUT_FULL);
        HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
        setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
        return WorkResult.idle("interaction.work.mining.output_full_blocked");
    }

    private WorkResult returnToMiningWorkArea(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredMiningMode mode,
            double speed) {
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (worker.taskState() != HiredWorkerTaskState.RETURNING_TO_WORK_AREA) {
            return null;
        }
        if (context.isInsideWorkArea(villager.blockPosition())) {
            HiredPathMemory.clearNavigationProgress(villager);
            return null;
        }
        BlockPos target = miningReturnTarget(level, villager, context, mode);
        if (target == null || !context.isLoaded(level, target)) {
            HiredWorkerBrain.setFailure(context, "return_path_missing", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, target);
            return WorkResult.idle("interaction.work.status.return_path_missing");
        }
        if (mode.excavatesArea() && isAtExcavationReturnTarget(context, villager, target)) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredPathMemory.clearNavigationProgress(villager);
            return null;
        }
        if (mode.excavatesArea() && VillagerTaskNavigationUtil.continueLadderRoute(level, villager, target, speed)) {
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, target);
            return WorkResult.progressed("interaction.work.status.returning_by_ladder");
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(level, villager, target, villager.distanceToSqr(target.getCenter()))) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredPathMemory.clearNavigationProgress(villager);
            } else {
                setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, target);
                return WorkResult.progressed("interaction.work.status.returning_bounds");
            }
        }
        Path path = HiredPathMemory.createPath(level, villager, target, 0);
        if (path != null
                && path.canReach()
                && VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target, speed, 0)) {
            HiredPathMemory.rememberNavigationProgress(level, villager, target, villager.distanceToSqr(target.getCenter()));
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, target);
            return WorkResult.progressed("interaction.work.status.returning_bounds");
        }
        if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target, speed)
                && observeManualNavigationProgress(level, villager, target)) {
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, target);
            return WorkResult.progressed("interaction.work.status.returning_by_ladder");
        }
        if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, target, speed)
                && observeManualNavigationProgress(level, villager, target)) {
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, target);
            return WorkResult.progressed("interaction.work.status.returning_by_ladder");
        }
        if (moveDirectlyTowardNearbyReturnTarget(level, villager, target, speed)
                && observeManualNavigationProgress(level, villager, target)) {
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, target);
            return WorkResult.progressed("interaction.work.status.returning_bounds");
        }
        HiredWorkerBrain.setFailure(context, "return_path_missing", level.getGameTime() + 100L);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, target);
        return WorkResult.idle("interaction.work.status.return_path_missing");
    }

    private WorkResult returnToMiningWorkAreaIfOutside(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredMiningMode mode,
            double speed) {
        if (context.isInsideWorkArea(villager.blockPosition())) {
            return returnToMiningWorkArea(level, villager, context, mode, speed);
        }

        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (worker.taskState().keepsStorageTarget() && worker.storageTargetPos() != null) {
            return null;
        }
        boolean recoveringFromTargetFailure = hasOutsideTargetDiagnostic(worker);
        if (worker.taskState() != HiredWorkerTaskState.RETURNING_TO_WORK_AREA
                && !recoveringFromTargetFailure
                && !mode.excavatesArea()) {
            return null;
        }

        if (recoveringFromTargetFailure) {
            clearOutsideTargetDiagnostics(context, worker);
        }
        if (worker.taskState() != HiredWorkerTaskState.RETURNING_TO_WORK_AREA) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, miningReturnTarget(level, villager, context, mode));
        }
        return returnToMiningWorkArea(level, villager, context, mode, speed);
    }

    private static boolean hasOutsideTargetDiagnostic(HiredWorkerBrain.Snapshot worker) {
        return isTargetDiagnostic(worker.failureReason()) || isTargetDiagnostic(worker.lastTargetScanResult());
    }

    private static boolean isAtExcavationReturnTarget(HiredWorkContext context, Villager villager, BlockPos target) {
        BlockPos pos = villager.blockPosition();
        if (pos.distSqr(target) <= 1.0D
                && Math.abs(pos.getY() - target.getY()) <= 1) {
            return true;
        }
        int horizontalDistance = Math.abs(pos.getX() - target.getX())
                + Math.abs(pos.getZ() - target.getZ());
        int allowedHorizontalDistance = target.getY() == context.workMax().getY() + 1 ? 2 : 1;
        return horizontalDistance <= allowedHorizontalDistance
                && pos.getY() >= target.getY()
                && pos.getY() <= target.getY() + 1;
    }

    private static void clearOutsideTargetDiagnostics(HiredWorkContext context, HiredWorkerBrain.Snapshot worker) {
        if (isTargetDiagnostic(worker.failureReason())) {
            HiredWorkerBrain.clearFailure(context);
        }
        if (isTargetDiagnostic(worker.lastTargetScanResult())) {
            HiredWorkerBrain.setLastTargetScanResult(context, "returning_to_work_area");
        }
    }

    private static boolean isTargetDiagnostic(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("target_unreachable")
                || normalized.contains("no_target")
                || normalized.contains("no_reachable_targets")
                || normalized.contains("no_targets");
    }

    private HiredPathTarget resolveOreTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        if (active != null
                && isValidMiningTarget(level, villager, context, active.blockPos(), anchor)
                && isSafeMiningWorkTarget(level, villager, active)) {
            MiningWorkerState.rememberMiningAnchor(level, context, active.blockPos());
            return active;
        }
        if (storedWorkTarget(context.state()) != null) {
            clearActiveBreakingTarget(level, context, villager);
        }

        HiredPathTarget planned = plannedOreTarget(
                level,
                villager,
                context,
                pos -> isValidMiningTarget(level, villager, context, pos, MiningWorkerState.miningAnchor(level, context)),
                MAX_PLANNED_MINING_TARGETS);
        if (planned != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, planned.blockPos());
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return planned;
        }

        BlockPos lastMined = MiningWorkerState.lastMinedBlock(context);
        if (lastMined != null) {
            HiredPathTarget adjacent = findAdjacentMineable(level, villager, context, lastMined, HiredMiningMode.EXPOSED_ORES);
            if (adjacent != null) {
                MiningWorkerState.rememberMiningAnchor(level, context, adjacent.blockPos());
                MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                return adjacent;
            }
        }

        HiredPathTarget pocketTarget = findMineableInCurrentPocket(level, villager, context);
        if (pocketTarget != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, pocketTarget.blockPos());
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return pocketTarget;
        }
        MiningWorkerState.clearMiningAnchor(context);

        HiredPathTarget recentlyExposedTarget = findRecentlyExposedMineableInRadius(level, villager, context);
        if (recentlyExposedTarget != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, recentlyExposedTarget.blockPos());
            context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return recentlyExposedTarget;
        }

        if (level.getGameTime() < context.state().getLong(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG)) {
            return null;
        }
        MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
        HiredPathTarget target = findNearestMineableInRadius(level, villager, context);
        if (target != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, target.blockPos());
        }
        return target;
    }

    private HiredPathTarget resolveExcavationTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget immediate = immediateExcavationWorkTarget(level, villager, context);
        if (immediate != null) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return immediate;
        }
        HiredPathTarget shaftExtension = ladderShaftExtensionTarget(level, villager, context);
        if (shaftExtension != null) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            HiredWorkPlan.replaceWithObjective(
                    context,
                    "ladder_shaft",
                    shaftExtension.blockPos(),
                    List.of(shaftExtension.blockPos()),
                    MAX_PLANNED_MINING_TARGETS);
            return shaftExtension;
        }
        return HiredTargetSearch.find(
                level,
                context,
                () -> {
                    HiredPathTarget active = activeExcavationWorkTarget(level, context, villager);
                    if (active == null && storedWorkTarget(context.state()) != null) {
                        clearActiveBreakingTarget(level, context, villager);
                    }
                    return active;
                },
                target -> isValidExcavationTarget(level, villager, context, target.blockPos()),
                filter -> {
                    HiredPathTarget planned = plannedExcavationTarget(
                            level,
                            villager,
                            context,
                            filter,
                            MAX_PLANNED_MINING_TARGETS);
                    if (planned != null) {
                        MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                    }
                    return planned;
                },
                pos -> isValidExcavationTarget(level, villager, context, pos),
                MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG,
                MiningWorkerState.EXCAVATION_SCAN_CURSOR_TAG,
                MAX_EXCAVATION_SCAN_POSITIONS,
                candidates -> {
                    MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                    return rebuildExcavationObjective(level, villager, context, candidates);
                },
                EXCAVATION_SEARCH_MESSAGES);
    }

    private HiredPathTarget ladderShaftExtensionTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos target = MiningExcavationSupport.nextNeededLadderShaftTarget(level, context);
        if (target == null || !context.isLoaded(level, target)) {
            return null;
        }
        List<BlockPos> approaches = new ArrayList<>();
        approaches.add(target.above());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            approaches.add(target.relative(direction));
            approaches.add(target.relative(direction).above());
        }
        approaches.sort(Comparator.comparingDouble(pos -> villager.distanceToSqr(pos.getCenter())));
        for (BlockPos approach : approaches) {
            if (!isValidExcavationWorkStance(level, context, approach)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, approach)) {
                continue;
            }
            Vec3 hit = HiredMoveToBlockFaceJob.visibleHitPosition(
                    level,
                    villager,
                    Vec3.atBottomCenterOf(approach).add(0.0D, villager.getEyeHeight(), 0.0D),
                    target);
            HiredPathTarget pathTarget = new HiredPathTarget(
                    target.immutable(),
                    approach.immutable(),
                    hit == null ? target.getCenter() : hit);
            if (approach.equals(villager.blockPosition()) && !canStartMining(level, villager, context, pathTarget, HiredMiningMode.EXCAVATE_AREA)) {
                continue;
            }
            return pathTarget;
        }
        return null;
    }

    private HiredPathTarget findAdjacentMineable(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos origin,
            HiredMiningMode mode) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz).immutable();
                    if (isValidMiningTarget(level, villager, context, pos, anchor, mode)) {
                        candidates.add(pos);
                    }
                }
            }
        }
        return mode.excavatesArea()
                ? chooseExcavationTarget(level, villager, context, candidates)
                : chooseReachableOreTarget(level, villager, context, candidates);
    }

    private HiredPathTarget findNearestExcavationTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                context,
                MiningWorkerState.EXCAVATION_SCAN_CURSOR_TAG,
                MAX_EXCAVATION_SCAN_POSITIONS,
                pos -> isValidExcavationTarget(level, villager, context, pos));
        if (!scan.candidates().isEmpty()) {
            context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
            HiredWorkerBrain.setLastTargetScanResult(context, "excavation_targets_found");
            return rebuildExcavationObjective(level, villager, context, scan.candidates());
        }
        if (!scan.completedFullPass()) {
            HiredWorkerBrain.setLastTargetScanResult(context, "excavation_scan_in_progress");
            return null;
        }
        context.state().putLong(
                MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG,
                level.getGameTime() + MiningWorkerState.noTargetScanCooldownTicks());
        HiredWorkerBrain.setLastTargetScanResult(context, "no_targets");
        return null;
    }

    private HiredPathTarget rebuildExcavationObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates) {
        List<BlockPos> ordered = MiningExcavationPlan.lineOrder(villager, context, candidates, MAX_PLANNED_MINING_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? "excavation" : "excavation_block",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_MINING_TARGETS);
        return plannedExcavationTarget(
                level,
                villager,
                context,
                pos -> isValidExcavationTarget(level, villager, context, pos),
                MAX_PLANNED_MINING_TARGETS);
    }

    private HiredPathTarget activeExcavationWorkTarget(ServerLevel level, HiredWorkContext context, Villager villager) {
        HiredPathTarget target = storedWorkTarget(context.state());
        if (target == null || HiredPathMemory.isAvoided(level, villager, target.blockPos())) {
            return null;
        }
        boolean ladderRecovery = MiningExcavationSupport.shouldUseLadderFallback(context, villager, target);
        if (!context.isInsideWorkArea(target.blockPos())) {
            return null;
        }
        if (!isValidExcavationTarget(level, villager, context, target.blockPos())
                || !context.isLoaded(level, target.blockPos())
                || !isValidExcavationWorkStance(level, context, target.approachPos())
                || isUnsafeExcavationUnderfoot(level, context, target.blockPos(), target.approachPos())) {
            return null;
        }
        if (!ladderRecovery
                && (!isUsableExcavationApproachForCurrentLayer(level, context, villager.blockPosition(), target.approachPos())
                || !isValidExcavationApproach(level, context, villager.blockPosition()))) {
            return null;
        }
        if (canMineFromCurrentPosition(level, villager, target)) {
            return target;
        }
        if (!HiredMoveToBlockFaceJob.isValidApproachPosition(level, target.approachPos())) {
            return null;
        }
        Vec3 approachEye = new Vec3(
                target.approachPos().getX() + 0.5D,
                target.approachPos().getY() + villager.getEyeHeight(),
                target.approachPos().getZ() + 0.5D);
        return HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, approachEye, target.blockPos(), target.hitPos())
                ? target
                : null;
    }

    private HiredPathTarget plannedExcavationTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            java.util.function.Predicate<BlockPos> validator,
            int maxPlanTargets) {
        java.util.function.Predicate<BlockPos> safeValidator = validator == null ? ignored -> true : validator;
        HiredWorkPlan.retainMatching(context, safeValidator, maxPlanTargets);
        for (BlockPos planned : HiredWorkPlan.targets(context)) {
            HiredPathTarget target = bestExcavationWorkTarget(level, villager, context, planned);
            if (target != null && safeValidator.test(target.blockPos())) {
                return target;
            }
        }
        HiredWorkPlan.clear(context);
        return null;
    }

    private HiredPathTarget plannedOreTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            java.util.function.Predicate<BlockPos> validator,
            int maxPlanTargets) {
        java.util.function.Predicate<BlockPos> safeValidator = validator == null ? ignored -> true : validator;
        HiredWorkPlan.retainMatching(context, safeValidator, maxPlanTargets);
        for (BlockPos planned : HiredWorkPlan.targets(context)) {
            HiredPathTarget target = bestOreWorkTarget(level, villager, context, planned);
            if (target != null && safeValidator.test(target.blockPos())) {
                return target;
            }
        }
        HiredWorkPlan.clear(context);
        return null;
    }

    private HiredPathTarget bestOreWorkTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos target) {
        if (!context.isInsideWorkArea(target)) {
            return null;
        }
        return chooseReachableOreTarget(level, villager, context, List.of(target));
    }

    private HiredPathTarget chooseReachableOreTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Iterable<BlockPos> targets) {
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                targets,
                MAX_PLANNED_MINING_TARGETS,
                context::isInsideWorkArea,
                context::isInsideWorkArea,
                context::isInsideWorkArea,
                ignored -> false,
                null,
                (target, approach) -> !isUnsafeUnderfootMiningTarget(level, target, approach))
                .search()
                .target();
    }

    private HiredPathTarget bestExcavationWorkTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos target) {
        if (!context.isInsideWorkArea(target)) {
            return null;
        }
        return chooseExcavationTarget(level, villager, context, List.of(target));
    }

    private HiredPathTarget chooseExcavationTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Iterable<BlockPos> targets) {
        BlockPos pathOrigin = villager.blockPosition().immutable();
        Predicate<BlockPos> routeFilter = pos -> isValidExcavationApproach(level, context, pos);
        Predicate<BlockPos> approachFilter = pos -> routeFilter.test(pos)
                && isValidExcavationWorkStance(level, context, pos)
                && isUsableExcavationApproachForCurrentLayer(level, context, pathOrigin, pos)
                && !level.getBlockState(pos).is(Blocks.LADDER);
        Predicate<BlockPos> pathFilter = pos -> routeFilter.test(pos) || pos.equals(pathOrigin);
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                targets,
                MAX_PLANNED_MINING_TARGETS,
                context::isInsideWorkArea,
                approachFilter,
                pathFilter,
                ignored -> false,
                (target, approach) -> canUseExcavationLadderApproach(level, villager, context, target, approach),
                (target, approach) -> !isUnsafeExcavationUnderfoot(level, context, target, approach))
                .search()
                .target();
    }

    private boolean canUseExcavationLadderApproach(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target,
            BlockPos approach) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null || currentLayerY >= context.workMax().getY()) {
            return false;
        }
        if (!context.isInsideWorkArea(target)
                || !context.isInsideWorkArea(approach)
                || !isValidExcavationWorkStance(level, context, approach)
                || !MiningExcavationSupport.hasCompleteLadderRouteToLayer(level, context, currentLayerY)
                || MiningExcavationSupport.entryTarget(level, context) == null) {
            return false;
        }
        return MiningExcavationSupport.shouldUseLadderFallback(
                context,
                villager,
                new HiredPathTarget(target.immutable(), approach.immutable(), target.getCenter()));
    }

    private HiredPathTarget immediateExcavationWorkTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos current = villager.blockPosition().immutable();
        if (!isValidExcavationWorkStance(level, context, current)) {
            return null;
        }

        HiredPathTarget stored = storedWorkTarget(context.state());
        if (stored != null) {
            HiredPathTarget recovered = immediateExcavationWorkTarget(level, villager, context, stored.blockPos(), current);
            if (recovered != null) {
                return recovered;
            }
        }

        for (BlockPos rawPos : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
            HiredPathTarget recovered = immediateExcavationWorkTarget(level, villager, context, rawPos, current);
            if (recovered != null) {
                return recovered;
            }
        }
        return null;
    }

    private HiredPathTarget immediateExcavationWorkTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos targetPos,
            BlockPos approachPos) {
        BlockPos target = targetPos.immutable();
        if (!isReachableCurrentExcavationTarget(level, context, target)
                || isUnsafeExcavationUnderfoot(level, context, target, approachPos)
                || approachPos.distSqr(target) > 4) {
            return null;
        }
        Vec3 hit = HiredMoveToBlockFaceJob.visibleHitPosition(level, villager, villager.getEyePosition(), target);
        if (hit == null) {
            return null;
        }
        HiredPathTarget pathTarget = new HiredPathTarget(target, approachPos, hit);
        if (!canStartMining(level, villager, context, pathTarget, HiredMiningMode.EXCAVATE_AREA)) {
            return null;
        }
        HiredPathMemory.clearAvoided(villager, target);
        HiredWorkPlan.prioritize(context, target, MAX_PLANNED_MINING_TARGETS);
        return pathTarget;
    }

    private boolean isReachableCurrentExcavationTarget(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        boolean neededShaftTarget = MiningExcavationSupport.isNeededLadderShaftTarget(level, context, pos);
        return context.isInsideWorkArea(pos)
                && (neededShaftTarget || MiningBlockRules.isMineableExcavationBlock(level, context, pos))
                && MiningBlockRules.isCurrentExcavationLayer(level, context, pos)
                && MiningExcavationSupport.canMineCurrentLayerTarget(level, context, pos)
                && !MiningBlockRules.hasAdjacentExcavationFluid(level, pos);
    }

    private HiredPathTarget findMineableInCurrentPocket(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        if (anchor == null) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : HiredOreBlockTracker.nearbyOreBlocks(level, anchor, MiningWorkerState.pocketRadius(context), context.verticalRadius())) {
            if (isValidMiningTarget(level, villager, context, pos, anchor)) {
                candidates.add(pos);
            }
        }
        return rebuildVeinObjective(level, villager, context, candidates, anchor);
    }

    private HiredPathTarget findRecentlyExposedMineableInRadius(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos center = context.workCenter();
        for (BlockPos pos : HiredOreBlockTracker.recentlyExposedOreBlocks(
                level,
                center,
                context.horizontalSearchRadius(),
                context.verticalRadius())) {
            if (isValidMiningTarget(level, villager, context, pos, null)) {
                candidates.add(pos);
            }
        }
        return rebuildVeinObjective(level, villager, context, candidates, center);
    }

    private HiredPathTarget findNearestMineableInRadius(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        if (level.getGameTime() < context.state().getLong(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG)) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos center = context.workCenter();
        for (BlockPos pos : HiredOreBlockTracker.nearbyOreBlocks(
                level,
                center,
                context.horizontalSearchRadius(),
                context.verticalRadius())) {
            if (isValidMiningTarget(level, villager, context, pos, null)) {
                candidates.add(pos);
            }
        }
        HiredPathTarget target = rebuildVeinObjective(level, villager, context, candidates, center);
        if (target == null) {
            context.state().putLong(
                    MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG,
                    level.getGameTime() + MiningWorkerState.noTargetScanCooldownTicks());
        } else {
            context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
        }
        return target;
    }

    private HiredPathTarget rebuildVeinObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            BlockPos origin) {
        List<BlockPos> vein = MiningVeinPlan.best(
                level,
                origin == null ? villager.blockPosition() : origin,
                candidates,
                MAX_PLANNED_MINING_TARGETS);
        if (!vein.isEmpty()) {
            HiredWorkPlan.replaceWithObjective(
                    context,
                    vein.size() > 1 ? "vein" : "ore",
                    vein.getFirst(),
                    vein,
                    MAX_PLANNED_MINING_TARGETS);
            HiredPathTarget target = plannedOreTarget(
                    level,
                    villager,
                    context,
                    pos -> isValidMiningTarget(level, villager, context, pos, MiningWorkerState.miningAnchor(level, context)),
                    MAX_PLANNED_MINING_TARGETS);
            if (target != null) {
                return target;
            }
        }

        List<BlockPos> ordered = HiredWorkPlan.routeOrder(
                origin == null ? villager.blockPosition() : origin,
                candidates,
                MAX_PLANNED_MINING_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? "ore_route" : "single_ore",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_MINING_TARGETS);
        return plannedOreTarget(
                level,
                villager,
                context,
                pos -> isValidMiningTarget(level, villager, context, pos, MiningWorkerState.miningAnchor(level, context)),
                MAX_PLANNED_MINING_TARGETS);
    }

    private boolean isValidMiningTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos pos) {
        return isValidMiningTarget(level, villager, context, pos, MiningWorkerState.miningAnchor(level, context));
    }

    private boolean isValidMiningTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos,
            BlockPos anchor,
            HiredMiningMode mode) {
        return mode.excavatesArea()
                ? isValidExcavationTarget(level, villager, context, pos)
                : isValidMiningTarget(level, villager, context, pos, anchor);
    }

    private boolean isValidMiningTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos,
            BlockPos anchor) {
        return context.isInsideWorkArea(pos)
                && isInsideMiningPocket(context, pos, anchor)
                && !isTemporarilyAvoidedTarget(level, villager, pos)
                && MiningBlockRules.isMineableOre(level, pos);
    }

    private boolean isValidExcavationTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos pos) {
        boolean neededShaftTarget = MiningExcavationSupport.isNeededLadderShaftTarget(level, context, pos);
        return context.isInsideWorkArea(pos)
                && !isTemporarilyAvoidedTarget(level, villager, pos)
                && (neededShaftTarget || MiningBlockRules.isMineableExcavationBlock(level, context, pos))
                && MiningBlockRules.isCurrentExcavationLayer(level, context, pos)
                && MiningExcavationSupport.canMineCurrentLayerTarget(level, context, pos)
                && !MiningBlockRules.hasAdjacentExcavationFluid(level, pos);
    }

    private static boolean isInsideMiningPocket(HiredWorkContext context, BlockPos pos, BlockPos anchor) {
        if (anchor == null) {
            return true;
        }
        int radius = MiningWorkerState.pocketRadius(context);
        return anchor.distSqr(pos) <= radius * radius;
    }


    private boolean canStartMining(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            HiredMiningMode mode) {
        if (mode.excavatesArea()) {
            return context.isInsideWorkArea(target.blockPos())
                    && context.isLoaded(level, target.blockPos())
                    && isValidExcavationWorkStance(level, context, target.approachPos())
                    && isValidExcavationWorkStance(level, context, villager.blockPosition())
                    && !isUnsafeExcavationUnderfoot(level, context, target.blockPos(), target.approachPos())
                    && !isUnsafeExcavationUnderfoot(level, context, target.blockPos(), villager.blockPosition())
                    && isSafeMiningWorkTarget(level, villager, target)
                    && hasLineOfSightToTarget(level, villager, target)
                    && canMineFromCurrentPosition(level, villager, target);
        }
        return canWorkFromCurrentPosition(level, villager, context, target)
                && isSafeMiningWorkTarget(level, villager, target)
                && hasLineOfSightToTarget(level, villager, target)
                && canMineFromCurrentPosition(level, villager, target);
    }

    private boolean moveToMiningTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            HiredMiningMode mode,
            double speed) {
        if (!mode.excavatesArea()) {
            return moveToTarget(level, villager, context, target, speed);
        }
        if (!context.isInsideWorkArea(target.blockPos())
                || !context.isLoaded(level, target.blockPos())
                || !isValidExcavationWorkStance(level, context, target.approachPos())) {
            return false;
        }
        BlockPos pathOrigin = villager.blockPosition().immutable();
        Predicate<BlockPos> approachFilter = pos -> isValidExcavationApproach(level, context, pos);
        Predicate<BlockPos> pathFilter = pos -> approachFilter.test(pos) || pos.equals(pathOrigin);
        boolean allowSurfaceEscape = canUseExcavationSurfaceEscape(level, context);
        if (canStartMining(level, villager, context, target, mode)) {
            holdWorkPosition(villager, target);
            return true;
        }
        if (MiningExcavationSupport.shouldUseLadderFallback(context, villager, target)
                && VillagerTaskNavigationUtil.continueLadderRoute(level, villager, target.approachPos(), speed)) {
            return true;
        }
        if (shouldEscapeUpBeforeMining(villager, target)
                && allowSurfaceEscape
                && MiningExcavationSupport.shouldUseLadderFallback(context, villager, target)
                && VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, target.approachPos(), speed)
                && observeManualNavigationProgress(level, villager, target.approachPos())) {
            return true;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            if (!currentExcavationPathIsValid(level, villager, pathFilter)) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredPathMemory.clearNavigationProgress(villager);
                return false;
            }
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()))) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredPathMemory.clearNavigationProgress(villager);
                if (MiningExcavationSupport.shouldUseLadderFallback(context, villager, target)
                        && VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target.approachPos(), speed)) {
                    return acceptExcavationLadderMovement(level, villager, target.approachPos());
                }
                if (MiningExcavationSupport.shouldUseLadderFallback(context, villager, target)
                        && allowSurfaceEscape
                        && VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, target.approachPos(), speed)
                        && observeManualNavigationProgress(level, villager, target.approachPos())) {
                    return true;
                }
                return false;
            }
            return true;
        }
        Path path = HiredPathMemory.createPath(level, villager, target.approachPos(), 0);
        if (path != null
                && path.canReach()
                && HiredMoveToBlockFaceJob.pathStaysInsideFilter(level, path, pathFilter)
                && VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target.approachPos(), speed, 0)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return true;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        if (MiningExcavationSupport.shouldUseLadderFallback(context, villager, target)) {
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target.approachPos(), speed)) {
                return acceptExcavationLadderMovement(level, villager, target.approachPos());
            }
            if (allowSurfaceEscape
                    && VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, target.approachPos(), speed)
                    && observeManualNavigationProgress(level, villager, target.approachPos())) {
                return true;
            }
            if (VillagerTaskNavigationUtil.moveOnLadderToward(level, villager, target.approachPos(), speed)) {
                return acceptExcavationLadderMovement(level, villager, target.approachPos());
            }
        }
        return false;
    }

    private static boolean shouldKeepSettlingNearExcavationTarget(
            HiredWorkContext context,
            Villager villager,
            HiredPathTarget target) {
        return target != null
                && context.isInsideWorkArea(villager.blockPosition())
                && villager.blockPosition().distSqr(target.blockPos()) <= 4;
    }

    private boolean settleNearExcavationTarget(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            HiredPathTarget target,
            double speed) {
        if (!shouldKeepSettlingNearExcavationTarget(context, villager, target)
                || !isValidExcavationWorkStance(level, context, target.approachPos())) {
            return false;
        }
        if (!settleIntoApproach(villager, target, speed)) {
            return false;
        }
        HiredPathMemory.rememberNavigationProgress(
                level,
                villager,
                target.approachPos(),
                villager.distanceToSqr(target.approachPos().getCenter()));
        return true;
    }

    private boolean settleIntoClearedExcavationFloor(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            double speed) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null) {
            return false;
        }
        BlockPos origin = villager.blockPosition().immutable();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos rawPos : BlockPos.betweenClosed(origin.offset(-4, -2, -4), origin.offset(4, 1, 4))) {
            BlockPos candidate = rawPos.immutable();
            if (candidate.getY() != currentLayerY
                    || !context.isInsideWorkArea(candidate)
                    || !isValidExcavationWorkStance(level, context, candidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)
                    || !hasAdjacentCurrentLayerExcavationTarget(level, villager, context, candidate)) {
                continue;
            }
            candidates.add(candidate);
        }
        if (candidates.isEmpty()) {
            return false;
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        Predicate<BlockPos> pathFilter = pos -> pos.equals(origin) || isValidExcavationApproach(level, context, pos);
        for (BlockPos candidate : candidates) {
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
            if (path != null
                    && path.canReach()
                    && HiredMoveToBlockFaceJob.pathStaysInsideFilter(level, path, pathFilter)
                    && VillagerTaskNavigationUtil.moveToHiredPath(villager, path, candidate, speed, 0)) {
                HiredPathMemory.rememberNavigationProgress(level, villager, candidate, villager.distanceToSqr(candidate.getCenter()));
                return true;
            }
            if (villager.distanceToSqr(candidate.getCenter()) <= 9.0D) {
                villager.getNavigation().stop();
                villager.getMoveControl().setWantedPosition(
                        candidate.getX() + 0.5D,
                        candidate.getY(),
                        candidate.getZ() + 0.5D,
                        speed);
                HiredPathMemory.rememberNavigationProgress(level, villager, candidate, villager.distanceToSqr(candidate.getCenter()));
                return true;
            }
        }
        return false;
    }

    private boolean hasAdjacentCurrentLayerExcavationTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos stance) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos target = stance.relative(direction);
            if (isValidExcavationTarget(level, villager, context, target)) {
                return true;
            }
        }
        BlockPos below = stance.below();
        return isValidExcavationTarget(level, villager, context, below);
    }

    private static boolean shouldEscapeUpBeforeMining(Villager villager, HiredPathTarget target) {
        return target != null && target.approachPos().getY() - villager.blockPosition().getY() > 2;
    }

    private static boolean canUseExcavationSurfaceEscape(ServerLevel level, HiredWorkContext context) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        return currentLayerY != null && currentLayerY >= context.workMax().getY();
    }

    private static boolean shouldUseExcavationDescentReturnTarget(
            HiredWorkContext context,
            Villager villager,
            BlockPos ladderEntryTarget,
            BlockPos descentTarget) {
        if (ladderEntryTarget == null || descentTarget == null) {
            return false;
        }
        if (isAtExcavationReturnTarget(context, villager, ladderEntryTarget)) {
            return true;
        }
        BlockPos pos = villager.blockPosition();
        return pos.getX() == ladderEntryTarget.getX()
                && pos.getZ() == ladderEntryTarget.getZ()
                && pos.getX() == descentTarget.getX()
                && pos.getZ() == descentTarget.getZ()
                && pos.getY() <= ladderEntryTarget.getY()
                && pos.getY() >= descentTarget.getY();
    }

    private BlockPos miningReturnTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredMiningMode mode) {
        if (mode.excavatesArea()) {
            BlockPos surfaceTarget = excavationReturnTarget(level, villager, context);
            BlockPos ladderEntryTarget = MiningExcavationSupport.entryTarget(level, context);
            BlockPos descentTarget = MiningExcavationSupport.currentLayerDescentTarget(level, context);
            return shouldUseExcavationDescentReturnTarget(context, villager, ladderEntryTarget, descentTarget)
                    ? descentTarget
                    : surfaceTarget;
        }
        BlockPos preferred = exposedMiningReturnPreference(level, context);
        List<ReturnCandidate> candidates = new ArrayList<>();
        HiredPathTarget stored = storedWorkTarget(context.state());
        if (stored != null) {
            addReturnCandidate(level, context, candidates, stored.approachPos(), preferred);
        }
        for (BlockPos rawPos : context.workAreaPositions()) {
            addReturnCandidate(level, context, candidates, rawPos.immutable(), preferred);
        }
        if (candidates.isEmpty()) {
            return context.isLoaded(level, context.workCenter()) ? context.workCenter() : null;
        }
        candidates.sort(Comparator.comparingDouble(ReturnCandidate::score));

        int attempts = 0;
        for (ReturnCandidate candidate : candidates) {
            if (attempts++ >= MAX_WORK_AREA_RETURN_PATH_ATTEMPTS) {
                break;
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate.pos(), 0);
            if (path != null && path.canReach()) {
                return candidate.pos();
            }
        }
        return candidates.get(0).pos();
    }

    private BlockPos exposedMiningReturnPreference(ServerLevel level, HiredWorkContext context) {
        HiredPathTarget stored = storedWorkTarget(context.state());
        if (stored != null && context.isInsideWorkArea(stored.approachPos())) {
            return stored.approachPos();
        }
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        if (anchor != null) {
            return anchor;
        }
        BlockPos lastMined = MiningWorkerState.lastMinedBlock(context);
        return lastMined != null ? lastMined : context.workCenter();
    }

    private static void addReturnCandidate(
            ServerLevel level,
            HiredWorkContext context,
            List<ReturnCandidate> candidates,
            BlockPos candidate,
            BlockPos preferred) {
        if (candidate == null
                || !context.isInsideWorkArea(candidate)
                || !context.isLoaded(level, candidate)
                || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)) {
            return;
        }
        for (ReturnCandidate existing : candidates) {
            if (existing.pos().equals(candidate)) {
                return;
            }
        }
        double preferenceCost = preferred == null ? 0.0D : candidate.distSqr(preferred) * 0.25D;
        candidates.add(new ReturnCandidate(
                candidate,
                preferenceCost + candidate.distSqr(context.workCenter()) * 0.05D + HiredMoveToBlockFaceJob.terrainCost(level, candidate)));
    }

    private static boolean observeManualNavigationProgress(ServerLevel level, Villager villager, BlockPos target) {
        if (HiredPathMemory.observeNavigationProgress(level, villager, target, villager.distanceToSqr(target.getCenter()))) {
            return true;
        }
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private static boolean acceptExcavationLadderMovement(ServerLevel level, Villager villager, BlockPos target) {
        HiredPathMemory.rememberNavigationProgress(level, villager, target, villager.distanceToSqr(target.getCenter()));
        return true;
    }

    private static boolean moveDirectlyTowardNearbyReturnTarget(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            double speed) {
        if (target == null
                || villager.distanceToSqr(target.getCenter()) > 16.0D
                || !isValidNearbyReturnPosition(level, target)) {
            return false;
        }
        Vec3 center = target.getCenter();
        villager.getMoveControl().setWantedPosition(center.x, target.getY(), center.z, speed);
        VillagerTaskNavigationUtil.setHiredWalkTarget(villager, target, speed, 0);
        return true;
    }

    private static boolean isValidNearbyReturnPosition(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return (feet.isAir() || feet.is(Blocks.LADDER))
                && (head.isAir() || head.is(Blocks.LADDER))
                && (floor.isSolid() || floor.is(Blocks.LADDER) || feet.is(Blocks.LADDER));
    }

    private record ReturnCandidate(BlockPos pos, double score) {
    }

    private static boolean currentExcavationPathIsValid(
            ServerLevel level,
            Villager villager,
            Predicate<BlockPos> pathFilter) {
        Path path = villager.getNavigation().getPath();
        return path == null || HiredMoveToBlockFaceJob.pathStaysInsideFilter(level, path, pathFilter);
    }

    private boolean isValidExcavationApproach(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        if (pos == null || !context.isLoaded(level, pos)) {
            return false;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null) {
            return context.isInsideWorkArea(pos);
        }
        if (currentLayerY >= context.workMax().getY()) {
            return context.isInsideWorkArea(pos) || isTopExcavationEntryPosition(context, pos);
        }
        return context.isInsideWorkArea(pos);
    }

    private boolean isValidExcavationWorkStance(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        if (!isValidExcavationApproach(level, context, pos)) {
            return false;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null || currentLayerY >= context.workMax().getY()) {
            return true;
        }
        return context.isInsideWorkArea(pos)
                && pos.getY() >= currentLayerY
                && pos.getY() <= currentLayerY + 1;
    }

    private static boolean isUsableExcavationApproachForCurrentLayer(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos current,
            BlockPos approach) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null || currentLayerY < context.workMax().getY()) {
            return true;
        }
        return isTopExcavationEntryPosition(context, approach) || approach.equals(current);
    }

    private static boolean isTopExcavationEntryPosition(HiredWorkContext context, BlockPos pos) {
        return pos.getY() == context.workMax().getY() + 1
                && pos.getX() >= context.workMin().getX() - 1
                && pos.getX() <= context.workMax().getX() + 1
                && pos.getZ() >= context.workMin().getZ() - 1
                && pos.getZ() <= context.workMax().getZ() + 1;
    }

    private static boolean isSafeMiningWorkTarget(ServerLevel level, Villager villager, HiredPathTarget target) {
        return !MiningSafety.isUnsafeMiningTarget(
                level,
                villager,
                target.blockPos(),
                target.approachPos());
    }

    private static boolean isUnsafeUnderfootMiningTarget(
            ServerLevel level,
            BlockPos target,
            BlockPos stance) {
        return MiningSafety.isUnsafeUnderfootTarget(level, target, stance);
    }

    private static boolean isUnsafeExcavationUnderfoot(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos target,
            BlockPos stance) {
        if (isUnsafeUnderfootMiningTarget(level, target, stance)) {
            return true;
        }
        return target != null
                && stance != null
                && stance.getX() == target.getX()
                && stance.getZ() == target.getZ()
                && stance.getY() == target.getY() + 1
                && MiningHazardManager.isPermanentBarrier(context, target.below());
    }

    private boolean storeMinedDrops(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            HiredPathTarget target,
            ItemStack tool,
            HiredMiningMode mode) {
        if (!mode.excavatesArea()) {
            if (!canStartMining(level, villager, context, target, mode)) {
                return false;
            }
            return storeDrops(level, context, villager, target, tool);
        }
        if (!context.isInsideWorkArea(target.blockPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())
                || !canStartMining(level, villager, context, target, mode)) {
            return false;
        }
        BlockState state = level.getBlockState(target.blockPos());
        List<ItemStack> drops = Block.getDrops(state, level, target.blockPos(), level.getBlockEntity(target.blockPos()), villager, tool);
        if (!context.canStoreOutputs(drops)) {
            return false;
        }
        for (ItemStack drop : drops) {
            if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                return false;
            }
        }
        faceBlock(villager, target);
        swingWorkTool(villager);
        EnchantmentHelper.onHitBlock(level, tool, villager, villager, EquipmentSlot.MAINHAND, target.hitPos(), state, ignored -> {
        });
        level.destroyBlock(target.blockPos(), false, villager);
        HiredPathMemory.onBlockChanged(level, target.blockPos());
        level.destroyBlockProgress(villager.getId(), target.blockPos(), -1);
        damageTool(context, villager, tool, level, state, target.blockPos());
        HiredPathMemory.rememberRecent(level, target.blockPos());
        return true;
    }

    private boolean hasLineOfSightToTarget(ServerLevel level, Villager villager, HiredPathTarget target) {
        return hasLineOfSightToBlock(level, villager, villager.getEyePosition(), target.blockPos(), target.hitPos());
    }

    private int actualBreakProgressGoal(ServerLevel level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0.0F) {
            return 1;
        }
        float speed = Math.max(0.001F, effectiveDestroySpeed(tool, state));
        int divisor = tool.isCorrectToolForDrops(state) ? 30 : 100;
        return Math.max(1, (int) Math.ceil(hardness * divisor / speed));
    }

    private static boolean isExcavationComplete(ServerLevel level, HiredWorkContext context, HiredMiningMode mode) {
        return mode.excavatesArea()
                && !HiredWorkAreaScan.isInProgress(context, MiningWorkerState.EXCAVATION_SCAN_CURSOR_TAG)
                && level.getGameTime() < context.state().getLong(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG)
                && MiningBlockRules.currentExcavationLayer(level, context) == null;
    }

    private static String miningStatusKey(HiredMiningMode mode, String status) {
        return "interaction.work.mining." + status + "." + (mode.excavatesArea() ? "excavate_area" : "exposed_ores");
    }

    private static String excavationResultStatusKey(DepositResult depositResult, String status) {
        return "interaction.work.mining." + status + "."
                + (depositResult == DepositResult.DEPOSITED ? "deposited" : "default");
    }

}
