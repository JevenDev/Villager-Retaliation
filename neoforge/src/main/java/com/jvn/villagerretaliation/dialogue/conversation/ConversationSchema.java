package com.jvn.villagerretaliation.dialogue.conversation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema;
import java.util.List;

public final class ConversationSchema {
    public static final String SCHEMA_ID = "villagerretaliation:conversation/v1";
    public static final int DEFAULT_DIFFICULTY = 10;

    private ConversationSchema() {
    }

    public static JsonObject export() {
        JsonObject shared = QuestV2Schema.export().getAsJsonObject("$defs");
        JsonObject definitions = new JsonObject();
        for (String name : List.of("condition", "action", "localized_reference")) definitions.add(name, shared.get(name));
        definitions.add("effect", object(List.of("id", "action"), "id", id(), "action", ref("action")));
        JsonObject speaker = object(List.of("type"), "type", enumeration("role", "player", "narrator"), "role", id());
        JsonObject role = object(List.of("type"), "type", enumeration("role"));
        role.addProperty("additionalProperties", true);
        speaker.add("if", role);
        JsonObject requiredRole = new JsonObject();
        requiredRole.add("required", strings("role"));
        speaker.add("then", requiredRole);
        JsonObject forbiddenRole = new JsonObject();
        forbiddenRole.add("not", requiredRole);
        speaker.add("else", forbiddenRole);
        definitions.add("speaker", speaker);
        definitions.add("exchange", object(List.of("id", "speaker", "text"),
                "id", id(), "speaker", ref("speaker"), "text", ref("localized_reference")));
        JsonObject response = object(List.of("id", "label"), "id", id(), "label", ref("localized_reference"),
                "conditions", array(ref("condition"), 0), "effects", array(ref("effect"), 0), "next", id(), "check", id());
        response.add("oneOf", exclusive("next", "check"));
        definitions.add("response", response);
        JsonObject node = object(List.of("id"), "id", id(), "exchanges", array(ref("exchange"), 0),
                "effects", array(ref("effect"), 0), "responses", array(ref("response"), 1), "next", id(), "outcome", id());
        node.add("oneOf", exclusive("responses", "next", "outcome"));
        definitions.add("node", node);
        definitions.add("topic", object(List.of("id", "label", "start"), "id", id(),
                "label", ref("localized_reference"), "start", id(), "conditions", array(ref("condition"), 0)));
        JsonObject difficulty = integer(0);
        difficulty.addProperty("default", DEFAULT_DIFFICULTY);
        definitions.add("check", object(List.of("id", "success", "failure"), "id", id(),
                "difficulty", difficulty, "success", id(), "failure", id(),
                "social", com.jvn.villagerretaliation.skill.player.SocialProgressionSchema.check()));
        definitions.add("outcome", object(List.of("id", "label"), "id", id(), "label", ref("localized_reference")));
        JsonObject root = object(List.of("schema", "id", "revision", "campaign", "character", "title", "topics", "nodes", "outcomes"),
                "schema", enumeration(SCHEMA_ID), "id", resource(), "revision", integer(1),
                "campaign", resource(), "character", id(), "quest", resource(), "title", ref("localized_reference"),
                "topics", array(ref("topic"), 1), "nodes", array(ref("node"), 1),
                "checks", array(ref("check"), 0), "outcomes", array(ref("outcome"), 1));
        root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.addProperty("$id", "https://villagerretaliation.dev/schema/conversation-v1.schema.json");
        root.addProperty("title", "Villager Retaliation Conversation v1");
        root.add("$defs", definitions);
        return root;
    }

    public static List<QuestJsonSchema.Issue> validate(JsonElement input) {
        return QuestJsonSchema.validate(input, export());
    }

    private static JsonObject object(List<String> required, Object... fields) {
        JsonObject result = type("object");
        result.addProperty("additionalProperties", false);
        result.add("required", strings(required.toArray(String[]::new)));
        JsonObject properties = new JsonObject();
        for (int index = 0; index < fields.length; index += 2) properties.add((String) fields[index], (JsonElement) fields[index + 1]);
        result.add("properties", properties);
        return result;
    }

    private static JsonArray exclusive(String... fields) {
        JsonArray alternatives = new JsonArray();
        for (String field : fields) {
            JsonObject option = new JsonObject();
            option.add("required", strings(field));
            alternatives.add(option);
        }
        return alternatives;
    }

    private static JsonObject type(String type) {
        JsonObject result = new JsonObject();
        result.addProperty("type", type);
        return result;
    }

    private static JsonObject ref(String name) {
        JsonObject result = new JsonObject();
        result.addProperty("$ref", "#/$defs/" + name);
        return result;
    }

    private static JsonObject id() { return text("^[a-z0-9][a-z0-9_./-]*$(?![\\s\\S])"); }
    private static JsonObject resource() { return text("^[a-z0-9_.-]+:[a-z0-9_./-]+$(?![\\s\\S])"); }

    private static JsonObject text(String pattern) {
        JsonObject result = type("string");
        result.addProperty("minLength", 1);
        result.addProperty("maxLength", 256);
        result.addProperty("pattern", pattern);
        return result;
    }

    private static JsonObject enumeration(String... values) {
        JsonObject result = type("string");
        result.add("enum", strings(values));
        return result;
    }

    private static JsonObject integer(int minimum) {
        JsonObject result = type("integer");
        result.addProperty("minimum", minimum);
        result.addProperty("maximum", Integer.MAX_VALUE);
        return result;
    }

    private static JsonObject array(JsonObject items, int minimum) {
        JsonObject result = type("array");
        result.add("items", items);
        result.addProperty("minItems", minimum);
        result.addProperty("maxItems", 1024);
        return result;
    }

    private static JsonArray strings(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }
}
