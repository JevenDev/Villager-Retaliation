package com.jvn.villagerretaliation.quest.tracking;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.function.Predicate;

public record QuestStageReadiness(
        boolean ready,
        String currentStage,
        String nextStage,
        String reason
) {
    public QuestStageReadiness {
        currentStage = currentStage == null ? "" : currentStage;
        nextStage = nextStage == null ? "" : nextStage;
        reason = reason == null ? "" : reason;
    }

    public static QuestStageReadiness forCurrentStage(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            Predicate<QuestDefinition.Objective> objectiveComplete) {
        if (definition == null || progress == null || definition.stages().isEmpty()) {
            return notReady("", "", "stage context unavailable");
        }
        QuestDefinition.Stage stage = definition.stages().get(progress.currentStage());
        if (stage == null) {
            return notReady(progress.currentStage(), "", "current stage missing");
        }
        if (stage.next().isBlank()) {
            return notReady(stage.id(), "", "stage has no next stage");
        }
        if (stage.completeWhen().isEmpty()) {
            return notReady(stage.id(), stage.next(), "stage has no readiness predicates");
        }
        if (!definition.stages().containsKey(stage.next())) {
            return notReady(stage.id(), stage.next(), "next stage missing");
        }
        if (!compositionMet(
                context,
                definition,
                stage.completeWhen(),
                stage.completionMode(),
                stage.completionCount(),
                objectiveComplete)) {
            return notReady(stage.id(), stage.next(), "stage predicates unmet");
        }
        return new QuestStageReadiness(true, stage.id(), stage.next(), "stage ready");
    }

    private static QuestStageReadiness notReady(String currentStage, String nextStage, String reason) {
        return new QuestStageReadiness(false, currentStage, nextStage, reason);
    }

    public static boolean compositionMet(
            DialogueContext context,
            QuestDefinition definition,
            java.util.List<QuestDefinition.StagePredicate> predicates,
            QuestDefinition.CompletionMode mode,
            int requiredCount,
            Predicate<QuestDefinition.Objective> objectiveComplete) {
        if (predicates == null || predicates.isEmpty()) {
            return false;
        }
        int matched = 0;
        for (QuestDefinition.StagePredicate predicate : predicates) {
            if (predicateMet(context, definition, predicate, objectiveComplete)) {
                matched++;
            }
        }
        QuestDefinition.CompletionMode normalized = mode == null ? QuestDefinition.CompletionMode.ALL : mode;
        return switch (normalized) {
            case ALL -> matched == predicates.size();
            case ANY -> matched > 0;
            case AT_LEAST -> matched >= Math.min(Math.max(1, requiredCount), predicates.size());
        };
    }

    private static boolean predicateMet(
            DialogueContext context,
            QuestDefinition definition,
            QuestDefinition.StagePredicate predicate,
            Predicate<QuestDefinition.Objective> objectiveComplete) {
        if (predicate == null || predicate.isEmpty() || objectiveComplete == null) {
            return false;
        }
        if (!predicate.objective().isBlank()) {
            QuestDefinition.Objective objective = definition.objectives().stream()
                    .filter(candidate -> candidate.id().equals(predicate.objective()))
                    .findFirst()
                    .orElse(null);
            if (objective == null || !objectiveComplete.test(objective)) {
                return false;
            }
        }
        return DialogueCondition.matchesAll(context, predicate.conditions());
    }
}
