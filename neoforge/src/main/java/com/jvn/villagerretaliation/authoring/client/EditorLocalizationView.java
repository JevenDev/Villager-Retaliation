package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import imgui.ImGui;
import imgui.type.ImString;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class EditorLocalizationView {
    private final ImString targetInput = new ImString("fr_fr", 32), search = new ImString(256), csvPath = new ImString(4096);
    private final EditorTextField textFields = new EditorTextField();
    private final Map<String, ImString> inputs = new HashMap<>();
    private AuthoringWorkspace owner;
    private Map<AuthoringDocument, Long> revisions = Map.of();
    private List<EditorLocalization.Row> rows = List.of();
    private Map<String, String> states = Map.of();
    private String sourceLanguage = "en_us", targetLanguage = "fr_fr", selected = "", filter = "All", status = "";
    private String cachedSource = "", cachedTarget = "", usageId = "";
    private List<EditorReferences.Location> usages = List.of();
    private EditorLocalization.Import preview;

    void beginFrame(boolean visible, Map<String, String> shortcuts) { textFields.beginFrame(visible, shortcuts); }

    AuthoringDocument selectedDocument() {
        return rows.stream().filter(row -> row.id().equals(selected)).findFirst().map(row -> row.target() == null ? row.source() : row.target()).orElse(null);
    }

    boolean ownsHistory(AuthoringDocument document) {
        return rows.stream().anyMatch(row -> row.id().equals(selected) && row.targetPath().equals(document.resourcePath()));
    }

    private void refresh(AuthoringWorkspace workspace) {
        if (owner != workspace) {
            owner = workspace; revisions = Map.of(); inputs.clear(); textFields.clear();
            selected = ""; usageId = ""; preview = null; status = "";
        }
        if (workspace == null) { rows = List.of(); return; }
        var latest = new java.util.LinkedHashMap<AuthoringDocument, Long>();
        workspace.documents().forEach(document -> latest.put(document, document.revision()));
        if (latest.equals(revisions) && cachedSource.equals(sourceLanguage) && cachedTarget.equals(targetLanguage)) return;
        revisions = latest; cachedSource = sourceLanguage; cachedTarget = targetLanguage; usageId = "";
        rows = sourceLanguage.equals(targetLanguage) ? List.of() : EditorLocalization.rows(workspace, sourceLanguage, targetLanguage);
        states = rows.stream().collect(java.util.stream.Collectors.toMap(EditorLocalization.Row::id, EditorLocalization.Row::state));
        if (rows.stream().noneMatch(row -> row.id().equals(selected))) selected = rows.isEmpty() ? "" : rows.getFirst().id();
    }

    void render(AuthoringWorkspace workspace, EditorDocumentBuffers buffers, float scale,
                Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        refresh(workspace);
        ImGui.textUnformatted("Translations");
        if (workspace == null) { ImGui.textWrapped("Open a workspace to translate its locale files."); return; }
        ImGui.setNextItemWidth(150 * scale);
        if (ImGui.beginCombo("Source language", sourceLanguage)) {
            for (String language : EditorLocalization.languages(workspace))
                if (ImGui.selectable(language, language.equals(sourceLanguage))) { sourceLanguage = language; preview = null; }
            ImGui.endCombo();
        }
        ImGui.setNextItemWidth(150 * scale);
        ImGui.inputText("Translation language", targetInput);
        EditorWidgets.sameLineFor("Use language");
        if (ImGui.button("Use language")) run.accept(() -> {
            String language = targetInput.get().strip().toLowerCase(java.util.Locale.ROOT);
            if (!language.matches("[a-z0-9_-]+") || language.equals(sourceLanguage))
                throw new IllegalArgumentException("Choose a different language code such as fr_fr");
            targetLanguage = language; targetInput.set(language); preview = null;
        });
        if (sourceLanguage.equals(targetLanguage)) { ImGui.textWrapped("Choose different source and translation languages."); return; }
        ImGui.setNextItemWidth(Math.max(120, ImGui.getContentRegionAvailX() - 200 * scale));
        ImGui.inputTextWithHint("##translation_search", "Find a key, file or wording...", search);
        ImGui.sameLine(); ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##translation_status", filter)) {
            for (String option : List.of("All", "Missing", "Needs review", "Source changed", "Placeholder warning", "Structure differs", "Reviewed"))
                if (ImGui.selectable(option, option.equals(filter))) filter = option;
            ImGui.endCombo();
        }
        var shown = rows.stream().filter(this::matches).toList();
        ImGui.textDisabled(shown.size() + " of " + rows.size() + " messages");
        csv(workspace, buffers, shown, run);
        if (!status.isEmpty()) ImGui.textWrapped(status);
        int flags = imgui.flag.ImGuiTableFlags.RowBg | imgui.flag.ImGuiTableFlags.Resizable
                | imgui.flag.ImGuiTableFlags.ScrollY | imgui.flag.ImGuiTableFlags.BordersInnerV;
        if (ImGui.beginTable("##translations", 4, flags, 0, 0)) {
            ImGui.tableSetupColumn("Key / file", imgui.flag.ImGuiTableColumnFlags.WidthStretch, 1.2f);
            ImGui.tableSetupColumn(sourceLanguage, imgui.flag.ImGuiTableColumnFlags.WidthStretch, 1);
            ImGui.tableSetupColumn(targetLanguage, imgui.flag.ImGuiTableColumnFlags.WidthStretch, 1);
            ImGui.tableSetupColumn("Status", imgui.flag.ImGuiTableColumnFlags.WidthFixed, 196 * scale);
            ImGui.tableSetupScrollFreeze(0, 1); ImGui.tableHeadersRow();
            float height = ImGui.getTextLineHeightWithSpacing() * 2 + 8 * scale;
            var clipper = new imgui.ImGuiListClipper();
            try {
                clipper.begin(shown.size(), height);
                while (clipper.step()) for (int i = clipper.getDisplayStart(); i < clipper.getDisplayEnd(); i++) {
                    var row = shown.get(i);
                    ImGui.tableNextRow(0, height); ImGui.tableSetColumnIndex(0);
                    ImGui.pushID(row.id());
                    if (ImGui.selectable(row.key(), selected.equals(row.id()), imgui.flag.ImGuiSelectableFlags.SpanAllColumns)) selected = row.id();
                    if (ImGui.isItemHovered()) ImGui.setTooltip(row.key() + "\n" + row.source().resourcePath());
                    ImGui.textDisabled(row.source().resourcePath());
                    ImGui.tableSetColumnIndex(1); summary(row.original());
                    ImGui.tableSetColumnIndex(2); summary(row.translated());
                    ImGui.tableSetColumnIndex(3); ImGui.textUnformatted(states.get(row.id()));
                    if (row.target() != null && buffers.pending(row.target())) ImGui.textDisabled("Unfinished edits");
                    ImGui.popID();
                }
                clipper.end();
            } finally { clipper.destroy(); }
            ImGui.endTable();
        }
        if (rows.isEmpty()) ImGui.textWrapped("No source locale messages found. Import locale JSON files or create a quest conversation first.");
    }

    private boolean matches(EditorLocalization.Row row) {
        String state = states.get(row.id());
        if (!filter.equals("All") && !(filter.equals("Needs review") ? state.equals("Not reviewed") || state.equals("Source changed") : filter.equals(state))) return false;
        String query = search.get().strip().toLowerCase(java.util.Locale.ROOT);
        return query.isEmpty() || (row.key() + " " + row.source().resourcePath() + " " + row.original() + " " + row.translated()).toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    private static void summary(com.google.gson.JsonElement value) {
        String text = String.join(" / ", EditorLocalization.text(value).values()).replace('\n', ' ');
        ImGui.textUnformatted(text.length() > 120 ? text.substring(0, 120) + "..." : text);
        if (ImGui.isItemHovered()) ImGui.setTooltip(String.join("\n", EditorLocalization.text(value).values()));
    }

    void details(AuthoringWorkspace workspace, EditorDocumentBuffers buffers, float scale,
                 Consumer<EditorReferences.Location> open, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        refresh(workspace);
        var row = rows.stream().filter(value -> value.id().equals(selected)).findFirst().orElse(null);
        if (row == null) { ImGui.textWrapped("Select a message in Translations."); return; }
        ImGui.textWrapped(row.key());
        ImGui.textWrapped(row.source().resourcePath());
        ImGui.textUnformatted(row.state());
        if (ImGui.button("Open source")) run.accept(() -> open.accept(new EditorReferences.Location(row.source(), row.sourcePointer())));
        if (!usageId.equals(row.id())) {
            usageId = row.id();
            usages = EditorReferences.usages(workspace, new EditorReferences.Location(row.source(), row.sourcePointer()), sourceLanguage);
        }
        if (!usages.isEmpty() && ImGui.collapsingHeader("Used by " + usages.size() + " fields")) for (var location : usages)
            if (ImGui.selectable(location.label())) run.accept(() -> open.accept(location));
        if (row.translated() == null) {
            ImGui.separator(); ImGui.textUnformatted(sourceLanguage);
            for (String line : EditorLocalization.text(row.original()).values()) ImGui.textWrapped(line);
            if (ImGui.button("Add translation")) run.accept(() -> {
                requireSaved(buffers, row);
                EditorLocalization.addTranslation(workspace, row);
            });
            return;
        }
        boolean raw = buffers.raw(row.target()) != null;
        if (raw) ImGui.textWrapped("This translation file has unfinished JSON. Apply or discard that draft before editing its fields.");
        var source = EditorLocalization.text(row.original());
        var target = EditorLocalization.text(row.translated());
        ImGui.beginDisabled(raw);
        for (var entry : target.entrySet()) {
            String path = row.targetPointer() + entry.getKey(), id = row.targetPath() + path;
            ImGui.pushID(id);
            ImGui.separator(); ImGui.textUnformatted(sourceLanguage + (entry.getKey().isEmpty() ? "" : " / " + entry.getKey()));
            ImGui.textWrapped(source.getOrDefault(entry.getKey(), "No corresponding source line"));
            ImGui.textUnformatted(targetLanguage);
            var edits = buffers.fields(row.target());
            String content = edits.text(path, entry.getValue());
            var input = inputs.computeIfAbsent(id, ignored -> new ImString(content, 65536));
            if (!input.get().equals(content)) input.set(content);
            textFields.context(new EditorTextCompletion.Context(DefinitionKind.TREE, row.id(), java.util.Set.of(), Map.of()));
            textFields.render(id, input, DefinitionKind.TREE, true, scale);
            edits.update(path, row.target().value(path), input.get());
            String issue = edits.issue(row.target(), path);
            if (!issue.isEmpty()) ImGui.textWrapped(issue);
            ImGui.popID();
        }
        ImGui.endDisabled();
        JsonObject candidate = new JsonObject(); candidate.add("value", row.translated().deepCopy());
        for (String relative : target.keySet()) {
            String path = row.targetPointer() + relative;
            if (buffers.fields(row.target()).issue(row.target(), path).isEmpty())
                com.jvn.villagerretaliation.authoring.core.JsonPointers.set(candidate, "/value" + relative, buffers.fields(row.target()).candidate(row.target(), path));
        }
        for (String warning : EditorLocalization.warnings(row.original(), candidate.get("value"))) ImGui.textWrapped(warning);
        ImGui.beginDisabled(raw);
        if (ImGui.button("Apply and mark reviewed")) run.accept(() -> {
            if (buffers.pending(row.source())) throw new IllegalStateException("Save the source edits before reviewing this translation");
            if (buffers.raw(row.target()) != null) throw new IllegalStateException("Apply or discard the translation JSON draft first");
            EditorLocalization.review(workspace, row, buffers.fields(row.target()));
        });
        ImGui.endDisabled();
        EditorWidgets.sameLineFor("Open translation file");
        if (ImGui.button("Open translation file")) run.accept(() -> open.accept(new EditorReferences.Location(row.target(), row.targetPointer())));
        ImGui.textWrapped("Save keeps your wording. Mark reviewed records the source version so later source changes are flagged.");
    }

    private static void requireSaved(EditorDocumentBuffers buffers, EditorLocalization.Row row) {
        if (buffers.pending(row.source()) || row.target() != null && buffers.pending(row.target()))
            throw new IllegalStateException("Save or discard unfinished source and translation edits first");
    }

    private void csv(AuthoringWorkspace workspace, EditorDocumentBuffers buffers, List<EditorLocalization.Row> shown,
                     Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        if (!ImGui.collapsingHeader("CSV import / export")) return;
        ImGui.setNextItemWidth(-1); ImGui.inputTextWithHint("##locale_csv", "CSV file path...", csvPath);
        ImGui.beginDisabled(csvPath.get().isBlank() || shown.isEmpty());
        if (ImGui.button("Export visible messages")) run.accept(() -> {
            shown.forEach(row -> requireSaved(buffers, row));
            Files.writeString(Path.of(csvPath.get()), EditorLocalization.exportCsv(shown), StandardOpenOption.CREATE_NEW);
            status = "CSV exported. Edit the translation column and keep the other columns for conflict checks.";
        });
        ImGui.endDisabled();
        EditorWidgets.sameLineFor("Preview CSV import");
        if (ImGui.button("Preview CSV import")) run.accept(() -> {
            Path path = Path.of(csvPath.get());
            if (Files.size(path) > 8 * 1024 * 1024) throw new IllegalArgumentException("Split CSV files larger than 8 MB before importing");
            preview = EditorLocalization.importCsv(workspace, sourceLanguage, targetLanguage, Files.readString(path));
            status = preview.changes().size() + " translated lines ready to review";
        });
        if (preview == null) return;
        var plan = preview;
        ImGui.beginChild("##translation_import", 0, 150, true);
        for (var change : plan.changes()) {
            ImGui.textWrapped(change.row().key() + change.pointer());
            ImGui.textWrapped("Before: " + change.before());
            ImGui.textWrapped("After: " + change.after());
        }
        ImGui.endChild();
        ImGui.beginDisabled(plan.changes().isEmpty());
        if (ImGui.button("Apply CSV import")) run.accept(() -> {
            plan.apply(workspace, buffers); preview = null; inputs.clear(); textFields.clear();
            if (!plan.changes().isEmpty()) selected = plan.changes().getFirst().row().id();
            status = "Imported " + plan.changes().size() + " translated lines. Save the workspace to write the files.";
        });
        ImGui.endDisabled();
        EditorWidgets.sameLineFor("Cancel import");
        if (ImGui.button("Cancel import")) preview = null;
    }
}
