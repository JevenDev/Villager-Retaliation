package com.jvn.villagerretaliation.gametest;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeDefinition;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDebugState;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRequirement;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveResult;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import com.jvn.villagerretaliation.quest.tracking.QuestStageReadiness;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * Generates one named GameTest for every built-in quest and questline.
 *
 * <p>The inventory is explicit so a content change cannot silently lose its dedicated test.
 */
@GameTestHolder
public final class BuiltInQuestContentGameTests {
    private static final String TEST_CLASS = "builtinquestcontentgametests";
    private static final String BATCH = "built_in_quest_content";
    private static final String EMPTY_TEMPLATE = "minecraft:empty";
    private static final UUID TEST_PROVIDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000085");
    private static final Map<String, List<String>> QUESTS_BY_QUESTLINE = questlines(
            questline("cartographers_atlas",
                    "blank_map_promise", "chart_the_drowned_coast", "choose_the_horizon",
                    "end_city_margin", "eye_of_the_last_room", "first_far_marker",
                    "ink_and_bearings", "ink_in_the_dark_roof", "nether_meridian",
                    "roads_that_remember", "the_atlas_test"),
            questline("courier_roads", "night_run", "wax_sealed_route"),
            questline("dangerous_commissions",
                    "gilded_debt", "house_of_ill_omens", "nether_wart_warranty", "trial_chamber_recall"),
            questline("deep_delvers", "below_the_bell", "iron_road", "mark_the_shaft", "timber_brace"),
            questline("end_survey", "choose_a_star", "chorus_trail", "city_lantern", "empty_sky"),
            questline("field_medicine", "clean_bandages", "night_ward"),
            questline("green_thumb", "canal_beds", "choose_the_soil", "new_furrows", "rich_earth"),
            questline("hearthbound", "first_fire", "shared_table"),
            questline("last_ember",
                    "breath_after_the_roar", "eye_before_dawn", "nether_candle", "pearl_lessons"),
            questline("lost_civilization", "end_city_survey", "tales_of_a_lost_civilization"),
            questline("nether_routes", "bastion_line", "choose_a_road", "fortress_line", "through_fire"),
            questline("old_roads", "sunken_ledger", "the_broken_milestone"),
            questline("redstone_works", "power_the_gate", "quiet_trigger", "small_spark", "sun_switch"),
            questline("village_commissions",
                    "apiary_smoke", "beacon_polish", "bell_rope", "compost_turn", "copper_weather",
                    "dragon_sample", "echo_trade", "ender_freight", "market_day", "ocean_glass",
                    "pond_restock", "powder_run", "road_mending", "spider_silk", "wither_ash"),
            questline("village_defense", "fletchers_countermark", "standing_watch", "watch_arrows"),
            questline("village_supply",
                    "beetroot_bundle", "berry_picking", "bottle_stock", "bread_delivery",
                    "clay_repairs", "egg_baskets", "feather_fletching", "fresh_cod",
                    "ink_supply", "kiln_fuel", "leather_repairs", "map_paper",
                    "seed_stockpile", "torch_bundle", "village_lanterns", "wool_blankets"),
            questline("workshop_oaths", "honed_handles", "temper_and_tally"));

    static {
        configureGameTestStructures();
    }

    private BuiltInQuestContentGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> generateBuiltInQuestContentTests() {
        List<TestFunction> tests = new ArrayList<>(expectedQuestCount() + QUESTS_BY_QUESTLINE.size());
        for (Map.Entry<String, List<String>> questline : QUESTS_BY_QUESTLINE.entrySet()) {
            String questlineId = questline.getKey();
            tests.add(test(
                    TEST_CLASS + ".questline." + questlineId,
                    helper -> assertQuestlineWorks(helper, questlineId)));
            for (String questPath : questline.getValue()) {
                tests.add(test(
                        TEST_CLASS + ".quest." + questPath,
                        helper -> assertQuestWorks(helper, questlineId, questPath)));
            }
        }
        return List.copyOf(tests);
    }

