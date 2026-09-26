package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class SettlementBuildingService {
    private static final int SURVEYS_PER_SECOND = 4;

    private SettlementBuildingService() {
    }

    public static boolean register(ServerPlayer player, VillageAllegianceId id, SettlementArea area) {
        ServerLevel level = player.serverLevel();
        var data = SettlementSavedData.get(level);
        var town = data.get(id).orElse(null);
        if (town == null || !player.isAlive() || player.isSpectator()
                || !town.dimension().equals(level.dimension().location())
                || area.min().getY() < level.getMinBuildHeight() || area.max().getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(area.min()) || !level.getWorldBorder().isWithinBounds(area.max())
                || !data.putArea(VillageAllegianceRegistrySavedData.get(level), id, player.getUUID(), area)) {
            return false;
        }
        refresh(level, town, area);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 20L != 0L) {
            return;
        }
        var towns = SettlementSavedData.get(server.overworld()).settlements();
        for (ServerLevel level : server.getAllLevels()) {
            List<Site> sites = new ArrayList<>();
            for (SettlementRecord town : towns) {
                if (town.dimension().equals(level.dimension().location())) {
                    town.areas().values().stream().filter(SettlementArea::enabled)
                            .forEach(area -> sites.add(new Site(town, area)));
                }
            }
            if (sites.isEmpty()) {
                continue;
            }
            int start = (int) Math.floorMod(level.getGameTime() / 20L * SURVEYS_PER_SECOND, sites.size());
            for (int index = 0; index < Math.min(SURVEYS_PER_SECOND, sites.size()); index++) {
                Site site = sites.get((start + index) % sites.size());
                refresh(level, site.town(), site.area());
            }
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        refreshChunk(event);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        refreshChunk(event);
    }

    private static void refreshChunk(ChunkEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        var position = chunk.getPos();
        for (SettlementRecord town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location())) {
                continue;
            }
            for (SettlementArea area : town.areas().values()) {
                if (area.min().getX() <= position.getMaxBlockX() + 2 && area.max().getX() >= position.getMinBlockX() - 2
                        && area.min().getZ() <= position.getMaxBlockZ() + 2 && area.max().getZ() >= position.getMinBlockZ() - 2) {
                    refresh(level, town, area, chunk);
                }
            }
        }
    }

    public static void refresh(ServerLevel level, SettlementRecord town, SettlementArea area) {
        refresh(level, town, area, null);
    }

    private static void refresh(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk supplied) {
        var observed = SettlementBuildingSurvey.inspect(level, area, supplied);
        var previous = town.surveys().get(area.id());
        if (previous != null && !observed.fullyLoaded()) {
            List<BlockPos> beds = new ArrayList<>(observed.beds());
            previous.beds().stream().filter(pos -> SettlementBuildingSurvey.inspectBed(level, area, pos, supplied)
                    == SettlementBuildingSurvey.BedState.UNOBSERVED).forEach(beds::add);
            List<BlockPos> stations = new ArrayList<>(observed.workstations());
            previous.workstations().stream().filter(pos -> !SettlementBuildingSurvey.observed(level, pos, supplied)).forEach(stations::add);
            var fixtures = new java.util.LinkedHashMap<>(observed.fixtures());
            previous.fixtures().forEach((pos, type) -> {
                if (!SettlementBuildingSurvey.observed(level, pos, supplied)) {
                    fixtures.put(pos, type);
                }
            });
            List<BlockPos> water = new ArrayList<>(observed.waterSources());
            previous.waterSources().stream().filter(pos -> !SettlementBuildingSurvey.observed(level, pos, supplied)).forEach(water::add);
            observed = new SettlementBuildingSurvey.Survey(observed.checkedAt(), beds, stations, previous.fullyLoaded(), fixtures, water);
        }
        town.recordSurvey(area.id(), observed);
    }

    private record Site(SettlementRecord town, SettlementArea area) {
    }
}
