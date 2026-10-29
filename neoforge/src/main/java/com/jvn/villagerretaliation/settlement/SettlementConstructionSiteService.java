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
    private static final Map<SettlementConstructionSite, Integer> REFRESH_CURSORS = new WeakHashMap<>();

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
            if (project.wallConstruction() != null) {
                for (var origin : project.wallConstruction().surveyOrigins()) {
                    if (supplied == null || supplied.getPos().x == origin.getX() >> 4 && supplied.getPos().z == origin.getZ() >> 4) origins.add(origin);
                }
            } else for (int y = site.min().getY() >> 4; y <= site.max().getY() >> 4; y++) {
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
            while (visited < origins.size() && (project.wallConstruction() == null || visited < 32)
                    && (project.wallConstruction() == null && supplied != null || captured < 2)) {
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
            if (!project.active() && !project.site().unsettled()) town.clearProjectSite(project);
            town.changed();
        }
    }

    static void refresh(ServerLevel level, SettlementConstructionSite site) {
        var refreshed = new java.util.HashSet<BlockPos>();
        for (var placement : site.awaitingCheckpoint().values().stream().limit(32).toList()) {
            if (placement.reconcile(level, site.project()) && placement.durable(level)) site.checkpointed(placement.id());
        }
        for (var placement : site.placements().values().stream().limit(32).toList()) {
            if (!placement.reconcile(level, site.project())) break;
            for (var part : placement.parts()) refreshed.add(SettlementTerrainSection.sectionOrigin(part.position()));
            site.applied(placement.id());
            if (placement.durable(level)) site.checkpointed(placement.id());
        }
        var sections = List.copyOf(site.sections().values());
        if (site.area().wall() && !sections.isEmpty()) {
            int start = Math.floorMod(REFRESH_CURSORS.getOrDefault(site, 0), sections.size());
            for (int index = 0; index < Math.min(2, sections.size()); index++) refreshed.add(sections.get((start + index) % sections.size()).origin());
            REFRESH_CURSORS.put(site, (start + 2) % sections.size());
            sections = refreshed.stream().map(site.sections()::get).filter(java.util.Objects::nonNull).toList();
        }
        for (var section : sections) {
            var chunk = level.getChunkSource().getChunkNow(section.min().getX() >> 4, section.min().getZ() >> 4);
            if (chunk != null) site.record(SettlementTerrainSection.capture("ConstructionSurvey", site.project(), section.min(), section.max(), chunk));
        }
    }
}
