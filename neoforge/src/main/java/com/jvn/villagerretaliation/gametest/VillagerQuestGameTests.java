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
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueLine;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCompiler;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.debug.QuestDebugFormatter;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.debug.QuestDebugTraceService;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDebugState;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventTrace;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRequirement;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveResult;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import com.jvn.villagerretaliation.quest.QuestRegistryMetadata;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.tracking.QuestStageReadiness;
import com.jvn.villagerretaliation.quest.QuestTriggerDispatchResult;
import com.jvn.villagerretaliation.quest.QuestTriggerDispatcher;
import com.jvn.villagerretaliation.quest.QuestTriggerIndex;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.tracking.QuestTrackerPresenter;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.VillagerQuestDeathProtectionService;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestObjective;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestStage;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTransition;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.provider.QuestProviderDeathProtection;
import com.jvn.villagerretaliation.quest.provider.QuestProviderType;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestStateMachine;
import com.jvn.villagerretaliation.quest.persistence.QuestSaveMigrations;
import com.jvn.villagerretaliation.quest.persistence.QuestDefinitionMigration;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Resource;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.network.QuestTrackerRequestPayload;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.io.ByteArrayInputStream;
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
import java.util.Locale;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
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
            VillagerActionDefinition.Kind.QUEST_TRANSITION,
            VillagerActionDefinition.Kind.REPUTATION,
            VillagerActionDefinition.Kind.GOSSIP,
            VillagerActionDefinition.Kind.MEMORY,
            VillagerActionDefinition.Kind.LOOT,
            VillagerActionDefinition.Kind.NOTIFICATION,
            VillagerActionDefinition.Kind.FORCED_DIALOGUE);
    private static final Map<String, Integer> EXPECTED_QUESTLINE_COUNTS =
            BuiltInQuestContentGameTests.expectedQuestlineCounts();
    private static final int EXPECTED_QUEST_COUNT =
            BuiltInQuestContentGameTests.expectedQuestCount();

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
    public static void questAvailabilityScansDoNotDiscoverVillages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        int allegiancesBefore = registry.activeRecords(level.dimension().location()).size();

        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        VillagerQuestService.embeddedDialogueOptions(context, DialogueDisposition.NEUTRAL);

        helper.assertValueEqual(
                registry.activeRecords(level.dimension().location()).size(),
                allegiancesBefore,
                "quest availability scans must not discover village footprints");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void compiledCatalogMatchesParsedBuiltIns(GameTestHelper helper) {
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
            if (compiled.schemaVersion() == QuestSchemaVersion.V1) {
                assertCompiledV1QuestMatchesParsed(helper, quest, compiled);
            } else {
                assertCompiledQuestKeepsCanonicalDefinition(helper, quest, compiled);
            }
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void mixedBuiltInQuestResourcesKeepStableAuthoredIds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<ResourceLocation, ResourceLocation> authoredIdsByResource = new LinkedHashMap<>();
        Set<ResourceLocation> v1Ids = new LinkedHashSet<>();
        Set<ResourceLocation> v2Ids = new LinkedHashSet<>();
        server.getResourceManager()
                .listResources("quests", location -> VillagerRetaliation.MOD_ID.equals(location.getNamespace())
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    JsonObject root = readJsonObject(entry.getKey(), entry.getValue());
                    helper.assertTrue(root.has("id"), entry.getKey() + " no longer declares its stable v1 id");
                    ResourceLocation authoredId = ResourceLocation.tryParse(root.get("id").getAsString());
                    helper.assertTrue(authoredId != null, entry.getKey() + " declares an invalid quest id");
                    QuestResourceEnvelope envelope = QuestResourceEnvelope.read(entry.getKey(), root)
                            .orElseThrow(() -> new GameTestAssertException(entry.getKey() + " did not route to a quest schema"));
                    if (envelope.schemaVersion() == QuestSchemaVersion.V1) {
                        helper.assertFalse(root.has("schema"), entry.getKey() + " v1 quest gained a schema marker");
                        v1Ids.add(authoredId);
                    } else {
                        helper.assertValueEqual(
                                envelope.schemaVersion(),
                                QuestSchemaVersion.V2,
                                entry.getKey() + " schema version");
                        helper.assertValueEqual(
                                root.get("schema").getAsString(),
                                QuestSchemaVersion.V2.schemaId(),
                                entry.getKey() + " v2 schema marker");
                        v2Ids.add(authoredId);
                    }
                    authoredIdsByResource.put(entry.getKey(), authoredId);
                });
        helper.assertValueEqual(authoredIdsByResource.size(), EXPECTED_QUEST_COUNT, "built-in quest resource count");
        helper.assertTrue(v1Ids.isEmpty(), "built-in catalog still contains legacy v1 quests");
        helper.assertValueEqual(v2Ids.size(), EXPECTED_QUEST_COUNT, "built-in v2 quest count");
        assertContainsAll(
                helper,
                v2Ids,
                Set.of(
                        VillagerRetaliation.id("egg_baskets"),
                        VillagerRetaliation.id("choose_the_horizon"),
                        VillagerRetaliation.id("first_far_marker"),
                        VillagerRetaliation.id("tales_of_a_lost_civilization")),
                "migrated v2 built-in quest ids");

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
    public static void datapackResourceLoaderSkipsMalformedOrNonObjectRoots(GameTestHelper helper) {
        ResourceLocation giftLocation = VillagerRetaliation.id("gifts/test/bad_root.json");

        DatapackDiagnostics.clear();
        helper.assertTrue(
                DatapackResourceLoader.readObject(giftLocation, "gift", stringResource("[{\"item\":\"minecraft:apple\"}]")).isEmpty(),
                "array-root gift resource should be skipped");
        assertRecentDiagnosticContains(helper, "could not load gift data");

        DatapackDiagnostics.clear();
        helper.assertTrue(
                DatapackResourceLoader.readObject(giftLocation, "skill trade", stringResource("{ nope")).isEmpty(),
                "malformed skill-trade resource should be skipped");
        assertRecentDiagnosticContains(helper, "could not load skill trade data");

        DatapackDiagnostics.clear();
        JsonObject loaded = DatapackResourceLoader
                .readObject(giftLocation, "notification", stringResource("{\"notifications\":[]}"))
                .orElseThrow(() -> new GameTestAssertException("valid object-root resource was skipped"));
        helper.assertTrue(loaded.has("notifications"), "valid object-root resource should load");
        helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "valid object-root resource should not warn");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questDefinitionsCoverAuthoredFeatures(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
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

        for (QuestDefinition quest : quests) {
            QuestDefinition.Rewards rewards = quest.rewards();
            helper.assertTrue(rewards.experience() > 0, quest.id() + " has no XP reward");
            helper.assertTrue(rewards.reputation() > 0, quest.id() + " has no reputation reward");
            helper.assertTrue(rewards.gossipReputation() != 0, quest.id() + " has no gossip reward");
            helper.assertTrue(rewards.lootTable() != null, quest.id() + " has no loot-table reward");
            helper.assertTrue(rewards.memoryEvent() != null, quest.id() + " has no memory-event reward");
            assertResourceExists(helper, quest.id(), rewards.lootTable(), "loot_table");
            ResourceKey<LootTable> rewardLootKey = ResourceKey.create(Registries.LOOT_TABLE, rewards.lootTable());
            helper.assertTrue(
                    server.reloadableRegistries().getLootTable(rewardLootKey) != LootTable.EMPTY,
                    quest.id() + " reward loot table did not load: " + rewards.lootTable());
            if (rewards.gossipReputation() > 0) {
                positiveGossip++;
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
                VillagerActionDefinition.Kind.NOTIFICATION,
                VillagerActionDefinition.Kind.TRACKER,
                VillagerActionDefinition.Kind.FORCED_DIALOGUE), "trigger action kinds");
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

        JsonObject drawWrapper = JsonParser.parseString(
                "{\"actions\":[{\"type\":\"draw_weapon\",\"duration_seconds\":3}]}").getAsJsonObject();
        List<VillagerActionDefinition> drawActions = VillagerActionDefinition.readList(
                ResourceLocation.fromNamespaceAndPath("test", "draw_weapon"),
                "draw weapon test",
                drawWrapper);
        helper.assertValueEqual(drawActions.size(), 1, "draw weapon action count");
        helper.assertValueEqual(
                drawActions.getFirst().kind(), VillagerActionDefinition.Kind.DRAW_WEAPON, "draw weapon action kind");
        helper.assertValueEqual(drawActions.getFirst().amount(), 60, "draw weapon duration");

        VillagerActionDefinition notification = new VillagerActionDefinition(
                VillagerActionDefinition.Kind.NOTIFICATION,
                null,
                VillagerActionDefinition.QuestAction.NONE,
                0,
                null,
                com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope.BOTH,
                null,
                "quest.test",
                "Test",
                "",
                false,
                QuestFactScope.PLAYER,
                null,
                "",
                "",
                Map.of(),
                CompiledQuestTransition.EMPTY,
                null,
                "",
                false,
                false);
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
                                objective -> 3,
                                (objective, stack) -> true,
                                objective -> 0,
                                objective -> false,
                                objective -> false,
                                objective -> QuestObjectiveDebugState.EMPTY),
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
    public static void objectiveRegistryTracesEventObjectiveMatches(GameTestHelper helper) {
        QuestDefinition.Objective giftObjective = registryGiftObjective(Set.of("loved"));
        QuestObjectiveEvaluationContext context = new QuestObjectiveEvaluationContext(
                null,
                null,
                helper.getLevel(),
                null,
                null,
                objective -> 0,
                (objective, stack) -> true,
                objective -> 0,
                objective -> false,
                objective -> false,
                objective -> QuestObjectiveDebugState.EMPTY);

        QuestObjectiveEventTrace unrelated = QuestObjectiveRegistry.traceEventMatches(
                context,
                List.of(giftObjective),
                QuestObjectiveEvent.reputation(0));
        helper.assertValueEqual(unrelated.evaluatedObjectives(), 0, "unrelated event evaluated objective count");
        helper.assertValueEqual(unrelated.matchedObjectives(), 0, "unrelated event matched objective count");

        Villager giftVillager = spawnVillager(helper, new BlockPos(1, 1, 1));
        QuestObjectiveEvent giftEvent = QuestObjectiveEvent.gift(
                giftVillager,
                new ItemStack(Items.EMERALD),
                VillagerGiftPreferences.GiftReaction.LOVED);
        helper.assertValueEqual(giftEvent.villager(), giftVillager,
                "gift objective event must retain its target villager");
        QuestObjectiveEventTrace related = QuestObjectiveRegistry.traceEventMatches(
                context,
                List.of(giftObjective),
                giftEvent);
        helper.assertValueEqual(related.evaluatedObjectives(), 1, "related event evaluated objective count");
        helper.assertValueEqual(related.matchedObjectives(), 1, "related event matched objective count");

        ResourceLocation crafted = ResourceLocation.fromNamespaceAndPath("test", "crafted");
        QuestDefinition.Objective criterionObjective = registryCriterionObjective(
                crafted,
                Map.of("quality", "fine"));
        QuestObjectiveEventTrace criterionMismatch = QuestObjectiveRegistry.traceEventMatches(
                context,
                List.of(criterionObjective),
                QuestObjectiveEvent.criterion(crafted, Map.of("quality", "rough"), ItemStack.EMPTY, null));
        helper.assertValueEqual(criterionMismatch.matchedObjectives(), 0, "criterion match data must be exact");
        QuestObjectiveEventTrace criterionMatch = QuestObjectiveRegistry.traceEventMatches(
                context,
                List.of(criterionObjective),
                QuestObjectiveEvent.criterion(crafted, Map.of("quality", "fine", "source", "test"), ItemStack.EMPTY, null));
        helper.assertValueEqual(criterionMatch.evaluatedObjectives(), 1, "criterion event evaluated objective count");
        helper.assertValueEqual(criterionMatch.matchedObjectives(), 1, "criterion event matched objective count");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void objectiveRegistryEvaluatesLogicalObjectives(GameTestHelper helper) {
        QuestDefinition.Objective choiceObjective = registryFactObjective(
                QuestDefinition.ObjectiveType.CHOICE,
                "choice",
                Set.of("coast"));
        QuestDefinition.Objective conditionObjective = registryConditionObjective();
        QuestObjectiveEvaluationContext context = new QuestObjectiveEvaluationContext(
                null,
                null,
                helper.getLevel(),
                null,
                null,
                objective -> 0,
                (objective, stack) -> false,
                objective -> 0,
                objective -> objective == choiceObjective,
                objective -> objective == conditionObjective,
                objective -> objective == choiceObjective
                        ? new QuestObjectiveDebugState(null, "", "quest:choice", "")
                        : new QuestObjectiveDebugState(null, "", "", "met"));

        QuestObjectiveResult choiceResult = QuestObjectiveRegistry.evaluate(context, choiceObjective)
                .orElseThrow(() -> new GameTestAssertException("choice objective registry did not evaluate"));
        helper.assertTrue(choiceResult.complete(), "choice objective did not use fact matcher");
        helper.assertValueEqual(QuestObjectiveRegistry.trackerStepKey(choiceObjective), "choice", "choice tracker step");
        helper.assertValueEqual(
                QuestObjectiveRegistry.debugState(context, choiceObjective, choiceResult).factScopeKey(),
                "quest:choice",
                "choice debug scope");

        QuestObjectiveResult conditionResult = QuestObjectiveRegistry.evaluate(context, conditionObjective)
                .orElseThrow(() -> new GameTestAssertException("condition objective registry did not evaluate"));
        helper.assertTrue(conditionResult.complete(), "condition objective did not use condition matcher");
        helper.assertValueEqual(QuestObjectiveRegistry.trackerStepKey(conditionObjective), "inactive", "condition tracker step");
        helper.assertValueEqual(
                QuestObjectiveRegistry.debugState(context, conditionObjective, conditionResult).conditionState(),
                "met",
                "condition debug state");

        QuestDefinition branchingQuest = quest(helper, VillagerRetaliation.id("choose_the_horizon"));
        helper.assertValueEqual(
                QuestObjectiveQuery.choiceObjectives(branchingQuest).size(),
                1,
                "choice query objective count");

        QuestDefinition stagedQuest = registryStageQuest(choiceObjective);
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), Level.OVERWORLD, BlockPos.ZERO, 1L);
        QuestStageReadiness blocked = QuestStageReadiness.forCurrentStage(
                null,
                stagedQuest,
                progress,
                objective -> false);
        helper.assertFalse(blocked.ready(), "stage readiness ignored incomplete objective");
        QuestStageReadiness ready = QuestStageReadiness.forCurrentStage(
                null,
                stagedQuest,
                progress,
                objective -> objective.id().equals(choiceObjective.id()));
        helper.assertTrue(ready.ready(), "stage readiness did not accept complete objective");
        helper.assertValueEqual(ready.nextStage(), "done", "stage readiness next stage");

        List<QuestDefinition.StagePredicate> composed = List.of(
                new QuestDefinition.StagePredicate(choiceObjective.id(), List.of()),
                new QuestDefinition.StagePredicate("missing", List.of()));
        helper.assertTrue(
                QuestStageReadiness.compositionMet(
                        null, stagedQuest, composed, QuestDefinition.CompletionMode.ANY, 1,
                        objective -> objective.id().equals(choiceObjective.id())),
                "any composition did not accept one completed predicate");
        helper.assertFalse(
                QuestStageReadiness.compositionMet(
                        null, stagedQuest, composed, QuestDefinition.CompletionMode.ALL, 1,
                        objective -> objective.id().equals(choiceObjective.id())),
                "all composition accepted an incomplete predicate");
        helper.assertFalse(
                QuestStageReadiness.compositionMet(
                        null, stagedQuest, composed, QuestDefinition.CompletionMode.AT_LEAST, 2,
                        objective -> objective.id().equals(choiceObjective.id())),
                "at_least composition ignored its count");

        VillagerQuestSavedData.QuestProgress revisionProgress = new VillagerQuestSavedData.QuestProgress();
        revisionProgress.start(UUID.randomUUID(), Level.OVERWORLD, BlockPos.ZERO, 1L);
        revisionProgress.adoptDefinitionRevision(1);
        revisionProgress.setCurrentStage("retired_stage");
        revisionProgress.markObjectiveComplete("retired_choice");
        QuestDefinition revisedQuest = withRevision(
                stagedQuest,
                new QuestDefinition.Revision(
                        2,
                        QuestDefinition.RevisionPolicy.KEEP,
                        Map.of("retired_stage", "started"),
                        Map.of("retired_choice", choiceObjective.id())));
        QuestDefinitionMigration.Result revisionResult = QuestDefinitionMigration.apply(revisedQuest, revisionProgress, 80L);
        helper.assertTrue(revisionResult.changed(), "definition revision did not migrate");
        helper.assertValueEqual(revisionProgress.currentStage(), "started", "definition stage alias");
        helper.assertTrue(revisionProgress.objectiveComplete(choiceObjective.id()), "definition objective alias");
        helper.assertValueEqual(revisionProgress.definitionRevision(), 2, "persisted definition revision");
        helper.assertValueEqual(revisionProgress.lastRevisionPolicy(), "keep", "definition revision audit policy");

        UUID bonusPlayer = UUID.randomUUID();
        ResourceLocation bonusQuest = VillagerRetaliation.id("bonus_persistence");
        VillagerQuestSavedData bonusData = new VillagerQuestSavedData();
        VillagerQuestSavedData.QuestProgress bonusProgress = bonusData.getOrCreate(bonusPlayer, bonusQuest);
        helper.assertTrue(bonusProgress.claimBonus("started", "optional_route"), "first bonus claim");
        helper.assertFalse(bonusProgress.claimBonus("started", "optional_route"), "duplicate bonus claim");
        bonusProgress.recordDefinitionMigration(3, QuestDefinition.RevisionPolicy.RESTART, 42L);
        CompoundTag savedBonuses = bonusData.save(new CompoundTag(), helper.getLevel().registryAccess());
        VillagerQuestSavedData loadedBonuses = VillagerQuestSavedData.load(savedBonuses, helper.getLevel().registryAccess());
        helper.assertTrue(
                loadedBonuses.get(bonusPlayer, bonusQuest).bonusClaimed("started", "optional_route"),
                "claimed bonus did not survive save/load");
        helper.assertValueEqual(
                loadedBonuses.get(bonusPlayer, bonusQuest).definitionRevision(),
                3,
                "definition revision did not survive save/load");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void triggerRegistryNormalizesEventsAndIndexesStages(GameTestHelper helper) {
        helper.assertValueEqual(
                QuestTriggerRegistry.canonicalEventId("stage_set"),
                "stage_changed",
                "stage_set trigger alias");
        helper.assertValueEqual(
                QuestDefinition.TriggerEvent.bySerializedName("stage_entered"),
                QuestDefinition.TriggerEvent.STAGE_CHANGED,
                "stage_entered trigger event");
        helper.assertValueEqual(
                QuestTriggerRegistry.defaultCooldownTicks(QuestDefinition.TriggerEvent.PLAYER_TICK),
                600L,
                "player_tick default cooldown");

        List<QuestDefinition.Trigger> triggers = List.of(
                registryTrigger("tick_global", QuestDefinition.TriggerEvent.PLAYER_TICK, Set.of(), 600L, true),
                registryTrigger("tick_started", QuestDefinition.TriggerEvent.PLAYER_TICK, Set.of("started"), 0L, true),
                registryTrigger("completed_once", QuestDefinition.TriggerEvent.COMPLETED, Set.of(), 0L, false),
                registryTrigger("tick_done", QuestDefinition.TriggerEvent.PLAYER_TICK, Set.of("done"), 20L, true));
        QuestTriggerIndex index = QuestTriggerRegistry.index(compiledTriggers(triggers));

        helper.assertValueEqual(
                index.candidates(QuestDefinition.TriggerEvent.PLAYER_TICK, "started").stream()
                        .map(CompiledQuestTrigger::id)
                        .toList(),
                List.of("tick_global", "tick_started"),
                "started trigger candidates");
        helper.assertValueEqual(
                index.candidates(QuestDefinition.TriggerEvent.PLAYER_TICK, "done").stream()
                        .map(CompiledQuestTrigger::id)
                        .toList(),
                List.of("tick_global", "tick_done"),
                "done trigger candidates");
        helper.assertTrue(
                index.candidates(QuestDefinition.TriggerEvent.PROGRESS, "started").isEmpty(),
                "unrelated event returned candidates");
        helper.assertValueEqual(index.continuousTriggers().size(), 3, "continuous trigger index size");

        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        QuestTriggerDispatcher.markContinuousTriggersUsed(progress, index, 777L);
        helper.assertValueEqual(progress.lastTriggerGameTime("tick_global"), 777L, "global continuous mark");
        helper.assertValueEqual(progress.lastTriggerGameTime("tick_started"), -1L, "zero-cooldown continuous mark");
        helper.assertValueEqual(progress.lastTriggerGameTime("tick_done"), 777L, "stage continuous mark");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void triggerDispatcherReportsIndexedDispatchMetrics(GameTestHelper helper) {
        List<QuestDefinition.Trigger> triggers = List.of(
                registryTrigger("progress_global", QuestDefinition.TriggerEvent.PROGRESS, Set.of(), 0L, true),
                registryTrigger("progress_started", QuestDefinition.TriggerEvent.PROGRESS, Set.of("started"), 30L, true),
                registryTrigger("progress_done", QuestDefinition.TriggerEvent.PROGRESS, Set.of("done"), 0L, true),
                registryTrigger("completed_once", QuestDefinition.TriggerEvent.COMPLETED, Set.of(), 0L, false));
        QuestDefinition quest = registryTriggerQuest(triggers);
        QuestTriggerIndex index = QuestTriggerRegistry.index(compiledTriggers(triggers));
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), Level.OVERWORLD, BlockPos.ZERO, 100L);
        progress.setCurrentStage("started");

        List<String> ranTriggers = new ArrayList<>();
        QuestTriggerDispatchResult first = QuestTriggerDispatcher.dispatchAtGameTime(
                null,
                200L,
                quest,
                index,
                progress,
                QuestDefinition.TriggerEvent.PROGRESS,
                (context, definition, activeProgress, trigger) -> {
                    ranTriggers.add(trigger.id());
                    return true;
                });
        helper.assertTrue(first.dirty(), "first dispatch did not dirty progress");
        helper.assertValueEqual(first.trace().candidateTriggers(), 2, "first dispatch candidate count");
        helper.assertValueEqual(first.trace().evaluatedTriggers(), 2, "first dispatch evaluated count");
        helper.assertValueEqual(first.trace().matchedTriggers(), 2, "first dispatch matched count");
        helper.assertValueEqual(first.trace().ranTriggers(), 2, "first dispatch ran count");
        helper.assertValueEqual(ranTriggers, List.of("progress_global", "progress_started"), "first dispatch order");
        helper.assertValueEqual(progress.lastTriggerGameTime("progress_global"), 200L, "global trigger time");
        helper.assertValueEqual(progress.lastTriggerGameTime("progress_started"), 200L, "started trigger time");

        ranTriggers.clear();
        QuestTriggerDispatchResult cooldown = QuestTriggerDispatcher.dispatchAtGameTime(
                null,
                210L,
                quest,
                index,
                progress,
                QuestDefinition.TriggerEvent.PROGRESS,
                (context, definition, activeProgress, trigger) -> {
                    ranTriggers.add(trigger.id());
                    return true;
                });
        helper.assertTrue(cooldown.dirty(), "cooldown dispatch did not run eligible trigger");
        helper.assertValueEqual(cooldown.trace().candidateTriggers(), 2, "cooldown dispatch candidate count");
        helper.assertValueEqual(cooldown.trace().evaluatedTriggers(), 2, "cooldown dispatch evaluated count");
        helper.assertValueEqual(cooldown.trace().matchedTriggers(), 1, "cooldown dispatch matched count");
        helper.assertValueEqual(cooldown.trace().ranTriggers(), 1, "cooldown dispatch ran count");
        helper.assertValueEqual(ranTriggers, List.of("progress_global"), "cooldown dispatch order");
        helper.assertValueEqual(progress.lastTriggerGameTime("progress_global"), 210L, "updated global trigger time");
        helper.assertValueEqual(progress.lastTriggerGameTime("progress_started"), 200L, "cooldown trigger time");

        QuestTriggerDispatchResult unrelated = QuestTriggerDispatcher.dispatchAtGameTime(
                null,
                220L,
                quest,
                index,
                progress,
                QuestDefinition.TriggerEvent.ABANDONED,
                (context, definition, activeProgress, trigger) -> true);
        helper.assertFalse(unrelated.dirty(), "unrelated dispatch dirtied progress");
        helper.assertValueEqual(unrelated.trace().candidateTriggers(), 0, "unrelated dispatch candidate count");
        helper.assertValueEqual(unrelated.trace().evaluatedTriggers(), 0, "unrelated dispatch evaluated count");

        VillagerQuestSavedData.QuestProgress zeroTimeProgress = new VillagerQuestSavedData.QuestProgress();
        zeroTimeProgress.start(UUID.randomUUID(), Level.OVERWORLD, BlockPos.ZERO, 0L);
        zeroTimeProgress.setCurrentStage("started");
        List<QuestDefinition.Trigger> continuousTriggers = List.of(
                registryTrigger("tick_started", QuestDefinition.TriggerEvent.PLAYER_TICK, Set.of("started"), 30L, true));
        QuestDefinition continuousQuest = registryTriggerQuest(continuousTriggers);
        QuestTriggerIndex continuousIndex = QuestTriggerRegistry.index(compiledTriggers(continuousTriggers));
        List<String> zeroTimeRuns = new ArrayList<>();
        QuestTriggerDispatchResult initialCooldown = QuestTriggerDispatcher.dispatchAtGameTime(
                null,
                1L,
                continuousQuest,
                continuousIndex,
                zeroTimeProgress,
                QuestDefinition.TriggerEvent.PLAYER_TICK,
                (context, definition, activeProgress, trigger) -> {
                    zeroTimeRuns.add(trigger.id());
                    return true;
                });
        helper.assertFalse(initialCooldown.dirty(), "quest started at zero must honor initial continuous cooldown");
        helper.assertTrue(zeroTimeRuns.isEmpty(), "initial continuous cooldown should suppress its trigger");

        zeroTimeRuns.clear();
        QuestTriggerDispatchResult firstCompletion = QuestTriggerDispatcher.dispatchAtGameTime(
                null,
                0L,
                quest,
                index,
                zeroTimeProgress,
                QuestDefinition.TriggerEvent.COMPLETED,
                (context, definition, activeProgress, trigger) -> {
                    zeroTimeRuns.add(trigger.id());
                    return true;
                });
        QuestTriggerDispatchResult duplicateCompletion = QuestTriggerDispatcher.dispatchAtGameTime(
                null,
                1L,
                quest,
                index,
                zeroTimeProgress,
                QuestDefinition.TriggerEvent.COMPLETED,
                (context, definition, activeProgress, trigger) -> true);
        helper.assertTrue(firstCompletion.dirty(), "non-repeatable trigger should run at game time zero");
        helper.assertFalse(duplicateCompletion.dirty(), "non-repeatable trigger run at zero must not run twice");
        helper.assertValueEqual(zeroTimeRuns, List.of("completed_once"), "zero-time completion trigger");
        helper.assertFalse(
                QuestAvailabilityService.cooldownElapsed(1L, 0L, 20L),
                "event at game time zero must retain its cooldown");

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
    public static void invalidDialogueConditionsFailClosed(GameTestHelper helper) {
        JsonObject unknownRoot = new JsonObject();
        JsonArray unknownConditions = new JsonArray();
        JsonObject unknown = new JsonObject();
        unknown.addProperty("type", "misspelled_condition");
        unknownConditions.add(unknown);
        unknownRoot.add("conditions", unknownConditions);

        List<DialogueCondition> parsed = DialogueCondition.readList(
                VillagerRetaliation.id("test/invalid_condition"),
                "invalid condition",
                unknownRoot);
        helper.assertValueEqual(parsed.size(), 1, "invalid condition sentinel count");
        helper.assertTrue(parsed.getFirst() instanceof DialogueCondition.Invalid, "unknown condition did not fail closed");

        JsonObject malformedRoot = new JsonObject();
        malformedRoot.addProperty("conditions", "not an array");
        List<DialogueCondition> malformed = DialogueCondition.readList(
                VillagerRetaliation.id("test/malformed_condition"),
                "malformed condition",
                malformedRoot);
        helper.assertTrue(malformed.getFirst() instanceof DialogueCondition.Invalid, "malformed condition list did not fail closed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void textKeyDialogueLinesParticipateInFreshSelection(GameTestHelper helper) {
        DialogueLine keyedLine = DialogueLine.builder("keyed_line", DialogueRequestType.QUESTION, List.of())
                .textKey("test.keyed_line")
                .build();

        helper.assertTrue(!keyedLine.recentlyUsed(List.of()), "new text_key line was considered recently used");
        helper.assertTrue(keyedLine.hasFreshVariant(List.of()), "new text_key line was filtered from fresh candidates");
        helper.assertTrue(keyedLine.recentlyUsed(List.of("keyed_line")), "used text_key line was not remembered");
        helper.assertTrue(!keyedLine.hasFreshVariant(List.of("keyed_line")), "used text_key line remained fresh");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void dialogueOptionNetworkBoundsAreExplicit(GameTestHelper helper) {
        String maximum = "x".repeat(DialogueOptionDefinition.MAX_NETWORK_ID_LENGTH);
        String oversized = maximum + "x";
        helper.assertTrue(DialogueOptionDefinition.isNetworkSafeId(maximum), "maximum-length option id was rejected");
        helper.assertTrue(!DialogueOptionDefinition.isNetworkSafeId(oversized), "oversized option id was accepted");
        helper.assertTrue(!DialogueOptionDefinition.isNetworkSafeLabel(oversized), "oversized option label was accepted");
        helper.assertValueEqual(
                DialogueOptionDefinition.networkSafeLabel(oversized).length(),
                DialogueOptionDefinition.MAX_NETWORK_LABEL_LENGTH,
                "network label truncation");
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
        List<QuestTrackerSyncPayload.RewardPreview> rewards = QuestTrackerPresenter.rewardPreviews(
                helper.makeMockServerPlayerInLevel(),
                quest,
                replacements);
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
                rewards,
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
                entry.journal().blocker().contains("Collect 1 more minecraft:echo_shard"),
                "presenter did not explain the remaining item requirement");
        helper.assertTrue(
                entry.rewardPreviews().stream().anyMatch(reward -> reward.kind().equals("experience") && reward.label().contains("430")),
                "presenter did not include XP reward preview");
        helper.assertTrue(
                entry.rewardPreviews().stream().anyMatch(reward -> reward.kind().equals("reputation") && reward.label().contains("+22")),
                "presenter did not include reputation reward preview");
        helper.assertTrue(
                entry.rewardPreviews().stream().anyMatch(reward -> reward.kind().equals("item")
                        && reward.itemId().equals("minecraft:emerald")
                        && reward.label().equals("30-44")),
                "presenter did not include item reward preview");
        helper.assertTrue(
                QuestTrackerPresenter.syncSignature(List.of(entry), quest.id()).contains(entry.questId()),
                "presenter signature omitted quest id");
        QuestTrackerSyncPayload.Journal journal = new QuestTrackerSyncPayload.Journal(
                "lost_civilization",
                List.of("story", "exploration"),
                "minecraft:filled_map",
                "#d4a35a",
                "#111111",
                25,
                false,
                1242L,
                -1L,
                new QuestTrackerSyncPayload.Waypoint("minecraft:overworld", 120, 70, -40))
                .withBlocker("Return to Lore Keeper.")
                .withQuestlineProgress(3, 7);
        QuestTrackerSyncPayload.Entry journalEntry = entry.withJournal(journal);
        helper.assertValueEqual(journalEntry.journal().outlineColor(), "#111111", "journal outline color");
        helper.assertValueEqual(journalEntry.journal().priority(), 25, "journal priority");
        helper.assertTrue(journalEntry.journal().waypoint().present(), "journal waypoint");
        helper.assertValueEqual(journalEntry.journal().blocker(), "Return to Lore Keeper.", "journal blocker");
        helper.assertValueEqual(journalEntry.journal().questlineCompleted(), 3, "journal questline completed");
        helper.assertValueEqual(journalEntry.journal().questlineTotal(), 7, "journal questline total");
        helper.assertFalse(
                QuestTrackerPresenter.entrySignature(journalEntry).equals(QuestTrackerPresenter.entrySignature(entry)),
                "journal data must participate in tracker sync signatures");
        QuestDefinition childQuest = quest(helper, VillagerRetaliation.id("standing_watch"));
        List<QuestTrackerSyncPayload.Prerequisite> unmetPrerequisites = QuestTrackerPresenter.prerequisites(
                null,
                childQuest,
                parentId -> "Watch Arrows",
                parentId -> false);
        helper.assertValueEqual(unmetPrerequisites.size(), 1, "presenter did not include parent prerequisite");
        helper.assertTrue(!unmetPrerequisites.getFirst().met(), "presenter marked unmet parent prerequisite complete");
        helper.assertTrue(
                unmetPrerequisites.getFirst().label().contains("Complete Watch Arrows"),
                "presenter prerequisite label omitted parent title");
        List<QuestTrackerSyncPayload.Prerequisite> metPrerequisites = QuestTrackerPresenter.prerequisites(
                null,
                childQuest,
                parentId -> "Watch Arrows",
                parentId -> true);
        helper.assertTrue(metPrerequisites.getFirst().met(), "presenter did not mark completed parent prerequisite");
        QuestDefinition endCitySurvey = quest(helper, VillagerRetaliation.id("end_city_survey"));
        VillagerQuestSavedData.QuestProgress completedLineQuest = new VillagerQuestSavedData.QuestProgress();
        completedLineQuest.start(UUID.randomUUID(), Level.OVERWORLD, BlockPos.ZERO, 1L);
        completedLineQuest.complete(2L, false);
        QuestTrackerPresenter.QuestlineProgress lineProgress = QuestTrackerPresenter.questlineProgress(
                        List.of(quest, endCitySurvey),
                        questId -> questId.equals(endCitySurvey.id()) ? completedLineQuest : progress)
                .get("lost_civilization");
        helper.assertValueEqual(lineProgress.completed(), 1, "questline completed count");
        helper.assertValueEqual(lineProgress.total(), 2, "questline total count");
        QuestDefinition bastionLine = quest(helper, VillagerRetaliation.id("bastion_line"));
        QuestDefinition fortressLine = quest(helper, VillagerRetaliation.id("fortress_line"));
        VillagerQuestSavedData.QuestProgress closedBranch = new VillagerQuestSavedData.QuestProgress();
        closedBranch.consume("branch_lock");
        QuestTrackerPresenter.QuestlineProgress branchProgress = QuestTrackerPresenter.questlineProgress(
                        List.of(bastionLine, fortressLine),
                        questId -> questId.equals(fortressLine.id()) ? closedBranch : null)
                .get("nether_routes");
        helper.assertValueEqual(branchProgress.total(), 1, "closed branch must not count toward questline total");
        QuestTrackerSyncPayload.Entry movedIssuerEntry = new QuestTrackerSyncPayload.Entry(
                entry.questId(),
                entry.title(),
                entry.objective(),
                entry.description(),
                entry.metadata(),
                entry.progress(),
                entry.showProgress(),
                entry.state(),
                entry.status(),
                entry.issuer(),
                "minecraft:overworld 6,65,-2",
                entry.questItems(),
                entry.rewardPreviews(),
                entry.prerequisites(),
                entry.objectiveSteps(),
                false,
                false).withJournal(entry.journal());
        helper.assertValueEqual(
                QuestTrackerPresenter.questProgressSignature(movedIssuerEntry),
                QuestTrackerPresenter.questProgressSignature(entry),
                "presenter progress signature changed for issuer location drift");

        List<QuestTrackerSyncPayload.QuestItem> manyItems = new ArrayList<>();
        for (int i = 0; i < QuestTrackerSyncPayload.MAX_QUEST_ITEMS + 4; i++) {
            manyItems.add(new QuestTrackerSyncPayload.QuestItem("minecraft:stone_" + i, "Stone " + i, i + 1));
        }
        QuestTrackerSyncPayload.Entry itemCappedEntry = new QuestTrackerSyncPayload.Entry(
                "villagerretaliation:item_cap",
                "Item Cap",
                "Objective",
                "Metadata",
                0.25F,
                true,
                "active",
                "Active",
                "Issuer",
                "Location",
                manyItems);
        helper.assertValueEqual(
                itemCappedEntry.questItems().size(),
                QuestTrackerSyncPayload.MAX_QUEST_ITEMS,
                "tracker entry did not cap quest items");

        List<QuestTrackerSyncPayload.RewardPreview> manyRewards = new ArrayList<>();
        for (int i = 0; i < QuestTrackerSyncPayload.MAX_REWARD_PREVIEWS + 4; i++) {
            manyRewards.add(new QuestTrackerSyncPayload.RewardPreview("test", "Reward " + i, i));
        }
        QuestTrackerSyncPayload.Entry rewardCappedEntry = new QuestTrackerSyncPayload.Entry(
                "villagerretaliation:reward_cap",
                "Reward Cap",
                "Objective",
                "",
                "Metadata",
                0.25F,
                true,
                "active",
                "Active",
                "Issuer",
                "Location",
                List.of(),
                manyRewards,
                false,
                false);
        helper.assertValueEqual(
                rewardCappedEntry.rewardPreviews().size(),
                QuestTrackerSyncPayload.MAX_REWARD_PREVIEWS,
                "tracker entry did not cap reward previews");

        List<QuestTrackerSyncPayload.Prerequisite> manyPrerequisites = new ArrayList<>();
        for (int i = 0; i < QuestTrackerSyncPayload.MAX_PREREQUISITES + 4; i++) {
            manyPrerequisites.add(new QuestTrackerSyncPayload.Prerequisite(
                    "villagerretaliation:parent_" + i,
                    "Parent " + i,
                    false));
        }
        QuestTrackerSyncPayload.Entry prerequisiteCappedEntry = new QuestTrackerSyncPayload.Entry(
                "villagerretaliation:prerequisite_cap",
                "Prerequisite Cap",
                "Objective",
                "",
                "Metadata",
                0.25F,
                true,
                "active",
                "Active",
                "Issuer",
                "Location",
                List.of(),
                List.of(),
                manyPrerequisites,
                false,
                false);
        helper.assertValueEqual(
                prerequisiteCappedEntry.prerequisites().size(),
                QuestTrackerSyncPayload.MAX_PREREQUISITES,
                "tracker entry did not cap prerequisites");

        List<QuestTrackerSyncPayload.ObjectiveStep> manyObjectiveSteps = new ArrayList<>();
        for (int i = 0; i < QuestTrackerSyncPayload.MAX_OBJECTIVE_STEPS + 4; i++) {
            manyObjectiveSteps.add(new QuestTrackerSyncPayload.ObjectiveStep("Objective " + i, true));
        }
        QuestTrackerSyncPayload.Entry objectiveStepCappedEntry = new QuestTrackerSyncPayload.Entry(
                "villagerretaliation:objective_step_cap",
                "Objective Step Cap",
                "Objective",
                "",
                "Metadata",
                0.25F,
                true,
                "active",
                "Active",
                "Issuer",
                "Location",
                List.of(),
                List.of(),
                List.of(),
                manyObjectiveSteps,
                false,
                false);
        helper.assertValueEqual(
                objectiveStepCappedEntry.objectiveSteps().size(),
                QuestTrackerSyncPayload.MAX_OBJECTIVE_STEPS,
                "tracker entry did not cap objective steps");

        List<QuestTrackerSyncPayload.Entry> manyEntries = new ArrayList<>();
        for (int i = 0; i < QuestTrackerSyncPayload.MAX_SYNC_ENTRIES + 4; i++) {
            manyEntries.add(new QuestTrackerSyncPayload.Entry(
                    "villagerretaliation:entry_" + i,
                    "Entry " + i,
                    "Objective",
                    "Metadata",
                    0.0F,
                    false,
                    "active",
                    "Active",
                    "",
                    "",
                    List.of()));
        }
        QuestTrackerSyncPayload payload = new QuestTrackerSyncPayload(manyEntries, quest.id().toString(), false);
        helper.assertValueEqual(
                payload.entries().size(),
                QuestTrackerSyncPayload.MAX_SYNC_ENTRIES,
                "tracker sync payload did not cap entries");

        String oversized = "x".repeat(400);
        List<String> manyParents = new ArrayList<>();
        for (int i = 0; i < QuestTrackerSyncPayload.MAX_PREREQUISITES + 4; i++) {
            manyParents.add("villagerretaliation:parent_" + i);
        }
        QuestTrackerSyncPayload.QuestlineNode boundedNode = new QuestTrackerSyncPayload.QuestlineNode(
                oversized,
                oversized,
                oversized,
                oversized,
                manyParents,
                oversized,
                oversized,
                oversized);
        List<QuestTrackerSyncPayload.QuestlineNode> manyQuestlineNodes = new ArrayList<>();
        for (int i = 0; i < QuestTrackerSyncPayload.MAX_QUESTLINE_NODES + 4; i++) {
            manyQuestlineNodes.add(i == 0
                    ? boundedNode
                    : new QuestTrackerSyncPayload.QuestlineNode(
                            "villagerretaliation:node_" + i,
                            "Node " + i,
                            "Description",
                            "test_line",
                            List.of(),
                            "minecraft:emerald",
                            "#70834a",
                            "locked"));
        }
        QuestTrackerSyncPayload graphPayload = new QuestTrackerSyncPayload(
                List.of(),
                List.of(),
                false,
                manyQuestlineNodes);
        helper.assertValueEqual(
                graphPayload.questlineNodes().size(),
                QuestTrackerSyncPayload.MAX_QUESTLINE_NODES,
                "tracker sync payload did not cap questline nodes");
        helper.assertTrue(
                boundedNode.questId().length() <= 128
                        && boundedNode.title().length() <= 128
                        && boundedNode.description().length() <= 256
                        && boundedNode.questline().length() <= 128
                        && boundedNode.parentQuestIds().size() == QuestTrackerSyncPayload.MAX_PREREQUISITES
                        && boundedNode.parentQuestIds().stream().allMatch(parent -> parent.length() <= 128)
                        && boundedNode.icon().length() <= 128
                        && boundedNode.color().length() <= 128
                        && boundedNode.state().length() <= 32,
                "questline node fields must fit their wire codec bounds");

        QuestTrackerSyncPayload.Entry boundedEntry = new QuestTrackerSyncPayload.Entry(
                oversized,
                oversized,
                oversized,
                oversized,
                oversized,
                Float.NaN,
                true,
                oversized,
                oversized,
                oversized,
                oversized,
                List.of(new QuestTrackerSyncPayload.QuestItem(oversized, oversized, 1)),
                List.of(new QuestTrackerSyncPayload.RewardPreview(oversized, oversized, 1, oversized)),
                List.of(new QuestTrackerSyncPayload.Prerequisite(oversized, oversized, false)),
                List.of(new QuestTrackerSyncPayload.ObjectiveStep(oversized, false)),
                false,
                false);
        helper.assertTrue(
                boundedEntry.questId().length() <= 128
                        && boundedEntry.title().length() <= 128
                        && boundedEntry.objective().length() <= 256
                        && boundedEntry.description().length() <= 256
                        && boundedEntry.metadata().length() <= 256
                        && boundedEntry.state().length() <= 32
                        && boundedEntry.status().length() <= 96
                        && boundedEntry.issuer().length() <= 160
                        && boundedEntry.issuerLocation().length() <= 192
                        && boundedEntry.questItems().getFirst().label().length() <= 128
                        && boundedEntry.rewardPreviews().getFirst().label().length() <= 160
                        && boundedEntry.rewardPreviews().getFirst().itemId().length() <= 128
                        && boundedEntry.prerequisites().getFirst().label().length() <= 160
                        && boundedEntry.objectiveSteps().getFirst().label().length() <= 256,
                "tracker strings must fit their wire codec bounds");
        helper.assertValueEqual(boundedEntry.progress(), 0.0F, "non-finite tracker progress must normalize");
        QuestTrackerSyncPayload.Journal boundedJournal = QuestTrackerSyncPayload.Journal.EMPTY
                .withBlocker(oversized)
                .withQuestlineProgress(12, 5);
        helper.assertTrue(boundedJournal.blocker().length() <= 256, "journal blocker must fit its wire codec bound");
        helper.assertValueEqual(boundedJournal.questlineCompleted(), 5, "questline progress must clamp to total");
        helper.assertValueEqual(
                new QuestTrackerRequestPayload(oversized, QuestTrackerRequestPayload.Action.TRACK).questId().length(),
                128,
                "tracker request quest id must fit its wire codec bound");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void activeTrackerEntryListsMobKillObjectiveSteps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));

        QuestDefinition.Objective defeatZombie = new QuestDefinition.Objective(
                "defeat_zombie",
                QuestDefinition.ObjectiveType.MOB_KILL,
                false,
                null,
                Level.OVERWORLD,
                null,
                8,
                List.of(),
                16,
                8,
                null,
                Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "zombie")),
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
                2,
                false,
                QuestDefinition.ItemRequirements.EMPTY,
                List.of(),
                QuestDefinition.ObjectiveTracker.EMPTY);
        QuestDefinition.Objective bringBones = new QuestDefinition.Objective(
                "bring_bones",
                QuestDefinition.ObjectiveType.ITEM_CHECK,
                false,
                null,
                Level.OVERWORLD,
                null,
                8,
                List.of(),
                16,
                8,
                ResourceLocation.fromNamespaceAndPath("minecraft", "bone"),
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
                3,
                false,
                QuestDefinition.ItemRequirements.EMPTY,
                List.of(),
                QuestDefinition.ObjectiveTracker.EMPTY);
        QuestDefinition.Stage started = new QuestDefinition.Stage(
                "started",
                List.of("defeat_zombie", "bring_bones"),
                List.of(
                        new QuestDefinition.StagePredicate("defeat_zombie", List.of()),
                        new QuestDefinition.StagePredicate("bring_bones", List.of())),
                "return",
                List.of(),
                List.of(),
                List.of());
        QuestDefinition.Stage returnStage = new QuestDefinition.Stage(
                "return",
                List.of(),
                List.of(),
                "",
                List.of(),
                List.of(),
                List.of());
        QuestDefinition quest = new QuestDefinition(
                VillagerRetaliation.id("active_mob_objective_steps"),
                "Active Mob Objective Steps",
                "Defeat a mob and bring back supplies.",
                "",
                "",
                "",
                Set.of("test"),
                null,
                List.of(),
                true,
                null,
                QuestDefinition.Target.EMPTY,
                List.of(defeatZombie, bringBones),
                QuestDefinition.Rules.DEFAULT,
                QuestDefinition.Tracker.EMPTY,
                "",
                Map.of("started", started, "return", returnStage),
                List.of(),
                QuestDefinition.Rewards.EMPTY,
                QuestDefinition.Dialogue.EMPTY,
                null,
                QuestDefinition.Links.EMPTY);
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(villager.getUUID(), Level.OVERWORLD, null, level.getGameTime());
        progress.setIssuer(
                villager.getUUID(),
                "Scout",
                "minecraft:weaponsmith",
                2,
                Level.OVERWORLD,
                villager.blockPosition(),
                "village:overworld:0:0");
        progress.addObjectiveCounter("defeat_zombie", 1);
        player.getInventory().add(new ItemStack(Items.BONE, 2));
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);

        QuestTrackerSyncPayload.Entry entry = VillagerQuestService.debugTrackerEntryForTests(
                player,
                context,
                quest,
                progress,
                true);

        helper.assertTrue(entry.objective().contains("Defeat 1/2 Zombie"), "active tracker objective did not update mob progress: " + entry.objective());
        helper.assertValueEqual(entry.objectiveSteps().size(), 2, "active tracker did not list all objectives");
        helper.assertTrue(
                entry.objectiveSteps().getFirst().label().contains("Defeat 1/2 Zombie"),
                "active tracker omitted required mob kill objective: " + entry.objectiveSteps());
        helper.assertTrue(
                entry.objectiveSteps().stream().anyMatch(step -> step.label().contains("Collect 2/3 Bone")),
                "active tracker omitted item objective beside mob kill: " + entry.objectiveSteps());
        helper.assertTrue(
                entry.questItems().stream().anyMatch(item -> item.itemId().equals("minecraft:bone")
                        && item.count() == 3
                        && item.currentCount() == 2),
                "active tracker quest item count did not update: " + entry.questItems());

        progress.addObjectiveCounter("defeat_zombie", 1);
        progress.markObjectiveComplete("defeat_zombie");
        progress.setCurrentStage("return");
        player.getInventory().clearContent();
        player.getInventory().setChanged();

        QuestTrackerSyncPayload.Entry droppedEntry = VillagerQuestService.debugTrackerEntryForTests(
                player,
                context,
                quest,
                progress,
                true);
        helper.assertValueEqual(droppedEntry.status(), "Active", "dropped item objective still looked ready");
        helper.assertTrue(
                droppedEntry.questItems().stream().anyMatch(item -> item.itemId().equals("minecraft:bone")
                        && item.count() == 3
                        && item.currentCount() == 0),
                "dropped required item disappeared from tracker counts: " + droppedEntry.questItems());

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questProgressPersistsDistinctCompletionHistory(GameTestHelper helper) {
        VillagerQuestSavedData data = new VillagerQuestSavedData();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID firstIssuer = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID secondIssuer = UUID.fromString("00000000-0000-0000-0000-000000000202");
        ResourceLocation questId = VillagerRetaliation.id("repeatable_history_fixture");

        VillagerQuestSavedData.QuestProgress progress = data.getOrCreate(playerId, questId);
        progress.start(firstIssuer, Level.OVERWORLD, new BlockPos(1, 64, 1), 10L);
        progress.setIssuer(
                firstIssuer,
                "First Keeper",
                "minecraft:librarian",
                2,
                Level.OVERWORLD,
                new BlockPos(3, 65, 3),
                "village:first");
        progress.complete(20L, false);
        progress.start(secondIssuer, Level.OVERWORLD, new BlockPos(8, 64, 8), 30L);
        progress.setIssuer(
                secondIssuer,
                "Second Keeper",
                "minecraft:cleric",
                4,
                Level.OVERWORLD,
                new BlockPos(9, 66, 9),
                "village:second");
        progress.complete(40L, false);

        helper.assertValueEqual(progress.completionCount(), 2, "completion count");
        helper.assertValueEqual(progress.completionHistory().size(), 2, "completion history count");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VillagerQuestSavedData loaded = VillagerQuestSavedData.load(saved, helper.getLevel().registryAccess());
        VillagerQuestSavedData.QuestProgress loadedProgress = loaded.get(playerId, questId);
        helper.assertTrue(loadedProgress != null, "completion history progress did not reload");
        List<VillagerQuestSavedData.CompletionHistoryEntry> history = loadedProgress.completionHistory();
        helper.assertValueEqual(history.size(), 2, "reloaded completion history count");
        helper.assertValueEqual(history.getFirst().completionIndex(), 1, "first completion index");
        helper.assertValueEqual(history.getFirst().issuerId(), firstIssuer, "first completion issuer");
        helper.assertValueEqual(history.getFirst().issuerName(), "First Keeper", "first completion issuer name");
        helper.assertValueEqual(history.getFirst().completedGameTime(), 20L, "first completion time");
        helper.assertValueEqual(history.get(1).completionIndex(), 2, "second completion index");
        helper.assertValueEqual(history.get(1).issuerId(), secondIssuer, "second completion issuer");
        helper.assertValueEqual(history.get(1).issuerName(), "Second Keeper", "second completion issuer name");
        helper.assertValueEqual(history.get(1).issuerPos(), new BlockPos(9, 66, 9), "second completion issuer position");

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
    public static void branchingQuestModuleV2CompilesResponseTransitions(GameTestHelper helper) {
        ResourceLocation questId = VillagerRetaliation.id("choose_the_horizon");
        CompiledQuest compiled = VillagerQuestResources
                .compiledQuest(helper.getLevel().getServer(), questId)
                .orElseThrow(() -> new GameTestAssertException("Missing compiled quest " + questId));
        QuestDefinition quest = compiled.asQuestDefinition();
        helper.assertValueEqual(compiled.schemaVersion(), QuestSchemaVersion.V2, "choose_the_horizon schema version");
        helper.assertValueEqual(quest.objectives().size(), 7, "choose_the_horizon objective count");
        QuestDefinition.Objective objective = quest.objectives().getFirst();
        helper.assertValueEqual(objective.id(), "started.choose_route", "branch objective id");
        helper.assertValueEqual(objective.type(), QuestDefinition.ObjectiveType.CHOICE, "branch objective type");
        helper.assertValueEqual(objective.factScope(), QuestFactScope.QUEST, "branch objective fact scope");
        helper.assertValueEqual(objective.factKey(), "choice", "branch objective fact key");
        helper.assertTrue(objective.factValues().containsAll(Set.of("coast", "dark_roof")),
                "branch objective choices changed");

        helper.assertValueEqual(quest.stages().size(), 5, "choose_the_horizon stage count");
        QuestDefinition.Stage started = quest.stages().get("started");
        helper.assertTrue(started != null, "choose_the_horizon has no started stage");
        helper.assertTrue(started.branches().isEmpty(), "v2 responses should not compile duplicate legacy branches");
        helper.assertValueEqual(started.objectives(), List.of("started.choose_route"), "started objective ownership");
        helper.assertValueEqual(started.completeWhen().getFirst().objective(), "started.choose_route", "started predicate");
        helper.assertTrue(quest.stages().containsKey("coast_final"), "coast_final stage missing");
        helper.assertTrue(quest.stages().containsKey("dark_roof_final"), "dark_roof_final stage missing");
        helper.assertTrue(quest.stages().containsKey("coast_chosen"), "coast_chosen stage missing");
        helper.assertTrue(quest.stages().containsKey("dark_roof_chosen"), "dark_roof_chosen stage missing");
        helper.assertValueEqual(
                quest.tracker().steps().get("started").text(),
                "Choose the final atlas horizon from the cartographer's branch options.",
                "choose horizon proof tracker");
        helper.assertValueEqual(
                quest.tracker().steps().get("coast_final").text(),
                "Reach the Ocean Monument, then bring prismarine crystals and shards.",
                "choose horizon coast proof tracker");
        helper.assertValueEqual(
                quest.tracker().steps().get("dark_roof_final").text(),
                "Reach the Woodland Mansion, then bring books and a totem.",
                "choose horizon dark roof proof tracker");
        helper.assertValueEqual(quest.tracker().steps().get("coast_chosen").progress(), 1.0F, "choose horizon coast return");
        helper.assertValueEqual(quest.tracker().steps().get("dark_roof_chosen").progress(), 1.0F, "choose horizon dark roof return");

        VillagerQuestSavedData.QuestProgress branchProgress = new VillagerQuestSavedData().getOrCreate(
                UUID.fromString("00000000-0000-0000-0000-000000000111"),
                questId);
        branchProgress.start(
                UUID.fromString("00000000-0000-0000-0000-000000000112"),
                Level.OVERWORLD,
                null,
                0L);
        branchProgress.setCurrentStage("coast_final");
        Set<String> coastObjectiveIds = QuestObjectiveQuery.activeObjectives(quest, branchProgress).stream()
                .map(QuestDefinition.Objective::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        helper.assertTrue(coastObjectiveIds.contains("coast_final.visit_monument"), "coast branch visit objective missing");
        helper.assertTrue(coastObjectiveIds.contains("coast_final.bring_prismarine_crystals"), "coast branch crystal objective missing");
        helper.assertFalse(coastObjectiveIds.contains("dark_roof_final.bring_books"), "dark roof objective leaked into coast branch");
        Set<ResourceLocation> coastHandIns = QuestObjectiveQuery.requiredItemHandIns(quest, branchProgress).stream()
                .map(QuestDefinition.Objective::item)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        helper.assertValueEqual(
                coastHandIns,
                Set.of(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "prismarine_crystals"),
                        ResourceLocation.fromNamespaceAndPath("minecraft", "prismarine_shard")),
                "coast branch hand-ins leaked another route");

        DialogueTreeDefinition generatedTree = DialogueTreeResources
                .tree(helper.getLevel().getServer(), LOCALE, QuestDialogueCompiler.treeId(questId))
                .orElseThrow(() -> new GameTestAssertException("choose_the_horizon generated dialogue tree missing"));
        DialogueTreeDefinition.Node responsesNode = generatedTree
                .node("stage.started.slot.offer")
                .orElseThrow(() -> new GameTestAssertException("choose_the_horizon offer node missing"));
        assertRouteResponse(
                helper,
                responsesNode,
                "coast",
                "coast_final",
                VillagerRetaliation.id("atlas_coast_route"));
        assertRouteResponse(
                helper,
                responsesNode,
                "dark_roof",
                "dark_roof_final",
                VillagerRetaliation.id("atlas_dark_roof_route"));

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builtInChooseTheHorizonBranchesRoundTripResponseHistory(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        VillagerQuestResources.clearCache();
        DialogueTreeResources.clearCache();
        ServerLevel level = helper.getLevel();
        ResourceLocation questId = VillagerRetaliation.id("choose_the_horizon");
        ResourceLocation treeId = QuestDialogueCompiler.treeId(questId);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        configureChooseTheHorizonProvider(level, villager);
        markQuestCompleted(level, player.getUUID(), VillagerRetaliation.id("the_atlas_test"));

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            assertChooseTheHorizonBranchRuntime(
                    helper,
                    level,
                    player,
                    villager,
                    questId,
                    treeId,
                    "coast",
                    "coast_final",
                    VillagerRetaliation.id("atlas_coast_route"));
            VillagerQuestService.debugRemoveQuest(player, questId);
            DialogueTreeService.clearRuntimeState();
            assertChooseTheHorizonBranchRuntime(
                    helper,
                    level,
                    player,
                    villager,
                    questId,
                    treeId,
                    "dark_roof",
                    "dark_roof_final",
                    VillagerRetaliation.id("atlas_dark_roof_route"));
            helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "choose_the_horizon branch runtime emitted diagnostics");
            DatapackDiagnostics.clear();
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            villager.discard();
            DialogueTreeService.clearRuntimeState();
            DialogueTreeResources.clearCache();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questDialogueTreesCoverEveryQuest(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QuestDialogueCatalog catalog = VillagerQuestResources.questDialogueCatalog(server);
        for (QuestDefinition quest : quests(helper)) {
            if (catalog.hasGeneratedQuestDialogue(quest.id())) {
                assertGeneratedQuestDialogueLifecycle(helper, server, quest.id(), catalog);
                continue;
            }
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
        Set<ResourceLocation> mobKillQuests = VillagerQuestResources
                .questIdsWithObjective(server, QuestDefinition.ObjectiveType.MOB_KILL);
        helper.assertValueEqual(mobKillQuests.size(), 21, "mob-kill quest index size");
        assertContainsAll(helper, mobKillQuests, Set.of(
                VillagerRetaliation.id("beacon_polish"),
                VillagerRetaliation.id("ender_freight"),
                VillagerRetaliation.id("fortress_line"),
                VillagerRetaliation.id("timber_brace")), "expanded mob-kill quest ids");
        helper.assertTrue(
                VillagerQuestResources.memoryEventQuestIds(server, VillagerRetaliation.id("player_defended_village"))
                        .contains(VillagerRetaliation.id("roads_that_remember")),
                "memory-event quest index is missing roads_that_remember");
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
    public static void finalQuestCompatibilityGuardsStayIndexedAndBounded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<CompiledQuest> compiled = VillagerQuestResources.compiledQuests(server).stream()
                .filter(quest -> VillagerRetaliation.MOD_ID.equals(quest.id().getNamespace()))
                .sorted(Comparator.comparing(quest -> quest.id().toString()))
                .toList();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        int v1 = 0;
        int v2 = 0;
        int triggerCount = 0;
        int continuousTriggerCount = 0;
        for (CompiledQuest quest : compiled) {
            helper.assertTrue(ids.add(quest.id()), "duplicate compiled quest id " + quest.id());
            if (quest.schemaVersion() == QuestSchemaVersion.V1) {
                v1++;
            } else if (quest.schemaVersion() == QuestSchemaVersion.V2) {
                v2++;
            }
            helper.assertValueEqual(
                    quest.objectivesById().size(),
                    quest.objectives().size(),
                    quest.id() + " objective index size");
            helper.assertValueEqual(
                    quest.stagesById().size(),
                    quest.stages().size(),
                    quest.id() + " stage index size");
            helper.assertValueEqual(
                    quest.triggerIndex().triggers().size(),
                    quest.triggers().size(),
                    quest.id() + " trigger index size");
            triggerCount += quest.triggers().size();
            continuousTriggerCount += quest.triggerIndex().continuousTriggers().size();
            for (CompiledQuestTrigger trigger : quest.triggers()) {
                if (trigger.definition().stages().isEmpty()) {
                    helper.assertTrue(
                            quest.triggerIndex().candidates(trigger.definition().event(), "").contains(trigger),
                            quest.id() + "/" + trigger.id() + " missing global trigger candidate");
                } else {
                    for (String stage : trigger.definition().stages()) {
                        helper.assertTrue(
                                quest.triggerIndex().candidates(trigger.definition().event(), stage).contains(trigger),
                                quest.id() + "/" + trigger.id() + " missing stage trigger candidate " + stage);
                    }
                }
            }
        }
        helper.assertValueEqual(compiled.size(), EXPECTED_QUEST_COUNT, "compiled compatibility quest count");
        helper.assertValueEqual(v1, 0, "compiled catalog still contains v1 quests");
        helper.assertValueEqual(v2, EXPECTED_QUEST_COUNT, "compiled v2 quest count");
        helper.assertTrue(triggerCount > 0, "compiled catalog lost quest triggers");
        helper.assertTrue(continuousTriggerCount > 0, "compiled catalog lost continuous trigger throttles");

        DatapackDiagnostics.clear();
        for (int index = 0; index < 100; index++) {
            DatapackDiagnostics.warnSkippedEntry(
                    VillagerRetaliation.id("diagnostics/bounds_" + index),
                    "quest",
                    "entries[" + index + "]",
                    "bounds check");
        }
        helper.assertValueEqual(DatapackDiagnostics.recent().size(), 80, "bounded recent diagnostics");
        helper.assertValueEqual(DatapackDiagnostics.structuredRecent().size(), 80, "bounded structured diagnostics");
        DatapackDiagnostics.clear();

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        try {
            QuestDebugTraceService.clear(player);
            for (int index = 0; index < QuestDebugTraceService.capacity() + 10; index++) {
                QuestDebugTraceService.record(
                        player,
                        QuestDebugTraceService.EventType.TRIGGER,
                        VillagerRetaliation.id("trace_bounds"),
                        "event " + index);
            }
            helper.assertValueEqual(
                    QuestDebugTraceService.recent(player, QuestDebugTraceService.capacity() + 10).size(),
                    QuestDebugTraceService.capacity(),
                    "bounded quest trace buffer");
            helper.assertValueEqual(
                    QuestDebugTraceService.recent(player, 3).size(),
                    3,
                    "bounded quest trace query limit");
            QuestDebugTraceService.setEnabled(player, true);
            VillagerQuestService.clearRuntimeState(player);
            helper.assertFalse(QuestDebugTraceService.isEnabled(player),
                    "player cleanup should disable quest tracing");
            helper.assertTrue(QuestDebugTraceService.recent(player, QuestDebugTraceService.capacity()).isEmpty(),
                    "player cleanup should discard buffered quest traces");
        } finally {
            QuestDebugTraceService.clear(player);
            QuestDebugTraceService.setEnabled(player, false);
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
        }

        List<QuestTrackerSyncPayload.QuestItem> manyItems = new ArrayList<>();
        for (int index = 0; index < QuestTrackerSyncPayload.MAX_QUEST_ITEMS + 4; index++) {
            manyItems.add(new QuestTrackerSyncPayload.QuestItem(
                    "minecraft:paper_" + index,
                    "Paper " + index,
                    index));
        }
        List<QuestTrackerSyncPayload.Entry> manyEntries = new ArrayList<>();
        for (int index = 0; index < QuestTrackerSyncPayload.MAX_SYNC_ENTRIES + 4; index++) {
            manyEntries.add(new QuestTrackerSyncPayload.Entry(
                    "villagerretaliation:quest_" + index,
                    "Quest " + index,
                    "Objective " + index,
                    "metadata",
                    index,
                    true,
                    "active",
                    "status",
                    "issuer",
                    "issuer location",
                    manyItems));
        }
        QuestTrackerSyncPayload payload = new QuestTrackerSyncPayload(manyEntries, "", false);
        helper.assertValueEqual(
                payload.entries().size(),
                QuestTrackerSyncPayload.MAX_SYNC_ENTRIES,
                "bounded quest tracker entry payload");
        helper.assertValueEqual(
                payload.entries().getFirst().questItems().size(),
                QuestTrackerSyncPayload.MAX_QUEST_ITEMS,
                "bounded quest tracker item payload");
        helper.assertValueEqual(payload.entries().getFirst().progress(), 0.0F, "tracker progress lower bound");

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
                    if (isAllowedDeferredForcedDialogue(quest, trigger, action)) {
                        continue;
                    }
                    helper.assertFalse(
                            LIVE_CONTEXT_ACTION_KINDS.contains(action.kind()),
                            quest.id() + "/" + trigger.id() + " uses " + action.kind()
                                    + " from " + trigger.event() + ", which may wait on an unloaded quest issuer");
                }
            }
        }

        helper.succeed();
    }

    private static boolean isAllowedDeferredForcedDialogue(
            QuestDefinition quest,
            QuestDefinition.Trigger trigger,
            VillagerActionDefinition action) {
        return action.kind() == VillagerActionDefinition.Kind.FORCED_DIALOGUE
                && quest.id().equals(VillagerRetaliation.id("tales_of_a_lost_civilization"))
                && trigger.id().equals("storm_reminder")
                && trigger.event() == QuestDefinition.TriggerEvent.PROXIMITY
                && action.forcedDialogue().equals("quest.lost_civilization.storm_reminder");
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void datapackDiagnosticsExposeStructuredEntries(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ResourceLocation resourceId = VillagerRetaliation.id("diagnostics/test");
        DatapackDiagnostics.warnInvalidDialogueCondition(
                resourceId,
                "root.conditions[0]",
                "/conditions/0",
                "condition must be an object.");

        List<DatapackDiagnostics.Entry> entries = DatapackDiagnostics.recent();
        helper.assertValueEqual(entries.size(), 1, "diagnostic entry count");
        DatapackDiagnostics.Entry entry = entries.getFirst();
        helper.assertTrue(
                entry.message().contains("condition must be an object"),
                "legacy diagnostic message was not preserved");
        QuestDiagnostic diagnostic = entry.diagnostic();
        helper.assertValueEqual(diagnostic.severity(), QuestDiagnostic.Severity.WARNING, "diagnostic severity");
        helper.assertValueEqual(
                diagnostic.code(),
                "datapack.invalid_dialogue_condition",
                "diagnostic code");
        helper.assertValueEqual(diagnostic.resourceId(), resourceId, "diagnostic resource id");
        helper.assertValueEqual(diagnostic.jsonPointer(), "/conditions/0", "diagnostic json pointer");
        helper.assertFalse(diagnostic.suggestedFix().isBlank(), "diagnostic suggested fix");
        helper.assertValueEqual(
                DatapackDiagnostics.structuredRecent().getFirst(),
                diagnostic,
                "structured recent diagnostic");
        DatapackDiagnostics.clear();

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questRegistryMetadataArtifactMatchesJavaExport(GameTestHelper helper) {
        Path metadataPath = projectPath("tools", "datapack-builder", "quest-registry-metadata.json");
        String checkedIn;
        try {
            checkedIn = normalizeLineEndings(Files.readString(metadataPath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new GameTestAssertException("Could not read quest registry metadata artifact: " + exception.getMessage());
        }
        helper.assertValueEqual(
                normalizeLineEndings(QuestRegistryMetadata.exportJson()),
                checkedIn,
                "quest registry metadata artifact drift");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2ParserAcceptsValidFixture(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ResourceLocation location = VillagerRetaliation.id("quests/v2_valid_fixture.json");
        QuestV2Resource parsed = QuestV2Parser.parse(location, validQuestV2Fixture())
                .orElseThrow(() -> new GameTestAssertException("valid quest module v2 fixture did not parse"));

        helper.assertValueEqual(parsed.id(), VillagerRetaliation.id("v2_valid_fixture"), "v2 fixture id");
        helper.assertValueEqual(parsed.entryStage(), "offer", "v2 entry stage");
        helper.assertValueEqual(parsed.stages().size(), 2, "v2 stage count");
        helper.assertTrue(parsed.stagesById().containsKey("finish"), "v2 finish stage indexed");
        helper.assertTrue(
                VillagerQuestResources.compiledQuest(helper.getLevel().getServer(), parsed.id()).isEmpty(),
                "validated v2 fixture leaked into compiled quest listings before compiler pass");
        helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "valid v2 parser emitted diagnostics");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2ProviderDeathProtectionParsesCompilesAndFallsBackSafely(GameTestHelper helper) {
        for (QuestProviderDeathProtection policy : QuestProviderDeathProtection.values()) {
            JsonObject root = validQuestV2Fixture();
            root.getAsJsonObject("provider").addProperty("death_protection", policy.serializedName());
            ResourceLocation location = VillagerRetaliation.id("quests/death_protection_" + policy.serializedName() + ".json");
            QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root).orElseThrow();
            QuestV2Resource parsed = QuestV2Parser.parse(envelope).orElseThrow();
            helper.assertValueEqual(parsed.provider().deathProtection(), policy, "parsed provider policy");
            helper.assertValueEqual(
                    QuestV2Compiler.compile(parsed, envelope).orElseThrow().provider().deathProtection(),
                    policy,
                    "compiled provider policy");
        }

        DatapackDiagnostics.clear();
        JsonObject invalid = validQuestV2Fixture();
        invalid.getAsJsonObject("provider").addProperty("death_protection", "immortal_forever");
        QuestV2Resource fallback = QuestV2Parser.parse(
                VillagerRetaliation.id("quests/death_protection_invalid.json"), invalid).orElseThrow();
        helper.assertValueEqual(
                fallback.provider().deathProtection(),
                QuestProviderDeathProtection.NONE,
                "invalid provider policy fallback");
        assertRecentDiagnosticPointer(helper, "/provider/death_protection", "using none");
        DatapackDiagnostics.clear();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void afterStartProtectionIsWrittenOnlyBySuccessfulQuestStart(GameTestHelper helper) {
        ResourceLocation location = VillagerRetaliation.id("quests/death_protection_after_start_runtime.json");
        JsonObject root = validQuestV2Fixture();
        root.getAsJsonObject("provider").addProperty("death_protection", "after_start");
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root).orElseThrow();
        CompiledQuest compiled = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager provider = spawnVillager(helper, new BlockPos(2, 2, 2));
        VillagerQuestResources.installCompiledTestCatalog(helper.getLevel().getServer(), List.of(compiled));
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        try {
            helper.assertTrue(
                    VillagerQuestDeathProtectionService.permanentAfterStartQuests(provider).isEmpty(),
                    "offering a quest should not protect its provider");
            helper.assertTrue(
                    VillagerQuestService.debugStartQuest(player, provider, compiled.id(), true).started(),
                    "after_start quest should start");
            helper.assertValueEqual(
                    VillagerQuestDeathProtectionService.permanentAfterStartQuests(provider),
                    Set.of(compiled.id()),
                    "successful start should persist its exact quest id");

            VillagerQuestService.debugStartQuest(player, provider, compiled.id(), true);
            helper.assertValueEqual(
                    VillagerQuestDeathProtectionService.permanentAfterStartQuests(provider).size(),
                    1,
                    "duplicate start should not duplicate protection state");
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void whileActiveProtectionUsesExactProviderAndEndsWithProgress(GameTestHelper helper) {
        ResourceLocation location = VillagerRetaliation.id("quests/death_protection_while_active_runtime.json");
        JsonObject root = validQuestV2Fixture();
        root.getAsJsonObject("provider").addProperty("death_protection", "while_active");
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root).orElseThrow();
        CompiledQuest compiled = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager provider = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager other = spawnVillager(helper, new BlockPos(4, 2, 2));
        VillagerQuestResources.installCompiledTestCatalog(helper.getLevel().getServer(), List.of(compiled));
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        try {
            helper.assertTrue(
                    VillagerQuestService.debugStartQuest(player, provider, compiled.id(), true).started(),
                    "while_active quest should start");
            helper.assertValueEqual(
                    VillagerQuestDeathProtectionService.activeWhileActiveQuests(helper.getLevel(), provider),
                    Set.of(compiled.id()),
                    "exact provider should be protected during active progress");
            helper.assertTrue(
                    VillagerQuestDeathProtectionService.activeWhileActiveQuests(helper.getLevel(), other).isEmpty(),
                    "different villager should not inherit provider protection");

            VillagerQuestSavedData data = VillagerQuestSavedData.get(helper.getLevel());
            data.get(player.getUUID(), compiled.id()).abandon(helper.getLevel().getGameTime(), false);
            data.setDirty();
            helper.assertTrue(
                    VillagerQuestDeathProtectionService.activeWhileActiveQuests(helper.getLevel(), provider).isEmpty(),
                    "terminal progress should end while_active protection");
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void pendingPartyRewardProtectsItsExactProvider(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager provider = spawnVillager(helper, new BlockPos(2, 2, 2));
        ResourceLocation questId = VillagerRetaliation.id("pending_party_reward_protection_fixture");
        VillagerQuestSavedData data = VillagerQuestSavedData.get(helper.getLevel());
        VillagerQuestSavedData.QuestProgress progress = data.getOrCreate(player.getUUID(), questId);
        progress.start(provider.getUUID(), helper.getLevel().dimension(), provider.blockPosition(), 0L);
        progress.complete(1L, false);
        progress.markPendingPartyReward();
        data.setDirty();
        try {
            helper.assertValueEqual(
                    VillagerQuestDeathProtectionService.pendingPartyRewardQuests(helper.getLevel(), provider),
                    Set.of(questId),
                    "pending party reward should protect its exact provider");
            progress.markPartyRewardClaimed();
            helper.assertTrue(
                    VillagerQuestDeathProtectionService.pendingPartyRewardQuests(helper.getLevel(), provider).isEmpty(),
                    "claiming the pending reward should release provider protection");
        } finally {
            data.remove(player.getUUID(), questId);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2CompilerFlattensIntoCanonicalRuntimeModel(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ResourceLocation location = VillagerRetaliation.id("quests/v2_valid_fixture.json");
        JsonObject root = validQuestV2Fixture();
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root)
                .orElseThrow(() -> new GameTestAssertException("v2 compiler fixture envelope did not parse"));
        QuestV2Resource parsed = QuestV2Parser.parse(envelope)
                .orElseThrow(() -> new GameTestAssertException("v2 compiler fixture did not parse"));
        CompiledQuest compiled = QuestV2Compiler.compile(parsed, envelope)
                .orElseThrow(() -> new GameTestAssertException("v2 compiler fixture did not compile"));

        helper.assertValueEqual(compiled.schemaVersion(), QuestSchemaVersion.V2, "compiled v2 schema version");
        helper.assertValueEqual(
                compiled.objectives().stream().map(CompiledQuestObjective::id).toList(),
                List.of("offer.talk", "finish.done"),
                "flattened v2 objective ids");
        helper.assertValueEqual(
                compiled.objectivesById().get("offer.talk").source().jsonPointer(),
                "/stages/0/objectives/0",
                "v2 objective source pointer");
        helper.assertValueEqual(compiled.stages().getFirst().id(), "offer", "v2 entry stage order");
        helper.assertValueEqual(
                compiled.stagesById().get("offer").definition().objectives(),
                List.of("offer.talk"),
                "v2 stage-local objective ownership");
        helper.assertValueEqual(
                compiled.stagesById().get("offer").definition().completeWhen().getFirst().objective(),
                "offer.talk",
                "v2 complete_when objective reference");
        helper.assertValueEqual(
                compiled.triggersByEvent().get(QuestDefinition.TriggerEvent.STARTED).getFirst().id(),
                "lifecycle.on_start",
                "v2 lifecycle trigger");
        helper.assertValueEqual(
                compiled.triggersByEvent().get(QuestDefinition.TriggerEvent.PROGRESS).getFirst().source().jsonPointer(),
                "/events/0",
                "v2 event source pointer");
        helper.assertValueEqual(compiled.rewards().definition().experience(), 5, "v2 reward action folded into XP");
        QuestDefinition compiledDefinition = compiled.asQuestDefinition();
        helper.assertValueEqual(
                compiledDefinition.tracker().steps().get("finish").text(),
                "Return to {issuer}",
                "v2 stage ui did not compile to a stage tracker step");
        helper.assertFalse(
                compiledDefinition.objectives().get(1).tracker().hasActiveDisplay(),
                "v2 stage ui leaked into objective tracker display");

        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.setCurrentStage("offer");
        QuestTriggerDispatchResult dispatch = QuestTriggerDispatcher.dispatchAtGameTime(
                null,
                200L,
                compiledDefinition,
                compiled.triggerIndex(),
                progress,
                QuestDefinition.TriggerEvent.PROGRESS,
                (context, definition, questProgress, trigger) -> true);
        helper.assertValueEqual(dispatch.trace().candidateTriggers(), 1, "bounded v2 progress candidates");
        helper.assertValueEqual(dispatch.trace().evaluatedTriggers(), 1, "bounded v2 progress evaluations");
        helper.assertValueEqual(dispatch.trace().matchedTriggers(), 1, "bounded v2 progress matches");
        DatapackDiagnostics.clear();

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2CompilerPreservesAdvancedRuntimeRules(GameTestHelper helper) {
        JsonObject root = validQuestV2Fixture();
        JsonObject metadata = root.getAsJsonObject("metadata");
        metadata.addProperty("revision", 4);
        metadata.add("migration", JsonParser.parseString("""
                {
                  "active_policy": "reset_stage",
                  "stage_aliases": {"old_offer": "offer"},
                  "objective_aliases": {"old_talk": "offer.talk"}
                }
                """));
        JsonObject availability = root.getAsJsonObject("availability");
        availability.add("active", JsonParser.parseString("""
                {
                  "conditions": [{"type": "quest_fact", "tag": "active"}],
                  "hide_when_unmet": true,
                  "pause_progress_when_unmet": false
                }
                """));
        availability.add("expiration", JsonParser.parseString("""
                {
                  "after_ticks": 2400,
                  "conditions": [{"type": "quest_fact", "tag": "expired"}],
                  "consume": true,
                  "allow_repickup": false,
                  "notify": false
                }
                """));
        availability.add("branch", JsonParser.parseString("""
                {
                  "exclusive_group": "villagerretaliation:test_route",
                  "exclusive_on": "completed",
                  "blocks_on_start": ["villagerretaliation:start_blocked"],
                  "blocks_on_completion": ["villagerretaliation:finish_blocked"]
                }
                """));
        JsonObject firstStage = root.getAsJsonArray("stages").get(0).getAsJsonObject();
        firstStage.add("completion", JsonParser.parseString("{\"mode\": \"any\", \"count\": 1}"));
        firstStage.add("complete_when", JsonParser.parseString("""
                [{"type": "quest_fact", "scope": "quest", "key": "route_ready", "value": "yes"}]
                """));
        firstStage.add("bonuses", JsonParser.parseString("""
                [{
                  "id": "swift_choice",
                  "when": ["talk"],
                  "actions": [{"type": "experience", "amount": 3}]
                }]
                """));
        JsonObject objectiveUi = new JsonObject();
        objectiveUi.addProperty("tracker_text", "Choose a route.");
        objectiveUi.addProperty("tracker_complete_text", "Route chosen.");
        firstStage.getAsJsonArray("objectives").get(0).getAsJsonObject().add("ui", objectiveUi);
        JsonObject rewards = root.getAsJsonObject("rewards");
        rewards.addProperty("memory_event", "villagerretaliation:test_memory");
        rewards.addProperty("memory_scope", "village");
        JsonObject questUi = root.getAsJsonObject("ui");
        questUi.addProperty("icon", "minecraft:filled_map");
        questUi.addProperty("color", "#d4a35a");
        questUi.addProperty("outline_color", "#201408");
        questUi.addProperty("priority", 25);
        questUi.addProperty("hidden", true);

        ResourceLocation location = VillagerRetaliation.id("quests/v2_parity_fixture.json");
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root).orElseThrow();
        CompiledQuest compiled = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
        QuestDefinition definition = compiled.asQuestDefinition();
        helper.assertValueEqual(definition.revision().number(), 4, "v2 definition revision");
        helper.assertValueEqual(
                definition.revision().activePolicy(),
                QuestDefinition.RevisionPolicy.RESET_STAGE,
                "v2 revision policy");
        helper.assertValueEqual(
                definition.revision().stageAliases().get("old_offer"),
                "offer",
                "v2 stage migration alias");
        helper.assertTrue(definition.rules().activeState().hideWhenUnmet(), "v2 active hide rule");
        helper.assertFalse(definition.rules().activeState().pauseProgressWhenUnmet(), "v2 active pause rule");
        helper.assertValueEqual(definition.rules().expiration().afterTicks(), 2400L, "v2 expiration ticks");
        helper.assertTrue(definition.rules().expiration().consume(), "v2 expiration consume rule");
        helper.assertValueEqual(
                definition.rules().branching().exclusiveOn(),
                QuestDefinition.BranchLockEvent.COMPLETED,
                "v2 branch lock event");
        helper.assertTrue(
                definition.rules().branching().blocksOnStart().contains(VillagerRetaliation.id("start_blocked")),
                "v2 blocks_on_start rule");
        helper.assertTrue(
                definition.stages().get("offer").completeWhen().getFirst().objective().isBlank(),
                "v2 conditional complete_when should remain a condition predicate");
        helper.assertFalse(
                definition.stages().get("offer").completeWhen().getFirst().conditions().isEmpty(),
                "v2 conditional complete_when lost its condition");
        helper.assertValueEqual(
                definition.stages().get("offer").completionMode(),
                QuestDefinition.CompletionMode.ANY,
                "v2 stage completion mode");
        helper.assertValueEqual(
                definition.stages().get("offer").bonuses().getFirst().when().getFirst().objective(),
                "offer.talk",
                "v2 bonus objective canonicalization");
        helper.assertValueEqual(
                definition.stages().get("offer").bonuses().getFirst().actions().getFirst().amount(),
                3,
                "v2 bonus action");
        helper.assertValueEqual(
                definition.objectives().getFirst().tracker().completeText(),
                "Route chosen.",
                "v2 objective completion tracker text");
        helper.assertValueEqual(definition.tracker().icon(), ResourceLocation.withDefaultNamespace("filled_map"), "v2 journal icon");
        helper.assertValueEqual(definition.tracker().color(), "#d4a35a", "v2 journal color");
        helper.assertValueEqual(definition.tracker().outlineColor(), "#201408", "v2 journal outline color");
        helper.assertValueEqual(definition.tracker().priority(), 25, "v2 journal priority");
        helper.assertTrue(definition.tracker().hidden(), "v2 journal hidden flag");
        helper.assertValueEqual(
                definition.rewards().memoryScope(),
                com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope.VILLAGE,
                "v2 reward memory scope");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2EntryStageRemainsAuthoritativeWhenStartedStageExists(GameTestHelper helper) {
        ResourceLocation location = VillagerRetaliation.id("quests/v2_entry_stage_contract.json");
        JsonObject root = validQuestV2Fixture();
        root.addProperty("id", "villagerretaliation:v2_entry_stage_contract");
        root.addProperty("entry_stage", "prologue");
        JsonArray stages = new JsonArray();
        stages.add(JsonParser.parseString("""
                {"id":"prologue","objectives":[],"next":"started"}
                """).getAsJsonObject());
        stages.add(JsonParser.parseString("""
                {"id":"started","objectives":[]}
                """).getAsJsonObject());
        root.add("stages", stages);
        root.remove("events");

        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root)
                .orElseThrow(() -> new GameTestAssertException("entry-stage fixture envelope did not parse"));
        QuestV2Resource parsed = QuestV2Parser.parse(envelope)
                .orElseThrow(() -> new GameTestAssertException("entry-stage fixture did not parse"));
        CompiledQuest compiled = QuestV2Compiler.compile(parsed, envelope)
                .orElseThrow(() -> new GameTestAssertException("entry-stage fixture did not compile"));
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();

        helper.assertValueEqual(parsed.entryStage(), "prologue", "parsed authored entry stage");
        helper.assertValueEqual(compiled.entryStage(), "prologue", "compiled authored entry stage");
        QuestLifecycleService.initializeStage(compiled, progress, 20L);
        helper.assertValueEqual(progress.currentStage(), "prologue", "runtime authored entry stage");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2PreservesEveryOrderedPrerequisite(GameTestHelper helper) {
        ResourceLocation location = VillagerRetaliation.id("quests/v2_prerequisite_contract.json");
        JsonObject root = validQuestV2Fixture();
        root.addProperty("id", "villagerretaliation:v2_prerequisite_contract");
        JsonArray prerequisites = new JsonArray();
        prerequisites.add("villagerretaliation:first");
        prerequisites.add("villagerretaliation:second");
        prerequisites.add("villagerretaliation:third");
        root.getAsJsonObject("availability").add("prerequisites", prerequisites);
        List<ResourceLocation> expected = List.of(
                VillagerRetaliation.id("first"),
                VillagerRetaliation.id("second"),
                VillagerRetaliation.id("third"));

        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root)
                .orElseThrow(() -> new GameTestAssertException("prerequisite fixture envelope did not parse"));
        QuestV2Resource parsed = QuestV2Parser.parse(envelope)
                .orElseThrow(() -> new GameTestAssertException("prerequisite fixture did not parse"));
        CompiledQuest compiled = QuestV2Compiler.compile(parsed, envelope)
                .orElseThrow(() -> new GameTestAssertException("prerequisite fixture did not compile"));

        helper.assertValueEqual(parsed.availability().prerequisites(), expected, "parsed prerequisites");
        helper.assertValueEqual(compiled.prerequisites(), expected, "compiled prerequisites");
        helper.assertValueEqual(compiled.asQuestDefinition().prerequisites(), expected, "runtime prerequisites");
        List<QuestTrackerSyncPayload.Prerequisite> presented = QuestTrackerPresenter.prerequisites(
                null,
                compiled.asQuestDefinition(),
                ResourceLocation::toString,
                id -> id.equals(expected.getFirst()));
        helper.assertValueEqual(
                presented.stream().map(QuestTrackerSyncPayload.Prerequisite::questId).toList(),
                expected.stream().map(ResourceLocation::toString).toList(),
                "presented prerequisite order");
        helper.assertTrue(presented.getFirst().met(), "first prerequisite should be met");
        helper.assertFalse(presented.get(1).met(), "second prerequisite should remain locked");
        helper.assertFalse(presented.get(2).met(), "third prerequisite should remain locked");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2CompiledQuestUsesRuntimeServices(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ServerLevel level = helper.getLevel();
        ResourceLocation location = VillagerRetaliation.id("quests/v2_runtime_fixture.json");
        JsonObject root = runtimeQuestV2Fixture();
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root)
                .orElseThrow(() -> new GameTestAssertException("v2 runtime fixture envelope did not parse"));
        QuestV2Resource parsed = QuestV2Parser.parse(envelope)
                .orElseThrow(() -> new GameTestAssertException("v2 runtime fixture did not parse"));
        CompiledQuest compiled = QuestV2Compiler.compile(parsed, envelope)
                .orElseThrow(() -> new GameTestAssertException("v2 runtime fixture did not compile"));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        VillagerProfileManager.setAttribute(
                level, villager, VillagerSocialAttribute.KNOWLEDGE, 50);

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(
                    level.getServer(),
                    List.of(compiled),
                    QuestDialogueCompiler.compile(parsed, envelope));
            int experienceBefore = player.totalExperience;
            VillagerQuestService.DebugStartResult started =
                    VillagerQuestService.debugStartQuest(player, villager, compiled.id(), true);
            helper.assertTrue(started.started(), "v2 debug start failed: " + started.message());
            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);

            VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
            VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), compiled.id());
            helper.assertTrue(progress != null, "v2 runtime progress was not created");
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.ACTIVE, "v2 started state");
            helper.assertValueEqual(progress.currentStage(), "offer", "v2 initial stage");
            helper.assertValueEqual(data.getTrackedQuest(player.getUUID()), compiled.id(), "v2 tracked quest after start");
            QuestTrackerSyncPayload.Entry activeEntry = VillagerQuestService.debugTrackerEntryForTests(
                    player,
                    context,
                    compiled.asQuestDefinition(),
                    progress,
                    true);
            helper.assertValueEqual(activeEntry.objective(), "Prepare the v2 fixture.", "v2 objective tracker text");
            helper.assertValueEqual(activeEntry.status(), "Active", "v2 active journal status");

            VillagerQuestFacts.get(level).setVariable(
                    QuestScopeKey.quest(player.getUUID(), compiled.id()),
                    "ready",
                    "yes");
            player.tickCount = 0;
            VillagerQuestService.onPlayerTick(player);
            helper.assertValueEqual(progress.currentStage(), "done", "v2 stage advanced through runtime scan");
            helper.assertTrue(progress.objectiveComplete("offer.ready"), "v2 fact objective did not complete");
            QuestTrackerSyncPayload.Entry doneEntry = VillagerQuestService.debugTrackerEntryForTests(
                    player,
                    context,
                    compiled.asQuestDefinition(),
                    progress,
                    true);
            helper.assertValueEqual(doneEntry.objective(), "Return to the issuer.", "v2 stage tracker text");
            helper.assertValueEqual(doneEntry.status(), "Ready to turn in", "v2 journal ready status");
            helper.assertTrue(doneEntry.metadata().contains("Ready to turn in"), "v2 journal metadata omitted status");
            helper.assertValueEqual(doneEntry.progress(), 1.0F, "v2 ready tracker progress");

            CompoundTag activeSaved = data.save(new CompoundTag(), level.registryAccess());
            VillagerQuestSavedData activeLoaded = VillagerQuestSavedData.load(activeSaved, level.registryAccess());
            VillagerQuestSavedData.QuestProgress activeLoadedProgress = activeLoaded.get(player.getUUID(), compiled.id());
            helper.assertTrue(activeLoadedProgress != null, "v2 active progress did not reload");
            helper.assertValueEqual(activeLoadedProgress.currentStage(), "done", "v2 active reload stage");
            helper.assertValueEqual(activeLoaded.getTrackedQuest(player.getUUID()), compiled.id(), "v2 tracked quest after reload");

            VillagerQuestService.performAction(
                    context,
                    compiled.id(),
                    VillagerActionDefinition.QuestAction.TURN_IN)
                    .orElseThrow(() -> new GameTestAssertException("v2 turn-in produced no outcome"));
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.COMPLETED, "v2 completed state");
            helper.assertValueEqual(player.totalExperience, experienceBefore + 7, "v2 reward XP");
            helper.assertValueEqual(
                    VillagerProfileManager.getOrCreateProfile(level, villager)
                            .socialAttributes().knowledge(),
                    52, "one-shot quest completion should add two provider knowledge");

            CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
            VillagerQuestSavedData loaded = VillagerQuestSavedData.load(saved, level.registryAccess());
            VillagerQuestSavedData.QuestProgress loadedProgress = loaded.get(player.getUUID(), compiled.id());
            helper.assertTrue(loadedProgress != null, "v2 completed progress did not reload");
            helper.assertValueEqual(
                    loadedProgress.state(),
                    VillagerQuestSavedData.QuestState.COMPLETED,
                    "v2 reloaded completed state");
            QuestTrackerSyncPayload.Entry entry = QuestTrackerPresenter.entry(new QuestTrackerPresenter.EntryInput(
                    player,
                    compiled.asQuestDefinition(),
                    null,
                    null,
                    Map.of(),
                    "",
                    "",
                    "",
                    List.of(),
                    1.0F,
                    true,
                    loadedProgress.state()));
            helper.assertValueEqual(entry.questId(), compiled.id().toString(), "v2 tracker entry quest id");
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            villager.discard();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questDebugToolsExplainTraceAndDryRun(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ServerLevel level = helper.getLevel();
        ResourceLocation location = VillagerRetaliation.id("quests/v2_debug_tools_fixture.json");
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, validQuestV2Fixture())
                .orElseThrow(() -> new GameTestAssertException("v2 debug fixture envelope did not parse"));
        QuestV2Resource parsed = QuestV2Parser.parse(envelope)
                .orElseThrow(() -> new GameTestAssertException("v2 debug fixture did not parse"));
        CompiledQuest compiled = QuestV2Compiler.compile(parsed, envelope)
                .orElseThrow(() -> new GameTestAssertException("v2 debug fixture did not compile"));
        QuestDialogueCatalog dialogueCatalog = QuestDialogueCompiler.compile(parsed, envelope);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(compiled), dialogueCatalog);

            VillagerQuestService.DebugInspectResult availability =
                    VillagerQuestService.debugWhyAvailable(player, villager, compiled.id());
            helper.assertTrue(availability.found(), "debug availability result missing");
            helper.assertTrue(
                    availability.lines().stream().anyMatch(line -> line.contains("available=false")),
                    "debug availability did not explain unavailable offer: " + availability.lines());
            helper.assertTrue(
                    availability.lines().stream().anyMatch(line -> line.contains("provider match=false")),
                    "debug availability did not include provider failure: " + availability.lines());

            VillagerQuestService.DebugStartResult started =
                    VillagerQuestService.debugStartQuest(player, villager, compiled.id(), true);
            helper.assertTrue(started.started(), "debug fixture quest did not force-start: " + started.message());

            VillagerQuestService.DebugInspectResult objectives =
                    VillagerQuestService.debugObjectives(player, compiled.id());
            helper.assertTrue(
                    objectives.lines().stream().anyMatch(line -> line.contains("objective offer.talk")),
                    "debug objectives omitted talk objective: " + objectives.lines());

            VillagerQuestService.DebugInspectResult dryRun =
                    VillagerQuestService.debugDryRunTriggerActions(player, compiled.id(), "progress");
            helper.assertTrue(
                    dryRun.lines().stream().anyMatch(line ->
                            line.contains("type=tracker")
                                    && line.contains("status=skipped")
                                    && line.contains("dry run")),
                    "debug dry-run did not report tracker action: " + dryRun.lines());

            VillagerQuestService.DebugInspectResult trigger =
                    VillagerQuestService.debugFireTrigger(player, compiled.id(), QuestDefinition.TriggerEvent.PROGRESS);
            helper.assertTrue(
                    trigger.lines().stream().anyMatch(line -> line.contains("trigger_result")
                            && line.contains("candidates=1")),
                    "debug trigger firing did not report candidate count: " + trigger.lines());

            VillagerQuestService.DebugInspectResult capture =
                    VillagerQuestService.debugTraceQuest(player, villager, compiled.id());
            assertDebugTraceContains(helper, capture, "type=provider");
            assertDebugTraceContains(helper, capture, "type=condition");
            assertDebugTraceContains(helper, capture, "type=dialogue_slot");
            assertDebugTraceContains(helper, capture, "type=response");
            assertDebugTraceContains(helper, capture, "type=trigger");
            assertDebugTraceContains(helper, capture, "type=objective_progress");
            assertDebugTraceContains(helper, capture, "type=tracker_sync");

            VillagerQuestService.DebugInspectResult stage =
                    VillagerQuestService.debugSetQuestStage(player, compiled.id(), "finish");
            helper.assertTrue(
                    stage.lines().stream().anyMatch(line -> line.contains("changed=true")
                            && line.contains("current=finish")),
                    "debug set_stage did not move to finish: " + stage.lines());

            QuestScopeKey scopeKey = QuestScopeKey.quest(player.getUUID(), compiled.id());
            VillagerQuestFacts facts = VillagerQuestFacts.get(level);
            facts.setVariable(scopeKey, "debug_key", "debug_value");
            facts.addCounter(scopeKey, "debug_count", 2);
            VillagerQuestService.DebugInspectResult factLines =
                    VillagerQuestService.debugFactScope(player, scopeKey.asString());
            helper.assertTrue(
                    factLines.lines().stream().anyMatch(line -> line.contains("debug_key=debug_value")),
                    "debug fact scope omitted variable: " + factLines.lines());
            helper.assertTrue(
                    factLines.lines().stream().anyMatch(line -> line.contains("debug_count=2")),
                    "debug fact scope omitted counter: " + factLines.lines());

            VillagerQuestService.debugTraceSetEnabled(player, true);
            QuestDebugTraceService.clear(player);
            for (int i = 0; i < QuestDebugTraceService.capacity() + 5; i++) {
                QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.NOTE, compiled.id(), "bounded=" + i);
            }
            helper.assertValueEqual(
                    QuestDebugTraceService.recent(player, QuestDebugTraceService.capacity() + 10).size(),
                    QuestDebugTraceService.capacity(),
                    "debug trace capacity");
            helper.assertTrue(
                    QuestDebugTraceService.recent(player, QuestDebugTraceService.capacity()).getFirst().message().contains("bounded=5"),
                    "debug trace did not evict oldest events");
            VillagerQuestService.debugTraceSetEnabled(player, false);
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            QuestDebugTraceService.clear(player);
            QuestDebugTraceService.setEnabled(player, false);
            villager.discard();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2OptionalTrackerDisplayDoesNotBlockReadyStatus(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ServerLevel level = helper.getLevel();
        ResourceLocation location = VillagerRetaliation.id("quests/v2_optional_tracker_fixture.json");
        JsonObject root = optionalTrackerQuestV2Fixture();
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root)
                .orElseThrow(() -> new GameTestAssertException("v2 optional tracker fixture envelope did not parse"));
        QuestV2Resource parsed = QuestV2Parser.parse(envelope)
                .orElseThrow(() -> new GameTestAssertException("v2 optional tracker fixture did not parse"));
        CompiledQuest compiled = QuestV2Compiler.compile(parsed, envelope)
                .orElseThrow(() -> new GameTestAssertException("v2 optional tracker fixture did not compile"));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(compiled));
            VillagerQuestService.DebugStartResult started =
                    VillagerQuestService.debugStartQuest(player, villager, compiled.id(), true);
            helper.assertTrue(started.started(), "v2 optional tracker quest did not start: " + started.message());
            VillagerQuestFacts.get(level).setVariable(
                    QuestScopeKey.quest(player.getUUID(), compiled.id()),
                    "ready",
                    "yes");

            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
            VillagerQuestSavedData.QuestProgress progress =
                    VillagerQuestSavedData.get(level).get(player.getUUID(), compiled.id());
            QuestTrackerSyncPayload.Entry entry = VillagerQuestService.debugTrackerEntryForTests(
                    player,
                    context,
                    compiled.asQuestDefinition(),
                    progress,
                    true);
            helper.assertValueEqual(entry.objective(), "Optional fact optional_done", "v2 optional objective tracker text");
            helper.assertValueEqual(entry.status(), "Ready to turn in", "v2 optional objective blocked ready status");
            helper.assertValueEqual(entry.progress(), 1.0F, "v2 optional objective blocked ready progress");
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            villager.discard();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2DialogueCompilerGeneratesStableDefinitions(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ResourceLocation location = VillagerRetaliation.id("quests/v2_dialogue_fixture.json");
        JsonObject root = dialogueQuestV2Fixture();
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root)
                .orElseThrow(() -> new GameTestAssertException("v2 dialogue fixture envelope did not parse"));
        QuestV2Resource parsed = QuestV2Parser.parse(envelope)
                .orElseThrow(() -> new GameTestAssertException("v2 dialogue fixture did not parse"));

        QuestDialogueCatalog catalog = QuestDialogueCompiler.compile(parsed, envelope);
        QuestDialogueCatalog secondCatalog = QuestDialogueCompiler.compile(parsed, envelope);
        ResourceLocation treeId = QuestDialogueCompiler.treeId(parsed.id());
        DialogueTreeDefinition tree = catalog.tree(treeId)
                .orElseThrow(() -> new GameTestAssertException("v2 generated dialogue tree missing"));
        DialogueTreeDefinition secondTree = secondCatalog.tree(treeId)
                .orElseThrow(() -> new GameTestAssertException("second v2 generated dialogue tree missing"));
        helper.assertValueEqual(
                dialogueTreeSnapshot(secondTree),
                dialogueTreeSnapshot(tree),
                "generated dialogue tree ids drifted between compiles");
        helper.assertValueEqual(
                dialogueTreeSnapshot(tree),
                """
                tree=villagerretaliation:quest_dialogue/v2_dialogue_fixture
                entries=lifecycle.opening->lifecycle.opening|stage.offer.offer->stage.offer.scene.offer_intro|stage.offer.ready->stage.offer.slot.ready|stage.offer.scene.details->stage.offer.scene.details|stage.offer.scene.offer_intro->stage.offer.scene.offer_intro|stage.offer.responses->stage.offer.responses
                node=lifecycle.opening lines=[Welcome to v2 dialogue.] actions=[] conditions=0 responses=[continue[Continue] actions=[] lines=[] conditions=0]
                node=stage.offer.responses lines=[Follow the generated dialogue.] actions=[] conditions=0 responses=[stage_help[What now?] actions=[notification:Quest updated: {quest}] lines=[Keep going.] conditions=0]
                node=stage.offer.scene.details lines=[Bring back proof.] actions=[] conditions=1 responses=[ask[What proof?] actions=[] lines=[Anything marked.] conditions=0]
                node=stage.offer.scene.offer_intro lines=[Can you help with this v2 errand?] actions=[tracker] conditions=0 responses=[accept[I will help.] ->stage.offer.scene.details actions=[quest:start] lines=[] conditions=0|decline[Not now.] actions=[] lines=[Maybe later.] conditions=0]
                node=stage.offer.slot.ready lines=[You look ready.] actions=[] conditions=0 responses=[ready_done[Ready.] actions=[set_variable:quest:ready_dialogue=seen] lines=[] conditions=0]
                """.stripTrailing(),
                "generated v2 dialogue tree snapshot");

        QuestDialogueCatalog.Binding offerBinding = catalog.bindings(parsed.id()).stream()
                .filter(binding -> binding.stageId().equals("offer") && binding.slot().equals("offer"))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("v2 offer binding missing"));
        helper.assertValueEqual(
                offerBinding.source().jsonPointer(),
                "/stages/0/dialogue/offer",
                "v2 generated offer source pointer");
        helper.assertValueEqual(catalog.bindings(parsed.id()).size(), 6, "v2 generated binding count");
        helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "v2 dialogue compiler emitted diagnostics");
        DatapackDiagnostics.clear();

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2EmbeddedDialogueRunsThroughExistingSessions(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ServerLevel level = helper.getLevel();
        EmbeddedDialogueQuest playable = embeddedDialogueQuest("v2_embedded_runtime", false);
        EmbeddedDialogueQuest unavailable = embeddedDialogueQuest("v2_embedded_unavailable", true);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager otherVillager = spawnVillager(helper, new BlockPos(4, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(
                    level.getServer(),
                    List.of(playable.quest(), unavailable.quest()),
                    QuestDialogueCatalog.merge(List.of(playable.dialogueCatalog(), unavailable.dialogueCatalog())));
            DialogueTreeResources.clearCache();
            DialogueTreeService.clearRuntimeState();

            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
            String offerOptionId = DialogueTreeService.entryOptionId(playable.treeId(), "stage.offer.offer");
            assertHasDialogueOption(helper, context, offerOptionId, "v2 embedded offer option");
            helper.assertValueEqual(
                    selectDialogueOption(helper, context, offerOptionId).text(),
                    "Can you take this embedded errand?",
                    "v2 embedded offer text");
            helper.assertValueEqual(
                    selectDialogueOption(
                            helper,
                            context,
                            DialogueTreeService.responseOptionId(playable.treeId(), "decline")).text(),
                    "Not now, then.",
                    "v2 embedded decline text");
            helper.assertTrue(
                    VillagerQuestSavedData.get(level).get(player.getUUID(), playable.quest().id()) == null,
                    "declining embedded v2 dialogue started the quest");

            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            selectDialogueOption(helper, context, offerOptionId);
            helper.assertValueEqual(
                    selectDialogueOption(
                            helper,
                            context,
                            DialogueTreeService.responseOptionId(playable.treeId(), "accept")).text(),
                    "Embedded quest started.",
                    "v2 embedded accept/start text");

            VillagerQuestSavedData.QuestProgress progress =
                    VillagerQuestSavedData.get(level).get(player.getUUID(), playable.quest().id());
            helper.assertTrue(progress != null, "embedded v2 dialogue did not create quest progress");
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.ACTIVE, "embedded v2 active state");
            helper.assertValueEqual(progress.currentStage(), "offer", "embedded v2 initial stage");

            DialogueContext otherContext = VillagerInteractionService.createDialogueContext(level, player, otherVillager);
            assertMissingDialogueOption(
                    helper,
                    otherContext,
                    offerOptionId,
                    "v2 embedded offer from second villager after start");
            assertMissingDialogueOption(
                    helper,
                    otherContext,
                    DialogueTreeService.entryOptionId(playable.treeId(), "stage.offer.reminder"),
                    "v2 embedded reminder from second villager");
            assertMissingDialogueOption(
                    helper,
                    otherContext,
                    DialogueTreeService.entryOptionId(playable.treeId(), "stage.offer.unavailable"),
                    "v2 embedded unavailable fallback from second villager");

            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            helper.assertValueEqual(
                    selectDialogueOption(
                            helper,
                            context,
                            DialogueTreeService.entryOptionId(playable.treeId(), "stage.offer.reminder")).text(),
                    "Embedded reminder active.",
                    "v2 embedded active reminder text");

            VillagerQuestFacts.get(level).setVariable(
                    QuestScopeKey.quest(player.getUUID(), playable.quest().id()),
                    "ready",
                    "yes");
            otherContext = VillagerInteractionService.createDialogueContext(level, player, otherVillager);
            assertMissingDialogueOption(
                    helper,
                    otherContext,
                    DialogueTreeService.entryOptionId(playable.treeId(), "stage.offer.ready"),
                    "v2 embedded ready turn-in from second villager");
            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            helper.assertValueEqual(
                    selectDialogueOption(
                            helper,
                            context,
                            DialogueTreeService.entryOptionId(playable.treeId(), "stage.offer.ready")).text(),
                    "Ready to close embedded quest?",
                    "v2 embedded ready text");
            helper.assertValueEqual(
                    selectDialogueOption(
                            helper,
                            context,
                            DialogueTreeService.responseOptionId(playable.treeId(), "complete")).text(),
                    "Embedded quest complete.",
                    "v2 embedded turn-in text");
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.COMPLETED, "embedded v2 completed state");

            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            helper.assertValueEqual(
                    selectDialogueOption(
                            helper,
                            context,
                            DialogueTreeService.entryOptionId(playable.treeId(), "stage.offer.already_completed")).text(),
                    "Embedded quest already complete.",
                    "v2 embedded already-completed text");
            helper.assertValueEqual(
                    selectDialogueOption(
                            helper,
                            context,
                            DialogueTreeService.entryOptionId(unavailable.treeId(), "stage.offer.unavailable")).text(),
                    "This embedded quest is unavailable.",
                    "v2 embedded unavailable text");
            helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "v2 embedded dialogue emitted diagnostics");
            DatapackDiagnostics.clear();
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            villager.discard();
            otherVillager.discard();
            DialogueTreeService.clearRuntimeState();
            DialogueTreeResources.clearCache();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2ExternalDialogueAndForcedScenesResolve(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ServerLevel level = helper.getLevel();
        ResourceLocation externalTreeId = VillagerRetaliation.id("external/v2_mixed_tree");
        ResourceLocation missingTreeId = VillagerRetaliation.id("external/v2_missing_tree");
        EmbeddedDialogueQuest mixed = embeddedDialogueQuest(
                "v2_external_mixed_runtime",
                externalDialogueQuestV2Fixture("v2_external_mixed_runtime", externalTreeId, missingTreeId));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(mixed.quest()), mixed.dialogueCatalog());
            DialogueTreeResources.installTestTrees(
                    level.getServer(),
                    LOCALE,
                    List.of(externalDialogueTree(externalTreeId)),
                    Set.of());
            DialogueTreeService.clearRuntimeState();
            ForcedDialogueService.clearRuntimeState();

            helper.assertTrue(
                    ForcedDialogueResources
                            .selectCandidates(level.getServer(), ForcedDialogueResources.ForcedDialogueTrigger.QUEST, null)
                            .stream()
                            .anyMatch(definition -> definition.id().equals("quest.lost_civilization.storm_reminder")),
                    "existing quest forced-dialogue resource disappeared");

            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
            String offerOptionId = DialogueTreeService.entryOptionId(mixed.treeId(), "stage.offer.offer");
            helper.assertValueEqual(
                    selectDialogueOption(helper, context, offerOptionId).text(),
                    "Inline mixed offer.",
                    "v2 mixed inline offer text");
            String startResponse = selectDialogueOption(helper, context,
                    DialogueTreeService.responseOptionId(mixed.treeId(), "accept")).text();
            helper.assertValueEqual(
                    startResponse,
                    "Mixed quest started.",
                    "v2 mixed inline start response should preserve the authored response");

            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            String externalReminderOptionId = DialogueTreeService.entryOptionId(externalTreeId, "long_scene");
            helper.assertValueEqual(
                    selectDialogueOption(helper, context, externalReminderOptionId).text(),
                    "External long scene.",
                    "v2 external reminder tree text");
            String externalSceneDefaultOptionId = DialogueTreeService.entryOptionId(externalTreeId, "external_default");
            helper.assertValueEqual(
                    selectDialogueOption(helper, context, externalSceneDefaultOptionId).text(),
                    "External scene default entry.",
                    "v2 external named scene default entry text");
            assertMissingDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(missingTreeId, "missing"),
                    "missing external tree option");
            assertMissingDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(externalTreeId, "not_there"),
                    "missing external entry option");

            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            assertForcedSceneResponse(
                    helper,
                    context,
                    mixed.dialogueCatalog(),
                    "storm_scene",
                    mixed.treeId(),
                    "stage.offer.scene.storm_scene",
                    DialogueTreeService.responseOptionId(mixed.treeId(), "storm_ack"),
                    "Stay ready.",
                    "inline forced scene response text");
            assertForcedSceneResponse(
                    helper,
                    context,
                    mixed.dialogueCatalog(),
                    "external_forced",
                    externalTreeId,
                    "forced_entry",
                    DialogueTreeService.responseOptionId(externalTreeId, "external_ack"),
                    "External forced response.",
                    "external forced scene response text");
            helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "v2 external dialogue emitted diagnostics");
            DatapackDiagnostics.clear();
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            VillagerConversationService.endForPlayer(player, false);
            ForcedDialogueService.clearRuntimeState();
            DialogueTreeService.clearRuntimeState();
            DialogueTreeResources.clearCache();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2LegacyTreeOverridePrecedenceIsSourceAware(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ServerLevel level = helper.getLevel();
        ResourceLocation legacyTreeId = VillagerRetaliation.id("v2_legacy_override_runtime");
        EmbeddedDialogueQuest migrated = embeddedDialogueQuest(
                "v2_legacy_override_runtime",
                embeddedDialogueQuestV2Fixture("v2_legacy_override_runtime"));
        DialogueTreeDefinition legacyTree = singleEntryDialogueTree(
                legacyTreeId,
                "offer",
                "Legacy Offer",
                "Legacy built-in tree.");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(migrated.quest()), migrated.dialogueCatalog());
            DialogueTreeService.clearRuntimeState();
            DialogueTreeResources.installTestTrees(level.getServer(), LOCALE, List.of(legacyTree), Set.of(legacyTreeId));

            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
            assertHasDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(migrated.treeId(), "stage.offer.offer"),
                    "generated inline migrated offer");
            assertMissingDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(legacyTreeId, "offer"),
                    "built-in legacy tree option");

            DialogueTreeResources.installTestTrees(level.getServer(), LOCALE, List.of(legacyTree), Set.of());
            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            assertHasDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(legacyTreeId, "offer"),
                    "higher-priority legacy tree override option");
            helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "legacy override precedence emitted diagnostics");
            DatapackDiagnostics.clear();
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            DialogueTreeService.clearRuntimeState();
            DialogueTreeResources.clearCache();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builtInEggBasketsQuestModuleV2MatchesLegacySemantics(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        VillagerQuestResources.clearCache();
        DialogueTreeResources.clearCache();
        ResourceLocation questId = VillagerRetaliation.id("egg_baskets");
        CompiledQuest compiled = VillagerQuestResources
                .compiledQuest(helper.getLevel().getServer(), questId)
                .orElseThrow(() -> new GameTestAssertException("Missing compiled quest " + questId));
        QuestDefinition quest = compiled.asQuestDefinition();

        helper.assertValueEqual(compiled.schemaVersion(), QuestSchemaVersion.V2, "egg baskets schema version");
        assertEggBasketsV2Semantics(helper, quest);
        helper.assertTrue(
                VillagerQuestResources.questDialogueCatalog(helper.getLevel().getServer()).hasGeneratedQuestDialogue(questId),
                "egg baskets did not compile generated v2 dialogue");
        DialogueTreeDefinition generatedTree = DialogueTreeResources
                .tree(helper.getLevel().getServer(), LOCALE, QuestDialogueCompiler.treeId(questId))
                .orElseThrow(() -> new GameTestAssertException("egg baskets generated dialogue tree missing"));
        DialogueTreeDefinition.Entry offerEntry = generatedTree
                .entry("stage.work.offer")
                .orElseThrow(() -> new GameTestAssertException("egg baskets generated offer entry missing"));
        helper.assertValueEqual(offerEntry.label(), "Egg Baskets", "egg baskets generated offer label");
        helper.assertValueEqual(offerEntry.order(), -20, "egg baskets generated offer order");
        helper.assertFalse(offerEntry.showForBabies(), "egg baskets generated offer baby visibility");
        DialogueTreeDefinition.Entry readyEntry = generatedTree
                .entry("stage.return.turn_in")
                .orElseThrow(() -> new GameTestAssertException("egg baskets generated ready entry missing"));
        helper.assertValueEqual(readyEntry.label(), "About Egg Baskets", "egg baskets generated ready label");
        helper.assertValueEqual(readyEntry.order(), -20, "egg baskets generated ready order");
        DialogueTreeDefinition.Node reminderNode = generatedTree
                .node("stage.work.slot.reminder")
                .orElseThrow(() -> new GameTestAssertException("egg baskets generated reminder node missing"));
        helper.assertValueEqual(
                reminderNode.responses().stream().map(DialogueTreeDefinition.Response::order).toList(),
                List.of(0, 90, 100),
                "egg baskets generated reminder response order");
        helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "egg baskets migration emitted diagnostics");
        DatapackDiagnostics.clear();

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builtInEggBasketsLegacyTreeOverrideRemainsSourceAware(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        VillagerQuestResources.clearCache();
        DialogueTreeResources.clearCache();
        ServerLevel level = helper.getLevel();
        ResourceLocation questId = VillagerRetaliation.id("egg_baskets");
        CompiledQuest compiled = VillagerQuestResources
                .compiledQuest(level.getServer(), questId)
                .orElseThrow(() -> new GameTestAssertException("Missing compiled quest " + questId));
        helper.assertValueEqual(compiled.schemaVersion(), QuestSchemaVersion.V2, "egg baskets schema version");

        DialogueTreeDefinition legacyTree = singleEntryDialogueTree(
                questId,
                "offer",
                "Legacy Egg Baskets",
                "Legacy Egg Baskets override.");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        configureEggBasketsProvider(level, villager);

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            DialogueTreeResources.installTestTrees(level.getServer(), LOCALE, List.of(legacyTree), Set.of(questId));
            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
            assertHasDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(QuestDialogueCompiler.treeId(questId), "stage.work.offer"),
                    "generated egg baskets offer");
            assertMissingDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(questId, "offer"),
                    "built-in legacy egg baskets tree");

            DialogueTreeResources.installTestTrees(level.getServer(), LOCALE, List.of(legacyTree), Set.of());
            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            assertHasDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(questId, "offer"),
                    "higher-priority legacy egg baskets override");
            helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "egg baskets legacy override emitted diagnostics");
            DatapackDiagnostics.clear();
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            villager.discard();
            DialogueTreeService.clearRuntimeState();
            DialogueTreeResources.clearCache();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builtInFirstFarMarkerQuestModuleV2PreservesObjectiveTargetSemantics(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        VillagerQuestResources.clearCache();
        DialogueTreeResources.clearCache();
        ResourceLocation questId = VillagerRetaliation.id("first_far_marker");
        CompiledQuest compiled = VillagerQuestResources
                .compiledQuest(helper.getLevel().getServer(), questId)
                .orElseThrow(() -> new GameTestAssertException("Missing compiled quest " + questId));
        QuestDefinition quest = compiled.asQuestDefinition();

        helper.assertValueEqual(compiled.schemaVersion(), QuestSchemaVersion.V2, "first_far_marker schema version");
        helper.assertFalse(quest.target().hasStructureTarget(), "first marker should use objective-level targeting");
        Set<String> firstMarkerObjectives = quest.objectives().stream()
                .map(QuestDefinition.Objective::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertContainsAll(
                helper,
                firstMarkerObjectives,
                Set.of("survey.visit_ruins", "survey.carry_brush", "survey.bring_copper"),
                "first marker objective ids");
        QuestDefinition.Objective visitRuins = quest.objectives().stream()
                .filter(objective -> objective.id().equals("survey.visit_ruins"))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("first marker visit objective missing"));
        helper.assertValueEqual(visitRuins.structure(), ResourceLocation.fromNamespaceAndPath("minecraft", "trail_ruins"), "first marker target structure");
        helper.assertValueEqual(visitRuins.searchRadius(), 192, "first marker search radius");
        helper.assertValueEqual(visitRuins.discoveryRadius(), 96, "first marker discovery radius");
        helper.assertValueEqual(quest.stages().get("survey").next(), "return", "first marker survey next stage");
        helper.assertValueEqual(quest.tracker().steps().get("survey").text(), "Reach the Trail Ruins near {target_x}, {target_z}.", "first marker travel tracker");
        helper.assertValueEqual(quest.tracker().steps().get("return").progress(), 1.0F, "first marker return tracker progress");

        DialogueTreeDefinition generatedTree = DialogueTreeResources
                .tree(helper.getLevel().getServer(), LOCALE, QuestDialogueCompiler.treeId(questId))
                .orElseThrow(() -> new GameTestAssertException("first_far_marker generated dialogue tree missing"));
        helper.assertValueEqual(
                generatedTree.entry("stage.survey.offer").orElseThrow().label(),
                "First Far Marker",
                "first marker generated offer label");
        helper.assertValueEqual(
                generatedTree.entry("stage.return.turn_in").orElseThrow().label(),
                "About First Far Marker",
                "first marker generated turn-in label");

        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000026");
        UUID villagerId = UUID.fromString("00000000-0000-0000-0000-000000000027");
        BlockPos targetPos = new BlockPos(384, 72, -144);
        VillagerQuestSavedData data = new VillagerQuestSavedData();
        VillagerQuestSavedData.QuestProgress progress = data.getOrCreate(playerId, questId);
        progress.start(villagerId, Level.OVERWORLD, targetPos, 26L);
        progress.setTarget(villagerId, Level.OVERWORLD, targetPos, "survey.visit_ruins");
        progress.setCurrentStage("return");
        progress.markObjectiveComplete("survey.bring_copper");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VillagerQuestSavedData loaded = VillagerQuestSavedData.load(saved, helper.getLevel().registryAccess());
        VillagerQuestSavedData.QuestProgress loadedProgress = loaded.get(playerId, questId);
        helper.assertTrue(loadedProgress != null, "first marker target progress did not reload");
        helper.assertValueEqual(loadedProgress.currentStage(), "return", "first marker reloaded stage");
        helper.assertValueEqual(loadedProgress.targetDimension(), Level.OVERWORLD, "first marker reloaded target dimension");
        helper.assertValueEqual(loadedProgress.targetPos(), targetPos, "first marker reloaded target position");
        helper.assertValueEqual(loadedProgress.targetObjectiveId(), "survey.visit_ruins", "first marker reloaded target objective");
        helper.assertTrue(loadedProgress.objectiveComplete("survey.bring_copper"), "first marker reloaded objective");
        helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "first marker migration emitted diagnostics");
        DatapackDiagnostics.clear();

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builtInFirstFarMarkerLegacyTreeOverrideRemainsSourceAware(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        VillagerQuestResources.clearCache();
        DialogueTreeResources.clearCache();
        ServerLevel level = helper.getLevel();
        ResourceLocation questId = VillagerRetaliation.id("first_far_marker");
        DialogueTreeDefinition legacyTree = singleEntryDialogueTree(
                questId,
                "offer",
                "Legacy First Marker",
                "Legacy First Marker override.");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        configureFirstFarMarkerProvider(level, villager);
        markQuestCompleted(level, player.getUUID(), VillagerRetaliation.id("blank_map_promise"));
        markQuestCompleted(level, player.getUUID(), VillagerRetaliation.id("ink_and_bearings"));

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            DialogueTreeResources.installTestTrees(level.getServer(), LOCALE, List.of(legacyTree), Set.of(questId));
            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
            assertHasDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(QuestDialogueCompiler.treeId(questId), "stage.survey.offer"),
                    "generated first marker offer");
            assertMissingDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(questId, "offer"),
                    "built-in legacy first marker tree");

            DialogueTreeResources.installTestTrees(level.getServer(), LOCALE, List.of(legacyTree), Set.of());
            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            assertHasDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(questId, "offer"),
                    "higher-priority legacy first marker override");
            helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "first marker legacy override emitted diagnostics");
            DatapackDiagnostics.clear();
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            villager.discard();
            DialogueTreeService.clearRuntimeState();
            DialogueTreeResources.clearCache();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builtInTalesLostCivilizationQuestModuleV2PreservesForcedTrigger(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        VillagerQuestResources.clearCache();
        DialogueTreeResources.clearCache();
        ForcedDialogueResources.clearCache();
        ResourceLocation questId = VillagerRetaliation.id("tales_of_a_lost_civilization");
        CompiledQuest compiled = VillagerQuestResources
                .compiledQuest(helper.getLevel().getServer(), questId)
                .orElseThrow(() -> new GameTestAssertException("Missing compiled quest " + questId));
        QuestDefinition quest = compiled.asQuestDefinition();

        helper.assertValueEqual(compiled.schemaVersion(), QuestSchemaVersion.V2, "lost civilization schema version");
        helper.assertValueEqual(quest.target().structure(), ResourceLocation.fromNamespaceAndPath("minecraft", "ancient_city"), "lost civilization target structure");
        helper.assertValueEqual(quest.target().pieces(), List.of(
                "ancient_city/city_center/city_center_1",
                "ancient_city/city_center/city_center_2",
                "ancient_city/city_center/city_center_3"), "lost civilization target pieces");
        helper.assertValueEqual(quest.target().searchRadius(), 256, "lost civilization search radius");
        helper.assertValueEqual(quest.target().discoveryRadius(), 128, "lost civilization discovery radius");
        helper.assertValueEqual(quest.target().proofItem(), ResourceLocation.fromNamespaceAndPath("minecraft", "echo_shard"), "lost civilization proof item");
        Set<String> lostCivilizationObjectives = quest.objectives().stream()
                .map(QuestDefinition.Objective::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertContainsAll(
                helper,
                lostCivilizationObjectives,
                Set.of("survey.visit_city_center", "survey.recover_echo_shard"),
                "lost civilization objective ids");
        helper.assertValueEqual(quest.stages().get("survey").next(), "return", "lost civilization survey next stage");
        helper.assertValueEqual(quest.tracker().steps().get("survey").progress(), 0.25F, "lost civilization travel tracker progress");
        helper.assertValueEqual(quest.tracker().steps().get("return").progress(), 1.0F, "lost civilization return tracker progress");

        QuestDefinition.Trigger stormReminder = quest.triggers().stream()
                .filter(trigger -> trigger.id().equals("storm_reminder"))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("lost civilization storm reminder trigger missing"));
        helper.assertValueEqual(stormReminder.event(), QuestDefinition.TriggerEvent.PROXIMITY, "lost civilization forced trigger event");
        helper.assertValueEqual(stormReminder.cooldownTicks(), 20L * 120L, "lost civilization forced trigger cooldown");
        helper.assertValueEqual(stormReminder.radius(), 10.0D, "lost civilization forced trigger radius");
        helper.assertFalse(stormReminder.repeatable(), "lost civilization forced trigger repeatable");
        helper.assertTrue(
                stormReminder.conditions().stream().anyMatch(DialogueCondition.Weather.class::isInstance),
                "lost civilization forced trigger weather condition missing");
        helper.assertValueEqual(
                stormReminder.actions().stream().map(VillagerActionRegistry::canonicalTypeId).toList(),
                List.of("tracker", "forced_dialogue"),
                "lost civilization forced trigger action order");
        helper.assertTrue(stormReminder.actions().getFirst().flashTracker(), "lost civilization tracker action");
        helper.assertValueEqual(
                stormReminder.actions().get(1).forcedDialogue(),
                "quest.lost_civilization.storm_reminder",
                "lost civilization forced dialogue id");
        helper.assertTrue(
                ForcedDialogueResources
                        .selectCandidates(helper.getLevel().getServer(), ForcedDialogueResources.ForcedDialogueTrigger.QUEST, null)
                        .stream()
                        .anyMatch(definition -> definition.id().equals("quest.lost_civilization.storm_reminder")),
                "lost civilization external forced dialogue resource missing");

        DialogueTreeDefinition generatedTree = DialogueTreeResources
                .tree(helper.getLevel().getServer(), LOCALE, QuestDialogueCompiler.treeId(questId))
                .orElseThrow(() -> new GameTestAssertException("lost civilization generated dialogue tree missing"));
        helper.assertValueEqual(
                generatedTree.entry("stage.survey.offer").orElseThrow().label(),
                "Lost Civilization",
                "lost civilization generated offer label");
        helper.assertValueEqual(
                generatedTree.entry("stage.return.turn_in").orElseThrow().label(),
                "About Lost Civilization",
                "lost civilization generated turn-in label");
        helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "lost civilization migration emitted diagnostics");
        DatapackDiagnostics.clear();

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builtInTalesLostCivilizationForcedTriggerQueuesAndSkipsWithoutIssuer(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        VillagerQuestResources.clearCache();
        DialogueTreeResources.clearCache();
        ForcedDialogueResources.clearCache();
        ForcedDialogueService.clearRuntimeState();
        ServerLevel level = helper.getLevel();
        ResourceLocation questId = VillagerRetaliation.id("tales_of_a_lost_civilization");
        CompiledQuest compiled = VillagerQuestResources
                .compiledQuest(level.getServer(), questId)
                .orElseThrow(() -> new GameTestAssertException("Missing compiled quest " + questId));
        QuestDefinition quest = compiled.asQuestDefinition();
        QuestDefinition.Trigger stormReminder = quest.triggers().stream()
                .filter(trigger -> trigger.id().equals("storm_reminder"))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("lost civilization storm reminder trigger missing"));
        QuestDefinition.Trigger deterministicStormReminder = new QuestDefinition.Trigger(
                stormReminder.id(),
                stormReminder.event(),
                List.of(),
                stormReminder.actions(),
                stormReminder.stages(),
                stormReminder.cooldownTicks(),
                stormReminder.radius(),
                stormReminder.repeatable());
        VillagerQuestResources.installCompiledTestCatalog(
                level.getServer(),
                List.of(compiledQuestWithTriggers(compiled, quest, List.of(deterministicStormReminder))));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        configureLostCivilizationProvider(level, villager);

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            level.setDayTime(18000L);
            VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
            VillagerQuestSavedData.QuestProgress progress = data.getOrCreate(player.getUUID(), questId);
            progress.start(villager.getUUID(), Level.OVERWORLD, new BlockPos(256, -40, -256), -2400L);
            progress.setIssuer(
                    villager.getUUID(),
                    "Storm Cartographer",
                    "minecraft:cartographer",
                    3,
                    Level.OVERWORLD,
                    villager.blockPosition(),
                    "village:minecraft:overworld:2,2,2");
            progress.setTarget(villager.getUUID(), Level.OVERWORLD, new BlockPos(256, -40, -256), "structure_target");
            progress.setCurrentStage("survey");
            data.setDirty();

            VillagerQuestService.DebugInspectResult fired =
                    VillagerQuestService.debugFireTrigger(player, questId, QuestDefinition.TriggerEvent.PROXIMITY);
            helper.assertTrue(fired.found(), "lost civilization forced trigger did not fire: " + fired.message());
            helper.assertTrue(
                    fired.lines().stream().anyMatch(line -> line.contains("trigger_result dirty=true")
                            && line.contains("matched=1")
                            && line.contains("ran=1")),
                    "lost civilization forced trigger did not report one run: " + fired.lines());
            helper.assertTrue(
                    ForcedDialogueService.hasSession(player, villager),
                    "lost civilization forced trigger did not queue a forced dialogue session");

            ForcedDialogueService.endForPlayer(player);
            VillagerQuestService.DebugInspectResult second =
                    VillagerQuestService.debugFireTrigger(player, questId, QuestDefinition.TriggerEvent.PROXIMITY);
            helper.assertTrue(second.found(), "lost civilization second forced trigger fire failed: " + second.message());
            helper.assertTrue(
                    second.lines().stream().anyMatch(line -> line.contains("not_repeatable")),
                    "lost civilization forced trigger did not filter the second run: " + second.lines());
            helper.assertTrue(
                    second.lines().stream().anyMatch(line -> line.contains("ran=0")),
                    "lost civilization forced trigger ran more than once: " + second.lines());

            UUID missingIssuer = UUID.fromString("00000000-0000-0000-0000-000000000127");
            ServerPlayer unloadedPlayer = helper.makeMockServerPlayerInLevel();
            VillagerQuestSavedData.QuestProgress unloadedProgress = data.getOrCreate(unloadedPlayer.getUUID(), questId);
            unloadedProgress.start(missingIssuer, Level.OVERWORLD, new BlockPos(512, -40, 512), -2400L);
            unloadedProgress.setIssuer(
                    missingIssuer,
                    "Absent Cartographer",
                    "minecraft:cartographer",
                    3,
                    Level.OVERWORLD,
                    new BlockPos(512, 64, 512),
                    "village:minecraft:overworld:512,64,512");
            unloadedProgress.setCurrentStage("survey");
            data.setDirty();

            VillagerQuestService.DebugInspectResult unloaded =
                    VillagerQuestService.debugFireTrigger(unloadedPlayer, questId, QuestDefinition.TriggerEvent.PROXIMITY);
            helper.assertFalse(unloaded.found(), "lost civilization unloaded issuer unexpectedly fired trigger");
            helper.assertTrue(
                    unloaded.message().contains("is not loaded"),
                    "lost civilization unloaded issuer message was not explainable: " + unloaded.message());

            VillagerActionDefinition forcedAction = stormReminder.actions().stream()
                    .filter(action -> action.kind() == VillagerActionDefinition.Kind.FORCED_DIALOGUE)
                    .findFirst()
                    .orElseThrow(() -> new GameTestAssertException("lost civilization forced action missing"));
            ActionResult missingContext = VillagerActionRegistry.execute(null, forcedAction, Map.of());
            helper.assertValueEqual(missingContext.status(), ActionStatus.FAILED, "lost civilization missing context status");
            helper.assertValueEqual(
                    missingContext.message(),
                    "live dialogue context unavailable",
                    "lost civilization missing context message");

            VillagerQuestService.DebugInspectResult dryRun =
                    VillagerQuestService.debugDryRunTriggerActions(unloadedPlayer, questId, "storm_reminder");
            helper.assertTrue(dryRun.found(), "lost civilization dry-run failed: " + dryRun.message());
            helper.assertTrue(
                    dryRun.lines().stream().anyMatch(line -> line.contains("live_context=false")),
                    "lost civilization dry-run did not report missing live context: " + dryRun.lines());
            helper.assertTrue(
                    dryRun.lines().stream().anyMatch(line -> line.contains("action[1] type=forced_dialogue")
                            && line.contains("status=skipped")),
                    "lost civilization dry-run did not report forced dialogue action: " + dryRun.lines());
            helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "lost civilization forced trigger emitted diagnostics");
            DatapackDiagnostics.clear();
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            ForcedDialogueService.clearRuntimeState();
            VillagerConversationService.endForPlayer(player, false);
            villager.discard();
            DialogueTreeService.clearRuntimeState();
            DialogueTreeResources.clearCache();
            ForcedDialogueResources.clearCache();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2ResponseTransitionsRecordChoiceHistory(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ServerLevel level = helper.getLevel();
        EmbeddedDialogueQuest branch = embeddedDialogueQuest(
                "v2_branch_transition_runtime",
                branchTransitionQuestV2Fixture("v2_branch_transition_runtime"));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        VillagerProfileManager.setAttribute(
                level, villager, VillagerSocialAttribute.GUTS, 50);
        VillagerProfileManager.setAttribute(
                level, villager, VillagerSocialAttribute.KNOWLEDGE, 50);

        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(branch.quest()), branch.dialogueCatalog());
            DialogueTreeResources.clearCache();
            DialogueTreeService.clearRuntimeState();

            VillagerQuestService.DebugStartResult started =
                    VillagerQuestService.debugStartQuest(player, villager, branch.quest().id(), true);
            helper.assertTrue(started.started(), "v2 branch transition quest did not start: " + started.message());
            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
            selectDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(branch.treeId(), "stage.choose.responses"));
            helper.assertValueEqual(
                    selectDialogueOption(
                            helper,
                            context,
                            DialogueTreeService.responseOptionId(branch.treeId(), "alpha")).text(),
                    "Alpha selected.",
                    "v2 alpha response text");

            VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
            VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), branch.quest().id());
            helper.assertValueEqual(progress.currentStage(), "alpha", "v2 alpha transition stage");
            helper.assertValueEqual(progress.choiceHistory().size(), 1, "v2 alpha choice history count");
            VillagerQuestSavedData.ChoiceHistoryEntry choice = progress.choiceHistory().getFirst();
            helper.assertValueEqual(choice.scenePath(), "stage.choose.responses", "v2 alpha choice scene path");
            helper.assertValueEqual(choice.responseId(), "alpha", "v2 alpha choice response");
            helper.assertValueEqual(choice.priorStage(), "choose", "v2 alpha choice prior stage");
            helper.assertValueEqual(choice.nextStage(), "alpha", "v2 alpha choice next stage");
            helper.assertValueEqual(
                    VillagerQuestFacts.get(level)
                            .variable(QuestScopeKey.quest(player.getUUID(), branch.quest().id()), "branch")
                            .orElse(""),
                    "alpha",
                    "v2 fact action before transition");
            helper.assertValueEqual(
                    VillagerProfileManager.getOrCreateProfile(level, villager).socialAttributes().guts(),
                    55,
                    "alpha resolution should grant its data-driven guts reward");
            helper.assertValueEqual(
                    VillagerProfileManager.getOrCreateProfile(level, villager).socialAttributes().knowledge(),
                    50,
                    "alpha resolution should not grant beta's knowledge reward");

            JsonObject conditionsRoot = JsonParser.parseString("""
                    {
                      "conditions": [
                        {
                          "type": "selected_choice",
                          "response": "alpha",
                          "scene_path": "stage.choose.responses",
                          "prior_stage": "choose",
                          "next_stage": "alpha"
                        },
                        {
                          "type": "stage_history",
                          "next_stage": "alpha"
                        }
                      ]
                    }
                    """).getAsJsonObject();
            List<DialogueCondition> conditions = DialogueCondition.readList(
                    VillagerRetaliation.id("test/v2_branch_conditions"),
                    "v2 branch conditions",
                    conditionsRoot,
                    branch.quest().id());
            helper.assertTrue(DialogueCondition.matchesAll(context, conditions), "v2 branch history conditions did not match");

            CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
            VillagerQuestSavedData loaded = VillagerQuestSavedData.load(saved, level.registryAccess());
            VillagerQuestSavedData.QuestProgress loadedProgress = loaded.get(player.getUUID(), branch.quest().id());
            helper.assertTrue(loadedProgress != null, "v2 branch choice history did not reload");
            helper.assertValueEqual(loadedProgress.choiceHistory().getFirst().responseId(), "alpha", "v2 reloaded choice response");

            var duplicate = VillagerQuestService.applyCompiledTransition(
                    context,
                    new CompiledQuestTransition(
                            branch.quest().id(),
                            "choose",
                            "stage.choose.responses",
                            "alpha",
                            CompiledQuestTransition.Target.STAGE,
                            "alpha",
                            "/stages/0/responses/0"),
                    Map.of());
            helper.assertTrue(duplicate.text().contains("already"), "v2 duplicate response replay was not reported");
            helper.assertValueEqual(progress.currentStage(), "alpha", "v2 duplicate replay changed stage");
            helper.assertValueEqual(
                    VillagerProfileManager.getOrCreateProfile(level, villager).socialAttributes().guts(),
                    55,
                    "duplicate resolution replay granted its attribute reward twice");

            VillagerQuestService.DebugInspectResult inspect =
                    VillagerQuestService.debugInspectQuest(player, branch.quest().id());
            helper.assertTrue(
                    inspect.lines().stream().anyMatch(line ->
                            line.contains("choice_history entries=1")
                                    && line.contains("latest_response=alpha")
                                    && line.contains("latest_next_stage=alpha")),
                    "v2 debug inspect did not include choice history");

            started = VillagerQuestService.debugStartQuest(player, villager, branch.quest().id(), true);
            helper.assertTrue(started.started(), "v2 branch transition quest did not restart for blocked path");
            DialogueTreeService.clearRuntimeState();
            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            selectDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(branch.treeId(), "stage.choose.responses"));
            selectDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.responseOptionId(branch.treeId(), "blocked"));
            progress = data.get(player.getUUID(), branch.quest().id());
            helper.assertValueEqual(progress.currentStage(), "choose", "v2 required failed action allowed transition");
            helper.assertTrue(
                    progress.choiceHistory().isEmpty(),
                    "v2 blocked transition recorded choice history: " + progress.choiceHistory());

            started = VillagerQuestService.debugStartQuest(player, villager, branch.quest().id(), true);
            helper.assertTrue(started.started(), "v2 branch transition quest did not restart for beta path");
            DialogueTreeService.clearRuntimeState();
            context = VillagerInteractionService.createDialogueContext(level, player, villager);
            selectDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.entryOptionId(branch.treeId(), "stage.choose.responses"));
            selectDialogueOption(
                    helper,
                    context,
                    DialogueTreeService.responseOptionId(branch.treeId(), "beta"));
            progress = data.get(player.getUUID(), branch.quest().id());
            helper.assertValueEqual(progress.currentStage(), "beta", "v2 beta transition stage");
            helper.assertValueEqual(progress.choiceHistory().getFirst().responseId(), "beta", "v2 beta choice history");
            helper.assertTrue(DatapackDiagnostics.recent().isEmpty(), "v2 branch transition emitted diagnostics");
            helper.assertValueEqual(
                    VillagerProfileManager.getOrCreateProfile(level, villager).socialAttributes().knowledge(),
                    53,
                    "beta resolution should grant its data-driven knowledge reward");
            helper.assertValueEqual(
                    VillagerProfileManager.getOrCreateProfile(level, villager).socialAttributes().guts(),
                    55,
                    "beta resolution should not repeat alpha's guts reward");
            DatapackDiagnostics.clear();
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            DialogueTreeService.clearRuntimeState();
            DialogueTreeResources.clearCache();
            VillagerQuestResources.clearCache();
            DatapackDiagnostics.clear();
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2ParserReportsPreciseInvalidDiagnostics(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        ResourceLocation location = VillagerRetaliation.id("quests/v2_invalid_fixture.json");

        helper.assertTrue(
                QuestV2Parser.parse(location, invalidQuestV2Fixture()).isEmpty(),
                "invalid quest module v2 fixture parsed");
        helper.assertTrue(
                QuestV2Parser.parse(VillagerRetaliation.id("quests/v2_missing_entry_fixture.json"), invalidQuestV2MissingEntryFixture()).isEmpty(),
                "invalid missing-entry quest module v2 fixture parsed");

        assertRecentDiagnosticPointer(helper, "/provider/required_capabilities", "does not support live capability");
        assertRecentDiagnosticPointer(helper, "/entry_stage", "entry_stage references missing stage");
        assertRecentDiagnosticPointer(helper, "/lifecycle/on_start", "cannot define both actions and a transition");
        assertRecentDiagnosticPointer(helper, "/stages/0/surprise", "unsupported field");
        assertRecentDiagnosticPointer(helper, "/stages/0/objectives/0/id", "reserved generated-id space");
        assertRecentDiagnosticPointer(helper, "/stages/0/objectives/0/type", "unknown objective type");
        assertRecentDiagnosticPointer(helper, "/stages/0/objectives/2/id", "duplicate objective id");
        assertRecentDiagnosticPointer(helper, "/stages/0/scenes/0", "external scene reference");
        assertRecentDiagnosticPointer(helper, "/stages/0/scenes/0/responses/0/id", "response id is required");
        assertRecentDiagnosticPointer(helper, "/stages/0/scenes/0/responses/0/actions/0", "cannot define both a transition");
        assertRecentDiagnosticPointer(helper, "/stages/0/dialogue/offer/scene", "missing local scene");
        assertRecentDiagnosticPointer(helper, "/stages/0/ui/tracker_text", "undefined UI placeholder");
        assertRecentDiagnosticPointer(helper, "/stages/1/id", "is unreachable");
        DatapackDiagnostics.clear();

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2SchemaArtifactMatchesJavaExport(GameTestHelper helper) {
        Path schemaPath = projectPath("tools", "datapack-builder", "quest-v2.schema.json");
        String checkedIn;
        try {
            checkedIn = normalizeLineEndings(Files.readString(schemaPath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new GameTestAssertException("Could not read quest module v2 schema artifact: " + exception.getMessage());
        }
        helper.assertValueEqual(
                normalizeLineEndings(QuestV2Schema.exportJson()),
                checkedIn,
                "quest module v2 schema artifact drift");

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

        CompoundTag legacyRoot = new CompoundTag();
        ListTag legacyEntries = new ListTag();
        CompoundTag legacyEntry = new CompoundTag();
        legacyEntry.putUUID("Player", playerId);
        legacyEntry.putString("Quest", questId.toString());
        legacyEntry.putString("State", "ACTIVE");
        legacyEntry.putUUID("StartedVillager", villagerId);
        legacyEntries.add(legacyEntry);
        legacyRoot.put("Entries", legacyEntries);
        VillagerQuestSavedData legacyLoaded = VillagerQuestSavedData.load(legacyRoot, helper.getLevel().registryAccess());
        VillagerQuestSavedData.QuestProgress legacyProgress = legacyLoaded.get(playerId, questId);
        helper.assertTrue(legacyProgress != null, "legacy v1 quest progress did not load");
        helper.assertValueEqual(legacyProgress.currentStage(), "started", "legacy missing stage fallback");
        helper.assertTrue(legacyProgress.choiceHistory().isEmpty(), "legacy missing choice history fallback");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questV2FailureIsDistinctFromAbandonment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EmbeddedDialogueQuest failure = embeddedDialogueQuest(
                "v2_failure_runtime",
                failureQuestV2Fixture("v2_failure_runtime"));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(failure.quest()), failure.dialogueCatalog());
            VillagerQuestService.DebugStartResult started =
                    VillagerQuestService.debugStartQuest(player, villager, failure.quest().id(), true);
            helper.assertTrue(started.started(), "failure quest did not start: " + started.message());
            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
            int experienceBefore = player.totalExperience;
            VillagerQuestService.applyCompiledTransition(
                    context,
                    new CompiledQuestTransition(
                            failure.quest().id(), "start", "stage.start.responses", "fail_now",
                            CompiledQuestTransition.Target.FAIL, "", "/stages/0/responses/0"),
                    Map.of());

            VillagerQuestSavedData.QuestProgress progress = VillagerQuestSavedData.get(level)
                    .get(player.getUUID(), failure.quest().id());
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.FAILED, "explicit fail state");
            helper.assertValueEqual(progress.abandonCount(), 0, "explicit failure dispatched abandonment");
            helper.assertValueEqual(progress.completionCount(), 0, "explicit failure completed quest");
            helper.assertValueEqual(player.totalExperience, experienceBefore, "explicit failure granted rewards");
            QuestScopeKey scope = QuestScopeKey.quest(player.getUUID(), failure.quest().id());
            helper.assertValueEqual(
                    VillagerQuestFacts.get(level).variable(scope, "failure_hook").orElse(""),
                    "ran",
                    "on_fail actions did not run");
            helper.assertTrue(
                    VillagerQuestFacts.get(level).variable(scope, "abandon_hook").isEmpty(),
                    "on_abandon ran during failure");

            VillagerQuestService.DebugStartResult restarted =
                    VillagerQuestService.debugStartQuest(player, villager, failure.quest().id(), false);
            helper.assertTrue(restarted.started(), "repeatable failed quest did not restart: " + restarted.message());
            VillagerQuestService.DebugInspectResult fired = VillagerQuestService.debugFireTrigger(
                    player, failure.quest().id(), QuestDefinition.TriggerEvent.PROGRESS);
            helper.assertTrue(fired.found(), "event failure trigger did not run: " + fired.message());
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.FAILED, "event-triggered fail state");
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            villager.discard();
            VillagerQuestResources.clearCache();
            DialogueTreeResources.clearCache();
            DialogueTreeService.clearRuntimeState();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questFailureStateAndSaveVersionsRoundTrip(GameTestHelper helper) {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID villagerId = UUID.fromString("00000000-0000-0000-0000-000000000402");
        ResourceLocation questId = VillagerRetaliation.id("failure_round_trip");
        VillagerQuestSavedData data = new VillagerQuestSavedData();
        VillagerQuestSavedData.QuestProgress progress = data.getOrCreate(playerId, questId);
        progress.start(villagerId, Level.OVERWORLD, null, 10L);
        QuestStateMachine.TransitionResult repeatedStart =
                QuestStateMachine.start(progress, villagerId, Level.OVERWORLD, null, 11L);
        helper.assertFalse(repeatedStart.dirty(), "active quest must reject a repeated start");
        helper.assertValueEqual(repeatedStart.blockerCode(), "quest_already_active",
                "repeated-start blocker");
        helper.assertValueEqual(progress.startedGameTime(), 10L,
                "repeated start must not reset active progress");

        QuestStateMachine.TransitionResult result = QuestStateMachine.fail(progress, 80L, " Provider Lost! ");
        helper.assertValueEqual(result.previousState(), VillagerQuestSavedData.QuestState.ACTIVE, "failure previous state");
        helper.assertValueEqual(result.newState(), VillagerQuestSavedData.QuestState.FAILED, "failure new state");
        helper.assertValueEqual(result.lifecycleEvent(), QuestStateMachine.LifecycleEvent.FAILED, "failure lifecycle event");
        helper.assertTrue(result.dirty(), "failure transition was not dirty");
        helper.assertValueEqual(progress.failedGameTime(), 80L, "failure game time");
        helper.assertValueEqual(progress.failureReason(), "provider_lost", "normalized failure reason");
        helper.assertValueEqual(progress.completionCount(), 0, "failure granted a completion");
        helper.assertValueEqual(progress.abandonCount(), 0, "failure counted as abandonment");
        QuestLifecycleService.LifecycleEvent blockedCompletion =
                QuestLifecycleService.complete(questId, progress, 81L, false);
        helper.assertValueEqual(
                blockedCompletion.type(),
                QuestLifecycleService.LifecycleEventType.NONE,
                "blocked completion must not report a terminal lifecycle event");
        helper.assertValueEqual(blockedCompletion.reason(), "quest_not_active", "blocked completion reason");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(
                saved.getInt("DataVersion"),
                VillagerQuestSavedData.CURRENT_DATA_VERSION,
                "quest save data version");
        VillagerQuestSavedData.QuestProgress loaded = VillagerQuestSavedData
                .load(saved, helper.getLevel().registryAccess())
                .get(playerId, questId);
        helper.assertTrue(loaded != null, "failed progress did not load");
        helper.assertValueEqual(loaded.state(), VillagerQuestSavedData.QuestState.FAILED, "loaded failed state");
        helper.assertValueEqual(loaded.failedGameTime(), 80L, "loaded failure time");
        helper.assertValueEqual(loaded.failureReason(), "provider_lost", "loaded failure reason");

        CompoundTag legacy = new CompoundTag();
        ListTag entries = new ListTag();
        CompoundTag abandoned = new CompoundTag();
        abandoned.putUUID("Player", playerId);
        abandoned.putString("Quest", questId.toString());
        abandoned.putString("State", "ABANDONED");
        entries.add(abandoned);
        legacy.put("Entries", entries);
        VillagerQuestSavedData.QuestProgress legacyProgress = VillagerQuestSavedData
                .load(legacy, helper.getLevel().registryAccess())
                .get(playerId, questId);
        helper.assertValueEqual(legacyProgress.state(), VillagerQuestSavedData.QuestState.ABANDONED,
                "unversioned abandonment was rewritten as failure");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questSaveMigrationsPreserveReadableFutureAndMalformedData(GameTestHelper helper) {
        CompoundTag unversioned = new CompoundTag();
        unversioned.putString("Marker", "keep");
        QuestSaveMigrations.MigrationResult migrated = QuestSaveMigrations.migrate(
                unversioned,
                VillagerQuestSavedData.CURRENT_DATA_VERSION);
        helper.assertValueEqual(migrated.sourceVersion(), 0, "unversioned source version");
        helper.assertValueEqual(migrated.data().getInt("DataVersion"), VillagerQuestSavedData.CURRENT_DATA_VERSION,
                "migrated data version");
        helper.assertValueEqual(migrated.data().getString("Marker"), "keep", "migration discarded readable data");
        helper.assertFalse(unversioned.contains("DataVersion"), "migration mutated its input");

        CompoundTag versionOne = new CompoundTag();
        versionOne.putInt("DataVersion", 1);
        versionOne.putString("Marker", "version_one");
        QuestSaveMigrations.MigrationResult upgraded = QuestSaveMigrations.migrate(
                versionOne,
                VillagerQuestSavedData.CURRENT_DATA_VERSION);
        helper.assertValueEqual(upgraded.sourceVersion(), 1, "version-one source version");
        helper.assertValueEqual(upgraded.targetVersion(), VillagerQuestSavedData.CURRENT_DATA_VERSION,
                "version-one target version");
        helper.assertValueEqual(upgraded.data().getString("Marker"), "version_one",
                "version-one migration discarded readable data");

        CompoundTag future = new CompoundTag();
        future.putInt("DataVersion", VillagerQuestSavedData.CURRENT_DATA_VERSION + 10);
        future.putString("Marker", "future");
        QuestSaveMigrations.MigrationResult preserved = QuestSaveMigrations.migrate(
                future,
                VillagerQuestSavedData.CURRENT_DATA_VERSION);
        helper.assertTrue(preserved.futureVersion(), "future version was not reported");
        helper.assertValueEqual(preserved.data().getString("Marker"), "future", "future readable data was reset");
        VillagerQuestSavedData futureQuestData =
                VillagerQuestSavedData.load(future, helper.getLevel().registryAccess());
        futureQuestData.getOrCreate(UUID.randomUUID(), VillagerRetaliation.id("future_mutation"));
        CompoundTag futureQuestRoundTrip =
                futureQuestData.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(
                futureQuestRoundTrip.getInt("DataVersion"),
                VillagerQuestSavedData.CURRENT_DATA_VERSION + 10,
                "future quest save version must not be downgraded");
        helper.assertValueEqual(
                futureQuestRoundTrip.getString("Marker"),
                "future",
                "future quest save fields must survive a downgrade load");

        VillagerQuestFacts facts = new VillagerQuestFacts();
        CompoundTag savedFacts = facts.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(savedFacts.getInt("DataVersion"), VillagerQuestFacts.CURRENT_DATA_VERSION,
                "quest facts data version");
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("DataVersion", VillagerQuestFacts.CURRENT_DATA_VERSION);
        malformed.putString("Entries", "not-a-list");
        VillagerQuestFacts.load(malformed, helper.getLevel().registryAccess());
        CompoundTag futureFacts = new CompoundTag();
        futureFacts.putInt("DataVersion", VillagerQuestFacts.CURRENT_DATA_VERSION + 10);
        futureFacts.putString("Marker", "future_facts");
        VillagerQuestFacts loadedFutureFacts =
                VillagerQuestFacts.load(futureFacts, helper.getLevel().registryAccess());
        loadedFutureFacts.setVariable("world", "runtime_change", "ignored_on_downgrade");
        CompoundTag futureFactsRoundTrip =
                loadedFutureFacts.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(
                futureFactsRoundTrip.getInt("DataVersion"),
                VillagerQuestFacts.CURRENT_DATA_VERSION + 10,
                "future quest facts version must not be downgraded");
        helper.assertValueEqual(futureFactsRoundTrip.getString("Marker"), "future_facts",
                "future quest facts fields must survive a downgrade load");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questTerminalStatesRoundTripWithoutSemanticDrift(GameTestHelper helper) {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000451");
        UUID providerId = UUID.fromString("00000000-0000-0000-0000-000000000452");
        VillagerQuestSavedData data = new VillagerQuestSavedData();
        Map<String, VillagerQuestSavedData.QuestState> expected = new LinkedHashMap<>();

        VillagerQuestSavedData.QuestProgress completed = data.getOrCreate(playerId, VillagerRetaliation.id("terminal_completed"));
        completed.start(providerId, Level.OVERWORLD, null, 1L);
        QuestStateMachine.complete(completed, 10L, false);
        expected.put("terminal_completed", VillagerQuestSavedData.QuestState.COMPLETED);

        VillagerQuestSavedData.QuestProgress failed = data.getOrCreate(playerId, VillagerRetaliation.id("terminal_failed"));
        failed.start(providerId, Level.OVERWORLD, null, 2L);
        QuestStateMachine.fail(failed, 11L, "contract_failure");
        expected.put("terminal_failed", VillagerQuestSavedData.QuestState.FAILED);

        VillagerQuestSavedData.QuestProgress abandoned = data.getOrCreate(playerId, VillagerRetaliation.id("terminal_abandoned"));
        abandoned.start(providerId, Level.OVERWORLD, null, 3L);
        QuestStateMachine.abandon(abandoned, 12L, false);
        expected.put("terminal_abandoned", VillagerQuestSavedData.QuestState.ABANDONED);

        VillagerQuestSavedData.QuestProgress expired = data.getOrCreate(playerId, VillagerRetaliation.id("terminal_expired"));
        expired.start(providerId, Level.OVERWORLD, null, 4L);
        QuestStateMachine.expire(expired, 13L, false);
        expected.put("terminal_expired", VillagerQuestSavedData.QuestState.EXPIRED);

        VillagerQuestSavedData.QuestProgress consumed = data.getOrCreate(playerId, VillagerRetaliation.id("terminal_consumed"));
        consumed.start(providerId, Level.OVERWORLD, null, 5L);
        QuestStateMachine.consume(consumed, "branch_lock");
        expected.put("terminal_consumed", VillagerQuestSavedData.QuestState.CONSUMED);

        VillagerQuestSavedData loaded = VillagerQuestSavedData.load(
                data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        expected.forEach((path, state) -> helper.assertValueEqual(
                loaded.get(playerId, VillagerRetaliation.id(path)).state(), state, path + " state after reload"));
        helper.assertValueEqual(loaded.get(playerId, VillagerRetaliation.id("terminal_completed")).completionCount(), 1,
                "completed terminal count");
        helper.assertValueEqual(loaded.get(playerId, VillagerRetaliation.id("terminal_failed")).failureReason(),
                "contract_failure", "failed terminal reason");
        helper.assertValueEqual(loaded.get(playerId, VillagerRetaliation.id("terminal_abandoned")).abandonCount(), 1,
                "abandoned terminal count");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questPersistenceHistoriesAndRecordsAreBounded(GameTestHelper helper) {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000901");
        UUID providerId = UUID.fromString("00000000-0000-0000-0000-000000000902");
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        for (int index = 0; index < VillagerQuestSavedData.MAX_COMPLETION_HISTORY + 3; index++) {
            progress.start(providerId, Level.OVERWORLD, null, index);
            progress.complete(index + 1L, false);
        }
        helper.assertValueEqual(
                progress.completionHistory().size(),
                VillagerQuestSavedData.MAX_COMPLETION_HISTORY,
                "completion history cap");

        progress.start(providerId, Level.OVERWORLD, null, 1000L);
        for (int index = 0; index < VillagerQuestSavedData.MAX_CHOICE_HISTORY + 3; index++) {
            progress.recordChoice("scene", "response_" + index, "before", "after", index);
        }
        helper.assertValueEqual(
                progress.choiceHistory().size(),
                VillagerQuestSavedData.MAX_CHOICE_HISTORY,
                "choice history cap");

        for (int index = 0; index < VillagerQuestSavedData.MAX_PROVIDER_REBIND_HISTORY + 3; index++) {
            progress.rebindProvider(
                    new QuestProviderBinding(
                            VillagerRetaliation.id("villager"),
                            UUID.randomUUID(),
                            "Provider",
                            null,
                            1,
                            Level.OVERWORLD,
                            null,
                            "",
                            Map.of(),
                            false),
                    index,
                    "test_rebind");
        }
        helper.assertValueEqual(
                progress.providerRebindHistory().size(),
                VillagerQuestSavedData.MAX_PROVIDER_REBIND_HISTORY,
                "provider rebind history cap");
        progress.addObjectiveCounter("overflow", Integer.MAX_VALUE);
        helper.assertValueEqual(
                progress.addObjectiveCounter("overflow", 1),
                Integer.MAX_VALUE,
                "objective counter positive saturation");

        VillagerQuestSavedData bounded = new VillagerQuestSavedData();
        for (int index = 0; index < VillagerQuestSavedData.MAX_QUEST_RECORDS_PER_PLAYER + 3; index++) {
            ResourceLocation questId = VillagerRetaliation.id("bounded_terminal_" + index);
            VillagerQuestSavedData.QuestProgress terminal = bounded.getOrCreate(playerId, questId);
            terminal.start(providerId, Level.OVERWORLD, null, index);
            terminal.complete(index + 1L, false);
        }
        helper.assertValueEqual(
                bounded.progress(playerId).size(),
                VillagerQuestSavedData.MAX_QUEST_RECORDS_PER_PLAYER,
                "per-player terminal quest record cap");
        helper.assertTrue(
                bounded.get(playerId, VillagerRetaliation.id("bounded_terminal_0")) == null,
                "oldest terminal quest record should be evicted first");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questSavedDataLimitsTrackedQuestsToThree(GameTestHelper helper) {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        ResourceLocation first = VillagerRetaliation.id("tracked_first");
        ResourceLocation second = VillagerRetaliation.id("tracked_second");
        ResourceLocation third = VillagerRetaliation.id("tracked_third");
        ResourceLocation fourth = VillagerRetaliation.id("tracked_fourth");

        VillagerQuestSavedData data = new VillagerQuestSavedData();
        data.setTrackedQuest(playerId, first);
        data.setTrackedQuest(playerId, second);
        data.setTrackedQuest(playerId, third);
        data.setTrackedQuest(playerId, fourth);

        helper.assertValueEqual(data.getTrackedQuests(playerId).size(), 3, "tracked quest cap");
        helper.assertValueEqual(data.getTrackedQuest(playerId), fourth, "primary tracked quest");
        helper.assertFalse(data.getTrackedQuests(playerId).contains(first), "oldest tracked quest was not evicted");
        helper.assertTrue(data.getTrackedQuests(playerId).contains(second), "second tracked quest missing");
        helper.assertTrue(data.getTrackedQuests(playerId).contains(third), "third tracked quest missing");

        data.toggleTrackedQuest(playerId, third);
        helper.assertFalse(data.getTrackedQuests(playerId).contains(third), "toggle did not remove tracked quest");
        data.toggleTrackedQuest(playerId, first);
        helper.assertValueEqual(data.getTrackedQuest(playerId), first, "toggle did not promote newly tracked quest");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VillagerQuestSavedData loaded = VillagerQuestSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.getTrackedQuests(playerId), data.getTrackedQuests(playerId), "tracked quest order after reload");

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
        facts.addCounter(QuestScopeKey.WORLD, "positive_saturation", Integer.MAX_VALUE);
        helper.assertValueEqual(
                facts.addCounter(QuestScopeKey.WORLD, "positive_saturation", 1),
                Integer.MAX_VALUE,
                "fact counter positive saturation");
        facts.addCounter(QuestScopeKey.WORLD, "negative_saturation", Integer.MIN_VALUE);
        helper.assertValueEqual(
                facts.addCounter(QuestScopeKey.WORLD, "negative_saturation", -1),
                Integer.MIN_VALUE,
                "fact counter negative saturation");
        facts.addCounter("merge_source", "saturation", 1);
        facts.addCounter("merge_target", "saturation", Integer.MAX_VALUE);
        facts.mergeScope("merge_source", "merge_target");
        helper.assertValueEqual(
                facts.counter("merge_target", "saturation"),
                Integer.MAX_VALUE,
                "merged fact counter saturation");

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
    public static void missingQuestProviderCanBeAbandonedAndExplicitlyRebound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation location = VillagerRetaliation.id("quests/provider_recovery.json");
        JsonObject root = providerRecoveryQuestV2Fixture("provider_recovery", true);
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root).orElseThrow();
        CompiledQuest compiled = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager original = spawnVillager(helper, new BlockPos(2, 2, 2));
        original.setVillagerData(original.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        Villager replacement = spawnVillager(helper, new BlockPos(4, 2, 2));
        replacement.setVillagerData(replacement.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        Villager incompatible = spawnVillager(helper, new BlockPos(6, 2, 2));
        incompatible.setVillagerData(incompatible.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(compiled));
            helper.assertTrue(
                    VillagerQuestService.debugStartQuest(player, original, compiled.id(), true).started(),
                    "provider recovery quest did not start");
            VillagerQuestSavedData.QuestProgress progress = VillagerQuestSavedData.get(level)
                    .get(player.getUUID(), compiled.id());
            UUID originalId = original.getUUID();
            original.discard();

            QuestProviderBinding snapshot = VillagerQuestProviderType.INSTANCE
                    .bindingFromProgress(level, progress).orElseThrow();
            helper.assertValueEqual(snapshot.providerId(), originalId, "offline snapshot provider id");
            helper.assertFalse(snapshot.live(), "missing provider snapshot was marked live");
            VillagerQuestService.handleTrackerRequest(
                    player, compiled.id().toString(), QuestTrackerRequestPayload.Action.ABANDON);
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.ABANDONED,
                    "journal abandonment required a live provider");
            helper.assertTrue(
                    progress.pendingLifecycleEvents().contains(QuestDefinition.TriggerEvent.ABANDONED),
                    "offline abandonment did not queue its lifecycle event");
            QuestScopeKey questScope = QuestScopeKey.quest(player.getUUID(), compiled.id());
            helper.assertValueEqual(
                    VillagerQuestFacts.get(level).counter(questScope, "abandon_replays"), 0,
                    "offline abandonment ran a provider-bound hook early");

            UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000499");
            progress.setIssuer(missingId, "Missing Farmer", "minecraft:farmer", 2,
                    Level.OVERWORLD, new BlockPos(20, 64, 20), "village:missing");
            CompoundTag pendingSave = VillagerQuestSavedData.get(level).save(new CompoundTag(), level.registryAccess());
            VillagerQuestSavedData.QuestProgress loadedPending = VillagerQuestSavedData
                    .load(pendingSave, level.registryAccess()).get(player.getUUID(), compiled.id());
            helper.assertTrue(
                    loadedPending.pendingLifecycleEvents().contains(QuestDefinition.TriggerEvent.ABANDONED),
                    "deferred lifecycle event did not reload");

            VillagerQuestService.ProviderRebindResult rejected = VillagerQuestService.debugRebindQuest(
                    player, incompatible, compiled.id());
            helper.assertFalse(rejected.rebound(), "incompatible provider rebind was accepted");
            helper.assertValueEqual(progress.startedVillagerId(), missingId, "rejected rebind mutated provider id");

            VillagerQuestService.ProviderRebindResult rebound = VillagerQuestService.debugRebindQuest(
                    player, replacement, compiled.id());
            helper.assertTrue(rebound.rebound(), "compatible provider rebind failed: " + rebound.message());
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.ABANDONED,
                    "rebind reopened the terminal quest");
            helper.assertValueEqual(progress.startedVillagerId(), replacement.getUUID(), "replacement provider id");
            helper.assertValueEqual(progress.providerRebindHistory().size(), 1, "provider rebind history count");
            helper.assertValueEqual(progress.providerRebindHistory().getFirst().previousProviderId(), missingId,
                    "provider rebind lost previous UUID");
            helper.assertFalse(progress.hasPendingLifecycleEvents(), "replayed lifecycle event remained queued");
            helper.assertValueEqual(
                    VillagerQuestFacts.get(level).counter(questScope, "abandon_replays"), 1,
                    "deferred abandonment hook did not replay exactly once");
            VillagerQuestService.ProviderRebindResult duplicate = VillagerQuestService.debugRebindQuest(
                    player, replacement, compiled.id());
            helper.assertFalse(duplicate.rebound(), "live replacement accepted a duplicate rebind");
            helper.assertValueEqual(
                    VillagerQuestFacts.get(level).counter(questScope, "abandon_replays"), 1,
                    "resolved lifecycle hook replayed twice");

            CompoundTag saved = VillagerQuestSavedData.get(level).save(new CompoundTag(), level.registryAccess());
            VillagerQuestSavedData.QuestProgress loaded = VillagerQuestSavedData
                    .load(saved, level.registryAccess()).get(player.getUUID(), compiled.id());
            helper.assertValueEqual(loaded.providerRebindHistory().size(), 1, "rebind history did not reload");
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            replacement.discard();
            incompatible.discard();
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void deferredLifecycleEventReplaysWhenOriginalProviderReturns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation location = VillagerRetaliation.id("quests/provider_return_replay.json");
        CompiledQuest compiled = compileQuestFixture(
                location, providerRecoveryQuestV2Fixture("provider_return_replay", true));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager provider = spawnVillager(helper, new BlockPos(2, 2, 2));
        provider.setVillagerData(provider.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        try {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(compiled));
            helper.assertTrue(
                    VillagerQuestService.debugStartQuest(player, provider, compiled.id(), true).started(),
                    "provider return quest did not start");
            VillagerQuestSavedData.QuestProgress progress = VillagerQuestSavedData.get(level)
                    .get(player.getUUID(), compiled.id());
            UUID providerId = provider.getUUID();
            progress.setIssuer(
                    UUID.fromString("00000000-0000-0000-0000-000000000498"),
                    "Temporarily Missing Farmer",
                    "minecraft:farmer",
                    2,
                    Level.OVERWORLD,
                    provider.blockPosition(),
                    "village:temporarily_missing");
            helper.assertTrue(
                    VillagerQuestService.abandonQuestFromJournal(player, compiled.id()),
                    "offline abandonment failed");
            helper.assertTrue(progress.hasPendingLifecycleEvents(), "offline hook was not deferred");

            progress.setIssuer(
                    providerId,
                    "Returned Farmer",
                    "minecraft:farmer",
                    2,
                    Level.OVERWORLD,
                    provider.blockPosition(),
                    "village:returning");
            player.tickCount = 0;
            VillagerQuestService.onPlayerTick(player);
            QuestScopeKey questScope = QuestScopeKey.quest(player.getUUID(), compiled.id());
            helper.assertFalse(progress.hasPendingLifecycleEvents(), "provider return left lifecycle work queued");
            helper.assertValueEqual(
                    VillagerQuestFacts.get(level).counter(questScope, "abandon_replays"), 1,
                    "provider return did not replay the deferred hook");
            VillagerQuestService.onPlayerTick(player);
            helper.assertValueEqual(
                    VillagerQuestFacts.get(level).counter(questScope, "abandon_replays"), 1,
                    "provider return replayed the hook twice");
        } finally {
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            provider.discard();
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void crossVillagerCompletionRequiresAuthoredCompatibility(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager original = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager other = spawnVillager(helper, new BlockPos(4, 2, 2));
        original.setVillagerData(original.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        other.setVillagerData(other.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        DialogueContext otherContext = VillagerInteractionService.createDialogueContext(level, player, other);
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(original.getUUID(), Level.OVERWORLD, null, 1L);

        QuestDefinition compatible = compileQuestFixture(
                VillagerRetaliation.id("quests/cross_provider_yes.json"),
                providerRecoveryQuestV2Fixture("cross_provider_yes", true)).asQuestDefinition();
        QuestDefinition locked = compileQuestFixture(
                VillagerRetaliation.id("quests/cross_provider_no.json"),
                providerRecoveryQuestV2Fixture("cross_provider_no", false)).asQuestDefinition();
        helper.assertTrue(QuestAvailabilityService.matchesProviderLock(otherContext, compatible, progress),
                "authored cross-villager compatibility was ignored");
        helper.assertFalse(QuestAvailabilityService.matchesProviderLock(otherContext, locked, progress),
                "locked quest completed through a replacement provider");
        original.discard();
        other.discard();
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

    private static void configureEggBasketsProvider(ServerLevel level, Villager villager) {
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.FARMER)
                .setLevel(1));
        VillagerProfileManager.setSkill(level, villager, VillagerSkill.ANIMAL_HANDLING, 5);
        VillagerProfileManager.setSkill(level, villager, VillagerSkill.COOKING, 4);
    }

    private static void configureChooseTheHorizonProvider(ServerLevel level, Villager villager) {
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.CARTOGRAPHER)
                .setLevel(5));
        VillagerProfileManager.setSkill(level, villager, VillagerSkill.CARTOGRAPHY, 48);
    }

    private static void configureFirstFarMarkerProvider(ServerLevel level, Villager villager) {
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.CARTOGRAPHER)
                .setLevel(2));
        VillagerProfileManager.setSkill(level, villager, VillagerSkill.CARTOGRAPHY, 14);
    }

    private static void configureLostCivilizationProvider(ServerLevel level, Villager villager) {
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.CARTOGRAPHER)
                .setLevel(3));
        VillagerProfileManager.setSkill(level, villager, VillagerSkill.CARTOGRAPHY, 50);
    }

    private static void markQuestCompleted(ServerLevel level, UUID playerId, ResourceLocation questId) {
        VillagerQuestSavedData.QuestProgress progress = VillagerQuestSavedData.get(level).getOrCreate(playerId, questId);
        progress.complete(level.getGameTime(), false);
        VillagerQuestSavedData.get(level).setDirty();
    }

    private static void assertEggBasketsV2Semantics(GameTestHelper helper, QuestDefinition quest) {
        helper.assertValueEqual(quest.id(), VillagerRetaliation.id("egg_baskets"), "egg baskets id");
        helper.assertValueEqual(quest.title(), "Egg Baskets", "egg baskets title");
        helper.assertValueEqual(
                quest.description(),
                "Bring eggs so the kitchens can stretch breakfast and broth.",
                "egg baskets description");
        helper.assertValueEqual(quest.questline(), "village_supply", "egg baskets questline");
        helper.assertValueEqual(
                quest.tags(),
                Set.of(
                        "group.village_supply",
                        "role.request",
                        "activity.deliver",
                        "activity.gather",
                        "destination.village",
                        "tier.early",
                        "difficulty.easy",
                        "commitment.quick",
                        "theme.community",
                        "pool.daily",
                        "pool.quest_board"),
                "egg baskets tags");

        helper.assertValueEqual(
                quest.offer().professions(),
                Set.of(VillagerProfession.FARMER, VillagerProfession.BUTCHER),
                "egg baskets offer professions");
        helper.assertValueEqual(quest.offer().minVillagerLevel(), 1, "egg baskets offer level");
        helper.assertValueEqual(
                quest.offer().minSkills(),
                Map.of(VillagerSkill.ANIMAL_HANDLING, 5, VillagerSkill.COOKING, 4),
                "egg baskets offer skills");

        QuestDefinition.Objective objective = quest.objectives().getFirst();
        helper.assertTrue(objective.id().endsWith(".bring_eggs"), "egg baskets objective id");
        helper.assertValueEqual(objective.type(), QuestDefinition.ObjectiveType.ITEM_CHECK, "egg baskets objective type");
        helper.assertValueEqual(objective.item(), ResourceLocation.fromNamespaceAndPath("minecraft", "egg"), "egg baskets item");
        helper.assertValueEqual(objective.count(), 12, "egg baskets count");
        helper.assertTrue(objective.tracker().hasAnyDisplay(), "egg baskets objective tracker");

        helper.assertValueEqual(new ArrayList<>(quest.stages().keySet()), List.of("work", "return"), "egg baskets stages");
        QuestDefinition.Stage workStage = quest.stages().get("work");
        helper.assertValueEqual(workStage.objectives(), List.of(objective.id()), "egg baskets work objectives");
        helper.assertValueEqual(workStage.completeWhen().getFirst().objective(), objective.id(), "egg baskets work predicate");
        helper.assertValueEqual(workStage.next(), "return", "egg baskets work next stage");

        helper.assertTrue(quest.rules().repeatable(), "egg baskets repeatable");
        helper.assertValueEqual(quest.rules().maxStarts(), 0, "egg baskets max starts");
        helper.assertValueEqual(quest.rules().maxCompletions(), 0, "egg baskets max completions");
        helper.assertValueEqual(quest.rules().completionCooldownTicks(), 24000L, "egg baskets completion cooldown");
        helper.assertValueEqual(quest.rules().abandonment(), QuestDefinition.AbandonmentMode.ALLOW_REPICKUP, "egg baskets abandonment");
        helper.assertTrue(quest.rules().consumeOnCompletion(), "egg baskets consume on completion");
        helper.assertTrue(quest.rules().lockedToVillager(), "egg baskets issuer lock");
        helper.assertTrue(quest.rules().crossVillagerCompatible(), "egg baskets cross-villager compatibility");

        helper.assertValueEqual(quest.rewards().experience(), 45, "egg baskets reward xp");
        helper.assertValueEqual(quest.rewards().reputation(), 4, "egg baskets reward reputation");
        helper.assertValueEqual(quest.rewards().gossipReputation(), 2, "egg baskets reward gossip");
        helper.assertValueEqual(quest.rewards().lootTable(), VillagerRetaliation.id("quest/egg_baskets"), "egg baskets reward loot");
        helper.assertValueEqual(quest.rewards().memoryEvent(), VillagerRetaliation.id("player_completed_quest"), "egg baskets reward memory event");
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new GameTestAssertException("Could not create villager");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(villager)) {
            throw new GameTestAssertException("Could not add villager to level");
        }
        level.tickNonPassenger(villager);
        return villager;
    }

    private static void movePlayer(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
    }

    private static String dialogueTreeSnapshot(DialogueTreeDefinition tree) {
        StringBuilder builder = new StringBuilder();
        builder.append("tree=").append(tree.id()).append('\n');
        builder.append("entries=").append(tree.entries().stream()
                .map(entry -> entry.id() + "->" + entry.start())
                .collect(Collectors.joining("|")))
                .append('\n');
        tree.nodes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    DialogueTreeDefinition.Node node = entry.getValue();
                    builder.append("node=").append(node.id())
                            .append(" lines=").append(node.lines())
                            .append(" actions=").append(dialogueActionSnapshot(node.actions()))
                            .append(" conditions=").append(node.conditions().size())
                            .append(" responses=").append(dialogueResponseSnapshot(node.responses()))
                            .append('\n');
                });
        return builder.toString().stripTrailing();
    }

    private static String dialogueResponseSnapshot(List<DialogueTreeDefinition.Response> responses) {
        return responses.stream()
                .map(response -> response.id()
                        + "[" + response.label() + "]"
                        + (response.next().isBlank() ? "" : " ->" + response.next())
                        + " actions=" + dialogueActionSnapshot(response.actions())
                        + " lines=" + response.lines()
                        + " conditions=" + response.conditions().size())
                .collect(Collectors.joining("|", "[", "]"));
    }

    private static String dialogueActionSnapshot(List<VillagerActionDefinition> actions) {
        return actions.stream()
                .map(VillagerQuestGameTests::dialogueActionSnapshot)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String dialogueActionSnapshot(VillagerActionDefinition action) {
        return switch (action.kind()) {
            case QUEST -> "quest:" + action.questAction().name().toLowerCase(Locale.ROOT);
            case SET_VARIABLE -> "set_variable:"
                    + action.factScope().name().toLowerCase(Locale.ROOT)
                    + ":" + action.factKey()
                    + "=" + action.factValue();
            case NOTIFICATION -> "notification:" + action.text();
            default -> action.kind().serializedName();
        };
    }

    private static void assertHasDialogueOption(
            GameTestHelper helper,
            DialogueContext context,
            String optionId,
            String message) {
        helper.assertTrue(
                VillagerDialogueResources
                        .dialogueOptions(context, VillagerDialogueService.moodFor(context))
                        .stream()
                        .anyMatch(option -> option.id().equals(optionId)),
                message + " missing");
    }

    private static void assertMissingDialogueOption(
            GameTestHelper helper,
            DialogueContext context,
            String optionId,
            String message) {
        helper.assertTrue(
                VillagerDialogueResources
                        .dialogueOptions(context, VillagerDialogueService.moodFor(context))
                        .stream()
                        .noneMatch(option -> option.id().equals(optionId)),
                message + " unexpectedly present");
    }

    private static VillagerDialogueService.DialogueResult selectDialogueOption(
            GameTestHelper helper,
            DialogueContext context,
            String optionId) {
        DialogueOptionDefinition option = VillagerDialogueResources
                .dialogueOptions(context, VillagerDialogueService.moodFor(context))
                .stream()
                .filter(candidate -> candidate.id().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Missing dialogue option " + optionId));
        return DialogueTreeService.handleDialogueOption(context, option)
                .orElseThrow(() -> new GameTestAssertException("Dialogue option did not produce a result " + optionId));
    }

    private static void assertForcedSceneResponse(
            GameTestHelper helper,
            DialogueContext context,
            QuestDialogueCatalog catalog,
            String forcedSceneId,
            ResourceLocation expectedTreeId,
            String expectedEntryId,
            String responseOptionId,
            String expectedResponseText,
            String message) {
        QuestDialogueCatalog.Binding binding = catalog
                .forcedBinding(forcedSceneId)
                .orElseThrow(() -> new GameTestAssertException("Missing forced scene binding " + forcedSceneId));
        helper.assertValueEqual(binding.treeId(), expectedTreeId, forcedSceneId + " tree id");
        helper.assertValueEqual(binding.entryId(), expectedEntryId, forcedSceneId + " entry id");
        DialogueTreeService.startEntry(context, binding.treeId(), binding.entryId(), Map.of("quest", "Mixed External Quest"))
                .orElseThrow(() -> new GameTestAssertException("Forced scene did not start " + forcedSceneId));
        DialogueOptionDefinition option = DialogueTreeService.activeOptions(context)
                .orElseThrow(() -> new GameTestAssertException("Forced scene had no active options " + forcedSceneId))
                .stream()
                .filter(candidate -> candidate.id().equals(responseOptionId))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Missing forced scene response " + responseOptionId));
        helper.assertValueEqual(
                DialogueTreeService.handleDialogueOption(context, option)
                        .orElseThrow(() -> new GameTestAssertException("Forced scene response did not produce a result " + responseOptionId))
                        .text(),
                expectedResponseText,
                message);
    }

    private static DialogueTreeDefinition singleEntryDialogueTree(
            ResourceLocation id,
            String entryId,
            String label,
            String text) {
        return new DialogueTreeDefinition(
                id,
                label,
                "",
                DialogueEntryMetadata.EMPTY,
                List.of(),
                List.of(dialogueEntry(entryId, label, entryId)),
                Map.of(entryId, dialogueNode(entryId, text, List.of())));
    }

    private static DialogueTreeDefinition externalDialogueTree(ResourceLocation id) {
        return new DialogueTreeDefinition(
                id,
                "External Mixed Tree",
                "",
                DialogueEntryMetadata.EMPTY,
                List.of(),
                List.of(
                        dialogueEntry("long_scene", "External Long Scene", "long_scene"),
                        dialogueEntry("external_default", "External Default Scene", "external_default"),
                        dialogueEntry("forced_entry", "External Forced Scene", "forced_entry")),
                Map.of(
                        "long_scene", dialogueNode("long_scene", "External long scene.", List.of()),
                        "external_default", dialogueNode("external_default", "External scene default entry.", List.of()),
                        "forced_entry", dialogueNode(
                                "forced_entry",
                                "External forced scene.",
                                List.of(new DialogueTreeDefinition.Response(
                                        "external_ack",
                                        "Enough",
                                        DialogueEntryMetadata.EMPTY,
                                        "",
                                        DialogueRequestType.QUESTION,
                                        List.of("External forced response."),
                                        List.of(),
                                        List.of(),
                                        true,
                                        0)))));
    }

    private static DialogueTreeDefinition.Entry dialogueEntry(String id, String label, String start) {
        return new DialogueTreeDefinition.Entry(
                id,
                label,
                DialogueEntryMetadata.EMPTY,
                start,
                DialogueRequestType.QUESTION,
                true,
                true,
                Set.of(),
                Set.of(),
                List.of(),
                false,
                0);
    }

    private static DialogueTreeDefinition.Node dialogueNode(
            String id,
            String text,
            List<DialogueTreeDefinition.Response> responses) {
        return new DialogueTreeDefinition.Node(
                id,
                List.of(text),
                List.of(),
                List.of(),
                responses,
                responses.isEmpty());
    }

    private static EmbeddedDialogueQuest embeddedDialogueQuest(String path, boolean unavailable) {
        JsonObject root = embeddedDialogueQuestV2Fixture(path);
        if (unavailable) {
            JsonObject availability = new JsonObject();
            JsonArray conditions = new JsonArray();
            JsonObject condition = new JsonObject();
            condition.addProperty("type", "quest");
            condition.addProperty("quest", VillagerRetaliation.id(path).toString());
            condition.addProperty("state", "completed");
            conditions.add(condition);
            availability.add("conditions", conditions);
            root.add("availability", availability);
        }
        return embeddedDialogueQuest(path, root);
    }

    private static EmbeddedDialogueQuest embeddedDialogueQuest(String path, JsonObject root) {
        ResourceLocation location = VillagerRetaliation.id("quests/" + path + ".json");
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root)
                .orElseThrow(() -> new GameTestAssertException(path + " envelope did not parse"));
        QuestV2Resource parsed = QuestV2Parser.parse(envelope)
                .orElseThrow(() -> new GameTestAssertException(path + " did not parse"));
        CompiledQuest compiled = QuestV2Compiler.compile(parsed, envelope)
                .orElseThrow(() -> new GameTestAssertException(path + " did not compile"));
        return new EmbeddedDialogueQuest(
                compiled,
                QuestDialogueCompiler.compile(parsed, envelope),
                QuestDialogueCompiler.treeId(parsed.id()));
    }

    private record EmbeddedDialogueQuest(
            CompiledQuest quest,
            QuestDialogueCatalog dialogueCatalog,
            ResourceLocation treeId) {
    }

    private static QuestDefinition withRevision(
            QuestDefinition definition,
            QuestDefinition.Revision revision) {
        return new QuestDefinition(
                definition.id(),
                definition.title(),
                definition.description(),
                definition.titleKey(),
                definition.descriptionKey(),
                definition.questline(),
                definition.tags(),
                definition.parent(),
                definition.prerequisites(),
                definition.showLockedAdventureHint(),
                definition.offer(),
                definition.target(),
                definition.objectives(),
                definition.rules(),
                definition.tracker(),
                definition.entryStage(),
                definition.stages(),
                definition.triggers(),
                definition.rewards(),
                definition.dialogue(),
                definition.metadata(),
                definition.links(),
                revision);
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

    private static QuestDefinition.Objective registryGiftObjective(Set<String> giftReactions) {
        return new QuestDefinition.Objective(
                "registry_gift",
                QuestDefinition.ObjectiveType.GIFT,
                false,
                null,
                Level.OVERWORLD,
                null,
                8,
                List.of(),
                16,
                8,
                null,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                giftReactions,
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
                1,
                false,
                QuestDefinition.ItemRequirements.EMPTY,
                List.of(),
                QuestDefinition.ObjectiveTracker.EMPTY);
    }

    private static QuestDefinition.Objective registryCriterionObjective(
            ResourceLocation criterion,
            Map<String, String> match) {
        return new QuestDefinition.Objective(
                "registry_criterion",
                QuestDefinition.ObjectiveType.CRITERION,
                false,
                null,
                Level.OVERWORLD,
                null,
                8,
                List.of(),
                16,
                8,
                null,
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
                criterion,
                match,
                1,
                false,
                QuestDefinition.ItemRequirements.EMPTY,
                List.of(),
                QuestDefinition.ObjectiveTracker.EMPTY);
    }

    private static QuestDefinition.Objective registryFactObjective(
            QuestDefinition.ObjectiveType type,
            String factKey,
            Set<String> factValues) {
        return new QuestDefinition.Objective(
                "registry_" + type.name().toLowerCase(java.util.Locale.ROOT),
                type,
                false,
                null,
                Level.OVERWORLD,
                null,
                8,
                List.of(),
                16,
                8,
                null,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                null,
                null,
                QuestFactScope.QUEST,
                null,
                Set.of(),
                factKey,
                factValues,
                null,
                null,
                1,
                false,
                QuestDefinition.ItemRequirements.EMPTY,
                List.of(),
                QuestDefinition.ObjectiveTracker.EMPTY);
    }

    private static QuestDefinition.Objective registryConditionObjective() {
        DialogueCondition condition = new DialogueCondition.QuestFact(
                QuestFactScope.QUEST,
                VillagerRetaliation.id("registry_stage"),
                Set.of(),
                "choice",
                Set.of("coast"),
                null,
                null);
        return new QuestDefinition.Objective(
                "registry_condition",
                QuestDefinition.ObjectiveType.CONDITION,
                false,
                null,
                Level.OVERWORLD,
                null,
                8,
                List.of(),
                16,
                8,
                null,
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
                1,
                false,
                QuestDefinition.ItemRequirements.EMPTY,
                List.of(condition),
                QuestDefinition.ObjectiveTracker.EMPTY);
    }

    private static QuestDefinition registryStageQuest(QuestDefinition.Objective objective) {
        QuestDefinition.Stage started = new QuestDefinition.Stage(
                "started",
                List.of(objective.id()),
                List.of(new QuestDefinition.StagePredicate(objective.id(), List.of())),
                "done",
                List.of(),
                List.of(),
                List.of());
        QuestDefinition.Stage done = new QuestDefinition.Stage(
                "done",
                List.of(),
                List.of(),
                "",
                List.of(),
                List.of(),
                List.of());
        return new QuestDefinition(
                VillagerRetaliation.id("registry_stage"),
                "Registry Stage",
                "",
                "",
                "",
                "",
                Set.of("test"),
                null,
                List.of(),
                true,
                null,
                QuestDefinition.Target.EMPTY,
                List.of(objective),
                QuestDefinition.Rules.DEFAULT,
                QuestDefinition.Tracker.EMPTY,
                "",
                Map.of("started", started, "done", done),
                List.of(),
                QuestDefinition.Rewards.EMPTY,
                QuestDefinition.Dialogue.EMPTY,
                null,
                QuestDefinition.Links.EMPTY);
    }

    private static QuestDefinition.Trigger registryTrigger(
            String id,
            QuestDefinition.TriggerEvent event,
            Set<String> stages,
            long cooldownTicks,
            boolean repeatable) {
        return new QuestDefinition.Trigger(
                id,
                event,
                List.of(),
                List.of(),
                stages,
                cooldownTicks,
                10.0D,
                repeatable);
    }

    private static QuestDefinition registryTriggerQuest(List<QuestDefinition.Trigger> triggers) {
        return new QuestDefinition(
                VillagerRetaliation.id("registry_triggers"),
                "Registry Triggers",
                "",
                "",
                "",
                "",
                Set.of("test"),
                null,
                List.of(),
                true,
                null,
                QuestDefinition.Target.EMPTY,
                List.of(),
                QuestDefinition.Rules.DEFAULT,
                QuestDefinition.Tracker.EMPTY,
                "",
                Map.of(),
                triggers,
                QuestDefinition.Rewards.EMPTY,
                QuestDefinition.Dialogue.EMPTY,
                null,
                QuestDefinition.Links.EMPTY);
    }

    private static List<CompiledQuestTrigger> compiledTriggers(List<QuestDefinition.Trigger> triggers) {
        List<CompiledQuestTrigger> compiled = new ArrayList<>();
        for (int index = 0; index < triggers.size(); index++) {
            QuestDefinition.Trigger trigger = triggers.get(index);
            compiled.add(new CompiledQuestTrigger(trigger.id(), index, trigger, null));
        }
        return List.copyOf(compiled);
    }

    private static CompiledQuest compiledQuestWithTriggers(
            CompiledQuest compiled,
            QuestDefinition definition,
            List<QuestDefinition.Trigger> triggers) {
        QuestDefinition compatibility = new QuestDefinition(
                definition.id(),
                definition.title(),
                definition.description(),
                definition.titleKey(),
                definition.descriptionKey(),
                definition.questline(),
                definition.tags(),
                definition.parent(),
                definition.prerequisites(),
                definition.showLockedAdventureHint(),
                definition.offer(),
                definition.target(),
                definition.objectives(),
                definition.rules(),
                definition.tracker(),
                definition.entryStage(),
                definition.stages(),
                triggers,
                definition.rewards(),
                definition.dialogue(),
                definition.metadata(),
                definition.links());
        List<CompiledQuestTrigger> compiledTriggers = compiledTriggers(triggers);
        QuestTriggerIndex index = QuestTriggerRegistry.index(compiledTriggers);
        return new CompiledQuest(
                compiled.id(),
                compiled.source(),
                compatibility,
                compiled.metadata(),
                compiled.provider(),
                compiled.target(),
                compiled.entryStage(),
                compiled.prerequisites(),
                compiled.rules(),
                compiled.ui(),
                compiled.objectives(),
                compiled.objectivesById(),
                compiled.stages(),
                compiled.stagesById(),
                compiledTriggers,
                index.triggersByEvent(),
                index,
                compiled.rewards());
    }

    private static void assertCompiledV1QuestMatchesParsed(
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

    private static void assertCompiledQuestKeepsCanonicalDefinition(
            GameTestHelper helper,
            QuestDefinition quest,
            CompiledQuest compiled) {
        helper.assertValueEqual(compiled.id(), quest.id(), quest.id() + " compiled id");
        helper.assertValueEqual(compiled.schemaVersion(), QuestSchemaVersion.V2, quest.id() + " schema");
        helper.assertValueEqual(compiled.asQuestDefinition(), quest, quest.id() + " compatibility definition");
        helper.assertValueEqual(compiled.source().resource().getNamespace(), VillagerRetaliation.MOD_ID,
                quest.id() + " source namespace");
        helper.assertFalse(compiled.source().resource().getPath().isBlank(), quest.id() + " source path");

        helper.assertValueEqual(compiled.metadata().title(), quest.title(), quest.id() + " title");
        helper.assertValueEqual(compiled.metadata().description(), quest.description(), quest.id() + " description");
        helper.assertValueEqual(compiled.metadata().questline(), quest.questline(), quest.id() + " questline");
        helper.assertValueEqual(compiled.provider().offer(), quest.offer(), quest.id() + " provider");
        helper.assertValueEqual(compiled.target(), quest.target(), quest.id() + " target");
        helper.assertValueEqual(compiled.rules(), quest.rules(), quest.id() + " rules");
        helper.assertValueEqual(compiled.objectives().size(), quest.objectives().size(), quest.id() + " objectives");
        helper.assertValueEqual(compiled.stages().size(), quest.stages().size(), quest.id() + " stages");
        helper.assertValueEqual(compiled.triggers().size(), quest.triggers().size(), quest.id() + " triggers");
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
            helper.assertValueEqual(
                    VillagerQuestResources.objectiveSource(helper.getLevel().getServer(), quest.id(), parsed.id())
                            .orElseThrow(() -> new GameTestAssertException("Missing objective source " + parsed.id())),
                    compiledObjective.source(),
                    quest.id() + "/" + parsed.id() + " objective source lookup");
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
        helper.assertValueEqual(
                compiled.triggerIndex().triggers().stream().map(CompiledQuestTrigger::id).toList(),
                parsedTriggerIds,
                quest.id() + " trigger index order");

        for (int index = 0; index < quest.triggers().size(); index++) {
            QuestDefinition.Trigger parsed = quest.triggers().get(index);
            CompiledQuestTrigger trigger = compiled.triggers().get(index);
            helper.assertValueEqual(trigger.index(), index, quest.id() + "/" + parsed.id() + " trigger index");
            helper.assertValueEqual(trigger.definition(), parsed, quest.id() + "/" + parsed.id() + " trigger");
            helper.assertTrue(
                    compiled.triggersByEvent().getOrDefault(parsed.event(), List.of()).contains(trigger),
                    quest.id() + "/" + parsed.id() + " trigger event index");
            helper.assertTrue(
                    compiled.triggerIndex().triggersByEvent().getOrDefault(parsed.event(), List.of()).contains(trigger),
                    quest.id() + "/" + parsed.id() + " compiled trigger event index");
            if (parsed.event().isContinuous()) {
                helper.assertTrue(
                        compiled.triggerIndex().continuousTriggers().contains(trigger),
                        quest.id() + "/" + parsed.id() + " continuous trigger index");
            }
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

    private static Resource stringResource(String json) {
        return new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertChooseTheHorizonBranchRuntime(
            GameTestHelper helper,
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            ResourceLocation questId,
            ResourceLocation treeId,
            String branchId,
            String nextStage,
        ResourceLocation routeTag) {
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        selectDialogueOption(
                helper,
                context,
                DialogueTreeService.entryOptionId(treeId, "stage.started.offer"));
        helper.assertTrue(
                selectDialogueOption(
                        helper,
                        context,
                        DialogueTreeService.responseOptionId(treeId, branchId)).text().contains("coast".equals(branchId) ? "coast" : "dark roof"),
                branchId + " response text");

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), questId);
        if (progress == null) {
            SceneSavedData scenes = SceneSavedData.get(level);
            var continuation = scenes.continuations().stream()
                    .filter(value -> value.playerId().equals(player.getUUID()) && !value.completionReceipt())
                    .findFirst().orElse(null);
            helper.assertTrue(continuation != null, branchId + " waiting scene continuation missing after response");
            var waitingScene = scenes.get(continuation.sceneInstanceId()).orElse(null);
            helper.assertTrue(waitingScene != null, branchId + " waiting scene instance missing after response");
            SceneTransitionService.complete(scenes, waitingScene, level.getGameTime());
            SceneContinuationService.maintain(level.getServer(), scenes);
            progress = data.get(player.getUUID(), questId);
        }
        helper.assertTrue(progress != null, branchId + " progress missing after response");
        helper.assertValueEqual(progress.currentStage(), nextStage, branchId + " transition stage");
        helper.assertValueEqual(progress.choiceHistory().size(), 1, branchId + " choice history count");
        VillagerQuestSavedData.ChoiceHistoryEntry choice = progress.choiceHistory().getFirst();
        helper.assertValueEqual(choice.scenePath(), "stage.started.scene.offer", branchId + " choice scene path");
        helper.assertValueEqual(choice.responseId(), branchId, branchId + " choice response");
        helper.assertValueEqual(choice.priorStage(), "started", branchId + " choice prior stage");
        helper.assertValueEqual(choice.nextStage(), nextStage, branchId + " choice next stage");

        QuestScopeKey questScope = QuestScopeKey.quest(player.getUUID(), questId);
        VillagerQuestFacts facts = VillagerQuestFacts.get(level);
        helper.assertValueEqual(
                facts.variable(questScope, "choice").orElse(""),
                branchId,
                branchId + " choice fact");
        helper.assertValueEqual(
                facts.variable(questScope, "last_choice_response").orElse(""),
                branchId,
                branchId + " response history fact");
        helper.assertValueEqual(
                facts.variable(questScope, "last_choice_next_stage").orElse(""),
                nextStage,
                branchId + " transition history fact");
        helper.assertTrue(facts.hasTag(QuestScopeKey.player(player.getUUID()), routeTag), branchId + " route tag fact");

        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        VillagerQuestSavedData loaded = VillagerQuestSavedData.load(saved, level.registryAccess());
        VillagerQuestSavedData.QuestProgress loadedProgress = loaded.get(player.getUUID(), questId);
        helper.assertTrue(loadedProgress != null, branchId + " progress did not reload");
        helper.assertValueEqual(loadedProgress.currentStage(), nextStage, branchId + " reloaded stage");
        helper.assertValueEqual(loadedProgress.choiceHistory().getFirst().responseId(), branchId, branchId + " reloaded response");

        VillagerQuestService.DebugInspectResult inspect = VillagerQuestService.debugInspectQuest(player, questId);
        assertDebugTraceContains(helper, inspect, "latest_response=" + branchId);
        assertDebugTraceContains(helper, inspect, "latest_next_stage=" + nextStage);
        VillagerQuestService.DebugInspectResult factScope = VillagerQuestService.debugFactScope(player, questScope.asString());
        assertDebugTraceContains(helper, factScope, "fact variable choice=" + branchId);
        assertDebugTraceContains(helper, factScope, "fact variable last_choice_response=" + branchId);
        assertDebugTraceContains(helper, factScope, "fact variable last_choice_next_stage=" + nextStage);

        var duplicate = VillagerQuestService.applyCompiledTransition(
                context,
                new CompiledQuestTransition(
                        questId,
                        "started",
                        choice.scenePath(),
                        branchId,
                        CompiledQuestTransition.Target.STAGE,
                        nextStage,
                        "/stages/0/dialogue/offer/responses/" + ("coast".equals(branchId) ? "0" : "1")),
                Map.of());
        helper.assertTrue(duplicate.text().contains("already"), branchId + " duplicate response replay was not reported");
        helper.assertValueEqual(progress.currentStage(), nextStage, branchId + " duplicate replay changed stage");
        helper.assertValueEqual(progress.choiceHistory().size(), 1, branchId + " duplicate replay recorded history");
    }

    private static void assertRouteResponse(
            GameTestHelper helper,
            DialogueTreeDefinition.Node node,
            String branchId,
            String nextStage,
            ResourceLocation routeTag) {
        DialogueTreeDefinition.Response response = node.responses().stream()
                .filter(candidate -> candidate.id().equals(branchId))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Missing response " + branchId));
        helper.assertValueEqual(response.actions().size(), 5, branchId + " response action count");
        assertSetVariableAction(helper, response.actions().get(0), "choice", branchId, branchId + " choice action");
        VillagerActionDefinition tagAction = response.actions().get(1);
        helper.assertValueEqual(tagAction.kind(), VillagerActionDefinition.Kind.SET_TAG, branchId + " route tag kind");
        helper.assertValueEqual(tagAction.factScope(), QuestFactScope.PLAYER, branchId + " route tag scope");
        helper.assertValueEqual(tagAction.factTag(), routeTag, branchId + " route tag id");
        VillagerActionDefinition notification = response.actions().get(2);
        helper.assertValueEqual(notification.kind(), VillagerActionDefinition.Kind.NOTIFICATION, branchId + " notification kind");
        helper.assertValueEqual(notification.notificationTrigger(), "quest.updated", branchId + " notification trigger");
        helper.assertFalse(notification.text().isBlank(), branchId + " notification text");
        VillagerActionDefinition scene = response.actions().get(3);
        helper.assertValueEqual(scene.kind(), VillagerActionDefinition.Kind.START_SCENE, branchId + " scene action kind");
        helper.assertValueEqual(scene.sceneId(), VillagerRetaliation.id("atlas_horizon_choice"), branchId + " scene id");
        helper.assertValueEqual(scene.sceneOperationId(), "atlas_horizon_choice_v1", branchId + " scene operation");
        VillagerActionDefinition transition = response.actions().get(4);
        helper.assertValueEqual(transition.kind(), VillagerActionDefinition.Kind.QUEST_TRANSITION, branchId + " transition kind");
        helper.assertValueEqual(transition.questTransition().responseId(), branchId, branchId + " transition response");
        helper.assertValueEqual(transition.questTransition().targetStage(), nextStage, branchId + " transition target");
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

    private static void assertDebugTraceContains(
            GameTestHelper helper,
            VillagerQuestService.DebugInspectResult trace,
            String expected) {
        helper.assertTrue(
                trace.lines().stream().anyMatch(line -> line.contains(expected)),
                "debug trace did not contain \"" + expected + "\": " + trace.lines());
    }

    private static void assertRecentDiagnosticContains(GameTestHelper helper, String expected) {
        helper.assertTrue(
                DatapackDiagnostics.recent().stream().anyMatch(entry -> entry.message().contains(expected)),
                "recent diagnostics did not contain \"" + expected + "\": "
                        + DatapackDiagnostics.recent().stream().map(DatapackDiagnostics.Entry::message).toList());
    }

    private static void assertRecentDiagnosticPointer(GameTestHelper helper, String pointer, String expectedMessage) {
        helper.assertTrue(
                DatapackDiagnostics.structuredRecent().stream().anyMatch(diagnostic ->
                        "quest.v2.validation".equals(diagnostic.code())
                                && pointer.equals(diagnostic.jsonPointer())
                                && diagnostic.message().contains(expectedMessage)),
                "recent structured diagnostics did not contain " + pointer + " / \"" + expectedMessage + "\": "
                        + DatapackDiagnostics.structuredRecent().stream()
                                .map(diagnostic -> diagnostic.jsonPointer() + " :: " + diagnostic.message())
                                .toList());
    }

    private static JsonObject validQuestV2Fixture() {
        return JsonParser.parseString("""
                {
                  "schema": "villagerretaliation:quest/v2",
                  "id": "villagerretaliation:v2_valid_fixture",
                  "metadata": {
                    "title": "A Clean V2 Fixture",
                    "questline": "tests"
                  },
                  "provider": {
                    "type": "villagerretaliation:villager",
                    "required_capabilities": [
                      "villagerretaliation:saved_provider"
                    ]
                  },
                  "availability": {
                    "conditions": [
                      {
                        "type": "quest_fact",
                        "tag": "ready"
                      }
                    ]
                  },
                  "lifecycle": {
                    "on_start": {
                      "actions": [
                        {
                          "type": "notification",
                          "text": "Started"
                        }
                      ]
                    }
                  },
                  "entry_stage": "offer",
                  "stages": [
                    {
                      "id": "offer",
                      "objectives": [
                        {
                          "id": "talk",
                          "type": "choice"
                        }
                      ],
                      "complete_when": [
                        "talk"
                      ],
                      "dialogue": {
                        "offer": {
                          "lines": [
                            "Can you help?"
                          ],
                          "responses": [
                            {
                              "id": "accept",
                              "label": "Yes",
                              "transition": {
                                "stage": "finish"
                              }
                            }
                          ]
                        }
                      },
                      "scenes": [
                        {
                          "id": "intro",
                          "lines": [
                            "Hello."
                          ],
                          "responses": [
                            {
                              "id": "continue",
                              "label": "Continue",
                              "transition": {
                                "stage": "finish"
                              }
                            }
                          ]
                        }
                      ],
                      "responses": [
                        {
                          "id": "skip",
                          "label": "Skip",
                          "transition": "finish"
                        }
                      ],
                      "next": "finish"
                    },
                    {
                      "id": "finish",
                      "objectives": [
                        {
                          "id": "done",
                          "type": "condition",
                          "conditions": [
                            {
                              "type": "quest_fact",
                              "tag": "done"
                            }
                          ]
                        }
                      ],
                      "complete_when": [
                        "done"
                      ],
                      "ui": {
                        "tracker_text": "Return to {issuer}",
                        "placeholders": {
                          "issuer": "provider.name"
                        }
                      }
                    }
                  ],
                  "events": [
                    {
                      "id": "progress",
                      "event": "progress",
                      "stages": [
                        "offer"
                      ],
                      "actions": [
                        {
                          "type": "tracker"
                        }
                      ]
                    }
                  ],
                  "rewards": {
                    "actions": [
                      {
                        "type": "experience",
                        "amount": 5
                      }
                    ]
                  },
                  "ui": {
                    "tracker_text": "Talk to {issuer}",
                    "placeholders": {
                      "issuer": "provider.name"
                    }
                  }
                }
                """).getAsJsonObject();
    }

    private static JsonObject runtimeQuestV2Fixture() {
        return JsonParser.parseString("""
                {
                  "schema": "villagerretaliation:quest/v2",
                  "id": "villagerretaliation:v2_runtime_fixture",
                  "metadata": {
                    "title": "Runtime V2 Fixture",
                    "description": "A silent v2 quest for runtime coverage.",
                    "questline": "tests",
                    "tags": [
                      "test"
                    ]
                  },
                  "provider": {
                    "type": "villagerretaliation:villager"
                  },
                  "entry_stage": "offer",
                  "stages": [
                    {
                      "id": "offer",
                      "objectives": [
                        {
                          "id": "ready",
                          "type": "fact",
                          "scope": "quest",
                          "key": "ready",
                          "value": "yes",
                          "ui": {
                            "tracker_text": "Prepare the v2 fixture."
                          }
                        }
                      ],
                      "complete_when": [
                        "ready"
                      ],
                      "next": "done"
                    },
                    {
                      "id": "done",
                      "objectives": [],
                      "on_enter": [
                        {
                          "type": "tracker"
                        }
                      ],
                      "ui": {
                        "tracker_text": "Return to the issuer."
                      }
                    }
                  ],
                  "rewards": {
                    "experience": 7
                  },
                  "ui": {
                    "tracker_text": "Prepare the v2 fixture."
                  }
                }
                """).getAsJsonObject();
    }

    private static JsonObject optionalTrackerQuestV2Fixture() {
        return JsonParser.parseString("""
                {
                  "schema": "villagerretaliation:quest/v2",
                  "id": "villagerretaliation:v2_optional_tracker_fixture",
                  "metadata": {
                    "title": "Optional Tracker V2 Fixture",
                    "description": "A v2 quest that keeps optional tracker text non-blocking.",
                    "questline": "tests"
                  },
                  "provider": {
                    "type": "villagerretaliation:villager"
                  },
                  "entry_stage": "offer",
                  "stages": [
                    {
                      "id": "offer",
                      "objectives": [
                        {
                          "id": "required",
                          "type": "fact",
                          "scope": "quest",
                          "key": "ready",
                          "value": "yes"
                        },
                        {
                          "id": "optional_hint",
                          "type": "fact",
                          "optional": true,
                          "scope": "quest",
                          "key": "optional_done",
                          "value": "yes",
                          "ui": {
                            "tracker_text": "Optional fact {objective_fact_key}"
                          }
                        }
                      ],
                      "complete_when": [
                        "required"
                      ]
                    }
                  ]
                }
                """).getAsJsonObject();
    }

    private static JsonObject dialogueQuestV2Fixture() {
        return JsonParser.parseString("""
                {
                  "schema": "villagerretaliation:quest/v2",
                  "id": "villagerretaliation:v2_dialogue_fixture",
                  "metadata": {
                    "title": "Dialogue V2 Fixture",
                    "description": "A v2 quest with embedded dialogue scenes.",
                    "questline": "tests",
                    "tags": [
                      "test"
                    ]
                  },
                  "provider": {
                    "type": "villagerretaliation:villager"
                  },
                  "lifecycle": {
                    "dialogue": [
                      {
                        "id": "opening",
                        "text": "Welcome to v2 dialogue.",
                        "responses": [
                          {
                            "id": "continue",
                            "label": "Continue"
                          }
                        ]
                      }
                    ]
                  },
                  "entry_stage": "offer",
                  "stages": [
                    {
                      "id": "offer",
                      "objectives": [
                        {
                          "id": "ready",
                          "type": "fact",
                          "scope": "quest",
                          "key": "ready",
                          "value": "yes"
                        }
                      ],
                      "complete_when": [
                        "ready"
                      ],
                      "dialogue": {
                        "offer": {
                          "scene": "offer_intro",
                          "conditions": [
                            {
                              "type": "quest",
                              "state": "not_started"
                            }
                          ]
                        },
                        "ready": {
                          "text": "You look ready.",
                          "responses": [
                            {
                              "id": "ready_done",
                              "label": "Ready.",
                              "actions": [
                                {
                                  "type": "set_variable",
                                  "scope": "quest",
                                  "key": "ready_dialogue",
                                  "value": "seen"
                                }
                              ]
                            }
                          ]
                        }
                      },
                      "scenes": [
                        {
                          "id": "offer_intro",
                          "text": "Can you help with this v2 errand?",
                          "actions": [
                            {
                              "type": "tracker"
                            }
                          ],
                          "responses": [
                            {
                              "id": "accept",
                              "label": "I will help.",
                              "actions": [
                                {
                                  "type": "quest",
                                  "action": "start"
                                }
                              ],
                              "scene": "details"
                            },
                            {
                              "id": "decline",
                              "label": "Not now.",
                              "text": "Maybe later."
                            }
                          ]
                        },
                        {
                          "id": "details",
                          "lines": [
                            "Bring back proof."
                          ],
                          "conditions": [
                            {
                              "type": "quest",
                              "state": "active"
                            }
                          ],
                          "responses": [
                            {
                              "id": "ask",
                              "label": "What proof?",
                              "text": "Anything marked."
                            }
                          ]
                        }
                      ],
                      "responses": [
                        {
                          "id": "stage_help",
                          "label": "What now?",
                          "text": "Keep going.",
                          "actions": [
                            {
                              "type": "notification",
                              "text": "Quest updated: {quest}"
                            }
                          ]
                        }
                      ],
                      "ui": {
                        "tracker_text": "Follow the generated dialogue."
                      }
                    }
                  ]
                }
                """).getAsJsonObject();
    }

    private static JsonObject embeddedDialogueQuestV2Fixture(String path) {
        JsonObject root = JsonParser.parseString("""
                {
                  "schema": "villagerretaliation:quest/v2",
                  "metadata": {
                    "title": "Embedded Runtime Quest",
                    "description": "A v2 quest whose dialogue is embedded in the quest file.",
                    "questline": "tests",
                    "tags": [
                      "test"
                    ]
                  },
                  "provider": {
                    "type": "villagerretaliation:villager"
                  },
                  "entry_stage": "offer",
                  "stages": [
                    {
                      "id": "offer",
                      "objectives": [
                        {
                          "id": "ready",
                          "type": "fact",
                          "scope": "quest",
                          "key": "ready",
                          "value": "yes"
                        }
                      ],
                      "complete_when": [
                        "ready"
                      ],
                      "dialogue": {
                        "offer": {
                          "text": "Can you take this embedded errand?",
                          "responses": [
                            {
                              "id": "accept",
                              "label": "I can help.",
                              "actions": [
                                {
                                  "type": "quest",
                                  "action": "start",
                                  "lines": {
                                    "started": [
                                      "Embedded quest started."
                                    ],
                                    "unavailable": [
                                      "This embedded quest is unavailable."
                                    ]
                                  }
                                }
                              ]
                            },
                            {
                              "id": "decline",
                              "label": "Not now.",
                              "text": "Not now, then."
                            }
                          ]
                        },
                        "reminder": {
                          "text": "Embedded reminder active."
                        },
                        "ready": {
                          "text": "Ready to close embedded quest?",
                          "responses": [
                            {
                              "id": "complete",
                              "label": "Close it.",
                              "actions": [
                                {
                                  "type": "quest",
                                  "action": "turn_in",
                                  "lines": {
                                    "completed": [
                                      "Embedded quest complete."
                                    ],
                                    "missing_objectives": [
                                      "Embedded quest is not ready."
                                    ],
                                    "unavailable": [
                                      "This embedded quest is unavailable."
                                    ]
                                  }
                                }
                              ]
                            }
                          ]
                        },
                        "already_completed": {
                          "text": "Embedded quest already complete."
                        },
                        "unavailable": {
                          "text": "This embedded quest is unavailable."
                        }
                      },
                      "ui": {
                        "tracker_text": "Set the ready fact to close the embedded quest."
                      }
                    }
                  ]
                }
                """).getAsJsonObject();
        root.addProperty("id", VillagerRetaliation.id(path).toString());
        return root;
    }

    private static JsonObject externalDialogueQuestV2Fixture(
            String path,
            ResourceLocation externalTreeId,
            ResourceLocation missingTreeId) {
        JsonObject root = JsonParser.parseString("""
                {
                  "schema": "villagerretaliation:quest/v2",
                  "metadata": {
                    "title": "Mixed External Quest",
                    "description": "A v2 quest with inline and external dialogue.",
                    "questline": "tests",
                    "tags": [
                      "test"
                    ]
                  },
                  "provider": {
                    "type": "villagerretaliation:villager"
                  },
                  "entry_stage": "offer",
                  "stages": [
                    {
                      "id": "offer",
                      "objectives": [
                        {
                          "id": "ready",
                          "type": "fact",
                          "scope": "quest",
                          "key": "ready",
                          "value": "yes"
                        }
                      ],
                      "complete_when": [
                        "ready"
                      ],
                      "dialogue": {
                        "offer": {
                          "text": "Inline mixed offer.",
                          "responses": [
                            {
                              "id": "accept",
                              "label": "Accept",
                              "text": "Mixed quest started.",
                              "actions": [
                                {
                                  "type": "quest",
                                  "action": "start"
                                }
                              ]
                            }
                          ]
                        },
                        "reminder": {
                          "external": {
                            "tree": "__external_tree__",
                            "entry": "long_scene"
                          }
                        },
                        "ready": {
                          "text": "Inline ready scene."
                        },
                        "named_external": {
                          "scene": "external_default"
                        },
                        "missing_tree": {
                          "external": {
                            "tree": "__missing_tree__",
                            "entry": "missing"
                          }
                        },
                        "missing_entry": {
                          "external": {
                            "tree": "__external_tree__",
                            "entry": "not_there"
                          }
                        }
                      },
                      "scenes": [
                        {
                          "id": "storm_scene",
                          "text": "Inline forced scene for {quest}.",
                          "responses": [
                            {
                              "id": "storm_ack",
                              "label": "I hear you.",
                              "text": "Stay ready."
                            }
                          ]
                        },
                        {
                          "id": "external_default",
                          "external": {
                            "tree": "__external_tree__"
                          }
                        },
                        {
                          "id": "external_forced",
                          "external": {
                            "tree": "__external_tree__",
                            "entry": "forced_entry"
                          }
                        }
                      ],
                      "ui": {
                        "tracker_text": "Follow the mixed external quest."
                      }
                    }
                  ]
                }
                """.replace("__external_tree__", externalTreeId.toString())
                .replace("__missing_tree__", missingTreeId.toString())).getAsJsonObject();
        root.addProperty("id", VillagerRetaliation.id(path).toString());
        return root;
    }

    private static JsonObject branchTransitionQuestV2Fixture(String path) {
        JsonObject root = JsonParser.parseString("""
                {
                  "schema": "villagerretaliation:quest/v2",
                  "metadata": {
                    "title": "Branch Transition Quest",
                    "description": "A v2 quest that records response-driven branch choices.",
                    "questline": "tests",
                    "tags": [
                      "test"
                    ]
                  },
                  "provider": {
                    "type": "villagerretaliation:villager"
                  },
                  "entry_stage": "choose",
                  "stages": [
                    {
                      "id": "choose",
                      "objectives": [],
                      "responses": [
                        {
                          "id": "alpha",
                          "label": "Take alpha.",
                          "text": "Alpha selected.",
                          "actions": [
                            {
                              "type": "set_variable",
                              "scope": "quest",
                              "key": "branch",
                              "value": "alpha"
                            }
                          ],
                          "transition": "alpha"
                        },
                        {
                          "id": "beta",
                          "label": "Take beta.",
                          "text": "Beta selected.",
                          "next": "beta"
                        },
                        {
                          "id": "blocked",
                          "label": "Try blocked.",
                          "text": "Blocked selected.",
                          "actions": [
                            {
                              "type": "loot",
                              "loot_table": "villagerretaliation:missing_required_transition_loot",
                              "required": true
                            }
                          ],
                          "transition": "beta"
                        }
                      ],
                      "ui": {
                        "tracker_text": "Choose a deterministic branch."
                      }
                    },
                    {
                      "id": "alpha",
                      "objectives": [],
                      "dialogue": {
                        "reminder": {
                          "text": "Alpha branch active."
                        }
                      },
                      "ui": {
                        "tracker_text": "Alpha branch."
                      },
                      "rewards": {
                        "actions": [
                          {"type": "profile_attribute", "attribute": "guts", "amount": 5}
                        ]
                      }
                    },
                    {
                      "id": "beta",
                      "objectives": [],
                      "dialogue": {
                        "reminder": {
                          "text": "Beta branch active."
                        }
                      },
                      "ui": {
                        "tracker_text": "Beta branch."
                      },
                      "rewards": {
                        "actions": [
                          {"type": "profile_attribute", "attribute": "knowledge", "amount": 3}
                        ]
                      }
                    }
                  ]
                }
                """).getAsJsonObject();
        root.addProperty("id", VillagerRetaliation.id(path).toString());
        return root;
    }

    private static JsonObject failureQuestV2Fixture(String path) {
        JsonObject root = JsonParser.parseString("""
                {
                  "schema": "villagerretaliation:quest/v2",
                  "metadata": {"title":"Failure Contract","questline":"tests"},
                  "provider": {"type":"villagerretaliation:villager"},
                  "availability": {"repeatable":true,"max_starts":3},
                  "lifecycle": {
                    "on_fail": {"actions":[{"type":"set_variable","scope":"quest","key":"failure_hook","value":"ran"}]},
                    "on_abandon": {"actions":[{"type":"set_variable","scope":"quest","key":"abandon_hook","value":"ran"}]}
                  },
                  "entry_stage": "start",
                  "stages": [{
                    "id":"start",
                    "objectives":[],
                    "responses":[{"id":"fail_now","label":"Fail","transition":{"fail":true}}]
                  }],
                  "events": [{"id":"event_fail","event":"progress","transition":{"fail":true}}],
                  "rewards": {"actions":[{"type":"experience","amount":25}]}
                }
                """).getAsJsonObject();
        root.addProperty("id", VillagerRetaliation.id(path).toString());
        return root;
    }

    private static JsonObject providerRecoveryQuestV2Fixture(String path, boolean crossVillagerCompatible) {
        JsonObject root = JsonParser.parseString("""
                {
                  "schema":"villagerretaliation:quest/v2",
                  "metadata":{"title":"Provider Recovery","questline":"tests"},
                  "provider":{"type":"villagerretaliation:villager","filters":{"professions":["minecraft:farmer"]}},
                  "availability":{"repeatable":true,"max_starts":4,"locked_to_villager":true},
                  "lifecycle":{"on_abandon":{"actions":[{"type":"counter","scope":"quest","key":"abandon_replays","amount":1}]}},
                  "entry_stage":"start",
                  "stages":[{"id":"start","objectives":[]}]
                }
                """).getAsJsonObject();
        root.addProperty("id", VillagerRetaliation.id(path).toString());
        root.getAsJsonObject("availability").addProperty("cross_villager_compatible", crossVillagerCompatible);
        return root;
    }

    private static CompiledQuest compileQuestFixture(ResourceLocation location, JsonObject root) {
        QuestResourceEnvelope envelope = QuestResourceEnvelope.read(location, root).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }

    private static JsonObject invalidQuestV2Fixture() {
        return JsonParser.parseString("""
                {
                  "schema": "villagerretaliation:quest/v2",
                  "id": "villagerretaliation:v2_invalid_fixture",
                  "provider": {
                    "type": "villagerretaliation:villager",
                    "required_capabilities": [
                      "villagerretaliation:fake_live"
                    ]
                  },
                  "lifecycle": {
                    "on_start": {
                      "actions": [
                        {
                          "type": "notification"
                        }
                      ],
                      "transition": {
                        "stage": "start"
                      }
                    }
                  },
                  "entry_stage": "start",
                  "stages": [
                    {
                      "id": "start",
                      "surprise": true,
                      "objectives": [
                        {
                          "id": "__generated_auto",
                          "type": "not_real"
                        },
                        {
                          "id": "same",
                          "type": "choice"
                        },
                        {
                          "id": "same",
                          "type": "choice"
                        }
                      ],
                      "complete_when": [
                        "missing_objective"
                      ],
                      "dialogue": {
                        "offer": {
                          "scene": "missing_scene"
                        }
                      },
                      "scenes": [
                        {
                          "id": "intro",
                          "lines": [
                            "Inline text"
                          ],
                          "external_scene": "villagerretaliation:intro",
                          "responses": [
                            {
                              "label": "No id",
                              "actions": [
                                {
                                  "type": "set_variable",
                                  "scope": "quest",
                                  "stage": "orphan"
                                }
                              ],
                              "transition": {
                                "stage": "missing_stage",
                                "complete": true
                              }
                            }
                          ]
                        }
                      ],
                      "next": {
                        "stage": "missing_stage",
                        "complete": true
                      },
                      "events": [
                        {
                          "id": "stage_event",
                          "event": "unknown_event",
                          "stages": [
                            "missing_stage"
                          ]
                        }
                      ],
                      "ui": {
                        "tracker_text": "Hi {missing}",
                        "placeholders": {}
                      }
                    },
                    {
                      "id": "orphan",
                      "objectives": [
                        {
                          "id": "orphan_obj",
                          "type": "choice"
                        }
                      ]
                    }
                  ]
                }
                """).getAsJsonObject();
    }

    private static JsonObject invalidQuestV2MissingEntryFixture() {
        JsonObject root = validQuestV2Fixture();
        root.addProperty("id", "villagerretaliation:v2_missing_entry_fixture");
        root.addProperty("entry_stage", "missing_stage");
        return root;
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

    private static void assertGeneratedQuestDialogueLifecycle(
            GameTestHelper helper,
            MinecraftServer server,
            ResourceLocation questId,
            QuestDialogueCatalog catalog) {
        assertGeneratedQuestDialogueSlot(helper, server, questId, catalog, "offer", VillagerActionDefinition.QuestAction.START);
        assertGeneratedQuestDialogueSlot(helper, server, questId, catalog, "reminder", VillagerActionDefinition.QuestAction.REMIND);
        assertGeneratedQuestDialogueSlot(helper, server, questId, catalog, "turn_in", VillagerActionDefinition.QuestAction.TURN_IN);
    }

    private static void assertGeneratedQuestDialogueSlot(
            GameTestHelper helper,
            MinecraftServer server,
            ResourceLocation questId,
            QuestDialogueCatalog catalog,
            String slot,
            VillagerActionDefinition.QuestAction action) {
        QuestDialogueCatalog.Binding binding = catalog.bindings(questId).stream()
                .filter(candidate -> candidate.slot().equals(slot))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException(questId + " has no generated " + slot + " binding"));
        DialogueTreeDefinition tree = DialogueTreeResources.tree(server, LOCALE, binding.treeId())
                .orElseThrow(() -> new GameTestAssertException(questId + " generated tree missing " + binding.treeId()));
        assertEntryLifecycle(helper, questId, tree, binding.entryId(), action);
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
        if (expectedAction == VillagerActionDefinition.QuestAction.START
                && action.kind() == VillagerActionDefinition.Kind.QUEST_TRANSITION
                && Objects.equals(action.questTransition().questId(), questId)) {
            return true;
        }
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

    private static Path projectPath(String first, String... more) {
        Path requested = Path.of(first, more);
        List<Path> candidates = List.of(
                requested,
                Path.of("..").resolve(requested),
                Path.of("..", "..").resolve(requested),
                Path.of("..", "..", "..").resolve(requested));
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.normalize();
            }
        }
        throw new GameTestAssertException("Could not find project file " + requested);
    }

    private static String normalizeLineEndings(String value) {
        return value == null ? "" : value.replace("\r\n", "\n");
    }
}
