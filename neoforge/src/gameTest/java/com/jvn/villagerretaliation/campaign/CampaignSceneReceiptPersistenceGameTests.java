package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt.ReceiptState;
import com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignSceneReceiptPersistenceGameTests {
    private CampaignSceneReceiptPersistenceGameTests() { }

    @GameTest(template = "empty", batch = "campaign_scene_receipt_persistence")
    public static void sceneSavesReceiptsBeforeAndAfterRewardsAndResumesItsSuffix(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var result = fixture.apply(true, () -> {
                helper.assertValueEqual(fixture.diskReceipt().state(), ReceiptState.PREPARED, "receipt is on disk before the reward");
                return fixture.reward();
            });
            helper.assertTrue(result.outcome().success(), "reward applies");
            helper.assertValueEqual(fixture.diskReceipt().state(), ReceiptState.COMPLETED, "completion is on disk before returning");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.scene().completionResult(), SceneInstance.CompletionResult.SUCCESS, "untouched suffix resumes after reload");
            helper.assertValueEqual(fixture.player.totalExperience, 12, "saved prefix and later reward each apply once");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.player.totalExperience, 12, "reloading the completed batch cannot repeat rewards");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_receipt_persistence")
    public static void interruptedEffectsStayAmbiguousInMemoryAndAfterDiskReload(GameTestHelper helper) throws IOException {
        for (boolean resumed : new boolean[]{false, true}) try (var fixture = new Fixture(helper)) {
            if (resumed) fixture.completedDependency();
            try {
                fixture.apply(!resumed, () -> {
                    helper.assertValueEqual(fixture.diskReceipt().state(), ReceiptState.PREPARED, "retry replaces its old wait on disk before effects");
                    fixture.reward();
                    throw new InterruptedEffect();
                });
                helper.fail("fixture must interrupt the action before its completion is recorded");
            } catch (InterruptedEffect expected) { }
            helper.assertTrue(fixture.apply(true, fixture::reward).ambiguous(), "even a fresh step cannot replay an attempted receipt");
            fixture.reload();
            helper.assertTrue(fixture.apply(false, fixture::reward).ambiguous(), "disk reload preserves ambiguous preparation");
            helper.assertValueEqual(fixture.player.totalExperience, 5, "an interrupted effect never runs twice");
            fixture.run();
            helper.assertFalse(fixture.scene().diagnostic().isBlank(), "blocked scene provides a repair diagnostic");
            helper.assertValueEqual(fixture.player.totalExperience, 5, "later rewards stay blocked behind the unresolved receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_receipt_persistence")
    public static void failedPreparationPausesTheSceneBeforeAnyReward(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.block(); fixture.run();
            helper.assertTrue(fixture.scene().campaignPaused(), "storage failure pauses the scene");
            helper.assertTrue(fixture.scene().diagnostic().contains("storage"), "pause explains the storage repair");
            helper.assertValueEqual(fixture.player.totalExperience, 0, "unpersisted preparation cannot grant a reward");
            fixture.restore(); fixture.run();
            helper.assertValueEqual(fixture.scene().completionResult(), SceneInstance.CompletionResult.SUCCESS, "same-process storage repair resumes known unattempted work");
            helper.assertValueEqual(fixture.player.totalExperience, 12, "both rewards apply once after repair");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_receipt_persistence")
    public static void failedCompletionWriteHoldsTheSuffixAndRetainsTheAppliedResult(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            expectPersistenceWait(helper, () -> fixture.apply(true, () -> {
                var result = fixture.reward(); fixture.block(); return result;
            }));
            fixture.run();
            helper.assertTrue(fixture.scene().campaignPaused(), "failed completion write pauses later work");
            helper.assertValueEqual(fixture.player.totalExperience, 5, "suffix cannot run while completion remains unsaved");
            fixture.restore(); fixture.run();
            helper.assertValueEqual(fixture.scene().completionResult(), SceneInstance.CompletionResult.SUCCESS, "successful save releases the suffix");
            helper.assertValueEqual(fixture.player.totalExperience, 12, "repair saves the applied receipt without granting again");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.player.totalExperience, 12, "saved completion remains stable after reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_receipt_persistence")
    public static void resumedActionsRecoverStorageFailuresOnEitherSideOfTheEffect(GameTestHelper helper) throws IOException {
        for (boolean afterEffect : new boolean[]{false, true}) try (var fixture = new Fixture(helper)) {
            fixture.completedDependency();
            if (!afterEffect) fixture.block();
            expectPersistenceWait(helper, () -> fixture.apply(false, () -> {
                var result = fixture.reward();
                if (afterEffect) fixture.block();
                return result;
            }));
            helper.assertValueEqual(fixture.player.totalExperience, afterEffect ? 5 : 0, "failure occurs on the intended side of the resumed effect");
            fixture.restore(); fixture.run();
            helper.assertValueEqual(fixture.scene().completionResult(), SceneInstance.CompletionResult.SUCCESS, "resumed action and untouched suffix recover");
            helper.assertValueEqual(fixture.player.totalExperience, 12, "neither repaired write replays the resumed reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_receipt_persistence")
    public static void legacyPreparationDoesNotInventPermissionToReplayMissingReceipts(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.data().persistCampaignHandoff(helper.getLevel()); fixture.reload();
            var result = fixture.apply(false, fixture::reward);
            helper.assertTrue(result.ambiguous(), "legacy preparation without a receipt remains ambiguous");
            helper.assertValueEqual(fixture.player.totalExperience, 0, "migration cannot invent an unattempted reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_receipt_persistence")
    public static void requiredFailuresPersistAndPreventLaterRewardsAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.apply(true, () -> ActionResult.failed("fixture rejects the required reward", Set.of()));
            helper.assertValueEqual(fixture.diskReceipt().state(), ReceiptState.REJECTED, "required failure is saved immediately");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.scene().completionResult(), SceneInstance.CompletionResult.FAILURE, "required failure remains a scene failure");
            helper.assertValueEqual(fixture.player.totalExperience, 0, "no reward runs beyond the rejected action");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_receipt_persistence")
    public static void dialogueReceiptsAreDurableWithoutChangingOrdinarySceneBehavior(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            int[] deliveries = {0};
            var first = SceneReceiptGuard.applyOnce(fixture.context(true), "delivery", SceneOperationReceipt.Kind.DIALOGUE_DELIVERY,
                    () -> deliveries[0]++, "delivered");
            helper.assertValueEqual(first.status(), SceneReceiptGuard.Status.APPLIED, "campaign dialogue applies");
            fixture.reload();
            var duplicate = SceneReceiptGuard.applyOnce(fixture.context(false), "delivery", SceneOperationReceipt.Kind.DIALOGUE_DELIVERY,
                    () -> deliveries[0]++, "delivered");
            helper.assertValueEqual(duplicate.status(), SceneReceiptGuard.Status.ALREADY_APPLIED, "actual disk receipt prevents redelivery");
            helper.assertValueEqual(deliveries[0], 1, "campaign dialogue was delivered once");
            var ordinary = fixture.data().start(fixture.definition, "ordinary", fixture.scene().owner(), UUID.randomUUID(), Set.of(), Map.of(), 0L).instance();
            var step = fixture.definition.steps().get(ordinary.currentStep());
            var context = new SceneExecutionContext(fixture.base.base.server, fixture.data(), ordinary, fixture.definition,
                    step, ordinary.currentRecord(step.type()), 0L, true);
            fixture.block();
            var result = SceneReceiptGuard.applyActionOnce(context, "ordinary", SceneOperationReceipt.Kind.EXPERIENCE_GRANT, fixture::reward);
            helper.assertTrue(result.outcome().success(), "ordinary scenes retain their existing persistence behavior");
            helper.assertValueEqual(fixture.player.totalExperience, 5, "ordinary reward is unchanged");
        }
        helper.succeed();
    }

    private static void expectPersistenceWait(GameTestHelper helper, Runnable work) {
        try { work.run(); helper.fail("fixture must fail a scene receipt write"); }
        catch (SceneReceiptGuard.PersistenceWait expected) { }
    }

    private static final class InterruptedEffect extends RuntimeException { }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final CampaignConversationGameTests.Fixture base;
        final CompiledScene definition;
        final ServerPlayer player;
        final UUID sceneId;
        private Path backup;
        private long time;

        Fixture(GameTestHelper helper) throws IOException {
            this.helper = helper;
            base = new CampaignConversationGameTests.Fixture(helper, false, true, false);
            base.effects("""
                    [{"id":"scene","action":{"type":"start_scene","quest":"campaign_fixture:characters",
                      "scene_id":"campaign_fixture:character_greeting","operation_id":"receipt_scene","wait_for_result":true,"required":true}}]
                    """);
            definition = CampaignConversationSceneHandoverGameTests.scene(base, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                     "ownership":"player","entry_step":"reward","actors":[],"steps":[
                      {"id":"reward","type":"villagerretaliation:action_batch","data":{"actions":[
                        {"id":"reward","type":"experience","amount":5,"required":true},
                        {"id":"later","type":"experience","amount":7,"required":true}]},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """);
            player = base.player();
            CampaignConversationConfirmationGameTests.acceptQuest(base, player);
            var result = base.start(player);
            var receipt = result.session().receipts().values().iterator().next();
            helper.assertTrue(receipt.result().suspended(), "conversation waits for the receipt fixture scene");
            sceneId = receipt.result().suspensionId();
            time = helper.getLevel().getGameTime();
            SceneStepEngine.process(base.base.server, data(), scene(), definition, time++);
        }

        SceneSavedData data() { return SceneSavedData.get(helper.getLevel()); }
        SceneInstance scene() { return data().get(sceneId).orElseThrow(); }
        SceneExecutionContext context(boolean fresh) {
            var step = definition.steps().get("reward");
            return new SceneExecutionContext(base.base.server, data(), scene(), definition, step, scene().currentRecord(step.type()), time++, fresh);
        }
        SceneReceiptGuard.ActionApplyResult apply(boolean fresh, Supplier<ActionResult> effect) {
            return SceneReceiptGuard.applyActionOnce(context(fresh), "action/reward", SceneOperationReceipt.Kind.EXPERIENCE_GRANT, effect);
        }
        ActionResult reward() { player.giveExperiencePoints(5); return ActionResult.success(VillagerActionResult.EMPTY, Set.of()); }
        Path path() { return base.base.server.getWorldPath(LevelResource.ROOT).resolve("data/villagerretaliation_scenes.dat"); }
        SceneSavedData disk() {
            try { return SceneSavedData.load(NbtIo.readCompressed(path(), NbtAccounter.unlimitedHeap()).getCompound("data"), helper.getLevel().registryAccess()); }
            catch (IOException exception) { throw new AssertionError("cannot read the actual scene save", exception); }
        }
        SceneOperationReceipt diskReceipt() { return disk().get(sceneId).orElseThrow().receipt(sceneId + "/reward/action/reward"); }
        void reload() { base.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", disk()); SceneStepEngine.clearRuntimeState(); }
        void run() {
            for (int tick = 0; tick < 8 && !scene().state().terminal(); tick++) SceneStepEngine.process(base.base.server, data(), scene(), definition, time++);
        }
        void completedDependency() throws IOException {
            var child = data().start(definition, "receipt_dependency", scene().owner(), UUID.randomUUID(), Set.of(), Map.of(), time++).instance();
            apply(true, () -> new ActionResult(ActionStatus.SUSPENDED, "waiting for dependency", Set.of(), VillagerActionResult.EMPTY,
                    child.id(), ActionResult.SuspensionKind.SCENE, true));
            SceneTransitionService.complete(data(), child, time++);
            data().persistCampaignHandoff(helper.getLevel()); reload();
        }
        void block() {
            try {
                backup = Files.createTempFile(base.base.disk.root, "receipt-backup-", ".dat");
                Files.delete(backup); if (Files.exists(path())) Files.move(path(), backup);
                Files.createDirectories(path()); Files.writeString(path().resolve("blocked"), "scene receipt storage failure fixture");
            } catch (IOException exception) { throw new AssertionError("cannot block scene storage", exception); }
        }
        void restore() throws IOException {
            if (backup == null) return;
            Files.delete(path().resolve("blocked")); Files.delete(path());
            if (Files.exists(backup)) Files.move(backup, path());
            backup = null;
        }
        @Override public void close() throws IOException { restore(); SceneStepEngine.clearRuntimeState(); base.close(); }
    }
}
