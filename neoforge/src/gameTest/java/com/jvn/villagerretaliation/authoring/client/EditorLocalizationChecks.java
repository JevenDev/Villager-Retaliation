package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

final class EditorLocalizationChecks {
    private static final String SOURCE = "data/test/quests/story/locales/en_us.json";
    static int run() throws IOException {
        csvPreservesUnicodeQuotesAndMultilineCells();
        missingMessagesKeepVariantsAndConditions();
        reviewIsAtomicAndTracksChangedSources();
        csvImportIsPreviewedUndoableAndScoped();
        importsRejectStaleOrConflictingChanges();
        placeholdersAndLanguageFilesStayDistinct();
        return 6;
    }

    private static void csvPreservesUnicodeQuotesAndMultilineCells() {
        var rows = List.of(List.of("key", "wording", "empty"), List.of("é,漢字", "Say \"hello\"\r\nAgain", ""));
        check(EditorCsv.read("\ufeff" + EditorCsv.write(rows)).equals(rows), "CSV round trip preserves Unicode, commas, quotes, newlines and empty cells");
        check(EditorCsv.read("a,b,\n").equals(List.of(List.of("a", "b", ""))), "unquoted CSV retains trailing empty columns");
        rejects(() -> EditorCsv.read("\"unfinished"));
        rejects(() -> EditorCsv.read("\"closed\"junk"));
        rejects(() -> EditorCsv.read("not\"quoted"));
    }

    private static void missingMessagesKeepVariantsAndConditions() throws IOException {
        var workspace = fixture();
        try {
            var row = row(workspace, "variants");
            EditorLocalization.addTranslation(workspace, row);
            var translated = row(workspace, "variants");
            check(translated.state().equals("Missing"), "new translation remains missing until its lines are filled");
            check(translated.translated().getAsJsonArray().get(1).getAsJsonObject().getAsJsonArray("conditions").size() == 1,
                    "translation scaffolding preserves conditional variants");
            check(EditorLocalization.text(translated.translated()).values().stream().allMatch(String::isEmpty), "source wording is not silently counted as translated");
            translated.target().undo();
            check(row(workspace, "variants").target() == null, "undo removes a newly created locale");
            translated.target().redo();
            check(row(workspace, "variants").target() == translated.target(), "redo restores the locale and its identity");
        } finally { cleanup(workspace); }
    }

    private static void reviewIsAtomicAndTracksChangedSources() throws IOException {
        var workspace = fixture();
        try {
            EditorLocalization.addTranslation(workspace, row(workspace, "greeting"));
            var row = row(workspace, "greeting");
            var edits = new EditorFieldEdits();
            edits.update(row.targetPointer(), row.translated(), "Bonjour");
            rejects(() -> EditorLocalization.review(workspace, row, edits));
            check(edits.pending() && row.target().value(row.targetPointer()).getAsString().isEmpty(), "invalid review retains drafts without applying any wording");
            edits.update(row.targetPointer(), row.translated(), "Bonjour, {player}!");
            EditorLocalization.review(workspace, row, edits);
            check(row(workspace, "greeting").state().equals("Reviewed") && !edits.pending(), "review applies wording and its source baseline together");
            row.target().undo();
            check(row(workspace, "greeting").state().equals("Missing"), "one undo restores both wording and review state");
            row.target().redo();
            workspace.save();
            var reopened = AuthoringWorkspace.open(workspace.directory());
            check(row(reopened, "greeting").state().equals("Reviewed"), "review metadata survives restart");
            row.source().edit("Change source", root -> root.getAsJsonObject("messages").addProperty("greeting", "Welcome, {player}!"));
            check(row(workspace, "greeting").state().equals("Source changed"), "edited source wording marks reviewed translations stale");
            check(!row.target().json().has("localization_reviews"), "review data stays out of the exported locale format");
        } finally { cleanup(workspace); }
    }

