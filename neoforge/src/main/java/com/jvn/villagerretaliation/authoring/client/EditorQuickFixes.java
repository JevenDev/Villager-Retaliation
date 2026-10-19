package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import imgui.ImGui;
import java.util.List;
import java.util.function.Consumer;

final class EditorQuickFixes {
    private EditorQuickFixes() { }
    record Target(String pointer, String missing) { }

    static Target target(AuthoringDocument document, AuthoringDiagnostic issue) {
        if (document == null || !document.resourcePath().equals(issue.resourcePath()) || document.kind() == DefinitionKind.FORCED) return null;
        if (!issue.message().startsWith("Missing target node ") && !List.of("Missing entry node", "Missing entry stage").contains(issue.message())) return null;
        try {
            var value = JsonPointers.get(document.json(), issue.pointer());
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            String id = value.getAsString();
            return DialogueGraph.nodes(document).stream().anyMatch(node -> node.id().equals(id)) ? null : new Target(issue.pointer(), id);
        } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException exception) { return null; }
    }

    static void apply(AuthoringWorkspace workspace, AuthoringDocument document, Target target, String replacement, boolean create) {
        var original = JsonPointers.get(document.json(), target.pointer());
        if (!original.equals(new JsonPrimitive(target.missing()))) throw new IllegalStateException("The reference changed. Validate again before fixing it");
        var draft = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        if (create) {
            DialogueGraph.addNode(draft, replacement);
            if (document.kind() == DefinitionKind.CONVERSATION && !DialogueGraph.array(draft.json(), "outcomes").isEmpty()) {
                String outcome = DialogueGraph.string(DialogueGraph.array(draft.json(), "outcomes").get(0).getAsJsonObject(), "id");
                var node = DialogueGraph.nodes(draft).stream().filter(value -> value.id().equals(replacement)).findFirst().orElseThrow();
                draft.edit("Set terminal outcome", root -> JsonPointers.get(root, node.pointer()).getAsJsonObject().addProperty("outcome", outcome));
            }
        } else if (DialogueGraph.nodes(draft).stream().noneMatch(node -> node.id().equals(replacement))) throw new IllegalArgumentException("Choose an existing destination");
        draft.edit("Repair reference", root -> JsonPointers.set(root, target.pointer(), new JsonPrimitive(replacement)));
        EditorMutation.validate(workspace, document, draft.json());
        document.edit(create ? "Create missing destination" : "Replace missing destination", root -> {
            root.keySet().clear(); draft.json().entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));
        });
    }

    static void render(AuthoringWorkspace workspace, AuthoringDocument document, AuthoringDiagnostic issue,
                       Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        var target = target(document, issue);
        if (target == null) return;
        ImGui.pushID("fix:" + issue.pointer());
        if (ImGui.beginCombo("##replacement", "Fix: choose an existing destination...")) {
            for (var node : DialogueGraph.nodes(document)) if (ImGui.selectable(node.id()))
                run.accept(() -> { apply(workspace, document, target, node.id(), false); refresh.run(); });
            ImGui.endCombo();
        }
        if (target.missing().matches("[A-Za-z0-9_.:-]+") && ImGui.smallButton("Create destination " + target.missing()))
            run.accept(() -> { apply(workspace, document, target, target.missing(), true); refresh.run(); });
        ImGui.popID();
    }
}
