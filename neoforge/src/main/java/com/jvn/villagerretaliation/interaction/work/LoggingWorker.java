package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private static final int MAX_TREE_LOGS_PER_HARVEST = 96;
    private static final int MAX_TREE_HORIZONTAL_DISTANCE = 8;
    private static final int MAX_TREE_VERTICAL_DISTANCE = 24;
    private static final int MIN_NATURAL_LEAVES = 4;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.LOGGING;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        expireWorkPathMemory(level);

        HiredPathTarget target = findTreeLog(level, villager, context);
        if (target == null) {
            clearActiveBreakingTarget(level, context, villager);
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
            if (depositResult == DepositResult.MOVING) {
                return WorkResult.progressed("No safe tree logs found in radius. Walking to assigned storage.");
            }
            return WorkResult.idle("No safe tree logs found in radius.");
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        ItemStack axe = context.inventory().equipBestTool(
                stack -> stack.is(ItemTags.AXES),
                stack -> effectiveDestroySpeed(stack, targetState));
        if (axe.isEmpty()) {
            clearActiveBreakingTarget(level, context, villager);
            return WorkResult.idle("Paused: logging needs an axe in job gear or supplies.");
        }

        prepareBreakingTarget(level, context, villager, target);
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            if (!moveToTarget(level, villager, context, target, 0.55D)) {
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    clearActiveBreakingTarget(level, context, villager);
                    return WorkResult.idle("Tree log blocked. Looking for another reachable trunk.");
                }
                return WorkResult.progressed("Tree log blocked. Repositioning for a reachable trunk face.");
            }
            return WorkResult.progressed("Moving to reachable tree face.");
        }
        clearWorkPathFailure(villager, target.blockPos());
        holdMiningPosition(villager, target);

        int needed = breakProgressGoal(level, target.blockPos(), axe);
        int progress = context.progressTicks() + 1;
        if (progress < needed) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            showBreakProgress(level, villager, target.blockPos(), progress, needed);
            return WorkResult.progressed("Logging tree: " + progress + "/" + needed + ".");
        }

        context.setProgressTicks(0);
        TreeHarvestResult harvestResult = harvestTree(level, context, villager, target, axe);
        if (harvestResult == TreeHarvestResult.OUTPUT_FULL) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.DEPOSITED) {
                harvestResult = harvestTree(level, context, villager, target, axe);
                if (harvestResult.completed()) {
                    clearActiveBreakingTarget(level, context, villager);
                    return WorkResult.completed("Cut " + harvestResult.logsCut() + " tree logs.");
                }
            }
            if (depositResult == DepositResult.MOVING) {
                return WorkResult.progressed("Output full. Walking to assigned storage before logging more.");
            }
            clearActiveBreakingTarget(level, context, villager);
            return WorkResult.idle("Paused: output storage is full.");
        }
        if (harvestResult == TreeHarvestResult.TARGET_CHANGED) {
            clearActiveBreakingTarget(level, context, villager);
            return WorkResult.idle("Tree target changed.");
        }
        clearActiveBreakingTarget(level, context, villager);
        return WorkResult.completed("Cut " + harvestResult.logsCut() + " tree logs.");
    }

    private HiredPathTarget findTreeLog(ServerLevel level, Villager villager, HiredWorkContext context) {
        String filter = context.state().getString("LoggingFilter");
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        if (active != null
                && context.isInsideWorkArea(active.blockPos())
                && context.isLoaded(level, active.blockPos())
                && !isTemporarilyAvoidedTarget(level, villager, active.blockPos())
                && isTreeLog(level, active.blockPos(), filter)) {
            return active;
        }
        if (level.getGameTime() < context.state().getLong(NEXT_TREE_SCAN_GAME_TIME_TAG)) {
            return null;
        }

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos rawPos : context.workAreaPositions()) {
            BlockPos pos = rawPos.immutable();
            if (!context.isInsideWorkArea(pos) || !context.isLoaded(level, pos) || !isTreeLog(level, pos, filter)) {
                continue;
            }
            candidates.add(pos);
        }
        HiredPathTarget target = chooseReachableTarget(level, villager, context, candidates);
        if (target == null) {
            context.state().putLong(NEXT_TREE_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
        } else {
            context.state().remove(NEXT_TREE_SCAN_GAME_TIME_TAG);
        }
        return target;
    }

    private static boolean matchesFilter(BlockState state, String filter) {
        if (filter == null || filter.isBlank() || "any".equals(filter)) {
            return true;
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(filter);
    }

    private static boolean isTreeLog(ServerLevel level, BlockPos pos, String filter) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return isMatchingLog(state, filter)
                && hasNearbyNaturalLeaves(level, pos)
                && isLikelyNaturalTree(level, pos, filter);
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

        String filter = context.state().getString("LoggingFilter");
        List<BlockPos> logs = connectedTreeLogs(level, target.blockPos(), filter);
        if (logs.isEmpty()) {
            return TreeHarvestResult.TARGET_CHANGED;
        }

        List<ItemStack> drops = new ArrayList<>();
        for (BlockPos log : logs) {
            if (!context.isLoaded(level, log)) {
                continue;
            }
            BlockState state = level.getBlockState(log);
            if (!state.is(BlockTags.LOGS)) {
                continue;
            }
            drops.addAll(Block.getDrops(state, level, log, level.getBlockEntity(log), villager, axe));
        }
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
            if (!state.is(BlockTags.LOGS) || !matchesFilter(state, filter)) {
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
            cut++;
            if (axe.isEmpty()) {
                break;
            }
        }
        return cut <= 0 ? TreeHarvestResult.TARGET_CHANGED : TreeHarvestResult.completed(cut);
    }

    private static List<BlockPos> connectedTreeLogs(ServerLevel level, BlockPos origin, String filter) {
        if (!isLikelyNaturalTree(level, origin, filter)) {
            return List.of();
        }
        return connectedLogs(level, origin, filter);
    }

    private static List<BlockPos> connectedLogs(ServerLevel level, BlockPos origin, String filter) {
        if (!level.hasChunkAt(origin)) {
            return List.of();
        }
        if (!isMatchingLog(level.getBlockState(origin), filter)) {
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
            if (!isMatchingLog(state, filter) || !isInsideTreeSearch(origin, current)) {
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

    private static boolean isLikelyNaturalTree(ServerLevel level, BlockPos origin, String filter) {
        List<BlockPos> logs = connectedLogs(level, origin, filter);
        return !logs.isEmpty()
                && hasRootedLog(level, logs)
                && naturalLeavesNearLogs(level, logs) >= MIN_NATURAL_LEAVES;
    }

    private static boolean isMatchingLog(BlockState state, String filter) {
        return state.is(BlockTags.LOGS) && matchesFilter(state, filter);
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
