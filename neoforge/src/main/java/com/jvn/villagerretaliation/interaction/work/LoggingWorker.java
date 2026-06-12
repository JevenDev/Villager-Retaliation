package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

public final class LoggingWorker extends AbstractBlockWorker {
    private static final String NEXT_TREE_SCAN_GAME_TIME_TAG = "NextLoggingTreeScanGameTime";
    private static final String TREE_SCAN_CURSOR_TAG = "LoggingTreeScanCursor";
    private static final int MAX_TREE_LOGS_PER_HARVEST = 96;
    private static final int MAX_TREE_HORIZONTAL_DISTANCE = 8;
    private static final int MAX_TREE_VERTICAL_DISTANCE = 24;
    private static final int MIN_NATURAL_LEAVES = 4;
    private static final int MAX_TREE_SCAN_POSITIONS_PER_WORK_TICK = 512;
    private static final String NEXT_SAPLING_SCAN_GAME_TIME_TAG = "NextLoggingSaplingScanGameTime";
    private static final String SAPLING_SCAN_CURSOR_TAG = "LoggingSaplingScanCursor";
    private static final int MAX_SAPLING_SCAN_POSITIONS_PER_WORK_TICK = 768;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;
    private static final int MAX_TREE_PROGRESS_TICKS = 180;
    private static final int MAX_PLANNED_TREE_TARGETS = 12;
    private static final int MAX_PLANNED_SAPLING_TARGETS = 8;
    private static final int MAX_TREE_LEAVES_PER_HARVEST = 192;
    private static final double DECAY_DROP_PICKUP_REACH_SQR = 2.25D;
    private static final int GROVE_LINK_RADIUS = 6;
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

    @Override
    public void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        super.maintain(level, villager, context);
        if (!context.hasWorkArea()) {
            return;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (worker.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET && worker.targetPos() != null) {
            HiredPathTarget active = activeWorkTarget(level, context, villager);
            if (active != null && canWorkFromCurrentPosition(level, villager, context, active)) {
                holdWorkPosition(villager, active);
                HiredWorkerBrain.clearFailure(context);
                setTaskState(context, HiredWorkerTaskState.WORKING, active.blockPos());
                return;
            }
            if (active == null && villager.getNavigation().isDone() && context.progressTicks() <= 0) {
                clearActiveBreakingTarget(level, context, villager);
                setTaskState(context, HiredWorkerTaskState.IDLE);
            }
        }
        collectDecayDrops(level, villager, context);
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
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
        if (harvestResult == TreeHarvestResult.OUTPUT_FULL) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.DEPOSITED) {
                harvestResult = harvestTree(level, context, villager, target, axe);
                if (harvestResult.completed()) {
                    HiredWorkPlan.removeTarget(context, target.blockPos());
                    clearActiveBreakingTarget(level, context, villager);
                    setTaskState(context, HiredWorkerTaskState.IDLE);
                    return WorkResult.completed("interaction.work.logging.completed", Map.of("logs", Integer.toString(harvestResult.logsCut())));
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
        return WorkResult.completed("interaction.work.logging.completed", Map.of("logs", Integer.toString(harvestResult.logsCut())));
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
        ItemEntity drop = findNearestDecayDrop(level, villager, context);
        if (drop == null) {
            return null;
        }

        context.setProgressTicks(0);
        BlockPos dropPos = drop.blockPosition();
        if (!context.hasOutputSpace()) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.DEPOSITED) {
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
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, dropPos);
            return WorkResult.idle("interaction.work.logging.output_full_blocked");
        }

