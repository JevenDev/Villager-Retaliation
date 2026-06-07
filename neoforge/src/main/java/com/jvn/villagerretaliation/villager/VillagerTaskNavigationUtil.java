package com.jvn.villagerretaliation.villager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
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
    private static final double LADDER_HORIZONTAL_TARGET_DISTANCE_SQR = 2.25D;
    private static final double LADDER_CLIMB_SPEED = 0.18D;
    private static final Map<UUID, Set<GlobalPos>> DOORS_TO_CLOSE = new HashMap<>();

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
        if (target != null) {
            moveOnLadderToward(level, villager, target, 0.45D);
        }
    }

    public static boolean moveOnLadderToward(ServerLevel level, Villager villager, BlockPos target, double speed) {
        if (target == null) {
            return false;
        }
        BlockPos ladder = ladderTouching(level, villager.blockPosition());
        if (ladder == null) {
            return false;
        }
        double targetDx = (target.getX() + 0.5D) - (ladder.getX() + 0.5D);
        double targetDz = (target.getZ() + 0.5D) - (ladder.getZ() + 0.5D);
        if (targetDx * targetDx + targetDz * targetDz > LADDER_HORIZONTAL_TARGET_DISTANCE_SQR) {
            return false;
        }
        double centerX = ladder.getX() + 0.5D;
        double centerZ = ladder.getZ() + 0.5D;
        double dx = centerX - villager.getX();
        double dz = centerZ - villager.getZ();
        if (dx * dx + dz * dz > LADDER_CENTERING_DISTANCE_SQR) {
            villager.getMoveControl().setWantedPosition(centerX, villager.getY(), centerZ, speed);
            return true;
        }
        double yDelta = target.getY() + 0.1D - villager.getY();
        if (Math.abs(yDelta) < 0.08D) {
            return false;
        }
        double climb = Math.clamp(yDelta, -LADDER_CLIMB_SPEED, LADDER_CLIMB_SPEED);
        Vec3 motion = villager.getDeltaMovement();
        villager.setDeltaMovement(motion.x * 0.65D, climb, motion.z * 0.65D);
        villager.setOnGround(false);
        return true;
    }

    public static void clearRuntimeState() {
        DOORS_TO_CLOSE.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        DOORS_TO_CLOSE.remove(villager.getUUID());
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
        if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER)) {
            return pos;
        }
        BlockPos above = pos.above();
        if (level.hasChunkAt(above) && level.getBlockState(above).is(Blocks.LADDER)) {
            return above;
        }
        BlockPos below = pos.below();
        if (level.hasChunkAt(below) && level.getBlockState(below).is(Blocks.LADDER)) {
            return below;
        }
        return null;
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
}
