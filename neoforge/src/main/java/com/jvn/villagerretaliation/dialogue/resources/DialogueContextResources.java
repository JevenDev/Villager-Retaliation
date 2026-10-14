package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class DialogueContextResources {
    public record Rule(ResourceLocation source, List<DialogueCondition> conditions) {
        public Rule { conditions = List.copyOf(conditions); }
    }

    private static volatile Map<ResourceLocation, Rule> rules = Map.of();
    private DialogueContextResources() { }

    public static Rule rule(ResourceLocation id) { return rules.get(id); }

    public static void reload(ResourceManager manager) {
        var documents = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (var entry : new TreeMap<>(manager.listResources("dialogue_contexts", id -> id.getPath().endsWith(".json"))).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                documents.put(entry.getKey(), JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | com.google.gson.JsonParseException | IllegalStateException exception) {
                throw new IllegalArgumentException("Invalid dialogue context at " + entry.getKey(), exception);
            }
        }
        var compiled = compile(documents);
        for (String root : List.of("dialogue", "dialogue_trees", "forced_dialogue", "quests")) {
            for (var entry : manager.listResources(root, id -> id.getPath().endsWith(".json")).entrySet()) {
                try (var reader = entry.getValue().openAsReader()) {
                    var value = JsonParser.parseReader(reader);
                    validateReferences(value, compiled, entry.getKey().toString());
                    com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.warnMarkup(entry.getKey(), value, "", false);
                } catch (IOException | com.google.gson.JsonParseException | IllegalStateException exception) {
                    com.jvn.villagerretaliation.util.DatapackDiagnostics.warnSkippedFile(entry.getKey(), "dialogue context references", exception);
                }
            }
        }
        rules = compiled;
    }

    public static Map<ResourceLocation, Rule> compile(Map<ResourceLocation, JsonObject> documents) {
        var parsed = new LinkedHashMap<ResourceLocation, Rule>();
        for (var entry : documents.entrySet()) {
            var value = entry.getValue();
            if (!value.keySet().equals(Set.of("id", "conditions")) || !value.get("id").isJsonPrimitive()
                    || !value.get("id").getAsString().contains(":") || !value.get("conditions").isJsonArray())
                throw new IllegalArgumentException(entry.getKey() + ": expected explicit id and conditions array");
            var id = ResourceLocation.parse(value.get("id").getAsString());
            var conditions = DialogueCondition.readList(entry.getKey(), "/conditions", value);
            validateConditions(conditions, entry.getKey().toString());
            if (parsed.putIfAbsent(id, new Rule(entry.getKey(), conditions)) != null)
                throw new IllegalArgumentException(entry.getKey() + ": duplicate dialogue context " + id);
        }
        for (var entry : documents.entrySet()) validateReferences(entry.getValue(), parsed, entry.getKey().toString());
        for (var id : parsed.keySet()) visit(id, parsed, new java.util.LinkedHashSet<>(), new java.util.HashSet<>());
        return Map.copyOf(parsed);
    }

    private static void validateConditions(List<DialogueCondition> conditions, String source) {
        for (var condition : conditions) {
            if (condition instanceof DialogueCondition.Invalid)
                throw new IllegalArgumentException(source + ": invalid context condition");
            if (condition instanceof DialogueCondition.AllOf all) validateConditions(all.conditions(), source);
            if (condition instanceof DialogueCondition.AnyOf any) validateConditions(any.conditions(), source);
            if (condition instanceof DialogueCondition.Not not) validateConditions(List.of(not.condition()), source);
        }
    }

    public static void validateReferences(JsonElement value, Map<ResourceLocation, Rule> definitions, String pointer) {
        if (value == null) return;
        if (value.isJsonArray()) {
            for (int index = 0; index < value.getAsJsonArray().size(); index++)
                validateReferences(value.getAsJsonArray().get(index), definitions, pointer + "/" + index);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("type") && object.get("type").isJsonPrimitive()
                    && object.get("type").getAsString().equals("context")) {
                ResourceLocation id = object.has("id") && object.get("id").isJsonPrimitive()
                        ? ResourceLocation.tryParse(object.get("id").getAsString()) : null;
                if (id == null || !definitions.containsKey(id))
                    throw new IllegalArgumentException(pointer + ": missing dialogue context " + object.get("id"));
            }
            for (var entry : object.entrySet())
                validateReferences(entry.getValue(), definitions, pointer + "/" + entry.getKey());
        }
    }

    private static void visit(ResourceLocation id, Map<ResourceLocation, Rule> definitions,
            Set<ResourceLocation> visiting, Set<ResourceLocation> complete) {
        if (complete.contains(id)) return;
        if (!visiting.add(id) || visiting.size() > 64)
            throw new IllegalArgumentException(definitions.get(id).source() + ": cyclic or excessively nested dialogue context " + visiting + " -> " + id);
        for (var child : references(definitions.get(id).conditions())) visit(child, definitions, visiting, complete);
        visiting.remove(id);
        complete.add(id);
    }

    private static List<ResourceLocation> references(List<DialogueCondition> conditions) {
        var result = new ArrayList<ResourceLocation>();
        for (var condition : conditions) {
            if (condition instanceof DialogueCondition.ContextRule reference) result.add(reference.id());
            if (condition instanceof DialogueCondition.AllOf all) result.addAll(references(all.conditions()));
            if (condition instanceof DialogueCondition.AnyOf any) result.addAll(references(any.conditions()));
            if (condition instanceof DialogueCondition.Not not) result.addAll(references(List.of(not.condition())));
        }
        return result;
    }
}
