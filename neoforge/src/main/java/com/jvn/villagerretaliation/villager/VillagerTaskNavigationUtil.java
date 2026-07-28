package com.jvn.villagerretaliation.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class VillagerTaskNavigationUtil {
    private static final double PATH_STEP_NODE_REACHED_DISTANCE_SQR = 0.64D;
    private static final double PATH_STEP_HEIGHT_EPSILON = 0.001D;
    private static final double PATH_STEP_MAX_HEIGHT_DIFFERENCE = 0.5D;
    private static final double PATH_STEP_SNAP_HEIGHT = 0.125D;
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
        if (nextTop > currentTop && heightDifference <= PATH_STEP_SNAP_HEIGHT) {
            villager.setPos(villager.getX(), villager.getY() + heightDifference, villager.getZ());
            villager.setOnGround(true);
            return true;
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

    public static void tickPathDoors(ServerLevel level, Villager villager) {
        VillagerDoorCoordinator.tickPathDoors(level, villager);
    }

    public static void tickPathLadders(ServerLevel level, Villager villager) {
        VillagerLadderTraversal.tickPathLadders(level, villager);
    }

    public static void tickVillagerWaterSafety(ServerLevel level, Villager villager) {
        VillagerWaterTraversal.tickVillagerWaterSafety(level, villager);
    }

    public static void enableHiredWaterTraversal(Villager villager) {
        VillagerWaterTraversal.enableHiredWaterTraversal(villager);
    }

    public static void enableHiredFarmingWaterTraversal(Villager villager) {
        VillagerWaterTraversal.enableHiredFarmingWaterTraversal(villager);
    }

    public static void restoreHiredWaterTraversal(Villager villager) {
        VillagerWaterTraversal.restoreHiredWaterTraversal(villager);
    }

    public static boolean moveInWaterTowardNavigationTarget(ServerLevel level, Villager villager, double speed) {
        return VillagerWaterTraversal.moveInWaterTowardNavigationTarget(level, villager, speed);
    }

    public static boolean moveOnLadderToward(ServerLevel level, Villager villager, BlockPos target, double speed) {
        return VillagerLadderTraversal.moveOnLadderToward(level, villager, target, speed);
    }

    public static boolean moveTowardNearbyLadderThenClimb(
            ServerLevel level, Villager villager, BlockPos target, double speed) {
        return VillagerLadderTraversal.moveTowardNearbyLadderThenClimb(level, villager, target, speed);
    }

    public static boolean continueLadderRoute(
            ServerLevel level, Villager villager, BlockPos target, double speed) {
        return VillagerLadderTraversal.continueLadderRoute(level, villager, target, speed);
    }

    public static String ladderRouteDebug(ServerLevel level, Villager villager, BlockPos target) {
        return VillagerLadderTraversal.ladderRouteDebug(level, villager, target);
    }

    public static boolean moveTowardHighestSafePositionInLoadedChunk(
            ServerLevel level, Villager villager, BlockPos target, double speed) {
        return VillagerSurfaceEscapePlanner.moveTowardHighestSafePositionInLoadedChunk(
                level, villager, target, speed);
    }

    public static void clearRuntimeState() {
        VillagerDoorCoordinator.clearRuntimeState();
        VillagerWaterTraversal.clearRuntimeState();
        VillagerLadderTraversal.clearRuntimeState();
        VillagerSurfaceEscapePlanner.clearRuntimeState();
    }

    public static void clearRuntimeState(Villager villager) {
        VillagerDoorCoordinator.clearRuntimeState(villager);
        VillagerWaterTraversal.clearRuntimeState(villager);
        VillagerLadderTraversal.clearRuntimeState(villager);
        VillagerSurfaceEscapePlanner.clearRuntimeState(villager);
        clearHiredWalkTarget(villager);
    }
}
