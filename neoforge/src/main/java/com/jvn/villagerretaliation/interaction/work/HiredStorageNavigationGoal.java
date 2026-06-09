package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

final class HiredStorageNavigationGoal {
    private static final String STORAGE_NAV_TARGET_TAG = "StorageNavigationTarget";
    private static final String STORAGE_NAV_NEXT_REPATH_GAME_TIME_TAG = "StorageNavigationNextRepathGameTime";
    private static final int STORAGE_APPROACH_SEARCH_RADIUS = 4;
    private static final int STORAGE_WALK_TARGET_CLOSE_ENOUGH = 2;
    private static final int MAX_APPROACH_PATH_ATTEMPTS = 4;
    private static final int STORAGE_REPATH_INTERVAL_TICKS = 30;
    private static final int STORAGE_WANDER_RADIUS = 4;
    private static final int STORAGE_WANDER_ATTEMPTS = 8;

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

        setStorageWalkTarget(villager, storage, speed);

        if (AssignedStorageService.canInteractWithAssignedStorage(villager, storage)) {
            clearStorageNavigationState(context);
            if (!villager.getNavigation().isDone()) {
                villager.getNavigation().stop();
            }
            return Result.ARRIVED;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        double distanceSqr = villager.distanceToSqr(storage.getCenter());
        if (shouldPreferLadderNavigation(villager, storage)) {
            if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, storage, speed)) {
                setStorageWalkTarget(villager, storage, speed);
                rememberStorageNavigationTarget(context, level, storage);
                HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
                return Result.MOVING;
            }
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, storage, speed)) {
                setStorageWalkTarget(villager, storage, speed);
                rememberStorageNavigationTarget(context, level, storage);
                HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
                return Result.MOVING;
            }
        }
        if (!villager.getNavigation().isDone() && isStorageNavigationTarget(storage, navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(level, villager, storage, distanceSqr)) {
                villager.getNavigation().stop();
                villager.getBrain().eraseMemory(MemoryModuleType.PATH);
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                clearStorageNavigationState(context);
                if (moveToStorageBlock(level, context, villager, storage, speed, distanceSqr)) {
                    return Result.MOVING;
                }
                if (moveToStorageApproach(level, context, villager, storage, speed, null)) {
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
            if (!shouldRepathStorage(context, level, storage)) {
                return Result.MOVING;
            }
            return Result.MOVING;
        }

        if (moveToStorageBlock(level, context, villager, storage, speed, distanceSqr)) {
            return Result.MOVING;
        }
        if (moveToStorageApproach(level, context, villager, storage, speed, null)) {
            return Result.MOVING;
        }
        if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, storage, speed)) {
            setStorageWalkTarget(villager, storage, speed);
            rememberStorageNavigationTarget(context, level, storage);
            HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
            return Result.MOVING;
        }
        if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, storage, speed)) {
            setStorageWalkTarget(villager, storage, speed);
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
            if (path != null && path.canReach() && villager.getNavigation().moveTo(path, speed * 0.75D)) {
                setStorageWalkTarget(villager, storage, speed);
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
        boolean moved = villager.getNavigation().moveTo(path, speed);
        if (moved) {
            setStorageWalkTarget(villager, storage, speed);
            rememberStorageNavigationTarget(context, level, storage);
            HiredPathMemory.rememberNavigationProgress(level, villager, storage, distanceSqr);
        }
        return moved;
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

    private static BlockPos bestStorageApproach(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            BlockPos excludedApproach) {
        if (!context.isLoaded(level, storage)) {
            return null;
        }
        if (AssignedStorageService.isInInteractionRange(villager, storage)) {
            return villager.blockPosition().immutable();
        }

        List<StorageApproach> candidates = new ArrayList<>();
        for (BlockPos rawCandidate : BlockPos.betweenClosed(
                storage.offset(-STORAGE_APPROACH_SEARCH_RADIUS, -2, -STORAGE_APPROACH_SEARCH_RADIUS),
                storage.offset(STORAGE_APPROACH_SEARCH_RADIUS, 2, STORAGE_APPROACH_SEARCH_RADIUS))) {
            BlockPos candidate = rawCandidate.immutable();
            if (!context.isLoaded(level, candidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)
                    || candidate.equals(excludedApproach)
                    || HiredPathMemory.isAvoided(level, villager, candidate)
                    || candidate.getCenter().distanceToSqr(storage.getCenter()) > 25.0D) {
                continue;
            }
            candidates.add(new StorageApproach(candidate, storageApproachScore(level, villager, candidate, storage)));
        }
        candidates.sort(Comparator.comparingDouble(StorageApproach::score));
        int attempts = 0;
        for (StorageApproach candidate : candidates) {
            if (attempts++ >= MAX_APPROACH_PATH_ATTEMPTS) {
                break;
            }
            Path path = villager.getNavigation().createPath(candidate.pos(), 0);
            if (path != null && path.canReach()) {
                return candidate.pos();
            }
        }
        return null;
    }

    private static boolean moveToApproach(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos approach,
            double speed,
            double distanceSqr) {
        Path path = villager.getNavigation().createPath(approach, STORAGE_WALK_TARGET_CLOSE_ENOUGH);
        if (path == null || !path.canReach()) {
            return false;
        }
        boolean moved = villager.getNavigation().moveTo(path, speed);
        if (moved) {
            setStorageWalkTarget(villager, approach, speed);
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

    static void clearStorageNavigationState(HiredWorkContext context) {
        context.state().remove(STORAGE_NAV_TARGET_TAG);
        context.state().remove(STORAGE_NAV_NEXT_REPATH_GAME_TIME_TAG);
    }

    private static boolean isStorageNavigationTarget(BlockPos storage, BlockPos navigationTarget) {
        return navigationTarget != null
                && (storage.equals(navigationTarget)
                || navigationTarget.distSqr(storage) <= STORAGE_APPROACH_SEARCH_RADIUS * STORAGE_APPROACH_SEARCH_RADIUS);
    }

    private static void setStorageWalkTarget(Villager villager, BlockPos target, double speed) {
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), (float) speed, STORAGE_WALK_TARGET_CLOSE_ENOUGH));
    }

    private static double storageApproachScore(ServerLevel level, Villager villager, BlockPos approach, BlockPos storage) {
        double distance = villager.distanceToSqr(approach.getCenter());
        int vertical = Math.abs(approach.getY() - villager.blockPosition().getY());
        double reachSlack = approach.getCenter().distanceToSqr(storage.getCenter());
        return distance
                + vertical * vertical * 3.0D
                + reachSlack * 0.25D
                + HiredMoveToBlockFaceJob.terrainCost(level, approach);
    }

    enum Result {
        ARRIVED,
        MOVING,
        FAILED
    }

    private record StorageApproach(BlockPos pos, double score) {
    }
}
