package com.jvn.villagerretaliation.scene.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import java.util.Arrays;
import java.util.Locale;

/** Checked-in browser schemas are generated from the same enums and registries used by compilation. */
public final class SceneSchema {
    private SceneSchema() {
    }

    public static JsonObject sceneV1() {
        VillagerRetaliationRegistries.registerBuiltIns();
        JsonObject root = object("Persistent cinematic scene v1");
        root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.addProperty("$id", "https://jeven.dev/villager-retaliation/schema/scene-v1.schema.json");
        root.addProperty("additionalProperties", false);
        root.add("required", strings("schema", "id", "ownership", "entry_step", "actors", "steps"));
        JsonObject properties = new JsonObject();
        properties.add("schema", constant("villagerretaliation:scene/v1"));
        properties.add("id", resourceLocation());
        properties.add("definition_version", integer(1));
        properties.add("ownership", enumValues(SceneResource.OwnershipMode.values()));
        properties.add("entry_step", text());
        properties.add("timeout_ticks", integer(0));
        properties.add("failure_policy", enumValues(SceneResource.TransitionPolicy.values()));
        properties.add("cancellation_policy", enumValues(SceneResource.TransitionPolicy.values()));
        properties.add("cleanup_policy", enumValues(SceneResource.CleanupPolicy.values()));
        properties.add("metadata", map());
        properties.add("actors", array(actor(), 0));
        properties.add("steps", array(step(), 1));
        root.add("properties", properties);
        return root;
    }

    public static JsonObject encounterV1() {
        VillagerRetaliationRegistries.registerBuiltIns();
        JsonObject root = object("Controlled encounter template v1");
        root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.addProperty("$id", "https://jeven.dev/villager-retaliation/schema/encounter-v1.schema.json");
        root.addProperty("additionalProperties", false);
        root.add("required", strings("schema", "id", "members"));
        JsonObject properties = new JsonObject();
        properties.add("schema", constant("villagerretaliation:encounter/v1"));
        properties.add("id", resourceLocation());
        properties.add("version", integer(1));
        properties.add("controller", registeredIds(VillagerRetaliationRegistries.ENCOUNTER_TEMPLATES));
        properties.add("members", array(member(), 1));
        properties.add("extra_per_player", integer(0));
        properties.add("max_party_size", integer(1));
        properties.add("placement_attempts", boundedInteger(1, 64));
        properties.add("spawn_radius", boundedInteger(1, 32));
        properties.add("respawn_policy", enumValues(EncounterTemplate.RespawnPolicy.values()));
        properties.add("cleanup_policy", enumValues(EncounterTemplate.CleanupPolicy.values()));
        properties.add("completion_condition", enumValues(EncounterTemplate.CompletionCondition.values()));
        root.add("properties", properties);
        return root;
    }

    private static JsonObject actor() {
        JsonObject actor = object("Actor declaration");
        actor.addProperty("additionalProperties", false);
        actor.add("required", strings("alias", "type", "binding_source", "replacement_policy"));
        JsonObject properties = new JsonObject();
        properties.add("alias", patternedText("^[a-z][a-z0-9_.-]{0,63}$"));
        properties.add("type", registeredIds(VillagerRetaliationRegistries.ACTOR_TYPES));
        properties.add("required", bool());
        properties.add("capabilities", array(resourceLocation(), 0));
        properties.add("binding_source", enumValues(SceneActorDeclaration.BindingSource.values()));
        properties.add("binding", text());
        properties.add("replacement_policy", enumValues(SceneActorDeclaration.ReplacementPolicy.values()));
        properties.add("missing_actor_policy", enumValues(SceneActorDeclaration.MissingActorPolicy.values()));
        properties.add("death_policy", enumValues(SceneActorDeclaration.DeathPolicy.values()));
        properties.add("filters", map());
        properties.add("timeout_ticks", integer(0));
        actor.add("properties", properties);
        return actor;
    }

    private static JsonObject step() {
        JsonObject step = object("Stable scene step");
        step.addProperty("additionalProperties", false);
        step.add("required", strings("id", "type"));
        JsonObject properties = new JsonObject();
        properties.add("id", text());
        properties.add("type", registeredIds(VillagerRetaliationRegistries.SCENE_STEPS));
        properties.add("actors", array(patternedText("^[a-z][a-z0-9_.-]{0,63}$"), 0));
        properties.add("data", map());
        properties.add("next", text());
        properties.add("failure_step", text());
        properties.add("transitions", map());
        step.add("properties", properties);
        return step;
    }

    private static JsonObject member() {
        JsonObject member = object("Encounter member");
        member.addProperty("additionalProperties", false);
        member.add("required", strings("entity"));
        JsonObject properties = new JsonObject();
        properties.add("entity", resourceLocation());
        properties.add("count", boundedInteger(1, 64));
        member.add("properties", properties);
        return member;
    }

    private static JsonObject registeredIds(com.jvn.villagerretaliation.api.registry.FreezableExtensionRegistry<?> registry) {
        JsonObject value = resourceLocation();
        JsonArray ids = new JsonArray();
        registry.descriptors().forEach(descriptor -> ids.add(descriptor.id().toString()));
        value.add("enum", ids);
        return value;
    }

    private static JsonObject object(String title) {
        JsonObject value = new JsonObject();
        value.addProperty("type", "object");
        value.addProperty("title", title);
        return value;
    }

    private static JsonObject text() {
        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
        value.addProperty("minLength", 1);
        return value;
    }

    private static JsonObject patternedText(String pattern) {
        JsonObject value = text();
        value.addProperty("pattern", pattern);
        return value;
    }

    private static JsonObject bool() {
        JsonObject value = new JsonObject();
        value.addProperty("type", "boolean");
        return value;
    }

    private static JsonObject resourceLocation() {
        return patternedText("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    }

    private static JsonObject integer(int minimum) {
        JsonObject value = new JsonObject();
        value.addProperty("type", "integer");
        value.addProperty("minimum", minimum);
        return value;
    }

    private static JsonObject boundedInteger(int minimum, int maximum) {
        JsonObject value = integer(minimum);
        value.addProperty("maximum", maximum);
        return value;
    }

    private static JsonObject map() {
        JsonObject value = object("Data");
        value.addProperty("additionalProperties", true);
        return value;
    }

    private static JsonObject array(JsonObject item, int minimumItems) {
        JsonObject value = new JsonObject();
        value.addProperty("type", "array");
        value.addProperty("minItems", minimumItems);
        value.add("items", item);
        return value;
    }

    private static JsonObject constant(String constant) {
        JsonObject value = new JsonObject();
        value.addProperty("const", constant);
        return value;
    }

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        Arrays.stream(values).forEach(array::add);
        return array;
    }

    private static JsonObject enumValues(Enum<?>[] values) {
        JsonObject value = text();
        JsonArray choices = new JsonArray();
        Arrays.stream(values).map(entry -> entry.name().toLowerCase(Locale.ROOT)).forEach(choices::add);
        value.add("enum", choices);
        return value;
    }
}
