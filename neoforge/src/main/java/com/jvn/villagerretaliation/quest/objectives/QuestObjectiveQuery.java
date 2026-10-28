package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

public final class QuestObjectiveQuery {
    private static final int MAX_INDEXED_DEFINITIONS = 512;
    private static final Map<ResourceLocation, StageIndex> STAGE_OBJECTIVES = new LinkedHashMap<>(16, 0.75F, true);

    private record StageIndex(QuestDefinition definition, Map<String, List<QuestDefinition.Objective>> stages) { }

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
        String normalizedStage = stageId == null ? "" : stageId.trim();
        QuestDefinition.Stage stage = definition.stages().get(normalizedStage);
        if (stage == null) {
            return List.of();
        }
        if (stage.objectives().isEmpty()) {
            return List.of();
        }
        return indexedStages(definition).get(normalizedStage);
    }

    public static synchronized void clearCache() {
        STAGE_OBJECTIVES.clear();
    }

    private static synchronized Map<String, List<QuestDefinition.Objective>> indexedStages(QuestDefinition definition) {
        var cached = STAGE_OBJECTIVES.get(definition.id());
        if (cached != null && cached.definition() == definition) return cached.stages();
        Map<String, QuestDefinition.Objective> byId = new LinkedHashMap<>();
        definition.objectives().forEach(objective -> byId.putIfAbsent(objective.id(), objective));
        Map<String, List<QuestDefinition.Objective>> stages = new LinkedHashMap<>();
        definition.stages().forEach((id, stage) -> stages.put(id, stage.objectives().stream()
                .map(byId::get).filter(java.util.Objects::nonNull).toList()));
        var resolved = Map.copyOf(stages);
        STAGE_OBJECTIVES.put(definition.id(), new StageIndex(definition, resolved));
        if (STAGE_OBJECTIVES.size() > MAX_INDEXED_DEFINITIONS) {
            var oldest = STAGE_OBJECTIVES.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return resolved;
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
