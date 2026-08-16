package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Bounded owner-fair scheduler. The only full scan occurs once while rebuilding indexes after load. */
public final class SceneScheduler {
    public static final int DEFAULT_MAX_WORK_PER_TICK = 64;
    private final int maxWork;
    private final Map<String, ArrayDeque<UUID>> byOwner = new HashMap<>();
    private final ArrayDeque<String> owners = new ArrayDeque<>();
    private final Set<UUID> queued = new HashSet<>();
    private final PriorityQueue<WakeEntry> wakes = new PriorityQueue<>(Comparator.comparingLong(WakeEntry::time));
    private final Map<UUID, Long> scheduledWakes = new HashMap<>();
    private Processor processor;

    public SceneScheduler(int maxWork, Processor processor) {
        this.maxWork = Math.max(1, maxWork);
        this.processor = processor == null ? SceneScheduler::defaultProcess : processor;
    }

    public void processor(Processor value) { this.processor = value == null ? SceneScheduler::defaultProcess : value; }

    public void rebuild(SceneSavedData data, long gameTime) {
        rebuild(null, data, gameTime);
    }

    public void rebuild(MinecraftServer server, SceneSavedData data, long gameTime) {
        byOwner.clear(); owners.clear(); queued.clear(); wakes.clear(); scheduledWakes.clear();
        for (SceneInstance instance : data.active()) {
            SceneStepRecord record = instance.stepRecords().get(instance.currentStep());
            var definition = server == null ? null : SceneResources.scene(server, instance.sceneId()).orElse(null);
            long deadline = overallDeadline(instance, definition);
            if (deadline > 0L && deadline <= gameTime) {
                enqueue(instance);
            } else if (instance.state() == SceneState.WAITING && record != null && record.wakeTime() > gameTime) {
                schedule(instance, earlierPositive(record.wakeTime(), deadline), gameTime);
            } else if (instance.state() == SceneState.BLOCKED) {
                schedule(instance, deadline, gameTime);
            } else {
                enqueue(instance);
            }
        }
    }

    public void enqueue(SceneInstance instance) {
        if (instance == null || instance.state().terminal() || !queued.add(instance.id())) return;
        scheduledWakes.remove(instance.id());
        String owner = instance.owner().stableKey();
        ArrayDeque<UUID> queue = byOwner.computeIfAbsent(owner, ignored -> { owners.addLast(owner); return new ArrayDeque<>(); });
        queue.addLast(instance.id());
    }

    public TickResult tick(MinecraftServer server, SceneSavedData data, long gameTime) {
        while (!wakes.isEmpty() && wakes.peek().time() <= gameTime) {
            WakeEntry wake = wakes.poll();
            Long scheduled = scheduledWakes.get(wake.instance());
            if (scheduled == null || scheduled.longValue() != wake.time()) continue;
            scheduledWakes.remove(wake.instance());
            data.get(wake.instance()).ifPresent(this::enqueue);
        }
        int work = 0;
        while (work < maxWork && !owners.isEmpty()) {
            String owner = owners.removeFirst(); ArrayDeque<UUID> queue = byOwner.get(owner);
            if (queue == null || queue.isEmpty()) { byOwner.remove(owner); continue; }
            UUID id = queue.removeFirst(); queued.remove(id); if (!queue.isEmpty()) owners.addLast(owner); else byOwner.remove(owner);
            SceneInstance instance = data.get(id).orElse(null); if (instance == null || instance.state().terminal()) continue;
            var definition = SceneResources.scene(server, instance.sceneId()).orElse(null);
            ProcessResult result = processor.process(server, data, instance, definition, gameTime); work++;
            if (result.kind() == ScheduleKind.NOW) enqueue(instance);
            else if (result.kind() == ScheduleKind.WAKE_AT) {
                schedule(instance, earlierPositive(result.wakeTime(), overallDeadline(instance, definition)), gameTime);
            } else if (instance.state() == SceneState.BLOCKED) {
                schedule(instance, overallDeadline(instance, definition), gameTime);
            }
        }
        return new TickResult(work, queued.size(), scheduledWakes.size());
    }

    private void schedule(SceneInstance instance, long requestedTime, long gameTime) {
        if (instance == null || instance.state().terminal() || requestedTime <= 0L) return;
        long wakeTime = Math.max(safeIncrement(gameTime), requestedTime);
        Long existing = scheduledWakes.put(instance.id(), wakeTime);
        if (existing == null || existing.longValue() != wakeTime) {
            wakes.add(new WakeEntry(wakeTime, instance.id()));
        }
    }

    private static long overallDeadline(SceneInstance instance,
            com.jvn.villagerretaliation.scene.model.CompiledScene definition) {
        if (instance == null || definition == null || definition.timeoutTicks() <= 0L
                || instance.deadlineHandled()) return 0L;
        long start = instance.startGameTime();
        long duration = definition.timeoutTicks();
        return duration > Long.MAX_VALUE - start ? Long.MAX_VALUE : start + duration;
    }

    private static long earlierPositive(long first, long second) {
        if (first <= 0L) return second;
        if (second <= 0L) return first;
        return Math.min(first, second);
    }

    private static long safeIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static ProcessResult defaultProcess(MinecraftServer server, SceneSavedData data, SceneInstance instance,
            com.jvn.villagerretaliation.scene.model.CompiledScene definition, long time) {
        if (definition == null) { instance.block("definition_missing", "scene definition is unavailable", time); data.changed(); return ProcessResult.idle(); }
        SceneDefinitionReconciler.Result reconciliation = SceneDefinitionReconciler.reconcile(instance, definition);
        if (!reconciliation.safe()) { instance.block("definition_incompatible", reconciliation.diagnostic(), time); data.changed(); return ProcessResult.idle(); }
        if (instance.state() == SceneState.PENDING) { instance.transition(SceneState.RUNNING, time); data.changed(); }
        return ProcessResult.idle();
    }

    public int maxWork() { return maxWork; }
    @FunctionalInterface public interface Processor { ProcessResult process(MinecraftServer server, SceneSavedData data, SceneInstance instance, com.jvn.villagerretaliation.scene.model.CompiledScene definition, long gameTime); }
    public enum ScheduleKind { NOW, WAKE_AT, IDLE }
    public record ProcessResult(ScheduleKind kind,long wakeTime){public static ProcessResult now(){return new ProcessResult(ScheduleKind.NOW,0);}public static ProcessResult wakeAt(long time){return new ProcessResult(ScheduleKind.WAKE_AT,time);}public static ProcessResult idle(){return new ProcessResult(ScheduleKind.IDLE,0);}}
    public record TickResult(int workPerformed,int readyQueued,int sleeping){}
    private record WakeEntry(long time,UUID instance){}
}
