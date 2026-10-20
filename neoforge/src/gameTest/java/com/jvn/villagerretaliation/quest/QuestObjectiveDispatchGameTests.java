package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatchService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatcher;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressState;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class QuestObjectiveDispatchGameTests {
    private QuestObjectiveDispatchGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_objective_dispatch")
    public static void countersRespectStageEventDataAndCompletedObjectives(GameTestHelper helper) {
        var definition = definition("""
                {"objectives":[
                  {"id":"evidence","type":"criterion","criterion":"villagerretaliation:evidence","match":{"source":"archive"},"count":2},
                  {"id":"later","type":"criterion","criterion":"villagerretaliation:evidence","count":1}],
                 "stages":{"active":{"objectives":["evidence"]},"later":{"objectives":["later"]}}}
                """);
        var progress = progress(helper);
        progress.setTarget(progress.startedVillagerId(), helper.getLevel().dimension(), BlockPos.ZERO, "evidence");
        var context = context(helper, definition, progress, objective -> false);
        List<String> completed = new ArrayList<>();
        var unrelated = event("road");
        helper.assertFalse(QuestObjectiveDispatchService.dispatch(context, unrelated, objective -> completed.add(objective.id())),
                "a criterion with different authored source data cannot advance the objective");
        var event = event("archive");
        helper.assertTrue(QuestObjectiveDispatchService.dispatch(context, event, objective -> completed.add(objective.id())),
                "the first matching event increments the counter");
        helper.assertFalse(progress.objectiveComplete("evidence"), "one event does not meet a two-event objective");
        helper.assertTrue(QuestObjectiveDispatchService.dispatch(context, event, objective -> completed.add(objective.id())),
                "the second matching event completes the objective");
        helper.assertFalse(QuestObjectiveDispatchService.dispatch(context, event, objective -> completed.add(objective.id())),
                "later matching events do not replay completed objective effects");
        helper.assertValueEqual(progress.objectiveCounter("evidence"), 2, "completed counters stop advancing");
        helper.assertValueEqual(progress.objectiveCounter("later"), 0, "events do not advance a future stage");
        helper.assertValueEqual(completed, List.of("evidence"), "completion publishes once");
        helper.assertTrue(progress.targetPos() == null && progress.targetObjectiveId().isBlank(),
                "completing a located objective clears its old target");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_objective_dispatch")
    public static void pollingPublishesCompletionBeforeFollowingObjectiveEvaluation(GameTestHelper helper) {
        var definition = definition("""
                {"objectives":[{"id":"first","type":"fact","key":"first","value":"yes"},
                  {"id":"second","type":"fact","key":"second","value":"yes"}],
                 "stages":{"active":{"objectives":["first","second"]}}}
                """);
        var progress = progress(helper);
        List<String> completed = new ArrayList<>();
        var context = context(helper, definition, progress,
                objective -> objective.id().equals("first") || completed.contains("first"));
        helper.assertTrue(QuestObjectiveDispatchService.poll(context, objective -> completed.add(objective.id())),
                "polling applies satisfied objectives");
        helper.assertValueEqual(completed, List.of("first", "second"),
                "a completion fact is visible when the next authored objective is evaluated");
        helper.assertFalse(QuestObjectiveDispatchService.poll(context, objective -> completed.add(objective.id())),
                "repeated polling does not replay completion effects");
        helper.assertValueEqual(completed.size(), 2, "each completion callback runs once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_objective_dispatch")
    public static void runtimeDispatcherUsesExplicitPolicyAndPreservesObserverOrdering(GameTestHelper helper) {
        var definition = definition("""
                {"objectives":[{"id":"evidence","type":"criterion","criterion":"villagerretaliation:evidence","count":2}],
                 "stages":{"active":{"objectives":["evidence"]}}}
                """);
        QuestProgressState progress = new QuestProgressState();
        progress.start(UUID.randomUUID(), helper.getLevel().dimension(), null, helper.getLevel().getGameTime());
        progress.setCurrentStage("active");
        progress.setTarget(progress.startedVillagerId(), helper.getLevel().dimension(), BlockPos.ZERO, "evidence");
        List<String> order = new ArrayList<>();
        var objectives = definition.objectives();
        helper.assertFalse(QuestObjectiveDispatcher.dispatch(progress, objectives, false, QuestObjectiveEventKind.CRITERION,
                        objective -> { helper.fail("denied policy must not evaluate gameplay facts"); return true; },
                        objective -> false, objective -> order.add("complete"), update -> order.add("observed")),
                "an explicit denied access decision prevents progression");
        for (int index = 0; index < 2; index++) {
            helper.assertTrue(QuestObjectiveDispatcher.dispatch(progress, objectives, true, QuestObjectiveEventKind.CRITERION,
                            objective -> true, objective -> false, objective -> order.add("complete"), update -> {
                                helper.assertValueEqual(update.counterDelta(), 1, "one observation produces one counter increment");
                                if (update.completed()) helper.assertTrue(progress.targetPos() == null, "target clears before progress observation");
                                order.add(update.completed() ? "observed_complete" : "observed_progress");
                            }), "permitted observations advance the runtime model");
        }
        helper.assertValueEqual(order, List.of("observed_progress", "complete", "observed_complete"),
                "completion effects remain ordered before the progress observer");
        helper.assertValueEqual(progress.objectiveCounter("evidence"), 2, "runtime progress uses the existing state owner");
        helper.succeed();
    }

    private static QuestDefinition definition(String raw) {
        var id = VillagerRetaliation.id("objective_dispatch");
        return QuestDefinitionFixtures.read(id, JsonParser.parseString(raw).getAsJsonObject(), id);
    }

    private static VillagerQuestSavedData.QuestProgress progress(GameTestHelper helper) {
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), helper.getLevel().dimension(), null, helper.getLevel().getGameTime());
        progress.setCurrentStage("active");
        return progress;
    }

    private static QuestObjectiveEvaluationContext context(GameTestHelper helper, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, Predicate<QuestDefinition.Objective> factMatcher) {
        return new QuestObjectiveEvaluationContext(null, null, helper.getLevel(), definition, progress,
                null, null, null, factMatcher, null, null);
    }

    private static QuestObjectiveEvent event(String source) {
        return QuestObjectiveEvent.criterion(VillagerRetaliation.id("evidence"), Map.of("source", source), ItemStack.EMPTY, null);
    }
}
