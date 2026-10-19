package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.List;

final class EditorWordingReplacement {
    record Change(String pointer, String before, String after, int occurrences) { }
    record Preview(AuthoringDocument document, JsonObject baseline, String find, String replacement, List<Change> changes) {
        int occurrences() { return changes.stream().mapToInt(Change::occurrences).sum(); }

        void apply() {
            if (!document.json().equals(baseline)) throw new IllegalStateException("This file changed. Preview the replacements again before applying");
            document.edit("Replace wording", root -> changes.forEach(change -> JsonPointers.set(root, change.pointer(), new JsonPrimitive(change.after()))));
        }
    }

    record Batch(List<Preview> files) {
        boolean current(com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace workspace) {
            return files.stream().allMatch(file -> workspace.documents().contains(file.document()) && file.baseline().equals(file.document().json()));
        }

        void apply(com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace workspace) {
            if (!current(workspace)) throw new IllegalStateException("Files changed. Preview the replacements again");
            var replacements = new java.util.LinkedHashMap<AuthoringDocument, JsonObject>();
            for (var file : files) {
                JsonObject changed = file.baseline().deepCopy();
                file.changes().forEach(change -> JsonPointers.set(changed, change.pointer(), new JsonPrimitive(change.after())));
                EditorMutation.validate(workspace, file.document(), changed);
                if (!file.changes().isEmpty()) replacements.put(file.document(), changed);
            }
            workspace.editDocuments("Replace workspace wording", replacements);
        }
    }

    static Batch preview(List<AuthoringDocument> documents, String find, String replacement) {
        return new Batch(documents.stream().distinct().map(document -> preview(document, find, replacement)).toList());
    }

    private EditorWordingReplacement() { }

    static Preview preview(AuthoringDocument document, String find, String replacement) {
        if (find.isBlank()) throw new IllegalArgumentException("Enter wording to find first");
        JsonObject baseline = document.json();
        var changes = new ArrayList<Change>();
        boolean translations = document.resourcePath().contains("/locales/") || document.resourcePath().contains("/lang/");
        collect(baseline, "", translations, find, replacement, changes);
        return new Preview(document, baseline, find, replacement, List.copyOf(changes));
    }

    private static void collect(JsonElement value, String path, boolean wording, String find, String replacement, List<Change> changes) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && wording) {
            String before = value.getAsString();
            String after = before.replace(find, replacement);
            if (before.equals(after)) return;
            int count = 0;
            for (int index = before.indexOf(find); index >= 0; index = before.indexOf(find, index + find.length())) count++;
            changes.add(new Change(path, before, after, count));
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                String field = entry.getKey();
                if (List.of("metadata", "conditions", "actions", "effects").contains(field)) continue;
                collect(entry.getValue(), path + "/" + JsonPointers.escape(field),
                        path.isEmpty() && wording || List.of("text", "line", "label", "title", "description", "lines", "variants").contains(field), find, replacement, changes);
            }
        } else if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++)
                collect(value.getAsJsonArray().get(i), path + "/" + i, wording, find, replacement, changes);
        }
    }
}
