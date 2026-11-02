package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.dialogue.DialogueWordPool;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

public final class PreviewWordPools {
    private PreviewWordPools() { }

    public static Function<ResourceLocation, List<String>> lookup(AuthoringWorkspace workspace, AuthoringDocument owner,
            String locale, java.util.function.BiFunction<ResourceLocation, String, List<String>> fallback) {
        var definitions = lookupDefinitions(workspace, owner, locale, (id, language) -> new DialogueWordPool(
                fallback.apply(id, language).stream().map(text -> new DialogueWordPool.Entry(text, 1, List.of())).toList()));
        return id -> definitions.apply(id).values();
    }

    public static Function<ResourceLocation, DialogueWordPool> lookupDefinitions(AuthoringWorkspace workspace, AuthoringDocument owner,
            String locale, java.util.function.BiFunction<ResourceLocation, String, DialogueWordPool> fallback) {
        Map<String, AuthoringDocument> documents = new LinkedHashMap<>();
        workspace.documents().forEach(document -> documents.put(document.resourcePath(), document));
        String scope = DefinitionKind.contentRoot(owner.resourcePath());
        return id -> {
            var loaded = new LinkedHashMap<ResourceLocation, DialogueWordPool>();
            var library = new com.jvn.villagerretaliation.dialogue.DialogueWordPoolLibrary(key -> {
                for (String language : List.of(VillagerLocale.normalize(locale), VillagerLocale.DEFAULT_LOCALE)) {
                    String suffix = "dialogue_words/" + language + "/" + key.getPath() + ".json";
                    String root = "data/" + key.getNamespace() + "/";
                    var scoped = scope.startsWith(root) ? documents.get(scope + suffix) : null;
                    var document = scoped == null ? documents.get(root + suffix) : scoped;
                    if (document != null) return document.json();
                    var installed = fallback.apply(key, language);
                    if (!installed.entries().isEmpty()) { loaded.put(key, installed); return null; }
                }
                return null;
            }, key -> loaded.getOrDefault(key, DialogueWordPool.EMPTY));
            try { return library.apply(id); }
            catch (IllegalArgumentException exception) {
                DatapackDiagnostics.warnSkippedEntry(id, "preview word pool", locale, exception.getMessage());
                return DialogueWordPool.EMPTY;
            }
        };
    }

    public static Function<ResourceLocation, com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.Rule> contexts(
            AuthoringWorkspace workspace, AuthoringDocument owner) {
        var definitions = new LinkedHashMap<ResourceLocation, com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.Rule>();
        String scope = DefinitionKind.contentRoot(owner.resourcePath());
        var documents = workspace.documents().stream().filter(document -> DefinitionKind.unscopedPath(document.resourcePath())
                .matches("data/[^/]+/dialogue_contexts/.+[.]json"))
                .sorted(java.util.Comparator.comparingInt(document -> DefinitionKind.contentRoot(document.resourcePath()).equals(scope) ? 1 : 0)).toList();
        for (var document : documents) {
            String root = DefinitionKind.contentRoot(document.resourcePath());
            if (root.split("/").length == 4 && !root.equals(scope)) continue;
            var json = document.json();
            if (!json.has("id") || !json.get("id").isJsonPrimitive() || !json.get("id").getAsJsonPrimitive().isString()) continue;
            var id = ResourceLocation.tryParse(json.get("id").getAsString());
            if (id == null) continue;
            try {
                definitions.put(id, com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.readRule(id, json));
            } catch (IllegalArgumentException exception) {
                definitions.put(id, new com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.Rule(id,
                        List.of(new com.jvn.villagerretaliation.dialogue.DialogueCondition.Invalid(exception.getMessage()))));
                DatapackDiagnostics.warnSkippedEntry(id, "preview context", "conditions", exception.getMessage());
            }
        }
        return id -> definitions.containsKey(id) ? definitions.get(id) : bundledContext(id);
    }

    public static com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.Rule bundledContext(ResourceLocation id) {
        var loaded = com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.rule(id);
        if (loaded != null) return loaded;
        String path = "/data/" + id.getNamespace() + "/dialogue_contexts/" + id.getPath() + ".json";
        var stream = PreviewWordPools.class.getResourceAsStream(path);
        if (stream == null) return null;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            return com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.readRule(id, json);
        } catch (IOException | com.google.gson.JsonParseException | IllegalStateException | IllegalArgumentException exception) {
            DatapackDiagnostics.warnSkippedEntry(id, "preview context", "conditions", exception.getMessage());
            return null;
        }
    }

    public static DialogueWordPool bundled(ResourceLocation id, String locale) {
        var library = new com.jvn.villagerretaliation.dialogue.DialogueWordPoolLibrary(key -> {
            for (String language : List.of(VillagerLocale.normalize(locale), VillagerLocale.DEFAULT_LOCALE)) {
                var definition = com.jvn.villagerretaliation.dialogue.DialogueWordPoolLibrary.bundledDefinition(key, language);
                if (definition != null) return definition;
            }
            return null;
        }, key -> DialogueWordPool.EMPTY);
        try { return library.apply(id); }
        catch (IllegalArgumentException exception) {
            DatapackDiagnostics.warnSkippedEntry(id, "preview word pool", locale, exception.getMessage());
            return DialogueWordPool.EMPTY;
        }
    }
}
