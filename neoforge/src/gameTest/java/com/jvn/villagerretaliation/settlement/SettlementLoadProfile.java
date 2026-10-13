package com.jvn.villagerretaliation.settlement;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("settlement_load_profile")
public final class SettlementLoadProfile {
    private static final int WARMUP = 400;
    private static final int SAMPLES = 1_200;

    private SettlementLoadProfile() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> profiles() {
        int population = Integer.getInteger("villagerretaliation.settlementLoadProfile", 0);
        if (population == 0) return List.of();
        if (population < 100 || population > 1_000 || population % 100 != 0) {
            throw new IllegalArgumentException("settlementLoadProfile must be a multiple of 100 from 100 to 1000");
        }
        return List.of(new TestFunction("settlement_load_profile", "settlement_load_profile_" + population,
                "settlement_load_profile:empty", 6_000, 0, true, helper -> new Run(helper, population).start()));
    }

    private static final class Run {
        private final GameTestHelper helper;
        private final int population;
        private final List<SettlementTownProfileScenario> towns = new ArrayList<>();
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<ChunkPos> forced = new ArrayList<>();
        private final List<SettlementTownProfileScenario.Snapshot> initial = new ArrayList<>();
        private final long[] samples = new long[SAMPLES];
        private final Consumer<ServerTickEvent.Pre> before = this::beforeTick;
        private final Consumer<ServerTickEvent.Post> after = this::afterTick;
        private final Path output = Path.of(System.getProperty("villagerretaliation.settlementLoadProfileOutput"));
        private Recording recording;
        private final boolean paced = Boolean.getBoolean("villagerretaliation.settlementLoadProfilePaced");
        private long nextTickAt;
        private long started;
        private long measuredAt;
        private int tick;
        private boolean cleaned;

        Run(GameTestHelper helper, int population) {
            this.helper = helper;
            this.population = population;
        }

