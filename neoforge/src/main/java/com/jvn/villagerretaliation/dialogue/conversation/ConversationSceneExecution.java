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
        var tag = scene.conversationEffect();
        if (tag.isEmpty()) return work.get();
        var source = Source.load(tag);
        return withEffect(source.session(), source.effect(), work);
    }

    public static CompoundTag capture() {
        var current = CURRENT.get();
        return current == null ? new CompoundTag() : current.save();
    }

    public static Access resolve(MinecraftServer server, SceneInstance scene) {
        try {
            var source = Source.load(scene.conversationEffect());
            var data = ConversationSavedData.get(server.overworld());
            ConversationService.require(data.diagnostic().isBlank(), data.diagnostic());
            var session = data.session(source.session());
            ConversationService.require(session != null && session.active(), "The scene's conversation is unavailable or has already ended.");
            ConversationService.require(data.issue(source.session()).isBlank(), data.issue(source.session()));
            var definition = ConversationService.definition(server, session);
            boolean node = session.phase == ConversationSession.Phase.NODE_EFFECTS;
            ConversationService.require(node || session.phase == ConversationSession.Phase.RESPONSE_EFFECTS, "The conversation is no longer waiting for this scene.");
            var current = definition.nodes().get(session.node);
            var response = node ? null : current.responses().get(session.response);
            ConversationService.require(node || response != null, "The scene's original conversation response is unavailable.");
            var effects = node ? current.effects() : response.effects();
            ConversationService.require(session.effect >= 0 && session.effect < effects.size(), "The scene's original conversation effect is unavailable.");
            String key = session.visit + "/" + (node ? "node" : "response/" + session.response) + "/" + effects.get(session.effect).id();
            ConversationService.require(key.equals(source.effect()), "The scene belongs to another saved conversation effect.");
            var receipt = session.receipts.get(key);
            ConversationService.require(receipt != null && receipt.state() == ConversationSession.ReceiptState.RESOLVED
                    && SceneActionSuspension.dependsOnScene(SceneSavedData.get(server.overworld()), receipt.result(), scene.id()), "The conversation has not saved its wait for this scene.");
            UUID actor = receipt.actor();
            ConversationService.require(scene.owningCampaignId() == null || scene.owningCampaignId().equals(session.identity.campaign()),
                    "The scene belongs to another campaign.");
            ConversationService.require(actor.equals(session.actingPlayer), "The scene's deciding player changed.");
            ConversationService.require(scene.owner().playerId() == null || actor.equals(scene.owner().playerId()), "The scene belongs to another player.");
            if (session.questRun != null) ConversationService.require(session.questRun.equals(ConversationService.questRun(server, actor, session.identity.quest())),
                    "The conversation's original quest run changed.");
            if (receipt.questRun() != null) ConversationService.require(receipt.questRun().equals(ConversationService.questRun(server, actor, receipt.action().questId())),
                    "The scene effect's original quest run changed.");
            if (scene.owningQuestInstance() != null && scene.owningQuestId() != null) {
                var progress = VillagerQuestSavedData.get(server.overworld()).get(actor, scene.owningQuestId());
                ConversationService.require(progress != null && scene.owningQuestInstance().equals(progress.questRunId()), "The scene's original quest run changed.");
            }
            var decision = scene.decisionParticipants().isEmpty() ? null : CampaignDecisionParticipants.load(scene.decisionParticipants());
            ConversationService.require(java.util.Objects.equals(decision, session.decisionParticipants), "The scene's participation record belongs to another decision.");
            var controller = session.controller == null ? null : server.getPlayerList().getPlayer(session.controller);
            ConversationService.require(controller != null && ConversationService.member(controller, session), "The scene is waiting for an authorized conversation controller.");
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

    private record Source(UUID session, String effect) {
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
