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
            for (String language : List.of(VillagerLocale.normalize(locale), VillagerLocale.DEFAULT_LOCALE)) {
                String suffix = "dialogue_words/" + language + "/" + id.getPath() + ".json";
                String root = "data/" + id.getNamespace() + "/";
                var scoped = scope.startsWith(root) ? documents.get(scope + suffix) : null;
                var document = scoped == null ? documents.get(root + suffix) : scoped;
                if (document != null) {
                    try {
                        return DialogueWordPool.read(document.json());
                    } catch (IllegalArgumentException exception) {
                        DatapackDiagnostics.warnSkippedEntry(id, "preview word pool", language, exception.getMessage());
                        continue;
                    }
                }
                var values = fallback.apply(id, language);
                if (!values.entries().isEmpty()) return values;
            }
            return DialogueWordPool.EMPTY;
        };
    }

    public static DialogueWordPool bundled(ResourceLocation id, String locale) {
        String path = "/data/" + id.getNamespace() + "/dialogue_words/" + VillagerLocale.normalize(locale) + "/" + id.getPath() + ".json";
        var stream = PreviewWordPools.class.getResourceAsStream(path);
        if (stream == null) return DialogueWordPool.EMPTY;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return DialogueWordPool.read(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | com.google.gson.JsonParseException | IllegalStateException | IllegalArgumentException exception) {
            DatapackDiagnostics.warnSkippedEntry(id, "preview word pool", locale, exception.getMessage());
            return DialogueWordPool.EMPTY;
        }
    }
}
