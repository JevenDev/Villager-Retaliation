package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public record DialogueWordPool(List<Entry> entries) {
    public static final DialogueWordPool EMPTY = new DialogueWordPool(List.of());
    private static final ResourceLocation SOURCE = ResourceLocation.parse("villagerretaliation:dialogue_words");

    public DialogueWordPool { entries = List.copyOf(entries); }

    public record Entry(String text, int weight, List<DialogueCondition> conditions) {
        public Entry { conditions = List.copyOf(conditions); }
    }

    public List<String> values() { return entries.stream().map(Entry::text).toList(); }

    public String select(DialogueRuntimeContext context, RandomSource random) {
        return selectMatching(conditions -> context != null && DialogueCondition.matchesAll(context, conditions), random);
    }

    public String selectMatching(java.util.function.Predicate<List<DialogueCondition>> matches, RandomSource random) {
        var matching = entries.stream().filter(entry -> !entry.conditions().isEmpty() && matches.test(entry.conditions())).toList();
        var eligible = matching.isEmpty() ? entries.stream().filter(entry -> entry.conditions().isEmpty()).toList() : matching;
        int total = eligible.stream().mapToInt(Entry::weight).sum();
        if (total == 0) return null;
        int roll = random.nextInt(total);
        for (var entry : eligible) {
            roll -= entry.weight();
            if (roll < 0) return entry.text();
        }
        throw new IllegalStateException("Word pool weight total changed during selection");
    }

    private static final class ConditionSchema {
        private static final JsonObject VALUE = create();
        private static JsonObject create() {
            var schema = new JsonObject();
            schema.add("$defs", com.jvn.villagerretaliation.quest.schema.ContentSchemaDefinitions.export().get("$defs"));
            schema.addProperty("$ref", "#/$defs/condition");
            return schema;
        }
    }

    public static DialogueWordPool read(JsonObject root) {
        if (!root.keySet().equals(Set.of("values")) || !root.get("values").isJsonArray()
                || root.getAsJsonArray("values").isEmpty() || root.getAsJsonArray("values").size() > 256)
            throw new IllegalArgumentException("word pools require 1 to 256 values and no unknown fields");
        var entries = new ArrayList<Entry>();
        for (var value : root.getAsJsonArray("values")) {
            JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
            if (object != null && (object.keySet().stream().anyMatch(key -> !Set.of("text", "weight", "conditions").contains(key))
                    || !object.has("text"))) throw new IllegalArgumentException("word entry requires text and accepts only weight and conditions");
            var literal = object == null ? value : object.get("text");
            if (!literal.isJsonPrimitive() || !literal.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("word pool text must be a string");
            String text = literal.getAsString();
            if (text.isBlank() || text.length() > 512 || text.chars().anyMatch(c -> "{}<>\u00a7".indexOf(c) >= 0 || Character.isISOControl(c)))
                throw new IllegalArgumentException("word pool text must be nonblank literal text without placeholders, markup, or control characters, at most 512 characters");
            int weight = 1;
            if (object != null && object.has("weight")) {
                var number = object.get("weight");
                if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()
                        || number.getAsDouble() < 1 || number.getAsDouble() > 1000 || number.getAsDouble() != number.getAsInt())
                    throw new IllegalArgumentException("word weight must be an integer from 1 to 1000");
                weight = number.getAsInt();
            }
            if (object != null && object.has("conditions")) {
                if (!object.get("conditions").isJsonArray()) throw new IllegalArgumentException("word conditions must be an array");
                for (var condition : object.getAsJsonArray("conditions")) {
                    if (!condition.isJsonObject()) throw new IllegalArgumentException("word conditions must be objects");
                    if (!QuestJsonSchema.validate(condition, ConditionSchema.VALUE).isEmpty())
                        throw new IllegalArgumentException("word condition does not match its registered schema");
                }
            }
            var conditions = object == null ? List.<DialogueCondition>of() : DialogueCondition.readList(SOURCE, "word", object);
            if (conditions.stream().anyMatch(condition -> condition instanceof DialogueCondition.Invalid))
                throw new IllegalArgumentException("invalid word condition");
            entries.add(new Entry(text, weight, conditions));
        }
        if (entries.stream().noneMatch(entry -> entry.conditions().isEmpty()))
            throw new IllegalArgumentException("word pools require an unconditional fallback");
        return new DialogueWordPool(entries);
    }
}
