package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

final class ConversationRuntime {
    private static final int MAX_STEPS = 16;

    private ConversationRuntime() { }

    static void drive(ServerPlayer player, UUID id) throws IOException {
        drive(player, id, true);
    }

    static void drive(ServerPlayer player, UUID id, boolean retryUnavailable) throws IOException {
        drive(player, id, retryUnavailable, VrConversationEffects.INSTANCE);
    }

    static void drive(ServerPlayer player, UUID id, boolean retryUnavailable, ConversationEffects execution) throws IOException {
        var data = ConversationSavedData.get(player.serverLevel());
        for (int step = 0; step < MAX_STEPS; step++) {
            ConversationService.require(data.issue(id).isBlank(), data.issue(id));
            ConversationNestedConfirmations.settleSession(player.server, id);
            var session = ConversationCharacters.reconcile(player.server, data, data.session(id));
            if (session == null || !session.active()) return;
            ConversationService.require(player.getUUID().equals(session.controller) && ConversationService.member(player, session)
                    && ConversationService.canControl(player, session), "An authorized controller must resume this conversation.");
            var definition = ConversationService.definition(player.server, session);
            var character = ConversationService.character(player, session);
            ConversationService.require(ConversationService.near(player, character), "Return to the conversation to continue.");
            ConversationService.require(!ConversationActors.interrupted(player, character), "Wait for the current interruption to finish.");
            boolean nodeEffects = session.phase == ConversationSession.Phase.NODE_EFFECTS;
            if (!nodeEffects && session.phase != ConversationSession.Phase.RESPONSE_EFFECTS) return;
            var node = definition.nodes().get(session.node);
            var response = nodeEffects ? null : node.responses().get(session.response);
            ConversationService.require(nodeEffects || response != null, "Saved conversation response is unavailable.");
            List<ConversationDefinition.Effect> effects = nodeEffects ? node.effects() : response.effects();
            if (nodeEffects && session.effect == 0 && session.consent.isEmpty()
                    && session.receipts.keySet().stream().noneMatch(key -> key.startsWith(session.visit + "/node/"))
                    && ConversationConfirmations.prepare(player, session, effects, null)) {
                ConversationService.save(player, data, session);
                return;
            }
            if (session.effect < effects.size()) {
                if (!executeEffect(player, data, session, effects.get(session.effect), nodeEffects, retryUnavailable, execution)) return;
                continue;
            }
            session.consent = new net.minecraft.nbt.CompoundTag();
            if (nodeEffects) {
                advanceExchange(player, session, node, 0);
            } else if (!response.check().isBlank()) {
                session.check = response.check();
                session.phase = ConversationSession.Phase.CHECK;
            } else ConversationService.enter(session, response.next());
            session.diagnostic = "";
            ConversationService.save(player, data, session);
        }
    }

    static void advanceExchange(ServerPlayer player, ConversationSession session, ConversationDefinition.Node node, int start) {
        var context = ConversationActors.context(player, ConversationService.character(player, session));
        for (int index = start; index < node.exchanges().size(); index++) {
            var exchange = node.exchanges().get(index);
            boolean enabled = true;
            for (var condition : exchange.conditions()) {
                String issue = condition.unavailableReason(context);
                ConversationService.require(issue.isBlank(), "Conversation line " + exchange.id() + ": " + issue);
                if (!condition.matches(context)) enabled = false;
            }
            if (!enabled) continue;
            appendLine(player, session, exchange);
            session.exchange = index;
            session.phase = ConversationSession.Phase.EXCHANGES;
            return;
        }
        ConversationService.finishExchanges(session, node);
    }

