package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class EditorGraphOrganization {
    private final ImString name = new ImString(256), comment = new ImString(2048);
    private String mode = "All nodes";
    private String selectedGroup = "";
    private AuthoringDocument owner;

    void focus(String value) { mode = value; }

    static Set<String> branch(AuthoringDocument document, Set<String> selection, boolean upstream) {
        Set<String> known = new LinkedHashSet<>();
        DialogueGraph.nodes(document).forEach(node -> known.add(node.id()));
        Set<String> result = new LinkedHashSet<>(selection); result.retainAll(known);
        var edges = DialogueGraph.edges(document);
        boolean changed;
        do {
            changed = false;
            for (var edge : edges) {
                String from = upstream ? edge.to() : edge.from(), to = upstream ? edge.from() : edge.to();
                if (result.contains(from) && known.contains(to)) changed |= result.add(to);
            }
        } while (changed);
        return Set.copyOf(result);
    }

    Set<String> highlighted(AuthoringDocument document, Set<String> selection) {
        return mode.equals("All nodes") || selection.isEmpty() ? DialogueGraph.nodes(document).stream().map(DialogueGraph.Node::id).collect(java.util.stream.Collectors.toSet())
                : branch(document, selection, mode.equals("Upstream"));
    }

    static void saveGroup(AuthoringDocument document, String previous, String name, String comment, Set<String> members) {
        String label = name.strip();
        if (label.isEmpty() || members.isEmpty()) throw new IllegalArgumentException("Enter a group name and select its nodes");
        var groups = EditorSchema.object(document.metadata().get("graph_groups"));
        if (!previous.equals(label) && groups.has(label)) throw new IllegalArgumentException("A group already uses that name");
        var known = DialogueGraph.nodes(document).stream().map(DialogueGraph.Node::id).toList();
        if (!known.containsAll(members)) throw new IllegalArgumentException("Select existing nodes");
        document.editMetadata("Save graph comment group", metadata -> {
            if (!metadata.has("graph_groups")) metadata.add("graph_groups", new JsonObject());
            var values = metadata.getAsJsonObject("graph_groups");
            if (!previous.isEmpty()) values.remove(previous);
            var group = new JsonObject(); group.addProperty("comment", comment);
            var ids = new JsonArray(); members.stream().sorted().forEach(ids::add); group.add("nodes", ids);
            values.add(label, group);
        });
    }

    void render(AuthoringDocument document, Set<String> selection, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Consumer<Set<String>> select) {
        if (owner != document) { owner = document; selectedGroup = ""; name.set(""); comment.set(""); }
        if (!EditorWidgets.section("Branch focus and comment groups")) return;
        if (ImGui.beginCombo(EditorWidgets.field("Highlight path"), mode)) {
            for (String value : List.of("All nodes", "Downstream", "Upstream"))
                if (ImGui.selectable(value, value.equals(mode))) mode = value;
            ImGui.endCombo();
        }
        ImGui.textWrapped("Upstream and downstream follow the current selection. Unrelated nodes are dimmed.");
        if (ImGui.button("Select highlighted branch")) select.accept(highlighted(document, selection));
        var groups = EditorSchema.object(document.metadata().get("graph_groups"));
        if (ImGui.beginCombo(EditorWidgets.field("Comment group"), selectedGroup.isEmpty() ? "New group" : selectedGroup)) {
            if (ImGui.selectable("New group")) { selectedGroup = ""; name.set(""); comment.set(""); }
            for (var entry : groups.entrySet()) if (ImGui.selectable(entry.getKey(), entry.getKey().equals(selectedGroup))) {
                selectedGroup = entry.getKey(); name.set(entry.getKey());
                comment.set(entry.getValue().getAsJsonObject().get("comment").getAsString());
            }
            ImGui.endCombo();
        }
        ImGui.inputText(EditorWidgets.field("Group name"), name);
        ImGui.inputTextMultiline(EditorWidgets.field("Group comment"), comment, -1, 70);
        if (ImGui.button("Save group from selection")) run.accept(() -> {
            saveGroup(document, selectedGroup, name.get(), comment.get(), selection); selectedGroup = name.get().strip();
        });
        if (groups.has(selectedGroup)) {
            if (ImGui.smallButton("Select group nodes")) {
                Set<String> ids = new LinkedHashSet<>();
                groups.getAsJsonObject(selectedGroup).getAsJsonArray("nodes").forEach(value -> ids.add(value.getAsString()));
                ids.retainAll(DialogueGraph.nodes(document).stream().map(DialogueGraph.Node::id).toList());
                select.accept(ids);
            }
            EditorWidgets.sameLineFor("Remove comment group");
            if (ImGui.smallButton("Remove comment group")) run.accept(() -> {
                document.editMetadata("Remove graph comment group", metadata -> metadata.getAsJsonObject("graph_groups").remove(selectedGroup));
                selectedGroup = "";
            });
        }
    }

    void draw(ImDrawList draw, AuthoringDocument document, List<EditorGraph.Card> cards, Map<String, float[]> positions, float originX, float originY, float zoom) {
        var groups = EditorSchema.object(document.metadata().get("graph_groups"));
        for (var entry : groups.entrySet()) {
            var group = entry.getValue().getAsJsonObject();
            Set<String> ids = new LinkedHashSet<>();
            group.getAsJsonArray("nodes").forEach(value -> ids.add(value.getAsString()));
            float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
            for (var card : cards) if (ids.contains(card.node().id()) && positions.containsKey(card.node().id())) {
                var point = positions.get(card.node().id());
                left = Math.min(left, point[0]); top = Math.min(top, point[1]);
                right = Math.max(right, point[0] + EditorGraph.WIDTH); bottom = Math.max(bottom, point[1] + card.height());
            }
            if (left == Float.MAX_VALUE) continue;
            float x = originX + (left - 16) * zoom, y = originY + (top - 46) * zoom;
            float endX = originX + (right + 16) * zoom, endY = originY + (bottom + 16) * zoom;
            draw.addRectFilled(x, y, endX, endY, 0x224f6650, 4);
            draw.addRect(x, y, endX, endY, 0xff6b806b, 4);
            draw.pushClipRect(x + 6, y, endX - 6, originY + (top - 8) * zoom, true);
            draw.addText(ImGui.getFont(), Math.max(6, (int) (ImGui.getFontSize() * zoom)), x + 8 * zoom, y + 8 * zoom, 0xffced6c8, entry.getKey() + (group.get("comment").getAsString().isBlank() ? "" : ": " + group.get("comment").getAsString().replace('\n', ' ')));
            draw.popClipRect();
        }
    }
}
