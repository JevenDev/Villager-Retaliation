package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

public final class SettlementFarmWater {
    private static final Map<ServerLevel, Map<SettlementRecord, Search>> SEARCHES = new WeakHashMap<>();
    private static final int SCAN_BUDGET = 1024;
    public record Source(BlockPos water, BlockPos approach) {}
    private SettlementFarmWater() {}

    public static boolean infinite(ServerLevel level, BlockPos pos) {
        if (level.dimensionType().ultraWarm() || !source(level, pos) || !level.hasChunkAt(pos.below())) return false;
        var below = level.getBlockState(pos.below());
        if (!below.isSolid() && !source(level, pos.below())) return false;
        int neighbors = 0;
        for (var direction : Direction.Plane.HORIZONTAL) {
            var neighbor = pos.relative(direction);
            if (source(level, neighbor) && net.neoforged.neoforge.event.EventHooks.canCreateFluidSource(
                    level, neighbor, level.getBlockState(neighbor))) neighbors++;
        }
        return neighbors >= 2;
    }

    private static boolean source(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource();
    }

    public static Source find(ServerLevel level, Villager villager, BlockPos origin, CompoundTag job) {
        var town = SettlementWorkforceService.duty(villager).map(SettlementWorkforceService.Duty::town).orElse(null);
        if (town == null) return null;
        if (job.contains("WaterSource") && job.contains("WaterApproach")) {
            var water = BlockPos.of(job.getLong("WaterSource"));
            var approach = BlockPos.of(job.getLong("WaterApproach"));
            if (town.contains(water) && town.contains(approach) && infinite(level, water) && standable(level, approach)
                    && !HiredPathMemory.isApproachRecentlyUnreachable(level, villager, approach)) return new Source(water, approach);
            job.remove("WaterSource");
            job.remove("WaterApproach");
        }
        var searches = SEARCHES.computeIfAbsent(level, ignored -> new WeakHashMap<>());
        var search = searches.get(town);
        if (search == null || search.revision != town.territory().revision() || level.getGameTime() >= search.expires && search.chunkIndex >= search.chunks.size()) {
            search = new Search(town, origin, level.getGameTime());
            searches.put(town, search);
        }
        search.scan(level, town);
        var candidates = search.sources.stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(villager.blockPosition()))).toList();
        for (var water : candidates) {
            if (!town.contains(water) || !infinite(level, water)) continue;
            for (var direction : Direction.Plane.HORIZONTAL) {
                var approach = water.relative(direction).above();
                if (!standable(level, approach) || !town.contains(approach)
                        || HiredPathMemory.isApproachRecentlyUnreachable(level, villager, approach)) continue;
                boolean arrived = villager.blockPosition().equals(approach);
                if (!arrived && HiredPathMemory.shouldDelayPathSearch(level, villager)) return null;
                var path = arrived ? null : HiredPathMemory.createPath(level, villager, approach, 0);
                if (arrived || path != null && (path.canReach() || path.getEndNode() != null
                        && path.getEndNode().asBlockPos().distSqr(approach) < villager.blockPosition().distSqr(approach))) {
                    job.putLong("WaterSource", water.asLong());
                    job.putLong("WaterApproach", approach.asLong());
                    return new Source(water, approach);
                }
                HiredPathMemory.rememberUnreachableApproach(level, villager, approach);
                return null;
            }
        }
        return null;
    }

    private static boolean hasBank(ServerLevel level, SettlementRecord town, BlockPos water) {
        for (var direction : Direction.Plane.HORIZONTAL) {
            var approach = water.relative(direction).above();
            if (town.contains(approach) && standable(level, approach)) return true;
        }
        return false;
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static final class Search {
        final long revision;
        final long expires;
        final List<ChunkPos> chunks;
        final List<BlockPos> sources = new ArrayList<>();
        long lastScan = Long.MIN_VALUE;
        int chunkIndex;
        int sectionIndex;
        int cell;

        Search(SettlementRecord town, BlockPos origin, long now) {
            revision = town.territory().revision();
            expires = now + 1200;
            chunks = town.territory().plots().stream().map(ChunkPos::new)
                    .sorted(Comparator.comparingDouble(chunk -> chunk.getMiddleBlockPosition(origin.getY()).distSqr(origin))).toList();
        }

        void scan(ServerLevel level, SettlementRecord town) {
            if (lastScan == level.getGameTime()) return;
            lastScan = level.getGameTime();
            int budget = SCAN_BUDGET;
            while (chunkIndex < chunks.size() && budget > 0) {
                var position = chunks.get(chunkIndex);
                var chunk = level.getChunkSource().getChunkNow(position.x, position.z);
                if (chunk == null || sectionIndex >= chunk.getSectionsCount()) {
                    chunkIndex++;
                    sectionIndex = 0;
                    cell = 0;
                    budget--;
                    continue;
                }
                var section = chunk.getSection(sectionIndex);
                if (cell == 0 && !section.maybeHas(state -> state.is(Blocks.WATER))) {
                    sectionIndex++;
                    budget--;
                    continue;
                }
                var pos = new BlockPos(position.getMinBlockX() + (cell & 15),
                        level.getMinBuildHeight() + sectionIndex * 16 + (cell >> 8),
                        position.getMinBlockZ() + ((cell >> 4) & 15));
                if (town.contains(pos) && infinite(level, pos) && hasBank(level, town, pos)) {
                    if (sources.size() == 64) sources.removeFirst();
                    sources.add(pos);
                }
                cell++;
                budget--;
                if (cell == 4096) {
                    cell = 0;
                    sectionIndex++;
                }
            }
        }
    }
}