    private static void appendLine(ServerPlayer player, ConversationSession session, ConversationDefinition.Exchange exchange) {
        UUID speaker = null;
        String name = "";
        String role = exchange.speaker().role();
        if (exchange.speaker().kind() == ConversationDefinition.SpeakerKind.PLAYER) {
            speaker = switch (exchange.speaker().player()) {
                case INITIATOR -> session.identity.owner();
                case CHOICE_MAKER -> session.choices.isEmpty() ? session.identity.owner() : session.choices.getLast().player();
                case CONTROLLER -> session.controller;
            };
            var actor = player.server.getPlayerList().getPlayer(speaker);
            ConversationService.require(actor != null, "The player speaking this line must return before the conversation can continue.");
            name = actor.getName().getString();
            player = actor;
        } else if (exchange.speaker().kind() == ConversationDefinition.SpeakerKind.ROLE) {
            var speakerEntity = ConversationBinding.speaker(player, session, role);
            var prior = session.speakers.get(role);
            ConversationService.require(prior == null || prior.entity().equals(speakerEntity.getUUID()), "A saved conversation speaker was replaced. Restore its identity or repair this session.");
            speaker = speakerEntity.getUUID();
            name = speakerEntity.getName().getString();
            UUID owner = ConversationCharacters.active(player.server, ConversationSavedData.get(player.serverLevel()), speaker);
            ConversationService.require(owner == null || owner.equals(session.identity.id()), "This speaker is taking part in another conversation. Resume when it finishes.");
            ConversationService.require(speakerEntity.level() == player.serverLevel(), "This speaker is in another dimension. Resume at its location.");
            ConversationService.require(!ConversationActors.interrupted(player, speakerEntity),
                    "This speaker is taking part in an ordinary conversation. Resume when it finishes.");
            session.speakers.putIfAbsent(role, new ConversationSession.Speaker(role, speaker, speakerEntity.level().dimension().location(), name));
        }
        var context = ConversationActors.context(player, ConversationService.character(player, session));
        var replacements = com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.merge(
                com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.base(context), session.replacements);
        var wordContext = exchange.speaker().kind() == ConversationDefinition.SpeakerKind.ROLE
                ? ConversationActors.context(player, ConversationBinding.speaker(player, session, role)) : null;
        replacements = ConversationText.prepare(player, session, "line/" + exchange.id(), exchange.textKey(), replacements, wordContext);
        String text = ConversationText.render(player, ConversationService.text(player, exchange.textKey()), replacements);
        com.jvn.villagerretaliation.dialogue.DialogueCameraReferences.bind(exchange.presentation().camera(), player.serverLevel(),
                player, speaker == null ? context.speaker() : player.serverLevel().getEntity(speaker),
                session.identity.campaign(), session.cameraReferences);
        session.transcript.add(new ConversationSession.Line(session.node, session.visit, exchange.id(), exchange.speaker().kind(),
                role, speaker, name, exchange.textKey(), text, replacements, true));
    }

