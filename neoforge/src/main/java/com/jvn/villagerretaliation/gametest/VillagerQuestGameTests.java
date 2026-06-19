package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueTreeDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
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
