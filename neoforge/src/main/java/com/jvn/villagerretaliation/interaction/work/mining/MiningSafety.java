package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
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
        if (!isLoadedBodyPosition(level, pos)) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        if (!isPassableBodyBlock(level, pos, feet)
                || !isPassableBodyBlock(level, pos.above(), head)
                || !isSafeFloor(level, pos.below(), floor)) {
            return false;
        }
        return !hasImmediateHeatHazard(level, pos);
    }

    public static boolean isSafeExcavationApproach(ServerLevel level, BlockPos pos) {
        return HiredMoveToBlockFaceJob.isValidApproachPosition(level, pos)
                && isSafePathPosition(level, pos);
    }

    public static boolean isUnsafeMiningTarget(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            BlockPos plannedStance) {
        return isUnsafeUnderfootTarget(level, target, plannedStance)
                || isUnsafeUnderfootTarget(level, target, villager == null ? null : villager.blockPosition())
                || hasUncontainedFluidFace(level, target);
    }

    public static boolean isUnsafeUnderfootTarget(ServerLevel level, BlockPos target, BlockPos stance) {
        if (target == null
                || stance == null
                || stance.getX() != target.getX()
                || stance.getZ() != target.getZ()
                || stance.getY() != target.getY() + 1) {
            return false;
        }
        if (level == null || !level.hasChunkAt(stance)) {
            return true;
        }
        if (level.getBlockState(stance).is(Blocks.LADDER)) {
            return false;
        }
        return needsFallGuard(level, target.below());
    }

    public static boolean needsFallGuard(ServerLevel level, BlockPos landing) {
        if (landing == null || !level.hasChunkAt(landing)) {
            return true;
        }
        BlockState state = level.getBlockState(landing);
        if (!state.getFluidState().isEmpty() || isDamagingBlock(state)) {
            return true;
        }
        return !state.isSolid() && !state.is(Blocks.LADDER);
    }

    public static boolean hasUncontainedFluidFace(ServerLevel level, BlockPos target) {
        if (target == null) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            if (!level.hasChunkAt(neighbor)) {
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

    private static boolean isLoadedBodyPosition(ServerLevel level, BlockPos pos) {
        return level != null
                && pos != null
                && level.hasChunkAt(pos)
                && level.hasChunkAt(pos.above())
                && level.hasChunkAt(pos.below());
    }

    private static boolean isPassableBodyBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty() || isDamagingBlock(state)) {
            return false;
        }
        return state.isAir()
                || state.is(Blocks.LADDER)
                || state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isSafeFloor(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty() || isDamagingBlock(state)) {
            return false;
        }
        return state.isSolid() || state.is(Blocks.LADDER);
    }

    private static boolean hasImmediateHeatHazard(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) {
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
