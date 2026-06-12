package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class HiredStorageNavigationGoal {
    private static final String STORAGE_NAV_TARGET_TAG = "StorageNavigationTarget";
    private static final String STORAGE_NAV_NEXT_REPATH_GAME_TIME_TAG = "StorageNavigationNextRepathGameTime";
    private static final int STORAGE_APPROACH_SEARCH_RADIUS = 4;
    private static final int STORAGE_WALK_TARGET_CLOSE_ENOUGH = 2;
    private static final int STORAGE_APPROACH_CLOSE_ENOUGH = 0;
    private static final double STORAGE_APPROACH_SETTLE_SQR = 9.0D;
    private static final double STORAGE_TRANSFER_REACH_SQR = 4.0D;
    private static final int MAX_APPROACH_PATH_ATTEMPTS = 4;
    private static final int STORAGE_REPATH_INTERVAL_TICKS = 30;
    private static final int STORAGE_WANDER_RADIUS = 4;
    private static final int STORAGE_WANDER_ATTEMPTS = 8;
    private static final int STORAGE_INTERMEDIATE_SEARCH_RADIUS = 10;
    private static final int STORAGE_INTERMEDIATE_VERTICAL_RADIUS = 3;
    private static final int MAX_STORAGE_INTERMEDIATE_PATH_ATTEMPTS = 24;
    private static final int STORAGE_INTERMEDIATE_CLOSE_ENOUGH = 2;

    private HiredStorageNavigationGoal() {
    }

    static Result moveToStorageTarget(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            double speed) {
        if (!context.isLoaded(level, storage)) {
            return Result.FAILED;
        }

        if (AssignedStorageService.canInteractWithAssignedStorage(villager, storage)) {
            clearStorageNavigationState(context);
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            return Result.ARRIVED;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        double distanceSqr = villager.distanceToSqr(storage.getCenter());
        if (continueStorageIntermediateNavigation(level, context, villager, storage, navigationTarget)) {
            return Result.MOVING;
        }
        if (villager.getNavigation().isDone()
                && (isStorageNavigationTarget(storage, navigationTarget)
                || distanceSqr <= STORAGE_APPROACH_SEARCH_RADIUS * STORAGE_APPROACH_SEARCH_RADIUS)) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            clearStorageNavigationState(context);
            if (settleTowardStorageApproach(level, context, villager, storage, speed)) {
                return Result.MOVING;
            }
        }
        if (shouldPreferLadderNavigation(villager, storage)) {
            if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, storage, speed)) {
                rememberStorageNavigationTarget(context, level, storage);
                HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
                return Result.MOVING;
            }
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, storage, speed)) {
                rememberStorageNavigationTarget(context, level, storage);
                HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
                return Result.MOVING;
            }
        }
        if (!villager.getNavigation().isDone() && isStorageNavigationTarget(storage, navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(level, villager, storage, distanceSqr)) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                clearStorageNavigationState(context);
                if (moveToStorageApproach(level, context, villager, storage, speed, null)) {
                    return Result.MOVING;
                }
                if (moveToStorageBlock(level, context, villager, storage, speed, distanceSqr)) {
                    return Result.MOVING;
                }
                if (moveTowardStorageIntermediate(level, context, villager, storage, speed)) {
                    return Result.MOVING;
                }
                if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, storage, speed)) {
                    HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
                    return Result.MOVING;
                }
                if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, storage, speed)) {
                    HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
                    return Result.MOVING;
                }
                HiredPathMemory.clearNavigationProgress(villager);
                return Result.FAILED;
            }
            if (!shouldRepathStorage(context, level, navigationTarget)) {
                return Result.MOVING;
            }
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            clearStorageNavigationState(context);
        }

        if (moveToStorageApproach(level, context, villager, storage, speed, null)) {
            return Result.MOVING;
        }
        if (moveToStorageBlock(level, context, villager, storage, speed, distanceSqr)) {
            return Result.MOVING;
        }
        if (moveTowardStorageIntermediate(level, context, villager, storage, speed)) {
            return Result.MOVING;
        }
        if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, storage, speed)) {
            rememberStorageNavigationTarget(context, level, storage);
            HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
            return Result.MOVING;
        }
        if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, storage, speed)) {
            rememberStorageNavigationTarget(context, level, storage);
            HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
            return Result.MOVING;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        clearStorageNavigationState(context);
        return Result.FAILED;
    }

    static boolean wanderNearStorage(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            double speed) {
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone()
                && navigationTarget != null
                && navigationTarget.distSqr(storage) <= STORAGE_WANDER_RADIUS * STORAGE_WANDER_RADIUS) {
            return true;
        }
        for (int attempt = 0; attempt < STORAGE_WANDER_ATTEMPTS; attempt++) {
            BlockPos candidate = storage.offset(
                    villager.getRandom().nextInt(STORAGE_WANDER_RADIUS * 2 + 1) - STORAGE_WANDER_RADIUS,
                    villager.getRandom().nextInt(3) - 1,
                    villager.getRandom().nextInt(STORAGE_WANDER_RADIUS * 2 + 1) - STORAGE_WANDER_RADIUS);
            if (!context.isLoaded(level, candidate)
                    || candidate.distSqr(storage) > STORAGE_WANDER_RADIUS * STORAGE_WANDER_RADIUS
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)) {
                continue;
            }
            Path path = villager.getNavigation().createPath(candidate, 0);
            if (path != null
                    && path.canReach()
                    && VillagerTaskNavigationUtil.moveToHiredPath(
                            villager,
                            path,
                            candidate,
                            speed * 0.75D,
                            STORAGE_APPROACH_CLOSE_ENOUGH)) {
                rememberStorageNavigationTarget(context, level, candidate);
                HiredPathMemory.rememberNavigationProgress(level, villager, storage, villager.distanceToSqr(storage.getCenter()));
                return true;
            }
        }
        return false;
    }

    private static boolean shouldPreferLadderNavigation(Villager villager, BlockPos target) {
        return target != null && Math.abs(villager.blockPosition().getY() - target.getY()) > 2;
    }

    private static boolean moveToStorageBlock(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            double speed,
            double distanceSqr) {
        Path path = villager.getNavigation().createPath(storage, STORAGE_WALK_TARGET_CLOSE_ENOUGH);
        if (path == null || !path.canReach()) {
            return false;
        }
        boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(
                villager,
                path,
                storage,
                speed,
                STORAGE_WALK_TARGET_CLOSE_ENOUGH);
        if (moved) {
            rememberStorageNavigationTarget(context, level, storage);
            HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
        }
        return moved;
    }

    private static boolean continueStorageIntermediateNavigation(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            BlockPos navigationTarget) {
        if (villager.getNavigation().isDone()
                || navigationTarget == null
                || isStorageNavigationTarget(storage, navigationTarget)
                || !isRememberedStorageNavigationTarget(context, navigationTarget)) {
            return false;
        }
        if (HiredPathMemory.isNavigationBlocked(
                level,
                villager,
                navigationTarget,
                villager.distanceToSqr(navigationTarget.getCenter()))) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            clearStorageNavigationState(context);
            return false;
        }
        return true;
    }

    private static boolean moveTowardStorageIntermediate(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            double speed) {
        BlockPos target = bestStorageIntermediateTarget(level, context, villager, storage);
        if (target == null) {
            return false;
        }
        Path path = villager.getNavigation().createPath(target, 0);
        if (path == null
                || !path.canReach()
                || !VillagerTaskNavigationUtil.moveToHiredPath(
                        villager,
                        path,
                        target,
                        speed,
                        STORAGE_INTERMEDIATE_CLOSE_ENOUGH)) {
            return false;
        }
        rememberStorageNavigationTarget(context, level, target);
        HiredPathMemory.rememberNavigationProgress(level, villager, target, villager.distanceToSqr(target.getCenter()));
        return true;
    }

    private static BlockPos bestStorageIntermediateTarget(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage) {
        BlockPos origin = villager.blockPosition();
        double currentStorageDistance = origin.distSqr(storage);
        List<StorageIntermediate> candidates = new ArrayList<>();
        for (BlockPos raw : BlockPos.betweenClosed(
                origin.offset(-STORAGE_INTERMEDIATE_SEARCH_RADIUS, -STORAGE_INTERMEDIATE_VERTICAL_RADIUS, -STORAGE_INTERMEDIATE_SEARCH_RADIUS),
                origin.offset(STORAGE_INTERMEDIATE_SEARCH_RADIUS, STORAGE_INTERMEDIATE_VERTICAL_RADIUS, STORAGE_INTERMEDIATE_SEARCH_RADIUS))) {
            BlockPos candidate = raw.immutable();
            if (candidate.equals(origin)
                    || !context.isLoaded(level, candidate)
                    || HiredPathMemory.isAvoided(level, villager, candidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)) {
                continue;
            }
            double storageDistance = candidate.distSqr(storage);
            if (storageDistance >= currentStorageDistance - 1.0D) {
                continue;
            }
            candidates.add(new StorageIntermediate(
                    candidate,
                    storageIntermediateScore(level, villager, candidate, storage, storageDistance)));
        }
        candidates.sort(Comparator.comparingDouble(StorageIntermediate::score));

        int attempts = 0;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (StorageIntermediate candidate : candidates) {
            if (attempts++ >= MAX_STORAGE_INTERMEDIATE_PATH_ATTEMPTS) {
                break;
            }
            Path path = villager.getNavigation().createPath(candidate.pos(), 0);
            if (path != null && path.canReach()) {
                double score = candidate.score() + HiredMoveToBlockFaceJob.pathTraversalCost(level, path);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate.pos();
                }
            }
        }
        return best;
    }

    private static double storageIntermediateScore(
            ServerLevel level,
            Villager villager,
            BlockPos candidate,
            BlockPos storage,
            double storageDistance) {
        int vertical = Math.abs(candidate.getY() - villager.blockPosition().getY());
        return storageDistance * 0.35D
                + villager.distanceToSqr(candidate.getCenter()) * 0.15D
                + vertical * vertical * 3.0D
                + HiredMoveToBlockFaceJob.terrainCost(level, candidate);
    }

    private static boolean moveToStorageApproach(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            double speed,
            BlockPos excludedApproach) {
        BlockPos approach = bestStorageApproach(level, context, villager, storage, excludedApproach);
        return approach != null
                && moveToApproach(level, context, villager, approach, speed, villager.distanceToSqr(approach.getCenter()));
    }

    private static boolean settleTowardStorageApproach(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            double speed) {
        BlockPos approach = bestStorageApproach(level, context, villager, storage, null);
        if (approach == null || villager.distanceToSqr(approach.getCenter()) > STORAGE_APPROACH_SETTLE_SQR) {
            return false;
        }
        villager.getMoveControl().setWantedPosition(
                approach.getX() + 0.5D,
                approach.getY(),
                approach.getZ() + 0.5D,
                speed);
        setStorageWalkTarget(villager, approach, speed, STORAGE_APPROACH_CLOSE_ENOUGH);
        rememberStorageNavigationTarget(context, level, approach);
        HiredPathMemory.rememberNavigationProgress(level, villager, approach, villager.distanceToSqr(approach.getCenter()));
        return true;
    }

    private static BlockPos bestStorageApproach(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            BlockPos excludedApproach) {
        if (!context.isLoaded(level, storage)) {
            return null;
        }
        if (AssignedStorageService.canInteractWithAssignedStorage(villager, storage)) {
            return villager.blockPosition().immutable();
        }

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos rawCandidate : BlockPos.betweenClosed(
                storage.offset(-STORAGE_APPROACH_SEARCH_RADIUS, -2, -STORAGE_APPROACH_SEARCH_RADIUS),
                storage.offset(STORAGE_APPROACH_SEARCH_RADIUS, 2, STORAGE_APPROACH_SEARCH_RADIUS))) {
            BlockPos candidate = rawCandidate.immutable();
            if (!context.isLoaded(level, candidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)
                    || candidate.equals(excludedApproach)
                    || HiredPathMemory.isAvoided(level, villager, candidate)
                    || !canInteractWithStorageFrom(level, villager, candidate, storage)) {
                continue;
            }
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparingDouble(candidate -> storageApproachScore(level, villager, candidate, storage)));
        int attempts = 0;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (attempts++ >= MAX_APPROACH_PATH_ATTEMPTS) {
                break;
            }
            Path path = villager.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                double score = storageApproachScore(level, villager, candidate, storage)
                        + HiredMoveToBlockFaceJob.pathTraversalCost(level, path);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static boolean canInteractWithStorageFrom(
            ServerLevel level,
            Villager villager,
            BlockPos approach,
            BlockPos storage) {
        if (approach.distSqr(storage) > STORAGE_TRANSFER_REACH_SQR) {
            return false;
        }
        Vec3 eye = new Vec3(
                approach.getX() + 0.5D,
                approach.getY() + villager.getEyeHeight(),
                approach.getZ() + 0.5D);
        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                Vec3.atCenterOf(storage),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                villager));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(storage);
    }

    private static boolean moveToApproach(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos approach,
            double speed,
            double distanceSqr) {
        Path path = villager.getNavigation().createPath(approach, STORAGE_APPROACH_CLOSE_ENOUGH);
        if (path == null || !path.canReach()) {
            return false;
        }
        boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(
                villager,
                path,
                approach,
                speed,
                STORAGE_APPROACH_CLOSE_ENOUGH);
        if (moved) {
            rememberStorageNavigationTarget(context, level, approach);
            HiredPathMemory.rememberNavigationProgress(level, villager, approach, distanceSqr);
        }
        return moved;
    }

    private static boolean shouldRepathStorage(HiredWorkContext context, ServerLevel level, BlockPos target) {
        long now = level.getGameTime();
        if (!context.state().contains(STORAGE_NAV_TARGET_TAG)
                || context.state().getLong(STORAGE_NAV_TARGET_TAG) != target.asLong()) {
            return true;
        }
        return context.state().getLong(STORAGE_NAV_NEXT_REPATH_GAME_TIME_TAG) <= now;
    }

    private static void rememberStorageNavigationTarget(HiredWorkContext context, ServerLevel level, BlockPos target) {
        context.state().putLong(STORAGE_NAV_TARGET_TAG, target.asLong());
        context.state().putLong(STORAGE_NAV_NEXT_REPATH_GAME_TIME_TAG, level.getGameTime() + STORAGE_REPATH_INTERVAL_TICKS);
    }

    private static boolean isRememberedStorageNavigationTarget(HiredWorkContext context, BlockPos target) {
        return target != null
                && context.state().contains(STORAGE_NAV_TARGET_TAG)
                && context.state().getLong(STORAGE_NAV_TARGET_TAG) == target.asLong();
    }

    private static void clearStorageNavigationState(HiredWorkContext context) {
        context.state().remove(STORAGE_NAV_TARGET_TAG);
        context.state().remove(STORAGE_NAV_NEXT_REPATH_GAME_TIME_TAG);
    }

    static void clearStorageTarget(HiredWorkContext context) {
        HiredWorkerBrain.clearStorageTarget(context);
        clearStorageNavigationState(context);
    }

    private static boolean isStorageNavigationTarget(BlockPos storage, BlockPos navigationTarget) {
        return navigationTarget != null
                && (storage.equals(navigationTarget)
                || navigationTarget.distSqr(storage) <= STORAGE_APPROACH_SEARCH_RADIUS * STORAGE_APPROACH_SEARCH_RADIUS);
    }

    private static void setStorageWalkTarget(Villager villager, BlockPos target, double speed, int closeEnough) {
        VillagerTaskNavigationUtil.setHiredWalkTarget(villager, target, speed, closeEnough);
    }

    private static double storageApproachScore(ServerLevel level, Villager villager, BlockPos approach, BlockPos storage) {
        double distance = villager.distanceToSqr(approach.getCenter());
        int vertical = Math.abs(approach.getY() - villager.blockPosition().getY());
        double reachSlack = approach.getCenter().distanceToSqr(storage.getCenter());
        return distance
                + vertical * vertical * 3.0D
                + reachSlack * 0.25D
                + HiredMoveToBlockFaceJob.terrainCost(level, approach)
                + HiredPathMemory.recentCost(villager, storage);
    }

    private record StorageIntermediate(BlockPos pos, double score) {
    }

    enum Result {
        ARRIVED,
        MOVING,
        FAILED
    }

}
