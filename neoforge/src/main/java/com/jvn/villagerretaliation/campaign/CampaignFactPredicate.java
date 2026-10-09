package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignFactPredicate(ResourceLocation campaign, String fact, Comparison comparison, String value) {
    public CampaignFactPredicate {
        if (campaign == null || !CampaignFactFields.validId(fact) || comparison == null || value == null || value.length() > 4096) {
            throw new IllegalArgumentException("Invalid campaign fact condition.");
        }
    }

    public static JsonObject schema() { return CampaignFactFields.schema(false); }
    public static Map<String, String> issues(JsonObject object) { return CampaignFactFields.issues(object, false); }

    public static CampaignFactPredicate read(JsonObject object) {
        if (!issues(object).isEmpty()) return null;
        return new CampaignFactPredicate(ResourceLocation.parse(object.get("campaign").getAsString()), object.get("fact").getAsString(),
                object.has("comparison") ? Comparison.valueOf(object.get("comparison").getAsString().toUpperCase(Locale.ROOT)) : Comparison.EQUALS,
                object.get("value").getAsString());
    }

    public Evaluation evaluate(DialogueContext context) {
        return evaluate(context == null ? null : context.level(), context == null || context.player() == null ? null : context.player().getUUID());
    }

    public Evaluation evaluate(net.minecraft.server.level.ServerLevel level, java.util.UUID playerId) {
        if (level == null || playerId == null || CampaignWorldModeSavedData.get(level).mode() != CampaignWorldMode.CAMPAIGN) {
            return new Evaluation(false, "Campaign Mode is required for this story condition.");
        }
        var content = CampaignResources.current(level.getServer()).campaigns().get(campaign);
        if (content == null) return new Evaluation(false, "Campaign content is missing or invalid: " + campaign);
        var declared = content.definition().facts().get(fact);
        if (declared == null) return new Evaluation(false, "Unknown campaign fact: " + fact);
        CampaignFacts.Value operand;
        try {
            operand = new CampaignFacts.Value(declared.kind(), value);
        } catch (IllegalArgumentException exception) {
            return new Evaluation(false, exception.getMessage());
        }
        if ((comparison == Comparison.AT_LEAST || comparison == Comparison.AT_MOST) && declared.kind() != CampaignDefinition.FactKind.COUNTER) {
            return new Evaluation(false, "Numeric campaign fact comparisons require a counter: " + fact);
        }
        var result = CampaignSavedData.get(level).readFact(content.definition(), playerId, fact);
        if (!result.diagnostic().isBlank()) return new Evaluation(false, result.diagnostic());
        if (result.value() == null) return new Evaluation(false, "");
        boolean matches = switch (comparison) {
            case EQUALS -> result.value().equals(operand);
            case NOT_EQUALS -> !result.value().equals(operand);
            case AT_LEAST -> Long.parseLong(result.value().text()) >= Long.parseLong(operand.text());
            case AT_MOST -> Long.parseLong(result.value().text()) <= Long.parseLong(operand.text());
        };
        return new Evaluation(matches, "");
    }

    public enum Comparison { EQUALS, NOT_EQUALS, AT_LEAST, AT_MOST }
    public record Evaluation(boolean matches, String diagnostic) {
    }
}
