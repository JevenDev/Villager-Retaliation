package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationDecisionRecoveryGameTests {
    private static final String EFFECTS = """
            [{"id":"wait","action":{"type":"start_scene","quest":"campaign_fixture:characters",
              "scene_id":"campaign_fixture:character_greeting","operation_id":"decision_recovery","wait_for_result":true,"required":true}},
             {"id":"decide","action":{"type":"campaign_outcome","quest":"campaign_fixture:characters",
              "milestone":"decision","outcome":"help","operation_id":"resolve"}},
             {"id":"personal","action":{"type":"experience","amount":13,"required":true}}]
            """;

    private CampaignConversationDecisionRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "conversation_decision_recovery")
    public static void authorizedHandoverFinishesTheWorldDecisionWhilePersonalEffectsWait(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var chooser = fixture.player(); var controller = fixture.player();
            fixture.party(chooser, controller);
            PartyService.setAdminPrivileges(chooser, controller.getUUID(), true);
            var pending = approve(fixture, chooser, controller);
            UUID session = pending.session().identity().id();
            UUID operation = pending.operation();
            disconnect(fixture, chooser);
            fixture.storage.reload();
            finishScene(fixture, pending.session());
            var result = fixture.intent(controller, ConversationService.view(controller, session), ConversationSession.Intent.TAKE_CONTROL, "");
            var outcome = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision");
            helper.assertTrue(outcome != null, "authorized handover commits the approved world decision: " + result.diagnostic());
            helper.assertValueEqual(outcome.operationId(), operation, "handover retains the original operation identity");
            helper.assertTrue(result.diagnostic().contains("personal effect"), "personal effects explain whose reconnect is needed");
            helper.assertValueEqual(controller.totalExperience, 0, "controller does not inherit the chooser's reward");
            helper.assertTrue(VillagerQuestSavedData.get(helper.getLevel()).get(controller.getUUID(), fixture.base.campaignId) == null,
                    "handover does not create personal quest progress");
            fixture.storage.reload();
            var returned = fixture.reconnect(chooser);
            result = fixture.intent(controller, ConversationService.view(controller, session), ConversationSession.Intent.RESUME, "");
            helper.assertValueEqual(returned.totalExperience, 13, "personal reward resumes for its original owner");
            helper.assertValueEqual(controller.totalExperience, 0, "resumption retains reward ownership");
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision"), outcome,
                    "personal recovery cannot repeat the world commit");
            fixture.storage.reload();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(returned.totalExperience, 13, "reload cannot repeat the personal effect");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_decision_recovery")
    public static void revokingTheChooserDoesNotRevokeAnAuthorizedSuccessor(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var leader = fixture.player(); var chooser = fixture.player();
            fixture.party(leader, chooser);
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), true);
            var pending = approve(fixture, chooser, leader);
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), false);
            ConversationTestStorage.maintain(fixture.base.server);
            finishScene(fixture, pending.session());
            var result = fixture.intent(leader, ConversationService.view(leader, pending.session().identity().id()), ConversationSession.Intent.TAKE_CONTROL, "");
            var outcome = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision");
            helper.assertTrue(outcome != null, "successor's authority resumes the saved decision: " + result.diagnostic());
            helper.assertValueEqual(outcome.operationId(), pending.operation(), "permission change does not create another operation");
            helper.assertValueEqual(chooser.totalExperience, 13, "chooser still owns the personal reward");
            helper.assertValueEqual(leader.totalExperience, 0, "successor controls execution without receiving the reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_decision_recovery")
    public static void handoverRevalidatesACompetingCommittedOutcome(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var chooser = fixture.player(); var controller = fixture.player();
            fixture.party(chooser, controller);
            PartyService.setAdminPrivileges(chooser, controller.getUUID(), true);
            var pending = approve(fixture, chooser, controller);
            disconnect(fixture, chooser);
            var data = CampaignSavedData.get(helper.getLevel());
            var content = CampaignResources.current(fixture.base.server).campaigns().get(fixture.base.campaignId);
            var winner = data.commitWorldOutcome(content, "decision", "leave", UUID.randomUUID(),
                    data.instances().get(fixture.base.campaignId).worldRevision(), CampaignInstance.Origin.PLAYER_DECISION,
                    Set.of(UUID.randomUUID()), helper.getLevel().getGameTime());
            finishScene(fixture, pending.session());
            var result = fixture.intent(controller, ConversationService.view(controller, pending.session().identity().id()), ConversationSession.Intent.TAKE_CONTROL, "");
            helper.assertTrue(result.diagnostic().contains(winner.committed().title()), "successor sees the actual world outcome");
            helper.assertValueEqual(controller.totalExperience, 0, "losing decision applies no later reward");
            helper.assertFalse(data.instances().get(fixture.base.campaignId).players().get(chooser.getUUID()).participation().contains("decision"),
                    "the approved losing choice receives no participant credit");
            helper.assertValueEqual(data.instances().get(fixture.base.campaignId).outcomes().get("decision"), winner.committed(), "handover cannot replace the winner");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_decision_recovery")
    public static void delegatedActionBoundaryRejectsWrongOwnersAndPersonalEffects(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var chooser = fixture.player(); var stranger = fixture.player();
            CampaignConversationConfirmationGameTests.acceptQuest(fixture, chooser);
            var action = VillagerActionDefinition.readList(fixture.base.campaignId, "fixture", JsonParser.parseString("""
                    {"actions":[{"type":"campaign_outcome","quest":"campaign_fixture:characters","milestone":"decision","outcome":"help","operation_id":"resolve"}]}
                    """).getAsJsonObject()).getFirst();
            var progress = VillagerQuestSavedData.get(helper.getLevel()).get(chooser.getUUID(), fixture.base.campaignId);
            var owner = new CampaignOutcomeService.Actor(chooser.getUUID(), progress.questRunId());
            var outsider = VillagerInteractionService.createDialogueContext(helper.getLevel(), stranger, fixture.mentor);
            helper.assertFalse(VillagerActionRegistry.execute(outsider, action, Map.of(), owner).success(), "unrelated controller cannot use another player's quest");
            fixture.party(chooser, stranger);
            helper.assertFalse(VillagerActionRegistry.execute(outsider, action, Map.of(), owner).success(), "listener without authority cannot resume a decision");
            PartyService.setAdminPrivileges(chooser, stranger.getUUID(), true);
            helper.assertFalse(VillagerActionRegistry.execute(outsider, action, Map.of(), owner).success(), "authority does not replace the original decision approval");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), chooser, fixture.mentor);
            var prepared = CampaignQuestAcceptanceService.prepareActions(context, java.util.List.of(action));
            var result = new ActionResult[1];
            CampaignQuestAcceptanceService.withConsent(prepared.confirmation().acceptedConsent(helper.getLevel().getGameTime()),
                    () -> result[0] = VillagerActionRegistry.execute(context, action, Map.of(), new CampaignOutcomeService.Actor(chooser.getUUID(), UUID.randomUUID())));
            helper.assertFalse(result[0].success(), "stale quest run cannot reuse the approval");
            var reward = VillagerActionDefinition.readList(fixture.base.campaignId, "fixture", JsonParser.parseString("{\"actions\":[{\"type\":\"experience\",\"amount\":13}]}").getAsJsonObject()).getFirst();
            helper.assertFalse(VillagerActionRegistry.execute(outsider, reward, Map.of(), owner).success(), "delegation cannot redirect a personal effect");
            helper.assertValueEqual(stranger.totalExperience, 0, "rejected delegation changes no personal state");
        }
        helper.succeed();
    }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper) throws IOException {
        return CampaignConversationConfirmationGameTests.fixture(helper, EFFECTS, false);
    }

    private static Pending approve(CampaignConversationGameTests.Fixture fixture, ServerPlayer chooser, ServerPlayer listener) throws IOException {
        CampaignConversationConfirmationGameTests.acceptQuest(fixture, chooser);
        var result = fixture.start(chooser);
        result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
        result = fixture.intent(chooser, result, ConversationSession.Intent.CHOOSE, "appeal");
        var confirmation = result.session().confirmation();
        fixture.helper.assertTrue(confirmation != null, "decision requires approval: " + result.diagnostic());
        var operation = confirmation.consent().decisions().keySet().iterator().next();
        result = fixture.intent(chooser, result, ConversationSession.Intent.CONFIRM, confirmation.id().toString());
        fixture.helper.assertTrue(result.session().receipts().values().iterator().next().result().suspended(), "approved decision waits for a scene");
        return new Pending(result.session(), operation);
    }

    private static void disconnect(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        ConversationService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        fixture.base.server.getPlayerList().remove(player);
        fixture.players.remove(player);
    }

    private static void finishScene(CampaignConversationGameTests.Fixture fixture, ConversationSession.Snapshot session) {
        var receipt = session.receipts().values().iterator().next();
        var scenes = SceneSavedData.get(fixture.helper.getLevel());
        scenes.get(receipt.result().suspensionId()).orElseThrow().complete(fixture.helper.getLevel().getGameTime());
        scenes.setDirty();
    }

    private record Pending(ConversationSession.Snapshot session, UUID operation) { }
}
