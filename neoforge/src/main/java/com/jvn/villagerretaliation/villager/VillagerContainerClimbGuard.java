package com.jvn.villagerretaliation.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class VillagerContainerClimbGuard {
    private static final int ESCAPE_RADIUS = 3;

    private VillagerContainerClimbGuard() {
    }

    public static void tick(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || villager.isPassenger()
                || villager.isSleeping()
                || !isForbiddenStandingFloor(level, villager.blockPosition().below())) {
            return;
        }
        BlockPos escape = nearestSafeStandPosition(level, villager);
        if (escape == null) {
            return;
        }
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.moveTo(
                escape.getX() + 0.5D,
                escape.getY(),
                escape.getZ() + 0.5D,
                villager.getYRot(),
                villager.getXRot());
    }

    public static boolean isForbiddenStandingFloor(BlockGetter level, BlockPos floorPos) {
        if (level == null || floorPos == null) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(floorPos);
        return blockEntity instanceof Container;
    }

    public static boolean isSafeStandPosition(ServerLevel level, BlockPos pos) {
        if (level == null
                || pos == null
                || !level.hasChunkAt(pos)
                || !level.hasChunkAt(pos.above())
                || !level.hasChunkAt(pos.below())
                || isForbiddenStandingFloor(level, pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return feet.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty()
                && head.getCollisionShape(level, pos.above(), CollisionContext.empty()).isEmpty()
                && floor.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static BlockPos nearestSafeStandPosition(ServerLevel level, Villager villager) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos raw : BlockPos.betweenClosed(
                origin.offset(-ESCAPE_RADIUS, -1, -ESCAPE_RADIUS),
                origin.offset(ESCAPE_RADIUS, 1, ESCAPE_RADIUS))) {
            BlockPos candidate = raw.immutable();
            if (candidate.equals(origin) || !isSafeStandPosition(level, candidate)) {
                continue;
            }
            double distance = villager.distanceToSqr(candidate.getCenter());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
