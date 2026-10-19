package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.authoring.preview.PlaytestDrafts;
import com.jvn.villagerretaliation.authoring.preview.PreviewCoverage;
import imgui.ImGui;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

final class EditorCoverage {
    private final java.util.Map<AuthoringDocument, Entry> entries = new WeakHashMap<>();
    private boolean show = true;
    private record Entry(JsonElement logic, Set<String> nodes, Set<String> choices) { }
    record Snapshot(Set<String> nodes, Set<String> choices) {
        boolean visited(String path) { return nodes.contains(path); }
        boolean chosen(String pointer) { return choices.stream().anyMatch(path -> pointer.equals(path) || pointer.startsWith(path + "/")); }
    }
    Snapshot snapshot(AuthoringDocument document) { var entry = entry(document); return new Snapshot(entry.nodes(), entry.choices()); }

    private Entry entry(AuthoringDocument document) {
        JsonElement logic = PlaytestDrafts.logic(document.json());
        Entry current = entries.get(document);
        if (current == null || !current.logic().equals(logic)) {
            current = new Entry(logic, new LinkedHashSet<>(), new LinkedHashSet<>()); entries.put(document, current);
        }
        return current;
    }

    Consumer<PreviewCoverage> observe(AuthoringDocument document) {
        JsonElement logic = PlaytestDrafts.logic(document.json());
        return batch -> {
            if (!PlaytestDrafts.logic(document.json()).equals(logic)) return;
            Entry current = entry(document); current.nodes().addAll(batch.nodes()); current.choices().addAll(batch.choices());
        };
    }

    void live(AuthoringDocument document, JsonObject source, PreviewCoverage batch) {
        if (!PlaytestDrafts.logic(source).equals(PlaytestDrafts.logic(document.json()))) return;
        Set<String> nodes = new LinkedHashSet<>(), choices = new LinkedHashSet<>();
        for (var node : DialogueGraph.nodes(document)) {
            if (batch.nodes().contains(node.id())) nodes.add(node.pointer());
            if (batch.choices().contains("transition:" + node.id())) choices.add(node.pointer() + "/transition");
        }
        for (String path : batch.choices()) if (path.startsWith("/")) choices.add(path.endsWith("/next") ? path.substring(0, path.length() - 5) + "/transition" : path);
        observe(document).accept(new PreviewCoverage(nodes, choices));
    }

    void clear(AuthoringDocument document) { entries.remove(document); }
    boolean visited(AuthoringDocument document, String pointer) { return entry(document).nodes().contains(pointer); }
    boolean chosen(AuthoringDocument document, String pointer) {
        return entry(document).choices().stream().anyMatch(path -> pointer.equals(path) || pointer.startsWith(path + "/"));
    }
    boolean visible() { return show; }

    static Set<String> choices(AuthoringDocument document) {
        Set<String> result = new LinkedHashSet<>(); JsonObject root = document.json();
        for (var node : DialogueGraph.nodes(document)) {
            JsonObject value = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            for (String field : java.util.List.of("responses", "options", "events")) {
                var values = DialogueGraph.array(value, field);
                for (int i = 0; i < values.size(); i++) if (values.get(i).isJsonObject()) result.add(node.pointer() + "/" + field + "/" + i);
            }
            for (String field : java.util.List.of("next", "transition")) if (value.has(field)) result.add(node.pointer() + "/" + field);
        }
        for (var edge : DialogueGraph.edges(document)) if (edge.pointer().startsWith("/checks/")) result.add(edge.pointer());
        return result;
    }

    String summary(AuthoringDocument document) {
        var nodes = DialogueGraph.nodes(document); var snapshot = snapshot(document);
        long visited = nodes.stream().filter(node -> snapshot.visited(node.pointer())).count();
        var choices = choices(document);
        long selected = choices.stream().filter(snapshot::chosen).count();
        return visited + "/" + nodes.size() + " nodes visited, " + selected + "/" + choices.size() + " choices tested";
    }

    void render(AuthoringDocument document, Consumer<String> focus) {
        if (document.kind() == DefinitionKind.RESOURCE || document.kind().campaignManifest() || !EditorWidgets.section("Playtest coverage")) return;
        if (ImGui.checkbox("Highlight playtest coverage", show)) show = !show;
        ImGui.textWrapped(summary(document));
        ImGui.textWrapped("Coverage accumulates during this editor session. Logic edits reset it. Green marks visited nodes and chosen paths.");
        if (ImGui.button("Clear coverage for this file")) clear(document);
        var snapshot = snapshot(document);
        for (var node : DialogueGraph.nodes(document)) if (!snapshot.visited(node.pointer())
                && ImGui.selectable("Untested: " + node.id())) focus.accept(node.id());
    }
}
