package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignEvidencePredicate(ResourceLocation campaign, String evidence) {
    public CampaignEvidencePredicate {
        if (campaign == null || !CampaignFactFields.validId(evidence) || evidence.length() > 256)
            throw new IllegalArgumentException("Invalid campaign evidence condition.");
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        var fields = new JsonObject();
        var type = new JsonObject(); type.addProperty("const", "campaign_evidence"); fields.add("type", type);
        fields.add("campaign", identifier("[a-z0-9_.-]+:[a-z0-9_./-]+"));
        fields.add("evidence", identifier(CampaignOutcomeAction.ID_PATTERN));
        schema.add("properties", fields);
        var required = new JsonArray(); required.add("campaign"); required.add("evidence"); schema.add("required", required);
        return schema;
    }

    private static JsonObject identifier(String pattern) {
        var field = CampaignFieldSchema.string("", 256);
        field.addProperty("pattern", "^" + pattern + "$(?![\\s\\S])");
        return field;
    }

    public static Map<String, String> issues(JsonObject object) { return CampaignFieldSchema.issues(object, schema(), "campaign evidence condition"); }

    public static CampaignEvidencePredicate read(JsonObject object) {
        return issues(object).isEmpty() ? new CampaignEvidencePredicate(ResourceLocation.parse(object.get("campaign").getAsString()),
                object.get("evidence").getAsString()) : null;
    }

    public Evaluation evaluate(DialogueContext context) {
        return evaluate(context == null ? null : context.level(), context == null ? null : context.player());
    }

    public Evaluation evaluate(net.minecraft.server.level.ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        if (player == null || level == null || !level.getServer().isSameThread())
            return new Evaluation(false, "Campaign evidence requires a player on the server thread.");
        if (CampaignWorldModeSavedData.get(level).mode() != CampaignWorldMode.CAMPAIGN)
            return new Evaluation(false, "Campaign Mode is required for this story condition.");
        var content = CampaignResources.current(level.getServer()).campaigns().get(campaign);
        if (content == null) return new Evaluation(false, "Campaign content is missing or invalid: " + campaign);
        if (!content.definition().evidence().containsKey(evidence)) return new Evaluation(false, "Unknown campaign evidence source: " + evidence);
        var campaigns = CampaignSavedData.get(level);
        String issue = campaigns.compatibility(content.definition());
        if (!issue.isBlank()) return new Evaluation(false, issue);
        var data = CampaignEvidenceSavedData.get(level);
        if (!data.diagnostic().isBlank()) return new Evaluation(false, data.diagnostic());
        var instance = campaigns.instances().get(campaign);
        var binding = data.bindings().get(new CampaignEvidenceBinding.Key(campaign, evidence));
        if (binding != null && (instance == null || !binding.instance().equals(instance.instanceId())
                || binding.definitionRevision() != content.definition().revision() || !binding.fingerprint().equals(content.definition().fingerprint())))
            return new Evaluation(false, "Saved evidence belongs to another campaign version or instance. Restore matching campaign and evidence data.");
        return new Evaluation(instance != null && data.discovered(campaign, instance.instanceId(), content.definition().fingerprint(),
                player.getUUID(), evidence), "");
    }

    public record Evaluation(boolean matches, String diagnostic) { }
}
