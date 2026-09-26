package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.mixin.SaplingBlockAccessor;
import com.jvn.villagerretaliation.mixin.TreeGrowerAccessor;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.neoforged.neoforge.event.EventHooks;

final class SettlementTreeGrowth {
    private SettlementTreeGrowth() {
    }

    static boolean sapling(BlockState state) {
        return state.is(BlockTags.SAPLINGS) || state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA)
                || state.is(Blocks.CRIMSON_FUNGUS) || state.is(Blocks.WARPED_FUNGUS);
    }

    static boolean growsNaturally(BlockState state) {
        return state != null && (state.getBlock().getClass() == SaplingBlock.class
                || state.getBlock().getClass() == MangrovePropaguleBlock.class
                && (!state.getValue(MangrovePropaguleBlock.HANGING) || state.getValue(MangrovePropaguleBlock.AGE) < MangrovePropaguleBlock.MAX_AGE));
    }

    static Plan bonemeal(ServerLevel level, SettlementForest forest, BlockPos position, RandomSource random) {
        var world = new SettlementTreeWorld(level, forest, random);
        try {
            var state = world.getBlockState(position);
            if (!(state.getBlock() instanceof SaplingBlock sapling)
                    || sapling.getClass() != SaplingBlock.class && sapling.getClass() != MangrovePropaguleBlock.class) {
                return new Plan(null, "tree_needs_native_growth", false);
            }
            boolean hanging = state.is(Blocks.MANGROVE_PROPAGULE) && state.getValue(MangrovePropaguleBlock.HANGING);
            if (hanging && state.getValue(MangrovePropaguleBlock.AGE) >= MangrovePropaguleBlock.MAX_AGE) {
                return new Plan(null, "waiting_for_trees", false);
            }
            if (!state.canSurvive(world, position)) return new Plan(null, "sapling_blocked", false);
            var event = EventHooks.fireBonemealEvent(null, level, position, state, new ItemStack(Items.BONE_MEAL));
            if (event.isCanceled()) return new Plan(null, "bonemealing_sapling", event.isSuccessful());
            if (hanging) {
                world.setBlock(position, state.cycle(MangrovePropaguleBlock.AGE), 2);
                return new Plan(world, "bonemealing_sapling", true);
            }
            if (random.nextFloat() >= 0.45F) return new Plan(null, "bonemealing_sapling", true);
            return advance(level, world, position, state, (SaplingBlockAccessor) sapling, random);
        } catch (SettlementTreeWorld.Unavailable unavailable) {
            return new Plan(null, unavailable.getMessage(), false);
        }
    }

    static Plan advance(ServerLevel level, SettlementForest forest, BlockPos position, RandomSource random) {
        var world = new SettlementTreeWorld(level, forest, random);
        try {
            var state = world.getBlockState(position);
            if (!(state.getBlock() instanceof SaplingBlock sapling)
                    || sapling.getClass() != SaplingBlock.class && sapling.getClass() != MangrovePropaguleBlock.class) {
                return new Plan(null, "tree_needs_native_growth", false);
            }
            if (!state.canSurvive(world, position)) return new Plan(null, "sapling_blocked", false);
            if (state.is(Blocks.MANGROVE_PROPAGULE) && state.getValue(MangrovePropaguleBlock.HANGING)) {
                if (state.getValue(MangrovePropaguleBlock.AGE) >= MangrovePropaguleBlock.MAX_AGE) return new Plan(null, "waiting_for_trees", false);
                world.setBlock(position, state.cycle(MangrovePropaguleBlock.AGE), 2);
                return new Plan(world, "sapling_growing", true);
            }
            return advance(level, world, position, state, (SaplingBlockAccessor) sapling, random);
        } catch (SettlementTreeWorld.Unavailable unavailable) {
            return new Plan(null, unavailable.getMessage(), false);
        }
    }

    private static Plan advance(ServerLevel level, SettlementTreeWorld world, BlockPos position, BlockState state,
                                SaplingBlockAccessor sapling, RandomSource random) {
        if (state.getValue(SaplingBlock.STAGE) == 0) {
            world.setBlock(position, state.cycle(SaplingBlock.STAGE), 4);
            return new Plan(world, "sapling_growing", true);
        }
        var grower = (TreeGrowerAccessor) (Object) sapling.villagerretaliation$treeGrower();
        var megaKey = grower.villagerretaliation$configuredMegaFeature(random);
        if (megaKey != null) {
            var mega = feature(level, position, megaKey, random);
            if (mega.canceled()) return new Plan(null, "sapling_blocked", true);
            if (mega.feature() != null) {
                for (int x = 0; x >= -1; x--) {
                    for (int z = 0; z >= -1; z--) {
                        var root = position.offset(x, 0, z);
                        var square = List.of(root, root.east(), root.south(), root.east().south());
                        if (square.stream().allMatch(pos -> world.getBlockState(pos).is(state.getBlock()))) {
                            square.forEach(pos -> world.setBlock(pos, Blocks.AIR.defaultBlockState(), 4));
                            return place(level, world, root, mega.feature(), random);
                        }
                    }
                }
            }
        }
        boolean flowers = false;
        for (BlockPos nearby : BlockPos.betweenClosed(position.offset(-2, -1, -2), position.offset(2, 1, 2))) {
            if (world.getBlockState(nearby).is(BlockTags.FLOWERS)) {
                flowers = true;
                break;
            }
        }
        var key = grower.villagerretaliation$configuredFeature(random, flowers);
        if (key == null) return new Plan(null, "sapling_blocked", true);
        var selected = feature(level, position, key, random);
        if (selected.canceled() || selected.feature() == null) return new Plan(null, "sapling_blocked", true);
        world.setBlock(position, world.getFluidState(position).createLegacyBlock(), 4);
        return place(level, world, position, selected.feature(), random);
    }

    private static Selection feature(ServerLevel level, BlockPos position, ResourceKey<ConfiguredFeature<?, ?>> key, RandomSource random) {
        var holder = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(key).orElse(null);
        var event = EventHooks.fireBlockGrowFeature(level, random, position, holder);
        return new Selection(event.getFeature(), event.isCanceled());
    }

    private static Plan place(ServerLevel level, SettlementTreeWorld world, BlockPos position,
                              Holder<ConfiguredFeature<?, ?>> feature, RandomSource random) {
        if (!(feature.value().feature() instanceof TreeFeature)) return new Plan(null, "tree_needs_native_growth", false);
        boolean placed = feature.value().place(world, level.getChunkSource().getGenerator(), random, position);
        return new Plan(placed ? world : null, placed ? "tree_grown" : "sapling_blocked", true);
    }

    record Plan(SettlementTreeWorld world, String status, boolean accepted) {
        boolean commit() {
            return this.accepted && (this.world == null || this.world.commit());
        }
    }

    private record Selection(Holder<ConfiguredFeature<?, ?>> feature, boolean canceled) {
    }
}
