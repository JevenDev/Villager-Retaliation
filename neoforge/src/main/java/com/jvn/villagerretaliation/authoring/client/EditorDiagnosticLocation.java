package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;

/** Resolves missing-field diagnostics to their nearest editable owner. */
final class EditorDiagnosticLocation {
    private EditorDiagnosticLocation() { }

    record Location(String pointer, String nodeId) { }

    static Location resolve(AuthoringDocument document, String requested) {
        String pointer = requested;
        while (!pointer.isEmpty()) {
            try {
                JsonPointers.get(document.json(), pointer);
                break;
            } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException
                    | ClassCastException | UnsupportedOperationException exception) {
                int separator = pointer.lastIndexOf('/');
                pointer = separator < 0 ? "" : pointer.substring(0, separator);
            }
        }
        String nodeId = "";
        int longest = -1;
        try {
            for (var node : DialogueGraph.nodes(document)) {
                if ((pointer.equals(node.pointer()) || pointer.startsWith(node.pointer() + "/"))
                        && node.pointer().length() > longest) {
                    nodeId = node.id();
                    longest = node.pointer().length();
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException | UnsupportedOperationException exception) {
            // A malformed graph must still allow its JSON fields to be repaired.
        }
        return new Location(pointer, nodeId);
    }
}
