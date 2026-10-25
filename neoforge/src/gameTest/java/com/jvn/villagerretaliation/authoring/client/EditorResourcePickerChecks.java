package com.jvn.villagerretaliation.authoring.client;

import java.util.List;

final class EditorResourcePickerChecks {
    static int run() {
        namesAndIdentifiersAreSearchable();
        namespacesKeepDuplicateNamesDistinct();
        exactMatchesBeatFavoritesAndRecents();
        filtersHandleEmptyResults();
        return 4;
    }

    private static final List<EditorResourceCatalog.Entry> ENTRIES = List.of(
            new EditorResourceCatalog.Entry("minecraft:diamond_sword", "Diamond Sword"),
            new EditorResourceCatalog.Entry("other:blade", "Diamond Sword"),
            new EditorResourceCatalog.Entry("minecraft:diamond", "Diamond"),
            new EditorResourceCatalog.Entry("other:polished_diamond", "Polished Diamond"));

    private static void namesAndIdentifiersAreSearchable() {
        check(matches("  DIAMOND sword  ", "").size() == 2, "display names ignore case and surrounding whitespace");
        check(matches("minecraft sword", "").getFirst().id().equals("minecraft:diamond_sword"), "query words combine names and identifiers");
        check(matches("diamond_sword", "").size() == 2, "underscores and spaces match the same wording");
        check(matches("other:blade", "").getFirst().id().equals("other:blade"), "localized labels do not replace stable IDs");
    }

    private static void namespacesKeepDuplicateNamesDistinct() {
        check(EditorResourceCatalog.namespaces(ENTRIES).equals(List.of("minecraft", "other")), "mod filters are unique and sorted");
        var matches = matches("Diamond Sword", "other");
        check(matches.size() == 1 && matches.getFirst().id().equals("other:blade"), "equal display names remain distinct by namespace");
    }

    private static void exactMatchesBeatFavoritesAndRecents() {
        var matches = EditorResourceCatalog.matching(ENTRIES, "diamond", "", List.of("other:blade"), List.of("other:polished_diamond"));
        check(matches.getFirst().id().equals("minecraft:diamond"), "an exact match precedes a favorite with a partial name");
        check(matches.get(1).id().equals("other:blade") && matches.get(2).id().equals("other:polished_diamond"), "favorites precede recent partial matches");
        check(matches("", "").size() == ENTRIES.size(), "an empty query preserves the entire catalog");
    }

    private static void filtersHandleEmptyResults() {
        check(matches("absent", "").isEmpty(), "unmatched queries are empty");
        check(matches("", "missing").isEmpty(), "missing namespaces cannot leak other mods");
        check(EditorResourceCatalog.matching(List.of(), "", "", List.of(), List.of()).isEmpty(), "empty registries remain usable");
    }

    private static List<EditorResourceCatalog.Entry> matches(String query, String namespace) {
        return EditorResourceCatalog.matching(ENTRIES, query, namespace, List.of(), List.of());
    }

    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
