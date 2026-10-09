package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.quest.runtime.QuestStageActionService;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneActionSuspension;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignRewardRecoveryGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("campaign_fixture:losses_catch_up");
    private static final String REWARD = "{\"type\":\"campaign_reward\",\"reward\":\"loss_catch_up_personal\",\"operation_id\":\"recovery_reward\"}";

    private CampaignRewardRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void failedPreparationResumesTheSavedStageCursorAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actions = fixture.actions(REWARD);
            fixture.blockRewards();
            var first = fixture.stage(actions);
            helper.assertTrue(first.suspended() && first.suspensionKind() == ActionResult.SuspensionKind.CONTINUATION, "stage waits on a persistent continuation");
            helper.assertValueEqual(fixture.player.totalExperience, 3, "only the prefix runs before failed preparation");
            helper.assertTrue(fixture.rewards().receipts().isEmpty(), "failed preparation publishes no reward");
            var pending = fixture.continuation();
            helper.assertValueEqual(pending.nextActionIndex(), 1, "cursor points at the undelivered reward");
            helper.assertTrue(pending.sceneInstanceId() == null, "reward wait requires no fabricated scene");
            fixture.stage(actions);
            helper.assertValueEqual(fixture.player.totalExperience, 3, "duplicate request cannot replay the prefix");
            fixture.restoreRewards(); fixture.reload(); fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 15, "retry delivers the reward and remaining suffix once");
            helper.assertTrue(QuestStageActionService.ready(fixture.context(), fixture.progress()), "recovered delivery releases the stage checkpoint");
            var receipt = fixture.rewards().receipts().values().iterator().next();
            fixture.reload(); fixture.retry(); fixture.stage(actions);
            helper.assertValueEqual(fixture.player.totalExperience, 15, "reload and duplicate acknowledgement replay no effects");
            helper.assertValueEqual(fixture.rewards().receipts().get(receipt.operation()), receipt, "reward keeps its original durable receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void playerDeliveryBeforeWorldAcknowledgementCannotDuplicateExperience(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var definition = CampaignResources.current(fixture.base.server).campaigns().get(fixture.base.campaignId).definition();
            var instance = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId);
            var reward = definition.rewards().get("loss_catch_up_personal");
            var operation = CampaignRewardService.operationId(instance.instanceId(), fixture.player.getUUID(), reward, "recovery_reward");
            var receipt = fixture.rewards().prepare(new CampaignRewardReceipt(operation, UUID.randomUUID(), definition.id(), instance.instanceId(),
                    definition.revision(), definition.fingerprint(), fixture.player.getUUID(), QUEST, fixture.progress().questRunId(), reward.id(),
                    "recovery_reward", reward.scope(), reward.milestone(), reward.experience(), List.of(), helper.getLevel().getGameTime(), false));
            CampaignPlayerRewards.deliver(fixture.player, receipt, CampaignPlayerRewards.directory(fixture.player));
            fixture.blockRewards(); helper.assertTrue(fixture.stage(fixture.actions(REWARD)).suspended(), "failed world acknowledgement waits");
            helper.assertValueEqual(fixture.player.totalExperience, 8, "player receipt prevents duplicate delivery during the failed acknowledgement");
            fixture.restoreRewards(); fixture.reload(); CampaignRewardService.recover(fixture.base.server); fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 15, "recovery acknowledges delivery and runs only the suffix");
            helper.assertTrue(fixture.rewards().receipts().get(operation).delivered(), "world acknowledgement becomes durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void unavailableSceneStoragePreventsEverySequenceEffect(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actions = fixture.actions(REWARD);
            try (var blocked = new BlockedSceneFile(fixture)) {
                helper.assertTrue(fixture.stage(actions).suspended(), "sequence waits for its initial save");
                helper.assertValueEqual(fixture.player.totalExperience, 0, "no prefix runs before the sequence is durable");
                fixture.retry();
                helper.assertValueEqual(fixture.player.totalExperience, 0, "retry cannot cross the failed save");
            }
            fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 15, "restored storage allows the original sequence to finish: " + fixture.continuation().actionDiagnostic());
            helper.assertTrue(fixture.continuation().completionReceipt(), "completion is acknowledged only after persistence");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void failedCursorSavePausesLaterEffectsWithoutRepeatingTheAppliedAction(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actions = fixture.actions("{\"type\":\"tracker\",\"flash_tracker\":true}," + REWARD);
            BlockedSceneFile[] blocked = {null}; int[] flashes = {0};
            try {
                var result = SceneContinuationService.run(fixture.context(), actions, Map.of(), () -> {
                    flashes[0]++;
                    try { blocked[0] = new BlockedSceneFile(fixture); }
                    catch (IOException exception) { throw new UncheckedIOException(exception); }
                }, "/recovery/cursor", null, QUEST, fixture.progress().questRunId());
                helper.assertTrue(result.suspended(), "failed cursor save suspends execution");
                helper.assertValueEqual(flashes[0], 1, "original tracker callback is preserved");
                helper.assertValueEqual(fixture.player.totalExperience, 3, "later reward and suffix remain unapplied");
                fixture.retry();
                helper.assertValueEqual(fixture.player.totalExperience, 3, "pending cursor save blocks all later effects");
            } finally { if (blocked[0] != null) blocked[0].close(); }
            fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 15, "saved cursor resumes after the applied tracker action: " + fixture.continuation().actionDiagnostic());
            helper.assertValueEqual(flashes[0], 1, "recovery never repeats the applied callback");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void crashDuringAnOrdinaryEffectRemainsExplicitlyAmbiguous(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actions = fixture.actions("{\"type\":\"tracker\",\"flash_tracker\":true}," + REWARD);
            BlockedSceneFile[] blocked = {null};
            try {
                SceneContinuationService.run(fixture.context(), actions, Map.of(), () -> {
                    try { blocked[0] = new BlockedSceneFile(fixture); }
                    catch (IOException exception) { throw new UncheckedIOException(exception); }
                }, "/recovery/crash", null, QUEST, fixture.progress().questRunId());
            } finally { if (blocked[0] != null) blocked[0].close(); }
            fixture.reloadScenes();
            helper.assertValueEqual(fixture.continuation().preparedRecoveryAction(), 1, "disk retains the pre-effect marker after the failed cursor write");
            fixture.retry();
            helper.assertFalse(fixture.continuation().completionReceipt(), "ambiguous effect cannot complete the sequence");
            helper.assertTrue(fixture.continuation().actionDiagnostic().contains("completion receipt"), "diagnostic requires inspecting the ambiguous effect");
            helper.assertValueEqual(fixture.player.totalExperience, 3, "neither prefix nor later effects replay after the crash");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void revokedAuthorityPausesEvenWhenNoConfirmationWasNeeded(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.blockRewards(); fixture.stage(fixture.actions(REWARD)); fixture.restoreRewards();
            var leader = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(PartyService.createParty(leader).success(), "fixture leader creates a party");
            try {
                var invitation = PartyService.sendInvitation(leader, fixture.player);
                helper.assertTrue(PartyService.acceptInvitation(fixture.player, invitation.invitationId()).success(), "controller joins the party");
                PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), false);
                fixture.retry();
                helper.assertValueEqual(fixture.player.totalExperience, 3, "revoked authority blocks remaining effects");
                helper.assertFalse(fixture.continuation().completionReceipt(), "revocation pauses without making the checkpoint terminal");
                PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), true);
                fixture.retry();
                helper.assertValueEqual(fixture.player.totalExperience, 15, "restored authority resumes the same saved sequence");
            } finally {
                PartyService.leaveParty(fixture.player);
                PartyService.leaveParty(leader);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void unloadedProviderAndDormantCampaignWaitWithoutReplayingThePrefix(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.base.mode("sandbox");
            var rejected = SceneContinuationService.run(fixture.context(), fixture.actions(REWARD), Map.of(), () -> { }, "/recovery/sandbox");
            helper.assertFalse(rejected.outcome().success(), "Sandbox Mode rejects campaign action sequences");
            helper.assertValueEqual(fixture.player.totalExperience, 0, "campaign gate also prevents ordinary prefix effects");
            helper.assertTrue(fixture.scenes().continuations().isEmpty(), "rejected campaign request records no pending work");
            fixture.base.mode("campaign");
            fixture.blockRewards(); fixture.stage(fixture.actions(REWARD)); fixture.restoreRewards();
            var saved = fixture.villager.saveWithoutId(new CompoundTag());
            fixture.villager.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 3, "unloaded provider retains the wait");
            fixture.villager = EntityType.VILLAGER.create(helper.getLevel()); fixture.villager.load(saved.copy());
            helper.assertTrue(helper.getLevel().addFreshEntity(fixture.villager), "original provider returns");
            var catalog = CampaignResources.current(fixture.base.server);
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(fixture.base.catalog, Map.of(), List.of()));
            fixture.retry(); helper.assertValueEqual(fixture.player.totalExperience, 3, "missing campaign content stays dormant");
            CampaignResources.installForTests(fixture.base.server, catalog); fixture.base.mode("sandbox"); fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 3, "Sandbox Mode cannot resume campaign effects");
            fixture.base.mode("campaign"); fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 15, "restored mode, content and provider release the original wait");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void requiredFailureStillStopsTheSequencePermanently(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actions = fixture.actions(REWARD.replace("loss_catch_up_personal", "missing_reward"));
            var result = fixture.stage(actions);
            helper.assertValueEqual(result.status(), ActionStatus.FAILED, "invalid authored reward is a required failure");
            fixture.reload(); fixture.retry(); fixture.stage(actions);
            helper.assertValueEqual(fixture.player.totalExperience, 3, "failure never repeats the prefix or advances to the suffix");
            helper.assertFalse(QuestStageActionService.ready(fixture.context(), fixture.progress()), "required failure remains a blocking checkpoint");
            helper.assertTrue(fixture.rewards().receipts().isEmpty(), "unknown reward creates no receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void malformedAndFutureRecoveryMetadataSurvivesForRepair(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.blockRewards(); fixture.stage(fixture.actions(REWARD)); fixture.restoreRewards();
            var original = fixture.continuation().save();
            for (String change : List.of("version", "cursor", "fingerprint", "operation", "next", "negative_next", "missing_next")) {
                var saved = original.copy(); var metadata = saved.getCompound("CampaignRecovery");
                if (change.equals("version")) metadata.putInt("DataVersion", 99);
                if (change.equals("cursor")) metadata.putInt("PreparedAction", 999);
                if (change.equals("fingerprint")) metadata.remove("QuestFingerprint");
                if (change.equals("operation")) metadata.putString("RewardOperation", "not a uuid");
                if (change.equals("next")) saved.putString("Next", "not an index");
                if (change.equals("negative_next")) saved.putInt("Next", -1);
                if (change.equals("missing_next")) saved.remove("Next");
                var loaded = SceneContinuation.load(saved);
                helper.assertFalse(loaded.recoveryIssue().isBlank(), "invalid recovery metadata is blocked: " + change);
                helper.assertValueEqual(loaded.save().get("CampaignRecovery"), metadata, "unreadable metadata is preserved for repair");
                helper.assertTrue(Objects.equals(loaded.save().get("Next"), saved.get("Next")), "unreadable cursor is preserved for repair");
            }
            var changed = original.copy(); changed.getCompound("CampaignRecovery").putString("QuestFingerprint", "different-content");
            helper.assertFalse(CampaignRewardRecovery.issue(fixture.base.server, SceneContinuation.load(changed)).isBlank(), "content mismatch blocks saved recovery");
            helper.assertTrue(CampaignRewardRecovery.issue(fixture.base.server, SceneContinuation.load(original)).isBlank(), "matching content preserves the saved wait");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void dialogueTurnInFinishesAfterRewardStorageRecovers(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.progress().setCurrentStage("catch_up_report"); fixture.blockRewards();
            fixture.base.select(fixture.context(), QUEST, "catch_up_report", "turn_in");
            helper.assertValueEqual(fixture.player.totalExperience, 0, "dialogue waits before delivering its reward");
            helper.assertValueEqual(fixture.progress().completionCount(), 0, "dialogue cannot turn in before required reward delivery");
            fixture.restoreRewards(); fixture.reload(); fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 5, "dialogue reward is delivered once");
            helper.assertValueEqual(fixture.progress().completionCount(), 1, "saved choice completes the quest after recovery");
            fixture.retry();
            helper.assertValueEqual(fixture.progress().completionCount(), 1, "duplicate resumption cannot repeat the turn-in");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void completedSequenceKeepsSynchronousTextAndTrackerFeedback(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            String reminder = "{\"type\":\"quest\",\"action\":\"remind\"}";
            var expected = VillagerActionRegistry.execute(fixture.context(), fixture.actions(reminder).get(1), Map.of());
            helper.assertFalse(expected.legacyResult().text().isBlank(), "fixture reminder has readable text");
            var actions = fixture.actions(reminder + "," + REWARD + ",{\"type\":\"tracker\",\"flash_tracker\":true}");
            int[] flashes = {0};
            var result = SceneContinuationService.run(fixture.context(), actions, Map.of("inherited", "kept"), () -> flashes[0]++,
                    "/recovery/synchronous", null, QUEST, fixture.progress().questRunId());
            helper.assertTrue(result.outcome().success(), "healthy sequence completes synchronously: " + result.outcome().message());
            helper.assertValueEqual(result.outcome().legacyResult().text(), expected.legacyResult().text(), "synchronous dialogue text survives persistence");
            helper.assertValueEqual(result.outcome().legacyResult().lineId(), expected.legacyResult().lineId(), "synchronous line identity is preserved");
            helper.assertValueEqual(result.outcome().legacyResult().replacements().get("inherited"), "kept", "inherited replacements are retained");
            helper.assertValueEqual(result.outcome().legacyResult().replacements().get("campaign_reward"), "loss_catch_up_personal", "reward output is retained");
            helper.assertValueEqual(flashes[0], 1, "provided tracker callback executes once");
            fixture.reload();
            SceneContinuationService.run(fixture.context(), actions, Map.of(), () -> flashes[0]++, "/recovery/synchronous", null, QUEST, fixture.progress().questRunId());
            helper.assertValueEqual(fixture.player.totalExperience, 15, "completed sequence cannot repeat rewards after reload");
            helper.assertValueEqual(flashes[0], 1, "completed sequence cannot repeat tracker feedback");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void nestedStageEntryResumesItsParentWithoutAScene(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.installQuest(root -> root.getAsJsonArray("stages").get(1).getAsJsonObject().add("on_enter",
                    JsonParser.parseString("[{\"type\":\"experience\",\"amount\":2}," + REWARD + ",{\"type\":\"experience\",\"amount\":11}]")));
            fixture.blockRewards();
            var actions = fixture.actions("{\"type\":\"set_variable\",\"scope\":\"quest\",\"key\":\"stage\",\"value\":\"catch_up_report\",\"required\":true}");
            var result = SceneContinuationService.run(fixture.context(), actions, Map.of(), () -> { }, "/recovery/nested", null, QUEST, fixture.progress().questRunId());
            helper.assertTrue(result.suspended(), "parent waits for the entry reward");
            var parent = fixture.scenes().continuation(result.continuationId()).orElseThrow();
            helper.assertTrue(parent.sceneInstanceId() == null && parent.dependencyContinuationId() != null, "nested wait references the child continuation without a scene");
            helper.assertValueEqual(fixture.player.totalExperience, 5, "parent and child prefixes each run once");
            fixture.restoreRewards(); fixture.reload(); fixture.retry(); fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 28, "child and parent suffixes resume in order");
            helper.assertTrue(fixture.scenes().continuations().stream().allMatch(SceneContinuation::completionReceipt), "both continuations acknowledge completion");
            fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 28, "nested recovery never repeats either sequence");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void changedQuestBundlePausesRecoveryUntilOriginalContentReturns(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.blockRewards(); fixture.stage(fixture.actions(REWARD)); fixture.restoreRewards();
            fixture.installQuest(root -> root.getAsJsonArray("stages").get(1).getAsJsonObject().add("on_enter",
                    JsonParser.parseString("[{\"type\":\"experience\",\"amount\":19}]")));
            fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 3, "changed content cannot run the saved sequence");
            helper.assertTrue(fixture.continuation().actionDiagnostic().contains("bundle changed"), "content mismatch explains the pause");
            fixture.installQuest(root -> { }); fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 15, "restoring original structure resumes the saved cursor");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void sceneActionReceiptResumesAnUndeliveredRewardAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var scene = fixture.scenes().start(fixture.base.scene, "reward_receipt",
                    new SceneOwner(SceneResource.OwnershipMode.PLAYER,
                            fixture.player.getUUID(), null, null, ""), fixture.progress().questRunId(), Set.of(fixture.player.getUUID()), Map.of(), 0L).instance();
            var step = fixture.base.scene.steps().get(fixture.base.scene.entryStep());
            var execution = new SceneExecutionContext(fixture.base.server, fixture.scenes(), scene,
                    fixture.base.scene, step, scene.currentRecord(step.type()), helper.getLevel().getGameTime(), true);
            var reward = fixture.actions(REWARD).get(1);
            fixture.blockRewards();
            var waiting = SceneReceiptGuard.applyActionOnce(execution, "reward",
                    SceneOperationReceipt.Kind.SCENE_TRANSITION, () -> CampaignRewardService.execute(fixture.context(), reward));
            helper.assertValueEqual(waiting.outcome().suspensionKind(), ActionResult.SuspensionKind.CAMPAIGN_REWARD, "scene waits on the original reward operation");
            helper.assertFalse(SceneActionSuspension.dependsOnScene(fixture.scenes(), waiting.outcome(), scene.id()), "reward wait is not a cyclic scene dependency");
            fixture.scenes().persistCampaignHandoff(fixture.base.server.overworld());
            fixture.restoreRewards(); fixture.reloadScenes();
            var restored = fixture.scenes().get(scene.id()).orElseThrow();
            var replay = new SceneExecutionContext(fixture.base.server, fixture.scenes(), restored,
                    fixture.base.scene, step, restored.currentRecord(step.type()), helper.getLevel().getGameTime(), false);
            fixture.atRetryTick(() -> {
                var result = SceneReceiptGuard.applyActionOnce(replay, "reward",
                        SceneOperationReceipt.Kind.SCENE_TRANSITION, () -> CampaignRewardService.execute(fixture.context(), reward));
                helper.assertTrue(result.outcome().success() && !result.ambiguous(), "saved reward wait can reconcile after reload");
                SceneReceiptGuard.applyActionOnce(replay, "reward",
                        SceneOperationReceipt.Kind.SCENE_TRANSITION, () -> {
                            helper.fail("completed scene receipt repeated the reward effect"); return ActionResult.skipped("unreachable", Set.of());
                        });
            });
            helper.assertValueEqual(fixture.player.totalExperience, 5, "scene receipt delivers the campaign reward exactly once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void recoveryWorkRotatesPastUnavailableProviders(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actions = VillagerActionDefinition.readList(QUEST, "retry_budget", JsonParser.parseString(
                    "{\"actions\":[{\"type\":\"experience\",\"amount\":7}]}").getAsJsonObject(), QUEST);
            for (int index = 0; index < 17; index++) {
                var continuation = fixture.scenes().suspendRewardContinuation(fixture.player.getUUID(), index < 16 ? UUID.randomUUID() : fixture.villager.getUUID(),
                        "/recovery/budget/" + index, actions, 0, Map.of(), QUEST, fixture.progress().questRunId());
                continuation.bindCampaign(fixture.base.campaignId);
                continuation.enableCampaignRecovery(CampaignRewardRecovery.fingerprint(fixture.base.server, QUEST));
            }
            fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 0, "first maintenance call observes the sixteen-attempt budget");
            fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 7, "later continuations are not starved by unavailable providers");
            fixture.retry();
            helper.assertValueEqual(fixture.player.totalExperience, 7, "rotating maintenance preserves the completion receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_recovery")
    public static void anotherVillagerCannotTakeOverPendingRecovery(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.blockRewards();
            var actions = fixture.actions(REWARD);
            fixture.stage(actions);
            var saved = fixture.continuation().save();
            var other = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
            other.setNoAi(true);
            try {
                var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player, other);
                String source = QUEST + "/" + fixture.progress().questRunId() + "/catch_up/reward_fixture";
                var rejected = SceneContinuationService.run(context, actions, Map.of(), () -> { }, source, null, QUEST, fixture.progress().questRunId());
                helper.assertValueEqual(rejected.outcome().status(), ActionStatus.UNAVAILABLE, "different provider cannot control the existing recovery");
                helper.assertValueEqual(fixture.continuation().save(), saved, "rejected request cannot overwrite the original cursor, ownership or result");
                helper.assertValueEqual(fixture.player.totalExperience, 3, "rejected provider cannot replay the prefix");
                fixture.restoreRewards(); fixture.retry();
                helper.assertValueEqual(fixture.player.totalExperience, 15, "original provider retains a playable recovery path");
            } finally { other.discard(); }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final CampaignCharacterTestFixture base;
        final CampaignRewardSavedData priorRewards;
        final ServerPlayer player;
        Villager villager;
        private CampaignRewardSavedData healthyRewards;
        private Path blockedRewards;

        Fixture(GameTestHelper helper) throws IOException {
            this.helper = helper;
            base = new CampaignCharacterTestFixture(helper, "losses", "loss_greeting", List.of("losses_catch_up"));
            priorRewards = CampaignRewardSavedData.get(helper.getLevel());
            rewards(CampaignRewardSavedData.load(new CompoundTag(), base.disk.root));
            var mentor = helper.spawn(EntityType.VILLAGER, 1, 1, 1); mentor.setNoAi(true); base.resolve(); mentor.discard();
            villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1); villager.setNoAi(true);
            helper.assertValueEqual(CampaignCharacterService.resolve(base.server, base.campaignId, "witness").entity(), villager, "fixture casts its provider");
            CampaignWorldEventService.reconcile(base.server);
            player = helper.makeMockServerPlayerInLevel(); player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            var content = CampaignResources.current(base.server).campaigns().get(base.campaignId);
            CampaignSavedData.get(helper.getLevel()).enterMilestone(content.definition(), player.getUUID(), "mentor_loss", helper.getLevel().getGameTime());
            var progress = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(player.getUUID(), QUEST);
            progress.start(villager.getUUID(), helper.getLevel().dimension(), villager.blockPosition(), helper.getLevel().getGameTime());
            progress.beginRun(player.getUUID(), QUEST, null); progress.bindCampaign(base.campaignId); progress.setCurrentStage("catch_up");
        }

        DialogueContext context() { return VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager); }
        VillagerQuestSavedData.QuestProgress progress() { return VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), QUEST); }
        CampaignRewardSavedData rewards() { return CampaignRewardSavedData.get(helper.getLevel()); }
        void rewards(CampaignRewardSavedData data) { base.server.overworld().getDataStorage().set(CampaignRewardSavedData.DATA_NAME, data); }
        SceneSavedData scenes() { return SceneSavedData.get(helper.getLevel()); }
        SceneContinuation continuation() { return scenes().continuations().stream().filter(value -> value.playerId().equals(player.getUUID())).findFirst().orElseThrow(); }
        Path scenePath() { return base.server.getWorldPath(LevelResource.ROOT).resolve("data/villagerretaliation_scenes.dat"); }
        List<VillagerActionDefinition> actions(String middle) { return VillagerActionDefinition.readList(QUEST, "recovery_fixture", JsonParser.parseString(
                "{\"actions\":[{\"type\":\"experience\",\"amount\":3}," + middle + ",{\"type\":\"experience\",\"amount\":7}]}").getAsJsonObject(), QUEST); }
        ActionResult stage(List<VillagerActionDefinition> actions) { return QuestStageActionService.run(context(), base.catalog.quest(QUEST).orElseThrow(), progress(), "reward_fixture", actions, Map.of(), () -> { }); }
        void installQuest(Consumer<JsonObject> update) throws IOException {
            var resources = new ArrayList<QuestBundleTransactions.RawResource>();
            for (String quest : List.of("losses", "losses_catch_up")) {
                for (String file : quest.equals("losses") ? List.of("quest.json", "locales/en_us.json", "scenes/loss_greeting.json") : List.of("quest.json", "locales/en_us.json")) {
                    String path = "quests/campaign_fixture/" + quest + "/" + file;
                    var raw = CampaignCharacterTestFixture.resource(path);
                    if (quest.equals("losses_catch_up") && file.equals("quest.json")) update.accept(raw);
                    resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture",
                            ResourceLocation.fromNamespaceAndPath("campaign_fixture", path), raw));
                }
            }
            var bundles = QuestBundleTransactions.compile(resources,
                    QuestBundleTransactions.CompatibilityRules.empty());
            helper.assertTrue(bundles.diagnostics().isEmpty(), "updated fixture bundle compiles: " + bundles.diagnostics());
            var quests = VillagerQuestResources.snapshotForCatalog(base.server, bundles);
            var catalog = new QuestContentCatalog(base.catalog.generation() + 1,
                    quests.compiledCatalog(), quests.dialogueCatalog(), quests.quests(), quests.objectiveEventQuestIds(), quests.factQuestIds(),
                    quests.memoryEventQuestIds(), quests.exclusiveGroupQuestIds(), quests.triggerEventQuestIds(), Map.of(base.sceneId, base.scene), Map.of(), List.of(),
                    bundles.bundles(), bundles.localization(), QuestRewardCatalog.fromBundles(bundles.bundles()));
            QuestContentCatalogs.installForTests(base.server, catalog, base.report);
            CampaignResources.installForTests(base.server, new CampaignContentCatalog(catalog, base.campaigns.campaigns(), List.of()));
        }
        void blockRewards() throws IOException {
            healthyRewards = rewards(); blockedRewards = Files.createTempFile(base.disk.root, "blocked-rewards-", ".tmp");
            rewards(CampaignRewardSavedData.load(healthyRewards.save(new CompoundTag(), helper.getLevel().registryAccess()), blockedRewards));
        }
        void restoreRewards() throws IOException {
            if (blockedRewards == null) return;
            rewards(healthyRewards); Files.delete(blockedRewards); blockedRewards = null;
        }
        void retry() { atRetryTick(() -> SceneContinuationService.maintain(base.server, scenes())); }
        void atRetryTick(Runnable work) {
            var level = base.server.overworld(); long before = level.getGameTime();
            try {
                ((ServerLevelData) level.getLevelData()).setGameTime(before + 100L - Math.floorMod(before, 100L));
                work.run();
            } finally { ((ServerLevelData) level.getLevelData()).setGameTime(before); }
        }
        void reloadScenes() throws IOException { base.server.overworld().getDataStorage().set("villagerretaliation_scenes",
                SceneSavedData.load(NbtIo.readCompressed(scenePath(), NbtAccounter.unlimitedHeap()).getCompound("data"), helper.getLevel().registryAccess())); }
        void reload() throws IOException {
            reloadScenes(); base.reload();
            rewards(CampaignRewardSavedData.load(base.disk.read(CampaignRewardSavedData.DATA_NAME), base.disk.root));
            var quests = VillagerQuestSavedData.get(helper.getLevel());
            base.server.overworld().getDataStorage().set("villagerretaliation_quests", VillagerQuestSavedData.load(quests.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess()));
        }
        @Override public void close() throws IOException {
            restoreRewards(); VillagerConversationService.endForPlayer(player, false); DialogueTreeService.clearRuntimeState(); villager.discard();
            Files.deleteIfExists(CampaignPlayerRewards.directory(player).resolve(player.getStringUUID() + ".dat"));
            Files.deleteIfExists(CampaignPlayerRewards.directory(player).resolve(player.getStringUUID() + ".dat_old"));
            rewards(priorRewards); Files.deleteIfExists(base.disk.root.resolve(CampaignRewardSavedData.DATA_NAME + ".dat")); base.close();
        }
    }

    private static final class BlockedSceneFile implements AutoCloseable {
        final Path path;
        final Path backup;
        BlockedSceneFile(Fixture fixture) throws IOException {
            path = fixture.scenePath(); backup = Files.createTempFile(fixture.base.disk.root, "scene-backup-", ".dat");
            Files.delete(backup); if (Files.exists(path)) Files.move(path, backup);
            Files.createDirectories(path); Files.writeString(path.resolve("blocked"), "storage failure fixture");
        }
        @Override public void close() throws IOException {
            Files.delete(path.resolve("blocked")); Files.delete(path);
            if (Files.exists(backup)) Files.move(backup, path);
        }
    }
}
