package com.jvn.villagerretaliation.quest.schema.v2;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.ContentSchemaDefinitions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public final class QuestV2Schema {
    public static final Path TOOLING_SCHEMA_PATH = Path.of("tools", "schemas", "quest-v2.schema.json");

    private QuestV2Schema() { }

    public static JsonObject export() {
        return ContentSchemaDefinitions.export();
    }

    public static Set<String> acceptedKeys(String definition, String... compatibilityFields) {
        return ContentSchemaDefinitions.acceptedKeys(definition, compatibilityFields);
    }

    public static String exportJson() {
        return ContentSchemaDefinitions.exportJson();
    }

    public static void write(Path output) throws IOException {
        ContentSchemaDefinitions.write(output);
    }
}
