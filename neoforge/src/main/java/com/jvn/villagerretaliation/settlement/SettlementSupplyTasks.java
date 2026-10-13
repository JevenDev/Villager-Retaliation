package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyAllocation;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class SettlementSupplyTasks {
    private static ThreadPoolExecutor executor;

    private SettlementSupplyTasks() { }

    static Future<WorkSupplyAllocation.Calculation> submit(WorkSupplyAllocation.Prepared snapshot) {
        if (executor == null) {
            executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), runnable -> {
                var thread = new Thread(runnable, "villager-supply-planning");
                thread.setDaemon(true);
                return thread;
            });
            executor.allowCoreThreadTimeOut(true);
        }
        try {
            return executor.submit(snapshot.task());
        } catch (RejectedExecutionException exception) {
            return null;
        }
    }

    public static void clearRuntimeState() {
        if (executor == null) return;
        for (var queued : executor.shutdownNow()) ((Future<?>) queued).cancel(false);
        executor = null;
    }
}
