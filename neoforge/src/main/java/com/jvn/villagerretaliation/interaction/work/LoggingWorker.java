package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class LoggingWorker extends AbstractBlockWorker {
    private static final String NEXT_TREE_SCAN_GAME_TIME_TAG = "NextLoggingTreeScanGameTime";
    private static final String TREE_SCAN_CURSOR_TAG = "LoggingTreeScanCursor";
    private static final int MAX_TREE_LOGS_PER_HARVEST = 96;
    private static final int MAX_TREE_HORIZONTAL_DISTANCE = 8;
    private static final int MAX_TREE_VERTICAL_DISTANCE = 24;
    private static final int MIN_NATURAL_LEAVES = 4;
    private static final int MAX_TREE_SCAN_POSITIONS_PER_WORK_TICK = 512;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;
    private static final int MAX_TREE_PROGRESS_TICKS = 180;
    private static final int MAX_PLANNED_TREE_TARGETS = 12;
    private static final int GROVE_LINK_RADIUS = 6;
    private static final HiredTargetSearch.Messages TREE_SEARCH_MESSAGES = new HiredTargetSearch.Messages(
            "active_tree_target",
            "planned_tree_target",
            "tree_scan_cooldown",
            "tree_scan_full_no_reachable_targets",
            "tree_scan_partial_",
            "tree_target_found",
            NO_TARGET_SCAN_COOLDOWN_TICKS);

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.LOGGING;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        expireWorkPathMemory(level);
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }

        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        HiredPathTarget target = findTreeLog(level, villager, context);
        if (target == null) {
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
        ItemStack axe = context.inventory().equipBestTool(
                stack -> stack.is(ItemTags.AXES),
                stack -> effectiveDestroySpeed(stack, targetState));
        if (axe.isEmpty()) {
            clearActiveBreakingTarget(level, context, villager);
            HiredWorkerBrain.setFailure(context, "missing_axe", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return WorkResult.idle("interaction.work.logging.missing_axe");
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
                    return WorkResult.completed("interaction.work.logging.completed", Map.of("logs", Integer.toString(harvestResult.logsCut())));
                }
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

        List<ItemStack> drops = treeDrops(level, context, villager, logs, filters, axe);
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
            for (ItemStack drop : Block.getDrops(state, level, log, level.getBlockEntity(log), villager, axe)) {
                if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                    return cut <= 0 ? TreeHarvestResult.OUTPUT_FULL : TreeHarvestResult.completed(cut);
                }
            }
            EnchantmentHelper.onHitBlock(level, axe, villager, villager, EquipmentSlot.MAINHAND, log.getCenter(), state, ignored -> {
            });
            level.destroyBlock(log, false, villager);
            level.destroyBlockProgress(villager.getId(), log, -1);
            damageTool(context, villager, axe);
            HiredPathMemory.rememberRecent(level, log);
            cut++;
            if (axe.isEmpty()) {
                break;
            }
        }
        return cut <= 0 ? TreeHarvestResult.TARGET_CHANGED : TreeHarvestResult.completed(cut);
    }

    private static List<ItemStack> treeDrops(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            List<BlockPos> logs,
            Set<ResourceLocation> filters,
            ItemStack axe) {
        List<ItemStack> drops = new ArrayList<>();
        for (BlockPos log : logs) {
            if (!context.isInsideWorkArea(log) || !context.isLoaded(level, log)) {
                continue;
            }
            BlockState state = level.getBlockState(log);
            if (isMatchingLog(state, filters)) {
                drops.addAll(Block.getDrops(state, level, log, level.getBlockEntity(log), villager, axe));
            }
        }
        return drops;
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
