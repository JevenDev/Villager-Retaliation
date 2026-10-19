package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.nio.file.Files;
import java.util.List;

final class EditorGraphInlineChecks {
    private EditorGraphInlineChecks() { }

    static int run() throws java.io.IOException {
        textAndBehaviorFieldsKeepTheirOwners();
        localizedVariantsEditTheirLocaleAndUndo();
        fallbackAndMissingTranslationsStayReadOnly();
        questContentAndCardGeometryStayConnected();
        return 4;
    }

    private static void textAndBehaviorFieldsKeepTheirOwners() {
        var document = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/inline.json", json("""
                {"nodes":{"start":{"lines":["First",{"text":"Second","weight":2}],
                  "conditions":[{"type":"reputation","min":3},{"type":"advancement","advancements":["test:one"],"all":true}],
                  "actions":[{"type":"experience","amount":4}],"responses":[{"id":"yes","label":"Continue","next":"finish"}]},
                  "finish":{"text":"Goodbye"}},"entries":[{"start":"start"}]}
                """));
        var original = document.json();
        var cards = EditorGraph.cards(document, value -> value == null ? "" : value.toString());
        var fields = cards.stream().flatMap(card -> card.blocks().stream()).flatMap(block -> block.fields().stream()).toList();
        check(fields.stream().map(EditorGraphInline.Field::pointer).toList().containsAll(List.of(
                "/nodes/start/lines/0", "/nodes/start/lines/1/text", "/nodes/start/responses/0/label",
                "/nodes/start/conditions/0/min", "/nodes/start/conditions/1/all", "/nodes/start/actions/0/amount", "/nodes/finish/text")),
                "all wording variants, response labels and common values are editable in their owning node");
        check(fields.stream().noneMatch(field -> field.pointer().endsWith("/type") || field.pointer().endsWith("/id") || field.pointer().endsWith("/next")),
                "quick fields preserve structural identifiers and connections");
        var edits = new EditorFieldEdits();
        var number = fields.stream().filter(field -> field.pointer().endsWith("/min")).findFirst().orElseThrow();
        edits.update(number.pointer(), number.value(), "invalid");
        check(!edits.issues(document).isEmpty(), "invalid inline numbers stay pending with a field error");
        edits.update(number.pointer(), number.value(), "7");
        var wording = fields.stream().filter(field -> field.pointer().endsWith("/1/text")).findFirst().orElseThrow();
        edits.update(wording.pointer(), wording.value(), "Changed\nSecond line");
        edits.apply(document);
        check(document.value(number.pointer()).getAsInt() == 7 && document.value(wording.pointer()).getAsString().contains("\n"), "inline edits preserve scalar types and newlines");
        check(document.value("/nodes/start/lines/1/weight").getAsInt() == 2, "editing wording preserves variant metadata");
        document.undo(); check(document.json().equals(original), "inline edits undo together through the existing property history");
    }

    private static void localizedVariantsEditTheirLocaleAndUndo() throws java.io.IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-inline-locale"));
        var document = new AuthoringDocument(DefinitionKind.CONVERSATION, "data/test/quests/_shared/conversations/inline.json", json("""
                {"id":"test:inline","nodes":[{"id":"start","exchanges":[{"id":"greeting","text":{"key":"test.hello"}}],
                  "responses":[{"id":"yes","label":{"key":"test.yes"},"next":"finish"}]},{"id":"finish","outcome":"done"}]}
                """));
        var locale = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/quests/_shared/locales/en_us.json", json("""
                {"messages":{"test.hello":{"variants":[{"text":"Welcome","weight":2},{"text":"Hello","weight":1}]},"test.yes":"Go on"}}
                """));
        workspace.add(document); workspace.add(locale);
        var original = document.json();
        var fields = EditorGraph.cards(workspace, document, "en_us").getFirst().blocks().stream().flatMap(block -> block.fields().stream()).toList();
        check(fields.size() == 3 && fields.stream().allMatch(field -> field.document() == locale && field.editable()), "localized lines and choices resolve to editable locale scalars");
        check(fields.getFirst().bufferId(document).equals(locale.resourcePath() + "/messages/test.hello/variants/0/text"), "graph and Details share the same localized buffer");
        var edits = new EditorFieldEdits();
        var field = fields.getFirst();
        edits.update(field.pointer(), field.value(), "Revised welcome"); edits.apply(locale);
        check(document.json().equals(original) && locale.value(field.pointer()).getAsString().equals("Revised welcome"), "inline editing preserves the localization reference");
        locale.undo(); check(locale.value(field.pointer()).getAsString().equals("Welcome"), "locale wording has normal undo");
        locale.redo(); check(locale.value(field.pointer()).getAsString().equals("Revised welcome"), "locale wording has normal redo");
    }

    private static void fallbackAndMissingTranslationsStayReadOnly() throws java.io.IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-inline-fallback"));
        var document = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/inline.json", json("""
                {"nodes":{"start":{"lines":[{"key":"test.hello"},{"key":"test.missing"}]}}}
                """));
        var english = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/quests/_shared/locales/en_us.json", json("{\"test.hello\":\"Hello\"}"));
        workspace.add(document); workspace.add(english);
        var fields = EditorGraph.cards(workspace, document, "fr_fr").getFirst().blocks().getFirst().fields();
        check(fields.size() == 2 && fields.stream().noneMatch(EditorGraphInline.Field::editable), "fallback wording and missing references cannot overwrite English");
        check(fields.getFirst().label().contains("English fallback") && fields.getFirst().value().getAsString().equals("Hello"), "fallback content remains visible");
        var french = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/quests/_shared/locales/fr_fr.json", json("{\"test.hello\":\"Bonjour\"}"));
        workspace.add(french);
        var translated = EditorGraph.cards(workspace, document, "fr_fr").getFirst().blocks().getFirst().fields().getFirst();
        check(translated.document() == french && translated.editable(), "an existing translation edits the selected language");
    }

    private static void questContentAndCardGeometryStayConnected() {
        var document = new AuthoringDocument(DefinitionKind.QUEST, "data/test/quests/line/inline/quest.json", json("""
                {"entry_stage":"first","stages":[{"id":"first","objectives":[{"id":"bread","type":"item_check","item":"minecraft:bread","count":16}],
                  "dialogue":{"offer":{"lines":["Bring bread."]}},"scenes":[{"id":"end","text":"Thank you."}],"transition":{"stage":"second"}},
                  {"id":"second","transition":{"complete":true}}]}
                """));
        var cards = EditorGraph.cards(document, value -> value == null ? "" : value.toString());
        var first = cards.getFirst();
        var paths = first.blocks().stream().flatMap(block -> block.fields().stream()).map(EditorGraphInline.Field::pointer).toList();
        check(paths.containsAll(List.of("/stages/0/objectives/0/item", "/stages/0/objectives/0/count", "/stages/0/dialogue/offer/lines/0", "/stages/0/scenes/0/text")), "quest objectives and spoken content are visible in the stage");
        for (int i = 0; i < first.blocks().size(); i++) {
            check(first.outputY(i) > first.blockY(i) && first.outputY(i) < first.blockY(i) + first.blocks().get(i).height(), "ports follow the expanded block");
            if (i > 0) check(first.blockY(i) == first.blockY(i - 1) + first.blocks().get(i - 1).height(), "expanded blocks remain adjacent without overlap");
        }
        check(first.height() == first.blockY(first.blocks().size()) + 34, "footer follows every inline control");
        check(EditorGraph.arrange(cards).get("second")[0] > 0, "expanded cards keep their stage connection");
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
