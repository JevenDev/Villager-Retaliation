package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignLocationDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_repairs")
@PrefixGameTestTemplate(false)
public final class CampaignLocationRepairGameTests {
    private CampaignLocationRepairGameTests() { }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void repairsPersistOnceAndKeepAContinuousHistory(GameTestHelper helper) throws Exception {
        try (var disk = new Disk()) {
            var data = disk.locations();
            var original = binding(UUID.randomUUID(), new BlockPos(0, 70, 0), 1L);
            data.bind(original);
            var second = moved(original, new BlockPos(10, 70, 0), 2L);
            UUID operation = UUID.randomUUID(), operator = UUID.randomUUID();
            var receipt = data.repair(operation, CampaignLocationRepair.token(original), second, operator, "restore destination", 2L);
            var restored = disk.locations();
            helper.assertValueEqual(restored.bindings().get(original.key()), second, "new binding is durable before publication");
            helper.assertValueEqual(restored.repairs().get(operation), receipt, "operator audit survives reload");
            helper.assertTrue(restored.knows(original), "original anchor remains recognizable");
            helper.assertValueEqual(data.token(original.key()), restored.token(original.key()), "tokens survive NBT round-trip");
            var third = moved(original, new BlockPos(20, 70, 0), 3L);
            restored.repair(UUID.randomUUID(), restored.token(original.key()), third, operator, "move to restored marker", 3L);
            helper.assertValueEqual(restored.authorization(third, original.dimension(), original.position()).size(), 2, "old consumers can follow the repair chain");
            helper.assertValueEqual(restored.authorization(third, second.dimension(), second.position()).size(), 1, "partially reconciled consumers follow the remaining repair");
            helper.assertTrue(restored.authorization(third, original.dimension(), new BlockPos(99, 70, 0)).isEmpty(), "unrelated coordinates cannot claim authorization");
            helper.assertValueEqual(restored.repair(operation, CampaignLocationRepair.token(original), second, operator, "restore destination", 2L), receipt, "duplicate request returns its receipt");
            helper.assertValueEqual(restored.bindings().get(original.key()), third, "old retry cannot undo a later repair");
            rejected(helper, () -> restored.repair(UUID.randomUUID(), CampaignLocationRepair.token(original), second, operator, "stale", 2L));
            rejected(helper, () -> restored.repair(operation, CampaignLocationRepair.token(original), third, operator, "conflicting request", 3L));
            helper.assertValueEqual(restored.repairs().size(), 2, "rejections add no audits");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void returningToTheSameLocationInOneTickStillInvalidatesOldRequests(GameTestHelper helper) throws Exception {
        try (var disk = new Disk()) {
            var data = disk.locations();
            var original = binding(UUID.randomUUID(), new BlockPos(0, 70, 0), 1L);
            data.bind(original);
            UUID expected = data.token(original.key()), operator = UUID.randomUUID();
            var second = moved(original, new BlockPos(10, 70, 0), 1L);
            var first = data.repair(UUID.randomUUID(), expected, second, operator, "move destination", 1L);
            data.repair(UUID.randomUUID(), data.token(original.key()), original, operator, "restore destination", 1L);
            helper.assertValueEqual(data.bindings().get(original.key()), original, "binding fields returned to the same value within one tick");
            helper.assertFalse(data.token(original.key()).equals(expected), "repair history gives each change its own token");
            rejected(helper, () -> data.repair(UUID.randomUUID(), expected, second, operator, "stale request", 1L));
            helper.assertValueEqual(data.repair(first.operation(), first.expected(), second, operator, first.reason(), 1L), first, "original retry remains idempotent");
            helper.assertValueEqual(disk.locations().token(original.key()), data.token(original.key()), "current token survives reload");
            helper.assertValueEqual(data.repairs().size(), 2, "stale and duplicate requests add no audit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void competingRepairsCommitOneAuthoritativeBinding(GameTestHelper helper) throws Exception {
        try (var disk = new Disk(); var executor = Executors.newFixedThreadPool(2)) {
            var data = disk.locations();
            var original = binding(UUID.randomUUID(), new BlockPos(0, 70, 0), 1L);
            data.bind(original);
            var token = CampaignLocationRepair.token(original);
            var operator = UUID.randomUUID();
            var tasks = List.<Callable<Boolean>>of(
                    () -> attempt(data, token, moved(original, new BlockPos(10, 70, 0), 2L), operator),
                    () -> attempt(data, token, moved(original, new BlockPos(20, 70, 0), 2L), operator));
            var results = executor.invokeAll(tasks);
            helper.assertTrue(results.getFirst().get() != results.getLast().get(), "exactly one request wins");
            helper.assertValueEqual(data.repairs().size(), 1, "only the winning audit exists");
            helper.assertValueEqual(disk.locations().bindings(), data.bindings(), "both readers observe the persisted winner");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void migrationCorruptionAndFailedWritesPreserveEvidence(GameTestHelper helper) throws Exception {
        try (var disk = new Disk()) {
            var data = disk.locations();
            var original = binding(UUID.randomUUID(), new BlockPos(0, 70, 0), 1L);
            data.bind(original);
            var before = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var legacy = before.copy();
            legacy.putInt("DataVersion", 1);
            legacy.remove("Repairs");
            var migrated = CampaignLocationSavedData.load(legacy, disk.root);
            helper.assertTrue(migrated.diagnostic().isBlank() && migrated.repairs().isEmpty(), "v1 locations migrate without invented repairs");
            helper.assertValueEqual(migrated.bindings().get(original.key()), original, "migration keeps original binding");
            Path blocked = disk.root.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            var failing = CampaignLocationSavedData.load(before, blocked);
            boolean failed = false;
            try { failing.repair(UUID.randomUUID(), CampaignLocationRepair.token(original), moved(original, new BlockPos(10, 70, 0), 2L), UUID.randomUUID(), "repair", 2L); }
            catch (IOException expected) { failed = true; }
            helper.assertTrue(failed, "storage failure is surfaced");
            helper.assertValueEqual(failing.save(new CompoundTag(), helper.getLevel().registryAccess()), before, "failed write publishes no partial repair");
            var second = moved(original, new BlockPos(10, 70, 0), 2L);
            data.repair(UUID.randomUUID(), CampaignLocationRepair.token(original), second, UUID.randomUUID(), "repair", 2L);
            var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var mismatched = saved.copy();
            mismatched.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).putInt("X", 77);
            var missing = saved.copy(); missing.remove("Repairs");
            var future = saved.copy(); future.putInt("DataVersion", CampaignLocationSavedData.CURRENT_DATA_VERSION + 1);
            var forgedLegacy = saved.copy(); forgedLegacy.putInt("DataVersion", 1);
            var duplicate = saved.copy();
            duplicate.getList("Repairs", Tag.TAG_COMPOUND).add(duplicate.getList("Repairs", Tag.TAG_COMPOUND).getCompound(0).copy());
            var wrongToken = saved.copy();
            wrongToken.getList("Repairs", Tag.TAG_COMPOUND).getCompound(0).putUUID("Expected", UUID.randomUUID());
            for (var invalid : List.of(mismatched, missing, future, forgedLegacy, duplicate, wrongToken)) {
                var rejected = CampaignLocationSavedData.load(invalid, disk.root);
                helper.assertFalse(rejected.diagnostic().isBlank(), "invalid history blocks changes");
                helper.assertValueEqual(rejected.save(new CompoundTag(), helper.getLevel().registryAccess()), invalid, "unreadable history is preserved verbatim");
            }
            var foreign = binding(UUID.randomUUID(), new BlockPos(20, 70, 0), 3L);
            rejected(helper, () -> data.repair(UUID.randomUUID(), data.token(second.key()), foreign, UUID.randomUUID(), "wrong owner", 3L));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void activeJourneysRetargetWithoutInventingOrChangingParticipation(GameTestHelper helper) throws Exception {
        try (var disk = new Disk()) {
            var locations = disk.locations();
            var original = binding(UUID.randomUUID(), new BlockPos(10, 70, 0), 1L);
            locations.bind(original);
            var participant = new CampaignFollowJourney.Participant(UUID.randomUUID(), id("repair_quest"), UUID.randomUUID(), "follow");
            var start = new Vec3(0.5, 70.5, 0.5);
            var position = start.add(3, 0, 0);
            var trail = new CampaignFollowJourney.Trail(start, position, 3);
            var journey = new CampaignFollowJourney(new CampaignFollowJourney.Key(UUID.randomUUID(), "walk"), original.campaign(), original.instance(),
                    original.fingerprint(), id("repair_scene"), "scene-fingerprint", "mentor", "home", UUID.randomUUID(), original.dimension(),
                    original.position(), 8, 1, start, position, 3, 1L, 2L, CampaignFollowJourney.State.ACTIVE, Map.of(participant, trail));
            var journeys = disk.journeys();
            journeys.record(journey);
            var next = moved(original, new BlockPos(20, 70, 0), 3L);
            rejected(helper, () -> journeys.retarget(journey, next, locations));
            locations.repair(UUID.randomUUID(), CampaignLocationRepair.token(original), next, UUID.randomUUID(), "reroute", 3L);
            var before = journeys.save(new CompoundTag(), helper.getLevel().registryAccess());
            Path blocked = disk.root.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            var failing = CampaignFollowSavedData.load(before, blocked);
            boolean failed = false;
            try { failing.retarget(journey, next, locations); }
            catch (IOException expected) { failed = true; }
            helper.assertTrue(failed, "journey save failure is surfaced");
            helper.assertValueEqual(failing.save(new CompoundTag(), helper.getLevel().registryAccess()), before, "failed retarget publishes no partial observations");
            var changed = journeys.retarget(journey, next, locations);
            helper.assertValueEqual(changed.destination(), next.position(), "active destination follows authorized repair");
            helper.assertValueEqual(changed.participants(), journey.participants(), "enrollment and accompanied distance are unchanged");
            helper.assertValueEqual(changed.state(), CampaignFollowJourney.State.ACTIVE, "repair does not complete the journey");
            helper.assertValueEqual(disk.journeys().journeys().get(journey.key()), changed, "new destination survives reload");
            var arrived = changed.observed(Vec3.atCenterOf(next.position()), Map.of(participant, trail.observed(Vec3.atCenterOf(next.position()))), 4L, true);
            journeys.record(arrived);
            var later = moved(original, new BlockPos(30, 70, 0), 5L);
            locations.repair(UUID.randomUUID(), locations.token(next.key()), later, UUID.randomUUID(), "later repair", 5L);
            helper.assertValueEqual(journeys.retarget(arrived, later, locations), arrived, "earned arrival stays at its original destination");
            helper.assertValueEqual(disk.journeys().journeys().get(journey.key()), arrived, "completed evidence is never rewritten");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void activeFollowSceneResumesAtTheRepairedDestinationAfterStorageRecovery(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player);
            fixture.step();
            fixture.walk(1, player);
            var originalJourney = fixture.journey();
            String deadline = fixture.scene().stepRecords().get("walk").durableValues().get("deadline");
            var locations = CampaignLocationSavedData.get(helper.getLevel());
            var original = locations.bindings().get(new CampaignLocationBinding.Key(fixture.base.campaignId, originalJourney.location()));
            var target = fixture.destination.offset(1, 0, 0);
            var request = new CampaignLocationRepairs.Request(fixture.base.campaignId, original.alias(), original.dimension(), target,
                    CampaignLocationRepair.token(original), UUID.randomUUID(), player.getUUID(), "repair blocked route");
            var repaired = CampaignLocationRepairs.repair(fixture.base.server, request);
            helper.assertTrue(repaired.accepted(), repaired.diagnostic());
            fixture.block(fixture.journeyPath());
            try {
                fixture.step();
                helper.assertValueEqual(fixture.journey(), originalJourney, "failed retarget keeps the last durable observation");
                helper.assertTrue(fixture.mentor.getNavigation().isDone(), "guide waits while the repaired destination cannot be saved");
                helper.assertFalse(fixture.complete(player), "a location repair never grants arrival credit");
            } finally { fixture.restore(fixture.journeyPath()); }
            fixture.step();
            helper.assertValueEqual(fixture.journey().destination(), target, "runtime accepts the authorized destination");
            helper.assertValueEqual(fixture.journey().participants(), originalJourney.participants(), "retarget keeps the original followers and trails");
            fixture.scenes().persistCampaignHandoff(helper.getLevel());
            fixture.reload();
            helper.assertValueEqual(fixture.scene().stepRecords().get("walk").durableValues().get("deadline"), deadline, "repair does not reset the movement deadline");
            for (int x = 2; x <= 7; x++) fixture.walk(x, player);
            for (int tick = 0; tick < 5 && !fixture.scene().state().terminal(); tick++) fixture.step();
            helper.assertTrue(fixture.scene().state().terminal() && fixture.complete(player), "reloaded scene completes at its repaired destination: " + fixture.scene().diagnostic());
            var completed = fixture.journey();
            var current = locations.bindings().get(original.key());
            var later = new CampaignLocationRepairs.Request(fixture.base.campaignId, current.alias(), current.dimension(), fixture.destination,
                    locations.token(current.key()), UUID.randomUUID(), player.getUUID(), "restore original route");
            helper.assertTrue(CampaignLocationRepairs.repair(fixture.base.server, later).accepted(), "later repair succeeds");
            helper.assertValueEqual(CampaignSceneLocations.resolve(fixture.base.server, fixture.scenes(), fixture.scene(), fixture.base.scene), "", "scene accepts the later repair");
            helper.assertValueEqual(CampaignFollowService.prepare(fixture.context()).outcome(), com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.READY,
                    "completed step can acknowledge its saved historical arrival");
            helper.assertValueEqual(fixture.journey(), completed, "later repair cannot rewrite completed participation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void operatorRepairValidatesModePermissionAndLoadedDestinations(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting")) {
            var dispatcher = fixture.server.getCommands().getDispatcher();
            var source = fixture.server.createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2).withSuppressedOutput();
            String prefix = "vr admin campaign binding location campaign_fixture:characters home";
            helper.assertValueEqual(dispatcher.execute(prefix, source), 1, "inspection reports unbound token");
            helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().isEmpty(), "inspection does not start campaign");
            boolean denied = false;
            try { dispatcher.execute(prefix, source.withPermission(0)); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            helper.assertTrue(denied, "ordinary player cannot repair bindings");
            var original = CampaignLocationService.resolve(fixture.server, fixture.campaignId, "home").binding();
            var target = fixture.origin.offset(2, 0, 0);
            var block = helper.getLevel().getBlockState(target);
            var request = request(fixture, original, target, UUID.randomUUID());
            fixture.mode("sandbox");
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, request).accepted(), "Sandbox does not accept campaign repairs");
            fixture.mode("legacy");
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, request).accepted(), "legacy world mode is preserved");
            fixture.mode("campaign");
            var remote = request(fixture, original, new BlockPos(20_000_000, 70, 20_000_000), UUID.randomUUID());
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, remote).accepted(), "unloaded target is recoverable");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "repair does not load chunks");
            var wrongDimension = new CampaignLocationRepairs.Request(request.campaign(), request.alias(), ResourceLocation.parse("minecraft:the_nether"),
                    target, request.expected(), request.operation(), request.operator(), request.reason());
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, wrongDimension).accepted(), "authored dimension remains required");
            String command = prefix + " repair " + request.expected() + " " + request.operation() + " "
                    + target.getX() + " " + target.getY() + " " + target.getZ() + " " + request.reason();
            helper.assertValueEqual(dispatcher.execute(command, source), 1, "operator command repairs destination");
            var data = CampaignLocationSavedData.get(helper.getLevel());
            helper.assertValueEqual(data.bindings().get(original.key()).position(), target, "server stores selected position");
            helper.assertValueEqual(data.repairs().size(), 1, "command writes one durable audit");
            helper.assertValueEqual(dispatcher.execute(command, source), 1, "duplicate command returns saved result");
            helper.assertValueEqual(data.repairs().size(), 1, "retry adds no audit");
            helper.assertValueEqual(helper.getLevel().getBlockState(target), block, "repair preserves player construction");
            var saved = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.campaignId);
            helper.assertTrue(saved.players().isEmpty() && saved.outcomes().isEmpty(), "repair grants no progression or participation");
            helper.assertValueEqual(dispatcher.execute(prefix + " 999", source), 0, "invalid history page is rejected");
            var changed = fixture.raw(); changed.addProperty("revision", 2); fixture.install(changed);
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, request).accepted(), "incompatible content blocks retries");
            CampaignResources.installForTests(fixture.server, new com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog(
                    fixture.catalog, Map.of(), List.of()));
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, request).accepted(), "missing campaign content blocks repairs");
            helper.assertValueEqual(data.repairs().size(), 1, "missing content preserves the repair history");
            CampaignResources.installForTests(fixture.server, fixture.campaigns);
            var freshDirectory = fixture.disk.root.resolve("missing-campaign");
            fixture.server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, CampaignSavedData.load(new CompoundTag(), freshDirectory));
            helper.assertValueEqual(dispatcher.execute(command, source), 0, "duplicate repair cannot acknowledge a binding with missing campaign ownership");
            helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().isEmpty(), "rejected retry cannot silently recreate campaign ownership");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void manualVillageSelectionStillRequiresRegisteredIdentity(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting")) {
            var raw = fixture.raw();
            var home = raw.getAsJsonArray("locations").get(0).getAsJsonObject();
            home.addProperty("source", "village"); home.addProperty("search_radius", 32);
            fixture.install(raw);
            var request = request(fixture, null, fixture.origin, UUID.randomUUID());
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, request).accepted(), "arbitrary coordinates cannot invent a village");
            helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().isEmpty(), "invalid selection does not create campaign state");
            com.jvn.villagerretaliation.village.VillageRegistrySavedData.get(helper.getLevel()).keyFor(helper.getLevel(), fixture.origin);
            var result = CampaignLocationRepairs.repair(fixture.server, request);
            helper.assertTrue(result.accepted(), "operator can choose a registered destination: " + result.diagnostic());
            helper.assertTrue(result.binding().targetIdentity().startsWith("village:"), "village identity is retained");
            helper.assertValueEqual(result.binding().source(), CampaignLocationDefinition.Source.VILLAGE, "repair preserves authored source policy");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void structureRepairsRequireTheAuthoredMarkerInsideARegisteredStart(GameTestHelper helper) throws Exception {
        try (var world = new CampaignStructureMarkerGameTests.StructureFixture(helper);
                var fixture = new CampaignCharacterTestFixture(helper, "structures", "structure_greeting")) {
            var raw = fixture.raw();
            var home = raw.getAsJsonArray("locations").get(0).getAsJsonObject();
            home.addProperty("source", "structure");
            home.addProperty("structure", "minecraft:desert_pyramid");
            home.addProperty("structure_marker", "campaign_fixture:waystation_entry");
            home.addProperty("search_radius", 64);
            fixture.install(raw);
            var target = world.origin.offset(2, 1, 2);
            var request = request(fixture, null, target, UUID.randomUUID());
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, request).accepted(), "structure interior is not an authored marker");
            var wrong = world.marker(target, "campaign_fixture:other");
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, request).accepted(), "unrelated marker cannot replace the destination");
            wrong.discard();
            var marker = world.marker(target, "campaign_fixture:waystation_entry");
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            var repaired = CampaignLocationRepairs.repair(fixture.server, request);
            helper.assertTrue(repaired.accepted(), repaired.diagnostic());
            helper.assertTrue(repaired.binding().targetIdentity().endsWith("/marker:" + marker.getUUID()), "repair records the exact registered marker");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "repair searches loaded structures only");
            marker.discard();
            helper.assertValueEqual(CampaignLocationRepairs.repair(fixture.server, request).binding(), repaired.binding(), "acknowledgement survives marker unloading");
            var outside = world.origin.offset(-1, 1, -1);
            world.marker(outside, "campaign_fixture:waystation_entry");
            helper.assertFalse(CampaignLocationRepairs.repair(fixture.server, request(fixture, repaired.binding(), outside, UUID.randomUUID())).accepted(),
                    "tagged marker outside the structure cannot replace its destination");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void scenesRebindOnlyFromSavedAuthorizationAndKeepTheirStepState(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting")) {
            var original = CampaignLocationService.resolve(fixture.server, fixture.campaignId, "home").binding();
            var scenes = SceneSavedData.get(helper.getLevel());
            UUID player = UUID.randomUUID();
            var scene = scenes.start(fixture.scene, "repair-test", new SceneOwner(SceneResource.OwnershipMode.PLAYER, player, null, null, ""),
                    UUID.randomUUID(), Set.of(player), Map.of(), 1L, fixture.campaignId).instance();
            scene.bindCampaign(fixture.campaignId);
            helper.assertValueEqual(CampaignSceneLocations.resolve(fixture.server, scenes, scene, fixture.scene), "", "initial scene uses canonical destination");
            String step = scene.currentStep();
            var repaired = CampaignLocationRepairs.repair(fixture.server, request(fixture, original, fixture.origin.offset(2, 0, 0), UUID.randomUUID()));
            helper.assertTrue(repaired.accepted(), repaired.diagnostic());
            var before = scene.actorBindings().get("home");
            var future = scenes.save(new CompoundTag(), helper.getLevel().registryAccess());
            future.putInt("DataVersion", SceneSavedData.CURRENT_DATA_VERSION + 1);
            var failing = SceneSavedData.load(future, helper.getLevel().registryAccess());
            helper.assertFalse(CampaignSceneLocations.resolve(fixture.server, failing, scene, fixture.scene).isBlank(), "failed scene save leaves a recoverable diagnostic");
            helper.assertValueEqual(scene.actorBindings().get("home"), before, "failed scene write restores the prior binding");
            helper.assertValueEqual(CampaignSceneLocations.resolve(fixture.server, scenes, scene, fixture.scene), "", "saved repair reconciles scene");
            var binding = scene.actorBindings().get("home");
            helper.assertValueEqual(binding.lastPosition(), repaired.binding().position(), "scene uses repaired destination");
            helper.assertValueEqual(binding.replacementHistory().size(), 1, "scene records repair operation");
            helper.assertValueEqual(scene.currentStep(), step, "repair does not restart or advance scene");
            helper.assertTrue(scene.receipts().isEmpty(), "repair creates no effect receipt");
            helper.assertValueEqual(CampaignSceneLocations.resolve(fixture.server, scenes, scene, fixture.scene), "", "reconciliation is idempotent");
            helper.assertValueEqual(scene.actorBindings().get("home"), binding, "retry leaves generation and audit unchanged");
            var restored = SceneSavedData.load(scenes.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertValueEqual(restored.get(scene.id()).orElseThrow().actorBindings().get("home"), binding, "scene repair survives reload");
            scene.replaceBinding("home", binding.withObservation(binding.lastDimension(), fixture.origin.offset(5, 0, 0), false));
            helper.assertFalse(CampaignSceneLocations.resolve(fixture.server, scenes, scene, fixture.scene).isBlank(), "unrecorded relocation cannot borrow authorization");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void ordinaryMovementRepairsKeepTheirDeadlineAndRequireDurableAuthorization(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting", List.of(), quest -> { }, campaign -> { },
                List.of(), scene -> scene.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("type", "villagerretaliation:move_actor"))) {
            var original = CampaignLocationService.resolve(fixture.server, fixture.campaignId, "home").binding();
            var scenes = SceneSavedData.get(helper.getLevel());
            UUID player = UUID.randomUUID();
            var scene = scenes.start(fixture.scene, "movement-repair", new SceneOwner(SceneResource.OwnershipMode.PLAYER, player, null, null, ""),
                    UUID.randomUUID(), Set.of(player), Map.of(), 1L, fixture.campaignId).instance();
            scene.bindCampaign(fixture.campaignId);
            var step = fixture.scene.steps().get(scene.currentStep());
            var record = scene.currentRecord(step.type());
            record.putDurableValue("destination_dimension", original.dimension().toString());
            record.putDurableValue("destination_x", Integer.toString(original.position().getX()));
            record.putDurableValue("destination_y", Integer.toString(original.position().getY()));
            record.putDurableValue("destination_z", Integer.toString(original.position().getZ()));
            record.putDurableValue("deadline", "1000");
            var before = record.durableValues();
            var repaired = CampaignLocationRepairs.repair(fixture.server, request(fixture, original, fixture.origin.offset(2, 0, 0), UUID.randomUUID()));
            helper.assertTrue(repaired.accepted(), repaired.diagnostic());
            var future = scenes.save(new CompoundTag(), helper.getLevel().registryAccess());
            future.putInt("DataVersion", SceneSavedData.CURRENT_DATA_VERSION + 1);
            var failing = SceneSavedData.load(future, helper.getLevel().registryAccess());
            var blocked = new com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext(fixture.server, failing, scene, fixture.scene, step, record, 2L, true);
            helper.assertValueEqual(CampaignMovementRepairs.reconcile(blocked).outcome(),
                    com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.WAIT, "movement waits for durable repair");
            helper.assertValueEqual(record.durableValues(), before, "failed save restores every prepared movement field");
            var context = new com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext(fixture.server, scenes, scene, fixture.scene, step, record, 3L, true);
            helper.assertValueEqual(CampaignMovementRepairs.reconcile(context).outcome(),
                    com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.READY, "ordinary movement accepts the saved repair");
            helper.assertValueEqual(record.durableValues().get("destination_x"), Integer.toString(repaired.binding().position().getX()), "movement follows the repaired location");
            helper.assertValueEqual(record.durableValues().get("deadline"), "1000", "repair keeps the prepared deadline");
            record.putDurableValue("destination_x", "99");
            helper.assertValueEqual(CampaignMovementRepairs.reconcile(context).outcome(),
                    com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.BLOCK, "unrelated movement coordinates cannot claim repair authorization");
            helper.assertValueEqual(record.durableValues().get("destination_x"), "99", "invalid movement data is preserved for diagnosis");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void offlineQuestTargetsRefreshWithoutResettingEarnedProgress(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting")) {
            var player = helper.makeMockServerPlayerInLevel();
            fixture.server.getPlayerList().remove(player);
            helper.assertTrue(fixture.server.getPlayerList().getPlayer(player.getUUID()) == null, "fixture player is disconnected during repair");
            var original = CampaignLocationService.resolve(fixture.server, fixture.campaignId, "home").binding();
            var quests = VillagerQuestSavedData.get(helper.getLevel());
            var progress = quests.getOrCreate(player.getUUID(), fixture.campaignId);
            UUID provider = UUID.randomUUID();
            progress.start(provider, helper.getLevel().dimension(), original.position(), 1L);
            progress.setCurrentStage("visit");
            var quest = fixture.catalog.quest(fixture.campaignId).orElseThrow();
            String objective = quest.objectives().getFirst().id();
            progress.setTarget(provider, helper.getLevel().dimension(), original.position(), objective);
            progress.markObjectiveComplete(objective);
            progress.markVisitedTarget();
            var repaired = CampaignLocationRepairs.repair(fixture.server, request(fixture, original, fixture.origin.offset(2, 0, 0), UUID.randomUUID()));
            helper.assertTrue(repaired.accepted(), repaired.diagnostic());
            helper.assertValueEqual(progress.targetPos(), original.position(), "offline snapshot remains pending until reconciliation");
            helper.assertTrue(CampaignQuestLocations.reconcile(player, quest, progress), "returning player refreshes target");
            helper.assertValueEqual(progress.targetPos(), repaired.binding().position(), "personal marker follows saved destination");
            helper.assertTrue(progress.objectiveComplete(objective) && progress.visitedTarget(), "earned visit credit is unchanged");
            helper.assertValueEqual(progress.startedVillagerId(), provider, "location repair never substitutes the quest provider");
            helper.assertFalse(CampaignQuestLocations.reconcile(player, quest, progress), "reconciliation does not repeat writes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_repairs")
    public static void plannedTerrainEffectsFinishAtTheirOriginalAnchorAfterRepair(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            fixture.commit("gate", "sealed", UUID.randomUUID());
            helper.assertValueEqual(fixture.reconcile(), "", "fixture completes its terrain effect");
            var originalReceipt = fixture.receipt("sealed");
            var saved = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            saved.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putInt("Cursor", 0);
            fixture.base.server.overworld().getDataStorage().set(CampaignVariantSavedData.DATA_NAME,
                    CampaignVariantSavedData.load(saved, fixture.base.disk.root));
            var original = CampaignLocationSavedData.get(helper.getLevel()).bindings().get(new CampaignLocationBinding.Key(fixture.base.campaignId, "home"));
            var target = fixture.base.origin.offset(4, 0, 0);
            var terrain = helper.getLevel().getBlockState(target.below());
            var repaired = CampaignLocationRepairs.repair(fixture.base.server, request(fixture.base, original, target, UUID.randomUUID()));
            helper.assertTrue(repaired.accepted(), repaired.diagnostic());
            helper.assertValueEqual(fixture.reconcile(), "", "interrupted acknowledgement resumes at the original cells");
            var receipt = fixture.receipt("sealed");
            helper.assertValueEqual(receipt.binding(), originalReceipt.binding(), "planned anchor is immutable");
            helper.assertValueEqual(receipt.cells(), originalReceipt.cells(), "cell operations are not recreated");
            helper.assertTrue(receipt.complete(), "old plan completes once");
            helper.assertValueEqual(helper.getLevel().getBlockState(target.below()), terrain, "new location receives no duplicate world effect");
        }
        helper.succeed();
    }

    private static CampaignLocationRepairs.Request request(CampaignCharacterTestFixture fixture, CampaignLocationBinding before, BlockPos position, UUID operation) {
        return new CampaignLocationRepairs.Request(fixture.campaignId, "home", fixture.helper.getLevel().dimension().location(), position,
                CampaignLocationSavedData.get(fixture.helper.getLevel()).token(before == null
                        ? new CampaignLocationBinding.Key(fixture.campaignId, "home") : before.key()), operation, new UUID(1, 2), "restore destination");
    }

    private static boolean attempt(CampaignLocationSavedData data, UUID token, CampaignLocationBinding binding, UUID operator) throws IOException {
        try { data.repair(UUID.randomUUID(), token, binding, operator, "repair", binding.boundAt()); return true; }
        catch (IllegalArgumentException expected) { return false; }
    }

    private static CampaignLocationBinding binding(UUID instance, BlockPos position, long time) {
        return new CampaignLocationBinding(id("repair"), instance, 1, "fingerprint", "home", CampaignLocationDefinition.Source.MARKER,
                "marker:home", ResourceLocation.parse("minecraft:overworld"), position, time);
    }

    private static CampaignLocationBinding moved(CampaignLocationBinding prior, BlockPos position, long time) {
        return new CampaignLocationBinding(prior.campaign(), prior.instance(), prior.definitionRevision(), prior.fingerprint(), prior.alias(),
                prior.source(), prior.targetIdentity(), prior.dimension(), position, time);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }

    private static void rejected(GameTestHelper helper, Checked action) throws Exception {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "invalid repair must be rejected");
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }

    private static final class Disk implements AutoCloseable {
        final Path root = Files.createTempDirectory("campaign-location-repairs-");
        Disk() throws IOException { }
        CompoundTag read(String name) throws IOException {
            var file = root.resolve(name + ".dat");
            return Files.exists(file) ? NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data") : new CompoundTag();
        }
        CampaignLocationSavedData locations() throws IOException { return CampaignLocationSavedData.load(read(CampaignLocationSavedData.DATA_NAME), root); }
        CampaignFollowSavedData journeys() throws IOException { return CampaignFollowSavedData.load(read(CampaignFollowSavedData.DATA_NAME), root); }
        @Override public void close() throws IOException {
            try (var files = Files.list(root)) { for (var file : files.toList()) Files.delete(file); }
            Files.delete(root);
        }
    }
}
