package com.jvn.villagerretaliation.gametest;

import com.jvn.villagerretaliation.dialogue.EntityDialogueContext;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutors;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.SceneRuntime;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class SceneActionContractGameTests {
    private SceneActionContractGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_scene_action_wait")
    public static void actionBatchResumesAfterReloadWithoutRepeatingPrefix(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            var definition = scene("batch_wait", "action_batch", """
                    {"actions":[{"id":"prefix","type":"experience","amount":2},
                     {"id":"child","type":"start_scene","scene":"villagerretaliation:receipt_child",
                      "operation_id":"child","wait_for_result":true,"required":true},
                     {"id":"suffix","type":"experience","amount":3}]}
                    """);
            var execution = fixture.execution(definition, true);
            int before = fixture.context.player().totalExperience;
            var executor = SceneStepExecutors.get(execution.step().type()).orElseThrow();
            helper.assertValueEqual(executor.apply(execution).outcome(), SceneStepResult.Outcome.WAIT, "batch waits on child scene");
            helper.assertValueEqual(fixture.context.player().totalExperience, before + 2, "suffix waits for child");
            var waiting = execution.instance().receipts().get(execution.operationId("action/child"));
            helper.assertValueEqual(waiting.state(), SceneOperationReceipt.ReceiptState.WAITING, "suspension has a pending receipt");
            var restored = fixture.reload();
            var child = restored.get(waiting.actionResult().suspensionId()).orElseThrow();
            SceneTransitionService.complete(restored, child, 2L);
            var replay = fixture.execution(definition, restored.get(execution.instance().id()).orElseThrow(), false);
            helper.assertValueEqual(executor.reconcile(replay).outcome(), SceneStepResult.Outcome.APPLIED, "batch resumes after reload");
            helper.assertValueEqual(fixture.context.player().totalExperience, before + 5, "prefix and suffix each apply once");
            helper.assertValueEqual(executor.reconcile(replay).outcome(), SceneStepResult.Outcome.APPLIED, "recorded batch can reconcile again");
            helper.assertValueEqual(fixture.context.player().totalExperience, before + 5, "repeat reconciliation cannot duplicate rewards");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_action_failure")
    public static void failedChildSceneStopsRequiredBatchSuffix(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            var definition = scene("batch_failure", "action_batch", """
                    {"actions":[{"id":"child","type":"start_scene","scene":"villagerretaliation:receipt_child",
                      "operation_id":"failed_child","wait_for_result":true,"required":true},
                     {"id":"suffix","type":"experience","amount":7}]}
                    """);
            var execution = fixture.execution(definition, true);
            var executor = SceneStepExecutors.get(execution.step().type()).orElseThrow();
            int before = fixture.context.player().totalExperience;
            helper.assertValueEqual(executor.apply(execution).outcome(), SceneStepResult.Outcome.WAIT, "batch waits initially");
            var waiting = execution.instance().receipts().get(execution.operationId("action/child"));
            var child = fixture.data.get(waiting.actionResult().suspensionId()).orElseThrow();
            SceneTransitionService.fail(fixture.data, child, fixture.child, "fixture_failure", "fixture failure", 2L);
            helper.assertValueEqual(executor.apply(execution).outcome(), SceneStepResult.Outcome.FAIL, "failed child prevents step success");
            helper.assertValueEqual(waiting.state(), SceneOperationReceipt.ReceiptState.REJECTED, "failure is durable");
            helper.assertValueEqual(fixture.context.player().totalExperience, before, "failed child blocks suffix reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_transition_failure")
    public static void rejectedQuestTransitionCannotReceiveAnAppliedReceipt(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.installQuest(false);
            var definition = scene("transition_rejected", "quest_transition",
                    "{\"quest\":\"villagerretaliation:receipt_quest\",\"target\":\"stage\",\"target_stage\":\"absent\"}");
            var execution = fixture.execution(definition, true);
            var executor = SceneStepExecutors.get(execution.step().type()).orElseThrow();
            helper.assertValueEqual(executor.apply(execution).outcome(), SceneStepResult.Outcome.FAIL, "unavailable transition fails the step");
            helper.assertValueEqual(execution.instance().receipts().get(execution.operationId("quest_transition")).state(),
                    SceneOperationReceipt.ReceiptState.REJECTED, "rejected transition has no applied receipt");
            var restored = fixture.reload();
            var replay = fixture.execution(definition, restored.get(execution.instance().id()).orElseThrow(), false);
            helper.assertValueEqual(executor.reconcile(replay).outcome(), SceneStepResult.Outcome.FAIL, "rejection survives reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_transition_wait")
    public static void questTransitionWaitsForExitAndEntryAcrossReload(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.installQuest(true);
            var definition = scene("transition_wait", "quest_transition",
                    "{\"quest\":\"villagerretaliation:receipt_quest\",\"target\":\"stage\",\"target_stage\":\"finish\"}");
            var execution = fixture.execution(definition, true);
            var executor = SceneStepExecutors.get(execution.step().type()).orElseThrow();
            int before = fixture.context.player().totalExperience;
            helper.assertValueEqual(executor.apply(execution).outcome(), SceneStepResult.Outcome.WAIT, "transition waits for exit");
            var receipt = execution.instance().receipts().get(execution.operationId("quest_transition"));
            fixture.finishDependency(receipt.actionResult());
            fixture.context.player().tickCount = 0;
            VillagerQuestService.onPlayerTick(fixture.context.player());
            helper.assertValueEqual(fixture.progress().currentStage(), "offer", "quest polling cannot consume the scene's pending choice");
            helper.assertValueEqual(executor.apply(execution).outcome(), SceneStepResult.Outcome.WAIT, "transition also waits for new stage entry");
            helper.assertValueEqual(fixture.progress().currentStage(), "finish", "the stage change has committed");
            var restored = fixture.reload();
            var replay = fixture.execution(definition, restored.get(execution.instance().id()).orElseThrow(), false);
            var savedReceipt = replay.instance().receipts().get(replay.operationId("quest_transition"));
            fixture.finishDependency(savedReceipt.actionResult());
            helper.assertValueEqual(executor.reconcile(replay).outcome(), SceneStepResult.Outcome.APPLIED, "committed choice resumes using its original stage");
            helper.assertValueEqual(fixture.context.player().totalExperience, before + 10, "exit and entry rewards apply once");
            helper.assertValueEqual(savedReceipt.state(), SceneOperationReceipt.ReceiptState.COMPLETED, "completed transition receipt");
            helper.assertValueEqual(executor.reconcile(replay).outcome(), SceneStepResult.Outcome.APPLIED, "completed transition remains applied");
            helper.assertValueEqual(fixture.context.player().totalExperience, before + 10, "repeated transition cannot replay rewards");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_action_cycle")
    public static void sceneCannotWaitForItsOwnQuestContinuation(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            var execution = fixture.execution(fixture.child, true);
            var continuation = fixture.data.suspendContinuation(execution.instance(), fixture.context.player().getUUID(),
                    fixture.context.villager().getUUID(), "cycle", List.of(), 0, Map.of());
            var result = SceneReceiptGuard.applyActionOnce(execution, "cycle", SceneOperationReceipt.Kind.QUEST_TRANSITION,
                    () -> ActionResult.suspendedContinuation(continuation.id(), "nested wait", Set.of(), VillagerActionResult.EMPTY));
            helper.assertValueEqual(result.outcome().status(), ActionStatus.FAILED, "mixed scene and continuation cycle fails explicitly");
            helper.assertValueEqual(result.receipt().state(), SceneOperationReceipt.ReceiptState.REJECTED, "cyclic operation cannot remain waiting");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_action_retention")
    public static void cancellingAParentReleasesItsPendingChildReceipt(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            var parent = fixture.execution(scene("receipt_parent", "wait_ticks", "{\"ticks\":20}"), true);
            var child = fixture.execution(fixture.child, true).instance();
            var waiting = SceneReceiptGuard.applyActionOnce(parent, "child", SceneOperationReceipt.Kind.SCENE_TRANSITION,
                    () -> ActionResult.suspended(child.id(), Set.of()));
            SceneTransitionService.complete(fixture.data, child, 1L);
            child.cleanupStatus(SceneInstance.CleanupStatus.COMPLETE);
            long later = SceneSavedData.TERMINAL_RETENTION_TICKS + 2L;
            helper.assertValueEqual(fixture.data.compactTerminalHistory(later, 8), 0,
                    "child result remains available until the waiting parent consumes it");
            helper.assertValueEqual(com.jvn.villagerretaliation.scene.runtime.SceneRecoveryPolicy.decide(
                    com.jvn.villagerretaliation.scene.runtime.StepExecutionStatus.PREPARED,
                    com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode.RECEIPT_REQUIRED,
                    waiting.receipt().state(), false),
                    com.jvn.villagerretaliation.scene.runtime.SceneRecoveryPolicy.Decision.RECONCILE,
                    "a durable wait can reconcile after reload");
            SceneTransitionService.cancel(fixture.data, parent.instance(), parent.definition(), "cancelled", "cancelled", 2L);
            helper.assertValueEqual(waiting.receipt().state(), SceneOperationReceipt.ReceiptState.REJECTED,
                    "cancelled parent settles its pending wait");
            helper.assertValueEqual(fixture.data.compactTerminalHistory(later, 8), 1,
                    "cancelled parent no longer retains child history");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entity_scene_context")
    public static void registeredMobScenesUseActorConditionsAndDurableActionReceipts(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper);
                var actors = com.jvn.villagerretaliation.dialogue.NarrativeActors.register(ResourceLocation.parse("minecraft:cow"),
                        (level, player, actor) -> new EntityDialogueContext(level, player, actor, "en_us", ""))) {
            var cow = helper.spawn(EntityType.COW, 1, 1, 1);
            cow.setNoAi(true);
            var definition = scene("mob_condition", "wait_condition",
                    "{\"conditions\":[{\"type\":\"health\",\"subject\":\"speaker\",\"max\":0.5}]}");
            var execution = fixture.execution(definition, true);
            var binding = SceneActorBinding.entity("provider", VillagerRetaliation.id("entity"), cow.getUUID(),
                    ResourceLocation.parse("minecraft:cow"), cow.level().dimension().location(), cow.blockPosition(), "Scout", true);
            execution.instance().replaceBinding("provider", binding);
            var executor = SceneStepExecutors.get(execution.step().type()).orElseThrow();
            helper.assertValueEqual(executor.apply(execution).outcome(), SceneStepResult.Outcome.WAIT, "healthy mob does not satisfy its injury condition");
            cow.setHealth(cow.getMaxHealth() / 4);
            helper.assertValueEqual(executor.apply(execution).outcome(), SceneStepResult.Outcome.APPLIED, "scene conditions read the mob's health");
            definition = scene("mob_actions", "action_batch", "{\"actions\":[{\"id\":\"gift\",\"type\":\"experience\",\"amount\":7,\"required\":true}]}");
            execution = fixture.execution(definition, true);
            execution.instance().replaceBinding("provider", binding);
            executor = SceneStepExecutors.get(execution.step().type()).orElseThrow();
            int before = fixture.context.player().totalExperience;
            helper.assertValueEqual(executor.apply(execution).outcome(), SceneStepResult.Outcome.APPLIED, "mob scene executes registered common actions");
            helper.assertValueEqual(fixture.context.player().totalExperience, before + 7, "mob scene grants its reward");
            var restored = fixture.reload();
            var replay = fixture.execution(definition, restored.get(execution.instance().id()).orElseThrow(), false);
            helper.assertValueEqual(executor.reconcile(replay).outcome(), SceneStepResult.Outcome.APPLIED, "mob scene reconciles its saved receipt");
            helper.assertValueEqual(fixture.context.player().totalExperience, before + 7, "mob scene never repeats its settled reward");
            cow.discard();
        }
        helper.succeed();
    }

    private static CompiledScene scene(String id, String type, String data) {
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:%s","entry_step":"action","actors":[],"steps":[
                  {"id":"action","type":"villagerretaliation:%s","data":%s,"next":"done"},
                  {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """.formatted(id, type, data)).getAsJsonObject();
        var result = SceneCompiler.compile(SceneParser.parse(VillagerRetaliation.id(id), raw).resource());
        if (!result.valid()) throw new IllegalStateException(result.diagnostics().toString());
        return result.scene();
    }

    private static final class Fixture implements AutoCloseable {
        private final DialogueContext context;
        private final SceneSavedData original;
        private final CompiledScene child = scene("receipt_child", "wait_ticks", "{\"ticks\":20}");
        private SceneSavedData data = new SceneSavedData();

        private Fixture(GameTestHelper helper) {
            var player = helper.makeMockServerPlayerInLevel();
            var villager = EntityType.VILLAGER.create(helper.getLevel());
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
            helper.assertTrue(helper.getLevel().addFreshEntity(villager), "fixture provider spawns");
            context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
            original = SceneSavedData.get(context.level());
            context.level().getServer().overworld().getDataStorage().set("villagerretaliation_scenes", data);
            SceneResources.installTestScenes(context.level().getServer(), List.of(child));
            SceneRuntime.initialize(context.level().getServer());
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        }

        private SceneExecutionContext execution(CompiledScene definition, boolean fresh) {
            var binding = SceneActorBinding.entity("provider", VillagerRetaliation.id("villager"), context.villager().getUUID(),
                    VillagerRetaliation.id("villager"), context.level().dimension().location(), context.villager().blockPosition(), "Provider", true);
            var instance = data.start(definition, definition.id().getPath(),
                    new SceneOwner(SceneResource.OwnershipMode.PLAYER, context.player().getUUID(), null, null, ""),
                    null, Set.of(context.player().getUUID()), Map.of("provider", binding), 0L).instance();
            return execution(definition, instance, fresh);
        }

        private SceneExecutionContext execution(CompiledScene definition, SceneInstance instance, boolean fresh) {
            var step = definition.steps().get(definition.entryStep());
            return new SceneExecutionContext(context.level().getServer(), data, instance, definition, step,
                    instance.currentRecord(step.type()), context.level().getGameTime(), fresh);
        }

        private SceneSavedData reload() {
            data = SceneSavedData.load(data.save(new CompoundTag(), context.level().registryAccess()), context.level().registryAccess());
            context.level().getServer().overworld().getDataStorage().set("villagerretaliation_scenes", data);
            return data;
        }

        private void finishDependency(ActionResult result) {
            var continuation = data.continuation(result.suspensionId()).orElseThrow();
            SceneTransitionService.complete(data, data.get(continuation.sceneInstanceId()).orElseThrow(), context.level().getGameTime());
            SceneContinuationService.maintain(context.level().getServer(), data);
        }

        private VillagerQuestSavedData.QuestProgress progress() {
            return VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), VillagerRetaliation.id("receipt_quest"));
        }

        private void installQuest(boolean waits) {
            String exit = waits ? """
                    [{"type":"experience","amount":2},
                     {"type":"start_scene","scene":"villagerretaliation:receipt_child","operation_id":"exit","wait_for_result":true,"required":true},
                     {"type":"experience","amount":3}]
                    """ : "[]";
            String entry = waits ? """
                    [{"type":"start_scene","scene":"villagerretaliation:receipt_child","operation_id":"entry","wait_for_result":true,"required":true},
                     {"type":"experience","amount":5}]
                    """ : "[]";
            var raw = JsonParser.parseString("""
                    {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:receipt_quest",
                     "provider":{"type":"villagerretaliation:villager"},"entry_stage":"offer","stages":[
                      {"id":"offer","objectives":[{"id":"wait","type":"choice"}],"complete_when":["wait"],"next":"finish","on_exit":%s},
                      {"id":"finish","objectives":[],"on_enter":%s}]}
                    """.formatted(exit, entry)).getAsJsonObject();
            var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/receipt_quest.json"), raw).orElseThrow();
            var compiled = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
            VillagerQuestResources.installCompiledTestCatalog(context.level().getServer(), List.of(compiled));
            if (!VillagerQuestService.debugStartQuest(context.player(), context.villager(), compiled.id(), true).started()) {
                throw new IllegalStateException("fixture quest did not start");
            }
        }

        @Override
        public void close() {
            context.level().getServer().overworld().getDataStorage().set("villagerretaliation_scenes", original);
            VillagerQuestService.setClientEffectsSuppressedForTests(context.player(), false);
            VillagerQuestResources.clearCache();
            SceneResources.clearCache();
        }
    }
}
