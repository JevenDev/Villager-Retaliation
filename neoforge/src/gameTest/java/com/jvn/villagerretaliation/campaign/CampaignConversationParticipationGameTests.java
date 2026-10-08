package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationParticipationGameTests {
    private static final String DECISION = """
            {"id":"decide","action":{"type":"campaign_outcome","quest":"campaign_fixture:characters",
             "milestone":"decision","outcome":"help","operation_id":"resolve"}}
            """;
    private static final String WAIT = """
            {"id":"wait","action":{"type":"start_scene","quest":"campaign_fixture:characters",
             "scene_id":"campaign_fixture:character_greeting","operation_id":"decision_wait","wait_for_result":true,"required":true}}
            """;

    private CampaignConversationParticipationGameTests() { }

    @GameTest(template = "empty", batch = "conversation_participation")
    public static void confirmedListenersKeepCreditThroughDisconnectAndSceneReload(GameTestHelper helper) throws IOException {
        try (var fixture = CampaignConversationConfirmationGameTests.fixture(helper, "[" + WAIT + "," + DECISION + "]", false)) {
            var leader = fixture.player(); var listener = fixture.player(); var distant = fixture.player();
            var late = fixture.player();
            fixture.party(leader, listener);
            for (var member : java.util.List.of(distant, late)) joinParty(helper, leader, member);
            CampaignConversationConfirmationGameTests.acceptQuest(fixture, leader);
            var result = fixture.start(leader);
            result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
            result = fixture.intent(distant, result, ConversationSession.Intent.JOIN, "");
            distant.setPos(distant.getX() + 80, distant.getY(), distant.getZ());
            result = fixture.intent(leader, result, ConversationSession.Intent.CHOOSE, "appeal");
            UUID confirmation = result.session().confirmation().id();
            UUID request = UUID.randomUUID(); long revision = result.session().revision();
            result = ConversationService.request(leader, result.session().identity().id(), revision, request,
                    ConversationSession.Intent.CONFIRM, confirmation.toString());
            var expected = Set.of(leader.getUUID(), listener.getUUID());
            helper.assertValueEqual(result.session().choices().getFirst().participants(), expected, "only present joined players witness the confirmed choice");
            var receipt = result.session().receipts().values().iterator().next();
            helper.assertTrue(receipt.result().suspended(), "outcome waits for the scene: " + result.diagnostic());
            var scenes = SceneSavedData.get(helper.getLevel());
            var scene = scenes.get(receipt.result().suspensionId()).orElseThrow();
            var savedDecision = CampaignDecisionParticipants.load(scene.decisionParticipants());
            helper.assertValueEqual(savedDecision.participants(), expected, "scene receives the decision snapshot");
            helper.assertValueEqual(SceneInstance.load(scene.save()).decisionParticipants(), scene.decisionParticipants(), "scene reload preserves evidence");
            helper.assertTrue(CampaignDecisionParticipants.capture().isEmpty(), "decision scope cannot leak into later server work");
            fixture.base.server.getPlayerList().remove(listener);
            fixture.players.remove(listener);
            fixture.storage.reload();
            result = ConversationService.view(leader, result.session().identity().id());
            result = fixture.intent(late, result, ConversationSession.Intent.JOIN, "");
            scene.complete(helper.getLevel().getGameTime());
            scenes.setDirty();
            ConversationTestStorage.maintain(fixture.base.server);
            assertParticipants(helper, fixture, expected, Set.of(distant.getUUID(), late.getUUID()));
            var personal = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).players().get(listener.getUUID());
            helper.assertValueEqual(personal.discoveredOutcomes().get("decision"), "help", "offline witness learns the committed outcome");
            helper.assertTrue(personal.milestones().isEmpty(), "witnessing does not invent personal quest progression");
            fixture.storage.reload();
            ConversationService.request(leader, result.session().identity().id(), revision, request, ConversationSession.Intent.CONFIRM, confirmation.toString());
            assertParticipants(helper, fixture, expected, Set.of(late.getUUID()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_participation")
    public static void nodeConfirmationCreditsPresentListenersWithoutAResponse(GameTestHelper helper) throws IOException {
        try (var fixture = CampaignConversationConfirmationGameTests.fixture(helper, "[" + DECISION + "]", true)) {
            var leader = fixture.player(); var listener = fixture.player();
            fixture.party(leader, listener);
            CampaignConversationConfirmationGameTests.acceptQuest(fixture, leader);
            var result = fixture.start(leader);
            result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
            result = fixture.intent(leader, result, ConversationSession.Intent.CONFIRM, result.session().confirmation().id().toString());
            helper.assertTrue(result.session().choices().isEmpty(), "node approval does not invent a response");
            fixture.storage.reload();
            helper.assertTrue(ConversationService.view(leader, result.session().identity().id()).session() != null, "node participation remains readable");
            assertParticipants(helper, fixture, Set.of(leader.getUUID(), listener.getUUID()), Set.of());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_participation")
    public static void actionContinuationRetainsDecisionEvidenceAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = CampaignConversationConfirmationGameTests.fixture(helper, "[]", false)) {
            var leader = fixture.player(); var listener = fixture.player();
            CampaignConversationConfirmationGameTests.acceptQuest(fixture, leader);
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), leader, fixture.mentor);
            var raw = new com.google.gson.JsonObject();
            var authored = new com.google.gson.JsonArray();
            authored.add(JsonParser.parseString(WAIT).getAsJsonObject().get("action"));
            authored.add(JsonParser.parseString(DECISION).getAsJsonObject().get("action"));
            raw.add("actions", authored);
            var actions = VillagerActionDefinition.readList(fixture.base.campaignId, "participation_fixture", raw);
            var prepared = CampaignQuestAcceptanceService.prepareActions(context, actions);
            helper.assertTrue(prepared.confirmation() != null, "continuation requires approval");
            var instance = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId);
            var evidence = new CampaignDecisionParticipants(UUID.randomUUID(), UUID.randomUUID(), fixture.base.campaignId,
                    instance.instanceId(), leader.getUUID(), Set.of(leader.getUUID(), listener.getUUID()));
            var applied = new SceneContinuationService.RunResult[1];
            CampaignQuestAcceptanceService.withConsent(prepared.confirmation().acceptedConsent(helper.getLevel().getGameTime()),
                    () -> applied[0] = CampaignDecisionParticipants.withParticipants(evidence.save(),
                            () -> SceneContinuationService.run(context, actions, Map.of(), null, "participation_fixture")));
            helper.assertTrue(applied[0].outcome().suspended(), "action sequence suspends");
            var data = SceneSavedData.get(helper.getLevel());
            var reloaded = SceneSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            fixture.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", reloaded);
            var continuation = reloaded.continuation(applied[0].continuationId()).orElseThrow();
            helper.assertValueEqual(continuation.decisionParticipants(), evidence.save(), "continuation saves decision evidence");
            reloaded.get(continuation.sceneInstanceId()).orElseThrow().complete(helper.getLevel().getGameTime());
            SceneContinuationService.maintain(fixture.base.server, reloaded);
            assertParticipants(helper, fixture, evidence.participants(), Set.of());
            helper.assertTrue(CampaignDecisionParticipants.capture().isEmpty(), "resumed decision scope is cleared");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_participation")
    public static void fixedOutcomeAfterACheckKeepsTheOriginalChoiceWitnesses(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper, false, true, true)) {
            var nodes = fixture.graph.getAsJsonArray("nodes");
            nodes.get(0).getAsJsonObject().add("exchanges", JsonParser.parseString("[]"));
            for (int index = 1; index < nodes.size(); index++) {
                nodes.get(index).getAsJsonObject().add("effects", JsonParser.parseString("[" + DECISION + "]"));
            }
            fixture.install(fixture.graph);
            var leader = fixture.player(); var listener = fixture.player(); var late = fixture.player();
            fixture.party(leader, listener);
            joinParty(helper, leader, late);
            CampaignConversationConfirmationGameTests.acceptQuest(fixture, leader);
            var result = fixture.start(leader);
            result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
            result = fixture.intent(leader, result, ConversationSession.Intent.CHOOSE, "appeal");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CHECK, "choice reaches its authored check");
            listener.setPos(listener.getX() + 80, listener.getY(), listener.getZ());
            result = fixture.intent(late, result, ConversationSession.Intent.JOIN, "");
            result = fixture.intent(leader, result, ConversationSession.Intent.ROLL, "");
            fixture.storage.reload();
            result = ConversationService.view(leader, result.session().identity().id());
            result = fixture.intent(leader, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(result.session().confirmation() == null, "a fixed accepted outcome needs no second approval");
            assertParticipants(helper, fixture, Set.of(leader.getUUID(), listener.getUUID()), Set.of(late.getUUID()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_participation")
    public static void sceneActionsUseTheDecisionActorAndSavedWitnesses(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper, false, true, true)) {
            var leader = fixture.player(); var listener = fixture.player();
            CampaignConversationConfirmationGameTests.acceptQuest(fixture, leader);
            var campaign = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId);
            var evidence = new CampaignDecisionParticipants(UUID.randomUUID(), UUID.randomUUID(), fixture.base.campaignId,
                    campaign.instanceId(), leader.getUUID(), Set.of(leader.getUUID(), listener.getUUID()));
            var raw = JsonParser.parseString("""
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:decision_scene","definition_version":1,
                     "ownership":"player","entry_step":"decide","actors":[],"steps":[
                      {"id":"decide","type":"villagerretaliation:action_batch","data":{"actions":[
                        {"id":"outcome","type":"campaign_outcome","quest":"campaign_fixture:characters","milestone":"decision","outcome":"help","operation_id":"scene_resolve"},
                        {"id":"reward","type":"experience","amount":9,"required":true}]},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """).getAsJsonObject();
            var sceneId = net.minecraft.resources.ResourceLocation.parse("campaign_fixture:decision_scene");
            var compiled = com.jvn.villagerretaliation.scene.compiler.SceneCompiler.compile(
                    com.jvn.villagerretaliation.scene.compiler.SceneParser.parse(sceneId, raw).resource());
            helper.assertTrue(compiled.valid(), "decision scene compiles: " + compiled.diagnostics());
            var definition = compiled.scene();
            var binding = com.jvn.villagerretaliation.scene.actor.SceneActorBinding.entity("provider",
                    net.minecraft.resources.ResourceLocation.parse("villagerretaliation:villager"), fixture.mentor.getUUID(),
                    net.minecraft.resources.ResourceLocation.parse("villagerretaliation:villager"), helper.getLevel().dimension().location(),
                    fixture.mentor.blockPosition(), "Mentor", true);
            var data = SceneSavedData.get(helper.getLevel());
            var scene = data.start(definition, "scene_decision",
                    new com.jvn.villagerretaliation.scene.runtime.SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                            leader.getUUID(), null, null, ""), null, Set.of(listener.getUUID()), Map.of("provider", binding), 0L).instance();
            scene.decisionParticipants(evidence.save());
            data = SceneSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            fixture.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", data);
            scene = data.get(scene.id()).orElseThrow();
            var invalid = evidence.save(); invalid.putInt("Version", 99);
            scene.decisionParticipants(invalid);
            com.jvn.villagerretaliation.scene.runtime.SceneStepEngine.process(fixture.base.server, data, scene, definition, 1L);
            helper.assertTrue(scene.diagnostic().contains("participation"), "unreadable scene evidence pauses execution");
            helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().isEmpty(), "invalid evidence commits no outcome");
            scene.decisionParticipants(evidence.save());
            for (int tick = 2; tick < 15 && !scene.state().terminal(); tick++) {
                com.jvn.villagerretaliation.scene.runtime.SceneStepEngine.process(fixture.base.server, data, scene, definition, tick);
            }
            helper.assertValueEqual(scene.completionResult(), SceneInstance.CompletionResult.SUCCESS, "scene actions finish: " + scene.diagnostic());
            assertParticipants(helper, fixture, evidence.participants(), Set.of());
            helper.assertValueEqual(leader.totalExperience, 9, "personal effects belong to the deciding player");
            helper.assertValueEqual(listener.totalExperience, 0, "scene attendance cannot redirect personal effects");
            helper.assertTrue(CampaignDecisionParticipants.capture().isEmpty(), "scene execution clears decision context");
        }
        helper.succeed();
    }

    private static void joinParty(GameTestHelper helper, ServerPlayer leader, ServerPlayer member) {
        var invitation = PartyService.sendInvitation(leader, member);
        helper.assertTrue(PartyService.acceptInvitation(member, invitation.invitationId()).success(), "fixture party member joins");
    }

    private static void assertParticipants(GameTestHelper helper, CampaignConversationGameTests.Fixture fixture, Set<UUID> credited, Set<UUID> excluded) {
        var instance = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId);
        helper.assertTrue(instance.outcomes().containsKey("decision"), "world decision was committed");
        for (UUID player : credited) helper.assertTrue(instance.players().getOrDefault(player, CampaignInstance.PlayerRecord.empty()).participation().contains("decision"),
                "present decision participant receives credit: " + player);
        for (UUID player : excluded) helper.assertFalse(instance.players().getOrDefault(player, CampaignInstance.PlayerRecord.empty()).participation().contains("decision"),
                "absent or late player receives no credit: " + player);
    }
}
