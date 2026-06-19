package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record QuestTriggerIndex(
        List<CompiledQuestTrigger> triggers,
        Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> triggersByEvent,
        Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> globalTriggersByEvent,
        Map<QuestDefinition.TriggerEvent, Map<String, List<CompiledQuestTrigger>>> stageTriggersByEvent,
        List<CompiledQuestTrigger> continuousTriggers
) {
    public static final QuestTriggerIndex EMPTY = new QuestTriggerIndex(
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of());

    public QuestTriggerIndex {
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        triggersByEvent = freezeEventMap(triggersByEvent);
        globalTriggersByEvent = freezeEventMap(globalTriggersByEvent);
        stageTriggersByEvent = freezeStageMap(stageTriggersByEvent);
        continuousTriggers = continuousTriggers == null ? List.of() : List.copyOf(continuousTriggers);
    }

    public boolean hasEvent(QuestDefinition.TriggerEvent event) {
        return event != null && this.triggersByEvent.containsKey(event);
    }

    public Set<QuestDefinition.TriggerEvent> events() {
        return this.triggersByEvent.keySet();
    }

    public List<CompiledQuestTrigger> candidates(QuestDefinition.TriggerEvent event, String currentStage) {
        if (event == null) {
            return List.of();
        }
        List<CompiledQuestTrigger> global = this.globalTriggersByEvent.getOrDefault(event, List.of());
        String stage = normalizeStage(currentStage);
        if (stage.isBlank()) {
            return global;
        }
        List<CompiledQuestTrigger> stageLocal = this.stageTriggersByEvent
                .getOrDefault(event, Map.of())
                .getOrDefault(stage, List.of());
        if (global.isEmpty()) {
            return stageLocal;
        }
        if (stageLocal.isEmpty()) {
            return global;
        }
        List<CompiledQuestTrigger> merged = new ArrayList<>(global.size() + stageLocal.size());
        merged.addAll(global);
        merged.addAll(stageLocal);
        merged.sort(Comparator.comparingInt(CompiledQuestTrigger::index));
        return List.copyOf(merged);
    }

    static String normalizeStage(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> freezeEventMap(
            Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> frozen =
                new EnumMap<>(QuestDefinition.TriggerEvent.class);
        for (Map.Entry<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return frozen.isEmpty() ? Map.of() : Collections.unmodifiableMap(frozen);
    }

    private static Map<QuestDefinition.TriggerEvent, Map<String, List<CompiledQuestTrigger>>> freezeStageMap(
            Map<QuestDefinition.TriggerEvent, Map<String, List<CompiledQuestTrigger>>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<QuestDefinition.TriggerEvent, Map<String, List<CompiledQuestTrigger>>> frozen =
                new EnumMap<>(QuestDefinition.TriggerEvent.class);
        for (Map.Entry<QuestDefinition.TriggerEvent, Map<String, List<CompiledQuestTrigger>>> eventEntry : values.entrySet()) {
            if (eventEntry.getKey() == null || eventEntry.getValue() == null || eventEntry.getValue().isEmpty()) {
                continue;
            }
            Map<String, List<CompiledQuestTrigger>> stages = new LinkedHashMap<>();
            for (Map.Entry<String, List<CompiledQuestTrigger>> stageEntry : eventEntry.getValue().entrySet()) {
                String stage = normalizeStage(stageEntry.getKey());
                if (!stage.isBlank() && stageEntry.getValue() != null && !stageEntry.getValue().isEmpty()) {
                    stages.put(stage, List.copyOf(stageEntry.getValue()));
                }
            }
            if (!stages.isEmpty()) {
                frozen.put(eventEntry.getKey(), Collections.unmodifiableMap(stages));
            }
        }
        return frozen.isEmpty() ? Map.of() : Collections.unmodifiableMap(frozen);
    }
}
