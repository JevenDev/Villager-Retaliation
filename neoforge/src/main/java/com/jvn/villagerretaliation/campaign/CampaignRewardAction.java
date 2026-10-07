package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CampaignRewardAction(String reward, String operationId) {
    public CampaignRewardAction {
        if (!CampaignFactFields.validId(reward) || !CampaignFactFields.validId(operationId)) throw new IllegalArgumentException("Campaign rewards require stable reward and operation IDs.");
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        var fields = new JsonObject();
        var required = new JsonArray();
        for (String key : new String[] {"reward", "operation_id"}) {
            fields.add(key, CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
            required.add(key);
        }
        var flag = new JsonObject();
        flag.addProperty("const", true);
        fields.add("required", flag);
        schema.add("properties", fields);
        schema.add("required", required);
        return schema;
    }

    public static Map<String, String> issues(JsonObject object) { return CampaignFieldSchema.issues(object, schema(), "campaign reward"); }

    public static CampaignRewardAction read(JsonObject object) {
        return issues(object).isEmpty() ? new CampaignRewardAction(object.get("reward").getAsString(), object.get("operation_id").getAsString()) : null;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Reward", reward);
        tag.putString("Operation", operationId);
        return tag;
    }

    public static CampaignRewardAction load(CompoundTag tag) {
        if (!tag.contains("Reward", Tag.TAG_STRING) || !tag.contains("Operation", Tag.TAG_STRING)) throw new IllegalArgumentException("Unreadable campaign reward action.");
        return new CampaignRewardAction(tag.getString("Reward"), tag.getString("Operation"));
    }
}
