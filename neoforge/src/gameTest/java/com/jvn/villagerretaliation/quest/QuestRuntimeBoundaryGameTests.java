package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionSequenceRunner;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.content.QuestContentAccess;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatcher;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import com.jvn.villagerretaliation.quest.runtime.QuestObservationDispatcher;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressState;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressionService;
import com.jvn.villagerretaliation.quest.runtime.QuestRewardDeliveryService;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_boundary")
@PrefixGameTestTemplate(false)
public final class QuestRuntimeBoundaryGameTests {
    private QuestRuntimeBoundaryGameTests() { }

    @GameTest(template = "empty", batch = "narrative_runtime_boundaries")
    public static void compiledQuestRunsWithDetachedStateAndInjectedEffects(GameTestHelper helper) {
        CompiledQuest compiled = compiled();
        var definition = compiled.asQuestDefinition();
        var progress = new QuestProgressState();
        UUID playerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        var binding = new QuestProviderBinding(VillagerRetaliation.id("villager"), providerId, "Issuer", null,
                1, helper.getLevel().dimension(), null, "", Map.of(), false);
        var started = QuestLifecycleService.start(definition.id(), progress, binding, null, 10L, playerId, null);
        helper.assertValueEqual(started.type(), QuestLifecycleService.LifecycleEventType.STARTED, "detached lifecycle starts");
        QuestLifecycleService.initializeStage(definition, progress, 10L);
        UUID runId = progress.questRunId();
        List<Integer> effects = new ArrayList<>();
        var result = ActionSequenceRunner.run(definition.stages().get("offer").entryActions(), 0, Map.of(), null,
                null, null, (action, replacements) -> {
                    effects.add(action.amount());
                    return ActionResult.success(VillagerActionResult.success(), Set.of());
                });
        helper.assertTrue(result.outcome().success(), "compiled actions use the existing runner without a dialogue assembly");
        helper.assertValueEqual(effects, List.of(3), "the injected domain executor receives the authored effect");
        String objective = definition.stages().get("offer").objectives().getFirst();
        helper.assertFalse(QuestProgressionService.advanceSaved(definition, progress, 11L,
                value -> progress.objectiveComplete(value.id()), conditions -> true), "unfinished objectives hold the stage");
        progress.markObjectiveComplete(objective);
        helper.assertTrue(QuestProgressionService.advanceSaved(definition, progress, 12L,
                value -> progress.objectiveComplete(value.id()), conditions -> true), "the same runtime advances detached state");
        helper.assertValueEqual(progress.currentStage(), "ready", "the authored transition is preserved");
        helper.assertValueEqual(progress.pendingStageEntry(), "ready", "world-dependent entry effects remain pending");
        helper.assertValueEqual(progress.questRunId(), runId, "progression keeps the original run identity");
        helper.assertValueEqual(progress.startedVillagerId(), providerId, "progression keeps the original provider identity");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "narrative_runtime_boundaries")
    public static void rewardRetryUsesRecordedPlanAndCheckpointsBeforeFollowingEffects(GameTestHelper helper) {
        var progress = new QuestProgressState();
        var rewards = new QuestDefinition.Rewards(7, 4, 0, null, null, VillageEventMemory.MemoryScope.BOTH);
        List<String> order = new ArrayList<>();
        var failed = QuestRewardDeliveryService.deliver(progress, rewards, Map.of(), (action, replacements) -> {
            order.add(action.kind().name());
            return action.kind() == VillagerActionDefinition.Kind.REPUTATION
                    ? ActionResult.unavailable("provider unavailable", Set.of(), VillagerActionResult.EMPTY)
                    : ActionResult.success(VillagerActionResult.success(), Set.of());
        }, (action, result) -> { }, () -> order.add("save:" + progress.rewardDelivery().nextAction()));
        helper.assertValueEqual(failed.status(), ActionStatus.UNAVAILABLE, "unavailable effects stop delivery");
        helper.assertValueEqual(order, List.of("save:0", "EXPERIENCE", "save:1", "REPUTATION"),
                "the recorded plan precedes effects and completion checkpoints precede later effects");
        helper.assertValueEqual(progress.rewardDelivery().nextAction(), 1, "retry retains the failed reward cursor");
        order.clear();
        var reloadedRewards = new QuestDefinition.Rewards(99, 99, 0, null, null, VillageEventMemory.MemoryScope.BOTH);
        var retried = QuestRewardDeliveryService.deliver(progress, reloadedRewards, Map.of(), (action, replacements) -> {
            order.add(action.kind().name() + ":" + action.amount());
            return ActionResult.success(VillagerActionResult.success(), Set.of());
        }, (action, result) -> { }, () -> { });
        helper.assertTrue(retried.success(), "the pending reward can resume");
        helper.assertValueEqual(order, List.of("REPUTATION:4"), "retry neither replays experience nor replaces the saved plan");
        order.clear();
        QuestRewardDeliveryService.deliver(progress, rewards, Map.of(), (action, replacements) -> {
            order.add(action.kind().name());
            return ActionResult.success(VillagerActionResult.success(), Set.of());
        }, (action, result) -> { }, () -> { });
        helper.assertTrue(order.isEmpty(), "resolved delivery never replays effects");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "narrative_runtime_boundaries")
    public static void observationsUseIndexesAndPreserveObjectiveTriggerOrder(GameTestHelper helper) {
        var compiled = compiled();
        var definition = compiled.asQuestDefinition();
        var data = new VillagerQuestSavedData();
        UUID player = UUID.randomUUID();
        var progress = data.getOrCreate(player, definition.id());
        progress.start(UUID.randomUUID(), helper.getLevel().dimension(), null, 10L);
        progress.setCurrentStage("offer");
        QuestContentAccess content = new QuestContentAccess() {
            public Optional<QuestDefinition> quest(ResourceLocation id) { return Optional.of(definition); }
            public Optional<CompiledQuest> compiledQuest(ResourceLocation id) { return Optional.of(compiled); }
            public Optional<QuestTriggerIndex> questTriggerIndex(ResourceLocation id) {
                return Optional.of(compiled.triggerIndex());
            }
            public Set<ResourceLocation> questIdsForObjectiveEvent(QuestObjectiveEventKind kind) {
                return kind == QuestObjectiveEventKind.CRITERION ? Set.of(definition.id()) : Set.of();
            }
            public Set<ResourceLocation> memoryEventQuestIds(ResourceLocation tag) { return Set.of(); }
        };
        List<String> order = new ArrayList<>();
        var access = new QuestObservationDispatcher.Access<VillagerQuestSavedData.QuestProgress>() {
            public boolean active(QuestDefinition quest, VillagerQuestSavedData.QuestProgress state) {
                order.add("active");
                return true;
            }
            public boolean objectives(QuestDefinition quest, VillagerQuestSavedData.QuestProgress state, QuestObjectiveEvent observation) {
                order.add("objectives");
                return QuestObjectiveDispatcher.dispatch(state, quest.objectives(), true, observation.kind(),
                        objective -> true, objective -> false, objective -> order.add("completed"), null);
            }
            public boolean triggers(QuestDefinition quest, VillagerQuestSavedData.QuestProgress state,
                    QuestDefinition.TriggerEvent event, QuestObjectiveEvent observation) {
                order.add(event.name());
                return false;
            }
            public void advance(QuestDefinition quest, VillagerQuestSavedData.QuestProgress state) {
                order.add("advance");
                QuestProgressionService.advanceSaved(quest, state, 11L,
                        objective -> state.objectiveComplete(objective.id()), conditions -> true);
            }
            public void updated(QuestDefinition quest, VillagerQuestSavedData.QuestProgress state) { order.add("updated"); }
        };
        var observation = QuestObjectiveEvent.criterion(VillagerRetaliation.id("boundary"), Map.of(), ItemStack.EMPTY, null);
        var result = QuestObservationDispatcher.dispatch(content, data, player, observation, Set.of(), access);
        helper.assertTrue(result.changed() && result.progressNotice(), "the observation reports one progress update");
        helper.assertValueEqual(order, List.of("active", "objectives", "completed", "CRITERION", "advance", "updated", "PROGRESS"),
                "objective completion precedes direct triggers, advancement, presentation notification and progress triggers");
        helper.assertTrue(data.get(player, definition.id()) == progress, "dispatch mutates the existing storage owner's state");
        order.clear();
        QuestObservationDispatcher.dispatch(content, data, player, observation, Set.of(definition.id()), access);
        helper.assertTrue(order.isEmpty(), "party credited quests remain excluded from personal dispatch");
        QuestObservationDispatcher.dispatch(content, data, player,
                QuestObjectiveEvent.block(QuestObjectiveEventKind.BLOCK_BREAK, BlockPos.ZERO, Blocks.STONE.defaultBlockState()), Set.of(), access);
        helper.assertTrue(order.isEmpty(), "unindexed observations perform no domain evaluation or effect dispatch");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "narrative_runtime_boundaries")
    public static void observationsOwnMutableValuesAndTriggersCaptureDurableFacts(GameTestHelper helper) {
        var position = new BlockPos.MutableBlockPos(1, 2, 3);
        var block = QuestObjectiveEvent.block(QuestObjectiveEventKind.BLOCK_BREAK, position, Blocks.STONE.defaultBlockState());
        position.set(7, 8, 9);
        helper.assertValueEqual(block.blockPos(), new BlockPos(1, 2, 3), "an observation owns its captured position");
        var stack = new ItemStack(Items.BREAD, 3);
        var event = QuestObjectiveEvent.criterion(VillagerRetaliation.id("boundary"), Map.of("source", "archive"), stack, null);
        stack.shrink(2);
        helper.assertValueEqual(event.itemStack().getCount(), 3, "later inventory mutation cannot change captured event item facts");
        var trigger = QuestTriggerContext.of(null, QuestDefinition.TriggerEvent.CRITERION, 10L, "offer", event);
        var restored = QuestTriggerContext.load(trigger.save());
        helper.assertValueEqual(restored.value("item_count"), "3", "durable trigger facts retain the observed item count");
        helper.assertValueEqual(restored.value("criterion_source"), "archive", "durable trigger facts retain criterion data");
        helper.assertTrue(restored.dialogueContext() == null, "durable facts do not retain live assembly context");
        helper.succeed();
    }

    private static CompiledQuest compiled() {
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:runtime_boundary",
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"offer",
                 "events":[{"id":"observed","event":"criterion","actions":[{"type":"tracker"}]}],
                 "stages":[{"id":"offer","objectives":[{"id":"answer","type":"criterion","criterion":"villagerretaliation:boundary","count":1}],
                   "entry_actions":[{"type":"experience","amount":3}],"next":"ready"},
                  {"id":"ready","objectives":[{"id":"wait","type":"choice"}],
                   "entry_actions":[{"type":"experience","amount":5}]}]}
                """).getAsJsonObject();
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/runtime_boundary.json"), raw).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }
}
