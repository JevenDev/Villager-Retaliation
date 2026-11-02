package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

public final class DialoguePresentationTemplates {
    private static final Set<String> PRESENTATION = Set.of("text", "lines", "variants", "text_key");
    private static final Set<String> FIELDS = Set.of("extends", "parameters", "arguments", "text", "lines", "variants", "text_key");
    private static final Pattern PARAMETER = Pattern.compile("\\{([a-z][a-z0-9_]*)}");
    private static final int MAX_TEXT = 16384;

    private DialoguePresentationTemplates() { }

    public record Definition(JsonObject presentation, Set<String> parameters, Map<String, String> arguments) { }

    public static final class Library {
        private final Function<ResourceLocation, JsonObject> lookup;
        private final Map<ResourceLocation, Definition> compiled = new HashMap<>();
        private final Set<ResourceLocation> active = new HashSet<>();

        public Library(Function<ResourceLocation, JsonObject> lookup) { this.lookup = lookup; }

        public Definition apply(ResourceLocation id) {
            if (compiled.containsKey(id)) return compiled.get(id);
            if (active.size() >= 16 || !active.add(id)) throw new IllegalArgumentException("cyclic or excessively nested presentation template: " + id);
            try {
                var root = lookup.apply(id);
                if (root == null) throw new IllegalArgumentException("missing presentation template: " + id);
                if (root.keySet().stream().anyMatch(key -> !FIELDS.contains(key))) throw new IllegalArgumentException("unknown presentation template field: " + id);
                var presentation = new JsonObject();
                var parameters = new LinkedHashSet<String>();
                var arguments = new LinkedHashMap<String, String>();
                if (root.has("extends")) {
                    var parent = apply(ResourceLocation.parse(DialogueWordPool.resourceId(root, "extends")));
                    presentation = parent.presentation().deepCopy();
                    parameters.addAll(parent.parameters());
                    arguments.putAll(parent.arguments());
                }
                if (root.has("parameters")) {
                    if (!root.get("parameters").isJsonArray() || root.getAsJsonArray("parameters").size() > 32)
                        throw new IllegalArgumentException("template parameters must be an array of at most 32 names");
                    var local = new HashSet<String>();
                    for (var value : root.getAsJsonArray("parameters")) {
                        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                                || !value.getAsString().matches("[a-z][a-z0-9_]{0,63}") || !local.add(value.getAsString()))
                            throw new IllegalArgumentException("template parameters require unique lowercase names");
                        parameters.add(value.getAsString());
                    }
                }
                arguments.putAll(arguments(root));
                for (String key : PRESENTATION) if (root.has(key)) {
                    if (!root.has("extends")) presentation.add(key, root.get(key).deepCopy());
                    else {
                        presentation = new JsonObject();
                        for (String field : PRESENTATION) if (root.has(field)) presentation.add(field, root.get(field).deepCopy());
                        break;
                    }
                }
                if (presentation.isEmpty()) throw new IllegalArgumentException("presentation template needs text or variants: " + id);
                if (!parameters.containsAll(arguments.keySet())) throw new IllegalArgumentException("unknown default template argument: " + id);
                var definition = new Definition(presentation, Set.copyOf(parameters), Map.copyOf(arguments));
                compiled.put(id, definition);
                return definition;
            } finally { active.remove(id); }
        }
    }

    private static Map<String, String> arguments(JsonObject source) {
        if (!source.has("arguments")) return Map.of();
        if (!source.get("arguments").isJsonObject() || source.getAsJsonObject("arguments").size() > 32)
            throw new IllegalArgumentException("template arguments must be an object of at most 32 strings");
        var result = new LinkedHashMap<String, String>();
        for (var entry : source.getAsJsonObject("arguments").entrySet()) {
            if (!entry.getKey().matches("[a-z][a-z0-9_]{0,63}") || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString() || entry.getValue().getAsString().length() > MAX_TEXT)
                throw new IllegalArgumentException("template arguments require lowercase names and string values");
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    public static JsonObject expand(JsonObject entry, Library library) {
        if (!entry.has("template")) {
            if (entry.has("arguments")) throw new IllegalArgumentException("arguments requires a presentation template");
            return entry;
        }
        if (PRESENTATION.stream().anyMatch(entry::has)) throw new IllegalArgumentException("template instances cannot also supply text or variants");
        var definition = library.apply(ResourceLocation.parse(DialogueWordPool.resourceId(entry, "template")));
        var arguments = new LinkedHashMap<>(definition.arguments());
        arguments.putAll(arguments(entry));
        if (!definition.parameters().containsAll(arguments.keySet())) throw new IllegalArgumentException("unknown template argument");
        for (String name : definition.parameters()) if (!arguments.containsKey(name)) throw new IllegalArgumentException("missing template argument: " + name);
        var resolved = new HashMap<String, String>();
        var active = new HashSet<String>();
        for (String name : definition.parameters()) resolve(name, arguments, definition.parameters(), resolved, active);
        var result = entry.deepCopy();
        result.remove("template");
        result.remove("arguments");
        definition.presentation().entrySet().forEach(field -> result.add(field.getKey(), substitute(field.getValue(), resolved, true)));
        return result;
    }

    private static String resolve(String name, Map<String, String> arguments, Set<String> parameters,
            Map<String, String> resolved, Set<String> active) {
        if (resolved.containsKey(name)) return resolved.get(name);
        if (!active.add(name)) throw new IllegalArgumentException("cyclic template argument: " + name);
        try {
            String value = replace(arguments.get(name), key -> parameters.contains(key) ? resolve(key, arguments, parameters, resolved, active) : null);
            resolved.put(name, value);
            return value;
        } finally { active.remove(name); }
    }

    private static String replace(String source, Function<String, String> values) {
        var matcher = PARAMETER.matcher(source);
        var result = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            String value = values.apply(matcher.group(1));
            if (value == null) continue;
            if (result.length() + matcher.start() - cursor + value.length() > MAX_TEXT) throw new IllegalArgumentException("expanded template text is too long");
            result.append(source, cursor, matcher.start()).append(value);
            cursor = matcher.end();
        }
        if (result.length() + source.length() - cursor > MAX_TEXT) throw new IllegalArgumentException("expanded template text is too long");
        return result.append(source, cursor, source.length()).toString();
    }

    private static JsonElement substitute(JsonElement value, Map<String, String> arguments, boolean text) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && text)
            return new JsonPrimitive(replace(value.getAsString(), arguments::get));
        if (value.isJsonArray()) {
            var result = new JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(substitute(child, arguments, text)));
            return result;
        }
        if (value.isJsonObject()) {
            var result = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(field -> result.add(field.getKey(),
                    substitute(field.getValue(), arguments, Set.of("text", "lines", "variants").contains(field.getKey()))));
            return result;
        }
        return value.deepCopy();
    }

    public static JsonObject expandDocument(JsonObject source, Library library, BiConsumer<String, String> diagnostic) {
        var result = source.deepCopy();
        if (result.has("template") || result.has("arguments")) {
            try { return expand(result, library); }
            catch (IllegalArgumentException exception) { diagnostic.accept("/template", exception.getMessage()); return new JsonObject(); }
        }
        for (String section : Set.of("messages", "openings", "closings", "lines")) {
            if (!result.has(section) || !result.get(section).isJsonArray()) continue;
            var entries = result.getAsJsonArray(section);
            for (int i = 0; i < entries.size(); i++) {
                if (!entries.get(i).isJsonObject() || !(entries.get(i).getAsJsonObject().has("template") || entries.get(i).getAsJsonObject().has("arguments"))) continue;
                try { entries.set(i, expand(entries.get(i).getAsJsonObject(), library)); }
                catch (IllegalArgumentException exception) {
                    diagnostic.accept("/" + section + "/" + i + "/template", exception.getMessage());
                    entries.set(i, com.google.gson.JsonNull.INSTANCE);
                }
            }
        }
        return result;
    }
}
