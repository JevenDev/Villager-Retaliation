package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.runtime.QuestStageActionService;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.SceneRuntime;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class QuestActionOrderingGameTests {
    private QuestActionOrderingGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_stage_failure")
    public static void failureAfterEntryChangesStageStillBlocksCaller(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "stage_failure", """
                [{"type":"experience","amount":2},
                 {"type":"set_variable","scope":"quest","key":"stage","value":"finish","required":true},
                 {"type":"start_scene","scene":"villagerretaliation:missing_ordering_scene","operation_id":"missing","required":true},
                 {"type":"experience","amount":3}]
                """, """
                [{"type":"experience","amount":5}]
                """, "[]")) {
            var result = fixture.startFromActions();
            helper.assertValueEqual(result.outcome().status(), ActionStatus.UNAVAILABLE, "entry failure reaches the caller after changing stages");
            helper.assertValueEqual(fixture.progress().currentStage(), "finish", "completed stage change is retained");
            helper.assertValueEqual(fixture.experience(), 18, "only the prefix and successful effects before the failure run");
            fixture.reload();
            helper.assertValueEqual(QuestStageActionService.pending(fixture.context, fixture.progress()).status(),
                    ActionStatus.UNAVAILABLE, "failure survives reload");
            helper.assertFalse(VillagerQuestService.syncQuestStage(fixture.context, fixture.quest.id(), "offer"),
                    "another stage transition cannot discard the failure");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_stage_nested_wait")
    public static void entryContinuationSurvivesAnotherStageChange(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "stage_nested_wait", """
                [{"type":"experience","amount":2},
                 {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"first","wait_for_result":true,"required":true},
                 {"type":"set_variable","scope":"quest","key":"stage","value":"finish","required":true},
                 {"type":"experience","amount":3},
                 {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"last","wait_for_result":true,"required":true},
                 {"type":"experience","amount":5}]
                """, """
                [{"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"middle","wait_for_result":true,"required":true},
                 {"type":"experience","amount":7}]
                """, "[]")) {
            var result = fixture.startFromActions();
            helper.assertTrue(result.suspended(), "caller waits for the first scene");
            fixture.completeScene("first");
            helper.assertValueEqual(fixture.progress().currentStage(), "finish", "resumed entry changes stage");
            helper.assertValueEqual(fixture.experience(), 13, "old and new entry suffixes still wait");
            fixture.completeScene("middle");
            helper.assertValueEqual(fixture.experience(), 23, "both entries resume without replaying prefixes");
            helper.assertFalse(fixture.continuation(result.continuationId()).completionReceipt(), "caller still waits for the original entry suffix");
            fixture.reload();
            fixture.completeScene("last");
            helper.assertValueEqual(fixture.continuation(result.continuationId()).actionStatus(), ActionStatus.SUCCESS, "caller completes after all entry work");
            helper.assertValueEqual(fixture.experience(), 45, "entry and caller rewards each apply once");
            helper.assertTrue(QuestStageActionService.ready(fixture.context, fixture.progress()), "all carried checkpoints settle");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_stage_trigger_failure")
    public static void stageChangeTriggerFailurePreventsEntry(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "stage_trigger_failure", "[]", """
                [{"type":"experience","amount":7}]
                """, """
                [{"id":"before_entry","event":"stage_changed","stages":["finish"],"actions":[
                  {"type":"start_scene","scene":"villagerretaliation:missing_ordering_scene","operation_id":"missing","required":true},
                  {"type":"experience","amount":3}]}]
                """)) {
            fixture.start();
            var result = fixture.chooseFromActions();
            helper.assertValueEqual(result.outcome().status(), ActionStatus.UNAVAILABLE, "required trigger failure reaches caller");
            helper.assertValueEqual(fixture.experience(), 11, "trigger, entry, and caller suffixes are blocked");
            fixture.reload();
            helper.assertValueEqual(fixture.progress().pendingStageEntry(), "finish", "blocked entry is saved");
            fixture.chooseFromActions();
            helper.assertValueEqual(fixture.experience(), 22, "new caller prefix runs but cannot bypass the saved failure");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_stage_trigger_wait")
    public static void stageChangeTriggerWaitsBeforeEntryAndCallerAcrossReload(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "stage_trigger_wait", "[]", """
                [{"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"entry","wait_for_result":true,"required":true},
                 {"type":"experience","amount":5}]
                """, """
                [{"id":"before_entry","event":"stage_changed","stages":["finish"],"actions":[
                  {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"trigger","wait_for_result":true,"required":true},
                  {"type":"experience","amount":3}]}]
                """)) {
            fixture.start();
            var result = fixture.chooseFromActions();
            helper.assertTrue(result.suspended(), "caller waits for the stage trigger");
            helper.assertValueEqual(fixture.experience(), 11, "entry waits for the trigger");
            fixture.reload();
            helper.assertValueEqual(fixture.progress().pendingStageEntry(), "finish", "entry wait survives reload");
            fixture.completeScene("trigger");
            helper.assertValueEqual(fixture.experience(), 14, "entry and caller suffixes wait for the entry scene");
            helper.assertFalse(fixture.continuation(result.continuationId()).completionReceipt(), "caller follows the entry wait after trigger completion");
            fixture.completeScene("entry");
            helper.assertValueEqual(fixture.continuation(result.continuationId()).actionStatus(), ActionStatus.SUCCESS, "transition completes");
            helper.assertValueEqual(fixture.experience(), 36, "all ordered effects apply once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_repeat_trigger")
    public static void repeatableTriggersDoNotReplayWhileWaiting(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "repeat_trigger", "[]", "[]", """
                [{"id":"repeat","event":"player_tick","repeatable":true,"cooldown_ticks":0,"actions":[
                  {"type":"experience","amount":2},
                  {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"repeat","wait_for_result":true,"required":true},
                  {"type":"experience","amount":3}]}]
                """)) {
            fixture.start();
            fixture.tickTrigger();
            helper.assertValueEqual(fixture.experience(), 2, "first event waits after its prefix");
            fixture.tickTrigger();
            helper.assertValueEqual(fixture.experience(), 2, "another event cannot replay a suspended prefix");
            fixture.completeScene("repeat");
            helper.assertValueEqual(fixture.experience(), 5, "first event finishes once");
            fixture.tickTrigger();
            fixture.maintain();
            helper.assertValueEqual(fixture.experience(), 10, "repeatable trigger can run again after completion");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_trigger_redirect")
    public static void resumedTriggerCanRedirectWithoutEnteringTheOldStage(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "trigger_redirect", "[]", """
                [{"type":"experience","amount":1000}]
                """, """
                [{"id":"redirect","event":"stage_changed","stages":["finish"],"actions":[
                  {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"redirect","wait_for_result":true,"required":true},
                  {"type":"set_variable","scope":"quest","key":"stage","value":"offer","required":true},
                  {"type":"experience","amount":3}]}]
                """)) {
            fixture.start();
            var result = fixture.chooseFromActions();
            fixture.completeScene("redirect");
            helper.assertValueEqual(fixture.progress().currentStage(), "offer", "trigger redirects to its alternate stage");
            helper.assertValueEqual(fixture.continuation(result.continuationId()).actionStatus(), ActionStatus.SUCCESS, "redirect settles the caller");
            helper.assertValueEqual(fixture.experience(), 31, "superseded entry never runs");
            helper.assertValueEqual(fixture.progress().pendingStageEntry(), "", "superseded entry is cleared");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_terminal_trigger")
    public static void terminalTriggerWaitAndFailureReachTheCaller(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "terminal_trigger", "[]", "[]", """
                [{"id":"on_failure","event":"failed","actions":[
                  {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"failure","wait_for_result":true,"required":true},
                  {"type":"experience","amount":3},
                  {"type":"start_scene","scene":"villagerretaliation:missing_ordering_scene","operation_id":"missing","required":true},
                  {"type":"experience","amount":5}]}]
                """)) {
            fixture.start();
            var result = fixture.run("""
                    {"type":"quest","quest":"villagerretaliation:terminal_trigger","action":"fail","required":true}
                    """);
            helper.assertTrue(result.suspended(), "terminal action waits for its trigger");
            helper.assertValueEqual(fixture.progress().state(), VillagerQuestSavedData.QuestState.FAILED, "quest outcome is already applied");
            helper.assertValueEqual(fixture.experience(), 11, "caller suffix waits for terminal trigger");
            fixture.reload();
            fixture.completeScene("failure");
            helper.assertValueEqual(fixture.continuation(result.continuationId()).actionStatus(), ActionStatus.UNAVAILABLE, "required terminal trigger failure reaches caller");
            helper.assertValueEqual(fixture.experience(), 14, "neither trigger nor caller runs effects after failure");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_terminal_transition")
    public static void terminalChoiceResumesWithoutRepeatingTheOutcome(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "terminal_transition", "[]", "[]", """
                [{"id":"on_abandon","event":"abandoned","actions":[
                  {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"abandon","wait_for_result":true,"required":true},
                  {"type":"experience","amount":3}]}]
                """)) {
            fixture.start();
            var result = fixture.run("""
                    {"type":"quest_transition","quest":"villagerretaliation:terminal_transition","from_stage":"offer",
                     "target":"abandon","scene_path":"offer","response_id":"decline","required":true}
                    """);
            helper.assertTrue(result.suspended(), "terminal choice waits for abandonment trigger");
            helper.assertTrue(fixture.progress().hasChoice("offer", "decline", "offer"), "original choice is recorded");
            fixture.reload();
            fixture.completeScene("abandon");
            helper.assertValueEqual(fixture.continuation(result.continuationId()).actionStatus(), ActionStatus.SUCCESS, "recorded terminal choice resumes successfully");
            helper.assertValueEqual(fixture.experience(), 31, "terminal effects and caller suffix run once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_terminal_action")
    public static void terminalActionResumesWithoutRepeatingItsTrigger(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "terminal_action", "[]", "[]", """
                [{"id":"on_failure","event":"failed","actions":[
                  {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"failure","wait_for_result":true,"required":true},
                  {"type":"experience","amount":3}]}]
                """)) {
            fixture.start();
            var result = fixture.run("""
                    {"type":"quest","quest":"villagerretaliation:terminal_action","action":"fail","required":true}
                    """);
            helper.assertTrue(result.suspended(), "terminal action waits for its hook");
            fixture.completeScene("failure");
            helper.assertValueEqual(fixture.continuation(result.continuationId()).actionStatus(), ActionStatus.SUCCESS, "applied terminal outcome resumes successfully");
            helper.assertValueEqual(fixture.experience(), 31, "terminal hook and caller suffix run once");
            fixture.maintain();
            helper.assertValueEqual(fixture.experience(), 31, "settled terminal action cannot repeat effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_nested_stage_trigger")
    public static void nestedStageTriggerDoesNotWaitOnItsCaller(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "nested_stage_trigger", """
                [{"type":"set_variable","scope":"quest","key":"stage","value":"finish","required":true},
                 {"type":"experience","amount":2}]
                """, """
                [{"type":"experience","amount":5}]
                """, """
                [{"id":"nested","event":"stage_changed","stages":["finish"],"actions":[
                  {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"nested","wait_for_result":true,"required":true},
                  {"type":"set_variable","scope":"quest","key":"stage","value":"finish","required":true},
                  {"type":"experience","amount":3}]}]
                """)) {
            var result = fixture.startFromActions();
            helper.assertTrue(result.suspended(), "entry and caller wait for the nested trigger");
            fixture.reload();
            fixture.completeScene("nested");
            helper.assertValueEqual(fixture.continuation(result.continuationId()).actionStatus(), ActionStatus.SUCCESS, "nested trigger can finish without waiting on its parent");
            helper.assertValueEqual(fixture.experience(), 38, "nested trigger, entries, and caller finish in order");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_forced_entry_failure")
    public static void forcedDialogueEntryFailureStopsCallerEffects(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "forced_entry_failure", "[]", "[]", "[]")) {
            fixture.start();
            fixture.installForcedTree("""
                    [{"type":"experience","amount":2},
                     {"type":"start_scene","scene":"villagerretaliation:missing_ordering_scene","operation_id":"missing","required":true},
                     {"type":"experience","amount":3}]
                    """);
            var result = fixture.forceFromActions();
            helper.assertValueEqual(result.outcome().status(), ActionStatus.UNAVAILABLE, "forced entry failure reaches caller");
            helper.assertValueEqual(fixture.experience(), 13, "required entry failure blocks both suffixes");
            helper.assertTrue(DialogueTreeService.activeOptions(fixture.context).isEmpty(), "failed entry cannot expose responses");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_forced_entry_wait")
    public static void forcedDialogueWaitResumesPresentationAndCallerAfterReload(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "forced_entry_wait", "[]", "[]", "[]")) {
            fixture.start();
            fixture.installForcedTree("""
                    [{"type":"experience","amount":2},
                     {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"first","wait_for_result":true,"required":true},
                     {"type":"experience","amount":3},
                     {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"second","wait_for_result":true,"required":true},
                     {"type":"experience","amount":5}]
                    """);
            var result = fixture.forceFromActions();
            helper.assertTrue(result.suspended(), "forced entry suspends its caller");
            helper.assertValueEqual(fixture.experience(), 13, "caller and tree prefixes apply once");
            fixture.completeScene("first");
            helper.assertValueEqual(fixture.experience(), 16, "caller waits through the second entry scene");
            helper.assertFalse(fixture.continuation(result.continuationId()).completionReceipt(), "entry work remains pending");
            fixture.reload();
            var resumes = SceneSavedData.get(fixture.context.level()).continuations().stream()
                    .filter(value -> value.playerId().equals(fixture.context.player().getUUID()) && value.dialogueResume() != null).toList();
            helper.assertFalse(resumes.isEmpty(), "dialogue continuation is saved");
            helper.assertTrue(resumes.stream().allMatch(value -> value.dialogueResume().forced()), "forced presentation survives reload");
            fixture.completeScene("second");
            helper.assertValueEqual(fixture.experience(), 38, "entry and caller suffixes apply once");
            helper.assertValueEqual(fixture.continuation(result.continuationId()).actionStatus(), ActionStatus.SUCCESS, "caller follows the full entry result");
            helper.assertTrue(VillagerConversationService.isForced(fixture.context.player(), fixture.context.villager()), "resumed entry opens a forced conversation");
            helper.assertFalse(DialogueTreeService.activeOptions(fixture.context).orElseThrow().isEmpty(), "resumed entry exposes its responses");
            fixture.forceFromActions();
            helper.assertValueEqual(fixture.experience(), 38, "a repeated request cannot replay the caller prefix or entry");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_forced_entry_wait_failure")
    public static void forcedDialogueFailureAfterSceneStopsCallerEffects(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "forced_entry_wait_failure", "[]", "[]", "[]")) {
            fixture.start();
            fixture.installForcedTree("""
                    [{"type":"experience","amount":2},
                     {"type":"start_scene","scene":"villagerretaliation:ordering_wait","operation_id":"before_failure","wait_for_result":true,"required":true},
                     {"type":"experience","amount":3},
                     {"type":"start_scene","scene":"villagerretaliation:missing_ordering_scene","operation_id":"missing","required":true},
                     {"type":"experience","amount":5}]
                    """);
            var result = fixture.forceFromActions();
            helper.assertTrue(result.suspended(), "forced entry initially waits");
            fixture.completeScene("before_failure");
            helper.assertValueEqual(fixture.continuation(result.continuationId()).actionStatus(), ActionStatus.UNAVAILABLE, "entry failure after the scene reaches caller");
            helper.assertValueEqual(fixture.experience(), 16, "scene completion cannot release effects after entry failure");
            helper.assertTrue(DialogueTreeService.activeOptions(fixture.context).isEmpty(), "failed entry stays closed");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final DialogueContext context;
        private final CompiledQuest quest;
        private final SceneSavedData originalScenes;
        private final VillagerQuestSavedData originalQuests;
        private final int initialExperience;

        private Fixture(GameTestHelper helper, String id, String entry, String finishEntry, String events) {
            var player = helper.makeMockServerPlayerInLevel();
            var villager = EntityType.VILLAGER.create(helper.getLevel());
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
            helper.assertTrue(helper.getLevel().addFreshEntity(villager), "fixture villager spawns");
            this.context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
            this.initialExperience = player.totalExperience;
            this.originalScenes = SceneSavedData.get(context.level());
            this.originalQuests = VillagerQuestSavedData.get(context.level());
            var raw = JsonParser.parseString("""
                    {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:%s",
                     "provider":{"type":"villagerretaliation:villager"},"entry_stage":"offer","events":%s,"stages":[
                      {"id":"offer","objectives":[{"id":"wait","type":"choice"}],"complete_when":["wait"],"next":"finish","on_enter":%s},
                      {"id":"finish","objectives":[],"on_enter":%s}]}
                    """.formatted(id, events, entry, finishEntry)).getAsJsonObject();
            var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/" + id + ".json"), raw).orElseThrow();
            this.quest = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
            var scene = SceneCompiler.compile(SceneParser.parse(VillagerRetaliation.id("ordering_scene"), JsonParser.parseString("""
                    {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:ordering_wait",
                     "entry_step":"wait","actors":[],"steps":[
                      {"id":"wait","type":"villagerretaliation:wait_ticks","data":{"ticks":20},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """).getAsJsonObject()).resource());
            helper.assertTrue(scene.valid(), "fixture scene compiles");
            SceneResources.installTestScenes(context.level().getServer(), List.of(scene.scene()));
            SceneRuntime.initialize(context.level().getServer());
            VillagerQuestResources.installCompiledTestCatalog(context.level().getServer(), List.of(quest), QuestDialogueCatalog.empty());
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        }

        private void start() {
            if (!VillagerQuestService.debugStartQuest(context.player(), context.villager(), quest.id(), true).started()) {
                throw new IllegalStateException("fixture quest did not start");
            }
        }

        private void installForcedTree(String actions) {
            context.player().moveTo(context.villager().getX(), context.villager().getY(), context.villager().getZ() + 1.0D, 0, 0);
            var treeId = VillagerRetaliation.id("forced_ordering/" + quest.id().getPath());
            var raw = JsonParser.parseString("""
                    {"id":"%s","entries":[{"id":"start","label":"Start","start":"opening"}],
                     "nodes":{"opening":{"lines":["Ready"],"actions":%s,
                      "responses":[{"id":"done","label":"Continue","end":true}]}}}
                    """.formatted(treeId, actions)).getAsJsonObject();
            var tree = DialogueTreeResources.readGeneratedTree(treeId, raw, quest.id());
            var binding = new QuestDialogueCatalog.Binding(quest.id(), "offer", "scene:forced", "forced", treeId, "start", quest.source());
            var catalog = new QuestDialogueCatalog(Map.of(treeId, tree), Map.of(binding.key(), binding), Map.of(treeId, quest.source()));
            VillagerQuestResources.installCompiledTestCatalog(context.level().getServer(), List.of(quest), catalog);
            DialogueTreeResources.clearCache();
        }

        private SceneContinuationService.RunResult forceFromActions() {
            return run("""
                    {"type":"forced_dialogue","quest":"%s","forced_dialogue":"%s#forced","required":true}
                    """.formatted(quest.id(), quest.id()));
        }

        private SceneContinuationService.RunResult startFromActions() {
            return run("{\"type\":\"quest\",\"quest\":\"" + quest.id() + "\",\"action\":\"start\",\"required\":true}");
        }

        private SceneContinuationService.RunResult chooseFromActions() {
            return run("""
                    {"type":"quest_transition","quest":"%s","from_stage":"offer","target_stage":"finish",
                     "scene_path":"offer","response_id":"accept","required":true}
                    """.formatted(quest.id()));
        }

        private SceneContinuationService.RunResult run(String action) {
            var raw = JsonParser.parseString("""
                    {"actions":[{"type":"experience","amount":11},%s,{"type":"experience","amount":17}]}
                    """.formatted(action)).getAsJsonObject();
            return SceneContinuationService.run(context,
                    VillagerActionDefinition.readList(quest.id(), "ordering", raw), Map.of(), null, "ordering/" + quest.id());
        }

        private void tickTrigger() {
            VillagerQuestService.debugFireTrigger(context.player(), quest.id(), QuestDefinition.TriggerEvent.PLAYER_TICK);
        }

        private VillagerQuestSavedData.QuestProgress progress() {
            return VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id());
        }

        private int experience() {
            return context.player().totalExperience - initialExperience;
        }

        private SceneContinuation continuation(UUID id) {
            return SceneSavedData.get(context.level()).continuation(id).orElseThrow();
        }

        private void completeScene(String operation) {
            var data = SceneSavedData.get(context.level());
            var scene = data.active().stream().filter(value -> value.operationId().equals(operation)
                    && value.participants().contains(context.player().getUUID())).findFirst().orElseThrow();
            SceneTransitionService.complete(data, scene, context.level().getGameTime());
            maintain();
        }

        private void maintain() {
            var data = SceneSavedData.get(context.level());
            for (int i = 0; i < 8; i++) SceneContinuationService.maintain(context.level().getServer(), data);
        }

        private void reload() {
            var storage = context.level().getServer().overworld().getDataStorage();
            var registries = context.level().registryAccess();
            storage.set("villagerretaliation_scenes", SceneSavedData.load(SceneSavedData.get(context.level()).save(new CompoundTag(), registries), registries));
            storage.set("villagerretaliation_quests", VillagerQuestSavedData.load(VillagerQuestSavedData.get(context.level()).save(new CompoundTag(), registries), registries));
        }

        @Override
        public void close() {
            var storage = context.level().getServer().overworld().getDataStorage();
            storage.set("villagerretaliation_scenes", originalScenes);
            storage.set("villagerretaliation_quests", originalQuests);
            originalScenes.continuations().stream().filter(value -> value.playerId().equals(context.player().getUUID())).forEach(SceneContinuation::complete);
            VillagerQuestService.setClientEffectsSuppressedForTests(context.player(), false);
            VillagerQuestResources.clearCache();
            SceneResources.clearCache();
            DialogueTreeResources.clearCache();
            DialogueTreeService.clearRuntimeState();
            VillagerConversationService.endForPlayer(context.player(), true);
        }
    }
}
