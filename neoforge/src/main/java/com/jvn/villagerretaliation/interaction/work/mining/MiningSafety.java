package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Central safety policy for mining movement and block removal.
 *
 * <p>Mining previously spread subtly different safety checks across target selection,
 * path filtering, ladder movement, and the final block-break validation. Keeping those
 * decisions here makes a planned route agree with the action performed at its end.</p>
 */
public final class MiningSafety {
    private MiningSafety() {
    }

    public static boolean isSafePathPosition(ServerLevel level, BlockPos pos) {
        return level != null && isSafePathPosition(level, level::hasChunkAt, pos);
    }

    public static boolean isSafePathPosition(BlockGetter level, Predicate<BlockPos> observed, BlockPos pos) {
        if (!isLoadedBodyPosition(level, observed, pos)) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        if (!isPassableBodyBlock(level, pos, feet)
                || !isPassableBodyBlock(level, pos.above(), head)
                || !isSafeFloor(level, pos.below(), floor) && !hasLadderSupport(level, observed, pos, feet)) {
            return false;
        }
        return !hasImmediateHeatHazard(level, observed, pos);
    }

    public static boolean isUnsafeMiningTarget(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            BlockPos plannedStance) {
        return level == null || isUnsafeMiningTarget(level, level::hasChunkAt, target, plannedStance,
                villager == null ? null : villager.blockPosition());
    }

    public static boolean isUnsafeMiningTarget(
            BlockGetter level,
            Predicate<BlockPos> observed,
            BlockPos target,
            BlockPos plannedStance,
            BlockPos actualStance) {
        return isUnsafeUnderfootTarget(level, observed, target, plannedStance)
                || isUnsafeUnderfootTarget(level, observed, target, actualStance)
                || hasUncontainedFluidFace(level, observed, target);
    }

    public static boolean isUnsafeUnderfootTarget(ServerLevel level, BlockPos target, BlockPos stance) {
        return isUnsafeUnderfootTarget(level, position -> level != null && level.hasChunkAt(position), target, stance);
    }

    public static boolean isUnsafeUnderfootTarget(BlockGetter level, Predicate<BlockPos> observed, BlockPos target, BlockPos stance) {
        if (target == null
                || stance == null
                || stance.getX() != target.getX()
                || stance.getZ() != target.getZ()
                || stance.getY() != target.getY() + 1) {
            return false;
        }
        if (level == null || !observed.test(stance)) {
            return true;
        }
        if (level.getBlockState(stance).is(Blocks.LADDER)) {
            return false;
        }
        return needsFallGuard(level, observed, target.below());
    }

    public static boolean needsFallGuard(ServerLevel level, BlockPos landing) {
        return level == null || needsFallGuard(level, level::hasChunkAt, landing);
    }

    public static boolean needsFallGuard(BlockGetter level, Predicate<BlockPos> observed, BlockPos landing) {
        if (landing == null || !observed.test(landing)) {
            return true;
        }
        BlockState state = level.getBlockState(landing);
        if (!state.getFluidState().isEmpty() || isDamagingBlock(state)) {
            return true;
        }
        return !state.isSolid() && !state.is(Blocks.LADDER);
    }

    public static boolean hasUncontainedFluidFace(ServerLevel level, BlockPos target) {
        return level == null || hasUncontainedFluidFace(level, level::hasChunkAt, target);
    }

    public static boolean hasUncontainedFluidFace(BlockGetter level, Predicate<BlockPos> observed, BlockPos target) {
        if (target == null) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            if (!observed.test(neighbor)) {
                return true;
            }
            FluidState fluid = level.getFluidState(neighbor);
            if (fluid.is(FluidTags.LAVA) || fluid.is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDamagingBlock(BlockState state) {
        return state.getBlock() instanceof BaseFireBlock
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW);
    }

    private static boolean isLoadedBodyPosition(BlockGetter level, Predicate<BlockPos> observed, BlockPos pos) {
        return level != null
                && pos != null
                && observed.test(pos)
                && observed.test(pos.above())
                && observed.test(pos.below());
    }

    private static boolean isPassableBodyBlock(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty() || isDamagingBlock(state)) {
            return false;
        }
        return state.isAir()
                || state.is(Blocks.LADDER)
                || state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean hasLadderSupport(BlockGetter level, Predicate<BlockPos> observed, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.LADDER)) return false;
        Direction facing = state.getValue(LadderBlock.FACING);
        BlockPos backing = pos.relative(facing.getOpposite());
        BlockState below = level.getBlockState(pos.below());
        return below.getFluidState().isEmpty() && !isDamagingBlock(below)
                && observed.test(backing) && level.getBlockState(backing).isFaceSturdy(level, backing, facing);
    }

    private static boolean isSafeFloor(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty() || isDamagingBlock(state)) {
            return false;
        }
        return state.isSolid() || state.is(Blocks.LADDER);
    }

    private static boolean hasImmediateHeatHazard(BlockGetter level, Predicate<BlockPos> observed, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (!observed.test(neighbor)) {
                return true;
            }
            if (level.getFluidState(neighbor).is(FluidTags.LAVA)
                    || isDamagingBlock(level.getBlockState(neighbor))) {
                return true;
            }
        }
        return false;
    }
}
