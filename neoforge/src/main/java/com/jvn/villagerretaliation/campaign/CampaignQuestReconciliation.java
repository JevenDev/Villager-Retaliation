package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class CampaignQuestReconciliation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, Map<String, String>> REPORTED = new WeakHashMap<>();

    private CampaignQuestReconciliation() {
    }

    static CampaignProgression.Entry pending(ServerLevel level, UUID playerId, QuestDefinition quest,
            VillagerQuestSavedData.QuestProgress progress) {
        if (quest.campaign() == null || progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) return null;
        var content = CampaignResources.current(level.getServer()).campaigns().get(quest.campaign());
        var instance = CampaignSavedData.get(level).instances().get(quest.campaign());
        if (content == null || instance == null) return null;
        for (var milestone : content.definition().milestones().values()) {
            if (!milestone.quest().equals(quest.id())) continue;
            var entry = CampaignProgression.entry(content.definition(), instance, playerId, milestone.id());
            if (entry != null && entry.route() == CampaignInstance.Route.CATCH_UP && entry.worldOperation() != null
                    && !progress.appliedCampaignCatchUp(entry.worldOperation())) return entry;
        }
        return null;
    }

    public static String executionIssue(ServerLevel level, UUID playerId, QuestDefinition quest,
            VillagerQuestSavedData.QuestProgress progress) {
        String issue = CampaignQuestAccess.contentIssue(level, quest, progress);
        if (!issue.isBlank() || quest.campaign() == null) return issue;
        if (VillagerQuestSavedData.get(level).campaignPersistencePending()) return "Campaign quest progress is waiting to be saved.";
        if (progress == null) return "";
        if (playerId == null) return "Campaign player is unavailable.";
        if (!progress.campaignWaitingQuest().isBlank()) return "Continue the authored catch-up quest " + progress.campaignWaitingQuest() + ".";
        var entry = pending(level, playerId, quest, progress);
        return entry == null ? "" : "The shared milestone " + entry.milestone() + " has resolved. Your catch-up path is being prepared.";
    }

    public static boolean reconcile(ServerPlayer player, QuestDefinition quest, VillagerQuestSavedData.QuestProgress progress) {
        ServerLevel level = player.serverLevel();
        if (quest.campaign() == null || !CampaignQuestAccess.contentIssue(level, quest, progress).isBlank()) return false;
        var quests = VillagerQuestSavedData.get(level);
        var reports = REPORTED.computeIfAbsent(level.getServer(), ignored -> new HashMap<>());
        String key = player.getUUID() + "/" + quest.id();
        try {
            boolean changed = quests.campaignPersistencePending();
            if (changed) quests.persistCampaignProgress(level);
            if (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) return changed;
            var instance = CampaignSavedData.get(level).instances().get(quest.campaign());
            var personal = instance == null ? null : instance.players().get(player.getUUID());
            if (!progress.campaignWaitingQuest().isBlank() && personal != null && personal.completedQuests().contains(quest.id())) {
                QuestLifecycleService.complete(quest.id(), progress, level.getGameTime(), quest.rules().consumeOnCompletion());
                quests.removeTrackedQuest(player.getUUID(), quest.id());
                quests.requireCampaignPersistence();
                quests.persistCampaignProgress(level);
                reports.remove(key);
                return true;
            }
            var entry = pending(level, player.getUUID(), quest, progress);
            if (entry == null) {
                reports.remove(key);
                return changed;
            }
            if (progress.campaignLegacyEntry() && entry.quest().equals(quest.id()) && entry.stage().equals(progress.campaignEntryStage())) {
                progress.recordCampaignCatchUp(entry.worldOperation());
                progress.finishCampaignEntryMigration();
                quests.requireCampaignPersistence();
                quests.persistCampaignProgress(level);
                reports.remove(key);
                return true;
            }
            var target = VillagerQuestResources.quest(level.getServer(), entry.quest()).orElse(null);
            if (target == null || !target.stages().containsKey(entry.stage())) return changed;
            SceneLifecycleIntegration.supersedeQuestRun(level, player.getUUID(), quest.id(), progress.questRunId());
            progress.enterCampaignCatchUp(entry.stage(), entry.quest().equals(quest.id()) ? null : entry.quest(), entry.worldOperation());
            quests.requireCampaignPersistence();
            quests.persistCampaignProgress(level);
            reports.remove(key);
            return true;
        } catch (IOException exception) {
            if (!exception.toString().equals(reports.put(key, exception.toString()))) {
                LOGGER.error("Could not save campaign catch-up for player {} and quest {}", player.getUUID(), quest.id(), exception);
            }
            return false;
        }
    }
}
