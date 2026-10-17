package com.jvn.villagerretaliation.quest.schema.v3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

public final class QuestV3Format {
    private QuestV3Format() { }

    public static boolean isV3(JsonObject quest) {
        return quest != null && quest.has("schema") && quest.get("schema").isJsonPrimitive()
                && QuestV3Schema.ID.equals(quest.get("schema").getAsString());
    }

    public static JsonObject runtime(JsonObject source) {
        if (!isV3(source)) throw new IllegalArgumentException("Quest V3 is required. Run tools/migrate-quest-v2-to-v3.mjs on this pack.");
        JsonObject quest = source.deepCopy();
        quest.remove("world_campaign");
        JsonObject metadata = object(quest, "metadata");
        move(quest, "story_arc", metadata, "campaign");
        move(quest, "revision", metadata, "revision");
        move(quest, "migration", metadata, "migration");
        lowerDisplay(quest);
        if (quest.has("ui")) {
            JsonObject display = quest.getAsJsonObject("ui");
            for (String name : List.of("title", "title_key", "description", "description_key", "show_locked_adventure_hint")) move(display, name, metadata, name);
            move(display, "tracker_title", display, "title");
            move(display, "tracker_title_key", display, "title_key");
        }
        if (quest.has("stages")) for (JsonElement element : quest.getAsJsonArray("stages")) {
            JsonObject stage = element.getAsJsonObject();
            lowerDisplay(stage);
            if (stage.has("ui")) {
                JsonObject display = stage.getAsJsonObject("ui");
                for (String name : List.of("title", "title_key", "description", "description_key")) move(display, name, stage, name);
                move(display, "tracker_title", display, "title");
                move(display, "tracker_title_key", display, "title_key");
            }
            move(stage, "transition", stage, "next");
            if (stage.has("next") && stage.get("next").isJsonObject()) {
                JsonObject next = stage.getAsJsonObject("next");
                if (next.size() == 1 && next.has("stage")) stage.add("next", next.get("stage"));
            }
            if (stage.has("objectives")) for (JsonElement objective : stage.getAsJsonArray("objectives")) {
                JsonObject value = objective.getAsJsonObject();
                lowerDisplay(value);
                if (value.has("ui")) {
                    JsonObject display = value.getAsJsonObject("ui");
                    move(display, "tracker_title", display, "title");
                    move(display, "tracker_title_key", display, "title_key");
                }
            }
        }
        return quest;
    }

    private static void lowerDisplay(JsonObject owner) {
        move(owner, "display", owner, "ui");
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (!parent.has(name)) parent.add(name, new JsonObject());
        return parent.getAsJsonObject(name);
    }

    private static void move(JsonObject from, String name, JsonObject to, String target) {
        if (from.has(name)) to.add(target, from.remove(name));
    }
}
