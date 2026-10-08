package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

final class ConversationConfirmations {
    private ConversationConfirmations() { }

    static boolean prepare(ServerPlayer player, ConversationSession session, List<ConversationDefinition.Effect> effects, UUID choice) throws IOException {
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, ConversationService.character(player, session));
        var prepared = CampaignQuestAcceptanceService.prepareActions(context, effects.stream().map(ConversationDefinition.Effect::action).toList());
        ConversationService.require(prepared.diagnostic().isBlank(), prepared.diagnostic());
        if (prepared.confirmation() == null) return false;
        session.confirmation = prepared.confirmation();
        session.pendingChoice = choice;
        session.phase = ConversationSession.Phase.CONFIRMATION;
        session.actingPlayer = player.getUUID();
        session.questRun = ConversationService.questRun(player, session.identity.quest());
        return true;
    }

    static String confirm(ServerPlayer player, ConversationSession session, ConversationDefinition definition, String id) {
        requirePending(session, id);
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, ConversationService.character(player, session));
        var access = CampaignQuestAcceptanceService.confirmationAccess(context, session.confirmation);
        if (!access.allowed()) {
            if (!access.paused()) {
                boolean entry = session.pendingChoice == null;
                discard(session);
                if (entry) session.controller = null;
            }
            return access.diagnostic();
        }
        if (session.pendingChoice != null) {
            var response = definition.nodes().get(session.node).responses().get(session.response);
            if (response == null || !ConversationService.conditions(context, response.conditions())) {
                discard(session);
                return "This response is no longer available. Review the current choices.";
            }
        }
        session.consent = session.confirmation.acceptedConsent(player.serverLevel().getGameTime());
        UUID choice = session.pendingChoice;
        if (choice == null) ConversationService.captureDecisionParticipants(player, session, session.confirmation.id());
        session.confirmation = null;
        session.pendingChoice = null;
        if (choice == null) session.phase = ConversationSession.Phase.NODE_EFFECTS;
        else ConversationService.selectResponse(player, session, choice, session.response);
        return "";
    }

    static void decline(ConversationSession session, String id) {
        requirePending(session, id);
        boolean entry = session.pendingChoice == null;
        discard(session);
        if (entry) session.controller = null;
    }

    static void discard(ConversationSession session) {
        session.phase = session.pendingChoice == null ? ConversationSession.Phase.NODE_EFFECTS : ConversationSession.Phase.RESPONSES;
        session.confirmation = null;
        session.pendingChoice = null;
        session.response = "";
    }

    private static void requirePending(ConversationSession session, String id) {
        ConversationService.require(session.phase == ConversationSession.Phase.CONFIRMATION && session.confirmation != null
                && session.confirmation.id().toString().equals(id), "This confirmation changed. Review the current consequences.");
    }
}
