package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class EditorContentBrowser {
    record Group(String key, String label, int order) { }
    record Entry(AuthoringDocument document, String title, String id, String path, List<Group> groups, String search, String payload) { }
    record Snapshot(List<Entry> entries, int quests, int dialogue) { }
    record Row(String key, String label, int depth, Entry entry, int count) { }
    record View(List<Row> rows, int matches, int total) { }
    private record Source(AuthoringDocument document, long revision, String path, JsonObject json, String payload) { }
    private final Set<String> expanded = new HashSet<>();
    private AuthoringWorkspace owner;
    private List<Source> sources = List.of();
    private String language = "";
    private Snapshot snapshot;
    private View view;
    private String query = "", kind = "";
    private boolean supporting, grouped;
    private AuthoringDocument revealed;

    Snapshot get(AuthoringWorkspace workspace, String locale) {
        var documents = workspace.documents();
        boolean current = owner == workspace && language.equals(locale) && sources.size() == documents.size();
        for (int i = 0; current && i < documents.size(); i++)
            current = sources.get(i).document() == documents.get(i) && sources.get(i).revision() == documents.get(i).revision();
        if (current) return snapshot;
        var previous = new HashMap<AuthoringDocument, Source>();
        if (owner == workspace) sources.forEach(source -> previous.put(source.document(), source));
        sources = documents.stream().map(document -> {
            var saved = previous.get(document);
            if (saved != null && saved.revision() == document.revision()) return saved;
            var json = document.json();
            String payload = saved != null && saved.json().equals(json) ? saved.payload() : json.toString().toLowerCase(Locale.ROOT);
            return new Source(document, document.revision(), document.resourcePath(), json, payload);
        }).toList();
        var roots = new HashMap<String, JsonObject>();
        sources.forEach(source -> roots.put(source.path(), source.json()));
        var entries = new ArrayList<Entry>();
        for (var source : sources) {
            String id = source.document().id(), path = source.path();
            String title = title(source, roots, locale);
            String search = (title + " " + id + " " + path).toLowerCase(Locale.ROOT);
            entries.add(new Entry(source.document(), title, id, path, groups(source, roots), search, source.payload()));
        }
        entries.sort(Comparator.comparing(Entry::title, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::path));
        snapshot = new Snapshot(List.copyOf(entries), (int) documents.stream().filter(document -> document.kind() == DefinitionKind.QUEST).count(),
                (int) documents.stream().filter(document -> List.of(DefinitionKind.TREE, DefinitionKind.CONVERSATION, DefinitionKind.FORCED).contains(document.kind())).count());
        owner = workspace; language = locale; view = null;
        return snapshot;
    }

    View rows(AuthoringWorkspace workspace, String locale, String search, boolean showSupporting, String contentKind, boolean group, AuthoringDocument selected) {
        get(workspace, locale);
        if (revealed != selected) { revealed = selected; reveal(selected); }
        String term = search.strip().toLowerCase(Locale.ROOT);
        if (view != null && query.equals(term) && supporting == showSupporting && kind.equals(contentKind) && grouped == group) return view;
        var matching = snapshot.entries().stream().filter(entry -> showSupporting || entry.document().kind() != DefinitionKind.RESOURCE)
                .filter(entry -> contentKind.equals("All") || kindLabel(entry.document().kind()).equals(contentKind))
                .filter(entry -> term.isEmpty() || (entry.search().contains(term) || entry.payload().contains(term))).toList();
        var rows = new ArrayList<Row>();
        if (!group) matching.forEach(entry -> rows.add(new Row(entry.path(), entry.title(), 0, entry, 1)));
        else {
            var root = new Branch(null);
            for (var entry : matching) {
                var branch = root;
                for (var parent : entry.groups()) branch = branch.children.computeIfAbsent(parent.key(), ignored -> new Branch(parent));
                branch.entries.add(entry);
            }
            flatten(root, 0, !term.isEmpty(), rows);
        }
        query = term; supporting = showSupporting; kind = contentKind; grouped = group;
        view = new View(List.copyOf(rows), matching.size(), snapshot.entries().size());
        return view;
    }

    void toggle(String key) { if (!expanded.add(key)) expanded.remove(key); view = null; }
    boolean expanded(String key) { return expanded.contains(key) || !query.isEmpty(); }
    void collapse() { expanded.clear(); view = null; }
    void reveal(AuthoringDocument selected) {
        if (snapshot == null || selected == null) return;
        snapshot.entries().stream().filter(entry -> entry.document() == selected).findFirst()
                .ifPresent(entry -> entry.groups().forEach(group -> expanded.add(group.key())));
        view = null;
    }

    private void flatten(Branch branch, int depth, boolean searching, List<Row> rows) {
        branch.children.values().stream().sorted(Comparator.comparingInt((Branch child) -> child.group.order()).thenComparing(child -> child.group.label()))
                .forEach(child -> {
                    rows.add(new Row(child.group.key(), child.group.label(), depth, null, child.count()));
                    if (searching || expanded.contains(child.group.key())) flatten(child, depth + 1, searching, rows);
                });
        for (var entry : branch.entries) rows.add(new Row(entry.path(), entry.title(), depth, entry, 1));
    }

    private static final class Branch {
        final Group group;
        final Map<String, Branch> children = new LinkedHashMap<>();
        final List<Entry> entries = new ArrayList<>();
        Branch(Group group) { this.group = group; }
        int count() { return entries.size() + children.values().stream().mapToInt(Branch::count).sum(); }
    }

    private static List<Group> groups(Source source, Map<String, JsonObject> roots) {
        String path = source.path();
        String[] parts = path.split("/", 5);
        String scope = "default", label = "Default content";
        if (parts.length == 5 && (parts[2].equals("world_campaigns") || parts[2].equals("campaign_addons"))) {
            scope = parts[2] + "/" + parts[1] + ":" + parts[3];
            String manifest = "data/" + parts[1] + "/" + parts[2] + "/" + parts[3] + (parts[2].equals("world_campaigns") ? "/campaign.json" : "/addon.json");
            String name = DialogueGraph.string(roots.getOrDefault(manifest, new JsonObject()), "name");
            label = (parts[2].equals("world_campaigns") ? "Campaign: " : "Addon: ") + (name.isBlank() || name.equals(parts[1] + ":" + parts[3]) ? humanize(parts[3]) : name);
        } else {
            String campaign = DialogueGraph.string(source.json(), "world_campaign");
            if (!campaign.isBlank()) {
                scope = campaign;
                label = campaign.equals("villagerretaliation:shared") ? "Shared content" : "Campaign: " + humanize(campaign.substring(campaign.indexOf(':') + 1));
            }
        }
        var result = new ArrayList<Group>();
        String key = "scope/" + scope;
        result.add(new Group(key, label, scope.equals("default") ? 1 : 0));
        if (source.document().kind().campaignManifest()) return List.copyOf(result);
        String category = switch (source.document().kind()) {
            case QUEST -> "Quests";
            case TREE -> "Dialogue trees";
            case CONVERSATION -> "Conversations";
            case FORCED -> "Triggered dialogue";
            default -> path.contains("/locales/") ? "Translations" : path.contains("/scenes/") ? "Scenes"
                    : path.contains("/encounters/") ? "Encounters" : path.contains("/dialogue_words/") ? "Word pools"
                    : path.contains("/dialogue/") ? "Dialogue pools" : path.contains("/campaign_scenarios/") ? "Scenarios"
                    : path.contains("/campaigns/") ? "Story arcs" : "Supporting files";
        };
        key += "/type/" + category;
        result.add(new Group(key, category, source.document().kind() == DefinitionKind.QUEST ? 0 : source.document().kind() == DefinitionKind.RESOURCE ? 2 : 1));
        String bundle = bundle(path);
        String folder = source.document().kind() == DefinitionKind.QUEST ? DialogueGraph.string(EditorSchema.object(source.json().get("metadata")), "questline") : "";
        if (folder.isBlank() && !bundle.isEmpty()) {
            String[] pieces = bundle.split("/");
            folder = pieces[pieces.length - (source.document().kind() == DefinitionKind.QUEST ? 2 : 1)];
        }
        if (!folder.isBlank()) result.add(new Group(key + "/folder/" + folder, humanize(folder), 0));
        return List.copyOf(result);
    }

    private static String title(Source source, Map<String, JsonObject> roots, String locale) {
        var root = source.json();
        String name = DialogueGraph.string(root, "name");
        JsonElement value = EditorSchema.object(root.get("display")).get("title");
        if (value == null) value = root.get("title");
        if (name.isBlank() && value != null) name = text(value, source, roots, locale, new HashSet<>(), 0);
        if (name.isBlank() || name.equals(source.document().id())) {
            String id = source.document().kind() == DefinitionKind.RESOURCE ? source.path().substring(source.path().lastIndexOf('/') + 1) : source.document().id();
            if (source.document().kind() == DefinitionKind.RESOURCE) id = id.replaceFirst("\\.[^.]+$", "");
            name = source.path().contains("/locales/") ? Locale.forLanguageTag(id.replace('_', '-')).getDisplayName(Locale.ENGLISH)
                    : humanize(id.substring(id.lastIndexOf(':') + 1));
        }
        return com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.plainText(
                com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(name, com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE)).replace('\n', ' ');
    }

    private static String text(JsonElement value, Source source, Map<String, JsonObject> roots, String locale, Set<String> visited, int depth) {
        if (value == null || depth > 12) return "";
        if (value.isJsonPrimitive()) return value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
        if (value.isJsonArray()) return value.getAsJsonArray().isEmpty() ? "" : text(value.getAsJsonArray().get(0), source, roots, locale, visited, depth + 1);
        if (!value.isJsonObject()) return "";
        var object = value.getAsJsonObject();
        String key = DialogueGraph.string(object, "key");
        if (!key.isBlank()) {
            String bundle = bundle(source.path());
            if (key.startsWith("#")) key = DialogueGraph.string(roots.getOrDefault(bundle + "quest.json", source.json()), "localization_prefix") + "." + key.substring(1);
            if (!visited.add(key)) return "";
            for (String language : List.of(locale, "en_us")) {
                for (String prefix : List.of(bundle, DefinitionKind.contentRoot(source.path()) + "quests/_shared/")) {
                    var translations = roots.get(prefix + "locales/" + language + ".json");
                    if (translations == null) continue;
                    var payload = translations.has("messages") ? EditorSchema.object(translations.get("messages")).get(key) : translations.get(key);
                    if (payload != null) return text(payload, source, roots, language, visited, depth + 1);
                }
            }
            return "";
        }
        for (String field : List.of("text", "line", "lines", "variants"))
            if (object.has(field)) return text(object.get(field), source, roots, locale, visited, depth + 1);
        return "";
    }

    private static String bundle(String path) {
        int start = path.indexOf("/quests/");
        if (start < 0) return "";
        String[] parts = path.substring(start + 8).split("/", 3);
        return parts.length == 3 && !parts[0].equals("_shared") ? path.substring(0, start + 8) + parts[0] + "/" + parts[1] + "/" : "";
    }

    static String humanize(String value) {
        String[] words = value.replace('_', ' ').replace('-', ' ').replace('/', ' ').strip().split("\\s+");
        var result = new ArrayList<String>();
        for (String word : words) if (!word.isEmpty()) result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        return String.join(" ", result);
    }

    static String kindLabel(DefinitionKind kind) {
        return switch (kind) {
            case TREE -> "Dialogue tree";
            case CONVERSATION -> "Conversation";
            case FORCED -> "Triggered dialogue";
            case QUEST -> "Quest";
            case WORLD_CAMPAIGN -> "World campaign";
            case CAMPAIGN_ADDON -> "Campaign addon";
            case RESOURCE -> "Supporting resource";
        };
    }
}
