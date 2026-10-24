package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.dialogue.DialogueStyle;
import com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources;
import java.util.LinkedHashMap;
import java.util.Map;

final class EditorDialogueStyles {
    private Map<AuthoringDocument, Long> revisions = Map.of();
    private Map<String, DialogueStyle> loaded = Map.of(), styles = Map.of();

    Map<String, DialogueStyle> catalog(AuthoringWorkspace workspace) {
        var current = DialogueStyleResources.current();
        var next = new LinkedHashMap<AuthoringDocument, Long>();
        if (workspace != null) for (var document : workspace.documents())
            if (document.resourcePath().matches("data/[^/]+/dialogue_styles/.+\\.json")) next.put(document, document.revision());
        if (next.equals(revisions) && current == loaded) return styles;
        var result = new LinkedHashMap<>(current);
        next.keySet().forEach(document -> {
            var root = document.json();
            if (com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(root, DialogueStyle.schema()).isEmpty()) {
                try { result.put(root.get("tag").getAsString(), DialogueStyle.read(root)); }
                catch (IllegalArgumentException invalid) { return; }
            }
        });
        revisions = Map.copyOf(next);
        loaded = current;
        styles = Map.copyOf(result);
        return styles;
    }
}
