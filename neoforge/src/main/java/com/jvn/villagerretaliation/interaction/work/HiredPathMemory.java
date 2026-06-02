package com.jvn.villagerretaliation.interaction.work;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

final class HiredPathMemory {
    private static final int PATH_FAILURE_LIMIT = 3;
    private static final long TARGET_BLACKLIST_TICKS = 20L * 30L;
    private static final long RECENT_TARGET_TICKS = 20L * 45L;
    private static final double RECENT_TARGET_EXTRA_COST = 36.0D;
    private static final int STUCK_CHECK_TICKS = 20;
    private static final int STUCK_LIMIT = 4;
    private static final double STUCK_MIN_PROGRESS_SQR = 0.20D;
    private static final double CLOSE_ENOUGH_SQR = 2.25D;
    private static final Map<UUID, Map<Long, Long>> AVOIDED_TARGETS = new HashMap<>();
    private static final Map<UUID, Map<Long, Integer>> PATH_FAILURES = new HashMap<>();
    private static final Map<Long, RecentTarget> RECENT_TARGETS = new HashMap<>();
    private static final Map<UUID, NavigationProgress> NAVIGATION_PROGRESS = new HashMap<>();

    private HiredPathMemory() {
    }

    static void clear() {
        AVOIDED_TARGETS.clear();
        PATH_FAILURES.clear();
        RECENT_TARGETS.clear();
        NAVIGATION_PROGRESS.clear();
    }

    static void expire(ServerLevel level) {
        long now = level.getGameTime();
        AVOIDED_TARGETS.values().forEach(targets -> targets.entrySet().removeIf(entry -> entry.getValue() <= now));
        AVOIDED_TARGETS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        PATH_FAILURES.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        RECENT_TARGETS.entrySet().removeIf(entry -> entry.getValue().expiresGameTime() <= now);
        NAVIGATION_PROGRESS.entrySet().removeIf(entry -> entry.getValue().expiresGameTime() <= now);
    }

    static boolean recordFailure(ServerLevel level, Villager villager, BlockPos pos) {
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

    static void clearFailure(Villager villager, BlockPos pos) {
        Map<Long, Integer> failures = PATH_FAILURES.get(villager.getUUID());
        if (failures != null) {
            failures.remove(pos.asLong());
        }
    }

    static boolean isAvoided(ServerLevel level, Villager villager, BlockPos pos) {
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

    static void rememberRecent(ServerLevel level, BlockPos pos) {
        RECENT_TARGETS.put(pos.asLong(), new RecentTarget(
                pos.immutable(),
                level.getGameTime() + RECENT_TARGET_TICKS,
                RECENT_TARGET_EXTRA_COST));
    }

    static double recentCost(Villager villager, BlockPos target) {
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

    static boolean isNavigationBlocked(ServerLevel level, Villager villager, BlockPos targetPos, double distanceSqr) {
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

    static void rememberNavigationProgress(ServerLevel level, Villager villager, BlockPos targetPos, double distanceSqr) {
        long now = level.getGameTime();
        NAVIGATION_PROGRESS.put(villager.getUUID(), new NavigationProgress(
                targetPos.asLong(),
                distanceSqr,
                now,
                0,
                now + TARGET_BLACKLIST_TICKS));
    }

    static void clearNavigationProgress(Villager villager) {
        NAVIGATION_PROGRESS.remove(villager.getUUID());
    }

    private record RecentTarget(BlockPos pos, long expiresGameTime, double extraCost) {
    }

    private record NavigationProgress(
            long targetPos,
            double distanceSqr,
            long lastCheckGameTime,
            int stuckChecks,
            long expiresGameTime) {
    }
}
