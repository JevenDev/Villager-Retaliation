package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition.Scope;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CampaignStandingAction(Scope scope, String subject, int amount, String operationId) {
    public CampaignStandingAction {
        CampaignStanding.require(scope != null && CampaignStanding.id(subject) && CampaignStanding.id(operationId), "Invalid campaign standing action");
    }
    public static JsonObject schema() { return CampaignStandingFields.schema(true); }
    public static Map<String, String> issues(JsonObject object) { return CampaignStandingFields.issues(object, true); }
    public static CampaignStandingAction read(JsonObject object) {
        if (!issues(object).isEmpty()) return null;
        return new CampaignStandingAction(Scope.valueOf(object.get("scope").getAsString().toUpperCase(Locale.ROOT)),
                object.get("subject").getAsString(), object.get("amount").getAsInt(), object.get("operation_id").getAsString());
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Scope", scope.name()); tag.putString("Subject", subject);
        tag.putInt("Amount", amount); tag.putString("OperationId", operationId);
        return tag;
    }
    public static CampaignStandingAction load(CompoundTag tag) {
        CampaignStanding.require(tag.contains("Scope", Tag.TAG_STRING) && tag.contains("Subject", Tag.TAG_STRING)
                && tag.contains("Amount", Tag.TAG_INT) && tag.contains("OperationId", Tag.TAG_STRING), "Unreadable campaign standing action");
        return new CampaignStandingAction(Scope.valueOf(tag.getString("Scope")), tag.getString("Subject"), tag.getInt("Amount"), tag.getString("OperationId"));
    }
}
