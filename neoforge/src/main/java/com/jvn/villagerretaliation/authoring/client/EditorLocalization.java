package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

final class EditorLocalization {
    private static final Pattern FILE = Pattern.compile("^(.*(?:/locales/|/lang/))([a-z0-9_-]+)\\.json$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9_.:/-]+}|%(?:[0-9]+\\$)?[sdif]");
    private static final List<String> CSV_HEADER = List.of("source_file", "key", "pointer", "source", "translation", "previous_translation");
    record Row(AuthoringDocument source, AuthoringDocument target, String targetPath, String key,
               String sourcePointer, String targetPointer, JsonElement original, JsonElement translated) {
        String id() { return source.resourcePath() + sourcePointer; }
        String state() {
            if (translated == null) return "Missing";
            var sourceText = text(original);
            var targetText = text(translated);
            if (!sourceText.keySet().equals(targetText.keySet())) return "Structure differs";
            if (targetText.values().stream().anyMatch(String::isBlank)) return "Missing";
            if (!warnings(original, translated).isEmpty()) return "Placeholder warning";
            JsonObject reviews = reviews(target.metadata());
            boolean unreviewed = false;
            for (var entry : sourceText.entrySet()) {
                String id = source.resourcePath() + sourcePointer + entry.getKey();
                var review = reviews.get(id);
                if (review == null || !review.isJsonObject()) { unreviewed = true; continue; }
                var saved = review.getAsJsonObject();
                if (!entry.getValue().equals(string(saved, "source"))) return "Source changed";
                if (!targetText.get(entry.getKey()).equals(string(saved, "translation"))) unreviewed = true;
            }
            return unreviewed ? "Not reviewed" : "Reviewed";
        }
    }
    record Change(Row row, String pointer, String before, String after) { }
    record Import(List<Change> changes, Map<AuthoringDocument, Long> baselines,
                  Map<String, JsonObject> definitions, Map<String, JsonObject> metadata) {
        void apply(AuthoringWorkspace workspace, EditorDocumentBuffers buffers) {
            for (var entry : baselines.entrySet()) {
                if (!workspace.documents().contains(entry.getKey()) || entry.getKey().revision() != entry.getValue())
                    throw new IllegalStateException("A source or translation changed. Preview the CSV again");
                if (buffers.pending(entry.getKey())) throw new IllegalStateException("Save or discard unfinished edits in " + entry.getKey().resourcePath());
            }
            for (String path : definitions.keySet())
                if (baselines.keySet().stream().noneMatch(document -> document.resourcePath().equals(path)) && workspace.filePaths().contains(path))
                    throw new IllegalStateException("A translation file was added. Preview the CSV again");
            workspace.editLocaleFiles("Import translations", definitions, metadata);
        }
    }

    static String language(String path) {
        var match = FILE.matcher(path);
        return match.matches() ? match.group(2) : "";
    }

    static List<String> languages(AuthoringWorkspace workspace) {
        return workspace.documents().stream().map(document -> language(document.resourcePath())).filter(value -> !value.isEmpty()).distinct().sorted().toList();
    }

    static String targetPath(String source, String language) {
        if (!language.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Use a language code such as fr_fr");
        var match = FILE.matcher(source);
        if (!match.matches()) throw new IllegalArgumentException("Choose a locale file");
        if (match.group(2).equals(language)) throw new IllegalArgumentException("Choose different source and translation languages");
        return match.group(1) + language + ".json";
    }

    static List<Row> rows(AuthoringWorkspace workspace, String sourceLanguage, String targetLanguage) {
        var result = new ArrayList<Row>();
        var documents = new LinkedHashMap<String, AuthoringDocument>();
        workspace.documents().forEach(document -> documents.put(document.resourcePath(), document));
        for (var source : workspace.documents()) if (language(source.resourcePath()).equals(sourceLanguage)) {
            String path = targetPath(source.resourcePath(), targetLanguage);
            var target = documents.get(path);
            JsonObject original = source.json(), translated = target == null ? emptyFile(original, targetLanguage) : target.json();
            var sourceMessages = messages(original);
            var targetMessages = messages(translated);
            for (var entry : sourceMessages.entrySet()) {
                if (sourceMessages == original && List.of("schema", "metadata", "locale").contains(entry.getKey())) continue;
                if (text(entry.getValue()).isEmpty()) continue;
                String key = entry.getKey(), escaped = JsonPointers.escape(key);
                result.add(new Row(source, target, path, key, prefix(original) + "/" + escaped, prefix(translated) + "/" + escaped,
                        entry.getValue(), targetMessages.get(key)));
            }
        }
        return List.copyOf(result);
    }

    static Map<String, String> text(JsonElement value) {
        var result = new LinkedHashMap<String, String>();
        collect(value, "", result);
        return result;
    }

    private static void collect(JsonElement value, String pointer, Map<String, String> result) {
        if (value == null) return;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) result.put(pointer, value.getAsString());
        else if (value.isJsonArray()) for (int i = 0; i < value.getAsJsonArray().size(); i++)
            collect(value.getAsJsonArray().get(i), pointer + "/" + i, result);
        else if (value.isJsonObject()) for (String field : List.of("text", "line", "lines", "variants"))
            if (value.getAsJsonObject().has(field)) collect(value.getAsJsonObject().get(field), pointer + "/" + field, result);
    }

