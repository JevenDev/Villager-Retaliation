package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class EditorCampaignGraph {
    record Node(EditorContentBrowser.Entry content, String category, boolean supporting) {
        AuthoringDocument document() { return content.document(); }
        String path() { return content.path(); }
        String title() { return content.title(); }
        String scope() { return content.groups().getFirst().key(); }
        boolean coverable() { return document().kind() != DefinitionKind.RESOURCE && !document().kind().campaignManifest(); }
    }
    record Link(Node from, Node to, String sourcePointer, String targetPointer) { }
    record Position(float x, float y) { }
    record Snapshot(List<Node> nodes, List<Link> links, Map<Node, List<Link>> incoming, Map<Node, List<Link>> outgoing) { }
    record Filter(String query, String scope, boolean supporting, boolean orphaned, boolean untested, Node nearby) { }
    private EditorContentBrowser.Snapshot source;
    private Snapshot snapshot;

    Snapshot get(AuthoringWorkspace workspace, EditorContentBrowser browser, String locale) {
        var current = browser.get(workspace, locale);
        if (source == current) return snapshot;
        var nodes = new ArrayList<Node>();
        var byDocument = new HashMap<AuthoringDocument, Node>();
        for (var entry : current.entries()) {
            String category = entry.groups().size() > 1 ? entry.groups().get(1).label() : EditorContentBrowser.kindLabel(entry.document().kind());
            var node = new Node(entry, category, entry.document().kind() == DefinitionKind.RESOURCE
                    && !List.of("Scenes", "Encounters", "Scenarios", "Story arcs").contains(category));
            nodes.add(node); byDocument.put(entry.document(), node);
        }
        var links = new ArrayList<Link>();
        Map<Node, List<Link>> incoming = new HashMap<>(), outgoing = new HashMap<>();
        nodes.forEach(node -> { incoming.put(node, new ArrayList<>()); outgoing.put(node, new ArrayList<>()); });
        for (var reference : EditorReferences.all(workspace, locale)) {
            var from = byDocument.get(reference.source().document());
            var to = byDocument.get(reference.target().document());
            if (from == null || to == null || from == to) continue;
            var link = new Link(from, to, reference.source().pointer(), reference.target().pointer());
            links.add(link); outgoing.get(from).add(link); incoming.get(to).add(link);
        }
        incoming.replaceAll((node, list) -> List.copyOf(list));
        outgoing.replaceAll((node, list) -> List.copyOf(list));
        snapshot = new Snapshot(List.copyOf(nodes), List.copyOf(links), Map.copyOf(incoming), Map.copyOf(outgoing));
        source = current;
        return snapshot;
    }

    static List<Node> filter(Snapshot graph, Filter filter, EditorCoverage coverage) {
        String[] terms = filter.query().strip().toLowerCase(Locale.ROOT).split("\\s+");
        Set<Node> nearby = new HashSet<>();
        if (filter.nearby() != null) {
            nearby.add(filter.nearby());
            graph.incoming().getOrDefault(filter.nearby(), List.of()).forEach(link -> nearby.add(link.from()));
            graph.outgoing().getOrDefault(filter.nearby(), List.of()).forEach(link -> nearby.add(link.to()));
        }
        return graph.nodes().stream().filter(node -> filter.supporting() || !node.supporting())
                .filter(node -> filter.scope().isEmpty() || node.scope().equals(filter.scope()))
                .filter(node -> !filter.orphaned() || graph.incoming().get(node).isEmpty())
                .filter(node -> !filter.untested() || node.coverable() && !recorded(node, coverage))
                .filter(node -> filter.nearby() == null || nearby.contains(node))
                .filter(node -> {
                    for (String term : terms) if (!node.content().search().contains(term)) return false;
                    return true;
                }).toList();
    }

    static boolean recorded(Node node, EditorCoverage coverage) {
        if (!node.coverable()) return false;
        var value = coverage.snapshot(node.document());
        return !value.nodes().isEmpty() || !value.choices().isEmpty();
    }

    static Map<Node, Position> arrange(Snapshot graph, List<Node> visible) {
        Set<Node> included = new HashSet<>(visible);
        Map<Node, Integer> depth = new LinkedHashMap<>();
        var queue = new ArrayDeque<Node>();
        for (var node : visible) if (graph.incoming().get(node).stream().noneMatch(link -> included.contains(link.from()))) {
            depth.put(node, 0); queue.add(node);
        }
        for (var seed : visible) {
            if (queue.isEmpty() && !depth.containsKey(seed)) { depth.put(seed, 0); queue.add(seed); }
            while (!queue.isEmpty()) {
                var node = queue.remove();
                for (var link : graph.outgoing().get(node)) if (included.contains(link.to()) && !depth.containsKey(link.to())) {
                    depth.put(link.to(), depth.get(node) + 1); queue.add(link.to());
                }
            }
        }
        var rows = new HashMap<Integer, Integer>();
        var positions = new LinkedHashMap<Node, Position>();
        for (var node : visible) {
            int column = depth.get(node), row = rows.getOrDefault(column, 0);
            positions.put(node, new Position(column * 300, row * 104));
            rows.put(column, row + 1);
        }
        return Map.copyOf(positions);
    }
}
