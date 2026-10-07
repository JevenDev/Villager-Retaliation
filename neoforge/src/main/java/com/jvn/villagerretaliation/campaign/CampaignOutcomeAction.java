package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CampaignOutcomeAction(String milestone, String outcome, String operationId) {
    public static final String ID_PATTERN = "[a-z0-9][a-z0-9_./-]{0,255}";

    public CampaignOutcomeAction {
        if (!valid(milestone) || !valid(outcome) || !valid(operationId)) {
            throw new IllegalArgumentException("Campaign outcomes require stable milestone, outcome, and operation IDs.");
        }
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        var required = new com.google.gson.JsonArray();
        var fields = new JsonObject();
        for (String key : java.util.List.of("milestone", "outcome", "operation_id")) {
            required.add(key);
            var field = new JsonObject();
            field.addProperty("type", "string");
            field.addProperty("pattern", "^" + ID_PATTERN + "$");
            fields.add(key, field);
        }
        var requiredFlag = new JsonObject();
        requiredFlag.addProperty("const", true);
        fields.add("required", requiredFlag);
        schema.add("required", required);
        schema.add("properties", fields);
        return schema;
    }

    public static Map<String, String> issues(JsonObject object) {
        var issues = new LinkedHashMap<String, String>();
        for (String key : java.util.List.of("milestone", "outcome", "operation_id")) {
            var value = object.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !valid(value.getAsString())) {
                issues.put(key, "campaign outcome requires a stable " + key + " matching " + ID_PATTERN);
            }
        }
        if (object.has("required") && (!object.get("required").isJsonPrimitive()
                || !object.getAsJsonPrimitive("required").isBoolean() || !object.get("required").getAsBoolean())) {
            issues.put("required", "campaign outcome actions must be required");
        }
        return issues;
    }

    public static CampaignOutcomeAction read(JsonObject object) {
        if (!issues(object).isEmpty()) return null;
        return new CampaignOutcomeAction(object.get("milestone").getAsString(), object.get("outcome").getAsString(),
                object.get("operation_id").getAsString());
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Milestone", milestone);
        tag.putString("Outcome", outcome);
        tag.putString("Operation", operationId);
        return tag;
    }

    public static CampaignOutcomeAction load(CompoundTag tag) {
        if (!tag.contains("Milestone", Tag.TAG_STRING) || !tag.contains("Outcome", Tag.TAG_STRING)
                || !tag.contains("Operation", Tag.TAG_STRING)) throw new IllegalArgumentException("Campaign outcome action is unreadable.");
        return new CampaignOutcomeAction(tag.getString("Milestone"), tag.getString("Outcome"), tag.getString("Operation"));
    }

    private static boolean valid(String value) { return value != null && value.matches(ID_PATTERN); }
}
