package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;

final class EditorResourceRecipes {
    enum Kind {
        SCENE("Scene", "scenes", "Actors, steps and outcome links for a scripted scene."),
        ENCOUNTER("Encounter", "encounters", "Entity rosters, waves and authored spawn points.");
        final String label, directory, description;
        Kind(String label, String directory, String description) { this.label = label; this.directory = directory; this.description = description; }
    }

    static AuthoringDocument create(AuthoringWorkspace workspace, Kind kind, String id, AuthoringDocument scope) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null || key.getPath().contains("/")) throw new IllegalArgumentException("Use a single-segment resource ID such as mypack:opening");
        String root = "data/" + key.getNamespace() + "/";
        if (scope != null) {
            if (!workspace.documents().contains(scope) || !scope.kind().campaignManifest()) throw new IllegalArgumentException("Choose a campaign or addon in this workspace");
            if (!scope.resourcePath().startsWith(root)) throw new IllegalArgumentException("Content must use its campaign or addon namespace");
            root = DefinitionKind.contentRoot(scope.resourcePath());
        }
        var json = seed(kind, id);
        String targetRoot = root;
        if (workspace.documents().stream().anyMatch(document -> document.id().equals(id)
                && DefinitionKind.contentRoot(document.resourcePath()).equals(targetRoot)
                && DialogueGraph.string(document.json(), "schema").equals(DialogueGraph.string(json, "schema"))))
            throw new IllegalArgumentException(kind.label + " ID already exists in this content folder: " + id);
        var document = new AuthoringDocument(DefinitionKind.RESOURCE, root + "quests/_shared/" + kind.directory + "/" + key.getPath() + ".json", json);
        String localePath = root + "quests/_shared/locales/en_us.json";
        var locale = workspace.documents().stream().filter(value -> value.resourcePath().equals(localePath)).findFirst().orElse(null);
        if (kind == Kind.SCENE) {
            if (locale == null && java.util.stream.Stream.concat(workspace.filePaths().stream(), workspace.folderPaths().stream()).anyMatch(path -> path.equalsIgnoreCase(localePath)))
                throw new IllegalArgumentException("The scene locale is not editable: " + localePath);
            var messages = locale == null ? new JsonObject() : locale.json();
            if (messages.has("messages")) messages = messages.getAsJsonObject("messages");
            if (messages.has(id + ".opening")) throw new IllegalArgumentException("Scene wording already exists: " + id + ".opening");
        }
        workspace.add(document);
        if (kind == Kind.SCENE) {
            if (locale == null) { locale = new AuthoringDocument(DefinitionKind.RESOURCE, localePath, new JsonObject()); workspace.add(locale); }
            locale.edit("Add scene wording", value -> {
                var messages = value.has("messages") ? value.getAsJsonObject("messages") : value;
                messages.addProperty(id + ".opening", "Let's get started.");
            });
        }
        return document;
    }

    static JsonObject seed(Kind kind, String id) {
        if (kind == Kind.SCENE) {
            var scene = EditorQuestLinks.scene(id, "");
            scene.addProperty("ownership", "player");
            var reference = new JsonObject(); reference.addProperty("key", id + ".opening");
            scene.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("data").add("text", reference);
            return scene;
        }
        var encounter = JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","controller":"villagerretaliation:controlled",
                 "members":[{"id":"guard","entity":"minecraft:zombie","count":2}],"spawn_radius":8}
                """).getAsJsonObject();
        encounter.addProperty("id", id);
        return encounter;
    }
}
