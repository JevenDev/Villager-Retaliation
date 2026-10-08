package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.SceneLaunchService;
import com.jvn.villagerretaliation.scene.SceneRuntime;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignSceneApprovalGameTests {
    private static final String DECISION = """
            {"id":"outcome","type":"campaign_outcome","quest":"campaign_fixture:characters",
             "milestone":"decision","outcome":"help","operation_id":"approved_scene"}
            """;

    private CampaignSceneApprovalGameTests() { }

    @GameTest(template = "empty", batch = "campaign_scene_approval")
    public static void sceneLaunchRetainsApprovalAcrossReloadAndDuplicateLaunch(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var player = fixture.player();
            var consent = approval(fixture, player);
            var progress = VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), fixture.base.campaignId);
            var request = new SceneLaunchService.LaunchRequest(fixture.base.server, fixture.base.sceneId, "approval_capture", true,
                    player.getUUID(), fixture.mentor.getUUID(), fixture.base.campaignId, progress.questRunId());
            var launched = new SceneLaunchService.LaunchResult[1];
            CampaignQuestAcceptanceService.withConsent(consent, () -> launched[0] = SceneRuntime.launch(request));
            helper.assertTrue(launched[0].created(), "approved scene is created");
            var data = reload(fixture);
            var scene = data.get(launched[0].instanceId()).orElseThrow();
            helper.assertValueEqual(scene.campaignConsent(), consent, "scene keeps its approved decision after reload");
            var repeated = SceneRuntime.launch(request);
            helper.assertFalse(repeated.created(), "duplicate launch reuses its scene");
            helper.assertValueEqual(data.get(repeated.instanceId()).orElseThrow().campaignConsent(), consent, "duplicate launch cannot erase approval");
            helper.assertTrue(CampaignQuestAcceptanceService.captureConsent().isEmpty(), "launch scope does not leak approval");

            var legacy = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            legacy.putInt("DataVersion", 14);
            for (var raw : legacy.getList("Instances", Tag.TAG_COMPOUND)) ((CompoundTag) raw).remove("CampaignConsent");
            var migrated = SceneSavedData.load(legacy, helper.getLevel().registryAccess());
            helper.assertTrue(migrated.get(scene.id()).orElseThrow().campaignConsent().isEmpty(), "migration cannot invent an earlier approval");
            helper.assertValueEqual(migrated.save(new CompoundTag(), helper.getLevel().registryAccess()).getInt("DataVersion"),
                    SceneSavedData.CURRENT_DATA_VERSION, "migration advances the scene format");
            var malformed = scene.save();
            malformed.putString("CampaignConsent", "unreadable approval");
            var retained = SceneInstance.load(malformed);
            helper.assertTrue(retained.campaignConsent().contains("Unreadable"), "wrongly typed approval is retained for repair");
            helper.assertFalse(CampaignSceneApproval.resolve(fixture.base.server, retained).diagnostic().isBlank(), "unreadable approval prevents execution");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_approval")
    public static void revokedAndMalformedApprovalPauseBeforeAnySceneEffects(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var leader = fixture.player(); var player = fixture.player();
            fixture.party(leader, player);
            PartyService.setAdminPrivileges(leader, player.getUUID(), true);
            var consent = approval(fixture, player);
            var definition = definition(helper, true);
            var scene = start(fixture, player, leader, definition, consent);
            var data = reload(fixture);
            scene = data.get(scene.id()).orElseThrow();
            var invalid = consent.copy(); invalid.putInt("Version", 99);
            scene.campaignConsent(invalid);
            SceneStepEngine.process(fixture.base.server, data, scene, definition, 1L);
            helper.assertTrue(scene.diagnostic().contains("consent"), "malformed approval has a recoverable diagnostic");
            helper.assertValueEqual(player.totalExperience, 0, "unreadable approval prevents preceding effects");
            scene.campaignConsent(consent);
            PartyService.setAdminPrivileges(leader, player.getUUID(), false);
            SceneStepEngine.process(fixture.base.server, data, scene, definition, 2L);
            helper.assertTrue(scene.diagnostic().contains("Admin Privileges"), "revoked authority pauses the scene");
            helper.assertTrue(scene.receipts().isEmpty(), "paused approval creates no effect receipts");
            PartyService.setAdminPrivileges(leader, player.getUUID(), true);
            finish(fixture, data, scene, definition, 3);
            helper.assertValueEqual(scene.completionResult(), SceneInstance.CompletionResult.SUCCESS, "restored authority resumes scene: " + scene.diagnostic());
            helper.assertValueEqual(player.totalExperience, 12, "approved actor receives the authored personal effects");
            helper.assertValueEqual(leader.totalExperience, 0, "scene attendance cannot redirect approved personal effects");
            var outcome = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision");
            helper.assertValueEqual(outcome.operationId(), CampaignQuestConsent.load(consent).decisions().keySet().iterator().next(), "scene uses the approved operation");
            helper.assertTrue(CampaignQuestAcceptanceService.captureConsent().isEmpty(), "scene execution restores the outer approval scope");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_approval")
    public static void preparedSceneOutcomeRecoversItsDurableCommit(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var player = fixture.player();
            var consent = approval(fixture, player);
            var definition = definition(helper, false);
            var scene = start(fixture, player, player, definition, consent);
            var data = SceneSavedData.get(helper.getLevel());
            SceneStepEngine.process(fixture.base.server, data, scene, definition, 0L);
            scene.prepareReceipt(scene.id() + "/decide/action/outcome", SceneOperationReceipt.Kind.ITEM_GRANT, 0L);
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, fixture.mentor);
            var committed = new ActionResult[1];
            CampaignQuestAcceptanceService.withConsent(consent,
                    () -> committed[0] = VillagerActionRegistry.execute(context, action(), Map.of()));
            helper.assertTrue(committed[0].success(), "world outcome commits before the scene receipt is saved");
            var before = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision");
            data = reload(fixture);
            scene = data.get(scene.id()).orElseThrow();
            SceneStepEngine.clearRuntimeState();
            finish(fixture, data, scene, definition, 1);
            helper.assertValueEqual(scene.completionResult(), SceneInstance.CompletionResult.SUCCESS, "durable outcome resolves a prepared receipt: " + scene.diagnostic());
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).outcomes().get("decision"), before,
                    "prepared recovery does not recommit the world decision");
            helper.assertTrue(scene.receipts().values().stream().allMatch(SceneOperationReceipt::settled), "recovery finishes the existing scene receipt");
            helper.assertTrue(CampaignSceneApproval.canRecover(scene, action()), "saved approval proves the recoverable operation");
            var changed = JsonParser.parseString(DECISION).getAsJsonObject(); changed.addProperty("operation_id", "different_operation");
            var wrapper = new com.google.gson.JsonObject(); var actions = new com.google.gson.JsonArray(); actions.add(changed); wrapper.add("actions", actions);
            var other = VillagerActionDefinition.readList(fixture.base.campaignId, "scene approval", wrapper).getFirst();
            helper.assertFalse(CampaignSceneApproval.canRecover(scene, other), "approval cannot authorize recovery of another operation");
        }
        helper.succeed();
    }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper) throws IOException {
        return new CampaignConversationGameTests.Fixture(helper, false, true);
    }

    private static CompoundTag approval(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) throws IOException {
        CampaignConversationConfirmationGameTests.acceptQuest(fixture, player);
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, fixture.mentor);
        var prepared = CampaignQuestAcceptanceService.prepareActions(context, List.of(action()));
        fixture.helper.assertTrue(prepared.confirmation() != null, "scene outcome requires explicit approval: " + prepared.diagnostic());
        return prepared.confirmation().acceptedConsent(player.serverLevel().getGameTime());
    }

    private static VillagerActionDefinition action() {
        return VillagerActionDefinition.readList(ResourceLocation.parse("campaign_fixture:characters"), "scene approval",
                JsonParser.parseString("{\"actions\":[" + DECISION + "]}").getAsJsonObject()).getFirst();
    }

    private static CompiledScene definition(GameTestHelper helper, boolean personal) {
        String actions = personal ? "{\"id\":\"before\",\"type\":\"experience\",\"amount\":5,\"required\":true}," + DECISION
                + ",{\"id\":\"after\",\"type\":\"experience\",\"amount\":7,\"required\":true}" : DECISION;
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:approved_scene","definition_version":1,
                 "ownership":"player","entry_step":"decide","actors":[],"steps":[
                  {"id":"decide","type":"villagerretaliation:action_batch","data":{"actions":[%s]},"next":"done"},
                  {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """.formatted(actions)).getAsJsonObject();
        var compiled = SceneCompiler.compile(SceneParser.parse(ResourceLocation.parse("campaign_fixture:approved_scene"), raw).resource());
        helper.assertTrue(compiled.valid(), "approved scene compiles: " + compiled.diagnostics());
        return compiled.scene();
    }

    private static SceneInstance start(CampaignConversationGameTests.Fixture fixture, ServerPlayer player, ServerPlayer attendee,
            CompiledScene definition, CompoundTag consent) {
        var progress = VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), fixture.base.campaignId);
        var scene = SceneSavedData.get(player.serverLevel()).start(definition, UUID.randomUUID().toString(),
                new SceneOwner(SceneResource.OwnershipMode.PLAYER, player.getUUID(), null, null, ""), progress.questRunId(),
                Set.of(attendee.getUUID()), Map.of(), 0L, fixture.base.campaignId).instance();
        scene.bindCampaign(fixture.base.campaignId);
        scene.campaignConsent(consent);
        return scene;
    }

    private static SceneSavedData reload(CampaignConversationGameTests.Fixture fixture) {
        var data = SceneSavedData.get(fixture.helper.getLevel());
        var loaded = SceneSavedData.load(data.save(new CompoundTag(), fixture.helper.getLevel().registryAccess()), fixture.helper.getLevel().registryAccess());
        fixture.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", loaded);
        return loaded;
    }

    private static void finish(CampaignConversationGameTests.Fixture fixture, SceneSavedData data, SceneInstance scene, CompiledScene definition, int start) {
        for (int tick = start; tick < start + 16 && !scene.state().terminal(); tick++) {
            SceneStepEngine.process(fixture.base.server, data, scene, definition, tick);
        }
    }
}
