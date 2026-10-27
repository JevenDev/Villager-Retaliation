package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressModel;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressState;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class QuestObjectiveDispatcher {
    private QuestObjectiveDispatcher() { }

    public static QuestDefinition.TriggerEvent triggerEventFor(QuestObjectiveEventKind kind) {
        return switch (kind) {
            case MOB_KILL -> QuestDefinition.TriggerEvent.MOB_KILL;
            case BLOCK_BREAK -> QuestDefinition.TriggerEvent.BLOCK_BREAK;
            case BLOCK_PLACE -> QuestDefinition.TriggerEvent.BLOCK_PLACE;
            case BLOCK_INTERACT -> QuestDefinition.TriggerEvent.BLOCK_INTERACT;
            case MEMORY_EVENT -> QuestDefinition.TriggerEvent.MEMORY_EVENT;
            case GIFT -> QuestDefinition.TriggerEvent.GIFT;
            case TRADE -> QuestDefinition.TriggerEvent.TRADE;
            case REPUTATION -> QuestDefinition.TriggerEvent.REPUTATION;
            case CRITERION -> QuestDefinition.TriggerEvent.CRITERION;
        };
    }


    public static boolean poll(QuestProgressModel progress, List<QuestDefinition.Objective> objectives,
            boolean executionAllowed, Predicate<QuestDefinition.Objective> complete,
            Consumer<QuestDefinition.Objective> completed) {
        if (!executionAllowed) return false;
        boolean changed = false;
        for (QuestDefinition.Objective objective : objectives) {
            if (objective.rules().live() && !complete.test(objective)) {
                changed |= progress.unmarkObjectiveComplete(objective.id());
                continue;
            }
            if (progress.objectiveComplete(objective.id()) || !complete.test(objective)) continue;
            boolean newlyComplete = progress.markObjectiveComplete(objective.id());
            changed |= newlyComplete;
            if (newlyComplete && (!objective.rules().live() || progress.publishObjectiveCompletion(objective.id()))) completed.accept(objective);
            clearCompletedTarget(progress, objective);
        }
        return changed;
    }

    public static boolean dispatch(QuestProgressModel progress, List<QuestDefinition.Objective> objectives,
            boolean executionAllowed, QuestObjectiveEventKind eventKind,
            Predicate<QuestDefinition.Objective> matchesEvent, Predicate<QuestDefinition.Objective> complete,
            Consumer<QuestDefinition.Objective> completed, Consumer<ObjectiveUpdate> observed) {
        if (!executionAllowed) return false;
        boolean changed = false;
        for (QuestDefinition.Objective objective : objectives) {
            int previousCounter = progress.objectiveCounter(objective.id());
            boolean previouslyComplete = progress.objectiveComplete(objective.id());
            if (previouslyComplete || !QuestObjectiveRegistry.eventKinds(objective).contains(eventKind)
                    || !matchesEvent.test(objective)) continue;
            if (QuestObjectiveRegistry.requirements(objective).contains(QuestObjectiveRequirement.COUNTER)) {
                int count = progress.addObjectiveCounter(objective.id(), 1);
                changed = true;
                if (count >= objective.count() && (objective.timer() == null || complete.test(objective))
                        && progress.markObjectiveComplete(objective.id())) completed.accept(objective);
            } else if (complete.test(objective) && progress.markObjectiveComplete(objective.id())) {
                changed = true;
                completed.accept(objective);
            }
            clearCompletedTarget(progress, objective);
            if (observed != null) observed.accept(new ObjectiveUpdate(objective.id(), eventKind,
                    progress.objectiveCounter(objective.id()) - previousCounter,
                    !previouslyComplete && progress.objectiveComplete(objective.id())));
        }
        return changed;
    }

    private static void clearCompletedTarget(QuestProgressModel progress, QuestDefinition.Objective objective) {
        if (objective.id().equals(progress.targetObjectiveId()) && progress.objectiveComplete(objective.id())) {
            progress.setTarget(progress.startedVillagerId(), progress.targetDimension(), null, "");
        }
    }

    public record ObjectiveUpdate(String objectiveId, QuestObjectiveEventKind eventKind, int counterDelta, boolean completed) { }
    public static boolean poll(QuestProgressState progress, List<QuestDefinition.Objective> objectives,
            boolean executionAllowed, Predicate<QuestDefinition.Objective> complete,
            Consumer<QuestDefinition.Objective> completed) {
        return poll((com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) progress, objectives, executionAllowed, complete, completed);
    }

    public static boolean dispatch(QuestProgressState progress, List<QuestDefinition.Objective> objectives,
            boolean executionAllowed, QuestObjectiveEventKind eventKind,
            Predicate<QuestDefinition.Objective> matchesEvent, Predicate<QuestDefinition.Objective> complete,
            Consumer<QuestDefinition.Objective> completed, Consumer<ObjectiveUpdate> observed) {
        return dispatch((com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) progress, objectives, executionAllowed, eventKind, matchesEvent, complete, completed, observed);
    }

}
