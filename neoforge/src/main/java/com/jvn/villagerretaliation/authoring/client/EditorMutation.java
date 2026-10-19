package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringValidation;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.util.List;

final class EditorMutation {
    private EditorMutation() { }

    static void validate(AuthoringWorkspace workspace, AuthoringDocument document, JsonObject changed) {
        List<AuthoringDocument> originals = workspace == null ? List.of(document) : workspace.documents();
        var before = AuthoringValidation.validate(originals);
        var candidate = new AuthoringDocument(document.kind(), document.resourcePath(), changed);
        var after = AuthoringValidation.validate(originals.stream().map(value -> value == document ? candidate : value).toList());
        var introduced = after.stream().filter(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR && !before.contains(issue)).findFirst();
        if (introduced.isPresent()) throw new IllegalArgumentException(introduced.get().pointer() + ": " + introduced.get().message());
    }
}
