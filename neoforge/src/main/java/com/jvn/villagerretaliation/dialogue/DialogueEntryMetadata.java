package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record DialogueEntryMetadata(
        String topic,
        Set<String> tags,
        String questline,
        String quest,
        String stage,
        String notes) {
    public static final DialogueEntryMetadata EMPTY = new DialogueEntryMetadata("", Set.of(), "", "", "", "");
    public static final Set<String> FIELD_KEYS = Set.of(
            "metadata");

    private static final Set<String> NESTED_KEYS = Set.of(
            "topic",
            "tags",
            "questline",
            "quest",
            "stage",
            "notes");

    public DialogueEntryMetadata {
        topic = normalizeTopic(topic);
        tags = normalizeTags(tags);
        questline = normalizeId(questline);
        quest = normalizeId(quest);
        stage = normalizeId(stage);
        notes = notes == null ? "" : notes.trim();
    }

    public static DialogueEntryMetadata read(ResourceLocation location, String systemName, String context, JsonObject entry) {
        JsonObject metadata = DatapackJsonReader.readObject(entry, "metadata");
        if (metadata != null) {
            DatapackDiagnostics.warnUnknownKeys(location, systemName, context + " metadata", metadata, NESTED_KEYS);
        }

        Set<String> tags = new LinkedHashSet<>();
        if (metadata != null) {
            tags.addAll(DatapackJsonReader.readStringList(metadata, "tags"));
        }

        String topic = metadata == null ? "" : DatapackJsonReader.readString(metadata, "topic");
        String questline = metadata == null ? "" : DatapackJsonReader.readString(metadata, "questline");
        String quest = metadata == null ? "" : DatapackJsonReader.readString(metadata, "quest");
        String stage = metadata == null ? "" : DatapackJsonReader.readString(metadata, "stage");
        String notes = metadata == null ? "" : DatapackJsonReader.readString(metadata, "notes");

        return new DialogueEntryMetadata(topic, tags, questline, quest, stage, notes);
    }

    public boolean isEmpty() {
        return this.topic.isBlank()
                && this.tags.isEmpty()
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
        return String.join(", ", parts);
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
        return new DialogueEntryMetadata(
                override.topic.isBlank() ? this.topic : override.topic,
                mergedTags,
                override.questline.isBlank() ? this.questline : override.questline,
                override.quest.isBlank() ? this.quest : override.quest,
                override.stage.isBlank() ? this.stage : override.stage,
                override.notes.isBlank() ? this.notes : override.notes
        );
    }

    private static Set<String> normalizeTags(Set<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return Set.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : rawTags) {
            String value = normalizeId(tag);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalizeTopic(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace(':', '.')
                .replace('/', '.')
                .replaceAll("[^a-z0-9_.-]+", "_");
        while (normalized.contains("..")) {
            normalized = normalized.replace("..", ".");
        }
        return normalized.replaceAll("^[._-]+|[._-]+$", "");
    }
}
