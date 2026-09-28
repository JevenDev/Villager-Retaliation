package com.jvn.villagerretaliation.client.settlement;

import com.google.gson.GsonBuilder;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.settlement.SettlementTownProfileScenario;
import com.jvn.villagerretaliation.settlement.SettlementViewService;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class SettlementTownProfile {
    private static final String OUTPUT = "villagerretaliation.settlementTownProfile";
    private static final int SAMPLE_TICKS = 600;
    private static final Map<Stage, List<Long>> SERVER_TIMES = new EnumMap<>(Stage.class);
    private static final Map<Stage, List<Long>> FRAME_INTERVALS = new EnumMap<>(Stage.class);
    private static final Map<Stage, List<Integer>> NATIVE_RESIDENTS = new EnumMap<>(Stage.class);
    private static final Map<Stage, List<Integer>> RENDERED_RESIDENTS = new EnumMap<>(Stage.class);
    private static final Map<String, SettlementTownProfileScenario.Snapshot> SNAPSHOTS = new LinkedHashMap<>();
    private static volatile Stage stage = Stage.PREPARING;
    private static volatile SettlementTownProfileScenario fixture;
    private static volatile int trackedResidents;
    private static volatile SettlementTownProfileScenario.Snapshot latest;
    private static CompletableFuture<SettlementTownProfileScenario> preparation;
    private static Set<UUID> residents = Set.of();
    private static UUID playerId;
    private static long started;
    private static long tickStarted;
    private static long previousFrame;
    private static Stage tickStage;
    private static Stage frameStage;
    private static Stage previousClientStage;
    private static Stage previousServerStage;
    private static SettlementViewScreen lastView;
    private static int stageTicks;
    private static int clientStageTicks;
    private static int checkedBlocks;
    private static int renderedResidents;
    private static boolean reported;

    private SettlementTownProfile() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null) return;
        var minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        if (started == 0L) started = Util.getMillis();
        if (Util.getMillis() - started > 600_000L) {
            capture("failure");
            throw new IllegalStateException("Settlement town profile timed out at " + stage + ": " + latest);
        }
        if (stage == Stage.DONE) {
            if (reported) return;
            writeReport();
            reported = true;
            minecraft.stop();
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        if (preparation == null && minecraft.screen == null) {
            minecraft.options.renderDistance().set(6);
            minecraft.options.guiScale().set(2);
            minecraft.options.framerateLimit().set(120);
            minecraft.options.enableVsync().set(false);
            minecraft.resizeDisplay();
            playerId = minecraft.player.getUUID();
            preparation = minecraft.getSingleplayerServer().submit(() -> SettlementTownProfileScenario.prepare(
                    minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId)));
        }
        if (fixture == null && preparation != null && preparation.isDone()) {
            fixture = preparation.join();
            residents = fixture.residents();
            stage = Stage.WARM_ACTIVE;
        }
        if ((stage == Stage.WARM_ACTIVE || stage == Stage.RELOADING)
                && minecraft.screen instanceof SettlementManagementScreen screen && !screen.expectingView()) {
            String label = Component.translatable("villagerretaliation.gui.settlement.world_view").getString();
            screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.active && button.getMessage().getString().equals(label))
                    .findFirst().ifPresent(Button::onPress);
        }
        int tracked = 0;
        for (var entity : minecraft.level.entitiesForRendering()) if (residents.contains(entity.getUUID())) tracked++;
        trackedResidents = tracked;
        if (minecraft.screen instanceof SettlementViewScreen screen) {
            if (lastView != screen) {
                lastView = screen;
                screen.mouseScrolled(40, 80, 0, -8);
            }
            if (previousClientStage != stage) {
                for (int key : new int[]{GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D}) screen.keyReleased(key, 0, 0);
                previousClientStage = stage;
                clientStageTicks = 0;
            }
            clientStageTicks++;
            if (clientStageTicks == 120 && (stage == Stage.WARM_ACTIVE || stage == Stage.RELOADING)) {
                capture(stage == Stage.WARM_ACTIVE ? "active" : "return");
            }
            if (stage == Stage.CAMERA && clientStageTicks < 480) {
                int key = switch (clientStageTicks / 120) {
                    case 0 -> GLFW.GLFW_KEY_W;
                    case 1 -> GLFW.GLFW_KEY_D;
                    case 2 -> GLFW.GLFW_KEY_S;
                    default -> GLFW.GLFW_KEY_A;
                };
                if (clientStageTicks % 120 == 60) screen.keyPressed(key, 0, 0);
                if (clientStageTicks % 120 == 80) screen.keyReleased(key, 0, 0);
                if (clientStageTicks == 240) screen.mouseScrolled(40, 80, 0, 1);
                if (clientStageTicks == 360) screen.mouseScrolled(40, 80, 0, -1);
            }
        }
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (System.getProperty(OUTPUT) == null) return;
        long now = System.nanoTime();
        Stage current = stage;
        if (current.sampled() && current == frameStage && previousFrame != 0L) {
            FRAME_INTERVALS.computeIfAbsent(current, ignored -> new ArrayList<>()).add(now - previousFrame);
        }
        previousFrame = now;
        frameStage = current;
        renderedResidents = 0;
    }

    @SubscribeEvent
    public static void onResidentRendered(RenderLivingEvent.Post<?, ?> event) {
        if (System.getProperty(OUTPUT) != null && stage.sampled() && residents.contains(event.getEntity().getUUID())) renderedResidents++;
    }

    @SubscribeEvent
    public static void afterFrame(RenderFrameEvent.Post event) {
        if (System.getProperty(OUTPUT) != null && stage.sampled() && stage == frameStage) {
            RENDERED_RESIDENTS.computeIfAbsent(stage, ignored -> new ArrayList<>()).add(renderedResidents);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeServerTick(ServerTickEvent.Pre event) {
        if (System.getProperty(OUTPUT) == null) return;
        tickStarted = System.nanoTime();
        tickStage = stage;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterServerTick(ServerTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || fixture == null || stage == Stage.DONE) return;
        long duration = System.nanoTime() - tickStarted;
        if (stage.sampled() && tickStage == stage) SERVER_TIMES.computeIfAbsent(stage, ignored -> new ArrayList<>()).add(duration);
        if (stage != previousServerStage) {
            previousServerStage = stage;
            stageTicks = 0;
            LogUtils.getLogger().info("Settlement town profile stage: {}", stage);
        }
        stageTicks++;
        if (stage.sampled()) {
            int nativeResidents = fixture.tickingResidents();
            NATIVE_RESIDENTS.computeIfAbsent(stage, ignored -> new ArrayList<>()).add(nativeResidents);
            if ((stage == Stage.ACTIVE || stage == Stage.RETURN) && nativeResidents != 100) {
                throw new IllegalStateException("every measured active tick has 100 living AI-enabled residents: " + nativeResidents
                        + ", " + fixture.snapshot(event.getServer().getPlayerList().getPlayer(playerId))
                        + ", view=" + fixture.viewState(event.getServer().getPlayerList().getPlayer(playerId)));
            }
            if (stage == Stage.BACKGROUND) require(nativeResidents == 0 && fixture.unloaded(),
                    "every background sample has the complete fixture unloaded");
        }
        ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
        require(player != null, "the observing client remains connected");
        if (stageTicks % 100 == 0) {
            latest = fixture.snapshot(player);
            LogUtils.getLogger().info("Settlement town profile {} tick {}: {}", stage, stageTicks, latest);
        }
        require(stageTicks < 2_400, "the stage completes within its deadline: " + stage + ", " + latest);
        switch (stage) {
            case WARM_ACTIVE -> {
                if (stageTicks < 200 || trackedResidents != SettlementTownProfileScenario.RESIDENTS) return;
                var snapshot = fixture.snapshot(player);
                if (snapshot.ticking() != SettlementTownProfileScenario.RESIDENTS) return;
                verify(snapshot, 100);
                fixture.beginActive(player);
                SNAPSHOTS.put("activeStart", snapshot);
                stage = Stage.ACTIVE;
            }
            case ACTIVE -> {
                if (stageTicks < SAMPLE_TICKS) return;
                var snapshot = fixture.snapshot(player);
                verify(snapshot, 100);
                var initial = SNAPSHOTS.get("activeStart");
                require(snapshot.deliveredIron() > initial.deliveredIron(), "native couriers deliver real cargo during the measured interval: " + snapshot);
                require(snapshot.placed() > initial.placed(), "native builders advance real projects during the measured interval: " + snapshot);
                require(snapshot.wheat() > 0, "native farmers produce real wheat: " + snapshot);
                require(snapshot.bread() < 512, "residents consume real depot meals: " + snapshot);
                SNAPSHOTS.put("activeEnd", snapshot);
                SNAPSHOTS.put("cameraStart", snapshot);
                stage = Stage.CAMERA;
            }
            case CAMERA -> {
                if (stageTicks < SAMPLE_TICKS) return;
                var snapshot = fixture.snapshot(player);
                verify(snapshot, -1);
                require(NATIVE_RESIDENTS.get(Stage.CAMERA).stream().mapToInt(Integer::intValue).min().orElseThrow() < 100,
                        "panning exercises mixed native and background town operation");
                SNAPSHOTS.put("cameraEnd", snapshot);
                SettlementViewService.close(playerId, true);
                stage = Stage.UNLOADING;
            }
            case UNLOADING -> {
                if (!fixture.unloaded()) return;
                fixture.beginBackground(player);
                var snapshot = fixture.snapshot(player);
                verify(snapshot, 0);
                SNAPSHOTS.put("backgroundStart", snapshot);
                stage = Stage.BACKGROUND;
            }
            case BACKGROUND -> {
                if (stageTicks < SAMPLE_TICKS) return;
                var snapshot = fixture.snapshot(player);
                verify(snapshot, 0);
                require(fixture.unloaded(), "background work does not force the town to load");
                var initial = SNAPSHOTS.get("backgroundStart");
                require(snapshot.deliveredIron() > initial.deliveredIron(), "unloaded couriers deliver real cargo: " + snapshot);
                require(snapshot.placed() > initial.placed(), "unloaded builders advance real construction: " + snapshot);
                SNAPSHOTS.put("backgroundEnd", snapshot);
                fixture.open(player);
                stage = Stage.RELOADING;
            }
            case RELOADING -> {
                if (stageTicks < 200 || trackedResidents != SettlementTownProfileScenario.RESIDENTS) return;
                var snapshot = fixture.snapshot(player);
                if (snapshot.ticking() != SettlementTownProfileScenario.RESIDENTS) return;
                verify(snapshot, 100);
                checkedBlocks = fixture.verifyReturnedWorld();
                SNAPSHOTS.put("returnStart", snapshot);
                stage = Stage.RETURN;
            }
            case RETURN -> {
                if (stageTicks < SAMPLE_TICKS) return;
                var snapshot = fixture.snapshot(player);
                verify(snapshot, 100);
                SNAPSHOTS.put("returnEnd", snapshot);
                SettlementViewService.close(playerId, true);
                stage = Stage.DONE;
            }
            default -> {
            }
        }
    }

    private static void verify(SettlementTownProfileScenario.Snapshot snapshot, int expectedLoaded) {
        require(snapshot.census() == 100 && snapshot.homes() == 100 && snapshot.workers() == 100,
                "all residents, homes and civic duties survive each phase: " + snapshot);
        require(snapshot.iron() == SettlementTownProfileScenario.IRON_SUPPLY,
                "depot stock and carried cargo conserve every ingot: " + snapshot);
        require(snapshot.leaderOffline(), "work continues with the leader offline");
        require(snapshot.bodyDisplacementSquared() < 0.01D, "the remote observer's body stays in place");
        if (expectedLoaded >= 0) require(snapshot.loaded() == expectedLoaded && snapshot.ticking() == expectedLoaded
                        && snapshot.aiEnabled() == expectedLoaded,
                "each phase uses its intended native or background authority: " + snapshot);
    }

    private static void writeReport() {
        var report = new LinkedHashMap<String, Object>();
        report.put("scenario", "Seeded City fixture, 100 AI-enabled residents, 100 assigned beds, 32 farmers, 32 couriers, 32 guards and 4 blueprint builders");
        report.put("scope", "Performance and loaded/background reconciliation. Founding and progression gates require separate acceptance coverage.");
        report.put("serverMeasurement", "Highest-priority ServerTickEvent.Pre through lowest-priority ServerTickEvent.Post, including settlement post-tick work and excluding benchmark probes");
        report.put("clientMeasurement", "Intervals between rendered frame starts at 1280x720, GUI scale 2, render distance 6, 120 FPS cap, VSync disabled");
        report.put("screenshots", "Captured during warmup, outside the measured intervals");
        report.put("operatingSystem", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        report.put("elapsedSeconds", (Util.getMillis() - started) / 1_000.0D);
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.put("maximumHeapBytes", Runtime.getRuntime().maxMemory());
        report.put("verifiedConstructionBlocks", checkedBlocks);
        report.put("snapshots", SNAPSHOTS);
        var stages = new LinkedHashMap<String, Object>();
        for (Stage sample : List.of(Stage.ACTIVE, Stage.CAMERA, Stage.BACKGROUND, Stage.RETURN)) {
            var serverTimes = SERVER_TIMES.getOrDefault(sample, List.of());
            require(serverTimes.size() >= SAMPLE_TICKS, "each mode records the complete server sample");
            stages.put(sample.name().toLowerCase(java.util.Locale.ROOT), Map.of(
                    "nativeResidentsMinimum", NATIVE_RESIDENTS.get(sample).stream().mapToInt(Integer::intValue).min().orElseThrow(),
                    "nativeResidentsMaximum", NATIVE_RESIDENTS.get(sample).stream().mapToInt(Integer::intValue).max().orElseThrow(),
                    "renderedResidentsPerFrame", summarizeCounts(RENDERED_RESIDENTS.getOrDefault(sample, List.of())),
                    "serverTickMilliseconds", summarize(serverTimes),
                    "frameIntervalMilliseconds", summarize(FRAME_INTERVALS.getOrDefault(sample, List.of()))));
        }
        report.put("measurements", stages);
        try {
            Files.writeString(Path.of(System.getProperty(OUTPUT), "profile.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Settlement town profile completed\n");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        LogUtils.getLogger().info("Settlement town profile completed: {}", stages);
    }

    private static Map<String, Object> summarize(List<Long> nanos) {
        require(!nanos.isEmpty(), "the measured phase has samples");
        var sorted = nanos.stream().mapToDouble(value -> value / 1_000_000.0D).sorted().toArray();
        return Map.of("samples", sorted.length, "mean", java.util.Arrays.stream(sorted).average().orElseThrow(),
                "p50", sorted[(int) Math.ceil(sorted.length * 0.50D) - 1],
                "p95", sorted[(int) Math.ceil(sorted.length * 0.95D) - 1],
                "p99", sorted[(int) Math.ceil(sorted.length * 0.99D) - 1],
                "maximum", sorted[sorted.length - 1],
                "over50Milliseconds", java.util.Arrays.stream(sorted).filter(value -> value > 50.0D).count());
    }

    private static Map<String, Object> summarizeCounts(List<Integer> counts) {
        require(!counts.isEmpty(), "the phase records rendered-resident counts");
        return Map.of("samples", counts.size(), "minimum", counts.stream().mapToInt(Integer::intValue).min().orElseThrow(),
                "maximum", counts.stream().mapToInt(Integer::intValue).max().orElseThrow(),
                "mean", counts.stream().mapToInt(Integer::intValue).average().orElseThrow());
    }

    private static void capture(String name) {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty(OUTPUT), name + ".png"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Stage {
        PREPARING, WARM_ACTIVE, ACTIVE, CAMERA, UNLOADING, BACKGROUND, RELOADING, RETURN, DONE;

        boolean sampled() {
            return this == ACTIVE || this == CAMERA || this == BACKGROUND || this == RETURN;
        }
    }
}