    private static void csvImportIsPreviewedUndoableAndScoped() throws IOException {
        var workspace = fixture();
        try {
            var other = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/world_campaigns/other/quests/story/locales/en_us.json",
                    JsonParser.parseString("{\"messages\":{\"greeting\":\"Other {player}\"}}").getAsJsonObject());
            workspace.add(other);
            var table = mutable(EditorCsv.read(EditorLocalization.exportCsv(EditorLocalization.rows(workspace, "en_us", "fr_fr"))));
            for (int i = 1; i < table.size(); i++) if (table.get(i).get(1).equals("greeting")) table.get(i).set(4, "Traduit {player}");
            var plan = EditorLocalization.importCsv(workspace, "en_us", "fr_fr", EditorCsv.write(table));
            check(plan.changes().size() == 2 && row(workspace, "greeting").target() == null, "CSV preview leaves source and translation documents untouched");
            plan.apply(workspace, new EditorDocumentBuffers());
            var targets = EditorLocalization.rows(workspace, "en_us", "fr_fr").stream().filter(value -> value.key().equals("greeting")).toList();
            check(targets.stream().allMatch(value -> value.state().equals("Reviewed")), "CSV import records both translations and their source versions");
            check(targets.get(0).target() != targets.get(1).target(), "equal keys in different campaign scopes remain separate");
            var undo = targets.getFirst().target();
            undo.undo();
            check(EditorLocalization.rows(workspace, "en_us", "fr_fr").stream().allMatch(value -> value.target() == null), "CSV import across files undoes as one operation");
            undo.redo();
            check(row(workspace, "greeting").state().equals("Reviewed"), "grouped redo restores imported translations");
        } finally { cleanup(workspace); }
    }

    private static void importsRejectStaleOrConflictingChanges() throws IOException {
        var workspace = fixture();
        try {
            var table = mutable(EditorCsv.read(EditorLocalization.exportCsv(EditorLocalization.rows(workspace, "en_us", "fr_fr"))));
            var greeting = table.stream().skip(1).filter(value -> value.get(1).equals("greeting")).findFirst().orElseThrow();
            greeting.set(4, "Bonjour {player}");
            String csv = EditorCsv.write(table);
            var plan = EditorLocalization.importCsv(workspace, "en_us", "fr_fr", csv);
            var source = row(workspace, "greeting").source();
            var buffers = new EditorDocumentBuffers();
            buffers.fields(source).update("/messages/greeting", source.value("/messages/greeting"), "Unfinished {player}");
            rejects(() -> plan.apply(workspace, buffers));
            check(row(workspace, "greeting").target() == null, "pending source drafts block imports without creating files");
            source.edit("Change source", root -> root.getAsJsonObject("messages").addProperty("greeting", "New {player}"));
            rejects(() -> plan.apply(workspace, new EditorDocumentBuffers()));
            rejects(() -> EditorLocalization.importCsv(workspace, "en_us", "fr_fr", csv));
            source.undo();
            greeting.set(4, "Missing variable");
            rejects(() -> EditorLocalization.importCsv(workspace, "en_us", "fr_fr", EditorCsv.write(table)));
            greeting.set(4, "Bonjour {player}");
            table.add(new ArrayList<>(greeting));
            rejects(() -> EditorLocalization.importCsv(workspace, "en_us", "fr_fr", EditorCsv.write(table)));
        } finally { cleanup(workspace); }
    }

    private static void placeholdersAndLanguageFilesStayDistinct() throws IOException {
        var workspace = fixture();
        try {
            var lang = new AuthoringDocument(DefinitionKind.RESOURCE, "assets/test/lang/en_us.json",
                    JsonParser.parseString("{\"item.test.crystal\":\"Crystal %1$s\"}").getAsJsonObject());
            workspace.add(lang);
            var row = EditorLocalization.rows(workspace, "en_us", "fr_fr").stream().filter(value -> value.source() == lang).findFirst().orElseThrow();
            check(row.targetPath().equals("assets/test/lang/fr_fr.json") && row.sourcePointer().equals("/item.test.crystal"), "Minecraft language files retain their flat shape");
            check(!EditorLocalization.warnings(row.original(), new com.google.gson.JsonPrimitive("Cristal")).isEmpty(), "printf placeholders are checked");
            check(EditorLocalization.warnings(new com.google.gson.JsonPrimitive("{a} {b}"), new com.google.gson.JsonPrimitive("{b} {a}")).isEmpty(), "translations may reorder variables");
            check(!EditorLocalization.warnings(new com.google.gson.JsonPrimitive("{a} {a}"), new com.google.gson.JsonPrimitive("{a}")).isEmpty(), "missing repeated variables are detected");
            rejects(() -> EditorLocalization.targetPath(SOURCE, "../outside"));
            rejects(() -> EditorLocalization.targetPath(SOURCE, "en_us"));
        } finally { cleanup(workspace); }
    }

    private static AuthoringWorkspace fixture() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-localization-check"));
        workspace.add(new AuthoringDocument(DefinitionKind.RESOURCE, SOURCE, JsonParser.parseString("""
                {"schema":"villagerretaliation:quest_locale/v1","messages":{
                  "greeting":"Hello, {player}!","variants":["First",{"text":"Second {count}","conditions":[{"type":"weather","value":"rain"}]}]
                }}
                """).getAsJsonObject()));
        return workspace;
    }
    private static EditorLocalization.Row row(AuthoringWorkspace workspace, String key) {
        return EditorLocalization.rows(workspace, "en_us", "fr_fr").stream().filter(value -> value.source().resourcePath().equals(SOURCE) && value.key().equals(key)).findFirst().orElseThrow();
    }
    private static List<List<String>> mutable(List<List<String>> rows) { return new ArrayList<>(rows.stream().map(row -> (List<String>) new ArrayList<>(row)).toList()); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected the invalid operation to be rejected");
    }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static void cleanup(AuthoringWorkspace workspace) throws IOException {
        try (var paths = Files.walk(workspace.directory())) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
