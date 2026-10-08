package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public record CampaignOutcomePredicate(ResourceLocation campaign, String milestone, String outcome, Scope scope) {
    public CampaignOutcomePredicate {
        if (campaign == null || !CampaignFactFields.validId(milestone) || !CampaignFactFields.validId(outcome) || scope == null) {
            throw new IllegalArgumentException("Invalid campaign outcome condition.");
        }
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        var fields = new JsonObject();
        var type = new JsonObject();
        type.addProperty("const", "campaign_outcome");
        fields.add("type", type);
        fields.add("campaign", CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
        fields.add("milestone", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
        fields.add("outcome", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
        fields.add("scope", CampaignFieldSchema.enumeration("world", "discovered", "participated", "completed"));
        var required = new JsonArray();
        for (String field : new String[] {"campaign", "milestone", "outcome", "scope"}) required.add(field);
        schema.add("required", required);
        schema.add("properties", fields);
        return schema;
    }

    public static Map<String, String> issues(JsonObject object) {
        return CampaignFieldSchema.issues(object, schema(), "campaign outcome condition");
    }

    public static CampaignOutcomePredicate read(JsonObject object) {
        if (!issues(object).isEmpty()) return null;
        return new CampaignOutcomePredicate(ResourceLocation.parse(object.get("campaign").getAsString()),
                object.get("milestone").getAsString(), object.get("outcome").getAsString(),
                Scope.valueOf(object.get("scope").getAsString().toUpperCase(Locale.ROOT)));
    }

    public Evaluation evaluate(DialogueContext context) {
        return evaluate(context == null ? null : context.level(), context == null || context.player() == null ? null : context.player().getUUID());
    }

    public Evaluation evaluate(net.minecraft.server.level.ServerLevel level, UUID playerId) {
        if (level == null || playerId == null || CampaignWorldModeSavedData.get(level).mode() != CampaignWorldMode.CAMPAIGN) {
            return new Evaluation(false, "Campaign Mode is required for this story condition.");
        }
        var content = CampaignResources.current(level.getServer()).campaigns().get(campaign);
        if (content == null) return new Evaluation(false, "Campaign content is missing or invalid: " + campaign);
        var data = CampaignSavedData.get(level);
        String issue = data.compatibility(content.definition());
        if (!issue.isBlank()) return new Evaluation(false, issue);
        return evaluate(content.definition(), data.instances().get(campaign), playerId);
    }

    Evaluation evaluate(CampaignDefinition definition, CampaignInstance instance, UUID playerId) {
        var declared = definition.milestones().get(milestone);
        if (declared == null) return new Evaluation(false, "Unknown campaign milestone: " + milestone);
        if (!declared.outcomes().containsKey(outcome)) return new Evaluation(false, "Unknown outcome for " + milestone + ": " + outcome);
        if (!declared.irreversible() && scope != Scope.COMPLETED) {
            return new Evaluation(false, "World outcomes, discovery and participation require an irreversible milestone: " + milestone);
        }
        if (instance == null) return new Evaluation(false, "");
        var player = instance.players().getOrDefault(playerId, CampaignInstance.PlayerRecord.empty());
        var shared = instance.outcomes().get(milestone);
        var progress = player.milestones().get(milestone);
        boolean matches = switch (scope) {
            case WORLD -> shared != null && outcome.equals(shared.outcome());
            case DISCOVERED -> outcome.equals(player.discoveredOutcomes().get(milestone));
            case PARTICIPATED -> shared != null && outcome.equals(shared.outcome()) && player.participation().contains(milestone);
            case COMPLETED -> progress != null && progress.completed() && outcome.equals(progress.outcome());
        };
        return new Evaluation(matches, "");
    }

    public enum Scope { WORLD, DISCOVERED, PARTICIPATED, COMPLETED }
    public record Evaluation(boolean matches, String diagnostic) {
    }
}
