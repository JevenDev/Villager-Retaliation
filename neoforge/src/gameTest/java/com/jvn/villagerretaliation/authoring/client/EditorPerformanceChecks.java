package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.preview.PreviewCoverage;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Set;

final class EditorPerformanceChecks {
    private EditorPerformanceChecks() { }

    static int run() throws IOException {
        revisionsAndDirtyStateFollowHistory();
        snapshotsStayIsolatedAndRefreshAfterEdits();
        graphModelsTrackWordingDependenciesAndLanguage();
        cachedPlacementDoesNotRetainDraggedCoordinates();
        highlightsAndCoverageTrackLogicChanges();
        fieldCandidatesPreserveValidationAndIsolation();
        schemaMenusPreserveDefaultsAndIsolation();
        benchmarkSchemaMenus();
        benchmarkGraphPreparation();
        return 7;
    }

    private static AuthoringWorkspace workspace() throws IOException {
        return AuthoringWorkspace.create(Files.createTempDirectory("vr-editor-performance"));
    }

    private static void revisionsAndDirtyStateFollowHistory() throws IOException {
        var workspace = workspace();
        var document = workspace.createDocument(DefinitionKind.TREE, "test:revisions");
        check(document.dirty(), "new documents are unsaved");
        workspace.save();
        long saved = document.revision();
        for (int i = 0; i < 100; i++) check(!document.dirty(), "repeated saved-state reads remain clean");
        document.edit("No change", root -> { });
        document.editMetadata("No metadata change", root -> { });
        check(document.revision() == saved && !document.dirty(), "no-op edits keep the same revision");
        document.edit("Change title", root -> root.addProperty("title", "Changed"));
        long edited = document.revision();
        check(edited > saved && document.dirty(), "edits invalidate saved-state reads");
        document.undo();
        check(document.revision() > edited && !document.dirty(), "undo advances the revision and recognizes saved content");
        document.redo();
        check(document.dirty(), "redo restores unsaved content");
        workspace.save();
        document.editMetadata("Bookmark", root -> root.addProperty("bookmark", true));
        check(document.dirty(), "editor metadata participates in dirty state");
        document.undo(); check(!document.dirty(), "metadata undo restores clean state");
        long beforeRename = document.revision();
        workspace.rename(document, "test:renamed");
        check(document.revision() > beforeRename && document.dirty(), "renames invalidate revision and dirty state");
        document.undo(); check(!document.dirty(), "rename undo restores saved identity");
    }

