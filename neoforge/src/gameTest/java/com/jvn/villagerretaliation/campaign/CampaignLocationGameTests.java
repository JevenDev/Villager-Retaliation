package com.jvn.villagerretaliation.campaign;

import java.util.LinkedHashMap;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignLocationDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.QuestContentLoadReport;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.executor.BuiltinSceneStepExecutors;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import com.jvn.villagerretaliation.village.VillageRegistrySavedData;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignLocationGameTests {
    private static final ResourceLocation CAMPAIGN = id("locations");
    private static final ResourceLocation SCENE = id("location_greeting");

    private CampaignLocationGameTests() { }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void locationDefinitionsValidateBoundsSelectorsAndSourcePointers(GameTestHelper helper) throws IOException {
        var root = resource("campaigns/locations/campaign.json");
        var parsed = CampaignDefinitionParser.parse(CAMPAIGN, root);
        helper.assertTrue(parsed.valid(), "all three authored location kinds compile: " + parsed.diagnostics());
        helper.assertValueEqual(new ArrayList<>(parsed.definition().locations().keySet()), List.of("home", "waypoint", "plains_village"), "authored ordering survives compilation");
        for (String invalid : List.of("{\"source\":\"unbounded\"}", "{\"search_radius\":257}", "{\"search_radius\":0}", "{\"search_radius\":1.5}",
                "{\"structure\":\"minecraft:village_plains\"}", "{\"source\":\"marker\"}", "{\"source\":\"structure\"}",
                "{\"origin\":{\"x\":0,\"z\":0}}", "{\"origin\":{\"x\":30000001,\"y\":70,\"z\":0}}", "{\"title_key\":\"outside.title\"}")) {
            var candidate = root.deepCopy();
            var location = candidate.getAsJsonArray("locations").get(0).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> location.add(entry.getKey(), entry.getValue()));
            var rejected = CampaignDefinitionParser.parse(CAMPAIGN, candidate);
            helper.assertFalse(rejected.valid(), "unsafe or ambiguous location rejected: " + invalid);
            helper.assertTrue(rejected.diagnostics().stream().anyMatch(issue -> issue.jsonPointer().startsWith("/locations/0")), "location diagnostic retains its pointer");
        }
        root.getAsJsonArray("locations").get(1).getAsJsonObject().getAsJsonObject("origin").addProperty("x", 12);
        helper.assertValueEqual(parsed.definition().locations().get("waypoint").origin(), new BlockPos(0, 80, 0), "typed location detached from JSON");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void locationObjectivesRejectOverridesAndRequireTheirCampaign(GameTestHelper helper) throws IOException {
        var raw = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Format.runtime(resource("quests/campaign_fixture/locations/quest.json"));
        var parsed = QuestV2Parser.parse(CAMPAIGN, raw).orElseThrow();
        var compiled = QuestV2Compiler.compile(parsed, QuestResourceEnvelope.read(CAMPAIGN, raw).orElseThrow()).orElseThrow();
        helper.assertValueEqual(compiled.objectives().getFirst().definition().campaignLocation(), "home", "compiled quest retains its location alias");
        for (String invalid : List.of("{\"campaign_location\":null}", "{\"campaign_location\":true}", "{\"campaign_location\":\"bad name\"}",
                "{\"type\":\"condition\"}", "{\"dimension\":\"minecraft:overworld\"}", "{\"location\":{\"x\":0,\"y\":80,\"z\":0}}")) {
            var candidate = raw.deepCopy();
            var objective = candidate.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonArray("objectives").get(0).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> objective.add(entry.getKey(), entry.getValue()));
            helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, candidate).isEmpty(), "objective cannot bypass its binding: " + invalid);
        }
        raw.getAsJsonObject("metadata").remove("campaign");
        helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, raw).isEmpty(), "ordinary quest cannot claim an unowned location");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void sceneLocationsRequireFixedPositionActors(GameTestHelper helper) throws IOException {
        var root = resource("quests/campaign_fixture/locations/scenes/location_greeting.json");
        helper.assertTrue(SceneCompiler.compile(SceneParser.parse(SCENE, root).resource()).valid(), "fixture scene compiles");
        for (String invalid : List.of("{\"type\":\"villagerretaliation:villager\"}", "{\"binding\":\"bad name\"}",
                "{\"replacement_policy\":\"operator_rebindable\"}", "{\"filters\":{\"x\":\"20\"}}")) {
            var candidate = root.deepCopy();
            var actor = candidate.getAsJsonArray("actors").get(1).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> actor.add(entry.getKey(), entry.getValue()));
            helper.assertFalse(SceneCompiler.compile(SceneParser.parse(SCENE, candidate).resource()).valid(), "campaign scene location cannot be replaced: " + invalid);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void bindingsAreDurableAndCompetingSearchesKeepTheWinner(GameTestHelper helper) throws Exception {
        try (var disk = new Disk(); var executor = Executors.newFixedThreadPool(2)) {
            var data = disk.open();
            var first = binding(UUID.randomUUID(), "home", new BlockPos(10, 70, 10));
            var second = binding(first.instance(), "home", new BlockPos(20, 70, 20));
            var results = executor.invokeAll(List.<Callable<CampaignLocationBinding>>of(() -> data.bind(first), () -> data.bind(second)));
            var winner = results.getFirst().get();
            helper.assertValueEqual(results.getLast().get(), winner, "both callers see the committed destination");
            helper.assertValueEqual(disk.reload().bindings().get(winner.key()), winner, "binding was written before publication");
            helper.assertValueEqual(disk.reload().bind(second), winner, "reload and another search do not move the destination");
            try {
                data.bind(binding(UUID.randomUUID(), "other", new BlockPos(1, 70, 1)));
                helper.fail("another alias cannot replace campaign instance ownership");
            } catch (IllegalArgumentException expected) {
                helper.assertValueEqual(data.bindings().size(), 1, "ownership conflict leaves original ledger intact");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void failedWritesDoNotPublishOrReplaceBindings(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var blocked = disk.root.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            try {
                var data = CampaignLocationSavedData.load(new CompoundTag(), blocked);
                try {
                    data.bind(binding(UUID.randomUUID(), "home", new BlockPos(1, 70, 1)));
                    helper.fail("location write must fail");
                } catch (IOException expected) {
                    helper.assertTrue(data.bindings().isEmpty(), "failed save does not publish a destination");
                }
            } finally { Files.delete(blocked); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void corruptAndFutureBindingsRemainReadableAndCannotBeOverwritten(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var data = disk.open();
            data.bind(binding(UUID.randomUUID(), "home", new BlockPos(1, 70, 1)));
            var original = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            for (int mutation = 0; mutation < 4; mutation++) {
                var tag = original.copy();
                if (mutation == 0) tag.putInt("DataVersion", 99);
                if (mutation == 1) tag.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).putString("X", "broken");
                if (mutation == 2) tag.getList("Bindings", Tag.TAG_COMPOUND).add(tag.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).copy());
                if (mutation == 3) tag = new CompoundTag();
                var loaded = CampaignLocationSavedData.load(tag, disk.root);
                helper.assertFalse(loaded.diagnostic().isBlank(), "invalid stored ledger is dormant");
                helper.assertValueEqual(loaded.save(new CompoundTag(), helper.getLevel().registryAccess()), tag, "raw data retained for recovery");
                try {
                    loaded.bind(binding(UUID.randomUUID(), "home", new BlockPos(2, 70, 2)));
                    helper.fail("cannot overwrite dormant location data");
                } catch (IOException expected) {
                    helper.assertValueEqual(disk.reload().save(new CompoundTag(), helper.getLevel().registryAccess()), original, "failed replacement cannot alter disk");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void searchesStayWithinTheirAreaAndNeverLoadMissingChunks(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var level = helper.getLevel();
            var origin = new BlockPos(20_000_000, 80, 20_000_000);
            int chunks = level.getChunkSource().getLoadedChunksCount();
            helper.assertTrue(level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4) == null, "remote test area starts unloaded");
            var structure = new CampaignLocationDefinition("unloaded", "test.title", CampaignLocationDefinition.Source.STRUCTURE,
                    level.dimension().location(), origin, 256, ResourceLocation.parse("minecraft:village_plains"), "");
            helper.assertTrue(CampaignLocationService.find(level, structure) == null, "unloaded structures remain unresolved");
            helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), chunks, "bounded search adds no chunk loads");
            var villages = VillageRegistrySavedData.get(level);
            villages.keyFor(level, fixture.origin.offset(80, 0, 0));
            var within = new CampaignLocationDefinition("nearby", "test.title", CampaignLocationDefinition.Source.VILLAGE,
                    level.dimension().location(), fixture.origin, 32, null, "");
            helper.assertTrue(CampaignLocationService.find(level, within) == null, "recorded village outside authored radius is ignored");
            villages.keyFor(level, fixture.origin);
            helper.assertValueEqual(CampaignLocationService.find(level, within).position(), fixture.origin, "recorded village within radius resolves");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void registeredStructureStartsResolveWithoutChangingBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var chunk = level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
        var prior = Map.copyOf(chunk.getAllStarts());
        var structureId = ResourceLocation.parse("minecraft:desert_pyramid");
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureId);
        var piece = new DesertPyramidPiece(RandomSource.create(1L), origin.getX(), origin.getZ());
        var start = new StructureStart(structure, chunk.getPos(), 0, new PiecesContainer(List.of(piece)));
        helper.setBlock(1, 1, 1, Blocks.GOLD_BLOCK);
        try {
            chunk.setStartForStructure(structure, start);
            var declaration = new CampaignLocationDefinition("ruin", "test.title", CampaignLocationDefinition.Source.STRUCTURE,
                    level.dimension().location(), origin, 64, structureId, "");
            var found = CampaignLocationService.find(level, declaration);
            helper.assertTrue(found != null && found.identity().startsWith("structure:minecraft:desert_pyramid@"), "registered loaded structure is eligible");
            helper.assertValueEqual(found.position(), start.getBoundingBox().getCenter(), "binding uses this structure start");
            helper.assertBlockPresent(Blocks.GOLD_BLOCK, 1, 1, 1);
        } finally { chunk.setAllStarts(prior); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void campaignBindingsOutliveVillageRecordsAndRemainSharedAcrossPlayers(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var first = fixture.bindHome();
            var village = VillageRegistrySavedData.get(helper.getLevel());
            village.keyFor(helper.getLevel(), fixture.origin.offset(2, 0, 0));
            helper.assertValueEqual(fixture.resolve("home").binding(), first, "village center updates cannot move a campaign destination");
            fixture.reload();
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_village_registry", new VillageRegistrySavedData());
            helper.assertValueEqual(fixture.resolve("home").binding(), first, "binding survives registry loss and reload");
            var objective = fixture.catalog.compiledQuest(CAMPAIGN).orElseThrow().objectives().getFirst().definition();
            var quest = fixture.catalog.quest(CAMPAIGN).orElseThrow();
            for (int index = 0; index < 2; index++) {
                var player = helper.makeMockServerPlayerInLevel();
                var progress = new VillagerQuestSavedData.QuestProgress();
                player.setPos(first.position().getX(), first.position().getY(), first.position().getZ());
                var context = new QuestObjectiveEvaluationContext(player, null, helper.getLevel(), quest, progress, null, null, null, null, null, null);
                helper.assertTrue(QuestObjectiveRegistry.evaluate(context, objective).orElseThrow().complete(), "each player visits the same campaign binding");
                player.setPos(first.position().getX() + 64, first.position().getY(), first.position().getZ());
                helper.assertFalse(QuestObjectiveRegistry.evaluate(context, objective).orElseThrow().complete(), "being near another destination cannot complete the objective");
            }
            helper.assertValueEqual(VillagerQuestTargets.locateCampaignLocation(helper.getLevel(), quest, objective).orElseThrow().pos(), first.position(), "quest guidance uses authoritative location");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void missingContentAndWorldModesDoNotReplaceBindings(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var original = fixture.bindHome();
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(fixture.catalog, Map.of(), List.of()));
            helper.assertFalse(fixture.resolve("home").available(), "removed content leaves locations dormant");
            CampaignResources.installForTests(fixture.server, fixture.campaigns);
            for (String mode : List.of("sandbox", "legacy")) {
                fixture.mode(mode);
                helper.assertFalse(fixture.resolve("home").available(), "campaign location unavailable in " + mode);
            }
            fixture.mode("campaign");
            helper.assertValueEqual(fixture.resolve("home").binding(), original, "restored mode and content recover original location");
            var changed = resource("campaigns/locations/campaign.json");
            changed.addProperty("revision", 2);
            var definition = CampaignDefinitionParser.parse(CAMPAIGN, changed).definition();
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(fixture.catalog, Map.of(CAMPAIGN,
                    new CampaignContentCatalog.Entry(definition, fixture.campaigns.campaigns().get(CAMPAIGN).localization())), List.of()));
            helper.assertFalse(fixture.resolve("home").available(), "incompatible campaign update blocks old location");
            helper.assertValueEqual(CampaignLocationSavedData.get(helper.getLevel()).bindings().get(original.key()), original, "blocked attempts preserve binding");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void sceneWaitsForMissingLocationAcrossReloadThenUsesTheSharedBinding(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var player = helper.makeMockServerPlayerInLevel();
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            var guide = SceneActorBinding.entity("guide", ResourceLocation.parse("villagerretaliation:villager"), villager.getUUID(),
                    ResourceLocation.parse("villagerretaliation:villager"), helper.getLevel().dimension().location(), villager.blockPosition(), "Guide", true);
            var scene = new SceneInstance(UUID.randomUUID(), fixture.scene, "fixture", new SceneOwner(SceneResource.OwnershipMode.PLAYER,
                    player.getUUID(), null, null, ""), UUID.randomUUID(), Set.of(player.getUUID()), Map.of("guide", guide), 1L);
            scene.linkQuest(CAMPAIGN);
            scene.bindCampaign(CAMPAIGN);
            var scenes = new SceneSavedData();
            SceneStepEngine.process(fixture.server, scenes, scene, fixture.scene, 3L);
            helper.assertTrue(scene.campaignPaused() && scene.stepRecords().isEmpty(), "unresolved required location pauses before effects");
            var restored = SceneInstance.load(scene.save());
            fixture.reload();
            var binding = fixture.bindHome();
            BuiltinSceneStepExecutors.register();
            for (long time = 103L; time < 125L && !restored.state().terminal(); time++) SceneStepEngine.process(fixture.server, scenes, restored, fixture.scene, time);
            helper.assertValueEqual(restored.state(), SceneState.COMPLETED, "restored scene resumes when destination becomes available");
            helper.assertValueEqual(restored.actorBindings().get("home").lastPosition(), binding.position(), "scene shares quest destination");
            helper.assertFalse(restored.campaignPaused(), "location recovery clears its durable pause");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void explicitMarkersBindWithoutLoadingChunksAndMissingDimensionsCanRecover(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var raw = resource("campaigns/locations/campaign.json");
            var marker = raw.getAsJsonArray("locations").get(1).getAsJsonObject();
            marker.addProperty("dimension", "test:missing_dimension");
            marker.getAsJsonObject("origin").addProperty("x", 20_000_000);
            marker.getAsJsonObject("origin").addProperty("z", 20_000_000);
            fixture.install(raw);
            helper.assertTrue(fixture.resolve("waypoint").diagnostic().contains("dimension is unavailable"), "missing dimension gives a repairable diagnostic");
            helper.assertTrue(CampaignLocationSavedData.get(helper.getLevel()).bindings().isEmpty(), "unavailable marker cannot reserve a false binding");
            marker.addProperty("dimension", "minecraft:overworld");
            fixture.install(raw);
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            var bound = fixture.resolve("waypoint");
            helper.assertTrue(bound.available(), "authored marker resolves after dimension restoration");
            helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(20_000_000 >> 4, 20_000_000 >> 4) == null, "marker chunk remains unloaded");
            fixture.reload();
            helper.assertValueEqual(fixture.resolve("waypoint").binding(), bound.binding(), "unloaded marker retains identity after reload");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "marker binding never loads terrain");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void missingAliasesAndLocationTextRejectCampaignPublication(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var raw = resource("campaigns/locations/campaign.json");
            raw.getAsJsonArray("locations").remove(0);
            var parsed = CampaignDefinitionParser.parse(CAMPAIGN, raw);
            var issues = CampaignDefinitionParser.validateQuestReferences(parsed.definition(), fixture.catalog);
            helper.assertTrue(issues.stream().anyMatch(issue -> issue.jsonPointer().endsWith("/campaign_location")), "quest reference retains the authored source pointer");
            helper.assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("scene") && issue.message().contains("home")), "scene references are checked against campaign aliases");
            var valid = resource("campaigns/locations/campaign.json");
            var locales = new LinkedHashMap<>(fixture.campaigns.campaigns().get(CAMPAIGN).localization().messages("en_us"));
            locales.remove("campaign_fixture.campaign.locations.home");
            var rejected = CampaignContentCatalog.compile(fixture.catalog, List.of(new CampaignContentCatalog.Source(CAMPAIGN, "fixture", valid,
                    QuestLocaleCatalog.empty().overlay("en_us", locales), List.of())));
            helper.assertTrue(rejected.campaigns().isEmpty() && rejected.diagnostics().stream().anyMatch(issue -> issue.jsonPointer().equals("/locations/0/title_key")), "location text follows campaign localization ownership");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void locationFixturePlaysThroughItsOfferSceneVisitAndTurnIn(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.bindHome();
            var player = helper.makeMockServerPlayerInLevel();
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
            try {
                fixture.select(context, "visit", "offer");
                var progress = VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), CAMPAIGN);
                helper.assertTrue(progress != null && progress.targetPos().equals(fixture.origin), "normal offer stores the shared destination in personal quest guidance");
                var scenes = SceneSavedData.get(helper.getLevel());
                helper.assertValueEqual(scenes.active().size(), 1, "offer launches its authored greeting scene");
                BuiltinSceneStepExecutors.register();
                long started = helper.getLevel().getGameTime();
                for (int tick = 0; tick < 40; tick++) {
                    for (var scene : List.copyOf(scenes.active())) SceneStepEngine.process(fixture.server, scenes, scene, fixture.scene, started + tick);
                    SceneContinuationService.maintain(fixture.server, scenes);
                    player.tickCount = tick;
                    VillagerQuestService.onPlayerTick(player);
                }
                helper.assertValueEqual(progress.currentStage(), "report", "scene completion allows the visited location to advance the quest");
                fixture.select(context, "report", "turn_in");
                helper.assertValueEqual(progress.completionCount(), 1, "localized fixture conversation completes the personal visit");
                helper.assertValueEqual(CampaignLocationSavedData.get(helper.getLevel()).bindings().size(), 1, "the entire flow shares one binding");
            } finally {
                VillagerConversationService.endForPlayer(player, false);
                DialogueTreeService.clearRuntimeState();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_locations")
    public static void spawnOutsideTheWorldBorderDoesNotTriggerHeightmapChunkLoads(GameTestHelper helper) {
        var level = helper.getLevel();
        var border = level.getWorldBorder();
        double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
        int chunks = level.getChunkSource().getLoadedChunksCount();
        try {
            border.setCenter(20_000_000, 20_000_000);
            border.setSize(32);
            helper.assertFalse(border.isWithinBounds(level.getLevelData().getSpawnPos()), "test spawn is outside the border");
            var location = new CampaignLocationDefinition("spawn_area", "test.title", CampaignLocationDefinition.Source.STRUCTURE,
                    level.dimension().location(), null, 32, ResourceLocation.parse("minecraft:village_plains"), "");
            helper.assertTrue(CampaignLocationService.find(level, location) == null, "unavailable spawn area stays unresolved");
            helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), chunks, "raw spawn origin avoids heightmap lookup at border center");
            helper.assertTrue(level.getChunkSource().getChunkNow(20_000_000 >> 4, 20_000_000 >> 4) == null, "border center remains unloaded");
        } finally {
            border.setCenter(x, z);
            border.setSize(size);
        }
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }

    private static CampaignLocationBinding binding(UUID instance, String alias, BlockPos pos) {
        return new CampaignLocationBinding(CAMPAIGN, instance, 1, "fixture", alias, CampaignLocationDefinition.Source.VILLAGE,
                "village:" + pos.toShortString(), ResourceLocation.parse("minecraft:overworld"), pos, 1L);
    }

    private static JsonObject resource(String path) throws IOException {
        try (var stream = CampaignLocationGameTests.class.getResourceAsStream("/campaign_fixture/data/campaign_fixture/" + path)) {
            if (stream == null) throw new IOException("Missing campaign location fixture " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        }
    }

    private static final class Disk implements AutoCloseable {
        private final Path root = Files.createTempDirectory("campaign-locations-");
        private Disk() throws IOException { }
        private CampaignLocationSavedData open() { return CampaignLocationSavedData.load(new CompoundTag(), root); }
        private CampaignLocationSavedData reload() throws IOException {
            return CampaignLocationSavedData.load(NbtIo.readCompressed(root.resolve(CampaignLocationSavedData.DATA_NAME + ".dat"), NbtAccounter.unlimitedHeap()).getCompound("data"), root);
        }
        @Override public void close() throws IOException {
            Files.deleteIfExists(root.resolve(CampaignLocationSavedData.DATA_NAME + ".dat"));
            Files.deleteIfExists(root.resolve(CampaignSavedData.DATA_NAME + ".dat"));
            Files.delete(root);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final QuestContentCatalog prior;
        private final QuestContentLoadReport report;
        private final CampaignSavedData priorCampaigns;
        private final CampaignLocationSavedData priorLocations;
        private final CampaignWorldModeSavedData priorMode;
        private final VillageRegistrySavedData priorVillages;
        private final VillagerQuestSavedData priorQuests;
        private final SceneSavedData priorScenes;
        private final Disk disk = new Disk();
        private final QuestContentCatalog catalog;
        private final CampaignContentCatalog campaigns;
        private final CompiledScene scene;
        private final BlockPos origin;

        private Fixture(GameTestHelper helper) throws IOException {
            this.helper = helper;
            server = helper.getLevel().getServer();
            prior = QuestContentCatalogs.current(server);
            report = QuestContentCatalogs.loadReport(server);
            priorCampaigns = CampaignSavedData.get(helper.getLevel());
            priorLocations = CampaignLocationSavedData.get(helper.getLevel());
            priorMode = CampaignWorldModeSavedData.get(helper.getLevel());
            priorVillages = VillageRegistrySavedData.get(helper.getLevel());
            priorQuests = VillagerQuestSavedData.get(helper.getLevel());
            priorScenes = SceneSavedData.get(helper.getLevel());
            var resources = new ArrayList<QuestBundleTransactions.RawResource>();
            for (String file : List.of("quest.json", "locales/en_us.json", "scenes/location_greeting.json")) {
                String path = "quests/campaign_fixture/locations/" + file;
                resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture", id(path), resource(path)));
            }
            var bundles = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
            helper.assertTrue(bundles.diagnostics().isEmpty(), "location bundle compiles: " + bundles.diagnostics());
            var quests = VillagerQuestResources.snapshotForCatalog(server, bundles);
            scene = SceneCompiler.compile(SceneParser.parse(SCENE, resource("quests/campaign_fixture/locations/scenes/location_greeting.json")).resource()).scene();
            helper.assertTrue(scene != null, "fixture scene compiles");
            catalog = new QuestContentCatalog(prior.generation() + 1L, quests.compiledCatalog(), quests.dialogueCatalog(), quests.quests(),
                    quests.objectiveEventQuestIds(), quests.factQuestIds(), quests.memoryEventQuestIds(), quests.exclusiveGroupQuestIds(),
                    quests.triggerEventQuestIds(), Map.of(SCENE, scene), Map.of(), List.of(), bundles.bundles(), bundles.localization(), QuestRewardCatalog.fromBundles(bundles.bundles()));
            var root = resource("campaigns/locations/campaign.json");
            origin = helper.absolutePos(new BlockPos(1, 1, 1));
            var position = new JsonObject();
            position.addProperty("x", origin.getX());
            position.addProperty("y", origin.getY());
            position.addProperty("z", origin.getZ());
            root.getAsJsonArray("locations").get(0).getAsJsonObject().add("origin", position);
            var locales = QuestLocaleCatalog.read(resource("campaigns/locations/locales/en_us.json"));
            campaigns = CampaignContentCatalog.compile(catalog, List.of(new CampaignContentCatalog.Source(id("campaigns/locations/campaign.json"),
                    "campaign_fixture", root, QuestLocaleCatalog.empty().overlay("en_us", locales.messages()), List.of())));
            helper.assertTrue(campaigns.diagnostics().isEmpty(), "campaign location references compile: " + campaigns.diagnostics());
            QuestContentCatalogs.installForTests(server, catalog, report);
            CampaignResources.installForTests(server, campaigns);
            var storage = server.overworld().getDataStorage();
            storage.set(CampaignSavedData.DATA_NAME, CampaignSavedData.load(new CompoundTag(), disk.root));
            storage.set(CampaignLocationSavedData.DATA_NAME, disk.open());
            storage.set("villagerretaliation_village_registry", new VillageRegistrySavedData());
            storage.set("villagerretaliation_quests", new VillagerQuestSavedData());
            storage.set("villagerretaliation_scenes", new SceneSavedData());
            mode("campaign");
        }

        private void select(DialogueContext context, String stage, String slot) {
            VillagerConversationService.endForPlayer(context.player(), false);
            helper.assertTrue(VillagerConversationService.start(context.player(), context.villager()), "fixture conversation starts");
            var entry = catalog.dialogueCatalog().bindings(CAMPAIGN).stream()
                    .filter(value -> value.stageId().equals(stage) && value.slot().equals(slot)).findFirst().orElseThrow();
            helper.assertTrue(DialogueTreeService.startEntry(context, entry.treeId(), entry.entryId()).isPresent(), "authored dialogue opens");
            var options = DialogueTreeService.activeOptions(context).orElseThrow();
            VillagerDialogueRequestHandler.handle(context.player(), context.villager().getId(), options.getFirst().id());
        }

        private void install(JsonObject raw) {
            var parsed = CampaignDefinitionParser.parse(CAMPAIGN, raw);
            helper.assertTrue(parsed.valid(), "alternate location content compiles");
            CampaignResources.installForTests(server, new CampaignContentCatalog(catalog, Map.of(CAMPAIGN,
                    new CampaignContentCatalog.Entry(parsed.definition(), campaigns.campaigns().get(CAMPAIGN).localization())), List.of()));
        }

        private void mode(String mode) {
            var tag = new CompoundTag(); tag.putInt("DataVersion", 1); tag.putString("Mode", mode);
            server.overworld().getDataStorage().set(CampaignWorldModeSavedData.DATA_NAME, CampaignWorldModeSavedData.load(tag, helper.getLevel().registryAccess()));
        }

        private CampaignLocationService.Result resolve(String alias) { return CampaignLocationService.resolve(server, CAMPAIGN, alias); }

        private CampaignLocationBinding bindHome() throws IOException {
            VillageRegistrySavedData.get(helper.getLevel()).keyFor(helper.getLevel(), origin);
            var result = resolve("home");
            helper.assertTrue(result.available(), "known home resolves and saves: " + result.diagnostic());
            return result.binding();
        }

        private void reload() throws IOException {
            var storage = server.overworld().getDataStorage();
            var campaign = CampaignSavedData.get(helper.getLevel());
            storage.set(CampaignSavedData.DATA_NAME, CampaignSavedData.load(campaign.save(new CompoundTag(), helper.getLevel().registryAccess()), disk.root));
            var locations = CampaignLocationSavedData.get(helper.getLevel());
            storage.set(CampaignLocationSavedData.DATA_NAME, CampaignLocationSavedData.load(locations.save(new CompoundTag(), helper.getLevel().registryAccess()), disk.root));
        }

        @Override public void close() throws IOException {
            var storage = server.overworld().getDataStorage();
            storage.set(CampaignSavedData.DATA_NAME, priorCampaigns);
            storage.set(CampaignLocationSavedData.DATA_NAME, priorLocations);
            storage.set(CampaignWorldModeSavedData.DATA_NAME, priorMode);
            storage.set("villagerretaliation_village_registry", priorVillages);
            storage.set("villagerretaliation_quests", priorQuests);
            storage.set("villagerretaliation_scenes", priorScenes);
            priorQuests.setDirty();
            priorScenes.setDirty();
            QuestContentCatalogs.installForTests(server, prior, report);
            CampaignResources.clearCache();
            disk.close();
        }
    }
}
