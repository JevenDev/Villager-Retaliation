package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class CampaignQuestEntries {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, Map<String, String>> REPORTED = new WeakHashMap<>();

    private CampaignQuestEntries() {
    }

    public static Entry start(DialogueContext context, QuestDefinition quest) {
        String stage = QuestLifecycleService.initialStage(quest);
        if (quest.campaign() == null) return Entry.original(quest.id(), stage);
        String issue = CampaignQuestAccess.availabilityIssue(context, quest);
        if (!issue.isBlank()) return Entry.blocked(quest.id(), stage, issue);
        var content = CampaignResources.current(context.level().getServer()).campaigns().get(quest.campaign());
        var data = CampaignSavedData.get(context.level());
        try {
            return enter(data, content, context.player().getUUID(), quest, stage, true, context.level().getGameTime());
        } catch (IOException exception) {
            LOGGER.error("Could not save campaign quest entry {} for {}", quest.id(), context.player().getUUID(), exception);
            return Entry.blocked(quest.id(), stage, "Campaign progress could not be saved. Check world storage before retrying.");
        }
    }

    public static Entry stage(DialogueContext context, QuestDefinition quest, String stage) {
        if (quest.campaign() == null) return Entry.original(quest.id(), stage);
        String issue = CampaignQuestAccess.contentIssue(context, quest);
        if (!issue.isBlank()) return Entry.blocked(quest.id(), stage, issue);
        var content = CampaignResources.current(context.level().getServer()).campaigns().get(quest.campaign());
        try {
            return enter(CampaignSavedData.get(context.level()), content, context.player().getUUID(), quest,
                    stage, false, context.level().getGameTime());
        } catch (IOException exception) {
            LOGGER.error("Could not save campaign milestone entry {}:{} for {}", quest.id(), stage, context.player().getUUID(), exception);
            return Entry.blocked(quest.id(), stage, "Campaign progress could not be saved. Check world storage before retrying.");
        }
    }

    private static Entry enter(CampaignSavedData data, CampaignContentCatalog.Entry content, UUID playerId,
            QuestDefinition quest, String requested, boolean starting, long time) throws IOException {
        var instance = data.open(content, time);
        var definition = content.definition();
        String initial = QuestLifecycleService.initialStage(quest);
        CampaignDefinition.Milestone selected = null;
        for (var milestone : definition.milestones().values()) {
            if (milestone.quest().equals(quest.id()) && entryStage(milestone, initial).equals(requested)) {
                selected = milestone;
                break;
            }
        }
        if (selected == null && starting) {
            var player = instance.players().get(playerId);
            for (var milestone : definition.milestones().values()) {
                if (milestone.quest().equals(quest.id()) || milestone.catchUp() == null || !milestone.catchUp().quest().equals(quest.id())
                        || !instance.outcomes().containsKey(milestone.id())) continue;
                var progress = player == null ? null : player.milestones().get(milestone.id());
                if (progress != null && progress.route() != CampaignInstance.Route.CATCH_UP) continue;
                if (!CampaignProgression.chapter(definition, instance, playerId, milestone.chapter()).available()) continue;
                selected = milestone;
                break;
            }
        }
        if (selected == null) return Entry.original(quest.id(), requested);
        var entered = data.enterMilestone(definition, playerId, selected.id(), time);
        if (entered.entry() == null) return Entry.blocked(quest.id(), requested, String.join(" ", entered.blockers()));
        var entry = entered.entry();
        String stage = entry.stage().isBlank() ? initial : entry.stage();
        if (!entry.quest().equals(quest.id())) {
            return Entry.blocked(quest.id(), requested, "This shared event has already resolved. Continue with quest " + entry.quest() + ".");
        }
        if (!quest.stages().containsKey(stage)) return Entry.blocked(quest.id(), requested, "The authored campaign entry stage is unavailable: " + stage);
        boolean catchingUp = entry.route() == CampaignInstance.Route.CATCH_UP;
        return new Entry(quest.id(), stage, catchingUp, catchingUp && selected.quest().equals(quest.id()), entry.worldOperation(), "");
    }

    public static String completionIssue(ServerLevel level, UUID playerId, QuestDefinition quest,
            VillagerQuestSavedData.QuestProgress progress) {
        if (quest.campaign() == null) return "";
        var content = CampaignResources.current(level.getServer()).campaigns().get(quest.campaign());
        if (content == null) return "Campaign content is unavailable.";
        var instance = CampaignSavedData.get(level).instances().get(quest.campaign());
        if (instance == null) return "Campaign progress has not started.";
        for (var milestone : content.definition().milestones().values()) {
            if (!milestone.quest().equals(quest.id())) continue;
            var entry = CampaignProgression.entry(content.definition(), instance, playerId, milestone.id());
            if (entry == null) return "Reach milestone " + milestone.id() + " before completing this quest.";
            if (entry.route() == CampaignInstance.Route.CATCH_UP
                    && (!entry.quest().equals(quest.id()) || !progress.campaignAlternateEntry())) {
                return "Continue the authored catch-up path for milestone " + milestone.id() + ".";
            }
            if (milestone.irreversible() && entry.worldOperation() == null) return "Resolve milestone " + milestone.id() + " before completing this quest.";
            if (!milestone.irreversible() && !entry.completed()) return "Resolve your personal outcome for milestone " + milestone.id() + ".";
        }
        return "";
    }

    public static boolean syncCompletion(ServerPlayer player, QuestDefinition quest, VillagerQuestSavedData.QuestProgress progress) {
        if (quest.campaign() == null || progress.completionCount() == 0
                || !CampaignQuestAccess.contentIssue(player.serverLevel(), quest, progress).isBlank()
                || !completionIssue(player.serverLevel(), player.getUUID(), quest, progress).isBlank()) return false;
        var server = player.serverLevel().getServer();
        var content = CampaignResources.current(server).campaigns().get(quest.campaign());
        var data = CampaignSavedData.get(player.serverLevel());
        var reports = REPORTED.computeIfAbsent(server, ignored -> new HashMap<>());
        String reportKey = player.getUUID() + "/" + quest.id();
        try {
            var instance = data.open(content, player.serverLevel().getGameTime());
            boolean changed = false;
            for (var milestone : content.definition().milestones().values()) {
                var entry = CampaignProgression.entry(content.definition(), instance, player.getUUID(), milestone.id());
                if (entry == null || entry.completed() || !entry.quest().equals(quest.id()) || entry.worldOperation() == null) continue;
                UUID operation = UUID.nameUUIDFromBytes((instance.instanceId() + "/" + player.getUUID() + "/"
                        + progress.questRunId() + "/" + milestone.id() + "/completion").getBytes(StandardCharsets.UTF_8));
                var completed = data.completeMilestone(content.definition(), player.getUUID(), milestone.id(), entry.route(),
                        entry.outcome(), operation, player.serverLevel().getGameTime());
                if (completed.status() != CampaignSavedData.Status.APPLIED && completed.status() != CampaignSavedData.Status.ALREADY_SATISFIED) {
                    throw new IOException(String.join(" ", completed.blockers()));
                }
                changed |= completed.status() == CampaignSavedData.Status.APPLIED;
            }
            changed |= data.completeQuest(content.definition(), player.getUUID(), quest.id());
            reports.remove(reportKey);
            return changed;
        } catch (IOException exception) {
            String diagnostic = exception.toString();
            if (!diagnostic.equals(reports.put(reportKey, diagnostic))) {
                LOGGER.error("Could not reconcile completed campaign quest {} for {}", quest.id(), player.getUUID(), exception);
            }
            return false;
        }
    }

    private static String entryStage(CampaignDefinition.Milestone milestone, String initial) {
        return milestone.stage().isBlank() ? initial : milestone.stage();
    }

    public record Entry(ResourceLocation quest, String stage, boolean catchUp, boolean alternateStage, UUID worldOperation, String diagnostic) {
        public boolean available() { return diagnostic.isBlank(); }

        private static Entry original(ResourceLocation quest, String stage) {
            return new Entry(quest, stage, false, false, null, "");
        }

        private static Entry blocked(ResourceLocation quest, String stage, String diagnostic) {
            return new Entry(quest, stage, false, false, null, diagnostic);
        }
    }
}
