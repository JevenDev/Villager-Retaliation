package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class EditorValidationCache {
    private record State(AuthoringDocument document, long revision, String path, JsonObject json) { }
    private AuthoringWorkspace owner;
    private List<State> states = List.of();
    private Map<String, String> supporting = Map.of();
    private List<AuthoringDiagnostic> diagnostics = List.of();

    List<AuthoringDiagnostic> get(AuthoringWorkspace workspace) {
        var documents = workspace.documents();
        var hashes = workspace.supportingFileHashes();
        boolean changed = owner != workspace || documents.size() != states.size() || !supporting.equals(hashes);
        var next = new ArrayList<State>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            var document = documents.get(i);
            var before = owner == workspace && i < states.size() ? states.get(i) : null;
            if (before != null && before.document() == document && before.revision() == document.revision()) next.add(before);
            else {
                var state = new State(document, document.revision(), document.resourcePath(), document.json());
                changed |= before == null || before.document() != document || !before.path().equals(state.path()) || !before.json().equals(state.json());
                next.add(state);
            }
        }
        if (changed) diagnostics = workspace.validate();
        owner = workspace; supporting = hashes; states = List.copyOf(next);
        return diagnostics;
    }
}
