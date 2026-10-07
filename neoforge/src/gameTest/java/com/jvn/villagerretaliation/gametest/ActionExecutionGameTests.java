package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.runtime.QuestActionSequenceRunner;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.runtime.QuestStageActionService;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.SceneRuntime;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class ActionExecutionGameTests {
    private ActionExecutionGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_action_contract")
    public static void requiredUnavailableActionsStopQuestEffects(GameTestHelper helper) {
        DialogueContext context = context(helper);
        List<VillagerActionDefinition> actions = actions("""
                {"actions":[
                  {"type":"start_scene","scene":"villagerretaliation:missing_contract_scene",
                   "operation_id":"missing","required":true},
                  {"type":"experience","amount":7}]}
                """);
        int before = context.player().totalExperience;
        var result = QuestActionSequenceRunner.runResult(context, actions, Map.of(), null, "required_missing");
        helper.assertValueEqual(result.outcome().status(), ActionStatus.UNAVAILABLE, "missing scene result");
        helper.assertValueEqual(context.player().totalExperience, before, "required failure must block the reward");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_action_contract")
    public static void optionalUnavailableActionsAllowFollowingEffects(GameTestHelper helper) {
        DialogueContext context = context(helper);
        var actions = actions("""
                {"actions":[
                  {"type":"start_scene","scene":"villagerretaliation:missing_contract_scene","operation_id":"optional"},
                  {"type":"experience","amount":7}]}
                """);
        int before = context.player().totalExperience;
        var result = QuestActionSequenceRunner.runResult(context, actions, Map.of(), null, "optional_missing");
        helper.assertTrue(result.outcome().success(), "optional failure must allow sequence completion");
        helper.assertValueEqual(context.player().totalExperience, before + 7, "optional failure reward");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_action_contract")
    public static void alreadySatisfiedFactsAllowRequiredSequences(GameTestHelper helper) {
        DialogueContext context = context(helper);
        var actions = actions("""
                {"actions":[
                  {"type":"set_variable","scope":"player","key":"contract_fact","value":"ready","required":true},
                  {"type":"experience","amount":7}]}
                """);
        var first = VillagerActionRegistry.execute(context, actions.getFirst(), Map.of());
        var repeat = VillagerActionRegistry.execute(context, actions.getFirst(), Map.of());
        helper.assertTrue(first.success(), "initial fact write");
        helper.assertValueEqual(repeat.status(), ActionStatus.ALREADY_SATISFIED, "repeated fact write");
        int before = context.player().totalExperience;
        var result = QuestActionSequenceRunner.runResult(context, actions, Map.of(), null, "satisfied_fact");
        helper.assertTrue(result.outcome().success(), "required satisfied fact must permit completion");
        helper.assertValueEqual(context.player().totalExperience, before + 7, "reward after satisfied fact");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_action_contract")
    public static void rejectedQuestTransitionDoesNotCountAsApplied(GameTestHelper helper) {
        DialogueContext context = context(helper);
        var actions = actions("""
                {"actions":[
                  {"type":"quest_transition","quest":"villagerretaliation:missing_contract_quest",
                   "from_stage":"start","target_stage":"finish","scene_path":"start","response_id":"choose",
                   "required":true},
                  {"type":"experience","amount":7}]}
                """);
        helper.assertValueEqual(actions.size(), 2, "transition fixture parses");
        int before = context.player().totalExperience;
        var result = QuestActionSequenceRunner.runResult(context, actions, Map.of(), null, "rejected_transition");
        helper.assertFalse(result.outcome().success(), "an unavailable transition cannot satisfy a required action");
        helper.assertValueEqual(context.player().totalExperience, before, "transition rejection blocks reward");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_dialogue_failure")
    public static void requiredResponseFailureDoesNotEnterNextNode(GameTestHelper helper) {
        DialogueContext context = context(helper);
        ResourceLocation treeId = VillagerRetaliation.id("action_contract_tree");
        JsonObject treeJson = json("""
                {"id":"villagerretaliation:action_contract_tree",
                 "entries":[{"id":"start","label":"Start","start":"opening"}],
                 "nodes":{"opening":{"lines":["Opening"],"responses":[
                   {"id":"choose","label":"Choose","next":"reward","actions":[
                     {"type":"start_scene","scene":"villagerretaliation:missing_contract_scene",
                      "operation_id":"blocked_response","required":true}]}]},
                  "reward":{"lines":["Reward"],"actions":[{"type":"experience","amount":7}],"end":true}}}
                """);
        var tree = DialogueTreeResources.readGeneratedTree(treeId, treeJson, treeId);
        DialogueTreeResources.installTestTrees(context.level().getServer(), context.locale(), List.of(tree), Set.of());
        try {
            DialogueTreeService.startEntry(context, treeId, "start").orElseThrow();
            var option = DialogueTreeService.activeOptions(context).orElseThrow().getFirst();
            int before = context.player().totalExperience;
            DialogueTreeService.handleDialogueOption(context, option).orElseThrow();
            helper.assertValueEqual(context.player().totalExperience, before, "failed response must not enter reward node");
            helper.assertTrue(DialogueTreeService.activeOptions(context).isEmpty(), "failed response invalidates its session");
        } finally {
            DialogueTreeResources.clearCache();
            DialogueTreeService.clearRuntimeState();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scene_suspension")
    public static void sceneSuspendsRemainingActionsAndResumesOnceAfterReload(GameTestHelper helper) {
        DialogueContext context = context(helper);
        CompiledScene definition = scene();
        SceneResources.installTestScenes(context.level().getServer(), List.of(definition));
        SceneRuntime.initialize(context.level().getServer());
        try {
            var actions = actions("""
                    {"actions":[
                      {"type":"start_scene","scene":"villagerretaliation:contract_wait","operation_id":"wait_once",
                       "wait_for_result":true,"required":true},
                      {"type":"experience","amount":7}]}
                    """);
            int before = context.player().totalExperience;
            var result = QuestActionSequenceRunner.runResult(context, actions, Map.of(), null, "wait_reload");
            helper.assertTrue(result.suspended(), "waiting scene suspends the sequence");
            helper.assertValueEqual(context.player().totalExperience, before, "reward waits for scene completion");
            SceneSavedData original = SceneSavedData.get(context.level());
            var continuation = original.continuations().stream()
                    .filter(value -> value.id().equals(result.continuationId())).findFirst().orElseThrow();
            continuation.dialogueResume(new SceneContinuation.DialogueResume(
                    VillagerRetaliation.id("missing_resume_tree"), "after", true, "Saved line"));
            var instance = original.get(continuation.sceneInstanceId()).orElseThrow();
            SceneTransitionService.complete(original, instance, context.level().getGameTime());
            SceneSavedData loaded = SceneSavedData.load(original.save(new CompoundTag(), context.level().registryAccess()),
                    context.level().registryAccess());
            continuation.complete();
            var saved = loaded.continuations().stream().filter(value -> value.id().equals(result.continuationId()))
                    .findFirst().orElseThrow();
            helper.assertValueEqual(saved.dialogueResume().nodeId(), "after", "dialogue destination survives reload");
            helper.assertTrue(saved.questId() == null, "an unbound continuation must stay unbound after reload");
            helper.assertValueEqual(saved.actions(), actions, "saved actions preserve absent optional references");
            SceneContinuationService.maintain(context.level().getServer(), loaded);
            SceneContinuationService.maintain(context.level().getServer(), loaded);
            helper.assertValueEqual(context.player().totalExperience, before + 7, "resumed reward applies once");
            helper.assertValueEqual(saved.actionStatus(), ActionStatus.SUCCESS, "saved sequence outcome");
            var duplicate = loaded.suspendContinuation(loaded.get(saved.sceneInstanceId()).orElseThrow(),
                    context.player().getUUID(), context.villager().getUUID(), saved.sourcePointer(), actions, 1, Map.of());
            helper.assertTrue(duplicate.completionReceipt(), "completed continuations cannot be recreated by duplicate requests");
        } finally {
            SceneResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_action_receipts")
    public static void rejectedSceneActionReceiptSurvivesReload(GameTestHelper helper) {
        DialogueContext dialogue = context(helper);
        CompiledScene definition = scene();
        SceneSavedData data = new SceneSavedData();
        var instance = data.start(definition, "rejected_receipt",
                new SceneOwner(SceneResource.OwnershipMode.PLAYER, dialogue.player().getUUID(), null, null, ""),
                null, Set.of(dialogue.player().getUUID()), Map.of(), 0L).instance();
        var step = definition.steps().get(definition.entryStep());
        var execution = new SceneExecutionContext(dialogue.level().getServer(), data, instance, definition, step,
                instance.currentRecord(step.type()), 1L, true);
        var action = actions("""
                {"actions":[{"type":"start_scene","scene":"villagerretaliation:missing_contract_scene",
                 "operation_id":"receipt_reject","required":true}]}
                """).getFirst();
        AtomicInteger attempts = new AtomicInteger();
        var first = SceneReceiptGuard.applyActionOnce(execution, "test", SceneOperationReceipt.Kind.SCENE_TRANSITION,
                () -> { attempts.incrementAndGet(); return VillagerActionRegistry.execute(dialogue, action, Map.of()); });
        helper.assertValueEqual(first.receipt().state(), SceneOperationReceipt.ReceiptState.REJECTED,
                "unavailable action cannot have an applied receipt");
        SceneSavedData loaded = SceneSavedData.load(data.save(new CompoundTag(), dialogue.level().registryAccess()),
                dialogue.level().registryAccess());
        var restored = loaded.get(instance.id()).orElseThrow();
        helper.assertTrue(restored.owningQuestId() == null, "an unbound scene must retain its operation key after reload");
        var replay = new SceneExecutionContext(dialogue.level().getServer(), loaded, restored, definition, step,
                restored.currentRecord(step.type()), 2L, false);
        var second = SceneReceiptGuard.applyActionOnce(replay, "test", SceneOperationReceipt.Kind.SCENE_TRANSITION,
                () -> { attempts.incrementAndGet(); return VillagerActionRegistry.execute(dialogue, action, Map.of()); });
        helper.assertValueEqual(second.outcome().status(), ActionStatus.UNAVAILABLE, "rejected result survives reload");
        helper.assertValueEqual(attempts.get(), 1, "reloading must not retry a recorded action attempt");
        CompoundTag missingResult = second.receipt().save();
        missingResult.remove("ActionStatus");
        helper.assertFalse(SceneOperationReceipt.load(missingResult).actionResult().success(),
                "a rejected receipt without a result must not be treated as applied");
        SceneTransitionService.complete(loaded, restored, 3L);
        restored.cleanupStatus(com.jvn.villagerretaliation.scene.runtime.SceneInstance.CleanupStatus.COMPLETE);
        long maintenanceTime = 3L + SceneSavedData.TERMINAL_RETENTION_TICKS;
        helper.assertValueEqual(loaded.compactTerminalHistory(maintenanceTime, 8), 1,
                "known rejected attempts do not retain settled scenes indefinitely");
        var compacted = SceneSavedData.load(loaded.save(new CompoundTag(), dialogue.level().registryAccess()),
                dialogue.level().registryAccess());
        var duplicate = compacted.start(definition, "rejected_receipt", restored.owner(), null,
                Set.of(dialogue.player().getUUID()), Map.of(), maintenanceTime + 1);
        helper.assertFalse(duplicate.created(), "compaction retains operation identity and prevents replay");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_transition_suspension")
    public static void waitingSceneDelaysRequiredTransitionAndPreservesItsFailure(GameTestHelper helper) {
        DialogueContext context = context(helper);
        SceneResources.installTestScenes(context.level().getServer(), List.of(scene()));
        SceneRuntime.initialize(context.level().getServer());
        try {
            var actions = actions("""
                    {"actions":[
                      {"type":"experience","amount":2},
                      {"type":"start_scene","scene":"villagerretaliation:contract_wait","operation_id":"wait_transition",
                       "wait_for_result":true,"required":true},
                      {"type":"quest_transition","quest":"villagerretaliation:missing_contract_quest",
                       "from_stage":"start","target_stage":"finish","scene_path":"start","response_id":"choose",
                       "required":true},
                      {"type":"experience","amount":7}]}
                    """);
            int before = context.player().totalExperience;
            var result = QuestActionSequenceRunner.runResult(context, actions, Map.of(), null, "wait_transition");
            helper.assertTrue(result.suspended(), "waiting scene suspends before transition");
            helper.assertFalse(result.outcome().legacyResult().replacements().containsKey("quest_transition_status"),
                    "the following transition must not run while the scene is pending");
            QuestActionSequenceRunner.runResult(context, actions, Map.of(), null, "wait_transition");
            helper.assertValueEqual(context.player().totalExperience, before + 2, "duplicate request must not replay earlier effects");
            SceneSavedData data = SceneSavedData.get(context.level());
            var continuation = data.continuations().stream().filter(value -> value.id().equals(result.continuationId()))
                    .findFirst().orElseThrow();
            SceneTransitionService.complete(data, data.get(continuation.sceneInstanceId()).orElseThrow(), context.level().getGameTime());
            SceneContinuationService.maintain(context.level().getServer(), data);
            helper.assertValueEqual(continuation.actionStatus(), ActionStatus.UNAVAILABLE, "resumed transition rejection is durable");
            helper.assertValueEqual(context.player().totalExperience, before + 2, "rejected resumed transition blocks following reward");
            QuestActionSequenceRunner.runResult(context, actions, Map.of(), null, "wait_transition");
            helper.assertValueEqual(context.player().totalExperience, before + 2, "completed failure cannot replay earlier effects");
        } finally {
            SceneResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_dialogue_suspension")
    public static void dialogueNodeResumesWithoutReplayingEntryActions(GameTestHelper helper) {
        DialogueContext context = context(helper);
        ResourceLocation treeId = VillagerRetaliation.id("suspended_contract_tree");
        var tree = DialogueTreeResources.readGeneratedTree(treeId, json("""
                {"id":"villagerretaliation:suspended_contract_tree",
                 "entries":[{"id":"start","label":"Start","start":"opening"}],
                 "nodes":{"opening":{"lines":["Ready"],"actions":[
                   {"type":"experience","amount":2},
                   {"type":"start_scene","scene":"villagerretaliation:contract_wait","operation_id":"wait_dialogue",
                    "wait_for_result":true,"required":true},
                   {"type":"experience","amount":3}],
                   "responses":[{"id":"choose","label":"Choose","next":"reward"}]},
                  "reward":{"lines":["Reward"],"actions":[{"type":"experience","amount":7}],"end":true}}}
                """), treeId);
        SceneResources.installTestScenes(context.level().getServer(), List.of(scene()));
        SceneRuntime.initialize(context.level().getServer());
        DialogueTreeResources.installTestTrees(context.level().getServer(), context.locale(), List.of(tree), Set.of());
        try {
            int before = context.player().totalExperience;
            DialogueTreeService.startEntry(context, treeId, "start").orElseThrow();
            helper.assertValueEqual(context.player().totalExperience, before + 2, "only pre-scene actions run initially");
            helper.assertTrue(DialogueTreeService.activeOptions(context).orElseThrow().isEmpty(), "pending node has no responses");
            helper.assertTrue(DialogueTreeService.startEntry(context, treeId, "start").isEmpty(), "pending entry cannot be replayed");
            SceneSavedData data = SceneSavedData.get(context.level());
            var continuation = data.continuations().stream().filter(value -> value.dialogueResume() != null
                    && treeId.equals(value.dialogueResume().treeId())).findFirst().orElseThrow();
            var restored = SceneContinuation.load(continuation.save());
            helper.assertFalse(restored.dialogueResume().runActions(), "saved node continuation must bypass entry actions");
            CompoundTag noDialogue = continuation.save();
            noDialogue.remove("DialogueResume");
            helper.assertTrue(SceneContinuation.load(noDialogue).dialogueResume() == null,
                    "absent dialogue state must not become an empty tree identity");
            SceneTransitionService.complete(data, data.get(continuation.sceneInstanceId()).orElseThrow(), context.level().getGameTime());
            SceneContinuationService.maintain(context.level().getServer(), data);
            helper.assertValueEqual(context.player().totalExperience, before + 5, "node resume applies remaining actions once");
            var option = DialogueTreeService.activeOptions(context).orElseThrow().getFirst();
            DialogueTreeService.handleDialogueOption(context, option).orElseThrow();
            helper.assertValueEqual(context.player().totalExperience, before + 12, "resumed dialogue remains playable");
            SceneContinuationService.maintain(context.level().getServer(), data);
            helper.assertValueEqual(context.player().totalExperience, before + 12, "completed dialogue continuation never replays");
        } finally {
            SceneResources.clearCache();
            DialogueTreeResources.clearCache();
            DialogueTreeService.clearRuntimeState();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_compiler_contract")
    public static void lifecycleCompilationPreservesRequiredSceneContracts(GameTestHelper helper) {
        var raw = json("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:contract_hook",
                 "provider":{"type":"villagerretaliation:villager"},
                 "lifecycle":{"on_stage_enter":{"actions":[
                   {"type":"start_scene","scene":"villagerretaliation:missing_contract_scene","operation_id":"hook",
                    "wait_for_result":true,"required":true}]}},
                 "entry_stage":"offer","stages":[{"id":"offer","objectives":[{"id":"wait","type":"choice"}],
                   "complete_when":["wait"],"entry_actions":[{"type":"experience","amount":7}]}]}
                """);
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/contract_hook.json"), raw).orElseThrow();
        var resource = QuestV2Parser.parse(envelope).orElseThrow();
        var compiled = QuestV2Compiler.compile(resource, envelope).orElseThrow();
        var actions = compiled.asQuestDefinition().stages().get("offer").entryActions();
        helper.assertValueEqual(actions.size(), 2, "global and local actions retain authored order");
        var scene = actions.getFirst();
        helper.assertTrue(scene.required() && scene.waitForScene(), "lifecycle compilation preserves scene control flags");
        helper.assertValueEqual(scene.sceneOperationId(), "hook", "lifecycle compilation preserves operation identity");
        DialogueContext context = context(helper);
        int before = context.player().totalExperience;
        var result = QuestActionSequenceRunner.runResult(context, actions, Map.of(), null, "compiled_hook");
        helper.assertValueEqual(result.outcome().status(), ActionStatus.UNAVAILABLE, "compiled required action remains a barrier");
        helper.assertValueEqual(context.player().totalExperience, before, "compiled hook failure blocks local effects");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_stage_failure")
    public static void failedEntryBlocksStageChangesAndRewardsAcrossReload(GameTestHelper helper) {
        DialogueContext context = context(helper);
        var raw = json("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:contract_stage_failure",
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"offer",
                 "rewards":{"experience":25},"stages":[
                  {"id":"offer","objectives":[{"id":"wait","type":"choice"}],"complete_when":["wait"],"next":"finish",
                   "on_enter":[{"type":"experience","amount":2},
                     {"type":"start_scene","scene":"villagerretaliation:missing_contract_scene",
                      "operation_id":"failed_entry","required":true},
                     {"type":"experience","amount":7}]},
                  {"id":"finish","objectives":[]}]}
                """);
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/contract_stage_failure.json"), raw).orElseThrow();
        var compiled = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
        VillagerQuestResources.installCompiledTestCatalog(context.level().getServer(), List.of(compiled), QuestDialogueCatalog.empty());
        VillagerQuestService.setClientEffectsSuppressedForTests(context.player(), true);
        try {
            int before = context.player().totalExperience;
            helper.assertTrue(VillagerQuestService.debugStartQuest(context.player(), context.villager(), compiled.id(), true).started(),
                    "fixture quest starts");
            helper.assertValueEqual(context.player().totalExperience, before + 2, "entry failure blocks subsequent effects");
            var data = VillagerQuestSavedData.get(context.level());
            var progress = data.get(context.player().getUUID(), compiled.id());
            progress.markObjectiveComplete("offer.wait");
            helper.assertFalse(VillagerQuestService.syncQuestStage(context, compiled.id(), "finish"), "entry failure prevents stage changes");
            var outcome = VillagerQuestService.performAction(context, compiled.id(), VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
            helper.assertValueEqual(outcome.status(), "unavailable", "entry failure blocks completion rewards");
            var loaded = VillagerQuestSavedData.load(data.save(new CompoundTag(), context.level().registryAccess()), context.level().registryAccess());
            var restored = loaded.get(context.player().getUUID(), compiled.id());
            helper.assertFalse(QuestStageActionService.ready(context, restored), "entry failure survives reload");
            helper.assertValueEqual(restored.stageActions().get("entry").status(), ActionStatus.UNAVAILABLE, "saved entry result");
            helper.assertValueEqual(context.player().totalExperience, before + 2, "blocked paths cannot replay earlier entry effects");
            helper.assertTrue(VillagerQuestService.debugObjectives(context.player(), compiled.id()).lines().stream()
                    .anyMatch(line -> line.contains("stage_action=entry") && line.contains("UNAVAILABLE")), "operator can inspect the blocker");
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(context.player(), false);
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_stage_suspension")
    public static void suspendedExitRetainsStageAndResumesWithoutRepeatingEffects(GameTestHelper helper) {
        DialogueContext context = context(helper);
        var raw = json("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:contract_stage_wait",
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"offer","stages":[
                  {"id":"offer","objectives":[{"id":"wait","type":"choice"}],"complete_when":["wait"],"next":"finish",
                   "on_exit":[{"type":"experience","amount":2},
                     {"type":"start_scene","scene":"villagerretaliation:contract_wait","operation_id":"wait_exit",
                      "wait_for_result":true,"required":true},
                     {"type":"experience","amount":3}]},
                  {"id":"finish","objectives":[]}]}
                """);
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/contract_stage_wait.json"), raw).orElseThrow();
        var compiled = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
        SceneResources.installTestScenes(context.level().getServer(), List.of(scene()));
        SceneRuntime.initialize(context.level().getServer());
        VillagerQuestResources.installCompiledTestCatalog(context.level().getServer(), List.of(compiled), QuestDialogueCatalog.empty());
        VillagerQuestService.setClientEffectsSuppressedForTests(context.player(), true);
        try {
            helper.assertTrue(VillagerQuestService.debugStartQuest(context.player(), context.villager(), compiled.id(), true).started(),
                    "fixture quest starts");
            int before = context.player().totalExperience;
            helper.assertFalse(VillagerQuestService.syncQuestStage(context, compiled.id(), "finish"), "exit suspension delays transition");
            var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), compiled.id());
            helper.assertValueEqual(progress.currentStage(), "offer", "previous stage remains active while exiting");
            helper.assertValueEqual(progress.pendingStageTransition(), "finish", "requested destination is saved");
            helper.assertFalse(VillagerQuestService.syncQuestStage(context, compiled.id(), "finish"), "duplicate transition stays pending");
            helper.assertValueEqual(context.player().totalExperience, before + 2, "duplicate transition does not replay exit effects");
            var data = SceneSavedData.get(context.level());
            var continuation = data.continuations().stream().filter(value -> value.id().equals(progress.stageActions().get("exit").continuationId()))
                    .findFirst().orElseThrow();
            SceneTransitionService.complete(data, data.get(continuation.sceneInstanceId()).orElseThrow(), context.level().getGameTime());
            SceneContinuationService.maintain(context.level().getServer(), data);
            helper.assertValueEqual(progress.stageActions().get("exit").status(), ActionStatus.SUCCESS,
                    "the quest retains its result before terminal scene history can be compacted");
            helper.assertTrue(VillagerQuestService.syncQuestStage(context, compiled.id(), "finish"), "completed exit allows transition");
            helper.assertValueEqual(progress.currentStage(), "finish", "requested stage is reached");
            helper.assertTrue(progress.pendingStageTransition().isBlank(), "completed stage clears pending destination");
            helper.assertValueEqual(context.player().totalExperience, before + 5, "exit suffix executes once");
            var repeatableActions = actions("{\"actions\":[{\"type\":\"experience\",\"amount\":2}]}");
            QuestStageActionService.run(context, compiled.asQuestDefinition(), progress, "branch/repeatable",
                    repeatableActions, Map.of(), null);
            QuestStageActionService.run(context, compiled.asQuestDefinition(), progress, "branch/repeatable",
                    repeatableActions, Map.of(), null);
            helper.assertValueEqual(context.player().totalExperience, before + 9,
                    "successful ordinary branch actions retain their existing repeat behavior");
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(context.player(), false);
            VillagerQuestResources.clearCache();
            SceneResources.clearCache();
        }
        helper.succeed();
    }

    private static DialogueContext context(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
        helper.assertTrue(helper.getLevel().addFreshEntity(villager), "fixture villager spawns");
        return VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
    }

    private static List<VillagerActionDefinition> actions(String raw) {
        return VillagerActionDefinition.readList(VillagerRetaliation.id("action_contract"), "fixture", json(raw));
    }

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static CompiledScene scene() {
        var raw = json("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:contract_wait",
                 "entry_step":"wait","actors":[],"steps":[
                  {"id":"wait","type":"villagerretaliation:wait_ticks","data":{"ticks":20},"next":"done"},
                  {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """);
        var compiled = SceneCompiler.compile(SceneParser.parse(VillagerRetaliation.id("action_contract_scene"), raw).resource());
        if (!compiled.valid()) throw new IllegalStateException(compiled.diagnostics().toString());
        return compiled.scene();
    }
}
