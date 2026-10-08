package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.io.IOException;
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
public final class CampaignConversationCheckRecoveryGameTests {
    private CampaignConversationCheckRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "conversation_check_recovery")
    public static void fixedOutcomeAfterARollUsesTheOriginalQuestDuringHandover(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, true)) {
            var chooser = fixture.player(); var successor = fixture.player();
            fixture.party(chooser, successor);
            PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
            var rolled = roll(fixture, chooser, null);
            var check = rolled.session().checks().get("convince");
            var result = fixture.intent(successor, rolled, ConversationSession.Intent.JOIN, "");
            disconnect(fixture, chooser);
            fixture.storage.reload();
            result = fixture.intent(successor, ConversationService.view(successor, result.session().identity().id()), ConversationSession.Intent.TAKE_CONTROL, "");
            result = fixture.intent(successor, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(result.diagnostic().isBlank(), "successor advances the original fixed decision: " + result.diagnostic());
            helper.assertTrue(result.session().confirmation() == null, "fixed acceptance requires no second approval");
            assertOutcome(helper, fixture, Set.of(chooser.getUUID()), Set.of(successor.getUUID()));
            helper.assertTrue(result.session().receipts().values().stream().allMatch(receipt -> receipt.actor().equals(chooser.getUUID())),
                    "node effect receipts retain the original actor");
            helper.assertValueEqual(result.session().checks().get("convince"), check, "handover cannot replace the saved roll");
            helper.assertTrue(VillagerQuestSavedData.get(helper.getLevel()).get(successor.getUUID(), fixture.base.campaignId) == null,
                    "successor does not need or receive the original player's personal quest");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_check_recovery")
    public static void pendingCheckConsequencesSurviveTwoControllersAndReload(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, false)) {
            var chooser = fixture.player(); var listener = fixture.player(); var late = fixture.player();
            fixture.party(chooser, listener);
            PartyService.setAdminPrivileges(chooser, listener.getUUID(), true);
            var invitation = PartyService.sendInvitation(chooser, late);
            helper.assertTrue(PartyService.acceptInvitation(late, invitation.invitationId()).success(), "later controller joins the party");
            PartyService.setAdminPrivileges(chooser, late.getUUID(), true);
            var rolled = roll(fixture, chooser, listener);
            var result = fixture.intent(chooser, rolled, ConversationSession.Intent.ADVANCE, "");
            var confirmation = result.session().confirmation();
            helper.assertTrue(confirmation != null, "new consequences require confirmation: " + result.diagnostic());
            result = fixture.intent(late, result, ConversationSession.Intent.JOIN, "");
            disconnect(fixture, chooser);
            fixture.storage.reload();
            result = fixture.intent(listener, ConversationService.view(listener, result.session().identity().id()), ConversationSession.Intent.TAKE_CONTROL, "");
            result = fixture.intent(listener, result, ConversationSession.Intent.HAND_OVER, late.getUUID().toString());
            helper.assertValueEqual(result.session().confirmation().id(), confirmation.id(), "handover preserves the pending consequences");
            fixture.storage.reload();
            result = ConversationService.view(late, result.session().identity().id());
            var denied = fixture.intent(listener, result, ConversationSession.Intent.CONFIRM, confirmation.id().toString());
            helper.assertFalse(denied.diagnostic().isBlank(), "previous controller cannot approve after handover");
            helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().isEmpty(),
                    "an old controller applies no outcome");
            UUID request = UUID.randomUUID();
            long revision = result.session().revision();
            result = ConversationService.request(late, result.session().identity().id(), revision, request,
                    ConversationSession.Intent.CONFIRM, confirmation.id().toString());
            helper.assertTrue(result.diagnostic().isBlank(), "current authority can confirm original consequences: " + result.diagnostic());
            assertOutcome(helper, fixture, Set.of(chooser.getUUID(), listener.getUUID()), Set.of(late.getUUID()));
            var committed = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision");
            helper.assertValueEqual(committed.operationId(), confirmation.consent().decisions().keySet().iterator().next(), "approval retains the original operation");
            fixture.storage.reload();
            result = ConversationService.request(late, result.session().identity().id(), revision, request,
                    ConversationSession.Intent.CONFIRM, confirmation.id().toString());
            helper.assertValueEqual(result.session().checks(), rolled.session().checks(), "repeated approval retains the saved check");
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision"), committed,
                    "repeated approval cannot commit another outcome");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_check_recovery")
    public static void competingOutcomeInvalidatesInheritedCheckConfirmation(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, false)) {
            var chooser = fixture.player(); var successor = fixture.player();
            fixture.party(chooser, successor);
            PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
            var rolled = roll(fixture, chooser, successor);
            disconnect(fixture, chooser);
            fixture.storage.reload();
            var result = fixture.intent(successor, ConversationService.view(successor, rolled.session().identity().id()), ConversationSession.Intent.TAKE_CONTROL, "");
            result = fixture.intent(successor, result, ConversationSession.Intent.ADVANCE, "");
            var confirmation = result.session().confirmation();
            helper.assertTrue(confirmation != null, "offline owner's check consequences await approval: " + result.diagnostic());
            helper.assertValueEqual(confirmation.consent().playerId(), chooser.getUUID(), "successor reviews the original player's outcome");
            var data = CampaignSavedData.get(helper.getLevel());
            var content = CampaignResources.current(fixture.base.server).campaigns().get(fixture.base.campaignId);
            var winner = data.commitWorldOutcome(content, "decision", "leave", UUID.randomUUID(),
                    data.instances().get(fixture.base.campaignId).worldRevision(), CampaignInstance.Origin.PLAYER_DECISION,
                    Set.of(UUID.randomUUID()), helper.getLevel().getGameTime());
            result = fixture.intent(successor, result, ConversationSession.Intent.CONFIRM, confirmation.id().toString());
            helper.assertTrue(result.diagnostic().contains(winner.committed().title()), "successor sees the committed competing outcome");
            helper.assertTrue(result.session().confirmation() == null && result.session().controller() == null, "stale consequences release control for recovery");
            helper.assertValueEqual(data.instances().get(fixture.base.campaignId).outcomes().get("decision"), winner.committed(), "stale approval cannot replace the winner");
            for (var player : Set.of(chooser.getUUID(), successor.getUUID())) {
                helper.assertFalse(data.instances().get(fixture.base.campaignId).players().getOrDefault(player, CampaignInstance.PlayerRecord.empty())
                        .participation().contains("decision"), "a losing choice receives no participation credit");
            }
            helper.assertValueEqual(result.session().checks(), rolled.session().checks(), "conflict preserves the rolled history");
        }
        helper.succeed();
    }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper, boolean fixed) throws IOException {
        var fixture = new CampaignConversationGameTests.Fixture(helper, false, true, fixed);
        var nodes = fixture.graph.getAsJsonArray("nodes");
        nodes.get(0).getAsJsonObject().add("exchanges", JsonParser.parseString("[]"));
        for (int index = 1; index < nodes.size(); index++) {
            nodes.get(index).getAsJsonObject().add("effects", JsonParser.parseString("""
                    [{"id":"decide","action":{"type":"campaign_outcome","quest":"campaign_fixture:characters",
                      "milestone":"decision","outcome":"help","operation_id":"check_resolution"}}]
                    """));
        }
        fixture.install(fixture.graph);
        return fixture;
    }

    private static ConversationService.Result roll(CampaignConversationGameTests.Fixture fixture, ServerPlayer chooser, ServerPlayer listener) throws IOException {
        CampaignConversationConfirmationGameTests.acceptQuest(fixture, chooser);
        var result = fixture.start(chooser);
        if (listener != null) result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
        result = fixture.intent(chooser, result, ConversationSession.Intent.CHOOSE, "appeal");
        result = fixture.intent(chooser, result, ConversationSession.Intent.ROLL, "");
        fixture.helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.ROLLED, "fixture check resolved once");
        return result;
    }

    private static void disconnect(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        ConversationService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        fixture.base.server.getPlayerList().remove(player);
        fixture.players.remove(player);
    }

    private static void assertOutcome(GameTestHelper helper, CampaignConversationGameTests.Fixture fixture, Set<UUID> included, Set<UUID> excluded) {
        var instance = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId);
        helper.assertTrue(instance.outcomes().containsKey("decision"), "original world decision commits");
        for (var player : included) helper.assertTrue(instance.players().getOrDefault(player, CampaignInstance.PlayerRecord.empty())
                .participation().contains("decision"), "original decision participant receives credit");
        for (var player : excluded) helper.assertFalse(instance.players().getOrDefault(player, CampaignInstance.PlayerRecord.empty())
                .participation().contains("decision"), "later controllers do not receive earlier participation credit");
    }
}
