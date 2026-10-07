package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignJournalPresenter {
    private CampaignJournalPresenter() {
    }

    public static List<Entry> entries(ServerPlayer player) {
        return entries(CampaignWorldModeSavedData.get(player.serverLevel()).mode(), CampaignResources.current(player.server),
                CampaignSavedData.get(player.serverLevel()), player.getUUID(), VillagerLocale.locale(player));
    }

    static List<Entry> entries(CampaignWorldMode mode, CampaignContentCatalog catalog, CampaignSavedData data, UUID playerId, String locale) {
        var ids = new TreeSet<ResourceLocation>(java.util.Comparator.comparing(ResourceLocation::toString));
        ids.addAll(data.instances().keySet());
        if (mode == CampaignWorldMode.CAMPAIGN) ids.addAll(catalog.campaigns().keySet());
        var entries = new ArrayList<Entry>();
        for (var id : ids) {
            var content = catalog.campaigns().get(id);
            var instance = data.instances().get(id);
            var personal = instance == null ? CampaignInstance.PlayerRecord.empty()
                    : instance.players().getOrDefault(playerId, CampaignInstance.PlayerRecord.empty());
            String diagnostic = diagnostic(mode, data, content);
            boolean compatible = content != null && data.compatibility(content.definition()).isBlank();
            String title = compatible ? content.localization().plainText(locale, content.definition().titleKey()).orElse(id.toString())
                    : instance == null ? id.toString() : instance.title();
            var chapters = new ArrayList<Chapter>();
            var milestones = new ArrayList<Milestone>();
            if (compatible) {
                var definition = content.definition();
                for (var chapter : definition.chapters().values()) {
                    var access = CampaignProgression.chapter(definition, instance, playerId, chapter.id());
                    chapters.add(new Chapter(chapter.id(), content.localization().plainText(locale, chapter.titleKey()).orElse(chapter.id()),
                            diagnostic.isBlank() && access.available(), access.completed(), access.blockers(),
                            (int) chapter.quests().stream().filter(personal.completedQuests()::contains).count(), chapter.quests().size()));
                }
                for (var milestone : definition.milestones().values()) {
                    var committed = instance == null ? null : instance.outcomes().get(milestone.id());
                    var progress = personal.milestones().get(milestone.id());
                    boolean discovered = committed != null && committed.outcome().equals(personal.discoveredOutcomes().get(milestone.id()));
                    var outcome = committed == null ? null : milestone.outcomes().get(committed.outcome());
                    String outcomeTitle = !discovered ? "" : outcome == null ? committed.title()
                            : content.localization().plainText(locale, outcome.titleKey()).orElse(committed.title());
                    boolean catchUp = progress != null && progress.route() == CampaignInstance.Route.CATCH_UP
                            || progress == null && committed != null;
                    var alternate = catchUp ? milestone.catchUp() : null;
                    milestones.add(new Milestone(milestone.id(), milestone.chapter(), questTitle(catalog, milestone.quest(), locale),
                            committed != null, discovered ? committed.outcome() : "", outcomeTitle, discovered,
                            personal.participation().contains(milestone.id()), progress != null, progress != null && progress.completed(),
                            catchUp, alternate == null ? "" : alternate.quest().toString(),
                            alternate == null ? "" : questTitle(catalog, alternate.quest(), locale),
                            alternate == null ? "" : alternate.stage()));
                }
            } else if (instance != null) {
                var savedMilestones = new LinkedHashSet<>(instance.outcomes().keySet());
                savedMilestones.addAll(personal.milestones().keySet());
                for (var milestoneId : savedMilestones) {
                    var committed = instance.outcomes().get(milestoneId);
                    var progress = personal.milestones().get(milestoneId);
                    boolean discovered = committed != null && committed.outcome().equals(personal.discoveredOutcomes().get(milestoneId));
                    milestones.add(new Milestone(milestoneId, "", "", committed != null,
                            discovered ? committed.outcome() : "", discovered ? committed.title() : "", discovered,
                            personal.participation().contains(milestoneId), progress != null, progress != null && progress.completed(),
                            progress != null && progress.route() == CampaignInstance.Route.CATCH_UP, "", "", ""));
                }
            }
            entries.add(new Entry(id.toString(), title, diagnostic, instance == null ? 0L : instance.worldRevision(),
                    personal.revision(), chapters, milestones));
        }
        return List.copyOf(entries);
    }

    private static String diagnostic(CampaignWorldMode mode, CampaignSavedData data, CampaignContentCatalog.Entry content) {
        if (mode != CampaignWorldMode.CAMPAIGN) return "This world's mode does not enable campaign progression.";
        if (!data.diagnostic().isBlank()) return data.diagnostic();
        if (content == null) return "Campaign content is unavailable. Saved outcomes and personal progress are preserved.";
        return data.compatibility(content.definition());
    }

    private static String questTitle(CampaignContentCatalog catalog, ResourceLocation questId, String locale) {
        var quest = catalog.quests().quests().get(questId);
        return quest == null ? questId.toString()
                : catalog.quests().localization().plainText(locale, quest.titleKey()).orElse(quest.title());
    }

    public record Entry(String campaignId, String title, String diagnostic, long worldRevision, long personalRevision,
            List<Chapter> chapters, List<Milestone> milestones) {
        public Entry {
            chapters = List.copyOf(chapters);
            milestones = List.copyOf(milestones);
        }
    }

    public record Chapter(String id, String title, boolean available, boolean completed, List<String> blockers,
            int completedQuests, int totalQuests) {
        public Chapter { blockers = List.copyOf(blockers); }
    }

    public record Milestone(String id, String chapter, String title, boolean resolved, String outcome, String outcomeTitle,
            boolean discovered, boolean participated, boolean entered, boolean completed, boolean catchUp,
            String catchUpQuest, String catchUpTitle, String catchUpStage) {
    }
}
