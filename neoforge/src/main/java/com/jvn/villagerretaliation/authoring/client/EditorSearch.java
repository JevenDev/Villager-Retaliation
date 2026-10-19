package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class EditorSearch {
    record Hit(AuthoringDocument document, String pointer, String text) { }

    private EditorSearch() { }

    static List<Hit> find(AuthoringWorkspace workspace, String query, String locale, int limit) {
        if (query.isBlank() || limit <= 0) return List.of();
        String term = query.strip().toLowerCase(Locale.ROOT);
        var hits = new ArrayList<Hit>();
        for (var document : workspace.documents()) {
            int before = hits.size();
            collect(workspace, document, document.json(), "", term, locale, limit, hits);
            if (hits.size() == before && (document.id() + " " + document.resourcePath()).toLowerCase(Locale.ROOT).contains(term))
                hits.add(new Hit(document, "", document.id()));
            if (hits.size() >= limit) break;
        }
        return List.copyOf(hits);
    }

    private static void collect(AuthoringWorkspace workspace, AuthoringDocument document, JsonElement value, String path,
                                String term, String locale, int limit, List<Hit> hits) {
        if (hits.size() >= limit) return;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (text.toLowerCase(Locale.ROOT).contains(term)) hits.add(new Hit(document, path, text));
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("key") && object.get("key").isJsonPrimitive() && object.getAsJsonPrimitive("key").isString()) {
                String text = workspace.resolveText(document, object, locale);
                if (text.toLowerCase(Locale.ROOT).contains(term)) {
                    hits.add(new Hit(document, path, text));
                    return;
                }
            }
            for (var entry : object.entrySet()) {
                String childPath = path + "/" + JsonPointers.escape(entry.getKey());
                int before = hits.size();
                collect(workspace, document, entry.getValue(), childPath, term, locale, limit, hits);
                if (hits.size() == before && entry.getKey().toLowerCase(Locale.ROOT).contains(term))
                    hits.add(new Hit(document, childPath, entry.getKey()));
                if (hits.size() >= limit) break;
            }
        } else if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size() && hits.size() < limit; i++)
                collect(workspace, document, value.getAsJsonArray().get(i), path + "/" + i, term, locale, limit, hits);
        }
    }

    static String snippet(String text, String query) {
        String line = text.replace('\n', ' ').replace('\r', ' ');
        int match = line.toLowerCase(Locale.ROOT).indexOf(query.strip().toLowerCase(Locale.ROOT));
        int start = Math.max(0, match - 40);
        int end = Math.min(line.length(), start + 160);
        return (start > 0 ? "..." : "") + line.substring(start, end) + (end < line.length() ? "..." : "");
    }

    static List<String> files(AuthoringWorkspace workspace, String query, List<String> recent) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        var documents = workspace.documents().stream().collect(java.util.stream.Collectors.toMap(AuthoringDocument::resourcePath, AuthoringDocument::id));
        var paths = workspace.filePaths().stream().filter(path -> (path + " " + documents.getOrDefault(path, "")).toLowerCase(Locale.ROOT).contains(term));
        if (term.isEmpty()) return paths.sorted(Comparator.<String>comparingInt(path -> recent.contains(path) ? recent.indexOf(path) : Integer.MAX_VALUE).thenComparing(Comparator.naturalOrder())).toList();
        return paths.sorted(Comparator.<String>comparingInt(path -> path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT).startsWith(term)
                || documents.getOrDefault(path, "").toLowerCase(Locale.ROOT).startsWith(term) ? 0 : 1).thenComparing(Comparator.naturalOrder())).toList();
    }
}
