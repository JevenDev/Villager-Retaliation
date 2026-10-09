package com.jvn.villagerretaliation.dialogue.conversation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService;
import com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants;
import com.jvn.villagerretaliation.campaign.CampaignQuestConsent;
import com.jvn.villagerretaliation.campaign.CampaignRewardRecovery;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import static com.jvn.villagerretaliation.dialogue.conversation.ConversationService.require;

public final class ConversationNestedConfirmations {
    private ConversationNestedConfirmations() { }

    public static Gate beforeScene(SceneExecutionContext execution, DialogueContext context, String actionId, VillagerActionDefinition action) {
        var scene = execution.instance();
        if (scene.conversationEffect().isEmpty() || !eligible(action)) return Gate.PASS;
        try {
            var source = ConversationSceneExecution.Source.load(scene.conversationEffect());
            var origin = new ConversationNestedDecision.Origin(ConversationNestedDecision.Kind.SCENE, scene.id(), source.effect(),
                    execution.step().id(), actionId, -1, execution.definition().definitionHash(), action);
            settle(execution.server(), scene.conversationEffect());
            var session = ConversationSavedData.get(execution.server().overworld()).session(source.session());
            var receipt = scene.receipt(execution.operationId("action/" + actionId));
            if (receipt != null && (session == null || session.nestedDecision == null || !session.nestedDecision.origin().equals(origin))) return Gate.PASS;
            var authority = ConversationSceneExecution.resolve(execution.server(), scene);
            require(authority.diagnostic().isBlank(), authority.diagnostic());
            return before(execution.server(), source, origin, context, authority, scene.campaignConsent(), scene::campaignConsent);
        } catch (IOException | ConversationService.Unavailable | IllegalArgumentException exception) {
            return new Gate(exception.getMessage(), null, false);
        }
    }

    public static Gate beforeContinuation(DialogueContext context, SceneContinuation continuation, int index) {
        var action = continuation.actions().get(index);
        if (continuation.conversationEffect().isEmpty() || !eligible(action)) return Gate.PASS;
        try {
            var source = ConversationSceneExecution.Source.load(continuation.conversationEffect());
            var origin = new ConversationNestedDecision.Origin(ConversationNestedDecision.Kind.CONTINUATION, continuation.id(), source.effect(),
                    "", "", index, continuation.recoveryFingerprint(), action);
            var authority = ConversationSceneExecution.resolve(context.level().getServer(), continuation);
            require(authority.diagnostic().isBlank(), authority.diagnostic());
            return before(context.level().getServer(), source, origin, context, authority, continuation.campaignConsent(), continuation::campaignConsent);
        } catch (IOException | ConversationService.Unavailable | IllegalArgumentException | Pause exception) {
            return new Gate(exception.getMessage(), null, false);
        }
    }

    private static boolean eligible(VillagerActionDefinition action) {
        return action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME
                || action.kind() == VillagerActionDefinition.Kind.QUEST && action.questAction() == VillagerActionDefinition.QuestAction.START;
    }

