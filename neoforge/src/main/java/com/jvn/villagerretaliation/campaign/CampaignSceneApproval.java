package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.Villager;

public final class CampaignSceneApproval {
    private CampaignSceneApproval() { }

    public static Access resolve(MinecraftServer server, SceneInstance scene) {
        CampaignQuestConsent consent;
        try { consent = CampaignQuestConsent.load(scene.campaignConsent()); }
        catch (IllegalArgumentException exception) { return new Access(null, exception.getMessage()); }
        if (scene.owner().playerId() != null && !scene.owner().playerId().equals(consent.playerId())) {
            return new Access(null, "The scene approval belongs to another player.");
        }
        if (scene.owningQuestId() != null && scene.owningQuestInstance() != null) {
            var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(server.overworld()).get(consent.playerId(), scene.owningQuestId());
            if (progress == null || !scene.owningQuestInstance().equals(progress.questRunId())) {
                return new Access(null, "The approved scene's original quest run changed.");
            }
        }
        var decision = scene.decisionParticipants();
        if (!decision.isEmpty()) {
            String issue = CampaignDecisionParticipants.issue(decision);
            if (!issue.isBlank()) return new Access(null, issue);
            if (!CampaignDecisionParticipants.load(decision).player().equals(consent.playerId())) {
                return new Access(null, "The scene approval and participation record belong to different players.");
            }
        }
        var authority = scene.conversationEffect().isEmpty() ? null
                : com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution.resolve(server, scene);
        if (authority != null && !authority.diagnostic().isBlank()) return new Access(null, authority.diagnostic());
        if (authority != null && !authority.actor().equals(consent.playerId())) return new Access(null, "The scene approval belongs to another conversation actor.");
        var player = authority == null ? server.getPlayerList().getPlayer(consent.playerId()) : authority.controller();
        if (player == null) return new Access(null, "The player who approved this scene must reconnect before its effects can continue.");
        var quest = scene.owningQuestId();
        if (quest == null) quest = consent.quests().keySet().stream().findFirst().orElseGet(() -> consent.decisions().values().iterator().next().questId());
        var provider = CampaignQuestCharacters.continuationProvider(server, quest, consent.providerId());
        if (provider != null) for (var level : server.getAllLevels()) {
            if (!(level.getEntity(provider) instanceof Villager villager)) continue;
            if (level != player.serverLevel()) return new Access(null, "The player who approved this scene must return to its character's dimension.");
            var context = VillagerInteractionService.createDialogueContext(level, player, villager);
            var access = CampaignQuestAcceptanceService.resumeAccess(context, consent.playerId(), scene.campaignConsent());
            return new Access(access.allowed() ? context : null, access.diagnostic());
        }
        return new Access(null, "The scene is waiting for its approved campaign character.");
    }

    public static boolean canRecover(SceneInstance scene, com.jvn.villagerretaliation.action.VillagerActionDefinition action) {
        if (action.kind() == com.jvn.villagerretaliation.action.VillagerActionDefinition.Kind.CAMPAIGN_PAYMENT)
            return action.questId() != null && action.campaignPayment() != null;
        if (action.kind() != com.jvn.villagerretaliation.action.VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME
                || scene.campaignConsent().isEmpty()) return false;
        try {
            return CampaignQuestConsent.load(scene.campaignConsent()).decisions().values().stream()
                    .anyMatch(consent -> consent.questId().equals(action.questId()) && consent.action().equals(action.campaignOutcome()));
        } catch (IllegalArgumentException exception) { return false; }
    }

    public record Access(DialogueContext context, String diagnostic) { }
}
