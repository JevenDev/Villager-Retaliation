package com.jvn.villagerretaliation.authoring.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.DialogueItemTransfer;
import com.jvn.villagerretaliation.quest.item.QuestItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;

final class AuthoringFeatureValidation {
    private AuthoringFeatureValidation() { }

    static ResourceLocation id(AuthoringDocument document) {
        String[] parts = document.resourcePath().split("/", 4);
        String path = parts[3];
        return ResourceLocation.fromNamespaceAndPath(parts[1], path.substring(0, path.length() - 5));
    }

    static List<AuthoringDiagnostic> validate(List<AuthoringDocument> documents, HolderLookup.Provider registries) {
        var issues = new ArrayList<AuthoringDiagnostic>();
        var items = new LinkedHashMap<ResourceLocation, AuthoringDocument>();
        var advancements = new LinkedHashMap<ResourceLocation, AuthoringDocument>();
        for (var document : documents) {
            if (document.resourcePath().matches("data/[^/]+/quest_items/.+[.]json")) items.put(id(document), document);
            if (document.resourcePath().matches("data/[^/]+/advancement/.+[.]json")) advancements.put(id(document), document);
        }
        for (var entry : items.entrySet()) check(issues, entry.getValue(), "", () -> {
            var definition = entry.getValue().json();
            var errors = com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(definition, QuestItems.schema());
            if (!errors.isEmpty()) throw new IllegalArgumentException(errors.getFirst().message());
            if (registries != null) QuestItems.create(registries, entry.getKey(), definition);
        });
        for (var entry : advancements.entrySet()) check(issues, entry.getValue(), "", () -> {
            var definition = entry.getValue().json();
            if (registries != null) net.minecraft.advancements.Advancement.CODEC.parse(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), definition)
                    .getOrThrow(message -> new IllegalArgumentException(message));
            var seen = new java.util.HashSet<ResourceLocation>();
            ResourceLocation current = entry.getKey();
            while (advancements.containsKey(current)) {
                if (!seen.add(current)) throw new IllegalArgumentException("Advancement parent cycle");
                var value = advancements.get(current).json();
                if (!value.has("parent")) break;
                current = ResourceLocation.parse(value.get("parent").getAsString());
                if (!advancements.containsKey(current) && !installed(current)) {
                    if (net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null)
                        throw new IllegalArgumentException("Unknown advancement parent: " + current);
                    issues.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.WARNING, entry.getValue().resourcePath(), "/parent",
                            "External advancement parent cannot be checked without its server: " + current));
                }
            }
        });
        var operations = new LinkedHashMap<String, JsonObject>();
        for (var document : documents) walk(document.json(), "", (value, pointer) -> {
            String type = DialogueGraph.string(value, "type");
            if (type.equals("item_transfer")) check(issues, document, pointer, () -> {
                DialogueItemTransfer.validate(value);
                String owner = document.kind() == DefinitionKind.QUEST ? document.id() : DialogueGraph.string(document.json(), "quest");
                if (value.has("quest")) owner = value.get("quest").getAsString();
                String operation = owner + "/" + value.get("operation_id").getAsString();
                var prior = operations.putIfAbsent(operation, value);
                if (prior != null && !prior.equals(value)) throw new IllegalArgumentException("Transfer operation ID has different terms elsewhere in this pack");
            });
            if (value.has("quest_item")) check(issues, document, pointer + "/quest_item", () -> {
                var key = ResourceLocation.parse(value.get("quest_item").getAsString());
                if (!items.containsKey(key)) QuestItems.definition(key);
            });
            if (type.equals("advancement") && value.has("advancement")) check(issues, document, pointer + "/advancement", () -> {
                var key = ResourceLocation.parse(value.get("advancement").getAsString());
                var draft = advancements.get(key);
                if (draft == null && !installed(key)) {
                    if (net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null)
                        throw new IllegalArgumentException("Unknown advancement: " + key);
                    issues.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.WARNING, document.resourcePath(), pointer + "/advancement",
                            "External advancement and criterion cannot be checked without their server: " + key));
                    return;
                }
                if (value.has("criterion") && !value.get("criterion").getAsString().isBlank()) {
                    String criterion = value.get("criterion").getAsString();
                    if (draft != null ? !draft.json().getAsJsonObject("criteria").has(criterion)
                            : !net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().getAdvancements().get(key).value().criteria().containsKey(criterion))
                        throw new IllegalArgumentException("Unknown advancement criterion: " + criterion);
                }
            });
        });
        return issues;
    }

    private static boolean installed(ResourceLocation id) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && server.getAdvancements().get(id) != null;
    }

    private static void check(List<AuthoringDiagnostic> issues, AuthoringDocument document, String pointer, Runnable run) {
        try { run.run(); }
        catch (IllegalArgumentException | IllegalStateException exception) {
            issues.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.ERROR, document.resourcePath(), pointer, exception.getMessage()));
        }
    }

    private static void walk(JsonElement value, String pointer, java.util.function.BiConsumer<JsonObject, String> visit) {
        if (value.isJsonObject()) {
            visit.accept(value.getAsJsonObject(), pointer);
            value.getAsJsonObject().entrySet().forEach(entry -> walk(entry.getValue(), pointer + "/" + JsonPointers.escape(entry.getKey()), visit));
        } else if (value.isJsonArray()) for (int index = 0; index < value.getAsJsonArray().size(); index++)
            walk(value.getAsJsonArray().get(index), pointer + "/" + index, visit);
    }
}