    private static Gate before(MinecraftServer server, ConversationSceneExecution.Source source, ConversationNestedDecision.Origin origin,
            DialogueContext personal, ConversationSceneExecution.Access authority, CompoundTag priorConsent, Consumer<CompoundTag> updateConsent) throws IOException {
        var data = ConversationSavedData.get(server.overworld());
        require(data.issue(source.session()).isBlank(), data.issue(source.session()));
        settle(server, source.save());
        var session = data.session(source.session());
        require(session != null && session.active(), "The interrupted conversation is unavailable.");
        require(personal != null, "The interrupted action is waiting for its original player or character.");
        var context = VillagerInteractionService.createDialogueContext(personal.level(), authority.controller(), personal.villager());
        var consent = CampaignQuestAcceptanceService.remainingConsent(context, authority.actor(), priorConsent);
        var nested = session.nestedDecision;
        if (nested != null) {
            require(nested.origin().equals(origin), "Finish the pending shared consequence before another action continues.");
            require(nested.state() != ConversationNestedDecision.State.PENDING, "Review the pending shared consequence before this action continues.");
            if (nested.state() != ConversationNestedDecision.State.APPROVED) return new Gate("", nested.rejection(), true);
            var accepted = nested.confirmation().acceptedConsent(nested.resolvedAt());
            var access = CampaignQuestAcceptanceService.resumeAccess(context, authority.actor(), accepted);
            if (!access.allowed()) return access.paused() ? new Gate(access.diagnostic(), null, false)
                    : new Gate("", unavailable(origin.action(), access.diagnostic()), true);
            consent = merge(context, authority.actor(), consent, CampaignQuestAcceptanceService.remainingConsent(context, authority.actor(), accepted));
            updateConsent.accept(consent);
            SceneSavedData.get(server.overworld()).changed();
            SceneSavedData.get(server.overworld()).persistCampaignHandoff(server.overworld());
            var evidence = session.nestedParticipation.get(nested.confirmation().id());
            require(evidence != null && evidence.effect().equals(source.effect()), "The saved approval has no matching participation evidence.");
            return new Gate("", null, true, evidence.participants());
        }
        if (!consent.equals(priorConsent)) {
            updateConsent.accept(consent);
            SceneSavedData.get(server.overworld()).changed();
        }
        var prepared = CampaignQuestAcceptanceService.prepareRemainingAction(context, authority.actor(), origin.action(), consent);
        if (!prepared.diagnostic().isBlank()) return new Gate("", unavailable(origin.action(), prepared.diagnostic()), false);
        if (prepared.confirmation() == null) return Gate.PASS;
        require(session.phase == ConversationSession.Phase.NODE_EFFECTS || session.phase == ConversationSession.Phase.RESPONSE_EFFECTS,
                "The conversation no longer owns this interrupted action.");
        session.nestedDecision = new ConversationNestedDecision(origin, session.phase, prepared.confirmation(),
                ConversationNestedDecision.State.PENDING, null, 0L, "");
        session.phase = ConversationSession.Phase.CONFIRMATION;
        session.diagnostic = "";
        ConversationService.save(authority.controller(), data, session);
        return new Gate("Review the pending shared consequence before this action continues.", null, false);
    }

    private static CompoundTag merge(DialogueContext context, UUID actor, CompoundTag remaining, CompoundTag accepted) {
        if (remaining.isEmpty()) return accepted;
        if (accepted.isEmpty()) return remaining;
        var previous = CampaignQuestConsent.load(remaining);
        var next = CampaignQuestConsent.load(accepted);
        require(previous.playerId().equals(actor) && next.playerId().equals(actor)
                && previous.providerId().equals(context.villager().getUUID()) && next.providerId().equals(previous.providerId()),
                "The interrupted action's saved approvals belong to another player or character.");
        var quests = new LinkedHashMap<>(previous.quests());
        next.quests().forEach((key, value) -> {
            var prior = quests.putIfAbsent(key, value);
            require(prior == null || prior.equals(value), "The interrupted action has conflicting quest approvals.");
        });
        var decisions = new LinkedHashMap<>(previous.decisions());
        next.decisions().forEach((key, value) -> {
            var prior = decisions.putIfAbsent(key, value);
            require(prior == null || prior.equals(value), "The interrupted action has conflicting outcome approvals.");
        });
        return new CampaignQuestConsent(actor, previous.providerId(), quests, decisions).save();
    }

    static String confirm(ServerPlayer player, ConversationSession session, String id) {
        var nested = pending(session, id);
        var context = confirmationContext(player, session, nested);
        var access = CampaignQuestAcceptanceService.confirmationAccess(context, session.actingPlayer, nested.confirmation());
        if (access.paused()) return access.diagnostic();
        if (access.allowed()) captureParticipants(player, session, nested);
        session.nestedDecision = nested.resolved(access.allowed() ? ConversationNestedDecision.State.APPROVED : ConversationNestedDecision.State.REJECTED,
                player.getUUID(), player.serverLevel().getGameTime(), access.diagnostic());
        session.phase = nested.resumePhase();
        return access.diagnostic();
    }

