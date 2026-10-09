package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.CampaignAuthority;
import com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants;
import com.jvn.villagerretaliation.campaign.CampaignSceneApproval;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneActionSuspension;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ConversationSceneExecution {
    private static final ThreadLocal<Source> CURRENT = new ThreadLocal<>();

    private ConversationSceneExecution() { }

    static <T> T withEffect(UUID session, String effect, Supplier<T> work) {
        var previous = CURRENT.get();
        CURRENT.set(new Source(session, effect));
        try { return work.get(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static <T> T withScene(SceneInstance scene, Supplier<T> work) {
        return withSource(scene.conversationEffect(), work);
    }

    public static <T> T withContinuation(SceneContinuation continuation, Supplier<T> work) {
        return withSource(continuation.conversationEffect(), work);
    }

    private static <T> T withSource(CompoundTag tag, Supplier<T> work) {
        if (tag.isEmpty()) return work.get();
        var source = Source.load(tag);
        return withEffect(source.session(), source.effect(), work);
    }

    public static CompoundTag capture() {
        var current = CURRENT.get();
        return current == null ? new CompoundTag() : current.save();
    }

    public static String paymentIssue(DialogueContext context, net.minecraft.resources.ResourceLocation quest, String transaction) {
        var source = CURRENT.get();
        if (source == null) return "";
        var data = ConversationSavedData.get(context.level());
        if (!data.diagnostic().isBlank()) return data.diagnostic();
        var session = data.session(source.session());
        if (session == null) return "The payment's conversation is missing. Restore its session before continuing.";
        return ConversationBargaining.paymentIssue(context, session, quest, transaction);
    }

    public static Access resolve(MinecraftServer server, SceneInstance scene) {
        return resolveScene(server, scene, false);
    }

    static Access resolveConfirmation(MinecraftServer server, SceneInstance scene) { return resolveScene(server, scene, true); }

    private static Access resolveScene(MinecraftServer server, SceneInstance scene, boolean confirming) {
        return resolve(server, scene.conversationEffect(), scene.owner().playerId(), scene.owningCampaignId(),
                scene.owningQuestId(), scene.owningQuestInstance(), scene.decisionParticipants(),
                result -> SceneActionSuspension.dependsOnScene(SceneSavedData.get(server.overworld()), result, scene.id()), confirming);
    }

    public static Access resolve(MinecraftServer server, SceneContinuation continuation) {
        return resolveContinuation(server, continuation, false);
    }

    static Access resolveConfirmation(MinecraftServer server, SceneContinuation continuation) { return resolveContinuation(server, continuation, true); }

    private static Access resolveContinuation(MinecraftServer server, SceneContinuation continuation, boolean confirming) {
        return resolve(server, continuation.conversationEffect(), continuation.playerId(), continuation.campaignId(),
                continuation.questId(), continuation.questRunId(), continuation.decisionParticipants(),
                result -> SceneActionSuspension.dependsOnContinuation(SceneSavedData.get(server.overworld()), result, continuation.id()), confirming);
    }

    private static Access resolve(MinecraftServer server, CompoundTag sourceTag, UUID owner,
            net.minecraft.resources.ResourceLocation campaign, net.minecraft.resources.ResourceLocation quest, UUID questRun,
            CompoundTag participants, java.util.function.Predicate<com.jvn.villagerretaliation.action.ActionResult> waiting, boolean confirming) {
        try {
            var source = Source.load(sourceTag);
            var data = ConversationSavedData.get(server.overworld());
            ConversationService.require(data.diagnostic().isBlank(), data.diagnostic());
            var session = data.session(source.session());
            ConversationService.require(session != null && session.active(), "The suspended action's conversation is unavailable or has already ended.");
            ConversationService.require(data.issue(source.session()).isBlank(), data.issue(source.session()));
            var definition = ConversationService.definition(server, session);
            var phase = session.phase;
            if (phase == ConversationSession.Phase.CONFIRMATION && session.nestedDecision != null) {
                ConversationService.require(confirming, "Review the pending shared consequence before this action continues.");
                phase = session.nestedDecision.resumePhase();
            }
            boolean node = phase == ConversationSession.Phase.NODE_EFFECTS;
            ConversationService.require(node || phase == ConversationSession.Phase.RESPONSE_EFFECTS, "The conversation is no longer waiting for this action.");
            var current = definition.nodes().get(session.node);
            var response = node ? null : current.responses().get(session.response);
            ConversationService.require(node || response != null, "The suspended action's original conversation response is unavailable.");
            var effects = node ? current.effects() : response.effects();
            ConversationService.require(session.effect >= 0 && session.effect < effects.size(), "The suspended action's original conversation effect is unavailable.");
            String key = session.visit + "/" + (node ? "node" : "response/" + session.response) + "/" + effects.get(session.effect).id();
            ConversationService.require(key.equals(source.effect()), "The suspended action belongs to another saved conversation effect.");
            var receipt = session.receipts.get(key);
            ConversationService.require(receipt != null && receipt.state() == ConversationSession.ReceiptState.RESOLVED
                    && waiting.test(receipt.result()), "The conversation has not saved its wait for this action.");
            UUID actor = receipt.actor();
            ConversationService.require(campaign == null || campaign.equals(session.identity.campaign()),
                    "The suspended action belongs to another campaign.");
            ConversationService.require(actor.equals(session.actingPlayer), "The suspended action's deciding player changed.");
            ConversationService.require(owner == null || actor.equals(owner), "The suspended action belongs to another player.");
            if (session.questRun != null) ConversationService.require(session.questRun.equals(ConversationService.questRun(server, actor, session.identity.quest())),
                    "The conversation's original quest run changed.");
            if (receipt.questRun() != null) ConversationService.require(receipt.questRun().equals(ConversationService.questRun(server, actor, receipt.action().questId())),
                    "The suspended effect's original quest run changed.");
            if (questRun != null && quest != null) {
                var progress = VillagerQuestSavedData.get(server.overworld()).get(actor, quest);
                ConversationService.require(progress != null && questRun.equals(progress.questRunId()), "The suspended action's original quest run changed.");
            }
            var decision = participants.isEmpty() ? null : CampaignDecisionParticipants.load(participants);
            var nestedEvidence = decision == null ? null : session.nestedParticipation.get(decision.decision());
            ConversationService.require(java.util.Objects.equals(decision, session.decisionParticipants)
                    || nestedEvidence != null && nestedEvidence.effect().equals(source.effect()) && nestedEvidence.participants().equals(decision),
                    "The suspended action's participation record belongs to another decision.");
            var controller = session.controller == null ? null : server.getPlayerList().getPlayer(session.controller);
            ConversationService.require(controller != null && ConversationService.member(controller, session), "The suspended action is waiting for an authorized conversation controller.");
            String issue = CampaignAuthority.controlIssue(controller, actor);
            ConversationService.require(issue.isBlank(), issue);
            var character = ConversationService.character(controller, session);
            ConversationService.require(ConversationService.near(controller, character), "The controller must return to the conversation before its scene continues.");
            ConversationService.require(!com.jvn.villagerretaliation.interaction.VillagerConversationService.isConversing(character),
                    "The conversation scene is waiting for its interruption to finish.");
            return new Access(controller, actor, "");
        } catch (ConversationService.Unavailable | IllegalArgumentException exception) {
            return new Access(null, null, exception.getMessage());
        }
    }

    public static DialogueContext context(MinecraftServer server, SceneInstance scene, VillagerActionDefinition action,
            net.minecraft.world.entity.npc.Villager provider) {
        var access = resolve(server, scene);
        if (!access.diagnostic().isBlank()) return null;
        DialogueContext approved;
        if (!scene.campaignConsent().isEmpty()) {
            approved = CampaignSceneApproval.resolve(server, scene).context();
            if (approved == null) return null;
        } else {
            var source = Source.load(scene.conversationEffect());
            var session = ConversationSavedData.get(server.overworld()).session(source.session());
            if (provider == null) provider = ConversationService.character(access.controller(), session);
            if (provider.level() != access.controller().serverLevel()) return null;
            approved = VillagerInteractionService.createDialogueContext(access.controller().serverLevel(), access.controller(), provider);
        }
        if (action != null && action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME) return approved;
        var actor = server.getPlayerList().getPlayer(access.actor());
        return actor == null || actor.serverLevel() != approved.level() ? null
                : VillagerInteractionService.createDialogueContext(actor.serverLevel(), actor, approved.villager());
    }

    public record Access(ServerPlayer controller, UUID actor, String diagnostic) { }

    record Source(UUID session, String effect) {
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putInt("Version", 1);
            tag.putUUID("Session", session);
            tag.putString("Effect", effect);
            return tag;
        }
        static Source load(CompoundTag tag) {
            if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Session")
                    || !tag.contains("Effect", Tag.TAG_STRING) || tag.getString("Effect").isBlank() || tag.getString("Effect").length() > 1024) {
                throw new IllegalArgumentException("The scene's saved conversation effect is unreadable. Restore its original link before continuing.");
            }
            return new Source(tag.getUUID("Session"), tag.getString("Effect"));
        }
    }
}
