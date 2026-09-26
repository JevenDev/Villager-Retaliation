package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class SettlementFieldService {
    private SettlementFieldService() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        reconcileChunk(event);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        reconcileChunk(event);
    }

    private static void reconcileChunk(ChunkEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location())) {
                continue;
            }
            for (var plot : List.copyOf(town.fields().values())) {
                if (new ChunkPos(plot.position()).equals(chunk.getPos())) {
                    if (plot.reconcile(chunk)) {
                        plot.observe(level, chunk, level.getGameTime());
                    } else if (!plot.pending()) {
                        town.removeField(plot.position());
                    }
                    plot.pause(level.getGameTime());
                    town.changed();
                }
            }
        }
    }

    static void observeArea(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk supplied) {
        if (area.type() != SettlementAreaType.FARM) {
            return;
        }
        for (int chunkX = Math.floorDiv(area.min().getX(), 16); chunkX <= Math.floorDiv(area.max().getX(), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(area.min().getZ(), 16); chunkZ <= Math.floorDiv(area.max().getZ(), 16); chunkZ++) {
                var chunk = supplied != null && supplied.getPos().x == chunkX && supplied.getPos().z == chunkZ
                        ? supplied : level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                int minY = Math.max(level.getMinBuildHeight(), area.min().getY() - 1);
                int maxY = Math.min(level.getMaxBuildHeight() - 2, area.max().getY());
                for (int sectionY = Math.floorDiv(minY, 16); sectionY <= Math.floorDiv(maxY, 16); sectionY++) {
                    var section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));
                    if (!section.maybeHas(SettlementFieldPlot::supportedSoil)) {
                        continue;
                    }
                    for (int y = Math.max(minY, sectionY * 16); y <= Math.min(maxY, sectionY * 16 + 15); y++) {
                        for (int x = Math.max(area.min().getX(), chunk.getPos().getMinBlockX()); x <= Math.min(area.max().getX(), chunk.getPos().getMaxBlockX()); x++) {
                            for (int z = Math.max(area.min().getZ(), chunk.getPos().getMinBlockZ()); z <= Math.min(area.max().getZ(), chunk.getPos().getMaxBlockZ()); z++) {
                                BlockPos position = new BlockPos(x, y, z);
                                var soil = chunk.getBlockState(position);
                                var crop = chunk.getBlockState(position.above());
                                if (!SettlementFieldPlot.supportedSoil(soil) || !crop.isAir() && !SettlementFieldPlot.supportedCrop(crop)) {
                                    continue;
                                }
                                var plot = town.fields().get(position);
                                if (plot == null || !plot.pending() && !plot.area().equals(area.id())) {
                                    town.recordField(SettlementFieldPlot.capture(level, area.id(), position, chunk, level.getGameTime()));
                                } else if (plot.reconcile(chunk)) {
                                    if (level.getChunkSource().isPositionTicking(chunk.getPos().toLong())) {
                                        plot.observe(level, chunk, level.getGameTime());
                                    } else {
                                        plot.refresh(chunk);
                                        plot.observeEnvironment(level, chunk);
                                    }
                                    town.changed();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        for (var plot : List.copyOf(town.fields().values())) {
            var area = town.areas().get(plot.area());
            var chunk = loaded(level, plot.position());
            if (chunk != null && !plot.reconcile(chunk)) {
                if (!plot.pending()) {
                    town.removeField(plot.position());
                }
                plot.pause(now);
                continue;
            }
            if (chunk != null) {
                plot.refresh(chunk);
            }
            if (!plot.pending() && (!SettlementFieldPlot.supportedSoil(plot.soil())
                    || !plot.crop().isAir() && !SettlementFieldPlot.supportedCrop(plot.crop()))) {
                town.removeField(plot.position());
            } else if (area == null || area.type() != SettlementAreaType.FARM
                    || !area.contains(plot.position()) && !area.contains(plot.position().above())) {
                if (!plot.pending()) {
                    town.removeField(plot.position());
                } else {
                    plot.pause(now);
                }
            } else if (!area.enabled()) {
                plot.pause(now);
            } else if (chunk != null && level.getChunkSource().isPositionTicking(chunk.getPos().toLong())) {
                plot.observe(level, chunk, now);
            } else {
                plot.advance(level, town, now);
                applyLoaded(level, plot);
            }
        }
        if (!town.fields().isEmpty()) {
            town.changed();
        }
    }

    static boolean available(ServerLevel level, SettlementFieldPlot plot) {
        if (plot.conflict()) {
            return false;
        }
        var chunk = loaded(level, plot.position());
        if (chunk == null) {
            return true;
        }
        if (!plot.reconcile(chunk)) {
            return false;
        }
        plot.refresh(chunk);
        return SettlementFieldPlot.supportedSoil(plot.soil()) && (plot.crop().isAir() || SettlementFieldPlot.supportedCrop(plot.crop()));
    }

    static void applyLoaded(ServerLevel level, SettlementFieldPlot plot) {
        var chunk = loaded(level, plot.position());
        if (chunk != null) {
            var soil = chunk.getBlockState(plot.position());
            var crop = chunk.getBlockState(plot.position().above());
            if (plot.reconcile(chunk)) {
                level.sendBlockUpdated(plot.position(), soil, plot.soil(), 2);
                level.sendBlockUpdated(plot.position().above(), crop, plot.crop(), 2);
            }
        }
    }

    private static LevelChunk loaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
    }
}
