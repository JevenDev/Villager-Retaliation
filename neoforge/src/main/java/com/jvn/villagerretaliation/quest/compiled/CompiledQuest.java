package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTriggerIndex;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

public record CompiledQuest(
        ResourceLocation id,
        QuestSourcePointer source,
        QuestDefinition compatibilityDefinition,
        CompiledQuestMetadata metadata,
        CompiledQuestProvider provider,
        QuestDefinition.Target target,
        String entryStage,
        List<ResourceLocation> prerequisites,
        QuestDefinition.Rules rules,
        CompiledQuestUi ui,
        List<CompiledQuestObjective> objectives,
        Map<String, CompiledQuestObjective> objectivesById,
        List<CompiledQuestStage> stages,
        Map<String, CompiledQuestStage> stagesById,
        List<CompiledQuestTrigger> triggers,
        Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> triggersByEvent,
        QuestTriggerIndex triggerIndex,
        CompiledQuestRewards rewards,
        boolean showLockedAdventureHint,
        QuestDefinition.Revision revision,
        List<com.jvn.villagerretaliation.quest.discovery.QuestDiscoveryDefinition> discovery
) {
    public CompiledQuest(
            ResourceLocation id,
            QuestSourcePointer source,
            QuestDefinition compatibilityDefinition,
            CompiledQuestMetadata metadata,
            CompiledQuestProvider provider,
            QuestDefinition.Target target,
            String entryStage,
            List<ResourceLocation> prerequisites,
            QuestDefinition.Rules rules,
            CompiledQuestUi ui,
            List<CompiledQuestObjective> objectives,
            Map<String, CompiledQuestObjective> objectivesById,
            List<CompiledQuestStage> stages,
            Map<String, CompiledQuestStage> stagesById,
            List<CompiledQuestTrigger> triggers,
            Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> triggersByEvent,
            QuestTriggerIndex triggerIndex,
            CompiledQuestRewards rewards,
            boolean showLockedAdventureHint,
            QuestDefinition.Revision revision) {
        this(id, source, compatibilityDefinition, metadata, provider, target, entryStage, prerequisites, rules, ui, objectives, objectivesById, stages, stagesById, triggers, triggersByEvent, triggerIndex, rewards, showLockedAdventureHint, revision, List.of());
    }

    public CompiledQuest {
        if (id == null) {
            throw new IllegalArgumentException("compiled quest id must not be null");
        }
        source = source == null ? new QuestSourcePointer(id, QuestSchemaVersion.V1, "", "") : source;
        metadata = metadata == null
                ? new CompiledQuestMetadata("", "", "", "", "", java.util.Set.of(), null, null)
                : metadata;
        provider = provider == null ? new CompiledQuestProvider(null) : provider;
        target = target == null ? QuestDefinition.Target.EMPTY : target;
        entryStage = entryStage == null ? "" : entryStage.trim();
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        rules = rules == null ? QuestDefinition.Rules.DEFAULT : rules;
        ui = ui == null ? new CompiledQuestUi(null, null, null) : ui;
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        objectivesById = index(objectives, CompiledQuestObjective::id);
        List<CompiledQuestStage> resolvedStages = new ArrayList<>();
        for (CompiledQuestStage stage : stages == null ? List.<CompiledQuestStage>of() : stages) {
            List<CompiledQuestObjective> stageObjectives = stage.definition().objectives().stream()
                    .map(objectivesById::get).filter(Objects::nonNull).toList();
            resolvedStages.add(new CompiledQuestStage(stage.id(), stage.index(), stage.definition(),
                    stageObjectives, stage.source()));
        }
        stages = List.copyOf(resolvedStages);
        stagesById = index(stages, CompiledQuestStage::id);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        triggerIndex = QuestTriggerRegistry.index(triggers);
        triggersByEvent = triggerIndex.triggersByEvent();
        rewards = rewards == null ? new CompiledQuestRewards(null) : rewards;
        revision = revision == null ? QuestDefinition.Revision.DEFAULT : revision;
        discovery = discovery == null ? List.of() : List.copyOf(discovery);
        Map<String, QuestDefinition.Stage> stageDefinitions = new LinkedHashMap<>();
        stages.forEach(stage -> stageDefinitions.put(stage.id(), stage.definition()));
        // derive the compatibility view from typed fields so runtime paths cannot disagree
        compatibilityDefinition = new QuestDefinition(id, metadata.title(), metadata.description(),
                metadata.titleKey(), metadata.descriptionKey(), metadata.questline(), metadata.tags(),
                metadata.parent(), prerequisites, showLockedAdventureHint, provider.offer(), target,
                objectives.stream().map(CompiledQuestObjective::definition).toList(), rules, ui.tracker(),
                entryStage, stageDefinitions, triggers.stream().map(CompiledQuestTrigger::definition).toList(),
                rewards.definition(), ui.dialogue(), metadata.dialogue(), ui.links(), revision, metadata.campaign());
    }

    public CompiledQuest(
            ResourceLocation id, QuestSourcePointer source, QuestDefinition compatibilityDefinition,
            CompiledQuestMetadata metadata, CompiledQuestProvider provider, QuestDefinition.Target target,
            String entryStage, List<ResourceLocation> prerequisites, QuestDefinition.Rules rules, CompiledQuestUi ui,
            List<CompiledQuestObjective> objectives, Map<String, CompiledQuestObjective> objectivesById,
            List<CompiledQuestStage> stages, Map<String, CompiledQuestStage> stagesById,
            List<CompiledQuestTrigger> triggers, Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> triggersByEvent,
            QuestTriggerIndex triggerIndex, CompiledQuestRewards rewards) {
        this(id, source, compatibilityDefinition, metadata, provider, target, entryStage, prerequisites, rules, ui,
                objectives, objectivesById, stages, stagesById, triggers, triggersByEvent, triggerIndex, rewards,
                Objects.requireNonNull(compatibilityDefinition, "compatibility definition").showLockedAdventureHint(),
                compatibilityDefinition.revision());
    }

    public QuestSchemaVersion schemaVersion() {
        return this.source.schemaVersion();
    }

    public QuestDefinition asQuestDefinition() {
        return this.compatibilityDefinition;
    }

    private static <T> Map<String, T> index(List<T> definitions, Function<T, String> id) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (T definition : definitions) {
            String key = id.apply(definition);
            if (indexed.putIfAbsent(key, definition) != null) {
                throw new IllegalArgumentException("duplicate compiled definition: " + key);
            }
        }
        return Collections.unmodifiableMap(indexed);
    }
}
