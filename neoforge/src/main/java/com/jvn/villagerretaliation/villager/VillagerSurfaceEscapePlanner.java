package com.jvn.villagerretaliation.villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;

public final class VillagerSurfaceEscapePlanner {
    private static final int SURFACE_ESCAPE_CHUNK_RADIUS = 0;
    private static final int SURFACE_ESCAPE_MIN_Y_GAIN = 3;
    private static final int SURFACE_ESCAPE_SURFACE_SCAN_DEPTH = 8;
    private static final double SURFACE_ESCAPE_TARGET_DISTANCE_MARGIN_SQR = 9.0D;
    private static final double SURFACE_ESCAPE_MAX_TARGET_DISTANCE_SQR = 64.0D;
    private static final int MAX_SURFACE_ESCAPE_PATH_ATTEMPTS = 4;
    private static final int SURFACE_ESCAPE_SEARCH_CACHE_TICKS = 20;
    private static final Map<UUID, SurfaceEscapeSearch> SURFACE_ESCAPE_SEARCHES = new HashMap<>();
    private static final double SURFACE_ESCAPE_SEARCH_ORIGIN_REUSE_DISTANCE_SQR = 16.0D;
    private VillagerSurfaceEscapePlanner() {
    }

    public static boolean moveTowardHighestSafePositionInLoadedChunk(ServerLevel level, Villager villager, BlockPos target, double speed) {
        if (target == null) {
            return false;
        }
        if (VillagerLadderTraversal.continueActiveLadderClimb(level, villager, target, speed)) {
            return true;
        }
        if (VillagerLadderTraversal.shouldAvoidRecentLadderDismount(level, villager, target)) {
            return false;
        }
        UUID villagerId = villager.getUUID();
        BlockPos origin = villager.blockPosition();
        SurfaceEscapeSearch cached = SURFACE_ESCAPE_SEARCHES.get(villagerId);
        if (cached != null
                && cached.expiresGameTime() > level.getGameTime()
                && cached.targetPos() == target.asLong()
                && cached.origin().distSqr(origin) <= SURFACE_ESCAPE_SEARCH_ORIGIN_REUSE_DISTANCE_SQR) {
            BlockPos escapeTarget = cached.escapeTarget();
            return escapeTarget != null
                    && isSafeSurfaceEscapeTarget(level, escapeTarget)
                    && moveToSurfaceEscapeTarget(level, villager, escapeTarget, speed);
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone()
                && navigationTarget != null
                && navigationTarget.getY() >= villager.blockPosition().getY() + SURFACE_ESCAPE_MIN_Y_GAIN
                && isSafeSurfaceEscapeTarget(level, navigationTarget)) {
            return true;
        }
        List<SurfaceEscapeTarget> candidates = highestSafeSurfaceTargets(level, villager, target);
        int attempts = 0;
        for (SurfaceEscapeTarget candidate : candidates) {
            if (attempts++ >= MAX_SURFACE_ESCAPE_PATH_ATTEMPTS) {
                break;
            }
            if (moveToSurfaceEscapeTarget(level, villager, candidate.pos(), speed)) {
                rememberSurfaceEscapeSearch(level, villager, target, candidate.pos());
                return true;
            }
        }
        rememberSurfaceEscapeSearch(level, villager, target, null);
        return false;
    }
    private static boolean moveToSurfaceEscapeTarget(ServerLevel level, Villager villager, BlockPos target, double speed) {
        if (VillagerLadderTraversal.moveOnLadderToward(level, villager, target, speed)) {
            return true;
        }
        Path path = villager.getNavigation().createPath(target, 0);
        if (path != null && path.canReach() && VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target, speed, 0)) {
            return true;
        }
        if (VillagerLadderTraversal.moveTowardNearbyLadderThenClimb(level, villager, target, speed)) {
            return true;
        }
        if (villager.distanceToSqr(target.getCenter()) <= 16.0D) {
            villager.getMoveControl().setWantedPosition(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
            return true;
        }
        return false;
    }
    private static void rememberSurfaceEscapeSearch(ServerLevel level, Villager villager, BlockPos target, BlockPos escapeTarget) {
        SURFACE_ESCAPE_SEARCHES.put(villager.getUUID(), new SurfaceEscapeSearch(
                villager.blockPosition().immutable(),
                target.asLong(),
                escapeTarget == null ? null : escapeTarget.immutable(),
                level.getGameTime() + SURFACE_ESCAPE_SEARCH_CACHE_TICKS));
    }
    private static List<SurfaceEscapeTarget> highestSafeSurfaceTargets(ServerLevel level, Villager villager, BlockPos target) {
        BlockPos origin = villager.blockPosition();
        int minimumY = Math.min(level.getMaxBuildHeight() - 2, origin.getY() + SURFACE_ESCAPE_MIN_Y_GAIN);
        List<SurfaceEscapeTarget> candidates = new ArrayList<>();
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        for (int chunkX = originChunkX - SURFACE_ESCAPE_CHUNK_RADIUS; chunkX <= originChunkX + SURFACE_ESCAPE_CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = originChunkZ - SURFACE_ESCAPE_CHUNK_RADIUS; chunkZ <= originChunkZ + SURFACE_ESCAPE_CHUNK_RADIUS; chunkZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int x = (chunkX << 4) + localX;
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int z = (chunkZ << 4) + localZ;
                        if (!level.hasChunkAt(new BlockPos(x, origin.getY(), z))) {
                            continue;
                        }
                        int surfaceY = Math.min(
                                level.getMaxBuildHeight() - 2,
                                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
                        int scanFloor = Math.max(minimumY, surfaceY - SURFACE_ESCAPE_SURFACE_SCAN_DEPTH);
                        for (int y = surfaceY; y >= scanFloor; y--) {
                            BlockPos candidate = new BlockPos(x, y, z);
                            if (isSafeSurfaceEscapeTarget(level, candidate)) {
                                if (!isUsefulSurfaceEscapeTarget(origin, target, candidate)) {
                                    break;
                                }
                                candidates.add(new SurfaceEscapeTarget(candidate, surfaceEscapeScore(villager, target, candidate)));
                                break;
                            }
                        }
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(SurfaceEscapeTarget::score));
        return candidates;
    }
    private static boolean isSafeSurfaceEscapeTarget(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return feet.isAir()
                && head.isAir()
                && floor.isSolid()
                && !floor.liquid();
    }
    private static double surfaceEscapeScore(Villager villager, BlockPos target, BlockPos pos) {
        double targetDistance = pos.distSqr(target);
        double villagerDistance = villager.distanceToSqr(pos.getCenter());
        return targetDistance * 4.0D + villagerDistance * 0.1D - pos.getY() * 0.5D;
    }
    private static boolean isUsefulSurfaceEscapeTarget(BlockPos origin, BlockPos target, BlockPos candidate) {
        double originTargetDistance = horizontalDistanceSqr(origin, target);
        double candidateTargetDistance = horizontalDistanceSqr(candidate, target);
        double maxAllowedDistance = Math.max(
                SURFACE_ESCAPE_MAX_TARGET_DISTANCE_SQR,
                originTargetDistance + SURFACE_ESCAPE_TARGET_DISTANCE_MARGIN_SQR);
        return candidateTargetDistance <= maxAllowedDistance;
    }
    private record SurfaceEscapeTarget(BlockPos pos, double score) {
    }
    private record SurfaceEscapeSearch(BlockPos origin, long targetPos, BlockPos escapeTarget, long expiresGameTime) {
    }

    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    public static void clearRuntimeState() {
        SURFACE_ESCAPE_SEARCHES.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        SURFACE_ESCAPE_SEARCHES.remove(villager.getUUID());
    }
}
