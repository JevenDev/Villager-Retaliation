package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.util.ArrayList;
import java.util.List;

final class EditorNavigation {
    record Location(AuthoringDocument document, String path, int view, boolean filesMode, String pointer,
                    float panX, float panY, float zoom, float scroll) {
        String currentPath() { return document == null ? path : document.resourcePath(); }

        boolean samePlace(Location other) {
            return view == other.view && (document != null ? document == other.document : other.document == null && (view != 4 || path.equals(other.path)));
        }

        boolean exists(AuthoringWorkspace workspace) {
            if (document != null) return workspace.documents().contains(document);
            return view != 4 || path.isEmpty() || workspace.filePaths().contains(path) || workspace.folderPaths().contains(path);
        }

        String label() {
            return switch (view) { case 1 -> "Preview"; case 2 -> "Build pack"; case 3 -> "Playtest"; case 4 -> "Files"; default -> "Edit"; }
                    + (currentPath().isEmpty() || document == null && view != 4 ? "" : ": " + currentPath());
        }
    }

    private AuthoringWorkspace workspace;
    private final List<Location> locations = new ArrayList<>();
    private int cursor = -1;
    private final java.util.Map<AuthoringDocument, Location> documentPositions = new java.util.IdentityHashMap<>();
    private final java.util.Map<String, Location> filePositions = new java.util.HashMap<>();
    private final List<Location> recentFiles = new ArrayList<>();

    void record(AuthoringWorkspace owner, Location next) {
        if (workspace != owner) {
            workspace = owner;
            locations.clear(); cursor = -1;
            documentPositions.clear(); filePositions.clear(); recentFiles.clear();
        }
        if (owner == null || !next.exists(owner)) return;
        if (next.view() == 0 && next.document() != null) documentPositions.put(next.document(), next);
        if (next.view() == 4) filePositions.put(next.currentPath(), next);
        if (!next.currentPath().isEmpty() && owner.filePaths().contains(next.currentPath())) {
            recentFiles.removeIf(location -> next.document() != null ? location.document() == next.document() : location.currentPath().equals(next.currentPath()));
            recentFiles.addFirst(next);
            if (recentFiles.size() > 20) recentFiles.removeLast();
        }
        if (cursor >= 0 && locations.get(cursor).samePlace(next)) { locations.set(cursor, next); return; }
        locations.subList(cursor + 1, locations.size()).clear();
        locations.add(next);
        if (locations.size() > 128) locations.removeFirst();
        cursor = locations.size() - 1;
    }

    Location remembered(AuthoringWorkspace owner, AuthoringDocument document, String path) {
        if (owner != workspace) return null;
        Location location = document == null ? filePositions.get(path) : documentPositions.get(document);
        return location != null && location.exists(owner) ? location : null;
    }

    List<String> recentFiles(AuthoringWorkspace owner) {
        if (owner != workspace) return List.of();
        return recentFiles.stream().filter(location -> location.exists(owner))
                .map(Location::currentPath).filter(owner.filePaths()::contains).distinct().toList();
    }

    Location target(int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Choose back or forward");
        if (workspace == null) return null;
        for (int i = cursor + direction; i >= 0 && i < locations.size(); i += direction) {
            Location candidate = locations.get(i);
            if (candidate.exists(workspace)) return candidate;
        }
        return null;
    }

    void accept(Location target) {
        int index = locations.indexOf(target);
        if (index < 0 || !target.exists(workspace)) throw new IllegalStateException("This editor location is no longer available");
        cursor = index;
    }

    static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    static List<String> ancestors(String path) {
        List<String> result = new ArrayList<>();
        for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) result.add(path.substring(0, slash));
        return List.copyOf(result);
    }
}
