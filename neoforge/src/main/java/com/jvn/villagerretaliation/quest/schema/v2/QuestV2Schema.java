package com.jvn.villagerretaliation.quest.schema.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.provider.QuestProviderRegistry;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;

public final class QuestV2Schema {
    public static final Path TOOLING_SCHEMA_PATH =
            Path.of("tools", "datapack-builder", "quest-v2.schema.json");
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private QuestV2Schema() {
    }

    public static JsonObject export() {
        JsonObject root = object();
        root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.addProperty("$id", "https://villagerretaliation.dev/schema/quest-v2.schema.json");
        root.addProperty("title", "Villager Retaliation Quest Module v2");
        root.addProperty("type", "object");
        root.addProperty("additionalProperties", false);
        root.add("required", strings("schema", "id", "provider", "entry_stage", "stages"));

        JsonObject properties = object();
        properties.add("schema", stringConst(QuestSchemaVersion.V2.schemaId()));
        properties.add("id", resourceLocation());
        properties.add("metadata", ref("#/$defs/metadata"));
        properties.add("provider", ref("#/$defs/provider"));
        properties.add("availability", ref("#/$defs/availability"));
        properties.add("lifecycle", ref("#/$defs/lifecycle"));
        properties.add("dialogue", objectMap(ref("#/$defs/dialogue_slot")));
        properties.add("entry_stage", idString());
        properties.add("stages", arrayOf(ref("#/$defs/stage")));
        properties.add("events", arrayOf(ref("#/$defs/event")));
        properties.add("rewards", ref("#/$defs/rewards"));
        properties.add("ui", ref("#/$defs/ui"));
        properties.add("external_scenes", arrayOf(resourceLocation()));
        root.add("properties", properties);

        JsonObject defs = object();
        defs.add("metadata", metadata());
        defs.add("provider", provider());
        defs.add("availability", availability());
        defs.add("lifecycle", lifecycle());
        defs.add("lifecycle_hook", lifecycleHook());
        defs.add("stage", stage());
        defs.add("objective", objective());
        defs.add("dialogue_slot", dialogueSlot());
        defs.add("scene", scene());
        defs.add("response", response());
        defs.add("transition", transition());
        defs.add("event", event());
        defs.add("rewards", rewards());
        defs.add("ui", ui());
        defs.add("condition", condition());
        defs.add("action", action());
        root.add("$defs", defs);
        return root;
    }

    public static String exportJson() {
        return GSON.toJson(export()) + System.lineSeparator();
    }

