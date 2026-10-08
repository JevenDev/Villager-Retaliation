package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.CampaignConfirmation;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import static com.jvn.villagerretaliation.dialogue.conversation.ConversationSessionCodec.*;

record ConversationNestedDecision(Origin origin, ConversationSession.Phase resumePhase, CampaignConfirmation confirmation,
        State state, UUID resolver, long resolvedAt, String diagnostic) {
    ConversationNestedDecision {
        require(origin != null && confirmation != null && state != null, "nested confirmation has no saved identity");
        require(resumePhase == ConversationSession.Phase.NODE_EFFECTS || resumePhase == ConversationSession.Phase.RESPONSE_EFFECTS,
                "nested confirmation has no suspended execution phase");
        require(state == State.PENDING ? resolver == null && resolvedAt == 0 : resolver != null && resolvedAt >= 0,
                "nested confirmation has an invalid resolution");
        require(diagnostic != null && diagnostic.length() <= 4096, "nested confirmation diagnostic is unreadable");
    }

    ConversationNestedDecision resolved(State next, UUID player, long time, String issue) {
        require(state == State.PENDING && next != State.PENDING, "nested confirmation already has a saved resolution");
        return new ConversationNestedDecision(origin, resumePhase, confirmation, next, player, time, issue);
    }

    ActionResult rejection() {
        var capabilities = VillagerActionRegistry.capabilities(origin.action());
        return state == State.DECLINED ? ActionResult.failed("The shared consequence was declined.", capabilities)
                : ActionResult.unavailable(diagnostic, capabilities, VillagerActionResult.EMPTY);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.put("Origin", origin.save());
        tag.putString("ResumePhase", resumePhase.name());
        tag.put("Confirmation", confirmation.save());
        tag.putString("State", state.name());
        if (resolver != null) tag.putUUID("Resolver", resolver);
        tag.putLong("ResolvedAt", resolvedAt);
        tag.putString("Diagnostic", diagnostic);
        return tag;
    }

    static ConversationNestedDecision load(CompoundTag tag) {
        require(integer(tag, "Version") == 1, "unsupported nested confirmation version");
        return new ConversationNestedDecision(Origin.load(compound(tag, "Origin")), ConversationSession.Phase.valueOf(text(tag, "ResumePhase")),
                CampaignConfirmation.load(compound(tag, "Confirmation")), State.valueOf(text(tag, "State")), optionalId(tag, "Resolver"),
                number(tag, "ResolvedAt"), string(tag, "Diagnostic"));
    }

    enum State { PENDING, APPROVED, DECLINED, REJECTED }
    enum Kind { SCENE, CONTINUATION }

    record Origin(Kind kind, UUID carrier, String effect, String step, String actionId, int index,
            String fingerprint, VillagerActionDefinition action) {
        Origin {
            require(kind != null && carrier != null && effect != null && !effect.isBlank() && effect.length() <= 1024
                    && fingerprint != null && !fingerprint.isBlank() && fingerprint.length() <= 1024 && action != null,
                    "nested confirmation has an invalid action source");
            require(kind == Kind.SCENE ? step != null && !step.isBlank() && actionId != null && !actionId.isBlank() && index == -1
                    : "".equals(step) && "".equals(actionId) && index >= 0, "nested confirmation has an invalid action cursor");
            require(action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME
                    || action.kind() == VillagerActionDefinition.Kind.QUEST && action.questAction() == VillagerActionDefinition.QuestAction.START,
                    "nested confirmation does not name a campaign decision or quest acceptance");
        }

        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("Kind", kind.name());
            tag.putUUID("Carrier", carrier);
            tag.putString("Effect", effect);
            tag.putString("Step", step);
            tag.putString("ActionId", actionId);
            tag.putInt("Index", index);
            tag.putString("Fingerprint", fingerprint);
            tag.put("Action", SceneContinuation.saveAction(action));
            return tag;
        }

        static Origin load(CompoundTag tag) {
            var action = SceneContinuation.loadAction(compound(tag, "Action"));
            require(SceneContinuation.saveAction(action).equals(compound(tag, "Action")), "nested confirmation action is incomplete");
            return new Origin(Kind.valueOf(text(tag, "Kind")), uuid(tag, "Carrier"), text(tag, "Effect"), string(tag, "Step"),
                    string(tag, "ActionId"), integer(tag, "Index"), text(tag, "Fingerprint"), action);
        }
    }
}
