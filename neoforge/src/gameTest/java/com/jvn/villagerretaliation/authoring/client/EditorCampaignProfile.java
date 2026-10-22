package com.jvn.villagerretaliation.authoring.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

final class EditorCampaignProfile {
    record Summary(double medianMillis, double p95Millis, double allocatedMegabytes, double frameMillis) { }
    private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final List<Long> wall = new ArrayList<>(), allocations = new ArrayList<>(), frames = new ArrayList<>();
    private static Recording recording;
    private static long start, allocated, previous;
    private static String scenario;

    private EditorCampaignProfile() { }

    static void start(String name) {
        scenario = name;
        try {
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2));
            recording.start();
            wall.clear(); allocations.clear(); frames.clear(); start = 0; previous = 0;
        } catch (IOException | java.text.ParseException exception) { throw new IllegalStateException(exception); }
    }

    static void beginFrame() {
        if (recording == null) return;
        start = System.nanoTime();
        allocated = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId());
        if (previous != 0) frames.add(start - previous);
        previous = start;
    }

    static void endUI() {
        if (recording == null || start == 0) return;
        wall.add(System.nanoTime() - start);
        allocations.add(THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated);
        start = 0;
    }

    static Summary stop(Path directory) {
        var pending = recording; recording = null;
        try (pending) {
            pending.stop(); pending.dump(directory.resolve(scenario + ".jfr"));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
        var sorted = wall.stream().sorted().toList();
        var frameTimes = frames.stream().sorted().toList();
        var result = new Summary(sorted.get(sorted.size() / 2) / 1e6, sorted.get((sorted.size() - 1) * 95 / 100) / 1e6,
                allocations.stream().mapToLong(Long::longValue).average().orElseThrow() / 1048576,
                frameTimes.get(frameTimes.size() / 2) / 1e6);
        System.out.printf(Locale.ROOT, "Campaign full editor [%s]: median %.3f ms, p95 %.3f ms, %.3f MB allocated per frame, %.3f ms frame interval (%d frames)%n",
                scenario, result.medianMillis(), result.p95Millis(), result.allocatedMegabytes(), result.frameMillis(), sorted.size());
        return result;
    }
}
