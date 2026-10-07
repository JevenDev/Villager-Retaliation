package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CampaignFactAction(String fact, CampaignFacts.Mutation mutation, String value, String operationId, String milestone) {
    public CampaignFactAction {
        if (!CampaignFactFields.validId(fact) || mutation == null || value == null || value.length() > 4096
                || !CampaignFactFields.validId(operationId) || milestone == null
                || !milestone.isEmpty() && !CampaignFactFields.validId(milestone)) throw new IllegalArgumentException("Invalid campaign fact action.");
    }

    public static JsonObject schema() { return CampaignFactFields.schema(true); }
    public static Map<String, String> issues(JsonObject object) { return CampaignFactFields.issues(object, true); }

    public static CampaignFactAction read(JsonObject object) {
        if (!issues(object).isEmpty()) return null;
        return new CampaignFactAction(object.get("fact").getAsString(), object.has("mutation")
                ? CampaignFacts.Mutation.valueOf(object.get("mutation").getAsString().toUpperCase(Locale.ROOT)) : CampaignFacts.Mutation.SET,
                object.get("value").getAsString(), object.get("operation_id").getAsString(), object.has("milestone") ? object.get("milestone").getAsString() : "");
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Fact", fact);
        tag.putString("Mutation", mutation.name());
        tag.putString("Value", value);
        tag.putString("Operation", operationId);
        if (!milestone.isBlank()) tag.putString("Milestone", milestone);
        return tag;
    }

    public static CampaignFactAction load(CompoundTag tag) {
        for (String key : java.util.List.of("Fact", "Mutation", "Value", "Operation")) {
            if (!tag.contains(key, Tag.TAG_STRING)) throw new IllegalArgumentException("Unreadable campaign fact action.");
        }
        if (tag.contains("Milestone") && !tag.contains("Milestone", Tag.TAG_STRING)) throw new IllegalArgumentException("Unreadable story fact milestone.");
        return new CampaignFactAction(tag.getString("Fact"), CampaignFacts.Mutation.valueOf(tag.getString("Mutation")),
                tag.getString("Value"), tag.getString("Operation"), tag.getString("Milestone"));
    }
}
