package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MiningBlockRules {
    private MiningBlockRules() {
    }

    public static boolean isMineableOre(ServerLevel level, BlockPos pos) {
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

    public static boolean isMineableExcavationBlock(ServerLevel level, BlockPos pos) {
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

    public static boolean isMineableExcavationBlock(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos pos) {
        return !MiningHazardManager.isProtectedBarrier(context, pos)
                && isMineableExcavationBlock(level, pos);
    }

    static boolean isMineableExcavationBlock(
            ServerLevel level,
            BlockPos pos,
            LongSet protectedBarriers) {
        return (protectedBarriers == null || !protectedBarriers.contains(pos.asLong()))
                && isMineableExcavationBlock(level, pos);
    }

    public static boolean isCurrentExcavationLayer(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        Integer layerY = currentExcavationLayer(level, context);
        return layerY != null && pos.getY() == layerY;
    }

    public static Integer currentExcavationLayer(ServerLevel level, HiredWorkContext context) {
        if (!isExcavationAreaLoaded(level, context)) {
            MiningWorkerState.clearExcavationLayerCache(context);
            return null;
        }
        if (MiningWorkerState.hasFreshExcavationLayerCache(level, context)) {
            return MiningWorkerState.cachedExcavationLayer(context);
        }
        LongSet protectedBarriers = MiningHazardManager.protectedBarrierPositions(context);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = context.workMax().getY(); y >= context.workMin().getY(); y--) {
            for (int x = context.workMin().getX(); x <= context.workMax().getX(); x++) {
                for (int z = context.workMin().getZ(); z <= context.workMax().getZ(); z++) {
                    pos.set(x, y, z);
                    if (isMineableExcavationBlock(level, pos, protectedBarriers)) {
                        MiningWorkerState.rememberExcavationLayer(level, context, y);
                        return y;
                    }
                }
            }
        }
        MiningWorkerState.rememberExcavationLayer(level, context, null);
        return null;
    }

    static boolean isExcavationAreaLoaded(ServerLevel level, HiredWorkContext context) {
        int minChunkX = SectionPos.blockToSectionCoord(context.workMin().getX());
        int maxChunkX = SectionPos.blockToSectionCoord(context.workMax().getX());
        int minChunkZ = SectionPos.blockToSectionCoord(context.workMin().getZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(context.workMax().getZ());
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean hasAdjacentExcavationFluid(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor) || !level.getFluidState(neighbor).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUsableMiningTool(HiredMiningMode mode, ItemStack stack, BlockState targetState) {
        if (mode.excavatesArea()) {
            return stack.isCorrectToolForDrops(targetState)
                    && matchesExcavationToolTag(stack, targetState);
        }
        return stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(targetState);
    }

    public static String requiredMiningToolLabel(HiredMiningMode mode, BlockState targetState) {
        return mode.excavatesArea() ? requiredExcavationToolLabel(targetState) : "pickaxe";
    }

    public static String requiredExcavationToolLabel(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return "pickaxe";
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return "shovel";
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return "axe";
        }
        return "tool";
    }

    public static boolean isBuilderClearableObstruction(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && !state.liquid()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity()
                && hasExcavationToolTag(state);
    }

    public static boolean isUsableBuilderClearingTool(ItemStack stack, BlockState targetState) {
        return stack.isCorrectToolForDrops(targetState)
                && matchesExcavationToolTag(stack, targetState);
    }

    public static boolean hasExcavationToolTag(BlockState state) {
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
            if (neighbor.isAir() || neighbor.liquid() || isExcavationSupportBlock(neighbor)) {
                return true;
            }
        }
        return false;
    }
}
