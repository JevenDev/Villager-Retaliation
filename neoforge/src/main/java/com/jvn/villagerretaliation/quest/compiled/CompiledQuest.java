package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTriggerIndex;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        CompiledQuestRewards rewards
) {
    public CompiledQuest {
        if (id == null) {
            throw new IllegalArgumentException("compiled quest id must not be null");
        }
        if (compatibilityDefinition == null) {
            throw new IllegalArgumentException("compiled quest compatibility definition must not be null");
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
        objectivesById = freezeOrderedMap(objectivesById);
        stages = stages == null ? List.of() : List.copyOf(stages);
        stagesById = freezeOrderedMap(stagesById);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        triggerIndex = triggerIndex == null ? QuestTriggerRegistry.index(triggers) : triggerIndex;
        triggersByEvent = freezeTriggerMap(triggersByEvent == null || triggersByEvent.isEmpty()
                ? triggerIndex.triggersByEvent()
                : triggersByEvent);
        rewards = rewards == null ? new CompiledQuestRewards(null) : rewards;
    }

    public QuestSchemaVersion schemaVersion() {
        return this.source.schemaVersion();
    }

    public QuestDefinition asQuestDefinition() {
        return this.compatibilityDefinition;
    }

    private static <K, V> Map<K, V> freezeOrderedMap(Map<K, V> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> freezeTriggerMap(
            Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> frozen =
                new EnumMap<>(QuestDefinition.TriggerEvent.class);
        for (Map.Entry<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> entry : values.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }
}
