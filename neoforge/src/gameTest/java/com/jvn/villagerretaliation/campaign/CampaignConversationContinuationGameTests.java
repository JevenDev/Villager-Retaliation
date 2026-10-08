package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationContinuationGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("campaign_fixture:characters");
    private static final String WAIT = """
            {"type":"start_scene","quest":"campaign_fixture:characters","scene_id":"campaign_fixture:character_greeting",
             "operation_id":"continuation_first","wait_for_result":true,"required":true}
            """;
    private static final String DECIDE = """
            {"type":"campaign_outcome","quest":"campaign_fixture:characters","milestone":"decision",
             "outcome":"help","operation_id":"continuation_decision","required":true}
            """;
    private static final String PERSONAL = """
            {"type":"experience","amount":11,"required":true},
            {"type":"campaign_fact","quest":"campaign_fixture:characters","fact":"observed","value":"true",
             "operation_id":"continuation_observed","required":true}
            """;

    private CampaignConversationContinuationGameTests() { }

    @GameTest(template = "empty", batch = "conversation_continuations")
    public static void questContinuationUsesSuccessorAuthorityAndWaitsForPersonalRecipient(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, false)) {
            var chooser = fixture.player(); var successor = fixture.player();
            fixture.party(chooser, successor);
            PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
            var result = approve(fixture, chooser, successor);
            var continuation = waiting(fixture, result);
            var id = continuation.id(); var source = continuation.conversationEffect();
            helper.assertFalse(source.isEmpty(), "quest action continuation captures its conversation effect");
            completeScene(fixture, continuation);
            disconnect(fixture, chooser);
            fixture.storage.reload();
            var data = reload(fixture);
            continuation = data.continuation(id).orElseThrow();
            helper.assertValueEqual(continuation.conversationEffect(), source, "source survives both saved-data reloads");
            takeControl(fixture, successor, result);
            SceneContinuationService.maintain(fixture.base.server, data);
            var committed = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision");
            helper.assertTrue(committed != null, "successor commits the original choice while its owner is offline: " + continuation.actionDiagnostic());
            helper.assertValueEqual(continuation.nextActionIndex(), 2, "offline personal action keeps its cursor");
            helper.assertValueEqual(continuation.preparedRecoveryAction(), -1, "waiting does not invent an ambiguous prepared effect");
            helper.assertFalse(continuation.completionReceipt(), "personal effects keep the continuation pending");
            helper.assertValueEqual(successor.totalExperience, 0, "successor receives no personal reward");
            data = reload(fixture);
            var returned = fixture.reconnect(chooser);
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertTrue(data.continuation(id).orElseThrow().completionReceipt(), "returning player completes the original sequence");
            helper.assertValueEqual(returned.totalExperience, 11, "original player receives their reward once");
            assertFact(fixture, returned, successor);
            result = fixture.intent(successor, ConversationService.view(successor, result.session().identity().id()), ConversationSession.Intent.RESUME, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CHECK, "conversation finishes the suspended quest transition: " + result.diagnostic());
            helper.assertValueEqual(VillagerQuestSavedData.get(helper.getLevel()).get(returned.getUUID(), QUEST).currentStage(), "report", "original quest transition completes");
            data = reload(fixture);
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertValueEqual(returned.totalExperience, 11, "completed continuation cannot repeat rewards");
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision"), committed, "shared outcome retains its receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_continuations")
    public static void revokedChooserKeepsPersonalFactsAfterAuthorizedHandover(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, false)) {
            var leader = fixture.player(); var chooser = fixture.player();
            fixture.party(leader, chooser);
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), true);
            var result = approve(fixture, chooser, leader);
            var continuation = waiting(fixture, result);
            completeScene(fixture, continuation);
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), false);
            var data = SceneSavedData.get(helper.getLevel());
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertValueEqual(continuation.nextActionIndex(), 1, "revoked controller cannot advance the action cursor");
            helper.assertValueEqual(chooser.totalExperience, 0, "revoked controller cannot apply personal effects");
            takeControl(fixture, leader, result);
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertTrue(continuation.completionReceipt(), "successor resumes the sequence: " + continuation.actionDiagnostic());
            helper.assertValueEqual(chooser.totalExperience, 11, "original actor retains personal reward ownership");
            assertFact(fixture, chooser, leader);
            helper.assertFalse(CampaignAuthority.controlIssue(chooser).isBlank(), "execution scope never restores revoked Admin Privileges");
            helper.assertTrue(ConversationSceneExecution.capture().isEmpty(), "resumption restores its conversation execution scope");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_continuations")
    public static void scenesLaunchedByResumedQuestActionsKeepTheirConversationLink(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, true)) {
            var leader = fixture.player(); var chooser = fixture.player();
            fixture.party(leader, chooser);
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), true);
            var result = approve(fixture, chooser, leader);
            var continuation = waiting(fixture, result);
            var first = continuation.sceneInstanceId(); var id = continuation.id();
            completeScene(fixture, continuation);
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), false);
            takeControl(fixture, leader, result);
            var data = SceneSavedData.get(helper.getLevel());
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertFalse(first.equals(continuation.sceneInstanceId()), "resumed sequence launches its second authored scene");
            var second = data.get(continuation.sceneInstanceId()).orElseThrow();
            helper.assertValueEqual(second.conversationEffect(), continuation.conversationEffect(), "second scene inherits the exact source");
            helper.assertTrue(ConversationSceneExecution.resolve(fixture.base.server, second).diagnostic().isBlank(), "conversation dependency traversal authorizes the new scene");
            helper.assertValueEqual(chooser.totalExperience, 0, "personal reward remains behind the second scene");
            data = reload(fixture);
            continuation = data.continuation(id).orElseThrow();
            completeScene(fixture, continuation);
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertTrue(continuation.completionReceipt(), "second scene completion resumes the sequence after reload: " + continuation.actionDiagnostic());
            helper.assertValueEqual(chooser.totalExperience, 11, "nested scene recovery rewards the original chooser once");
            assertFact(fixture, chooser, leader);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_continuations")
    public static void damagedContinuationLinksAndRecoveryCursorsPauseForRepair(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, false)) {
            var chooser = fixture.player();
            var result = approve(fixture, chooser, null);
            var continuation = waiting(fixture, result);
            var data = SceneSavedData.get(helper.getLevel());
            completeScene(fixture, continuation);
            var source = continuation.conversationEffect();
            for (String field : List.of("Session", "Effect")) {
                var invalid = source.copy();
                if (field.equals("Session")) invalid.putUUID(field, UUID.randomUUID());
                else invalid.putString(field, "another_effect");
                continuation.conversationEffect(invalid);
                SceneContinuationService.maintain(fixture.base.server, data);
                helper.assertValueEqual(continuation.nextActionIndex(), 1, "wrong " + field + " cannot authorize the sequence");
            }
            continuation.conversationEffect(source);
            var participants = continuation.decisionParticipants();
            continuation.decisionParticipants(new CompoundTag());
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertValueEqual(continuation.nextActionIndex(), 1, "missing decision evidence cannot discard the saved participants");
            continuation.decisionParticipants(participants);
            var saved = continuation.save(); saved.putString("ConversationEffect", "broken");
            var broken = SceneContinuation.load(saved);
            helper.assertTrue(broken.conversationEffect().contains("Unreadable"), "malformed source is preserved for repair");
            helper.assertFalse(ConversationSceneExecution.resolve(fixture.base.server, broken).diagnostic().isBlank(), "malformed source cannot fall back to ordinary authority");
            for (String field : List.of("Next", "PreparedAction")) {
                saved = continuation.save();
                if (field.equals("Next")) saved.putInt("Next", -3);
                else saved.getCompound("ConversationRecovery").putInt("PreparedAction", 300);
                broken = SceneContinuation.load(saved);
                helper.assertFalse(CampaignRewardRecovery.issue(fixture.base.server, broken).isBlank(), "unreadable " + field + " blocks recovery");
                helper.assertValueEqual(broken.save().get(field.equals("Next") ? "Next" : "ConversationRecovery"),
                        saved.get(field.equals("Next") ? "Next" : "ConversationRecovery"), "malformed cursor is retained for repair");
            }
            saved = continuation.save(); saved.getCompound("ConversationRecovery").putString("QuestFingerprint", "different-content");
            helper.assertFalse(CampaignRewardRecovery.issue(fixture.base.server, SceneContinuation.load(saved)).isBlank(), "changed quest bundle blocks its saved action sequence");
            fixture.install(null);
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertValueEqual(continuation.nextActionIndex(), 1, "missing conversation content preserves its dormant cursor");
            fixture.install(fixture.graph);
            var legacy = data.save(new CompoundTag(), helper.getLevel().registryAccess()); legacy.putInt("DataVersion", 16);
            for (var raw : legacy.getList("Continuations", Tag.TAG_COMPOUND)) {
                ((CompoundTag) raw).remove("ConversationEffect"); ((CompoundTag) raw).remove("ConversationRecovery");
            }
            helper.assertTrue(SceneSavedData.load(legacy, helper.getLevel().registryAccess()).continuation(continuation.id()).orElseThrow().conversationEffect().isEmpty(), "migration preserves legacy continuations without inventing authority");
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertTrue(continuation.completionReceipt(), "repair restores the original sequence");
            helper.assertValueEqual(chooser.totalExperience, 11, "repaired source applies personal effects only once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_continuations")
    public static void ambiguousPersonalEffectsRemainBlockedAcrossDisconnectAndReload(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, false)) {
            var chooser = fixture.player(); var successor = fixture.player();
            fixture.party(chooser, successor);
            PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
            var result = approve(fixture, chooser, successor);
            var continuation = waiting(fixture, result); var id = continuation.id();
            completeScene(fixture, continuation);
            continuation.nextActionIndex(2); continuation.prepareRecoveryAction(2);
            disconnect(fixture, chooser);
            takeControl(fixture, successor, result);
            var data = reload(fixture);
            SceneContinuationService.maintain(fixture.base.server, data);
            continuation = data.continuation(id).orElseThrow();
            helper.assertValueEqual(continuation.preparedRecoveryAction(), 2, "offline wait cannot clear an ambiguous prepared action");
            helper.assertTrue(continuation.actionDiagnostic().contains("no saved completion receipt"), "ambiguous personal effect explains the required repair");
            var returned = fixture.reconnect(chooser);
            data = reload(fixture);
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertValueEqual(returned.totalExperience, 0, "reconnect never replays an effect with unknown completion");
            helper.assertFalse(data.continuation(id).orElseThrow().completionReceipt(), "ambiguous action remains pending");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_continuations")
    public static void preparedSharedActionReusesItsDurableOutcomeAfterInterruptedSave(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, false)) {
            var chooser = fixture.player(); var successor = fixture.player();
            fixture.party(chooser, successor);
            PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
            var result = approve(fixture, chooser, successor);
            var continuation = waiting(fixture, result); var id = continuation.id();
            completeScene(fixture, continuation);
            continuation.prepareRecoveryAction(1);
            var data = SceneSavedData.get(helper.getLevel());
            var prepared = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            disconnect(fixture, chooser);
            takeControl(fixture, successor, result);
            SceneContinuationService.maintain(fixture.base.server, data);
            var committed = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision");
            helper.assertTrue(committed != null, "recoverable prepared shared action commits its approved outcome");
            data = SceneSavedData.load(prepared, helper.getLevel().registryAccess());
            fixture.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", data);
            SceneContinuationService.maintain(fixture.base.server, data);
            continuation = data.continuation(id).orElseThrow();
            helper.assertValueEqual(continuation.nextActionIndex(), 2, "the saved world receipt releases the original prepared action");
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision"), committed, "recovery preserves the original commit and participant record");
            var returned = fixture.reconnect(chooser);
            SceneContinuationService.maintain(fixture.base.server, data);
            helper.assertTrue(continuation.completionReceipt(), "personal effects finish after the recipient returns");
            helper.assertValueEqual(returned.totalExperience, 11, "interrupted commit recovery does not duplicate personal rewards");
            helper.assertValueEqual(successor.totalExperience, 0, "receipt recovery never transfers rewards to the controller");
        }
        helper.succeed();
    }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper, boolean secondScene) throws IOException {
        var sequence = "[" + WAIT + "," + DECIDE + "," + (secondScene ? WAIT.replace("continuation_first", "continuation_second") + "," : "") + PERSONAL + "]";
        var fixture = new CampaignConversationGameTests.Fixture(helper, false, true, false,
                campaign -> campaign.add("facts", JsonParser.parseString("[{\"id\":\"observed\",\"scope\":\"player\",\"kind\":\"tag\"}]")),
                quest -> quest.getAsJsonArray("stages").get(0).getAsJsonObject().add("on_exit", JsonParser.parseString(sequence)));
        var opening = fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
        opening.add("exchanges", JsonParser.parseString("[]"));
        opening.getAsJsonArray("responses").get(0).getAsJsonObject().add("effects", JsonParser.parseString("""
                [{"id":"advance","action":{"type":"set_variable","quest":"campaign_fixture:characters","scope":"quest",
                  "key":"stage","value":"report","required":true}},
                 {"id":"decide","action":%s}]
                """.formatted(DECIDE)));
        fixture.install(fixture.graph);
        return fixture;
    }

    private static ConversationService.Result approve(CampaignConversationGameTests.Fixture fixture, ServerPlayer chooser, ServerPlayer listener) throws IOException {
        CampaignConversationConfirmationGameTests.acceptQuest(fixture, chooser);
        var result = fixture.start(chooser);
        if (listener != null) result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
        result = fixture.intent(chooser, result, ConversationSession.Intent.CHOOSE, "appeal");
        fixture.helper.assertTrue(result.session().confirmation() != null, "decision requires explicit approval: " + result.diagnostic());
        result = fixture.intent(chooser, result, ConversationSession.Intent.CONFIRM, result.session().confirmation().id().toString());
        var receipt = result.session().receipts().values().iterator().next();
        fixture.helper.assertValueEqual(receipt.result().suspensionKind(), ActionResult.SuspensionKind.CONTINUATION, "conversation waits for a real quest action continuation: " + result.diagnostic());
        return result;
    }

    private static SceneContinuation waiting(CampaignConversationGameTests.Fixture fixture, ConversationService.Result result) {
        return SceneSavedData.get(fixture.helper.getLevel()).continuation(result.session().receipts().values().iterator().next().result().suspensionId()).orElseThrow();
    }

    private static void completeScene(CampaignConversationGameTests.Fixture fixture, SceneContinuation continuation) {
        SceneSavedData.get(fixture.helper.getLevel()).get(continuation.sceneInstanceId()).orElseThrow().complete(fixture.helper.getLevel().getGameTime());
    }

    private static SceneSavedData reload(CampaignConversationGameTests.Fixture fixture) {
        var data = SceneSavedData.get(fixture.helper.getLevel());
        var loaded = SceneSavedData.load(data.save(new CompoundTag(), fixture.helper.getLevel().registryAccess()), fixture.helper.getLevel().registryAccess());
        fixture.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", loaded);
        return loaded;
    }

    private static void disconnect(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        ConversationService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        fixture.base.server.getPlayerList().remove(player);
        fixture.players.remove(player);
    }

    private static void takeControl(CampaignConversationGameTests.Fixture fixture, ServerPlayer successor, ConversationService.Result prior) {
        var result = fixture.intent(successor, ConversationService.view(successor, prior.session().identity().id()), ConversationSession.Intent.TAKE_CONTROL, "");
        fixture.helper.assertValueEqual(result.session().controller(), successor.getUUID(), "authorized successor takes control: " + result.diagnostic());
    }

    private static void assertFact(CampaignConversationGameTests.Fixture fixture, ServerPlayer chooser, ServerPlayer successor) {
        var campaign = CampaignResources.current(fixture.base.server).campaigns().get(fixture.base.campaignId).definition();
        var saved = CampaignSavedData.get(fixture.helper.getLevel());
        fixture.helper.assertValueEqual(saved.readFact(campaign, chooser.getUUID(), "observed").value().text(), "true", "personal discovery belongs to the original actor");
        fixture.helper.assertTrue(saved.readFact(campaign, successor.getUUID(), "observed").value() == null, "successor authority does not transfer discovery");
    }
}
