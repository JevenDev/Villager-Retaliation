package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

public final class CampaignQuestReference {
    private static final String PATTERN = "^[a-z0-9_.-]+:[a-z0-9_./-]+$";
    private static final int MAX_LENGTH = 256;

    private CampaignQuestReference() {
    }

    public static ResourceLocation read(JsonElement value) {
        if (value == null) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().length() > MAX_LENGTH || !value.getAsString().matches(PATTERN)) {
            throw new IllegalArgumentException("campaign must be a namespaced resource id of at most " + MAX_LENGTH + " characters");
        }
        return ResourceLocation.parse(value.getAsString());
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "string");
        schema.addProperty("pattern", PATTERN);
        schema.addProperty("maxLength", MAX_LENGTH);
        return schema;
    }
}
