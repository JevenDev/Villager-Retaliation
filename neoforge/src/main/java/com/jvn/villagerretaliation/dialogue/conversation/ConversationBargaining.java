package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.campaign.CampaignAuthority;
import com.jvn.villagerretaliation.campaign.CampaignTransaction;
import com.jvn.villagerretaliation.campaign.CampaignTransactionSavedData;
import com.jvn.villagerretaliation.campaign.CampaignTransactions;
import com.jvn.villagerretaliation.skill.player.PlayerSocialAward;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class ConversationBargaining {
    private ConversationBargaining() { }

    static void roll(ServerPlayer controller, ConversationSavedData data, ConversationSession session,
            ConversationDefinition.Check definition, UUID request) throws IOException {
        var buyer = controller.server.getPlayerList().getPlayer(session.actingPlayer);
        ConversationService.require(buyer != null && ConversationService.member(buyer, session), "The player who chose this bargain must reconnect to continue.");
        String authority = CampaignAuthority.controlIssue(controller, buyer.getUUID());
        ConversationService.require(authority.isBlank(), authority);
        var character = ConversationService.character(controller, session);
        ConversationService.require(ConversationService.near(buyer, character), "The buyer must return to the seller before bargaining.");
        ConversationService.require(session.questRun != null && session.questRun.equals(ConversationService.questRun(buyer, session.identity.quest())),
                "The buyer's original quest run changed. Repair this conversation before bargaining.");
        var runtime = ConversationActors.context(buyer, character);
        ConversationService.require(runtime instanceof DialogueContext,
                "Campaign bargaining requires its campaign actor adapter.");
        var context = (DialogueContext) runtime;
        var reading = CampaignTransactions.read(context, session.identity.quest(), definition.transaction());
        ConversationService.require(reading.available(), reading.diagnostic());
        var source = new CampaignTransaction.Source(session.identity.id(), session.identity.conversation(),
                session.identity.definitionRevision(), session.identity.fingerprint(), definition.id());
        try {
            var receipt = CampaignTransactionSavedData.get(controller.serverLevel()).negotiate(reading.terms(), source, () -> {
                var social = ConversationSocialProgression.capture(buyer, data, definition);
                var modifiers = ConversationCheckModifier.capture(context, definition);
                return ConversationSession.Check.roll(request, definition, buyer.getRandom().nextInt(20) + 1,
                        buyer.getUUID(), buyer.serverLevel().getGameTime(), social, modifiers);
            }, buyer.serverLevel().getGameTime());
            session.checks.put(definition.id(), receipt.negotiation().forRequest(request));
            session.transactionChecks.put(definition.id(), receipt.operation());
            session.replacements.putAll(receipt.replacements(buyer.getName().getString()));
        } catch (IllegalArgumentException exception) {
            throw new ConversationService.Unavailable(exception.getMessage());
        }
    }

    static String paymentIssue(DialogueRuntimeContext context, ConversationSession session,
            net.minecraft.resources.ResourceLocation quest, String transaction) {
        try {
            ConversationService.definition(context.player().server, session);
            if (java.util.Objects.equals(quest, session.identity.quest())) for (var operation : session.transactionChecks.values()) {
                var receipt = CampaignTransactionSavedData.get(context.level()).receipts().get(operation);
                if (receipt != null && transaction.equals(receipt.terms().key().transaction())
                        && !context.player().getUUID().equals(receipt.terms().key().player()))
                    return "This bargain belongs to the player who chose it. Hand control back to that buyer before choosing payment.";
            }
            return "";
        } catch (ConversationService.Unavailable | IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }

    static PlayerSocialAward experienceAward(MinecraftServer server, ConversationSession session, ConversationSession.Check check) {
        if (!session.transactionChecks.containsKey(check.id()))
            return new PlayerSocialAward(session.identity.id(), check.id(), check.request(), check.player(), check.social());
        return receipt(server, session, check).negotiation().experienceAward();
    }

    static void validate(MinecraftServer server, ConversationSession session, ConversationDefinition definition) {
        for (var check : session.checks.values()) {
            var authored = definition.checks().get(check.id());
            if (authored == null) continue;
            boolean linked = session.transactionChecks.containsKey(check.id());
            ConversationService.require(linked == !authored.transaction().isEmpty(), "The saved check is missing its transaction identity. Restore matching conversation data.");
            if (!linked) continue;
            try {
                var receipt = receipt(server, session, check);
                ConversationService.require(receipt.terms().key().transaction().equals(authored.transaction()),
                        "The saved bargain belongs to another transaction. Restore matching conversation data.");
            } catch (IllegalArgumentException exception) {
                throw new ConversationService.Unavailable(exception.getMessage());
            }
        }
    }

    private static CampaignTransaction.Receipt receipt(MinecraftServer server, ConversationSession session, ConversationSession.Check check) {
        var data = CampaignTransactionSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) throw new IllegalArgumentException(data.diagnostic());
        var receipt = data.receipts().get(session.transactionChecks.get(check.id()));
        var identity = session.identity;
        var source = new CampaignTransaction.Source(identity.id(), identity.conversation(), identity.definitionRevision(), identity.fingerprint(), check.id());
        if (receipt == null || receipt.negotiation() == null || !receipt.negotiation().source().compatible(source)
                || !receipt.negotiation().matches(check) || !receipt.terms().key().campaign().equals(identity.campaign())
                || !receipt.terms().key().instance().equals(identity.campaignInstance()) || !receipt.terms().key().quest().equals(identity.quest())
                || !receipt.terms().key().player().equals(check.player()) || !receipt.terms().character().equals(identity.characterRole()))
            throw new IllegalArgumentException("The saved bargain is missing or does not match this conversation. Restore its transaction data.");
        return receipt;
    }
}
