package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CampaignPaymentAction(String transaction) {
    public CampaignPaymentAction {
        if (!CampaignFactFields.validId(transaction) || transaction.length() > 256)
            throw new IllegalArgumentException("Campaign payments require a stable transaction ID.");
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        var fields = new JsonObject();
        fields.add("transaction", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
        var flag = new JsonObject();
        flag.addProperty("const", true);
        fields.add("required", flag);
        schema.add("properties", fields);
        var required = new JsonArray();
        required.add("transaction");
        schema.add("required", required);
        return schema;
    }

    public static Map<String, String> issues(JsonObject object) { return CampaignFieldSchema.issues(object, schema(), "campaign payment"); }

    public static CampaignPaymentAction read(JsonObject object) {
        return issues(object).isEmpty() ? new CampaignPaymentAction(object.get("transaction").getAsString()) : null;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Transaction", transaction);
        return tag;
    }

    public static CampaignPaymentAction load(CompoundTag tag) {
        if (!tag.contains("Transaction", Tag.TAG_STRING)) throw new IllegalArgumentException("Unreadable campaign payment action.");
        return new CampaignPaymentAction(tag.getString("Transaction"));
    }
}
