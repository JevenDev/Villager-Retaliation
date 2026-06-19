package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.ActionCapability;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueTreeDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources;
import com.jvn.villagerretaliation.quest.QuestDebugFormatter;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.QuestObjectiveRequirement;
import com.jvn.villagerretaliation.quest.QuestObjectiveResult;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.QuestTrackerPresenter;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestObjective;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestStage;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.provider.QuestProviderType;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerQuestGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final String LOCALE = "en_us";
    private static final ResourceLocation FAKE_PROVIDER_CAPABILITY = VillagerRetaliation.id("fake_provider");
    private static final EnumSet<QuestDefinition.TriggerEvent> DEFERRED_TRIGGER_EVENTS = EnumSet.of(
            QuestDefinition.TriggerEvent.PLAYER_TICK,
            QuestDefinition.TriggerEvent.PROXIMITY,
            QuestDefinition.TriggerEvent.PROGRESS);
    private static final EnumSet<VillagerActionDefinition.Kind> LIVE_CONTEXT_ACTION_KINDS = EnumSet.of(
            VillagerActionDefinition.Kind.QUEST,
            VillagerActionDefinition.Kind.REPUTATION,
            VillagerActionDefinition.Kind.GOSSIP,
            VillagerActionDefinition.Kind.MEMORY,
            VillagerActionDefinition.Kind.LOOT,
            VillagerActionDefinition.Kind.NOTIFICATION,
            VillagerActionDefinition.Kind.FORCED_DIALOGUE);
    private static final Map<String, Integer> EXPECTED_QUESTLINE_COUNTS = orderedMap(
            Map.entry("cartographers_atlas", 8),
            Map.entry("dangerous_commissions", 4),
            Map.entry("lost_civilization", 2),
            Map.entry("old_roads", 2),
            Map.entry("village_defense", 3),
            Map.entry("village_supply", 16));
    private static final int EXPECTED_QUEST_COUNT = EXPECTED_QUESTLINE_COUNTS.values().stream()
            .mapToInt(Integer::intValue)
            .sum();

    static {
        configureGameTestStructures();
    }

    private VillagerQuestGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builtInQuestLinesLoad(GameTestHelper helper) {
        List<QuestDefinition> quests = quests(helper);

        helper.assertValueEqual(quests.size(), EXPECTED_QUEST_COUNT, "built-in quest count");
        Map<String, Long> counts = quests.stream()
                .collect(Collectors.groupingBy(
                        QuestDefinition::questline,
                        LinkedHashMap::new,
                        Collectors.counting()));
        for (Map.Entry<String, Integer> expected : EXPECTED_QUESTLINE_COUNTS.entrySet()) {
            helper.assertValueEqual(
                    counts.getOrDefault(expected.getKey(), 0L).intValue(),
                    expected.getValue(),
                    expected.getKey() + " quest count");
        }
        helper.assertValueEqual(counts.size(), EXPECTED_QUESTLINE_COUNTS.size(), "questline count");

        for (QuestDefinition quest : quests) {
            helper.assertValueEqual(quest.id().getNamespace(), VillagerRetaliation.MOD_ID, "quest namespace");
            helper.assertFalse(quest.title().isBlank(), quest.id() + " has no title");
            helper.assertFalse(quest.description().isBlank(), quest.id() + " has no description");
            helper.assertFalse(quest.questline().isBlank(), quest.id() + " has no questline");
            helper.assertFalse(quest.tags().isEmpty(), quest.id() + " has no grouping tags");
            helper.assertFalse(quest.objectives().isEmpty(), quest.id() + " has no objectives");
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void compiledV1CatalogMatchesParsedBuiltIns(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<QuestDefinition> quests = quests(helper);
        Map<ResourceLocation, CompiledQuest> compiledById = VillagerQuestResources.compiledQuests(server).stream()
                .filter(quest -> VillagerRetaliation.MOD_ID.equals(quest.id().getNamespace()))
                .sorted(Comparator.comparing(quest -> quest.id().toString()))
                .collect(Collectors.toMap(
                        CompiledQuest::id,
                        quest -> quest,
                        (first, second) -> first,
                        LinkedHashMap::new));

        helper.assertValueEqual(compiledById.size(), quests.size(), "compiled built-in quest count");
        for (QuestDefinition quest : quests) {
            CompiledQuest compiled = compiledById.get(quest.id());
            helper.assertTrue(compiled != null, "Missing compiled quest " + quest.id());
            assertCompiledQuestMatchesParsed(helper, quest, compiled);
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void schemaLessBuiltInQuestResourcesKeepExplicitV1Ids(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<ResourceLocation, ResourceLocation> authoredIdsByResource = new LinkedHashMap<>();
        server.getResourceManager()
                .listResources("quests", location -> VillagerRetaliation.MOD_ID.equals(location.getNamespace())
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    JsonObject root = readJsonObject(entry.getKey(), entry.getValue());
                    helper.assertFalse(root.has("schema"), entry.getKey() + " is no longer schema-less v1");
                    helper.assertTrue(root.has("id"), entry.getKey() + " no longer declares its stable v1 id");
                    ResourceLocation authoredId = ResourceLocation.tryParse(root.get("id").getAsString());
                    helper.assertTrue(authoredId != null, entry.getKey() + " declares an invalid v1 id");
                    authoredIdsByResource.put(entry.getKey(), authoredId);
                });
        helper.assertValueEqual(authoredIdsByResource.size(), EXPECTED_QUEST_COUNT, "built-in quest resource count");

        Set<ResourceLocation> loadedIds = quests(helper).stream()
                .map(QuestDefinition::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : authoredIdsByResource.entrySet()) {
            helper.assertTrue(
                    loadedIds.contains(entry.getValue()),
                    entry.getKey() + " did not load as explicit v1 id " + entry.getValue());
        }
        helper.assertFalse(
                loadedIds.contains(VillagerRetaliation.id("cartographers_atlas/blank_map_promise")),
                "v1 explicit ids must not be replaced by path-inferred questline ids");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questResourceEnvelopeRoutesVersionedSchemas(GameTestHelper helper) {
        ResourceLocation location = VillagerRetaliation.id("quests/test/envelope.json");

        DatapackDiagnostics.clear();
        JsonObject absentSchema = new JsonObject();
        QuestResourceEnvelope v1 = QuestResourceEnvelope.read(location, absentSchema)
                .orElseThrow(() -> new GameTestAssertException("schema-less quest did not route as v1"));
        helper.assertValueEqual(v1.schemaVersion(), QuestSchemaVersion.V1, "absent schema version");
        helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "schema-less v1 produced diagnostics");

        JsonObject validV2 = new JsonObject();
        validV2.addProperty("schema", QuestSchemaVersion.V2.schemaId());
        QuestResourceEnvelope v2 = QuestResourceEnvelope.read(location, validV2)
                .orElseThrow(() -> new GameTestAssertException("v2 quest schema was not recognized"));
        helper.assertValueEqual(v2.schemaVersion(), QuestSchemaVersion.V2, "v2 schema version");
        helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "valid v2 schema produced diagnostics");

        JsonObject malformed = new JsonObject();
        malformed.add("schema", new JsonObject());
        helper.assertTrue(QuestResourceEnvelope.read(location, malformed).isEmpty(), "malformed schema was not skipped");
        assertRecentDiagnosticContains(helper, "schema must be a string");

        DatapackDiagnostics.clear();
        JsonObject unknown = new JsonObject();
        unknown.addProperty("schema", "example:quest/v9");
        helper.assertTrue(QuestResourceEnvelope.read(location, unknown).isEmpty(), "unknown schema was not skipped");
        assertRecentDiagnosticContains(helper, "unsupported schema \"example:quest/v9\"");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questDefinitionsCoverAuthoredFeatures(GameTestHelper helper) {
        List<QuestDefinition> quests = quests(helper);
        EnumSet<QuestDefinition.ObjectiveType> objectiveTypes = EnumSet.noneOf(QuestDefinition.ObjectiveType.class);
        EnumSet<QuestDefinition.TriggerEvent> triggerEvents = EnumSet.noneOf(QuestDefinition.TriggerEvent.class);
        EnumSet<VillagerActionDefinition.Kind> actionKinds = EnumSet.noneOf(VillagerActionDefinition.Kind.class);
        EnumSet<QuestDefinition.AbandonmentMode> abandonmentModes = EnumSet.noneOf(QuestDefinition.AbandonmentMode.class);
        int targeted = 0;
        int parented = 0;
        int staged = 0;
        int repeatable = 0;
        int oneShot = 0;
        int positiveGossip = 0;
        int negativeGossip = 0;

        for (QuestDefinition quest : quests) {
            QuestDefinition.Rewards rewards = quest.rewards();
            helper.assertTrue(rewards.experience() > 0, quest.id() + " has no XP reward");
            helper.assertTrue(rewards.reputation() > 0, quest.id() + " has no reputation reward");
            helper.assertTrue(rewards.gossipReputation() != 0, quest.id() + " has no gossip reward");
            helper.assertTrue(rewards.lootTable() != null, quest.id() + " has no loot-table reward");
            helper.assertTrue(rewards.memoryEvent() != null, quest.id() + " has no memory-event reward");
            assertResourceExists(helper, quest.id(), rewards.lootTable(), "loot_table");
            if (rewards.gossipReputation() > 0) {
                positiveGossip++;
            } else {
                negativeGossip++;
            }

            if (quest.target().hasStructureTarget()) {
                targeted++;
                helper.assertTrue(quest.target().searchRadius() >= quest.target().discoveryRadius(),
                        quest.id() + " target search radius is smaller than discovery radius");
            }
            if (quest.parent() != null) {
                parented++;
            }
            if (!quest.stages().isEmpty()) {
                staged++;
            }
            if (quest.rules().repeatable()) {
                repeatable++;
            } else {
                oneShot++;
            }
            abandonmentModes.add(quest.rules().abandonment());

            for (QuestDefinition.Objective objective : quest.objectives()) {
                objectiveTypes.add(objective.type());
                helper.assertFalse(objective.id().isBlank(), quest.id() + " has a blank objective id");
                helper.assertTrue(objective.count() >= 1, quest.id() + "/" + objective.id() + " has invalid count");
                helper.assertTrue(objective.tracker().hasAnyDisplay(),
                        quest.id() + "/" + objective.id() + " has no tracker display");
                assertObjectiveShape(helper, quest.id(), objective);
            }

            for (QuestDefinition.Trigger trigger : quest.triggers()) {
                triggerEvents.add(trigger.event());
                helper.assertFalse(trigger.actions().isEmpty(), quest.id() + "/" + trigger.id() + " trigger has no actions");
                for (VillagerActionDefinition action : trigger.actions()) {
                    actionKinds.add(action.kind());
                    if (action.kind() == VillagerActionDefinition.Kind.FORCED_DIALOGUE) {
                        assertForcedDialogueExists(helper, quest.id(), action.forcedDialogue());
                    }
                }
            }
        }

        assertContainsAll(helper, objectiveTypes, Set.of(
                QuestDefinition.ObjectiveType.ITEM_CHECK,
                QuestDefinition.ObjectiveType.MOB_KILL,
                QuestDefinition.ObjectiveType.MEMORY_EVENT,
                QuestDefinition.ObjectiveType.CHOICE), "objective types");
        assertContainsAll(helper, triggerEvents, Set.of(
                QuestDefinition.TriggerEvent.STARTED,
                QuestDefinition.TriggerEvent.PROXIMITY,
                QuestDefinition.TriggerEvent.COMPLETED), "trigger events");
        assertContainsAll(helper, actionKinds, Set.of(
                VillagerActionDefinition.Kind.SET_TAG,
                VillagerActionDefinition.Kind.SET_VARIABLE,
                VillagerActionDefinition.Kind.NOTIFICATION,
                VillagerActionDefinition.Kind.TRACKER), "trigger action kinds");
        assertContainsAll(helper, abandonmentModes, Set.of(
                QuestDefinition.AbandonmentMode.ALLOW_REPICKUP,
                QuestDefinition.AbandonmentMode.COOLDOWN,
                QuestDefinition.AbandonmentMode.REMOVE_FOREVER), "abandonment modes");
        helper.assertTrue(targeted > 0, "No structure-target quests loaded");
        helper.assertTrue(parented > 0, "No parented quests loaded");
        helper.assertTrue(staged > 0, "No staged quests loaded");
        helper.assertTrue(repeatable > 0, "No repeatable quests loaded");
        helper.assertTrue(oneShot > 0, "No one-shot quests loaded");
        helper.assertTrue(positiveGossip > 0, "No positive gossip rewards loaded");
        helper.assertTrue(negativeGossip > 0, "No negative gossip rewards loaded");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void v1QuestAliasesRemainAccepted(GameTestHelper helper) {
        helper.assertValueEqual(
                QuestDefinition.ObjectiveType.bySerializedName("entity_kill"),
                QuestDefinition.ObjectiveType.MOB_KILL,
                "entity_kill objective alias");
        helper.assertValueEqual(
                QuestDefinition.ObjectiveType.bySerializedName("coords"),
                QuestDefinition.ObjectiveType.LOCATION_VISIT,
                "coords objective alias");
        helper.assertValueEqual(
                QuestDefinition.ObjectiveType.bySerializedName("quest_stage"),
                QuestDefinition.ObjectiveType.FACT,
                "quest_stage objective alias");
        helper.assertValueEqual(
                QuestDefinition.TriggerEvent.bySerializedName("stage_set"),
                QuestDefinition.TriggerEvent.STAGE_CHANGED,
                "stage_set trigger alias");
        helper.assertValueEqual(
                QuestDefinition.CompletionScope.bySerializedName("global"),
                QuestDefinition.CompletionScope.WORLD,
                "global completion scope alias");
        helper.assertValueEqual(
                QuestDefinition.BranchLockEvent.bySerializedName("accepted"),
                QuestDefinition.BranchLockEvent.STARTED,
                "accepted branch lock alias");
        helper.assertValueEqual(
                VillagerActionDefinition.Kind.bySerializedName("set_stage"),
                VillagerActionDefinition.Kind.SET_VARIABLE,
                "set_stage action alias");
        helper.assertValueEqual(
                VillagerActionDefinition.Kind.bySerializedName("notify"),
                VillagerActionDefinition.Kind.NOTIFICATION,
                "notify action alias");
        helper.assertValueEqual(
                VillagerActionDefinition.QuestAction.bySerializedName("claim"),
                VillagerActionDefinition.QuestAction.TURN_IN,
                "claim quest-action alias");
        helper.assertValueEqual(
                QuestFactScope.bySerializedName("quest_giver", QuestFactScope.PLAYER),
                QuestFactScope.VILLAGER,
                "quest_giver fact scope alias");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void actionRegistryNormalizesAliasesAndDryRuns(GameTestHelper helper) {
        helper.assertValueEqual(VillagerActionRegistry.canonicalTypeId("notify"), "notification", "notify action alias");
        helper.assertValueEqual(VillagerActionRegistry.canonicalTypeId("quest_action"), "quest", "quest_action alias");
        helper.assertValueEqual(
                VillagerActionDefinition.Kind.bySerializedName("set_stage"),
                VillagerActionDefinition.Kind.SET_VARIABLE,
                "set_stage registry alias");
        helper.assertTrue(
                VillagerActionRegistry.descriptors().stream()
                        .anyMatch(descriptor -> descriptor.id().equals("set_variable")
                                && descriptor.aliases().contains("quest_stage")),
                "set_variable descriptor did not expose quest_stage alias");

        VillagerActionDefinition notification = new VillagerActionDefinition(
                VillagerActionDefinition.Kind.NOTIFICATION,
                null,
                VillagerActionDefinition.QuestAction.NONE,
                0,
                null,
                null,
                "quest.test",
                "Test",
                "",
                false,
                QuestFactScope.PLAYER,
                null,
                "",
                "",
                Map.of());
        ActionResult dryRun = VillagerActionRegistry.dryRun(null, notification, Map.of());
        helper.assertValueEqual(dryRun.status(), ActionStatus.SKIPPED, "dry run status");
        helper.assertValueEqual(dryRun.message(), "dry run", "dry run message");
        helper.assertFalse(dryRun.legacyResult().ran(), "dry run executed legacy result");
        helper.assertTrue(
                dryRun.capabilities().contains(ActionCapability.PROVIDER_LIVE),
                "notification provider live capability");

        ActionResult missingContext = VillagerActionRegistry.execute(null, notification, Map.of());
        helper.assertValueEqual(missingContext.status(), ActionStatus.FAILED, "missing context status");
        helper.assertValueEqual(
                missingContext.message(),
                "live dialogue context unavailable",
                "missing context message");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void objectiveRegistryNormalizesStaticObjectiveTypes(GameTestHelper helper) {
        helper.assertValueEqual(QuestObjectiveRegistry.canonicalTypeId("coords"), "location_visit", "coords objective alias");
        helper.assertValueEqual(
                QuestDefinition.ObjectiveType.bySerializedName("coords"),
                QuestDefinition.ObjectiveType.LOCATION_VISIT,
                "coords registry objective type");
        helper.assertTrue(
                QuestObjectiveRegistry.descriptors().stream()
                        .anyMatch(descriptor -> descriptor.id().equals("location_visit")
                                && descriptor.aliases().contains("region_visit")),
                "location_visit descriptor did not expose region_visit alias");

        QuestDefinition.Objective itemObjective = registryObjective(
                QuestDefinition.ObjectiveType.ITEM_CHECK,
                null,
                null,
                ResourceLocation.fromNamespaceAndPath("minecraft", "emerald"),
                3,
                true);
        helper.assertTrue(
                QuestObjectiveRegistry.requirements(itemObjective).contains(QuestObjectiveRequirement.INVENTORY),
                "item objective did not advertise inventory requirement");
        helper.assertValueEqual(QuestObjectiveRegistry.trackerStepKey(itemObjective), "proof", "item tracker step hook");
        helper.assertTrue(QuestObjectiveRegistry.requiresItemHandIn(itemObjective), "consume item objective hand-in hook");

        QuestObjectiveResult result = QuestObjectiveRegistry.evaluate(
                        new QuestObjectiveEvaluationContext(
                                null,
                                null,
                                helper.getLevel(),
                                null,
                                null,
                                objective -> 3),
                        itemObjective)
                .orElseThrow(() -> new GameTestAssertException("item objective registry did not evaluate"));
        helper.assertTrue(result.complete(), "item objective registry did not complete at required count");
        helper.assertValueEqual(result.progress(), 1.0F, "item objective registry progress");

        QuestDefinition.Objective invalidItem = registryObjective(
                QuestDefinition.ObjectiveType.ITEM_CHECK,
                null,
                null,
                null,
                1,
                true);
        helper.assertValueEqual(
                QuestObjectiveRegistry.validationError(invalidItem).orElse(""),
                "item_check objective must define item.",
                "item objective validation message");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void conditionRegistryNormalizesAliasesAndCapabilities(GameTestHelper helper) {
        helper.assertValueEqual(DialogueCondition.canonicalTypeId("all_of"), "all", "all_of condition alias");
        helper.assertValueEqual(DialogueCondition.canonicalTypeId("quest_stage"), "quest_fact", "quest_stage condition alias");
        helper.assertTrue(
                DialogueCondition.descriptors().stream()
                        .anyMatch(descriptor -> descriptor.id().equals("quest_fact")
                                && descriptor.aliases().contains("stage")),
                "quest_fact descriptor did not expose legacy stage alias");

        JsonObject root = new JsonObject();
        JsonArray conditions = new JsonArray();
        JsonObject all = new JsonObject();
        all.addProperty("type", "all_of");
        JsonArray children = new JsonArray();
        JsonObject stage = new JsonObject();
        stage.addProperty("type", "quest_stage");
        stage.addProperty("stage", "started");
        children.add(stage);
        all.add("conditions", children);
        conditions.add(all);
        root.add("conditions", conditions);

        List<DialogueCondition> parsed = DialogueCondition.readList(
                VillagerRetaliation.id("test/condition_registry"),
                "condition registry",
                root,
                VillagerRetaliation.id("choose_the_horizon"));
        helper.assertValueEqual(parsed.size(), 1, "parsed registry condition count");
        DialogueCondition condition = parsed.getFirst();
        helper.assertValueEqual(DialogueCondition.canonicalTypeId(condition), "all", "parsed canonical condition id");
        helper.assertTrue(
                DialogueCondition.capabilities(condition).contains(DialogueCondition.ConditionCapability.WORLD_KNOWN),
                "compound condition did not inherit child capabilities");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void conditionTraceReportsFirstUnknownCondition(GameTestHelper helper) {
        DialogueCondition condition = new DialogueCondition.QuestFact(
                QuestFactScope.QUEST,
                VillagerRetaliation.id("choose_the_horizon"),
                Set.of(),
                "stage",
                Set.of("started"),
                null,
                null);

        DialogueCondition.ConditionEvaluationTrace trace = DialogueCondition.trace(null, condition);
        helper.assertValueEqual(
                trace.outcome(),
                DialogueCondition.ConditionOutcome.UNKNOWN,
                "trace outcome without live context");
        DialogueCondition.ConditionEvaluationTrace failed = trace.firstUnmatched()
                .orElseThrow(() -> new GameTestAssertException("trace did not report first unmatched condition"));
        helper.assertValueEqual(failed.canonicalTypeId(), "quest_fact", "first unmatched condition type");
        helper.assertValueEqual(failed.message(), "live context unavailable", "first unmatched condition message");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questTrackerPresenterFormatsEntryPayload(GameTestHelper helper) {
        QuestDefinition quest = quest(helper, VillagerRetaliation.id("tales_of_a_lost_civilization"));
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), Level.OVERWORLD, new BlockPos(100, 64, 100), 42L);
        progress.setIssuer(
                UUID.randomUUID(),
                "Lore Keeper",
                "minecraft:cartographer",
                3,
                Level.OVERWORLD,
                new BlockPos(4, 65, -2),
                "village:overworld:0:0");

        List<QuestTrackerSyncPayload.QuestItem> items = QuestTrackerPresenter.questItems(
                quest,
                progress,
                ResourceLocation::toString);
        helper.assertTrue(
                items.stream().anyMatch(item -> item.itemId().equals("minecraft:echo_shard") && item.count() == 1),
                "presenter did not include proof item");

        QuestDefinition.Step step = new QuestDefinition.Step(
                "Recover {proof_item}",
                "",
                true,
                0.5F,
                Map.of("hint", "Proof: {proof_item}"));
        Map<String, String> replacements = Map.of(
                "quest", quest.title(),
                "proof_item", "Echo Shard",
                "issuer", "Lore Keeper");
        QuestTrackerSyncPayload.Entry entry = QuestTrackerPresenter.entry(new QuestTrackerPresenter.EntryInput(
                null,
                quest,
                new QuestDefinition.SelectedText("{quest}", ""),
                step,
                replacements,
                "Active",
                "Lore Keeper",
                "minecraft:overworld 4,65,-2",
                items,
                0.5F,
                true,
                progress.state()));

        helper.assertValueEqual(entry.questId(), quest.id().toString(), "presenter quest id");
        helper.assertValueEqual(entry.title(), quest.title(), "presenter title");
        helper.assertValueEqual(entry.objective(), "Recover Echo Shard", "presenter objective text");
        helper.assertValueEqual(entry.metadata(), "Active | Issued by Lore Keeper | Proof: Echo Shard", "presenter metadata");
        helper.assertValueEqual(entry.progress(), 0.5F, "presenter progress");
        helper.assertTrue(entry.showProgress(), "presenter show progress");
        helper.assertValueEqual(entry.state(), "active", "presenter state");
        helper.assertTrue(
                QuestTrackerPresenter.syncSignature(List.of(entry), quest.id()).contains(entry.questId()),
                "presenter signature omitted quest id");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questDebugFormatterOutputsStableInspectLines(GameTestHelper helper) {
        QuestDefinition quest = quest(helper, VillagerRetaliation.id("choose_the_horizon"));
        VillagerQuestSavedData.QuestProgress parentProgress = new VillagerQuestSavedData.QuestProgress();
        parentProgress.complete(99L, false);
        String parentId = VillagerRetaliation.id("atlas_parent").toString();

        helper.assertValueEqual(
                QuestDebugFormatter.header(quest),
                "Quest " + quest.id() + " | " + quest.title(),
                "debug formatter header");
        helper.assertTrue(
                QuestDebugFormatter.identityLine(quest, QuestDebugFormatter.parentState(VillagerRetaliation.id("atlas_parent"), parentProgress))
                        .contains("parent=" + parentId + "(completed,completed=true)"),
                "debug formatter parent state");
        helper.assertValueEqual(
                QuestDebugFormatter.progressLine(new QuestDebugFormatter.ProgressLine(
                        true,
                        VillagerQuestSavedData.QuestState.ACTIVE,
                        "started",
                        1,
                        0,
                        0,
                        "false",
                        "met",
                        false)),
                "progress saved=true state=active stage=started starts=1 completions=0 abandons=0 ready=false active_conditions=met branch_locked=false",
                "debug formatter progress line");

        QuestDefinition.Objective objective = quest.objectives().getFirst();
        String objectiveLine = QuestDebugFormatter.objectiveLine(
                quest.id(),
                objective,
                new QuestDebugFormatter.ObjectiveLineState(false, 0, 0, 0, "quest:choose_the_horizon", ""));
        helper.assertTrue(objectiveLine.contains("objective " + objective.id()), "debug formatter objective id");
        helper.assertTrue(objectiveLine.contains("type=choice"), "debug formatter objective type");
        helper.assertTrue(objectiveLine.contains("choices=[coast, dark_roof]"), "debug formatter choices");
        helper.assertValueEqual(
                QuestDebugFormatter.inventoryCacheLine(new QuestDebugFormatter.InventoryCacheLine(
                        true,
                        true,
                        7,
                        2,
                        1,
                        true,
                        3,
                        1,
                        4L,
                        36,
                        5,
                        2,
                        1,
                        9)),
                "inventory_cache state=warm change_count=7 item_objectives=2 exact_item_objectives=1 proof_item=true simple_item_entries=3 exact_objective_entries=1 rebuilt_age_ticks=4 simple_scan_slots=36 simple_lookups=5 exact_lookups=2 exact_cache_misses=1 exact_scan_slots=9",
                "debug formatter inventory cache line");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void stagedBranchQuestKeepsLegacyNextAndSetStageSemantics(GameTestHelper helper) {
        QuestDefinition quest = quest(helper, VillagerRetaliation.id("choose_the_horizon"));
        helper.assertValueEqual(quest.objectives().size(), 1, "choose_the_horizon objective count");
        QuestDefinition.Objective objective = quest.objectives().getFirst();
        helper.assertValueEqual(objective.type(), QuestDefinition.ObjectiveType.CHOICE, "branch objective type");
        helper.assertValueEqual(objective.factScope(), QuestFactScope.QUEST, "branch objective fact scope");
        helper.assertValueEqual(objective.factKey(), "choice", "branch objective fact key");
        helper.assertTrue(objective.factValues().containsAll(Set.of("coast", "dark_roof")),
                "branch objective choices changed");

        helper.assertValueEqual(quest.stages().size(), 3, "choose_the_horizon stage count");
        QuestDefinition.Stage started = quest.stages().get("started");
        helper.assertTrue(started != null, "choose_the_horizon has no started stage");
        helper.assertValueEqual(started.branches().size(), 2, "started branch count");
        assertRouteBranch(helper, started, "coast", "coast_chosen", VillagerRetaliation.id("atlas_coast_route"));
        assertRouteBranch(helper, started, "dark_roof", "dark_roof_chosen", VillagerRetaliation.id("atlas_dark_roof_route"));
        helper.assertTrue(quest.stages().containsKey("coast_chosen"), "coast_chosen stage missing");
        helper.assertTrue(quest.stages().containsKey("dark_roof_chosen"), "dark_roof_chosen stage missing");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questDialogueTreesCoverEveryQuest(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (QuestDefinition quest : quests(helper)) {
            DialogueTreeDefinition tree = DialogueTreeResources.tree(server, LOCALE, quest.id())
                    .orElseThrow(() -> new GameTestAssertException(quest.id() + " has no matching quest dialogue tree"));
            assertEntryLifecycle(helper, quest.id(), tree, "offer", VillagerActionDefinition.QuestAction.START);
            assertEntryLifecycle(helper, quest.id(), tree, "reminder", VillagerActionDefinition.QuestAction.REMIND);
            assertEntryLifecycle(helper, quest.id(), tree, "turn_in", VillagerActionDefinition.QuestAction.TURN_IN);
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questResourceIndexesFindObjectiveAndTriggerFeatures(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertValueEqual(
                VillagerQuestResources.questIdsWithObjective(server, QuestDefinition.ObjectiveType.MOB_KILL).size(),
                3,
                "mob-kill quest index size");
        helper.assertTrue(
                VillagerQuestResources.memoryEventQuestIds(server, VillagerRetaliation.id("player_defended_village"))
                        .contains(VillagerRetaliation.id("standing_watch")),
                "memory-event quest index is missing standing_watch");
        helper.assertTrue(
                VillagerQuestResources.hasQuestTrigger(
                        server,
                        VillagerRetaliation.id("tales_of_a_lost_civilization"),
                        QuestDefinition.TriggerEvent.PROXIMITY),
                "proximity trigger index is missing tales_of_a_lost_civilization");
        helper.assertTrue(
                VillagerQuestResources.hasQuestTrigger(
                        server,
                        VillagerRetaliation.id("end_city_margin"),
                        QuestDefinition.TriggerEvent.COMPLETED),
                "completed trigger index is missing end_city_margin");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void deferredQuestTriggersAvoidLiveIssuerActions(GameTestHelper helper) {
        for (QuestDefinition quest : quests(helper)) {
            for (QuestDefinition.Trigger trigger : quest.triggers()) {
                if (!DEFERRED_TRIGGER_EVENTS.contains(trigger.event())) {
                    continue;
                }
                for (VillagerActionDefinition action : trigger.actions()) {
                    helper.assertFalse(
                            LIVE_CONTEXT_ACTION_KINDS.contains(action.kind()),
                            quest.id() + "/" + trigger.id() + " uses " + action.kind()
                                    + " from " + trigger.event() + ", which may wait on an unloaded quest issuer");
                }
            }
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questSavedDataRoundTripsV1ProgressFields(GameTestHelper helper) {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID villagerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ResourceLocation questId = VillagerRetaliation.id("choose_the_horizon");
        BlockPos issuerPos = new BlockPos(11, 65, 21);
        BlockPos targetPos = new BlockPos(120, 70, -30);

        VillagerQuestSavedData data = new VillagerQuestSavedData();
        VillagerQuestSavedData.QuestProgress progress = data.getOrCreate(playerId, questId);
        progress.start(villagerId, Level.OVERWORLD, targetPos, 1234L);
        progress.setIssuer(
                villagerId,
                "Map Keeper",
                "minecraft:cartographer",
                3,
                Level.OVERWORLD,
                issuerPos,
                "village:overworld:0:0");
        progress.setTarget(villagerId, Level.OVERWORLD, targetPos, "route_target");
        progress.markVisitedTarget();
        progress.markHasProof();
        progress.setCurrentStage("coast_chosen");
        progress.markObjectiveComplete("choose_route");
        progress.addObjectiveCounter("choose_route", 2);
        progress.markTriggerUsed("completed_0", 4321L);
        data.setTrackedQuest(playerId, questId);

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VillagerQuestSavedData loaded = VillagerQuestSavedData.load(saved, helper.getLevel().registryAccess());
        VillagerQuestSavedData.QuestProgress loadedProgress = loaded.get(playerId, questId);
        helper.assertTrue(loadedProgress != null, "quest progress did not load");
        helper.assertValueEqual(loadedProgress.state(), VillagerQuestSavedData.QuestState.ACTIVE, "saved state");
        helper.assertValueEqual(loadedProgress.startedVillagerId(), villagerId, "started villager id");
        helper.assertValueEqual(loadedProgress.startedGameTime(), 1234L, "started game time");
        helper.assertTrue(loadedProgress.visitedTarget(), "visited target flag");
        helper.assertTrue(loadedProgress.hasProof(), "proof flag");
        helper.assertValueEqual(loadedProgress.issuerName(), "Map Keeper", "issuer name");
        helper.assertValueEqual(loadedProgress.issuerProfession(), "minecraft:cartographer", "issuer profession");
        helper.assertValueEqual(loadedProgress.issuerLevel(), 3, "issuer level");
        helper.assertValueEqual(loadedProgress.issuerDimension(), Level.OVERWORLD, "issuer dimension");
        helper.assertValueEqual(loadedProgress.issuerPos(), issuerPos, "issuer position");
        helper.assertValueEqual(loadedProgress.issuerVillageKey(), "village:overworld:0:0", "issuer village key");
        helper.assertValueEqual(loadedProgress.targetDimension(), Level.OVERWORLD, "target dimension");
        helper.assertValueEqual(loadedProgress.targetPos(), targetPos, "target position");
        helper.assertValueEqual(loadedProgress.targetObjectiveId(), "route_target", "target objective");
        helper.assertValueEqual(loadedProgress.currentStage(), "coast_chosen", "current stage");
        helper.assertTrue(loadedProgress.objectiveComplete("choose_route"), "completed objective");
        helper.assertValueEqual(loadedProgress.objectiveCounter("choose_route"), 2, "objective counter");
        helper.assertValueEqual(loadedProgress.lastTriggerGameTime("completed_0"), 4321L, "trigger time");
        helper.assertValueEqual(loaded.getTrackedQuest(playerId), questId, "tracked quest");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questScopeKeysSerializeLegacyStrings(GameTestHelper helper) {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000006");
        UUID villagerId = UUID.fromString("00000000-0000-0000-0000-000000000007");
        ResourceLocation questId = VillagerRetaliation.id("choose_the_horizon");

        helper.assertValueEqual(QuestScopeKey.player(playerId).asString(), "player:" + playerId, "player scope");
        helper.assertValueEqual(
                QuestScopeKey.playerWorld(playerId).asString(),
                "player:" + playerId,
                "player-world scope");
        helper.assertValueEqual(QuestScopeKey.WORLD.asString(), "world", "world scope");
        helper.assertValueEqual(
                QuestScopeKey.quest(playerId, questId).asString(),
                "quest:" + playerId + ":" + questId,
                "quest scope");
        helper.assertValueEqual(
                QuestScopeKey.villager(villagerId).asString(),
                "villager:" + villagerId,
                "villager scope");
        helper.assertValueEqual(
                QuestScopeKey.village("village:minecraft:overworld:4,65,-2").asString(),
                "village:minecraft:overworld:4,65,-2",
                "village scope");
        helper.assertValueEqual(
                QuestScopeKey.parse("player:" + playerId).orElseThrow().asString(),
                "player:" + playerId,
                "parsed player");
        helper.assertValueEqual(
                QuestScopeKey.parse("quest:" + playerId + ":" + questId).orElseThrow().asString(),
                "quest:" + playerId + ":" + questId,
                "parsed quest");
        helper.assertValueEqual(
                QuestScopeKey.parse("world").orElseThrow().kind(),
                QuestScopeKey.Kind.WORLD,
                "parsed world kind");
        helper.assertTrue(QuestScopeKey.parse("player:not-a-uuid").isEmpty(), "bad player parsed");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questFactsRoundTripTypedAndLegacyScopeKeys(GameTestHelper helper) {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000008");
        ResourceLocation questId = VillagerRetaliation.id("choose_the_horizon");
        ResourceLocation tag = VillagerRetaliation.id("scope_key_test");
        QuestScopeKey playerKey = QuestScopeKey.player(playerId);
        QuestScopeKey questKey = QuestScopeKey.quest(playerId, questId);

        VillagerQuestFacts facts = new VillagerQuestFacts();
        facts.setTag(playerKey, tag);
        facts.setVariable(questKey, "stage", "started");
        facts.addCounter(QuestScopeKey.WORLD, "completion:" + questId, 2);

        CompoundTag saved = facts.save(new CompoundTag(), helper.getLevel().registryAccess());
        ListTag entries = saved.getList("Entries", Tag.TAG_COMPOUND);
        Set<String> scopes = new LinkedHashSet<>();
        for (Tag rawEntry : entries) {
            if (rawEntry instanceof CompoundTag entry) {
                scopes.add(entry.getString("Scope"));
            }
        }
        helper.assertTrue(scopes.contains(playerKey.asString()), "saved player typed scope");
        helper.assertTrue(scopes.contains(questKey.asString()), "saved quest typed scope");
        helper.assertTrue(scopes.contains("world"), "saved world typed scope");

        VillagerQuestFacts loaded = VillagerQuestFacts.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.hasTag(playerKey, tag), "typed player tag did not load");
        helper.assertValueEqual(loaded.variable(questKey, "stage").orElse(""), "started", "typed quest variable");
        helper.assertValueEqual(
                loaded.counter(QuestScopeKey.WORLD, "completion:" + questId),
                2,
                "typed world counter");

        CompoundTag legacy = new CompoundTag();
        ListTag legacyEntries = new ListTag();
        CompoundTag legacyEntry = new CompoundTag();
        legacyEntry.putString("Scope", "player:" + playerId);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(tag.toString()));
        legacyEntry.put("Tags", tags);
        legacyEntries.add(legacyEntry);
        legacy.put("Entries", legacyEntries);
        VillagerQuestFacts legacyLoaded = VillagerQuestFacts.load(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(legacyLoaded.hasTag(playerKey, tag), "legacy player tag did not load");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questExecutionContextUsesSavedVillagerProviderSnapshot(GameTestHelper helper) {
        QuestDefinition quest = quest(helper, VillagerRetaliation.id("tales_of_a_lost_civilization"));
        UUID providerId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        BlockPos providerPos = new BlockPos(4, 65, -2);
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(providerId, Level.OVERWORLD, new BlockPos(10, 64, 10), 5L);
        progress.setIssuer(
                providerId,
                "Lore Keeper",
                "minecraft:cartographer",
                3,
                Level.OVERWORLD,
                providerPos,
                "village:minecraft:overworld:4,65,-2");

        QuestProviderBinding binding = VillagerQuestProviderType.INSTANCE
                .bindingFromProgress(helper.getLevel(), progress)
                .orElseThrow(() -> new GameTestAssertException("saved provider snapshot was not restored"));
        QuestExecutionContext execution = QuestExecutionContext.fromSavedProvider(
                helper.getLevel(),
                null,
                quest,
                "snapshot_test",
                VillagerQuestProviderType.INSTANCE,
                binding);

        helper.assertValueEqual(binding.providerType(), VillagerQuestProviderType.ID, "provider type");
        helper.assertValueEqual(binding.providerId(), providerId, "provider id");
        helper.assertValueEqual(binding.displayName(), "Lore Keeper", "provider display name");
        helper.assertValueEqual(binding.professionId(), ResourceLocation.tryParse("minecraft:cartographer"), "provider profession");
        helper.assertValueEqual(binding.level(), 3, "provider level");
        helper.assertValueEqual(binding.dimension(), Level.OVERWORLD, "provider dimension");
        helper.assertValueEqual(binding.pos(), providerPos, "provider position");
        helper.assertValueEqual(binding.villageKey(), "village:minecraft:overworld:4,65,-2", "provider village key");
        helper.assertFalse(binding.live(), "saved provider marked live");
        helper.assertTrue(execution.hasCapability(QuestExecutionContext.SAVED_PROVIDER), "saved provider capability");
        helper.assertTrue(execution.liveProviderEntity().isEmpty(), "saved provider has live entity");
        helper.assertTrue(execution.dialogueContext().isEmpty(), "saved provider has dialogue context");
        helper.assertTrue(
                VillagerQuestProviderType.INSTANCE.matchesIssuerLock(execution, quest, progress),
                "saved provider did not satisfy issuer lock");

        QuestProviderBinding otherBinding = new QuestProviderBinding(
                VillagerQuestProviderType.ID,
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "Other Keeper",
                ResourceLocation.tryParse("minecraft:cartographer"),
                3,
                Level.OVERWORLD,
                providerPos,
                "village:minecraft:overworld:4,65,-2",
                Map.of(),
                false);
        QuestExecutionContext otherExecution = QuestExecutionContext.fromSavedProvider(
                helper.getLevel(),
                null,
                quest,
                "snapshot_test",
                VillagerQuestProviderType.INSTANCE,
                otherBinding);
        helper.assertFalse(
                VillagerQuestProviderType.INSTANCE.matchesIssuerLock(otherExecution, quest, progress),
                "different saved provider satisfied issuer lock");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fakeQuestProviderTypeProvesContextIsNotVillagerSpecific(GameTestHelper helper) {
        QuestDefinition quest = quest(helper, VillagerRetaliation.id("choose_the_horizon"));
        ResourceLocation fakeProviderId = VillagerRetaliation.id("fake_provider_type");
        QuestProviderType fakeProvider = new QuestProviderType() {
            @Override
            public ResourceLocation id() {
                return fakeProviderId;
            }

            @Override
            public boolean matchesOffer(QuestExecutionContext context, QuestDefinition definition) {
                return context != null
                        && context.hasCapability(FAKE_PROVIDER_CAPABILITY)
                        && context.providerBinding()
                                .map(binding -> fakeProviderId.equals(binding.providerType()))
                                .orElse(false);
            }

            @Override
            public boolean matchesIssuerLock(
                    QuestExecutionContext context,
                    QuestDefinition definition,
                    VillagerQuestSavedData.QuestProgress progress) {
                return context != null
                        && context.providerBinding()
                                .map(binding -> fakeProviderId.equals(binding.providerType()))
                                .orElse(false);
            }
        };
        QuestProviderBinding binding = new QuestProviderBinding(
                fakeProvider.id(),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                "Notice Board",
                VillagerRetaliation.id("notice_board"),
                0,
                Level.OVERWORLD,
                new BlockPos(0, 64, 0),
                "village:test",
                Map.of(),
                false);
        QuestExecutionContext context = new QuestExecutionContext(
                helper.getLevel(),
                null,
                quest,
                "fake_provider_test",
                fakeProvider,
                binding,
                null,
                binding.villageKey(),
                Set.of(FAKE_PROVIDER_CAPABILITY),
                null);

        helper.assertTrue(context.liveProviderEntity().isEmpty(), "fake provider has live entity");
        helper.assertTrue(context.dialogueContext().isEmpty(), "fake provider has dialogue context");
        helper.assertTrue(context.hasCapability(FAKE_PROVIDER_CAPABILITY), "fake provider capability");
        helper.assertTrue(fakeProvider.matchesOffer(context, quest), "fake provider did not match offer");
        helper.assertTrue(
                fakeProvider.matchesIssuerLock(context, quest, new VillagerQuestSavedData.QuestProgress()),
                "fake provider did not match issuer lock");

        helper.succeed();
    }

    private static List<QuestDefinition> quests(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        VillagerQuestResources.clearCache();
        DialogueTreeResources.clearCache();
        ForcedDialogueResources.clearCache();
        return VillagerQuestResources.quests(server).stream()
                .filter(quest -> VillagerRetaliation.MOD_ID.equals(quest.id().getNamespace()))
                .sorted(Comparator.comparing(quest -> quest.id().toString()))
                .toList();
    }

    private static QuestDefinition quest(GameTestHelper helper, ResourceLocation questId) {
        return VillagerQuestResources.quest(helper.getLevel().getServer(), questId)
                .orElseThrow(() -> new GameTestAssertException("Missing quest " + questId));
    }

    private static QuestDefinition.Objective registryObjective(
            QuestDefinition.ObjectiveType type,
            ResourceLocation structure,
            BlockPos location,
            ResourceLocation item,
            int count,
            boolean consume) {
        return new QuestDefinition.Objective(
                "registry_static",
                type,
                false,
                structure,
                Level.OVERWORLD,
                location,
                8,
                List.of(),
                16,
                8,
                item,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                null,
                null,
                QuestFactScope.PLAYER,
                null,
                Set.of(),
                "",
                Set.of(),
                null,
                null,
                count,
                consume,
                QuestDefinition.ItemRequirements.EMPTY,
                List.of(),
                QuestDefinition.ObjectiveTracker.EMPTY);
    }

    private static void assertCompiledQuestMatchesParsed(
            GameTestHelper helper,
            QuestDefinition quest,
            CompiledQuest compiled) {
        helper.assertValueEqual(compiled.id(), quest.id(), quest.id() + " compiled id");
        helper.assertValueEqual(compiled.schemaVersion(), QuestSchemaVersion.V1, quest.id() + " schema");
        helper.assertValueEqual(compiled.asQuestDefinition(), quest, quest.id() + " compatibility definition");
        helper.assertValueEqual(compiled.source().jsonPointer(), "", quest.id() + " source pointer");
        helper.assertValueEqual(compiled.source().resource().getNamespace(), VillagerRetaliation.MOD_ID,
                quest.id() + " source namespace");

        helper.assertValueEqual(compiled.metadata().title(), quest.title(), quest.id() + " title");
        helper.assertValueEqual(compiled.metadata().description(), quest.description(), quest.id() + " description");
        helper.assertValueEqual(compiled.metadata().titleKey(), quest.titleKey(), quest.id() + " title key");
        helper.assertValueEqual(compiled.metadata().descriptionKey(), quest.descriptionKey(),
                quest.id() + " description key");
        helper.assertValueEqual(compiled.metadata().questline(), quest.questline(), quest.id() + " questline");
        helper.assertValueEqual(compiled.metadata().tags(), quest.tags(), quest.id() + " tags");
        helper.assertTrue(Objects.equals(compiled.metadata().parent(), quest.parent()), quest.id() + " parent");
        helper.assertValueEqual(compiled.metadata().dialogue(), quest.metadata(), quest.id() + " dialogue metadata");
        helper.assertValueEqual(compiled.provider().offer(), quest.offer(), quest.id() + " provider");
        helper.assertValueEqual(compiled.target(), quest.target(), quest.id() + " target");
        helper.assertValueEqual(compiled.rules(), quest.rules(), quest.id() + " rules");
        helper.assertValueEqual(compiled.rewards().definition(), quest.rewards(), quest.id() + " rewards");
        helper.assertValueEqual(compiled.ui().tracker(), quest.tracker(), quest.id() + " tracker");
        helper.assertValueEqual(compiled.ui().dialogue(), quest.dialogue(), quest.id() + " dialogue");
        helper.assertValueEqual(compiled.ui().links(), quest.links(), quest.id() + " links");

        assertCompiledObjectivesMatchParsed(helper, quest, compiled);
        assertCompiledStagesMatchParsed(helper, quest, compiled);
        assertCompiledTriggersMatchParsed(helper, quest, compiled);
    }

    private static void assertCompiledObjectivesMatchParsed(
            GameTestHelper helper,
            QuestDefinition quest,
            CompiledQuest compiled) {
        List<String> parsedObjectiveIds = quest.objectives().stream()
                .map(QuestDefinition.Objective::id)
                .toList();
        helper.assertValueEqual(
                compiled.objectives().stream().map(CompiledQuestObjective::id).toList(),
                parsedObjectiveIds,
                quest.id() + " objective order");
        helper.assertValueEqual(
                new ArrayList<>(compiled.objectivesById().keySet()),
                parsedObjectiveIds,
                quest.id() + " objective index order");

        for (int index = 0; index < quest.objectives().size(); index++) {
            QuestDefinition.Objective parsed = quest.objectives().get(index);
            CompiledQuestObjective compiledObjective = compiled.objectives().get(index);
            helper.assertValueEqual(compiledObjective.index(), index, quest.id() + "/" + parsed.id() + " objective index");
            helper.assertValueEqual(compiledObjective.definition(), parsed, quest.id() + "/" + parsed.id() + " objective");
            helper.assertTrue(compiledObjective.source().jsonPointer().startsWith("/objectives/"),
                    quest.id() + "/" + parsed.id() + " objective source pointer");
        }
    }

    private static void assertCompiledStagesMatchParsed(
            GameTestHelper helper,
            QuestDefinition quest,
            CompiledQuest compiled) {
        List<String> parsedStageIds = new ArrayList<>(quest.stages().keySet());
        helper.assertValueEqual(
                compiled.stages().stream().map(CompiledQuestStage::id).toList(),
                parsedStageIds,
                quest.id() + " stage order");
        helper.assertValueEqual(
                new ArrayList<>(compiled.stagesById().keySet()),
                parsedStageIds,
                quest.id() + " stage index order");

        int index = 0;
        for (Map.Entry<String, QuestDefinition.Stage> entry : quest.stages().entrySet()) {
            CompiledQuestStage stage = compiled.stages().get(index);
            helper.assertValueEqual(stage.index(), index, quest.id() + "/" + entry.getKey() + " stage index");
            helper.assertValueEqual(stage.definition(), entry.getValue(), quest.id() + "/" + entry.getKey() + " stage");
            helper.assertValueEqual(
                    stage.objectives().stream().map(CompiledQuestObjective::id).toList(),
                    entry.getValue().objectives().stream()
                            .filter(compiled.objectivesById()::containsKey)
                            .toList(),
                    quest.id() + "/" + entry.getKey() + " stage objectives");
            helper.assertTrue(stage.source().jsonPointer().startsWith("/stages/"),
                    quest.id() + "/" + entry.getKey() + " stage source pointer");
            index++;
        }
    }

    private static void assertCompiledTriggersMatchParsed(
            GameTestHelper helper,
            QuestDefinition quest,
            CompiledQuest compiled) {
        List<String> parsedTriggerIds = quest.triggers().stream()
                .map(QuestDefinition.Trigger::id)
                .toList();
        helper.assertValueEqual(
                compiled.triggers().stream().map(CompiledQuestTrigger::id).toList(),
                parsedTriggerIds,
                quest.id() + " trigger order");

        for (int index = 0; index < quest.triggers().size(); index++) {
            QuestDefinition.Trigger parsed = quest.triggers().get(index);
            CompiledQuestTrigger trigger = compiled.triggers().get(index);
            helper.assertValueEqual(trigger.index(), index, quest.id() + "/" + parsed.id() + " trigger index");
            helper.assertValueEqual(trigger.definition(), parsed, quest.id() + "/" + parsed.id() + " trigger");
            helper.assertTrue(
                    compiled.triggersByEvent().getOrDefault(parsed.event(), List.of()).contains(trigger),
                    quest.id() + "/" + parsed.id() + " trigger event index");
            helper.assertTrue(trigger.source().jsonPointer().startsWith("/triggers/"),
                    quest.id() + "/" + parsed.id() + " trigger source pointer");
        }
    }

    private static JsonObject readJsonObject(ResourceLocation location, Resource resource) {
        try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | IllegalStateException exception) {
            throw new GameTestAssertException("Could not read quest resource " + location + ": " + exception.getMessage());
        }
    }

    private static void assertRouteBranch(
            GameTestHelper helper,
            QuestDefinition.Stage stage,
            String branchId,
            String nextStage,
            ResourceLocation routeTag) {
        QuestDefinition.StageBranch branch = stage.branches().stream()
                .filter(candidate -> candidate.id().equals(branchId))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Missing branch " + branchId));
        helper.assertValueEqual(branch.next(), nextStage, branchId + " branch next stage");
        helper.assertValueEqual(branch.actions().size(), 4, branchId + " branch action count");
        assertSetVariableAction(helper, branch.actions().get(0), "choice", branchId, branchId + " choice action");
        assertSetVariableAction(helper, branch.actions().get(1), "stage", nextStage, branchId + " set_stage action");
        VillagerActionDefinition tagAction = branch.actions().get(2);
        helper.assertValueEqual(tagAction.kind(), VillagerActionDefinition.Kind.SET_TAG, branchId + " route tag kind");
        helper.assertValueEqual(tagAction.factScope(), QuestFactScope.PLAYER, branchId + " route tag scope");
        helper.assertValueEqual(tagAction.factTag(), routeTag, branchId + " route tag id");
        VillagerActionDefinition notification = branch.actions().get(3);
        helper.assertValueEqual(notification.kind(), VillagerActionDefinition.Kind.NOTIFICATION, branchId + " notification kind");
        helper.assertValueEqual(notification.notificationTrigger(), "quest.updated", branchId + " notification trigger");
        helper.assertFalse(notification.text().isBlank(), branchId + " notification text");
    }

    private static void assertSetVariableAction(
            GameTestHelper helper,
            VillagerActionDefinition action,
            String key,
            String value,
            String label) {
        helper.assertValueEqual(action.kind(), VillagerActionDefinition.Kind.SET_VARIABLE, label + " kind");
        helper.assertValueEqual(action.factScope(), QuestFactScope.QUEST, label + " scope");
        helper.assertValueEqual(action.factKey(), key, label + " key");
        helper.assertValueEqual(action.factValue(), value, label + " value");
    }

    private static void assertRecentDiagnosticContains(GameTestHelper helper, String expected) {
        helper.assertTrue(
                DatapackDiagnostics.recent().stream().anyMatch(entry -> entry.message().contains(expected)),
                "recent diagnostics did not contain \"" + expected + "\": "
                        + DatapackDiagnostics.recent().stream().map(DatapackDiagnostics.Entry::message).toList());
    }

    private static void assertObjectiveShape(
            GameTestHelper helper,
            ResourceLocation questId,
            QuestDefinition.Objective objective) {
        switch (objective.type()) {
            case ITEM_CHECK -> helper.assertTrue(objective.item() != null,
                    questId + "/" + objective.id() + " item objective has no item");
            case MOB_KILL -> helper.assertFalse(objective.entityTypes().isEmpty() && objective.entityTags().isEmpty(),
                    questId + "/" + objective.id() + " mob-kill objective has no entity selector");
            case MEMORY_EVENT -> helper.assertFalse(objective.memoryTags().isEmpty(),
                    questId + "/" + objective.id() + " memory objective has no memory tag");
            case CHOICE -> helper.assertFalse(objective.factValues().isEmpty() && objective.factKey().isBlank(),
                    questId + "/" + objective.id() + " choice objective has no choices or fact key");
            default -> {
            }
        }
    }

    private static void assertEntryLifecycle(
            GameTestHelper helper,
            ResourceLocation questId,
            DialogueTreeDefinition tree,
            String entryId,
            VillagerActionDefinition.QuestAction action) {
        DialogueTreeDefinition.Entry entry = tree.entry(entryId)
                .orElseThrow(() -> new GameTestAssertException(questId + " dialogue tree has no " + entryId + " entry"));
        helper.assertTrue(tree.node(entry.start()).isPresent(),
                questId + " dialogue tree entry " + entryId + " points to missing node " + entry.start());
        helper.assertTrue(hasReachableQuestAction(tree, entry.start(), action, questId),
                questId + " dialogue tree entry " + entryId + " has no reachable " + action + " action");
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
            if (node.get().actions().stream().anyMatch(action -> isQuestAction(action, expectedAction, questId))) {
                return true;
            }
            for (DialogueTreeDefinition.Response response : node.get().responses()) {
                if (response.actions().stream().anyMatch(action -> isQuestAction(action, expectedAction, questId))) {
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
        return action.kind() == VillagerActionDefinition.Kind.QUEST
                && action.questAction() == expectedAction
                && Objects.equals(action.questId(), questId);
    }

    private static void assertForcedDialogueExists(
            GameTestHelper helper,
            ResourceLocation questId,
            String forcedDialogue) {
        helper.assertFalse(forcedDialogue == null || forcedDialogue.isBlank(),
                questId + " has blank forced dialogue id");
        String normalized = forcedDialogue.trim();
        helper.assertTrue(
                ForcedDialogueResources.selectCandidates(
                        helper.getLevel().getServer(),
                        ForcedDialogueResources.ForcedDialogueTrigger.QUEST,
                        null)
                        .stream()
                        .anyMatch(definition -> definition.id().equals(normalized)
                                || definition.source().toString().equals(normalized)),
                questId + " references missing quest forced dialogue " + forcedDialogue);
    }

    private static void assertResourceExists(
            GameTestHelper helper,
            ResourceLocation questId,
            ResourceLocation id,
            String root) {
        ResourceLocation resource = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(),
                root + "/" + id.getPath() + ".json");
        helper.assertTrue(
                helper.getLevel().getServer().getResourceManager().getResource(resource).isPresent(),
                questId + " references missing resource " + resource);
    }

    private static <T> void assertContainsAll(
            GameTestHelper helper,
            Set<T> actual,
            Set<T> expected,
            String label) {
        Set<T> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        helper.assertTrue(missing.isEmpty(), label + " missing " + missing + "; loaded " + actual);
    }

    @SafeVarargs
    private static <K, V> Map<K, V> orderedMap(Map.Entry<K, V>... entries) {
        Map<K, V> map = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(map);
    }

    private static void configureGameTestStructures() {
        String configured = System.getProperty("villagerretaliation.gameteststructures");
        if (configured != null && !configured.isBlank()) {
            StructureUtils.testStructuresDir = configured;
            return;
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("src/main/gameteststructures"));
        candidates.add(Path.of("../src/main/gameteststructures"));
        candidates.add(Path.of("neoforge/src/main/gameteststructures"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                StructureUtils.testStructuresDir = candidate.toAbsolutePath().normalize().toString();
                return;
            }
        }
    }
}
