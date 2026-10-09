package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition.Scope;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignStandingPredicate(ResourceLocation campaign, Scope scope, String subject,
        CampaignFactPredicate.Comparison comparison, Integer value, String reaction) {
    public CampaignStandingPredicate {
        CampaignStanding.require(campaign != null && scope != null && CampaignStanding.id(subject) && reaction != null
                && (value != null ? comparison != null && reaction.isEmpty() : comparison == null && CampaignStanding.id(reaction)),
                "Invalid campaign standing condition");
    }
    public static JsonObject schema() { return CampaignStandingFields.schema(false); }
    public static Map<String, String> issues(JsonObject object) { return CampaignStandingFields.issues(object, false); }
    public static CampaignStandingPredicate read(JsonObject object) {
        if (!issues(object).isEmpty()) return null;
        boolean numeric = object.has("value");
        return new CampaignStandingPredicate(ResourceLocation.parse(object.get("campaign").getAsString()),
                Scope.valueOf(object.get("scope").getAsString().toUpperCase(Locale.ROOT)), object.get("subject").getAsString(),
                numeric ? object.has("comparison") ? CampaignFactPredicate.Comparison.valueOf(object.get("comparison").getAsString().toUpperCase(Locale.ROOT))
                        : CampaignFactPredicate.Comparison.EQUALS : null,
                numeric ? object.get("value").getAsInt() : null, numeric ? "" : object.get("reaction").getAsString());
    }
    public Evaluation evaluate(DialogueContext context) {
        if (context == null || context.player() == null) return new Evaluation(false, "Campaign standing requires a player.");
        var result = CampaignStandingService.read(context.player().server, campaign, context.player().getUUID(), scope, subject);
        if (!result.diagnostic().isBlank()) return new Evaluation(false, result.diagnostic());
        if (value == null) {
            if (!result.definition().reactions().containsKey(reaction)) return new Evaluation(false, "Unknown standing reaction: " + reaction);
            return new Evaluation(result.reaction().id().equals(reaction), "");
        }
        return new Evaluation(switch (comparison) {
            case EQUALS -> result.value() == value;
            case NOT_EQUALS -> result.value() != value;
            case AT_LEAST -> result.value() >= value;
            case AT_MOST -> result.value() <= value;
        }, "");
    }
    public record Evaluation(boolean matches, String diagnostic) { }
}
