package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import jdk.jfr.Event;
import jdk.jfr.Name;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.opengl.GL33;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class MapTerrainPerformanceChecks {
    private static final String[] MODES = {"world-warmup", "hud-baseline", "open", "idle", "zoom", "pan", "zoom-pan", "distant-pan", "warm-zoom", "warm-zoom-pan", "close", "reopen", "reclose"};
    private static final List<String> report = new ArrayList<>();
    private static final List<Double> screens = new ArrayList<>(), frames = new ArrayList<>(), gpu = new ArrayList<>();
    private static final ArrayDeque<Integer> queries = new ArrayDeque<>(), available = new ArrayDeque<>();
    private static final int[] timers = new int[8], timerStages = new int[8];
    private static CompletableFuture<MapRepository> loading;
    private static Recording recording;
    private static int stage = -1, query = -1;
    private static long started, screenStart, lastFrame;
    private static double cameraX, cameraZ;
    private static int uploads, worldFrames;
    private static MapView lastShown;
    private static Path output;
    private static long allocated, totalAllocated;
    private static final Map<Long, Long> threadAllocations = new HashMap<>();
    private static double longestFrame;
    private static Phase phase;
    @Name("villagerretaliation.MapTerrainPhase")
    static final class Phase extends Event { String mode; }
    @Name("villagerretaliation.MapTerrainStall")
    static final class Stall extends Event { String mode; double millis; }
    private static long allocatedBytes() {
        var threads = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        for (var thread : threads.getThreadInfo(threads.getAllThreadIds())) {
            if (thread == null || !(thread.getThreadName().startsWith("VR map") || thread.getThreadName().startsWith("VR minimap")
                    || thread.getThreadId() == Thread.currentThread().threadId())) continue;
            long current = Math.max(0, threads.getThreadAllocatedBytes(thread.getThreadId()));
            var previous = threadAllocations.put(thread.getThreadId(), current);
            totalAllocated += current - (previous == null ? 0 : previous);
        }
        return totalAllocated;
    }
    private static void beginPhase() {
        allocated = allocatedBytes();
        phase = new Phase(); phase.mode = MODES[stage]; phase.begin();
    }

    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        mc.setWindowActive(true);
        mc.options.pauseOnLostFocus = false;
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(Integer.getInteger("villagerretaliation.mapPerformanceFps", 260));
        if (loading == null) {
            output = Path.of(System.getProperty("villagerretaliation.exploredMapClientSmoke"));
            loading = CompletableFuture.supplyAsync(MapTerrainPerformanceChecks::fixture);
            return;
        }
        if (stage == -1) {
            if (!loading.isDone() || ExploredMapClient.repository() == null) return;
            ExploredMapClient.repository().close();
            set(ExploredMapClient.class, null, "repository", loading.join());
            set(ExploredMapClient.class, null, "observation", null);
            ExploredMapClient.tiles().close();
            VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.set(Boolean.getBoolean("villagerretaliation.mapPerformanceMinimap"));
            try {
                report.add("Java " + System.getProperty("java.version") + ", collectors "
                        + ManagementFactory.getGarbageCollectorMXBeans().stream().map(bean -> bean.getName()).toList()
                        + ", JVM " + ManagementFactory.getRuntimeMXBean().getInputArguments());
                recording = new Recording(Configuration.getConfiguration("profile"));
                recording.start();
            } catch (IOException | java.text.ParseException e) { throw new AssertionError(e); }
            for (int i = 0; i < timers.length; i++) { timers[i] = GL33.glGenQueries(); available.addLast(i); }
            stage = 0; started = System.nanoTime(); beginPhase();
        }
        set(ExploredMapClient.class, null, "observation", null);
        loading.join().tick();
        if (System.nanoTime() - started < 8_000_000_000L) return;
        phase.end(); phase.commit();
        if (stage > 0) {
            Collections.sort(screens); Collections.sort(frames); Collections.sort(gpu);
            String result = String.format(Locale.ROOT,
                    "%s: %d frames, map CPU p50/p95/p99/max %.3f/%.3f/%.3f/%.3f ms, GPU p95 %.3f ms, frame interval p95/p99/max %.3f/%.3f/%.3f ms",
                    MODES[stage], frames.size(), percentile(screens, .5), percentile(screens, .95), percentile(screens, .99), percentile(screens, 1),
                    percentile(gpu, .95), percentile(frames, .95), percentile(frames, .99), percentile(frames, 1));
            if (frames.size() < 100 || mc.screen instanceof ExploredMapScreen && lastShown == null) throw new AssertionError("Missing rendered terrain frames");
            if ((MODES[stage].equals("close") || MODES[stage].equals("reclose")) && worldFrames == 0)
                throw new AssertionError("Closing the map did not restore world rendering during " + MODES[stage]);
            if (moving() && uploads < 10) throw new AssertionError("Map view stopped responding during " + MODES[stage]);
            if (MODES[stage].equals("idle") && ExploredMapClient.tiles().uploads() < 10)
                throw new AssertionError("Recorded terrain did not reach the tile atlas");
            result += ", view changes " + uploads + ", frame intervals over 16.7 ms " + frames.stream().filter(millis -> millis > 1000.0 / 60).count();
            result += String.format(Locale.ROOT, ", map/render allocation %.2f MB/s", (allocatedBytes() - allocated) / 1e6 / ((System.nanoTime() - started) / 1e9));
            if (stage >= 2) longestFrame = Math.max(longestFrame, percentile(frames, 1));
            report.add(result); System.out.println(result);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(output.resolve("terrain-" + MODES[stage] + ".png"));
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        screens.clear(); frames.clear(); gpu.clear(); lastFrame = 0; uploads = 0; worldFrames = 0;
        if (++stage == MODES.length) {
            double budget = Double.parseDouble(System.getProperty("villagerretaliation.mapPerformanceMaxFrameMillis", "16.667"));
            try {
                recording.stop(); recording.dump(output.resolve("terrain-performance.jfr")); recording.close();
                Files.writeString(output.resolve("result.txt"), (longestFrame <= budget ? "Explored map client checks passed\n" : "Recorded terrain stutter budget FAILED\n") + String.join("\n", report));
            } catch (IOException e) { throw new UncheckedIOException(e); }
            for (int timer : timers) GL33.glDeleteQueries(timer);
            if (longestFrame > budget) throw new AssertionError(String.format(Locale.ROOT,
                    "Recorded terrain stutter budget failed: %.3f ms worst frame exceeds %.3f ms", longestFrame, budget));
            mc.stop(); return;
        }
        started = System.nanoTime(); beginPhase(); lastFrame = System.nanoTime();
        if (MODES[stage].equals("open") || MODES[stage].equals("reopen")) open();
        if (MODES[stage].equals("close") || MODES[stage].equals("reclose")) mc.setScreen(null);
        if (MODES[stage].equals("warm-zoom")) {
            ExploredMapClientSmoke.navigation((ExploredMapScreen) mc.screen).focus(cameraX, cameraZ);
        }
    }
    private static boolean moving() {
        return switch (MODES[stage]) {
            case "zoom", "pan", "zoom-pan", "distant-pan", "warm-zoom", "warm-zoom-pan" -> true;
            default -> false;
        };
    }
    private static void open() {
        var mc = Minecraft.getInstance();
        mc.setScreen(new ExploredMapScreen());
        var screen = (ExploredMapScreen)mc.screen;
        ExploredMapClientSmoke.navigation(screen).focus(cameraX, cameraZ);
        ExploredMapClientSmoke.zoom(screen, 2.5);
    }

    private static MapRepository fixture() {
        var metadata = new Properties();
        try (var in = Files.newInputStream(output.resolve("terrain-fixture.properties"))) { metadata.load(in); }
        catch (IOException e) { throw new UncheckedIOException(e); }
        cameraX = Double.parseDouble(metadata.getProperty("cameraX")); cameraZ = Double.parseDouble(metadata.getProperty("cameraZ"));
        report.add(metadata.getProperty("summary")); System.out.println(metadata.getProperty("summary"));
        return new MapRepository(new MapStore(output.resolve("recorded-terrain"), "benchmark", "recorded", "player", "dimension"));
    }

    @SubscribeEvent public static void worldRendered(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (stage < 0 || stage >= MODES.length) return;
        if (Minecraft.getInstance().screen instanceof ExploredMapScreen) throw new AssertionError("Hidden world rendered behind the full-screen map");
        worldFrames++;
    }
    @SubscribeEvent public static void beforeFrame(RenderFrameEvent.Pre event) {
        if (stage < 0 || stage >= MODES.length) return;
        long now = System.nanoTime();
        if (lastFrame != 0) {
            double millis = (now - lastFrame) / 1e6; frames.add(millis);
            if (millis > 1000.0 / 60) { var stall = new Stall(); stall.mode = MODES[stage]; stall.millis = millis; stall.commit(); }
        }
        lastFrame = now;
    }
    @SubscribeEvent public static void beforeScreen(ScreenEvent.Render.Pre event) {
        if (stage < 0 || stage >= MODES.length || !(event.getScreen() instanceof ExploredMapScreen screen)) return;
        double seconds = (System.nanoTime() - started) / 1e9;
        if (MODES[stage].contains("zoom")) {
            double target = Math.exp(Math.log(.3) + (Math.sin(seconds * 1.8) + 1) * .5 * Math.log(16 / .3));
            double zoom = screen.view().scale();
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, Math.log(target / zoom) / Math.log(1.15));
        }
        if (MODES[stage].contains("pan")) {
            double x = cameraX + 180 * Math.sin(seconds), z = cameraZ + 160 * Math.cos(seconds * .7);
            if (MODES[stage].equals("distant-pan")) { x = cameraX + 500 * Math.sin(seconds * 1.5); z = cameraZ + 400 * Math.cos(seconds); }
            double dx = x - screen.view().x(), dz = z - screen.view().z();
            var view = currentView(screen);
            screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, 0);
            screen.mouseDragged(screen.width / 2.0, screen.height / 2.0, 0,
                    -dx * view.scale(),
                    -dz * view.scale());
        }
        while (!queries.isEmpty() && GL33.glGetQueryObjecti(timers[queries.peekFirst()], GL33.GL_QUERY_RESULT_AVAILABLE) != 0) {
            int index = queries.removeFirst();
            if (timerStages[index] == stage) gpu.add(GL33.glGetQueryObjecti64(timers[index], GL33.GL_QUERY_RESULT) / 1e6);
            available.addLast(index);
        }
        query = available.isEmpty() ? -1 : available.removeFirst();
        if (query >= 0) GL33.glBeginQuery(GL33.GL_TIME_ELAPSED, timers[query]);
        screenStart = System.nanoTime();
    }
    @SubscribeEvent public static void afterScreen(ScreenEvent.Render.Post event) {
        if (screenStart == 0 || !(event.getScreen() instanceof ExploredMapScreen)) return;
        var shown = ((ExploredMapScreen)event.getScreen()).view();
        if (!shown.equals(lastShown)) { uploads++; lastShown = shown; }
        event.getGuiGraphics().flush();
        screens.add((System.nanoTime() - screenStart) / 1e6); screenStart = 0;
        if (query >= 0) { GL33.glEndQuery(GL33.GL_TIME_ELAPSED); timerStages[query] = stage; queries.addLast(query); query = -1; }
    }
    private static double percentile(List<Double> values, double p) { return values.isEmpty() ? 0 : values.get(Math.min(values.size() - 1, (int)(values.size() * p))); }
    private static MapView currentView(ExploredMapScreen screen) { return screen.view(); }
    private static void set(Class<?> type, Object target, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
