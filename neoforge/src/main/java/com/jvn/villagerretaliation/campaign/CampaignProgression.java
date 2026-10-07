package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class CampaignProgression {
    private CampaignProgression() {
    }

    public static ChapterAccess chapter(CampaignDefinition definition, CampaignInstance instance, UUID playerId, String chapterId) {
        var chapter = definition.chapters().get(chapterId);
        if (chapter == null) return new ChapterAccess(false, false, List.of("Unknown chapter " + chapterId));
        var player = instance == null ? CampaignInstance.PlayerRecord.empty()
                : instance.players().getOrDefault(playerId, CampaignInstance.PlayerRecord.empty());
        List<String> blockers = new ArrayList<>();
        for (var prerequisite : chapter.prerequisites()) {
            if (!prerequisite.chapter().isBlank()) {
                if (!chapterCompleted(definition, player, prerequisite.chapter())) blockers.add("Complete chapter " + prerequisite.chapter());
            } else {
                var progress = player.milestones().get(prerequisite.milestone());
                if (progress == null || !progress.completed()) blockers.add("Complete milestone " + prerequisite.milestone());
                else if (!prerequisite.outcome().isBlank() && !prerequisite.outcome().equals(progress.outcome())) {
                    blockers.add("Milestone " + prerequisite.milestone() + " requires outcome " + prerequisite.outcome()
                            + ", but resolved as " + progress.outcome());
                }
            }
        }
        return new ChapterAccess(blockers.isEmpty(), chapterCompleted(definition, player, chapterId), blockers);
    }

    private static boolean chapterCompleted(CampaignDefinition definition, CampaignInstance.PlayerRecord player, String chapterId) {
        var chapter = definition.chapters().get(chapterId);
        if (chapter == null || !player.completedQuests().containsAll(chapter.quests())) return false;
        for (var milestone : definition.milestones().values()) {
            if (!milestone.chapter().equals(chapterId)) continue;
            var progress = player.milestones().get(milestone.id());
            if (progress == null || !progress.completed()) return false;
        }
        return true;
    }

    static Set<ResourceLocation> completedQuests(CampaignDefinition definition,
            Map<String, CampaignInstance.MilestoneProgress> milestones, Set<ResourceLocation> completedQuests) {
        Set<ResourceLocation> completed = new HashSet<>(completedQuests);
        boolean changed;
        do {
            changed = false;
            for (var milestone : definition.milestones().values()) {
                var progress = milestones.get(milestone.id());
                var alternate = milestone.catchUp();
                if (progress == null || !progress.completed() || progress.route() != CampaignInstance.Route.CATCH_UP
                        || alternate == null || !completed.contains(alternate.quest())) continue;
                boolean allResolved = definition.milestones().values().stream().filter(value -> value.quest().equals(milestone.quest()))
                        .allMatch(value -> milestones.containsKey(value.id()) && milestones.get(value.id()).completed());
                if (allResolved) changed |= completed.add(milestone.quest());
            }
        } while (changed);
        return Set.copyOf(completed);
    }

    static CampaignInstance.MilestoneProgress enter(CampaignInstance.OutcomeCommit committed, long gameTime) {
        return new CampaignInstance.MilestoneProgress(committed == null ? CampaignInstance.Route.ORIGINAL : CampaignInstance.Route.CATCH_UP,
                committed == null ? "" : committed.outcome(), committed == null ? null : committed.operationId(), gameTime, null, -1L);
    }

    static CampaignInstance.MilestoneProgress reconcile(CampaignInstance.MilestoneProgress progress,
            CampaignInstance.OutcomeCommit outcome, boolean participant) {
        return new CampaignInstance.MilestoneProgress(participant ? progress.route() : CampaignInstance.Route.CATCH_UP,
                outcome.outcome(), outcome.operationId(), progress.enteredAt(), progress.completionOperation(), progress.completedAt());
    }

    public static Entry entry(CampaignDefinition definition, CampaignInstance instance, UUID playerId, String milestoneId) {
        var milestone = definition.milestones().get(milestoneId);
        var player = instance.players().get(playerId);
        var progress = player == null ? null : player.milestones().get(milestoneId);
        if (milestone == null || progress == null) return null;
        var catchUp = milestone.catchUp();
        boolean catchingUp = progress.route() == CampaignInstance.Route.CATCH_UP;
        if (catchingUp && catchUp == null) return null;
        return new Entry(milestoneId, progress.route(), catchingUp ? catchUp.quest() : milestone.quest(),
                catchingUp ? catchUp.stage() : milestone.stage(), progress.outcome(), progress.worldOperation(), progress.completed());
    }

    public record ChapterAccess(boolean available, boolean completed, List<String> blockers) {
        public ChapterAccess {
            blockers = List.copyOf(blockers);
        }
    }

    public record Entry(String milestone, CampaignInstance.Route route, ResourceLocation quest, String stage,
            String outcome, UUID worldOperation, boolean completed) {
    }
}
