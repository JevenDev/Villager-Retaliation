package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.tracking.QuestStageReadiness;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class QuestParallelService {
    private static final int MAX_ADVANCES = 16;

    private QuestParallelService() { }

    public static void enter(QuestDefinition definition, QuestProgressModel progress, String stageId, long time) {
        progress.parallelPaths.clear();
        var stage = definition.stages().get(stageId);
        if (stage == null) return;
        for (String branch : stage.parallel()) {
            progress.parallelPaths.put(branch, new QuestParallelPath(branch, "", QuestParallelPath.Phase.ENTERING));
            QuestTimers.enter(definition, progress, branch, time);
        }
    }

    public static boolean complete(QuestDefinition.Stage stage, QuestProgressModel progress) {
        return stage.parallel().isEmpty() || progress.parallelPaths.keySet().equals(Set.copyOf(stage.parallel()))
                && progress.parallelPaths.values().stream().allMatch(path -> path.phase() == QuestParallelPath.Phase.ARRIVED);
    }

    public static boolean request(QuestDefinition definition, QuestProgressModel progress, String from, String target) {
        var parent = definition.stages().get(progress.currentStage());
        if (parent == null || parent.parallel().isEmpty() || !definition.stages().containsKey(target)) return false;
        for (var entry : progress.parallelPaths.entrySet()) {
            var path = entry.getValue();
            if (!path.stage().equals(from) || path.phase() != QuestParallelPath.Phase.ACTIVE
                    && path.phase() != QuestParallelPath.Phase.ENTERING || from.equals(target)) continue;
            progress.parallelPaths.put(entry.getKey(), new QuestParallelPath(from, target, path.phase() == QuestParallelPath.Phase.ENTERING
                    ? QuestParallelPath.Phase.ENTERING : QuestParallelPath.Phase.EXITING));
            return true;
        }
        return false;
    }

    public static boolean advance(QuestDefinition definition, QuestProgressModel progress, long time,
            Predicate<QuestDefinition.Objective> objectiveComplete, Predicate<List<DialogueCondition>> conditionsMet,
            Actions actions, java.util.function.Consumer<String> entered) {
        var parent = definition.stages().get(progress.currentStage());
        if (parent == null || parent.parallel().isEmpty() || !progress.parallelPaths.keySet().equals(Set.copyOf(parent.parallel()))) return false;
        boolean changed = false;
        for (String branch : parent.parallel()) {
            for (int step = 0; step < MAX_ADVANCES; step++) {
                if (!progress.currentStage().equals(parent.id()) || progress.status() != QuestProgressTypes.QuestState.ACTIVE) return changed;
                var path = progress.parallelPaths.get(branch);
                var stage = definition.stages().get(path.stage());
                if (stage == null || path.phase() == QuestParallelPath.Phase.ARRIVED) break;
                String key = "parallel/" + pathSegment(branch) + "/" + pathSegment(stage.id()) + "/";
                if (path.phase() == QuestParallelPath.Phase.ENTERING) {
                    if (!actions.run(stage.id(), key + "entry", stage.entryActions())) break;
                    if (!progress.currentStage().equals(parent.id()) || progress.status() != QuestProgressTypes.QuestState.ACTIVE) return true;
                    String requested = progress.parallelPaths.get(branch).target();
                    progress.parallelPaths.put(branch, new QuestParallelPath(stage.id(), requested,
                            requested.isBlank() ? QuestParallelPath.Phase.ACTIVE : QuestParallelPath.Phase.EXITING));
                    changed = true;
                    entered.accept(stage.id());
                    if (progress.stageActions().values().stream().anyMatch(checkpoint -> !checkpoint.outcome().success())) return true;
                    continue;
                }
                if (path.phase() == QuestParallelPath.Phase.ACTIVE) {
                    boolean blocked = false;
                    for (var bonus : stage.bonuses()) {
                        if (progress.bonusClaimed(stage.id(), bonus.id()) || !QuestStageReadiness.compositionMet(definition,
                                bonus.when(), bonus.mode(), bonus.count(), objectiveComplete, conditionsMet)) continue;
                        if (!actions.run(stage.id(), key + "bonus/" + bonus.id(), bonus.actions())) { blocked = true; break; }
                        progress.claimBonus(stage.id(), bonus.id());
                        changed = true;
                    }
                    if (!progress.currentStage().equals(parent.id()) || progress.status() != QuestProgressTypes.QuestState.ACTIVE) return true;
                    if (progress.parallelPaths.get(branch).phase() != QuestParallelPath.Phase.ACTIVE) continue;
                    if (blocked || stage.next().isBlank() || !ready(definition, progress, stage, objectiveComplete, conditionsMet)) break;
                    progress.parallelPaths.put(branch, new QuestParallelPath(stage.id(), stage.next(), QuestParallelPath.Phase.EXITING));
                    changed = true;
                    continue;
                }
                if (!definition.stages().containsKey(path.target()) || !actions.run(stage.id(), key + "exit", stage.exitActions())) break;
                if (!progress.currentStage().equals(parent.id())) return true;
                for (var predicate : stage.completeWhen())
                    definition.objectives().stream().filter(objective -> objective.id().equals(predicate.objective()) && objectiveComplete.test(objective))
                            .forEach(objective -> progress.markObjectiveComplete(objective.id()));
                boolean arrived = parent.next().equals(path.target());
                progress.parallelPaths.put(branch, new QuestParallelPath(arrived ? stage.id() : path.target(), "",
                        arrived ? QuestParallelPath.Phase.ARRIVED : QuestParallelPath.Phase.ENTERING));
                if (!arrived) QuestTimers.enter(definition, progress, path.target(), time);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean ready(QuestDefinition definition, QuestProgressModel progress, QuestDefinition.Stage stage,
            Predicate<QuestDefinition.Objective> objectives, Predicate<List<DialogueCondition>> conditions) {
        if (!QuestTimers.allowsStage(stage, progress)) return false;
        return stage.completeWhen().isEmpty() ? stage.objectives().isEmpty()
                || stage.timer() != null && stage.timer().mode() == com.jvn.villagerretaliation.quest.QuestTimer.Mode.DURATION
                : QuestStageReadiness.compositionMet(definition, stage.completeWhen(), stage.completionMode(),
                        stage.completionCount(), objectives, conditions);
    }

    private static String pathSegment(String id) {
        return id.replace("%", "%25").replace("/", "%2F");
    }

    public interface Actions { boolean run(String stage, String key, List<VillagerActionDefinition> actions); }
    public static void enter(QuestDefinition definition, QuestProgressState progress, String stageId, long time) {
        enter(definition, (QuestProgressModel) progress, stageId, time);
    }

    public static boolean complete(QuestDefinition.Stage stage, QuestProgressState progress) {
        return complete(stage, (QuestProgressModel) progress);
    }

    public static boolean request(QuestDefinition definition, QuestProgressState progress, String from, String target) {
        return request(definition, (QuestProgressModel) progress, from, target);
    }

    public static boolean advance(QuestDefinition definition, QuestProgressState progress, long time,
            Predicate<QuestDefinition.Objective> objectiveComplete, Predicate<List<DialogueCondition>> conditionsMet,
            Actions actions, java.util.function.Consumer<String> entered) {
        return advance(definition, (QuestProgressModel) progress, time, objectiveComplete, conditionsMet, actions, entered);
    }

}