    private static void snapshotsStayIsolatedAndRefreshAfterEdits() {
        var document = largeDocument(3);
        var cache = new EditorDocumentCache();
        var first = cache.json(document);
        check(first == cache.json(document) && cache.nodes(document) == cache.nodes(document), "unchanged render data is reused");
        document.json().addProperty("external", true);
        document.value("/nodes/node_0").getAsJsonObject().addProperty("external", true);
        check(!cache.json(document).has("external") && !document.value("/nodes/node_0").getAsJsonObject().has("external"), "public JSON and subtree reads remain defensive");
        document.edit("Change line", root -> root.getAsJsonObject("nodes").getAsJsonObject("node_0").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Changed")));
        var changed = cache.json(document);
        check(first != changed && !first.equals(changed), "render snapshots update after content edits");
        document.undo(); check(cache.json(document).equals(first), "undo cannot leave stale render data");
        document.redo(); check(cache.json(document).equals(changed), "redo cannot leave stale render data");
        document.editMetadata("Move", root -> root.addProperty("x", 42));
        check(cache.metadata(document).get("x").getAsInt() == 42, "metadata caches track revisions");
        var borrowed = new JsonObject[2];
        document.edit("Capture callback", root -> { borrowed[0] = root; root.addProperty("title", "Owned"); }, metadata -> { borrowed[1] = metadata; metadata.addProperty("title", "Owned"); });
        var owned = cache.json(document);
        borrowed[0].addProperty("title", "Escaped"); borrowed[1].addProperty("title", "Escaped");
        check(cache.json(document) == owned && document.value("/title").getAsString().equals("Owned") && document.metadata().get("title").getAsString().equals("Owned"), "edit callbacks cannot mutate cached state after returning");
    }

    private static void graphModelsTrackWordingDependenciesAndLanguage() throws IOException {
        var workspace = workspace();
        var document = largeDocument(2); workspace.add(document);
        document.edit("Localize", root -> root.getAsJsonObject("nodes").getAsJsonObject("node_0").getAsJsonArray("lines").set(0, JsonParser.parseString("{\"key\":\"greeting\"}")));
        var locale = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/quests/_shared/locales/en_us.json", JsonParser.parseString("{\"greeting\":\"Hello\"}").getAsJsonObject());
        workspace.add(locale);
        var model = new EditorGraphModel();
        var first = model.get(workspace, document, "en_us");
        check(first == model.get(workspace, document, "en_us"), "idle graph frames reuse their model");
        check(first.cards().getFirst().blocks().stream().anyMatch(block -> "Hello".equals(block.styledText())), "cached graph uses resolved wording");
        locale.edit("Translate", root -> root.addProperty("greeting", "Updated"));
        var updated = model.get(workspace, document, "en_us");
        check(first != updated && updated.cards().getFirst().blocks().stream().anyMatch(block -> "Updated".equals(block.styledText())), "wording edits invalidate graphs without editing the owner");
        locale.undo();
        check(model.get(workspace, document, "en_us").cards().equals(first.cards()), "localized undo updates graph text");
        var french = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/quests/_shared/locales/fr_fr.json", JsonParser.parseString("{\"greeting\":\"Bonjour\"}").getAsJsonObject());
        workspace.add(french);
        var translated = model.get(workspace, document, "fr_fr");
        check(translated.cards().getFirst().blocks().stream().anyMatch(block -> "Bonjour".equals(block.styledText())), "language changes and added locale files invalidate graphs");
        workspace.remove(french);
        check(model.get(workspace, document, "fr_fr") != translated, "removed wording files invalidate graphs");
        check(model.get(workspace(), document, "en_us") != first, "switching workspaces does not reuse another workspace's graph");
    }

    private static void cachedPlacementDoesNotRetainDraggedCoordinates() throws IOException {
        var workspace = workspace(); var document = largeDocument(4); workspace.add(document);
        var model = new EditorGraphModel().get(workspace, document, "en_us");
        var first = new HashMap<String, float[]>(); model.placeMissing(first);
        float original = first.get("node_0")[0];
        first.get("node_0")[0] += 500;
        model.placeMissing(first);
        check(first.get("node_0")[0] == original + 500, "cached layout never overwrites user positions");
        var second = new HashMap<String, float[]>(); model.placeMissing(second);
        check(second.get("node_0")[0] == original, "dragging does not mutate cached default positions");
    }

    private static void highlightsAndCoverageTrackLogicChanges() throws IOException {
        var workspace = workspace(); var document = largeDocument(4); workspace.add(document);
        var models = new EditorGraphModel(); var model = models.get(workspace, document, "en_us");
        check(EditorGraphOrganization.branch(document, Set.of("node_1"), false).equals(Set.of("node_1", "node_2", "node_3")), "graph models retain downstream reachability");
        var coverage = new EditorCoverage(); var observer = coverage.observe(document);
        observer.accept(new PreviewCoverage(Set.of("/nodes/node_0"), Set.of()));
        var before = coverage.snapshot(document);
        check(before.nodes() == coverage.snapshot(document).nodes(), "idle frames reuse coverage logic");
        document.edit("Wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("node_0").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("New wording")));
        check(coverage.snapshot(document).visited("/nodes/node_0"), "presentation edits retain coverage");
        document.edit("Reroute", root -> root.getAsJsonObject("nodes").getAsJsonObject("node_1").addProperty("next", "node_3"));
        check(!coverage.snapshot(document).visited("/nodes/node_0"), "logic edits invalidate cached coverage");
        observer.accept(new PreviewCoverage(Set.of("/nodes/node_0"), Set.of()));
        check(!coverage.snapshot(document).visited("/nodes/node_0"), "obsolete preview callbacks cannot repopulate coverage");
        check(models.get(workspace, document, "en_us") != model && EditorGraphOrganization.branch(document, Set.of("node_1"), false).equals(Set.of("node_1", "node_3")), "graph changes invalidate branch reachability");
    }

    private static void fieldCandidatesPreserveValidationAndIsolation() {
        var document = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/fields.json", JsonParser.parseString("{\"count\":2,\"nested\":{\"value\":1}}").getAsJsonObject());
        var edits = new EditorFieldEdits(); edits.update("/count", document.value("/count"), "3");
        check(edits.candidate(document, "/count").getAsInt() == 3 && document.value("/count").getAsInt() == 2, "candidate reads validate without modifying the document");
        edits.candidate(document, "/nested").getAsJsonObject().addProperty("value", 9);
        check(document.value("/nested/value").getAsInt() == 1, "unchanged subtree candidates remain defensive");
        document.edit("External edit", root -> root.addProperty("count", 4));
        check(!edits.issue(document, "/count").isEmpty() && edits.issues(document).containsKey("/count"), "targeted reads still reject stale field edits");
        edits.update("/count", document.value("/count"), "\"wrong type\"");
        check(edits.issue(document, "/count").contains("Expected a number"), "targeted reads retain type validation");
    }

    private static void schemaMenusPreserveDefaultsAndIsolation() {
        var root = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export();
        var schema = new EditorSchema(root);
        var rule = JsonParser.parseString("{\"$ref\":\"#/$defs/objective\"}").getAsJsonObject();
        var options = schema.options(rule);
        var types = root.getAsJsonObject("$defs").getAsJsonObject("objective").getAsJsonObject("properties").getAsJsonObject("type").getAsJsonArray("enum");
        check(options.keySet().stream().toList().equals(types.asList().stream().map(value -> value.getAsString().replace('_', ' ')).toList()),
                "objective menus retain every type and alias in schema order");
        for (String type : java.util.List.of("block_break", "break_block")) {
            var factory = options.get(type.replace('_', ' '));
            var selected = factory.get().getAsJsonObject();
            check(selected.get("type").getAsString().equals(type) && selected.has("id") && schema.effective(root.getAsJsonObject("$defs").getAsJsonObject("objective"), selected).getAsJsonObject("properties").has("block"),
                    "selecting a block objective retains its required defaults and subtype fields");
            var expected = selected.deepCopy();
            selected.addProperty("block", "minecraft:stone");
            check(factory.get().equals(expected), "repeated selections create independent objective defaults");
        }
        var condition = schema.options(root.getAsJsonObject("$defs").getAsJsonObject("condition"))
                .get("Player has entered dimensions").get().getAsJsonObject();
        check(condition.equals(EditorProgressConditions.seed("player_dimension_visited")), "condition menus retain complete progress presets");
        condition.getAsJsonArray("dimensions").add("test:changed");
        check(!EditorProgressConditions.seed("player_dimension_visited").equals(condition), "nested condition defaults remain isolated");
        var camera = schema.options(com.jvn.villagerretaliation.dialogue.DialogueCamera.schema());
        check(camera.size() == 3 && camera.values().stream().allMatch(factory -> factory.get().isJsonObject()), "structured alternatives remain selectable");
        var extension = schema.options(new JsonObject());
        check(extension.size() == 5 && extension.get("Object").get() != extension.get("Object").get()
                && extension.get("List").get() != extension.get("List").get(), "extension menus create independent objects and lists");
    }

    private static void benchmarkSchemaMenus() {
        var schema = new EditorSchema(com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export());
        var rule = JsonParser.parseString("{\"$ref\":\"#/$defs/objective\"}").getAsJsonObject();
        for (int i = 0; i < 10; i++) { schema.choices(rule); schema.options(rule); }
        long start = System.nanoTime(); int entries = 0;
        for (int i = 0; i < 30; i++) entries += schema.choices(rule).size();
        double eager = (System.nanoTime() - start) / 30_000_000.0;
        start = System.nanoTime();
        for (int i = 0; i < 300; i++) entries += schema.options(rule).size();
        double deferred = (System.nanoTime() - start) / 300_000_000.0;
        check(entries == schema.options(rule).size() * 330, "benchmark enumerates the complete objective menu");
        System.out.printf(java.util.Locale.ROOT, "Editor objective menu (%d entries): eager %.3f ms, deferred %.6f ms per frame%n", schema.options(rule).size(), eager, deferred);
    }

    private static AuthoringDocument largeDocument(int count) {
        var root = new JsonObject(); root.addProperty("id", "test:performance");
        var entries = new JsonArray(); var entry = new JsonObject(); entry.addProperty("id", "start"); entry.addProperty("start", "node_0"); entries.add(entry); root.add("entries", entries);
        var nodes = new JsonObject();
        for (int i = 0; i < count; i++) {
            var node = new JsonObject(); var lines = new JsonArray(); lines.add("Line " + i); node.add("lines", lines);
            if (i + 1 < count) node.addProperty("next", "node_" + (i + 1)); else node.addProperty("end", true);
            nodes.add("node_" + i, node);
        }
        root.add("nodes", nodes);
        return new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/performance.json", root);
    }

    private static void benchmarkGraphPreparation() throws IOException {
        var workspace = workspace(); var document = largeDocument(300); workspace.add(document);
        var cache = new EditorGraphModel();
        for (int i = 0; i < 10; i++) { EditorGraph.cards(document, value -> workspace.resolveText(document, value, "en_us")); cache.get(workspace, document, "en_us"); }
        long start = System.nanoTime(); int blocks = 0;
        for (int i = 0; i < 30; i++) blocks += EditorGraph.cards(document, value -> workspace.resolveText(document, value, "en_us")).size();
        double uncached = (System.nanoTime() - start) / 30_000_000.0;
        start = System.nanoTime();
        for (int i = 0; i < 300; i++) blocks += cache.get(workspace, document, "en_us").cards().size();
        double cached = (System.nanoTime() - start) / 300_000_000.0;
        check(blocks == 99_000, "benchmark consumes the complete graph");
        System.out.printf(java.util.Locale.ROOT, "Editor graph preparation (300 nodes): uncached %.3f ms, cached %.6f ms per read%n", uncached, cached);
    }

    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
