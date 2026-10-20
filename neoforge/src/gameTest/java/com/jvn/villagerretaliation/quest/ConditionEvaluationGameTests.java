package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionLeafAccess;
import com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_boundary")
@PrefixGameTestTemplate(false)
public final class ConditionEvaluationGameTests {
    private static final DialogueCondition LEAF = new DialogueCondition.Reputation(Set.of(), 1, null);

    private ConditionEvaluationGameTests() { }

    @GameTest(template = "empty", batch = "campaign_condition_structure")
    public static void nestedCompositionUsesTheSameTriStateRulesForMatchesAndTraces(GameTestHelper helper) {
        for (ConditionOutcome left : ConditionOutcome.values()) {
            for (ConditionOutcome right : ConditionOutcome.values()) {
                DialogueCondition other = new DialogueCondition.Reputation(Set.of(), 2, null);
                var context = context((condition, trigger) -> condition == LEAF ? left : right);
                List<DialogueCondition> children = List.of(LEAF, other);
                ConditionOutcome all = left == ConditionOutcome.UNMET || right == ConditionOutcome.UNMET ? ConditionOutcome.UNMET
                        : left == ConditionOutcome.UNKNOWN || right == ConditionOutcome.UNKNOWN ? ConditionOutcome.UNKNOWN : ConditionOutcome.MATCHED;
                ConditionOutcome any = left == ConditionOutcome.MATCHED || right == ConditionOutcome.MATCHED ? ConditionOutcome.MATCHED
                        : left == ConditionOutcome.UNKNOWN || right == ConditionOutcome.UNKNOWN ? ConditionOutcome.UNKNOWN : ConditionOutcome.UNMET;
                assertOutcome(helper, context, new DialogueCondition.AllOf(children), all);
                assertOutcome(helper, context, new DialogueCondition.AnyOf(children), any);
                assertOutcome(helper, context, new DialogueCondition.Not(new DialogueCondition.AnyOf(children)), negate(any));
                assertOutcome(helper, context, new DialogueCondition.Not(new DialogueCondition.AllOf(children)), negate(all));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_condition_structure")
    public static void unavailableLeafDiagnosticsSurviveNestedNegation(GameTestHelper helper) {
        var context = context(new ConditionLeafAccess() {
            @Override
            public ConditionOutcome evaluate(DialogueCondition condition, QuestTriggerContext trigger) {
                return ConditionOutcome.UNKNOWN;
            }

            @Override
            public String unavailableReason(DialogueCondition condition, QuestTriggerContext trigger) {
                return "saved provider state unavailable";
            }
        });
        DialogueCondition nested = new DialogueCondition.Not(new DialogueCondition.AllOf(List.of(LEAF)));
        var trace = ConditionEvaluator.trace(context, null, nested);
        helper.assertValueEqual(trace.outcome(), ConditionOutcome.UNKNOWN, "unknown state cannot become a successful negation");
        helper.assertValueEqual(ConditionEvaluator.unavailableReason(trace), "saved provider state unavailable",
                "the leaf repair diagnostic survives composition");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_condition_structure")
    public static void playerOnlyConditionsNeedNoVillagerContextAndDoNotLoadProviders(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var context = VillagerConditionAccess.forPlayer(player);
        var world = new DialogueCondition.Dimension(Set.of(player.serverLevel().dimension().location()));
        int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
        assertOutcome(helper, context, new DialogueCondition.Not(LEAF), ConditionOutcome.UNKNOWN);
        assertOutcome(helper, context, new DialogueCondition.AnyOf(List.of(LEAF, world)), ConditionOutcome.MATCHED);
        assertOutcome(helper, context, new DialogueCondition.AllOf(List.of(LEAF, world)), ConditionOutcome.UNKNOWN);
        helper.assertTrue(context.liveProvider() == null && context.savedProvider() == null, "player discovery needs no provider snapshot");
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(player),
                        List.of(new DialogueCondition.AnyOf(List.of(LEAF, world)))),
                "legacy assembly uses the same decisive any branch");
        helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "conditions do not load provider chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_condition_structure")
    public static void matchingDoesNotEvaluateBranchesAfterADecisiveResult(GameTestHelper helper) {
        DialogueCondition unexpected = new DialogueCondition.Reputation(Set.of(), 2, null);
        int[] evaluations = {0};
        var context = context((condition, trigger) -> {
            evaluations[0]++;
            helper.assertTrue(condition == LEAF, "decisive branches preserve short circuit evaluation");
            return ConditionOutcome.MATCHED;
        });
        helper.assertValueEqual(ConditionEvaluator.evaluate(context, null,
                new DialogueCondition.AnyOf(List.of(LEAF, unexpected))), ConditionOutcome.MATCHED, "any stops after its match");
        helper.assertValueEqual(evaluations[0], 1, "one leaf query runs");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_condition_structure")
    public static void savedTriggerPayloadNeedsNoParticipantContext(GameTestHelper helper) {
        var snapshot = new QuestTriggerContext(QuestDefinition.TriggerEvent.GIFT, 42L, "before_reward",
                Map.of("item", "minecraft:emerald", "item_count", "3", "reputation", "12"), null);
        var trigger = QuestTriggerContext.load(snapshot.save());
        var payload = new DialogueCondition.TriggerPayload(Set.of("gift"), Map.of(),
                Map.of("item", Set.of("minecraft:emerald")), Map.of("gift_reaction", Set.of("disliked")), 10, 20);
        var mismatch = new DialogueCondition.TriggerPayload(Set.of("gift"), Map.of(),
                Map.of("item", Set.of("minecraft:bread")), Map.of(), null, null);
        var expressions = List.of(payload, new DialogueCondition.AnyOf(List.of(LEAF, payload)),
                new DialogueCondition.Not(mismatch), new DialogueCondition.AllOf(List.of(payload, new DialogueCondition.Not(mismatch))));
        for (DialogueCondition expression : expressions) {
            assertOutcome(helper, null, trigger, expression, ConditionOutcome.MATCHED);
            helper.assertTrue(DialogueCondition.matchesAll(null, trigger, List.of(expression)),
                    "saved event facts evaluate without a participant assembly");
        }
        assertOutcome(helper, null, trigger, mismatch, ConditionOutcome.UNMET);
        assertOutcome(helper, null, trigger, new DialogueCondition.Not(payload), ConditionOutcome.UNMET);
        assertOutcome(helper, null, trigger, new DialogueCondition.AllOf(List.of(payload, LEAF)), ConditionOutcome.UNKNOWN);
        assertOutcome(helper, null, trigger, new DialogueCondition.Not(LEAF), ConditionOutcome.UNKNOWN);
        assertOutcome(helper, null, null, payload, ConditionOutcome.UNKNOWN);
        assertOutcome(helper, null, null, new DialogueCondition.Not(payload), ConditionOutcome.UNKNOWN);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_condition_structure")
    public static void personalHistoryMatchesDirectPlayerAndSavedComposition(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        var definition = fixture();
        var data = VillagerQuestSavedData.get(level);
        var progress = data.getOrCreate(player.getUUID(), definition.id());
        progress.start(UUID.randomUUID(), level.dimension(), null, level.getGameTime());
        progress.recordChoice("offer", "accept", "opening", "delivery", level.getGameTime());
        var choice = new DialogueCondition.SelectedChoice(definition.id(), "offer", "accept", "opening", "delivery");
        var history = new DialogueCondition.StageHistory(definition.id(), "delivery", "opening", "delivery");
        var missing = new DialogueCondition.SelectedChoice(definition.id(), "offer", "decline", "", "");
        var dialogue = DialogueContext.forPlayer(player);
        var queries = VillagerQuestService.conditionQueries(player);
        int chunks = level.getChunkSource().getLoadedChunksCount();
        try {
            helper.assertTrue(choice.matches(dialogue) && history.matches(dialogue), "direct history queries need only the player's history");
            helper.assertTrue(DialogueCondition.matchesAll(dialogue, List.of(choice, history, new DialogueCondition.Not(missing))),
                    "player composition uses the same recorded choice and stage facts");
            var playerContext = VillagerConditionAccess.forPlayer(player, queries);
            var savedContext = VillagerConditionAccess.forSaved(level, player, definition, progress, queries);
            for (var context : List.of(playerContext, savedContext)) {
                assertOutcome(helper, context, choice, ConditionOutcome.MATCHED);
                assertOutcome(helper, context, history, ConditionOutcome.MATCHED);
                assertOutcome(helper, context, missing, ConditionOutcome.UNMET);
                assertOutcome(helper, context, new DialogueCondition.Not(missing), ConditionOutcome.MATCHED);
            }
            var unavailable = VillagerConditionAccess.forSaved(level, player, definition, progress);
            assertOutcome(helper, unavailable, choice, ConditionOutcome.UNKNOWN);
            assertOutcome(helper, unavailable, new DialogueCondition.Not(history), ConditionOutcome.UNKNOWN);
            helper.assertValueEqual(ConditionEvaluator.unavailableReason(ConditionEvaluator.trace(unavailable, null,
                    new DialogueCondition.Not(history))), "quest history is unavailable", "unavailable history retains its diagnostic through negation");
            helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), chunks, "personal history cannot load its saved provider");
        } finally {
            data.remove(player.getUUID(), definition.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_condition_structure")
    public static void liveQuestContextRetainsTheQuestAndRunIdentity(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        var definition = fixture();
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(villager.getUUID(), helper.getLevel().dimension(), null, helper.getLevel().getGameTime());
        UUID run = progress.beginRun(player.getUUID(), definition.id(), null);
        var dialogue = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var context = QuestExecutionContext.fromDialogueContext(dialogue, definition, "condition_test").conditions(progress);
        helper.assertValueEqual(context.questRun().questId(), definition.id(), "live conditions retain the owning quest");
        helper.assertValueEqual(context.questRun().runId(), run, "live conditions retain the definitive run");
        helper.assertValueEqual(context.providerId(), villager.getUUID(), "live conditions retain provider identity");
        helper.assertTrue(context.liveProvider() == villager, "live conditions use the existing loaded provider");
        helper.succeed();
    }

    private static QuestDefinition fixture() {
        var id = VillagerRetaliation.id("condition_history_boundary");
        return QuestDefinitionFixtures.read(id, JsonParser.parseString("""
                {"objectives":[{"id":"supplies","type":"item_check","item":"minecraft:wheat","count":6}]}
                """).getAsJsonObject(), id);
    }

    private static ConditionContext context(ConditionLeafAccess access) {
        return new ConditionContext(null, null, null, null, null, Set.of(), access);
    }

    private static void assertOutcome(GameTestHelper helper, ConditionContext context, DialogueCondition condition, ConditionOutcome expected) {
        assertOutcome(helper, context, null, condition, expected);
    }

    private static void assertOutcome(GameTestHelper helper, ConditionContext context, QuestTriggerContext trigger,
            DialogueCondition condition, ConditionOutcome expected) {
        helper.assertValueEqual(ConditionEvaluator.evaluate(context, trigger, condition), expected, "condition outcome");
        var trace = ConditionEvaluator.trace(context, trigger, condition);
        helper.assertValueEqual(trace.outcome(), expected, "trace agrees with matching");
        if (expected == ConditionOutcome.MATCHED) helper.assertTrue(trace.firstUnmatched().isEmpty(),
                "a matched expression does not expose rejected alternatives as failures");
    }

    private static ConditionOutcome negate(ConditionOutcome outcome) {
        return switch (outcome) {
            case MATCHED -> ConditionOutcome.UNMET;
            case UNMET -> ConditionOutcome.MATCHED;
            case UNKNOWN -> ConditionOutcome.UNKNOWN;
        };
    }
}
