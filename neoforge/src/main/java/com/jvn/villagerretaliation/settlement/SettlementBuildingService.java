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
    private SettlementBuildingService() {
    }

    public static boolean register(ServerPlayer player, VillageAllegianceId id, SettlementArea area) {
        ServerLevel level = player.serverLevel();
        var data = SettlementSavedData.get(level);
        var town = data.get(id).orElse(null);
        var previous = town == null ? null : town.areas().get(area.id());
        if (previous != null && previous.farm() != null && area.type() == SettlementAreaType.FARM
                && area.min().equals(previous.min()) && area.max().equals(previous.max()))
            area = new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(),
                    area.priority(), area.enabled(), area.crops(), false, previous.farm());
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
        if (server.overworld().getGameTime() % 20L != 0L) return;
        var data = SettlementSavedData.get(server.overworld());
        data.simulation.enqueue("buildings", SettlementWork.each(data.settlements(), town -> {
            var level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, town.dimension()));
            if (level != null && (town.autonomous()==null || AutonomousVillageService.loaded(level,town))) {
                SettlementConstructionService.tickQueue(level, town);
                for (var area : town.workplaces()) {
                    if (area.enabled()) enqueue(level, town, area);
                }
            }
        }));
    }

    static void enqueue(ServerLevel level, SettlementRecord town, SettlementArea area) {
        SettlementSavedData.get(level).simulation.enqueueLazy(new Site(town, area), () -> work(level, town, area));
    }

    static Runnable work(ServerLevel level, SettlementRecord town, SettlementArea area) {
        var jobs = new ArrayList<Runnable>();
        var slices = new ArrayList<SettlementArea>();
        for (int x = area.min().getX(); x <= area.max().getX(); x += 16) {
            for (int y = area.min().getY(); y <= area.max().getY(); y += 16) {
                for (int z = area.min().getZ(); z <= area.max().getZ(); z += 16) {
                    slices.add(new SettlementArea(area.id(), area.type(), area.name(), new BlockPos(x, y, z),
                            new BlockPos(Math.min(x + 15, area.max().getX()), Math.min(y + 15, area.max().getY()),
                                    Math.min(z + 15, area.max().getZ())), area.priority(), area.enabled()));
                }
            }
        }
        if (area.type().usesFields()) jobs.add(SettlementWork.each(slices, slice -> SettlementFieldService.observeArea(level, town, slice, null)));
        if (area.type() == SettlementAreaType.FISHERY) jobs.add(() -> SettlementFisheryService.observeArea(level, town, area, null));
        if (area.type() == SettlementAreaType.FORESTRY) jobs.add(() -> SettlementForestService.observeArea(level, town, area, null));
        if (area.type() == SettlementAreaType.MINE) jobs.add(() -> SettlementMineService.observeArea(level, town, area, null));
        if (area.type() == SettlementAreaType.CONSTRUCTION) jobs.add(() -> SettlementConstructionSiteService.observeArea(level, town, area, null));
        if (area.type() == SettlementAreaType.ANIMAL_PEN) jobs.add(() -> SettlementLivestockService.observeArea(level, town, area));
        if (area.type() == SettlementAreaType.HUNTING_GROUNDS) jobs.add(() -> SettlementHuntingTargetService.observeArea(level, town, area));
        if (area.type().role() != null) jobs.add(() -> SettlementGroundItemService.observeArea(level, town, area));
        jobs.add(() -> SettlementGroundExperienceService.observeArea(level, town, area));
        var results = new ArrayList<SettlementBuildingSurvey.Survey>();
        long requestedAt = level.getGameTime();
        var baseline = town.surveys().get(area.id());
        jobs.add(SettlementWork.lazy(() -> {
            var blocks = new SettlementBuildingSurvey.ObservedBlocks(level, null, town);
            return SettlementWork.each(slices, slice -> results.add(SettlementBuildingSurvey.inspect(blocks, area, slice)));
        }));
        jobs.add(() -> {
            var previous = town.surveys().get(area.id());
            if (previous != baseline) return;
            var beds = new ArrayList<BlockPos>();
            var stations = new ArrayList<BlockPos>();
            var water = new ArrayList<BlockPos>();
            var fixtures = new java.util.LinkedHashMap<BlockPos, net.minecraft.resources.ResourceLocation>();
            var availability = new java.util.LinkedHashMap<BlockPos, Boolean>();
            boolean loaded = true;
            for (var result : results) {
                beds.addAll(result.beds());
                stations.addAll(result.workstations());
                water.addAll(result.waterSources());
                fixtures.putAll(result.fixtures());
                availability.putAll(result.bedAvailability());
                loaded &= result.fullyLoaded();
            }
            publish(level, town, area, new SettlementBuildingSurvey.ObservedBlocks(level, null, town),
                    new SettlementBuildingSurvey.Survey(requestedAt, beds, stations, loaded, fixtures, water, availability));
        });
        // unfinished scans never publish a partial survey or survive an area replacement
        return SettlementWork.guard(new SettlementWork(jobs),
                () -> SettlementSavedData.get(level).get(town.id()).orElse(null) == town && area.equals(town.workplace(area.id())));
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
            for (SettlementArea area : town.workplaces()) {
                int margin = area.type() == SettlementAreaType.FISHERY ? 11 : 2;
                if (area.min().getX() <= position.getMaxBlockX() + margin && area.max().getX() >= position.getMinBlockX() - margin
                        && area.min().getZ() <= position.getMaxBlockZ() + margin && area.max().getZ() >= position.getMinBlockZ() - margin) {
                    // supplied chunks are not safely queryable later, reconcile before native ticking or saving
                    refresh(level, town, area, chunk);
                }
            }
        }
    }

    public static void refresh(ServerLevel level, SettlementRecord town, SettlementArea area) {
        refresh(level, town, area, null);
    }

    private static void refresh(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk supplied) {
        SettlementSimulationService.synchronous(level, () -> observe(level, town, area, supplied));
    }

    private static void observe(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk supplied) {
        SettlementFieldService.observeArea(level, town, area, supplied);
        SettlementFisheryService.observeArea(level, town, area, supplied);
        SettlementForestService.observeArea(level, town, area, supplied);
        SettlementMineService.observeArea(level, town, area, supplied);
        SettlementConstructionSiteService.observeArea(level, town, area, supplied);
        SettlementLivestockService.observeArea(level, town, area);
        SettlementHuntingTargetService.observeArea(level, town, area);
        SettlementGroundItemService.observeArea(level, town, area);
        SettlementGroundExperienceService.observeArea(level, town, area);
        var blocks = new SettlementBuildingSurvey.ObservedBlocks(level, supplied, town);
        var observed = SettlementBuildingSurvey.inspect(blocks, area);
        publish(level, town, area, blocks, observed);
    }

    private static void publish(ServerLevel level, SettlementRecord town, SettlementArea area,
                                SettlementBuildingSurvey.ObservedBlocks blocks, SettlementBuildingSurvey.Survey observed) {
        var previous = town.surveys().get(area.id());
        if (previous != null && !observed.fullyLoaded()) {
            List<BlockPos> beds = new ArrayList<>(observed.beds());
            previous.beds().stream().filter(pos -> SettlementBuildingSurvey.inspectBed(blocks, area, pos)
                    == SettlementBuildingSurvey.BedState.UNOBSERVED).forEach(beds::add);
            List<BlockPos> stations = new ArrayList<>(observed.workstations());
            previous.workstations().stream().filter(pos -> !blocks.observed(pos) && !blocks.blocked(pos)).forEach(stations::add);
            var fixtures = new java.util.LinkedHashMap<>(observed.fixtures());
            previous.fixtures().forEach((pos, type) -> {
                if (!blocks.observed(pos) && !blocks.blocked(pos)) {
                    fixtures.put(pos, type);
                }
            });
            List<BlockPos> water = new ArrayList<>(observed.waterSources());
            previous.waterSources().stream().filter(pos -> !blocks.observed(pos) && !blocks.blocked(pos)).forEach(water::add);
            observed = new SettlementBuildingSurvey.Survey(observed.checkedAt(), beds, stations, previous.fullyLoaded(), fixtures, water, observed.bedAvailability());
        }
        if (previous != null) {
            var availability = new java.util.LinkedHashMap<>(observed.bedAvailability());
            for (var pos : observed.beds()) {
                if (availability.containsKey(pos)) continue;
                if (blocks.chunk(pos.getX() >> 4, pos.getZ() >> 4) != null) {
                    availability.put(pos, !level.getPoiManager().existsAtPosition(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME, pos)
                            || level.getPoiManager().getFreeTickets(pos) > 0);
                } else if (previous.bedAvailability().containsKey(pos)) {
                    availability.put(pos, previous.bedAvailability().get(pos));
                }
            }
            observed = new SettlementBuildingSurvey.Survey(observed.checkedAt(), observed.beds(), observed.workstations(),
                    observed.fullyLoaded(), observed.fixtures(), observed.waterSources(), availability);
        }
        town.recordSurvey(area.id(), observed);
    }

    private record Site(SettlementRecord town, SettlementArea area) {
    }
}
