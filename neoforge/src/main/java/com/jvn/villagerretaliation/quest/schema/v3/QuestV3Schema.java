package com.jvn.villagerretaliation.quest.schema.v3;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.ContentSchemaDefinitions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class QuestV3Schema {
    public static final String ID = "villagerretaliation:quest/v3";
    public static final Path TOOLING_SCHEMA_PATH = Path.of("tools", "datapack-builder", "quest-v3.schema.json");

    private QuestV3Schema() { }

    public static JsonObject export() {
        JsonObject root = ContentSchemaDefinitions.export();
        root.addProperty("$id", "https://villagerretaliation.dev/schema/quest-v3.schema.json");
        root.addProperty("title", "Villager Retaliation Quest V3");
        JsonObject properties = root.getAsJsonObject("properties");
        properties.getAsJsonObject("schema").addProperty("const", ID);
        JsonObject definitions = root.getAsJsonObject("$defs");
        JsonObject metadata = fields(definitions, "metadata");
        for (String name : List.of("revision", "migration")) properties.add(name, metadata.remove(name));
        properties.add("story_arc", metadata.remove("campaign"));
        properties.add("world_campaign", properties.get("story_arc").deepCopy());
        properties.add("display", properties.remove("ui"));
        properties.getAsJsonObject("display").addProperty("$ref", "#/$defs/display");
        definitions.add("display", definitions.remove("ui"));
        JsonObject display = fields(definitions, "display");
        display.add("show_locked_adventure_hint", metadata.remove("show_locked_adventure_hint"));
        for (String name : List.of("title", "title_key", "description", "description_key")) metadata.remove(name);
        removeKeyAliases(display);
        display.add("tracker_title", display.get("title").deepCopy());
        for (String kind : List.of("stage", "objective")) {
            JsonObject fields = fields(definitions, kind);
            fields.remove("ui");
            JsonObject reference = new JsonObject();
            reference.addProperty("$ref", "#/$defs/display");
            fields.add("display", reference);
        }
        JsonObject stage = fields(definitions, "stage");
        for (String name : List.of("title", "title_key", "description", "description_key", "entry_actions", "exit_actions", "completion_mode", "completion_count", "next")) stage.remove(name);
        stage.add("transition", reference("transition"));
        fields(definitions, "provider").remove("capabilities");
        fields(definitions, "migration").remove("on_active_change");
        for (String kind : List.of("scene", "response", "event")) fields(definitions, kind).remove("next");
        for (String name : List.of("stage", "scene", "response", "complete", "abandon", "fail")) fields(definitions, "response").remove(name);
        fields(definitions, "event").remove("trigger");
        fields(definitions, "event").remove("type");
        return root;
    }

    private static JsonObject reference(String name) {
        JsonObject result = new JsonObject();
        result.addProperty("$ref", "#/$defs/" + name);
        return result;
    }

    private static JsonObject fields(JsonObject definitions, String name) {
        return definitions.getAsJsonObject(name).getAsJsonObject("properties");
    }

    private static void removeKeyAliases(JsonObject fields) {
        for (String name : List.copyOf(fields.keySet())) if (name.endsWith("_key")) fields.remove(name);
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0 ? TOOLING_SCHEMA_PATH : Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(export()) + "\n");
    }
}
