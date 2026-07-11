package com.jvn.villagerretaliation.scene.compiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class SceneParser {
    public static final ResourceLocation SCHEMA_V1 = VillagerRetaliation.id("scene/v1");

    private SceneParser() {
    }

    public static ParseResult parse(ResourceLocation source, JsonObject root) {
        List<SceneDiagnostic> diagnostics = new ArrayList<>();
        if (root == null) return new ParseResult(null, List.of(error("scene.root", "", "scene root must be an object")));
        ResourceLocation schema = ResourceLocation.tryParse(DatapackJsonReader.readString(root, "schema"));
        if (!SCHEMA_V1.equals(schema)) diagnostics.add(error("scene.schema", "schema", "schema must be " + SCHEMA_V1));
        ResourceLocation id = parseId(DatapackJsonReader.readString(root, "id"), source, diagnostics, "id");
        List<SceneActorDeclaration> actors = parseActors(root.get("actors"), source, diagnostics);
        List<SceneResource.StepResource> steps = parseSteps(root.get("steps"), source, diagnostics);
        String entry = DatapackJsonReader.readString(root, "entry_step", "entry");
        if (entry.isBlank()) diagnostics.add(error("scene.entry.missing", "entry_step", "entry_step is required"));
        SceneResource resource = id == null ? null : new SceneResource(id, schema, stringMap(root.get("metadata")),
                enumValue(SceneResource.OwnershipMode.class, DatapackJsonReader.readString(root, "ownership"),
                        SceneResource.OwnershipMode.PLAYER, diagnostics, "ownership"),
                actors, entry, steps,
                enumValue(SceneResource.TransitionPolicy.class, DatapackJsonReader.readString(root, "failure_policy"),
                        SceneResource.TransitionPolicy.FAIL_SCENE, diagnostics, "failure_policy"),
                enumValue(SceneResource.TransitionPolicy.class, DatapackJsonReader.readString(root, "cancellation_policy"),
                        SceneResource.TransitionPolicy.CANCEL_SCENE, diagnostics, "cancellation_policy"),
                enumValue(SceneResource.CleanupPolicy.class, DatapackJsonReader.readString(root, "cleanup_policy"),
                        SceneResource.CleanupPolicy.OWNED_ENTITIES, diagnostics, "cleanup_policy"),
                Math.max(0L, valueLong(root, "timeout_ticks", 0L)),
                Math.max(1, valueInt(root, "definition_version", 1)), root);
        return new ParseResult(resource, List.copyOf(diagnostics));
    }

    private static List<SceneActorDeclaration> parseActors(JsonElement element, ResourceLocation source,
            List<SceneDiagnostic> diagnostics) {
        if (element == null || !element.isJsonArray()) {
            diagnostics.add(error("scene.actors", "actors", "actors must be an array"));
            return List.of();
        }
        List<SceneActorDeclaration> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            String path = "actors[" + index++ + "]";
            if (!child.isJsonObject()) {
                diagnostics.add(error("scene.actor.object", path, "actor must be an object"));
                continue;
            }
            JsonObject actor = child.getAsJsonObject();
            String alias = DatapackJsonReader.readString(actor, "alias").toLowerCase(Locale.ROOT);
            ResourceLocation type = parseId(DatapackJsonReader.readString(actor, "type"), source, diagnostics, path + ".type");
            if (!ids.add(alias)) diagnostics.add(error("scene.actor.duplicate", path + ".alias", "duplicate actor alias " + alias));
            if (type == null || alias.isBlank()) continue;
            try {
                result.add(new SceneActorDeclaration(alias, type, idSet(actor.get("capabilities"), source, diagnostics, path),
                        !actor.has("required") || actor.get("required").getAsBoolean(),
                        enumValue(SceneActorDeclaration.BindingSource.class, DatapackJsonReader.readString(actor, "binding_source"),
                                SceneActorDeclaration.BindingSource.UNBOUND, diagnostics, path + ".binding_source"),
                        DatapackJsonReader.readString(actor, "binding", "source"),
                        enumValue(SceneActorDeclaration.ReplacementPolicy.class, DatapackJsonReader.readString(actor, "replacement_policy"),
                                SceneActorDeclaration.ReplacementPolicy.FIXED, diagnostics, path + ".replacement_policy"),
                        enumValue(SceneActorDeclaration.MissingActorPolicy.class, DatapackJsonReader.readString(actor, "missing_actor_policy"),
                                SceneActorDeclaration.MissingActorPolicy.BLOCK, diagnostics, path + ".missing_actor_policy"),
                        enumValue(SceneActorDeclaration.DeathPolicy.class, DatapackJsonReader.readString(actor, "death_policy"),
                                SceneActorDeclaration.DeathPolicy.APPLY_MISSING_POLICY, diagnostics, path + ".death_policy"),
                        stringMap(actor.get("filters")), valueLong(actor, "timeout_ticks", 0L)));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(error("scene.actor.invalid", path, exception.getMessage()));
            }
        }
        return List.copyOf(result);
    }

    private static List<SceneResource.StepResource> parseSteps(JsonElement element, ResourceLocation source,
            List<SceneDiagnostic> diagnostics) {
        if (element == null || !element.isJsonArray()) {
            diagnostics.add(error("scene.steps", "steps", "steps must be an array with explicit stable ids"));
            return List.of();
        }
        List<SceneResource.StepResource> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            String path = "steps[" + index++ + "]";
            if (!child.isJsonObject()) {
                diagnostics.add(error("scene.step.object", path, "step must be an object"));
                continue;
            }
            JsonObject step = child.getAsJsonObject();
            String id = DatapackJsonReader.readString(step, "id");
            if (id.isBlank()) diagnostics.add(error("scene.step.id", path + ".id", "step requires an explicit stable id"));
            else if (!ids.add(id)) diagnostics.add(error("scene.step.duplicate", path + ".id", "duplicate step id " + id));
            ResourceLocation type = parseId(DatapackJsonReader.readString(step, "type"), source, diagnostics, path + ".type");
            if (id.isBlank() || type == null) continue;
            Map<String, String> transitions = stringMap(step.get("transitions"));
            String next = DatapackJsonReader.readString(step, "next");
            if (!next.isBlank()) {
                Map<String, String> withNext = new LinkedHashMap<>(transitions);
                withNext.putIfAbsent("success", next);
                transitions = Map.copyOf(withNext);
            }
            result.add(new SceneResource.StepResource(id, type, DatapackJsonReader.readStringList(step, "actors", "actor"),
                    step.has("data") && step.get("data").isJsonObject() ? step.getAsJsonObject("data") : step,
                    transitions, DatapackJsonReader.readString(step, "failure_step", "on_failure")));
        }
        return List.copyOf(result);
    }

    private static ResourceLocation parseId(String value, ResourceLocation source, List<SceneDiagnostic> diagnostics, String path) {
        ResourceLocation id = value.contains(":") ? ResourceLocation.tryParse(value)
                : value.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(source == null ? VillagerRetaliation.MOD_ID : source.getNamespace(), value);
        if (id == null) diagnostics.add(error("scene.resource_id", path, "invalid namespaced resource id \"" + value + "\""));
        return id;
    }

    private static Set<ResourceLocation> idSet(JsonElement element, ResourceLocation source,
            List<SceneDiagnostic> diagnostics, String path) {
        if (element == null) return Set.of();
        List<JsonElement> values = element.isJsonArray() ? element.getAsJsonArray().asList() : List.of(element);
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement value : values) if (value.isJsonPrimitive()) {
            ResourceLocation id = parseId(value.getAsString(), source, diagnostics, path);
            if (id != null) result.add(id);
        }
        return Set.copyOf(result);
    }

    private static Map<String, String> stringMap(JsonElement element) {
        if (element == null || !element.isJsonObject()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        element.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue().isJsonPrimitive())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        return Map.copyOf(result);
    }

    private static long valueLong(JsonObject object, String key, long fallback) {
        try { return object.has(key) ? object.get(key).getAsLong() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int valueInt(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback,
            List<SceneDiagnostic> diagnostics, String path) {
        if (value == null || value.isBlank()) return fallback;
        try { return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) {
            diagnostics.add(error("scene.enum", path, "unsupported value \"" + value + "\""));
            return fallback;
        }
    }

    private static SceneDiagnostic error(String code, String path, String message) {
        return new SceneDiagnostic(SceneDiagnostic.Severity.ERROR, code, path, message);
    }

    public record ParseResult(SceneResource resource, List<SceneDiagnostic> diagnostics) {
        public boolean valid() { return resource != null && diagnostics.stream().noneMatch(d -> d.severity() == SceneDiagnostic.Severity.ERROR); }
    }
}
