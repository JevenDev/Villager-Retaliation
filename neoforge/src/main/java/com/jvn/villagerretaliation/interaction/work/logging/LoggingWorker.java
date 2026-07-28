package com.jvn.villagerretaliation.interaction.work.logging;

import static com.jvn.villagerretaliation.interaction.work.logging.LoggingWorkerState.NEXT_SAPLING_SCAN_GAME_TIME_TAG;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingWorkerState.NEXT_TREE_SCAN_GAME_TIME_TAG;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingWorkerState.SAPLING_SCAN_CURSOR_TAG;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingWorkerState.TREE_SCAN_CURSOR_TAG;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.isMatchingLog;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.isNaturalLeaf;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.logFamilyKey;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.naturalTreeLeaves;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.saplingForTree;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.saplingPlantingPositions;
import static com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.treeRoot;

import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.work.HiredTargetSearch;
import com.jvn.villagerretaliation.interaction.work.HiredStorageNavigationGoal;
import com.jvn.villagerretaliation.interaction.work.HiredPathTarget;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.HiredItemPickup;
import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class LoggingWorker extends AbstractBlockWorker {
    private static final String NEXT_WORK_GAME_TIME_TAG = "NextWorkGameTime";
    private static final String TREE_DEPOSIT_PENDING_TAG = "LoggingTreeDepositPending";
    private static final int MAX_TREE_LOGS_PER_HARVEST_TICK = 10;
    private static final int MAX_TREE_LEAVES_PER_HARVEST_TICK = 32;
    private static final int MAX_TREE_SCAN_POSITIONS_PER_WORK_TICK = 512;
    private static final int MAX_SAPLING_SCAN_POSITIONS_PER_WORK_TICK = 768;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;
    private static final int MAX_TREE_PROGRESS_TICKS = 180;
    private static final int MAX_LOGGING_TARGETS_TO_PATHFIND = 64;
    private static final int MAX_PLANNED_TREE_TARGETS = 12;
    private static final int MAX_PLANNED_SAPLING_TARGETS = 8;
    private static final String TREE_OBJECTIVE = "tree";
    private static final String TREE_ROUTE_OBJECTIVE = "tree_route";
    private static final String SINGLE_TREE_OBJECTIVE = "single_tree";
    private static final String GROVE_OBJECTIVE = "grove";
    private static final String SAPLING_ROUTE_OBJECTIVE = "sapling_route";
    private static final String SINGLE_SAPLING_OBJECTIVE = "single_sapling";
    private static final double DECAY_DROP_PICKUP_REACH_SQR = 2.25D;
    private static final int GROVE_LINK_RADIUS = 6;
    private static final HiredItemPickup.Messages DECAY_DROP_PICKUP_MESSAGES = new HiredItemPickup.Messages(
            "interaction.work.logging.output_full_depositing",
            "interaction.work.logging.output_full_blocked",
            "decay_drop_unreachable",
            "interaction.work.logging.decay_drop_unreachable",
            "interaction.work.logging.decay_drop_repositioning",
            "interaction.work.logging.moving_to_decay_drop",
            "interaction.work.logging.collected_decay_drops",
            false,
            true);
    private static final HiredTargetSearch.Messages TREE_SEARCH_MESSAGES = new HiredTargetSearch.Messages(
            "active_tree_target",
            "planned_tree_target",
            "tree_scan_cooldown",
            "tree_scan_full_no_reachable_targets",
            "tree_scan_partial_",
            "tree_target_found",
            NO_TARGET_SCAN_COOLDOWN_TICKS);
    private static final HiredTargetSearch.Messages SAPLING_SEARCH_MESSAGES = new HiredTargetSearch.Messages(
            "active_sapling_target",
            "planned_sapling_target",
            "sapling_scan_cooldown",
            "sapling_scan_full_no_reachable_targets",
            "sapling_scan_partial_",
            "sapling_target_found",
            NO_TARGET_SCAN_COOLDOWN_TICKS);
    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.LOGGING;
    }

    public static String debugSummary(HiredWorkContext context) {
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
        String pending = plan == null
                ? "pending none"
                : "pending logs=" + plan.logs().length
                + ", leaves=" + plan.leaves().length
                + ", saplings=" + plan.saplings().length
                + ", cut=" + plan.logsCut();
        return "Logging: " + pending
                + ", treeScan=" + scanState(context, TREE_SCAN_CURSOR_TAG, NEXT_TREE_SCAN_GAME_TIME_TAG)
                + ", saplingScan=" + scanState(context, SAPLING_SCAN_CURSOR_TAG, NEXT_SAPLING_SCAN_GAME_TIME_TAG);
    }

    private static String scanState(HiredWorkContext context, String cursorTag, String cooldownTag) {
        CompoundTag state = context.state();
        if (HiredWorkAreaScan.isInProgress(context, cursorTag)) {
            return "in_progress@" + state.getLong(cursorTag);
        }
        return state.contains(cooldownTag) ? "cooldown_until=" + state.getLong(cooldownTag) : "ready";
    }

    @Override
    public void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        super.maintain(level, villager, context);
        context.state().remove(NEXT_WORK_GAME_TIME_TAG);
        if (!context.hasWorkArea()) {
            LoggingHarvestPlan.clear(context);
            LoggingWorkerState.clear(context);
            return;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (worker.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET && worker.targetPos() != null) {
            HiredPathTarget active = activeWorkTarget(level, context, villager);
            if (active != null) {
                boolean physicalLeafTarget = isPendingLeafTarget(context, active.blockPos())
                        || LoggingWorkerState.isAccessLeaf(context, active.blockPos());
                boolean saplingTarget = isBonemealableSapling(level, active.blockPos());
                boolean canWork = physicalLeafTarget
                        ? canBreakAccessLeafFromCurrentPosition(level, villager, context, active)
                        : saplingTarget
                        ? canBonemealSaplingFromCurrentPosition(level, villager, context, active)
                        : canWorkFromCurrentPosition(level, villager, context, active);
                if (canWork) {
                    holdWorkPosition(villager, active);
                    HiredWorkerBrain.clearFailure(context);
                    setTaskState(context, HiredWorkerTaskState.WORKING, active.blockPos());
                }
                return;
            }
            if (active == null && villager.getNavigation().isDone() && context.progressTicks() <= 0) {
                clearActiveBreakingTarget(level, context, villager);
                setTaskState(context, HiredWorkerTaskState.IDLE);
            }
        }
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            LoggingHarvestPlan.clear(context);
            LoggingWorkerState.clear(context);
            return waitForWorkAreaAssignment(level, villager, context);
        }

        WorkResult blockedExitResult = clearTreeBlockedExit(level, villager, context);
        if (blockedExitResult != null) {
            return blockedExitResult;
        }

        WorkResult treeDepositResult = continueCompletedTreeDeposit(level, villager, context);
        if (treeDepositResult != null) {
            return treeDepositResult;
        }

        WorkResult pendingHarvestResult = continuePendingTreeHarvest(level, villager, context);
        if (pendingHarvestResult != null) {
            return pendingHarvestResult;
        }

        WorkResult activeAccessLeafResult = continueActiveAccessLeaf(level, villager, context);
        if (activeAccessLeafResult != null) {
            return activeAccessLeafResult;
        }

        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        if (hasSaplingPlan(context)) {
            WorkResult bonemealResult = tryBonemealSapling(level, villager, context);
            if (bonemealResult != null) {
                return bonemealResult;
            }
        }

        WorkResult decayDropResult = collectDecayDrops(level, villager, context);
        if (decayDropResult != null) {
            return decayDropResult;
        }

        HiredPathTarget target = findTreeLog(level, villager, context);
        if (target == null) {
            if (!hasSaplingPlan(context)) {
                WorkResult bonemealResult = tryBonemealSapling(level, villager, context);
                if (bonemealResult != null) {
                    return bonemealResult;
                }
            }
            clearActiveBreakingTarget(level, context, villager);
            if (isTreeScanInProgress(context)) {
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("interaction.work.logging.searching_scan");
            }
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.logging.no_target_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            if (roamInsideWorkArea(level, villager, context, 0.4D)) {
                return WorkResult.progressed("interaction.work.logging.roaming");
            }
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.logging.no_targets");
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        ToolStorageResult toolResult = equipBestToolOrCollectFromStorage(
                level,
                villager,
                context,
                stack -> stack.is(ItemTags.AXES),
                stack -> effectiveDestroySpeed(stack, targetState),
                0.55D);
        if (toolResult.status() != ToolStorageStatus.READY && toolResult.status() != ToolStorageStatus.COLLECTED) {
            if (toolResult.status() == ToolStorageStatus.MOVING) {
                return WorkResult.progressed("interaction.work.status.collecting_tool");
            }
            clearActiveBreakingTarget(level, context, villager);
            if (toolResult.status() == ToolStorageStatus.UNREACHABLE) {
                HiredWorkerBrain.setFailure(context, "tool_storage_unreachable_axe", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, toolResult.storagePos());
                return WorkResult.idle("interaction.work.status.tool_storage_unreachable", Map.of("tool", "axe"));
            }
            if (toolResult.status() == ToolStorageStatus.INVENTORY_FULL) {
                HiredWorkerBrain.setFailure(context, "tool_inventory_full_axe", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, toolResult.storagePos());
                return WorkResult.idle("interaction.work.status.tool_inventory_full", Map.of("tool", "axe"));
            }
            HiredWorkerBrain.setFailure(context, "missing_axe", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return WorkResult.idle("interaction.work.logging.missing_axe");
        }
        WorkResult leafToolResult = collectOptionalLeafTool(level, villager, context);
        if (leafToolResult != null) {
            return leafToolResult;
        }
        ItemStack axe = context.inventory().equipBestTool(
                stack -> stack.is(ItemTags.AXES),
                stack -> effectiveDestroySpeed(stack, targetState));
        if (axe.isEmpty()) {
            axe = toolResult.tool();
        }

        prepareBreakingTarget(level, context, villager, target);
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            if (!moveToTarget(level, villager, context, target, 0.55D)) {
                WorkResult blockingLeafResult = clearBlockingLeafForTarget(level, villager, context, target);
                if (blockingLeafResult != null) {
                    return blockingLeafResult;
                }
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
                    return WorkResult.idle("interaction.work.logging.blocked_target");
                }
                return WorkResult.progressed("interaction.work.logging.repositioning");
            }
            return WorkResult.progressed("interaction.work.logging.moving_to_target");
        }
        clearWorkPathFailure(villager, target.blockPos());
        holdWorkPosition(villager, target);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, target.blockPos());

        BlockPos blockingLeaf = firstBlockingLeaf(level, villager, target);
        if (blockingLeaf != null) {
            context.setProgressTicks(0);
            return workBlockingLeaf(level, villager, context, target, blockingLeaf);
        }

        int needed = adjustedTreeHarvestProgressGoal(level, context, target.blockPos(), axe);
        int progress = context.progressTicks() + 1;
        if (progress < needed) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            showBreakProgress(level, villager, target.blockPos(), progress, needed);
            return WorkResult.progressed("interaction.work.logging.working_target");
        }

        context.setProgressTicks(0);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, target.blockPos());
        TreeHarvestResult harvestResult = harvestTree(level, context, villager, target, axe);
        if (harvestResult.inProgress()) {
            return WorkResult.progressed("interaction.work.logging.working_target");
        }
        if (harvestResult == TreeHarvestResult.OUTPUT_FULL) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.DEPOSITED) {
                return retryPendingHarvestAfterDeposit(level, villager, context, axe, target.blockPos());
            }
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.logging.output_full_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            clearActiveBreakingTarget(level, context, villager);
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("interaction.work.logging.output_full_blocked");
        }
        if (harvestResult == TreeHarvestResult.TARGET_CHANGED) {
            return targetChanged(level, villager, context, target.blockPos());
        }
        HiredWorkPlan.removeTarget(context, target.blockPos());
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return completedTreeWork(context, harvestResult.logsCut());
    }

    @Override
    public void pause(ServerLevel level, Villager villager, HiredWorkContext context) {
        LoggingWorkerState.clearAccessLeaf(context);
        LoggingWorkerState.clearBreakGoal(context);
        super.pause(level, villager, context);
    }

    @Override
    public void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        LoggingHarvestPlan.clear(context);
        context.state().remove(TREE_DEPOSIT_PENDING_TAG);
        LoggingWorkerState.clear(context);
        super.stop(level, villager, context);
    }

    @Override
    protected HiredPathTarget chooseReachableTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Iterable<BlockPos> targets) {
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                targets,
                MAX_LOGGING_TARGETS_TO_PATHFIND,
                context::isInsideWorkArea,
                LoggingTreeGeometry::isNaturalLeaf).search().target();
    }

    @Override
    protected boolean canMineFromCurrentPosition(ServerLevel level, Villager villager, HiredPathTarget target) {
        return HiredMoveToBlockFaceJob.canReachFromCurrentPosition(level, villager, target, LoggingTreeGeometry::isNaturalLeaf);
    }

    @Override
    protected boolean hasLineOfSightToBlock(ServerLevel level, Villager villager, Vec3 start, BlockPos target, Vec3 hitPos) {
        return HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, start, target, hitPos, LoggingTreeGeometry::isNaturalLeaf);
    }

    private WorkResult continuePendingTreeHarvest(ServerLevel level, Villager villager, HiredWorkContext context) {
        LoggingHarvestPlan.Snapshot pendingPlan = LoggingHarvestPlan.read(context);
        if (pendingPlan == null) {
            return null;
        }
        if (!context.isInsideWorkArea(villager.blockPosition())) {
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
            return WorkResult.progressed("interaction.work.status.returning_bounds");
        }
        if (!pendingPlan.hasLogs() && !pendingPlan.hasLeaves() && pendingPlan.saplings().length > 0) {
            WorkResult decayDropResult = collectDecayDrops(level, villager, context);
            if (decayDropResult != null) {
                return decayDropResult;
            }
        }
        WorkResult activeAccessLeafResult = continueActiveAccessLeaf(level, villager, context);
        if (activeAccessLeafResult != null) {
            return activeAccessLeafResult;
        }

        ItemStack axe = ItemStack.EMPTY;
        BlockState nextLogState = firstPendingLogState(level, context, pendingPlan);
        if (nextLogState != null) {
            ToolStorageResult toolResult = equipBestToolOrCollectFromStorage(
                    level,
                    villager,
                    context,
                    stack -> stack.is(ItemTags.AXES),
                    stack -> effectiveDestroySpeed(stack, nextLogState),
                    0.55D);
            if (toolResult.status() != ToolStorageStatus.READY && toolResult.status() != ToolStorageStatus.COLLECTED) {
                if (toolResult.status() == ToolStorageStatus.MOVING) {
                    return WorkResult.progressed("interaction.work.status.collecting_tool");
                }
                if (toolResult.status() == ToolStorageStatus.UNREACHABLE) {
                    HiredWorkerBrain.setFailure(context, "tool_storage_unreachable_axe", level.getGameTime() + 100L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, toolResult.storagePos());
                    return WorkResult.idle("interaction.work.status.tool_storage_unreachable", Map.of("tool", "axe"));
                }
                if (toolResult.status() == ToolStorageStatus.INVENTORY_FULL) {
                    HiredWorkerBrain.setFailure(context, "tool_inventory_full_axe", level.getGameTime() + 100L);
                    setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, toolResult.storagePos());
                    return WorkResult.idle("interaction.work.status.tool_inventory_full", Map.of("tool", "axe"));
                }
                HiredWorkerBrain.setFailure(context, "missing_axe", 0L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
                return WorkResult.idle("interaction.work.logging.missing_axe");
            }
            axe = toolResult.tool();
        }

        WorkResult positioningResult = moveToPendingHarvestTarget(level, villager, context, pendingPlan);
        if (positioningResult != null) {
            return positioningResult;
        }

        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, pendingPlan.origin());
        TreeHarvestResult harvestResult = processPendingTreeHarvest(level, context, villager, axe);
        if (harvestResult.inProgress()) {
            return WorkResult.progressed(pendingHarvestStatus(context));
        }
        if (harvestResult == TreeHarvestResult.OUTPUT_FULL) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.DEPOSITED) {
                return retryPendingHarvestAfterDeposit(level, villager, context, axe, pendingPlan.origin());
            }
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.logging.output_full_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("interaction.work.logging.output_full_blocked");
        }
        if (harvestResult == TreeHarvestResult.TARGET_CHANGED) {
            return targetChanged(level, villager, context, pendingPlan.origin());
        }
        return completePendingTreeHarvest(level, villager, context, harvestResult);
    }

    private WorkResult retryPendingHarvestAfterDeposit(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            ItemStack axe,
            BlockPos fallbackTarget) {
        if (!context.isInsideWorkArea(villager.blockPosition())) {
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
            return WorkResult.progressed("interaction.work.status.returning_bounds");
        }
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
        if (plan == null) {
            return targetChanged(level, villager, context, fallbackTarget);
        }
        WorkResult repositioningResult = moveToPendingHarvestTarget(level, villager, context, plan);
        if (repositioningResult != null) {
            return repositioningResult;
        }
        TreeHarvestResult harvestResult = processPendingTreeHarvest(level, context, villager, axe);
        if (harvestResult.inProgress()) {
            return WorkResult.progressed(pendingHarvestStatus(context));
        }
        if (harvestResult.completed()) {
            return completePendingTreeHarvest(level, villager, context, harvestResult);
        }
        if (harvestResult == TreeHarvestResult.TARGET_CHANGED) {
            return targetChanged(level, villager, context, plan.origin());
        }
        return WorkResult.progressed("interaction.work.logging.output_full_depositing");
    }

    private WorkResult targetChanged(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos targetPos) {
        LoggingHarvestPlan.clear(context);
        if (targetPos != null) {
            HiredWorkPlan.removeTarget(context, targetPos);
        }
        clearActiveBreakingTarget(level, context, villager);
        HiredWorkerBrain.setFailure(context, "target_changed", level.getGameTime() + 40L);
        setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN);
        return WorkResult.idle("interaction.work.logging.target_changed");
    }

    private WorkResult moveToPendingHarvestTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            LoggingHarvestPlan.Snapshot plan) {
        PendingHarvestTargets candidates = pendingHarvestTargets(level, context, plan);
        if (candidates.positions().isEmpty()) {
            return null;
        }
        if (candidates.kind() == PendingTargetKind.LEAF) {
            return null;
        }
        if (candidates.kind() == PendingTargetKind.LOG) {
            BlockPos harvestPos = plan.harvestPos();
            if (canContinuePendingLogsFrom(level, villager, context, harvestPos)) {
                stopWorkNavigation(villager);
                HiredPathMemory.clearNavigationProgress(villager);
                HiredWorkerBrain.clearFailure(context);
                return null;
            }
            WorkResult returnToHarvestPos = moveToPendingHarvestPosition(
                    level,
                    villager,
                    context,
                    plan.origin(),
                    harvestPos,
                    0.55D);
            if (returnToHarvestPos != null) {
                return returnToHarvestPos;
            }
        }
        HiredPathTarget target = candidates.kind() == PendingTargetKind.LEAF
                ? choosePhysicalReachableTarget(level, villager, context, candidates.positions())
                : candidates.kind() == PendingTargetKind.SAPLING
                ? choosePlantingTarget(level, villager, context, candidates.positions())
                : chooseReachableTarget(level, villager, context, candidates.positions());
        if (target == null) {
            if (candidates.kind() == PendingTargetKind.LEAF) {
                clearPendingTreeLeaves(context);
                clearActiveBreakingTarget(level, context, villager);
                return null;
            }
            if (candidates.kind() == PendingTargetKind.SAPLING) {
                LoggingHarvestPlan.clearSaplings(context);
                clearActiveBreakingTarget(level, context, villager);
                return null;
            }
            WorkResult blockingLeafResult = clearBlockingLeafTowardPositions(level, villager, context, candidates.positions());
            if (blockingLeafResult != null) {
                return blockingLeafResult;
            }
            BlockPos origin = pendingTreeOrigin(context);
            if (recordWorkPathFailure(level, villager, origin)) {
                LoggingHarvestPlan.clear(context);
                clearActiveBreakingTarget(level, context, villager);
                HiredWorkerBrain.setFailure(context, "pending_tree_unreachable", level.getGameTime() + 20L * 30L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, origin);
                return WorkResult.idle("interaction.work.logging.blocked_target");
            }
            return WorkResult.progressed("interaction.work.logging.repositioning");
        }

        prepareBreakingTarget(level, context, villager, target);
        boolean canWork = candidates.kind() == PendingTargetKind.LEAF
                ? canBreakAccessLeafFromCurrentPosition(level, villager, context, target)
                : candidates.kind() == PendingTargetKind.SAPLING
                ? canPlantFromCurrentPosition(level, villager, context, target)
                : canWorkFromCurrentPosition(level, villager, context, target);
        if (!canWork) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            boolean moved = candidates.kind() == PendingTargetKind.LEAF
                    ? moveToAccessLeafTarget(level, villager, context, target, 0.55D)
                    : candidates.kind() == PendingTargetKind.SAPLING
                    ? moveToPlantingTarget(level, villager, context, target, 0.55D)
                    : moveToTarget(level, villager, context, target, 0.55D);
            if (!moved) {
                if (candidates.kind() == PendingTargetKind.LEAF) {
                    LoggingHarvestPlan.removeLeaf(context, target.blockPos());
                    clearActiveBreakingTarget(level, context, villager);
                    return null;
                }
                if (candidates.kind() == PendingTargetKind.SAPLING) {
                    LoggingHarvestPlan.removeSapling(context, target.blockPos().above());
                    clearActiveBreakingTarget(level, context, villager);
                    return null;
                }
                WorkResult blockingLeafResult = clearBlockingLeafForTarget(level, villager, context, target);
                if (blockingLeafResult != null) {
                    return blockingLeafResult;
                }
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    LoggingHarvestPlan.clear(context);
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "pending_tree_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
                    return WorkResult.idle("interaction.work.logging.blocked_target");
                }
                return WorkResult.progressed("interaction.work.logging.repositioning");
            }
            return WorkResult.progressed("interaction.work.logging.moving_to_target");
        }

        clearWorkPathFailure(villager, target.blockPos());
        holdWorkPosition(villager, target);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, target.blockPos());
        return null;
    }

    private static boolean canContinuePendingLogsFrom(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos harvestPos) {
        return harvestPos != null
                && context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(harvestPos)
                && context.isLoaded(level, harvestPos)
                && villager.distanceToSqr(harvestPos.getCenter()) <= 2.25D;
    }

    private WorkResult moveToPendingHarvestPosition(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos origin,
            BlockPos harvestPos,
            double speed) {
        if (harvestPos == null
                || !context.isInsideWorkArea(harvestPos)
                || !context.isLoaded(level, harvestPos)) {
            return null;
        }
        BlockPos lookTarget = origin == null ? harvestPos : origin;
        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null
                && !HiredMoveToBlockFaceJob.pathStaysInsideFilter(currentPath, context::isInsideWorkArea)) {
            stopWorkNavigation(villager);
            return null;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && harvestPos.equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    harvestPos,
                    villager.distanceToSqr(harvestPos.getCenter()))) {
                stopWorkNavigation(villager);
                return null;
            }
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, lookTarget);
            return WorkResult.progressed("interaction.work.logging.moving_to_target");
        }

        Path path = HiredPathMemory.createPath(level, villager, harvestPos, 0);
        if (path == null
                || !path.canReach()
                || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
            HiredPathMemory.clearNavigationProgress(villager);
            return null;
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(lookTarget));
        if (!VillagerTaskNavigationUtil.moveToHiredPath(villager, path, harvestPos, speed, 0)) {
            HiredPathMemory.clearNavigationProgress(villager);
            return null;
        }
        HiredPathMemory.rememberNavigationProgress(
                level,
                villager,
                harvestPos,
                villager.distanceToSqr(harvestPos.getCenter()));
        setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, lookTarget);
        return WorkResult.progressed("interaction.work.logging.moving_to_target");
    }

    private HiredPathTarget choosePlantingTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Iterable<BlockPos> soilTargets) {
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                soilTargets,
                MAX_LOGGING_TARGETS_TO_PATHFIND,
                soil -> context.isInsideWorkArea(soil.above()),
                context::isInsideWorkArea,
                context::isInsideWorkArea,
                LoggingTreeGeometry::isNaturalLeaf).search().target();
    }

    private static boolean canPlantFromCurrentPosition(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target) {
        return context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(target.blockPos().above())
                && context.isInsideWorkArea(target.approachPos())
                && context.isLoaded(level, target.blockPos())
                && context.isLoaded(level, target.approachPos())
                && HiredMoveToBlockFaceJob.canReachFromCurrentPosition(level, villager, target, LoggingTreeGeometry::isNaturalLeaf);
    }

    private boolean moveToPlantingTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            double speed) {
        if (canPlantFromCurrentPosition(level, villager, context, target)) {
            holdWorkPosition(villager, target);
            return true;
        }
        if (!context.isInsideWorkArea(target.blockPos().above())
                || !context.isInsideWorkArea(target.approachPos())) {
            return false;
        }
        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null && !HiredMoveToBlockFaceJob.pathStaysInsideFilter(currentPath, context::isInsideWorkArea)) {
            stopWorkNavigation(villager);
            return false;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            return true;
        }
        Path path = HiredPathMemory.createPath(level, villager, target.approachPos(), 0);
        if (path == null || !path.canReach()
                || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
            return false;
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.blockPos().above()));
        return VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target.approachPos(), speed, 0);
    }

    private WorkResult completePendingTreeHarvest(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            TreeHarvestResult harvestResult) {
        HiredWorkPlan.removeTarget(context, pendingTreeOrigin(context));
        LoggingHarvestPlan.clear(context);
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return completedTreeWork(context, harvestResult.logsCut());
    }

    private static String pendingHarvestStatus(HiredWorkContext context) {
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
        return plan != null && !plan.hasLogs() && plan.hasLeaves()
                ? "interaction.work.logging.clearing_access_leaf"
                : "interaction.work.logging.working_target";
    }

    private WorkResult clearTreeBlockedExit(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        if (context.inventory() == null || !context.isInsideWorkArea(villager.blockPosition())) {
            return null;
        }
        LoggingHarvestPlan.Snapshot activePlan = LoggingHarvestPlan.read(context);
        if (activePlan != null && activePlan.hasLogs()) {
            return null;
        }

        BlockPos feet = villager.blockPosition();
        ExitObstruction best = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos lower = feet.relative(direction);
            BlockPos upper = lower.above();
            if (isPassableExitBlock(level, lower) && isPassableExitBlock(level, upper)) {
                return null;
            }
            ExitObstruction candidate = treeExitObstruction(level, context, lower, upper);
            if (candidate != null && (best == null || candidate.leaf() && !best.leaf())) {
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }

        BlockState state = level.getBlockState(best.pos());
        ItemStack tool = ItemStack.EMPTY;
        if (best.leaf() && HiredLoggingOptions.harvestLeaves(context.state())) {
            tool = context.inventory().equipBestTool(
                    LoggingWorker::isLeafHarvestTool,
                    LoggingWorker::leafToolScore);
        } else if (!best.leaf()) {
            tool = context.inventory().equipBestTool(
                    stack -> stack.is(ItemTags.AXES),
                    stack -> effectiveDestroySpeed(stack, state));
            if (tool.isEmpty()) {
                return null;
            }
        }
        return breakTreeExitObstruction(level, villager, context, best, tool);
    }

    private static ExitObstruction treeExitObstruction(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos lower,
            BlockPos upper) {
        ExitObstruction best = null;
        for (BlockPos pos : List.of(lower, upper)) {
            if (isPassableExitBlock(level, pos)) {
                continue;
            }
            if (!context.isInsideWorkArea(pos) || !context.isLoaded(level, pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            boolean leaf = state.is(BlockTags.LEAVES);
            if (!leaf && !state.is(BlockTags.LOGS)) {
                return null;
            }
            ExitObstruction candidate = new ExitObstruction(pos.immutable(), leaf);
            if (best == null || candidate.leaf() && !best.leaf()) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isPassableExitBlock(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    private WorkResult breakTreeExitObstruction(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            ExitObstruction obstruction,
            ItemStack tool) {
        BlockPos pos = obstruction.pos();
        BlockState state = level.getBlockState(pos);
        if (obstruction.leaf() ? !state.is(BlockTags.LEAVES) : !state.is(BlockTags.LOGS)) {
            return null;
        }

        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), villager, tool);
        faceBlock(villager, pos);
        swingWorkTool(villager);
        if (!tool.isEmpty()) {
            EnchantmentHelper.onHitBlock(level, tool, villager, villager, EquipmentSlot.MAINHAND, pos.getCenter(), state, ignored -> {
            });
        }
        boolean removed = level.destroyBlock(pos, false, villager);
        level.destroyBlockProgress(villager.getId(), pos, -1);
        boolean changed = obstruction.leaf()
                ? !level.getBlockState(pos).is(BlockTags.LEAVES)
                : !level.getBlockState(pos).is(BlockTags.LOGS);
        if (!removed && !changed) {
            return null;
        }

        HiredPathMemory.onBlockChanged(level, pos);
        if (removed) {
            for (ItemStack drop : drops) {
                ItemStack remainder = context.storeOutputAfterDepositIfFull(villager, drop);
                if (!remainder.isEmpty()) {
                    Block.popResource(level, villager.blockPosition(), remainder);
                }
            }
            if (!tool.isEmpty()) {
                damageTool(context, villager, tool, level, state, pos);
            }
            HiredPathMemory.rememberRecent(level, pos);
        }
        if (obstruction.leaf()) {
            restoreLoggingAxe(context, level, pos);
        }
        stopWorkNavigation(villager);
        HiredPathMemory.clearNavigationProgress(villager);
        HiredWorkerBrain.clearFailure(context);
        return WorkResult.progressed(obstruction.leaf()
                ? "interaction.work.logging.clearing_access_leaf"
                : "interaction.work.logging.working_target");
    }

    private WorkResult continueCompletedTreeDeposit(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        if (!context.state().getBoolean(TREE_DEPOSIT_PENDING_TAG)) {
            return null;
        }
        if (!context.autoDepositOutputs() || !context.hasOutputToDeposit()) {
            context.state().remove(TREE_DEPOSIT_PENDING_TAG);
            return null;
        }

        DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
        if (depositResult == DepositResult.MOVING) {
            return WorkResult.progressed("interaction.work.logging.no_target_depositing");
        }
        if (depositResult == DepositResult.DEPOSITED) {
            if (!context.hasOutputToDeposit()) {
                context.state().remove(TREE_DEPOSIT_PENDING_TAG);
            }
            return WorkResult.progressed("interaction.work.logging.no_target_depositing");
        }
        if (depositResult == DepositResult.STORAGE_FULL) {
            return WorkResult.idle(storageFullStatus(context));
        }
        if (depositResult == DepositResult.UNAVAILABLE) {
            return WorkResult.idle("interaction.work.logging.no_target_depositing");
        }
        context.state().remove(TREE_DEPOSIT_PENDING_TAG);
        return null;
    }

    private static WorkResult completedTreeWork(HiredWorkContext context, int logsCut) {
        context.state().remove(NEXT_WORK_GAME_TIME_TAG);
        context.state().putBoolean(TREE_DEPOSIT_PENDING_TAG, true);
        LoggingWorkerState.wakeTreeSearch(context);
        return WorkResult.progressedWithPractice(
                "interaction.work.logging.completed",
                Map.of("logs", Integer.toString(logsCut)),
                HiredWorkPractice.logging(logsCut));
    }

    private HiredPathTarget findTreeLog(ServerLevel level, Villager villager, HiredWorkContext context) {
        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        LoggingTreeGeometry.DiscoveryCache trees = LoggingTreeGeometry.discovery(level, filters);
        HiredPathTarget storedTarget = storedWorkTarget(context.state());
        BlockPos validatedTarget = storedTarget == null ? null : storedTarget.blockPos();
        return HiredTargetSearch.find(
                level,
                context,
                () -> activeWorkTarget(level, context, villager),
                target -> context.isInsideWorkArea(target.blockPos())
                        && context.isLoaded(level, target.blockPos())
                        && !isTemporarilyAvoidedTarget(level, villager, target.blockPos())
                        && isTreeLog(level, target.blockPos(), filters, trees, validatedTarget),
                candidateFilter -> plannedTreeTarget(level, villager, context, candidateFilter),
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos)
                        && isTreeLog(level, pos, filters, trees, validatedTarget),
                NEXT_TREE_SCAN_GAME_TIME_TAG,
                TREE_SCAN_CURSOR_TAG,
                MAX_TREE_SCAN_POSITIONS_PER_WORK_TICK,
                candidates -> rebuildTreeObjective(level, villager, context, candidates, trees),
                TREE_SEARCH_MESSAGES);
    }

    private HiredPathTarget plannedTreeTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Predicate<BlockPos> validator) {
        if (!hasTreePlan(context)) {
            return null;
        }
        return plannedTarget(level, villager, context, validator, MAX_PLANNED_TREE_TARGETS);
    }

    private HiredPathTarget plannedSaplingTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Predicate<BlockPos> validator) {
        if (!hasSaplingPlan(context)) {
            return null;
        }
        Predicate<BlockPos> safeValidator = validator == null ? ignored -> true : validator;
        HiredWorkPlan.retainMatching(context, safeValidator, MAX_PLANNED_SAPLING_TARGETS);
        for (BlockPos planned : HiredWorkPlan.targets(context)) {
            HiredPathTarget target = bestSaplingTarget(level, villager, context, planned);
            if (target != null && safeValidator.test(target.blockPos())) {
                return target;
            }
        }
        HiredWorkPlan.clear(context);
        return null;
    }

    private HiredPathTarget bestSaplingTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target) {
        if (!context.isInsideWorkArea(target)) {
            return null;
        }
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(target),
                MAX_LOGGING_TARGETS_TO_PATHFIND,
                context::isInsideWorkArea,
                pos -> canUseSaplingMovementPosition(level, context, pos),
                pos -> canUseSaplingPathPosition(level, villager, context, pos),
                ignored -> false).search().target();
    }

    private static boolean hasTreePlan(HiredWorkContext context) {
        return hasPlanObjective(context, TREE_OBJECTIVE, TREE_ROUTE_OBJECTIVE, SINGLE_TREE_OBJECTIVE, GROVE_OBJECTIVE);
    }

    private static boolean hasSaplingPlan(HiredWorkContext context) {
        return hasPlanObjective(context, SAPLING_ROUTE_OBJECTIVE, SINGLE_SAPLING_OBJECTIVE);
    }

    private static boolean hasPlanObjective(HiredWorkContext context, String... objectives) {
        String objective = HiredWorkPlan.objectiveType(context);
        for (String expected : objectives) {
            if (expected.equals(objective)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTreeScanInProgress(HiredWorkContext context) {
        return HiredWorkAreaScan.isInProgress(context, TREE_SCAN_CURSOR_TAG);
    }

    private WorkResult workBlockingLeaf(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget treeTarget,
            BlockPos blockingLeaf) {
        HiredPathTarget leafTarget = choosePhysicalReachableTarget(level, villager, context, List.of(blockingLeaf));
        if (leafTarget != null) {
            if (canBreakAccessLeafFromCurrentPosition(level, villager, context, leafTarget)) {
                return breakBlockingLeafInPlace(level, villager, context, treeTarget, leafTarget.blockPos());
            }
            return workTreeAccessLeaf(level, villager, context, leafTarget);
        }

        clearActiveBreakingTarget(level, context, villager);
        HiredWorkerBrain.setFailure(context, "leaf_blocked_target", level.getGameTime() + 40L);
        setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, blockingLeaf);
        return WorkResult.idle("interaction.work.logging.blocked_target");
    }

    private WorkResult breakBlockingLeafInPlace(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget treeTarget,
            BlockPos leaf) {
        clearWorkPathFailure(villager, leaf);
        holdWorkPosition(villager, treeTarget);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, treeTarget.blockPos());

        LeafBreakResult leafBreakResult = breakAccessLeaf(level, context, villager, leaf);
        if (leafBreakResult == LeafBreakResult.BLOCKED) {
            HiredWorkerBrain.setFailure(context, "access_leaf_blocked", level.getGameTime() + 40L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, leaf);
            return WorkResult.idle("interaction.work.logging.blocked_target");
        }
        if (leafBreakResult == LeafBreakResult.OUTPUT_FULL) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.DEPOSITED) {
                return WorkResult.progressed("interaction.work.logging.clearing_access_leaf");
            }
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.logging.output_full_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, leaf);
            return WorkResult.idle("interaction.work.logging.output_full_blocked");
        }

        return WorkResult.progressed("interaction.work.logging.clearing_access_leaf");
    }

    private WorkResult clearBlockingLeafForTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target) {
        BlockPos blockingLeaf = firstBlockingLeaf(level, villager, target);
        if (blockingLeaf == null) {
            return null;
        }
        context.setProgressTicks(0);
        return workBlockingLeaf(level, villager, context, target, blockingLeaf);
    }

    private WorkResult clearBlockingLeafTowardPositions(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> positions) {
        for (BlockPos pos : HiredWorkPlan.routeOrder(villager.blockPosition(), positions, MAX_LOGGING_TARGETS_TO_PATHFIND)) {
            if (!context.isInsideWorkArea(pos) || !context.isLoaded(level, pos)) {
                continue;
            }
            BlockPos blockingLeaf = firstBlockingLeaf(level, villager, pos, pos.getCenter());
            if (blockingLeaf == null) {
                continue;
            }
            HiredPathTarget leafTarget = choosePhysicalReachableTarget(level, villager, context, List.of(blockingLeaf));
            if (leafTarget == null) {
                continue;
            }
            context.setProgressTicks(0);
            return workTreeAccessLeaf(level, villager, context, leafTarget);
        }
        return null;
    }

    private WorkResult continueActiveAccessLeaf(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        if (active == null || !LoggingWorkerState.isAccessLeaf(context, active.blockPos())) {
            LoggingWorkerState.clearAccessLeaf(context);
            return null;
        }
        if (!context.isLoaded(level, active.blockPos()) || !isNaturalLeaf(level.getBlockState(active.blockPos()))) {
            LoggingWorkerState.clearAccessLeaf(context);
            clearActiveBreakingTarget(level, context, villager);
            return null;
        }
        return workTreeAccessLeaf(level, villager, context, active);
    }

    private WorkResult workTreeAccessLeaf(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target) {
        LoggingWorkerState.markAccessLeaf(context, target.blockPos());
        prepareBreakingTarget(level, context, villager, target);
        if (!canBreakAccessLeafFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            if (!moveToAccessLeafTarget(level, villager, context, target, 0.55D)) {
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    HiredWorkPlan.removeTarget(context, target.blockPos());
                    LoggingWorkerState.clearAccessLeaf(context);
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "access_leaf_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
                    return WorkResult.idle("interaction.work.logging.blocked_target");
                }
                return WorkResult.progressed("interaction.work.logging.repositioning");
            }
            return WorkResult.progressed("interaction.work.logging.moving_to_target");
        }

        clearWorkPathFailure(villager, target.blockPos());
        holdWorkPosition(villager, target);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, target.blockPos());

        LeafBreakResult leafBreakResult = breakAccessLeaf(level, context, villager, target.blockPos());
        if (leafBreakResult == LeafBreakResult.BLOCKED) {
            HiredWorkPlan.removeTarget(context, target.blockPos());
            LoggingWorkerState.clearAccessLeaf(context);
            clearActiveBreakingTarget(level, context, villager);
            HiredWorkerBrain.setFailure(context, "access_leaf_blocked", level.getGameTime() + 40L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
            return WorkResult.idle("interaction.work.logging.blocked_target");
        }
        if (leafBreakResult == LeafBreakResult.OUTPUT_FULL) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.DEPOSITED) {
                return WorkResult.progressed("interaction.work.logging.clearing_access_leaf");
            }
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.logging.output_full_depositing");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, target.blockPos());
            return WorkResult.idle("interaction.work.logging.output_full_blocked");
        }

        HiredWorkPlan.removeTarget(context, target.blockPos());
        LoggingWorkerState.clearAccessLeaf(context);
        clearActiveBreakingTarget(level, context, villager);
        LoggingWorkerState.clearTargetSearch(context);
        return WorkResult.progressed("interaction.work.logging.clearing_access_leaf");
    }

    private HiredPathTarget choosePhysicalReachableTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Iterable<BlockPos> targets) {
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                targets,
                MAX_LOGGING_TARGETS_TO_PATHFIND,
                context::isInsideWorkArea).search().target();
    }

    private static boolean canBreakAccessLeafFromCurrentPosition(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target) {
        return context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(target.blockPos())
                && context.isLoaded(level, target.blockPos())
                && context.isLoaded(level, target.approachPos())
                && HiredMoveToBlockFaceJob.canReachFromCurrentPosition(level, villager, target);
    }

    private boolean moveToAccessLeafTarget(
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
        if (canBreakAccessLeafFromCurrentPosition(level, villager, context, target)) {
            holdWorkPosition(villager, target);
            return true;
        }

        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null && !HiredMoveToBlockFaceJob.pathStaysInsideFilter(currentPath, context::isInsideWorkArea)) {
            stopWorkNavigation(villager);
            return false;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()))) {
                stopWorkNavigation(villager);
                return false;
            }
            return true;
        }

        Path path = HiredPathMemory.createPath(level, villager, target.approachPos(), 0);
        if (path != null && path.canReach() && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.blockPos()));
            boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target.approachPos(), speed, 0);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        target.approachPos(),
                        villager.distanceToSqr(target.approachPos().getCenter()));
            } else {
                HiredPathMemory.clearNavigationProgress(villager);
            }
            return moved;
        }

        if (villager.distanceToSqr(target.approachPos().getCenter()) <= 2.25D
                && settleIntoApproach(villager, target, speed)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return true;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private WorkResult collectOptionalLeafTool(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!HiredLoggingOptions.harvestLeaves(context.state())
                || !context.inventory().findTool(LoggingWorker::isLeafHarvestTool).isEmpty()
                || !context.useAssignedStorageForSupplies()) {
            return null;
        }
        BlockPos storage = AssignedStorageService.nearestAssignedToolStoragePosContaining(
                level,
                villager,
                LoggingWorker::isLeafHarvestTool);
        if (storage == null) {
            return null;
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result result = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                0.55D);
        if (result == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, storage);
            return WorkResult.progressed("interaction.work.logging.collecting_leaf_tool");
        }
        if (result == HiredStorageNavigationGoal.Result.FAILED) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            return null;
        }

        int moved = AssignedStorageService.transferToolAtAssignedStorage(
                villager,
                storage,
                LoggingWorker::isLeafHarvestTool,
                context.inventory()::insertToolFromStorage);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (moved <= 0) {
            return null;
        }
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
        return WorkResult.progressed("interaction.work.logging.collected_leaf_tool");
    }

    private WorkResult collectDecayDrops(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!HiredLoggingOptions.pickUpDecayDrops(context.state()) || !canSeekDecayDrops(level, villager, context)) {
            return null;
        }
        return HiredItemPickup.collectNearestOutputItem(
                level,
                villager,
                context,
                this,
                LoggingWorker::isTreeDecayDrop,
                DECAY_DROP_PICKUP_REACH_SQR,
                0.55D,
                DECAY_DROP_PICKUP_MESSAGES);
    }

    private boolean canSeekDecayDrops(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (context.progressTicks() > 0) {
            return false;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (worker.taskState().keepsStorageTarget()
                || worker.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA
                || worker.taskState() == HiredWorkerTaskState.PAUSED_FULL_INVENTORY
                || worker.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL
                || worker.taskState() == HiredWorkerTaskState.PAUSED_NO_STORAGE
                || worker.taskState() == HiredWorkerTaskState.PAUSED_MISSING_TOOL) {
            return false;
        }
        return activeWorkTarget(level, context, villager) == null;
    }

    private static boolean isTreeDecayDrop(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(Items.STICK)
                || stack.is(Items.APPLE)
                || stack.is(ItemTags.SAPLINGS)
                || stack.is(Items.MANGROVE_PROPAGULE)
                || stack.is(Items.CRIMSON_FUNGUS)
                || stack.is(Items.WARPED_FUNGUS));
    }

    private WorkResult tryBonemealSapling(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!HiredLoggingOptions.bonemealSaplings(context.state()) || !hasBoneMealAvailable(villager, context)) {
            return null;
        }

        HiredPathTarget target = findSaplingToBonemeal(level, villager, context);
        if (target == null) {
            if (isSaplingScanInProgress(context)) {
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("interaction.work.logging.searching_saplings");
            }
            return null;
        }

        prepareBreakingTarget(level, context, villager, target);
        if (!canBonemealSaplingFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            if (!moveToSaplingTarget(level, villager, context, target, 0.55D)) {
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "sapling_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
                    return WorkResult.idle("interaction.work.logging.sapling_blocked");
                }
                return WorkResult.progressed("interaction.work.logging.sapling_repositioning");
            }
            return WorkResult.progressed("interaction.work.logging.moving_to_sapling");
        }

        clearWorkPathFailure(villager, target.blockPos());
        holdWorkPosition(villager, target);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, target.blockPos());
        faceBlock(villager, target);
        swingWorkTool(villager);
        if (!applyBoneMeal(level, villager, context, target.blockPos())) {
            HiredWorkPlan.removeTarget(context, target.blockPos());
            clearActiveBreakingTarget(level, context, villager);
            HiredWorkerBrain.setFailure(context, "sapling_changed", level.getGameTime() + 40L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
            return WorkResult.idle("interaction.work.logging.sapling_changed");
        }
        if (isBonemealableSapling(level, target.blockPos())) {
            prioritizeSaplingPlan(context, target.blockPos());
        } else {
            HiredWorkPlan.removeTarget(context, target.blockPos());
            LoggingWorkerState.wakeTreeSearch(context);
        }
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return WorkResult.progressed("interaction.work.logging.bonemealing_sapling");
    }

    private static boolean canBonemealSaplingFromCurrentPosition(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target) {
        return canUseSaplingMovementPosition(level, context, villager.blockPosition())
                && context.isInsideWorkArea(target.blockPos())
                && context.isLoaded(level, target.blockPos())
                && context.isLoaded(level, target.approachPos())
                && HiredMoveToBlockFaceJob.canReachFromCurrentPosition(level, villager, target);
    }

    private boolean moveToSaplingTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            double speed) {
        if (!context.isInsideWorkArea(target.blockPos())
                || !canUseSaplingMovementPosition(level, context, target.approachPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())) {
            return false;
        }
        if (canBonemealSaplingFromCurrentPosition(level, villager, context, target)) {
            holdWorkPosition(villager, target);
            return true;
        }

        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null && !HiredMoveToBlockFaceJob.pathStaysInsideFilter(currentPath, pos -> canUseSaplingPathPosition(level, villager, context, pos))) {
            stopWorkNavigation(villager);
            return false;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()))) {
                stopWorkNavigation(villager);
                return false;
            }
            return true;
        }

        Path path = HiredPathMemory.createPath(level, villager, target.approachPos(), 0);
        if (path != null && path.canReach() && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, pos -> canUseSaplingPathPosition(level, villager, context, pos))) {
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.blockPos()));
            boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target.approachPos(), speed, 0);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        target.approachPos(),
                        villager.distanceToSqr(target.approachPos().getCenter()));
            } else {
                HiredPathMemory.clearNavigationProgress(villager);
            }
            return moved;
        }

        if (villager.distanceToSqr(target.approachPos().getCenter()) <= 2.25D
                && settleIntoApproach(villager, target, speed)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return true;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private HiredPathTarget findSaplingToBonemeal(ServerLevel level, Villager villager, HiredWorkContext context) {
        return HiredTargetSearch.find(
                level,
                context,
                () -> activeWorkTarget(level, context, villager),
                target -> context.isInsideWorkArea(target.blockPos())
                        && context.isLoaded(level, target.blockPos())
                        && !isTemporarilyAvoidedTarget(level, villager, target.blockPos())
                        && isBonemealableSapling(level, target.blockPos()),
                candidateFilter -> plannedSaplingTarget(level, villager, context, candidateFilter),
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos)
                        && isBonemealableSapling(level, pos),
                NEXT_SAPLING_SCAN_GAME_TIME_TAG,
                SAPLING_SCAN_CURSOR_TAG,
                MAX_SAPLING_SCAN_POSITIONS_PER_WORK_TICK,
                candidates -> rebuildSaplingObjective(level, villager, context, candidates),
                SAPLING_SEARCH_MESSAGES);
    }

    private HiredPathTarget rebuildSaplingObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates) {
        List<BlockPos> ordered = HiredWorkPlan.routeOrder(villager.blockPosition(), candidates, MAX_PLANNED_SAPLING_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? SAPLING_ROUTE_OBJECTIVE : SINGLE_SAPLING_OBJECTIVE,
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_SAPLING_TARGETS);
        return plannedSaplingTarget(
                level,
                villager,
                context,
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos)
                        && isBonemealableSapling(level, pos));
    }

    private static void prioritizeSaplingPlan(HiredWorkContext context, BlockPos target) {
        List<BlockPos> targets = new ArrayList<>();
        targets.add(target.immutable());
        if (hasSaplingPlan(context)) {
            for (BlockPos planned : HiredWorkPlan.targets(context)) {
                if (!planned.equals(target)) {
                    targets.add(planned);
                }
            }
        }
        HiredWorkPlan.replaceWithObjective(
                context,
                targets.size() > 1 ? SAPLING_ROUTE_OBJECTIVE : SINGLE_SAPLING_OBJECTIVE,
                target,
                targets,
                MAX_PLANNED_SAPLING_TARGETS);
    }

    private static boolean isSaplingScanInProgress(HiredWorkContext context) {
        return HiredWorkAreaScan.isInProgress(context, SAPLING_SCAN_CURSOR_TAG);
    }

    private static boolean canUseSaplingMovementPosition(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        return context.isInsideWorkArea(pos)
                && context.isLoaded(level, pos)
                && context.isLoaded(level, pos.above())
                && !isSaplingStandingPosition(level, pos);
    }

    private static boolean canUseSaplingPathPosition(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos) {
        return pos.equals(villager.blockPosition()) || canUseSaplingMovementPosition(level, context, pos);
    }

    private static boolean isSaplingStandingPosition(ServerLevel level, BlockPos pos) {
        return isSaplingBlock(level, pos) || isSaplingBlock(level, pos.above());
    }

    private static boolean isSaplingBlock(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && isTreePlantingBlock(level.getBlockState(pos));
    }

    private static boolean isBonemealableSapling(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return isTreePlantingBlock(state)
                && state.getBlock() instanceof BonemealableBlock bonemealable
                && bonemealable.isValidBonemealTarget(level, pos, state);
    }

    private static boolean isTreePlantingBlock(BlockState state) {
        return state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.CRIMSON_FUNGUS)
                || state.is(Blocks.WARPED_FUNGUS);
    }

    private static boolean hasBoneMealAvailable(Villager villager, HiredWorkContext context) {
        if (!context.inventory().findSupply(stack -> stack.is(Items.BONE_MEAL)).isEmpty()) {
            return true;
        }
        return context.useAssignedStorageForSupplies()
                && AssignedStorageService.countItems(villager, stack -> stack.is(Items.BONE_MEAL)) > 0;
    }

    private static boolean applyBoneMeal(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos) {
        if (!hasBoneMealAvailable(villager, context)) {
            return false;
        }
        if (context.consumeSupply(villager, stack -> stack.is(Items.BONE_MEAL), 1) <= 0) {
            return false;
        }
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL);
        if (!BoneMealItem.applyBonemeal(boneMeal, level, pos, null)) {
            ItemStack remainder = context.inventory().insertSupply(new ItemStack(Items.BONE_MEAL));
            if (!remainder.isEmpty()) {
                Block.popResource(level, villager.blockPosition(), remainder);
            }
            return false;
        }
        level.levelEvent(1505, pos, 15);
        return true;
    }

    private static boolean isTreeLog(
            ServerLevel level,
            BlockPos pos,
            Set<ResourceLocation> filters,
            LoggingTreeGeometry.DiscoveryCache trees,
            BlockPos validatedTarget) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        return pos.equals(validatedTarget)
                ? isMatchingLog(level.getBlockState(pos), filters)
                : trees.isNaturalTree(pos);
    }

    private static boolean isTreeLog(
            ServerLevel level,
            BlockPos pos,
            LoggingTreeGeometry.DiscoveryCache trees) {
        return level.hasChunkAt(pos) && trees.isNaturalTree(pos);
    }

    private HiredPathTarget rebuildTreeObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            LoggingTreeGeometry.DiscoveryCache trees) {
        List<BlockPos> grove = bestGrovePlan(villager, candidates, trees);
        if (!grove.isEmpty()) {
            HiredWorkPlan.replaceWithObjective(
                    context,
                    grove.size() > 1 ? GROVE_OBJECTIVE : TREE_OBJECTIVE,
                    grove.getFirst(),
                    grove,
                    MAX_PLANNED_TREE_TARGETS);
            HiredPathTarget target = plannedTreeTarget(
                    level,
                    villager,
                    context,
                    pos -> context.isInsideWorkArea(pos)
                            && context.isLoaded(level, pos)
                            && !isTemporarilyAvoidedTarget(level, villager, pos)
                            && isTreeLog(level, pos, trees));
            if (target != null) {
                return target;
            }
        }

        List<BlockPos> ordered = HiredWorkPlan.routeOrder(villager.blockPosition(), candidates, MAX_PLANNED_TREE_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? TREE_ROUTE_OBJECTIVE : SINGLE_TREE_OBJECTIVE,
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_TREE_TARGETS);
        return plannedTreeTarget(
                level,
                villager,
                context,
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos)
                        && isTreeLog(level, pos, trees));
    }

    private static List<BlockPos> bestGrovePlan(
            Villager villager,
            List<BlockPos> candidates,
            LoggingTreeGeometry.DiscoveryCache trees) {
        List<BlockPos> roots = trees.distinctRoots(candidates);
        if (roots.isEmpty()) {
            return List.of();
        }

        List<BlockPos> bestCluster = List.of();
        double bestScore = Double.NEGATIVE_INFINITY;
        int linkRadiusSqr = GROVE_LINK_RADIUS * GROVE_LINK_RADIUS;
        for (BlockPos root : roots) {
            List<BlockPos> cluster = new ArrayList<>();
            for (BlockPos other : roots) {
                if (root.distSqr(other) <= linkRadiusSqr) {
                    cluster.add(other);
                }
            }
            List<BlockPos> ordered = HiredWorkPlan.routeOrder(root, cluster, MAX_PLANNED_TREE_TARGETS);
            double score = ordered.size() * 1000.0D - villager.distanceToSqr(root.getCenter());
            if (!ordered.isEmpty() && score > bestScore) {
                bestCluster = ordered;
                bestScore = score;
            }
        }
        return bestCluster;
    }

    private TreeHarvestResult harvestTree(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            HiredPathTarget target,
            ItemStack axe) {
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            return TreeHarvestResult.TARGET_CHANGED;
        }

        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        List<BlockPos> logs = LoggingTreeGeometry.connectedTreeLogs(level, target.blockPos(), filters);
        if (logs.isEmpty()) {
            return TreeHarvestResult.TARGET_CHANGED;
        }

        boolean stripLogs = HiredLoggingOptions.stripLogs(context.state());
        List<BlockPos> leaves = naturalTreeLeaves(level, logs);
        ItemStack sapling = HiredLoggingOptions.plantSaplings(context.state())
                ? saplingForTree(level, logs)
                : ItemStack.EMPTY;
        List<BlockPos> saplingPositions = sapling.isEmpty()
                ? List.of()
                : saplingPlantingPositions(level, context, logs);
        LoggingHarvestPlan.begin(
                context,
                target.blockPos(),
                target.approachPos(),
                logs,
                leaves,
                saplingPositions,
                sapling,
                stripLogs,
                logFamilyKey(level.getBlockState(target.blockPos())));
        return processPendingTreeHarvest(level, context, villager, axe);
    }

    private TreeHarvestResult processPendingTreeHarvest(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            ItemStack axe) {
        TreeHarvestResult logResult = harvestPendingLogs(level, context, villager, axe);
        if (logResult == TreeHarvestResult.OUTPUT_FULL || logResult == TreeHarvestResult.TARGET_CHANGED) {
            return logResult;
        }
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
        if (plan == null) {
            return TreeHarvestResult.TARGET_CHANGED;
        }
        if (plan.hasLogs()) {
            return TreeHarvestResult.progressed(plan.logsCut());
        }
        if (!harvestPendingLeaves(level, context, villager)) {
            return TreeHarvestResult.OUTPUT_FULL;
        }
        plan = LoggingHarvestPlan.read(context);
        if (plan == null) {
            return TreeHarvestResult.TARGET_CHANGED;
        }
        if (plan.hasLeaves()) {
            return TreeHarvestResult.progressed(plan.logsCut());
        }

        restoreLoggingAxe(context, level, plan.origin());
        if (!plan.sapling().isEmpty() && plan.saplings().length > 0) {
            boolean waitingForSapling = plantPendingSapling(level, context, villager, plan.sapling());
            plan = LoggingHarvestPlan.read(context);
            if (waitingForSapling) {
                return TreeHarvestResult.progressed(plan == null ? 0 : plan.logsCut());
            }
            if (plan != null && plan.saplings().length > 0) {
                return TreeHarvestResult.progressed(plan.logsCut());
            }
        }
        int logsCut = plan == null ? 0 : plan.logsCut();
        return logsCut <= 0 ? TreeHarvestResult.TARGET_CHANGED : TreeHarvestResult.completed(logsCut);
    }

    private TreeHarvestResult harvestPendingLogs(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            ItemStack axe) {
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
        if (plan == null) {
            return TreeHarvestResult.TARGET_CHANGED;
        }
        long[] pendingLogs = plan.logs();
        if (pendingLogs.length <= 0) {
            return TreeHarvestResult.progressed(plan.logsCut());
        }

        String logFamily = plan.logFamily();
        if (logFamily.isBlank()) {
            logFamily = inferPendingLogFamily(level, context, pendingLogs);
            LoggingHarvestPlan.rememberLogFamily(context, logFamily);
        }
        boolean stripLogs = plan.stripLogs();
        List<Long> remaining = new ArrayList<>(pendingLogs.length);
        int cut = 0;
        for (int index = 0; index < pendingLogs.length; index++) {
            long packedLog = pendingLogs[index];
            BlockPos log = BlockPos.of(packedLog);
            if (!context.isInsideWorkArea(log)) {
                continue;
            }
            if (!context.isLoaded(level, log)) {
                remaining.add(packedLog);
                continue;
            }
            BlockState state = level.getBlockState(log);
            if (!isPendingTreeLog(state, logFamily)) {
                continue;
            }
            if (cut >= MAX_TREE_LOGS_PER_HARVEST_TICK) {
                remaining.add(packedLog);
                continue;
            }

            ItemStack activeAxe = context.inventory().equipBestTool(
                    stack -> stack.is(ItemTags.AXES),
                    stack -> effectiveDestroySpeed(stack, state));
            if (!activeAxe.isEmpty()) {
                axe = activeAxe;
            }
            if (axe.isEmpty()) {
                remaining.add(packedLog);
                for (int rest = index + 1; rest < pendingLogs.length; rest++) {
                    remaining.add(pendingLogs[rest]);
                }
                break;
            }

            BlockState strippedState = stripLogs ? strippedLogState(state) : null;
            BlockState harvestState = strippedState == null ? state : strippedState;
            boolean stripped = !harvestState.equals(state);
            List<ItemStack> drops = Block.getDrops(harvestState, level, log, level.getBlockEntity(log), villager, axe);
            if (!context.canStoreOutputs(drops)) {
                context.depositOutputs(villager);
            }
            if (!context.canStoreOutputs(drops)) {
                remaining.add(packedLog);
                for (int rest = index + 1; rest < pendingLogs.length; rest++) {
                    remaining.add(pendingLogs[rest]);
                }
                LoggingHarvestPlan.replaceLogs(context, packedPositions(remaining));
                return TreeHarvestResult.OUTPUT_FULL;
            }
            faceBlock(villager, log);
            swingWorkTool(villager);
            EnchantmentHelper.onHitBlock(level, axe, villager, villager, EquipmentSlot.MAINHAND, log.getCenter(), harvestState, ignored -> {
            });
            boolean destroyed = level.destroyBlock(log, false, villager);
            if (!destroyed) {
                level.destroyBlockProgress(villager.getId(), log, -1);
                if (isPendingTreeLog(level.getBlockState(log), logFamily)) {
                    remaining.add(packedLog);
                    for (int rest = index + 1; rest < pendingLogs.length; rest++) {
                        remaining.add(pendingLogs[rest]);
                    }
                    LoggingHarvestPlan.replaceLogs(context, packedPositions(remaining));
                    return TreeHarvestResult.TARGET_CHANGED;
                }
                HiredPathMemory.onBlockChanged(level, log);
                continue;
            }
            HiredPathMemory.onBlockChanged(level, log);
            level.destroyBlockProgress(villager.getId(), log, -1);
            for (ItemStack drop : drops) {
                context.storeOutputAfterDepositIfFull(villager, drop);
            }
            damageTool(context, villager, axe, level, harvestState, log);
            if (stripped && !axe.isEmpty()) {
                damageTool(context, villager, axe);
            }
            HiredPathMemory.rememberRecent(level, log);
            cut++;
            LoggingHarvestPlan.incrementLogsCut(context);
        }

        LoggingHarvestPlan.replaceLogs(context, packedPositions(remaining));
        LoggingHarvestPlan.Snapshot updated = LoggingHarvestPlan.read(context);
        return TreeHarvestResult.progressed(updated == null ? plan.logsCut() + cut : updated.logsCut());
    }

    private static BlockState strippedLogState(BlockState state) {
        return AxeItem.getAxeStrippingState(state);
    }

    private static BlockPos firstBlockingLeaf(ServerLevel level, Villager villager, HiredPathTarget target) {
        return firstBlockingLeaf(level, villager, target.blockPos(), target.hitPos());
    }

    private static BlockPos firstBlockingLeaf(ServerLevel level, Villager villager, BlockPos target, Vec3 hitPos) {
        if (!level.hasChunkAt(target)) {
            return null;
        }
        ClipContext.Block blockMode = level.getBlockState(target)
                .getCollisionShape(level, target, CollisionContext.empty())
                .isEmpty()
                ? ClipContext.Block.OUTLINE
                : ClipContext.Block.COLLIDER;
        BlockHitResult hit = level.clip(new ClipContext(
                villager.getEyePosition(),
                hitPos,
                blockMode,
                ClipContext.Fluid.NONE,
                villager));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().equals(target)) {
            return null;
        }
        BlockPos hitBlock = hit.getBlockPos();
        return level.hasChunkAt(hitBlock) && isNaturalLeaf(level.getBlockState(hitBlock)) ? hitBlock : null;
    }

    private LeafBreakResult breakAccessLeaf(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos leaf) {
        if (!context.isInsideWorkArea(leaf) || !context.isLoaded(level, leaf)) {
            return LeafBreakResult.BLOCKED;
        }
        BlockState state = level.getBlockState(leaf);
        if (!isNaturalLeaf(state)) {
            return LeafBreakResult.BLOCKED;
        }
        ItemStack leafTool = HiredLoggingOptions.harvestLeaves(context.state())
                ? context.inventory().equipBestTool(
                        LoggingWorker::isLeafHarvestTool,
                        LoggingWorker::leafToolScore)
                : ItemStack.EMPTY;
        List<ItemStack> drops = Block.getDrops(state, level, leaf, level.getBlockEntity(leaf), villager, leafTool);
        if (!context.canStoreOutputs(drops)) {
            context.depositOutputs(villager);
        }
        if (!context.canStoreOutputs(drops)) {
            restoreLoggingAxe(context, level, leaf);
            return LeafBreakResult.OUTPUT_FULL;
        }
        faceBlock(villager, leaf);
        swingWorkTool(villager);
        EnchantmentHelper.onHitBlock(level, leafTool, villager, villager, EquipmentSlot.MAINHAND, leaf.getCenter(), state, ignored -> {
        });
        boolean removed = level.destroyBlock(leaf, false, villager);
        level.destroyBlockProgress(villager.getId(), leaf, -1);
        boolean changed = !isNaturalLeaf(level.getBlockState(leaf));
        if (removed) {
            for (ItemStack drop : drops) {
                context.storeOutputAfterDepositIfFull(villager, drop);
            }
            HiredPathMemory.onBlockChanged(level, leaf);
            damageTool(context, villager, leafTool, level, state, leaf);
            HiredPathMemory.rememberRecent(level, leaf);
        } else if (changed) {
            HiredPathMemory.onBlockChanged(level, leaf);
        }
        restoreLoggingAxe(context, level, leaf);
        return removed || changed ? LeafBreakResult.BROKEN : LeafBreakResult.BLOCKED;
    }

    private boolean harvestPendingLeaves(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager) {
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
        if (plan == null || plan.leaves().length <= 0) {
            return true;
        }

        long[] pendingLeaves = plan.leaves();
        List<Long> remaining = new ArrayList<>(pendingLeaves.length);
        int harvested = 0;
        for (int index = 0; index < pendingLeaves.length; index++) {
            long packedLeaf = pendingLeaves[index];
            BlockPos leaf = BlockPos.of(packedLeaf);
            if (!context.isInsideWorkArea(leaf)) {
                continue;
            }
            if (!context.isLoaded(level, leaf)) {
                remaining.add(packedLeaf);
                continue;
            }
            BlockState state = level.getBlockState(leaf);
            if (!isNaturalLeaf(state)) {
                continue;
            }
            if (harvested >= MAX_TREE_LEAVES_PER_HARVEST_TICK) {
                remaining.add(packedLeaf);
                continue;
            }

            ItemStack leafTool = HiredLoggingOptions.harvestLeaves(context.state())
                    ? context.inventory().equipBestTool(
                            LoggingWorker::isLeafHarvestTool,
                            LoggingWorker::leafToolScore)
                    : ItemStack.EMPTY;
            List<ItemStack> drops = Block.getDrops(state, level, leaf, level.getBlockEntity(leaf), villager, leafTool);
            if (!context.canStoreOutputs(drops)) {
                context.depositOutputs(villager);
            }
            if (!context.canStoreOutputs(drops)) {
                remaining.add(packedLeaf);
                for (int rest = index + 1; rest < pendingLeaves.length; rest++) {
                    remaining.add(pendingLeaves[rest]);
                }
                LoggingHarvestPlan.replaceLeaves(context, packedPositions(remaining));
                restoreLoggingAxe(context, level, leaf);
                return false;
            }

            faceBlock(villager, leaf);
            swingWorkTool(villager);
            if (!leafTool.isEmpty()) {
                EnchantmentHelper.onHitBlock(level, leafTool, villager, villager, EquipmentSlot.MAINHAND, leaf.getCenter(), state, ignored -> {
                });
            }
            boolean removed = level.destroyBlock(leaf, false, villager);
            level.destroyBlockProgress(villager.getId(), leaf, -1);
            boolean changed = !isNaturalLeaf(level.getBlockState(leaf));
            if (removed) {
                for (ItemStack drop : drops) {
                    context.storeOutputAfterDepositIfFull(villager, drop);
                }
                HiredPathMemory.onBlockChanged(level, leaf);
                if (!leafTool.isEmpty()) {
                    damageTool(context, villager, leafTool, level, state, leaf);
                }
                HiredPathMemory.rememberRecent(level, leaf);
                harvested++;
            } else if (changed) {
                HiredPathMemory.onBlockChanged(level, leaf);
            }
        }

        LoggingHarvestPlan.replaceLeaves(context, packedPositions(remaining));
        restoreLoggingAxe(context, level, plan.origin());
        return true;
    }

    private static void restoreLoggingAxe(HiredWorkContext context, ServerLevel level, BlockPos referencePos) {
        BlockState referenceState = context.isLoaded(level, referencePos)
                ? level.getBlockState(referencePos)
                : net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
        context.inventory().equipBestTool(
                stack -> stack.is(ItemTags.AXES),
                stack -> stack.getDestroySpeed(referenceState));
    }

    private static boolean isPendingLeafTarget(HiredWorkContext context, BlockPos pos) {
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
        return plan != null && LoggingHarvestPlan.contains(plan.leaves(), pos);
    }

    private static BlockPos pendingTreeOrigin(HiredWorkContext context) {
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
        return plan == null ? context.workCenter() : plan.origin();
    }

    private static BlockState firstPendingLogState(
            ServerLevel level,
            HiredWorkContext context,
            LoggingHarvestPlan.Snapshot plan) {
        String logFamily = plan.logFamily();
        if (logFamily.isBlank()) {
            logFamily = inferPendingLogFamily(level, context, plan.logs());
            LoggingHarvestPlan.rememberLogFamily(context, logFamily);
        }
        for (long packed : plan.logs()) {
            BlockPos pos = BlockPos.of(packed);
            if (!context.isInsideWorkArea(pos) || !context.isLoaded(level, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (isPendingTreeLog(state, logFamily)) {
                return state;
            }
        }
        return null;
    }

    private static PendingHarvestTargets pendingHarvestTargets(
            ServerLevel level,
            HiredWorkContext context,
            LoggingHarvestPlan.Snapshot plan) {
        String logFamily = plan.logFamily();
        if (logFamily.isBlank()) {
            logFamily = inferPendingLogFamily(level, context, plan.logs());
            LoggingHarvestPlan.rememberLogFamily(context, logFamily);
        }
        String expectedLogFamily = logFamily;
        List<BlockPos> logs = pendingMatchingPositions(
                level,
                context,
                plan.logs(),
                state -> isPendingTreeLog(state, expectedLogFamily),
                LoggingHarvestPlan.MAX_LOGS);
        if (plan.hasLogs()) {
            return new PendingHarvestTargets(logs, PendingTargetKind.LOG);
        }
        for (long packed : plan.leaves()) {
            BlockPos leaf = BlockPos.of(packed);
            if (context.isLoaded(level, leaf) && !isNaturalLeaf(level.getBlockState(leaf))) {
                LoggingHarvestPlan.removeLeaf(context, leaf);
            }
        }
        plan = LoggingHarvestPlan.read(context);
        if (plan == null) {
            return new PendingHarvestTargets(List.of(), PendingTargetKind.LEAF);
        }
        List<BlockPos> leaves = pendingMatchingPositions(
                level,
                context,
                plan.leaves(),
                LoggingTreeGeometry::isNaturalLeaf,
                LoggingHarvestPlan.MAX_LEAVES);
        if (plan.hasLeaves()) {
            return new PendingHarvestTargets(leaves, PendingTargetKind.LEAF);
        }

        ItemStack sapling = plan.sapling();
        if (!sapling.isEmpty() && sapling.getItem() instanceof BlockItem blockItem) {
            BlockState saplingState = blockItem.getBlock().defaultBlockState();
            List<BlockPos> plantingTargets = new ArrayList<>();
            for (long packed : plan.saplings()) {
                BlockPos plantingPos = BlockPos.of(packed);
                if (!context.isLoaded(level, plantingPos)) {
                    continue;
                }
                if (canPlaceSapling(level, context, plantingPos, saplingState)) {
                    plantingTargets.add(plantingPos.below().immutable());
                } else {
                    LoggingHarvestPlan.removeSapling(context, plantingPos);
                }
            }
            if (!plantingTargets.isEmpty()) {
                return new PendingHarvestTargets(plantingTargets, PendingTargetKind.SAPLING);
            }
        }
        return new PendingHarvestTargets(List.of(), PendingTargetKind.SAPLING);
    }

    private static List<BlockPos> pendingMatchingPositions(
            ServerLevel level,
            HiredWorkContext context,
            long[] packedPositions,
            Predicate<BlockState> matcher,
            int maxPositions) {
        List<BlockPos> positions = new ArrayList<>();
        int safeMaxPositions = Math.max(1, maxPositions);
        for (long packed : packedPositions) {
            BlockPos pos = BlockPos.of(packed);
            if (!context.isInsideWorkArea(pos) || !context.isLoaded(level, pos)) {
                continue;
            }
            if (matcher.test(level.getBlockState(pos))) {
                positions.add(pos.immutable());
                if (positions.size() >= safeMaxPositions) {
                    break;
                }
            }
        }
        return positions;
    }

    private static void clearPendingTreeLeaves(HiredWorkContext context) {
        LoggingHarvestPlan.clearLeaves(context);
    }

    private static long[] packedPositions(List<Long> positions) {
        long[] packed = new long[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            packed[i] = positions.get(i);
        }
        return packed;
    }

    private static String inferPendingLogFamily(
            ServerLevel level,
            HiredWorkContext context,
            long[] pendingLogs) {
        for (long packed : pendingLogs) {
            BlockPos pos = BlockPos.of(packed);
            if (!context.isLoaded(level, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS)) {
                return logFamilyKey(state);
            }
        }
        return "";
    }

    private static boolean isPendingTreeLog(BlockState state, String logFamily) {
        return state.is(BlockTags.LOGS)
                && (logFamily == null || logFamily.isBlank() || logFamilyKey(state).equals(logFamily));
    }

    static boolean isLeafHarvestTool(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(Items.SHEARS)
                || (stack.is(ItemTags.HOES) && hasSilkTouch(stack)));
    }

    private static boolean hasSilkTouch(ItemStack stack) {
        ResourceLocation silkTouchId = ResourceLocation.withDefaultNamespace("silk_touch");
        return stack.getEnchantments().entrySet().stream().anyMatch(entry ->
                entry.getIntValue() > 0
                        && entry.getKey().unwrapKey()
                        .map(key -> key.location().equals(silkTouchId))
                        .orElse(false));
    }

    private static double leafToolScore(ItemStack stack) {
        if (stack.is(Items.SHEARS)) {
            return 100.0D;
        }
        if (stack.is(ItemTags.HOES)) {
            return 50.0D + stack.getMaxDamage() / 1000.0D;
        }
        return 0.0D;
    }

    private boolean plantPendingSapling(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            ItemStack sapling) {
        if (!(sapling.getItem() instanceof BlockItem blockItem)) {
            LoggingHarvestPlan.clearSaplings(context);
            return false;
        }
        HiredPathTarget target = storedWorkTarget(context.state());
        if (target == null || !canPlantFromCurrentPosition(level, villager, context, target)) {
            return false;
        }
        BlockPos pos = target.blockPos().above();
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
        if (plan == null || !LoggingHarvestPlan.contains(plan.saplings(), pos)) {
            return false;
        }
        BlockState saplingState = blockItem.getBlock().defaultBlockState();
        if (!canPlaceSapling(level, context, pos, saplingState)) {
            LoggingHarvestPlan.removeSapling(context, pos);
            clearActiveBreakingTarget(level, context, villager);
            return false;
        }
        if (!consumeSapling(villager, context, sapling)) {
            clearActiveBreakingTarget(level, context, villager);
            return true;
        }
        facePlacedSapling(villager, pos);
        swingWorkItem(level, villager, sapling);
        if (!level.setBlock(pos, saplingState, Block.UPDATE_ALL)) {
            ItemStack remainder = context.inventory().insertSupply(sapling.copyWithCount(1));
            if (!remainder.isEmpty()) {
                Block.popResource(level, villager.blockPosition(), remainder);
            }
            clearActiveBreakingTarget(level, context, villager);
            return false;
        }
        HiredPathMemory.onBlockChanged(level, pos);
        HiredPathMemory.rememberRecent(level, pos);
        LoggingHarvestPlan.removeSapling(context, pos);
        clearActiveBreakingTarget(level, context, villager);
        LoggingWorkerState.wakeSaplingSearch(context);
        return false;
    }

    private static void facePlacedSapling(Villager villager, BlockPos pos) {
        villager.getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 60.0F, 60.0F);
    }

    private static boolean canPlaceSapling(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos pos,
            BlockState saplingState) {
        return context.isInsideWorkArea(pos)
                && context.isLoaded(level, pos)
                && level.getBlockState(pos).canBeReplaced()
                && saplingState.canSurvive(level, pos);
    }

    private static boolean consumeSapling(Villager villager, HiredWorkContext context, ItemStack sapling) {
        if (!context.inventory().consumeOutput(stack -> matchesPlantingItem(stack, sapling), 1).isEmpty()) {
            return true;
        }
        return context.consumeSupply(villager, stack -> matchesPlantingItem(stack, sapling), 1) > 0;
    }

    private static boolean matchesPlantingItem(ItemStack stack, ItemStack plantingItem) {
        return !stack.isEmpty()
                && !plantingItem.isEmpty()
                && (ItemStack.isSameItemSameComponents(stack, plantingItem) || stack.is(plantingItem.getItem()));
    }

    private int adjustedTreeHarvestProgressGoal(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos origin,
            ItemStack axe) {
        String toolId = BuiltInRegistries.ITEM.getKey(axe.getItem()).toString();
        return LoggingWorkerState.breakGoal(
                context,
                origin,
                toolId,
                efficiencyLevel(axe),
                100,
                () -> calculateTreeHarvestProgressGoal(level, context, origin, axe));
    }

    private int calculateTreeHarvestProgressGoal(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos origin,
            ItemStack axe) {
        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        List<BlockPos> logs = LoggingTreeGeometry.connectedTreeLogs(level, origin, filters);
        int total = 0;
        for (BlockPos log : logs) {
            if (!context.isInsideWorkArea(log) || !context.isLoaded(level, log)) {
                continue;
            }
            BlockState state = level.getBlockState(log);
            if (isMatchingLog(state, filters)) {
                total += breakProgressGoal(level, log, axe);
            }
        }
        if (total <= 0) {
            total = breakProgressGoal(level, origin, axe);
        }
        return Math.clamp(total, 1, MAX_TREE_PROGRESS_TICKS);
    }

    private record ExitObstruction(BlockPos pos, boolean leaf) {
    }

    private record PendingHarvestTargets(List<BlockPos> positions, PendingTargetKind kind) {
    }

    private enum PendingTargetKind {
        LOG,
        LEAF,
        SAPLING
    }

    private record TreeHarvestResult(int logsCut, boolean completed, boolean outputFull, boolean targetChanged) {
        private static final TreeHarvestResult OUTPUT_FULL = new TreeHarvestResult(0, false, true, false);
        private static final TreeHarvestResult TARGET_CHANGED = new TreeHarvestResult(0, false, false, true);

        private static TreeHarvestResult progressed(int logsCut) {
            return new TreeHarvestResult(logsCut, false, false, false);
        }

        private static TreeHarvestResult completed(int logsCut) {
            return new TreeHarvestResult(logsCut, true, false, false);
        }

        private boolean inProgress() {
            return !this.completed && !this.outputFull && !this.targetChanged;
        }
    }

    private enum LeafBreakResult {
        BROKEN,
        OUTPUT_FULL,
        BLOCKED
    }
}
