package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CampaignScenarioSchema {
    public static final String SCHEMA_ID = "villagerretaliation:campaign_scenario/v1";
    private static final String ID = "^[a-z0-9][a-z0-9_./-]*$";
    private static final String RESOURCE = "^[a-z0-9_.-]+:[a-z0-9_./-]+$";

    private CampaignScenarioSchema() { }

    public static JsonObject export() {
        var questSymbol = QuestV2Schema.export().getAsJsonObject("properties").getAsJsonObject("entry_stage");
        var steps = new JsonArray();
        steps.add(step("player", "player action", "player", local(), "action", choices("connect", "disconnect")));
        steps.add(step("party", "leader member", "leader", local(), "member", local(), "admin", bool()));
        steps.add(step("privilege", "leader member enabled", "leader", local(), "member", local(), "enabled", bool()));
        steps.add(step("quest_prompt", "player actor quest stage slot", "player", local(), "actor", local(), "quest", resource(),
                "stage", questSymbol, "slot", choices("offer", "reminder", "turn_in")));
        steps.add(step("quest_select", "prompt response", "prompt", local(), "response", questSymbol, "confirm", bool()));
        steps.add(step("quest_response", "player actor quest stage slot response", "player", local(), "actor", local(),
                "quest", resource(), "stage", questSymbol, "slot", choices("offer", "reminder", "turn_in"),
                "response", questSymbol, "confirm", bool()));
        steps.add(step("quest_confirmation", "player accept", "player", local(), "accept", bool()));
        steps.add(step("conversation_start", "player session conversation topic", "player", local(), "session", local(),
                "conversation", resource(), "topic", local(), "accepted", bool()));
        steps.add(step("conversation_intent", "player session intent", "player", local(), "session", local(),
                "intent", choices("advance", "choose", "roll", "resume", "join", "take_control", "hand_over", "release_control", "confirm", "decline"),
                "argument", local(), "revision_from", local(), "seed", integer(Integer.MIN_VALUE, Integer.MAX_VALUE), "accepted", bool()));
        steps.add(step("replay", "request", "request", local(), "accepted", bool()));
        steps.add(step("interrupt", "player actor active", "player", local(), "actor", local(), "active", bool()));
        steps.add(step("fixture_entry", "player entry", "player", local(), "entry", local(), "accepted", bool()));
        steps.add(step("world_event", "event", "event", choices("minecraft:ender_dragon_defeated")));
        steps.add(step("storage", "store available", "store", choices("campaign", "conversation"), "available", bool()));
        steps.add(step("reload", ""));
        steps.add(step("wait", "ticks", "ticks", integer(1, 1200)));
        steps.add(step("checkpoint", "scope", "scope", choices("campaign", "conversation", "player"), "target", local()));
        steps.add(step("assert_same", "checkpoint", "checkpoint", local()));
        steps.add(step("assert_outcome", "milestone outcome", "milestone", local(), "outcome", local(),
                "origin", choices("player_decision", "world_observation", "operator_fixture")));
        steps.add(step("assert_player", "player", "player", local(), "experience", integer(0, Integer.MAX_VALUE),
                "participation", CampaignSchema.array(local(), 0)));
        steps.add(step("assert_quest", "player quest", "player", local(), "quest", resource(), "stage", questSymbol,
                "completions", integer(0, Integer.MAX_VALUE)));
        steps.add(step("assert_conversation", "session", "session", local(), "node", local(),
                "phase", choices("node_effects", "exchanges", "responses", "response_effects", "check", "rolled", "complete", "confirmation"),
                "controller", local(), "outcome", local(), "choices", integer(0, 1024),
                "checks", integer(0, 1024), "receipts", integer(0, 65536)));
        steps.add(step("assert_check", "session check", "session", local(), "check", local(), "die", integer(1, 20), "success", bool()));
        steps.add(step("assert_campaign", "", "outcomes", integer(0, 1024), "audits", integer(0, 1024), "rewards", integer(0, 65536)));
        steps.add(step("assert_standing", "player scope subject value", "player", local(), "scope", choices("character", "faction"),
                "subject", local(), "value", integer(Integer.MIN_VALUE, Integer.MAX_VALUE), "reaction", local()));
        var stepSchema = new JsonObject();
        stepSchema.add("oneOf", steps);
        var position = CampaignSchema.object(List.of("x", "y", "z"),
                "x", integer(-32, 32), "y", integer(0, 32), "z", integer(-32, 32));
        var actor = CampaignSchema.object(List.of("id", "position"), "id", local(), "role", local(), "position", position);
        var location = CampaignSchema.object(List.of("alias", "position"), "alias", local(), "position", position);
        var schema = CampaignSchema.object(List.of("schema", "id", "campaign", "players", "steps"),
                "schema", choices(SCHEMA_ID), "id", resource(), "campaign", resource(),
                "players", boundedArray(local(), 1, 16), "actors", boundedArray(actor, 0, 64),
                "locations", boundedArray(location, 0, 64), "steps", boundedArray(stepSchema, 1, 1024));
        schema.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.addProperty("$id", "https://villagerretaliation.dev/schema/campaign-scenario-v1.schema.json");
        schema.addProperty("title", "Villager Retaliation Campaign Scenario v1");
        return schema;
    }

    private static JsonObject step(String type, String required, Object... fields) {
        var names = new ArrayList<>(List.of("id", "type"));
        if (!required.isBlank()) Collections.addAll(names, required.split(" "));
        var values = new ArrayList<Object>(List.of("id", local(), "type", choices(type)));
        Collections.addAll(values, fields);
        return CampaignSchema.object(names, values.toArray());
    }

    private static JsonObject local() { return CampaignSchema.text(ID); }
    private static JsonObject resource() { return CampaignSchema.text(RESOURCE); }
    private static JsonObject bool() { return CampaignSchema.type("boolean"); }
    private static JsonObject integer(long minimum, long maximum) {
        var result = CampaignSchema.type("integer");
        result.addProperty("minimum", minimum);
        result.addProperty("maximum", maximum);
        return result;
    }
    private static JsonObject choices(String... values) {
        var result = CampaignSchema.type("string");
        var options = new JsonArray();
        for (String value : values) options.add(value);
        result.add("enum", options);
        return result;
    }
    private static JsonObject boundedArray(JsonObject items, int minimum, int maximum) {
        var result = CampaignSchema.array(items, minimum);
        result.addProperty("maxItems", maximum);
        return result;
    }
}
