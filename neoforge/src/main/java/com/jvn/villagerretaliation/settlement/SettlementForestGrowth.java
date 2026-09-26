package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.state.BlockState;

final class SettlementForestGrowth {
    private static final int MAX_GROWTH_ATTEMPTS = 32;

    private SettlementForestGrowth() {
    }

    static void advance(ServerLevel level, SettlementForest forest, boolean enabled, long now) {
        var ready = new ArrayList<BlockPos>();
        int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        for (var section : forest.sections().values()) {
            int elapsed = section.elapsed(now);
            if (!enabled || elapsed <= 0 || randomTickSpeed <= 0 || forest.conflict() || !section.environmentKnown()
                    || level.getChunkSource().isPositionTicking(new ChunkPos(section.min()).toLong())) continue;
            for (var position : section.saplings()) {
                var state = forest.getBlockState(position);
                if (!canGrow(level, forest, position, state)) continue;
                // accumulate expected growth attempts from vanilla's 4096-block section draws
                double interval = elapsed * (double) randomTickSpeed / 4096.0D / (hanging(state) ? 1.0D : 7.0D);
                section.growth(position, section.growth(position) + interval);
                if (section.growth(position) >= 1.0D) ready.add(position);
            }
        }
        ready.sort(Comparator.naturalOrder());
        if (ready.isEmpty()) return;
        int start = Math.floorMod(forest.growthCursor(), ready.size());
        int visited = Math.min(MAX_GROWTH_ATTEMPTS, ready.size());
        for (int index = 0; index < visited; index++) {
            var position = ready.get((start + index) % ready.size());
            var state = forest.getBlockState(position);
            if (!SettlementTreeGrowth.growsNaturally(state) || !canGrow(level, forest, position, state)) continue;
            var section = forest.sections().get(SettlementForestSection.sectionOrigin(position));
            section.growth(position, 0.0D);
            SettlementTreeGrowth.advance(level, forest, position, level.random).commit();
        }
        forest.growthCursor((start + visited) % ready.size());
    }

    private static boolean canGrow(ServerLevel level, SettlementForest forest, BlockPos position, BlockState state) {
        return state.is(Blocks.MANGROVE_PROPAGULE) || forest.brightness(position.above(), level.getSkyDarken()) >= 9;
    }

    private static boolean hanging(BlockState state) {
        return state.is(Blocks.MANGROVE_PROPAGULE) && state.getValue(MangrovePropaguleBlock.HANGING);
    }
}