    private static void captureParticipants(ServerPlayer player, ConversationSession session, ConversationNestedDecision nested) {
        var scenes = SceneSavedData.get(player.serverLevel());
        var origin = nested.origin();
        var previous = origin.kind() == ConversationNestedDecision.Kind.SCENE
                ? scenes.get(origin.carrier()).orElseThrow().decisionParticipants()
                : scenes.continuation(origin.carrier()).orElseThrow().decisionParticipants();
        var witnesses = new java.util.LinkedHashSet<UUID>();
        if (!previous.isEmpty()) witnesses.addAll(CampaignDecisionParticipants.load(previous).participants());
        witnesses.add(session.actingPlayer);
        witnesses.addAll(ConversationService.presentParticipants(player, session));
        var participants = new CampaignDecisionParticipants(session.identity.id(), nested.confirmation().id(), session.identity.campaign(),
                session.identity.campaignInstance(), session.actingPlayer, witnesses);
        var evidence = new ConversationParticipationEvidence(origin.effect(), participants, player.getUUID());
        require(session.nestedParticipation.putIfAbsent(nested.confirmation().id(), evidence) == null,
                "This decision already has saved participation evidence.");
    }

    static void decline(ServerPlayer player, ConversationSession session, String id) {
        var nested = pending(session, id);
        confirmationContext(player, session, nested);
        session.nestedDecision = nested.resolved(ConversationNestedDecision.State.DECLINED, player.getUUID(), player.serverLevel().getGameTime(), "");
        session.phase = nested.resumePhase();
    }

    private static ConversationNestedDecision pending(ConversationSession session, String id) {
        var nested = session.nestedDecision;
        require(session.phase == ConversationSession.Phase.CONFIRMATION && nested != null
                && nested.state() == ConversationNestedDecision.State.PENDING && nested.confirmation().id().toString().equals(id),
                "This confirmation changed. Review the current consequences.");
        return nested;
    }

    private static DialogueContext confirmationContext(ServerPlayer player, ConversationSession session, ConversationNestedDecision nested) {
        var origin = nested.origin();
        var data = SceneSavedData.get(player.serverLevel());
        CompoundTag source;
        ConversationSceneExecution.Access authority;
        if (origin.kind() == ConversationNestedDecision.Kind.SCENE) {
            var scene = data.get(origin.carrier()).orElse(null);
            require(scene != null && !scene.state().terminal(), "The interrupted scene is no longer waiting for this decision.");
            var definition = SceneResources.scene(player.server, scene.sceneId()).orElse(null);
            require(definition != null && definition.definitionHash().equals(origin.fingerprint()) && scene.currentStep().equals(origin.step()),
                    "The interrupted scene changed. Restore its original content before confirming.");
            var step = definition.steps().get(origin.step());
            require(step != null && step.parameters().has("actions") && step.parameters().get("actions").isJsonArray(),
                    "The interrupted scene action is unavailable.");
            VillagerActionDefinition current = null;
            for (var raw : step.parameters().getAsJsonArray("actions")) {
                if (!raw.isJsonObject() || !raw.getAsJsonObject().has("id")
                        || !raw.getAsJsonObject().get("id").getAsString().equals(origin.actionId())) continue;
                var wrapper = new JsonObject(); var actions = new JsonArray(); actions.add(raw); wrapper.add("actions", actions);
                var parsed = VillagerActionDefinition.readList(definition.id(), "scene action " + origin.actionId(), wrapper, null);
                if (parsed.size() == 1) current = parsed.getFirst();
            }
            require(origin.action().equals(current), "The interrupted scene action no longer matches its confirmation.");
            var receipt = scene.receipt(origin.carrier() + "/" + origin.step() + "/action/" + origin.actionId());
            require(receipt == null, "This scene action already has an execution receipt. Inspect it before continuing.");
            source = scene.conversationEffect();
            authority = ConversationSceneExecution.resolveConfirmation(player.server, scene);
        } else {
            var continuation = data.continuation(origin.carrier()).orElse(null);
            require(continuation != null && !continuation.completionReceipt() && continuation.nextActionIndex() == origin.index()
                    && continuation.preparedRecoveryAction() < 0, "The interrupted quest action no longer matches this confirmation.");
            require(continuation.recoveryFingerprint().equals(origin.fingerprint())
                    && CampaignRewardRecovery.fingerprint(player.server, continuation.questId()).equals(origin.fingerprint()),
                    "The interrupted quest bundle changed. Restore its original content before confirming.");
            require(origin.index() < continuation.actions().size() && origin.action().equals(continuation.actions().get(origin.index())),
                    "The interrupted quest action changed.");
            source = continuation.conversationEffect();
            authority = ConversationSceneExecution.resolveConfirmation(player.server, continuation);
        }
        var link = ConversationSceneExecution.Source.load(source);
        require(link.session().equals(session.identity.id()) && link.effect().equals(origin.effect()), "This confirmation belongs to another conversation effect.");
        require(authority.diagnostic().isBlank(), authority.diagnostic());
        require(authority.controller().getUUID().equals(player.getUUID()) && authority.actor().equals(session.actingPlayer),
                "Only the current authorized controller can confirm this consequence.");
        var provider = player.serverLevel().getEntity(nested.confirmation().consent().providerId());
        require(provider instanceof Villager, "Return to the assigned campaign character before confirming.");
        return VillagerInteractionService.createDialogueContext(player.serverLevel(), player, (Villager) provider);
    }

