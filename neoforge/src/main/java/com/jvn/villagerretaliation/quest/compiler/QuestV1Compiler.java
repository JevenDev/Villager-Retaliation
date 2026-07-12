package com.jvn.villagerretaliation.quest.compiler;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTriggerIndex;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestMetadata;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestObjective;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestProvider;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestRewards;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestStage;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestUi;
import com.jvn.villagerretaliation.quest.compiled.QuestSourcePointer;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class QuestV1Compiler {
    private QuestV1Compiler() {
    }

    public static CompiledQuest compile(QuestDefinition definition, QuestResourceEnvelope envelope) {
        if (definition == null) {
            throw new IllegalArgumentException("quest definition must not be null");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("quest resource envelope must not be null");
        }

        QuestSourcePointer source = QuestSourcePointer.from(envelope);
        List<CompiledQuestObjective> objectives = compileObjectives(definition, source);
        Map<String, CompiledQuestObjective> objectivesById = indexObjectives(objectives);
        List<CompiledQuestStage> stages = compileStages(definition, objectivesById, source);
        Map<String, CompiledQuestStage> stagesById = indexStages(stages);
        List<CompiledQuestTrigger> triggers = compileTriggers(definition, source);
        QuestTriggerIndex triggerIndex = QuestTriggerRegistry.index(triggers);

        return new CompiledQuest(
                definition.id(),
                source,
                definition,
                new CompiledQuestMetadata(
                        definition.title(),
                        definition.description(),
                        definition.titleKey(),
                        definition.descriptionKey(),
                        definition.questline(),
                        definition.tags(),
                        definition.parent(),
                        definition.metadata()),
                new CompiledQuestProvider(definition.offer()),
                definition.target(),
                "",
                definition.prerequisites(),
                definition.rules(),
                new CompiledQuestUi(definition.tracker(), definition.dialogue(), definition.links()),
                objectives,
                objectivesById,
                stages,
                stagesById,
                triggers,
                triggerIndex.triggersByEvent(),
                triggerIndex,
                new CompiledQuestRewards(definition.rewards()));
    }

    private static List<CompiledQuestObjective> compileObjectives(
            QuestDefinition definition,
            QuestSourcePointer source) {
        List<CompiledQuestObjective> objectives = new ArrayList<>();
        int index = 0;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            objectives.add(new CompiledQuestObjective(
                    objective.id(),
                    index,
                    objective,
                    source.child("objectives", Integer.toString(index))));
            index++;
        }
        return List.copyOf(objectives);
    }

    private static Map<String, CompiledQuestObjective> indexObjectives(List<CompiledQuestObjective> objectives) {
        Map<String, CompiledQuestObjective> byId = new LinkedHashMap<>();
        for (CompiledQuestObjective objective : objectives) {
            byId.put(objective.id(), objective);
        }
        return byId;
    }

    private static List<CompiledQuestStage> compileStages(
            QuestDefinition definition,
            Map<String, CompiledQuestObjective> objectivesById,
            QuestSourcePointer source) {
        List<CompiledQuestStage> stages = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, QuestDefinition.Stage> entry : definition.stages().entrySet()) {
            stages.add(new CompiledQuestStage(
                    entry.getKey(),
                    index,
                    entry.getValue(),
                    resolveStageObjectives(entry.getValue(), objectivesById),
                    source.child("stages", entry.getKey())));
            index++;
        }
        return List.copyOf(stages);
    }

    private static List<CompiledQuestObjective> resolveStageObjectives(
            QuestDefinition.Stage stage,
            Map<String, CompiledQuestObjective> objectivesById) {
        return stage.objectives().stream()
                .map(objectivesById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Map<String, CompiledQuestStage> indexStages(List<CompiledQuestStage> stages) {
        Map<String, CompiledQuestStage> byId = new LinkedHashMap<>();
        for (CompiledQuestStage stage : stages) {
            byId.put(stage.id(), stage);
        }
        return byId;
    }

    private static List<CompiledQuestTrigger> compileTriggers(
            QuestDefinition definition,
            QuestSourcePointer source) {
        List<CompiledQuestTrigger> triggers = new ArrayList<>();
        int index = 0;
        for (QuestDefinition.Trigger trigger : definition.triggers()) {
            triggers.add(new CompiledQuestTrigger(
                    trigger.id(),
                    index,
                    trigger,
                    source.child("triggers", Integer.toString(index))));
            index++;
        }
        return List.copyOf(triggers);
    }

}
