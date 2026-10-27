package com.jvn.villagerretaliation.settlement;

import com.google.gson.GsonBuilder;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyMetrics;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("diplomacy_profile")
public final class SettlementDiplomacyProfile {
    private static final int MINUTES = Integer.getInteger("villagerretaliation.diplomacyProfileMinutes", 3);
    private static final int SAMPLES = MINUTES * 1200;
    private SettlementDiplomacyProfile() { }
    @GameTestGenerator public static Collection<TestFunction> profiles() {
        int population = Integer.getInteger("villagerretaliation.diplomacyProfile", 0);
        if (population == 0) return List.of();
        if (MINUTES < 1 || MINUTES > 5) throw new IllegalArgumentException("Profile windows need one to five minutes");
        if (population < 500 || population > 2000 || population % 10 != 0) throw new IllegalArgumentException("Diplomacy profile needs 500 to 2000 residents in groups of ten");
        return List.of(new TestFunction("diplomacy_profile", "diplomacy_profile_" + population, "diplomacy_profile:empty", SAMPLES * 3 + 8000, 0, true,
                helper -> new Run(helper, population).start()));
    }
    private enum Stage { WARM_BASELINE, BASELINE, WARM_CACHED, CACHED, GENERATION, DRAIN, DONE }
    private record Sample(long tickNanos, long allocationBytes, long contactsNanos, long aidNanos, long courierNanos, long routesNanos, long rootCheckpointNanos, long nativeCheckpointNanos, long gameTime, long gcMillis) { }
    private record Stats(int samples, double mean, double p50, double p95, double p99, double maximum) { }
    private static final class Run {
        final GameTestHelper helper;
        final int population;
        final Consumer<ServerTickEvent.Pre> before = this::before;
        final Consumer<ServerTickEvent.Post> after = this::after;
        final Map<Stage, List<Sample>> samples = new LinkedHashMap<>();
        final Map<String, Object> snapshots = new LinkedHashMap<>();
        final List<String> failures = new ArrayList<>();
        final com.sun.management.ThreadMXBean allocations = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        SettlementManyVillageProfileScenario fixture;
        SettlementDiplomacyProfileWorkload workload;
        Stage stage = Stage.WARM_BASELINE;
        long nextTick, started, bytes, gcBefore;
        jdk.jfr.Recording recording;
        final List<java.lang.management.GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        final long[] subsystems = new long[VillageDiplomacyMetrics.Part.values().length];
        int ticks, peakPhysical, peakActive, peakQueue, peakTickets, originalCap;
        long cachedStartProgress, cachedEndProgress, generatedBefore, submissionsBefore;
        long activeSamples, physicalSamples, measuredSamples;
        boolean cleaned;
        Run(GameTestHelper helper, int population) { this.helper = helper; this.population = population; }
        void start() {
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo test) { }
                @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { cleanup(); }
                @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { cleanup(); }
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { }
            });
            originalCap = VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.get();
            var player = helper.makeMockServerPlayerInLevel(); player.setInvulnerable(true);
            fixture = SettlementManyVillageProfileScenario.prepare(player, population / 10, true);
            VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.set(Math.min(100, population / 10));
            workload = new SettlementDiplomacyProfileWorkload(helper.getLevel(), fixture.towns());
            if (!allocations.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Allocation accounting is unavailable");
            allocations.setThreadAllocatedMemoryEnabled(true);
            if (Boolean.getBoolean("villagerretaliation.diplomacyProfileRecording")) {
                recording = new jdk.jfr.Recording();
                recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(20));
                recording.enable("jdk.ObjectAllocationSample").with("throttle", "100/s");
                recording.enable("jdk.GarbageCollection"); recording.enable("jdk.GCPhasePause");
                recording.setMaxSize(128 * 1024 * 1024L); recording.start();
            }
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, before);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, after);
        }
        void before(ServerTickEvent.Pre event) {
            if (cleaned || event.getServer() != helper.getLevel().getServer()) return;
            for (var part : VillageDiplomacyMetrics.Part.values()) subsystems[part.ordinal()] = VillageDiplomacyMetrics.total(event.getServer(), part);
            gcBefore = collectors.stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
            bytes = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()); started = System.nanoTime();
        }
        void after(ServerTickEvent.Post event) {
            if (cleaned || started == 0 || event.getServer() != helper.getLevel().getServer()) return;
            long duration = System.nanoTime() - started;
            long allocated = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
            var route = DiplomaticRouteService.metrics(event.getServer());
            var deltas = new long[subsystems.length];
            for (var part : VillageDiplomacyMetrics.Part.values()) deltas[part.ordinal()] = VillageDiplomacyMetrics.total(event.getServer(), part) - subsystems[part.ordinal()];
            ticks++;
            if (stage == Stage.BASELINE || stage == Stage.CACHED || stage == Stage.GENERATION) {
                samples.computeIfAbsent(stage, ignored -> new ArrayList<>()).add(new Sample(duration, allocated, deltas[0], deltas[1], deltas[2], route.tickNanos(), deltas[3], deltas[4],
                        helper.getLevel().getGameTime(), collectors.stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum() - gcBefore));
                if (fixture.towns().stream().mapToInt(town -> town.residents().size()).sum() != population) fail("Resident census changed during measurement");
            }
            var current = workload.snapshot();
            if (stage == Stage.CACHED || stage == Stage.GENERATION) {
                measuredSamples++; activeSamples += current.active(); physicalSamples += current.physical();
            }
            if (stage == Stage.WARM_CACHED || stage == Stage.CACHED || stage == Stage.GENERATION) workload.sustain(stage == Stage.GENERATION);
            peakPhysical = Math.max(peakPhysical, current.physical()); peakActive = Math.max(peakActive, current.active());
            peakQueue = Math.max(peakQueue, route.queued()); peakTickets = Math.max(peakTickets, route.tickets());
            if (current.physical() > 8 || current.active() > 32 || route.tickets() > 1) fail("A configured global concurrency cap was exceeded");
            if (ticks % 200 == 0) LogUtils.getLogger().info("Diplomacy profile {} residents {} tick {}, native {}, active {}, queue {}, tickets {}",
                    population, stage, ticks, fixture.tickingResidents(), current.active(), route.queued(), route.tickets());
            switch (stage) {
                case WARM_BASELINE -> {
                    if (ticks % 400 == 0 && fixture.tickingResidents() != 50)
                        LogUtils.getLogger().info("Profile missing residents {}", fixture.missingResidents());
                    if (fixture.observeAndRelease() && ticks >= 800 && fixture.ready()) change(Stage.BASELINE);
                    else if (ticks > 3000) throw new IllegalStateException("Many-village fixture failed to unload: " + fixture.tickingResidents() + "/" + fixture.unloadedVillages());
                }
                case BASELINE -> { if (ticks == SAMPLES) { workload.start(); change(Stage.WARM_CACHED); } }
                case WARM_CACHED -> {
                    if (ticks >= 400 && route.queued() == 0) { cachedStartProgress = current.progress(); change(Stage.CACHED); }
                    else if (ticks > 2400) throw new IllegalStateException("Cached preparation did not finish");
                }
                case CACHED -> {
                    if (ticks == SAMPLES) {
                        cachedEndProgress = current.progress(); generatedBefore = route.generatedDependencies(); submissionsBefore = route.generationSubmissions();
                        workload.generation(); change(Stage.GENERATION);
                    }
                }
                case GENERATION -> { if (ticks == SAMPLES) { workload.cancelRoutes(); change(Stage.DRAIN); } }
                case DRAIN -> {
                    if (route.tickets() == 0 && route.queued() == 0) {
                        if (route.generatedDependencies() <= generatedBefore || route.generationSubmissions() <= submissionsBefore) fail("New-generation workload did not generate dependency chunks");
                        if (cachedEndProgress <= cachedStartProgress) fail("Cached couriers made no progress");
                        if ((double) activeSamples / measuredSamples < 30 || (double) physicalSamples / measuredSamples < 7) fail("Sustained journey workload fell idle");
                        if (peakActive != 32 || peakPhysical != 8 || peakQueue < 8) fail("Required concurrent workload was not exercised");
                        report(route); cleanup(); stage = Stage.DONE;
                        if (failures.isEmpty()) helper.succeed();
                        else helper.testInfo.fail(new net.minecraft.gametest.framework.GameTestAssertException(String.join(" / ", failures)));
                    } else if (ticks > 1200) throw new IllegalStateException("Route preparation retained work after cancellation");
                }
                default -> { }
            }
            if (nextTick == 0) nextTick = System.nanoTime();
            long remaining;
            while ((remaining = nextTick - System.nanoTime()) > 0) java.util.concurrent.locks.LockSupport.parkNanos(remaining);
            nextTick = Math.max(nextTick + 50_000_000L, System.nanoTime() - 2_000_000_000L);
        }
        void change(Stage next) {
            snapshots.put(stage.name(), Map.of("couriers", workload.snapshot(), "routes", DiplomaticRouteService.metrics(helper.getLevel().getServer()),
                    "nativeResidents", fixture.tickingResidents(), "unloadedVillages", fixture.unloadedVillages()));
            stage = next; ticks = 0;
        }
        void fail(String reason) { if (!failures.contains(reason)) failures.add(reason); }
        void report(DiplomaticRouteService.Metrics routes) {
            var windows = new LinkedHashMap<String, Object>();
            for (var entry : samples.entrySet()) {
                var values = entry.getValue(); var window = new LinkedHashMap<String, Object>();
                window.put("tickMillis", stats(values.stream().mapToLong(Sample::tickNanos).toArray(), 1000000));
                window.put("allocatedBytesPerTick", stats(values.stream().mapToLong(Sample::allocationBytes).toArray(), 1));
                window.put("contactMillis", stats(values.stream().mapToLong(Sample::contactsNanos).toArray(), 1000000));
                window.put("aidMillis", stats(values.stream().mapToLong(Sample::aidNanos).toArray(), 1000000));
                window.put("courierMillis", stats(values.stream().mapToLong(Sample::courierNanos).toArray(), 1000000));
                window.put("routeMillis", stats(values.stream().mapToLong(Sample::routesNanos).toArray(), 1000000));
                window.put("rootCheckpointMillis", stats(values.stream().mapToLong(Sample::rootCheckpointNanos).toArray(), 1000000));
                window.put("nativeCheckpointMillis", stats(values.stream().mapToLong(Sample::nativeCheckpointNanos).toArray(), 1000000));
                window.put("ticksOver50Millis", values.stream().filter(value -> value.tickNanos > 50_000_000L).count());
                window.put("worstTicks", values.stream().sorted(java.util.Comparator.comparingLong(Sample::tickNanos).reversed()).limit(20).toList());
                window.put("diplomacyMillis", stats(values.stream().mapToLong(value -> value.contactsNanos + value.aidNanos + value.courierNanos + value.routesNanos).toArray(), 1000000));
                windows.put(entry.getKey().name(), window);
            }
            var baseline = stats(samples.get(Stage.BASELINE).stream().mapToLong(Sample::tickNanos).toArray(), 1000000);
            var cached = stats(samples.get(Stage.CACHED).stream().mapToLong(Sample::tickNanos).toArray(), 1000000);
            double delta = cached.p95 - baseline.p95;
            if (delta >= 1) fail("Cached diplomacy exceeds the 1 ms additional p95 tick gate: " + delta);
            var report = new LinkedHashMap<String, Object>();
            report.put("minutesPerStage", MINUTES);
            report.put("meanActiveJourneys", (double) activeSamples / measuredSamples); report.put("meanPhysicalCouriers", (double) physicalSamples / measuredSamples);
            report.put("population", population); report.put("villages", fixture.towns().size()); report.put("samplesPerStage", SAMPLES);
            report.put("measurement", "Paced server Pre to Post, excluding fixture probes and pacing. Test-only forced chunks keep five nearby villages ticking for the mock player. Other villages are verified unloaded. Same many-village fixture for baseline, 32 resident journeys with eight nearby couriers, 16 cached preparations, then eight overlapping new-generation preparations. Completed journeys and route batches are replenished outside the measured tick.");
            report.put("java", System.getProperty("java.version")); report.put("processors", Runtime.getRuntime().availableProcessors());
            report.put("windows", windows); report.put("cachedP95DeltaMillis", delta); report.put("snapshots", snapshots);
            report.put("routesAfterCancellation", routes); report.put("peakPhysical", peakPhysical); report.put("peakActive", peakActive);
            report.put("peakQueue", peakQueue); report.put("peakTickets", peakTickets); report.put("failures", failures); report.put("passed", failures.isEmpty());
            try {
                var output = Path.of(System.getProperty("villagerretaliation.diplomacyProfileOutput")); Files.createDirectories(output);
                Files.writeString(output.resolve("profile-" + population + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            LogUtils.getLogger().info("Diplomacy profile {} residents p95 delta {} ms, failures {}", population, delta, failures);
        }
        void cleanup() {
            if (cleaned) return; cleaned = true;
            NeoForge.EVENT_BUS.unregister(before); NeoForge.EVENT_BUS.unregister(after);
            if (workload != null) workload.cancelRoutes();
            if (fixture != null) fixture.releaseProfileChunks();
            VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.set(originalCap);
            if (recording != null) {
                try {
                    recording.stop();
                    var output = Path.of(System.getProperty("villagerretaliation.diplomacyProfileOutput")); Files.createDirectories(output);
                    recording.dump(output.resolve("profile-" + population + ".jfr"));
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
                finally { recording.close(); }
            }
        }
    }
    private static Stats stats(long[] values, double divisor) {
        java.util.Arrays.sort(values);
        if (values.length != SAMPLES) throw new IllegalStateException("Incomplete profile window");
        return new Stats(values.length, java.util.Arrays.stream(values).average().orElseThrow() / divisor, values[values.length / 2] / divisor,
                values[(int) Math.ceil(values.length * .95) - 1] / divisor, values[(int) Math.ceil(values.length * .99) - 1] / divisor, values[values.length - 1] / divisor);
    }
}
