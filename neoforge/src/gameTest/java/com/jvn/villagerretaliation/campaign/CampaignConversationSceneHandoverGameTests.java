package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
public final class CampaignConversationSceneHandoverGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("campaign_fixture:characters");
    private static final String EFFECTS = """
            [{"id":"wait","action":{"type":"start_scene","quest":"campaign_fixture:characters",
              "scene_id":"campaign_fixture:character_greeting","operation_id":"delegated_scene","wait_for_result":true,"required":true}},
             {"id":"decide","action":{"type":"campaign_outcome","quest":"campaign_fixture:characters",
              "milestone":"decision","outcome":"help","operation_id":"scene_decision"}}]
            """;

    private CampaignConversationSceneHandoverGameTests() { }

    @GameTest(template = "empty", batch = "conversation_scene_handover")
    public static void offlineChooserKeepsPersonalEffectsWhileSuccessorResumesTheScene(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var definition = scene(fixture);
            var chooser = fixture.player(); var successor = fixture.player();
            fixture.party(chooser, successor);
            PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
            var result = approve(fixture, chooser, successor);
            UUID conversation = result.session().identity().id();
            var savedScene = waitingScene(fixture, result);
            var source = savedScene.conversationEffect();
            helper.assertFalse(source.isEmpty(), "launch captures the exact conversation effect");
            helper.assertTrue(ConversationSceneExecution.capture().isEmpty(), "launch restores its enclosing execution scope");
            disconnect(fixture, chooser);
            fixture.storage.reload();
            var data = reload(fixture);
            savedScene = data.get(savedScene.id()).orElseThrow();
            helper.assertValueEqual(savedScene.conversationEffect(), source, "scene link survives reload");
            result = fixture.intent(successor, ConversationService.view(successor, conversation), ConversationSession.Intent.TAKE_CONTROL, "");
            run(fixture, data, savedScene, definition, 1);
            var commit = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision");
            helper.assertTrue(commit != null, "successor commits the approved outcome for the offline chooser: " + savedScene.diagnostic());
            helper.assertFalse(savedScene.state().terminal(), "personal effects wait for their original player");
            helper.assertValueEqual(successor.totalExperience, 0, "the successor never receives the chooser's reward");
            data = reload(fixture);
            savedScene = data.get(savedScene.id()).orElseThrow();
            var returned = fixture.reconnect(chooser);
            SceneStepEngine.clearRuntimeState();
            run(fixture, data, savedScene, definition, 100);
            helper.assertValueEqual(savedScene.completionResult(), SceneInstance.CompletionResult.SUCCESS, "scene resumes after the owner returns: " + savedScene.diagnostic());
            helper.assertValueEqual(returned.totalExperience, 9, "personal effect goes to the returning original player");
            helper.assertValueEqual(successor.totalExperience, 0, "personal recovery never redirects rewards");
            result = fixture.intent(successor, ConversationService.view(successor, conversation), ConversationSession.Intent.RESUME, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CHECK, "conversation continues beyond the completed scene: " + result.diagnostic());
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision"), commit,
                    "the later conversation effect reuses the scene's durable outcome");
            fixture.storage.reload();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(returned.totalExperience, 9, "scene reload and conversation recovery cannot repeat the reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_scene_handover")
    public static void revokedControllerPausesSceneUntilAnAuthorizedMemberTakesControl(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var definition = scene(fixture);
            var leader = fixture.player(); var chooser = fixture.player();
            fixture.party(leader, chooser);
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), true);
            var result = approve(fixture, chooser, leader);
            var savedScene = waitingScene(fixture, result);
            var data = SceneSavedData.get(helper.getLevel());
            PartyService.setAdminPrivileges(leader, chooser.getUUID(), false);
            run(fixture, data, savedScene, definition, 1);
            helper.assertTrue(savedScene.receipts().isEmpty(), "revoked authority pauses before any scene effects");
            helper.assertTrue(savedScene.diagnostic().contains("Admin Privileges"), "the scene explains the missing authority");
            result = fixture.intent(leader, result, ConversationSession.Intent.TAKE_CONTROL, "");
            run(fixture, data, savedScene, definition, 100);
            helper.assertValueEqual(savedScene.completionResult(), SceneInstance.CompletionResult.SUCCESS, "authorized handover resumes scene effects: " + savedScene.diagnostic());
            helper.assertValueEqual(chooser.totalExperience, 9, "revoked chooser retains personal reward ownership");
            helper.assertValueEqual(leader.totalExperience, 0, "controller authority does not transfer personal rewards");
            var campaign = CampaignResources.current(fixture.base.server).campaigns().get(fixture.base.campaignId).definition();
            var saved = CampaignSavedData.get(helper.getLevel());
            helper.assertValueEqual(saved.readFact(campaign, chooser.getUUID(), "observed").value().text(), "true", "current controller authorizes the original player's personal fact");
            helper.assertTrue(saved.readFact(campaign, leader.getUUID(), "observed").value() == null, "delegated authority never redirects personal discovery");
            helper.assertFalse(CampaignAuthority.controlIssue(chooser).isBlank(), "execution does not grant the chooser persistent authority");
            helper.assertFalse(CampaignAuthority.canControl(chooser), "party Admin Privileges remain revoked");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_scene_handover")
    public static void corruptedSceneLinksPauseWithoutInventingLegacyAuthority(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var definition = scene(fixture);
            var chooser = fixture.player();
            var result = approve(fixture, chooser, null);
            var savedScene = waitingScene(fixture, result);
            var data = SceneSavedData.get(helper.getLevel());
            var source = savedScene.conversationEffect();
            var legacy = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            legacy.putInt("DataVersion", 15);
            for (var raw : legacy.getList("Instances", Tag.TAG_COMPOUND)) ((CompoundTag) raw).remove("ConversationEffect");
            helper.assertTrue(SceneSavedData.load(legacy, helper.getLevel().registryAccess()).get(savedScene.id()).orElseThrow().conversationEffect().isEmpty(),
                    "legacy migration does not invent a conversation link");
            for (String field : List.of("Effect", "Session")) {
                var invalid = source.copy();
                if (field.equals("Effect")) invalid.putString(field, "1/response/appeal/some_other_effect");
                else invalid.putUUID(field, UUID.randomUUID());
                savedScene.conversationEffect(invalid);
                run(fixture, data, savedScene, definition, 1);
                helper.assertTrue(savedScene.receipts().isEmpty(), "a mismatched " + field + " cannot authorize scene effects");
            }
            var malformed = savedScene.save(); malformed.putString("ConversationEffect", "broken link");
            var unreadable = SceneInstance.load(malformed);
            helper.assertTrue(unreadable.conversationEffect().contains("Unreadable"), "malformed link is preserved for repair");
            helper.assertFalse(ConversationSceneExecution.resolve(fixture.base.server, unreadable).diagnostic().isBlank(), "malformed source cannot fall back to ordinary authority");
            savedScene.conversationEffect(source);
            var evidence = savedScene.decisionParticipants(); savedScene.decisionParticipants(new CompoundTag());
            run(fixture, data, savedScene, definition, 50);
            helper.assertTrue(savedScene.receipts().isEmpty(), "missing saved participation cannot silently remove witnesses");
            savedScene.decisionParticipants(evidence);
            run(fixture, data, savedScene, definition, 100);
            helper.assertValueEqual(savedScene.completionResult(), SceneInstance.CompletionResult.SUCCESS, "restored source and evidence resume the scene: " + savedScene.diagnostic());
            helper.assertValueEqual(chooser.totalExperience, 9, "repair applies the personal reward once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_scene_handover")
    public static void recordedPersonalEffectsDoNotBlockAnOfflinePlayersSharedOutcome(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var definition = scene(fixture, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                     "ownership":"player","entry_step":"decide","actors":[],"steps":[
                      {"id":"decide","type":"villagerretaliation:action_batch","data":{"actions":[
                        {"id":"personal","type":"experience","amount":9,"required":true},
                        {"id":"outcome","type":"campaign_outcome","quest":"campaign_fixture:characters",
                         "milestone":"decision","outcome":"help","operation_id":"scene_decision","required":true}]},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """);
            var chooser = fixture.player(); var successor = fixture.player();
            fixture.party(chooser, successor);
            PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
            var result = approve(fixture, chooser, successor);
            var savedScene = waitingScene(fixture, result);
            var data = SceneSavedData.get(helper.getLevel());
            SceneStepEngine.process(fixture.base.server, data, savedScene, definition, 1);
            savedScene.prepareReceipt(savedScene.id() + "/decide/action/personal",
                    com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt.Kind.EXPERIENCE_GRANT, 2)
                    .recordAction(com.jvn.villagerretaliation.action.ActionResult.success(
                            com.jvn.villagerretaliation.action.VillagerActionResult.EMPTY, java.util.Set.of()), 2);
            data = reload(fixture);
            savedScene = data.get(savedScene.id()).orElseThrow();
            SceneStepEngine.clearRuntimeState();
            disconnect(fixture, chooser);
            takeControl(fixture, successor, result);
            run(fixture, data, savedScene, definition, 100);
            helper.assertValueEqual(savedScene.completionResult(), SceneInstance.CompletionResult.SUCCESS, "completed personal receipt does not require its owner online: " + savedScene.diagnostic());
            helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().containsKey("decision"), "successor finishes the approved shared outcome");
            helper.assertValueEqual(successor.totalExperience, 0, "completed personal receipt cannot reward the successor");
            var returned = fixture.reconnect(chooser);
            helper.assertValueEqual(returned.totalExperience, 0, "completed personal receipt cannot replay its reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_scene_handover")
    public static void personalQuestTransitionsResumeAfterTheOriginalPlayerReturns(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var definition = scene(fixture, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                     "ownership":"player","entry_step":"decide","actors":[],"steps":[
                      {"id":"decide","type":"villagerretaliation:action_batch","data":{"actions":[
                        {"id":"outcome","type":"campaign_outcome","quest":"campaign_fixture:characters",
                         "milestone":"decision","outcome":"help","operation_id":"scene_decision","required":true}]},"next":"report"},
                      {"id":"report","type":"villagerretaliation:quest_transition",
                       "data":{"quest":"campaign_fixture:characters","target_stage":"report"},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """);
            var chooser = fixture.player(); var successor = fixture.player();
            fixture.party(chooser, successor);
            PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
            var result = approve(fixture, chooser, successor);
            var savedScene = waitingScene(fixture, result);
            disconnect(fixture, chooser);
            takeControl(fixture, successor, result);
            var data = SceneSavedData.get(helper.getLevel());
            run(fixture, data, savedScene, definition, 1);
            helper.assertTrue(savedScene.receipts().values().stream().noneMatch(receipt ->
                    receipt.kind() == com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt.Kind.QUEST_TRANSITION), "offline player cannot receive a quest transition");
            data = reload(fixture);
            savedScene = data.get(savedScene.id()).orElseThrow();
            fixture.reconnect(chooser);
            SceneStepEngine.clearRuntimeState();
            run(fixture, data, savedScene, definition, 100);
            helper.assertValueEqual(savedScene.completionResult(), SceneInstance.CompletionResult.SUCCESS, "saved actor wait permits the original transition: " + savedScene.diagnostic());
            var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(helper.getLevel());
            helper.assertValueEqual(progress.get(chooser.getUUID(), QUEST).currentStage(), "report", "the original player's quest advances");
            helper.assertTrue(progress.get(successor.getUUID(), QUEST) == null, "handover does not invent progress for the controller");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_scene_handover")
    public static void missingPlayersCannotMakeAmbiguousEffectsReplayable(GameTestHelper helper) throws IOException {
        for (boolean transition : List.of(false, true)) {
            try (var fixture = fixture(helper)) {
                var definition = transition ? transitionScene(fixture) : scene(fixture, """
                        {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                         "ownership":"player","entry_step":"personal","actors":[],"steps":[
                          {"id":"personal","type":"villagerretaliation:action_batch","data":{"actions":[
                            {"id":"reward","type":"experience","amount":9,"required":true}]},"next":"done"},
                          {"id":"done","type":"villagerretaliation:scene_complete"}]}
                        """);
                var chooser = fixture.player(); var successor = fixture.player();
                fixture.party(chooser, successor);
                PartyService.setAdminPrivileges(chooser, successor.getUUID(), true);
                var result = approve(fixture, chooser, successor);
                var savedScene = waitingScene(fixture, result);
                var data = SceneSavedData.get(helper.getLevel());
                SceneStepEngine.process(fixture.base.server, data, savedScene, definition, 1);
                data = reload(fixture);
                savedScene = data.get(savedScene.id()).orElseThrow();
                SceneStepEngine.clearRuntimeState();
                disconnect(fixture, chooser);
                takeControl(fixture, successor, result);
                run(fixture, data, savedScene, definition, 10);
                var returned = fixture.reconnect(chooser);
                run(fixture, data, savedScene, definition, 100);
                helper.assertValueEqual(savedScene.state(), com.jvn.villagerretaliation.scene.runtime.SceneState.BLOCKED, "uncertain prepared effects still require repair");
                helper.assertTrue(savedScene.diagnostic().contains("cannot prove"), "recovery explains the ambiguous effect");
                helper.assertValueEqual(returned.totalExperience, 0, "reconnecting does not replay an ambiguous reward");
                helper.assertValueEqual(com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(helper.getLevel())
                        .get(chooser.getUUID(), QUEST).currentStage(), "visit", "reconnecting does not replay an ambiguous transition");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_scene_handover")
    public static void nestedScenesKeepTheConversationControllerAndOriginalActor(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var nestedId = ResourceLocation.parse("campaign_fixture:nested_greeting");
            var definition = scene(fixture, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                     "ownership":"player","entry_step":"nested","actors":[],"steps":[
                      {"id":"nested","type":"villagerretaliation:action_batch","data":{"actions":[
                        {"id":"child","type":"start_scene","quest":"campaign_fixture:characters","scene_id":"campaign_fixture:nested_greeting",
                         "operation_id":"nested_scene","wait_for_result":true,"required":true},
                        {"id":"personal","type":"experience","amount":4,"required":true}]},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:nested_greeting","definition_version":1,
                     "ownership":"player","entry_step":"decide","actors":[],"steps":[
                      {"id":"decide","type":"villagerretaliation:action_batch","data":{"actions":[
                        {"id":"outcome","type":"campaign_outcome","quest":"campaign_fixture:characters",
                         "milestone":"decision","outcome":"help","operation_id":"scene_decision","required":true},
                        {"id":"personal","type":"experience","amount":9,"required":true}]},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """);
            var successor = fixture.player(); var chooser = fixture.player();
            fixture.party(successor, chooser);
            PartyService.setAdminPrivileges(successor, chooser.getUUID(), true);
            var result = approve(fixture, chooser, successor);
            var parent = waitingScene(fixture, result);
            var data = SceneSavedData.get(helper.getLevel());
            run(fixture, data, parent, definition, 1);
            var child = data.all().stream().filter(value -> value.sceneId().equals(nestedId)).findFirst().orElseThrow();
            var nested = QuestContentCatalogs.current(fixture.base.server).scenes().get(nestedId);
            helper.assertValueEqual(child.conversationEffect(), parent.conversationEffect(), "child retains the exact conversation source");
            helper.assertTrue(ConversationSceneExecution.capture().isEmpty(), "nested execution restores its source scope");
            var unrelated = child.save(); unrelated.putUUID("InstanceId", UUID.randomUUID());
            helper.assertFalse(ConversationSceneExecution.resolve(fixture.base.server, SceneInstance.load(unrelated)).diagnostic().isBlank(),
                    "copying a source cannot authorize a scene outside the saved dependency chain");
            data = reload(fixture);
            parent = data.get(parent.id()).orElseThrow(); child = data.get(child.id()).orElseThrow();
            SceneStepEngine.clearRuntimeState();
            PartyService.setAdminPrivileges(successor, chooser.getUUID(), false);
            run(fixture, data, child, nested, 100);
            helper.assertTrue(child.receipts().isEmpty(), "revoked controller cannot run nested scene effects");
            takeControl(fixture, successor, result);
            run(fixture, data, child, nested, 200);
            helper.assertValueEqual(child.completionResult(), SceneInstance.CompletionResult.SUCCESS, "authorized successor completes the child: " + child.diagnostic());
            run(fixture, data, parent, definition, 300);
            helper.assertValueEqual(parent.completionResult(), SceneInstance.CompletionResult.SUCCESS, "parent resumes its recorded child wait: " + parent.diagnostic());
            helper.assertValueEqual(chooser.totalExperience, 13, "both scenes retain the original reward owner");
            helper.assertValueEqual(successor.totalExperience, 0, "controller handover never transfers scene rewards");
            result = fixture.intent(successor, ConversationService.view(successor, result.session().identity().id()), ConversationSession.Intent.RESUME, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CHECK, "conversation resumes beyond the whole scene chain: " + result.diagnostic());
            helper.assertFalse(CampaignAuthority.canControl(chooser), "nested execution does not restore revoked privileges");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_scene_handover")
    public static void personalConversationEffectsUseTheSuccessorsAuthorityAfterAScene(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("responses").get(0)
                    .getAsJsonObject().getAsJsonArray("effects").add(JsonParser.parseString("""
                            {"id":"discovery","action":{"type":"campaign_fact","quest":"campaign_fixture:characters",
                             "fact":"observed","value":"true","operation_id":"personal_discovery","required":true}}
                            """));
            fixture.install(fixture.graph);
            var definition = scene(fixture, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                     "ownership":"player","entry_step":"done","actors":[],"steps":[{"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """);
            var successor = fixture.player(); var chooser = fixture.player();
            fixture.party(successor, chooser);
            PartyService.setAdminPrivileges(successor, chooser.getUUID(), true);
            var result = approve(fixture, chooser, successor);
            var savedScene = waitingScene(fixture, result);
            PartyService.setAdminPrivileges(successor, chooser.getUUID(), false);
            takeControl(fixture, successor, result);
            run(fixture, SceneSavedData.get(helper.getLevel()), savedScene, definition, 1);
            result = fixture.intent(successor, ConversationService.view(successor, result.session().identity().id()), ConversationSession.Intent.RESUME, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CHECK, "conversation effects use the current authority: " + result.diagnostic());
            var campaign = CampaignResources.current(fixture.base.server).campaigns().get(fixture.base.campaignId).definition();
            var data = CampaignSavedData.get(helper.getLevel());
            helper.assertValueEqual(data.readFact(campaign, chooser.getUUID(), "observed").value().text(), "true", "conversation discovery stays with the deciding player");
            helper.assertTrue(data.readFact(campaign, successor.getUUID(), "observed").value() == null, "successor does not inherit personal discovery");
            helper.assertFalse(CampaignAuthority.controlIssue(chooser).isBlank(), "effect authority ends when execution returns");
            fixture.storage.reload();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(ConversationService.view(successor, result.session().identity().id()).session().phase(),
                    ConversationSession.Phase.CHECK, "saved personal effect stays resolved after reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_scene_handover")
    public static void independentScenesDoNotInheritAConversationWait(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            fixture.effects("""
                    [{"id":"greet","action":{"type":"start_scene","scene_id":"campaign_fixture:character_greeting",
                      "operation_id":"independent_greeting","wait_for_result":false,"required":true}}]
                    """);
            var player = fixture.player();
            var result = fixture.start(player);
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.EXCHANGES, "conversation continues without waiting");
            var scenes = SceneSavedData.get(helper.getLevel()).all();
            helper.assertValueEqual(scenes.size(), 1, "independent scene launches once");
            helper.assertTrue(scenes.getFirst().conversationEffect().isEmpty(), "independent scene is not tied to a finished conversation effect");
        }
        helper.succeed();
    }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper) throws IOException {
        var fixture = new CampaignConversationGameTests.Fixture(helper, false, true, false, campaign ->
                campaign.add("facts", JsonParser.parseString("[{\"id\":\"observed\",\"scope\":\"player\",\"kind\":\"tag\"}]")));
        var opening = fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
        opening.add("exchanges", JsonParser.parseString("[]"));
        opening.getAsJsonArray("responses").get(0).getAsJsonObject().add("effects", JsonParser.parseString(EFFECTS));
        fixture.install(fixture.graph);
        return fixture;
    }

    private static CompiledScene transitionScene(CampaignConversationGameTests.Fixture fixture) {
        return scene(fixture, """
                {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                 "ownership":"player","entry_step":"report","actors":[],"steps":[
                  {"id":"report","type":"villagerretaliation:quest_transition",
                   "data":{"quest":"campaign_fixture:characters","target_stage":"report"},"next":"done"},
                  {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """);
    }

    private static CompiledScene scene(CampaignConversationGameTests.Fixture fixture) {
        return scene(fixture, """
                {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                 "ownership":"player","entry_step":"decide","actors":[],"steps":[
                  {"id":"decide","type":"villagerretaliation:action_batch","data":{"actions":[
                    {"id":"outcome","type":"campaign_outcome","quest":"campaign_fixture:characters",
                     "milestone":"decision","outcome":"help","operation_id":"scene_decision","required":true},
                    {"id":"personal","type":"experience","amount":9,"required":true},
                    {"id":"observed","type":"campaign_fact","quest":"campaign_fixture:characters","fact":"observed",
                     "value":"true","operation_id":"scene_observed","required":true}]},"next":"done"},
                  {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """);
    }

    private static CompiledScene scene(CampaignConversationGameTests.Fixture fixture, String json, String... additional) {
        var raw = JsonParser.parseString(json).getAsJsonObject();
        var definition = SceneCompiler.compile(SceneParser.parse(fixture.base.sceneId, raw).resource());
        fixture.helper.assertTrue(definition.valid(), "handover scene compiles: " + definition.diagnostics());
        Map<ResourceLocation, CompiledScene> scenes = new java.util.LinkedHashMap<>();
        scenes.put(fixture.base.sceneId, definition.scene());
        for (String extra : additional) {
            var resource = JsonParser.parseString(extra).getAsJsonObject();
            var id = ResourceLocation.parse(resource.get("id").getAsString());
            var compiled = SceneCompiler.compile(SceneParser.parse(id, resource).resource());
            fixture.helper.assertTrue(compiled.valid(), "nested scene compiles: " + compiled.diagnostics());
            scenes.put(id, compiled.scene());
        }
        var old = QuestContentCatalogs.current(fixture.base.server);
        var campaigns = CampaignResources.current(fixture.base.server).campaigns();
        var catalog = new QuestContentCatalog(old.generation() + 1, old.compiledQuestCatalog(), old.dialogueCatalog(), old.quests(),
                old.objectiveEventQuestIds(), old.factQuestIds(), old.memoryEventQuestIds(), old.exclusiveGroupQuestIds(), old.triggerEventQuestIds(),
                scenes, old.encounters(), old.pools(), old.bundles(), old.localization(), old.rewards(), old.conversations());
        QuestContentCatalogs.installForTests(fixture.base.server, catalog, fixture.base.report);
        CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(catalog, campaigns, List.of()));
        return definition.scene();
    }

    private static ConversationService.Result approve(CampaignConversationGameTests.Fixture fixture, ServerPlayer chooser, ServerPlayer listener) throws IOException {
        CampaignConversationConfirmationGameTests.acceptQuest(fixture, chooser);
        var result = fixture.start(chooser);
        if (listener != null) result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
        result = fixture.intent(chooser, result, ConversationSession.Intent.CHOOSE, "appeal");
        var confirmation = result.session().confirmation();
        fixture.helper.assertTrue(confirmation != null, "scene decision requires explicit approval: " + result.diagnostic());
        result = fixture.intent(chooser, result, ConversationSession.Intent.CONFIRM, confirmation.id().toString());
        fixture.helper.assertTrue(result.session().receipts().values().iterator().next().result().suspended(), "conversation waits for its launched scene");
        return result;
    }

    private static void takeControl(CampaignConversationGameTests.Fixture fixture, ServerPlayer successor, ConversationService.Result prior) {
        var current = ConversationService.view(successor, prior.session().identity().id());
        var result = fixture.intent(successor, current, ConversationSession.Intent.TAKE_CONTROL, "");
        fixture.helper.assertValueEqual(result.session().controller(), successor.getUUID(), "successor takes control at the current revision: " + result.diagnostic());
    }

    private static SceneInstance waitingScene(CampaignConversationGameTests.Fixture fixture, ConversationService.Result result) {
        var receipt = result.session().receipts().values().iterator().next();
        return SceneSavedData.get(fixture.helper.getLevel()).get(receipt.result().suspensionId()).orElseThrow();
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

    private static void run(CampaignConversationGameTests.Fixture fixture, SceneSavedData data, SceneInstance scene, CompiledScene definition, int start) {
        for (int tick = start; tick < start + 25 && !scene.state().terminal(); tick++) SceneStepEngine.process(fixture.base.server, data, scene, definition, tick);
    }
}
