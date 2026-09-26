package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;

public final class SettlementFisheryService {
    static final int MAX_SPOTS = 64;
    private static final java.util.Map<SettlementRecord, java.util.Map<java.util.UUID, Scan>> SCANS = new java.util.WeakHashMap<>();

    private SettlementFisheryService() {
    }

    static void observeArea(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk supplied) {
        if (area.type() != SettlementAreaType.FISHERY) {
            return;
        }
        var previous = town.fishingSpots().getOrDefault(area.id(), List.of());
        var spots = new ArrayList<SettlementFishingSpot>();
        boolean changed = false;
        for (var spot : previous) {
            changed |= spot.observe(level, supplied);
            if (spot.within(area) && spot.valid()) {
                spots.add(spot);
            }
        }
        if (spots.size() < MAX_SPOTS) {
            discover(level, town, area, supplied, spots);
        }
        if (changed || !spots.equals(previous)) {
            town.recordFishingSpots(area.id(), spots);
        }
    }

    private static void discover(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk supplied,
                                 List<SettlementFishingSpot> spots) {
        var blocks = new SettlementBuildingSurvey.ObservedBlocks(level, supplied);
        int minY = Math.max(level.getMinBuildHeight(), area.min().getY() - 8);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, area.max().getY() + 8);
        if (minY > maxY) {
            return;
        }
        int width = area.max().getX() - area.min().getX() + 17;
        int depth = area.max().getZ() - area.min().getZ() + 17;
        int height = maxY - minY + 1;
        int firstY = Math.clamp(area.min().getY() - 1, minY, maxY);
        long volume = (long) width * depth * height;
        var cursors = SCANS.computeIfAbsent(town, ignored -> new java.util.HashMap<>());
        var previous = cursors.get(area.id());
        long cursor = previous != null && previous.min().equals(area.min()) && previous.max().equals(area.max()) ? previous.next() : 0L;
        int waters = 0;
        for (int scanned = 0; scanned < Math.min(volume, 4_096L) && waters < 32 && spots.size() < MAX_SPOTS; scanned++) {
            long index = cursor;
            cursor = (cursor + 1) % volume;
            int x = (int) (index % width);
            index /= width;
            int z = (int) (index % depth);
            int y = (int) (index / depth);
            BlockPos water = new BlockPos(area.min().getX() - 8 + x, minY + (firstY - minY + y) % height, area.min().getZ() - 8 + z);
            if (!blocks.observed(water) || !blocks.observed(water.above())
                    || !blocks.getFluidState(water).is(Fluids.WATER) || blocks.getFluidState(water.above()).is(FluidTags.WATER)
                    || !blocks.getBlockState(water).getCollisionShape(blocks, water).isEmpty()
                    || spots.stream().anyMatch(spot -> spot.water().equals(water))) {
                continue;
            }
            waters++;
            var approaches = new ArrayList<BlockPos>();
            for (BlockPos approach : BlockPos.betweenClosed(water.offset(-6, -2, -6), water.offset(6, 2, 6))) {
                if (area.contains(approach) && water.distSqr(approach) <= 64.0D && blocks.observed(approach)
                        && SettlementFishingSpot.dryApproach(blocks, approach)) {
                    approaches.add(approach.immutable());
                }
            }
            approaches.sort(Comparator.comparingDouble(approach -> Math.abs(Math.sqrt(water.distSqr(approach)) - 5.0D)));
            for (BlockPos approach : approaches) {
                if (!SettlementFishingSpot.clearCast(blocks, approach, water)) {
                    continue;
                }
                var spot = SettlementFishingSpot.capture(level, area, water, approach, supplied);
                if (spot != null) {
                    spots.add(spot);
                    break;
                }
            }
        }
        cursors.put(area.id(), new Scan(area.min(), area.max(), cursor));
    }

    private record Scan(BlockPos min, BlockPos max, long next) {
    }

    static void update(ServerLevel level, SettlementRecord town) {
        var scans = SCANS.get(town);
        if (scans != null) {
            scans.keySet().removeIf(id -> !town.areas().containsKey(id) || town.areas().get(id).type() != SettlementAreaType.FISHERY);
        }
        for (var entry : List.copyOf(town.fishingSpots().entrySet())) {
            var area = town.areas().get(entry.getKey());
            var valid = new ArrayList<SettlementFishingSpot>();
            boolean changed = false;
            for (var spot : entry.getValue()) {
                changed |= spot.observe(level, null);
                if (spot.within(area) && spot.valid()) {
                    valid.add(spot);
                }
            }
            if (changed || !valid.equals(entry.getValue())) {
                town.recordFishingSpots(entry.getKey(), valid);
            }
        }
    }
}
