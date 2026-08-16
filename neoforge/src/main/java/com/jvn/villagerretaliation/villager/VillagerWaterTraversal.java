package com.jvn.villagerretaliation.villager;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class VillagerWaterTraversal {
    private static final float HIRED_WATER_PATH_COST = 32.0F;
    private static final float HIRED_WATER_BORDER_PATH_COST = 16.0F;
    private static final float HIRED_RISKY_PATH_COST = 32.0F;
    private static final float HIRED_BLOCKED_PATH_COST = -1.0F;
    private static final float HIRED_FARMING_WATER_PATH_COST = HIRED_WATER_PATH_COST;
    private static final float HIRED_FARMING_WATER_BORDER_PATH_COST = HIRED_WATER_BORDER_PATH_COST;
    private static final double WATER_TARGET_REACHED_DISTANCE_SQR = 2.25D;
    private static final double DEFAULT_WATER_SWIM_SPEED = 0.5D;
    private static final double WATER_VERTICAL_SPEED_LIMIT = 0.08D;
    private static final double WATER_IDLE_FLOAT_SPEED = 0.04D;
    private static final int WATER_SURFACE_SCAN_DEPTH = 3;
    private static final int WATER_STUCK_CHECK_TICKS = 10;
    private static final int WATER_STUCK_LIMIT = 2;
    private static final long WATER_ESCAPE_TICKS = 15L;
    private static final double WATER_STUCK_MIN_PROGRESS_SQR = 0.05D;
    private static final Map<UUID, HiredNavigationSettings> HIRED_NAVIGATION_SETTINGS = new HashMap<>();
    private static final Map<UUID, WaterMovementProgress> WATER_MOVEMENT_PROGRESS = new HashMap<>();
    private VillagerWaterTraversal() {
    }

    public static void tickVillagerWaterSafety(ServerLevel level, Villager villager) {
        restoreVillagerGravity(villager);
        if (!villager.isInWater()) {
            WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
            return;
        }
        keepVillagerBreathing(villager);
        if (villager.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanFloat(true);
        }
        moveInWaterTowardNavigationTarget(level, villager, DEFAULT_WATER_SWIM_SPEED);
    }
    public static void enableHiredWaterTraversal(Villager villager) {
        enableHiredWaterTraversal(villager, HIRED_WATER_PATH_COST, HIRED_WATER_BORDER_PATH_COST);
    }
    public static void enableHiredFarmingWaterTraversal(Villager villager) {
        enableHiredWaterTraversal(villager, HIRED_FARMING_WATER_PATH_COST, HIRED_FARMING_WATER_BORDER_PATH_COST);
    }
    public static void restoreHiredWaterTraversal(Villager villager) {
        WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
        HiredNavigationSettings settings = HIRED_NAVIGATION_SETTINGS.remove(villager.getUUID());
        if (settings == null) {
            return;
        }
        settings.pathMaluses().forEach(villager::setPathfindingMalus);
        if (villager.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanFloat(settings.canFloat());
        }
    }
    private static void applyHiredNavigationPolicy(Villager villager, float waterPathCost, float waterBorderPathCost) {
        villager.setPathfindingMalus(PathType.WATER, waterPathCost);
        villager.setPathfindingMalus(PathType.WATER_BORDER, waterBorderPathCost);
        villager.setPathfindingMalus(PathType.TRAPDOOR, HIRED_RISKY_PATH_COST);
        villager.setPathfindingMalus(PathType.DAMAGE_FIRE, HIRED_RISKY_PATH_COST);
        villager.setPathfindingMalus(PathType.DANGER_FIRE, HIRED_RISKY_PATH_COST);
        villager.setPathfindingMalus(PathType.DAMAGE_OTHER, HIRED_RISKY_PATH_COST);
        villager.setPathfindingMalus(PathType.DANGER_OTHER, HIRED_RISKY_PATH_COST);
        villager.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, HIRED_BLOCKED_PATH_COST);
        villager.setPathfindingMalus(PathType.POWDER_SNOW, HIRED_BLOCKED_PATH_COST);
        villager.setPathfindingMalus(PathType.LAVA, HIRED_BLOCKED_PATH_COST);
        villager.setPathfindingMalus(PathType.FENCE, HIRED_BLOCKED_PATH_COST);
        villager.setPathfindingMalus(PathType.LEAVES, HIRED_BLOCKED_PATH_COST);
        villager.setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, 0.0F);
        if (villager.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanFloat(true);
        }
    }
    public static boolean moveInWaterTowardNavigationTarget(ServerLevel level, Villager villager, double speed) {
        if (!villager.isInWater()) {
            WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
            return false;
        }
        keepVillagerBreathing(villager);
        Path path = villager.getNavigation().getPath();
        BlockPos target = nextWaterPathTarget(villager, path);
        if (target == null) {
            WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
            floatIdleInWater(level, villager);
            return false;
        }

        UUID villagerId = villager.getUUID();
        long now = level.getGameTime();
        WaterMovementProgress progress = WATER_MOVEMENT_PROGRESS.get(villagerId);
        if (progress != null
                && progress.targetPos() == target.asLong()
                && progress.escapeTarget() != null
                && progress.escapeUntilGameTime() > now
                && isWaterEscapeTarget(level, progress.escapeTarget())) {
            swimToward(villager, progress.escapeTarget(), speed * 0.85D);
            return true;
        }

        if (progress == null
                || progress.targetPos() != target.asLong()
                || now - progress.lastCheckGameTime() >= WATER_STUCK_CHECK_TICKS) {
            WaterMovementProgress nextProgress = waterMovementProgress(level, villager, target, progress, now);
            WATER_MOVEMENT_PROGRESS.put(villagerId, nextProgress);
            if (nextProgress.escapeTarget() != null && nextProgress.escapeUntilGameTime() > now) {
                swimToward(villager, nextProgress.escapeTarget(), speed * 0.85D);
                return true;
            }
        }

        swimToward(villager, target, speed);
        return true;
    }
    private static BlockPos nextWaterPathTarget(Villager villager, Path path) {
        if (path == null || path.isDone()) {
            return null;
        }
        int nodeIndex = path.getNextNodeIndex();
        while (nodeIndex + 1 < path.getNodeCount()
                && villager.distanceToSqr(path.getNode(nodeIndex).asBlockPos().getCenter())
                <= WATER_TARGET_REACHED_DISTANCE_SQR) {
            nodeIndex++;
        }
        BlockPos target = path.getNode(nodeIndex).asBlockPos();
        return villager.distanceToSqr(target.getCenter()) > WATER_TARGET_REACHED_DISTANCE_SQR
                ? target
                : null;
    }
    private static void keepVillagerBreathing(Villager villager) {
        if (villager.getAirSupply() < villager.getMaxAirSupply()) {
            villager.setAirSupply(villager.getMaxAirSupply());
        }
    }
    private static void floatIdleInWater(ServerLevel level, Villager villager) {
        BlockPos pos = villager.blockPosition();
        boolean hasAirAbove = false;
        for (int dy = 0; dy <= WATER_SURFACE_SCAN_DEPTH; dy++) {
            BlockPos check = pos.above(dy);
            if (!level.hasChunkAt(check) || level.getBlockState(check).liquid()) {
                continue;
            }
            hasAirAbove = true;
            break;
        }
        Vec3 motion = villager.getDeltaMovement();
        double vertical = hasAirAbove ? WATER_IDLE_FLOAT_SPEED : Math.max(motion.y, 0.0D);
        villager.setDeltaMovement(motion.x * 0.85D, vertical, motion.z * 0.85D);
        villager.setOnGround(false);
    }
    private static WaterMovementProgress waterMovementProgress(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            WaterMovementProgress previous,
            long now) {
        int stuckChecks = 0;
        if (previous != null && previous.targetPos() == target.asLong()) {
            double dx = villager.getX() - previous.x();
            double dz = villager.getZ() - previous.z();
            stuckChecks = dx * dx + dz * dz > WATER_STUCK_MIN_PROGRESS_SQR ? 0 : previous.stuckChecks() + 1;
        }
        BlockPos escapeTarget = null;
        long escapeUntil = 0L;
        if (stuckChecks >= WATER_STUCK_LIMIT) {
            escapeTarget = bestWaterEscapeTarget(level, villager, target);
            if (escapeTarget != null) {
                stuckChecks = 0;
                escapeUntil = now + WATER_ESCAPE_TICKS;
            }
        }
        return new WaterMovementProgress(
                target.asLong(),
                villager.getX(),
                villager.getZ(),
                now,
                stuckChecks,
                escapeTarget,
                escapeUntil);
    }
    private static BlockPos bestWaterEscapeTarget(ServerLevel level, Villager villager, BlockPos target) {
        BlockPos origin = villager.blockPosition();
        Vec3 targetDirection = target.getCenter().subtract(villager.position()).normalize();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos rawPos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 1, 2))) {
            BlockPos candidate = rawPos.immutable();
            if (candidate.equals(origin) || !isWaterEscapeTarget(level, candidate)) {
                continue;
            }
            Vec3 offset = candidate.getCenter().subtract(villager.position());
            double distance = offset.lengthSqr();
            if (distance > 9.0D) {
                continue;
            }
            Vec3 direction = offset.normalize();
            double forwardAlignment = direction.dot(targetDirection);
            double score = Math.abs(forwardAlignment) * 2.0D
                    + candidate.distSqr(target) * 0.02D
                    + distance * 0.25D
                    - waterClearance(level, candidate);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
    private static boolean isWaterEscapeTarget(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return isWaterPassable(feet)
                && isWaterPassable(head)
                && (feet.liquid() || floor.isSolid() || floor.liquid());
    }
    private static boolean isWaterPassable(BlockState state) {
        return state.isAir() || state.liquid() || state.is(Blocks.LADDER);
    }
    private static double waterClearance(ServerLevel level, BlockPos pos) {
        double clearance = 0.0D;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (level.hasChunkAt(side) && isWaterPassable(level.getBlockState(side))) {
                clearance += 1.0D;
            }
        }
        return clearance;
    }
    private static void swimToward(Villager villager, BlockPos target, double speed) {
        double targetY = target.getY() + 0.1D;
        villager.getMoveControl().setWantedPosition(target.getX() + 0.5D, targetY, target.getZ() + 0.5D, speed);
        Vec3 motion = villager.getDeltaMovement();
        double vertical = Math.clamp(targetY - villager.getY(), -WATER_VERTICAL_SPEED_LIMIT, WATER_VERTICAL_SPEED_LIMIT);
        villager.setDeltaMovement(motion.x, vertical, motion.z);
        villager.setOnGround(false);
    }
    private static void restoreVillagerGravity(Villager villager) {
        if (villager.isNoGravity()) {
            villager.setNoGravity(false);
        }
    }
    private record HiredNavigationSettings(Map<PathType, Float> pathMaluses, boolean canFloat) {
        private static HiredNavigationSettings capture(Villager villager, boolean canFloat) {
            EnumMap<PathType, Float> pathMaluses = new EnumMap<>(PathType.class);
            pathMaluses.put(PathType.WATER, villager.getPathfindingMalus(PathType.WATER));
            pathMaluses.put(PathType.WATER_BORDER, villager.getPathfindingMalus(PathType.WATER_BORDER));
            pathMaluses.put(PathType.TRAPDOOR, villager.getPathfindingMalus(PathType.TRAPDOOR));
            pathMaluses.put(PathType.DAMAGE_FIRE, villager.getPathfindingMalus(PathType.DAMAGE_FIRE));
            pathMaluses.put(PathType.DANGER_FIRE, villager.getPathfindingMalus(PathType.DANGER_FIRE));
            pathMaluses.put(PathType.DAMAGE_OTHER, villager.getPathfindingMalus(PathType.DAMAGE_OTHER));
            pathMaluses.put(PathType.DANGER_OTHER, villager.getPathfindingMalus(PathType.DANGER_OTHER));
            pathMaluses.put(PathType.DANGER_POWDER_SNOW, villager.getPathfindingMalus(PathType.DANGER_POWDER_SNOW));
            pathMaluses.put(PathType.POWDER_SNOW, villager.getPathfindingMalus(PathType.POWDER_SNOW));
            pathMaluses.put(PathType.LAVA, villager.getPathfindingMalus(PathType.LAVA));
            pathMaluses.put(PathType.FENCE, villager.getPathfindingMalus(PathType.FENCE));
            pathMaluses.put(PathType.LEAVES, villager.getPathfindingMalus(PathType.LEAVES));
            pathMaluses.put(PathType.DOOR_WOOD_CLOSED, villager.getPathfindingMalus(PathType.DOOR_WOOD_CLOSED));
            return new HiredNavigationSettings(pathMaluses, canFloat);
        }
    }
    private record WaterMovementProgress(
            long targetPos,
            double x,
            double z,
            long lastCheckGameTime,
            int stuckChecks,
            BlockPos escapeTarget,
            long escapeUntilGameTime) {
    }

    private static void enableHiredWaterTraversal(Villager villager, float waterPathCost, float waterBorderPathCost) {
        boolean canFloat = villager.getNavigation() instanceof GroundPathNavigation navigation && navigation.canFloat();
        HIRED_NAVIGATION_SETTINGS.computeIfAbsent(villager.getUUID(), ignored -> HiredNavigationSettings.capture(villager, canFloat));
        applyHiredNavigationPolicy(villager, waterPathCost, waterBorderPathCost);
    }

    public static void clearRuntimeState() {
        HIRED_NAVIGATION_SETTINGS.clear();
        WATER_MOVEMENT_PROGRESS.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        HIRED_NAVIGATION_SETTINGS.remove(villager.getUUID());
        WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
    }
}
