package com.jvn.villagerretaliation.authoring.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class EditorResourceCatalog {
    record Entry(String id, String title) {
        String namespace() {
            int colon = id.indexOf(':');
            return colon < 0 ? "" : id.substring(0, colon);
        }
    }

    static List<String> namespaces(List<Entry> entries) {
        return entries.stream().map(Entry::namespace).filter(value -> !value.isEmpty()).distinct().sorted().toList();
    }

    static List<Entry> matching(List<Entry> entries, String query, String namespace, List<String> favorites, List<String> recent) {
        String term = normalize(query);
        String[] words = term.split("\\s+");
        return entries.stream().filter(entry -> namespace.isEmpty() || namespace.equals(entry.namespace()))
                .filter(entry -> {
                    String text = normalize(entry.id() + " " + entry.title());
                    for (String word : words) if (!text.contains(word)) return false;
                    return true;
                }).sorted(Comparator.<Entry>comparingInt(entry -> {
                    String id = normalize(entry.id());
                    return !term.isEmpty() && (id.equals(term) || id.substring(id.indexOf(':') + 1).equals(term)
                            || normalize(entry.title()).equals(term)) ? 0 : 1;
                }).thenComparingInt(entry -> favorites.contains(entry.id()) ? 0 : recent.contains(entry.id()) ? 1 + recent.indexOf(entry.id()) : Integer.MAX_VALUE)
                        .thenComparing(Entry::title, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id)).toList();
    }

    private static String normalize(String text) { return text.strip().toLowerCase(Locale.ROOT).replace('_', ' '); }
}
