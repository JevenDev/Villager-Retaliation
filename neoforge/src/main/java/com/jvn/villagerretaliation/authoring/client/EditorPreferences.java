package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public record EditorPreferences(boolean guideDismissed, int layoutVersion, String workspace,
        java.util.List<String> recentWorkspaces, String exportFolder, String locale, float textScale) {
    public EditorPreferences(boolean guideDismissed, int layoutVersion, String workspace) {
        this(guideDismissed, layoutVersion, workspace, workspace.isBlank() ? java.util.List.of() : java.util.List.of(workspace), "", "en_us", 1);
    }

    public EditorPreferences withWorkspace(String value) {
        var recent = new java.util.ArrayList<>(recentWorkspaces);
        recent.remove(value);
        recent.addFirst(value);
        return new EditorPreferences(guideDismissed, layoutVersion, value, recent.stream().limit(8).toList(), exportFolder, locale, textScale);
    }

    public EditorPreferences withGuide(boolean value) { return new EditorPreferences(value, layoutVersion, workspace, recentWorkspaces, exportFolder, locale, textScale); }
    public EditorPreferences withLayout(int value) { return new EditorPreferences(guideDismissed, value, workspace, recentWorkspaces, exportFolder, locale, textScale); }
    public EditorPreferences withExport(String value) { return new EditorPreferences(guideDismissed, layoutVersion, workspace, recentWorkspaces, value, locale, textScale); }
    public EditorPreferences withLocale(String value) { return new EditorPreferences(guideDismissed, layoutVersion, workspace, recentWorkspaces, exportFolder, value, textScale); }
    public EditorPreferences withScale(float value) { return new EditorPreferences(guideDismissed, layoutVersion, workspace, recentWorkspaces, exportFolder, locale, Math.clamp(value, .8f, 2f)); }
    public static final int CURRENT_LAYOUT = 1;

    public static EditorPreferences defaults() { return new EditorPreferences(false, 0, ""); }

    public static EditorPreferences load(Path path) throws IOException {
        if (!Files.exists(path)) return defaults();
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            return new EditorPreferences(json.has("guide_dismissed") && json.get("guide_dismissed").getAsBoolean(),
                    json.has("layout_version") ? json.get("layout_version").getAsInt() : 0,
                    json.has("workspace") ? json.get("workspace").getAsString() : "",
                    json.has("recent_workspaces") ? json.getAsJsonArray("recent_workspaces").asList().stream().map(com.google.gson.JsonElement::getAsString).limit(8).toList() : java.util.List.of(),
                    json.has("export_folder") ? json.get("export_folder").getAsString() : "",
                    json.has("locale") ? json.get("locale").getAsString() : "en_us",
                    json.has("text_scale") ? Math.clamp(json.get("text_scale").getAsFloat(), .8f, 2f) : 1);
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException exception) {
            throw new IOException("Cannot read editor preferences: " + exception.getMessage(), exception);
        }
    }

    public void save(Path path) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("guide_dismissed", guideDismissed);
        json.addProperty("layout_version", layoutVersion);
        json.addProperty("workspace", workspace);
        var recent = new com.google.gson.JsonArray();
        recentWorkspaces.forEach(recent::add);
        json.add("recent_workspaces", recent);
        json.addProperty("export_folder", exportFolder);
        json.addProperty("locale", locale);
        json.addProperty("text_scale", textScale);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "authoring-editor-", ".tmp");
        try {
            Files.writeString(temporary, json.toString());
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
