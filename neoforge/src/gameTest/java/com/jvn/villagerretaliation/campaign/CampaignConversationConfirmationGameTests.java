package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationConfirmationGameTests {
    private static final String START = """
            [{"id":"before","action":{"type":"experience","amount":7}},
             {"id":"accept","action":{"type":"quest","quest":"campaign_fixture:characters","action":"start","required":true}},
             {"id":"after","action":{"type":"experience","amount":13}}]
            """;
    private static final String DECIDE = """
            [{"id":"before","action":{"type":"experience","amount":7}},
             {"id":"decide","action":{"type":"campaign_outcome","quest":"campaign_fixture:characters","milestone":"decision","outcome":"help","operation_id":"resolve"}},
             {"id":"after","action":{"type":"experience","amount":13}}]
            """;
    private CampaignConversationConfirmationGameTests() { }

    @GameTest(template = "empty", batch = "conversation_confirmations")
    public static void declinedConfirmationRestoresItsOriginalResponseDeadline(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, START, false)) {
            var opening = fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
            opening.add("response_timer", JsonParser.parseString("""
                    {"duration_ticks":40,"next":"declined"}
                    """));
            fixture.install(fixture.graph);
            var player = fixture.player();
            var result = fixture.start(player);
            long deadline = result.session().responseDeadline();
            helper.assertTrue(deadline >= 0, "the response timer is armed");
            result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "appeal");
            helper.assertTrue(result.session().confirmation() != null, "the chosen response needs confirmation");
            helper.assertTrue(result.session().responseTimerResolved(), "confirmation suspends timeout resolution");
            UUID id = result.session().identity().id();
            fixture.storage.reload();
            result = ConversationService.view(player, id);
            result = fixture.intent(player, result, ConversationSession.Intent.DECLINE, result.session().confirmation().id().toString());
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "decline returns to the choices");
            helper.assertFalse(result.session().responseTimerResolved(), "decline restores timeout resolution");
            helper.assertValueEqual(result.session().responseDeadline(), deadline, "decline does not extend the deadline");
            fixture.storage.reload();
            result = ConversationService.view(player, id);
            helper.assertFalse(result.session().responseTimerResolved(), "the restored timer survives a reload");
            fixture.storage.setResponseDeadline(id, helper.getLevel().getGameTime());
            fixture.storage.maintain();
            result = ConversationService.view(player, id);
            helper.assertValueEqual(result.session().node(), "declined", "the original silence destination still runs");
            helper.assertValueEqual(player.totalExperience, 0, "declined response effects never run");
            helper.assertTrue(progress(fixture, player) == null, "declining cannot accept the quest");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_confirmations")
    public static void acceptanceResumesAfterReloadAndDeclineAppliesNothing(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, START, false)) {
            var player = fixture.player();
            var result = choose(fixture, player);
            UUID id = result.session().identity().id();
            helper.assertTrue(result.session().confirmation() != null, "choice needs saved acceptance: " + result.diagnostic());
            helper.assertTrue(VillagerConversationService.sessionId(player, fixture.mentor) == null, "persistent confirmations do not need an ordinary session");
            helper.assertTrue(result.session().choices().isEmpty() && result.session().receipts().isEmpty(), "unconfirmed choice applies no effects or choice history");
            helper.assertValueEqual(player.totalExperience, 0, "even preceding effects await acceptance");
            var pending = result.session().confirmation();
            helper.assertValueEqual(CampaignConfirmation.load(pending.save()), pending, "confirmation round trips independently");
            fixture.storage.reload();
            result = ConversationService.view(player, id);
            helper.assertValueEqual(result.session().confirmation(), pending, "reconnect presents the same consequences");
            result = fixture.intent(player, result, ConversationSession.Intent.DECLINE, pending.id().toString());
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "decline returns to authored responses");
            helper.assertTrue(progress(fixture, player) == null, "decline does not start the quest");
            result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "appeal");
            var confirmation = result.session().confirmation();
            helper.assertFalse(confirmation.id().equals(pending.id()), "a new selection has a new confirmation identity");
            UUID request = UUID.randomUUID(); long revision = result.session().revision();
            result = ConversationService.request(player, id, revision, request, ConversationSession.Intent.CONFIRM, confirmation.id().toString());
            helper.assertTrue(progress(fixture, player) != null, "confirmed acceptance starts the quest: " + result.diagnostic());
            helper.assertValueEqual(player.totalExperience, 20, "effects before and after acceptance run once");
            helper.assertValueEqual(result.session().choices().size(), 1, "only accepted response enters choice history");
            fixture.storage.reload();
            ConversationService.request(player, id, revision, request, ConversationSession.Intent.CONFIRM, confirmation.id().toString());
            helper.assertValueEqual(player.totalExperience, 20, "duplicate approval cannot repeat effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_confirmations")
    public static void competingOutcomeRefreshesChoicesWithoutEffects(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, DECIDE, false)) {
            var player = fixture.player();
            acceptQuest(fixture, player);
            var result = choose(fixture, player);
            helper.assertTrue(result.session().confirmation() != null, "variable outcome requires its own confirmation: " + result.diagnostic());
            var confirmation = result.session().confirmation();
            var content = CampaignResources.current(fixture.base.server).campaigns().get(fixture.base.campaignId);
            var data = CampaignSavedData.get(helper.getLevel());
            var instance = data.instances().get(fixture.base.campaignId);
            var winner = data.commitWorldOutcome(content, "decision", "leave", UUID.randomUUID(), instance.worldRevision(),
                    CampaignInstance.Origin.PLAYER_DECISION, Set.of(UUID.randomUUID()), helper.getLevel().getGameTime());
            helper.assertValueEqual(winner.status(), CampaignSavedData.Status.APPLIED, "another decision wins first");
            result = fixture.intent(player, result, ConversationSession.Intent.CONFIRM, confirmation.id().toString());
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "losing approval refreshes the response list");
            helper.assertTrue(result.diagnostic().contains(winner.committed().title()), "loser sees the actual committed outcome");
            helper.assertValueEqual(player.totalExperience, 0, "losing choice cannot apply earlier or later rewards");
            helper.assertTrue(result.session().choices().isEmpty(), "loser has no committed conversation choice");
            helper.assertTrue(data.instances().get(fixture.base.campaignId).players().get(player.getUUID()).participation().isEmpty(),
                    "loser receives no participant credit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_confirmations")
    public static void handoverRequiresTheNewControllerToReviewTheirOwnOffer(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, START, false)) {
            var leader = fixture.player(); var member = fixture.player();
            fixture.party(leader, member);
            PartyService.setAdminPrivileges(leader, member.getUUID(), false);
            var result = choose(fixture, leader);
            result = fixture.intent(member, result, ConversationSession.Intent.JOIN, "");
            var pending = result.session().confirmation();
            var denied = fixture.intent(member, result, ConversationSession.Intent.CONFIRM, pending.id().toString());
            helper.assertValueEqual(denied.session().revision(), result.session().revision(), "listener cannot confirm");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            result = fixture.intent(leader, result, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "new controller reviews their own quest state");
            denied = fixture.intent(member, result, ConversationSession.Intent.CONFIRM, pending.id().toString());
            helper.assertFalse(denied.diagnostic().isBlank(), "old controller's consent cannot transfer");
            result = fixture.intent(member, result, ConversationSession.Intent.CHOOSE, "appeal");
            helper.assertValueEqual(result.session().confirmation().consent().playerId(), member.getUUID(), "fresh confirmation belongs to new controller");
            PartyService.setAdminPrivileges(leader, member.getUUID(), false);
            denied = fixture.intent(member, result, ConversationSession.Intent.CONFIRM, result.session().confirmation().id().toString());
            helper.assertValueEqual(denied.session().revision(), result.session().revision(), "revoked authority cannot approve a saved offer");
            helper.assertTrue(progress(fixture, member) == null, "revoked authority starts no quest");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            result = fixture.intent(member, result, ConversationSession.Intent.CONFIRM, result.session().confirmation().id().toString());
            helper.assertTrue(progress(fixture, member) != null && progress(fixture, leader) == null, "personal acceptance belongs to the confirming player");
            helper.assertValueEqual(member.totalExperience, 20, "confirmed effects belong to the new chooser");
            helper.assertValueEqual(leader.totalExperience, 0, "handover cannot accept on behalf of the old controller");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_confirmations")
    public static void unsavedApprovalRunsOnlyAfterStorageRecovers(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, START, false)) {
            var player = fixture.player();
            var result = choose(fixture, player);
            UUID id = result.session().identity().id();
            var confirmation = result.session().confirmation();
            fixture.storage.blockWrites();
            try {
                result = fixture.intent(player, result, ConversationSession.Intent.CONFIRM, confirmation.id().toString());
                helper.assertFalse(result.diagnostic().isBlank(), "failed approval save is visible");
                helper.assertValueEqual(player.totalExperience, 0, "unsaved acceptance applies no earlier effects");
                helper.assertTrue(progress(fixture, player) == null, "unsaved acceptance starts no quest");
            } finally { fixture.storage.restoreWrites(); }
            ConversationTestStorage.maintain(fixture.base.server);
            result = ConversationService.view(player, id);
            helper.assertTrue(progress(fixture, player) != null, "durable recovered approval can resume: " + result.diagnostic());
            helper.assertValueEqual(player.totalExperience, 20, "recovery applies the approved effects once");
            fixture.storage.reload();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(player.totalExperience, 20, "reloaded approval cannot replay effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_confirmations")
    public static void nodeEntryConfirmationCanBeDeferredWithoutSkippingItsEffects(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, START, true)) {
            var player = fixture.player();
            var result = fixture.start(player);
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CONFIRMATION, "node entry waits before all effects");
            result = fixture.intent(player, result, ConversationSession.Intent.DECLINE, result.session().confirmation().id().toString());
            helper.assertTrue(result.session().controller() == null, "deferring entry releases control");
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(player.totalExperience, 0, "maintenance cannot approve or skip the entry");
            result = fixture.intent(player, result, ConversationSession.Intent.TAKE_CONTROL, "");
            helper.assertTrue(result.session().confirmation() != null, "resuming entry presents its consequences again");
            result = fixture.intent(player, result, ConversationSession.Intent.CONFIRM, result.session().confirmation().id().toString());
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "confirmed entry continues to authored responses");
            helper.assertValueEqual(player.totalExperience, 20, "entry effects apply once after confirmation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_confirmations")
    public static void confirmedOutcomeSurvivesAnInterruptedScene(GameTestHelper helper) throws IOException {
        var effects = JsonParser.parseString(DECIDE).getAsJsonArray();
        effects.set(0, JsonParser.parseString("""
                {"id":"wait","action":{"type":"start_scene","scene_id":"campaign_fixture:character_greeting",
                 "operation_id":"reviewed_decision","wait_for_result":true,"required":true}}
                """));
        try (var fixture = fixture(helper, effects.toString(), false)) {
            var player = fixture.player();
            acceptQuest(fixture, player);
            var result = choose(fixture, player);
            result = fixture.intent(player, result, ConversationSession.Intent.CONFIRM, result.session().confirmation().id().toString());
            UUID id = result.session().identity().id();
            var receipt = result.session().receipts().values().iterator().next();
            helper.assertTrue(receipt.result().suspended(), "approved choice waits on its scene: " + result.diagnostic());
            helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().isEmpty(),
                    "approval does not jump ahead of scene ordering");
            fixture.storage.reload();
            var scenes = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel());
            scenes.get(receipt.result().suspensionId()).orElseThrow().complete(helper.getLevel().getGameTime());
            scenes.setDirty();
            ConversationTestStorage.maintain(fixture.base.server);
            result = ConversationService.view(player, id);
            var outcome = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision");
            helper.assertTrue(outcome != null && outcome.outcome().equals("help"), "saved consent resumes the approved outcome: " + result.diagnostic());
            helper.assertValueEqual(player.totalExperience, 13, "following reward waits for the committed outcome");
            fixture.storage.reload();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(player.totalExperience, 13, "recovery cannot repeat the following reward");
        }
        helper.succeed();
    }

    static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper, String effects, boolean entry) throws IOException {
        var fixture = new CampaignConversationGameTests.Fixture(helper, false, true);
        var opening = fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
        opening.add("exchanges", JsonParser.parseString("[]"));
        var response = opening.getAsJsonArray("responses").get(0).getAsJsonObject();
        response.remove("check");
        response.addProperty("next", "agreed");
        (entry ? opening : response).add("effects", JsonParser.parseString(effects));
        fixture.install(fixture.graph);
        return fixture;
    }

    private static ConversationService.Result choose(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        return fixture.intent(player, fixture.start(player), ConversationSession.Intent.CHOOSE, "appeal");
    }

    private static VillagerQuestSavedData.QuestProgress progress(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        return VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), fixture.base.campaignId);
    }

    static void acceptQuest(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) throws IOException {
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, fixture.mentor);
        var action = VillagerActionDefinition.readList(fixture.base.campaignId, "fixture", JsonParser.parseString("""
                {"actions":[{"type":"quest","quest":"campaign_fixture:characters","action":"start","required":true}]}
                """).getAsJsonObject()).getFirst();
        var prepared = CampaignQuestAcceptanceService.prepareActions(context, List.of(action));
        fixture.helper.assertTrue(prepared.confirmation() != null, "fixture quest requires acceptance: " + prepared.diagnostic());
        CampaignQuestAcceptanceService.withConsent(prepared.confirmation().acceptedConsent(player.serverLevel().getGameTime()),
                () -> VillagerQuestService.performAction(context, fixture.base.campaignId, VillagerActionDefinition.QuestAction.START));
        fixture.helper.assertTrue(progress(fixture, player) != null, "fixture quest accepted");
    }
}
