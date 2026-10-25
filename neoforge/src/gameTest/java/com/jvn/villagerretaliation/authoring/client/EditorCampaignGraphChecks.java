package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.preview.PreviewCoverage;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

final class EditorCampaignGraphChecks {
    static int run() throws IOException {
        linksRespectScopeAndReferencePointers();
        localizedTextAndDuplicateReferencesKeepTheirOwners();
        filtersUseWholeWorkspaceAndLiveCoverage();
        layoutHandlesCyclesAndDisconnectedFiles();
        cacheFollowsEditsUndoAndWorkspaceMembership();
        largeGraphUsesIndexedReferences();
        return 6;
    }

    private static AuthoringWorkspace workspace() throws IOException { return AuthoringWorkspace.create(Files.createTempDirectory("campaign-graph-check")); }

    private static AuthoringDocument add(AuthoringWorkspace workspace, DefinitionKind kind, String path, String json) {
        var document = new AuthoringDocument(kind, path, JsonParser.parseString(json).getAsJsonObject());
        workspace.add(document); return document;
    }

    private static EditorCampaignGraph.Snapshot graph(AuthoringWorkspace workspace) {
        return new EditorCampaignGraph().get(workspace, new EditorContentBrowser(), "en_us");
    }

    private static void linksRespectScopeAndReferencePointers() throws IOException {
        var workspace = workspace();
        var quest = add(workspace, DefinitionKind.QUEST, "data/test/world_campaigns/one/quests/story/quest.json",
                "{\"id\":\"test:quest\",\"scene\":\"test:scene\",\"world_campaign\":\"test:one\"}");
        var local = add(workspace, DefinitionKind.RESOURCE, "data/test/world_campaigns/one/scenes/scene.json",
                "{\"id\":\"test:scene\",\"schema\":\"villagerretaliation:scene/v1\",\"template\":\"test:encounter\"}");
        add(workspace, DefinitionKind.RESOURCE, "data/test/world_campaigns/two/scenes/scene.json",
                "{\"id\":\"test:scene\",\"schema\":\"villagerretaliation:scene/v1\"}");
        var encounter = add(workspace, DefinitionKind.RESOURCE, "data/test/world_campaigns/one/encounters/encounter.json",
                "{\"id\":\"test:encounter\",\"schema\":\"villagerretaliation:encounter/v1\"}");
        var campaign = workspace.createDocument(DefinitionKind.WORLD_CAMPAIGN, "test:one");
        var graph = graph(workspace);
        check(graph.links().size() == 3, "scoped IDs resolve once and manifests receive explicit references");
        check(graph.links().stream().anyMatch(link -> link.from().document() == quest && link.to().document() == local && link.sourcePointer().equals("/scene")), "scene links retain their owning field");
        check(graph.links().stream().anyMatch(link -> link.from().document() == local && link.to().document() == encounter), "encounter templates are linked");
        check(graph.links().stream().anyMatch(link -> link.to().document() == campaign), "campaign references are linked");
        for (var link : graph.links()) check(EditorReferences.definitions(workspace, link.from().document(), link.sourcePointer(),
                com.jvn.villagerretaliation.authoring.core.JsonPointers.get(link.from().document().json(), link.sourcePointer()).getAsString(), "en_us")
                .contains(new EditorReferences.Location(link.to().document(), link.targetPointer())), "overview and go-to-definition agree");
    }

    private static void localizedTextAndDuplicateReferencesKeepTheirOwners() throws IOException {
        var workspace = workspace();
        var quest = add(workspace, DefinitionKind.QUEST, "data/test/quests/story/first/quest.json",
                "{\"id\":\"test:first\",\"localization_prefix\":\"quest.first\",\"display\":{\"title\":{\"key\":\"#title\"}},\"nested\":{\"key\":\"#title\"},\"text\":\"test:first\"}");
        var locale = add(workspace, DefinitionKind.RESOURCE, "data/test/quests/story/first/locales/en_us.json",
                "{\"messages\":{\"quest.first.title\":\"A beginning\"}}");
        var graph = graph(workspace);
        check(graph.links().size() == 2 && graph.links().stream().allMatch(link -> link.to().document() == locale), "repeated translations keep both source pointers without treating prose as an ID");
        check(graph.links().getFirst().targetPointer().equals("/messages/quest.first.title"), "localized edges point to the actual payload");
        check(EditorReferences.usages(workspace, new EditorReferences.Location(locale, "/messages/quest.first.title"), "en_us").size() == 2, "indexed usage lookup preserves duplicate references");
        check(graph.nodes().stream().filter(node -> node.document() == quest).findFirst().orElseThrow().title().equals("A beginning"), "overview titles use the selected locale");
    }

