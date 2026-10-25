package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.resources.ResourceLocation;

final class EditorSceneChecks {
    static int run() throws IOException {
        recipesUseLoadableScopedBundlePaths();
        dialogueStepsGroupWordingAndUndo();
        stepRenamesUpdateRoutesAndEncounterHandles();
        stepRemovalRedirectsIncomingLinksAtomically();
        rehearsalRespectsRoutesFailuresAndLoopLimits();
        actorRenamesKeepBindingsAndUpdateOnlyReferences();
        actorPickingWritesRuntimeBindings();
        wavesKeepCompositionAndStableMemberIds();
        spawnPointsAndReorderingPreserveOtherSettings();
        return 9;
    }

    private static AuthoringWorkspace workspace() throws IOException { return AuthoringWorkspace.create(Files.createTempDirectory("scene-editor-check")); }
    private static AuthoringDocument scene(AuthoringWorkspace workspace) { return EditorResourceRecipes.create(workspace, EditorResourceRecipes.Kind.SCENE, "test:scene", null); }
    private static AuthoringDocument encounter(AuthoringWorkspace workspace) { return EditorResourceRecipes.create(workspace, EditorResourceRecipes.Kind.ENCOUNTER, "test:encounter", null); }

    private static void recipesUseLoadableScopedBundlePaths() throws IOException {
        var workspace = workspace(); var campaign = workspace.createDocument(DefinitionKind.WORLD_CAMPAIGN, "test:story");
        var scene = EditorResourceRecipes.create(workspace, EditorResourceRecipes.Kind.SCENE, "test:opening", campaign);
        check(scene.resourcePath().equals("data/test/world_campaigns/story/quests/_shared/scenes/opening.json"), "new scenes use their campaign shared bundle");
        var parsed = SceneParser.parse(ResourceLocation.parse(scene.id()), scene.json());
        check(parsed.diagnostics().isEmpty() && SceneCompiler.compile(parsed.resource()).diagnostics().stream()
                .noneMatch(value -> value.severity().name().equals("ERROR")), "the scene recipe compiles with runtime rules");
        var encounter = encounter(workspace);
        check(com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(encounter.json(),
                com.jvn.villagerretaliation.scene.schema.SceneSchema.encounterV1()).isEmpty(), "the encounter recipe matches its runtime schema");
        rejects(() -> EditorResourceRecipes.create(workspace, EditorResourceRecipes.Kind.SCENE, "test:opening", campaign));
        rejects(() -> EditorResourceRecipes.create(workspace, EditorResourceRecipes.Kind.SCENE, "other:opening", campaign));
        rejects(() -> EditorResourceRecipes.create(workspace, EditorResourceRecipes.Kind.SCENE, "test:nested/name", null));
        check(workspace.documents().size() == 4, "rejected recipes do not leave partial files");
    }

    private static void dialogueStepsGroupWordingAndUndo() throws IOException {
        var workspace = workspace(); var scene = scene(workspace);
        String id = EditorSceneDraft.addStep(workspace, scene, "villagerretaliation:dialogue");
        var step = EditorSceneDraft.steps(scene.json()).stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        String key = step.value().getAsJsonObject("data").getAsJsonObject("text").get("key").getAsString();
        var location = workspace.textLocation(scene, key, "en_us");
        check(location != null && JsonPointers.get(location.document().json(), location.pointer()).getAsString().equals("Hello, {player}!"), "dialogue steps create localized wording");
        scene.undo();
        check(EditorSceneDraft.steps(scene.json()).size() == 2 && workspace.textLocation(scene, key, "en_us") == null, "undo removes the step and its wording together");
        scene.redo();
        check(EditorSceneDraft.steps(scene.json()).size() == 3 && workspace.textLocation(scene, key, "en_us") != null, "redo restores the step and wording");
        var browser = new EditorContentBrowser();
        check(browser.rows(workspace, "en_us", "", false, "Scene", false, scene).matches() == 1, "scenes are discoverable without showing supporting files");
        check(workspace.validate().stream().noneMatch(issue -> issue.message().contains("literal")), "scene recipes and dialogue steps use locale references");
    }

