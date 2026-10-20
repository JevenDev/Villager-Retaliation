package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTimer;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

public final class QuestTimers {
    private QuestTimers() { }

    public record Entry(String key, QuestTimer definition, QuestTimerState state) { }

    public static void enter(QuestDefinition definition, QuestProgressState progress, String stageId, long time) {
        var stage = definition.stages().get(stageId);
        if (stage == null) return;
        if (stage.timer() != null) progress.timers.put(stageKey(stageId), new QuestTimerState(time));
        for (var objective : QuestObjectiveQuery.stageObjectives(definition, stageId))
            if (objective.timer() != null && !progress.objectiveComplete(objective.id()))
                progress.timers.put(objectiveKey(objective.id()), new QuestTimerState(time));
    }

    public static List<Entry> active(QuestDefinition definition, QuestProgressState progress) {
        var result = new ArrayList<Entry>();
        for (String id : progress.activeStages()) {
            var stage = definition.stages().get(id);
            if (stage != null && stage.timer() != null) add(result, progress, stageKey(stage.id()), stage.timer());
        }
        for (var objective : progress.activeStages().stream().flatMap(stage -> QuestObjectiveQuery.stageObjectives(definition, stage).stream()).toList())
            if (objective.timer() != null && !progress.objectiveComplete(objective.id()))
                add(result, progress, objectiveKey(objective.id()), objective.timer());
        return List.copyOf(result);
    }

    private static void add(List<Entry> entries, QuestProgressState progress, String key, QuestTimer timer) {
        var state = progress.timers.get(key);
        if (state != null) entries.add(new Entry(key, timer, state));
    }

    public static boolean update(QuestDefinition definition, QuestProgressState progress, long time, boolean active) {
        boolean changed = false;
        for (String id : progress.activeStages()) {
            var stage = definition.stages().get(id);
            if (stage != null && stage.timer() != null && !progress.timers.containsKey(stageKey(stage.id()))) {
                progress.timers.put(stageKey(stage.id()), new QuestTimerState(time));
                changed = true;
            }
        }
        for (var objective : progress.activeStages().stream().flatMap(stage -> QuestObjectiveQuery.stageObjectives(definition, stage).stream()).toList())
            if (objective.timer() != null && !progress.objectiveComplete(objective.id())
                    && !progress.timers.containsKey(objectiveKey(objective.id()))) {
                progress.timers.put(objectiveKey(objective.id()), new QuestTimerState(time));
                changed = true;
            }
        for (var entry : active(definition, progress))
            changed |= entry.state().update(time, active || !entry.definition().pauseWhenInactive());
        return changed;
    }

    public static boolean allowsObjective(QuestDefinition.Objective objective, QuestProgressState progress) {
        return allowsObjective(objective, progress, Long.MIN_VALUE);
    }

    public static boolean allowsObjective(QuestDefinition.Objective objective, QuestProgressState progress, long time) {
        if (objective.timer() == null) return true;
        var timer = progress.timer(objectiveKey(objective.id()));
        if (timer == null) return false;
        return objective.timer().mode() == QuestTimer.Mode.DURATION ? timer.elapsed(objective.timer())
                : !timer.elapsedAt(objective.timer(), time);
    }

    public static boolean allowsStage(QuestDefinition.Stage stage, QuestProgressState progress) {
        if (stage.timer() == null) return true;
        var timer = progress.timer(stageKey(stage.id()));
        if (timer == null) return false;
        return stage.timer().mode() == QuestTimer.Mode.DURATION ? timer.elapsed(stage.timer()) : !timer.elapsed(stage.timer());
    }

    public static void connection(ServerPlayer player, boolean online) {
        var data = VillagerQuestSavedData.get(player.serverLevel());
        for (var entry : data.progress(player.getUUID())) {
            var definition = VillagerQuestResources.quest(player.server, entry.getKey()).orElse(null);
            if (definition == null || entry.getValue().state() != VillagerQuestSavedData.QuestState.ACTIVE) continue;
            for (var timer : active(definition, entry.getValue())) {
                if (!timer.definition().pauseWhenOffline()) continue;
                if (online) timer.state().resume(player.serverLevel().getGameTime());
                else timer.state().update(player.serverLevel().getGameTime(), false);
                data.setDirty();
            }
        }
    }

    public static String stageKey(String id) { return "stage/" + id; }
    public static String objectiveKey(String id) { return "objective/" + id; }
}
