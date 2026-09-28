package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class SettlementSimulationService {
    static final int MAX_STEPS_PER_TICK = 32;
    private static final long BUDGET_NANOS = 3_000_000L;
    private final Map<VillageAllegianceId, Entry> entries = new LinkedHashMap<>();
    private List<Entry> scheduled = List.of();
    private long refreshAt;
    private long day = -1L;
    private int limit = -1;
    private int cursor;
    private long completedCycles;
    private List<Runnable> arrivals = List.of();
    private int arrivalCursor;

    SettlementSimulationService() {
    }

    void invalidate() {
        this.entries.clear();
        this.scheduled = List.of();
        this.arrivals = List.of();
        this.refreshAt = 0L;
    }

    public static void tick(MinecraftServer server) {
        SettlementMineReactions.tick(server);
        SettlementTerrainDrops.tick(server);
        var simulation = SettlementSavedData.get(server.overworld()).simulation;
        simulation.update(server);
    }

    static List<SettlementRecord> select(List<SettlementRecord> towns, long day, int limit) {
        if (limit <= 0 || towns.isEmpty()) return List.of();
        var ordered = towns.stream().sorted(Comparator.comparing(SettlementRecord::id)).toList();
        int count = Math.min(limit, ordered.size());
        int start = (int) Math.floorMod(Math.floorMod(day, ordered.size()) * count, ordered.size());
        List<SettlementRecord> selected = new ArrayList<>(count);
        for (int index = 0; index < count; index++) selected.add(ordered.get((start + index) % ordered.size()));
        return selected;
    }

    private void update(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        long currentDay = Math.floorDiv(now, 24_000L);
        int cap = Math.clamp(VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.get(), 0, 10000);
        if (now >= this.refreshAt || currentDay != this.day || cap != this.limit) refresh(server, now, currentDay, cap);
        for (int index = 0; index < Math.min(4, this.arrivals.size()); index++) {
            this.arrivals.get(Math.floorMod(this.arrivalCursor++, this.arrivals.size())).run();
        }
        if (this.scheduled.isEmpty()) return;
        long started = System.nanoTime();
        int idle = 0;
        for (int steps = 0; steps < MAX_STEPS_PER_TICK && idle < this.scheduled.size();) {
            var entry = this.scheduled.get(Math.floorMod(this.cursor++, this.scheduled.size()));
            if (entry.nextAt > now) {
                idle++;
                continue;
            }
            idle = 0;
            if (entry.step()) this.completedCycles++;
            steps++;
            if (System.nanoTime() - started >= BUDGET_NANOS) break;
        }
    }

    private void refresh(MinecraftServer server, long now, long currentDay, int cap) {
        var towns = SettlementSavedData.get(server.overworld()).settlements().stream().filter(town -> town.territory().active()).toList();
        var reconciliation = new ArrayList<Runnable>();
        for (var town : towns) {
            if (town.arrivals().isEmpty()) continue;
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, town.dimension()));
            if (level != null) {
                for (var id : town.arrivals().keySet()) reconciliation.add(() -> SettlementArrivalService.materialize(level, town, id));
            }
        }
        this.arrivals = List.copyOf(reconciliation);
        this.arrivalCursor = 0;
        var selected = select(towns, currentDay, cap);
        var retained = new java.util.HashSet<VillageAllegianceId>();
        int offset = 0;
        for (var town : selected) {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, town.dimension()));
            if (level == null) continue;
            retained.add(town.id());
            if (!this.entries.containsKey(town.id())) {
                town.resumeSimulation(now);
                this.entries.put(town.id(), new Entry(level, town, now + offset * 200L / selected.size()));
            }
            offset++;
        }
        this.entries.keySet().retainAll(retained);
        this.scheduled = List.copyOf(this.entries.values());
        this.cursor = 0;
        this.refreshAt = now + 200L;
        this.day = currentDay;
        this.limit = cap;
    }

    public static long completedCycles(ServerLevel level) {
        return SettlementSavedData.get(level).simulation.completedCycles;
    }

    static final class Entry {
        private final ServerLevel level;
        private final SettlementRecord town;
        private long nextAt;
        private List<Runnable> work = List.of();
        private int index;

        Entry(ServerLevel level, SettlementRecord town, long nextAt) {
            this.level = level;
            this.town = town;
            this.nextAt = nextAt;
        }

        boolean step() {
            if (this.work.isEmpty()) {
                long cycleAt = this.nextAt;
                var jobs = new ArrayList<Runnable>();
                SettlementCareService.schedule(this.level, this.town, cycleAt, jobs);
                jobs.add(() -> SettlementDepartureService.process(this.level, this.town, cycleAt));
                SettlementBackgroundService.schedule(this.level, this.town, cycleAt, jobs);
                jobs.add(() -> SettlementAutonomyService.update(this.level, this.town));
                jobs.add(() -> SettlementProgressionService.update(this.level, this.town, cycleAt));
                jobs.add(() -> SettlementPopulationService.update(this.level, this.town, cycleAt));
                jobs.add(() -> SettlementTradeService.process(this.level, this.town, cycleAt));
                this.work = jobs;
            }
            SettlementSimulationClock.run(this.town, this.work.get(this.index++));
            if (this.index < this.work.size()) return false;
            this.work = List.of();
            this.index = 0;
            // advance by the consumed interval so a busy queue never discards simulation time
            this.nextAt += 200L;
            return true;
        }
    }
}
