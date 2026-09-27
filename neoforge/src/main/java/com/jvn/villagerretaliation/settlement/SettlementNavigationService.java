package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class SettlementNavigationService {
    private SettlementNavigationService() {
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        var positions = wanted(level, town, null);
        town.retainNavigation(positions);
        var wanted = new ArrayList<>(positions);
        if (wanted.isEmpty()) return;
        int start = (int) Math.floorMod(now / 200L * 8L, wanted.size());
        int captured = 0;
        for (int offset = 0; offset < wanted.size() && captured < 8; offset++) {
            var origin = wanted.get((start + offset) % wanted.size());
            var chunk = level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
            if (chunk != null) {
                town.recordNavigation(SettlementNavigationSection.capture(origin, chunk));
                captured++;
            }
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        observeChunk(event);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        observeChunk(event);
    }

    private static void observeChunk(ChunkEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (town.dimension().equals(level.dimension().location())) observe(level, town, chunk);
        }
    }

    static void observe(ServerLevel level, SettlementRecord town, LevelChunk chunk) {
        int radius = town.stage().radius() + 16;
        var position = chunk.getPos();
        if (position.getMaxBlockX() < town.center().getX() - radius || position.getMinBlockX() > town.center().getX() + radius
                || position.getMaxBlockZ() < town.center().getZ() - radius || position.getMinBlockZ() > town.center().getZ() + radius) return;
        for (var origin : wanted(level, town, position)) town.recordNavigation(SettlementNavigationSection.capture(origin, chunk));
    }

    private static Set<BlockPos> wanted(ServerLevel level, SettlementRecord town, net.minecraft.world.level.ChunkPos focus) {
        var anchors = new LinkedHashSet<BlockPos>();
        town.residents().values().stream().filter(SettlementResidentState::present).forEach(resident -> anchors.add(resident.position()));
        town.areas().values().stream().filter(area -> area.enabled() && area.type() == SettlementAreaType.HOME).forEach(area -> {
            var survey = town.surveys().get(area.id());
            if (survey != null) anchors.addAll(survey.beds());
        });
        var result = new java.util.TreeSet<BlockPos>();
        int radius = town.stage().radius() + 16;
        for (var anchor : anchors) {
            int minX = Math.max(anchor.getX() - 48, town.center().getX() - radius) >> 4;
            int maxX = Math.min(anchor.getX() + 48, town.center().getX() + radius) >> 4;
            int minZ = Math.max(anchor.getZ() - 48, town.center().getZ() - radius) >> 4;
            int maxZ = Math.min(anchor.getZ() + 48, town.center().getZ() + radius) >> 4;
            if (focus != null) {
                minX = Math.max(minX, focus.x);
                maxX = Math.min(maxX, focus.x);
                minZ = Math.max(minZ, focus.z);
                maxZ = Math.min(maxZ, focus.z);
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = Math.max(level.getMinBuildHeight(), anchor.getY() - 16) >> 4;
                         y <= Math.min(level.getMaxBuildHeight() - 1, anchor.getY() + 16) >> 4; y++) {
                        result.add(new BlockPos(x << 4, y << 4, z << 4));
                    }
                }
            }
        }
        return result;
    }
}
