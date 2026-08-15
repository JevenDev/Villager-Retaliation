package com.jvn.villagerretaliation.quest.content;

import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTriggerIndex;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestCatalog;
import com.jvn.villagerretaliation.quest.compiled.QuestSourcePointer;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import com.jvn.villagerretaliation.quest.pool.QuestPoolDefinition;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** One immutable, internally consistent view of all quest-owned gameplay content. */
public record QuestContentCatalog(
        long generation,
        CompiledQuestCatalog compiledQuestCatalog,
        QuestDialogueCatalog dialogueCatalog,
        Map<ResourceLocation, QuestDefinition> quests,
        Map<QuestObjectiveEventKind, Set<ResourceLocation>> objectiveEventQuestIds,
        Set<ResourceLocation> factQuestIds,
        Map<ResourceLocation, Set<ResourceLocation>> memoryEventQuestIds,
        Map<ResourceLocation, Set<ResourceLocation>> exclusiveGroupQuestIds,
        Map<QuestDefinition.TriggerEvent, Set<ResourceLocation>> triggerEventQuestIds,
        Map<ResourceLocation, CompiledScene> scenes,
        Map<ResourceLocation, EncounterTemplate> encounters,
        List<QuestPoolDefinition> pools) {
    public QuestContentCatalog {
        compiledQuestCatalog = compiledQuestCatalog == null
                ? new CompiledQuestCatalog(Map.of())
                : compiledQuestCatalog;
        dialogueCatalog = dialogueCatalog == null ? QuestDialogueCatalog.empty() : dialogueCatalog;
        quests = freezeMap(quests);
        objectiveEventQuestIds = freezeEnumSetMap(objectiveEventQuestIds);
        factQuestIds = factQuestIds == null ? Set.of() : Set.copyOf(factQuestIds);
        memoryEventQuestIds = freezeSetMap(memoryEventQuestIds);
        exclusiveGroupQuestIds = freezeSetMap(exclusiveGroupQuestIds);
        triggerEventQuestIds = freezeEnumSetMap(triggerEventQuestIds);
        scenes = freezeMap(scenes);
        encounters = freezeMap(encounters);
        pools = pools == null ? List.of() : List.copyOf(pools);
    }

    public static QuestContentCatalog empty() {
        return new QuestContentCatalog(
                0L,
                new CompiledQuestCatalog(Map.of()),
                QuestDialogueCatalog.empty(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of());
    }

    public Collection<QuestDefinition> questDefinitions() {
        return this.quests.values();
    }

    public Collection<CompiledQuest> compiledQuests() {
        return this.compiledQuestCatalog.quests();
    }

    public Optional<QuestDefinition> quest(ResourceLocation id) {
        return Optional.ofNullable(id == null ? null : this.quests.get(id));
    }

    public Optional<CompiledQuest> compiledQuest(ResourceLocation id) {
        return id == null ? Optional.empty() : this.compiledQuestCatalog.quest(id);
    }

    public Optional<QuestSourcePointer> objectiveSource(ResourceLocation questId, String objectiveId) {
        if (objectiveId == null || objectiveId.isBlank()) {
            return Optional.empty();
        }
        return compiledQuest(questId)
                .map(quest -> quest.objectivesById().get(objectiveId))
                .map(objective -> objective == null ? null : objective.source());
    }

    public Optional<QuestTriggerIndex> questTriggerIndex(ResourceLocation id) {
        return compiledQuest(id).map(CompiledQuest::triggerIndex);
    }

    public Set<ResourceLocation> questIdsForObjectiveEvent(QuestObjectiveEventKind kind) {
        return kind == null ? Set.of() : this.objectiveEventQuestIds.getOrDefault(kind, Set.of());
    }

    public Set<ResourceLocation> memoryEventQuestIds(ResourceLocation memoryTag) {
        return memoryTag == null ? Set.of() : this.memoryEventQuestIds.getOrDefault(memoryTag, Set.of());
    }

    public Set<ResourceLocation> exclusiveGroupQuestIds(ResourceLocation group) {
        return group == null ? Set.of() : this.exclusiveGroupQuestIds.getOrDefault(group, Set.of());
    }

    public boolean hasQuestTrigger(ResourceLocation id, QuestDefinition.TriggerEvent event) {
        return id != null && event != null
                && this.triggerEventQuestIds.getOrDefault(event, Set.of()).contains(id);
    }

    public Optional<CompiledScene> scene(ResourceLocation id) {
        return Optional.ofNullable(id == null ? null : this.scenes.get(id));
    }

    public Optional<EncounterTemplate> encounter(ResourceLocation id) {
        return Optional.ofNullable(id == null ? null : this.encounters.get(id));
    }

    private static <K, V> Map<K, V> freezeMap(Map<K, V> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static <K> Map<K, Set<ResourceLocation>> freezeSetMap(
            Map<K, Set<ResourceLocation>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<K, Set<ResourceLocation>> frozen = new LinkedHashMap<>();
        values.forEach((key, value) -> frozen.put(key, value == null ? Set.of() : Set.copyOf(value)));
        return Collections.unmodifiableMap(frozen);
    }

    private static <E extends Enum<E>> Map<E, Set<ResourceLocation>> freezeEnumSetMap(
            Map<E, Set<ResourceLocation>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        E first = values.keySet().stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (first == null) {
            return Map.of();
        }
        Map<E, Set<ResourceLocation>> frozen = new EnumMap<>(first.getDeclaringClass());
        values.forEach((key, value) -> frozen.put(key, value == null ? Set.of() : Set.copyOf(value)));
        return Collections.unmodifiableMap(frozen);
    }
}
