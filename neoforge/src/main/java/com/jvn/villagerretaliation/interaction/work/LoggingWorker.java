package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class LoggingWorker extends AbstractBlockWorker {
    private static final String NEXT_WORK_GAME_TIME_TAG = "NextWorkGameTime";
    private static final String NEXT_TREE_SCAN_GAME_TIME_TAG = "NextLoggingTreeScanGameTime";
    private static final String TREE_SCAN_CURSOR_TAG = "LoggingTreeScanCursor";
    private static final String NEXT_ACCESS_LEAF_SCAN_GAME_TIME_TAG = "NextLoggingAccessLeafScanGameTime";
    private static final String ACCESS_LEAF_SCAN_CURSOR_TAG = "LoggingAccessLeafScanCursor";
    private static final String PENDING_TREE_ORIGIN_TAG = "PendingLoggingTreeOrigin";
    private static final String PENDING_TREE_LOGS_TAG = "PendingLoggingTreeLogs";
    private static final String PENDING_TREE_LEAVES_TAG = "PendingLoggingTreeLeaves";
    private static final String PENDING_TREE_SAPLINGS_TAG = "PendingLoggingTreeSaplings";
    private static final String PENDING_TREE_SAPLING_ITEM_TAG = "PendingLoggingTreeSaplingItem";
    private static final String PENDING_TREE_STRIP_LOGS_TAG = "PendingLoggingTreeStripLogs";
    private static final String PENDING_TREE_LOGS_CUT_TAG = "PendingLoggingTreeLogsCut";
    private static final int MAX_TREE_LOGS_PER_HARVEST = 96;
    private static final int MAX_TREE_LOGS_PER_HARVEST_TICK = 10;
    private static final int MAX_TREE_HORIZONTAL_DISTANCE = 8;
    private static final int MAX_TREE_VERTICAL_DISTANCE = 24;
    private static final int MIN_NATURAL_LEAVES = 4;
    private static final int MAX_TREE_SCAN_POSITIONS_PER_WORK_TICK = 512;
    private static final int MAX_ACCESS_LEAF_SCAN_POSITIONS_PER_WORK_TICK = 768;
    private static final String NEXT_SAPLING_SCAN_GAME_TIME_TAG = "NextLoggingSaplingScanGameTime";
    private static final String SAPLING_SCAN_CURSOR_TAG = "LoggingSaplingScanCursor";
    private static final int MAX_SAPLING_SCAN_POSITIONS_PER_WORK_TICK = 768;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;
    private static final int MAX_TREE_PROGRESS_TICKS = 180;
    private static final int MAX_LOGGING_TARGETS_TO_PATHFIND = 64;
    private static final int MAX_PLANNED_TREE_TARGETS = 12;
    private static final int MAX_PLANNED_ACCESS_LEAF_TARGETS = 8;
    private static final int MAX_PLANNED_SAPLING_TARGETS = 8;
    private static final int MAX_TREE_LEAVES_PER_HARVEST = 192;
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
    private static final HiredTargetSearch.Messages ACCESS_LEAF_SEARCH_MESSAGES = new HiredTargetSearch.Messages(
            "active_tree_access_leaf",
            "planned_tree_access_leaf",
            "tree_access_leaf_scan_cooldown",
            "tree_access_leaf_scan_full_no_reachable_targets",
            "tree_access_leaf_scan_partial_",
            "tree_access_leaf_target_found",
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
        CompoundTag state = context.state();
        String pending = hasPendingTreeHarvest(context)
                ? "pending logs=" + state.getLongArray(PENDING_TREE_LOGS_TAG).length
                + ", leaves=" + state.getLongArray(PENDING_TREE_LEAVES_TAG).length
                + ", saplings=" + state.getLongArray(PENDING_TREE_SAPLINGS_TAG).length
                + ", cut=" + state.getInt(PENDING_TREE_LOGS_CUT_TAG)
                : "pending none";
        return "Logging: " + pending
                + ", treeScan=" + scanState(context, TREE_SCAN_CURSOR_TAG, NEXT_TREE_SCAN_GAME_TIME_TAG)
                + ", accessLeafScan=" + scanState(context, ACCESS_LEAF_SCAN_CURSOR_TAG, NEXT_ACCESS_LEAF_SCAN_GAME_TIME_TAG)
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
            clearPendingTreeHarvest(context);
            clearTreeTargetSearch(context);
            return;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (worker.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET && worker.targetPos() != null) {
            HiredPathTarget active = activeWorkTarget(level, context, villager);
            if (active != null) {
                Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
                boolean physicalLeafTarget = isPendingLeafTarget(context, active.blockPos())
                        || isTreeAccessLeaf(level, active.blockPos(), filters);
                boolean canWork = physicalLeafTarget
                        ? canBreakAccessLeafFromCurrentPosition(level, villager, context, active)
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
            clearPendingTreeHarvest(context);
            clearTreeTargetSearch(context);
            return waitForWorkAreaAssignment(level, villager, context);
        }

        WorkResult pendingHarvestResult = continuePendingTreeHarvest(level, villager, context);
        if (pendingHarvestResult != null) {
            return pendingHarvestResult;
        }

        WorkResult activeAccessLeafResult = continueActiveAccessLeaf(level, villager, context);
        if (activeAccessLeafResult != null) {
            return activeAccessLeafResult;
        }

        WorkResult decayDropResult = collectDecayDrops(level, villager, context);
        if (decayDropResult != null) {
            return decayDropResult;
        }

        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        HiredPathTarget target = findTreeLog(level, villager, context);
        if (target == null) {
            clearActiveBreakingTarget(level, context, villager);
            if (isTreeScanInProgress(context)) {
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("interaction.work.logging.searching_scan");
            }
            WorkResult accessLeafResult = clearTreeAccessLeaf(level, villager, context);
            if (accessLeafResult != null) {
                return accessLeafResult;
            }
            WorkResult bonemealResult = tryBonemealSapling(level, villager, context);
            if (bonemealResult != null) {
                return bonemealResult;
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
                HiredWorkerBrain.setFailure(context, "tool_storage_unreachable", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, toolResult.storagePos());
                return WorkResult.idle("interaction.work.status.tool_storage_unreachable");
            }
            if (toolResult.status() == ToolStorageStatus.INVENTORY_FULL) {
                HiredWorkerBrain.setFailure(context, "tool_inventory_full", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, toolResult.storagePos());
                return WorkResult.idle("interaction.work.status.tool_inventory_full");
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
                WorkResult accessLeafResult = clearTreeAccessLeaf(level, villager, context);
                if (accessLeafResult != null) {
                    return accessLeafResult;
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
                harvestResult = harvestTree(level, context, villager, target, axe);
                if (harvestResult.inProgress()) {
                    return WorkResult.progressed("interaction.work.logging.working_target");
                }
                if (harvestResult.completed()) {
                    HiredWorkPlan.removeTarget(context, target.blockPos());
                    clearActiveBreakingTarget(level, context, villager);
                    setTaskState(context, HiredWorkerTaskState.IDLE);
                    return completedTreeWork(context, harvestResult.logsCut());
                }
            }
            if (depositResult == DepositResult.DEPOSITED && harvestResult == TreeHarvestResult.OUTPUT_FULL) {
                return WorkResult.progressed("interaction.work.logging.output_full_depositing");
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
            HiredWorkPlan.removeTarget(context, target.blockPos());
            clearActiveBreakingTarget(level, context, villager);
            HiredWorkerBrain.setFailure(context, "target_changed", level.getGameTime() + 40L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN);
            return WorkResult.idle("interaction.work.logging.target_changed");
        }
        HiredWorkPlan.removeTarget(context, target.blockPos());
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return completedTreeWork(context, harvestResult.logsCut());
    }

    @Override
    public void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        clearPendingTreeHarvest(context);
        clearTreeTargetSearch(context);
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
                LoggingWorker::isNaturalLeaf).search().target();
    }

    @Override
    protected boolean canMineFromCurrentPosition(ServerLevel level, Villager villager, HiredPathTarget target) {
        return HiredMoveToBlockFaceJob.canReachFromCurrentPosition(level, villager, target, LoggingWorker::isNaturalLeaf);
    }

    @Override
    protected boolean hasLineOfSightToBlock(ServerLevel level, Villager villager, Vec3 start, BlockPos target, Vec3 hitPos) {
        return HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, start, target, hitPos, LoggingWorker::isNaturalLeaf);
    }

    private WorkResult continuePendingTreeHarvest(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!hasPendingTreeHarvest(context)) {
            return null;
        }
        if (!context.isInsideWorkArea(villager.blockPosition())) {
            setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
            return WorkResult.progressed("interaction.work.status.returning_bounds");
        }
        WorkResult activeAccessLeafResult = continueActiveAccessLeaf(level, villager, context);
        if (activeAccessLeafResult != null) {
            return activeAccessLeafResult;
        }

        ItemStack axe = ItemStack.EMPTY;
        BlockState nextLogState = firstPendingLogState(level, context);
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
                    HiredWorkerBrain.setFailure(context, "tool_storage_unreachable", level.getGameTime() + 100L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, toolResult.storagePos());
                    return WorkResult.idle("interaction.work.status.tool_storage_unreachable");
                }
                if (toolResult.status() == ToolStorageStatus.INVENTORY_FULL) {
                    HiredWorkerBrain.setFailure(context, "tool_inventory_full", level.getGameTime() + 100L);
                    setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, toolResult.storagePos());
                    return WorkResult.idle("interaction.work.status.tool_inventory_full");
                }
                HiredWorkerBrain.setFailure(context, "missing_axe", 0L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
                return WorkResult.idle("interaction.work.logging.missing_axe");
            }
            axe = toolResult.tool();
        }

        WorkResult positioningResult = moveToPendingHarvestTarget(level, villager, context);
        if (positioningResult != null) {
            return positioningResult;
        }

        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, pendingTreeOrigin(context));
        TreeHarvestResult harvestResult = processPendingTreeHarvest(level, context, villager, axe);
        if (harvestResult.inProgress()) {
            return WorkResult.progressed(pendingHarvestStatus(context));
        }
        if (harvestResult == TreeHarvestResult.OUTPUT_FULL) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.DEPOSITED) {
                if (!context.isInsideWorkArea(villager.blockPosition())) {
                    setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                    return WorkResult.progressed("interaction.work.status.returning_bounds");
                }
                harvestResult = processPendingTreeHarvest(level, context, villager, axe);
                if (harvestResult.inProgress()) {
                    return WorkResult.progressed(pendingHarvestStatus(context));
                }
                if (harvestResult.completed()) {
                    return completePendingTreeHarvest(level, villager, context, harvestResult);
                }
            }
            if (depositResult == DepositResult.DEPOSITED && harvestResult == TreeHarvestResult.OUTPUT_FULL) {
                return WorkResult.progressed("interaction.work.logging.output_full_depositing");
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
            clearPendingTreeHarvest(context);
            clearActiveBreakingTarget(level, context, villager);
            HiredWorkerBrain.setFailure(context, "target_changed", level.getGameTime() + 40L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN);
            return WorkResult.idle("interaction.work.logging.target_changed");
        }
        return completePendingTreeHarvest(level, villager, context, harvestResult);
    }

    private WorkResult moveToPendingHarvestTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        PendingHarvestTargets candidates = pendingHarvestTargets(level, context);
        if (candidates.positions().isEmpty()) {
            return null;
        }
        HiredPathTarget target = candidates.leaves()
                ? choosePhysicalReachableTarget(level, villager, context, candidates.positions())
                : chooseReachableTarget(level, villager, context, candidates.positions());
        if (target == null) {
            if (candidates.leaves()) {
                clearPendingTreeLeaves(context);
                clearActiveBreakingTarget(level, context, villager);
                return null;
            }
            WorkResult accessLeafResult = clearTreeAccessLeaf(level, villager, context);
            if (accessLeafResult != null) {
                return accessLeafResult;
            }
            BlockPos origin = pendingTreeOrigin(context);
            if (recordWorkPathFailure(level, villager, origin)) {
                clearPendingTreeHarvest(context);
                clearActiveBreakingTarget(level, context, villager);
                HiredWorkerBrain.setFailure(context, "pending_tree_unreachable", level.getGameTime() + 20L * 30L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, origin);
                return WorkResult.idle("interaction.work.logging.blocked_target");
            }
            return WorkResult.progressed("interaction.work.logging.repositioning");
        }

        prepareBreakingTarget(level, context, villager, target);
        boolean canWork = candidates.leaves()
                ? canBreakAccessLeafFromCurrentPosition(level, villager, context, target)
                : canWorkFromCurrentPosition(level, villager, context, target);
        if (!canWork) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            boolean moved = candidates.leaves()
                    ? moveToAccessLeafTarget(level, villager, context, target, 0.55D)
                    : moveToTarget(level, villager, context, target, 0.55D);
            if (!moved) {
                if (candidates.leaves()) {
                    removePendingPosition(context, PENDING_TREE_LEAVES_TAG, target.blockPos());
                    clearActiveBreakingTarget(level, context, villager);
                    return null;
                }
                WorkResult accessLeafResult = clearTreeAccessLeaf(level, villager, context);
                if (accessLeafResult != null) {
                    return accessLeafResult;
                }
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    clearPendingTreeHarvest(context);
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

    private WorkResult completePendingTreeHarvest(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            TreeHarvestResult harvestResult) {
        HiredWorkPlan.removeTarget(context, pendingTreeOrigin(context));
        clearPendingTreeHarvest(context);
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return completedTreeWork(context, harvestResult.logsCut());
    }

    private static String pendingHarvestStatus(HiredWorkContext context) {
        return !hasPendingLogs(context) && hasPendingLeaves(context)
                ? "interaction.work.logging.clearing_access_leaf"
                : "interaction.work.logging.working_target";
    }

    private static WorkResult completedTreeWork(HiredWorkContext context, int logsCut) {
        context.state().remove(NEXT_WORK_GAME_TIME_TAG);
        wakeTreeTargetSearch(context);
        return WorkResult.skilledProgress(
                "interaction.work.logging.completed",
                Map.of("logs", Integer.toString(logsCut)));
    }

    private HiredPathTarget findTreeLog(ServerLevel level, Villager villager, HiredWorkContext context) {
        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        return HiredTargetSearch.find(
                level,
                context,
                () -> activeWorkTarget(level, context, villager),
                target -> context.isInsideWorkArea(target.blockPos())
                        && context.isLoaded(level, target.blockPos())
                        && !isTemporarilyAvoidedTarget(level, villager, target.blockPos())
                        && isTreeLog(level, target.blockPos(), filters),
                candidateFilter -> plannedTarget(level, villager, context, candidateFilter, MAX_PLANNED_TREE_TARGETS),
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos)
                        && isTreeLog(level, pos, filters),
                NEXT_TREE_SCAN_GAME_TIME_TAG,
                TREE_SCAN_CURSOR_TAG,
                MAX_TREE_SCAN_POSITIONS_PER_WORK_TICK,
                candidates -> rebuildTreeObjective(level, villager, context, candidates, filters),
                TREE_SEARCH_MESSAGES);
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
        WorkResult fallback = clearTreeAccessLeaf(level, villager, context);
        if (fallback != null) {
            return fallback;
        }

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

    private WorkResult continueActiveAccessLeaf(ServerLevel level, Villager villager, HiredWorkContext context) {
        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        if (active == null || !isTreeAccessLeaf(level, active.blockPos(), filters)) {
            return null;
        }
        return workTreeAccessLeaf(level, villager, context, active);
    }

    private WorkResult clearTreeAccessLeaf(ServerLevel level, Villager villager, HiredWorkContext context) {
        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        HiredPathTarget target = findTreeAccessLeaf(level, villager, context);
        if (target == null) {
            if (isAccessLeafScanInProgress(context)) {
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("interaction.work.logging.searching_scan");
            }
            return null;
        }
        return workTreeAccessLeaf(level, villager, context, target);
    }

    private WorkResult workTreeAccessLeaf(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target) {
        prepareBreakingTarget(level, context, villager, target);
        if (!canBreakAccessLeafFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            if (!moveToAccessLeafTarget(level, villager, context, target, 0.55D)) {
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    HiredWorkPlan.removeTarget(context, target.blockPos());
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
        clearActiveBreakingTarget(level, context, villager);
        clearTreeTargetSearch(context);
        return WorkResult.progressed("interaction.work.logging.clearing_access_leaf");
    }

    private HiredPathTarget findTreeAccessLeaf(ServerLevel level, Villager villager, HiredWorkContext context) {
        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        return HiredTargetSearch.find(
                level,
                context,
                () -> activeWorkTarget(level, context, villager),
                target -> context.isInsideWorkArea(target.blockPos())
                        && context.isLoaded(level, target.blockPos())
                        && !isTemporarilyAvoidedTarget(level, villager, target.blockPos())
                        && isTreeAccessLeaf(level, target.blockPos(), filters),
                candidateFilter -> plannedAccessLeafTarget(level, villager, context, candidateFilter),
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos)
                        && isTreeAccessLeaf(level, pos, filters),
                NEXT_ACCESS_LEAF_SCAN_GAME_TIME_TAG,
                ACCESS_LEAF_SCAN_CURSOR_TAG,
                MAX_ACCESS_LEAF_SCAN_POSITIONS_PER_WORK_TICK,
                candidates -> rebuildAccessLeafObjective(level, villager, context, candidates, filters),
                ACCESS_LEAF_SEARCH_MESSAGES);
    }

    private HiredPathTarget rebuildAccessLeafObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            Set<ResourceLocation> filters) {
        List<BlockPos> ordered = HiredWorkPlan.routeOrder(villager.blockPosition(), candidates, MAX_PLANNED_ACCESS_LEAF_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? "tree_access_leaf_route" : "tree_access_leaf",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_ACCESS_LEAF_TARGETS);
        return plannedAccessLeafTarget(
                level,
                villager,
                context,
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos)
                        && isTreeAccessLeaf(level, pos, filters));
    }

    private HiredPathTarget plannedAccessLeafTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Predicate<BlockPos> validator) {
        Predicate<BlockPos> safeValidator = validator == null ? ignored -> true : validator;
        HiredWorkPlan.retainMatching(context, safeValidator, MAX_PLANNED_ACCESS_LEAF_TARGETS);
        for (BlockPos planned : HiredWorkPlan.targets(context)) {
            HiredPathTarget target = choosePhysicalReachableTarget(level, villager, context, List.of(planned));
            if (target != null && safeValidator.test(target.blockPos())) {
                return target;
            }
        }
        HiredWorkPlan.clear(context);
        return null;
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

        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
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

    private static boolean isAccessLeafScanInProgress(HiredWorkContext context) {
        return HiredWorkAreaScan.isInProgress(context, ACCESS_LEAF_SCAN_CURSOR_TAG);
    }

    private static void clearTreeTargetSearch(HiredWorkContext context) {
        HiredWorkAreaScan.clearCursor(context, TREE_SCAN_CURSOR_TAG);
        HiredWorkAreaScan.clearCursor(context, ACCESS_LEAF_SCAN_CURSOR_TAG);
        wakeTreeTargetSearch(context);
    }

    private static void wakeTreeTargetSearch(HiredWorkContext context) {
        context.state().remove(NEXT_TREE_SCAN_GAME_TIME_TAG);
        context.state().remove(NEXT_ACCESS_LEAF_SCAN_GAME_TIME_TAG);
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
                context.inventory()::insertTool);
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
                || stack.is(Items.MANGROVE_PROPAGULE));
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
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            if (!moveToTarget(level, villager, context, target, 0.55D)) {
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
        HiredWorkPlan.removeTarget(context, target.blockPos());
        clearActiveBreakingTarget(level, context, villager);
        HiredPathMemory.rememberRecent(level, target.blockPos());
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return WorkResult.progressed("interaction.work.logging.bonemealing_sapling");
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
                candidateFilter -> plannedTarget(level, villager, context, candidateFilter, MAX_PLANNED_SAPLING_TARGETS),
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
                ordered.size() > 1 ? "sapling_route" : "single_sapling",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_SAPLING_TARGETS);
        return plannedTarget(
                level,
                villager,
                context,
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos)
                        && isBonemealableSapling(level, pos),
                MAX_PLANNED_SAPLING_TARGETS);
    }

    private static boolean isSaplingScanInProgress(HiredWorkContext context) {
        return HiredWorkAreaScan.isInProgress(context, SAPLING_SCAN_CURSOR_TAG);
    }

    private static boolean isBonemealableSapling(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.SAPLINGS)
                && state.getBlock() instanceof BonemealableBlock bonemealable
                && bonemealable.isValidBonemealTarget(level, pos, state);
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
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL);
        if (!BoneMealItem.applyBonemeal(boneMeal, level, pos, null)) {
            return false;
        }
        if (context.consumeSupply(villager, stack -> stack.is(Items.BONE_MEAL), 1) <= 0) {
            return false;
        }
        level.levelEvent(1505, pos, 15);
        return true;
    }

    private static boolean isTreeLog(
            ServerLevel level,
            BlockPos pos,
            Set<ResourceLocation> filters) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return isMatchingLog(state, filters)
                && hasNearbyNaturalLeaves(level, pos)
                && isLikelyNaturalTree(level, pos, filters);
    }

    private static boolean isTreeAccessLeaf(
            ServerLevel level,
            BlockPos pos,
            Set<ResourceLocation> filters) {
        if (!level.hasChunkAt(pos) || !isNaturalLeaf(level.getBlockState(pos))) {
            return false;
        }
        for (BlockPos rawLog : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 4, 4))) {
            BlockPos log = rawLog.immutable();
            if (level.hasChunkAt(log)
                    && isMatchingLog(level.getBlockState(log), filters)
                    && isLikelyNaturalTree(level, log, filters)) {
                return true;
            }
        }
        return false;
    }

    private HiredPathTarget rebuildTreeObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            Set<ResourceLocation> filters) {
        List<BlockPos> grove = bestGrovePlan(level, villager, candidates, filters);
        if (!grove.isEmpty()) {
            HiredWorkPlan.replaceWithObjective(
                    context,
                    grove.size() > 1 ? "grove" : "tree",
                    grove.getFirst(),
                    grove,
                    MAX_PLANNED_TREE_TARGETS);
            HiredPathTarget target = plannedTarget(
                    level,
                    villager,
                    context,
                    pos -> context.isInsideWorkArea(pos)
                            && context.isLoaded(level, pos)
                            && !isTemporarilyAvoidedTarget(level, villager, pos)
                            && isTreeLog(level, pos, filters),
                    MAX_PLANNED_TREE_TARGETS);
            if (target != null) {
                return target;
            }
        }

        List<BlockPos> ordered = HiredWorkPlan.routeOrder(villager.blockPosition(), candidates, MAX_PLANNED_TREE_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? "tree_route" : "single_tree",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_TREE_TARGETS);
        return plannedTarget(
                level,
                villager,
                context,
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos)
                        && isTreeLog(level, pos, filters),
                MAX_PLANNED_TREE_TARGETS);
    }

    private static List<BlockPos> bestGrovePlan(
            ServerLevel level,
            Villager villager,
            List<BlockPos> candidates,
            Set<ResourceLocation> filters) {
        List<BlockPos> roots = distinctTreeRoots(level, candidates, filters);
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

    private static List<BlockPos> distinctTreeRoots(
            ServerLevel level,
            List<BlockPos> candidates,
            Set<ResourceLocation> filters) {
        Set<Long> seenRoots = new HashSet<>();
        List<BlockPos> roots = new ArrayList<>();
        for (BlockPos candidate : candidates) {
            List<BlockPos> logs = connectedTreeLogs(level, candidate, filters);
            if (logs.isEmpty()) {
                continue;
            }
            BlockPos root = treeRoot(logs);
            if (root != null && seenRoots.add(root.asLong())) {
                roots.add(root.immutable());
            }
        }
        return roots;
    }

    private static BlockPos treeRoot(List<BlockPos> logs) {
        BlockPos root = null;
        for (BlockPos log : logs) {
            if (root == null
                    || log.getY() < root.getY()
                    || (log.getY() == root.getY() && log.asLong() < root.asLong())) {
                root = log;
            }
        }
        return root;
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
        List<BlockPos> logs = connectedTreeLogs(level, target.blockPos(), filters);
        if (logs.isEmpty()) {
            return TreeHarvestResult.TARGET_CHANGED;
        }

        boolean stripLogs = HiredLoggingOptions.stripLogs(context.state());
        boolean harvestLeaves = HiredLoggingOptions.harvestLeaves(context.state());
        ItemStack leafTool = harvestLeaves
                ? bestLeafTool(context)
                : ItemStack.EMPTY;
        List<BlockPos> leaves = harvestLeaves
                ? naturalTreeLeaves(level, logs)
                : List.of();
        ItemStack sapling = HiredLoggingOptions.plantSaplings(context.state())
                ? saplingForTree(level, logs)
                : ItemStack.EMPTY;
        List<BlockPos> saplingPositions = sapling.isEmpty()
                ? List.of()
                : saplingPlantingPositions(level, context, logs);
        List<ItemStack> drops = treeDrops(level, context, villager, logs, filters, axe, stripLogs, leaves, leafTool);
        if (!context.canStoreOutputs(drops)) {
            context.depositOutputs(villager);
        }
        if (!context.canStoreOutputs(drops)) {
            return TreeHarvestResult.OUTPUT_FULL;
        }

        beginPendingTreeHarvest(context, target.blockPos(), logs, leaves, saplingPositions, sapling, stripLogs);
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
        if (hasPendingLogs(context)) {
            return TreeHarvestResult.progressed(context.state().getInt(PENDING_TREE_LOGS_CUT_TAG));
        }
        if (!harvestPendingLeaves(level, context, villager)) {
            return TreeHarvestResult.OUTPUT_FULL;
        }
        if (hasPendingLeaves(context)) {
            return TreeHarvestResult.progressed(context.state().getInt(PENDING_TREE_LOGS_CUT_TAG));
        }

        restoreLoggingAxe(context, level, pendingTreeOrigin(context));
        ItemStack sapling = pendingSapling(context);
        if (!sapling.isEmpty()) {
            plantSaplings(level, context, villager, pendingPositions(context, PENDING_TREE_SAPLINGS_TAG), sapling);
        }
        int logsCut = context.state().getInt(PENDING_TREE_LOGS_CUT_TAG);
        return logsCut <= 0 ? TreeHarvestResult.TARGET_CHANGED : TreeHarvestResult.completed(logsCut);
    }

    private TreeHarvestResult harvestPendingLogs(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            ItemStack axe) {
        long[] pendingLogs = context.state().getLongArray(PENDING_TREE_LOGS_TAG);
        if (pendingLogs.length <= 0) {
            return TreeHarvestResult.progressed(context.state().getInt(PENDING_TREE_LOGS_CUT_TAG));
        }

        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        boolean stripLogs = context.state().getBoolean(PENDING_TREE_STRIP_LOGS_TAG);
        int processed = 0;
        int cut = 0;
        while (processed < pendingLogs.length && cut < MAX_TREE_LOGS_PER_HARVEST_TICK) {
            BlockPos log = BlockPos.of(pendingLogs[processed]);
            if (!context.isInsideWorkArea(log) || !context.isLoaded(level, log)) {
                processed++;
                continue;
            }
            BlockState state = level.getBlockState(log);
            if (!isMatchingLog(state, filters)) {
                processed++;
                continue;
            }

            ItemStack activeAxe = context.inventory().equipBestTool(
                    stack -> stack.is(ItemTags.AXES),
                    stack -> effectiveDestroySpeed(stack, state));
            if (!activeAxe.isEmpty()) {
                axe = activeAxe;
            }
            if (axe.isEmpty()) {
                break;
            }

            BlockState harvestState = stripLogs ? stripLog(level, log, state) : state;
            boolean stripped = !harvestState.equals(state);
            for (ItemStack drop : Block.getDrops(harvestState, level, log, level.getBlockEntity(log), villager, axe)) {
                if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                    context.state().putLongArray(PENDING_TREE_LOGS_TAG, Arrays.copyOfRange(pendingLogs, processed, pendingLogs.length));
                    return TreeHarvestResult.OUTPUT_FULL;
                }
            }
            faceBlock(villager, log);
            swingWorkTool(villager);
            EnchantmentHelper.onHitBlock(level, axe, villager, villager, EquipmentSlot.MAINHAND, log.getCenter(), harvestState, ignored -> {
            });
            level.destroyBlock(log, false, villager);
            level.destroyBlockProgress(villager.getId(), log, -1);
            damageTool(context, villager, axe);
            if (stripped && !axe.isEmpty()) {
                damageTool(context, villager, axe);
            }
            HiredPathMemory.rememberRecent(level, log);
            processed++;
            cut++;
            context.state().putInt(PENDING_TREE_LOGS_CUT_TAG, context.state().getInt(PENDING_TREE_LOGS_CUT_TAG) + 1);
            if (axe.isEmpty()) {
                break;
            }
        }

        context.state().putLongArray(PENDING_TREE_LOGS_TAG, Arrays.copyOfRange(pendingLogs, processed, pendingLogs.length));
        return TreeHarvestResult.progressed(context.state().getInt(PENDING_TREE_LOGS_CUT_TAG));
    }

    private static List<ItemStack> treeDrops(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            List<BlockPos> logs,
            Set<ResourceLocation> filters,
            ItemStack axe,
            boolean stripLogs,
            List<BlockPos> leaves,
            ItemStack leafTool) {
        List<ItemStack> drops = new ArrayList<>();
        for (BlockPos log : logs) {
            if (!context.isInsideWorkArea(log) || !context.isLoaded(level, log)) {
                continue;
            }
            BlockState state = level.getBlockState(log);
            if (isMatchingLog(state, filters)) {
                BlockState dropState = stripLogs ? strippedLogState(state) : null;
                drops.addAll(Block.getDrops(dropState == null ? state : dropState, level, log, level.getBlockEntity(log), villager, axe));
            }
        }
        if (!leaves.isEmpty()) {
            for (BlockPos leaf : leaves) {
                if (!context.isInsideWorkArea(leaf) || !context.isLoaded(level, leaf)) {
                    continue;
                }
                BlockState state = level.getBlockState(leaf);
                if (isNaturalLeaf(state)) {
                    drops.addAll(Block.getDrops(state, level, leaf, level.getBlockEntity(leaf), villager, leafTool));
                }
            }
        }
        return drops;
    }

    private static BlockState strippedLogState(BlockState state) {
        return AxeItem.getAxeStrippingState(state);
    }

    private static BlockState stripLog(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState stripped = strippedLogState(state);
        if (stripped == null || stripped.equals(state)) {
            return state;
        }
        return level.setBlock(pos, stripped, Block.UPDATE_ALL) ? stripped : state;
    }

    private static BlockPos firstBlockingLeaf(ServerLevel level, Villager villager, HiredPathTarget target) {
        if (!level.hasChunkAt(target.blockPos())) {
            return null;
        }
        ClipContext.Block blockMode = level.getBlockState(target.blockPos())
                .getCollisionShape(level, target.blockPos(), CollisionContext.empty())
                .isEmpty()
                ? ClipContext.Block.OUTLINE
                : ClipContext.Block.COLLIDER;
        BlockHitResult hit = level.clip(new ClipContext(
                villager.getEyePosition(),
                target.hitPos(),
                blockMode,
                ClipContext.Fluid.NONE,
                villager));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().equals(target.blockPos())) {
            return null;
        }
        BlockPos hitPos = hit.getBlockPos();
        return level.hasChunkAt(hitPos) && isNaturalLeaf(level.getBlockState(hitPos)) ? hitPos : null;
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
        for (ItemStack drop : Block.getDrops(state, level, leaf, level.getBlockEntity(leaf), villager, leafTool)) {
            if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                restoreLoggingAxe(context, level, leaf);
                return LeafBreakResult.OUTPUT_FULL;
            }
        }
        faceBlock(villager, leaf);
        swingWorkTool(villager);
        EnchantmentHelper.onHitBlock(level, leafTool, villager, villager, EquipmentSlot.MAINHAND, leaf.getCenter(), state, ignored -> {
        });
        boolean removed = level.destroyBlock(leaf, false, villager);
        level.destroyBlockProgress(villager.getId(), leaf, -1);
        if (removed || !isNaturalLeaf(level.getBlockState(leaf))) {
            damageTool(context, villager, leafTool);
            HiredPathMemory.rememberRecent(level, leaf);
        }
        restoreLoggingAxe(context, level, leaf);
        return removed || !isNaturalLeaf(level.getBlockState(leaf)) ? LeafBreakResult.BROKEN : LeafBreakResult.BLOCKED;
    }

    private boolean harvestPendingLeaves(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager) {
        long[] pendingLeaves = context.state().getLongArray(PENDING_TREE_LEAVES_TAG);
        if (pendingLeaves.length <= 0) {
            return true;
        }

        HiredPathTarget target = activeWorkTarget(level, context, villager);
        if (target == null
                || !containsPendingPosition(pendingLeaves, target.blockPos())
                || !canBreakAccessLeafFromCurrentPosition(level, villager, context, target)) {
            return true;
        }

        BlockPos leaf = target.blockPos();
        LeafBreakResult result = breakAccessLeaf(level, context, villager, leaf);
        if (result == LeafBreakResult.OUTPUT_FULL) {
            return false;
        }
        removePendingPosition(context, PENDING_TREE_LEAVES_TAG, leaf);
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

    private static void beginPendingTreeHarvest(
            HiredWorkContext context,
            BlockPos origin,
            List<BlockPos> logs,
            List<BlockPos> leaves,
            List<BlockPos> saplingPositions,
            ItemStack sapling,
            boolean stripLogs) {
        CompoundTag state = context.state();
        state.putLong(PENDING_TREE_ORIGIN_TAG, origin.asLong());
        state.putLongArray(PENDING_TREE_LOGS_TAG, positionsToArray(logs));
        state.putLongArray(PENDING_TREE_LEAVES_TAG, positionsToArray(leaves));
        state.putLongArray(PENDING_TREE_SAPLINGS_TAG, positionsToArray(saplingPositions));
        state.putBoolean(PENDING_TREE_STRIP_LOGS_TAG, stripLogs);
        state.putInt(PENDING_TREE_LOGS_CUT_TAG, 0);
        if (sapling.isEmpty()) {
            state.remove(PENDING_TREE_SAPLING_ITEM_TAG);
        } else {
            state.putString(PENDING_TREE_SAPLING_ITEM_TAG, BuiltInRegistries.ITEM.getKey(sapling.getItem()).toString());
        }
    }

    private static boolean hasPendingTreeHarvest(HiredWorkContext context) {
        CompoundTag state = context.state();
        return state.contains(PENDING_TREE_ORIGIN_TAG)
                || state.contains(PENDING_TREE_LOGS_TAG)
                || state.contains(PENDING_TREE_LEAVES_TAG)
                || state.contains(PENDING_TREE_SAPLINGS_TAG);
    }

    private static boolean hasPendingLogs(HiredWorkContext context) {
        return context.state().getLongArray(PENDING_TREE_LOGS_TAG).length > 0;
    }

    private static boolean hasPendingLeaves(HiredWorkContext context) {
        return context.state().getLongArray(PENDING_TREE_LEAVES_TAG).length > 0;
    }

    private static boolean isPendingLeafTarget(HiredWorkContext context, BlockPos pos) {
        return containsPendingPosition(context.state().getLongArray(PENDING_TREE_LEAVES_TAG), pos);
    }

    private static BlockPos pendingTreeOrigin(HiredWorkContext context) {
        CompoundTag state = context.state();
        return state.contains(PENDING_TREE_ORIGIN_TAG)
                ? BlockPos.of(state.getLong(PENDING_TREE_ORIGIN_TAG))
                : context.workCenter();
    }

    private static BlockState firstPendingLogState(ServerLevel level, HiredWorkContext context) {
        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        for (long packed : context.state().getLongArray(PENDING_TREE_LOGS_TAG)) {
            BlockPos pos = BlockPos.of(packed);
            if (!context.isInsideWorkArea(pos) || !context.isLoaded(level, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (isMatchingLog(state, filters)) {
                return state;
            }
        }
        return null;
    }

    private static PendingHarvestTargets pendingHarvestTargets(ServerLevel level, HiredWorkContext context) {
        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        List<BlockPos> logs = pendingMatchingPositions(
                level,
                context,
                PENDING_TREE_LOGS_TAG,
                state -> isMatchingLog(state, filters),
                MAX_TREE_LOGS_PER_HARVEST);
        if (!logs.isEmpty()) {
            return new PendingHarvestTargets(logs, false);
        }
        List<BlockPos> leaves = pendingMatchingPositions(
                level,
                context,
                PENDING_TREE_LEAVES_TAG,
                LoggingWorker::isNaturalLeaf,
                MAX_TREE_LEAVES_PER_HARVEST);
        if (leaves.isEmpty() && hasPendingLeaves(context)) {
            context.state().remove(PENDING_TREE_LEAVES_TAG);
        }
        return new PendingHarvestTargets(
                leaves,
                true);
    }

    private static List<BlockPos> pendingMatchingPositions(
            ServerLevel level,
            HiredWorkContext context,
            String tag,
            Predicate<BlockState> matcher,
            int maxPositions) {
        List<BlockPos> positions = new ArrayList<>();
        int safeMaxPositions = Math.max(1, maxPositions);
        for (long packed : context.state().getLongArray(tag)) {
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

    private static ItemStack pendingSapling(HiredWorkContext context) {
        String itemIdText = context.state().getString(PENDING_TREE_SAPLING_ITEM_TAG);
        if (itemIdText.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(itemIdText);
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(itemId)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    private static List<BlockPos> pendingPositions(HiredWorkContext context, String tag) {
        List<BlockPos> positions = new ArrayList<>();
        for (long packed : context.state().getLongArray(tag)) {
            positions.add(BlockPos.of(packed));
        }
        return positions;
    }

    private static boolean containsPendingPosition(long[] packedPositions, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long packedPos = pos.asLong();
        for (long packed : packedPositions) {
            if (packed == packedPos) {
                return true;
            }
        }
        return false;
    }

    private static void removePendingPosition(HiredWorkContext context, String tag, BlockPos pos) {
        if (pos == null) {
            return;
        }
        long packedPos = pos.asLong();
        long[] packedPositions = context.state().getLongArray(tag);
        long[] retained = Arrays.stream(packedPositions)
                .filter(packed -> packed != packedPos)
                .toArray();
        context.state().putLongArray(tag, retained);
    }

    private static void clearPendingTreeLeaves(HiredWorkContext context) {
        context.state().remove(PENDING_TREE_LEAVES_TAG);
    }

    private static long[] positionsToArray(List<BlockPos> positions) {
        long[] packed = new long[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            packed[i] = positions.get(i).asLong();
        }
        return packed;
    }

    private static void clearPendingTreeHarvest(HiredWorkContext context) {
        CompoundTag state = context.state();
        state.remove(PENDING_TREE_ORIGIN_TAG);
        state.remove(PENDING_TREE_LOGS_TAG);
        state.remove(PENDING_TREE_LEAVES_TAG);
        state.remove(PENDING_TREE_SAPLINGS_TAG);
        state.remove(PENDING_TREE_SAPLING_ITEM_TAG);
        state.remove(PENDING_TREE_STRIP_LOGS_TAG);
        state.remove(PENDING_TREE_LOGS_CUT_TAG);
    }

    private static List<BlockPos> naturalTreeLeaves(ServerLevel level, List<BlockPos> logs) {
        List<BlockPos> leaves = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (BlockPos log : logs) {
            for (BlockPos rawPos : BlockPos.betweenClosed(log.offset(-3, -2, -3), log.offset(3, 4, 3))) {
                BlockPos pos = rawPos.immutable();
                if (seen.add(pos.asLong()) && level.hasChunkAt(pos) && isNaturalLeaf(level.getBlockState(pos))) {
                    leaves.add(pos);
                    if (leaves.size() >= MAX_TREE_LEAVES_PER_HARVEST) {
                        return leaves;
                    }
                }
            }
        }
        return leaves;
    }

    private static boolean isLeafHarvestTool(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.SHEARS) || stack.is(ItemTags.HOES));
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

    private static ItemStack bestLeafTool(HiredWorkContext context) {
        ItemStack best = ItemStack.EMPTY;
        double bestScore = Double.NEGATIVE_INFINITY;
        ItemStack mainhand = context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT);
        if (isLeafHarvestTool(mainhand)) {
            best = mainhand;
            bestScore = leafToolScore(mainhand);
        }
        for (int slot : context.inventory().supplySlots()) {
            ItemStack stack = context.inventory().getItem(slot);
            if (!isLeafHarvestTool(stack)) {
                continue;
            }
            double score = leafToolScore(stack);
            if (score > bestScore) {
                best = stack;
                bestScore = score;
            }
        }
        return best;
    }

    private static ItemStack saplingForTree(ServerLevel level, List<BlockPos> logs) {
        BlockPos root = treeRoot(logs);
        if (root != null && level.hasChunkAt(root)) {
            ItemStack sapling = saplingForLogState(level.getBlockState(root));
            if (!sapling.isEmpty()) {
                return sapling;
            }
        }
        for (BlockPos log : logs) {
            if (!level.hasChunkAt(log)) {
                continue;
            }
            ItemStack sapling = saplingForLogState(level.getBlockState(log));
            if (!sapling.isEmpty()) {
                return sapling;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack saplingForLogState(BlockState state) {
        if (state.is(BlockTags.OAK_LOGS)) {
            return new ItemStack(Items.OAK_SAPLING);
        }
        if (state.is(BlockTags.SPRUCE_LOGS)) {
            return new ItemStack(Items.SPRUCE_SAPLING);
        }
        if (state.is(BlockTags.BIRCH_LOGS)) {
            return new ItemStack(Items.BIRCH_SAPLING);
        }
        if (state.is(BlockTags.JUNGLE_LOGS)) {
            return new ItemStack(Items.JUNGLE_SAPLING);
        }
        if (state.is(BlockTags.ACACIA_LOGS)) {
            return new ItemStack(Items.ACACIA_SAPLING);
        }
        if (state.is(BlockTags.DARK_OAK_LOGS)) {
            return new ItemStack(Items.DARK_OAK_SAPLING);
        }
        if (state.is(BlockTags.MANGROVE_LOGS)) {
            return new ItemStack(Items.MANGROVE_PROPAGULE);
        }
        if (state.is(BlockTags.CHERRY_LOGS)) {
            return new ItemStack(Items.CHERRY_SAPLING);
        }
        return ItemStack.EMPTY;
    }

    private static List<BlockPos> saplingPlantingPositions(
            ServerLevel level,
            HiredWorkContext context,
            List<BlockPos> logs) {
        int minY = logs.stream().mapToInt(BlockPos::getY).min().orElse(Integer.MIN_VALUE);
        List<BlockPos> positions = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (BlockPos log : logs) {
            if (log.getY() != minY || !context.isInsideWorkArea(log) || !context.isLoaded(level, log)) {
                continue;
            }
            BlockPos below = log.below();
            if (level.hasChunkAt(below)
                    && isNaturalTreeBase(level.getBlockState(below))
                    && seen.add(log.asLong())) {
                positions.add(log.immutable());
            }
        }
        return positions;
    }

    private static void plantSaplings(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            List<BlockPos> positions,
            ItemStack sapling) {
        if (!(sapling.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        BlockState saplingState = blockItem.getBlock().defaultBlockState();
        for (BlockPos pos : positions) {
            if (!canPlaceSapling(level, context, pos, saplingState)) {
                continue;
            }
            if (!consumeSapling(villager, context, sapling)) {
                return;
            }
            facePlacedSapling(villager, pos);
            villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            level.setBlock(pos, saplingState, Block.UPDATE_ALL);
            HiredPathMemory.rememberRecent(level, pos);
        }
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
        Set<ResourceLocation> filters = HiredLoggingFilters.selectedFilterIds(context.state());
        List<BlockPos> logs = connectedTreeLogs(level, origin, filters);
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
        float multiplier = 100.0F / Math.max(25.0F, context.efficiency());
        return Math.clamp(Math.round(total * multiplier), 1, MAX_TREE_PROGRESS_TICKS);
    }

    private static List<BlockPos> connectedTreeLogs(
            ServerLevel level,
            BlockPos origin,
            Set<ResourceLocation> filters) {
        List<BlockPos> logs = connectedLogs(level, origin, filters);
        return isLikelyNaturalTree(level, logs) ? logs : List.of();
    }

    private static List<BlockPos> connectedLogs(
            ServerLevel level,
            BlockPos origin,
            Set<ResourceLocation> filters) {
        if (!level.hasChunkAt(origin)) {
            return List.of();
        }
        if (!isMatchingLog(level.getBlockState(origin), filters)) {
            return List.of();
        }
        List<BlockPos> logs = new ArrayList<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(origin.immutable());
        visited.add(origin.asLong());

        while (!queue.isEmpty() && logs.size() < MAX_TREE_LOGS_PER_HARVEST) {
            BlockPos current = queue.remove();
            if (!level.hasChunkAt(current)) {
                continue;
            }
            BlockState state = level.getBlockState(current);
            if (!isMatchingLog(state, filters) || !isInsideTreeSearch(origin, current)) {
                continue;
            }
            logs.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz).immutable();
                        if (visited.add(next.asLong())) {
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return logs;
    }

    private static boolean isLikelyNaturalTree(
            ServerLevel level,
            BlockPos origin,
            Set<ResourceLocation> filters) {
        return isLikelyNaturalTree(level, connectedLogs(level, origin, filters));
    }

    private static boolean isLikelyNaturalTree(ServerLevel level, List<BlockPos> logs) {
        return !logs.isEmpty()
                && hasRootedLog(level, logs)
                && naturalLeavesNearLogs(level, logs) >= MIN_NATURAL_LEAVES;
    }

    private static boolean isMatchingLog(BlockState state, Set<ResourceLocation> filters) {
        return state.is(BlockTags.LOGS) && HiredLoggingFilters.matches(state, filters);
    }

    private static boolean isInsideTreeSearch(BlockPos origin, BlockPos pos) {
        return Math.abs(origin.getX() - pos.getX()) <= MAX_TREE_HORIZONTAL_DISTANCE
                && Math.abs(origin.getZ() - pos.getZ()) <= MAX_TREE_HORIZONTAL_DISTANCE
                && Math.abs(origin.getY() - pos.getY()) <= MAX_TREE_VERTICAL_DISTANCE;
    }

    private static boolean hasNearbyNaturalLeaves(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos leafPos = pos.relative(direction);
            if (level.hasChunkAt(leafPos) && isNaturalLeaf(level.getBlockState(leafPos))) {
                return true;
            }
        }
        for (BlockPos rawPos : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 3, 2))) {
            if (level.hasChunkAt(rawPos) && isNaturalLeaf(level.getBlockState(rawPos))) {
                return true;
            }
        }
        return false;
    }

    private static int naturalLeavesNearLogs(ServerLevel level, List<BlockPos> logs) {
        Set<Long> leaves = new HashSet<>();
        for (BlockPos log : logs) {
            for (BlockPos rawPos : BlockPos.betweenClosed(log.offset(-2, -1, -2), log.offset(2, 3, 2))) {
                BlockPos pos = rawPos.immutable();
                if (level.hasChunkAt(pos) && isNaturalLeaf(level.getBlockState(pos)) && leaves.add(pos.asLong())) {
                    if (leaves.size() >= MIN_NATURAL_LEAVES) {
                        return leaves.size();
                    }
                }
            }
        }
        return leaves.size();
    }

    private static boolean isNaturalLeaf(BlockState state) {
        return state.is(BlockTags.LEAVES)
                && (!state.hasProperty(BlockStateProperties.PERSISTENT)
                || !state.getValue(BlockStateProperties.PERSISTENT));
    }

    private static boolean hasRootedLog(ServerLevel level, List<BlockPos> logs) {
        for (BlockPos log : logs) {
            BlockPos below = log.below();
            if (level.hasChunkAt(below)
                    && !level.getBlockState(below).is(BlockTags.LOGS)
                    && isNaturalTreeBase(level.getBlockState(below))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNaturalTreeBase(BlockState state) {
        return state.is(BlockTags.DIRT);
    }

    private record PendingHarvestTargets(List<BlockPos> positions, boolean leaves) {
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
