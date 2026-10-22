package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorGraphSizingChecks {
    private EditorGraphSizingChecks() { }

    static int run() throws IOException {
        draggingUsesGraphCoordinatesAndMovesPortsWithFields();
        sizesSurviveHistoryMovementAndWorkspaceSaves();
        resizedBoundsDriveSelectionAndArrangement();
        resizingClampsToUsableFiniteDimensions();
        sizesFollowRenamesCopiesAndDeletion();
        return 5;
    }

    private static AuthoringDocument document() {
        return new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/sizing.json", JsonParser.parseString("""
                {"id":"test:sizing","nodes":{"start":{"lines":["Hello"],"responses":[{"id":"go","label":"Continue","next":"finish"}]},
                "finish":{"lines":["Goodbye"]}},"entries":[{"id":"default","start":"start"}]}
                """).getAsJsonObject());
    }

    private static EditorGraph.Card card(AuthoringDocument document, String id) {
        return EditorGraph.cards(document, value -> value == null ? "" : value.toString()).stream()
                .filter(card -> card.node().id().equals(id)).findFirst().orElseThrow();
    }

    private static void draggingUsesGraphCoordinatesAndMovesPortsWithFields() {
        var document = document();
        var original = card(document, "start");
        var drag = new EditorGraphSizing.Drag(original, 100, 200, .5f);
        var resized = drag.preview(original, 180, 260);
        check(resized.width() == original.width() + 160 && resized.height() == original.height() + 120, "drag distances account for graph zoom");
        check(resized.fieldGrowth() == 60, "extra height grows each multiline field equally");
        check(resized.blockY(resized.blocks().size()) + 34 == resized.height(), "the footer follows the resized body");
        for (int i = 0; i < resized.blocks().size(); i++)
            check(resized.outputY(i) == resized.blockY(i) + resized.blockHeight(i) / 2, "output ports follow their owning rows");
        var other = card(document, "finish");
        check(drag.preview(other, 180, 260) == other, "resizing leaves other cards untouched");
        check(!document.metadata().has("node_positions"), "drag preview does not create history entries");
    }

    private static void sizesSurviveHistoryMovementAndWorkspaceSaves() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("editor-node-sizing"));
        var document = document(); workspace.add(document);
        var original = document.json();
        var positions = Map.of("start", new float[] {20, 30}, "finish", new float[] {600, 30});
        var resized = card(document, "start").resized(560, 520);
        EditorGraphSizing.save(document, resized, positions);
        check(document.undoLabel().equals("Resize node") && document.undoLabels().size() == 1, "a resize creates one undo entry");
        document.undo(); check(card(document, "start").width() == EditorGraph.WIDTH, "undo restores the automatic node size");
        document.redo(); check(card(document, "start").height() == resized.height(), "redo restores the resized height");
        document.editMetadata("Move node", metadata -> EditorGraphSizing.positions(metadata, Map.of("start", new float[] {80, 90}, "finish", new float[] {700, 30})));
        check(card(document, "start").width() == 560, "moving a node keeps its size");
        workspace.save();
        var loaded = AuthoringWorkspace.open(workspace.directory()).documents().getFirst();
        check(card(loaded, "start").width() == 560 && card(loaded, "start").height() == resized.height(), "sizes survive workspace reload");
        check(loaded.json().equals(original), "resizing preserves the runtime definition");
        var export = Files.createTempDirectory("editor-node-sizing-export").resolve("pack");
        workspace.exportPack(export);
        check(JsonParser.parseString(Files.readString(export.resolve(document.resourcePath()))).getAsJsonObject().equals(original), "exports contain only runtime content");
        var point = loaded.metadata().getAsJsonObject("node_positions").getAsJsonObject("start");
        EditorGraphSizing.reset(loaded, Set.of("start"));
        check(card(loaded, "start").width() == EditorGraph.WIDTH && card(loaded, "start").requestedHeight() == 0, "reset restores the automatic size");
        check(loaded.metadata().getAsJsonObject("node_positions").getAsJsonObject("start").get("x").equals(point.get("x")), "reset keeps node positions");
        loaded.undo(); check(card(loaded, "start").width() == 560, "size reset is undoable");
    }

    private static void resizedBoundsDriveSelectionAndArrangement() {
        var document = document();
        var wide = card(document, "start").resized(780, 520);
        var other = card(document, "finish");
        var cards = List.of(wide, other);
        var arranged = EditorGraph.arrange(cards);
        check(arranged.get("finish")[0] >= arranged.get("start")[0] + wide.width() + 90, "arranged columns leave room for wide nodes");
        var selected = EditorGraphSelection.box(cards, Map.of("start", new float[] {0, 0}, "finish", new float[] {1000, 0}), 700, 450, 710, 460);
        check(selected.equals(Set.of("start")), "box selection includes the expanded part of a node");
        var spaced = EditorGraphSelection.layout(cards, Map.of("start", new float[] {0, 0}, "finish", new float[] {900, 0}), Set.of("start", "finish"), "Space horizontally");
        check(spaced.get("finish")[0] >= spaced.get("start")[0] + wide.width(), "spacing uses each node's actual width");
    }

    private static void resizingClampsToUsableFiniteDimensions() {
        var document = document();
        var original = card(document, "start");
        var small = original.resized(1, 1);
        check(small.width() == 240 && small.height() == small.minimumHeight(), "small sizes keep controls usable");
        check(76 + small.fieldGrowth() >= 36, "multiline editors keep a usable minimum height");
        var invalid = original.resized(Float.POSITIVE_INFINITY, Float.NaN);
        check(invalid.width() == EditorGraph.WIDTH && invalid.height() == original.height(), "nonfinite dimensions fall back to automatic sizes");
        var huge = original.resized(Float.MAX_VALUE, Float.MAX_VALUE);
        check(huge.width() == 2000 && huge.height() == 10000, "oversized dimensions stay bounded");
        var metadata = JsonParser.parseString("{\"node_positions\":{\"start\":{\"width\":\"invalid\",\"height\":-4}}}").getAsJsonObject();
        var restored = EditorGraphSizing.restore(original, metadata);
        check(restored.width() == original.width() && restored.height() == original.height(), "malformed saved dimensions use defaults");
    }

    private static void sizesFollowRenamesCopiesAndDeletion() {
        var document = document();
        EditorGraphSizing.save(document, card(document, "start").resized(620, 540), EditorGraph.arrange(EditorGraph.cards(document, value -> "")));
        EditorContentTools.renameNode(document, "start", "greeting");
        check(card(document, "greeting").width() == 620, "renaming keeps node dimensions");
        var clipboard = new EditorGraphClipboard();
        clipboard.copy(document, Set.of("greeting"));
        String copied = clipboard.paste(document).getFirst();
        check(card(document, copied).width() == 620 && card(document, copied).height() == 540, "copy and paste keep node dimensions");
        var snippet = new EditorGraphClipboard();
        snippet.importSnippet(clipboard.exportSnippet());
        String fromSnippet = snippet.paste(document).getFirst();
        check(card(document, fromSnippet).width() == 620, "saved snippets retain node dimensions");
        EditorGraphActions.delete(document, Set.of(copied), "greeting");
        check(!document.metadata().getAsJsonObject("node_positions").has(copied), "deletion removes the node's geometry");
        document.undo(); check(card(document, copied).width() == 620, "undo deletion restores its size");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
