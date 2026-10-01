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
    static final int MAX_STEPS_PER_TICK = 64;
    private static final long BUDGET_NANOS = 3_000_000L;
    private final Map<VillageAllegianceId, Entry> entries = new LinkedHashMap<>();
    private List<Entry> scheduled = List.of();
    private long refreshAt;
    private long day = -1L;
    private int limit = -1;
    private int cursor;
    private long completedCycles;
    private final java.util.ArrayDeque<Pending> pending = new java.util.ArrayDeque<>();
    private final java.util.Set<Object> queued = new java.util.HashSet<>();
    private final Map<java.util.UUID, Management> management = new java.util.HashMap<>();
    private boolean auxiliaryTurn;
    private Runnable selection;
    private int selectionTurn;
    private int lastSteps;
    private long lastNanos;
    private long largestStepNanos;
    private long overruns;
    private long largestSynchronousNanos;

    SettlementSimulationService() {
    }

    void invalidate() {
        this.entries.clear();
        this.management.clear();
        this.scheduled = List.of();
        this.selection = null;
        this.pending.clear();
        this.queued.clear();
        this.refreshAt = 0L;
    }

    public static void tick(MinecraftServer server) {
        SettlementMineReactions.tick(server);
        SettlementTerrainDrops.tick(server);
        var data = SettlementSavedData.get(server.overworld());
        if (!data.readOnly()) data.simulation.update(server);
    }

    static String suspension(ServerLevel level, SettlementRecord town) {
        if (!town.territory().active()) return "simulation_suspended";
        int cap = VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.get();
        if (foreground(level, town)) return backlog(level, town);
        if (cap <= 0) return "simulation_disabled";
        var towns = SettlementSavedData.get(level).settlements().stream().filter(value -> value.territory().active())
                .filter(value -> {
                    var dimension = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, value.dimension()));
                    return dimension != null && !foreground(dimension, value);
                }).toList();
        return select(towns, Math.floorDiv(level.getServer().overworld().getGameTime(), 24_000L), cap).stream().anyMatch(value -> value.id().equals(town.id()))
                ? backlog(level, town) : "simulation_capped";
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

    static void managed(net.minecraft.server.level.ServerPlayer player, SettlementRecord town) {
        var service = SettlementSavedData.get(player.serverLevel()).simulation;
        service.management.put(player.getUUID(), new Management(town.id(), player.serverLevel().getGameTime() + 400L));
    }

    static boolean foreground(ServerLevel level, SettlementRecord town) {
        var service = SettlementSavedData.get(level).simulation;
        boolean managed = service.management.entrySet().stream().anyMatch(entry -> {
            var player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            return entry.getValue().town().equals(town.id()) && entry.getValue().until() > level.getGameTime()
                    && player != null && player.serverLevel() == level && town.permits(player.getUUID(), SettlementPermission.VIEW);
        });
        return managed || SettlementViewService.active(town) || town.territory().plots().stream()
                .anyMatch(position -> level.getChunkSource().isPositionTicking(position));
    }

    private static String backlog(ServerLevel level, SettlementRecord town) {
        var entry = SettlementSavedData.get(level).simulation.entries.get(town.id());
        return entry != null && level.getGameTime() - entry.nextAt > 400L ? "simulation_queued" : "";
    }

    void enqueue(Object key, Runnable work) {
        if (this.queued.add(key)) this.pending.addLast(new Pending(key, work));
    }

    boolean runPending() {
        if (this.pending.isEmpty()) return false;
        var job = this.pending.removeFirst();
        job.work().run();
        if (SettlementWork.complete(job.work())) this.queued.remove(job.key());
        else this.pending.addLast(job);
        return true;
    }

    private void update(MinecraftServer server) {
        long started = System.nanoTime();
        long now = server.overworld().getGameTime();
        long currentDay = Math.floorDiv(now, 24_000L);
        int cap = Math.clamp(VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.get(), 0, 10000);
        if (now >= this.refreshAt || currentDay != this.day || cap != this.limit) refresh(server, now, currentDay, cap);
        this.lastSteps = 0;
        int idle = 0;
        while (this.lastSteps < MAX_STEPS_PER_TICK && idle < this.scheduled.size() + 2
                && System.nanoTime() - started < BUDGET_NANOS) {
            this.auxiliaryTurn = !this.auxiliaryTurn;
            long stepStarted = System.nanoTime();
            if (this.selection != null && (this.selectionTurn++ % 4 == 0 || this.pending.isEmpty() && this.scheduled.isEmpty())) {
                this.selection.run();
                if (SettlementWork.complete(this.selection)) this.selection = null;
            } else if (!this.pending.isEmpty() && (this.auxiliaryTurn || this.scheduled.isEmpty())) {
                runPending();
            } else if (!this.scheduled.isEmpty()) {
                var entry = this.scheduled.get(Math.floorMod(this.cursor++, this.scheduled.size()));
                if (entry.nextAt > now) {
                    idle++;
                    continue;
                }
                if (entry.step()) this.completedCycles++;
            } else {
                idle++;
                continue;
            }
            idle = 0;
            this.lastSteps++;
            long elapsed = System.nanoTime() - stepStarted;
            this.largestStepNanos = Math.max(this.largestStepNanos, elapsed);
            if (elapsed > BUDGET_NANOS) this.overruns++;
        }
        this.lastNanos = System.nanoTime() - started;
    }

    private void refresh(MinecraftServer server, long now, long currentDay, int cap) {
        this.management.entrySet().removeIf(entry -> entry.getValue().until() <= now
                || server.getPlayerList().getPlayer(entry.getKey()) == null);
        var towns = SettlementSavedData.get(server.overworld()).settlements();
        var foreground = new ArrayList<SettlementRecord>();
        var background = new ArrayList<SettlementRecord>();
        var jobs = new ArrayList<Runnable>();
        jobs.add(SettlementWork.each(towns, town -> {
            if (!town.territory().active()) return;
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, town.dimension()));
            if (level == null) return;
            (foreground(level, town) ? foreground : background).add(town);
            for (var id : town.arrivals().keySet()) arrival(level, town, id);
        }));
        jobs.add(() -> {
            var selected = new ArrayList<>(foreground);
            selected.addAll(select(background, currentDay, cap));
            var retained = new java.util.HashSet<VillageAllegianceId>();
            int offset = 0;
            for (var town : selected) {
                var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, town.dimension()));
                if (level == null || SettlementSavedData.get(level).get(town.id()).orElse(null) != town) continue;
                retained.add(town.id());
                if (!this.entries.containsKey(town.id())) {
                    town.resumeSimulation(now);
                    this.entries.put(town.id(), new Entry(level, town, now + offset * 200L / selected.size()));
                }
                offset++;
            }
            this.entries.keySet().retainAll(retained);
            SettlementAllocationService.retain(server.overworld(), towns.stream().map(SettlementRecord::id).collect(java.util.stream.Collectors.toSet()));
            this.scheduled = List.copyOf(this.entries.values());
        });
        if (this.selection == null) this.selection = new SettlementWork(jobs);
        this.refreshAt = now + 200L;
        this.day = currentDay;
        this.limit = cap;
    }

    public static Metrics metrics(ServerLevel level) {
        var service = SettlementSavedData.get(level).simulation;
        return new Metrics(service.scheduled.size(), service.pending.size() + (service.selection == null ? 0 : 1), service.lastSteps, service.lastNanos,
                service.largestStepNanos, service.largestSynchronousNanos, service.overruns, service.completedCycles);
    }

    static void synchronous(ServerLevel level, Runnable work) {
        long started = System.nanoTime();
        try {
            work.run();
        } finally {
            var service = SettlementSavedData.get(level).simulation;
            service.largestSynchronousNanos = Math.max(service.largestSynchronousNanos, System.nanoTime() - started);
        }
    }

    public record Metrics(int towns, int queued, int steps, long tickNanos, long largestStepNanos,
                          long largestSynchronousNanos, long overruns, long cycles) {
    }

    private record Management(VillageAllegianceId town, long until) {
    }

    private record Pending(Object key, Runnable work) {
    }

    void arrival(ServerLevel level, SettlementRecord town, java.util.UUID id) {
        enqueue(new ArrivalKey(town.id(), id), SettlementWork.repeat(() ->
                SettlementSavedData.get(level).get(town.id()).orElse(null) != town || !town.arrivals().containsKey(id)
                        || SettlementArrivalService.materialize(level, town, id)));
    }

    private record ArrivalKey(VillageAllegianceId town, java.util.UUID resident) {
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
                SettlementWorkerPoolService.schedule(this.level, this.town, cycleAt, jobs);
                jobs.add(() -> SettlementProgressionService.update(this.level, this.town, cycleAt));
                SettlementPopulationService.schedule(this.level, this.town, cycleAt, jobs);
                SettlementTradeService.schedule(this.level, this.town, cycleAt, jobs);
                jobs.add(() -> SettlementNotifications.update(this.level, this.town, cycleAt));
                this.work = jobs;
            }
            var job = this.work.get(this.index);
            // receipt-backed mutations are indivisible, the budget is checked between safe work units
            SettlementSimulationClock.run(this.town, job);
            if (!SettlementWork.complete(job)) return false;
            this.index++;
            if (this.index < this.work.size()) return false;
            this.work = List.of();
            this.index = 0;
            // advance by the consumed interval so a busy queue never discards simulation time
            this.nextAt += 200L;
            return true;
        }
    }
}
