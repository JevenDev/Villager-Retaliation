package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class MiningBlockRules {
    private MiningBlockRules() {
    }

    static boolean isMineableOre(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity()
                && HiredOreBlockTracker.isTrackedOre(state)
                && isExposed(level, pos);
    }

    static boolean isMineableExcavationBlock(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && !state.liquid()
                && !isExcavationSupportBlock(state)
                && isFullExcavationBlock(level, pos, state)
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity()
                && hasExcavationToolTag(state)
                && isExposed(level, pos);
    }

    static boolean isCurrentExcavationLayer(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        Integer layerY = currentExcavationLayer(level, context);
        return layerY != null && pos.getY() == layerY;
    }

    static Integer currentExcavationLayer(ServerLevel level, HiredWorkContext context) {
        if (MiningWorkerState.hasFreshExcavationLayerCache(level, context)) {
            return MiningWorkerState.cachedExcavationLayer(context);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = context.workMax().getY(); y >= context.workMin().getY(); y--) {
            for (int x = context.workMin().getX(); x <= context.workMax().getX(); x++) {
                for (int z = context.workMin().getZ(); z <= context.workMax().getZ(); z++) {
                    pos.set(x, y, z);
                    if (isMineableExcavationBlock(level, pos)
                            && !hasAdjacentExcavationFluid(level, pos)) {
                        MiningWorkerState.rememberExcavationLayer(level, context, y);
                        return y;
                    }
                }
            }
        }
        MiningWorkerState.rememberExcavationLayer(level, context, null);
        return null;
    }

    static boolean hasAdjacentExcavationFluid(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.hasChunkAt(neighbor) && !level.getFluidState(neighbor).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean isUsableMiningTool(HiredMiningMode mode, ItemStack stack, BlockState targetState) {
        if (mode.excavatesArea()) {
            return stack.isCorrectToolForDrops(targetState)
                    && matchesExcavationToolTag(stack, targetState);
        }
        return stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(targetState);
    }

    static boolean isBuilderClearableObstruction(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && !state.liquid()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity()
                && hasExcavationToolTag(state);
    }

    static boolean isUsableBuilderClearingTool(ItemStack stack, BlockState targetState) {
        return stack.isCorrectToolForDrops(targetState)
                && matchesExcavationToolTag(stack, targetState);
    }

    static boolean hasExcavationToolTag(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.MINEABLE_WITH_AXE);
    }

    private static boolean matchesExcavationToolTag(ItemStack stack, BlockState state) {
        return (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && stack.is(ItemTags.PICKAXES))
                || (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && stack.is(ItemTags.SHOVELS))
                || (state.is(BlockTags.MINEABLE_WITH_AXE) && stack.is(ItemTags.AXES));
    }

    private static boolean isFullExcavationBlock(ServerLevel level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            if (!state.isFaceSturdy(level, pos, direction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isExcavationSupportBlock(BlockState state) {
        return state.is(Blocks.LADDER)
                || state.is(Blocks.TORCH)
                || state.is(Blocks.WALL_TORCH);
    }

    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.hasChunkAt(pos.relative(direction))) {
                continue;
            }
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.isAir() || neighbor.liquid()) {
                return true;
            }
        }
        return false;
    }
}
