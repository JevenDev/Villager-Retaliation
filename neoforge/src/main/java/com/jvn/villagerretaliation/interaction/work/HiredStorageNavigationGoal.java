package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class HiredStorageNavigationGoal {
    private static final String STORAGE_NAV_TARGET_TAG = "StorageNavigationTarget";
    private static final String STORAGE_NAV_NEXT_REPATH_GAME_TIME_TAG = "StorageNavigationNextRepathGameTime";
    private static final int STORAGE_APPROACH_SEARCH_RADIUS = 4;
    private static final int STORAGE_WALK_TARGET_CLOSE_ENOUGH = 2;
    private static final int STORAGE_APPROACH_CLOSE_ENOUGH = 0;
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

    public static Result moveToStorageTarget(
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

        List<BlockPos> storagePositions = storageInteractionPositions(level, villager, storage);
        BlockPos primaryStorage = nearestStoragePosition(villager, storagePositions);
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        double distanceSqr = nearestStorageDistanceSqr(villager, storagePositions);
        boolean preferLadderNavigation = shouldPreferLadderNavigation(villager, primaryStorage);
        if (preferLadderNavigation
                && navigationTarget != null
                && !isStorageNavigationTarget(storagePositions, navigationTarget)
                && !isVerticallyToward(villager.blockPosition(), navigationTarget, primaryStorage)) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            clearStorageNavigationState(context);
            navigationTarget = null;
        }
        if (preferLadderNavigation) {
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, primaryStorage, speed)) {
                if (rememberManualStorageNavigationProgress(level, context, villager, primaryStorage, distanceSqr)) {
                    return Result.MOVING;
                }
            }
            if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, primaryStorage, speed)) {
                if (rememberManualStorageNavigationProgress(level, context, villager, primaryStorage, distanceSqr)) {
                    return Result.MOVING;
                }
            }
        }
        if (continueStorageIntermediateNavigation(level, context, villager, storagePositions, primaryStorage, navigationTarget)) {
            return Result.MOVING;
        }
        if (villager.getNavigation().isDone()
                && (isStorageNavigationTarget(storagePositions, navigationTarget)
                || distanceSqr <= STORAGE_APPROACH_SEARCH_RADIUS * STORAGE_APPROACH_SEARCH_RADIUS)) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            clearStorageNavigationState(context);
            if (moveToStorageApproach(level, context, villager, storagePositions, speed, null)) {
                return Result.MOVING;
            }
        }
        if (!villager.getNavigation().isDone() && isStorageNavigationTarget(storagePositions, navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(level, villager, primaryStorage, distanceSqr)) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                clearStorageNavigationState(context);
                if (moveToStorageApproach(level, context, villager, storagePositions, speed, null)) {
                    return Result.MOVING;
                }
                if (moveToStorageBlock(level, context, villager, storagePositions, speed, distanceSqr)) {
                    return Result.MOVING;
                }
                if (moveTowardStorageIntermediate(level, context, villager, storagePositions, primaryStorage, speed)) {
                    return Result.MOVING;
                }
                if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, primaryStorage, speed)) {
                    if (rememberManualStorageNavigationProgress(level, context, villager, primaryStorage, distanceSqr)) {
                        return Result.MOVING;
                    }
                }
                if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, primaryStorage, speed)) {
                    if (rememberManualStorageNavigationProgress(level, context, villager, primaryStorage, distanceSqr)) {
                        return Result.MOVING;
                    }
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

        if (moveToStorageApproach(level, context, villager, storagePositions, speed, null)) {
            return Result.MOVING;
        }
        if (moveToStorageBlock(level, context, villager, storagePositions, speed, distanceSqr)) {
            return Result.MOVING;
        }
        if (moveTowardStorageIntermediate(level, context, villager, storagePositions, primaryStorage, speed)) {
            return Result.MOVING;
        }
        if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, primaryStorage, speed)) {
            if (rememberManualStorageNavigationProgress(level, context, villager, primaryStorage, distanceSqr)) {
                return Result.MOVING;
            }
        }
        if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, primaryStorage, speed)) {
            if (rememberManualStorageNavigationProgress(level, context, villager, primaryStorage, distanceSqr)) {
                return Result.MOVING;
            }
        }
        HiredPathMemory.clearNavigationProgress(villager);
        clearStorageNavigationState(context);
        return Result.FAILED;
    }

    public static boolean wanderNearStorage(
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
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
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

    private static boolean isVerticallyToward(BlockPos origin, BlockPos navigationTarget, BlockPos target) {
        if (target == null || navigationTarget == null) {
            return true;
        }
        int targetDelta = target.getY() - origin.getY();
        int navigationDelta = navigationTarget.getY() - origin.getY();
        if (Math.abs(targetDelta) <= 2 || navigationDelta == 0) {
            return true;
        }
        return Integer.signum(targetDelta) == Integer.signum(navigationDelta);
    }

    private static boolean rememberManualStorageNavigationProgress(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos target,
            double distanceSqr) {
        rememberStorageNavigationTarget(context, level, target);
        if (HiredPathMemory.observeNavigationProgress(level, villager, target, distanceSqr)) {
            return true;
        }
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        HiredPathMemory.clearNavigationProgress(villager);
        clearStorageNavigationState(context);
        return false;
    }

    private static boolean moveToStorageBlock(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            List<BlockPos> storagePositions,
            double speed,
            double distanceSqr) {
        for (BlockPos storage : orderedStoragePositions(villager, storagePositions)) {
            if (!context.isLoaded(level, storage)) {
                continue;
            }
            Path path = HiredPathMemory.createPath(level, villager, storage, STORAGE_WALK_TARGET_CLOSE_ENOUGH);
            if (path == null || !path.canReach()) {
                continue;
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
                return true;
            }
        }
        return false;
    }

    private static boolean continueStorageIntermediateNavigation(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            List<BlockPos> storagePositions,
            BlockPos primaryStorage,
            BlockPos navigationTarget) {
        if (villager.getNavigation().isDone()
                || navigationTarget == null
                || isStorageNavigationTarget(storagePositions, navigationTarget)
                || !isRememberedStorageNavigationTarget(context, navigationTarget)) {
            return false;
        }
        if (shouldPreferLadderNavigation(villager, primaryStorage)
                && !isVerticallyToward(villager.blockPosition(), navigationTarget, primaryStorage)) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            clearStorageNavigationState(context);
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
            List<BlockPos> storagePositions,
            BlockPos primaryStorage,
            double speed) {
        BlockPos target = bestStorageIntermediateTarget(level, context, villager, storagePositions, primaryStorage);
        if (target == null) {
            return false;
        }
        Path path = HiredPathMemory.createPath(level, villager, target, 0);
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
            List<BlockPos> storagePositions,
            BlockPos primaryStorage) {
        BlockPos origin = villager.blockPosition();
        double currentStorageDistance = nearestBlockDistanceSqr(origin, storagePositions);
        List<StorageIntermediate> candidates = new ArrayList<>();
        for (BlockPos raw : BlockPos.betweenClosed(
                origin.offset(-STORAGE_INTERMEDIATE_SEARCH_RADIUS, -STORAGE_INTERMEDIATE_VERTICAL_RADIUS, -STORAGE_INTERMEDIATE_SEARCH_RADIUS),
                origin.offset(STORAGE_INTERMEDIATE_SEARCH_RADIUS, STORAGE_INTERMEDIATE_VERTICAL_RADIUS, STORAGE_INTERMEDIATE_SEARCH_RADIUS))) {
            BlockPos candidate = raw.immutable();
            if (candidate.equals(origin)
                    || !context.isLoaded(level, candidate)
                    || HiredPathMemory.isAvoided(level, villager, candidate)
                    || !isVerticallyToward(origin, candidate, primaryStorage)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)) {
                continue;
            }
            double storageDistance = nearestBlockDistanceSqr(candidate, storagePositions);
            if (storageDistance >= currentStorageDistance - 1.0D) {
                continue;
            }
            candidates.add(new StorageIntermediate(
                    candidate,
                    storageIntermediateScore(level, villager, candidate, storageDistance)));
        }
        candidates.sort(Comparator.comparingDouble(StorageIntermediate::score));

        int attempts = 0;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (StorageIntermediate candidate : candidates) {
            if (attempts++ >= MAX_STORAGE_INTERMEDIATE_PATH_ATTEMPTS) {
                break;
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate.pos(), 0);
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
            List<BlockPos> storagePositions,
            double speed,
            BlockPos excludedApproach) {
        BlockPos approach = bestStorageApproach(level, context, villager, storagePositions, excludedApproach);
        return approach != null
                && moveToApproach(level, context, villager, approach, speed, villager.distanceToSqr(approach.getCenter()));
    }

    private static BlockPos bestStorageApproach(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            List<BlockPos> storagePositions,
            BlockPos excludedApproach) {
        List<BlockPos> candidates = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos storage : orderedStoragePositions(villager, storagePositions)) {
            if (!context.isLoaded(level, storage)) {
                continue;
            }
            for (BlockPos rawCandidate : BlockPos.betweenClosed(
                    storage.offset(-STORAGE_APPROACH_SEARCH_RADIUS, -2, -STORAGE_APPROACH_SEARCH_RADIUS),
                    storage.offset(STORAGE_APPROACH_SEARCH_RADIUS, 2, STORAGE_APPROACH_SEARCH_RADIUS))) {
                BlockPos candidate = rawCandidate.immutable();
                if (!seen.add(candidate)
                        || !context.isLoaded(level, candidate)
                        || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)
                        || candidate.equals(excludedApproach)
                        || HiredPathMemory.isAvoided(level, villager, candidate)
                        || !canInteractWithStorageFrom(level, villager, candidate, storagePositions)) {
                    continue;
                }
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparingDouble(candidate -> storageApproachScore(level, villager, candidate, storagePositions)));
        int attempts = 0;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (attempts++ >= MAX_APPROACH_PATH_ATTEMPTS) {
                break;
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
            if (path != null && path.canReach()) {
                double score = storageApproachScore(level, villager, candidate, storagePositions)
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
            List<BlockPos> storagePositions) {
        Vec3 eye = new Vec3(
                approach.getX() + 0.5D,
                approach.getY() + villager.getEyeHeight(),
                approach.getZ() + 0.5D);
        for (BlockPos storage : storagePositions) {
            if (approach.distSqr(storage) > STORAGE_TRANSFER_REACH_SQR) {
                continue;
            }
            BlockHitResult hit = level.clip(new ClipContext(
                    eye,
                    Vec3.atCenterOf(storage),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    villager));
            if (hit.getType() == HitResult.Type.BLOCK && containsStoragePosition(storagePositions, hit.getBlockPos())) {
                return true;
            }
        }
        return false;
    }

    private static boolean moveToApproach(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos approach,
            double speed,
            double distanceSqr) {
        Path path = HiredPathMemory.createPath(level, villager, approach, STORAGE_APPROACH_CLOSE_ENOUGH);
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

    public static void clearStorageTarget(HiredWorkContext context) {
        HiredWorkerBrain.clearStorageTarget(context);
        clearStorageNavigationState(context);
    }

    private static boolean isStorageNavigationTarget(List<BlockPos> storagePositions, BlockPos navigationTarget) {
        if (navigationTarget == null) {
            return false;
        }
        for (BlockPos storage : storagePositions) {
            if (storage.equals(navigationTarget)
                    || navigationTarget.distSqr(storage) <= STORAGE_APPROACH_SEARCH_RADIUS * STORAGE_APPROACH_SEARCH_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static double storageApproachScore(ServerLevel level, Villager villager, BlockPos approach, List<BlockPos> storagePositions) {
        double distance = villager.distanceToSqr(approach.getCenter());
        int vertical = Math.abs(approach.getY() - villager.blockPosition().getY());
        BlockPos nearestStorage = nearestStoragePosition(approach, storagePositions);
        double reachSlack = nearestCenterDistanceSqr(approach.getCenter(), storagePositions);
        return distance
                + vertical * vertical * 3.0D
                + reachSlack * 0.25D
                + HiredMoveToBlockFaceJob.terrainCost(level, approach)
                + HiredPathMemory.recentCost(villager, nearestStorage);
    }

    private static List<BlockPos> storageInteractionPositions(ServerLevel level, Villager villager, BlockPos storage) {
        List<BlockPos> positions = AssignedStorageService.assignedStorageInteractionPositions(level, villager, storage);
        return positions.isEmpty() ? List.of(storage.immutable()) : positions;
    }

    private static List<BlockPos> orderedStoragePositions(Villager villager, List<BlockPos> storagePositions) {
        return storagePositions.stream()
                .sorted(Comparator.comparingDouble(storage -> villager.distanceToSqr(storage.getCenter())))
                .toList();
    }

    private static BlockPos nearestStoragePosition(Villager villager, List<BlockPos> storagePositions) {
        return storagePositions.stream()
                .min(Comparator.comparingDouble(storage -> villager.distanceToSqr(storage.getCenter())))
                .orElse(BlockPos.ZERO);
    }

    private static BlockPos nearestStoragePosition(BlockPos reference, List<BlockPos> storagePositions) {
        return storagePositions.stream()
                .min(Comparator.comparingDouble(reference::distSqr))
                .orElse(BlockPos.ZERO);
    }

    private static double nearestStorageDistanceSqr(Villager villager, List<BlockPos> storagePositions) {
        double best = Double.MAX_VALUE;
        for (BlockPos storage : storagePositions) {
            best = Math.min(best, villager.distanceToSqr(storage.getCenter()));
        }
        return best;
    }

    private static double nearestBlockDistanceSqr(BlockPos reference, List<BlockPos> storagePositions) {
        double best = Double.MAX_VALUE;
        for (BlockPos storage : storagePositions) {
            best = Math.min(best, reference.distSqr(storage));
        }
        return best;
    }

    private static double nearestCenterDistanceSqr(Vec3 reference, List<BlockPos> storagePositions) {
        double best = Double.MAX_VALUE;
        for (BlockPos storage : storagePositions) {
            best = Math.min(best, reference.distanceToSqr(storage.getCenter()));
        }
        return best;
    }

    private static boolean containsStoragePosition(List<BlockPos> storagePositions, BlockPos pos) {
        for (BlockPos storage : storagePositions) {
            if (storage.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private record StorageIntermediate(BlockPos pos, double score) {
    }

    public enum Result {
        ARRIVED,
        MOVING,
        FAILED
    }

}