    static List<String> warnings(JsonElement source, JsonElement target) {
        var result = new ArrayList<String>();
        var before = text(source); var after = text(target);
        for (var entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) { result.add("Missing line " + entry.getKey()); continue; }
            var expected = placeholders(entry.getValue()); var actual = placeholders(after.get(entry.getKey()));
            if (!expected.equals(actual)) result.add("Placeholders at " + (entry.getKey().isEmpty() ? "this line" : entry.getKey())
                    + ": expected " + expected + ", found " + actual);
        }
        return List.copyOf(result);
    }

    private static Map<String, Integer> placeholders(String text) {
        var result = new TreeMap<String, Integer>();
        var matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) result.merge(matcher.group(), 1, Integer::sum);
        return result;
    }

    static void addTranslation(AuthoringWorkspace workspace, Row row) {
        ensureCurrent(workspace, row);
        JsonObject changed = row.target() == null ? emptyFile(row.source().json(), language(row.targetPath())) : row.target().json();
        if (messages(changed).has(row.key())) throw new IllegalStateException("This translation already exists");
        messages(changed).add(row.key(), blank(row.original()));
        workspace.editLocaleFiles("Add translation", Map.of(row.targetPath(), changed), Map.of());
    }

    static void review(AuthoringWorkspace workspace, Row row, EditorFieldEdits edits) {
        ensureCurrent(workspace, row);
        if (row.target() == null) throw new IllegalArgumentException("Add a translation first");
        JsonObject definition = edits.candidate(row.target());
        JsonElement translated = JsonPointers.get(definition, row.targetPointer());
        if (!text(row.original()).keySet().equals(text(translated).keySet())
                || text(translated).values().stream().anyMatch(String::isBlank) || !warnings(row.original(), translated).isEmpty())
            throw new IllegalArgumentException("Complete the translation and resolve placeholder warnings before marking it reviewed");
        JsonObject metadata = row.target().metadata();
        var reviews = reviews(metadata);
        for (var entry : text(row.original()).entrySet()) {
            JsonObject review = new JsonObject();
            review.addProperty("source", entry.getValue());
            review.addProperty("translation", text(translated).get(entry.getKey()));
            reviews.add(row.id() + entry.getKey(), review);
        }
        metadata.add("localization_reviews", reviews);
        workspace.editDocuments("Review translation", Map.of(row.target(), definition), Map.of(row.target(), metadata));
        edits.clear();
    }

    private static void ensureCurrent(AuthoringWorkspace workspace, Row row) {
        if (!workspace.documents().contains(row.source()) || !row.source().value(row.sourcePointer()).equals(row.original()))
            throw new IllegalStateException("The source changed. Select the translation again");
        if (row.target() == null && workspace.filePaths().contains(row.targetPath()))
            throw new IllegalStateException("A translation file was added. Select it again");
        if (row.target() != null && (!workspace.documents().contains(row.target())
                || row.translated() != null && !row.target().value(row.targetPointer()).equals(row.translated())))
            throw new IllegalStateException("The translation changed. Select it again");
    }

    static String exportCsv(List<Row> rows) {
        var values = new ArrayList<List<String>>();
        values.add(CSV_HEADER);
        for (Row row : rows) for (var entry : text(row.original()).entrySet()) {
            String translated = text(row.translated()).getOrDefault(entry.getKey(), "");
            values.add(List.of(row.source().resourcePath(), row.key(), entry.getKey(), entry.getValue(), translated, translated));
        }
        return EditorCsv.write(values);
    }

    static Import importCsv(AuthoringWorkspace workspace, String sourceLanguage, String targetLanguage, String csv) {
        var table = EditorCsv.read(csv);
        if (table.isEmpty() || !table.getFirst().equals(CSV_HEADER)) throw new IllegalArgumentException("Use the columns from Export CSV without renaming them");
        var rows = new LinkedHashMap<String, Row>();
        rows(workspace, sourceLanguage, targetLanguage).forEach(row -> rows.put(row.source().resourcePath() + "\n" + row.key(), row));
        var changes = new ArrayList<Change>();
        var baselines = new LinkedHashMap<AuthoringDocument, Long>();
        var definitions = new LinkedHashMap<String, JsonObject>();
        var metadata = new LinkedHashMap<String, JsonObject>();
        var seen = new java.util.HashSet<String>();
        for (int i = 1; i < table.size(); i++) {
            var cells = table.get(i);
            if (cells.size() != CSV_HEADER.size()) throw new IllegalArgumentException("CSV row " + (i + 1) + " has the wrong number of columns");
            Row row = rows.get(cells.get(0) + "\n" + cells.get(1));
            String pointer = cells.get(2);
            if (row == null || !text(row.original()).containsKey(pointer)) throw new IllegalArgumentException("Unknown translation on CSV row " + (i + 1));
            if (!seen.add(row.id() + pointer)) throw new IllegalArgumentException("Duplicate translation on CSV row " + (i + 1));
            String before = text(row.translated()).getOrDefault(pointer, ""), after = cells.get(4);
            if (!text(row.original()).get(pointer).equals(cells.get(3)) || !before.equals(cells.get(5)))
                throw new IllegalStateException("Source or translation changed since export on CSV row " + (i + 1));
            if (before.equals(after)) continue;
            if (!placeholders(cells.get(3)).equals(placeholders(after)))
                throw new IllegalArgumentException("Placeholder mismatch on CSV row " + (i + 1));
            baselines.putIfAbsent(row.source(), row.source().revision());
            if (row.target() != null) baselines.putIfAbsent(row.target(), row.target().revision());
            JsonObject changed = definitions.computeIfAbsent(row.targetPath(), ignored -> row.target() == null ? emptyFile(row.source().json(), language(row.targetPath())) : row.target().json());
            var messages = messages(changed);
            if (!messages.has(row.key())) messages.add(row.key(), blank(row.original()));
            JsonPointers.set(changed, prefix(changed) + "/" + JsonPointers.escape(row.key()) + pointer, new JsonPrimitive(after));
            JsonObject notes = metadata.computeIfAbsent(row.targetPath(), ignored -> row.target() == null ? new JsonObject() : row.target().metadata());
            var reviewed = reviews(notes);
            JsonObject note = new JsonObject(); note.addProperty("source", cells.get(3)); note.addProperty("translation", after);
            reviewed.add(row.id() + pointer, note); notes.add("localization_reviews", reviewed);
            changes.add(new Change(row, pointer, before, after));
        }
        return new Import(List.copyOf(changes), Map.copyOf(baselines), Map.copyOf(definitions), Map.copyOf(metadata));
    }

    private static JsonElement blank(JsonElement value) {
        JsonObject wrapper = new JsonObject(); wrapper.add("value", value.deepCopy());
        text(value).keySet().forEach(path -> JsonPointers.set(wrapper, "/value" + path, new JsonPrimitive("")));
        return wrapper.get("value");
    }

    private static JsonObject emptyFile(JsonObject source, String language) {
        JsonObject result = source.deepCopy();
        if (source.has("messages") && source.get("messages").isJsonObject()) result.add("messages", new JsonObject());
        else result.keySet().removeIf(key -> !List.of("schema", "metadata", "locale").contains(key));
        if (result.has("locale")) result.addProperty("locale", language);
        return result;
    }
    private static String prefix(JsonObject root) { return root.has("messages") && root.get("messages").isJsonObject() ? "/messages" : ""; }
    private static JsonObject messages(JsonObject root) { return prefix(root).isEmpty() ? root : root.getAsJsonObject("messages"); }
    private static JsonObject reviews(JsonObject metadata) {
        return metadata.has("localization_reviews") && metadata.get("localization_reviews").isJsonObject()
                ? metadata.getAsJsonObject("localization_reviews") : new JsonObject();
    }
    private static String string(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonPrimitive() ? value.get(key).getAsString() : ""; }
}
