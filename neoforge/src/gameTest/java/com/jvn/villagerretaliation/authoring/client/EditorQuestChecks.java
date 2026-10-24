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
        advancedObjectivesCreateCompleteSetups();
        optionalObjectivesStayOutOfCompletionRequirements();
        conflictingFollowersRejectTheWholeEdit();
        questSettingsPreserveAuthoredValues();
        performanceTiersValidateReferencesAndPriority();
        discoveryRoutesValidateBeforeChangingTheDraft();
        return 9;
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

    private static void advancedObjectivesCreateCompleteSetups() {
        for (String type : List.of("location_visit", "structure_visit", "follower_arrival", "rescue", "criterion", "fact")) {
            var document = quest();
            var original = document.json();
            var setup = new EditorQuestObjectives.Setup(new EditorQuestObjectives.Location("minecraft:overworld", 0, 64, 0, 4),
                    "minecraft:pillager_outpost", "companion", "quest_destination", "villagerretaliation:captive_rescued", "done");
            var objective = EditorQuestObjectives.template(type, document.id(), setup);
            var target = type.equals("rescue") ? json("""
                    {"dimension":"minecraft:overworld","site":{"selection":"prefer_existing","structures":["minecraft:pillager_outpost"],
                     "templates":["villagerretaliation:quest/holding_camp"],"rescue":"escort_home","min_distance":256,"max_distance":2048}}
                    """) : null;
            String path = EditorQuestObjectives.append(document, "start", objective,
                    type.equals("follower_arrival") ? EditorQuestObjectives.follower("companion", "quest_companion") : null, target);
            check(JsonPointers.get(document.json(), path) != null, "advanced objective is created");
            var issues = com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(document.json(),
                    com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export());
            check(issues.isEmpty(), type + " setup follows the runtime schema: " + issues);
            if (type.equals("follower_arrival")) check(document.json().getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonArray("followers").size() == 1,
                    "escort setup includes its actor binding");
            if (type.equals("rescue")) check(document.json().getAsJsonObject("target").getAsJsonObject("site").get("rescue").getAsString().equals("escort_home"),
                    "rescue setup includes its site");
            document.undo();
            check(document.json().equals(original), "objective and related setup undo together");
        }
    }

    private static void optionalObjectivesStayOutOfCompletionRequirements() {
        var document = quest();
        EditorContentTools.appendObjective(document, "start", json("""
                {"type":"trade","count":1}
                """));
        EditorContentTools.appendObjective(document, "start", json("""
                {"type":"trade","count":2,"optional":true}
                """));
        var stage = document.json().getAsJsonArray("stages").get(0).getAsJsonObject();
        check(stage.getAsJsonArray("complete_when").size() == 1 && stage.getAsJsonArray("objectives").size() == 2,
                "optional objectives do not become required");
    }

    private static void conflictingFollowersRejectTheWholeEdit() {
        var document = quest();
        var objective = json("""
                {"type":"follower_arrival","match":{"actor":"companion","destination_tag":"home"}}
                """);
        EditorQuestObjectives.append(document, "start", objective, EditorQuestObjectives.follower("companion", "first"), null);
        var original = document.json();
        reject(() -> EditorQuestObjectives.append(document, "start", objective, EditorQuestObjectives.follower("companion", "other"), null));
        check(document.json().equals(original), "conflicting follower settings do not partially append an objective");
    }

    private static void questSettingsPreserveAuthoredValues() {
        var document = quest();
        document.edit("Existing settings", root -> root.add("availability", json("""
                {"repeatable":true,"max_completions":5,"expiration":{"after_ticks":40,"notify":false}}
                """)));
        var before = document.json();
        EditorQuestSettings.defaults(document, "availability", json("""
                {"repeatable":false,"max_completions":0,"prerequisites":[],"expiration":{"after_ticks":100,"consume":true}}
                """));
        var availability = document.json().getAsJsonObject("availability");
        check(availability.get("repeatable").getAsBoolean() && availability.get("max_completions").getAsInt() == 5, "setup shortcuts preserve authored limits");
        check(availability.getAsJsonObject("expiration").get("after_ticks").getAsInt() == 40
                && availability.getAsJsonObject("expiration").get("consume").getAsBoolean(), "nested settings only fill missing values");
        document.undo();
        check(document.json().equals(before), "settings shortcuts undo atomically");
    }

    private static void performanceTiersValidateReferencesAndPriority() {
        var document = quest();
        EditorQuestSettings.performanceTier(document, "swift", "time", "elapsed_ticks", "", "", "", 100, true, 50, 2);
        EditorQuestSettings.performanceTier(document, "steady", "time", "elapsed_ticks", "", "", "", 200, true, 20, 1);
        var performance = com.jvn.villagerretaliation.quest.QuestPerformance.read(document.json().get("performance"));
        check(performance.select(java.util.Map.of("time", 90.0)).id().equals("swift"), "tier insertion preserves first-match priority");
        check(performance.select(java.util.Map.of("time", 150.0)).id().equals("steady"), "later tiers match their own bounds");
        var before = document.json();
        reject(() -> EditorQuestSettings.performanceTier(document, "bad", "health", "final_health_percent", "missing", "", "", 90, false, 20, 1));
        reject(() -> EditorQuestSettings.performanceTier(document, "swift", "time", "elapsed_ticks", "", "", "", 100, true, 10, 0));
        reject(() -> EditorQuestSettings.performanceTier(document, "bad", "time", "deaths", "", "", "", 0, true, 10, 0));
        check(document.json().equals(before), "invalid tier references and duplicate IDs never change rewards");
    }

    private static void discoveryRoutesValidateBeforeChangingTheDraft() {
        var document = quest();
        EditorQuestSettings.discoveryRoute(document, "letter", "item_acquired", "offer", json("""
                {"item":"minecraft:paper"}
                """));
        var before = document.json();
        reject(() -> EditorQuestSettings.discoveryRoute(document, "letter", "item_used", "automatic", new JsonObject()));
        reject(() -> EditorQuestSettings.discoveryRoute(document, "place", "location", "offer", new JsonObject()));
        check(document.json().equals(before), "duplicate or incomplete discoveries leave the draft intact");
        document.undo();
        check(!document.json().has("discovery"), "discovery routes support undo");
    }

    static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
    static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid edit to fail"); }
        catch (IllegalArgumentException expected) { }
    }
}
