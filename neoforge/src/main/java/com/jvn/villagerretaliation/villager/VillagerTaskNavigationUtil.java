package com.jvn.villagerretaliation.villager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VillagerTaskNavigationUtil {
    private static final double DOOR_REACH_DISTANCE = 2.25D;
    private static final double DOOR_REACH_DISTANCE_SQR = DOOR_REACH_DISTANCE * DOOR_REACH_DISTANCE;
    private static final double LADDER_CENTERING_DISTANCE_SQR = 0.36D;
    private static final double LADDER_ENTRY_DISTANCE_SQR = 2.25D;
    private static final double LADDER_HORIZONTAL_SPEED_LIMIT = 0.15D;
    private static final double LADDER_CLIMB_SPEED = 0.20D;
    private static final double LADDER_DESCEND_SPEED = -0.15D;
    private static final int LADDER_ESCAPE_SEARCH_RADIUS = 8;
    private static final int LADDER_ESCAPE_VERTICAL_RADIUS = 18;
    private static final int LADDER_SEARCH_CACHE_TICKS = 20;
    private static final double LADDER_SEARCH_ORIGIN_REUSE_DISTANCE_SQR = 16.0D;
    private static final float HIRED_WATER_PATH_COST = 32.0F;
    private static final float HIRED_WATER_BORDER_PATH_COST = 16.0F;
    private static final double WATER_TARGET_REACHED_DISTANCE_SQR = 2.25D;
    private static final double WATER_VERTICAL_SPEED_LIMIT = 0.08D;
    private static final double WATER_IDLE_FLOAT_SPEED = 0.04D;
    private static final int WATER_SURFACE_SCAN_DEPTH = 3;
    private static final int WATER_STUCK_CHECK_TICKS = 10;
    private static final int WATER_STUCK_LIMIT = 2;
    private static final long WATER_ESCAPE_TICKS = 15L;
    private static final double WATER_STUCK_MIN_PROGRESS_SQR = 0.05D;
    private static final Map<UUID, Set<GlobalPos>> DOORS_TO_CLOSE = new HashMap<>();
    private static final Map<UUID, LadderSearch> LADDER_SEARCHES = new HashMap<>();
    private static final Map<UUID, WaterPathSettings> WATER_PATH_SETTINGS = new HashMap<>();
    private static final Map<UUID, WaterMovementProgress> WATER_MOVEMENT_PROGRESS = new HashMap<>();

    private VillagerTaskNavigationUtil() {
    }

    public static void stopNavigationAndClearTargets(Villager villager) {
        if (!villager.getNavigation().isDone()) {
            villager.getNavigation().stop();
        }
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
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
        BlockPos target = villager.getNavigation().getTargetPos();
        Path path = villager.getNavigation().getPath();
        Node previousNode = path != null && !path.isDone() ? path.getPreviousNode() : null;
        Node nextNode = path != null && !path.isDone() ? path.getNextNode() : null;
        if (target != null && (isStandingInLadder(level, villager.blockPosition())
                || isLadderPathNode(level, previousNode)
                || isLadderPathNode(level, nextNode))) {
            moveOnLadderToward(level, villager, target, 0.45D);
        }
    }

    public static void tickVillagerWaterSafety(ServerLevel level, Villager villager) {
        restoreVillagerGravity(villager);
        enableWaterTraversal(villager);
        if (!villager.isInWater()) {
            WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
            return;
        }
        keepVillagerBreathing(villager);
        Path path = villager.getNavigation().getPath();
        Node nextNode = path != null && !path.isDone() ? path.getNextNode() : null;
        if (nextNode == null) {
            floatIdleInWater(level, villager);
        }
    }

    public static void enableHiredWaterTraversal(Villager villager) {
        boolean canFloat = villager.getNavigation() instanceof GroundPathNavigation navigation && navigation.canFloat();
        WATER_PATH_SETTINGS.computeIfAbsent(villager.getUUID(), ignored -> new WaterPathSettings(
                villager.getPathfindingMalus(PathType.WATER),
                villager.getPathfindingMalus(PathType.WATER_BORDER),
                canFloat));
        enableWaterTraversal(villager);
    }

    private static void enableWaterTraversal(Villager villager) {
        villager.setPathfindingMalus(PathType.WATER, HIRED_WATER_PATH_COST);
        villager.setPathfindingMalus(PathType.WATER_BORDER, HIRED_WATER_BORDER_PATH_COST);
        if (villager.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanFloat(true);
        }
    }

    public static void restoreHiredWaterTraversal(Villager villager) {
        WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
        WaterPathSettings settings = WATER_PATH_SETTINGS.remove(villager.getUUID());
        if (settings == null) {
            return;
        }
        villager.setPathfindingMalus(PathType.WATER, settings.waterMalus());
        villager.setPathfindingMalus(PathType.WATER_BORDER, settings.waterBorderMalus());
        if (villager.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanFloat(settings.canFloat());
        }
    }

    public static boolean moveInWaterTowardNavigationTarget(ServerLevel level, Villager villager, double speed) {
        if (!villager.isInWater()) {
            WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
            return false;
        }
        keepVillagerBreathing(villager);
        Path path = villager.getNavigation().getPath();
        Node nextNode = path != null && !path.isDone() ? path.getNextNode() : null;
        if (nextNode == null) {
            WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
            floatIdleInWater(level, villager);
            return false;
        }
        BlockPos target = nextNode.asBlockPos();
        if (target == null || villager.distanceToSqr(target.getCenter()) <= WATER_TARGET_REACHED_DISTANCE_SQR) {
            WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
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
        BlockPos ladder = ladderTouching(level, villager.blockPosition(), target);
        if (ladder == null) {
            return false;
        }
        double centerX = ladder.getX() + 0.5D;
        double centerZ = ladder.getZ() + 0.5D;
        double dx = centerX - villager.getX();
        double dz = centerZ - villager.getZ();
        if (!isStandingInLadder(level, villager.blockPosition()) && dx * dx + dz * dz > LADDER_CENTERING_DISTANCE_SQR) {
            villager.getMoveControl().setWantedPosition(centerX, villager.getY(), centerZ, speed);
            return true;
        }

        BlockPos columnEnd = target.getY() >= ladder.getY() ? topOfLadderColumn(level, ladder) : bottomOfLadderColumn(level, ladder);
        boolean climbingUp = target.getY() >= ladder.getY();
        if (reachedLadderColumnEnd(villager, columnEnd, climbingUp)) {
            BlockPos dismount = safeLadderDismount(level, villager, columnEnd, target, climbingUp);
            if (dismount != null) {
                villager.getMoveControl().setWantedPosition(dismount.getX() + 0.5D, dismount.getY(), dismount.getZ() + 0.5D, speed);
                return true;
            }
            villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
            villager.getMoveControl().setWantedPosition(columnEnd.getX() + 0.5D, villager.getY(), columnEnd.getZ() + 0.5D, speed);
            return true;
        }

        Vec3 motion = villager.getDeltaMovement();
        double yDelta = target.getY() + 0.1D - villager.getY();
        double climb = yDelta >= 0.0D ? LADDER_CLIMB_SPEED : LADDER_DESCEND_SPEED;
        villager.setDeltaMovement(
                Math.clamp(motion.x, -LADDER_HORIZONTAL_SPEED_LIMIT, LADDER_HORIZONTAL_SPEED_LIMIT),
                climb,
                Math.clamp(motion.z, -LADDER_HORIZONTAL_SPEED_LIMIT, LADDER_HORIZONTAL_SPEED_LIMIT));
        villager.setOnGround(false);
        return true;
    }

    public static boolean moveTowardNearbyLadderThenClimb(ServerLevel level, Villager villager, BlockPos target, double speed) {
        if (target == null) {
            return false;
        }
        BlockPos touchingLadder = ladderTouching(level, villager.blockPosition(), target);
        if (touchingLadder != null) {
            return forceMoveOnLadderToward(level, villager, target, speed);
        }
        if (Math.abs(target.getY() - villager.blockPosition().getY()) <= 2) {
            return false;
        }
        BlockPos ladder = nearestLadder(level, villager);
        if (ladder == null) {
            return false;
        }
        BlockPos approach = ladderApproach(level, villager, ladder);
        if (approach == null) {
            return false;
        }
        if (tryEnterLadderBlock(level, villager, ladder, target, speed)) {
            return true;
        }
        Path path = villager.getNavigation().createPath(approach, 0);
        if (path != null && path.canReach()) {
            return villager.getNavigation().moveTo(path, speed);
        }
        if (villager.distanceToSqr(approach.getCenter()) <= 16.0D) {
            villager.getMoveControl().setWantedPosition(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D, speed);
            return true;
        }
        return false;
    }

    private static boolean forceMoveOnLadderToward(ServerLevel level, Villager villager, BlockPos target, double speed) {
        BlockPos ladder = ladderTouching(level, villager.blockPosition(), target);
        if (ladder == null) {
            return false;
        }
        if (!villager.getNavigation().isDone()) {
            villager.getNavigation().stop();
        }
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        return moveOnLadderToward(level, villager, target, speed);
    }

    private static boolean tryEnterLadderBlock(ServerLevel level, Villager villager, BlockPos ladder, BlockPos target, double speed) {
        if (isStandingInLadder(level, villager.blockPosition())
                || Math.abs(villager.getY() - ladder.getY()) > 1.0D
                || villager.distanceToSqr(ladder.getCenter()) > LADDER_ENTRY_DISTANCE_SQR
                || !isWalkable(level, ladder)) {
            return false;
        }
        if (!villager.getNavigation().isDone()) {
            villager.getNavigation().stop();
        }
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.setPos(ladder.getX() + 0.5D, villager.getY(), ladder.getZ() + 0.5D);
        return moveOnLadderToward(level, villager, target, speed);
    }

    public static void clearRuntimeState() {
        DOORS_TO_CLOSE.clear();
        LADDER_SEARCHES.clear();
        WATER_PATH_SETTINGS.clear();
        WATER_MOVEMENT_PROGRESS.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        DOORS_TO_CLOSE.remove(villager.getUUID());
        LADDER_SEARCHES.remove(villager.getUUID());
        WATER_PATH_SETTINGS.remove(villager.getUUID());
        WATER_MOVEMENT_PROGRESS.remove(villager.getUUID());
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

    private static BlockPos nearestLadder(ServerLevel level, Villager villager) {
        UUID villagerId = villager.getUUID();
        BlockPos origin = villager.blockPosition();
        LadderSearch cached = LADDER_SEARCHES.get(villagerId);
        if (cached != null
                && cached.expiresGameTime() > level.getGameTime()
                && cached.origin().distSqr(origin) <= LADDER_SEARCH_ORIGIN_REUSE_DISTANCE_SQR
                && (cached.ladder() == null || isLoadedLadder(level, cached.ladder()))) {
            return cached.ladder();
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos rawPos : BlockPos.betweenClosed(
                origin.offset(-LADDER_ESCAPE_SEARCH_RADIUS, -LADDER_ESCAPE_VERTICAL_RADIUS, -LADDER_ESCAPE_SEARCH_RADIUS),
                origin.offset(LADDER_ESCAPE_SEARCH_RADIUS, LADDER_ESCAPE_VERTICAL_RADIUS, LADDER_ESCAPE_SEARCH_RADIUS))) {
            BlockPos pos = rawPos.immutable();
            if (!isLoadedLadder(level, pos)) {
                continue;
            }
            double distance = origin.distSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        LADDER_SEARCHES.put(villagerId, new LadderSearch(
                origin.immutable(),
                best == null ? null : best.immutable(),
                level.getGameTime() + LADDER_SEARCH_CACHE_TICKS));
        return best;
    }

    private static boolean isLoadedLadder(ServerLevel level, BlockPos pos) {
        return pos != null && level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER);
    }

    private static boolean reachedLadderColumnEnd(Villager villager, BlockPos columnEnd, boolean climbingUp) {
        return climbingUp
                ? villager.getY() >= columnEnd.getY() - 0.05D
                : villager.getY() <= columnEnd.getY() + 0.15D;
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

    private static BlockPos ladderApproach(ServerLevel level, Villager villager, BlockPos ladder) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = ladder.relative(direction);
            if (!isWalkable(level, pos)) {
                continue;
            }
            double distance = villager.distanceToSqr(pos.getCenter());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        if (best != null) {
            return best;
        }
        if (isWalkable(level, ladder)) {
            return ladder;
        }
        for (BlockPos rawPos : BlockPos.betweenClosed(ladder.offset(-1, -1, -1), ladder.offset(1, 1, 1))) {
            BlockPos pos = rawPos.immutable();
            if (!isWalkable(level, pos)) {
                continue;
            }
            double distance = villager.distanceToSqr(pos.getCenter());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
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

    private record LadderSearch(BlockPos origin, BlockPos ladder, long expiresGameTime) {
    }

    private record WaterPathSettings(float waterMalus, float waterBorderMalus, boolean canFloat) {
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