        void start() {
            this.helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo test) {}
                @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { cleanup(); }
                @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { cleanup(); }
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            });
            var level = this.helper.getLevel();
            for (int index = 0; index < this.population / 100; index++) {
                var center = new BlockPos(1_024 + index * 192, -60, 1_024);
                for (int x = (center.getX() - 64) >> 4; x <= (center.getX() + 80) >> 4; x++) {
                    for (int z = (center.getZ() - 64) >> 4; z <= (center.getZ() + 64) >> 4; z++) {
                        level.getChunk(x, z);
                        level.setChunkForced(x, z, true);
                        this.forced.add(new ChunkPos(x, z));
                    }
                }
                var player = this.helper.makeMockServerPlayerInLevel();
                player.setInvulnerable(true);
                player.setNoGravity(true);
                this.players.add(player);
                this.towns.add(SettlementTownProfileScenario.prepare(player, center, false));
            }
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this.before);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this.after);
            LogUtils.getLogger().info("Settlement load profile warming up: {} towns, {} residents", this.towns.size(), this.population);
        }

        private void beforeTick(ServerTickEvent.Pre event) {
            if (this.cleaned || event.getServer() != this.helper.getLevel().getServer()) return;
            if (this.paced) {
                if (this.nextTickAt == 0L) this.nextTickAt = System.nanoTime();
                long remaining;
                while ((remaining = this.nextTickAt - System.nanoTime()) > 0L) java.util.concurrent.locks.LockSupport.parkNanos(remaining);
                this.nextTickAt = Math.max(this.nextTickAt + 50_000_000L, System.nanoTime() - 2_000_000_000L);
            }
            this.started = System.nanoTime();
        }

        private void afterTick(ServerTickEvent.Post event) {
            if (this.cleaned || this.started == 0L || event.getServer() != this.helper.getLevel().getServer()) return;
            long duration = System.nanoTime() - this.started;
            this.tick++;
            if (this.tick == WARMUP) {
                for (int index = 0; index < this.towns.size(); index++) {
                    this.towns.get(index).beginActive(this.players.get(index));
                    this.initial.add(this.towns.get(index).snapshot(this.players.get(index)));
                }
                try {
                    Files.createDirectories(this.output);
                    this.recording = new Recording(Configuration.getConfiguration("profile"));
                    this.recording.start();
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                } catch (java.text.ParseException exception) {
                    throw new IllegalStateException(exception);
                }
                this.measuredAt = System.nanoTime();
            }
            if (this.tick > WARMUP) {
                this.samples[this.tick - WARMUP - 1] = duration;
                int ticking = this.towns.stream().mapToInt(SettlementTownProfileScenario::tickingResidents).sum();
                this.helper.assertTrue(ticking == this.population, "every sample retains the entire living AI-enabled population: " + ticking);
            }
            if (this.tick % 200 == 0) LogUtils.getLogger().info("Settlement load profile {} residents tick {}", this.population, this.tick);
            if (this.tick != WARMUP + SAMPLES) return;
            double elapsed = (System.nanoTime() - this.measuredAt) / 1_000_000_000.0D;
            var snapshots = new ArrayList<SettlementTownProfileScenario.Snapshot>();
            for (int index = 0; index < this.towns.size(); index++) snapshots.add(this.towns.get(index).snapshot(this.players.get(index)));
            var failures = new ArrayList<String>();
            for (int index = 0; index < this.towns.size(); index++) {
                var snapshot = snapshots.get(index);
                var initial = this.initial.get(index);
                if (snapshot.census() != 100 || snapshot.homes() != 100 || snapshot.workers() != 100)
                    failures.add("Town " + index + " lost roster, housing or workers: " + snapshot);
                if (snapshot.iron() != SettlementTownProfileScenario.IRON_SUPPLY)
                    failures.add("Town " + index + " did not conserve cargo");
                if (snapshot.deliveredIron() <= initial.deliveredIron()) failures.add("Town " + index + " did not deliver cargo");
                if (snapshot.placed() <= initial.placed()) failures.add("Town " + index + " did not build: " + snapshot + ", initial=" + initial);
                if (snapshot.wheat() <= 0 || snapshot.bread() >= 512) failures.add("Town " + index + " did not farm or consume food");
            }
            writeReport(snapshots, elapsed, failures);
            this.helper.assertTrue(failures.isEmpty(), String.join("\n", failures));
            cleanup();
            this.helper.succeed();
        }

        private void writeReport(List<SettlementTownProfileScenario.Snapshot> snapshots, double elapsed, List<String> failures) {
            long[] sorted = this.samples.clone();
            Arrays.sort(sorted);
            var report = new LinkedHashMap<String, Object>();
            report.put("workloadPassed", failures.isEmpty());
            report.put("failures", failures);
            report.put("population", this.population);
            report.put("settlements", this.towns.size());
            report.put("warmupTicks", WARMUP);
            report.put("sampleTicks", SAMPLES);
            report.put("paced", this.paced);
            report.put("measurement", "ServerTickEvent.Pre through Post, excluding population probes and pacing. "
                    + (this.paced ? "The harness paces ticks at 20 TPS. Wall throughput measures achieved TPS."
                    : "GameTest runs unpaced, so wall ticks per second is throughput, not live-server TPS."));
            report.put("javaVersion", System.getProperty("java.version"));
            report.put("maximumHeapBytes", Runtime.getRuntime().maxMemory());
            report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            report.put("meanMs", Arrays.stream(sorted).average().orElseThrow() / 1_000_000.0D);
            report.put("p95Ms", sorted[(int) Math.ceil(SAMPLES * 0.95D) - 1] / 1_000_000.0D);
            report.put("p99Ms", sorted[(int) Math.ceil(SAMPLES * 0.99D) - 1] / 1_000_000.0D);
            report.put("maxMs", sorted[SAMPLES - 1] / 1_000_000.0D);
            report.put("ticksOver50Ms", Arrays.stream(sorted).filter(value -> value > 50_000_000L).count());
            report.put("wallThroughput", SAMPLES / elapsed);
            report.put("builderDiagnostics", this.towns.stream().map(SettlementTownProfileScenario::builderDiagnostics).toList());
            report.put("initial", this.initial);
            report.put("final", snapshots);
            try {
                this.recording.stop();
                this.recording.dump(this.output.resolve("profile.jfr"));
                Files.writeString(this.output.resolve("profile.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
                var raw = new StringBuilder("tick,mspt\n");
                for (int index = 0; index < SAMPLES; index++) raw.append(index).append(',').append(this.samples[index] / 1_000_000.0D).append('\n');
                Files.writeString(this.output.resolve("ticks.csv"), raw);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            LogUtils.getLogger().info("Settlement load profile results: {}", report);
        }

        private void cleanup() {
            if (this.cleaned) return;
            this.cleaned = true;
            NeoForge.EVENT_BUS.unregister(this.before);
            NeoForge.EVENT_BUS.unregister(this.after);
            if (this.recording != null) this.recording.close();
            var level = this.helper.getLevel();
            for (var town : this.towns) for (var id : town.residents()) {
                var entity = level.getEntity(id);
                if (entity != null) entity.discard();
            }
            this.players.forEach(ServerPlayer::discard);
            this.forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        }
    }
}
