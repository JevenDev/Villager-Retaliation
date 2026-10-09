package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.LinkedHashMap;
import java.util.Map;

final class CampaignStandingFields {
    private CampaignStandingFields() { }

    static JsonObject schema(boolean action) {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        var properties = new JsonObject();
        properties.add("scope", CampaignFieldSchema.enumeration("character", "faction"));
        properties.add("subject", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
        var required = new JsonArray();
        required.add("scope"); required.add("subject");
        if (action) {
            properties.add("amount", number());
            properties.add("operation_id", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
            var flag = new JsonObject(); flag.addProperty("const", true); properties.add("required", flag);
            required.add("amount"); required.add("operation_id");
        } else {
            schema.addProperty("additionalProperties", false);
            var type = new JsonObject(); type.addProperty("const", "campaign_standing"); properties.add("type", type);
            properties.add("campaign", CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
            properties.add("comparison", CampaignFieldSchema.enumeration("equals", "not_equals", "at_least", "at_most"));
            properties.add("value", number());
            properties.add("reaction", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
            required.add("campaign");
            var alternatives = new JsonArray();
            var numeric = requires("value");
            numeric.add("not", requires("reaction"));
            alternatives.add(numeric);
            var reaction = requires("reaction");
            var excluded = new JsonObject();
            var exclusions = new JsonArray(); exclusions.add(requires("value")); exclusions.add(requires("comparison"));
            excluded.add("anyOf", exclusions);
            reaction.add("not", excluded);
            alternatives.add(reaction);
            schema.add("oneOf", alternatives);
        }
        schema.add("required", required);
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject requires(String name) {
        var value = new JsonObject();
        var fields = new JsonArray(); fields.add(name); value.add("required", fields);
        return value;
    }

    private static JsonObject number() {
        var value = new JsonObject();
        value.addProperty("type", "integer"); value.addProperty("minimum", Integer.MIN_VALUE); value.addProperty("maximum", Integer.MAX_VALUE);
        return value;
    }

    static Map<String, String> issues(JsonObject object, boolean action) {
        var issues = new LinkedHashMap<String, String>();
        QuestJsonSchema.validate(object, schema(action)).forEach(issue ->
                issues.put(issue.pointer().startsWith("/") ? issue.pointer().substring(1) : issue.pointer(), issue.message()));
        return issues;
    }
}
