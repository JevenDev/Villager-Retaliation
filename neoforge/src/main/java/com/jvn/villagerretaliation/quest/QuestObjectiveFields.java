package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Resource;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

final class QuestObjectiveFields {
    private static final Set<String> FACT_FIELDS = Set.of("tag", "tags", "fact_tag", "quest_tag", "key", "variable",
            "counter", "fact", "stage", "stages", "value", "values", "choice", "choices", "min", "max");

    private final JsonObject root;
    private final JsonObject target;
    private final JsonObject location;
    private final String id;
    private final String type;
    private final String authoredId;
    private final boolean optional;
    private final boolean v2;
    private final int index;

    private QuestObjectiveFields(JsonObject root, String id, String type, String authoredId, boolean optional, boolean v2, int index) {
        this.root = root;
        this.id = id;
        this.type = type;
        this.authoredId = authoredId;
        this.optional = optional;
        this.v2 = v2;
        this.index = index;
        this.target = v2 ? object(root.get("target")) : null;
        this.location = v2 ? object(this.target != null && this.target.has("location")
                ? this.target.get("location") : root.get("location")) : null;
    }

    static QuestObjectiveFields legacy(JsonObject root, int index) {
        String id = DatapackJsonReader.readString(root, "id");
        return new QuestObjectiveFields(root, id.isBlank() ? "objective_" + index : id,
                DatapackJsonReader.readString(root, "type"), "", DatapackJsonReader.readBoolean(root, "optional", false), false, index);
    }

    static QuestObjectiveFields v2(QuestV2Resource.Objective objective, String id, int index) {
        return new QuestObjectiveFields(objective.data(), id, objective.type(), objective.id(), objective.optional(), true, index);
    }

    String id() {
        String value = overriddenString("id", this.id);
        return value.isBlank() ? "objective_" + this.index : value;
    }
    String type() { return overriddenString("type", this.type); }
    boolean optional() {
        JsonElement value = overridden("optional");
        return value != null ? DatapackJsonReader.readBoolean(value, false)
                : this.optional || DatapackJsonReader.readBoolean(this.root.get("optional"), false);
    }
    boolean v2() { return this.v2; }

    JsonElement get(String key) {
        JsonElement override = overridden(key);
        return override == null ? this.root.get(key) : override;
    }

    private JsonElement overridden(String key) {
        if (this.location != null && this.location.has(key)) return this.location.get(key);
        if (this.target != null && this.target.has(key)) return this.target.get(key);
        return null;
    }

    private String overriddenString(String key, String fallback) {
        JsonElement value = overridden(key);
        return value == null ? fallback : value.isJsonPrimitive() ? value.getAsString().trim() : "";
    }

    boolean has(String key) { return get(key) != null; }
    JsonObject object(String key) { return object(get(key)); }

    String string(String... keys) {
        for (String key : keys) {
            JsonElement value = get(key);
            if (value != null && value.isJsonPrimitive()) return value.getAsString().trim();
        }
        return "";
    }

    List<String> strings(String... keys) {
        var values = new ArrayList<String>();
        for (String key : keys) values.addAll(DatapackJsonReader.readStringValues(get(key)));
        return List.copyOf(values);
    }

    int integer(String key, int fallback) { return DatapackJsonReader.readInt(get(key), fallback); }
    Integer nullableInt(String key) { return DatapackJsonReader.readNullableInt(get(key)); }
    OptionalInt optionalInt(String key) {
        Integer value = nullableInt(key);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
    Optional<ResourceLocation> resource(String key) { return DatapackJsonReader.parseResourceLocation(string(key)); }
    boolean bool(String key, boolean fallback) { return DatapackJsonReader.readBoolean(get(key), fallback); }

    boolean usesDefaultQuest() { return this.v2 && (this.type.equals("fact") || this.type.equals("choice")) && !has("quest"); }

    String choiceDefault() {
        return this.v2 && this.type.equals("choice") && FACT_FIELDS.stream().noneMatch(this::has) ? this.authoredId : "";
    }

    JsonObject schemaInput() {
        if (!this.v2) return this.root;
        var keys = new LinkedHashSet<>(this.root.keySet());
        if (this.target != null) keys.addAll(this.target.keySet());
        if (this.location != null) keys.addAll(this.location.keySet());
        var fields = new JsonObject();
        for (String key : keys) fields.add(key, get(key));
        if (overridden("id") == null) fields.addProperty("id", this.id);
        if (overridden("type") == null) fields.addProperty("type", this.type);
        if (overridden("optional") == null && this.optional) fields.addProperty("optional", true);
        return fields;
    }

    JsonObject itemPredicateInput() {
        var fields = new JsonObject();
        for (String key : List.of("components", "durability", "custom_data", "nbt", "min_durability", "max_durability")) {
            JsonElement value = get(key);
            if (value != null) fields.add(key, value);
        }
        return fields;
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
