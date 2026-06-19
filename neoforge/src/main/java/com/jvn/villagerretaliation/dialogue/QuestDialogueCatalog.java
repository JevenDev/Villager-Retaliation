package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.quest.compiled.QuestSourcePointer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record QuestDialogueCatalog(
        Map<ResourceLocation, DialogueTreeDefinition> trees,
        Map<BindingKey, Binding> bindings,
        Map<ResourceLocation, QuestSourcePointer> treeSources
) {
    private static final QuestDialogueCatalog EMPTY =
            new QuestDialogueCatalog(Map.of(), Map.of(), Map.of());

    public QuestDialogueCatalog {
        trees = immutableCopy(trees);
        bindings = immutableCopy(bindings);
        treeSources = immutableCopy(treeSources);
    }

    public static QuestDialogueCatalog empty() {
        return EMPTY;
    }

    public static QuestDialogueCatalog merge(Collection<QuestDialogueCatalog> catalogs) {
        if (catalogs == null || catalogs.isEmpty()) {
            return empty();
        }
        Map<ResourceLocation, DialogueTreeDefinition> trees = new LinkedHashMap<>();
        Map<BindingKey, Binding> bindings = new LinkedHashMap<>();
        Map<ResourceLocation, QuestSourcePointer> treeSources = new LinkedHashMap<>();
        for (QuestDialogueCatalog catalog : catalogs) {
            if (catalog == null) {
                continue;
            }
            trees.putAll(catalog.trees());
            bindings.putAll(catalog.bindings());
            treeSources.putAll(catalog.treeSources());
        }
        if (trees.isEmpty() && bindings.isEmpty() && treeSources.isEmpty()) {
            return empty();
        }
        return new QuestDialogueCatalog(trees, bindings, treeSources);
    }

    public Optional<DialogueTreeDefinition> tree(ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(this.trees.get(id));
    }

    public List<Binding> bindings(ResourceLocation questId) {
        if (questId == null || this.bindings.isEmpty()) {
            return List.of();
        }
        List<Binding> matches = new ArrayList<>();
        for (Binding binding : this.bindings.values()) {
            if (questId.equals(binding.questId())) {
                matches.add(binding);
            }
        }
        return List.copyOf(matches);
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public record BindingKey(ResourceLocation questId, String stageId, String slot) {
        public BindingKey {
            stageId = stageId == null ? "" : stageId;
            slot = slot == null ? "" : slot;
        }
    }

    public record Binding(
            ResourceLocation questId,
            String stageId,
            String slot,
            String sceneId,
            ResourceLocation treeId,
            String entryId,
            QuestSourcePointer source
    ) {
        public Binding {
            stageId = stageId == null ? "" : stageId;
            slot = slot == null ? "" : slot;
            sceneId = sceneId == null ? "" : sceneId;
            entryId = entryId == null ? "" : entryId;
        }

        public BindingKey key() {
            return new BindingKey(this.questId, this.stageId, this.slot);
        }
    }
}
