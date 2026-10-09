package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record CampaignCompanionPredicate(ResourceLocation campaign, String role, int radius) {
    public static final int DEFAULT_RADIUS = 8;
    public static final int MAX_RADIUS = 32;

    public CampaignCompanionPredicate {
        if (campaign == null || !CampaignFactFields.validId(role) || role.length() > 256 || radius < 1 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Invalid campaign companion condition.");
        }
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("description", "Checks whether a bound, loaded campaign character is nearby and actively recruited into the player's party without casting or spawning.");
        schema.addProperty("additionalProperties", false);
        var fields = new JsonObject();
        var type = new JsonObject(); type.addProperty("const", "campaign_companion");
        fields.add("type", type);
        fields.add("campaign", CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
        fields.add("role", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
        var radius = new JsonObject(); radius.addProperty("type", "integer");
        radius.addProperty("minimum", 1); radius.addProperty("maximum", MAX_RADIUS); radius.addProperty("default", DEFAULT_RADIUS);
        fields.add("radius", radius);
        var required = new JsonArray();
        for (String field : new String[]{"campaign", "role"}) required.add(field);
        schema.add("required", required); schema.add("properties", fields);
        return schema;
    }

    public static Map<String, String> issues(JsonObject object) { return CampaignFieldSchema.issues(object, schema(), "campaign companion condition"); }

    public static CampaignCompanionPredicate read(JsonObject object) {
        if (!issues(object).isEmpty()) return null;
        return new CampaignCompanionPredicate(ResourceLocation.parse(object.get("campaign").getAsString()), object.get("role").getAsString(),
                object.has("radius") ? object.get("radius").getAsInt() : DEFAULT_RADIUS);
    }

    public Evaluation evaluate(DialogueContext context) {
        return evaluate(context == null ? null : context.level(), context == null ? null : context.player());
    }

    public Evaluation evaluate(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null || player.serverLevel() != level)
            return new Evaluation(false, "Campaign companion needs a live player context.");
        var result = CampaignBindingQueries.character(level.getServer(), campaign, role);
        if (!result.diagnostic().isBlank()) return new Evaluation(false, result.diagnostic());
        var character = result.entity();
        boolean matches = result.available() && !character.isRemoved() && character.level() == level
                && character.distanceToSqr(player) <= (double) radius * radius
                && PartyService.arePlayerAndVillagerInSameParty(level, player.getUUID(), character.getUUID())
                && PartyVillagerContractService.isActivePartyVillager(level, character);
        return new Evaluation(matches, "");
    }

    public record Evaluation(boolean matches, String diagnostic) { }
}
