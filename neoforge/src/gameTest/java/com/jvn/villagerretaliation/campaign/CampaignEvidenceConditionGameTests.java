package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEvidenceConditionGameTests {
    private static final ResourceLocation CONVERSATION = ResourceLocation.parse("campaign_fixture:ledger_challenge");

    private CampaignEvidenceConditionGameTests() { }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void personalKnowledgeSurvivesReloadUnloadingAndNewQuestRuns(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var source = fixture.source(1, 2);
            var first = fixture.player();
            var second = fixture.player();
            fixture.accept(first); fixture.accept(second); fixture.party(first, second);
            var condition = new CampaignEvidencePredicate(fixture.base.campaignId, "ledger");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), first, fixture.mentor);
            first.getInventory().add(new ItemStack(Items.WRITTEN_BOOK));
            helper.assertFalse(condition.evaluate(context).matches(), "inventory possession cannot replace an investigation");
            helper.assertTrue(CampaignEvidenceService.investigate(first, source).applied(), "first player investigates");
            helper.assertTrue(condition.evaluate(context).matches(), "saved discovery unlocks evidence");
            helper.assertFalse(condition.evaluate(VillagerInteractionService.createDialogueContext(helper.getLevel(), second, fixture.mentor)).matches(),
                    "party membership never copies another player's knowledge");
            fixture.base.reload(); fixture.reload();
            source.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(condition.evaluate(context).matches(), "saved knowledge needs no loaded source");
            helper.assertFalse(fixture.resolve().available(), "source remains unloaded");
            var progress = fixture.context(first).progress();
            progress.start(fixture.mentor.getUUID(), first.serverLevel().dimension(), fixture.mentor.blockPosition(), first.serverLevel().getGameTime());
            progress.beginRun(first.getUUID(), fixture.base.campaignId, null);
            progress.setCurrentStage("inspect");
            helper.assertFalse(fixture.complete(first), "a new objective still requires its own investigation");
            helper.assertTrue(condition.evaluate(context).matches(), "dialogue retains previously discovered information");
            helper.assertValueEqual(fixture.data().receipts().size(), 1, "reading knowledge creates no extra receipts");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void unavailableEvidenceCannotUnlockANegatedCondition(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player);
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, fixture.mentor);
            var condition = new DialogueCondition.CampaignEvidence(new CampaignEvidencePredicate(fixture.base.campaignId, "ledger"));
            var absent = new DialogueCondition.Not(condition);
            helper.assertTrue(DialogueCondition.matchesAll(context, List.of(absent)), "valid undiscovered evidence permits its authored alternative");
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(fixture.base.catalog, Map.of(), List.of()));
            helper.assertFalse(absent.unavailableReason(context).isBlank(), "missing content is diagnosed");
            helper.assertFalse(DialogueCondition.matchesAll(context, List.of(absent)), "missing content does not mean evidence was not discovered");
            CampaignResources.installForTests(fixture.base.server, fixture.base.campaigns);
            fixture.base.mode("sandbox");
            helper.assertFalse(DialogueCondition.matchesAll(context, List.of(absent)), "sandbox cannot unlock a negated campaign condition");
            fixture.base.mode("campaign");
            var malformed = fixture.saved(); malformed.putInt("DataVersion", 2); fixture.reload(malformed);
            helper.assertFalse(DialogueCondition.matchesAll(context, List.of(absent)), "unreadable evidence remains unavailable under negation");
            helper.assertFalse(absent.unavailableReason(context).isBlank(), "unreadable evidence gives a repair diagnostic");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void changedContentAndCampaignInstancesDoNotReuseKnowledge(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var source = fixture.source(1, 2); var player = fixture.player(); fixture.accept(player);
            helper.assertTrue(CampaignEvidenceService.investigate(player, source).applied(), "fixture discovers evidence");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, fixture.mentor);
            var condition = new CampaignEvidencePredicate(fixture.base.campaignId, "ledger");
            var original = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId);
            helper.assertFalse(fixture.data().discovered(fixture.base.campaignId, UUID.randomUUID(), original.fingerprint(), player.getUUID(), "ledger"),
                    "a different campaign instance cannot use an earlier discovery");
            var changed = fixture.base.raw();
            changed.getAsJsonArray("evidence").get(0).getAsJsonObject().addProperty("search_radius", 9);
            fixture.base.install(changed);
            helper.assertFalse(condition.evaluate(context).matches(), "changed content cannot reuse saved knowledge");
            helper.assertFalse(condition.evaluate(context).diagnostic().isBlank(), "changed content is diagnosed");
            CampaignResources.installForTests(fixture.base.server, fixture.base.campaigns);
            helper.assertTrue(condition.evaluate(context).matches(), "restoring matching content recovers discovery");
            helper.assertFalse(new CampaignEvidencePredicate(fixture.base.campaignId, "missing").evaluate(context).diagnostic().isBlank(),
                    "an unknown authored evidence ID is unavailable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void authoredChallengeRejectsInventedProofAndResumesTheSavedChoice(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper); var storage = new ConversationTestStorage(helper.getLevel())) {
            var source = fixture.source(1, 2); var player = fixture.player(); fixture.accept(player);
            var unavailableTopic = ConversationService.start(player, CONVERSATION, "discovery", UUID.randomUUID());
            helper.assertTrue(unavailableTopic.session() == null, "undiscovered evidence cannot open the follow-up topic");
            var result = ConversationService.start(player, CONVERSATION, "shipment", UUID.randomUUID());
            helper.assertTrue(result.session() != null, "authored challenge starts: " + result.diagnostic());
            result = intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(available(result, "proof"), "proof requires personal discovery");
            helper.assertTrue(available(result, "uncertain"), "authored missing-evidence alternative is playable");
            var denied = intent(player, result, ConversationSession.Intent.CHOOSE, "proof");
            helper.assertFalse(denied.diagnostic().isBlank(), "server rejects a fabricated proof response");
            helper.assertValueEqual(denied.session().node(), "challenge", "rejected intent cannot leave the current node");
            result = intent(player, result, ConversationSession.Intent.CHOOSE, "uncertain");
            helper.assertValueEqual(result.session().node(), "unproved", "refusal follows the authored alternative");
            result = intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "alternative conversation finishes");
            helper.assertTrue(CampaignEvidenceService.investigate(player, source).applied(), "player investigates the actual source");
            source.discard(); fixture.reload();
            result = ConversationService.start(player, CONVERSATION, "shipment", UUID.randomUUID());
            result = intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(available(result, "proof"), "later conversation reads saved knowledge");
            helper.assertFalse(available(result, "uncertain"), "old missing-evidence response is no longer available");
            var before = result;
            var request = UUID.randomUUID();
            result = ConversationService.request(player, before.session().identity().id(), before.session().revision(), request,
                    ConversationSession.Intent.CHOOSE, "proof");
            helper.assertValueEqual(result.session().node(), "proved", "proof follows the authored challenge branch");
            var transcript = result.session().transcript();
            storage.reload(); fixture.reload();
            var resumed = ConversationService.view(player, result.session().identity().id());
            helper.assertValueEqual(resumed.session().transcript(), transcript, "saved admission survives reload");
            var duplicate = ConversationService.request(player, before.session().identity().id(), before.session().revision(), request,
                    ConversationSession.Intent.CHOOSE, "proof");
            helper.assertValueEqual(duplicate.session().transcript(), transcript, "duplicate choice cannot replay the admission");
            result = intent(player, resumed, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "proved conversation finishes");
            var followUp = ConversationService.start(player, CONVERSATION, "discovery", UUID.randomUUID());
            helper.assertTrue(followUp.session() != null && followUp.session().node().equals("proved"), "saved knowledge opens the follow-up topic");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void unloadedIssuerKeepsPersonalEvidenceConditionsAndUnknownStates(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper, quest -> quest.getAsJsonObject("availability").add("active",
                com.google.gson.JsonParser.parseString("""
                        {"conditions":[{"type":"campaign_evidence","campaign":"campaign_fixture:investigation","evidence":"ledger"}],
                         "hide_when_unmet":false,"pause_progress_when_unmet":false}
                        """)))) {
            var source = fixture.source(1, 2); var player = fixture.player(); fixture.accept(player);
            fixture.mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var before = com.jvn.villagerretaliation.quest.VillagerQuestService.debugInspectQuest(player, fixture.base.campaignId);
            helper.assertTrue(before.lines().stream().anyMatch(line -> line.contains("active_conditions=unmet")),
                    "undiscovered evidence stays unmet with an unloaded issuer: " + before.lines());
            helper.assertTrue(CampaignEvidenceService.investigate(player, source).applied(), "investigation can use its source while the issuer is unloaded");
            var after = com.jvn.villagerretaliation.quest.VillagerQuestService.debugInspectQuest(player, fixture.base.campaignId);
            helper.assertTrue(after.lines().stream().anyMatch(line -> line.contains("active_conditions=met")),
                    "recorded discovery stays met with an unloaded issuer: " + after.lines());
            var saved = fixture.saved(); saved.putInt("DataVersion", 2); fixture.reload(saved);
            var unavailable = com.jvn.villagerretaliation.quest.VillagerQuestService.debugInspectQuest(player, fixture.base.campaignId);
            helper.assertTrue(unavailable.lines().stream().anyMatch(line -> line.contains("active_conditions=unknown")),
                    "unreadable evidence stays unknown with an unloaded issuer: " + unavailable.lines());
        }
        helper.succeed();
    }

    private static boolean available(ConversationService.Result result, String id) {
        return result.responses().stream().anyMatch(response -> response.id().equals(id) && response.available());
    }

    private static ConversationService.Result intent(ServerPlayer player, ConversationService.Result result, ConversationSession.Intent intent, String argument) {
        return ConversationService.request(player, result.session().identity().id(), result.session().revision(), UUID.randomUUID(), intent, argument);
    }
}
