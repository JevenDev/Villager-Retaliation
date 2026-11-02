package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public record DialogueWordPool(List<Entry> entries, String selectionGroup, int avoidRecent, String role, boolean optional, double chance) {
    public static final DialogueWordPool EMPTY = new DialogueWordPool(List.of());
    public static final Set<String> FIELDS = Set.of("values", "groups", "selection_group", "avoid_recent", "extends", "remove", "role", "optional", "chance", "text_overrides");
    private static final ResourceLocation SOURCE = ResourceLocation.parse("villagerretaliation:dialogue_words");

    public DialogueWordPool { entries = List.copyOf(entries); }
    public static final Set<String> ROLES = Set.of("literal", "sentence", "opening_clause", "address", "adjective_phrase");
    public DialogueWordPool(List<Entry> entries, String group, int recent) { this(entries, group, recent, "literal", false, 1); }
    public DialogueWordPool(List<Entry> entries) { this(entries, "", 0); }

    public record Entry(String text, int weight, List<DialogueCondition> conditions, int priority, String variant, String id, String source) {
        public Entry { conditions = List.copyOf(conditions); }
        public Entry(String text, int weight, List<DialogueCondition> conditions, int priority, String variant) { this(text, weight, conditions, priority, variant, "", ""); }
        public Entry(String text, int weight, List<DialogueCondition> conditions) { this(text, weight, conditions, 0, ""); }
    }

    public record Candidates(List<Entry> entries, boolean fallback) {
        public Candidates { entries = List.copyOf(entries); }
    }

    public List<String> values() {
        var values = new ArrayList<>(entries.stream().map(entry -> format(entry.text())).toList());
        if (optional && !values.contains("")) values.add("");
        return List.copyOf(values);
    }

    public String format(String text) {
        if (!role.equals("sentence") || text.isEmpty()) return text;
        int first = text.codePointAt(0);
        String capitalized = new String(Character.toChars(Character.toUpperCase(first))) + text.substring(Character.charCount(first));
        return capitalized + (capitalized.matches("(?s).*[.!?,:;]$") ? "" : ".");
    }

    public void validateResolved() {
        if (entries.isEmpty() || entries.size() > 256) throw new IllegalArgumentException("resolved word pools require 1 to 256 values");
        if (!optional && entries.stream().noneMatch(entry -> entry.conditions().isEmpty() && entry.variant().isEmpty()))
            throw new IllegalArgumentException("word pools require an unconditional, untagged fallback");
        if (selectionGroup.isEmpty() && entries.stream().anyMatch(entry -> !entry.variant().isEmpty()))
            throw new IllegalArgumentException("variant requires a selection_group");
        if (!optional && chance != 1) throw new IllegalArgumentException("chance requires an optional pool");
        if (!optional && entries.stream().anyMatch(entry -> entry.text().isEmpty())) throw new IllegalArgumentException("empty text requires an optional pool");
    }

    public String select(DialogueRuntimeContext context, RandomSource random) {
        return selectMatching(conditions -> context != null && DialogueCondition.matchesAll(context, conditions), random);
    }

    public Candidates candidates(Predicate<List<DialogueCondition>> matches) {
        var matching = entries.stream().filter(entry -> !entry.conditions().isEmpty() && matches.test(entry.conditions())).toList();
        boolean fallback = matching.isEmpty();
        var eligible = fallback ? entries.stream().filter(entry -> entry.conditions().isEmpty()).toList() : matching;
        int priority = eligible.stream().mapToInt(Entry::priority).max().orElse(0);
        return new Candidates(eligible.stream().filter(entry -> entry.priority() == priority).toList(), fallback);
    }

    public String selectMatching(Predicate<List<DialogueCondition>> matches, RandomSource random) {
        if (optional && (chance == 0 || chance < 1 && random.nextDouble() >= chance)) return "";
        var selected = pick(candidates(matches).entries(), random);
        return selected == null ? optional ? "" : null : format(selected.text());
    }

    public static Entry pick(List<Entry> eligible, RandomSource random) {
        int total = eligible.stream().mapToInt(Entry::weight).sum();
        if (total == 0) return null;
        int roll = random.nextInt(total);
        for (var entry : eligible) {
            roll -= entry.weight();
            if (roll < 0) return entry;
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

    private static int integer(JsonObject object, String key, int fallback, int min, int max) {
        if (!object.has(key)) return fallback;
        var value = object.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                int number = value.getAsBigDecimal().intValueExact();
                if (number >= min && number <= max) return number;
            } catch (ArithmeticException | NumberFormatException invalid) { }
        }
        throw new IllegalArgumentException(key + " must be an integer from " + min + " to " + max);
    }

    public static String resourceId(JsonObject root, String field) {
        var value = root.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !value.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || value.getAsString().length() > 256)
            throw new IllegalArgumentException(field + " must be a namespaced resource ID");
        return value.getAsString();
    }

    private static String literalText(JsonElement literal, boolean allowEmpty) {
        if (!literal.isJsonPrimitive() || !literal.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("word pool text must be a string");
        String text = literal.getAsString();
        if (text.isBlank() && !(allowEmpty && text.isEmpty()) || text.length() > 512
                || text.chars().anyMatch(c -> "{}<>\u00a7".indexOf(c) >= 0 || Character.isISOControl(c)))
            throw new IllegalArgumentException("word pool text must be nonblank literal text without placeholders, markup, or control characters, at most 512 characters");
        return text;
    }

    public static Map<String, String> textOverrides(JsonObject root) {
        if (!root.has("text_overrides")) return Map.of();
        if (!root.has("extends") || !root.get("text_overrides").isJsonObject() || root.getAsJsonObject("text_overrides").size() > 256)
            throw new IllegalArgumentException("text_overrides requires a parent and an object of at most 256 entry IDs");
        var result = new LinkedHashMap<String, String>();
        for (var entry : root.getAsJsonObject("text_overrides").entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_.-]{1,64}"))
                throw new IllegalArgumentException("text_overrides requires lowercase entry IDs");
            result.put(entry.getKey(), literalText(entry.getValue(), true));
        }
        return Map.copyOf(result);
    }

    private static List<DialogueCondition> conditions(JsonObject object) {
        if (!object.has("conditions")) return List.of();
        if (!object.get("conditions").isJsonArray()) throw new IllegalArgumentException("word conditions must be an array");
        for (var condition : object.getAsJsonArray("conditions"))
            if (!condition.isJsonObject() || !QuestJsonSchema.validate(condition, ConditionSchema.VALUE).isEmpty())
                throw new IllegalArgumentException("word condition does not match its registered schema");
        var conditions = DialogueCondition.readList(SOURCE, "word", object);
        if (conditions.stream().anyMatch(condition -> condition instanceof DialogueCondition.Invalid)) throw new IllegalArgumentException("invalid word condition");
        return conditions;
    }

    private static void flatten(JsonObject root, String pointer, JsonObject defaults, List<JsonObject> entries, List<String> sources) {
        if (!root.has("values")) return;
        if (!root.get("values").isJsonArray()) throw new IllegalArgumentException("values must be an array");
        int index = 0;
        for (var value : root.getAsJsonArray("values")) {
            JsonObject entry;
            if (value.isJsonObject()) entry = value.getAsJsonObject().deepCopy();
            else { entry = new JsonObject(); entry.add("text", value); }
            var combined = new com.google.gson.JsonArray();
            if (defaults.has("conditions")) defaults.getAsJsonArray("conditions").forEach(combined::add);
            if (entry.has("conditions")) {
                if (!entry.get("conditions").isJsonArray()) throw new IllegalArgumentException("word conditions must be an array");
                entry.getAsJsonArray("conditions").forEach(combined::add);
            }
            if (!combined.isEmpty()) entry.add("conditions", combined);
            for (String field : List.of("priority", "variant")) if (!entry.has(field) && defaults.has(field)) entry.add(field, defaults.get(field));
            entries.add(entry); sources.add(pointer + "/values/" + index++);
        }
    }

    public static DialogueWordPool read(JsonObject root) {
        if (root.keySet().stream().anyMatch(key -> !FIELDS.contains(key))) throw new IllegalArgumentException("unknown word pool field");
        boolean inherited = root.has("extends");
        if (inherited) resourceId(root, "extends");
        var overrides = textOverrides(root);
        if (root.has("remove")) {
            if (!inherited || !root.get("remove").isJsonArray() || root.getAsJsonArray("remove").size() > 256)
                throw new IllegalArgumentException("remove requires a parent and at most 256 entry IDs");
            var removed = new java.util.HashSet<String>();
            for (var value : root.getAsJsonArray("remove"))
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !value.getAsString().matches("[a-z0-9_.-]{1,64}") || !removed.add(value.getAsString()))
                    throw new IllegalArgumentException("remove requires unique entry IDs");
        }
        String role = root.has("role") && root.get("role").isJsonPrimitive() && root.get("role").getAsJsonPrimitive().isString() ? root.get("role").getAsString() : "literal";
        if (root.has("role") && (!root.get("role").isJsonPrimitive() || !root.get("role").getAsJsonPrimitive().isString()) || !ROLES.contains(role))
            throw new IllegalArgumentException("unknown grammatical role");
        if (root.has("optional") && (!root.get("optional").isJsonPrimitive() || !root.get("optional").getAsJsonPrimitive().isBoolean()))
            throw new IllegalArgumentException("optional must be a boolean");
        boolean optional = root.has("optional") && root.get("optional").getAsBoolean();
        double chance = 1;
        if (root.has("chance")) {
            var number = root.get("chance");
            if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber() || !Double.isFinite(number.getAsDouble()) || number.getAsDouble() < 0 || number.getAsDouble() > 1)
                throw new IllegalArgumentException("chance must be a number from 0 to 1");
            chance = number.getAsDouble();
            if (!optional && !inherited) throw new IllegalArgumentException("chance requires an optional pool");
        }
        var flattened = new ArrayList<JsonObject>();
        var sources = new ArrayList<String>();
        flatten(root, "", new JsonObject(), flattened, sources);
        if (root.has("groups")) {
            if (!root.get("groups").isJsonArray() || root.getAsJsonArray("groups").size() > 256) throw new IllegalArgumentException("groups must be an array of at most 256 groups");
            int index = 0;
            for (var raw : root.getAsJsonArray("groups")) {
                if (!raw.isJsonObject()) throw new IllegalArgumentException("phrase groups must be objects");
                var value = raw.getAsJsonObject();
                if (value.keySet().stream().anyMatch(key -> !Set.of("values", "conditions", "priority", "variant").contains(key)) || !value.has("values"))
                    throw new IllegalArgumentException("groups accept values, conditions, priority, and variant");
                integer(value, "priority", 0, -1000000, 1000000);
                if (value.has("conditions")) conditions(value);
                if (value.has("variant") && (!value.get("variant").isJsonPrimitive() || !value.get("variant").getAsJsonPrimitive().isString()
                        || !value.get("variant").getAsString().matches("[a-z0-9_.-]{1,64}") || !root.has("selection_group") && !inherited))
                    throw new IllegalArgumentException("invalid group variant");
                flatten(value, "/groups/" + index++, value, flattened, sources);
            }
        }
        if (flattened.size() > 256 || flattened.isEmpty() && !inherited) throw new IllegalArgumentException("word pools require 1 to 256 values");
        String group = "";
        if (root.has("selection_group")) {
            var value = root.get("selection_group");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || !value.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || value.getAsString().length() > 256)
                throw new IllegalArgumentException("selection_group must be a namespaced resource ID, at most 256 characters");
            group = value.getAsString();
        }
        int avoidRecent = integer(root, "avoid_recent", 0, 0, 16);
        var entries = new ArrayList<Entry>();
        var ids = new java.util.HashSet<String>();
        int entryIndex = 0;
        for (var value : flattened) {
            JsonObject object = value;
            if (object != null && (object.keySet().stream().anyMatch(key -> !Set.of("text", "weight", "conditions", "priority", "variant", "id").contains(key))
                    || !object.has("text"))) throw new IllegalArgumentException("word entry requires text and accepts only weight, conditions, priority, and variant");
            var literal = object == null ? value : object.get("text");
            String text = literalText(literal, optional || inherited);
            int weight = object == null ? 1 : integer(object, "weight", 1, 1, 1000);
            int priority = object == null ? 0 : integer(object, "priority", 0, -1000000, 1000000);
            String variant = "";
            if (object != null && object.has("variant")) {
                var tag = object.get("variant");
                if (group.isEmpty() && !inherited || !tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()
                        || !tag.getAsString().matches("[a-z0-9_.-]{1,64}"))
                    throw new IllegalArgumentException("variant requires a selection_group and a lowercase name, at most 64 characters");
                variant = tag.getAsString();
            }
            var conditions = conditions(object);
            String id = "";
            if (object.has("id")) {
                var name = object.get("id");
                if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString() || !name.getAsString().matches("[a-z0-9_.-]{1,64}") || !ids.add(name.getAsString()))
                    throw new IllegalArgumentException("entry IDs must be unique lowercase names, at most 64 characters");
                id = name.getAsString();
            }
            if (overrides.containsKey(id)) throw new IllegalArgumentException("entry has both a full replacement and a text override: " + id);
            entries.add(new Entry(text, weight, conditions, priority, variant, id, sources.get(entryIndex++)));
        }
        var pool = new DialogueWordPool(entries, group, avoidRecent, role, optional, chance);
        if (!inherited) pool.validateResolved();
        return pool;
    }
}
