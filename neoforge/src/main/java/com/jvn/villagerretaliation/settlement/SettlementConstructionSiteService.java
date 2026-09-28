package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

final class SettlementConstructionSiteService {
    private static final Map<SettlementConstructionSite, Integer> CURSORS = new WeakHashMap<>();

    private SettlementConstructionSiteService() {
    }

    static void observeArea(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk supplied) {
        if (area.type() != SettlementAreaType.CONSTRUCTION || !area.enabled() || !town.owns(area)) return;
        for (var project : town.projects().values()) {
            if (!project.active() || !project.area().equals(area.id())) continue;
            project.observeSite(area);
            var site = project.site();
            if (!site.matches(area)) continue;
            refresh(level, site);
            var origins = new ArrayList<BlockPos>();
            for (int y = site.min().getY() >> 4; y <= site.max().getY() >> 4; y++) {
                for (int x = site.min().getX() >> 4; x <= site.max().getX() >> 4; x++) {
                    for (int z = site.min().getZ() >> 4; z <= site.max().getZ() >> 4; z++) {
                        if (supplied == null || supplied.getPos().x == x && supplied.getPos().z == z) origins.add(new BlockPos(x << 4, y << 4, z << 4));
                    }
                }
            }
            if (origins.isEmpty()) continue;
            int start = Math.floorMod(CURSORS.getOrDefault(site, 0), origins.size());
            int visited = 0;
            int captured = 0;
            while (visited < origins.size() && (supplied != null || captured < 2)) {
                var origin = origins.get((start + visited++) % origins.size());
                var chunk = supplied != null ? supplied : level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
                if (chunk == null || origin.getY() < level.getMinBuildHeight() || origin.getY() >= level.getMaxBuildHeight()) continue;
                var min = new BlockPos(Math.max(origin.getX(), site.min().getX()), Math.max(origin.getY(), site.min().getY()), Math.max(origin.getZ(), site.min().getZ()));
                var max = new BlockPos(Math.min(origin.getX() + 15, site.max().getX()), Math.min(origin.getY() + 15, site.max().getY()), Math.min(origin.getZ() + 15, site.max().getZ()));
                site.record(SettlementTerrainSection.capture("ConstructionSurvey", project.id(), min, max, chunk));
                captured++;
            }
            CURSORS.put(site, (start + visited) % origins.size());
            if (captured > 0) town.changed();
        }
    }

    static void update(ServerLevel level, SettlementRecord town) {
        for (var project : town.projects().values()) {
            if (project.site() == null || !town.owns(project)) continue;
            refresh(level, project.site());
            if (!project.active() && !project.site().pending()) town.clearProjectSite(project);
            town.changed();
        }
    }

    static void refresh(ServerLevel level, SettlementConstructionSite site) {
        for (var placement : List.copyOf(site.placements().values()).stream().limit(32).toList()) {
            if (!placement.reconcile(level, site.project())) break;
            site.applied(placement.id());
        }
        for (var section : List.copyOf(site.sections().values())) {
            var chunk = level.getChunkSource().getChunkNow(section.min().getX() >> 4, section.min().getZ() >> 4);
            if (chunk != null) site.record(SettlementTerrainSection.capture("ConstructionSurvey", site.project(), section.min(), section.max(), chunk));
        }
    }
}
