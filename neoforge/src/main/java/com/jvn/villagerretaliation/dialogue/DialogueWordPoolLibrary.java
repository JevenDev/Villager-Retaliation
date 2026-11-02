package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

public final class DialogueWordPoolLibrary implements Function<ResourceLocation, DialogueWordPool> {
    private final Function<ResourceLocation, JsonObject> definitions;
    private final Function<ResourceLocation, DialogueWordPool> fallback;
    private final Map<ResourceLocation, DialogueWordPool> compiled = new HashMap<>();
    private final Set<ResourceLocation> active = new HashSet<>();

    public DialogueWordPoolLibrary(Function<ResourceLocation, JsonObject> definitions, Function<ResourceLocation, DialogueWordPool> fallback) {
        this.definitions = definitions; this.fallback = fallback;
    }

    public static JsonObject bundledDefinition(ResourceLocation id, String locale) {
        String path = "/data/" + id.getNamespace() + "/dialogue_words/" + locale + "/" + id.getPath() + ".json";
        var stream = DialogueWordPoolLibrary.class.getResourceAsStream(path);
        if (stream == null) return null;
        try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException | com.google.gson.JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Cannot read bundled word pool " + id, exception);
        }
    }

    @Override public DialogueWordPool apply(ResourceLocation id) {
        if (compiled.containsKey(id)) return compiled.get(id);
        if (active.size() >= 16 || !active.add(id)) throw new IllegalArgumentException("cyclic or excessively nested word pool inheritance: " + id);
        try {
            var json = definitions.apply(id);
            if (json == null) return fallback.apply(id);
            var local = DialogueWordPool.read(json);
            var entries = new ArrayList<DialogueWordPool.Entry>();
            var parent = DialogueWordPool.EMPTY;
            if (json.has("extends")) {
                var parentId = ResourceLocation.parse(DialogueWordPool.resourceId(json, "extends"));
                parent = apply(parentId);
                if (parent.entries().isEmpty()) throw new IllegalArgumentException("missing parent word pool: " + parentId);
                entries.addAll(parent.entries());
                if (json.has("remove")) for (var removed : json.getAsJsonArray("remove")) {
                    if (!entries.removeIf(entry -> entry.id().equals(removed.getAsString()))) throw new IllegalArgumentException("unknown inherited entry ID: " + removed.getAsString());
                }
            }
            for (var entry : local.entries()) {
                var sourced = new DialogueWordPool.Entry(entry.text(), entry.weight(), entry.conditions(), entry.priority(), entry.variant(), entry.id(), id + entry.source());
                int index = -1;
                if (!entry.id().isEmpty()) for (int i = 0; i < entries.size(); i++) if (entry.id().equals(entries.get(i).id())) { index = i; break; }
                if (index < 0) entries.add(sourced); else entries.set(index, sourced);
            }
            for (var override : DialogueWordPool.textOverrides(json).entrySet()) {
                int index = -1;
                for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(override.getKey())) { index = i; break; }
                if (index < 0) throw new IllegalArgumentException("unknown inherited entry ID in text_overrides: " + override.getKey());
                var entry = entries.get(index);
                entries.set(index, new DialogueWordPool.Entry(override.getValue(), entry.weight(), entry.conditions(),
                        entry.priority(), entry.variant(), entry.id(), id + "/text_overrides/" + entry.id()));
            }
            var result = new DialogueWordPool(entries,
                    json.has("selection_group") ? local.selectionGroup() : parent.selectionGroup(),
                    json.has("avoid_recent") ? local.avoidRecent() : parent.avoidRecent(),
                    json.has("role") ? local.role() : parent.role(),
                    json.has("optional") ? local.optional() : parent.optional(),
                    json.has("chance") ? local.chance() : parent.chance());
            result.validateResolved(); compiled.put(id, result); return result;
        } finally { active.remove(id); }
    }
}
