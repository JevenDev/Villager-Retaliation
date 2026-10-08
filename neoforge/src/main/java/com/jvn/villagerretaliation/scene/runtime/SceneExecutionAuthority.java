package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import net.minecraft.server.MinecraftServer;

public final class SceneExecutionAuthority {
    private SceneExecutionAuthority() { }

    public static String campaignIssue(MinecraftServer server, SceneInstance instance) {
        String campaignIssue = CampaignQuestAccess.executionIssue(
                server, instance.owningQuestId(), instance.owningCampaignId(), instance.owner().playerId());
        if (campaignIssue.isBlank()) campaignIssue = com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.issue(instance.decisionParticipants());
        if (campaignIssue.isBlank() && !instance.conversationEffect().isEmpty()) {
            campaignIssue = com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution.resolve(server, instance).diagnostic();
        }
        if (campaignIssue.isBlank() && !instance.campaignConsent().isEmpty()) {
            campaignIssue = com.jvn.villagerretaliation.campaign.CampaignSceneApproval.resolve(server, instance).diagnostic();
        }
        if (campaignIssue.isBlank() && instance.owner().playerId() == null && instance.owningQuestInstance() != null) {
            for (var player : instance.participants()) {
                var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(server.overworld()).get(player, instance.owningQuestId());
                if (progress == null || !instance.owningQuestInstance().equals(progress.questRunId())) continue;
                campaignIssue = CampaignQuestAccess.executionIssue(server, instance.owningQuestId(), instance.owningCampaignId(), player);
                if (!campaignIssue.isBlank()) break;
            }
        }
        return campaignIssue;
    }
}