    private static void stepRenamesUpdateRoutesAndEncounterHandles() throws IOException {
        var document = scene(workspace());
        String start = EditorSceneDraft.addStep(document, "villagerretaliation:start_encounter");
        String wait = EditorSceneDraft.addStep(document, "villagerretaliation:wait_encounter");
        EditorSceneDraft.route(document, "opening", "success", start);
        EditorSceneDraft.route(document, start, EditorSceneDraft.FAILURE, "done");
        EditorSceneDraft.renameStep(document, start, "ambush");
        var root = document.json();
        check(EditorSceneDraft.steps(root).stream().filter(step -> step.id().equals("opening")).findFirst().orElseThrow().routes().get("success").equals("ambush"), "renaming updates incoming transitions");
        var waitStep = EditorSceneDraft.steps(root).stream().filter(step -> step.id().equals(wait)).findFirst().orElseThrow();
        check(waitStep.value().getAsJsonObject("data").get("encounter_step").getAsString().equals("ambush"), "renaming updates encounter ownership handles");
        rejects(() -> EditorSceneDraft.renameStep(document, "ambush", "done"));
        document.undo();
        check(EditorSceneDraft.steps(document.json()).stream().anyMatch(step -> step.id().equals(start)), "rename is a single undo step");
        document.redo();
        String duplicate = EditorSceneDraft.duplicateStep(document, "ambush");
        check(!duplicate.equals("ambush") && EditorSceneDraft.steps(document.json()).stream().anyMatch(step -> step.id().equals(duplicate)), "duplicated steps receive distinct IDs");
    }

    private static void stepRemovalRedirectsIncomingLinksAtomically() throws IOException {
        var document = scene(workspace());
        rejects(() -> EditorSceneDraft.removeStep(document, "opening", ""));
        EditorSceneDraft.removeStep(document, "opening", "done");
        check(document.json().get("entry_step").getAsString().equals("done") && EditorSceneDraft.steps(document.json()).size() == 1, "removing the entry redirects it");
        rejects(() -> EditorSceneDraft.removeStep(document, "done", ""));
        document.undo();
        check(EditorSceneDraft.steps(document.json()).size() == 2 && document.json().get("entry_step").getAsString().equals("opening"), "undo restores the step and its links together");
    }

    private static void rehearsalRespectsRoutesFailuresAndLoopLimits() throws IOException {
        var document = scene(workspace()); var root = document.json();
        var trace = EditorSceneDraft.advance(root, EditorSceneDraft.start(root), "success");
        check(trace.current().equals("done"), "rehearsal follows next");
        check(EditorSceneDraft.advance(root, trace, "success").status().equals("Scene completes"), "terminal steps finish rehearsal");
        EditorSceneDraft.route(document, "opening", EditorSceneDraft.FAILURE, "done");
        check(EditorSceneDraft.advance(document.json(), EditorSceneDraft.start(document.json()), EditorSceneDraft.FAILURE).status().contains("blocks"), "failure policy overrides authored failure routes");
        document.edit("Follow failure", value -> value.addProperty("failure_policy", "run_failure_step"));
        check(EditorSceneDraft.advance(document.json(), EditorSceneDraft.start(document.json()), EditorSceneDraft.FAILURE).current().equals("done"), "run_failure_step uses the failure link");
        EditorSceneDraft.route(document, "opening", "success", "opening");
        root = document.json(); trace = EditorSceneDraft.start(root);
        for (int i = 0; i < 257; i++) trace = EditorSceneDraft.advance(root, trace, "success");
        check(trace.current().isEmpty() && trace.status().contains("256"), "cyclic rehearsal remains bounded");
    }

    private static void actorRenamesKeepBindingsAndUpdateOnlyReferences() throws IOException {
        var document = scene(workspace());
        String actor = EditorSceneDraft.addActor(document);
        String step = EditorSceneDraft.addStep(document, "villagerretaliation:move_actor");
        document.edit("Reference actor", root -> {
            var data = EditorSceneDraft.steps(root).stream().filter(value -> value.id().equals(step)).findFirst().orElseThrow().value().getAsJsonObject("data");
            data.addProperty("actor", actor); data.addProperty("target_actor", actor); data.addProperty("text", actor);
        });
        EditorSceneDraft.renameActor(document, actor, "guide");
        var data = EditorSceneDraft.steps(document.json()).stream().filter(value -> value.id().equals(step)).findFirst().orElseThrow().value().getAsJsonObject("data");
        check(data.get("actor").getAsString().equals("guide") && data.get("target_actor").getAsString().equals("guide"), "actor references follow rename");
        check(data.get("text").getAsString().equals(actor), "literal wording is preserved");
        rejects(() -> EditorSceneDraft.removeActor(document, "/actors/1"));
        rejects(() -> EditorSceneDraft.renameActor(document, "guide", "player"));
        check(document.json().getAsJsonArray("actors").get(1).getAsJsonObject().get("binding_source").getAsString().equals("unbound"), "renaming preserves binding rules");
    }

