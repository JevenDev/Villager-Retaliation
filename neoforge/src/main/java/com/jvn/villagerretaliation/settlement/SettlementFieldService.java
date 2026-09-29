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
                if (!town.contains(plot.position()) || town.areas().values().stream().anyMatch(area -> !town.owns(area) && area.type().usesFields() && area.contains(plot.position()))) continue;
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
        if (!area.type().usesFields() || !town.owns(area)) {
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
                    int aboveSection = chunk.getSectionIndexFromSectionY(sectionY + 1);
                    boolean cropsAbove = aboveSection < chunk.getSections().length
                            && chunk.getSection(aboveSection).maybeHas(state -> SettlementFieldPlot.supportedCrop(state)
                            || state.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS));
                    if (!section.maybeHas(state -> SettlementFieldPlot.supportedSoil(state)
                            || SettlementFieldPlot.supportedCrop(state) || state.is(net.minecraft.tags.BlockTags.DIRT)
                            || state.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)) && !cropsAbove) {
                        continue;
                    }
                    for (int y = Math.max(minY, sectionY * 16); y <= Math.min(maxY, sectionY * 16 + 15); y++) {
                        for (int x = Math.max(area.min().getX(), chunk.getPos().getMinBlockX()); x <= Math.min(area.max().getX(), chunk.getPos().getMaxBlockX()); x++) {
                            for (int z = Math.max(area.min().getZ(), chunk.getPos().getMinBlockZ()); z <= Math.min(area.max().getZ(), chunk.getPos().getMaxBlockZ()); z++) {
                                BlockPos position = new BlockPos(x, y, z);
                                var soil = chunk.getBlockState(position);
                                var crop = chunk.getBlockState(position.above());
                                if (!(area.type() == SettlementAreaType.ANIMAL_PEN
                                        ? SettlementFieldPlot.supportedPasture(soil, crop) : SettlementFieldPlot.supportedSite(soil, crop))) {
                                    continue;
                                }
                                var plot = town.fields().get(position);
                                if (plot == null) {
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
        for (var plot : List.copyOf(town.fields().values())) updatePlot(level, town, plot, now);
    }

    static void schedule(ServerLevel level, SettlementRecord town, long now, List<Runnable> jobs) {
        jobs.add(SettlementWork.each(List.copyOf(town.fields().values()), plot -> updatePlot(level, town, plot, now)));
    }

    private static void updatePlot(ServerLevel level, SettlementRecord town, SettlementFieldPlot plot, long now) {
        if (town.fields().get(plot.position()) != plot) return;
        town.changed();
        if (!town.contains(plot.position()) || town.areas().values().stream().anyMatch(area -> !town.owns(area) && area.type().usesFields() && area.contains(plot.position()))) {
            plot.pause(now);
            return;
        }
        var areas = town.areas().values().stream().filter(area -> serves(area, plot)).toList();
        var chunk = loaded(level, plot.position());
        if (chunk != null && !plot.reconcile(chunk)) {
            if (!plot.pending()) {
                town.removeField(plot.position());
            }
            plot.pause(now);
            return;
        }
        if (chunk != null) {
            plot.refresh(chunk);
        }
        if (!plot.pending() && !SettlementFieldPlot.supportedPasture(plot.soil(), plot.crop())
                && !SettlementFieldPlot.supportedSite(plot.soil(), plot.crop())) {
            town.removeField(plot.position());
        } else if (areas.isEmpty()) {
            if (!plot.pending()) {
                town.removeField(plot.position());
            } else {
                plot.pause(now);
            }
        } else if (areas.stream().noneMatch(SettlementArea::enabled)) {
            plot.pause(now);
        } else if (chunk != null && level.getChunkSource().isPositionTicking(chunk.getPos().toLong())) {
            plot.observe(level, chunk, now);
        } else {
            plot.observeEnvironment(level, chunk);
            plot.advance(level, town, now);
            applyLoaded(level, plot);
        }
    }

    static boolean serves(SettlementArea area, SettlementFieldPlot plot) {
        return area != null && area.type().usesFields()
                && (area.contains(plot.position()) || area.contains(plot.position().above()));
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
        plot.observeEnvironment(level, chunk);
        return SettlementFieldPlot.supportedSite(plot.soil(), plot.crop()) || SettlementFieldPlot.supportedPasture(plot.soil(), plot.crop());
    }

    static void applyLoaded(ServerLevel level, SettlementFieldPlot plot) {
        var chunk = loaded(level, plot.position());
        if (chunk != null) {
            var soil = chunk.getBlockState(plot.position());
            var crop = chunk.getBlockState(plot.position().above());
            var top = chunk.getBlockState(plot.position().above(2));
            if (plot.reconcile(chunk)) {
                level.sendBlockUpdated(plot.position(), soil, plot.soil(), 2);
                level.sendBlockUpdated(plot.position().above(), crop, plot.crop(), 2);
                if (plot.position().getY() + 2 < level.getMaxBuildHeight() && top != plot.top()) {
                    level.sendBlockUpdated(plot.position().above(2), top, plot.top(), 2);
                }
            }
        }
    }

    static void releaseFruit(ServerLevel level, SettlementRecord town, SettlementFieldPlot fruit) {
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var stem = town.fields().get(fruit.position().relative(direction));
            if (stem != null && available(level, stem) && SettlementFarmPlants.attachedStem(stem.crop())
                    && stem.crop().getValue(net.minecraft.world.level.block.AttachedStemBlock.FACING) == direction.getOpposite()
                    && !fruit.crop().is(SettlementFarmPlants.fruitFor(stem.crop()).getBlock())) {
                stem.change(stem.soil(), SettlementFarmPlants.detachedFor(stem.crop()));
                applyLoaded(level, stem);
            }
        }
    }

    private static LevelChunk loaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
    }
}
