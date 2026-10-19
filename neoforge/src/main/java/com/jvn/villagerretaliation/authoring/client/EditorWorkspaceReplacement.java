package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

final class EditorWorkspaceReplacement {
    private final ImString find = new ImString(1024), replacement = new ImString(1024), filter = new ImString(256);
    private final Set<AuthoringDocument> selected = new LinkedHashSet<>();
    private AuthoringWorkspace owner;
    private EditorWordingReplacement.Batch preview;
    private String previewFind = "", previewReplacement = "";

    void render(AuthoringWorkspace workspace, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Consumer<AuthoringDocument> applied) {
        if (!ImGui.collapsingHeader("Replace wording across files")) return;
        if (owner != workspace) { owner = workspace; selected.clear(); selected.addAll(workspace.documents()); preview = null; }
        selected.retainAll(workspace.documents());
        if (ImGui.inputText("Find across files", find) | ImGui.inputText("Replacement wording", replacement)) preview = null;
        ImGui.inputTextWithHint("##replace_file_filter", "Filter file paths...", filter);
        if (ImGui.smallButton("Select matching files")) { workspace.documents().stream().filter(this::matches).forEach(selected::add); preview = null; }
        ImGui.sameLine();
        if (ImGui.smallButton("Clear selected files")) { selected.clear(); preview = null; }
        ImGui.beginChild("##replace_files", 0, 130, true);
        for (var document : workspace.documents()) if (matches(document)) {
            if (ImGui.checkbox(document.resourcePath(), selected.contains(document))) {
                if (!selected.remove(document)) selected.add(document);
                preview = null;
            }
        }
        ImGui.endChild();
        ImGui.textDisabled(selected.size() + " selected files");
        ImGui.beginDisabled(selected.isEmpty() || find.get().isBlank());
        if (ImGui.button("Preview workspace replacements")) run.accept(() -> {
            preview = EditorWordingReplacement.preview(java.util.List.copyOf(selected), find.get(), replacement.get());
            previewFind = find.get(); previewReplacement = replacement.get();
        });
        ImGui.endDisabled();
        if (preview == null) return;
        var plan = preview;
        boolean current = plan.current(workspace) && previewFind.equals(find.get()) && previewReplacement.equals(replacement.get());
        int count = plan.files().stream().mapToInt(EditorWordingReplacement.Preview::occurrences).sum();
        long files = plan.files().stream().filter(file -> !file.changes().isEmpty()).count();
        ImGui.textWrapped(count + " replacements in " + files + " files");
        if (!current) ImGui.textWrapped("Files changed. Preview again before applying.");
        ImGui.beginDisabled(!current || count == 0);
        if (ImGui.button("Apply workspace replacements")) run.accept(() -> {
            if (preview != plan || !previewFind.equals(find.get()) || !previewReplacement.equals(replacement.get()))
                throw new IllegalStateException("Preview the current wording again");
            plan.apply(workspace);
            preview = null;
            plan.files().stream().filter(file -> !file.changes().isEmpty()).findFirst().ifPresent(file -> applied.accept(file.document()));
        });
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Cancel workspace preview")) preview = null;
        ImGui.beginChild("##workspace_replacement_preview", 0, 220, true);
        for (var file : plan.files()) if (!file.changes().isEmpty()) {
            ImGui.textWrapped(file.document().resourcePath());
            for (var change : file.changes()) {
                ImGui.textWrapped(change.pointer());
                ImGui.textWrapped("Before: " + change.before());
                ImGui.textWrapped("After: " + change.after());
            }
            ImGui.separator();
        }
        ImGui.endChild();
    }

    private boolean matches(AuthoringDocument document) {
        return document.resourcePath().toLowerCase(java.util.Locale.ROOT).contains(filter.get().strip().toLowerCase(java.util.Locale.ROOT));
    }
}
