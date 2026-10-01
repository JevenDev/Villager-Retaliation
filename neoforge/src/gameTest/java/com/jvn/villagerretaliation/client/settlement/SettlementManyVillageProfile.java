package com.jvn.villagerretaliation.client.settlement;

import com.google.gson.GsonBuilder;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.settlement.SettlementManyVillageProfileScenario;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class SettlementManyVillageProfile {
    private static final String OUTPUT = "villagerretaliation.settlementManyVillageProfile";
    private static final int SAMPLE_TICKS = 1_200;
    private static final List<Long> BASELINE = new ArrayList<>();
    private static final List<Long> ENABLED = new ArrayList<>();
    private static final LinkedHashMap<String, SettlementManyVillageProfileScenario.Snapshot> SNAPSHOTS = new LinkedHashMap<>();
    private static volatile SettlementManyVillageProfileScenario fixture;
    private static volatile Stage stage = Stage.PREPARING;
    private static CompletableFuture<SettlementManyVillageProfileScenario> preparation;
    private static long started;
    private static long tickStarted;
    private static int stageTicks;
    private static int originalCap;
    private static boolean reported;

    private SettlementManyVillageProfile() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null) return;
        var minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        if (started == 0L) started = Util.getMillis();
        if (Util.getMillis() - started > 900_000L) throw new IllegalStateException("Many-village profile timed out: " + stage);
        if (stage == Stage.DONE) {
            if (!reported) {
                reported = true;
                writeReport();
                minecraft.stop();
            }
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        if (preparation == null && minecraft.screen == null) {
            minecraft.options.renderDistance().set(6);
            minecraft.options.framerateLimit().set(60);
            minecraft.options.enableVsync().set(false);
            var playerId = minecraft.player.getUUID();
            originalCap = VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.get();
            preparation = minecraft.getSingleplayerServer().submit(() -> SettlementManyVillageProfileScenario.prepare(
                    minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId)));
        }
        if (fixture == null && preparation != null && preparation.isDone()) {
            fixture = preparation.join();
            stage = Stage.WARM_BASELINE;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeServerTick(ServerTickEvent.Pre event) {
        if (System.getProperty(OUTPUT) != null) tickStarted = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterServerTick(ServerTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || fixture == null || stage == Stage.DONE || stage == Stage.PREPARING) return;
        long duration = System.nanoTime() - tickStarted;
        stageTicks++;
        if (stage == Stage.BASELINE || stage == Stage.ENABLED) {
            (stage == Stage.BASELINE ? BASELINE : ENABLED).add(duration);
            require(fixture.ready(), "every measured tick has 50 native residents and 95 completely unloaded villages");
        }
        if (stageTicks % 200 == 0) LogUtils.getLogger().info("Many-village profile {} tick {}, native {}, unloaded {}",
                stage, stageTicks, fixture.tickingResidents(), fixture.unloadedVillages());
        require(stageTicks < 4_800, "profile stage finishes: " + stage);
        switch (stage) {
            case WARM_BASELINE -> {
                if (fixture.observeAndRelease() && stageTicks >= 600 && fixture.ready()) transition(Stage.BASELINE, "baselineStart");
            }
            case BASELINE -> {
                if (stageTicks < SAMPLE_TICKS) return;
                var end = fixture.snapshot();
                SNAPSHOTS.put("baselineEnd", end);
                var start = SNAPSHOTS.get("baselineStart");
                require(end.deliveries().equals(start.deliveries()) && end.careTimes().equals(start.careTimes()),
                        "cap zero pauses unloaded villages while foreground towns remain eligible");
                VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.set(100);
                stage = Stage.WARM_ENABLED;
                stageTicks = 0;
            }
            case WARM_ENABLED -> {
                if (stageTicks >= 400) transition(Stage.ENABLED, "enabledStart");
            }
            case ENABLED -> {
                if (stageTicks < SAMPLE_TICKS) return;
                var end = fixture.snapshot();
                SNAPSHOTS.put("enabledEnd", end);
                var start = SNAPSHOTS.get("enabledStart");
                for (int index = 0; index < 95; index++) require(end.deliveries().get(index) > start.deliveries().get(index),
                        "every unloaded village completes physical hauling during measurement: " + index);
                for (int index = 0; index < 950; index++) require(end.careTimes().get(index) - start.careTimes().get(index) >= 800L,
                        "every background resident advances throughout measurement: " + index);
                require(end.completedCycles() - start.completedCycles() >= 500L,
                        "the scheduler completes at least five cycles per village on average: " + (end.completedCycles() - start.completedCycles()));
                VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.set(originalCap);
                stage = Stage.DONE;
            }
            default -> { }
        }
    }

    private static void transition(Stage next, String snapshot) {
        SNAPSHOTS.put(snapshot, fixture.snapshot());
        stage = next;
        stageTicks = 0;
        LogUtils.getLogger().info("Many-village profile stage: {}", next);
    }

    private static void writeReport() {
        var baseline = statistics(BASELINE);
        var enabled = statistics(ENABLED);
        double delta = enabled.p95Millis() - baseline.p95Millis();
        boolean passed = enabled.p95Millis() < 50.0D && delta <= 5.0D;
        var report = new LinkedHashMap<String, Object>();
        report.put("scenario", "100 managed villages with 10 residents and 10 beds each. Two couriers per village move finite depot stock. Five villages remain near one stationary player and 95 stay unloaded.");
        report.put("measurement", "Same integrated server, world and machine. Highest-priority server pre-tick to lowest-priority post-tick. Benchmark probes excluded. 600 baseline warmup ticks and 400 enabled warmup ticks.");
        report.put("java", System.getProperty("java.version"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("cap0", baseline);
        report.put("cap100", enabled);
        report.put("p95DeltaMillis", delta);
        report.put("gatesPassed", passed);
        report.put("snapshots", SNAPSHOTS);
        try {
            var output = Path.of(System.getProperty(OUTPUT));
            Files.createDirectories(output);
            Files.writeString(output.resolve("profile.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.writeString(output.resolve("result.txt"), (passed ? "Many-village profile passed" : "Many-village profile failed")
                    + "\ncap 0 p95=" + baseline.p95Millis() + " ms, p99=" + baseline.p99Millis()
                    + " ms\ncap 100 p95=" + enabled.p95Millis() + " ms, p99=" + enabled.p99Millis()
                    + " ms\np95 delta=" + delta + " ms\n");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Statistics statistics(List<Long> values) {
        var sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        require(sorted.length == SAMPLE_TICKS, "both configurations collect 1,200 samples");
        return new Statistics(sorted.length, java.util.Arrays.stream(sorted).average().orElseThrow() / 1_000_000.0D,
                sorted[(int) Math.ceil(sorted.length * 0.95D) - 1] / 1_000_000.0D,
                sorted[(int) Math.ceil(sorted.length * 0.99D) - 1] / 1_000_000.0D,
                sorted[sorted.length - 1] / 1_000_000.0D);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Stage { PREPARING, WARM_BASELINE, BASELINE, WARM_ENABLED, ENABLED, DONE }
    private record Statistics(int samples, double meanMillis, double p95Millis, double p99Millis, double maxMillis) { }
}
