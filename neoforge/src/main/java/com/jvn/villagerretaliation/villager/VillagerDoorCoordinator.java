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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

public final class VillagerDoorCoordinator {
    private static final double DOOR_REACH_DISTANCE = 2.25D;
    private static final double DOOR_REACH_DISTANCE_SQR = DOOR_REACH_DISTANCE * DOOR_REACH_DISTANCE;
    private static final Map<UUID, Set<GlobalPos>> DOORS_TO_CLOSE = new HashMap<>();
    private VillagerDoorCoordinator() {
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

    public static void clearRuntimeState() {
        DOORS_TO_CLOSE.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        DOORS_TO_CLOSE.remove(villager.getUUID());
    }
}
