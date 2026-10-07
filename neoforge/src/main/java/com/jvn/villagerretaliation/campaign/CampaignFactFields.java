package com.jvn.villagerretaliation.campaign;

import static com.jvn.villagerretaliation.campaign.CampaignFieldSchema.enumeration;
import static com.jvn.villagerretaliation.campaign.CampaignFieldSchema.string;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;

final class CampaignFactFields {
    private CampaignFactFields() {
    }

    static JsonObject schema(boolean action) {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        var fields = new JsonObject();
        fields.add("fact", string(CampaignOutcomeAction.ID_PATTERN, 256));
        fields.add("value", string("", 4096));
        var required = new JsonArray();
        required.add("fact");
        required.add("value");
        if (action) {
            fields.add("milestone", string(CampaignOutcomeAction.ID_PATTERN, 256));
            fields.add("operation_id", string(CampaignOutcomeAction.ID_PATTERN, 256));
            fields.add("mutation", enumeration("set", "add"));
            var requiredFlag = new JsonObject();
            requiredFlag.addProperty("const", true);
            fields.add("required", requiredFlag);
            required.add("operation_id");
        } else {
            var type = new JsonObject();
            type.addProperty("const", "campaign_fact");
            fields.add("type", type);
            schema.addProperty("additionalProperties", false);
            fields.add("campaign", string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
            fields.add("comparison", enumeration("equals", "not_equals", "at_least", "at_most"));
            required.add("campaign");
        }
        schema.add("required", required);
        schema.add("properties", fields);
        return schema;
    }

    static Map<String, String> issues(JsonObject object, boolean action) {
        return CampaignFieldSchema.issues(object, schema(action), action ? "campaign fact" : "campaign fact condition");
    }

    static boolean validId(String id) { return id != null && id.matches(CampaignOutcomeAction.ID_PATTERN); }
}
