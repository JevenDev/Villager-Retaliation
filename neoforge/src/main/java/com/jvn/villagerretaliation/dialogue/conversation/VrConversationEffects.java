package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants;
import com.jvn.villagerretaliation.campaign.CampaignOutcomeService;
import com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService;
import com.jvn.villagerretaliation.campaign.CampaignRewardRecovery;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import net.minecraft.server.level.ServerPlayer;

final class VrConversationEffects implements ConversationEffects {
    static final VrConversationEffects INSTANCE = new VrConversationEffects();

    private VrConversationEffects() { }

    @Override public ActionResult apply(Operation operation) {
        var applied = new ActionResult[1];
        var outcomeActor = operation.action().kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME
                ? new CampaignOutcomeService.Actor(operation.actor(), operation.questRun()) : null;
        CampaignQuestAcceptanceService.withConsent(operation.consent(), () -> applied[0] =
                ConversationSceneExecution.withEffect(operation.identity().id(), operation.effect(),
                        () -> CampaignDecisionParticipants.withParticipants(operation.participants(),
                                () -> ConversationService.withController(operation.controller(), operation.actor(), operation.identity(),
                                        () -> VillagerActionRegistry.execute(operation.context(), operation.action(), new java.util.LinkedHashMap<>(operation.replacements()), outcomeActor)))));
        return applied[0];
    }

    @Override public ActionResult poll(ServerPlayer player, ActionResult waiting) {
        if (waiting.suspensionKind() == ActionResult.SuspensionKind.CAMPAIGN_REWARD) return CampaignRewardRecovery.poll(player.server, waiting);
        if (waiting.suspensionKind() == ActionResult.SuspensionKind.CAMPAIGN_OUTCOME)
            return com.jvn.villagerretaliation.campaign.CampaignLocationVariants.poll(player.server, waiting);
        var scenes = SceneSavedData.get(player.serverLevel());
        if (waiting.suspensionKind() == ActionResult.SuspensionKind.CONTINUATION) {
            var continuation = scenes.continuation(waiting.suspensionId()).orElse(null);
            ConversationService.require(continuation != null, "The saved action continuation is unavailable. Restore its scene data.");
            return continuation.completionReceipt() ? new ActionResult(continuation.actionStatus(), continuation.actionDiagnostic(),
                    waiting.capabilities(), new VillagerActionResult(false, "", "", continuation.replacements(), false)) : waiting;
        }
        var scene = scenes.get(waiting.suspensionId()).orElse(null);
        ConversationService.require(scene != null, "The saved scene is unavailable. Restore its scene data.");
        if (!scene.state().terminal()) return waiting;
        return scene.completionResult() == SceneInstance.CompletionResult.SUCCESS
                ? ActionResult.alreadySatisfied("Scene completed.", waiting.capabilities(), VillagerActionResult.EMPTY)
                : ActionResult.failed("Scene ended with " + scene.completionResult() + ".", waiting.capabilities());
    }

    @Override public boolean recoverable(VillagerActionDefinition action) {
        return VillagerActionRegistry.reconcilesPreparedOperation(action);
    }
}
