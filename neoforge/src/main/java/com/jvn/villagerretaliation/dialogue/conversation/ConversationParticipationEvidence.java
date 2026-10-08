package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import static com.jvn.villagerretaliation.dialogue.conversation.ConversationSessionCodec.*;

record ConversationParticipationEvidence(String effect, CampaignDecisionParticipants participants, UUID controller) {
    ConversationParticipationEvidence {
        require(effect != null && !effect.isBlank() && effect.length() <= 1024 && participants != null && controller != null,
                "nested participation has no saved decision identity");
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putString("Effect", effect);
        tag.put("Participants", participants.save());
        tag.putUUID("Controller", controller);
        return tag;
    }

    static ConversationParticipationEvidence load(CompoundTag tag) {
        require(integer(tag, "Version") == 1, "unsupported nested participation version");
        return new ConversationParticipationEvidence(text(tag, "Effect"), CampaignDecisionParticipants.load(compound(tag, "Participants")),
                uuid(tag, "Controller"));
    }

    void validate(ConversationSession session) {
        var identity = session.identity;
        var receipt = session.receipts.get(effect);
        require(participants.conversation().equals(identity.id()) && participants.campaign().equals(identity.campaign())
                && participants.instance().equals(identity.campaignInstance()) && receipt != null && receipt.actor().equals(participants.player())
                && session.participants.containsAll(participants.participants()) && session.participants.contains(controller),
                "nested participation belongs to another conversation effect or player");
        require(session.requests.values().stream().anyMatch(request -> request.intent() == ConversationSession.Intent.CONFIRM
                && request.player().equals(controller) && request.argument().equals(participants.decision().toString())),
                "nested participation has no confirming request receipt");
    }

    static void migrate(CompoundTag session) {
        var entries = new ListTag();
        if (session.contains("NestedDecision")) {
            var nested = ConversationNestedDecision.load(compound(session, "NestedDecision"));
            if (nested.state() == ConversationNestedDecision.State.APPROVED) {
                var previous = compound(session, "DecisionParticipants");
                UUID actor = uuid(session, "ActingPlayer");
                var witnesses = previous.isEmpty() ? Set.of(actor) : CampaignDecisionParticipants.load(previous).participants();
                var participants = new CampaignDecisionParticipants(uuid(session, "Id"), nested.confirmation().id(),
                        resource(session, "Campaign"), uuid(session, "CampaignInstance"), actor, witnesses);
                entries.add(new ConversationParticipationEvidence(nested.origin().effect(), participants, nested.resolver()).save());
            }
        }
        session.put("NestedParticipation", entries);
    }
}
