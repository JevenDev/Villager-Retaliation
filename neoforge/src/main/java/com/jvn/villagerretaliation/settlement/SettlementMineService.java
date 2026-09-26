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

public final class SettlementMineService {
    private static final int SECTIONS_PER_SURVEY = 2;
    private static final Map<SettlementMine, Integer> CURSORS = new WeakHashMap<>();

    private SettlementMineService() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        reconcileChunk(event);
        if (event.getLevel() instanceof ServerLevel && event.getChunk() instanceof LevelChunk chunk) SettlementMineReactions.track(chunk);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        reconcileChunk(event);
        if (event.getChunk() instanceof LevelChunk chunk) SettlementMineReactions.forget(chunk);
    }

    private static void reconcileChunk(ChunkEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location())) continue;
            for (var mine : town.mines().values()) {
                for (var section : List.copyOf(mine.sections().values())) {
                    if (section.min().getX() >> 4 == chunk.getPos().x && section.min().getZ() >> 4 == chunk.getPos().z) {
                        if (!reconcile(level, section, chunk) && !section.pending()) mine.remove(section.origin());
                        town.changed();
                    }
                }
            }
        }
    }

    static void observeArea(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk supplied) {
        if (area.type() != SettlementAreaType.MINE) return;
        var mine = town.mines().get(area.id());
        if (mine == null || !mine.matches(area) && !mine.pending()) {
            mine = new SettlementMine(area);
            town.recordMine(mine);
        }
        if (!mine.matches(area)) return;
        BlockPos boundsMin = mine.observedMin();
        BlockPos boundsMax = mine.observedMax();
        var origins = new ArrayList<BlockPos>();
        for (int y = boundsMin.getY() >> 4; y <= boundsMax.getY() >> 4; y++) {
            for (int x = boundsMin.getX() >> 4; x <= boundsMax.getX() >> 4; x++) {
                for (int z = boundsMin.getZ() >> 4; z <= boundsMax.getZ() >> 4; z++) {
                    if (supplied == null || supplied.getPos().x == x && supplied.getPos().z == z) {
                        origins.add(new BlockPos(x << 4, y << 4, z << 4));
                    }
                }
            }
        }
        if (origins.isEmpty()) return;
        int cursor = supplied == null ? Math.floorMod(CURSORS.getOrDefault(mine, 0), origins.size()) : 0;
        int visited = 0;
        int observed = 0;
        while (visited < origins.size() && (supplied != null || observed < SECTIONS_PER_SURVEY)) {
            BlockPos origin = origins.get((cursor + visited++) % origins.size());
            var chunk = supplied != null ? supplied : level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
            if (chunk == null || origin.getY() < level.getMinBuildHeight() || origin.getY() >= level.getMaxBuildHeight()) continue;
            BlockPos min = new BlockPos(Math.max(origin.getX(), boundsMin.getX()), Math.max(origin.getY(), boundsMin.getY()),
                    Math.max(origin.getZ(), boundsMin.getZ()));
            BlockPos max = new BlockPos(Math.min(origin.getX() + 15, boundsMax.getX()), Math.min(origin.getY() + 15, boundsMax.getY()),
                    Math.min(origin.getZ() + 15, boundsMax.getZ()));
            var section = mine.sections().get(origin);
            if (section == null || !reconcile(level, section, chunk) && !section.pending()) {
                mine.record(SettlementTerrainSection.capture("Mine", area.id(), min, max, chunk));
            }
            observed++;
        }
        if (supplied == null) CURSORS.put(mine, (cursor + visited) % origins.size());
        if (observed > 0) {
            town.changed();
        }
    }

    static void update(ServerLevel level, SettlementRecord town) {
        for (var mine : List.copyOf(town.mines().values())) {
            refresh(level, mine);
            var area = town.areas().get(mine.area());
            if (!mine.pending() && (area == null || !mine.matches(area))) {
                town.removeMine(mine.area());
            }
        }
        if (!town.mines().isEmpty()) town.changed();
    }

    static void refresh(ServerLevel level, SettlementMine mine) {
        for (var section : List.copyOf(mine.sections().values())) {
            var chunk = level.getChunkSource().getChunkNow(section.min().getX() >> 4, section.min().getZ() >> 4);
            if (chunk != null) {
                if (!reconcile(level, section, chunk) && !section.pending()) mine.remove(section.origin());
            }
        }
    }

    private static boolean reconcile(ServerLevel level, SettlementTerrainSection section, LevelChunk chunk) {
        var previous = new java.util.LinkedHashMap<BlockPos, BlockState>();
        if (section.pending() && level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) == chunk) {
            for (BlockPos position : BlockPos.betweenClosed(section.min(), section.max())) {
                if (chunk.getBlockState(position) != section.state(position)) {
                    previous.put(position.immutable(), chunk.getBlockState(position));
                }
            }
        }
        if (!section.reconcile(chunk, (position, before) -> SettlementMineReactions.queue(chunk, position, before, chunk.getBlockState(position)))) return false;
        previous.forEach((position, state) -> {
            var updated = chunk.getBlockState(position);
            if (state != updated) {
                level.sendBlockUpdated(position, state, updated, 2);
                com.jvn.villagerretaliation.interaction.work.HiredPathMemory.onBlockChanged(level, position);
                com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker.onBlockBroken(level, position);
            }
        });
        return true;
    }
}
