package com.jvn.villagerretaliation.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class VillagerNavigationSafety {
    private VillagerNavigationSafety() {
    }

    static boolean isSafeLadderPosition(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        if (!feet.getFluidState().isEmpty()
                || !head.getFluidState().isEmpty()
                || !floor.getFluidState().isEmpty()
                || isDangerous(feet)
                || isDangerous(head)
                || isDangerous(floor)
                || VillagerContainerClimbGuard.isForbiddenStandingFloor(level, pos.below())) {
            return false;
        }
        return (feet.isAir() || feet.is(Blocks.LADDER) || feet.getCollisionShape(level, pos).isEmpty())
                && (head.isAir() || head.is(Blocks.LADDER) || head.getCollisionShape(level, pos.above()).isEmpty())
                && (floor.isSolid() || feet.is(Blocks.LADDER));
    }

    static boolean isDangerous(BlockState state) {
        return state.getBlock() instanceof BaseFireBlock
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW)
                || state.getFluidState().is(FluidTags.LAVA);
    }

}
