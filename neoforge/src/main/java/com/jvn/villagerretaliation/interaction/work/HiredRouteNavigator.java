package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

public final class HiredRouteNavigator {
    private static final String ROUTE_NODE_INDEX_TAG = "RouteNodeIndex";
    private static final String ROUTE_DIRECTION_TAG = "RouteDirection";
    private static final String ROUTE_RETRY_AFTER_GAME_TIME_TAG = "RouteRetryAfterGameTime";
    private static final String ROUTE_FAILED_PATH_COUNT_TAG = "RouteFailedPathCount";
    private static final String ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG = "RouteLastNodeReachedGameTime";
    private static final RouteTraversalCursor.Tags CURSOR_TAGS = new RouteTraversalCursor.Tags(
            ROUTE_NODE_INDEX_TAG, ROUTE_DIRECTION_TAG, ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG);
    private static final double ARRIVAL_DISTANCE_SQR = 4.0D;
    private static final long PATH_RETRY_TICKS = 60L;
    private static final long ROUTE_RECOVERY_TICKS = 20L * 30L;
    private static final int PATH_FAILURE_SKIP_LIMIT = 3;
    private static final int CLOSE_ENOUGH_DISTANCE = 1;

    private HiredRouteNavigator() {
    }

    public static boolean maintainRoute(ServerLevel level, Villager villager, HiredWorkContext context, double speed) {
        HiredRoute route = context.route();
        if (route == null || !route.usableForNavigation()) {
            return false;
        }
        CompoundTag state = context.state();
        long gameTime = level.getGameTime();
        RouteTraversalCursor cursor = new RouteTraversalCursor(state, CURSOR_TAGS);
        RouteTraversalCursor.Prepared prepared = cursor.prepareCurrentNode(
                villager, route.nodes(), nearestNodeIndex(route, state), gameTime,
                ARRIVAL_DISTANCE_SQR, ROUTE_RECOVERY_TICKS);
        int nodeIndex = prepared.index();
        BlockPos target = prepared.target();
        if (prepared.recovered()) {
            clearPathFailure(state);
        }
        if (villager.blockPosition().distSqr(target) > ARRIVAL_DISTANCE_SQR
                && gameTime < state.getLong(ROUTE_RETRY_AFTER_GAME_TIME_TAG)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, target);
            return true;
        }