    public static void write(Path output) throws IOException {
        Path target = output == null ? TOOLING_SCHEMA_PATH : output;
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, exportJson(), StandardCharsets.UTF_8);
    }

    private static JsonObject metadata() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        properties.add("title", string());
        properties.add("description", string());
        properties.add("title_key", string());
        properties.add("description_key", string());
        properties.add("questline", string());
        properties.add("tags", arrayOf(string()));
        properties.add("parent", resourceLocation());
        properties.add("author", string());
        properties.add("version", string());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject provider() {
        JsonObject schema = typedObject();
        schema.add("required", strings("type"));
        JsonObject properties = object();
        properties.add("type", stringEnum(providerTypes()));
        properties.add("required_capabilities", arrayOf(stringEnum(providerCapabilities())));
        properties.add("capabilities", arrayOf(stringEnum(providerCapabilities())));
        properties.add("filters", openObject());
        properties.add("data", openObject());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject availability() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        properties.add("conditions", arrayOf(ref("#/$defs/condition")));
        properties.add("active", booleanSchema());
        properties.add("cooldown", string());
        properties.add("cooldown_ticks", integer());
        properties.add("cooldown_days", integer());
        properties.add("cooldown_seconds", integer());
        properties.add("completion_cooldown", string());
        properties.add("completion_cooldown_ticks", integer());
        properties.add("completion_cooldown_days", integer());
        properties.add("completion_cooldown_seconds", integer());
        properties.add("exclusive_group", resourceLocation());
        properties.add("repeatable", booleanSchema());
        properties.add("max_starts", integer());
        properties.add("max_completions", integer());
        properties.add("completion_scope", string());
        properties.add("scope", string());
        properties.add("abandonment", string());
        properties.add("abandonment_cooldown", string());
        properties.add("abandonment_cooldown_ticks", integer());
        properties.add("abandonment_cooldown_days", integer());
        properties.add("abandonment_cooldown_seconds", integer());
        properties.add("consume_on_completion", booleanSchema());
        properties.add("consume_on_abandonment", booleanSchema());
        properties.add("locked_to_villager", booleanSchema());
        properties.add("cross_villager_compatible", booleanSchema());
        properties.add("prerequisites", arrayOf(resourceLocation()));
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject lifecycle() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        for (String hook : new String[]{"on_start", "on_complete", "on_abandon", "on_expire", "on_fail", "on_stage_enter", "on_stage_exit"}) {
            properties.add(hook, ref("#/$defs/lifecycle_hook"));
        }
        properties.add("dialogue", arrayOf(ref("#/$defs/scene")));
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject lifecycleHook() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        properties.add("actions", arrayOf(ref("#/$defs/action")));
        properties.add("transition", ref("#/$defs/transition"));
        properties.add("next", idString());
        properties.add("stage", idString());
        properties.add("scene", idString());
        properties.add("complete", booleanSchema());
        properties.add("abandon", booleanSchema());
        properties.add("fail", booleanSchema());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject stage() {
        JsonObject schema = typedObject();
        schema.add("required", strings("id", "objectives"));
        JsonObject properties = object();
        properties.add("id", idString());
        properties.add("title", string());
        properties.add("title_key", string());
        properties.add("description", string());
        properties.add("description_key", string());
        properties.add("objectives", arrayOf(ref("#/$defs/objective")));
        properties.add("complete_when", arrayOf(idString()));
        properties.add("next", transitionStringOrObject());
        properties.add("dialogue", objectMap(ref("#/$defs/dialogue_slot")));
        properties.add("scenes", arrayOf(ref("#/$defs/scene")));
        properties.add("responses", arrayOf(ref("#/$defs/response")));
        properties.add("events", arrayOf(ref("#/$defs/event")));
        properties.add("on_enter", arrayOf(ref("#/$defs/action")));
        properties.add("on_exit", arrayOf(ref("#/$defs/action")));
        properties.add("entry_actions", arrayOf(ref("#/$defs/action")));
        properties.add("exit_actions", arrayOf(ref("#/$defs/action")));
        properties.add("rewards", ref("#/$defs/rewards"));
        properties.add("ui", ref("#/$defs/ui"));
        properties.add("metadata", openObject());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject objective() {
        JsonObject schema = typedObject();
        schema.add("required", strings("id", "type"));
        JsonObject properties = object();
        properties.add("id", idString());
        properties.add("type", stringEnum(objectiveTypes()));
        properties.add("optional", booleanSchema());
        properties.add("count", integer());
        properties.add("consume", booleanSchema());
        properties.add("tracker", openObject());
        properties.add("conditions", arrayOf(ref("#/$defs/condition")));
        properties.add("metadata", openObject());
        properties.add("ui", ref("#/$defs/ui"));
        schema.add("properties", properties);
        schema.add("additionalProperties", booleanLiteral(true));
        return schema;
    }

    private static JsonObject dialogueSlot() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        properties.add("scene", idString());
        properties.add("scene_ref", idString());
        properties.add("external", externalSceneRef());
        properties.add("external_scene", externalSceneRef());
        properties.add("external_entry", idString());
        properties.add("label", string());
        properties.add("request", string());
        properties.add("show_for_babies", booleanSchema());
        properties.add("order", integer());
        properties.add("text", string());
        properties.add("text_key", string());
        properties.add("lines", arrayOf(string()));
        properties.add("responses", arrayOf(ref("#/$defs/response")));
        properties.add("conditions", arrayOf(ref("#/$defs/condition")));
        properties.add("actions", arrayOf(ref("#/$defs/action")));
        properties.add("metadata", openObject());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject scene() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        properties.add("id", idString());
        properties.add("slot", idString());
        properties.add("label", string());
        properties.add("request", string());
        properties.add("show_for_babies", booleanSchema());
        properties.add("order", integer());
        properties.add("text", string());
        properties.add("text_key", string());
        properties.add("lines", arrayOf(string()));
        properties.add("responses", arrayOf(ref("#/$defs/response")));
        properties.add("actions", arrayOf(ref("#/$defs/action")));
        properties.add("conditions", arrayOf(ref("#/$defs/condition")));
        properties.add("next", transitionStringOrObject());
        properties.add("transition", ref("#/$defs/transition"));
        properties.add("external", externalSceneRef());
        properties.add("external_scene", externalSceneRef());
        properties.add("external_entry", idString());
        properties.add("scene_ref", resourceLocation());
        properties.add("metadata", openObject());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject response() {
        JsonObject schema = typedObject();
        schema.add("required", strings("id"));
        JsonObject properties = object();
        properties.add("id", idString());
        properties.add("label", string());
        properties.add("label_key", string());
        properties.add("text", string());
        properties.add("text_key", string());
        properties.add("lines", arrayOf(string()));
        properties.add("conditions", arrayOf(ref("#/$defs/condition")));
        properties.add("actions", arrayOf(ref("#/$defs/action")));
        properties.add("transition", ref("#/$defs/transition"));
        properties.add("next", idString());
        properties.add("stage", idString());
        properties.add("scene", idString());
        properties.add("response", idString());
        properties.add("complete", booleanSchema());
        properties.add("abandon", booleanSchema());
        properties.add("fail", booleanSchema());
        properties.add("request", string());
        properties.add("order", integer());
        properties.add("metadata", openObject());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject transition() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        properties.add("stage", idString());
        properties.add("scene", idString());
        properties.add("response", idString());
        properties.add("complete", booleanSchema());
        properties.add("abandon", booleanSchema());
        properties.add("fail", booleanSchema());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject event() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        properties.add("id", idString());
        properties.add("event", stringEnum(triggerEvents()));
        properties.add("trigger", stringEnum(triggerEvents()));
        properties.add("type", stringEnum(triggerEvents()));
        properties.add("stage", idString());
        properties.add("stages", arrayOf(idString()));
        properties.add("conditions", arrayOf(ref("#/$defs/condition")));
        properties.add("actions", arrayOf(ref("#/$defs/action")));
        properties.add("transition", ref("#/$defs/transition"));
        properties.add("next", idString());
        properties.add("cooldown", string());
        properties.add("cooldown_ticks", integer());
        properties.add("radius", number());
        properties.add("repeatable", booleanSchema());
        properties.add("metadata", openObject());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject rewards() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        properties.add("actions", arrayOf(ref("#/$defs/action")));
        properties.add("experience", integer());
        properties.add("reputation", integer());
        properties.add("gossip_reputation", integer());
        properties.add("loot_table", resourceLocation());
        properties.add("memory_event", resourceLocation());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject ui() {
        JsonObject schema = typedObject();
        JsonObject properties = object();
        properties.add("title", string());
        properties.add("title_key", string());
        properties.add("description", string());
        properties.add("description_key", string());
        properties.add("tracker_text", string());
        properties.add("tracker_text_key", string());
        properties.add("show_progress", booleanSchema());
        properties.add("progress", number());
        properties.add("placeholders", objectMap(string()));
        properties.add("icon", resourceLocation());
        properties.add("color", string());
        properties.add("priority", integer());
        properties.add("hidden", booleanSchema());
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject condition() {
        JsonObject schema = typedObject();
        schema.add("required", strings("type"));
        JsonObject properties = object();
        properties.add("type", stringEnum(conditionTypes()));
        schema.add("properties", properties);
        schema.add("additionalProperties", booleanLiteral(true));
        return schema;
    }

    private static JsonObject action() {
        JsonObject schema = typedObject();
        schema.add("required", strings("type"));
        JsonObject properties = object();
        properties.add("type", stringEnum(actionTypes()));
        properties.add("action", stringEnum(actionTypes()));
        schema.add("properties", properties);
        schema.add("additionalProperties", booleanLiteral(true));
        return schema;
    }

    private static Collection<String> conditionTypes() {
        return DialogueCondition.descriptors().stream()
                .flatMap(descriptor -> Stream.concat(Stream.of(descriptor.id()), descriptor.aliases().stream()))
                .sorted()
                .toList();
    }

    private static Collection<String> actionTypes() {
        return VillagerActionRegistry.descriptors().stream()
                .flatMap(descriptor -> Stream.concat(Stream.of(descriptor.id()), descriptor.aliases().stream()))
                .sorted()
                .toList();
    }

    private static Collection<String> objectiveTypes() {
        return QuestObjectiveRegistry.descriptors().stream()
                .flatMap(descriptor -> Stream.concat(Stream.of(descriptor.id()), descriptor.aliases().stream()))
                .sorted()
                .toList();
    }

    private static Collection<String> triggerEvents() {
        return QuestTriggerRegistry.descriptors().stream()
                .flatMap(descriptor -> Stream.concat(Stream.of(descriptor.id()), descriptor.aliases().stream()))
                .sorted()
                .toList();
    }

    private static Collection<String> providerTypes() {
        return QuestProviderRegistry.descriptors().stream()
                .map(descriptor -> descriptor.id().toString())
                .sorted()
                .toList();
    }

    private static Collection<String> providerCapabilities() {
        return QuestProviderRegistry.descriptors().stream()
                .flatMap(descriptor -> descriptor.capabilities().stream())
                .map(Object::toString)
                .sorted()
                .toList();
    }

    private static JsonObject typedObject() {
        JsonObject schema = object();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject openObject() {
        JsonObject schema = object();
        schema.addProperty("type", "object");
        return schema;
    }

    private static JsonObject objectMap(JsonObject valueSchema) {
        JsonObject schema = openObject();
        schema.add("additionalProperties", valueSchema);
        return schema;
    }

    private static JsonObject transitionStringOrObject() {
        JsonObject schema = object();
        JsonArray options = array();
        options.add(idString());
        options.add(ref("#/$defs/transition"));
        schema.add("oneOf", options);
        return schema;
    }

    private static JsonObject externalSceneRef() {
        JsonObject schema = object();
        JsonArray options = array();
        options.add(resourceLocation());

        JsonObject external = typedObject();
        JsonObject properties = object();
        properties.add("tree", resourceLocation());
        properties.add("tree_id", resourceLocation());
        properties.add("dialogue_tree", resourceLocation());
        properties.add("entry", idString());
        properties.add("entry_id", idString());
        properties.add("metadata", openObject());
        external.add("properties", properties);
        options.add(external);

        schema.add("oneOf", options);
        return schema;
    }

    private static JsonObject arrayOf(JsonObject itemSchema) {
        JsonObject schema = object();
        schema.addProperty("type", "array");
        schema.add("items", itemSchema);
        return schema;
    }

    private static JsonObject ref(String ref) {
        JsonObject schema = object();
        schema.addProperty("$ref", ref);
        return schema;
    }

    private static JsonObject resourceLocation() {
        JsonObject schema = string();
        schema.addProperty("pattern", "^[a-z0-9_.-]+:[a-z0-9_./-]+$");
        return schema;
    }

    private static JsonObject idString() {
        JsonObject schema = string();
        schema.addProperty("minLength", 1);
        schema.addProperty("pattern", "^(?!__generated)(?!vr\\$)[A-Za-z0-9_.:-]+$");
        return schema;
    }

    private static JsonObject string() {
        JsonObject schema = object();
        schema.addProperty("type", "string");
        return schema;
    }

    private static JsonObject stringConst(String value) {
        JsonObject schema = string();
        schema.addProperty("const", value);
        return schema;
    }

    private static JsonObject stringEnum(Collection<String> values) {
        JsonObject schema = string();
        schema.add("enum", strings(values));
        return schema;
    }

    private static JsonObject integer() {
        JsonObject schema = object();
        schema.addProperty("type", "integer");
        return schema;
    }

    private static JsonObject number() {
        JsonObject schema = object();
        schema.addProperty("type", "number");
        return schema;
    }

    private static JsonObject booleanSchema() {
        JsonObject schema = object();
        schema.addProperty("type", "boolean");
        return schema;
    }

    private static com.google.gson.JsonPrimitive booleanLiteral(boolean value) {
        return new com.google.gson.JsonPrimitive(value);
    }

    private static JsonObject object() {
        return new JsonObject();
    }

    private static JsonArray array() {
        return new JsonArray();
    }

    private static JsonArray strings(String... values) {
        JsonArray array = array();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray strings(Collection<String> values) {
        JsonArray array = array();
        if (values == null) {
            return array;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted(Comparator.naturalOrder())
                .forEach(array::add);
        return array;
    }
}
