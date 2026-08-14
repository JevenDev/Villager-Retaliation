package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.CandidateArbitrator;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** A reusable, stable-id dialogue text candidate shared by every dialogue surface. */
public record DialogueTextVariant(
        String id,
        String text,
        String textKey,
        int priority,
        int weight,
        double chance,
        List<DialogueCondition> conditions,
        DialogueEntryMetadata metadata,
        DialogueUsagePolicy usage
) {
    public DialogueTextVariant {
        id = id == null ? "" : id.trim();
        text = text == null ? "" : text;
        textKey = textKey == null ? "" : textKey.trim();
        weight = Math.max(0, Math.min(10_000, weight));
        chance = Double.isFinite(chance) ? Math.clamp(chance, 0.0D, 1.0D) : 1.0D;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        metadata = metadata == null ? DialogueEntryMetadata.EMPTY : metadata;
        usage = usage == null ? DialogueUsagePolicy.DEFAULT : usage;
    }

    public boolean matches(DialogueContext context) {
        return this.conditions.isEmpty() || DialogueCondition.matchesAll(context, this.conditions);
    }

    public static List<DialogueTextVariant> legacy(
            String ownerId,
            List<String> lines,
            String textKey,
            DialogueEntryMetadata metadata,
            DialogueUsagePolicy usage) {
        List<DialogueTextVariant> variants = new ArrayList<>();
        List<String> safe = lines == null ? List.of() : lines;
        for (int index = 0; index < safe.size(); index++) {
            variants.add(new DialogueTextVariant(
                    legacyId(ownerId, index, safe.size()), safe.get(index),
                    index == 0 ? textKey : "", 0, 1, 1.0D, List.of(), metadata, usage));
        }
        if (variants.isEmpty() && textKey != null && !textKey.isBlank()) {
            variants.add(new DialogueTextVariant(ownerId, "", textKey,
                    0, 1, 1.0D, List.of(), metadata, usage));
        }
        return List.copyOf(variants);
    }

    public static List<DialogueTextVariant> read(
            ResourceLocation location,
            String systemName,
            String context,
            String ownerId,
            JsonElement element,
            ResourceLocation defaultQuestId,
            DialogueEntryMetadata inheritedMetadata,
            DialogueUsagePolicy inheritedUsage) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<JsonElement> raw = element.isJsonArray()
                ? element.getAsJsonArray().asList()
                : List.of(element);
        List<DialogueTextVariant> variants = new ArrayList<>();
        int index = 0;
        for (JsonElement child : raw) {
            if (child.isJsonPrimitive()) {
                variants.add(new DialogueTextVariant(
                        legacyId(ownerId, index, raw.size()), child.getAsString(), "",
                        0, 1, 1.0D, List.of(), inheritedMetadata, inheritedUsage));
            } else if (child.isJsonObject()) {
                JsonObject object = child.getAsJsonObject();
                String localId = DatapackJsonReader.readString(object, "id");
                String text = firstNonBlank(
                        DatapackJsonReader.readString(object, "text"),
                        DatapackJsonReader.readString(object, "line"));
                String textKey = firstNonBlank(
                        DatapackJsonReader.readString(object, "text_key"),
                        DatapackJsonReader.readString(object, "line_key"));
                DialogueEntryMetadata metadata = (inheritedMetadata == null ? DialogueEntryMetadata.EMPTY : inheritedMetadata)
                        .merge(DialogueEntryMetadata.read(location, systemName, context + " variant[" + index + "]", object));
                variants.add(new DialogueTextVariant(
                        stableId(ownerId, localId.isBlank() ? "line_" + index : localId),
                        text,
                        textKey,
                        DatapackJsonReader.readInt(object, "priority", 0),
                        DatapackJsonReader.readInt(object, "weight", 1),
                        DatapackJsonReader.readDouble(object, "chance", 1.0D),
                        DialogueCondition.readList(location, context + " variant[" + index + "]", object, defaultQuestId),
                        metadata,
                        DialogueUsagePolicy.read(object, inheritedUsage)));
            }
            index++;
        }
        return List.copyOf(variants);
    }

    public static java.util.Optional<DialogueTextVariant> select(
            List<DialogueTextVariant> variants,
            DialogueContext context,
            List<String> recentIds) {
        List<String> recent = recentIds == null ? List.of() : recentIds;
        List<DialogueTextVariant> matched = variants == null ? List.of() : variants.stream()
                .filter(variant -> variant.matches(context))
                .filter(variant -> DialogueUsageService.available(context, variant.id(), variant.usage()))
                .filter(variant -> !variant.usage().antiRepeat() || !recent.contains(variant.id()))
                .toList();
        if (matched.isEmpty() && variants != null) {
            matched = variants.stream()
                    .filter(variant -> variant.matches(context))
                    .filter(variant -> DialogueUsageService.available(context, variant.id(), variant.usage()))
                    .toList();
        }
        RandomSource random = context == null ? RandomSource.create(0L) : context.random();
        return CandidateArbitrator.select(
                matched.stream().map(variant -> CandidateArbitrator.Candidate.eligible(
                        variant.id(), variant, variant.priority(), variant.chance(), variant.weight())).toList(),
                random,
                ignored -> true);
    }

    public static java.util.Optional<DialogueTextVariant> selectAndRecord(
            List<DialogueTextVariant> variants,
            DialogueContext context,
            List<String> recentIds) {
        java.util.Optional<DialogueTextVariant> selected = select(variants, context, recentIds);
        selected.ifPresent(variant -> DialogueUsageService.record(context, variant.id(), variant.usage()));
        return selected;
    }

    private static String legacyId(String ownerId, int index, int total) {
        return total == 1 ? ownerId : stableId(ownerId, "line_" + index);
    }

    private static String stableId(String ownerId, String localId) {
        String owner = ownerId == null || ownerId.isBlank() ? "dialogue" : ownerId.trim();
        String local = localId == null || localId.isBlank() ? "line" : localId.trim();
        return local.startsWith(owner + "#") ? local : owner + "#" + local;
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }
}