    private static void filtersUseWholeWorkspaceAndLiveCoverage() throws IOException {
        var workspace = workspace();
        var first = workspace.createDocument(DefinitionKind.TREE, "test:first");
        var second = workspace.createDocument(DefinitionKind.TREE, "test:second");
        first.edit("Link dialogue", root -> root.addProperty("tree", "test:second"));
        var graph = graph(workspace); var coverage = new EditorCoverage();
        var firstNode = graph.nodes().stream().filter(node -> node.document() == first).findFirst().orElseThrow();
        check(EditorCampaignGraph.filter(graph, new EditorCampaignGraph.Filter("second", "", false, true, false, null), coverage).isEmpty(), "searching never makes a referenced file appear orphaned");
        check(EditorCampaignGraph.filter(graph, new EditorCampaignGraph.Filter("", "", false, false, true, null), coverage).size() == 2, "new dialogues have no recorded coverage");
        coverage.observe(first).accept(new PreviewCoverage(Set.of("/nodes/start"), Set.of()));
        check(EditorCampaignGraph.filter(graph, new EditorCampaignGraph.Filter("", "", false, false, true, null), coverage).size() == 1, "coverage updates filters without rebuilding content");
        check(EditorCampaignGraph.filter(graph, new EditorCampaignGraph.Filter("", "", false, false, false, firstNode), coverage).size() == 2, "nearby includes the selected file and its direct neighbours");
        first.edit("Change logic", root -> root.addProperty("tree", "test:missing"));
        check(!EditorCampaignGraph.recorded(firstNode, coverage), "logic changes invalidate recorded coverage");
        check(second != first, "separate files retain separate coverage");
    }

    private static void layoutHandlesCyclesAndDisconnectedFiles() throws IOException {
        var workspace = workspace();
        var a = workspace.createDocument(DefinitionKind.TREE, "test:a");
        var b = workspace.createDocument(DefinitionKind.TREE, "test:b");
        workspace.createDocument(DefinitionKind.TREE, "test:isolated");
        a.edit("Connect", root -> root.addProperty("tree", "test:b"));
        b.edit("Connect", root -> root.addProperty("tree", "test:a"));
        var graph = graph(workspace);
        var positions = EditorCampaignGraph.arrange(graph, graph.nodes());
        check(positions.size() == graph.nodes().size() && Set.copyOf(positions.values()).size() == positions.size(), "cycles and disconnected files receive non-overlapping positions");
        check(positions.equals(EditorCampaignGraph.arrange(graph, graph.nodes())), "layout is deterministic");
        check(EditorCampaignGraph.arrange(graph, List.of()).isEmpty(), "empty filters have an empty layout");
    }

    private static void cacheFollowsEditsUndoAndWorkspaceMembership() throws IOException {
        var workspace = workspace(); var browser = new EditorContentBrowser(); var model = new EditorCampaignGraph();
        var a = workspace.createDocument(DefinitionKind.TREE, "test:a");
        var b = workspace.createDocument(DefinitionKind.TREE, "test:b");
        var first = model.get(workspace, browser, "en_us");
        check(first == model.get(workspace, browser, "en_us"), "idle frames reuse graph references");
        a.edit("Connect", root -> root.addProperty("tree", "test:b"));
        check(model.get(workspace, browser, "en_us").links().size() == 1, "edits refresh graph references");
        a.undo();
        check(model.get(workspace, browser, "en_us").links().isEmpty(), "undo refreshes the graph");
        workspace.remove(b);
        check(model.get(workspace, browser, "en_us").nodes().size() == 1, "removed files leave the graph");
        check(model.get(workspace(), browser, "en_us").nodes().isEmpty(), "workspace switching drops old graph state");
    }

    private static void largeGraphUsesIndexedReferences() throws IOException {
        var workspace = workspace();
        for (int i = 0; i < 1200; i++) add(workspace, DefinitionKind.TREE, "data/test/dialogue_trees/en_us/node_" + i + ".json",
                "{\"id\":\"test:node_" + i + "\",\"tree\":\"test:node_" + ((i + 1) % 1200) + "\"}");
        var graph = graph(workspace);
        check(graph.links().size() == 1200 && EditorCampaignGraph.arrange(graph, graph.nodes()).size() == 1200, "large cyclic packs retain every node and link");
        check(EditorCampaignGraph.filter(graph, new EditorCampaignGraph.Filter("", "scope/default", false, true, false, null), new EditorCoverage()).isEmpty(), "scope and incoming-reference filters work on large graphs");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