    private static void actorPickingWritesRuntimeBindings() throws IOException {
        var document = scene(workspace()); EditorSceneDraft.addActor(document);
        String uuid = "12345678-1234-1234-1234-123456789abc";
        EditorSceneDraft.bindEntity(document, "/actors/1", "villagerretaliation:villager", uuid);
        var actor = document.json().getAsJsonArray("actors").get(1).getAsJsonObject();
        check(actor.get("binding_source").getAsString().equals("uuid") && actor.get("binding").getAsString().equals(uuid), "entity picker writes UUID bindings");
        EditorSceneDraft.bindPosition(document, "/actors/1", "minecraft:the_nether", -12, 70, 9);
        actor = document.json().getAsJsonArray("actors").get(1).getAsJsonObject();
        check(actor.get("binding_source").getAsString().equals("marker") && actor.getAsJsonObject("filters").get("x").getAsString().equals("-12"), "position picker writes string filters for marker resolution");
        check(SceneParser.parse(ResourceLocation.parse(document.id()), document.json()).diagnostics().isEmpty(), "picked bindings parse at runtime");
        EditorSceneDraft.removeActor(document, "/actors/1");
        check(document.json().getAsJsonArray("actors").size() == 1, "unused actors can be removed");
    }

    private static void wavesKeepCompositionAndStableMemberIds() throws IOException {
        var document = encounter(workspace());
        EditorSceneDraft.makeWaves(document);
        EditorSceneDraft.addWave(document); EditorSceneDraft.addWave(document);
        EditorSceneDraft.addMember(document, "/waves/0/members");
        document.edit("Scale encounter", root -> { root.addProperty("max_party_size", 4); root.addProperty("extra_per_player", 2); });
        check(com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(document.json(),
                com.jvn.villagerretaliation.scene.schema.SceneSchema.encounterV1()).isEmpty(), "new waves and members match the runtime schema");
        var ids = new java.util.HashSet<String>();
        for (var wave : document.json().getAsJsonArray("waves")) for (var member : wave.getAsJsonObject().getAsJsonArray("members"))
            check(ids.add(member.getAsJsonObject().get("id").getAsString()), "member identities remain unique across waves");
        check(ids.size() == 4, "wave conversion preserves the existing roster and all new members");
        check(!document.json().has("members") && document.json().get("spawn_mode").getAsString().equals("raid_waves"), "conversion keeps exactly one composition mode");
        var repeated = encounter(workspace());
        repeated.edit("Repeat roster", root -> { root.addProperty("spawn_mode", "raid_waves"); root.addProperty("wave_count", 3); });
        rejects(() -> EditorSceneDraft.makeWaves(repeated));
        check(repeated.json().has("members"), "conversion cannot silently drop repeated waves");
    }

    private static void spawnPointsAndReorderingPreserveOtherSettings() throws IOException {
        var document = encounter(workspace());
        document.edit("Near player", root -> root.addProperty("spawn_mode", "near_player"));
        EditorSceneDraft.addSpawn(document, "minecraft:overworld", 10, 70, 20);
        EditorSceneDraft.addSpawn(document, "minecraft:the_nether", -1, 80, -2);
        check(document.json().get("spawn_mode").getAsString().equals("group"), "absolute spawn points switch out of near-player mode");
        check(document.json().getAsJsonArray("spawn_points").size() == 2, "spawn picks preserve existing points");
        var scene = scene(workspace());
        var before = scene.json().getAsJsonArray("steps").get(0).deepCopy();
        check(EditorSceneDraft.move(scene, "/steps/0", 1).equals("/steps/1"), "reordering returns the selected entry's new pointer");
        check(JsonPointers.get(scene.json(), "/steps/1").equals(before) && scene.json().get("entry_step").getAsString().equals("opening"), "reordering keeps stable IDs and flow");
        scene.undo(); check(JsonPointers.get(scene.json(), "/steps/0").equals(before), "reorder is undoable");
    }

    private static void rejects(Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected validation failure"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
