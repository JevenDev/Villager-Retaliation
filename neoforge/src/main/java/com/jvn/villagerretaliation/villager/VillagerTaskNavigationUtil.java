package com.jvn.villagerretaliation.villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class VillagerTaskNavigationUtil {
    private static final double DOOR_REACH_DISTANCE = 2.25D;
    private static final double DOOR_REACH_DISTANCE_SQR = DOOR_REACH_DISTANCE * DOOR_REACH_DISTANCE;
    private static final double LADDER_ENTRY_DISTANCE_SQR = 2.25D;
    // A villager approaching a ladder from the adjacent block can stop with its
    // bounding box against the rung at about one block from its center. Leave a
    // small floating-point margin around that boundary while remaining well
    // inside the adjacent-block entry gate.
    private static final double LADDER_FORCED_ENTRY_HORIZONTAL_SQR = 1.21D;
    private static final int LADDER_VERTICAL_TARGET_DEADZONE = 0;
    private static final double LADDER_HORIZONTAL_SPEED_LIMIT = 0.15D;
    private static final double LADDER_CLIMB_SPEED = 0.20D;
    private static final double LADDER_DESCEND_SPEED = -0.15D;
    private static final double LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT = 0.18D;
    private static final double LADDER_DISMOUNT_UPWARD_SPEED = 0.10D;
    private static final double LADDER_TOP_DISMOUNT_Y_OFFSET = 1.0D;
    private static final double LADDER_TOP_HEAD_DISMOUNT_OFFSET = 0.75D;
    private static final double LADDER_BOTTOM_DISMOUNT_Y_OFFSET = 0.15D;
    private static final double LADDER_DISMOUNT_REACHED_DISTANCE_SQR = 1.0D;
    private static final long ACTIVE_LADDER_CLIMB_TICKS = 80L;
    private static final long RECENT_LADDER_DISMOUNT_TICKS = 60L;
    private static final int RECENT_LADDER_SETTLE_CHECKS = 20;
    private static final double RECENT_LADDER_DISMOUNT_DISTANCE_SQR = 16.0D;
    private static final double ACTIVE_LADDER_COLUMN_DISTANCE_SQR = 2.25D;
    private static final int LADDER_ESCAPE_SEARCH_RADIUS = 12;
    private static final int LADDER_ESCAPE_VERTICAL_RADIUS = 24;
    private static final int LADDER_SEARCH_CACHE_TICKS = 20;
    private static final double LADDER_SEARCH_ORIGIN_REUSE_DISTANCE_SQR = 16.0D;
    private static final double LADDER_ROUTE_PROGRESS_EPSILON_SQR = 0.10D;
    private static final int LADDER_ROUTE_STALLED_CHECKS = 20;
    private static final double LADDER_DIRECT_APPROACH_SPEED_LIMIT = 0.16D;
    private static final int SURFACE_ESCAPE_CHUNK_RADIUS = 0;
    private static final int SURFACE_ESCAPE_MIN_Y_GAIN = 3;
    private static final int SURFACE_ESCAPE_SURFACE_SCAN_DEPTH = 8;
    private static final double SURFACE_ESCAPE_TARGET_DISTANCE_MARGIN_SQR = 9.0D;
    private static final double SURFACE_ESCAPE_MAX_TARGET_DISTANCE_SQR = 64.0D;
    private static final int MAX_SURFACE_ESCAPE_PATH_ATTEMPTS = 4;
    private static final int SURFACE_ESCAPE_SEARCH_CACHE_TICKS = 20;
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
    private static final double PATH_STEP_NODE_REACHED_DISTANCE_SQR = 0.64D;
    private static final double PATH_STEP_HEIGHT_EPSILON = 0.001D;
    private static final double PATH_STEP_MAX_HEIGHT_DIFFERENCE = 0.5D;
    private static final Map<UUID, Set<GlobalPos>> DOORS_TO_CLOSE = new HashMap<>();
    private static final Map<UUID, ActiveLadderClimb> ACTIVE_LADDER_CLIMBS = new HashMap<>();
    private static final Map<UUID, RecentLadderDismount> RECENT_LADDER_DISMOUNTS = new HashMap<>();
    private static final Map<UUID, LadderSearch> LADDER_SEARCHES = new HashMap<>();
    private static final Map<UUID, SurfaceEscapeSearch> SURFACE_ESCAPE_SEARCHES = new HashMap<>();
    private static final Map<UUID, HiredNavigationSettings> HIRED_NAVIGATION_SETTINGS = new HashMap<>();
    private static final Map<UUID, WaterMovementProgress> WATER_MOVEMENT_PROGRESS = new HashMap<>();
    private VillagerTaskNavigationUtil() {
    }

    public static void stopNavigationAndClearTargets(Villager villager) {
        clearHiredWalkTarget(villager);
        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearMovement(villager);
    }

    public static boolean moveToHiredPath(
            Villager villager,
            Path path,
            BlockPos target,
            double speed,
            int closeEnough) {
        if (path == null || target == null) {
            return false;
        }
        Brain<Villager> brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.PATH, path);
        setHiredWalkTarget(villager, target, speed, closeEnough);
        if (villager.getNavigation().moveTo(path, speed)) {
            return true;
        }
        clearHiredWalkTarget(villager);
        VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);
        return false;
    }

    public static void setHiredWalkTarget(Villager villager, BlockPos target, double speed, int closeEnough) {
        if (target == null) {
            clearHiredWalkTarget(villager);
            VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);
            return;
        }
        hiredNavigationState(villager).villagerretaliation$setHiredWalkTarget(target);
        float speedModifier = (float) speed;
        if (hasMatchingWalkTarget(villager, target, speedModifier, closeEnough)) {
            return;
        }
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), speedModifier, closeEnough));
    }

    public static void stopHiredNavigation(Villager villager) {
        clearHiredWalkTarget(villager);
        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
    }

    public static boolean hasActiveHiredWalkTarget(Villager villager) {
        return !villager.getNavigation().isDone() && isHiredWalkTarget(villager);
    }

    public static boolean isHiredWalkTarget(Villager villager) {
        BlockPos target = hiredNavigationState(villager).villagerretaliation$getHiredWalkTarget();
        if (target == null) {
            return false;
        }
        return villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .map(tracker -> tracker.currentBlockPosition().equals(target))
                .orElse(false);
    }

    private static HiredNavigationState hiredNavigationState(Villager villager) {
        return (HiredNavigationState) villager;
    }

    private static void clearHiredWalkTarget(Villager villager) {
        hiredNavigationState(villager).villagerretaliation$setHiredWalkTarget(null);
    }

    private static boolean hasMatchingWalkTarget(Villager villager, BlockPos target, float speedModifier, int closeEnough) {
        return villager.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(walkTarget -> walkTarget.getTarget().currentBlockPosition().equals(target)
                        && walkTarget.getCloseEnoughDist() == closeEnough
                        && Float.compare(walkTarget.getSpeedModifier(), speedModifier) == 0)
                .orElse(false);
    }

    public static void tickPathDoors(ServerLevel level, Villager villager) {
        Path path = villager.getNavigation().getPath();
        Node previousNode = path != null && !path.isDone() ? path.getPreviousNode() : null;
        Node nextNode = path != null && !path.isDone() ? path.getNextNode() : null;
        if (previousNode != null || nextNode != null) {
            openDoorAtPathNode(level, villager, previousNode);
            openDoorAtPathNode(level, villager, nextNode);
        }
        closeRememberedDoors(level, villager, previousNode, nextNode);
    }

    public static void tickPathLadders(ServerLevel level, Villager villager) {
        if (settleRecentLadderDismount(level, villager)) {
            return;
        }
        BlockPos target = villager.getNavigation().isDone() ? null : villager.getNavigation().getTargetPos();
        if (continueActiveLadderClimb(level, villager, target, 0.45D)) {
            return;
        }
        if (target == null || !needsLadderRoute(villager, target)) {
            return;
        }
        if (shouldAvoidRecentLadderDismount(level, villager, target)) {
            return;
        }
        Path path = villager.getNavigation().getPath();
        Node previousNode = path != null && !path.isDone() ? path.getPreviousNode() : null;
        Node nextNode = path != null && !path.isDone() ? path.getNextNode() : null;
        if (isStandingInLadder(level, villager.blockPosition())
                || isLadderPathNode(level, previousNode)
                || isLadderPathNode(level, nextNode)) {
            BlockPos ladder = ladderTouching(level, villager.blockPosition(), target);
            if (ladder == null) {
                ladder = isLadderPathNode(level, nextNode) ? nextNode.asBlockPos() : previousNode.asBlockPos();
            }
            beginActiveLadderClimb(level, villager, ladder, target);
            continueActiveLadderClimb(level, villager, target, 0.45D);
        }
    }

    public static boolean tickHiredPathStepAssist(ServerLevel level, Villager villager) {
        if (!hasActiveHiredWalkTarget(villager)
                || !villager.horizontalCollision
                || !villager.onGround()
                || villager.isInWater()) {
            return false;
        }
        Path path = villager.getNavigation().getPath();
        BlockPos nextNode = nextUnreachedPathNode(villager, path);
        if (nextNode == null
                || !level.hasChunkAt(nextNode)
                || !level.hasChunkAt(nextNode.below())
                || !level.getBlockState(nextNode).getCollisionShape(level, nextNode).isEmpty()
                || !level.getBlockState(nextNode.above()).getCollisionShape(level, nextNode.above()).isEmpty()) {
            return false;
        }

        BlockPos currentSupport = BlockPos.containing(
                villager.getX(),
                villager.getY() - PATH_STEP_HEIGHT_EPSILON,
                villager.getZ());
        BlockPos nextSupport = nextNode.below();
        double currentTop = supportTop(level, currentSupport);
        double nextTop = supportTop(level, nextSupport);
        if (!Double.isFinite(currentTop) || !Double.isFinite(nextTop)) {
            return false;
        }
        double heightDifference = Math.abs(nextTop - currentTop);
        double maximumAssistHeight = Math.min(PATH_STEP_MAX_HEIGHT_DIFFERENCE, villager.maxUpStep());
        if (heightDifference <= PATH_STEP_HEIGHT_EPSILON || heightDifference > maximumAssistHeight) {
            return false;
        }
        villager.getJumpControl().jump();
        return true;
    }

    private static BlockPos nextUnreachedPathNode(Villager villager, Path path) {
        if (path == null || path.isDone()) {
            return null;
        }
        int nodeIndex = path.getNextNodeIndex();
        while (nodeIndex + 1 < path.getNodeCount()
                && horizontalDistanceSqr(villager, path.getNode(nodeIndex).asBlockPos())
                <= PATH_STEP_NODE_REACHED_DISTANCE_SQR) {
            nodeIndex++;
        }
        return path.getNode(nodeIndex).asBlockPos();
    }

    private static double horizontalDistanceSqr(Villager villager, BlockPos target) {
        double dx = villager.getX() - (target.getX() + 0.5D);
        double dz = villager.getZ() - (target.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private static double supportTop(ServerLevel level, BlockPos position) {
        VoxelShape shape = level.getBlockState(position).getCollisionShape(level, position);
        return shape.isEmpty() ? Double.NaN : position.getY() + shape.max(Direction.Axis.Y);
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

    private static void enableHiredWaterTraversal(Villager villager, float waterPathCost, float waterBorderPathCost) {
        boolean canFloat = villager.getNavigation() instanceof GroundPathNavigation navigation && navigation.canFloat();
        HIRED_NAVIGATION_SETTINGS.computeIfAbsent(villager.getUUID(), ignored -> HiredNavigationSettings.capture(villager, canFloat));
        applyHiredNavigationPolicy(villager, waterPathCost, waterBorderPathCost);
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

    public static boolean moveOnLadderToward(ServerLevel level, Villager villager, BlockPos target, double speed) {
        if (target == null) {
            return false;
        }
        if (shouldAvoidRecentLadderDismount(level, villager, target)) {
            return false;
        }
        BlockPos ladder = ladderTouching(level, villager.blockPosition(), target);
        if (ladder == null) {
            return false;
        }
        if (!needsLadderRoute(villager, target)) {
            return moveOffTouchedLadderToward(level, villager, ladder, target, speed);
        }
        beginActiveLadderClimb(level, villager, ladder, target);
        boolean climbingUp = target.getY() >= villager.blockPosition().getY();
        return moveOnSpecificLadderToward(level, villager, ladder, target, climbingUp, speed);
    }

    private static boolean moveOnSpecificLadderToward(
            ServerLevel level,
            Villager villager,
            BlockPos ladder,
            BlockPos target,
            boolean climbingUp,
            double speed) {
        double centerX = ladder.getX() + 0.5D;
        double centerZ = ladder.getZ() + 0.5D;
        double dx = centerX - villager.getX();
        double dz = centerZ - villager.getZ();
        if (!isStandingInLadder(level, villager.blockPosition())) {
            double horizontalDistanceSqr = dx * dx + dz * dz;
            if (horizontalDistanceSqr <= LADDER_FORCED_ENTRY_HORIZONTAL_SQR
                    && needsLadderRoute(villager, target)) {
                forceEnterAndClimbLadder(villager, ladder, target, speed);
                return true;
            }
            villager.getMoveControl().setWantedPosition(centerX, villager.getY(), centerZ, speed);
            return true;
        }

        int verticalDelta = target.getY() - villager.blockPosition().getY();
        BlockPos columnEnd = Math.abs(verticalDelta) <= LADDER_VERTICAL_TARGET_DEADZONE
                ? ladder
                : (climbingUp ? topOfLadderColumn(level, ladder) : bottomOfLadderColumn(level, ladder));
        BlockPos dismount = safeLadderDismount(level, villager, columnEnd, target, climbingUp);
        double ladderExitY = ladderExitY(columnEnd, dismount, climbingUp);
        if (dismount != null && reachedLadderBlockEnd(villager, columnEnd, climbingUp)
                && snapToLadderDismount(level, villager, ladder, dismount)) {
            return true;
        }
        if (dismount != null && reachedLadderExitHeight(villager, ladderExitY, climbingUp)) {
            moveOffLadderToDismount(level, villager, columnEnd, dismount, climbingUp, speed);
            return true;
        }
        if (dismount == null && reachedLadderColumnEnd(villager, columnEnd, climbingUp)) {
            clearActiveLadderClimb(villager);
            villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
            villager.getMoveControl().setWantedPosition(columnEnd.getX() + 0.5D, villager.getY(), columnEnd.getZ() + 0.5D, speed);
            return false;
        }

        double yDelta = ladderExitY - villager.getY();
        double climb = yDelta >= 0.0D ? LADDER_CLIMB_SPEED : LADDER_DESCEND_SPEED;
        villager.setNoGravity(true);
        villager.move(MoverType.SELF, new Vec3(0.0D, climb, 0.0D));
        villager.setDeltaMovement(Vec3.ZERO);
        villager.setOnGround(false);
        return true;
    }

    private static double ladderExitY(BlockPos columnEnd, BlockPos dismount, boolean climbingUp) {
        if (dismount != null) {
            return dismount.getY();
        }
        return climbingUp
                ? columnEnd.getY() + LADDER_TOP_DISMOUNT_Y_OFFSET
                : columnEnd.getY() + LADDER_BOTTOM_DISMOUNT_Y_OFFSET;
    }

    private static boolean reachedLadderExitHeight(Villager villager, double ladderExitY, boolean climbingUp) {
        return climbingUp
                ? villager.getY() >= ladderExitY - 0.05D
                : villager.getY() <= ladderExitY + 0.15D;
    }

    private static void moveOffLadderToDismount(
            ServerLevel level,
            Villager villager,
            BlockPos ladder,
            BlockPos dismount,
            boolean climbingUp,
            double speed) {
        double targetX = dismount.getX() + 0.5D;
        double targetZ = dismount.getZ() + 0.5D;
        double dx = targetX - villager.getX();
        double dz = targetZ - villager.getZ();
        boolean stillOnLadder = isStandingInLadder(level, villager.blockPosition());
        villager.getMoveControl().setWantedPosition(targetX, dismount.getY(), targetZ, speed);
        if (!stillOnLadder && villager.distanceToSqr(dismount.getCenter()) <= LADDER_DISMOUNT_REACHED_DISTANCE_SQR) {
            snapToLadderDismount(level, villager, ladder, dismount);
            return;
        }

        Vec3 motion = villager.getDeltaMovement();
        if (!climbingUp && stillOnLadder) {
            if (villager.getY() <= ladder.getY() + 0.35D
                    && snapToLadderDismount(level, villager, ladder, dismount)) {
                return;
            }
            double ladderCenterX = ladder.getX() + 0.5D;
            double ladderCenterZ = ladder.getZ() + 0.5D;
            double ladderDx = ladderCenterX - villager.getX();
            double ladderDz = ladderCenterZ - villager.getZ();
            villager.getMoveControl().setWantedPosition(ladderCenterX, ladder.getY(), ladderCenterZ, speed);
            villager.setNoGravity(true);
            villager.setDeltaMovement(
                    Math.clamp(ladderDx * 0.24D + motion.x * 0.15D, -LADDER_HORIZONTAL_SPEED_LIMIT, LADDER_HORIZONTAL_SPEED_LIMIT),
                    Math.clamp((ladder.getY() - villager.getY()) * 0.2D, -0.08D, -0.02D),
                    Math.clamp(ladderDz * 0.24D + motion.z * 0.15D, -LADDER_HORIZONTAL_SPEED_LIMIT, LADDER_HORIZONTAL_SPEED_LIMIT));
            villager.setOnGround(false);
            return;
        }

        double vertical = motion.y;
        if (stillOnLadder) {
            vertical = climbingUp
                    ? Math.max(motion.y, LADDER_DISMOUNT_UPWARD_SPEED)
                    : Math.clamp((dismount.getY() - villager.getY()) * 0.2D, -0.02D, 0.04D);
        }
        villager.setNoGravity(stillOnLadder);
        villager.setDeltaMovement(
                Math.clamp(dx * 0.28D + motion.x * 0.2D, -LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT, LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT),
                vertical,
                Math.clamp(dz * 0.28D + motion.z * 0.2D, -LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT, LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT));
        villager.setOnGround(false);
    }

    private static boolean snapToLadderDismount(ServerLevel level, Villager villager, BlockPos ladder, BlockPos dismount) {
        double targetX = dismount.getX() + 0.5D;
        double targetY = dismount.getY();
        double targetZ = dismount.getZ() + 0.5D;
        if (!isWalkable(level, dismount)) {
            return false;
        }
        rememberRecentLadderDismount(level, villager, ladder, dismount);
        clearActiveLadderClimb(villager);
        stopHiredNavigation(villager);
        villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
        villager.moveTo(targetX, targetY, targetZ, villager.getYRot(), villager.getXRot());
        villager.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 0.0D);
        villager.getBrain().setMemoryWithExpiry(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(dismount), 0.0F, 0),
                RECENT_LADDER_DISMOUNT_TICKS);
        villager.setOnGround(true);
        return true;
    }

    private static void rememberRecentLadderDismount(ServerLevel level, Villager villager, BlockPos ladder, BlockPos dismount) {
        RECENT_LADDER_DISMOUNTS.put(villager.getUUID(), new RecentLadderDismount(
                bottomOfLadderColumn(level, ladder).immutable(),
                topOfLadderColumn(level, ladder).immutable(),
                dismount.immutable(),
                level.getGameTime() + RECENT_LADDER_DISMOUNT_TICKS,
                0));
    }

    private static boolean settleRecentLadderDismount(ServerLevel level, Villager villager) {
        UUID villagerId = villager.getUUID();
        RecentLadderDismount recent = RECENT_LADDER_DISMOUNTS.get(villagerId);
        if (recent == null) {
            return false;
        }
        if (recent.expiresGameTime() <= level.getGameTime()
                || recent.settleChecks() >= RECENT_LADDER_SETTLE_CHECKS
                || villager.distanceToSqr(recent.dismount().getCenter()) > LADDER_DISMOUNT_REACHED_DISTANCE_SQR) {
            RECENT_LADDER_DISMOUNTS.remove(villagerId);
            return false;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (isHiredWalkTarget(villager)
                && navigationTarget != null
                && !navigationTarget.equals(recent.dismount())) {
            RECENT_LADDER_DISMOUNTS.remove(villagerId);
            return false;
        }

        stopHiredNavigation(villager);
        villager.setNoGravity(false);
        villager.setDeltaMovement(Vec3.ZERO);
        villager.moveTo(
                recent.dismount().getX() + 0.5D,
                recent.dismount().getY(),
                recent.dismount().getZ() + 0.5D,
                villager.getYRot(),
                villager.getXRot());
        villager.getMoveControl().setWantedPosition(
                recent.dismount().getX() + 0.5D,
                recent.dismount().getY(),
                recent.dismount().getZ() + 0.5D,
                0.0D);
        villager.getBrain().setMemoryWithExpiry(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(recent.dismount()), 0.0F, 0),
                RECENT_LADDER_DISMOUNT_TICKS);
        RECENT_LADDER_DISMOUNTS.put(villagerId, new RecentLadderDismount(
                recent.bottom(),
                recent.top(),
                recent.dismount(),
                recent.expiresGameTime(),
                recent.settleChecks() + 1));
        return true;
    }

    private static boolean shouldAvoidRecentLadderDismount(ServerLevel level, Villager villager) {
        return shouldAvoidRecentLadderDismount(level, villager, null);
    }

    private static boolean shouldAvoidRecentLadderDismount(ServerLevel level, Villager villager, BlockPos target) {
        RecentLadderDismount recent = RECENT_LADDER_DISMOUNTS.get(villager.getUUID());
        if (recent == null) {
            return false;
        }
        if (recent.expiresGameTime() <= level.getGameTime()) {
            RECENT_LADDER_DISMOUNTS.remove(villager.getUUID());
            return false;
        }
        if (isStandingInLadder(level, villager.blockPosition())) {
            return false;
        }
        if (villager.distanceToSqr(recent.dismount().getCenter()) > RECENT_LADDER_DISMOUNT_DISTANCE_SQR) {
            RECENT_LADDER_DISMOUNTS.remove(villager.getUUID());
            return false;
        }
        if (shouldReenterRecentLadderColumn(recent, target)) {
            RECENT_LADDER_DISMOUNTS.remove(villager.getUUID());
            return false;
        }
        return true;
    }

    private static boolean shouldReenterRecentLadderColumn(RecentLadderDismount recent, BlockPos target) {
        if (target == null) {
            return false;
        }
        int verticalDelta = target.getY() - recent.dismount().getY();
        if (Math.abs(verticalDelta) <= LADDER_VERTICAL_TARGET_DEADZONE) {
            return false;
        }
        return verticalDelta < 0
                ? recent.bottom().getY() < recent.dismount().getY()
                : recent.top().getY() > recent.dismount().getY();
    }

    public static boolean moveTowardNearbyLadderThenClimb(ServerLevel level, Villager villager, BlockPos target, double speed) {
        if (target == null) {
            return false;
        }
        if (continueActiveLadderClimb(level, villager, target, speed)) {
            return true;
        }
        if (shouldAvoidRecentLadderDismount(level, villager, target)) {
            return false;
        }
        BlockPos touchingLadder = ladderTouching(level, villager.blockPosition(), target);
        if (touchingLadder != null) {
            if (!needsLadderRoute(villager, target)) {
                return moveOffTouchedLadderToward(level, villager, touchingLadder, target, speed);
            }
            return forceMoveOnLadderToward(level, villager, target, speed);
        }
        if (!needsLadderRoute(villager, target)) {
            return moveDirectlyToNearbyTarget(villager, target, speed);
        }
        VillagerLadderRoutePlanner.Route route = nearestLadderRoute(level, villager, target);
        if (route == null) {
            return false;
        }
        BlockPos ladder = route.entryRung();
        BlockPos approach = route.approach();
        if (tryEnterLadderBlock(level, villager, ladder, target, speed)) {
            return true;
        }
        if (route.directApproach() && villager.distanceToSqr(approach.getCenter()) <= 16.0D) {
            steerDirectlyToLadderApproach(villager, approach, speed);
            return true;
        }
        if (!villager.getNavigation().isDone()
                && approach.equals(villager.getNavigation().getTargetPos())) {
            return true;
        }
        Path path = villager.getNavigation().createPath(approach, 0);
        if (path != null && path.canReach()) {
            return moveToHiredPath(villager, path, approach, speed, 0);
        }
        return false;
    }

    public static boolean continueLadderRoute(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            double speed) {
        LadderSearch search = activeLadderSearch(level, villager, target);
        if (search == null) {
            return false;
        }
        double currentDistanceSqr = villager.distanceToSqr(search.route().approach().getCenter());
        boolean madeProgress = currentDistanceSqr
                < search.approachDistanceSqr() - LADDER_ROUTE_PROGRESS_EPSILON_SQR;
        int progressChecks = madeProgress ? 0 : search.progressChecks() + 1;
        VillagerLadderRoutePlanner.Route route = search.route();
        if (!route.directApproach()
                && progressChecks >= LADDER_ROUTE_STALLED_CHECKS
                && currentDistanceSqr <= 16.0D
                && VillagerLadderRoutePlanner.hasClearDirectApproach(level, villager, route.approach())) {
            route = route.withDirectApproach();
            progressChecks = 0;
        }
        LadderSearch updated = new LadderSearch(
                search.origin(),
                route,
                search.targetPos(),
                search.expiresGameTime(),
                madeProgress ? currentDistanceSqr : search.approachDistanceSqr(),
                search.stalledAttempts(),
                progressChecks);
        LADDER_SEARCHES.put(villager.getUUID(), updated);
        if (tryEnterLadderBlock(level, villager, route.entryRung(), target, speed)) {
            return true;
        }
        if (route.directApproach()) {
            if (villager.distanceToSqr(route.approach().getCenter()) > 16.0D) {
                return false;
            }
            steerDirectlyToLadderApproach(villager, route.approach(), speed);
            return true;
        }
        return !villager.getNavigation().isDone()
                && route.approach().equals(villager.getNavigation().getTargetPos());
    }

    private static LadderSearch activeLadderSearch(ServerLevel level, Villager villager, BlockPos target) {
        if (level == null || villager == null || target == null) {
            return null;
        }
        LadderSearch search = LADDER_SEARCHES.get(villager.getUUID());
        if (search == null
                || search.expiresGameTime() <= level.getGameTime()
                || search.targetPos() != target.asLong()
                || search.route() == null
                || !VillagerLadderRoutePlanner.remainsUsable(level, villager, target, search.route())) {
            return null;
        }
        return search;
    }

    private static void steerDirectlyToLadderApproach(Villager villager, BlockPos approach, double speed) {
        villager.getNavigation().stop();
        villager.getMoveControl().setWantedPosition(
                approach.getX() + 0.5D,
                approach.getY(),
                approach.getZ() + 0.5D,
                speed);
        setHiredWalkTarget(villager, approach, speed, 0);
        double dx = approach.getX() + 0.5D - villager.getX();
        double dz = approach.getZ() + 0.5D - villager.getZ();
        Vec3 motion = villager.getDeltaMovement();
        villager.setDeltaMovement(
                Math.clamp(dx * 0.16D + motion.x * 0.15D,
                        -LADDER_DIRECT_APPROACH_SPEED_LIMIT,
                        LADDER_DIRECT_APPROACH_SPEED_LIMIT),
                motion.y,
                Math.clamp(dz * 0.16D + motion.z * 0.15D,
                        -LADDER_DIRECT_APPROACH_SPEED_LIMIT,
                        LADDER_DIRECT_APPROACH_SPEED_LIMIT));
    }

    public static String ladderRouteDebug(ServerLevel level, Villager villager, BlockPos target) {
        VillagerLadderRoutePlanner.Route route = VillagerLadderRoutePlanner.findBest(
                level,
                villager,
                target,
                LADDER_ESCAPE_SEARCH_RADIUS,
                LADDER_ESCAPE_VERTICAL_RADIUS);
        if (route == null) {
            return "none";
        }
        return "rung=" + route.entryRung()
                + ", approach=" + route.approach()
                + ", bottom=" + route.bottom()
                + ", top=" + route.top()
                + ", dismount=" + route.dismount()
                + ", up=" + route.climbingUp()
                + ", direct=" + route.directApproach()
                + ", score=" + route.score();
    }

    public static boolean moveTowardHighestSafePositionInLoadedChunk(ServerLevel level, Villager villager, BlockPos target, double speed) {
        if (target == null) {
            return false;
        }
        if (continueActiveLadderClimb(level, villager, target, speed)) {
            return true;
        }
        if (shouldAvoidRecentLadderDismount(level, villager, target)) {
            return false;
        }
        UUID villagerId = villager.getUUID();
        BlockPos origin = villager.blockPosition();
        SurfaceEscapeSearch cached = SURFACE_ESCAPE_SEARCHES.get(villagerId);
        if (cached != null
                && cached.expiresGameTime() > level.getGameTime()
                && cached.targetPos() == target.asLong()
                && cached.origin().distSqr(origin) <= LADDER_SEARCH_ORIGIN_REUSE_DISTANCE_SQR) {
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
        if (moveOnLadderToward(level, villager, target, speed)) {
            return true;
        }
        Path path = villager.getNavigation().createPath(target, 0);
        if (path != null && path.canReach() && moveToHiredPath(villager, path, target, speed, 0)) {
            return true;
        }
        if (moveTowardNearbyLadderThenClimb(level, villager, target, speed)) {
            return true;
        }
        if (villager.distanceToSqr(target.getCenter()) <= 16.0D) {
            villager.getMoveControl().setWantedPosition(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
            return true;
        }
        return false;
    }

    private static boolean moveDirectlyToNearbyTarget(Villager villager, BlockPos target, double speed) {
        Path path = villager.getNavigation().createPath(target, 0);
        if (path != null && path.canReach() && moveToHiredPath(villager, path, target, speed, 0)) {
            return true;
        }
        if (villager.distanceToSqr(target.getCenter()) > 16.0D) {
            return false;
        }
        villager.getMoveControl().setWantedPosition(
                target.getX() + 0.5D,
                target.getY(),
                target.getZ() + 0.5D,
                speed);
        return true;
    }

    private static void rememberSurfaceEscapeSearch(ServerLevel level, Villager villager, BlockPos target, BlockPos escapeTarget) {
        SURFACE_ESCAPE_SEARCHES.put(villager.getUUID(), new SurfaceEscapeSearch(
                villager.blockPosition().immutable(),
                target.asLong(),
                escapeTarget == null ? null : escapeTarget.immutable(),
                level.getGameTime() + SURFACE_ESCAPE_SEARCH_CACHE_TICKS));
    }

    private static boolean forceMoveOnLadderToward(ServerLevel level, Villager villager, BlockPos target, double speed) {
        BlockPos ladder = ladderTouching(level, villager.blockPosition(), target);
        if (ladder == null) {
            return false;
        }
        if (!needsLadderRoute(villager, target)) {
            return moveOffTouchedLadderToward(level, villager, ladder, target, speed);
        }
        stopNavigationPath(villager);
        beginActiveLadderClimb(level, villager, ladder, target);
        ActiveLadderClimb climb = ACTIVE_LADDER_CLIMBS.get(villager.getUUID());
        boolean climbingUp = climb == null ? target.getY() >= villager.blockPosition().getY() : climb.climbingUp();
        return moveOnSpecificLadderToward(level, villager, ladder, target, climbingUp, speed);
    }

    private static boolean moveOffTouchedLadderToward(
            ServerLevel level,
            Villager villager,
            BlockPos ladder,
            BlockPos target,
            double speed) {
        boolean climbingUp = target.getY() >= villager.blockPosition().getY();
        BlockPos dismount = safeLadderDismount(level, villager, ladder, target, climbingUp);
        if (dismount == null) {
            return false;
        }
        if (villager.distanceToSqr(dismount.getCenter()) <= LADDER_DISMOUNT_REACHED_DISTANCE_SQR
                && snapToLadderDismount(level, villager, ladder, dismount)) {
            return true;
        }
        moveOffLadderToDismount(level, villager, ladder, dismount, climbingUp, speed);
        return true;
    }

    private static boolean tryEnterLadderBlock(ServerLevel level, Villager villager, BlockPos ladder, BlockPos target, double speed) {
        if (!needsLadderRoute(villager, target)
                || isStandingInLadder(level, villager.blockPosition())
                || Math.abs(villager.getY() - ladder.getY()) > 2.0D
                || horizontalDistanceToBlockCenterSqr(villager, ladder) > LADDER_ENTRY_DISTANCE_SQR
                || !isWalkable(level, ladder)
                || !isUsefulLadderRoute(level, villager, ladder, target)) {
            return false;
        }
        stopNavigationPath(villager);
        beginActiveLadderClimb(level, villager, ladder, target);
        forceEnterAndClimbLadder(villager, ladder, target, speed);
        return true;
    }

    private static void forceEnterAndClimbLadder(Villager villager, BlockPos ladder, BlockPos target, double speed) {
        double centerX = ladder.getX() + 0.5D;
        double centerZ = ladder.getZ() + 0.5D;
        double dx = centerX - villager.getX();
        double dz = centerZ - villager.getZ();
        int verticalDelta = target.getY() - villager.blockPosition().getY();
        double climb = verticalDelta >= 0 ? LADDER_CLIMB_SPEED : LADDER_DESCEND_SPEED;
        double targetY = verticalDelta >= 0 ? Math.max(villager.getY() + 0.25D, ladder.getY() + 0.05D) : ladder.getY();
        villager.getMoveControl().setWantedPosition(centerX, targetY, centerZ, speed);

        if (dx * dx + dz * dz <= LADDER_FORCED_ENTRY_HORIZONTAL_SQR) {
            // Snap into the validated ladder block before the vanilla move
            // controller can keep treating its thin collision face as a path
            // endpoint. This transition is limited to the adjacent block.
            villager.moveTo(centerX, villager.getY(), centerZ, villager.getYRot(), villager.getXRot());
            villager.setDeltaMovement(0.0D, climb, 0.0D);
            villager.setNoGravity(true);
            villager.setOnGround(false);
        }
    }

    private static boolean continueActiveLadderClimb(ServerLevel level, Villager villager, BlockPos target, double speed) {
        ActiveLadderClimb climb = ACTIVE_LADDER_CLIMBS.get(villager.getUUID());
        if (climb == null) {
            return false;
        }
        if (climb.expiresGameTime() <= level.getGameTime()
                || (target != null && climb.targetPos() != target.asLong())) {
            clearActiveLadderClimb(villager);
            return false;
        }
        BlockPos routeTarget = target == null ? BlockPos.of(climb.targetPos()) : target;
        if (!needsLadderRoute(villager, routeTarget)) {
            BlockPos touchingLadder = ladderTouching(level, villager.blockPosition(), routeTarget);
            if (touchingLadder == null) {
                clearActiveLadderClimb(villager);
                return false;
            }
            ACTIVE_LADDER_CLIMBS.put(villager.getUUID(), climb.refreshed(level.getGameTime() + ACTIVE_LADDER_CLIMB_TICKS));
            return moveOnSpecificLadderToward(level, villager, touchingLadder, routeTarget, climb.climbingUp(), speed);
        }
        BlockPos ladder = activeLadderBlock(level, villager, climb);
        if (ladder == null) {
            clearActiveLadderClimb(villager);
            return false;
        }
        stopNavigationPath(villager);
        ACTIVE_LADDER_CLIMBS.put(villager.getUUID(), climb.refreshed(level.getGameTime() + ACTIVE_LADDER_CLIMB_TICKS));
        return moveOnSpecificLadderToward(level, villager, ladder, routeTarget, climb.climbingUp(), speed);
    }

    private static void beginActiveLadderClimb(ServerLevel level, Villager villager, BlockPos ladder, BlockPos target) {
        if (ladder == null || target == null || !isLoadedLadder(level, ladder) || !needsLadderRoute(villager, target)) {
            return;
        }
        BlockPos bottom = bottomOfLadderColumn(level, ladder);
        BlockPos top = topOfLadderColumn(level, ladder);
        ActiveLadderClimb existing = ACTIVE_LADDER_CLIMBS.get(villager.getUUID());
        if (existing != null
                && existing.targetPos() == target.asLong()
                && existing.bottom().equals(bottom)
                && existing.top().equals(top)) {
            ACTIVE_LADDER_CLIMBS.put(villager.getUUID(), existing.refreshed(level.getGameTime() + ACTIVE_LADDER_CLIMB_TICKS));
            return;
        }
        boolean climbingUp = target.getY() >= villager.blockPosition().getY();
        ACTIVE_LADDER_CLIMBS.put(villager.getUUID(), new ActiveLadderClimb(
                bottom.immutable(),
                top.immutable(),
                target.asLong(),
                climbingUp,
                level.getGameTime() + ACTIVE_LADDER_CLIMB_TICKS));
    }

    private static boolean needsLadderRoute(Villager villager, BlockPos target) {
        return target != null && Math.abs(target.getY() - villager.blockPosition().getY()) > LADDER_VERTICAL_TARGET_DEADZONE;
    }

    private static BlockPos activeLadderBlock(ServerLevel level, Villager villager, ActiveLadderClimb climb) {
        BlockPos origin = villager.blockPosition();
        double centerX = climb.bottom().getX() + 0.5D;
        double centerZ = climb.bottom().getZ() + 0.5D;
        double dx = centerX - villager.getX();
        double dz = centerZ - villager.getZ();
        if (dx * dx + dz * dz > ACTIVE_LADDER_COLUMN_DISTANCE_SQR) {
            return null;
        }
        int minY = Math.min(climb.bottom().getY(), climb.top().getY());
        int maxY = Math.max(climb.bottom().getY(), climb.top().getY());
        int y = Math.clamp(origin.getY(), minY, maxY);
        for (int offset = 0; offset <= 2; offset++) {
            BlockPos above = new BlockPos(climb.bottom().getX(), Math.min(maxY, y + offset), climb.bottom().getZ());
            if (isLoadedLadder(level, above)) {
                return above;
            }
            BlockPos below = new BlockPos(climb.bottom().getX(), Math.max(minY, y - offset), climb.bottom().getZ());
            if (isLoadedLadder(level, below)) {
                return below;
            }
        }
        return null;
    }

    private static void clearActiveLadderClimb(Villager villager) {
        ACTIVE_LADDER_CLIMBS.remove(villager.getUUID());
        if (villager.isNoGravity()) {
            villager.setNoGravity(false);
        }
    }

    private static void stopNavigationPath(Villager villager) {
        stopHiredNavigation(villager);
    }

    public static void clearRuntimeState() {
        DOORS_TO_CLOSE.clear();
        ACTIVE_LADDER_CLIMBS.clear();
        RECENT_LADDER_DISMOUNTS.clear();
        LADDER_SEARCHES.clear();
        SURFACE_ESCAPE_SEARCHES.clear();
        HIRED_NAVIGATION_SETTINGS.clear();
        WATER_MOVEMENT_PROGRESS.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        DOORS_TO_CLOSE.remove(villager.getUUID());
        clearActiveLadderClimb(villager);
        RECENT_LADDER_DISMOUNTS.remove(villager.getUUID());
        LADDER_SEARCHES.remove(villager.getUUID());
        SURFACE_ESCAPE_SEARCHES.remove(villager.getUUID());
        HIRED_NAVIGATION_SETTINGS.remove(villager.getUUID());
        WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
        clearHiredWalkTarget(villager);
    }

    private static void openDoorAtPathNode(ServerLevel level, Villager villager, Node node) {
        if (node == null) {
            return;
        }
        BlockPos pos = node.asBlockPos();
        if (!canReachDoor(level, villager, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (isDoorOrGate(state) && setOpen(villager, level, state, pos, true)) {
            DOORS_TO_CLOSE.computeIfAbsent(villager.getUUID(), ignored -> new HashSet<>())
                    .add(GlobalPos.of(level.dimension(), pos));
        }
    }

    private static void closeRememberedDoors(ServerLevel level, Villager villager, Node previousNode, Node nextNode) {
        Set<GlobalPos> doors = DOORS_TO_CLOSE.get(villager.getUUID());
        if (doors == null || doors.isEmpty()) {
            return;
        }
        Iterator<GlobalPos> iterator = doors.iterator();
        while (iterator.hasNext()) {
            GlobalPos door = iterator.next();
            BlockPos pos = door.pos();
            if (!door.dimension().equals(level.dimension())) {
                iterator.remove();
                continue;
            }
            if (isCurrentPathNode(pos, previousNode) || isCurrentPathNode(pos, nextNode)) {
                continue;
            }
            if (!canReachDoor(level, villager, pos)) {
                iterator.remove();
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!isDoorOrGate(state) || !isOpen(state)) {
                iterator.remove();
                continue;
            }
            if (anotherVillagerIsUsingDoor(level, villager, pos)) {
                continue;
            }
            setOpen(villager, level, state, pos, false);
            iterator.remove();
        }
        if (doors.isEmpty()) {
            DOORS_TO_CLOSE.remove(villager.getUUID());
        }
    }

    private static BlockPos ladderTouching(ServerLevel level, BlockPos pos) {
        return ladderTouching(level, pos, null);
    }

    private static boolean isStandingInLadder(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER);
    }

    private static boolean isLadderPathNode(ServerLevel level, Node node) {
        if (node == null) {
            return false;
        }
        BlockPos pos = node.asBlockPos();
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER);
    }

    private static BlockPos ladderTouching(ServerLevel level, BlockPos pos, BlockPos target) {
        if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER)) {
            return pos;
        }
        BlockPos below = pos.below();
        if (target != null
                && target.getY() < pos.getY()
                && level.hasChunkAt(below)
                && level.getBlockState(below).is(Blocks.LADDER)) {
            return below;
        }
        return null;
    }

    private static VillagerLadderRoutePlanner.Route nearestLadderRoute(
            ServerLevel level,
            Villager villager,
            BlockPos target) {
        UUID villagerId = villager.getUUID();
        BlockPos origin = villager.blockPosition();
        LadderSearch cached = LADDER_SEARCHES.get(villagerId);
        if (cached != null
                && cached.expiresGameTime() > level.getGameTime()
                && cached.targetPos() == target.asLong()
                && cached.origin().distSqr(origin) <= LADDER_SEARCH_ORIGIN_REUSE_DISTANCE_SQR
                && (cached.route() == null
                || VillagerLadderRoutePlanner.remainsUsable(level, villager, target, cached.route()))) {
            return cached.route();
        }
        boolean failedToApproach = cached != null
                && cached.targetPos() == target.asLong()
                && cached.route() != null
                && VillagerLadderRoutePlanner.remainsUsable(level, villager, target, cached.route())
                && villager.distanceToSqr(cached.route().approach().getCenter())
                >= cached.approachDistanceSqr() - LADDER_ROUTE_PROGRESS_EPSILON_SQR;
        int stalledAttempts = failedToApproach ? cached.stalledAttempts() + 1 : 0;
        VillagerLadderRoutePlanner.Route best = VillagerLadderRoutePlanner.findBest(
                level,
                villager,
                target,
                LADDER_ESCAPE_SEARCH_RADIUS,
                LADDER_ESCAPE_VERTICAL_RADIUS);
        if (best != null
                && stalledAttempts > 0
                && horizontalDistanceSqr(best.approach(), origin) <= 16.0D
                && VillagerLadderRoutePlanner.hasClearDirectApproach(level, villager, best.approach())) {
            best = best.withDirectApproach();
        }
        LADDER_SEARCHES.put(villagerId, new LadderSearch(
                origin.immutable(),
                best,
                target.asLong(),
                level.getGameTime() + LADDER_SEARCH_CACHE_TICKS,
                best == null ? Double.POSITIVE_INFINITY : villager.distanceToSqr(best.approach().getCenter()),
                stalledAttempts,
                0));
        return best;
    }

    private static boolean isLoadedLadder(ServerLevel level, BlockPos pos) {
        return pos != null && level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER);
    }

    private static boolean reachedLadderColumnEnd(Villager villager, BlockPos columnEnd, boolean climbingUp) {
        return climbingUp
                ? villager.getY() >= columnEnd.getY() + LADDER_TOP_DISMOUNT_Y_OFFSET
                : villager.getY() <= columnEnd.getY() + LADDER_BOTTOM_DISMOUNT_Y_OFFSET;
    }

    private static boolean reachedLadderBlockEnd(Villager villager, BlockPos columnEnd, boolean climbingUp) {
        return climbingUp
                ? villager.getY() + villager.getBbHeight() >= columnEnd.getY() + LADDER_TOP_HEAD_DISMOUNT_OFFSET
                : villager.getY() <= columnEnd.getY() + 0.15D;
    }

    private static boolean isUsefulLadderRoute(ServerLevel level, Villager villager, BlockPos ladder, BlockPos target) {
        return ladderRouteScore(level, villager, ladder, target) < Double.MAX_VALUE;
    }

    private static double ladderRouteScore(ServerLevel level, Villager villager, BlockPos ladder, BlockPos target) {
        if (target == null || !isLoadedLadder(level, ladder)) {
            return Double.MAX_VALUE;
        }
        BlockPos origin = villager.blockPosition();
        int verticalDelta = target.getY() - origin.getY();
        if (Math.abs(verticalDelta) <= LADDER_VERTICAL_TARGET_DEADZONE) {
            return Double.MAX_VALUE;
        }
        boolean climbingUp = verticalDelta > 0;
        if (climbingUp && ladder.getY() < origin.getY()) {
            return Double.MAX_VALUE;
        }
        if (!climbingUp && ladder.getY() > origin.getY()) {
            return Double.MAX_VALUE;
        }
        BlockPos columnEnd = climbingUp ? topOfLadderColumn(level, ladder) : bottomOfLadderColumn(level, ladder);
        if (climbingUp ? columnEnd.getY() <= origin.getY() : columnEnd.getY() >= origin.getY()) {
            return Double.MAX_VALUE;
        }
        int currentVerticalMiss = Math.abs(target.getY() - origin.getY());
        int ladderVerticalMiss = Math.abs(target.getY() - columnEnd.getY());
        if (ladderVerticalMiss >= currentVerticalMiss) {
            return Double.MAX_VALUE;
        }
        BlockPos dismount = safeLadderDismount(level, villager, columnEnd, target, climbingUp);
        if (dismount == null) {
            return Double.MAX_VALUE;
        }
        return villager.distanceToSqr(ladder.getCenter())
                + ladderVerticalMiss * ladderVerticalMiss * 4.0D
                + dismount.distSqr(target) * 0.5D;
    }

    private static BlockPos safeLadderDismount(
            ServerLevel level,
            Villager villager,
            BlockPos columnEnd,
            BlockPos target,
            boolean climbingUp) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = columnEnd.relative(direction);
            BlockPos[] candidates = climbingUp
                    ? new BlockPos[] { side, side.above() }
                    : new BlockPos[] { side, side.below() };
            for (BlockPos candidate : candidates) {
                if (!isWalkable(level, candidate)) {
                    continue;
                }
                double score = candidate.distSqr(target)
                        + villager.distanceToSqr(candidate.getCenter()) * 0.2D
                        + Math.abs(candidate.getY() - columnEnd.getY()) * 4.0D;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate.immutable();
                }
            }
        }
        return best;
    }

    private static BlockPos topOfLadderColumn(ServerLevel level, BlockPos ladder) {
        BlockPos top = ladder;
        while (level.hasChunkAt(top.above()) && level.getBlockState(top.above()).is(Blocks.LADDER)) {
            top = top.above();
        }
        return top;
    }

    private static BlockPos bottomOfLadderColumn(ServerLevel level, BlockPos ladder) {
        BlockPos bottom = ladder;
        while (level.hasChunkAt(bottom.below()) && level.getBlockState(bottom.below()).is(Blocks.LADDER)) {
            bottom = bottom.below();
        }
        return bottom;
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

    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static double horizontalDistanceToBlockCenterSqr(Entity entity, BlockPos pos) {
        double dx = entity.getX() - (pos.getX() + 0.5D);
        double dz = entity.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private static boolean isWalkable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return (feet.isAir() || feet.liquid() || feet.is(Blocks.LADDER))
                && (head.isAir() || head.liquid() || head.is(Blocks.LADDER))
                && (floor.isSolid() || feet.is(Blocks.LADDER));
    }

    private static boolean isCurrentPathNode(BlockPos pos, Node node) {
        return node != null && pos.equals(node.asBlockPos());
    }

    private static boolean canReachDoor(ServerLevel level, Villager villager, BlockPos pos) {
        return level.hasChunkAt(pos) && villager.distanceToSqr(pos.getCenter()) <= DOOR_REACH_DISTANCE_SQR;
    }

    private static boolean anotherVillagerIsUsingDoor(ServerLevel level, Villager villager, BlockPos pos) {
        AABB area = new AABB(pos).inflate(1.25D);
        return !level.getEntitiesOfClass(Villager.class, area, other -> other != villager && other.isAlive()).isEmpty();
    }

    private static boolean isDoorOrGate(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN)
                && (state.is(BlockTags.WOODEN_DOORS) || state.getBlock() instanceof FenceGateBlock);
    }

    private static boolean isOpen(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }

    private static boolean setOpen(Entity entity, ServerLevel level, BlockState state, BlockPos pos, boolean open) {
        if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN) == open) {
            return false;
        }
        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        level.playSound(
                null,
                pos,
                openCloseSound(state, open),
                SoundSource.BLOCKS,
                0.75F,
                0.9F + level.getRandom().nextFloat() * 0.1F);
        return true;
    }

    private static net.minecraft.sounds.SoundEvent openCloseSound(BlockState state, boolean open) {
        if (state.getBlock() instanceof FenceGateBlock) {
            return open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE;
        }
        return open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE;
    }

    private record ActiveLadderClimb(BlockPos bottom, BlockPos top, long targetPos, boolean climbingUp, long expiresGameTime) {
        private ActiveLadderClimb refreshed(long expiresGameTime) {
            return new ActiveLadderClimb(this.bottom, this.top, this.targetPos, this.climbingUp, expiresGameTime);
        }
    }

    private record RecentLadderDismount(
            BlockPos bottom,
            BlockPos top,
            BlockPos dismount,
            long expiresGameTime,
            int settleChecks) {
    }

    private record LadderSearch(
            BlockPos origin,
            VillagerLadderRoutePlanner.Route route,
            long targetPos,
            long expiresGameTime,
            double approachDistanceSqr,
            int stalledAttempts,
            int progressChecks) {
    }

    private record SurfaceEscapeTarget(BlockPos pos, double score) {
    }

    private record SurfaceEscapeSearch(BlockPos origin, long targetPos, BlockPos escapeTarget, long expiresGameTime) {
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
}
