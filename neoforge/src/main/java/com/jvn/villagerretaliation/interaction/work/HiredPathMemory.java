package com.jvn.villagerretaliation.interaction.work;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

public final class HiredPathMemory {
    private static final int PATH_FAILURE_LIMIT = 3;
    private static final long TARGET_BLACKLIST_TICKS = 20L * 30L;
    private static final long RECENT_TARGET_TICKS = 20L * 45L;
    private static final long TARGET_RESERVATION_TICKS = 20L * 20L;
    private static final long UNREACHABLE_APPROACH_BASE_TICKS = 20L * 4L;
    private static final long UNREACHABLE_APPROACH_MAX_TICKS = 20L * 20L;
    private static final long PATH_BACKOFF_BASE_TICKS = 10L;
    private static final long PATH_BACKOFF_MAX_TICKS = 20L * 8L;
    private static final int PATH_BACKOFF_JITTER_TICKS = 11;
    private static final int MIN_BACKED_OFF_CANDIDATES = 8;
    private static final double RECENT_TARGET_EXTRA_COST = 36.0D;
    private static final int STUCK_CHECK_TICKS = 20;
    private static final int STUCK_LIMIT = 4;
    private static final double STUCK_MIN_PROGRESS_SQR = 0.20D;
    private static final double CLOSE_ENOUGH_SQR = 2.25D;
    private static final Map<UUID, Map<Long, Long>> AVOIDED_TARGETS = new HashMap<>();
    private static final Map<UUID, Map<Long, Integer>> PATH_FAILURES = new HashMap<>();
    private static final Map<UUID, Map<Long, Long>> UNREACHABLE_APPROACHES = new HashMap<>();
    private static final Map<UUID, PathSearchBackoff> PATH_SEARCH_BACKOFFS = new HashMap<>();
    private static final Map<UUID, PathCreationCounter> PATH_CREATION_COUNTERS = new HashMap<>();
    private static final Map<Long, RecentTarget> RECENT_TARGETS = new HashMap<>();
    private static final Map<UUID, NavigationProgress> NAVIGATION_PROGRESS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, TargetReservation>> TARGET_RESERVATIONS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LAST_EXPIRE_GAME_TIME = new HashMap<>();

    private HiredPathMemory() {
    }

    public static void clear() {
        AVOIDED_TARGETS.clear();
        PATH_FAILURES.clear();
        UNREACHABLE_APPROACHES.clear();
        PATH_SEARCH_BACKOFFS.clear();
        PATH_CREATION_COUNTERS.clear();
        RECENT_TARGETS.clear();
        NAVIGATION_PROGRESS.clear();
        TARGET_RESERVATIONS.clear();
        LAST_EXPIRE_GAME_TIME.clear();
    }

