package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class EditorDiagnostics {
    private EditorDiagnostics() { }

    static List<AuthoringDiagnostic> matching(List<AuthoringDiagnostic> diagnostics, boolean errors, boolean warnings, String query) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        return diagnostics.stream().filter(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR ? errors : warnings)
                .filter(issue -> (issue.resourcePath() + " " + issue.pointer() + " " + issue.message()).toLowerCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparing(AuthoringDiagnostic::resourcePath).thenComparing(AuthoringDiagnostic::severity)
                        .thenComparing(AuthoringDiagnostic::pointer).thenComparing(AuthoringDiagnostic::message)).toList();
    }

    static AuthoringDiagnostic next(List<AuthoringDiagnostic> visible, AuthoringDiagnostic current, int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Choose previous or next issue");
        if (visible.isEmpty()) return null;
        int index = visible.indexOf(current);
        if (index < 0) return direction < 0 ? visible.getLast() : visible.getFirst();
        return visible.get(Math.floorMod(index + direction, visible.size()));
    }
}
