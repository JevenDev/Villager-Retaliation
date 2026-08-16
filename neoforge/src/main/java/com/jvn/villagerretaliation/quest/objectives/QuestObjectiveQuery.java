package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class QuestObjectiveQuery {
    private QuestObjectiveQuery() {
    }

    public static List<QuestDefinition.Objective> requiredObjectives(QuestDefinition definition) {
        if (definition == null || definition.objectives().isEmpty()) {
            return List.of();
        }
        return definition.objectives().stream()
                .filter(objective -> !objective.optional())
                .toList();
    }

    public static List<QuestDefinition.Objective> activeObjectives(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (definition == null || definition.objectives().isEmpty()) {
            return List.of();
        }
        if (progress == null || definition.stages().isEmpty()) {
            return definition.objectives();
        }
        return stageObjectives(definition, progress.currentStage());
    }

    public static List<QuestDefinition.Objective> stageObjectives(
            QuestDefinition definition,
            String stageId) {
        if (definition == null || definition.objectives().isEmpty()) {
            return List.of();
        }
        if (definition.stages().isEmpty()) {
            return definition.objectives();
        }
        QuestDefinition.Stage stage = definition.stages().get(stageId == null ? "" : stageId.trim());
        if (stage == null) {
            return List.of();
        }
        if (stage.objectives().isEmpty()) {
            return List.of();
        }
        Map<String, QuestDefinition.Objective> objectivesById = definition.objectives().stream()
                .collect(Collectors.toMap(
                        QuestDefinition.Objective::id,
                        objective -> objective,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new));
        return stage.objectives().stream()
                .map(objectivesById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public static List<QuestDefinition.Objective> requiredObjectives(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return activeObjectives(definition, progress).stream()
                .filter(objective -> !objective.optional())
                .toList();
    }

    public static List<QuestDefinition.Objective> requiredItemHandIns(QuestDefinition definition) {
        return requiredObjectives(definition).stream()
                .filter(QuestObjectiveRegistry::requiresItemHandIn)
                .toList();
    }

    public static List<QuestDefinition.Objective> requiredItemHandIns(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return requiredObjectives(definition, progress).stream()
                .filter(QuestObjectiveRegistry::requiresItemHandIn)
                .toList();
    }

    public static Optional<QuestDefinition.Objective> firstIncompleteRequired(
            QuestDefinition definition,
            Predicate<QuestDefinition.Objective> complete) {
        if (complete == null) {
            return Optional.empty();
        }
        return requiredObjectives(definition).stream()
                .filter(objective -> !complete.test(objective))
                .findFirst();
    }

    public static Optional<QuestDefinition.Objective> firstIncompleteRequired(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            Predicate<QuestDefinition.Objective> complete) {
        if (complete == null) {
            return Optional.empty();
        }
        return requiredObjectives(definition, progress).stream()
                .filter(objective -> !complete.test(objective))
                .findFirst();
    }

    public static List<QuestDefinition.Objective> choiceObjectives(QuestDefinition definition) {
        if (definition == null || definition.objectives().isEmpty()) {
            return List.of();
        }
        return definition.objectives().stream()
                .filter(objective -> objective.type() == QuestDefinition.ObjectiveType.CHOICE)
                .toList();
    }
}
