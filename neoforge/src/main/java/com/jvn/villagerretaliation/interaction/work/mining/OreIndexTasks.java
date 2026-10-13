package com.jvn.villagerretaliation.interaction.work.mining;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class OreIndexTasks {
    private static ThreadPoolExecutor executor;

    private OreIndexTasks() { }

    static boolean hasCapacity() {
        return executor == null || executor.getQueue().remainingCapacity() > 0;
    }

    static Future<LongSet> submit(Callable<LongSet> task) {
        if (executor == null) {
            int threads = Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
            executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), runnable -> {
                var thread = new Thread(runnable, "villager-ore-index");
                thread.setDaemon(true);
                return thread;
            });
            executor.allowCoreThreadTimeOut(true);
        }
        try {
            return executor.submit(task);
        } catch (RejectedExecutionException exception) {
            return null;
        }
    }

    static void clear() {
        if (executor == null) return;
        for (var queued : executor.shutdownNow()) ((Future<?>) queued).cancel(false);
        executor = null;
    }
}