    private static boolean executeEffect(ServerPlayer controller, ConversationSavedData data, ConversationSession session,
            ConversationDefinition.Effect effect, boolean nodeEffect, boolean retryUnavailable, ConversationEffects execution) throws IOException {
        String key = session.visit + "/" + (nodeEffect ? "node" : "response/" + session.response) + "/" + effect.id();
        var receipt = session.receipts.get(key);
        boolean execute = receipt == null;
        if (receipt != null && receipt.state() == ConversationSession.ReceiptState.PREPARED) {
            ConversationService.require(execution.recoverable(ConversationActors.context(controller, ConversationService.character(controller, session)), receipt.action()), "Effect " + effect.id() + " has no completion receipt. Inspect its effects before repairing this conversation.");
            execute = true;
        }
        if (receipt != null && receipt.state() == ConversationSession.ReceiptState.RESOLVED) {
            var outcome = receipt.result();
            if (outcome.suspended()) {
                outcome = execution.poll(controller, outcome);
                ConversationService.require(!outcome.suspended(), outcome.message());
                session.replacements.putAll(outcome.legacyResult().replacements());
                if (outcome.success() && receipt.result().retryOnResume()) execute = true;
                else {
                    receipt = new ConversationSession.Receipt(key, receipt.actor(), receipt.questRun(), receipt.action(), ConversationSession.ReceiptState.RESOLVED, outcome);
                    session.receipts.put(key, receipt);
                    settle(controller, data, session, receipt);
                    return true;
                }
            } else if (outcome.status() == ActionStatus.UNAVAILABLE) {
                if (!retryUnavailable) return false;
                execute = true;
            }
            else {
                settle(controller, data, session, receipt);
                return true;
            }
        }
        if (!execute) return false;
        var actorId = receipt == null ? session.actingPlayer : receipt.actor();
        var action = receipt == null ? scopedAction(session.identity.id(), key, effect.action()) : receipt.action();
        boolean sharedOutcome = action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME;
        var actor = sharedOutcome || actorId.equals(controller.getUUID()) ? controller : controller.server.getPlayerList().getPlayer(actorId);
        ConversationService.require(actor != null, "The player who chose this personal effect must reconnect before it can finish.");
        var character = ConversationService.character(controller, session);
        ConversationService.require(actor.serverLevel() == character.level(), "The player who chose this personal effect must return to its dimension.");
        if (session.questRun != null) ConversationService.require(session.questRun.equals(ConversationService.questRun(controller.server, actorId, session.identity.quest())),
                "The original quest run changed. Repair this conversation before applying its remaining effects.");
        UUID run = receipt == null ? ConversationService.questRun(controller.server, actorId, action.questId()) : receipt.questRun();
        ConversationService.require(run == null || run.equals(ConversationService.questRun(controller.server, actorId, action.questId())), "The effect's original quest run changed.");
        ConversationService.require(!sharedOutcome || run != null, "The deciding player's original quest run is unavailable.");
        String authority = ConversationService.controlIssue(controller, actorId, session.identity);
        ConversationService.require(authority.isBlank(), authority);
        var context = ConversationActors.context(actor, character);
        var controllerContext = ConversationActors.context(controller, character);
        if (controllerContext instanceof DialogueContext legacyController
                && context instanceof DialogueContext legacy) {
            var access = CampaignQuestAcceptanceService.resumeAccess(legacyController, actorId, session.consent);
            ConversationService.require(access.allowed(), access.diagnostic());
            session.consent = CampaignQuestAcceptanceService.remainingConsent(legacy, actorId, session.consent);
        } else ConversationService.require(session.consent.isEmpty() && session.identity.campaign() == null,
                "Campaign consent requires its campaign actor adapter.");
        receipt = new ConversationSession.Receipt(key, actorId, run, action, ConversationSession.ReceiptState.PREPARED,
                ActionResult.unavailable("Effect completion has not been saved.", VillagerActionRegistry.capabilities(action), VillagerActionResult.EMPTY));
        session.receipts.put(key, receipt);
        ConversationService.save(controller, data, session);
        var participants = session.decisionParticipants == null ? new net.minecraft.nbt.CompoundTag() : session.decisionParticipants.save();
        var outcome = execution.apply(new ConversationEffects.Operation(context, controller, session.identity, key, actorId, run,
                action, session.replacements, session.consent, participants));
        if (outcome.suspended() && outcome.suspensionId() == null) outcome = ActionResult.failed("Suspended effect has no persistent dependency.", outcome.capabilities());
        session = data.session(session.identity.id());
        if (context instanceof DialogueContext legacy)
            session.consent = CampaignQuestAcceptanceService.remainingConsent(legacy, actorId, session.consent);
        receipt = new ConversationSession.Receipt(key, actorId, run, action, ConversationSession.ReceiptState.RESOLVED, outcome);
        session.receipts.put(key, receipt);
        session.replacements.putAll(outcome.legacyResult().replacements());
        if (session.questRun == null) session.questRun = ConversationService.questRun(controller.server, actorId, session.identity.quest());
        if (outcome.suspended()) {
            session.diagnostic = outcome.message();
            ConversationService.save(controller, data, session);
            throw new ConversationService.Unavailable(outcome.message());
        }
        settle(controller, data, session, receipt);
        return true;
    }

    private static void settle(ServerPlayer controller, ConversationSavedData data, ConversationSession session, ConversationSession.Receipt receipt) throws IOException {
        var result = receipt.result();
        if (!result.success() && receipt.action().required()) {
            String issue = result.message().isBlank() ? "A required conversation effect could not complete." : result.message();
            if (!issue.equals(session.diagnostic) || !java.util.Objects.equals(receipt, data.session(session.identity.id()).receipts.get(receipt.key()))) {
                session.diagnostic = issue;
                ConversationService.save(controller, data, session);
            }
            throw new ConversationService.Unavailable(issue);
        }
        session.effect++;
        session.diagnostic = "";
        ConversationService.save(controller, data, session);
    }

    private static VillagerActionDefinition scopedAction(UUID session, String effect, VillagerActionDefinition action) {
        if (action.kind() != VillagerActionDefinition.Kind.START_SCENE) return action;
        String operation = "conversation/" + UUID.nameUUIDFromBytes((session + "/" + effect + "/" + action.sceneOperationId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new VillagerActionDefinition(action.kind(), action.questId(), action.questAction(), action.amount(), action.memoryTag(), action.memoryScope(),
                action.lootTable(), action.notificationTrigger(), action.text(), action.forcedDialogue(), action.flashTracker(), action.factScope(),
                action.factTag(), action.factKey(), action.factValue(), action.linesByStatus(), action.lineKeysByStatus(), action.questTransition(),
                action.sceneId(), operation, action.waitForScene(), action.required(), action.campaignOutcome(), action.campaignFact(), action.campaignReward(), action.campaignStanding(), action.campaignPayment());
    }

}
