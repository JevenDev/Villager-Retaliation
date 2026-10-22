package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.util.List;

final class EditorResourceViewChecks {
    private EditorResourceViewChecks() { }

    static int run() {
        scenesSeparateStepsActorsAndSettings();
        supportingFormatsHaveEditableSections();
        revisionsRefreshEntriesAndSearch();
        sceneFieldsUseTheRuntimeSchema();
        return 4;
    }

    private static AuthoringDocument document(String path, String json) {
        return new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/" + path, JsonParser.parseString(json).getAsJsonObject());
    }

    private static AuthoringDocument scene() {
        return document("quests/example/scenes/scene.json", """
                {"schema":"villagerretaliation:scene/v1","id":"test:scene","entry_step":"wait","ownership":"quest_instance",
                "actors":[{"alias":"player","type":"villagerretaliation:player","binding_source":"owner_player"}],
                "steps":[{"id":"wait","type":"villagerretaliation:wait_ticks","next":"done","data":{"ticks":20}},
                {"id":"done","type":"villagerretaliation:scene_complete"}],"timeout_ticks":100}
                """);
    }

    private static void scenesSeparateStepsActorsAndSettings() {
        var scene = scene(); var view = new EditorResourceView(); var cache = new EditorDocumentCache();
        var sections = view.sections(scene, cache.json(scene));
        check(EditorResourceView.title(scene, cache.json(scene)).equals("Scene"), "scene files have their own work area heading");
        check(sections.stream().map(EditorResourceView.Section::key).toList().equals(List.of("steps", "actors", "settings")), "scene flow opens before actors and file settings");
        var step = sections.getFirst().items().getFirst();
        check(step.pointer().equals("/steps/0") && step.title().equals("wait") && step.summary().contains("Next: done"), "step cards expose their actual identity and transition");
        check(sections.get(1).items().getFirst().title().equals("player"), "actor cards use their alias");
        check(sections == view.sections(scene, cache.json(scene)), "idle frames reuse the resource index");
    }

    private static void supportingFormatsHaveEditableSections() {
        var view = new EditorResourceView(); var cache = new EditorDocumentCache();
        var messages = document("dialogue/en_us/messages.json", """
                {"metadata":{"topic":"gifts"},"messages":[{"id":"gift","key":"gift_given","lines":["Thank you"]}]}
                """);
        check(view.sections(messages, cache.json(messages)).getFirst().key().equals("messages"), "keyed dialogue messages have a section");
        var locale = document("quests/example/locales/en_us.json", """
                {"messages":{"scene/a~b":{"text":"Words"},"second":"Other"}}
                """);
        check(view.sections(locale, cache.json(locale)).getFirst().items().getFirst().pointer().equals("/messages/scene~1a~0b"), "translation keys preserve JSON pointer escaping");
        var legacy = document("quests/example/locales/fr_fr.json", """
                {"schema":"locale/v1","scene/a":"Texte","metadata":{"locale":"fr_fr"}}
                """);
        var sections = view.sections(legacy, cache.json(legacy));
        check(sections.getFirst().label().equals("Translations") && sections.getFirst().items().size() == 1, "flat locales do not mix metadata into their translations");
        var words = document("dialogue_words/en_us/greetings.json", """
                {"values":["Hello",{"text":"Welcome","conditions":[]}]}
                """);
        check(view.sections(words, cache.json(words)).getFirst().items().size() == 2, "word pools support plain and conditional entries");
        var empty = document("other.json", "{}");
        check(view.sections(empty, cache.json(empty)).getFirst().key().equals("settings"), "unclassified and empty resources still have an editor");
    }

    private static void revisionsRefreshEntriesAndSearch() {
        var scene = scene(); var view = new EditorResourceView(); var cache = new EditorDocumentCache();
        var initial = view.sections(scene, cache.json(scene));
        var found = view.matches(initial.getFirst(), "wait ticks");
        check(found.size() == 1 && found == view.matches(initial.getFirst(), "wait ticks"), "search uses displayed step types and reuses unchanged results");
        scene.edit("Rename step", root -> root.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("id", "pause"));
        var changed = view.sections(scene, cache.json(scene));
        check(changed != initial && changed.getFirst().items().getFirst().title().equals("pause"), "content edits rebuild resource summaries");
        check(view.matches(changed.getFirst(), "pause").size() == 1, "search follows edited identities");
        scene.undo();
        check(view.sections(scene, cache.json(scene)).getFirst().items().getFirst().title().equals("wait"), "undo refreshes scene cards");
        scene.redo();
        check(view.sections(scene, cache.json(scene)).getFirst().items().getFirst().title().equals("pause"), "redo refreshes scene cards");
    }

    private static void sceneFieldsUseTheRuntimeSchema() {
        var scene = scene(); var schema = EditorSchema.forDocument(scene);
        check(!schema.scalarIssues(scene.json(), "/timeout_ticks", new com.google.gson.JsonPrimitive(-1)).isEmpty(), "scene fields retain numeric constraints");
        scene.edit("Add step conditions", root -> root.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("data")
                .add("conditions", JsonParser.parseString("""
                        [{"type":"player_dimension_visited","dimensions":["minecraft:overworld"]}]
                        """)));
        var conditionReference = new com.google.gson.JsonObject(); conditionReference.addProperty("$ref", "#/$defs/condition");
        check(schema.effective(conditionReference, scene.value("/steps/0/data/conditions/0")).has("properties"), "scene parameters keep the existing condition authoring definitions");
        var ownership = schema.at(scene.json(), "/ownership");
        check(ownership.has("enum") && ownership.getAsJsonArray("enum").contains(new com.google.gson.JsonPrimitive("quest_instance")), "scene settings expose supported values");
        var encounter = document("quests/_shared/encounters/test.json", """
                {"schema":"villagerretaliation:encounter/v1","id":"test:encounter","members":[{"entity":"minecraft:pillager","count":2}]}
                """);
        check(!EditorSchema.forDocument(encounter).scalarIssues(encounter.json(), "/members/0/count", new com.google.gson.JsonPrimitive(-1)).isEmpty(), "encounter composition uses its schema constraints");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
