package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class CampaignQuestAccess {
    private CampaignQuestAccess() {
    }

    public static String contentIssue(ServerLevel level, QuestDefinition quest) {
        if (quest == null) return "Quest content is missing.";
        if (quest.campaign() == null) return "";
        if (level == null) return "Campaign world is unavailable.";
        return contentIssue(CampaignWorldModeSavedData.get(level).mode(), quest,
                CampaignSavedData.get(level), CampaignResources.current(level.getServer()));
    }

    public static String contentIssue(ServerLevel level, QuestDefinition quest, VillagerQuestSavedData.QuestProgress progress) {
        if (progress != null && !progress.campaignId().isBlank()
                && (quest == null || !progress.campaignId().equals(String.valueOf(quest.campaign())))) {
            return "The saved quest belongs to campaign " + progress.campaignId() + ". Restore its original campaign ownership to continue.";
        }
        return contentIssue(level, quest);
    }

    public static String contentIssue(DialogueContext context, QuestDefinition quest) {
        if (context == null) return contentIssue((ServerLevel) null, quest);
        return contentIssue(context.level(), quest,
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id()));
    }

    public static String executionIssue(DialogueContext context, QuestDefinition quest) {
        if (context == null) return contentIssue(context, quest);
        return executionIssue(context.level(), context.player().getUUID(), quest,
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id()));
    }

    public static String executionIssue(ServerLevel level, UUID playerId, QuestDefinition quest,
            VillagerQuestSavedData.QuestProgress progress) {
        return CampaignQuestReconciliation.executionIssue(level, playerId, quest, progress);
    }

    public static String executionIssue(MinecraftServer server, ResourceLocation questId, ResourceLocation savedCampaign, UUID playerId) {
        String issue = contentIssue(server, questId, savedCampaign);
        if (!issue.isBlank() || server == null || playerId == null || questId == null) return issue;
        var quest = VillagerQuestResources.quest(server, questId).orElse(null);
        return quest == null ? "" : executionIssue(server.overworld(), playerId, quest,
                VillagerQuestSavedData.get(server.overworld()).get(playerId, questId));
    }

    public static ResourceLocation campaignFor(MinecraftServer server, ResourceLocation questId) {
        return questId == null ? null : VillagerQuestResources.quest(server, questId).map(QuestDefinition::campaign).orElse(null);
    }

    public static String contentIssue(MinecraftServer server, ResourceLocation questId, ResourceLocation savedCampaign) {
        if (server == null) return savedCampaign == null ? "" : "Campaign server is unavailable.";
        var quest = questId == null ? null : VillagerQuestResources.quest(server, questId).orElse(null);
        if (savedCampaign != null && (quest == null || !savedCampaign.equals(quest.campaign()))) {
            return "The owning campaign quest is missing or its ownership changed. Restore the saved campaign content.";
        }
        return quest == null ? "" : contentIssue(server.overworld(), quest);
    }

    static String contentIssue(CampaignWorldMode mode, QuestDefinition quest, CampaignSavedData data, CampaignContentCatalog catalog) {
        if (quest.campaign() == null) return "";
        if (mode != CampaignWorldMode.CAMPAIGN) return "This quest requires a Campaign Mode world.";
        var content = catalog.campaigns().get(quest.campaign());
        if (content == null) return "Campaign content is missing or invalid. Restore the campaign datapack to continue.";
        String issue = data.compatibility(content.definition());
        if (!issue.isBlank()) return issue;
        if (!content.definition().questBundles().contains(quest.id())) return "This quest is not declared by its campaign.";
        return "";
    }

    public static String availabilityIssue(DialogueContext context, QuestDefinition quest) {
        String issue = contentIssue(context, quest);
        if (!issue.isBlank()) return issue;
        if (quest.campaign() == null) return "";
        return availabilityIssue(quest, context.player().getUUID(), CampaignSavedData.get(context.level()),
                CampaignResources.current(context.level().getServer()));
    }

    static String availabilityIssue(QuestDefinition quest, UUID playerId, CampaignSavedData data, CampaignContentCatalog catalog) {
        if (quest.campaign() == null) return "";
        var definition = catalog.campaigns().get(quest.campaign()).definition();
        var instance = data.instances().get(definition.id());
        var player = instance == null ? null : instance.players().get(playerId);
        if (player != null && player.completedQuests().contains(quest.id())) return "You have already completed this campaign quest.";
        Set<String> blockers = new LinkedHashSet<>();
        for (var chapter : definition.chapters().values()) {
            if (!chapter.quests().contains(quest.id())) continue;
            var access = CampaignProgression.chapter(definition, instance, playerId, chapter.id());
            if (access.available()) return "";
            blockers.addAll(access.blockers());
        }
        for (var milestone : definition.milestones().values()) {
            if (milestone.catchUp() == null || !milestone.catchUp().quest().equals(quest.id())) continue;
            if (instance == null || !instance.outcomes().containsKey(milestone.id())) {
                blockers.add("The shared milestone " + milestone.id() + " has not resolved.");
                continue;
            }
            var progress = player == null ? null : player.milestones().get(milestone.id());
            if (progress != null && progress.route() != CampaignInstance.Route.CATCH_UP) {
                blockers.add("No catch-up is needed for milestone " + milestone.id() + ".");
                continue;
            }
            var access = CampaignProgression.chapter(definition, instance, playerId, milestone.chapter());
            if (access.available()) return "";
            blockers.addAll(access.blockers());
        }
        return blockers.isEmpty() ? "This quest has no playable campaign chapter or catch-up entry." : String.join(" ", blockers);
    }
}