        if (!canCollectDecayDropFromCurrentPosition(villager, context, drop)) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, dropPos);
            if (!moveToDecayDrop(level, villager, context, drop, 0.55D)) {
                if (recordWorkPathFailure(level, villager, dropPos)) {
                    HiredWorkerBrain.setFailure(context, "decay_drop_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, dropPos);
                    return WorkResult.idle("interaction.work.logging.decay_drop_unreachable");
                }
                return WorkResult.progressed("interaction.work.logging.decay_drop_repositioning");
            }
            return WorkResult.progressed("interaction.work.logging.moving_to_decay_drop");
        }

        clearWorkPathFailure(villager, dropPos);
        stopWorkNavigation(villager);
        faceBlock(villager, drop.position());
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, dropPos);

        ItemStack stack = drop.getItem();
        ItemStack remainder = context.storeOutputAfterDepositIfFull(villager, stack.copy());
        int moved = stack.getCount() - remainder.getCount();
        if (moved <= 0) {
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, dropPos);
            return WorkResult.idle("interaction.work.logging.output_full_blocked");
        }

        if (remainder.isEmpty()) {
            drop.discard();
        } else {
            drop.setItem(remainder);
        }
        swingWorkTool(villager);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return WorkResult.progressed(
                "interaction.work.logging.collected_decay_drops",
                Map.of("count", Integer.toString(moved)));
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

    private ItemEntity findNearestDecayDrop(ServerLevel level, Villager villager, HiredWorkContext context) {
        List<ItemEntity> drops = new ArrayList<>(level.getEntitiesOfClass(
                ItemEntity.class,
                workAreaBounds(context),
                drop -> isCollectableDecayDrop(level, context, villager, drop)));
        drops.sort(Comparator.comparingDouble(villager::distanceToSqr));
        return drops.isEmpty() ? null : drops.getFirst();
    }

    private static boolean isCollectableDecayDrop(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            ItemEntity drop) {
        BlockPos pos = drop.blockPosition();
        return drop.isAlive()
                && !drop.hasPickUpDelay()
                && isTreeDecayDrop(drop.getItem())
                && context.isInsideWorkArea(pos)
                && context.isLoaded(level, pos)
                && !HiredPathMemory.isAvoided(level, villager, pos);
    }

    private static boolean isTreeDecayDrop(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(Items.STICK)
                || stack.is(Items.APPLE)
                || stack.is(ItemTags.SAPLINGS)
                || stack.is(Items.MANGROVE_PROPAGULE));
    }

    private static boolean canCollectDecayDropFromCurrentPosition(
            Villager villager,
            HiredWorkContext context,
            ItemEntity drop) {
        return context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(drop.blockPosition())
                && villager.distanceToSqr(drop) <= DECAY_DROP_PICKUP_REACH_SQR;
    }

    private boolean moveToDecayDrop(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            ItemEntity drop,
            double speed) {
        if (!context.isInsideWorkArea(villager.blockPosition())
                || !context.isInsideWorkArea(drop.blockPosition())) {
            stopWorkNavigation(villager);
            return false;
        }
        if (villager.distanceToSqr(drop) <= DECAY_DROP_PICKUP_REACH_SQR) {
            stopWorkNavigation(villager);
            faceBlock(villager, drop.position());
            return true;
        }

        BlockPos targetPos = drop.blockPosition();
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
        List<BlockPos> leaves = harvestLeaves && !leafTool.isEmpty()
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

        faceBlock(villager, target);
        swingWorkTool(villager);
        int cut = 0;
        for (BlockPos log : logs) {
            if (!context.isInsideWorkArea(log) || !context.isLoaded(level, log)) {
                continue;
            }
            BlockState state = level.getBlockState(log);
            if (!isMatchingLog(state, filters)) {
                continue;
            }
            BlockState harvestState = stripLogs ? stripLog(level, log, state) : state;
            boolean stripped = !harvestState.equals(state);
            for (ItemStack drop : Block.getDrops(harvestState, level, log, level.getBlockEntity(log), villager, axe)) {
                if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                    return cut <= 0 ? TreeHarvestResult.OUTPUT_FULL : TreeHarvestResult.completed(cut);
                }
            }
            EnchantmentHelper.onHitBlock(level, axe, villager, villager, EquipmentSlot.MAINHAND, log.getCenter(), harvestState, ignored -> {
            });
            level.destroyBlock(log, false, villager);
            level.destroyBlockProgress(villager.getId(), log, -1);
            damageTool(context, villager, axe);
            if (stripped && !axe.isEmpty()) {
                damageTool(context, villager, axe);
            }
            HiredPathMemory.rememberRecent(level, log);
            cut++;
            if (axe.isEmpty()) {
                break;
            }
        }
        if (!leaves.isEmpty() && !harvestLeaves(level, context, villager, leaves)) {
            return cut <= 0 ? TreeHarvestResult.OUTPUT_FULL : TreeHarvestResult.completed(cut);
        }
        if (!sapling.isEmpty() && !saplingPositions.isEmpty()) {
            plantSaplings(level, context, villager, saplingPositions, sapling);
        }
        return cut <= 0 ? TreeHarvestResult.TARGET_CHANGED : TreeHarvestResult.completed(cut);
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
        if (!leafTool.isEmpty()) {
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

    private boolean harvestLeaves(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            List<BlockPos> leaves) {
        ItemStack leafTool = context.inventory().equipBestTool(
                LoggingWorker::isLeafHarvestTool,
                LoggingWorker::leafToolScore);
        if (leafTool.isEmpty()) {
            return true;
        }

        for (BlockPos leaf : leaves) {
            if (!context.isInsideWorkArea(leaf) || !context.isLoaded(level, leaf)) {
                continue;
            }
            BlockState state = level.getBlockState(leaf);
            if (!isNaturalLeaf(state)) {
                continue;
            }
            for (ItemStack drop : Block.getDrops(state, level, leaf, level.getBlockEntity(leaf), villager, leafTool)) {
                if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                    return false;
                }
            }
            faceBlock(villager, leaf);
            swingWorkTool(villager);
            EnchantmentHelper.onHitBlock(level, leafTool, villager, villager, EquipmentSlot.MAINHAND, leaf.getCenter(), state, ignored -> {
            });
            level.destroyBlock(leaf, false, villager);
            level.destroyBlockProgress(villager.getId(), leaf, -1);
            damageTool(context, villager, leafTool);
            HiredPathMemory.rememberRecent(level, leaf);
            if (leafTool.isEmpty()) {
                return true;
            }
        }
        return true;
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

    private static AABB workAreaBounds(HiredWorkContext context) {
        return new AABB(
                context.workMin().getX(),
                context.workMin().getY(),
                context.workMin().getZ(),
                context.workMax().getX() + 1.0D,
                context.workMax().getY() + 1.0D,
                context.workMax().getZ() + 1.0D);
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

    private record TreeHarvestResult(int logsCut, boolean completed, boolean outputFull, boolean targetChanged) {
        private static final TreeHarvestResult OUTPUT_FULL = new TreeHarvestResult(0, false, true, false);
        private static final TreeHarvestResult TARGET_CHANGED = new TreeHarvestResult(0, false, false, true);

        private static TreeHarvestResult completed(int logsCut) {
            return new TreeHarvestResult(logsCut, true, false, false);
        }
    }
}
