package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public final class QuestObjectiveQuery {
    private QuestObjectiveQuery() {
    }

    public static ResourceLocation resolvedItem(
            QuestDefinition.Objective objective,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressModel progress) {
        if (objective == null) {
            return null;
        }
        return objective.usesRandomItemSelection()
                ? progress == null ? null : progress.resolvedObjectiveItem(objective.id())
                : objective.item();
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
            com.jvn.villagerretaliation.quest.runtime.QuestProgressModel progress) {
        if (definition == null || definition.objectives().isEmpty()) {
            return List.of();
        }
        if (progress == null || definition.stages().isEmpty()) {
            return definition.objectives();
        }
        return progress.activeStages().stream().flatMap(stage -> stageObjectives(definition, stage).stream()).distinct().toList();
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
            com.jvn.villagerretaliation.quest.runtime.QuestProgressModel progress) {
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
            com.jvn.villagerretaliation.quest.runtime.QuestProgressModel progress) {
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
            com.jvn.villagerretaliation.quest.runtime.QuestProgressModel progress,
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

    public static ResourceLocation resolvedItem(
            QuestDefinition.Objective objective,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
        return resolvedItem(objective, (com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) progress);
    }

    public static List<QuestDefinition.Objective> activeObjectives(
            QuestDefinition definition,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
        return activeObjectives(definition, (com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) progress);
    }

    public static List<QuestDefinition.Objective> requiredObjectives(
            QuestDefinition definition,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
        return requiredObjectives(definition, (com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) progress);
    }

    public static List<QuestDefinition.Objective> requiredItemHandIns(
            QuestDefinition definition,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
        return requiredItemHandIns(definition, (com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) progress);
    }

    public static Optional<QuestDefinition.Objective> firstIncompleteRequired(
            QuestDefinition definition,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress,
            Predicate<QuestDefinition.Objective> complete) {
        return firstIncompleteRequired(definition, (com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) progress, complete);
    }

}