    static Map<String, Integer> expectedQuestlineCounts() {
        return QUESTS_BY_QUESTLINE.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().size()));
    }

    static int expectedQuestCount() {
        return QUESTS_BY_QUESTLINE.values().stream().mapToInt(List::size).sum();
    }

    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> body) {
        return new TestFunction(BATCH, name, EMPTY_TEMPLATE, 100, 0L, true, body);
    }

    private static void assertQuestWorks(GameTestHelper helper, String expectedQuestline, String questPath) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation questId = VillagerRetaliation.id(questPath);
        ResourceLocation expectedSource =
                VillagerRetaliation.id(
                        "quests/" + expectedQuestline + "/" + questPath + "/quest.json");
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(server, questId)
                .orElseThrow(() -> new GameTestAssertException("Missing compiled quest " + questId));
        QuestDefinition definition = compiled.asQuestDefinition();

        helper.assertValueEqual(compiled.id(), questId, questId + " compiled id");
        helper.assertValueEqual(definition.id(), questId, questId + " runtime id");
        helper.assertValueEqual(definition.questline(), expectedQuestline, questId + " questline");
        helper.assertValueEqual(compiled.schemaVersion(), QuestSchemaVersion.V2, questId + " schema");
        helper.assertValueEqual(
                compiled.source().resource(), expectedSource, questId + " source resource");
        helper.assertFalse(definition.title().isBlank(), questId + " title is blank");
        helper.assertFalse(definition.description().isBlank(), questId + " description is blank");
        helper.assertFalse(definition.tags().isEmpty(), questId + " has no grouping tags");
        helper.assertTrue(compiled.provider().providerType() != null, questId + " has no provider type");

        assertParentResolves(helper, definition);
        assertRewardsResolve(helper, server, definition);
        assertObjectivesWork(helper, definition, compiled);
        assertStagesWork(helper, definition, compiled);
        assertDialogueWorks(helper, server, definition);
        assertRepeatabilityContract(helper, definition);
        assertPacingContracts(helper, definition);
        helper.succeed();
    }

    private static void assertPacingContracts(GameTestHelper helper, QuestDefinition definition) {
        if (definition.id().equals(VillagerRetaliation.id("first_fire"))) {
            QuestDefinition.Stage entry = definition.stages().get(definition.entryStage());
            helper.assertValueEqual(entry.objectives().size(), 4, "Kindling discloses its full supply list");
            helper.assertValueEqual(entry.next(), "return", "Kindling has no second supply-list stage");
            VillagerQuestSavedData.QuestProgress oldProgress = new VillagerQuestSavedData.QuestProgress();
            oldProgress.setCurrentStage("fill_pots");
            helper.assertTrue(VillagerQuestService.migrateRetiredHearthboundStage(definition, oldProgress),
                    "Kindling migrates the retired fill_pots stage");
        }
        if (definition.id().equals(VillagerRetaliation.id("shared_table"))) {
            QuestDefinition.Stage entry = definition.stages().get(definition.entryStage());
            helper.assertValueEqual(entry.objectives().size(), 4, "Supper discloses its full ingredient list");
            helper.assertValueEqual(entry.next(), "return", "Supper has no second ingredient-list stage");
            helper.assertValueEqual(
                    definition.rules().prerequisiteCooldownTicks(),
                    24_000L,
                    "Supper waits one Minecraft day after Kindling");
            VillagerQuestSavedData.QuestProgress oldProgress = new VillagerQuestSavedData.QuestProgress();
            oldProgress.setCurrentStage("sweeten");
            helper.assertTrue(VillagerQuestService.migrateRetiredHearthboundStage(definition, oldProgress),
                    "Supper migrates the retired sweeten stage");
        }
    }

    private static void assertParentResolves(GameTestHelper helper, QuestDefinition definition) {
        ResourceLocation parent = definition.parent();
        if (parent == null) {
            return;
        }
        QuestDefinition parentDefinition = VillagerQuestResources.quest(helper.getLevel().getServer(), parent)
                .orElseThrow(() -> new GameTestAssertException(definition.id() + " has missing parent " + parent));
        helper.assertValueEqual(
                parentDefinition.questline(),
                definition.questline(),
                definition.id() + " parent questline");
        helper.assertTrue(
                QUESTS_BY_QUESTLINE.getOrDefault(definition.questline(), List.of()).contains(parent.getPath()),
                definition.id() + " parent is absent from the test inventory: " + parent);
    }

    private static void assertRewardsResolve(
            GameTestHelper helper,
            MinecraftServer server,
            QuestDefinition definition) {
        QuestDefinition.Rewards rewards = definition.rewards();
        ResourceLocation lootTable = rewards.lootTable();
        helper.assertTrue(rewards.experience() > 0, definition.id() + " has no XP reward");
        helper.assertTrue(rewards.reputation() > 0, definition.id() + " has no reputation reward");
        helper.assertTrue(rewards.gossipReputation() != 0, definition.id() + " has no gossip reward");
        helper.assertTrue(lootTable != null, definition.id() + " has no loot-table reward");
        helper.assertTrue(rewards.memoryEvent() != null, definition.id() + " has no memory-event reward");
        QuestRewardResolver.Resolution resolved = QuestRewardResolver.resolve(server, lootTable);
        helper.assertTrue(
                resolved.resolved() && resolved.source() == QuestRewardResolver.Source.BUNDLED,
                definition.id() + " bundled reward did not resolve: " + resolved.diagnostic());
    }

    private static void assertObjectivesWork(
            GameTestHelper helper,
            QuestDefinition definition,
            CompiledQuest compiled) {
        helper.assertFalse(definition.objectives().isEmpty(), definition.id() + " has no objectives");
        helper.assertValueEqual(
                compiled.objectives().size(),
                definition.objectives().size(),
                definition.id() + " compiled objective count");
        Set<String> objectiveIds = new LinkedHashSet<>();
        for (QuestDefinition.Objective objective : definition.objectives()) {
            helper.assertTrue(objectiveIds.add(objective.id()),
                    definition.id() + " repeats objective id " + objective.id());
            helper.assertTrue(compiled.objectivesById().containsKey(objective.id()),
                    definition.id() + " did not compile objective " + objective.id());
            Optional<String> validationError = QuestObjectiveRegistry.validationError(objective);
            helper.assertTrue(validationError.isEmpty(),
                    definition.id() + "/" + objective.id() + " is invalid: " + validationError.orElse(""));
            helper.assertFalse(QuestObjectiveRegistry.canonicalTypeId(objective).isBlank(),
                    definition.id() + "/" + objective.id() + " has no registered objective type");
            helper.assertFalse(QuestObjectiveRegistry.requirements(objective).isEmpty(),
                    definition.id() + "/" + objective.id() + " has no runtime requirements");
            helper.assertFalse(QuestObjectiveRegistry.trackerStepKey(objective).isBlank(),
                    definition.id() + "/" + objective.id() + " has no tracker step");
            helper.assertTrue(objective.tracker().hasAnyDisplay(),
                    definition.id() + "/" + objective.id() + " has no tracker display");
            helper.assertValueEqual(
                    compiled.objectivesById().get(objective.id()).source().resource(),
                    compiled.source().resource(),
                    definition.id() + "/" + objective.id() + " source");

            VillagerQuestSavedData.QuestProgress progress = syntheticProgress(helper);
            progress.addObjectiveCounter(objective.id(), objective.count());
            progress.markVisitedTarget();
            QuestObjectiveEvaluationContext context = new QuestObjectiveEvaluationContext(
                    null,
                    null,
                    helper.getLevel(),
                    definition,
                    progress,
                    ignored -> objective.count(),
                    (ignored, stack) -> true,
                    ignored -> syntheticReputation(objective),
                    ignored -> true,
                    ignored -> true,
                    ignored -> QuestObjectiveDebugState.EMPTY);
            QuestObjectiveResult result = QuestObjectiveRegistry.evaluate(context, objective)
                    .orElseThrow(() -> new GameTestAssertException(
                            definition.id() + "/" + objective.id() + " has no runtime evaluator"));
            helper.assertTrue(result.progress() >= 0.0F && result.progress() <= 1.0F,
                    definition.id() + "/" + objective.id() + " returned invalid progress");
            if (objective.type() != QuestDefinition.ObjectiveType.STRUCTURE_VISIT
                    && objective.type() != QuestDefinition.ObjectiveType.LOCATION_VISIT) {
                helper.assertTrue(result.complete(),
                        definition.id() + "/" + objective.id()
                                + " rejected a satisfied objective: " + result.message());
            }
            if (QuestObjectiveRegistry.requirements(objective).contains(QuestObjectiveRequirement.EVENT)) {
                helper.assertFalse(QuestObjectiveRegistry.eventKinds(objective).isEmpty(),
                        definition.id() + "/" + objective.id() + " event objective has no subscription");
            }
        }
    }

    private static int syntheticReputation(QuestDefinition.Objective objective) {
        if (objective.minReputation() != null) {
            return objective.minReputation();
        }
        if (objective.maxReputation() != null) {
            return objective.maxReputation();
        }
        return 100;
    }

    private static VillagerQuestSavedData.QuestProgress syntheticProgress(GameTestHelper helper) {
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(
                TEST_PROVIDER_ID,
                helper.getLevel().dimension(),
                helper.absolutePos(BlockPos.ZERO),
                helper.getLevel().getGameTime());
        return progress;
    }

    private static void assertStagesWork(
            GameTestHelper helper,
            QuestDefinition definition,
            CompiledQuest compiled) {
        helper.assertFalse(definition.stages().isEmpty(), definition.id() + " has no stages");
        helper.assertValueEqual(
                compiled.stages().size(),
                definition.stages().size(),
                definition.id() + " compiled stage count");
        helper.assertValueEqual(
                QuestLifecycleService.initialStage(definition),
                definition.entryStage(),
                definition.id() + " initial stage");
        VillagerQuestSavedData.QuestProgress progress = syntheticProgress(helper);
        QuestLifecycleService.initializeStage(definition, progress, helper.getLevel().getGameTime());
        helper.assertValueEqual(progress.currentStage(), definition.entryStage(), definition.id() + " entry stage");

        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (String stageId : definition.stages().keySet()) {
            graph.put(stageId, new LinkedHashSet<>());
        }
        Set<String> objectiveIds = definition.objectives().stream()
                .map(QuestDefinition.Objective::id)
                .collect(Collectors.toSet());
        for (QuestDefinition.Stage stage : definition.stages().values()) {
            helper.assertTrue(compiled.stagesById().containsKey(stage.id()),
                    definition.id() + " did not compile stage " + stage.id());
            helper.assertValueEqual(
                    compiled.stagesById().get(stage.id()).source().resource(),
                    compiled.source().resource(),
                    definition.id() + "/" + stage.id() + " stage source");
            for (String objectiveId : stage.objectives()) {
                helper.assertTrue(objectiveIds.contains(objectiveId),
                        definition.id() + "/" + stage.id() + " references missing objective " + objectiveId);
            }
            for (QuestDefinition.StagePredicate predicate : stage.completeWhen()) {
                if (!predicate.objective().isBlank()) {
                    helper.assertTrue(objectiveIds.contains(predicate.objective()),
                            definition.id() + "/" + stage.id()
                                    + " readiness references missing objective " + predicate.objective());
                }
            }
            if (!stage.next().isBlank()) {
                assertStageTarget(helper, definition, stage.id(), stage.next());
                graph.get(stage.id()).add(stage.next());
                if (stage.completeWhen().stream().allMatch(predicate ->
                        !predicate.objective().isBlank() && predicate.conditions().isEmpty())) {
                    progress.setCurrentStage(stage.id());
                    QuestStageReadiness readiness =
                            QuestStageReadiness.forCurrentStage(null, definition, progress, ignored -> true);
                    helper.assertTrue(readiness.ready(),
                            definition.id() + "/" + stage.id()
                                    + " is not ready when every objective is complete");
                }
                assertLifecycleTransition(helper, definition, progress, stage.id(), stage.next());
            }
            for (QuestDefinition.StageBranch branch : stage.branches()) {
                if (!branch.next().isBlank()) {
                    assertStageTarget(helper, definition, stage.id(), branch.next());
                    graph.get(stage.id()).add(branch.next());
                    assertLifecycleTransition(helper, definition, progress, stage.id(), branch.next());
                }
            }
        }
        QuestDialogueCatalog catalog = VillagerQuestResources.questDialogueCatalog(helper.getLevel().getServer());
        addDialogueTransitions(helper, definition, catalog, graph);
        helper.assertValueEqual(
                reachableStages(definition.entryStage(), graph),
                new LinkedHashSet<>(definition.stages().keySet()),
                definition.id() + " reachable stages");
    }

    private static void assertStageTarget(
            GameTestHelper helper,
            QuestDefinition definition,
            String source,
            String target) {
        helper.assertTrue(definition.stages().containsKey(target),
                definition.id() + "/" + source + " transitions to missing stage " + target);
    }

    private static void assertLifecycleTransition(
            GameTestHelper helper,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String source,
            String target) {
        progress.setCurrentStage(source);
        QuestLifecycleService.StageTransition transition =
                QuestLifecycleService.transitionStage(definition, progress, target, helper.getLevel().getGameTime());
        helper.assertTrue(transition.changed(),
                definition.id() + " lifecycle rejected " + source + " -> " + target);
        helper.assertValueEqual(progress.currentStage(), target,
                definition.id() + " lifecycle target for " + source);
    }

    private static void addDialogueTransitions(
            GameTestHelper helper,
            QuestDefinition definition,
            QuestDialogueCatalog catalog,
            Map<String, Set<String>> graph) {
        for (QuestDialogueCatalog.Binding binding : catalog.bindings(definition.id())) {
            DialogueTreeDefinition tree = catalog.tree(binding.treeId())
                    .orElseThrow(() -> new GameTestAssertException(
                            definition.id() + " binding points to missing tree " + binding.treeId()));
            for (DialogueTreeDefinition.Node node : tree.nodes().values()) {
                addDialogueTransitions(helper, definition, node.actions(), graph);
                for (DialogueTreeDefinition.Response response : node.responses()) {
                    addDialogueTransitions(helper, definition, response.actions(), graph);
                }
            }
        }
    }

    private static void addDialogueTransitions(
            GameTestHelper helper,
            QuestDefinition definition,
            List<VillagerActionDefinition> actions,
            Map<String, Set<String>> graph) {
        for (VillagerActionDefinition action : actions) {
            if (action.kind() != VillagerActionDefinition.Kind.QUEST_TRANSITION
                    || !definition.id().equals(action.questTransition().questId())
                    || action.questTransition().target()
                            != com.jvn.villagerretaliation.quest.compiled.CompiledQuestTransition.Target.STAGE) {
                continue;
            }
            String source = action.questTransition().stageId();
            String target = action.questTransition().targetStage();
            assertStageTarget(helper, definition, source, target);
            helper.assertTrue(graph.containsKey(source),
                    definition.id() + " dialogue transition starts at missing stage " + source);
            graph.get(source).add(target);
        }
    }

    private static Set<String> reachableStages(String entryStage, Map<String, Set<String>> graph) {
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(entryStage);
        while (!pending.isEmpty()) {
            String stage = pending.removeFirst();
            if (visited.add(stage)) {
                pending.addAll(graph.getOrDefault(stage, Set.of()));
            }
        }
        return visited;
    }

    private static void assertDialogueWorks(
            GameTestHelper helper,
            MinecraftServer server,
            QuestDefinition definition) {
        QuestDialogueCatalog catalog = VillagerQuestResources.questDialogueCatalog(server);
        helper.assertTrue(catalog.hasGeneratedQuestDialogue(definition.id()),
                definition.id() + " has no generated dialogue");
        assertDialogueSlot(
                helper, definition.id(), catalog, "offer", VillagerActionDefinition.QuestAction.START);
        assertDialogueSlot(
                helper, definition.id(), catalog, "reminder", VillagerActionDefinition.QuestAction.REMIND);
        assertDialogueSlot(
                helper, definition.id(), catalog, "turn_in", VillagerActionDefinition.QuestAction.TURN_IN);
        for (QuestDialogueCatalog.Binding binding : catalog.bindings(definition.id())) {
            DialogueTreeDefinition tree = catalog.tree(binding.treeId())
                    .orElseThrow(() -> new GameTestAssertException(
                            definition.id() + " binding points to missing tree " + binding.treeId()));
            for (DialogueTreeDefinition.Node node : tree.nodes().values()) {
                assertDialogueActionSafety(helper, definition, node.actions(), node.id());
                for (DialogueTreeDefinition.Response response : node.responses()) {
                    assertDialogueActionSafety(
                            helper,
                            definition,
                            response.actions(),
                            node.id() + "/" + response.id());
                }
            }
        }
    }

    private static void assertDialogueActionSafety(
            GameTestHelper helper,
            QuestDefinition definition,
            List<VillagerActionDefinition> actions,
            String source) {
        int firstBlockingScene = -1;
        int firstTransition = -1;
        for (int index = 0; index < actions.size(); index++) {
            VillagerActionDefinition action = actions.get(index);
            if (action.kind() == VillagerActionDefinition.Kind.NOTIFICATION) {
                helper.assertTrue(action.questId() != null,
                        definition.id() + " notification lacks quest placeholder context at " + source);
            }
            if (firstBlockingScene < 0
                    && action.kind() == VillagerActionDefinition.Kind.START_SCENE
                    && action.waitForScene()) {
                firstBlockingScene = index;
            }
            if (firstTransition < 0
                    && action.kind() == VillagerActionDefinition.Kind.QUEST_TRANSITION) {
                firstTransition = index;
            }
        }
        if (firstBlockingScene >= 0 && firstTransition >= 0) {
            helper.assertValueEqual(firstTransition, firstBlockingScene + 1,
                    definition.id() + " does not commit immediately after launching its blocking scene at " + source);
        }
    }

    private static void assertDialogueSlot(
            GameTestHelper helper,
            ResourceLocation questId,
            QuestDialogueCatalog catalog,
            String slot,
            VillagerActionDefinition.QuestAction expectedAction) {
        QuestDialogueCatalog.Binding binding = catalog.bindings(questId).stream()
                .filter(candidate -> candidate.slot().equals(slot))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException(questId + " has no " + slot + " binding"));
        DialogueTreeDefinition tree = catalog.tree(binding.treeId())
                .orElseThrow(() -> new GameTestAssertException(questId + " has no tree " + binding.treeId()));
        DialogueTreeDefinition.Entry entry = tree.entry(binding.entryId())
                .orElseThrow(() -> new GameTestAssertException(
                        questId + " tree " + binding.treeId() + " has no entry " + binding.entryId()));
        helper.assertTrue(tree.node(entry.start()).isPresent(),
                questId + " " + slot + " entry points to missing node " + entry.start());
        helper.assertTrue(hasReachableQuestAction(tree, entry.start(), expectedAction, questId),
                questId + " " + slot + " dialogue cannot reach " + expectedAction);
    }

    private static boolean hasReachableQuestAction(
            DialogueTreeDefinition tree,
            String start,
            VillagerActionDefinition.QuestAction expectedAction,
            ResourceLocation questId) {
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String nodeId = pending.removeFirst();
            if (!visited.add(nodeId)) {
                continue;
            }
            Optional<DialogueTreeDefinition.Node> node = tree.node(nodeId);
            if (node.isEmpty()) {
                continue;
            }
            if (node.get().actions().stream()
                    .anyMatch(action -> isQuestAction(action, expectedAction, questId))) {
                return true;
            }
            for (DialogueTreeDefinition.Response response : node.get().responses()) {
                if (response.actions().stream()
                        .anyMatch(action -> isQuestAction(action, expectedAction, questId))) {
                    return true;
                }
                if (!response.next().isBlank()) {
                    pending.add(response.next());
                }
            }
        }
        return false;
    }

    private static boolean isQuestAction(
            VillagerActionDefinition action,
            VillagerActionDefinition.QuestAction expectedAction,
            ResourceLocation questId) {
        if (expectedAction == VillagerActionDefinition.QuestAction.START
                && action.kind() == VillagerActionDefinition.Kind.QUEST_TRANSITION
                && questId.equals(action.questTransition().questId())) {
            return true;
        }
        return action.kind() == VillagerActionDefinition.Kind.QUEST
                && action.questAction() == expectedAction
                && questId.equals(action.questId());
    }

    private static void assertRepeatabilityContract(GameTestHelper helper, QuestDefinition definition) {
        if (!definition.rules().repeatable()) {
            return;
        }
        helper.assertTrue(definition.rules().consumeOnCompletion(),
                definition.id() + " repeatable quest does not consume hand-in items");
        helper.assertTrue(definition.rules().completionCooldownTicks() > 0L,
                definition.id() + " repeatable quest has no completion cooldown");
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK) {
                helper.assertTrue(objective.consume(),
                        definition.id() + "/" + objective.id()
                                + " repeatable item objective is not consumed");
            }
        }
    }

    private static void assertQuestlineWorks(GameTestHelper helper, String questlineId) {
        List<ResourceLocation> expected = QUESTS_BY_QUESTLINE.get(questlineId).stream()
                .map(VillagerRetaliation::id)
                .toList();
        List<QuestDefinition> actual = VillagerQuestResources.quests(helper.getLevel().getServer()).stream()
                .filter(quest -> quest.questline().equals(questlineId))
                .sorted(Comparator.comparing(quest -> quest.id().toString()))
                .toList();
        Set<ResourceLocation> actualIds = actual.stream()
                .map(QuestDefinition::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        helper.assertValueEqual(
                actualIds,
                new LinkedHashSet<>(expected),
                questlineId + " exact quest membership");

        Map<ResourceLocation, Set<ResourceLocation>> children = new LinkedHashMap<>();
        Set<ResourceLocation> roots = new LinkedHashSet<>();
        for (QuestDefinition quest : actual) {
            children.put(quest.id(), new LinkedHashSet<>());
        }
        for (QuestDefinition quest : actual) {
            if (quest.parent() == null) {
                roots.add(quest.id());
                continue;
            }
            helper.assertTrue(children.containsKey(quest.parent()),
                    quest.id() + " parent is outside " + questlineId + ": " + quest.parent());
            children.get(quest.parent()).add(quest.id());
        }
        helper.assertFalse(roots.isEmpty(), questlineId + " has no root quest");

        Set<ResourceLocation> reachable = new LinkedHashSet<>();
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            ResourceLocation questId = pending.removeFirst();
            helper.assertTrue(reachable.add(questId),
                    questlineId + " contains a prerequisite cycle at " + questId);
            pending.addAll(children.getOrDefault(questId, Set.of()));
        }
        helper.assertValueEqual(reachable, actualIds, questlineId + " prerequisite reachability");
        helper.succeed();
    }

    @SafeVarargs
    private static Map<String, List<String>> questlines(Map.Entry<String, List<String>>... entries) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : entries) {
            if (result.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("Duplicate questline " + entry.getKey());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map.Entry<String, List<String>> questline(String id, String... quests) {
        return Map.entry(id, List.of(quests));
    }

    private static void configureGameTestStructures() {
        String configured = System.getProperty("villagerretaliation.gameteststructures");
        if (configured != null && !configured.isBlank()) {
            StructureUtils.testStructuresDir = configured;
            return;
        }
        for (Path candidate : List.of(
                Path.of("src/main/gameteststructures"),
                Path.of("../src/main/gameteststructures"),
                Path.of("neoforge/src/main/gameteststructures"))) {
            if (Files.isDirectory(candidate)) {
                StructureUtils.testStructuresDir = candidate.toAbsolutePath().normalize().toString();
                return;
            }
        }
    }
}
