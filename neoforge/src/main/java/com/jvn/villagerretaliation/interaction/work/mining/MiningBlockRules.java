package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MiningBlockRules {
    private MiningBlockRules() {
    }

    public static int breakTicks(BlockGetter level, BlockPos pos, ItemStack tool, int speedPercent) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0.0F) return 1;
        int divisor = tool.isCorrectToolForDrops(state) ? 30 : 100;
        int ticks = Math.max(1, (int) Math.ceil(hardness * divisor / Math.max(0.001F, destroySpeed(tool, state))));
        return com.jvn.villagerretaliation.interaction.HiredVillagerRoles.scaledDurationTicks(ticks, speedPercent);
    }

    public static float destroySpeed(ItemStack tool, BlockState state) {
        float speed = tool.getDestroySpeed(state);
        int efficiency = 0;
        for (var entry : tool.getEnchantments().entrySet()) {
            if (entry.getKey().unwrapKey().map(key -> key.location().getPath().equals("efficiency")).orElse(false)) {
                efficiency = Math.max(efficiency, entry.getIntValue());
            }
        }
        return efficiency > 0 && speed > 1.0F ? speed + efficiency * efficiency + 1 : speed;
    }

    public static boolean isMineableOre(ServerLevel level, BlockPos pos) {
        return isMineableOre(level, level::hasChunkAt, pos);
    }

    public static boolean isMineableOre(BlockGetter level, Predicate<BlockPos> observed, BlockPos pos) {
        if (!observed.test(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity()
                && HiredOreBlockTracker.isTrackedOre(state)
                && isExposed(level, observed, pos);
    }

    public static boolean isMineableExcavationBlock(ServerLevel level, BlockPos pos) {
        return isMineableExcavationBlock(level, level::hasChunkAt, pos);
    }

    public static boolean isMineableExcavationBlock(BlockGetter level, Predicate<BlockPos> observed, BlockPos pos) {
        return isExcavationMaterial(level, observed, pos) && isExposed(level, observed, pos);
    }

    public static boolean isExcavationMaterial(BlockGetter level, Predicate<BlockPos> observed, BlockPos pos) {
        if (!observed.test(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && !state.liquid()
                && !isExcavationSupportBlock(state)
                && isFullExcavationBlock(level, pos, state)
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity()
                && hasExcavationToolTag(state);
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
        Integer layer = currentExcavationLayer(level, level::hasChunkAt, context);
        MiningWorkerState.rememberExcavationLayer(level, context, layer);
        return layer;
    }

    public static Integer currentExcavationLayer(BlockGetter level, Predicate<BlockPos> observed, HiredWorkContext context) {
        LongSet protectedBarriers = MiningHazardManager.protectedBarrierPositions(context);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = context.workMax().getY(); y >= context.workMin().getY(); y--) {
            for (int x = context.workMin().getX(); x <= context.workMax().getX(); x++) {
                for (int z = context.workMin().getZ(); z <= context.workMax().getZ(); z++) {
                    pos.set(x, y, z);
                    if (!protectedBarriers.contains(pos.asLong()) && isMineableExcavationBlock(level, observed, pos)) {
                        return y;
                    }
                }
            }
        }
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
        return hasAdjacentExcavationFluid(level, level::hasChunkAt, pos);
    }

    public static boolean hasAdjacentExcavationFluid(BlockGetter level, Predicate<BlockPos> observed, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!observed.test(neighbor) || !level.getFluidState(neighbor).isEmpty()) {
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
        return isBuilderClearableObstruction((BlockGetter) level, pos, state);
    }

    public static boolean isBuilderClearableObstruction(BlockGetter level, BlockPos pos, BlockState state) {
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

    private static boolean isFullExcavationBlock(BlockGetter level, BlockPos pos, BlockState state) {
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

    private static boolean isExposed(BlockGetter level, Predicate<BlockPos> observed, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!observed.test(pos.relative(direction))) {
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