    public static void clear(Villager villager) {
        UUID villagerId = villager.getUUID();
        AVOIDED_TARGETS.remove(villagerId);
        PATH_FAILURES.remove(villagerId);
        UNREACHABLE_APPROACHES.remove(villagerId);
        PATH_SEARCH_BACKOFFS.remove(villagerId);
        PATH_CREATION_COUNTERS.remove(villagerId);
        NAVIGATION_PROGRESS.remove(villagerId);
        TARGET_RESERVATIONS.values().forEach(targets -> targets.entrySet().removeIf(entry -> entry.getValue().villagerId().equals(villagerId)));
        TARGET_RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static void expire(ServerLevel level) {
        long now = level.getGameTime();
        ResourceKey<Level> dimension = level.dimension();
        Long lastExpireGameTime = LAST_EXPIRE_GAME_TIME.get(dimension);
        if (lastExpireGameTime != null && lastExpireGameTime.longValue() == now) {
            return;
        }
        LAST_EXPIRE_GAME_TIME.put(dimension, now);

        AVOIDED_TARGETS.values().forEach(targets -> targets.entrySet().removeIf(entry -> entry.getValue() <= now));
        AVOIDED_TARGETS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        PATH_FAILURES.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        UNREACHABLE_APPROACHES.values().forEach(targets -> targets.entrySet().removeIf(entry -> entry.getValue() <= now));
        UNREACHABLE_APPROACHES.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        PATH_SEARCH_BACKOFFS.entrySet().removeIf(entry -> entry.getValue().expiresGameTime() <= now && entry.getValue().retryAfterGameTime() <= now);
        RECENT_TARGETS.entrySet().removeIf(entry -> entry.getValue().expiresGameTime() <= now);
        NAVIGATION_PROGRESS.entrySet().removeIf(entry -> entry.getValue().expiresGameTime() <= now);
        TARGET_RESERVATIONS.values().forEach(targets -> targets.entrySet().removeIf(entry -> entry.getValue().expiresGameTime() <= now));
        TARGET_RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static boolean recordFailure(ServerLevel level, Villager villager, BlockPos pos) {
        UUID villagerId = villager.getUUID();
        long packed = pos.asLong();
        Map<Long, Integer> failures = PATH_FAILURES.computeIfAbsent(villagerId, ignored -> new HashMap<>());
        int count = failures.getOrDefault(packed, 0) + 1;
        failures.put(packed, count);
        if (count < PATH_FAILURE_LIMIT) {
            return false;
        }
        AVOIDED_TARGETS
                .computeIfAbsent(villagerId, ignored -> new HashMap<>())
                .put(packed, level.getGameTime() + TARGET_BLACKLIST_TICKS);
        failures.remove(packed);
        return true;
    }

    public static void clearFailure(Villager villager, BlockPos pos) {
        Map<Long, Integer> failures = PATH_FAILURES.get(villager.getUUID());
        if (failures != null) {
            failures.remove(pos.asLong());
        }
    }

    public static void clearAvoided(Villager villager, BlockPos pos) {
        UUID villagerId = villager.getUUID();
        Map<Long, Long> avoided = AVOIDED_TARGETS.get(villagerId);
        if (avoided != null) {
            avoided.remove(pos.asLong());
            if (avoided.isEmpty()) {
                AVOIDED_TARGETS.remove(villagerId);
            }
        }
        clearFailure(villager, pos);
    }

    public static Path createPath(ServerLevel level, Villager villager, BlockPos pos, int closeEnoughDistance) {
        recordPathCreated(level, villager);
        return villager.getNavigation().createPath(pos, closeEnoughDistance);
    }

    public static boolean shouldDelayPathSearch(ServerLevel level, Villager villager) {
        PathSearchBackoff backoff = PATH_SEARCH_BACKOFFS.get(villager.getUUID());
        return backoff != null && backoff.retryAfterGameTime() > level.getGameTime();
    }

    public static long recordPathSearchFailure(ServerLevel level, Villager villager) {
        UUID villagerId = villager.getUUID();
        PathSearchBackoff previous = PATH_SEARCH_BACKOFFS.get(villagerId);
        int failures = previous == null ? 1 : Math.min(previous.consecutiveFailures() + 1, 8);
        long now = level.getGameTime();
        long retryAfterGameTime = now + pathBackoffDelayTicks(villager, failures);
        PATH_SEARCH_BACKOFFS.put(villagerId, new PathSearchBackoff(
                failures,
                retryAfterGameTime,
                now + TARGET_BLACKLIST_TICKS));
        return retryAfterGameTime;
    }

    public static void clearPathSearchFailures(Villager villager) {
        PATH_SEARCH_BACKOFFS.remove(villager.getUUID());
    }

    public static int adjustedCandidateLimit(Villager villager, int requestedLimit) {
        int safeLimit = Math.max(1, requestedLimit);
        int failures = pathSearchFailureStreak(villager);
        if (failures <= 1) {
            return safeLimit;
        }
        int divisor = 1 << Math.min(3, failures - 1);
        return Math.clamp((int) Math.ceil(safeLimit / (double) divisor), Math.min(MIN_BACKED_OFF_CANDIDATES, safeLimit), safeLimit);
    }

    public static int pathSearchFailureStreak(Villager villager) {
        PathSearchBackoff backoff = PATH_SEARCH_BACKOFFS.get(villager.getUUID());
        return backoff == null ? 0 : backoff.consecutiveFailures();
    }

    public static long pathSearchRetryCooldownTicks(ServerLevel level, Villager villager) {
        PathSearchBackoff backoff = PATH_SEARCH_BACKOFFS.get(villager.getUUID());
        if (backoff == null) {
            return 0L;
        }
        return Math.max(0L, backoff.retryAfterGameTime() - level.getGameTime());
    }

    public static void rememberUnreachableApproach(ServerLevel level, Villager villager, BlockPos approach) {
        int failures = Math.max(1, pathSearchFailureStreak(villager));
        long delay = Math.min(UNREACHABLE_APPROACH_MAX_TICKS, UNREACHABLE_APPROACH_BASE_TICKS << Math.min(3, failures - 1));
        long expiresGameTime = level.getGameTime() + delay + jitterTicks(villager, failures);
        UNREACHABLE_APPROACHES
                .computeIfAbsent(villager.getUUID(), ignored -> new HashMap<>())
                .put(approach.asLong(), expiresGameTime);
    }

    public static void clearUnreachableApproach(Villager villager, BlockPos approach) {
        UUID villagerId = villager.getUUID();
        Map<Long, Long> approaches = UNREACHABLE_APPROACHES.get(villagerId);
        if (approaches == null) {
            return;
        }
        approaches.remove(approach.asLong());
        if (approaches.isEmpty()) {
            UNREACHABLE_APPROACHES.remove(villagerId);
        }
    }

    public static boolean isApproachRecentlyUnreachable(ServerLevel level, Villager villager, BlockPos approach) {
        Map<Long, Long> approaches = UNREACHABLE_APPROACHES.get(villager.getUUID());
        if (approaches == null) {
            return false;
        }
        long packed = approach.asLong();
        Long expiresGameTime = approaches.get(packed);
        if (expiresGameTime == null) {
            return false;
        }
        if (expiresGameTime <= level.getGameTime()) {
            approaches.remove(packed);
            return false;
        }
        return true;
    }

    public static int recentlyUnreachableApproachCount(Villager villager) {
        Map<Long, Long> approaches = UNREACHABLE_APPROACHES.get(villager.getUUID());
        return approaches == null ? 0 : approaches.size();
    }

    public static PathCreationDebug pathCreationDebug(ServerLevel level, Villager villager) {
        PathCreationCounter counter = PATH_CREATION_COUNTERS.get(villager.getUUID());
        if (counter == null) {
            return new PathCreationDebug(0, 0, 0L, 0L, 0, 0, 0L);
        }
        long now = level.getGameTime();
        int currentTickCount = counter.currentGameTime() == now ? counter.currentTickCount() : 0;
        int lastTickCount = counter.currentGameTime() == now ? counter.lastTickCount() : counter.currentTickCount();
        long lastGameTime = counter.currentGameTime() == now ? counter.lastGameTime() : counter.currentGameTime();
        return new PathCreationDebug(
                currentTickCount,
                lastTickCount,
                counter.totalCount(),
                lastGameTime,
                pathSearchFailureStreak(villager),
                recentlyUnreachableApproachCount(villager),
                pathSearchRetryCooldownTicks(level, villager));
    }

    public static boolean isAvoided(ServerLevel level, Villager villager, BlockPos pos) {
        Map<Long, Long> avoided = AVOIDED_TARGETS.get(villager.getUUID());
        if (avoided == null) {
            return false;
        }
        Long expiresGameTime = avoided.get(pos.asLong());
        if (expiresGameTime == null) {
            return false;
        }
        if (expiresGameTime <= level.getGameTime()) {
            avoided.remove(pos.asLong());
            return false;
        }
        return true;
    }

    public static boolean isReservedByOther(ServerLevel level, Villager villager, BlockPos pos) {
        Map<Long, TargetReservation> reservations = TARGET_RESERVATIONS.get(level.dimension());
        if (reservations == null) {
            return false;
        }
        TargetReservation reservation = reservations.get(pos.asLong());
        if (reservation == null) {
            return false;
        }
        if (reservation.expiresGameTime() <= level.getGameTime()) {
            reservations.remove(pos.asLong());
            return false;
        }
        return !reservation.villagerId().equals(villager.getUUID());
    }

    public static void reserveTarget(ServerLevel level, Villager villager, BlockPos pos) {
        TARGET_RESERVATIONS
                .computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .put(pos.asLong(), new TargetReservation(villager.getUUID(), level.getGameTime() + TARGET_RESERVATION_TICKS));
    }

    public static void releaseTarget(ServerLevel level, Villager villager, BlockPos pos) {
        Map<Long, TargetReservation> reservations = TARGET_RESERVATIONS.get(level.dimension());
        if (reservations == null) {
            return;
        }
        TargetReservation reservation = reservations.get(pos.asLong());
        if (reservation != null && reservation.villagerId().equals(villager.getUUID())) {
            reservations.remove(pos.asLong());
        }
    }

    public static void releaseAll(Villager villager) {
        UUID villagerId = villager.getUUID();
        TARGET_RESERVATIONS.values().forEach(targets -> targets.entrySet().removeIf(entry -> entry.getValue().villagerId().equals(villagerId)));
        TARGET_RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static void rememberRecent(ServerLevel level, BlockPos pos) {
        RECENT_TARGETS.put(pos.asLong(), new RecentTarget(
                pos.immutable(),
                level.getGameTime() + RECENT_TARGET_TICKS,
                RECENT_TARGET_EXTRA_COST));
    }

    public static double recentCost(Villager villager, BlockPos target) {
        long now = villager.level().getGameTime();
        double cost = 0.0D;
        for (RecentTarget recent : RECENT_TARGETS.values()) {
            if (recent.expiresGameTime() <= now) {
                continue;
            }
            int manhattan = Math.abs(recent.pos().getX() - target.getX())
                    + Math.abs(recent.pos().getY() - target.getY())
                    + Math.abs(recent.pos().getZ() - target.getZ());
            if (manhattan <= 3) {
                cost = Math.max(cost, recent.extraCost() / (1.0D + manhattan));
            }
        }
        return cost;
    }

    public static boolean isNavigationBlocked(ServerLevel level, Villager villager, BlockPos targetPos, double distanceSqr) {
        if (distanceSqr <= CLOSE_ENOUGH_SQR) {
            rememberNavigationProgress(level, villager, targetPos, distanceSqr);
            return false;
        }

        UUID villagerId = villager.getUUID();
        NavigationProgress progress = NAVIGATION_PROGRESS.get(villagerId);
        long packedTargetPos = targetPos.asLong();
        long now = level.getGameTime();
        if (progress == null || progress.targetPos() != packedTargetPos) {
            rememberNavigationProgress(level, villager, targetPos, distanceSqr);
            return false;
        }
        if (now - progress.lastCheckGameTime() < STUCK_CHECK_TICKS) {
            return false;
        }

        double improvement = progress.distanceSqr() - distanceSqr;
        int stuckChecks = improvement > STUCK_MIN_PROGRESS_SQR ? 0 : progress.stuckChecks() + 1;
        NAVIGATION_PROGRESS.put(villagerId, new NavigationProgress(
                packedTargetPos,
                distanceSqr,
                now,
                stuckChecks,
                now + TARGET_BLACKLIST_TICKS));
        return stuckChecks >= STUCK_LIMIT;
    }

    public static boolean observeNavigationProgress(ServerLevel level, Villager villager, BlockPos targetPos, double distanceSqr) {
        return !isNavigationBlocked(level, villager, targetPos, distanceSqr);
    }

    public static void rememberNavigationProgress(ServerLevel level, Villager villager, BlockPos targetPos, double distanceSqr) {
        long now = level.getGameTime();
        NAVIGATION_PROGRESS.put(villager.getUUID(), new NavigationProgress(
                targetPos.asLong(),
                distanceSqr,
                now,
                0,
                now + TARGET_BLACKLIST_TICKS));
    }

    public static void clearNavigationProgress(Villager villager) {
        NAVIGATION_PROGRESS.remove(villager.getUUID());
    }

    private static void recordPathCreated(ServerLevel level, Villager villager) {
        UUID villagerId = villager.getUUID();
        long now = level.getGameTime();
        PathCreationCounter previous = PATH_CREATION_COUNTERS.get(villagerId);
        if (previous == null) {
            PATH_CREATION_COUNTERS.put(villagerId, new PathCreationCounter(now, 1, 0L, 0, 1L));
            return;
        }
        if (previous.currentGameTime() == now) {
            PATH_CREATION_COUNTERS.put(villagerId, new PathCreationCounter(
                    now,
                    previous.currentTickCount() + 1,
                    previous.lastGameTime(),
                    previous.lastTickCount(),
                    previous.totalCount() + 1L));
            return;
        }
        PATH_CREATION_COUNTERS.put(villagerId, new PathCreationCounter(
                now,
                1,
                previous.currentGameTime(),
                previous.currentTickCount(),
                previous.totalCount() + 1L));
    }

    private static long pathBackoffDelayTicks(Villager villager, int failures) {
        long exponential = PATH_BACKOFF_BASE_TICKS << Math.min(4, Math.max(0, failures - 1));
        return Math.min(PATH_BACKOFF_MAX_TICKS, exponential) + jitterTicks(villager, failures);
    }

    private static int jitterTicks(Villager villager, int failures) {
        long mixed = villager.getUUID().getLeastSignificantBits()
                ^ Long.rotateLeft(villager.getUUID().getMostSignificantBits(), failures & 31)
                ^ (long) failures * 0x9E3779B97F4A7C15L;
        return (int) Math.floorMod(mixed, PATH_BACKOFF_JITTER_TICKS + 1L);
    }

    private record RecentTarget(BlockPos pos, long expiresGameTime, double extraCost) {
    }

    private record PathSearchBackoff(int consecutiveFailures, long retryAfterGameTime, long expiresGameTime) {
    }

    private record PathCreationCounter(
            long currentGameTime,
            int currentTickCount,
            long lastGameTime,
            int lastTickCount,
            long totalCount) {
    }

    public record PathCreationDebug(
            int currentTickCount,
            int lastTickCount,
            long totalCount,
            long lastGameTime,
            int failureStreak,
            int unreachableApproaches,
            long retryCooldownTicks) {
    }

    private record TargetReservation(UUID villagerId, long expiresGameTime) {
    }

    private record NavigationProgress(
            long targetPos,
            double distanceSqr,
            long lastCheckGameTime,
            int stuckChecks,
            long expiresGameTime) {
    }
}
