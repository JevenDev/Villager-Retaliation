package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public record EditorPreferences(boolean guideDismissed, int layoutVersion, String workspace) {
    public static final int CURRENT_LAYOUT = 1;

    public static EditorPreferences defaults() { return new EditorPreferences(false, 0, ""); }

    public static EditorPreferences load(Path path) throws IOException {
        if (!Files.exists(path)) return defaults();
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            return new EditorPreferences(json.has("guide_dismissed") && json.get("guide_dismissed").getAsBoolean(),
                    json.has("layout_version") ? json.get("layout_version").getAsInt() : 0,
                    json.has("workspace") ? json.get("workspace").getAsString() : "");
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException exception) {
            throw new IOException("Cannot read editor preferences: " + exception.getMessage(), exception);
        }
    }

    public void save(Path path) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("guide_dismissed", guideDismissed);
        json.addProperty("layout_version", layoutVersion);
        json.addProperty("workspace", workspace);
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
