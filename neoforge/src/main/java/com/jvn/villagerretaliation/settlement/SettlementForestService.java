package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class SettlementForestService {
    private static final int SECTIONS_PER_SURVEY = 2;
    private static final Map<SettlementForest, Integer> CURSORS = new WeakHashMap<>();

    private SettlementForestService() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        reconcileChunk(event);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        reconcileChunk(event);
    }

    private static void reconcileChunk(ChunkEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location())) continue;
            for (var forest : town.forests().values()) {
                for (var section : List.copyOf(forest.sections().values())) {
                    if (section.min().getX() >> 4 == chunk.getPos().x && section.min().getZ() >> 4 == chunk.getPos().z) {
                        if (!reconcile(level, section, chunk) && !section.pending()) forest.remove(section.origin());
                        town.changed();
                    }
                }
            }
        }
    }

    static void observeArea(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk supplied) {
        if (area.type() != SettlementAreaType.FORESTRY) return;
        var forest = town.forests().get(area.id());
        if (forest == null || !forest.matches(area) && !forest.pending()) {
            forest = new SettlementForest(area);
            town.recordForest(forest);
        }
        if (!forest.matches(area)) return;
        var origins = new ArrayList<BlockPos>();
        for (int y = area.min().getY() >> 4; y <= area.max().getY() >> 4; y++) {
            for (int x = area.min().getX() >> 4; x <= area.max().getX() >> 4; x++) {
                for (int z = area.min().getZ() >> 4; z <= area.max().getZ() >> 4; z++) {
                    if (supplied == null || supplied.getPos().x == x && supplied.getPos().z == z) {
                        origins.add(new BlockPos(x << 4, y << 4, z << 4));
                    }
                }
            }
        }
        if (origins.isEmpty()) return;
        int cursor = supplied == null ? Math.floorMod(CURSORS.getOrDefault(forest, 0), origins.size()) : 0;
        int visited = 0;
        int observed = 0;
        while (visited < origins.size() && (supplied != null || observed < SECTIONS_PER_SURVEY)) {
            BlockPos origin = origins.get((cursor + visited++) % origins.size());
            var chunk = supplied != null ? supplied : level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
            if (chunk == null || origin.getY() < level.getMinBuildHeight() || origin.getY() >= level.getMaxBuildHeight()) continue;
            BlockPos min = new BlockPos(Math.max(origin.getX(), area.min().getX()), Math.max(origin.getY(), area.min().getY()),
                    Math.max(origin.getZ(), area.min().getZ()));
            BlockPos max = new BlockPos(Math.min(origin.getX() + 15, area.max().getX()), Math.min(origin.getY() + 15, area.max().getY()),
                    Math.min(origin.getZ() + 15, area.max().getZ()));
            var section = forest.sections().get(origin);
            if (section == null || !reconcile(level, section, chunk) && !section.pending()) {
                forest.record(SettlementForestSection.capture(area.id(), min, max, chunk));
            }
            observed++;
        }
        if (supplied == null) CURSORS.put(forest, (cursor + visited) % origins.size());
        if (observed > 0) town.changed();
    }

    static void update(ServerLevel level, SettlementRecord town) {
        for (var forest : List.copyOf(town.forests().values())) {
            refresh(level, forest);
            var area = town.areas().get(forest.area());
            if (!forest.pending() && (area == null || !forest.matches(area))) {
                town.removeForest(forest.area());
            }
        }
        if (!town.forests().isEmpty()) town.changed();
    }

    static void refresh(ServerLevel level, SettlementForest forest) {
        for (var section : List.copyOf(forest.sections().values())) {
            var chunk = level.getChunkSource().getChunkNow(section.min().getX() >> 4, section.min().getZ() >> 4);
            if (chunk != null && !reconcile(level, section, chunk) && !section.pending()) forest.remove(section.origin());
        }
    }

    static void applyLoaded(ServerLevel level, SettlementForest forest) {
        for (var section : forest.sections().values()) {
            if (!section.pending()) continue;
            var chunk = level.getChunkSource().getChunkNow(section.min().getX() >> 4, section.min().getZ() >> 4);
            if (chunk != null) reconcile(level, section, chunk);
        }
    }

    private static boolean reconcile(ServerLevel level, SettlementForestSection section, LevelChunk chunk) {
        var previous = new java.util.LinkedHashMap<BlockPos, BlockState>();
        if (section.pending() && level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) == chunk) {
            for (BlockPos position : BlockPos.betweenClosed(section.min(), section.max())) {
                if (chunk.getBlockState(position) != section.state(position)) {
                    previous.put(position.immutable(), chunk.getBlockState(position));
                }
            }
        }
        if (!section.reconcile(chunk)) return false;
        previous.forEach((position, state) -> {
            var updated = chunk.getBlockState(position);
            if (state != updated) {
                level.sendBlockUpdated(position, state, updated, 2);
                com.jvn.villagerretaliation.interaction.work.HiredPathMemory.onBlockChanged(level, position);
            }
        });
        return true;
    }
}
