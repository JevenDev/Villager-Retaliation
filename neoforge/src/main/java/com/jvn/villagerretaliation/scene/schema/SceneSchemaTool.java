package com.jvn.villagerretaliation.scene.schema;

import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SceneSchemaTool {
    private SceneSchemaTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected scene and encounter schema output paths");
        }
        var gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        write(Path.of(args[0]), gson.toJson(SceneSchema.sceneV1()) + System.lineSeparator());
        write(Path.of(args[1]), gson.toJson(SceneSchema.encounterV1()) + System.lineSeparator());
    }

    private static void write(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value, StandardCharsets.UTF_8);
        System.out.println("Wrote " + path);
    }
}
