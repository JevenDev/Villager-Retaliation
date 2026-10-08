package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationNestedConfirmationGameTests {
    private static final String DECISION = """
            {"id":"decision","type":"campaign_outcome","quest":"campaign_fixture:characters","milestone":"decision",
             "outcome":"help","operation_id":"nested_decision","required":true}
            """;
    private static final String WAIT = """
            {"type":"start_scene","quest":"campaign_fixture:characters","scene_id":"campaign_fixture:character_greeting",
             "operation_id":"nested_wait","wait_for_result":true,"required":true}
            """;
    private static final String SCENE = """
            {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
             "ownership":"player","entry_step":"decide","failure_policy":"run_failure_step","actors":[],"steps":[
              {"id":"decide","type":"villagerretaliation:action_batch","data":{"actions":[
                {"id":"prefix","type":"experience","amount":5,"required":true},%s,
                {"id":"later","type":"experience","amount":7,"required":true}]},"next":"done","failure_step":"refused"},
              {"id":"refused","type":"villagerretaliation:action_batch","data":{"actions":[
                {"id":"refusal","type":"experience","amount":2,"required":true}]},"next":"done"},
              {"id":"done","type":"villagerretaliation:scene_complete"}]}
            """.formatted(DECISION);

    private CampaignConversationNestedConfirmationGameTests() { }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void sceneDecisionWaitsAfterItsPrefixAndResumesOnceAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false, false)) {
            var player = fixture.conversation.player();
            fixture.start(player, null);
            fixture.reach();
            var pending = fixture.view(player);
            helper.assertValueEqual(pending.session().phase(), ConversationSession.Phase.CONFIRMATION, "scene-only outcome opens the existing confirmation screen");
            helper.assertValueEqual(player.totalExperience, 5, "earlier effect runs before the reached decision");
            helper.assertTrue(fixture.outcome() == null, "pending impact never commits early");
            fixture.reload();
            pending = fixture.view(player);
            UUID request = UUID.randomUUID(); long revision = pending.session().revision();
            String confirmation = pending.session().confirmation().id().toString();
            var accepted = ConversationService.request(player, fixture.session, revision, request, ConversationSession.Intent.CONFIRM, confirmation);
            helper.assertTrue(accepted.session().confirmation() == null, "approval resumes the saved effect cursor: " + accepted.diagnostic());
            fixture.reload(); fixture.run();
            helper.assertTrue(fixture.outcome() != null && fixture.outcome().outcome().equals("help"), "approved scene outcome commits");
            helper.assertValueEqual(player.totalExperience, 12, "prefix and suffix rewards each run once");
            var committed = fixture.outcome();
            ConversationService.request(player, fixture.session, revision, request, ConversationSession.Intent.CONFIRM, confirmation);
            fixture.reload(); fixture.run();
            helper.assertValueEqual(player.totalExperience, 12, "duplicate confirmation and disk reload never repeat rewards");
            helper.assertValueEqual(fixture.outcome(), committed, "duplicate confirmation retains the actual committed outcome");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void decliningAReachedDecisionUsesTheAuthoredSceneFailurePath(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false, false)) {
            var player = fixture.conversation.player();
            fixture.start(player, null); fixture.reach();
            fixture.answer(player, ConversationSession.Intent.DECLINE);
            fixture.reload(); fixture.run();
            helper.assertTrue(fixture.outcome() == null, "refusal never commits the world outcome");
            helper.assertValueEqual(player.totalExperience, 7, "only the prefix and authored refusal reward run");
            helper.assertTrue(fixture.view(player).session().confirmation() == null, "consumed refusal no longer holds the conversation");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(player.totalExperience, 7, "refusal recovery never replays the prefix");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void aSuccessorConfirmsTheOriginalPlayersSceneWhileTheyAreOffline(GameTestHelper helper) throws IOException {
        for (boolean entry : new boolean[]{false, true}) try (var fixture = new Fixture(helper, false, entry)) {
            var chooser = fixture.conversation.player(); var successor = fixture.conversation.player();
            fixture.conversation.party(chooser, successor);
            PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
            fixture.start(chooser, successor); fixture.reach();
            var pending = fixture.view(chooser);
            fixture.conversation.intent(chooser, pending, ConversationSession.Intent.HAND_OVER, successor.getUUID().toString());
            fixture.disconnect(chooser); fixture.reload();
            fixture.answer(successor, ConversationSession.Intent.CONFIRM); fixture.run();
            helper.assertTrue(fixture.outcome() != null, "authorized successor commits the original player's pending decision");
            helper.assertValueEqual(successor.totalExperience, 0, "personal effects never transfer to the controller");
            var returned = fixture.conversation.reconnect(chooser);
            fixture.run();
            helper.assertValueEqual(returned.totalExperience, 12, "original player's suffix resumes after reconnect");
            helper.assertValueEqual(successor.totalExperience, 0, "successor keeps their own personal progression");
            helper.assertTrue(fixture.participated(chooser.getUUID()) && fixture.participated(successor.getUUID()),
                    "the recorded participants retain their actual involvement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void revokedControlCannotConfirmButAnAuthorizedHandoverCanResume(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false, false)) {
            var leader = fixture.conversation.player(); var chooser = fixture.conversation.player();
            fixture.conversation.party(leader, chooser);
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), true);
            fixture.start(chooser, leader); fixture.reach();
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), false);
            fixture.answer(chooser, ConversationSession.Intent.CONFIRM); fixture.run();
            helper.assertTrue(fixture.outcome() == null, "revoked player cannot commit a saved prompt");
            helper.assertValueEqual(chooser.totalExperience, 5, "revocation preserves the already completed prefix");
            fixture.conversation.intent(leader, fixture.view(leader), ConversationSession.Intent.TAKE_CONTROL, "");
            fixture.answer(leader, ConversationSession.Intent.CONFIRM); fixture.run();
            helper.assertTrue(fixture.outcome() != null, "leader resumes the exact pending decision");
            helper.assertValueEqual(chooser.totalExperience, 12, "revoked chooser still owns their personal effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void changedSceneContentBlocksItsSavedConfirmationUntilRestored(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false, false)) {
            var player = fixture.conversation.player();
            fixture.start(player, null); fixture.reach();
            CampaignConversationSceneHandoverGameTests.scene(fixture.conversation, SCENE.replace("\"amount\":7", "\"amount\":9"));
            var denied = fixture.answer(player, ConversationSession.Intent.CONFIRM);
            helper.assertTrue(denied.diagnostic().contains("scene changed"), "changed scene hash explains the repair: " + denied.diagnostic());
            helper.assertTrue(fixture.outcome() == null, "changed content cannot reuse the old approval");
            CampaignConversationSceneHandoverGameTests.scene(fixture.conversation, SCENE);
            fixture.answer(player, ConversationSession.Intent.CONFIRM); fixture.run();
            helper.assertValueEqual(player.totalExperience, 12, "restoring compatible content resumes without replaying the prefix");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void questContinuationsPersistReachedApprovalsAndRefusals(GameTestHelper helper) throws IOException {
        for (boolean initialWait : new boolean[]{false, true}) for (boolean accept : new boolean[]{true, false})
                try (var fixture = new Fixture(helper, true, false, initialWait)) {
            var player = fixture.conversation.player();
            fixture.start(player, null); fixture.reach();
            helper.assertValueEqual(fixture.view(player).session().phase(), ConversationSession.Phase.CONFIRMATION, "resumed quest sequence opens its reached confirmation");
            helper.assertValueEqual(player.totalExperience, 5, "sequence stops at the irreversible action");
            fixture.reload();
            fixture.answer(player, accept ? ConversationSession.Intent.CONFIRM : ConversationSession.Intent.DECLINE);
            fixture.reload(); fixture.run();
            helper.assertValueEqual(player.totalExperience, accept ? 12 : 5, "sequence preserves its prefix and respects the resolved choice");
            helper.assertTrue(accept == (fixture.outcome() != null), "only approval commits the shared milestone");
            var progress = VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), fixture.conversation.base.campaignId);
            helper.assertValueEqual(progress.currentStage(), accept ? "report" : "visit", "required refusal prevents the parent quest transition");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(player.totalExperience, accept ? 12 : 5, "continuation reload cannot repeat effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void failedConfirmationStoragePreventsEffectsUntilTheAcknowledgmentIsSaved(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false, false)) {
            var player = fixture.conversation.player();
            fixture.start(player, null); fixture.reach();
            fixture.conversation.storage.blockWrites();
            try {
                fixture.answer(player, ConversationSession.Intent.CONFIRM); fixture.run();
                helper.assertTrue(fixture.outcome() == null, "unsaved approval cannot release the scene");
                helper.assertValueEqual(player.totalExperience, 5, "unsaved approval cannot release the suffix");
            } finally { fixture.conversation.storage.restoreWrites(); }
            fixture.run(); fixture.reload(); fixture.run();
            helper.assertValueEqual(player.totalExperience, 12, "storage repair resumes the acknowledged choice once");
            helper.assertTrue(fixture.outcome() != null, "recovered approval commits its exact outcome");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void failedDecisionReceiptCleanupCannotRepeatTheCommittedOutcome(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false, false)) {
            var player = fixture.conversation.player();
            fixture.start(player, null); fixture.reach();
            fixture.answer(player, ConversationSession.Intent.CONFIRM);
            fixture.conversation.storage.blockWrites();
            try {
                fixture.reach();
                helper.assertTrue(fixture.outcome() != null, "approved outcome commits before its acknowledgment cleanup");
                helper.assertValueEqual(player.totalExperience, 5, "failed cleanup holds subsequent effects");
            } finally { fixture.conversation.storage.restoreWrites(); }
            var committed = fixture.outcome();
            fixture.run(); fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.outcome(), committed, "cleanup retry reuses the committed outcome");
            helper.assertValueEqual(player.totalExperience, 12, "suffix resumes once after cleanup becomes durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void nodeEntryScenesUseTheSamePersistentConfirmationPath(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false, true)) {
            var player = fixture.conversation.player();
            fixture.start(player, null); fixture.reach(); fixture.reload();
            helper.assertValueEqual(fixture.view(player).session().phase(), ConversationSession.Phase.CONFIRMATION, "node entry can pause for a reached consequence");
            fixture.answer(player, ConversationSession.Intent.CONFIRM); fixture.run();
            helper.assertTrue(fixture.outcome() != null, "node entry approval commits its authored outcome");
            helper.assertValueEqual(player.totalExperience, 12, "node entry retains its original effect order");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void aCompetingOutcomeRejectsTheReachedDecisionWithoutReplayingItsPrefix(GameTestHelper helper) throws IOException {
        for (boolean continuation : new boolean[]{false, true}) for (boolean confirm : new boolean[]{false, true})
                try (var fixture = new Fixture(helper, continuation, false)) {
            var player = fixture.conversation.player();
            fixture.start(player, null); fixture.reach();
            var data = CampaignSavedData.get(helper.getLevel());
            var content = CampaignResources.current(fixture.conversation.base.server).campaigns().get(fixture.conversation.base.campaignId);
            var winner = data.commitWorldOutcome(content, "decision", "leave", UUID.randomUUID(),
                    data.instances().get(fixture.conversation.base.campaignId).worldRevision(), CampaignInstance.Origin.PLAYER_DECISION,
                    java.util.Set.of(UUID.randomUUID()), helper.getLevel().getGameTime());
            helper.assertValueEqual(winner.status(), CampaignSavedData.Status.APPLIED, "another player resolves the shared milestone first");
            if (confirm) {
                var denied = fixture.answer(player, ConversationSession.Intent.CONFIRM);
                helper.assertTrue(denied.diagnostic().contains(winner.committed().title()), "losing confirmation explains the actual outcome");
            }
            var quest = com.jvn.villagerretaliation.quest.VillagerQuestResources.quest(fixture.conversation.base.server,
                    fixture.conversation.base.campaignId).orElseThrow();
            var progress = VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), quest.id());
            helper.assertTrue(CampaignQuestReconciliation.reconcile(player, quest, progress), "catch-up supersedes the original quest path");
            fixture.run(); fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.outcome(), winner.committed(), "a stale nested approval cannot overwrite the winner");
            helper.assertFalse(fixture.participated(player.getUUID()), "losing approval never invents participation credit");
            helper.assertValueEqual(player.totalExperience, 5, "catch-up blocks remaining effects from the superseded quest path");
            var view = fixture.view(player);
            helper.assertTrue(view.session() != null && view.session().confirmation() == null,
                    "superseded confirmation is consumed without replaying its parent: " + view.diagnostic());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void reachedNodeDecisionsRetainPresentListenersAcrossReloads(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false, true)) {
            var player = fixture.conversation.player(); var witness = fixture.conversation.player();
            var distant = fixture.conversation.player(); var late = fixture.conversation.player();
            fixture.conversation.party(player, witness);
            for (var member : java.util.List.of(distant, late)) {
                var invitation = PartyService.sendInvitation(player, member);
                helper.assertTrue(PartyService.acceptInvitation(member, invitation.invitationId()).success(), "fixture member joins");
            }
            fixture.start(player, witness);
            fixture.conversation.intent(distant, fixture.view(player), ConversationSession.Intent.JOIN, "");
            distant.setPos(distant.getX() + 80, distant.getY(), distant.getZ());
            fixture.reach(); fixture.answer(player, ConversationSession.Intent.CONFIRM);
            fixture.disconnect(witness); fixture.reload();
            fixture.conversation.intent(late, fixture.view(player), ConversationSession.Intent.JOIN, "");
            fixture.run();
            helper.assertTrue(fixture.participated(player.getUUID()) && fixture.participated(witness.getUUID()),
                    "original actor and present witness retain their involvement after approval and reload");
            helper.assertFalse(fixture.participated(distant.getUUID()) || fixture.participated(late.getUUID()),
                    "distant members and post-approval arrivals receive no participation credit");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(player.totalExperience, 12, "saved witnesses cannot repeat personal effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void reachedQuestDecisionsRecordListenersWhoJoinedAfterTheOuterChoice(GameTestHelper helper) throws IOException {
        for (boolean initialWait : new boolean[]{false, true}) try (var fixture = new Fixture(helper, true, false, initialWait)) {
            var player = fixture.conversation.player(); var witness = fixture.conversation.player();
            fixture.conversation.party(player, witness);
            fixture.start(player, null);
            fixture.conversation.intent(witness, fixture.view(player), ConversationSession.Intent.JOIN, "");
            fixture.reach(); fixture.answer(player, ConversationSession.Intent.CONFIRM);
            fixture.disconnect(witness); fixture.reload(); fixture.run();
            helper.assertTrue(fixture.participated(player.getUUID()) && fixture.participated(witness.getUUID()),
                    "reached quest decision retains its actual witnesses after a reload");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(player.totalExperience, 12, "continuation acknowledgment does not repeat personal rewards");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_nested_confirmations")
    public static void reachedQuestAcceptanceKeepsItsOriginalPlayerAndChildWitnesses(GameTestHelper helper) throws IOException {
        try (var conversation = new CampaignConversationGameTests.Fixture(helper, false, true, true, campaign -> { }, quest ->
                quest.getAsJsonArray("stages").get(0).getAsJsonObject().add("on_enter", JsonParser.parseString("""
                        [{"type":"start_scene","quest":"campaign_fixture:characters","scene_id":"campaign_fixture:accepted_child",
                          "operation_id":"acceptance_child","wait_for_result":true,"required":true}]
                        """)))) {
            var opening = conversation.graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
            opening.add("exchanges", JsonParser.parseString("[]"));
            opening.add("effects", JsonParser.parseString("[{\"id\":\"scene\",\"action\":" + WAIT + "}]"));
            conversation.install(conversation.graph);
            var definition = CampaignConversationSceneHandoverGameTests.scene(conversation, SCENE.replace(DECISION, """
                    {"id":"accept","type":"quest","action":"start","quest":"campaign_fixture:characters","required":true}
                    """), """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:accepted_child","definition_version":1,
                     "ownership":"player","entry_step":"decide","actors":[],"steps":[
                      {"id":"decide","type":"villagerretaliation:action_batch","data":{"actions":[%s,
                        {"id":"reward","type":"experience","amount":11,"required":true}]},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """.formatted(DECISION));
            var player = conversation.player(); var successor = conversation.player();
            conversation.party(player, successor);
            PartyService.setAdminPrivileges(player, successor.getUUID(), true);
            var result = conversation.start(player);
            result = conversation.intent(successor, result, ConversationSession.Intent.JOIN, "");
            UUID session = result.session().identity().id();
            var scenes = SceneSavedData.get(helper.getLevel());
            var parent = scenes.all().getFirst();
            long time = helper.getLevel().getGameTime();
            for (int count = 0; count < 16; count++) SceneStepEngine.process(conversation.base.server, scenes, parent, definition, time++);
            result = ConversationService.view(player, session);
            helper.assertTrue(result.session().confirmation() != null, "reached quest acceptance requires its own approval");
            result = conversation.intent(player, result, ConversationSession.Intent.CONFIRM, result.session().confirmation().id().toString());
            result = conversation.intent(player, result, ConversationSession.Intent.HAND_OVER, successor.getUUID().toString());
            ConversationService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            conversation.base.server.getPlayerList().remove(player); conversation.players.remove(player);
            for (int count = 0; count < 16; count++) SceneStepEngine.process(conversation.base.server, scenes, parent, definition, time++);
            helper.assertTrue(VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), conversation.base.campaignId) == null,
                    "personal acceptance waits for its original player");
            helper.assertTrue(VillagerQuestSavedData.get(helper.getLevel()).get(successor.getUUID(), conversation.base.campaignId) == null,
                    "handover never gives the controller another player's quest");
            var returned = conversation.reconnect(player);
            for (int count = 0; count < 16; count++) SceneStepEngine.process(conversation.base.server, scenes, parent, definition, time++);
            helper.assertTrue(VillagerQuestSavedData.get(helper.getLevel()).get(returned.getUUID(), conversation.base.campaignId) != null,
                    "saved acceptance starts for the original player after reconnect");
            var child = scenes.all().stream().filter(scene -> !scene.id().equals(parent.id())).findFirst().orElseThrow();
            helper.assertValueEqual(CampaignDecisionParticipants.load(child.decisionParticipants()).participants(),
                    java.util.Set.of(player.getUUID(), successor.getUUID()), "child inherits the saved acceptance witnesses");
            conversation.storage.reload();
            scenes = reloadScenes(conversation, helper);
            child = scenes.get(child.id()).orElseThrow();
            var authority = com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution.resolve(conversation.base.server, child);
            helper.assertTrue(authority.diagnostic().isBlank(), "consumed approval still proves child authority: " + authority.diagnostic());
            for (int pass = 0; pass < 8; pass++) {
                for (var scene : scenes.all()) {
                    var compiled = com.jvn.villagerretaliation.scene.SceneResources.scene(conversation.base.server, scene.sceneId()).orElseThrow();
                    for (int count = 0; count < 16 && !scene.state().terminal(); count++)
                        SceneStepEngine.process(conversation.base.server, scenes, scene, compiled, time++);
                }
                SceneContinuationService.maintain(conversation.base.server, scenes);
                ConversationTestStorage.maintain(conversation.base.server);
            }
            var instance = CampaignSavedData.get(helper.getLevel()).instances().get(conversation.base.campaignId);
            helper.assertTrue(instance.outcomes().containsKey("decision"), "approved child commits its fixed outcome");
            for (UUID member : java.util.Set.of(player.getUUID(), successor.getUUID()))
                helper.assertTrue(instance.players().get(member).participation().contains("decision"), "child retains confirmed participation");
            helper.assertValueEqual(returned.totalExperience, 23, "parent and child rewards stay with the original player and apply once");
            helper.assertValueEqual(successor.totalExperience, 0, "controller receives no personal rewards");
        } finally { SceneStepEngine.clearRuntimeState(); }
        helper.succeed();
    }

    private static SceneSavedData reloadScenes(CampaignConversationGameTests.Fixture conversation, GameTestHelper helper) throws IOException {
        var path = conversation.base.server.getWorldPath(LevelResource.ROOT).resolve("data/villagerretaliation_scenes.dat");
        var loaded = SceneSavedData.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompound("data"), helper.getLevel().registryAccess());
        conversation.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", loaded);
        SceneStepEngine.clearRuntimeState();
        return loaded;
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final CampaignConversationGameTests.Fixture conversation;
        final CompiledScene definition;
        final boolean continuation;
        final boolean entry;
        UUID session;
        long time;

        Fixture(GameTestHelper helper, boolean continuation, boolean entry) throws IOException {
            this(helper, continuation, entry, true);
        }

        Fixture(GameTestHelper helper, boolean continuation, boolean entry, boolean initialWait) throws IOException {
            this.helper = helper; this.continuation = continuation; this.entry = entry;
            if (continuation) {
                conversation = new CampaignConversationGameTests.Fixture(helper, false, true, false, campaign -> { }, quest -> {
                    var actions = "[" + (initialWait ? WAIT + "," : "") + "{\"type\":\"experience\",\"amount\":5,\"required\":true}," + DECISION
                            + ",{\"type\":\"experience\",\"amount\":7,\"required\":true}]";
                    quest.getAsJsonArray("stages").get(0).getAsJsonObject().add("on_exit", JsonParser.parseString(actions));
                });
                var opening = conversation.graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
                opening.add("exchanges", JsonParser.parseString("[]"));
                var response = opening.getAsJsonArray("responses").get(0).getAsJsonObject();
                response.remove("check"); response.addProperty("next", "agreed");
                response.add("effects", JsonParser.parseString("""
                        [{"id":"advance","action":{"type":"set_variable","quest":"campaign_fixture:characters","scope":"quest",
                          "key":"stage","value":"report","required":true}}]
                        """));
                conversation.install(conversation.graph);
            } else {
                conversation = CampaignConversationConfirmationGameTests.fixture(helper,
                        "[{\"id\":\"scene\",\"action\":" + WAIT + "}]", entry);
            }
            definition = CampaignConversationSceneHandoverGameTests.scene(conversation, continuation ? """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                     "ownership":"player","entry_step":"wait","actors":[],"steps":[
                      {"id":"wait","type":"villagerretaliation:wait_ticks","data":{"ticks":1},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """ : SCENE);
            time = helper.getLevel().getGameTime();
        }

        void start(ServerPlayer player, ServerPlayer listener) throws IOException {
            CampaignConversationConfirmationGameTests.acceptQuest(conversation, player);
            var result = conversation.start(player);
            if (listener != null) result = conversation.intent(listener, result, ConversationSession.Intent.JOIN, "");
            if (!entry) result = conversation.intent(player, result, ConversationSession.Intent.CHOOSE, "appeal");
            helper.assertTrue(result.session().confirmation() == null, "outer choice has no premature confirmation");
            session = result.session().identity().id();
        }
        ConversationService.Result view(ServerPlayer player) { return ConversationService.view(player, session); }
        ConversationService.Result answer(ServerPlayer player, ConversationSession.Intent answer) {
            var current = view(player);
            helper.assertTrue(current.session() != null && current.session().confirmation() != null, "fixture has a saved confirmation: " + current.diagnostic());
            return conversation.intent(player, current, answer, current.session().confirmation().id().toString());
        }
        SceneSavedData scenes() { return SceneSavedData.get(helper.getLevel()); }
        void reach() {
            for (var scene : scenes().all()) for (int count = 0; count < 16 && !scene.state().terminal(); count++)
                SceneStepEngine.process(conversation.base.server, scenes(), scene, definition, time++);
            if (continuation) SceneContinuationService.maintain(conversation.base.server, scenes());
        }
        void run() {
            ConversationTestStorage.maintain(conversation.base.server);
            reach();
            ConversationTestStorage.maintain(conversation.base.server);
        }
        boolean participated(UUID player) {
            return CampaignSavedData.get(helper.getLevel()).instances().get(conversation.base.campaignId).players()
                    .getOrDefault(player, CampaignInstance.PlayerRecord.empty()).participation().contains("decision");
        }
        CampaignInstance.OutcomeCommit outcome() {
            return CampaignSavedData.get(helper.getLevel()).instances().get(conversation.base.campaignId).outcomes().get("decision");
        }
        void reload() throws IOException {
            conversation.storage.reload();
            var path = conversation.base.server.getWorldPath(LevelResource.ROOT).resolve("data/villagerretaliation_scenes.dat");
            conversation.base.server.overworld().getDataStorage().set("villagerretaliation_scenes",
                    SceneSavedData.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompound("data"), helper.getLevel().registryAccess()));
            SceneStepEngine.clearRuntimeState();
        }
        void disconnect(ServerPlayer player) {
            ConversationService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            conversation.base.server.getPlayerList().remove(player); conversation.players.remove(player);
        }
        @Override public void close() throws IOException { SceneStepEngine.clearRuntimeState(); conversation.close(); }
    }
}
