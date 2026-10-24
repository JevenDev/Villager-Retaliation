package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

final class EditorQuestChecks {
    private EditorQuestChecks() { }

    static int run() throws IOException {
        progressionPreservesChoicesAndSupportsUndo();
        conditionalRoutesAppearInTheGraph();
        invalidRoutesDoNotChangeDrafts();
        return 3;
    }

    static AuthoringDocument quest() {
        return new AuthoringDocument(DefinitionKind.QUEST, "data/test/quests/authored/flow/quest.json", json("""
                {"schema":"villagerretaliation:quest/v3","id":"test:flow","localization_prefix":"test.quest.flow","metadata":{"questline":"authored"},
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"start",
                 "stages":[{"id":"start","objectives":[]},{"id":"finish","objectives":[],"transition":{"complete":true}}]}
                """));
    }

    private static void progressionPreservesChoicesAndSupportsUndo() {
        var document = quest();
        String path = EditorQuestFlow.add(document, "start", EditorQuestFlow.Route.CHOICE, "stage", "finish", "Help", "", new JsonArray());
        var before = document.json();
        EditorQuestFlow.add(document, "start", EditorQuestFlow.Route.AUTOMATIC, "fail", "", "", "", new JsonArray());
        check(JsonPointers.get(document.json(), path).getAsJsonObject().get("label").getAsString().equals("Help"), "endings preserve authored choices");
        document.undo();
        check(document.json().equals(before), "progression edits undo atomically");
        check(DialogueGraph.edges(document).stream().anyMatch(edge -> edge.to().equals("finish")), "player branches have graph edges");
        check(com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(document.json(),
                com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export()).isEmpty(), "authored player branch follows the runtime schema");
    }

    private static void conditionalRoutesAppearInTheGraph() {
        var document = quest();
        var conditions = JsonParser.parseString("[{\"type\":\"reputation\",\"min\":5}]").getAsJsonArray();
        String path = EditorQuestFlow.add(document, "start", EditorQuestFlow.Route.CONDITIONAL, "stage", "finish", "", "progress", conditions);
        conditions.get(0).getAsJsonObject().addProperty("min", 99);
        check(JsonPointers.get(document.json(), path + "/conditions/0/min").getAsInt() == 5, "branch conditions are owned by the draft");
        check(DialogueGraph.edges(document).stream().anyMatch(edge -> edge.pointer().equals(path + "/transition/stage")), "conditional branch is a graph edge");
        check(EditorGraph.cards(document, Object::toString).stream().flatMap(card -> card.blocks().stream()).anyMatch(block -> block.pointer().equals(path) && block.output() != null),
                "conditional branch has a visible graph output");
    }

    private static void invalidRoutesDoNotChangeDrafts() {
        var document = quest();
        var before = document.json();
        reject(() -> EditorQuestFlow.add(document, "start", EditorQuestFlow.Route.AUTOMATIC, "stage", "missing", "", "", new JsonArray()));
        reject(() -> EditorQuestFlow.add(document, "start", EditorQuestFlow.Route.CHOICE, "complete", "", "", "", new JsonArray()));
        reject(() -> EditorQuestFlow.add(document, "start", EditorQuestFlow.Route.CONDITIONAL, "complete", "", "", "progress", new JsonArray()));
        check(document.json().equals(before), "invalid progression never partially changes content");
    }

    static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
    static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid edit to fail"); }
        catch (IllegalArgumentException expected) { }
    }
}
