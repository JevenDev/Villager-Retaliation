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
    private static final double ARRIVAL_DISTANCE_SQR = 4.0D;
    private static final long PATH_RETRY_TICKS = 60L;
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
        int nodeIndex = routeNodeIndex(state, route);
        BlockPos target = route.nodes().get(nodeIndex);
        double distanceSqr = villager.blockPosition().distSqr(target);
        long gameTime = level.getGameTime();
        if (distanceSqr > ARRIVAL_DISTANCE_SQR
                && gameTime < state.getLong(ROUTE_RETRY_AFTER_GAME_TIME_TAG)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, target);
            return true;
        }

        NodeMovement movement = moveToRouteNode(level, villager, target, speed);
        if (movement == NodeMovement.ARRIVED) {
            nodeIndex = advanceRouteNode(state, route, nodeIndex);
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
        state.remove(ROUTE_NODE_INDEX_TAG);
        state.remove(ROUTE_DIRECTION_TAG);
        state.remove(ROUTE_RETRY_AFTER_GAME_TIME_TAG);
        state.remove(ROUTE_FAILED_PATH_COUNT_TAG);
    }

    private static int routeNodeIndex(CompoundTag state, HiredRoute route) {
        int maxIndex = route.nodes().size() - 1;
        int index = state.contains(ROUTE_NODE_INDEX_TAG, Tag.TAG_INT)
                ? Math.clamp(state.getInt(ROUTE_NODE_INDEX_TAG), 0, maxIndex)
                : nearestNodeIndex(route, state);
        state.putInt(ROUTE_NODE_INDEX_TAG, index);
        if (!state.contains(ROUTE_DIRECTION_TAG, Tag.TAG_INT) || state.getInt(ROUTE_DIRECTION_TAG) == 0) {
            state.putInt(ROUTE_DIRECTION_TAG, 1);
        }
        return index;
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

    private static int advanceRouteNode(CompoundTag state, HiredRoute route, int currentIndex) {
        clearPathFailure(state);
        int size = route.nodes().size();
        if (size <= 1) {
            state.putInt(ROUTE_NODE_INDEX_TAG, 0);
            state.putInt(ROUTE_DIRECTION_TAG, 1);
            return 0;
        }
        int nextIndex;
        if (route.loop()) {
            nextIndex = Math.floorMod(currentIndex + 1, size);
        } else {
            int direction = state.getInt(ROUTE_DIRECTION_TAG) < 0 ? -1 : 1;
            nextIndex = currentIndex + direction;
            if (nextIndex >= size) {
                direction = -1;
                nextIndex = size - 2;
            } else if (nextIndex < 0) {
                direction = 1;
                nextIndex = 1;
            }
            state.putInt(ROUTE_DIRECTION_TAG, direction);
        }
        state.putInt(ROUTE_NODE_INDEX_TAG, nextIndex);
        return nextIndex;
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
            advanceRouteNode(state, route, nodeIndex);
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
