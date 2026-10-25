package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

final class EditorPackFolder {
    private AuthoringWorkspace owner;
    private record Scan(String stamp, List<AuthoringWorkspace.SourceChange> changes) { }
    private record Version(AuthoringDocument document, long revision) { }
    private CompletableFuture<Scan> scan;
    private List<AuthoringWorkspace.SourceChange> changes = List.of();
    private final ImBoolean review = new ImBoolean(false);
    private String selected = "", error = "";
    private long nextCheck, nextDeepCheck;
    private boolean focusReview;
    private String stamp = "";
    private List<Version> reviewedVersions;
    private Map<String, String> reviewedSupporting = Map.of();
    private List<AuthoringWorkspace.SourceChange> reviewedChanges = List.of();
    private String reviewedPath = "", problem = "", editorPreview = "", diskPreview = "";
    private final java.util.Set<String> conflicts = new java.util.HashSet<>();

    void refresh() { scan = null; nextCheck = 0; nextDeepCheck = 0; stamp = ""; reviewedVersions = null; }

    void render(AuthoringWorkspace workspace, java.util.function.Predicate<String> pending, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable changed) {
        if (owner != workspace) {
            owner = workspace; scan = null; changes = List.of(); review.set(false);
            selected = ""; error = ""; refresh();
        }
        if (!workspace.editsPackFolder()) return;
        poll(workspace);
        ImGui.textWrapped("Editing pack folder: " + workspace.location().getFileName());
        if (ImGui.isItemHovered()) ImGui.setTooltip(workspace.location().toString());
        ImGui.textDisabled("Save updates the pack's files.");
        if (!error.isEmpty()) ImGui.textWrapped(error);
        if (!changes.isEmpty()) {
            if (ImGui.button("Review " + changes.size() + " external change(s)")) { review.set(true); focusReview = true; }
        } else if (error.isEmpty()) ImGui.textDisabled(scan == null ? "Disk files are up to date." : "Checking disk files...");
        if (ImGui.smallButton("Check disk now")) refresh();
        ImGui.separator();
        if (review.get()) review(workspace, pending, run, changed);
    }

    private void poll(AuthoringWorkspace workspace) {
        if (scan != null && scan.isDone()) {
            try {
                Scan result = scan.join();
                stamp = result.stamp();
                if (result.changes() != null) {
                    changes = result.changes();
                    nextDeepCheck = System.nanoTime() + 30_000_000_000L;
                }
                error = "";
                if (changes.stream().noneMatch(change -> change.path().equals(selected)))
                    selected = changes.isEmpty() ? "" : changes.getFirst().path();
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof UncheckedIOException io) cause = io.getCause();
                error = "Could not check the pack folder: " + cause.getMessage();
            }
            scan = null;
            nextCheck = System.nanoTime() + 2_000_000_000L;
        }
        if (scan == null && System.nanoTime() >= nextCheck) {
            String previous = stamp;
            boolean deep = System.nanoTime() >= nextDeepCheck;
            scan = CompletableFuture.supplyAsync(() -> {
                try {
                    String current = workspace.sourceStamp();
                    return new Scan(current, deep || !current.equals(previous) ? workspace.sourceChanges(workspace.scanSource()) : null);
                } catch (IOException exception) { throw new UncheckedIOException(exception); }
            }, net.minecraft.Util.backgroundExecutor());
        }
    }

    private void review(AuthoringWorkspace workspace, java.util.function.Predicate<String> pending, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable changed) {
        var chosen = changes.stream().filter(change -> change.path().equals(selected)).findFirst().orElse(null);
        var versions = workspace.documents().stream().map(document -> new Version(document, document.revision())).toList();
        var supporting = workspace.supportingFileHashes();
        if (!versions.equals(reviewedVersions) || !supporting.equals(reviewedSupporting) || reviewedChanges != changes || !selected.equals(reviewedPath)) {
            reviewedVersions = versions; reviewedSupporting = supporting; reviewedChanges = changes; reviewedPath = selected;
            conflicts.clear(); problem = ""; editorPreview = ""; diskPreview = "";
            try {
                for (var change : changes) if (workspace.sourceConflict(change.path())) conflicts.add(change.path());
                if (chosen != null) {
                    editorPreview = preview(chosen.path(), workspace.filePaths().contains(chosen.path()) ? workspace.readFile(chosen.path()) : null);
                    diskPreview = preview(chosen.path(), chosen.disk());
                }
            } catch (IOException exception) { problem = "Could not read the editor version: " + exception.getMessage(); }
        }
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowSize(Math.min(1050, viewport.getWorkSizeX() - 40), Math.min(700, viewport.getWorkSizeY() - 40), ImGuiCond.FirstUseEver);
        if (focusReview) { ImGui.setNextWindowFocus(); focusReview = false; }
        if (ImGui.begin("External pack changes", review, ImGuiWindowFlags.NoCollapse)) {
            ImGui.textWrapped("Review changes made outside the editor before saving this pack.");
            if (!error.isEmpty()) ImGui.textWrapped(error);
            if (ImGui.button("Refresh review")) refresh();
            if (changes.isEmpty()) ImGui.textWrapped("No unresolved external changes.");
            else {
                ImGui.beginChild("##source_files", Math.min(330, ImGui.getContentRegionAvailX() * .35f), 0, true);
                for (var change : changes) {
                    String label = (conflicts.contains(change.path()) ? "Conflict: " : change.kind() + ": ") + change.path();
                    if (ImGui.selectable(label, change == chosen)) selected = change.path();
                    if (ImGui.isItemHovered()) ImGui.setTooltip(change.path());
                }
                ImGui.endChild();
                ImGui.sameLine();
                ImGui.beginChild("##source_versions", 0, 0, true);
                if (chosen != null) {
                    ImGui.textWrapped(chosen.path());
                    if (conflicts.contains(chosen.path())) ImGui.textWrapped("Both versions changed. Choose which version to keep.");
                    if (!problem.isEmpty()) ImGui.textWrapped(problem);
                    boolean unfinished = pending.test(chosen.path());
                    if (unfinished) ImGui.textWrapped("Apply or discard this file's unfinished text before choosing a version.");
                    ImGui.beginDisabled(unfinished || !problem.isEmpty());
                    if (ImGui.button("Use disk version")) run.accept(() -> {
                        workspace.resolveSourceChange(chosen, true); changed.run(); refresh();
                    });
                    EditorWidgets.sameLineFor("Keep editor version");
                    if (ImGui.button("Keep editor version")) run.accept(() -> {
                        workspace.resolveSourceChange(chosen, false); changed.run(); refresh();
                    });
                    ImGui.endDisabled();
                    ImGui.separator();
                    ImGui.beginChild("##editor_source", (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) * .5f, 0, true);
                    ImGui.textDisabled("Editor version");
                    ImGui.textWrapped(editorPreview);
                    ImGui.endChild();
                    ImGui.sameLine();
                    ImGui.beginChild("##disk_source", 0, 0, true);
                    ImGui.textDisabled("Disk version");
                    ImGui.textWrapped(diskPreview);
                    ImGui.endChild();
                }
                ImGui.endChild();
            }
        }
        ImGui.end();
    }

    private static String preview(String path, byte[] bytes) {
        if (bytes == null) return "(absent)";
        if (!EditorPackFiles.textFile(path)) return bytes.length + " bytes";
        String text = new String(bytes, 0, Math.min(bytes.length, 24_000), StandardCharsets.UTF_8);
        return bytes.length > 24_000 ? text + "\n... preview limited to 24 KB" : text;
    }
}
