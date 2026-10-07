package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.network.CampaignJournalPagePayload;
import com.jvn.villagerretaliation.network.CampaignJournalRequestPayload;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignJournalService {
    private CampaignJournalService() {
    }

    public static void handle(ServerPlayer player, CampaignJournalRequestPayload request) {
        VillagerReputationNetworking.sendCampaignJournalPage(player, page(CampaignJournalPresenter.entries(player), request));
    }

    static CampaignJournalPagePayload page(List<CampaignJournalPresenter.Entry> entries, CampaignJournalRequestPayload request) {
        if (request.campaignId().isBlank()) {
            int pages = pages(entries.size());
            int page = Math.clamp(request.page(), 0, pages - 1);
            var summaries = slice(entries, page).stream().map(entry ->
                    new CampaignJournalPagePayload.Summary(entry.campaignId(), shortTitle(entry.title()))).toList();
            return new CampaignJournalPagePayload(request.requestId(), "", page, pages, summaries, List.of());
        }
        var entry = entries.stream().filter(value -> value.campaignId().equals(request.campaignId())).findFirst().orElse(null);
        var paragraphs = entry == null ? List.of("This campaign is currently unavailable.") : paragraphs(entry);
        int pages = pages(paragraphs.size());
        int page = Math.clamp(request.page(), 0, pages - 1);
        return new CampaignJournalPagePayload(request.requestId(), request.campaignId(), page, pages, List.of(), slice(paragraphs, page));
    }

    static List<String> paragraphs(CampaignJournalPresenter.Entry entry) {
        var result = new ArrayList<String>();
        append(result, entry.title());
        if (!entry.diagnostic().isBlank()) append(result, entry.diagnostic());
        for (var chapter : entry.chapters()) {
            String state = chapter.completed() ? "Completed" : chapter.available() ? "Available" : "Unavailable";
            append(result, "Chapter: " + chapter.title() + "\n" + state + " (" + chapter.completedQuests() + "/" + chapter.totalQuests() + " quests)"
                    + (chapter.blockers().isEmpty() ? "" : "\n" + String.join("\n", chapter.blockers())));
        }
        for (var milestone : entry.milestones()) {
            String name = milestone.title().isBlank() ? "Shared event" : milestone.title();
            String world = milestone.resolved() ? milestone.discovered() ? milestone.outcomeTitle() : "Resolved, details undiscovered" : "Unresolved";
            String personal = milestone.completed() ? "Completed" : milestone.entered() ? "In progress" : "Not started";
            String catchUp = milestone.catchUp() ? "\nCatch-up path" + (milestone.catchUpTitle().isBlank() ? "" : ": " + milestone.catchUpTitle()) : "";
            append(result, name + "\nWorld outcome: " + world + "\nParticipation: " + (milestone.participated() ? "You participated" : "No participation recorded")
                    + "\nYour progress: " + personal + catchUp);
        }
        return List.copyOf(result);
    }

    private static void append(List<String> paragraphs, String text) {
        while (text.length() > CampaignJournalPagePayload.MAX_PARAGRAPH_LENGTH) {
            int end = CampaignJournalPagePayload.MAX_PARAGRAPH_LENGTH;
            if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
            paragraphs.add(text.substring(0, end));
            text = text.substring(end);
        }
        if (!text.isEmpty()) paragraphs.add(text);
    }

    private static String shortTitle(String title) {
        if (title.length() <= 512) return title;
        int end = Character.isHighSurrogate(title.charAt(510)) ? 510 : 511;
        return title.substring(0, end) + "…";
    }

    private static int pages(int count) {
        return Math.max(1, (count + CampaignJournalPagePayload.PAGE_SIZE - 1) / CampaignJournalPagePayload.PAGE_SIZE);
    }

    private static <T> List<T> slice(List<T> values, int page) {
        int start = page * CampaignJournalPagePayload.PAGE_SIZE;
        return values.subList(start, Math.min(values.size(), start + CampaignJournalPagePayload.PAGE_SIZE));
    }
}
