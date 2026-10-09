package com.jvn.villagerretaliation.skill.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.Arrays;
import java.util.List;

public final class SocialProgressionSchema {
    public static final String SCHEMA_ID = "villagerretaliation:social_progression/v1";
    public static final int MAX_BONUS = 20;

    private SocialProgressionSchema() { }

    public static JsonObject export() {
        var level = object(List.of("experience", "bonus"), "experience", integer(0, Integer.MAX_VALUE),
                "bonus", integer(0, MAX_BONUS));
        var skill = object(List.of("id", "levels"), "id", skill(), "levels", array(level, 1, 1024));
        var root = object(List.of("schema", "id", "revision", "skills"), "schema", choices(SCHEMA_ID),
                "id", resource(), "revision", integer(1, Integer.MAX_VALUE), "skills", array(skill, 4, 4));
        root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.addProperty("$id", "https://villagerretaliation.dev/schema/social-progression-v1.schema.json");
        root.addProperty("title", "Villager Retaliation Social Progression v1");
        return root;
    }

    public static JsonObject check() {
        return object(List.of("progression", "skill"), "progression", resource(), "skill", skill(),
                "experience", integer(0, Integer.MAX_VALUE));
    }

    public static List<QuestJsonSchema.Issue> validate(JsonElement input) { return QuestJsonSchema.validate(input, export()); }

    private static JsonObject skill() {
        return choices(Arrays.stream(PlayerSocialSkill.values()).map(PlayerSocialSkill::id).toArray(String[]::new));
    }

    private static JsonObject object(List<String> required, Object... fields) {
        var root = type("object");
        root.addProperty("additionalProperties", false);
        var names = new JsonArray();
        required.forEach(names::add);
        root.add("required", names);
        var properties = new JsonObject();
        for (int index = 0; index < fields.length; index += 2) properties.add((String) fields[index], (JsonElement) fields[index + 1]);
        root.add("properties", properties);
        return root;
    }

    private static JsonObject type(String kind) { var value = new JsonObject(); value.addProperty("type", kind); return value; }
    private static JsonObject resource() {
        var value = type("string");
        value.addProperty("pattern", "^[a-z0-9_.-]+:[a-z0-9_./-]+$(?![\\s\\S])");
        value.addProperty("maxLength", 256);
        return value;
    }
    private static JsonObject choices(String... values) {
        var value = type("string");
        var options = new JsonArray();
        for (var option : values) options.add(option);
        value.add("enum", options);
        return value;
    }
    private static JsonObject integer(int minimum, int maximum) {
        var value = type("integer");
        value.addProperty("minimum", minimum);
        value.addProperty("maximum", maximum);
        return value;
    }
    private static JsonObject array(JsonObject item, int minimum, int maximum) {
        var value = type("array");
        value.add("items", item);
        value.addProperty("minItems", minimum);
        value.addProperty("maxItems", maximum);
        return value;
    }
}
