package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignTransactionPredicate(ResourceLocation campaign, ResourceLocation quest, String transaction, State state) {
    public CampaignTransactionPredicate {
        CampaignTransaction.require(campaign != null && quest != null && CampaignFactFields.validId(transaction)
                && transaction.length() <= 256 && state != null, "Invalid campaign transaction condition");
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        var fields = new JsonObject();
        var type = new JsonObject(); type.addProperty("const", "campaign_transaction"); fields.add("type", type);
        for (String field : new String[] {"campaign", "quest"})
            fields.add(field, CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
        fields.add("transaction", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
        fields.add("state", CampaignFieldSchema.enumeration("unarranged", "negotiated", "payment_pending", "paid", "unpaid"));
        schema.add("properties", fields);
        var required = new JsonArray();
        for (String field : new String[] {"campaign", "quest", "transaction", "state"}) required.add(field);
        schema.add("required", required);
        return schema;
    }

    public static Map<String, String> issues(JsonObject object) { return CampaignFieldSchema.issues(object, schema(), "campaign transaction"); }

    public static CampaignTransactionPredicate read(JsonObject object) {
        return issues(object).isEmpty() ? new CampaignTransactionPredicate(ResourceLocation.parse(object.get("campaign").getAsString()),
                ResourceLocation.parse(object.get("quest").getAsString()), object.get("transaction").getAsString(),
                State.valueOf(object.get("state").getAsString().toUpperCase(Locale.ROOT))) : null;
    }

    public Evaluation evaluate(DialogueContext context) {
        if (context == null || context.player() == null) return new Evaluation(false, "Campaign transactions require a player.");
        String issue = CampaignQuestAccess.contentIssue(context.player().server, quest, campaign);
        if (!issue.isBlank()) return new Evaluation(false, issue);
        var content = CampaignResources.current(context.player().server).campaigns().get(campaign).definition();
        var definition = content.transactions().get(transaction);
        if (definition == null || !definition.quest().equals(quest)) return new Evaluation(false, "Unknown transaction for this campaign quest.");
        var data = CampaignTransactionSavedData.get(context.level());
        if (!data.diagnostic().isBlank()) return new Evaluation(false, data.diagnostic());
        var instance = CampaignSavedData.get(context.level()).instances().get(campaign);
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest);
        if (instance == null || progress == null || progress.questRunId() == null) return new Evaluation(false, "");
        var owner = VillagerQuestResources.quest(context.player().server, quest).orElseThrow();
        issue = CampaignQuestAccess.contentIssue(context.level(), owner, progress);
        if (!issue.isBlank()) return new Evaluation(false, issue);
        var key = new CampaignTransaction.Key(campaign, instance.instanceId(), context.player().getUUID(), quest, progress.questRunId(), transaction);
        var receipt = data.receipts().get(key.operation());
        if (!data.issue(key.operation()).isBlank()) return new Evaluation(false, data.issue(key.operation()));
        if (receipt == null && definition.bargainedCount() != null) {
            var conversations = ConversationSavedData.get(context.level());
            if (!conversations.diagnostic().isBlank()) return new Evaluation(false, conversations.diagnostic());
            if (conversations.hasTransaction(key.operation())) return new Evaluation(false, "Restore the missing transaction referenced by this player's saved conversation.");
        }
        if (receipt != null && (receipt.terms().definitionRevision() != content.revision() || !receipt.terms().fingerprint().equals(content.fingerprint())))
            return new Evaluation(false, "Saved transaction terms changed. Restore matching campaign content.");
        return new Evaluation(switch (state) {
            case UNARRANGED -> receipt == null;
            case NEGOTIATED -> receipt != null && receipt.negotiation() != null;
            case PAYMENT_PENDING -> receipt != null && receipt.phase() == CampaignTransaction.Phase.PAYMENT_PENDING;
            case PAID -> receipt != null && receipt.phase() == CampaignTransaction.Phase.PAID;
            case UNPAID -> receipt == null || receipt.phase() != CampaignTransaction.Phase.PAID;
        }, "");
    }

    public enum State { UNARRANGED, NEGOTIATED, PAYMENT_PENDING, PAID, UNPAID }
    public record Evaluation(boolean matches, String diagnostic) { }
}
