package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import java.util.List;
import java.util.Map;

final class EditorGraphModel {
    record Snapshot(List<EditorGraph.Card> cards, List<DialogueGraph.Node> nodes, java.util.Set<String> ids,
                    List<DialogueGraph.Edge> edges, Map<String, float[]> defaults) {
        void placeMissing(Map<String, float[]> positions) {
            defaults.forEach((id, point) -> positions.computeIfAbsent(id, ignored -> point.clone()));
        }
    }
    private AuthoringWorkspace workspace;
    private AuthoringDocument document;
    private long revision;
    private String locale;
    private List<AuthoringDocument> dependencies = List.of();
    private long[] revisions = new long[0];
    private Snapshot snapshot;

    void clear() {
        workspace = null; document = null; dependencies = List.of(); revisions = new long[0]; snapshot = null;
    }

    Snapshot get(AuthoringWorkspace owner, AuthoringDocument selected, String language) {
        var documents = owner.documents();
        boolean current = workspace == owner && document == selected && revision == selected.revision()
                && java.util.Objects.equals(locale, language) && dependencies.size() == documents.size();
        for (int i = 0; current && i < documents.size(); i++)
            current = dependencies.get(i) == documents.get(i) && revisions[i] == documents.get(i).revision();
        if (current) return snapshot;
        var cards = EditorGraph.cards(selected, value -> owner.resolveText(selected, value, language));
        var nodes = cards.stream().map(EditorGraph.Card::node).toList();
        var next = new Snapshot(cards, nodes, nodes.stream().map(DialogueGraph.Node::id).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                DialogueGraph.edges(selected), EditorGraph.arrange(cards));
        workspace = owner; document = selected; revision = selected.revision(); locale = language;
        dependencies = documents;
        revisions = documents.stream().mapToLong(AuthoringDocument::revision).toArray();
        snapshot = next;
        return snapshot;
    }
}
