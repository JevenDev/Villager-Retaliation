package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.ContentTags;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record DialogueEntryMetadata(
        String topic,
        Set<String> tags,
        Set<String> routingTags,
        Set<String> antiRepeatGroups,
        String questline,
        String quest,
        String stage,
        String notes) {
    public static final DialogueEntryMetadata EMPTY = new DialogueEntryMetadata(
            "", Set.of(), Set.of(), Set.of(), "", "", "", "");
    public static final Set<String> FIELD_KEYS = Set.of(
            "metadata");

    private static final Set<String> NESTED_KEYS = Set.of(
            "topic",
            "tags",
            "routing_tags",
            "anti_repeat_groups",
            "questline",
            "quest",
            "stage",
            "notes");

    public DialogueEntryMetadata {
        topic = ContentTags.normalize(topic);
        tags = ContentTags.normalizeAll(tags);
        routingTags = ContentTags.normalizeAll(routingTags);
        antiRepeatGroups = ContentTags.normalizeAll(antiRepeatGroups);
        questline = ContentTags.normalize(questline);
        quest = ContentTags.normalize(quest);
        stage = ContentTags.normalize(stage);
        notes = notes == null ? "" : notes.trim();
    }

    public DialogueEntryMetadata(
            String topic,
            Set<String> tags,
            String questline,
            String quest,
            String stage,
            String notes) {
        this(topic, tags, Set.of(), Set.of(), questline, quest, stage, notes);
    }

    public static DialogueEntryMetadata read(ResourceLocation location, String systemName, String context, JsonObject entry) {
        JsonObject metadata = DatapackJsonReader.readObject(entry, "metadata");
        if (metadata != null) {
            DatapackDiagnostics.warnUnknownKeys(location, systemName, context + " metadata", metadata, NESTED_KEYS);
        }

        Set<String> tags = new LinkedHashSet<>();
        Set<String> routingTags = new LinkedHashSet<>();
        Set<String> antiRepeatGroups = new LinkedHashSet<>();
        if (metadata != null) {
            tags.addAll(DatapackJsonReader.readStringList(metadata, "tags"));
            routingTags.addAll(DatapackJsonReader.readStringList(metadata, "routing_tags"));
            antiRepeatGroups.addAll(DatapackJsonReader.readStringList(metadata, "anti_repeat_groups"));
        }

        String topic = metadata == null ? "" : DatapackJsonReader.readString(metadata, "topic");
        String questline = metadata == null ? "" : DatapackJsonReader.readString(metadata, "questline");
        String quest = metadata == null ? "" : DatapackJsonReader.readString(metadata, "quest");
        String stage = metadata == null ? "" : DatapackJsonReader.readString(metadata, "stage");
        String notes = metadata == null ? "" : DatapackJsonReader.readString(metadata, "notes");

        return new DialogueEntryMetadata(
                topic, tags, routingTags, antiRepeatGroups, questline, quest, stage, notes);
    }

    public boolean isEmpty() {
        return this.topic.isBlank()
                && this.tags.isEmpty()
                && this.routingTags.isEmpty()
                && this.antiRepeatGroups.isEmpty()
                && this.questline.isBlank()
                && this.quest.isBlank()
                && this.stage.isBlank()
                && this.notes.isBlank();
    }

    public String summary() {
        if (isEmpty()) {
            return "";
        }

        java.util.List<String> parts = new java.util.ArrayList<>();
        if (!this.topic.isBlank()) {
            parts.add("topic=" + this.topic);
        }
        if (!this.questline.isBlank()) {
            parts.add("questline=" + this.questline);
        }
        if (!this.quest.isBlank()) {
            parts.add("quest=" + this.quest);
        }
        if (!this.stage.isBlank()) {
            parts.add("stage=" + this.stage);
        }
        if (!this.tags.isEmpty()) {
            parts.add("tags=" + String.join("|", this.tags));
        }
        if (!this.routingTags.isEmpty()) {
            parts.add("routing_tags=" + String.join("|", this.routingTags));
        }
        if (!this.antiRepeatGroups.isEmpty()) {
            parts.add("anti_repeat_groups=" + String.join("|", this.antiRepeatGroups));
        }
        return String.join(", ", parts);
    }

    public Set<String> effectiveRoutingTags() {
        if (!this.routingTags.isEmpty()) {
            return this.routingTags;
        }
        return this.tags.stream()
                .filter(tag -> !ContentTags.isStructuralDialogueTag(tag))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<String> effectiveAntiRepeatGroups() {
        if (!this.antiRepeatGroups.isEmpty()) {
            return this.antiRepeatGroups;
        }
        return this.topic.isBlank() ? Set.of() : Set.of(this.topic);
    }

    public DialogueEntryMetadata merge(DialogueEntryMetadata override) {
        if (override == null || override.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return override;
        }

        Set<String> mergedTags = new LinkedHashSet<>(this.tags);
        mergedTags.addAll(override.tags);
        Set<String> mergedRoutingTags = new LinkedHashSet<>(this.routingTags);
        mergedRoutingTags.addAll(override.routingTags);
        Set<String> mergedAntiRepeatGroups = new LinkedHashSet<>(this.antiRepeatGroups);
        mergedAntiRepeatGroups.addAll(override.antiRepeatGroups);
        return new DialogueEntryMetadata(
                override.topic.isBlank() ? this.topic : override.topic,
                mergedTags,
                mergedRoutingTags,
                mergedAntiRepeatGroups,
                override.questline.isBlank() ? this.questline : override.questline,
                override.quest.isBlank() ? this.quest : override.quest,
                override.stage.isBlank() ? this.stage : override.stage,
                override.notes.isBlank() ? this.notes : override.notes
        );
    }

}