    public static void settle(MinecraftServer server, CompoundTag source) {
        if (source.isEmpty()) return;
        try { settleSession(server, ConversationSceneExecution.Source.load(source).session()); }
        catch (IOException exception) { throw new Pause("The conversation decision is waiting for world storage.", exception); }
    }

    static void settleSession(MinecraftServer server, UUID id) throws IOException {
        var data = ConversationSavedData.get(server.overworld());
        require(data.issue(id).isBlank(), data.issue(id));
        var session = data.session(id);
        if (session == null || session.nestedDecision == null) return;
        boolean pending = session.nestedDecision.state() == ConversationNestedDecision.State.PENDING;
        var origin = session.nestedDecision.origin();
        var source = new ConversationSceneExecution.Source(id, origin.effect()).save();
        var scenes = SceneSavedData.get(server.overworld());
        boolean consumed;
        if (origin.kind() == ConversationNestedDecision.Kind.SCENE) {
            var scene = scenes.get(origin.carrier()).orElse(null);
            var receipt = scene == null ? null : scene.receipt(origin.carrier() + "/" + origin.step() + "/action/" + origin.actionId());
            consumed = scene != null && source.equals(scene.conversationEffect())
                    && (scene.state().terminal() || !pending && receipt != null && receipt.state() != SceneOperationReceipt.ReceiptState.PREPARED);
        } else {
            var continuation = scenes.continuation(origin.carrier()).orElse(null);
            consumed = continuation != null && source.equals(continuation.conversationEffect()) && !continuation.recoveryPersistencePending()
                    && (continuation.completionReceipt() || !pending && continuation.nextActionIndex() > origin.index());
        }
        if (!consumed) return;
        if (pending) session.phase = session.nestedDecision.resumePhase();
        session.nestedDecision = null;
        long revision = session.revision++;
        data.write(server.overworld(), revision, session);
    }

    private static ActionResult unavailable(VillagerActionDefinition action, String diagnostic) {
        return ActionResult.unavailable(diagnostic, VillagerActionRegistry.capabilities(action), VillagerActionResult.EMPTY);
    }

    public record Gate(String waiting, ActionResult rejection, boolean resolved, CampaignDecisionParticipants participants) {
        public static final Gate PASS = new Gate("", null, false);
        public Gate(String waiting, ActionResult rejection, boolean resolved) { this(waiting, rejection, resolved, null); }
    }

    public static final class Pause extends RuntimeException {
        private Pause(String message, IOException cause) { super(message, cause); }
    }
}
