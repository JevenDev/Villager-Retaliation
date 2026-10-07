package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignLocationPredicate(ResourceLocation campaign, String location, int radius) {
    public static final int DEFAULT_RADIUS = 12;
    public static final int MAX_RADIUS = 256;

    public CampaignLocationPredicate {
        if (campaign == null || !CampaignFactFields.validId(location) || location.length() > 256 || radius < 1 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Invalid campaign location condition.");
        }
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("description", "Checks a player's distance from an already-bound campaign destination.");
        schema.addProperty("additionalProperties", false);
        var fields = new JsonObject();
        var type = new JsonObject(); type.addProperty("const", "campaign_location");
        fields.add("type", type);
        fields.add("campaign", CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
        fields.add("location", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
        var radius = new JsonObject(); radius.addProperty("type", "integer");
        radius.addProperty("minimum", 1); radius.addProperty("maximum", MAX_RADIUS); radius.addProperty("default", DEFAULT_RADIUS);
        fields.add("radius", radius);
        var required = new JsonArray();
        for (String field : new String[]{"campaign", "location"}) required.add(field);
        schema.add("required", required); schema.add("properties", fields);
        return schema;
    }

    public static Map<String, String> issues(JsonObject object) { return CampaignFieldSchema.issues(object, schema(), "campaign location condition"); }

    public static CampaignLocationPredicate read(JsonObject object) {
        if (!issues(object).isEmpty()) return null;
        return new CampaignLocationPredicate(ResourceLocation.parse(object.get("campaign").getAsString()), object.get("location").getAsString(),
                object.has("radius") ? object.get("radius").getAsInt() : DEFAULT_RADIUS);
    }

    public Evaluation evaluate(DialogueContext context) {
        if (context == null || context.level() == null || context.player() == null) return new Evaluation(false, "Campaign location needs a live player context.");
        var result = CampaignBindingQueries.location(context.level().getServer(), campaign, location);
        if (!result.diagnostic().isBlank()) return new Evaluation(false, result.diagnostic());
        var binding = result.binding();
        if (binding == null) return new Evaluation(false, "Campaign location has not been bound yet. Resolve its authored destination before continuing.");
        boolean matches = binding.dimension().equals(context.player().level().dimension().location())
                && binding.position().distToCenterSqr(context.player().position()) <= (double) radius * radius;
        return new Evaluation(matches, "");
    }

    public record Evaluation(boolean matches, String diagnostic) { }
}
