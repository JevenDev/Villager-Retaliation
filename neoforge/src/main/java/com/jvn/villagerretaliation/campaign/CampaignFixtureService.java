package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignFixtureEntry;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class CampaignFixtureService {
    private CampaignFixtureService() { }

    public static Preview inspect(MinecraftServer server, ResourceLocation campaign, String entryId, UUID player) {
        String issue = environment(server);
        if (!issue.isBlank()) return new Preview(null, List.of(), issue);
        var content = CampaignResources.current(server).campaigns().get(campaign);
        if (content == null) return new Preview(null, List.of(), "Restore the campaign's valid datapack before entering a fixture chapter.");
        var entry = content.definition().fixtureEntries().get(entryId);
        if (entry == null || player == null) return new Preview(null, List.of(), "Choose an authored fixture entry and target player.");
        var data = CampaignSavedData.get(server.overworld());
        var token = data.fixtureToken(content.definition());
        var lines = describe(content, entry);
        issue = data.compatibility(content.definition());
        if (issue.isBlank()) issue = activity(server, content, player);
        if (issue.isBlank()) {
            try {
                CampaignFixtureSetup.prepare(content, data.instances().get(campaign),
                        new Request(campaign, entryId, player, token, UUID.randomUUID(), player, "preview fixture entry"), server.overworld().getGameTime());
            } catch (IllegalArgumentException exception) { issue = exception.getMessage(); }
        }
        return new Preview(token, lines, issue);
    }

    public static Result enter(MinecraftServer server, Request request) {
        String issue = environment(server);
        if (!issue.isBlank() || request == null) return new Result(null, false, issue.isBlank() ? "Fixture request is missing." : issue);
        var content = CampaignResources.current(server).campaigns().get(request.campaign());
        if (content == null) return new Result(null, false, "Restore the campaign's valid datapack before entering a fixture chapter.");
        var data = CampaignSavedData.get(server.overworld());
        issue = data.compatibility(content.definition());
        if (!issue.isBlank()) return new Result(null, false, issue);
        var current = data.instances().get(request.campaign());
        var prior = current == null ? null : current.fixtureEntries().get(request.operation());
        if (prior == null) {
            issue = activity(server, content, request.player());
            if (!issue.isBlank()) return new Result(null, false, issue);
        }
        CampaignFixtureReceipt receipt;
        try { receipt = data.enterFixture(content, request, server.overworld().getGameTime()); }
        catch (IOException | IllegalArgumentException exception) { return new Result(null, false, "Fixture entry could not be saved: " + exception.getMessage()); }
        issue = CampaignLocationVariants.reconcile(server, request.campaign());
        return new Result(receipt, issue.isBlank(), issue.isBlank() ? "" : "Fixture state and audit are saved. Location changes are pending: " + issue);
    }

    private static String environment(MinecraftServer server) {
        if (server == null || !server.isSameThread()) return "Fixture entry requires the server thread.";
        return CampaignWorldModeSavedData.get(server.overworld()).mode() == CampaignWorldMode.CAMPAIGN ? "" : "Fixture chapter entry requires Campaign Mode.";
    }

    private static String activity(MinecraftServer server, CampaignContentCatalog.Entry content, UUID player) {
        var quests = VillagerQuestSavedData.get(server.overworld());
        if (quests.futureVersion() || quests.campaignPersistencePending()) return "Restore readable, saved quest progress before entering a fixture.";
        var campaign = content.definition().id();
        if (quests.activeProgress(player).stream().anyMatch(entry -> campaign.toString().equals(entry.getValue().campaignId())
                || content.definition().questBundles().contains(entry.getKey()))) return "Finish or abandon this player's active campaign quest before entering a fixture chapter.";
        var conversations = ConversationSavedData.get(server.overworld());
        if (!conversations.diagnostic().isBlank()) return conversations.diagnostic();
        if (conversations.snapshots().values().stream().anyMatch(session -> session.phase() != ConversationSession.Phase.COMPLETE
                && campaign.equals(session.identity().campaign()) && session.participants().contains(player)))
            return "Finish this player's active campaign conversation before entering a fixture chapter.";
        var scenes = SceneSavedData.get(server.overworld());
        if (scenes.futureVersion()) return "Restore compatible scene data before entering a fixture chapter.";
        if (scenes.active().stream().anyMatch(scene -> campaign.equals(scene.owningCampaignId()) && scene.participants().contains(player)))
            return "Finish or cancel this player's active campaign scene before entering a fixture chapter.";
        return "";
    }

    private static List<String> describe(CampaignContentCatalog.Entry content, CampaignFixtureEntry entry) {
        var lines = new ArrayList<String>();
        lines.add("Chapter: " + entry.chapter() + ". Setup records campaign progress without granting participation or rewards.");
        lines.add("Source: " + content.definition().source() + "#" + entry.pointer() + " [" + content.sourcePack() + "]");
        for (var effect : entry.effects()) {
            String prefix = effect.id() + ": ";
            if (effect instanceof CampaignFixtureEntry.WorldOutcome outcome) {
                lines.add(prefix + "world outcome " + outcome.milestone() + " = " + outcome.outcome() + ". Existing conflicting outcomes are refused.");
                for (String id : content.definition().milestones().get(outcome.milestone()).outcomes().get(outcome.outcome()).locationVariants()) {
                    var variant = content.definition().locationVariants().get(id);
                    lines.add("Location variant " + id + " at " + variant.location() + ", layout " + variant.layout() + " revision " + variant.layoutRevision() + ".");
                    variant.cells().forEach(cell -> lines.add("Owned cell " + cell.id() + ": " + cell.expected() + " -> " + cell.placed()));
                }
            } else if (effect instanceof CampaignFixtureEntry.CompleteMilestone completion)
                lines.add(prefix + "personal milestone " + completion.milestone() + " = " + completion.outcome() + ", completed.");
            else if (effect instanceof CampaignFixtureEntry.CompleteQuest completion)
                lines.add(prefix + "campaign quest completion " + completion.quest() + ".");
            else if (effect instanceof CampaignFixtureEntry.SetFact fact)
                lines.add(prefix + "set " + content.definition().facts().get(fact.fact()).scope().name().toLowerCase(java.util.Locale.ROOT)
                        + " fact " + fact.fact() + " = " + fact.value() + ".");
        }
        return List.copyOf(lines);
    }

    public record Request(ResourceLocation campaign, String entry, UUID player, UUID expected, UUID operation, UUID operator, String reason) {
        public Request {
            CampaignStateCodec.require(campaign != null && CampaignFactFields.validId(entry) && player != null && expected != null
                    && operation != null && operator != null && reason != null && !reason.isBlank() && reason.length() <= 256,
                    "Fixture entry requires its campaign, entry, player, expected token, operation, operator, and reason.");
        }
    }

    public record Preview(UUID token, List<String> effects, String diagnostic) {
        public Preview { effects = List.copyOf(effects); }
        public boolean available() { return token != null && diagnostic.isBlank(); }
    }

    public record Result(CampaignFixtureReceipt receipt, boolean ready, String diagnostic) { }
}
