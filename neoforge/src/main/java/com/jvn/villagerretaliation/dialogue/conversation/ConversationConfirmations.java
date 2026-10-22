package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

final class ConversationConfirmations {
    private ConversationConfirmations() { }

    static boolean prepare(ServerPlayer player, ConversationSession session, List<ConversationDefinition.Effect> effects, UUID choice) throws IOException {
        var context = ConversationActors.context(player, ConversationService.character(player, session));
        if (session.questRun != null) ConversationService.require(session.questRun.equals(
                ConversationService.questRun(player.server, session.actingPlayer, session.identity.quest())),
                "The original quest run changed. Repair this conversation before reviewing its remaining effects.");
        if (!(context instanceof DialogueContext legacy)) {
            ConversationService.require(session.identity.campaign() == null, "Campaign consequences require their campaign actor adapter.");
            return false;
        }
        var prepared = CampaignQuestAcceptanceService.prepareActions(legacy, session.actingPlayer,
                effects.stream().map(ConversationDefinition.Effect::action).toList());
        ConversationService.require(prepared.diagnostic().isBlank(), prepared.diagnostic());
        if (prepared.confirmation() == null) return false;
        ConversationService.require(session.identity.campaign() != null, "Campaign consequences require a campaign conversation.");
        session.confirmation = prepared.confirmation();
        session.pendingChoice = choice;
        session.phase = ConversationSession.Phase.CONFIRMATION;
        return true;
    }

    static String confirm(ServerPlayer player, ConversationSession session, ConversationDefinition definition, String id) {
        if (session.nestedDecision != null) return ConversationNestedConfirmations.confirm(player, session, id);
        requirePending(session, id);
        var context = ConversationActors.context(player, ConversationService.character(player, session));
        if (!(context instanceof DialogueContext legacy))
            return "Campaign confirmation requires its campaign actor adapter.";
        var access = CampaignQuestAcceptanceService.confirmationAccess(legacy, session.actingPlayer, session.confirmation);
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
        if (choice == null && !continuesDecision(session)) ConversationService.captureDecisionParticipants(player, session, session.confirmation.id());
        session.confirmation = null;
        session.pendingChoice = null;
        if (choice == null) session.phase = ConversationSession.Phase.NODE_EFFECTS;
        else ConversationService.selectResponse(player, session, choice, session.response);
        return "";
    }

    static boolean continuesDecision(ConversationSession session) {
        return session.decisionParticipants != null && session.decisionParticipants.player().equals(session.actingPlayer);
    }

    static void decline(ServerPlayer player, ConversationSession session, String id) {
        if (session.nestedDecision != null) { ConversationNestedConfirmations.decline(player, session, id); return; }
        requirePending(session, id);
        boolean entry = session.pendingChoice == null;
        discard(session);
        if (entry) session.controller = null;
    }

    static void discard(ConversationSession session) {
        session.phase = session.pendingChoice == null ? ConversationSession.Phase.NODE_EFFECTS : ConversationSession.Phase.RESPONSES;
        if (session.pendingChoice != null) session.responseTimerResolved = false;
        session.confirmation = null;
        session.pendingChoice = null;
        session.response = "";
    }

    private static void requirePending(ConversationSession session, String id) {
        ConversationService.require(session.phase == ConversationSession.Phase.CONFIRMATION && session.confirmation != null
                && session.confirmation.id().toString().equals(id), "This confirmation changed. Review the current consequences.");
    }
}
