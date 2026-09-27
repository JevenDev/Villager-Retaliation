package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

final class SettlementTerrainObservations {
    private final SettlementBuildingSurvey.ObservedBlocks blocks;
    private final SettlementConstructionObservations construction;
    private final Map<BlockPos, SettlementNavigationSection> navigation;
    private final Map<BlockPos, List<SettlementTerrainSection>> sections = new HashMap<>();
    private final Map<BlockPos, SettlementFieldPlot> plots = new HashMap<>();
    private final Set<Long> chunks = new HashSet<>();

    SettlementTerrainObservations(SettlementRecord town, SettlementBuildingSurvey.ObservedBlocks blocks) {
        this.blocks = blocks;
        this.construction = new SettlementConstructionObservations(town, blocks);
        this.navigation = town != null && town.dimension().equals(blocks.level().dimension().location()) ? town.navigation() : Map.of();
        if (town == null || !town.dimension().equals(blocks.level().dimension().location())) return;
        this.navigation.keySet().forEach(origin -> this.chunks.add(ChunkPos.asLong(origin)));
        for (var forest : town.forests().values()) {
            var area = town.areas().get(forest.area());
            if (forest.pending() || area != null && forest.matches(area)) {
                forest.sections().values().forEach(section -> add(section.terrain()));
            }
        }
        for (var mine : town.mines().values()) {
            var area = town.areas().get(mine.area());
            if (mine.pending() || area != null && mine.matches(area)) {
                mine.sections().values().forEach(this::add);
            }
        }
        for (var plot : town.fields().values()) {
            if (plot.pending() || town.areas().values().stream().anyMatch(area -> SettlementFieldService.serves(area, plot))) {
                this.plots.put(plot.position(), plot);
                this.chunks.add(ChunkPos.asLong(plot.position()));
            }
        }
    }

    private void add(SettlementTerrainSection section) {
        this.sections.computeIfAbsent(section.origin(), ignored -> new ArrayList<>()).add(section);
        this.chunks.add(ChunkPos.asLong(section.min()));
    }

    Map<BlockPos, BlockState> constructionChanges() {
        return this.construction.changes();
    }

    boolean observedChunk(int x, int z) {
        return this.chunks.contains(ChunkPos.asLong(x, z)) || this.construction.observedChunk(x, z);
    }

    BlockState state(BlockPos position) {
        var observation = observe(position);
        return observation.blocked() ? null : observation.state();
    }

    boolean blocked(BlockPos position) {
        return observe(position).blocked();
    }

    private Observation observe(BlockPos position) {
        var projected = this.construction.changedState(position);
        var chunk = this.blocks.chunk(position.getX() >> 4, position.getZ() >> 4);
        if (chunk != null) {
            return new Observation(projected == null ? chunk.getBlockState(position) : projected, false);
        }
        var combined = new Combined();
        combined.accept(this.construction.state(position), this.construction.changedFrom(position), this.construction.blocked(position));
        var nearby = this.sections.get(SettlementTerrainSection.sectionOrigin(position));
        if (nearby != null) {
            for (var section : nearby) {
                if (section.contains(position)) combined.accept(section.state(position), section.changedFrom(position), section.conflict());
            }
        }
        for (int height = 0; height <= 2; height++) {
            var plot = this.plots.get(position.below(height));
            if (plot != null) {
                var state = plot.observedState(position);
                if (state != null) combined.accept(state, plot.changedFrom(position), plot.conflict());
            }
        }
        var result = combined.result();
        if (result.state() != null || result.blocked()) return result;
        var observed = this.navigation.get(SettlementTerrainSection.sectionOrigin(position));
        return observed == null ? result : new Observation(observed.state(position), false);
    }

    private record Observation(BlockState state, boolean blocked) {
    }

    private static final class Combined {
        private final Set<BlockState> baselines = new HashSet<>();
        private BlockState before;
        private BlockState changed;
        private boolean changeConflict;
        private boolean unavailable;

        private void accept(BlockState state, BlockState before, boolean unavailable) {
            this.unavailable |= unavailable;
            if (state == null) return;
            if (before != null) {
                this.changeConflict |= this.changed != null && (this.changed != state || this.before != before);
                this.changed = state;
                this.before = before;
            } else {
                this.baselines.add(state);
            }
        }

        private Observation result() {
            boolean baselineConflict = this.changed == null ? this.baselines.size() > 1
                    : this.baselines.stream().anyMatch(state -> state != this.before && state != this.changed);
            return new Observation(this.changed == null ? this.baselines.stream().findFirst().orElse(null) : this.changed,
                    this.unavailable || this.changeConflict || baselineConflict);
        }
    }
}