        NodeMovement movement = moveToRouteNode(level, villager, target, speed);
        if (movement == NodeMovement.ARRIVED) {
            cursor.markNodeReached(gameTime);
            nodeIndex = advanceRouteNode(state, route, cursor, nodeIndex);
            target = route.nodes().get(nodeIndex);
            if (route.nodes().size() == 1) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredWorkerBrain.clearFailure(context);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, target);
                return true;
            }
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target);
            return true;
        }
        if (movement == NodeMovement.MOVING) {
            clearPathFailure(state);
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target);
            return true;
        }

        rememberPathFailure(level, villager, context, route, nodeIndex, target);
        return true;
    }

    public static NodeMovement moveToRouteNode(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            double speed) {
        return moveToRouteNode(level, villager, target, speed, ARRIVAL_DISTANCE_SQR);
    }

    public static NodeMovement moveToRouteNode(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            double speed,
            double arrivalDistanceSqr) {
        double distanceSqr = villager.blockPosition().distSqr(target);
        if (distanceSqr <= arrivalDistanceSqr) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredPathMemory.clearNavigationProgress(villager);
            return NodeMovement.ARRIVED;
        }

        boolean targetIsStandable = HiredMoveToBlockFaceJob.isValidApproachPosition(level, target);
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone()
                && navigationTarget != null
                && navigationTarget.distSqr(target) <= arrivalDistanceSqr) {
            int closeEnough = targetIsStandable ? CLOSE_ENOUGH_DISTANCE : 0;
            VillagerTaskNavigationUtil.setHiredWalkTarget(
                    villager,
                    navigationTarget,
                    speed,
                    closeEnough);
            if (HiredPathMemory.observeNavigationProgress(level, villager, target, distanceSqr)) {
                return NodeMovement.MOVING;
            }
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredPathMemory.clearNavigationProgress(villager);
        }

        Path path;
        BlockPos walkTarget;
        if (targetIsStandable) {
            walkTarget = target;
            path = HiredPathMemory.createPath(level, villager, target, CLOSE_ENOUGH_DISTANCE);
        } else {
            RouteApproach approach = nearestReachableApproach(level, villager, target, arrivalDistanceSqr);
            if (approach == null) {
                return NodeMovement.FAILED;
            }
            walkTarget = approach.pos();
            path = approach.path();
        }

        if (path == null || !path.canReach()
                || !VillagerTaskNavigationUtil.moveToHiredPath(
                        villager,
                        path,
                        walkTarget,
                        speed,
                        targetIsStandable ? CLOSE_ENOUGH_DISTANCE : 0)) {
            return NodeMovement.FAILED;
        }
        HiredPathMemory.rememberNavigationProgress(level, villager, target, distanceSqr);
        return NodeMovement.MOVING;
    }

    private static RouteApproach nearestReachableApproach(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            double arrivalDistanceSqr) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos rawCandidate : BlockPos.betweenClosed(
                target.offset(-2, -2, -2),
                target.offset(2, 2, 2))) {
            BlockPos candidate = rawCandidate.immutable();
            if (!candidate.equals(target)
                    && candidate.distSqr(target) <= arrivalDistanceSqr
                    && HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator
                .comparingDouble((BlockPos candidate) -> target.distSqr(candidate))
                .thenComparingDouble(candidate -> villager.distanceToSqr(candidate.getCenter())));
        for (BlockPos candidate : candidates) {
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
            if (path != null && path.canReach()) {
                return new RouteApproach(candidate, path);
            }
        }
        return null;
    }

    public static void clearProgress(CompoundTag state) {
        if (state == null) {
            return;
        }
        new RouteTraversalCursor(state, CURSOR_TAGS).clear();
        state.remove(ROUTE_RETRY_AFTER_GAME_TIME_TAG);
        state.remove(ROUTE_FAILED_PATH_COUNT_TAG);
    }

    private static int nearestNodeIndex(HiredRoute route, CompoundTag state) {
        if (state.contains("WorkerTaskTargetPos", Tag.TAG_LONG)) {
            BlockPos previous = BlockPos.of(state.getLong("WorkerTaskTargetPos"));
            int previousIndex = route.indexOf(previous);
            if (previousIndex >= 0) {
                return previousIndex;
            }
        }
        return 0;
    }

    private static int advanceRouteNode(CompoundTag state, HiredRoute route,
            RouteTraversalCursor cursor, int currentIndex) {
        clearPathFailure(state);
        return cursor.advancePatrol(currentIndex, route.nodes().size(), route.loop());
    }

    private static void rememberPathFailure(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route,
            int nodeIndex,
            BlockPos target) {
        CompoundTag state = context.state();
        int failures = Math.max(0, state.getInt(ROUTE_FAILED_PATH_COUNT_TAG)) + 1;
        if (failures >= PATH_FAILURE_SKIP_LIMIT && route.nodes().size() > 1) {
            state.putInt(ROUTE_FAILED_PATH_COUNT_TAG, 0);
            advanceRouteNode(state, route, new RouteTraversalCursor(state, CURSOR_TAGS), nodeIndex);
            HiredWorkerBrain.setFailure(context, "route_node_unreachable_skipped", level.getGameTime() + PATH_RETRY_TICKS);
        } else {
            state.putInt(ROUTE_FAILED_PATH_COUNT_TAG, failures);
            HiredWorkerBrain.setFailure(context, "route_node_unreachable", level.getGameTime() + PATH_RETRY_TICKS);
        }
        state.putLong(ROUTE_RETRY_AFTER_GAME_TIME_TAG, level.getGameTime() + PATH_RETRY_TICKS);
        HiredPathMemory.recordFailure(level, villager, target);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target);
    }

    private static void clearPathFailure(CompoundTag state) {
        state.remove(ROUTE_RETRY_AFTER_GAME_TIME_TAG);
        state.remove(ROUTE_FAILED_PATH_COUNT_TAG);
    }

    public enum NodeMovement {
        MOVING,
        ARRIVED,
        FAILED
    }

    private record RouteApproach(BlockPos pos, Path path) {
    }
}
