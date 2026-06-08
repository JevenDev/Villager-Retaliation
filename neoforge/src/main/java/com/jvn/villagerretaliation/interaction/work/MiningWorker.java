package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class MiningWorker extends AbstractBlockWorker {
    private static final String MINING_STATE_TAG = "MiningState";
    private static final String LAST_MINED_BLOCK_POS_TAG = "LastMinedBlockPos";
    private static final String MINING_ANCHOR_POS_TAG = "MiningAnchorPos";
    private static final String MINING_ANCHOR_EXPIRES_GAME_TIME_TAG = "MiningAnchorExpiresGameTime";
    private static final String NEXT_FULL_SCAN_GAME_TIME_TAG = "NextMiningFullScanGameTime";
    private static final String EXCAVATION_SCAN_CURSOR_TAG = "MiningExcavationScanCursor";
    private static final String LAST_BREAK_PROGRESS_GAME_TIME_TAG = "LastMiningBreakProgressGameTime";
    private static final String EXCAVATION_LADDER_X_TAG = "ExcavationLadderX";
    private static final String EXCAVATION_LADDER_Z_TAG = "ExcavationLadderZ";
    private static final String EXCAVATION_LADDER_FACING_TAG = "ExcavationLadderFacing";
    private static final int MINING_POCKET_RADIUS = 6;
    private static final long MINING_ANCHOR_TICKS = 20L * 90L;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;
    private static final int MAX_PLANNED_MINING_TARGETS = 20;
    private static final int MAX_EXCAVATION_SCAN_POSITIONS = 768;
    private static final int EXCAVATION_TORCH_LAYER_INTERVAL = 5;
    private static final HiredTargetSearch.Messages EXCAVATION_SEARCH_MESSAGES = new HiredTargetSearch.Messages(
            "active_excavation_target",
            "planned_excavation_target",
            "excavation_scan_cooldown",
            "no_targets",
            "excavation_scan_partial_",
            "excavation_targets_found",
            NO_TARGET_SCAN_COOLDOWN_TICKS);

    private enum HorizontalAxis {
        X,
        Z
    }

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.MINING;
    }

    public static void clearRuntimeState() {
        clearSharedRuntimeState();
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        expireWorkPathMemory(level);
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }

        HiredMiningMode mode = HiredMiningMode.fromState(context.state());
        WorkResult fullInventoryResult = depositFullInventoryBeforeMining(level, villager, context);
        if (fullInventoryResult != null) {
            return fullInventoryResult;
        }
        if (mode.excavatesArea()) {
            WorkResult supportResult = maintainExcavationSupports(level, villager, context, deepestOpenSupportY(level, context));
            if (supportResult != null) {
                return supportResult;
            }
        }
        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        HiredPathTarget target = resolveTarget(level, villager, context, mode);
        if (target == null) {
            clearActiveBreakingTarget(level, context, villager);
            if (isExcavationScanInProgress(context, mode)) {
                setMiningState(context, MiningState.FIND_TARGET);
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("I am still checking the excavation area for reachable blocks.");
            }
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
            setMiningState(context, depositResult == DepositResult.DEPOSITED || depositResult == DepositResult.MOVING
                    ? MiningState.DEPOSIT_OUTPUT
                    : MiningState.WAITING_NO_TARGETS);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed(noTargetDepositStatus(mode));
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            if (depositResult != DepositResult.UNAVAILABLE && isExcavationComplete(level, context, mode)) {
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
                return WorkResult.completed(completedExcavationStatus(depositResult));
            }
            if (roamInsideWorkArea(level, villager, context, 0.4D)) {
                return WorkResult.progressed(searchingStatus(mode));
            }
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            ensureNoTargetScanCooldown(level, context);
            return WorkResult.idle(depositResult == DepositResult.DEPOSITED
                    ? depositedAndWaitingStatus(mode)
                    : noTargetsStatus(mode));
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        ItemStack tool = context.inventory().equipBestTool(
                stack -> isUsableMiningTool(mode, stack, targetState),
                stack -> effectiveDestroySpeed(stack, targetState));
        if (tool.isEmpty()) {
            HiredWorkPlan.clear(context);
            clearActiveBreakingTarget(level, context, villager);
            setMiningState(context, MiningState.BLOCKED_MISSING_TOOL);
            HiredWorkerBrain.setFailure(context, "missing_pickaxe", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return WorkResult.idle(missingToolStatus(mode));
        }
        WorkResult outputCapacityResult = depositBeforeMiningIfTargetDropsDoNotFit(level, villager, context, target, targetState, tool);
        if (outputCapacityResult != null) {
            return outputCapacityResult;
        }

        prepareBreakingTarget(level, context, villager, target);
        if (!canStartMining(level, villager, context, target, mode)) {
            context.setProgressTicks(0);
            setMiningState(context, MiningState.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            boolean closeEnough = isCloseEnough(villager, target);
            boolean hasLineOfSight = hasLineOfSightToTarget(level, villager, target);
            if (!moveToMiningTarget(level, villager, context, target, mode, 0.55D)) {
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
                    return WorkResult.idle(blockedTargetStatus(mode));
                }
                return WorkResult.progressed(repositioningStatus(mode));
            }
            return WorkResult.progressed(closeEnough && !hasLineOfSight
                    ? blockedSwingStatus(mode)
                    : movingToTargetStatus(mode));
        }

        clearWorkPathFailure(villager, target.blockPos());
        holdMiningPosition(villager, target);
        setMiningState(context, MiningState.MINE_TARGET);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, target.blockPos());

        int needed = actualBreakProgressGoal(level, target.blockPos(), tool);
        int progress = context.progressTicks() + elapsedBreakProgressTicks(level, context);
        if (progress < needed) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            showBreakProgress(level, villager, target.blockPos(), progress, needed);
            return WorkResult.progressed(workingTargetStatus(mode));
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
                setMiningState(context, MiningState.DEPOSIT_OUTPUT);
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("My packs are full, so I am taking the mined output to storage before I continue.");
            }
        }
        if (!context.canStoreOutputs(drops)) {
            context.setProgressTicks(0);
            setMiningState(context, MiningState.BLOCKED_OUTPUT_FULL);
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("I cannot carry more ore, and I have nowhere to stow it.");
        }

        context.setProgressTicks(0);
        if (!storeMinedDrops(level, context, villager, target, tool, mode)) {
            setMiningState(context, MiningState.BLOCKED_OUTPUT_FULL);
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("I cannot carry more ore, and I have nowhere to stow it.");
        }
        HiredOreBlockTracker.onBlockBroken(level, target.blockPos());
        HiredWorkPlan.removeTarget(context, target.blockPos());

        rememberLastMined(context, target.blockPos());
        if (!mode.excavatesArea()) {
            rememberMiningAnchor(level, context, target.blockPos());
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
                rememberMiningAnchor(level, context, nextTarget.blockPos());
                HiredWorkPlan.prioritize(context, nextTarget.blockPos(), MAX_PLANNED_MINING_TARGETS);
            }
            prepareBreakingTarget(level, context, villager, nextTarget);
            setMiningState(context, MiningState.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, nextTarget.blockPos());
            return WorkResult.skilledProgress(nextTargetStatus(mode));
        }

        clearMiningAnchor(context);
        if (isExcavationScanInProgress(context, mode)) {
            setMiningState(context, MiningState.FIND_TARGET);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.skilledProgress("I cleared that block and am checking the assigned area for the next reachable face.");
        }
        DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
        setMiningState(context, depositResult == DepositResult.DEPOSITED || depositResult == DepositResult.MOVING
                ? MiningState.DEPOSIT_OUTPUT
                : MiningState.WAITING_NO_TARGETS);
        if (depositResult == DepositResult.MOVING) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.skilledProgress("I have gathered the mined output and am taking it to storage.");
        }
        if (depositResult == DepositResult.STORAGE_FULL) {
            return WorkResult.idle(storageFullStatus(context));
        }
        if (depositResult != DepositResult.UNAVAILABLE && isExcavationComplete(level, context, mode)) {
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            ensureNoTargetScanCooldown(level, context);
            return WorkResult.completed(completedExcavationStatus(depositResult));
        }
        if (roamInsideWorkArea(level, villager, context, 0.4D)) {
            return WorkResult.skilledProgress(completedSearchingStatus(mode));
        }
        if (mode.excavatesArea()) {
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.skilledProgress(continuingExcavationStatus(depositResult));
        }
        setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
        ensureNoTargetScanCooldown(level, context);
        return WorkResult.completed(depositResult == DepositResult.DEPOSITED
                ? completedDepositedStatus(mode)
                : completedNoNextTargetStatus(mode));
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
            ItemStack tool) {
        List<ItemStack> drops = Block.getDrops(
                targetState,
                level,
                target.blockPos(),
                level.getBlockEntity(target.blockPos()),
                villager,
                tool);
        if (context.canStoreOutputs(drops)) {
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
            setMiningState(context, MiningState.DEPOSIT_OUTPUT);
            return WorkResult.progressed("I put away my mined output and am checking my packs before I continue.");
        }
        if (depositResult == DepositResult.MOVING) {
            setMiningState(context, MiningState.DEPOSIT_OUTPUT);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("My packs are full, so I am taking the mined output to storage before I continue.");
        }
        if (depositResult == DepositResult.STORAGE_FULL) {
            setMiningState(context, MiningState.BLOCKED_OUTPUT_FULL);
            return WorkResult.idle(storageFullStatus(context));
        }
        setMiningState(context, MiningState.BLOCKED_OUTPUT_FULL);
        HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
        setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
        return WorkResult.idle("I cannot carry more ore, and I have nowhere to stow it.");
    }

    private HiredPathTarget resolveOreTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        BlockPos anchor = miningAnchor(level, context);
        if (active != null && isValidMiningTarget(level, villager, context, active.blockPos(), anchor)) {
            rememberMiningAnchor(level, context, active.blockPos());
            return active;
        }
        if (storedWorkTarget(context.state()) != null) {
            clearActiveBreakingTarget(level, context, villager);
        }

        HiredPathTarget planned = plannedTarget(
                level,
                villager,
                context,
                pos -> isValidMiningTarget(level, villager, context, pos, miningAnchor(level, context)),
                MAX_PLANNED_MINING_TARGETS);
        if (planned != null) {
            rememberMiningAnchor(level, context, planned.blockPos());
            setMiningState(context, MiningState.FIND_TARGET);
            return planned;
        }

        BlockPos lastMined = lastMinedBlock(context);
        if (lastMined != null) {
            HiredPathTarget adjacent = findAdjacentMineable(level, villager, context, lastMined, HiredMiningMode.EXPOSED_ORES);
            if (adjacent != null) {
                rememberMiningAnchor(level, context, adjacent.blockPos());
                setMiningState(context, MiningState.FIND_TARGET);
                return adjacent;
            }
        }

        HiredPathTarget pocketTarget = findMineableInCurrentPocket(level, villager, context);
        if (pocketTarget != null) {
            rememberMiningAnchor(level, context, pocketTarget.blockPos());
            setMiningState(context, MiningState.FIND_TARGET);
            return pocketTarget;
        }
        clearMiningAnchor(context);

        HiredPathTarget recentlyExposedTarget = findRecentlyExposedMineableInRadius(level, villager, context);
        if (recentlyExposedTarget != null) {
            rememberMiningAnchor(level, context, recentlyExposedTarget.blockPos());
            context.state().remove(NEXT_FULL_SCAN_GAME_TIME_TAG);
            setMiningState(context, MiningState.FIND_TARGET);
            return recentlyExposedTarget;
        }

        if (level.getGameTime() < context.state().getLong(NEXT_FULL_SCAN_GAME_TIME_TAG)) {
            return null;
        }
        setMiningState(context, MiningState.FIND_TARGET);
        HiredPathTarget target = findNearestMineableInRadius(level, villager, context, false);
        if (target != null) {
            rememberMiningAnchor(level, context, target.blockPos());
        }
        return target;
    }

    private HiredPathTarget resolveExcavationTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
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
                        setMiningState(context, MiningState.FIND_TARGET);
                    }
                    return planned;
                },
                pos -> isValidExcavationTarget(level, villager, context, pos),
                NEXT_FULL_SCAN_GAME_TIME_TAG,
                EXCAVATION_SCAN_CURSOR_TAG,
                MAX_EXCAVATION_SCAN_POSITIONS,
                candidates -> {
                    setMiningState(context, MiningState.FIND_TARGET);
                    return rebuildExcavationObjective(level, villager, context, candidates);
                },
                EXCAVATION_SEARCH_MESSAGES);
    }

    private HiredPathTarget findAdjacentMineable(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos origin,
            HiredMiningMode mode) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos anchor = miningAnchor(level, context);
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
                ? chooseReachableTarget(level, villager, candidates)
                : chooseReachableTarget(level, villager, context, candidates);
    }

    private HiredPathTarget findNearestExcavationTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                context,
                EXCAVATION_SCAN_CURSOR_TAG,
                MAX_EXCAVATION_SCAN_POSITIONS,
                pos -> isValidExcavationTarget(level, villager, context, pos));
        if (!scan.candidates().isEmpty()) {
            context.state().remove(NEXT_FULL_SCAN_GAME_TIME_TAG);
            HiredWorkerBrain.setLastTargetScanResult(context, "excavation_targets_found");
            return rebuildExcavationObjective(level, villager, context, scan.candidates());
        }
        if (!scan.completedFullPass()) {
            HiredWorkerBrain.setLastTargetScanResult(context, "excavation_scan_in_progress");
            return null;
        }
        context.state().putLong(NEXT_FULL_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
        HiredWorkerBrain.setLastTargetScanResult(context, "no_targets");
        return null;
    }

    private HiredPathTarget rebuildExcavationObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates) {
        List<BlockPos> ordered = excavationLineOrder(villager, context, candidates);
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

    private static List<BlockPos> excavationLineOrder(Villager villager, HiredWorkContext context, List<BlockPos> candidates) {
        List<BlockPos> ordered = new ArrayList<>();
        for (BlockPos candidate : candidates) {
            if (candidate != null) {
                ordered.add(candidate.immutable());
            }
        }
        HorizontalAxis depthAxis = excavationDepthAxis(villager.blockPosition(), context);
        HorizontalAxis lineAxis = depthAxis == HorizontalAxis.X ? HorizontalAxis.Z : HorizontalAxis.X;
        boolean depthFromMin = startsFromMin(villager.blockPosition(), context, depthAxis);
        boolean lineFromMin = startsFromMin(villager.blockPosition(), context, lineAxis);
        ordered.sort((left, right) -> compareExcavationLineOrder(
                left,
                right,
                context,
                depthAxis,
                lineAxis,
                depthFromMin,
                lineFromMin,
                villager.blockPosition()));
        if (ordered.size() > MAX_PLANNED_MINING_TARGETS) {
            return new ArrayList<>(ordered.subList(0, MAX_PLANNED_MINING_TARGETS));
        }
        return ordered;
    }

    private static int compareExcavationLineOrder(
            BlockPos left,
            BlockPos right,
            HiredWorkContext context,
            HorizontalAxis depthAxis,
            HorizontalAxis lineAxis,
            boolean depthFromMin,
            boolean lineFromMin,
            BlockPos villagerPos) {
        int result = Integer.compare(context.workMax().getY() - left.getY(), context.workMax().getY() - right.getY());
        if (result != 0) {
            return result;
        }

        int leftDepth = orderedAxisCoordinate(left, context, depthAxis, depthFromMin);
        int rightDepth = orderedAxisCoordinate(right, context, depthAxis, depthFromMin);
        result = Integer.compare(leftDepth, rightDepth);
        if (result != 0) {
            return result;
        }

        boolean lineDirection = (leftDepth & 1) == 0 ? lineFromMin : !lineFromMin;
        int leftLine = orderedAxisCoordinate(left, context, lineAxis, lineDirection);
        int rightLine = orderedAxisCoordinate(right, context, lineAxis, lineDirection);
        result = Integer.compare(leftLine, rightLine);
        if (result != 0) {
            return result;
        }

        return Double.compare(left.distSqr(villagerPos), right.distSqr(villagerPos));
    }

    private static HorizontalAxis excavationDepthAxis(BlockPos villagerPos, HiredWorkContext context) {
        int xOutside = distanceOutside(villagerPos.getX(), context.workMin().getX(), context.workMax().getX());
        int zOutside = distanceOutside(villagerPos.getZ(), context.workMin().getZ(), context.workMax().getZ());
        if (xOutside != zOutside) {
            return xOutside > zOutside ? HorizontalAxis.X : HorizontalAxis.Z;
        }
        int sizeX = context.workMax().getX() - context.workMin().getX();
        int sizeZ = context.workMax().getZ() - context.workMin().getZ();
        return sizeZ >= sizeX ? HorizontalAxis.Z : HorizontalAxis.X;
    }

    private static boolean startsFromMin(BlockPos villagerPos, HiredWorkContext context, HorizontalAxis axis) {
        int min = axis == HorizontalAxis.X ? context.workMin().getX() : context.workMin().getZ();
        int max = axis == HorizontalAxis.X ? context.workMax().getX() : context.workMax().getZ();
        int value = axis == HorizontalAxis.X ? villagerPos.getX() : villagerPos.getZ();
        return value <= min + (max - min) / 2;
    }

    private static int orderedAxisCoordinate(BlockPos pos, HiredWorkContext context, HorizontalAxis axis, boolean fromMin) {
        int min = axis == HorizontalAxis.X ? context.workMin().getX() : context.workMin().getZ();
        int max = axis == HorizontalAxis.X ? context.workMax().getX() : context.workMax().getZ();
        int value = axis == HorizontalAxis.X ? pos.getX() : pos.getZ();
        return fromMin ? value - min : max - value;
    }

    private static int distanceOutside(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    private WorkResult maintainExcavationSupports(ServerLevel level, Villager villager, HiredWorkContext context, int currentY) {
        int lowestOpenY = Math.clamp(currentY, context.workMin().getY(), context.workMax().getY());
        SupportPlacement placement = nextSupportPlacement(level, villager, context, lowestOpenY);
        if (placement == null) {
            return null;
        }
        HiredPathTarget target = supportPlacementTarget(level, villager, context, placement);
        if (target == null) {
            return null;
        }
        if (!moveToSupportTarget(level, villager, context, target, 0.55D)) {
            setMiningState(context, MiningState.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, placement.pos());
            return WorkResult.progressed("I am moving into position to place excavation supports.");
        }
        if (!placeSupportPlacement(level, villager, context, placement)) {
            return null;
        }
        return WorkResult.progressed(placement.type().placedStatus());
    }

    private static int deepestOpenSupportY(ServerLevel level, HiredWorkContext context) {
        int lowestOpenY = context.workMax().getY();
        LadderShaft stored = storedLadderShaft(context);
        Iterable<LadderShaft> candidates = stored == null ? ladderShaftCandidates(context) : List.of(stored);
        for (LadderShaft candidate : candidates) {
            for (int y = context.workMax().getY(); y >= context.workMin().getY(); y--) {
                BlockPos pos = new BlockPos(candidate.x(), y, candidate.z());
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(Blocks.LADDER)) {
                    lowestOpenY = Math.min(lowestOpenY, y);
                }
            }
        }
        return lowestOpenY;
    }

    private static SupportPlacement nextSupportPlacement(ServerLevel level, Villager villager, HiredWorkContext context, int lowestOpenY) {
        SupportPlacement ladder = nextLadderPlacement(level, context, lowestOpenY);
        if (ladder != null) {
            return ladder;
        }
        return nextTorchPlacement(level, context, lowestOpenY);
    }

    private static SupportPlacement nextLadderPlacement(ServerLevel level, HiredWorkContext context, int lowestOpenY) {
        LadderShaft shaft = ladderShaft(level, context, lowestOpenY);
        if (shaft == null) {
            return null;
        }
        for (int y = context.workMax().getY(); y >= lowestOpenY; y--) {
            BlockPos pos = new BlockPos(shaft.x(), y, shaft.z());
            BlockState current = level.getBlockState(pos);
            if (current.is(Blocks.LADDER)) {
                continue;
            }
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, shaft.facing());
            if (hasSupportSupply(context, SupportType.LADDER)
                    && (canPlaceSupportBlock(level, pos, ladder) || canPrepareSupportBacking(level, context, pos, ladder))) {
                return new SupportPlacement(pos, ladder, SupportType.LADDER);
            }
        }
        return null;
    }

    private static LadderShaft ladderShaft(ServerLevel level, HiredWorkContext context, int lowestOpenY) {
        LadderShaft stored = storedLadderShaft(context);
        if (stored != null) {
            return stored;
        }
        for (LadderShaft candidate : ladderShaftCandidates(context)) {
            for (int y = context.workMax().getY(); y >= lowestOpenY; y--) {
                BlockPos pos = new BlockPos(candidate.x(), y, candidate.z());
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, candidate.facing());
                if (level.getBlockState(pos).is(Blocks.LADDER)
                        || canPlaceSupportBlock(level, pos, ladder)
                        || canPrepareSupportBacking(level, context, pos, ladder)) {
                    storeLadderShaft(context, candidate);
                    return candidate;
                }
            }
        }
        return null;
    }

    private static LadderShaft storedLadderShaft(HiredWorkContext context) {
        if (!context.state().contains(EXCAVATION_LADDER_X_TAG)
                || !context.state().contains(EXCAVATION_LADDER_Z_TAG)
                || !context.state().contains(EXCAVATION_LADDER_FACING_TAG)) {
            return null;
        }
        int x = context.state().getInt(EXCAVATION_LADDER_X_TAG);
        int z = context.state().getInt(EXCAVATION_LADDER_Z_TAG);
        Direction facing = Direction.byName(context.state().getString(EXCAVATION_LADDER_FACING_TAG));
        if (facing == null || facing.getAxis().isVertical()) {
            return null;
        }
        BlockPos pos = new BlockPos(x, context.workMax().getY(), z);
        return context.isInsideWorkArea(pos) ? new LadderShaft(x, z, facing) : null;
    }

    private static void storeLadderShaft(HiredWorkContext context, LadderShaft shaft) {
        context.state().putInt(EXCAVATION_LADDER_X_TAG, shaft.x());
        context.state().putInt(EXCAVATION_LADDER_Z_TAG, shaft.z());
        context.state().putString(EXCAVATION_LADDER_FACING_TAG, shaft.facing().getName());
    }

    private static List<LadderShaft> ladderShaftCandidates(HiredWorkContext context) {
        int minX = context.workMin().getX();
        int maxX = context.workMax().getX();
        int minZ = context.workMin().getZ();
        int maxZ = context.workMax().getZ();
        List<LadderShaft> candidates = new ArrayList<>();
        candidates.add(new LadderShaft(minX, minZ, Direction.SOUTH));
        candidates.add(new LadderShaft(minX, minZ, Direction.EAST));
        candidates.add(new LadderShaft(maxX, minZ, Direction.SOUTH));
        candidates.add(new LadderShaft(maxX, minZ, Direction.WEST));
        candidates.add(new LadderShaft(maxX, maxZ, Direction.NORTH));
        candidates.add(new LadderShaft(maxX, maxZ, Direction.WEST));
        candidates.add(new LadderShaft(minX, maxZ, Direction.NORTH));
        candidates.add(new LadderShaft(minX, maxZ, Direction.EAST));
        return candidates;
    }

    private static SupportPlacement nextTorchPlacement(ServerLevel level, HiredWorkContext context, int lowestOpenY) {
        for (int y = context.workMax().getY(); y >= lowestOpenY; y--) {
            if (!isTorchLayer(context, y)) {
                continue;
            }
            for (TorchPlacement placement : torchPlacements(context, y)) {
                if (isLadderShaft(context, placement.pos())) {
                    continue;
                }
                BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, placement.facing());
                if (hasSupportSupply(context, SupportType.TORCH)
                        && (canPlaceSupportBlock(level, placement.pos(), torch)
                        || canPrepareSupportBacking(level, context, placement.pos(), torch))) {
                    return new SupportPlacement(placement.pos(), torch, SupportType.TORCH);
                }
            }
        }
        return null;
    }

    private boolean moveToSupportTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            double speed) {
        if (canReachSupportPlacement(level, villager, target.blockPos())) {
            holdMiningPosition(villager, target);
            return true;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            return false;
        }
        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
        if (path != null && path.canReach() && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
            villager.getNavigation().moveTo(path, speed);
            return false;
        }
        VillagerTaskNavigationUtil.moveOnLadderToward(level, villager, target.approachPos(), speed);
        return false;
    }

    private static boolean hasSupportSupply(HiredWorkContext context, SupportType type) {
        return !context.inventory().findSupply(type::matchesSupply).isEmpty()
                || context.useAssignedStorageForSupplies();
    }

    private static HiredPathTarget supportPlacementTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            SupportPlacement placement) {
        Vec3 hitPos = placement.pos().getCenter();
        if (canReachSupportPlacement(level, villager, placement.pos())) {
            return new HiredPathTarget(placement.pos(), villager.blockPosition().immutable(), hitPos);
        }

        BlockPos bestApproach = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos rawCandidate : BlockPos.betweenClosed(
                placement.pos().offset(-1, -1, -1),
                placement.pos().offset(1, 1, 1))) {
            BlockPos approach = rawCandidate.immutable();
            if (!context.isInsideWorkArea(approach)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, approach)
                    || approach.getCenter().distanceToSqr(hitPos) > HiredMoveToBlockFaceJob.MAX_REACH_SQR) {
                continue;
            }
            Path path = villager.getNavigation().createPath(approach, 0);
            if (path == null || !path.canReach() || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
                continue;
            }
            double score = villager.distanceToSqr(approach.getCenter())
                    + Math.abs(approach.getY() - villager.blockPosition().getY()) * 3.0D
                    + HiredMoveToBlockFaceJob.terrainCost(level, approach);
            if (score < bestScore) {
                bestScore = score;
                bestApproach = approach;
            }
        }
        return bestApproach == null ? null : new HiredPathTarget(placement.pos(), bestApproach, hitPos);
    }

    private static boolean canReachSupportPlacement(ServerLevel level, Villager villager, BlockPos pos) {
        return level.hasChunkAt(pos)
                && villager.getEyePosition().distanceToSqr(pos.getCenter()) <= HiredMoveToBlockFaceJob.MAX_REACH_SQR;
    }

    private static boolean placeSupportPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            SupportPlacement placement) {
        if (!prepareSupportBlockPlacement(level, context, placement.pos(), placement.state())) {
            return false;
        }
        if (context.consumeSupply(villager, placement.type()::matchesSupply, 1) <= 0) {
            return false;
        }
        level.setBlock(placement.pos(), placement.state(), Block.UPDATE_ALL);
        return true;
    }

    private static boolean isTorchLayer(HiredWorkContext context, int y) {
        return y == context.workMin().getY()
                || Math.floorMod(context.workMax().getY() - y, EXCAVATION_TORCH_LAYER_INTERVAL) == 0;
    }

    private static List<TorchPlacement> torchPlacements(HiredWorkContext context, int y) {
        int centerX = Math.floorDiv(context.workMin().getX() + context.workMax().getX(), 2);
        int centerZ = Math.floorDiv(context.workMin().getZ() + context.workMax().getZ(), 2);
        LinkedHashSet<TorchPlacement> placements = new LinkedHashSet<>();
        placements.add(new TorchPlacement(new BlockPos(centerX, y, context.workMin().getZ()), Direction.SOUTH));
        placements.add(new TorchPlacement(new BlockPos(context.workMax().getX(), y, centerZ), Direction.WEST));
        placements.add(new TorchPlacement(new BlockPos(centerX, y, context.workMax().getZ()), Direction.NORTH));
        placements.add(new TorchPlacement(new BlockPos(context.workMin().getX(), y, centerZ), Direction.EAST));
        return new ArrayList<>(placements);
    }

    private static boolean canPlaceSupportBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return level.hasChunkAt(pos)
                && level.getBlockState(pos).isAir()
                && state.canSurvive(level, pos);
    }

    private static boolean prepareSupportBlockPlacement(ServerLevel level, HiredWorkContext context, BlockPos pos, BlockState state) {
        if (canPlaceSupportBlock(level, pos, state)) {
            return true;
        }
        if (!canPrepareSupportBacking(level, context, pos, state)) {
            return false;
        }
        BlockPos backingPos = supportBackingPos(pos, state);
        Direction supportFace = supportFace(state);
        if (backingPos == null || supportFace == null) {
            return false;
        }
        ItemStack backingStack = context.inventory().consumeOutput(
                stack -> canUseOutputAsSupportBacking(level, backingPos, supportFace, stack),
                1);
        if (!(backingStack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        level.setBlock(backingPos, blockItem.getBlock().defaultBlockState(), Block.UPDATE_ALL);
        return canPlaceSupportBlock(level, pos, state);
    }

    private static boolean canPrepareSupportBacking(ServerLevel level, HiredWorkContext context, BlockPos pos, BlockState state) {
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos backingPos = supportBackingPos(pos, state);
        Direction supportFace = supportFace(state);
        return backingPos != null
                && supportFace != null
                && isInsideOrAdjacentToWorkArea(context, backingPos)
                && level.hasChunkAt(backingPos)
                && level.getBlockState(backingPos).isAir()
                && context.inventory().hasOutput(stack -> canUseOutputAsSupportBacking(level, backingPos, supportFace, stack));
    }

    private static boolean canUseOutputAsSupportBacking(ServerLevel level, BlockPos pos, Direction supportFace, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() instanceof FallingBlock) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return !state.isAir()
                && !state.liquid()
                && !state.hasBlockEntity()
                && !HiredOreBlockTracker.isTrackedOre(state)
                && state.canSurvive(level, pos)
                && state.isFaceSturdy(level, pos, supportFace);
    }

    private static BlockPos supportBackingPos(BlockPos pos, BlockState state) {
        Direction face = supportFace(state);
        return face == null ? null : pos.relative(face.getOpposite());
    }

    private static Direction supportFace(BlockState state) {
        return state.hasProperty(LadderBlock.FACING) ? state.getValue(LadderBlock.FACING) : null;
    }

    private static boolean isInsideOrAdjacentToWorkArea(HiredWorkContext context, BlockPos pos) {
        return pos.getX() >= context.workMin().getX() - 1
                && pos.getX() <= context.workMax().getX() + 1
                && pos.getY() >= context.workMin().getY()
                && pos.getY() <= context.workMax().getY()
                && pos.getZ() >= context.workMin().getZ() - 1
                && pos.getZ() <= context.workMax().getZ() + 1;
    }

    private static boolean isLadderShaft(HiredWorkContext context, BlockPos pos) {
        LadderShaft shaft = storedLadderShaft(context);
        return shaft != null && shaft.x() == pos.getX() && shaft.z() == pos.getZ();
    }

    private HiredPathTarget activeExcavationWorkTarget(ServerLevel level, HiredWorkContext context, Villager villager) {
        HiredPathTarget target = storedWorkTarget(context.state());
        if (target == null || HiredPathMemory.isAvoided(level, villager, target.blockPos())) {
            return null;
        }
        if (!context.isInsideWorkArea(target.blockPos())) {
            return null;
        }
        if (!context.isLoaded(level, target.blockPos()) || !context.isLoaded(level, target.approachPos())) {
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

    private HiredPathTarget bestExcavationWorkTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos target) {
        if (!context.isInsideWorkArea(target)) {
            return null;
        }
        return chooseReachableTarget(level, villager, List.of(target));
    }

    private HiredPathTarget findMineableInCurrentPocket(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos anchor = miningAnchor(level, context);
        if (anchor == null) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : HiredOreBlockTracker.nearbyOreBlocks(level, anchor, pocketRadius(context), context.verticalRadius())) {
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
            HiredWorkContext context,
            boolean ignoreScanCooldown) {
        if (!ignoreScanCooldown && level.getGameTime() < context.state().getLong(NEXT_FULL_SCAN_GAME_TIME_TAG)) {
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
            context.state().putLong(NEXT_FULL_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
        } else {
            context.state().remove(NEXT_FULL_SCAN_GAME_TIME_TAG);
        }
        return target;
    }

    private HiredPathTarget rebuildVeinObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            BlockPos origin) {
        List<BlockPos> vein = bestVeinPlan(level, origin == null ? villager.blockPosition() : origin, candidates);
        if (!vein.isEmpty()) {
            HiredWorkPlan.replaceWithObjective(
                    context,
                    vein.size() > 1 ? "vein" : "ore",
                    vein.getFirst(),
                    vein,
                    MAX_PLANNED_MINING_TARGETS);
            HiredPathTarget target = plannedTarget(
                    level,
                    villager,
                    context,
                    pos -> isValidMiningTarget(level, villager, context, pos, miningAnchor(level, context)),
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
        return plannedTarget(
                level,
                villager,
                context,
                pos -> isValidMiningTarget(level, villager, context, pos, miningAnchor(level, context)),
                MAX_PLANNED_MINING_TARGETS);
    }

    private static List<BlockPos> bestVeinPlan(ServerLevel level, BlockPos origin, List<BlockPos> candidates) {
        Set<Long> remaining = new java.util.LinkedHashSet<>();
        for (BlockPos candidate : candidates) {
            remaining.add(candidate.asLong());
        }

        List<BlockPos> bestVein = List.of();
        double bestScore = Double.NEGATIVE_INFINITY;
        while (!remaining.isEmpty()) {
            BlockPos seed = BlockPos.of(remaining.iterator().next());
            BlockState seedState = level.getBlockState(seed);
            List<BlockPos> vein = new ArrayList<>();
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
            queue.add(seed);
            remaining.remove(seed.asLong());

            while (!queue.isEmpty() && vein.size() < MAX_PLANNED_MINING_TARGETS) {
                BlockPos current = queue.removeFirst();
                vein.add(current);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            BlockPos next = current.offset(dx, dy, dz).immutable();
                            if (!remaining.contains(next.asLong())) {
                                continue;
                            }
                            if (level.getBlockState(next).getBlock() != seedState.getBlock()) {
                                continue;
                            }
                            remaining.remove(next.asLong());
                            queue.addLast(next);
                        }
                    }
                }
            }

            List<BlockPos> ordered = HiredWorkPlan.routeOrder(origin, vein, MAX_PLANNED_MINING_TARGETS);
            double score = ordered.size() * 1000.0D - origin.distSqr(seed);
            if (!ordered.isEmpty() && score > bestScore) {
                bestVein = ordered;
                bestScore = score;
            }
        }
        return bestVein;
    }

    private boolean isValidMiningTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos pos) {
        return isValidMiningTarget(level, villager, context, pos, miningAnchor(level, context));
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
        return isInsideWorkArea(context, pos)
                && isInsideMiningPocket(context, pos, anchor)
                && !isTemporarilyAvoidedTarget(level, villager, pos)
                && isMineableOre(level, pos);
    }

    private boolean isValidExcavationTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos pos) {
        return isInsideWorkArea(context, pos)
                && !isTemporarilyAvoidedTarget(level, villager, pos)
                && isMineableExcavationBlock(level, pos)
                && isCurrentExcavationLayer(level, context, pos)
                && !hasAdjacentExcavationFluid(level, context, pos);
    }

    private static boolean isInsideWorkArea(HiredWorkContext context, BlockPos pos) {
        return context.isInsideWorkArea(pos);
    }

    private static boolean isCurrentExcavationLayer(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        Integer layerY = currentExcavationLayer(level, context);
        return layerY != null && pos.getY() == layerY;
    }

    private static Integer currentExcavationLayer(ServerLevel level, HiredWorkContext context) {
        Integer layerY = null;
        for (BlockPos rawPos : context.workAreaPositions()) {
            BlockPos pos = rawPos.immutable();
            if (isMineableExcavationBlock(level, pos)
                    && !hasAdjacentExcavationFluid(level, context, pos)
                    && (layerY == null || pos.getY() > layerY)) {
                layerY = pos.getY();
            }
        }
        return layerY;
    }

    private static boolean hasAdjacentExcavationFluid(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.hasChunkAt(neighbor) && !level.getFluidState(neighbor).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsideMiningPocket(HiredWorkContext context, BlockPos pos, BlockPos anchor) {
        if (anchor == null) {
            return true;
        }
        int radius = pocketRadius(context);
        return anchor.distSqr(pos) <= radius * radius;
    }

    private static boolean isOre(BlockState state) {
        return HiredOreBlockTracker.isTrackedOre(state);
    }

    private static boolean isMineableOre(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity()
                && isOre(state)
                && isExposed(level, pos);
    }

    private static boolean isMineableExcavationBlock(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && !state.liquid()
                && !isExcavationSupportBlock(state)
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity()
                && hasExcavationToolTag(state)
                && isExposed(level, pos);
    }

    private static boolean isExcavationSupportBlock(BlockState state) {
        return state.is(Blocks.LADDER)
                || state.is(Blocks.TORCH)
                || state.is(Blocks.WALL_TORCH);
    }

    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.hasChunkAt(pos.relative(direction))) {
                continue;
            }
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.isAir() || neighbor.liquid()) {
                return true;
            }
        }
        return false;
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
                    && context.isLoaded(level, target.approachPos())
                    && hasLineOfSightToTarget(level, villager, target)
                    && canMineFromCurrentPosition(level, villager, target);
        }
        return canWorkFromCurrentPosition(level, villager, context, target)
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
                || !context.isLoaded(level, target.approachPos())) {
            return false;
        }
        if (canStartMining(level, villager, context, target, mode)) {
            holdMiningPosition(villager, target);
            return true;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            return true;
        }
        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
        return path != null && path.canReach() && villager.getNavigation().moveTo(path, speed)
                || VillagerTaskNavigationUtil.moveOnLadderToward(level, villager, target.approachPos(), speed);
    }

    private boolean storeMinedDrops(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            HiredPathTarget target,
            ItemStack tool,
            HiredMiningMode mode) {
        if (!mode.excavatesArea()) {
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
        level.destroyBlockProgress(villager.getId(), target.blockPos(), -1);
        damageTool(context, villager, tool);
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

    private static int elapsedBreakProgressTicks(ServerLevel level, HiredWorkContext context) {
        long now = level.getGameTime();
        long previous = context.progressTicks() <= 0 || !context.state().contains(LAST_BREAK_PROGRESS_GAME_TIME_TAG)
                ? now - 1L
                : context.state().getLong(LAST_BREAK_PROGRESS_GAME_TIME_TAG);
        context.state().putLong(LAST_BREAK_PROGRESS_GAME_TIME_TAG, now);
        return (int) Math.clamp(now - previous, 1L, 200L);
    }

    private static boolean isUsableMiningTool(HiredMiningMode mode, ItemStack stack, BlockState targetState) {
        if (mode.excavatesArea()) {
            return stack.isCorrectToolForDrops(targetState)
                    && matchesExcavationToolTag(stack, targetState);
        }
        return stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(targetState);
    }

    private static boolean hasExcavationToolTag(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.MINEABLE_WITH_AXE);
    }

    private static boolean matchesExcavationToolTag(ItemStack stack, BlockState state) {
        return (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && stack.is(ItemTags.PICKAXES))
                || (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && stack.is(ItemTags.SHOVELS))
                || (state.is(BlockTags.MINEABLE_WITH_AXE) && stack.is(ItemTags.AXES));
    }

    private static String noTargetDepositStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "There are no reachable excavation blocks right now, so I am heading to storage for now."
                : "There is no exposed ore in this pocket, so I am heading to storage for now.";
    }

    private static String searchingStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "I see no reachable block face yet, so I am searching the excavation area."
                : "I see no exposed ore nearby, so I am searching the work area.";
    }

    private static String depositedAndWaitingStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "I am putting away what I mined while I wait for another reachable block face."
                : "I am putting away what I mined while I wait for fresh ore to show itself.";
    }

    private static String noTargetsStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "There are no reachable excavation blocks within the assigned area just now."
                : "There is no exposed ore within reach just now.";
    }

    private static String missingToolStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "I need a better pickaxe, shovel, or axe before I can excavate that block."
                : "I need a better pickaxe before I can break that ore.";
    }

    private static String blockedTargetStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "That block face is blocked off. I am looking for another reachable part of the area."
                : "That ore is blocked off. I am looking for another vein I can reach.";
    }

    private static String repositioningStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "That block is awkward from here, so I am changing my position."
                : "That ore is awkward from here, so I am changing my position.";
    }

    private static String blockedSwingStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "I cannot get a clean swing at that block yet, so I am repositioning."
                : "I cannot get a clean swing at that ore yet, so I am repositioning.";
    }

    private static String movingToTargetStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "I am moving toward the excavation face now."
                : "I am moving toward the ore now.";
    }

    private static String workingTargetStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "I am cutting into the assigned area now."
                : "I am working the ore face now.";
    }

    private static String nextTargetStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "That block is cleared, and I am moving on to the next reachable face."
                : "That ore is mined out, and I am moving on to the next exposed vein.";
    }

    private static String completedSearchingStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "I have gathered what I could, and I am searching the area for another reachable face."
                : "I have gathered what I could, and I am searching the area for more ore.";
    }

    private static String completedDepositedStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "I cleared that block, gathered it, and put it away."
                : "I mined the ore, gathered it, and put it away.";
    }

    private static String completedNoNextTargetStatus(HiredMiningMode mode) {
        return mode.excavatesArea()
                ? "I cleared that block and gathered it, but there is no other reachable face nearby."
                : "I mined the ore and gathered it, but there is no other exposed vein nearby.";
    }

    private static String continuingExcavationStatus(DepositResult depositResult) {
        return depositResult == DepositResult.DEPOSITED
                ? "I cleared that block, gathered it, put it away, and am checking for the next reachable face."
                : "I cleared that block and gathered it. I am checking the assigned area for the next reachable face.";
    }

    private static String completedExcavationStatus(DepositResult depositResult) {
        return depositResult == DepositResult.DEPOSITED
                ? "The assigned excavation is finished, and I put the last of the output away."
                : "The assigned excavation is finished. I do not see another block to clear.";
    }

    private static boolean isExcavationComplete(ServerLevel level, HiredWorkContext context, HiredMiningMode mode) {
        return mode.excavatesArea()
                && !HiredWorkAreaScan.isInProgress(context, EXCAVATION_SCAN_CURSOR_TAG)
                && level.getGameTime() < context.state().getLong(NEXT_FULL_SCAN_GAME_TIME_TAG)
                && currentExcavationLayer(level, context) == null;
    }

    private static boolean isExcavationScanInProgress(HiredWorkContext context, HiredMiningMode mode) {
        return mode.excavatesArea() && HiredWorkAreaScan.isInProgress(context, EXCAVATION_SCAN_CURSOR_TAG);
    }

    private static void ensureNoTargetScanCooldown(ServerLevel level, HiredWorkContext context) {
        if (context.state().getLong(NEXT_FULL_SCAN_GAME_TIME_TAG) <= level.getGameTime()) {
            context.state().putLong(NEXT_FULL_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
        }
    }

    private static void setMiningState(HiredWorkContext context, MiningState state) {
        context.state().putString(MINING_STATE_TAG, state.id);
    }

    private static void rememberLastMined(HiredWorkContext context, BlockPos pos) {
        context.state().putLong(LAST_MINED_BLOCK_POS_TAG, pos.asLong());
    }

    private static BlockPos lastMinedBlock(HiredWorkContext context) {
        return context.state().contains(LAST_MINED_BLOCK_POS_TAG)
                ? BlockPos.of(context.state().getLong(LAST_MINED_BLOCK_POS_TAG))
                : null;
    }

    private static void rememberMiningAnchor(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        context.state().putLong(MINING_ANCHOR_POS_TAG, pos.asLong());
        context.state().putLong(MINING_ANCHOR_EXPIRES_GAME_TIME_TAG, level.getGameTime() + MINING_ANCHOR_TICKS);
    }

    private static BlockPos miningAnchor(ServerLevel level, HiredWorkContext context) {
        if (!context.state().contains(MINING_ANCHOR_POS_TAG)) {
            return null;
        }
        if (context.state().getLong(MINING_ANCHOR_EXPIRES_GAME_TIME_TAG) <= level.getGameTime()) {
            clearMiningAnchor(context);
            return null;
        }
        return BlockPos.of(context.state().getLong(MINING_ANCHOR_POS_TAG));
    }

    private static void clearMiningAnchor(HiredWorkContext context) {
        context.state().remove(MINING_ANCHOR_POS_TAG);
        context.state().remove(MINING_ANCHOR_EXPIRES_GAME_TIME_TAG);
    }

    private static int pocketRadius(HiredWorkContext context) {
        return Math.min(Math.max(1, context.radius()), MINING_POCKET_RADIUS);
    }

    private record LadderShaft(int x, int z, Direction facing) {
    }

    private record TorchPlacement(BlockPos pos, Direction facing) {
    }

    private record SupportPlacement(BlockPos pos, BlockState state, SupportType type) {
    }

    private enum SupportType {
        LADDER {
            @Override
            boolean matchesSupply(ItemStack stack) {
                return stack.is(Items.LADDER);
            }

            @Override
            String placedStatus() {
                return "I placed the next ladder support for the excavation.";
            }
        },
        TORCH {
            @Override
            boolean matchesSupply(ItemStack stack) {
                return stack.is(Items.TORCH);
            }

            @Override
            String placedStatus() {
                return "I placed a torch for the excavation.";
            }
        };

        abstract boolean matchesSupply(ItemStack stack);

        abstract String placedStatus();
    }

    private enum MiningState {
        IDLE("idle"),
        FIND_TARGET("find_target"),
        PATH_TO_TARGET("path_to_target"),
        MINE_TARGET("mine_target"),
        DEPOSIT_OUTPUT("deposit_output"),
        WAITING_NO_TARGETS("waiting_no_targets"),
        BLOCKED_OUTPUT_FULL("blocked_output_full"),
        BLOCKED_MISSING_TOOL("blocked_missing_tool");

        private final String id;

        MiningState(String id) {
            this.id = id;
        }
    }
}
