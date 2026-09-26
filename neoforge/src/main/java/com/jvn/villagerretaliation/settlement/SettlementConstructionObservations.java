package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

final class SettlementConstructionObservations {
    private final Map<BlockPos, List<SettlementConstructionSite>> sections = new HashMap<>();
    private final Map<BlockPos, BlockState> changes = new HashMap<>();
    private final java.util.Set<SettlementConstructionSite> unavailable = new java.util.HashSet<>();

    SettlementConstructionObservations(SettlementRecord town, SettlementBuildingSurvey.ObservedBlocks blocks) {
        if (town == null || !town.dimension().equals(blocks.level().dimension().location())) return;
        for (var project : town.projects().values()) {
            var site = project.site();
            if (site == null || !site.pending()) continue;
            site.sections().keySet().forEach(origin -> this.sections.computeIfAbsent(origin, ignored -> new ArrayList<>()).add(site));
            var projected = new HashMap<BlockPos, BlockState>();
            boolean compatible = !site.conflict();
            for (var placement : site.placements().values()) {
                for (var part : placement.parts()) {
                    var pos = part.position();
                    LevelChunk chunk = blocks.chunk(pos.getX() >> 4, pos.getZ() >> 4);
                    if (chunk != null && placement.applied(chunk, project.id(), pos)) continue;
                    if (blocks.level().isOutsideBuildHeight(pos) || !blocks.level().getWorldBorder().isWithinBounds(pos)
                            || chunk != null && projected.getOrDefault(pos, chunk.getBlockState(pos)) != part.before()) {
                        compatible = false;
                    }
                    projected.put(pos, part.after());
                }
            }
            if (compatible) {
                projected.forEach((pos, state) -> {
                    if (this.changes.containsKey(pos) && this.changes.get(pos) != state) {
                        throw new IllegalStateException("Pending construction projects overlap");
                    }
                    this.changes.put(pos, state);
                });
            } else {
                this.unavailable.add(site);
            }
        }
    }

    Map<BlockPos, BlockState> changes() {
        return java.util.Collections.unmodifiableMap(this.changes);
    }

    BlockState changedState(BlockPos pos) {
        return this.changes.get(pos);
    }

    boolean observedChunk(int x, int z) {
        return this.sections.keySet().stream().anyMatch(pos -> pos.getX() >> 4 == x && pos.getZ() >> 4 == z);
    }

    boolean blocked(BlockPos pos) {
        if (this.changes.containsKey(pos)) return false;
        var sites = this.sections.get(SettlementTerrainSection.sectionOrigin(pos));
        if (sites == null) return false;
        BlockState observed = null;
        for (var site : sites) {
            if (!site.observed(pos)) continue;
            if (this.unavailable.contains(site)) return true;
            var state = site.getBlockState(pos);
            if (observed != null && observed != state) return true;
            observed = state;
        }
        return false;
    }

    BlockState state(BlockPos pos) {
        if (this.changes.containsKey(pos)) return this.changes.get(pos);
        var sites = this.sections.get(SettlementTerrainSection.sectionOrigin(pos));
        if (sites == null || blocked(pos)) return null;
        for (var site : sites) {
            if (site.observed(pos)) return site.getBlockState(pos);
        }
        return null;
    }
}
