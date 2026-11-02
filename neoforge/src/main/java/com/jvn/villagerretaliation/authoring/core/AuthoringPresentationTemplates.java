package com.jvn.villagerretaliation.authoring.core;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialoguePresentationTemplates;
import com.jvn.villagerretaliation.dialogue.resources.DialogueTextTemplateResources;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class AuthoringPresentationTemplates {
    private AuthoringPresentationTemplates() { }

    public static DialoguePresentationTemplates.Library library(List<AuthoringDocument> documents, AuthoringDocument owner) {
        var paths = new LinkedHashMap<String, AuthoringDocument>();
        documents.forEach(document -> paths.put(document.resourcePath(), document));
        String[] parts = DefinitionKind.unscopedPath(owner.resourcePath()).split("/", 5);
        String locale = parts.length > 3 && List.of("dialogue", "dialogue_templates").contains(parts[2]) ? parts[3] : "en_us";
        locale = com.jvn.villagerretaliation.util.VillagerLocale.normalize(locale);
        String scope = DefinitionKind.contentRoot(owner.resourcePath());
        var languages = new java.util.LinkedHashSet<>(List.of(locale, "en_us"));
        return new DialoguePresentationTemplates.Library(id -> {
            for (String language : languages) {
                String root = "data/" + id.getNamespace() + "/";
                String suffix = "dialogue_templates/" + language + "/" + id.getPath() + ".json";
                var document = scope.startsWith(root) ? paths.get(scope + suffix) : null;
                if (document == null) document = paths.get(root + suffix);
                if (document != null) return document.json();
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                var installed = DialogueTextTemplateResources.localizedDefinition(server, language, id);
                if (installed != null) return installed;
            }
            return null;
        });
    }

    public static JsonObject expand(AuthoringDocument owner, List<AuthoringDocument> documents) {
        return DialoguePresentationTemplates.expandDocument(owner.json(), library(documents, owner),
                (pointer, issue) -> { throw new IllegalArgumentException(pointer + ": " + issue); });
    }

    public static List<AuthoringDiagnostic> validate(List<AuthoringDocument> documents) {
        var result = new ArrayList<AuthoringDiagnostic>();
        for (var document : documents) {
            String path = DefinitionKind.unscopedPath(document.resourcePath());
            boolean definition = path.matches("data/[^/]+/dialogue_templates/[^/]+/.+[.]json");
            if (!definition && !path.matches("data/[^/]+/dialogue/[^/]+/.+[.]json")) continue;
            try {
                var library = library(documents, document);
                if (definition) {
                    String[] parts = path.split("/", 5);
                    library.apply(ResourceLocation.fromNamespaceAndPath(parts[1], parts[4].substring(0, parts[4].length() - 5)));
                } else DialoguePresentationTemplates.expandDocument(document.json(), library,
                        (pointer, issue) -> result.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.ERROR, document.resourcePath(), pointer, issue)));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                result.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.ERROR, document.resourcePath(), "", exception.getMessage()));
            }
        }
        return List.copyOf(result);
    }
}
