package com.jvn.villagerretaliation.quest.site;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.quest.*;
import com.jvn.villagerretaliation.quest.follower.QuestFollowerSavedData;
import com.jvn.villagerretaliation.quest.follower.QuestFollowerService;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.site.QuestSiteSavedData.Phase;
import com.jvn.villagerretaliation.quest.site.QuestSiteSavedData.Site;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_sites")
@PrefixGameTestTemplate(false)
public final class QuestSiteGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:rescue");
    private static final ResourceLocation TEMPLATE = ResourceLocation.parse("villagerretaliation:quest/holding_camp");
    private QuestSiteGameTests() { }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void retiredSitesRemainSavedButLeaveTheTickQueue(GameTestHelper helper) {
        var data = new QuestSiteSavedData();
        var dimension = helper.getLevel().dimension().location();
        for (int index = 0; index < 128; index++) {
            var site = new Site(UUID.randomUUID(), UUID.randomUUID(), QUEST, null, dimension, BlockPos.ZERO);
            site.position = new BlockPos(index * 32, 64, 0);
            data.add(site);
            data.retire(site);
        }
        var active = new Site(UUID.randomUUID(), UUID.randomUUID(), QUEST, null, dimension, BlockPos.ZERO);
        active.phase = Phase.ACTIVE;
        data.add(active);
        helper.assertValueEqual(data.sites.size(), 129, "historical placement records remain available");
        helper.assertValueEqual(data.tickingSites(), List.of(active), "only unfinished sites need regular ticks");
        var restored = QuestSiteSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.sites.size(), 129, "all historical sites survive reload");
        helper.assertValueEqual(restored.tickingSites().size(), 1, "reload rebuilds only the live tick queue");
        var restoredActive = restored.sites.get(active.id);
        restored.retire(restoredActive);
        helper.assertTrue(restored.tickingSites().isEmpty(), "retirement immediately removes the live site");
        helper.assertTrue(restored.sites.containsKey(active.id), "retirement preserves placement history");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void allAuthoringPoliciesAndInvalidInputs(GameTestHelper helper) {
        for (String selection : List.of("existing_only", "prefer_existing", "new_site"))
            for (String terrain : List.of("new_terrain_only", "generated_wilderness"))
                for (String style : List.of("exact", "search_circle"))
                    for (String rescue : List.of("free_at_site", "escort_home")) {
                        var root = config(rescue);
                        root.addProperty("selection", selection);
                        root.addProperty("terrain", terrain);
                        root.addProperty("map_style", style);
                        var parsed = QuestSiteDefinition.read(root);
                        helper.assertValueEqual(parsed.rescue().name().toLowerCase(java.util.Locale.ROOT), rescue, "rescue policy survives parsing");
                    }
        for (String field : List.of("selection", "terrain", "map_style", "rescue")) {
            var root = config("escort_home");
            root.addProperty(field, "unknown");
            expectInvalid(helper, root);
        }
        var missing = config("escort_home");
        missing.remove("templates");
        expectInvalid(helper, missing);
        var reversed = config("escort_home");
        reversed.addProperty("min_distance", 4096);
        expectInvalid(helper, reversed);
        var extra = config("escort_home");
        extra.addProperty("overwrite_player_blocks", true);
        expectInvalid(helper, extra);
        var scalar = JsonParser.parseString("{\"type\":[\"string\",\"number\",\"boolean\"]}").getAsJsonObject();
        helper.assertTrue(com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(
                new com.google.gson.JsonPrimitive("test:rescue"), scalar).isEmpty(), "criterion scalar unions accept strings");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void persistenceRetainsOwnershipAndNeverReplaysInterruptedPlacement(GameTestHelper helper) {
        var data = new QuestSiteSavedData();
        var site = new Site(UUID.randomUUID(), UUID.randomUUID(), QUEST, UUID.randomUUID(),
                helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(48, 3, 48)));
        site.position = site.home.offset(256, 0, 0);
        site.template = TEMPLATE;
        site.captive = UUID.randomUUID();
        site.run = UUID.randomUUID();
        site.guards.add(UUID.randomUUID());
        site.defeated.add(site.guards.getFirst());
        site.mapRecipients.add(site.player);
        site.phase = Phase.ACTIVE;
        site.released = true;
        data.add(site);
        var reloaded = QuestSiteSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        var copy = reloaded.sites.get(site.id);
        helper.assertValueEqual(copy.captive, site.captive, "captive UUID is stable");
        helper.assertValueEqual(copy.run, site.run, "shared run identity is stable");
        helper.assertValueEqual(copy.defeated, site.defeated, "guard deaths survive reload");
        helper.assertTrue(copy.released && copy.mapRecipients.contains(site.player), "release and map receipts survive reload");
        for (var phase : List.of(Phase.PLACING, Phase.SPAWNING)) {
            site.phase = phase;
            copy = QuestSiteSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess()).sites.get(site.id);
            helper.assertValueEqual(copy.phase, Phase.UNAVAILABLE, "interrupted world mutations are never replayed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void searchCircleHidesExactCoordinatesAndRoundTripsPacket(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "escort_home")) {
            fixture.site.discovered = false;
            var values = new LinkedHashMap<String, String>();
            values.put("target_x", Integer.toString(fixture.site.position.getX()));
            values.put("objective_target_x", values.get("target_x"));
            QuestSiteService.sanitize(values, fixture.player, fixture.quest);
            helper.assertValueEqual(values.get("target_y"), "unknown", "height is hidden");
            helper.assertValueEqual(values.get("distance"), "unknown", "exact target distance is hidden");
            var center = QuestSiteService.displayPosition(helper.getLevel(), fixture.player.getUUID(), fixture.quest, fixture.site.position);
            helper.assertTrue(center.distSqr(new BlockPos(fixture.site.position.getX(), 0, fixture.site.position.getZ())) < 96 * 96,
                    "search circle contains the encounter");
            var waypoint = new QuestTrackerSyncPayload.Waypoint(helper.getLevel().dimension().location().toString(),
                    center.getX(), 0, center.getZ(), 96);
            var journal = QuestTrackerSyncPayload.Journal.EMPTY.withRuntime(-1, -1, waypoint);
            var entry = new QuestTrackerSyncPayload.Entry(QUEST.toString(), "Rescue", "Search", "", "", 0, false, "active",
                    "", "", "", List.of(), List.of(), List.of(), List.of(), false, false, journal);
            var payload = new QuestTrackerSyncPayload(List.of(entry), QUEST.toString(), false);
            var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                QuestTrackerSyncPayload.STREAM_CODEC.encode(buffer, payload);
                var decoded = QuestTrackerSyncPayload.STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(decoded.entries().getFirst().journal().waypoint(), waypoint, "map style survives the wire format");
            } finally { buffer.release(); }
            fixture.site.discovered = true;
            helper.assertValueEqual(QuestSiteService.displayRadius(helper.getLevel(), fixture.player.getUUID(), fixture.quest), 0,
                    "discovery reveals an exact destination");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void mapsAreBoundReplaceableAndNotDuplicated(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "escort_home")) {
            fixture.site.discovered = false;
            var otherDimension = new Site(UUID.randomUUID(), fixture.player.getUUID(), QUEST, null,
                    net.minecraft.world.level.Level.NETHER.location(), fixture.site.home);
            otherDimension.position = fixture.site.position;
            QuestSiteMaps.give(fixture.player, fixture.quest, otherDimension, false);
            helper.assertTrue(fixture.player.getInventory().isEmpty(), "map delivery waits for the destination dimension");
            QuestSiteMaps.give(fixture.player, fixture.quest, fixture.site, false);
            QuestSiteMaps.give(fixture.player, fixture.quest, fixture.site, false);
            var maps = fixture.player.getInventory().items.stream().filter(stack -> stack.is(Items.FILLED_MAP)).toList();
            helper.assertValueEqual(maps.size(), 1, "initial delivery has one map");
            var map = maps.getFirst();
            var data = MapItem.getSavedData(map, helper.getLevel());
            helper.assertTrue(data != null && data.locked, "search circle survives vanilla map terrain updates");
            helper.assertTrue(map.get(DataComponents.CUSTOM_DATA).copyTag().getUUID("QuestSite").equals(fixture.site.id),
                    "physical map is bound to its saved site");
            int red = Byte.toUnsignedInt(net.minecraft.world.level.material.MapColor.COLOR_RED.getPackedId(
                    net.minecraft.world.level.material.MapColor.Brightness.NORMAL));
            int pixels = 0;
            for (byte color : data.colors) if (Byte.toUnsignedInt(color) == red) pixels++;
            helper.assertTrue(pixels > 100 && pixels < 400, "physical map contains a bounded search ring");
            fixture.player.getInventory().clearContent();
            QuestSiteMaps.give(fixture.player, fixture.quest, fixture.site, false);
            helper.assertTrue(fixture.player.getInventory().isEmpty(), "saved delivery receipt prevents automatic duplicate maps");
            QuestSiteMaps.give(fixture.player, fixture.quest, fixture.site, true);
            helper.assertValueEqual(fixture.player.getInventory().items.stream().filter(stack -> stack.is(Items.FILLED_MAP)).count(), 1L,
                    "lost map replacement does not prepare another site");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void guardDeathsReleaseAndEscortCompleteTheQuestObjective(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "escort_home")) {
            var captive = fixture.captive();
            fixture.site.guards.add(UUID.randomUUID());
            fixture.interact(captive);
            helper.assertFalse(fixture.site.released, "living or unloaded guards cannot count as defeated");
            fixture.site.defeated.addAll(fixture.site.guards);
            fixture.interact(captive);
            helper.assertTrue(fixture.site.released && !captive.isNoAi(), "freeing the captive restores its AI");
            helper.assertTrue(QuestFollowerService.retained(captive), "escort uses the existing follower runtime");
            helper.assertFalse(fixture.site.rescued, "release alone does not satisfy an escort");
            captive.moveTo(fixture.site.home.getCenter());
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertTrue(fixture.site.rescued, "arrival at the saved home completes the rescue");
            var memory = new com.jvn.villagerretaliation.dialogue.DialogueCondition.Memory(
                    java.util.Set.of(ResourceLocation.parse("villagerretaliation:player_rescued_me")),
                    com.jvn.villagerretaliation.dialogue.DialogueCondition.MemorySource.THIS_VILLAGER, true,
                    com.jvn.villagerretaliation.dialogue.DialogueCondition.MemoryKind.EVENT_TAG);
            helper.assertTrue(memory.matches(com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(
                    helper.getLevel(), fixture.player, captive)), "rescued villager remembers the player who brought them home");
            helper.assertFalse(QuestFollowerService.retained(captive), "rescued villagers stop following");
            helper.assertValueEqual(fixture.progress.currentStage(), "return", "rescue event advances the quest to turn-in");
            fixture.interact(captive);
            helper.assertTrue(fixture.site.rescued, "repeat interactions cannot reset the rescue");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void releaseOnlyAndDeathHaveDistinctOutcomes(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "free_at_site")) {
            var captive = fixture.captive();
            fixture.interact(captive);
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertTrue(fixture.site.rescued, "release-only quests do not require an escort");
            helper.assertFalse(QuestFollowerService.retained(captive), "release-only quests never acquire a follower");
        }
        try (var fixture = new Fixture(helper, "escort_home")) {
            var captive = fixture.captive();
            QuestSiteService.died(captive);
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertTrue(fixture.progress.state() != VillagerQuestSavedData.QuestState.ACTIVE, "captive death fails the run");
            helper.assertValueEqual(fixture.site.captive, captive.getUUID(), "dead actors are not replaced");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void peacefulGuardRemovalAllowsRescueAfterDifficultyChanges(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var difficulty = server.getWorldData().getDifficulty();
        try (var fixture = new Fixture(helper, "free_at_site")) {
            var captive = fixture.captive();
            var guard = fixture.guard();
            fixture.interact(captive);
            helper.assertFalse(fixture.site.released, "living guards block release");
            fixture.site.phase = Phase.READY;
            fixture.data.setDirty(false);
            server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            guard.checkDespawn();
            helper.assertTrue(guard.isRemoved(), "persistent pillagers are discarded on Peaceful");
            helper.assertTrue(fixture.site.defeated.contains(guard.getUUID()), "Peaceful removal clears prepared guards");
            helper.assertTrue(fixture.data.isDirty(), "guard removal is persisted");
            var reloaded = QuestSiteSavedData.load(fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess());
            helper.assertTrue(reloaded.sites.get(fixture.site.id).defeated.contains(guard.getUUID()), "cleared guards survive reload");
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            fixture.site.phase = Phase.ACTIVE;
            fixture.interact(captive);
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertTrue(fixture.site.rescued, "removed guards no longer block rescue after leaving Peaceful");
            helper.succeed();
        } finally { server.setDifficulty(difficulty, true); }
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void guardUnloadsAndNonPeacefulDiscardsDoNotCountAsDefeats(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var difficulty = server.getWorldData().getDifficulty();
        try {
            for (var reason : List.of(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK,
                    net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION,
                    net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)) {
                try (var fixture = new Fixture(helper, "free_at_site")) {
                    var captive = fixture.captive();
                    var guard = fixture.guard();
                    server.setDifficulty(reason == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED
                            ? net.minecraft.world.Difficulty.NORMAL : net.minecraft.world.Difficulty.PEACEFUL, true);
                    guard.remove(reason);
                    helper.assertTrue(fixture.site.defeated.isEmpty(), "non-defeat removal keeps guards pending: " + reason);
                    fixture.interact(captive);
                    helper.assertFalse(fixture.site.released, "pending guards still block release: " + reason);
                }
            }
            helper.succeed();
        } finally { server.setDifficulty(difficulty, true); }
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void rescuedVillagerLossPreservesTurnIn(GameTestHelper helper) throws Exception {
        for (String rescue : List.of("free_at_site", "escort_home")) {
            try (var fixture = new Fixture(helper, rescue)) {
                var captive = fixture.captive();
                fixture.interact(captive);
                captive.moveTo(fixture.site.home.getCenter());
                QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
                helper.assertTrue(fixture.site.rescued, "rescue is completed: " + rescue);
                helper.assertValueEqual(fixture.progress.currentStage(), "return", "quest is ready to turn in");
                QuestSiteService.died(captive);
                captive.discard();
                helper.assertFalse(fixture.site.dead, "rescued villager death does not fail the encounter");
                helper.assertTrue(helper.getLevel().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(fixture.site.actorPosition).toLong()),
                        "last known actor chunk is loaded");
                fixture.site.activatedAt = helper.getLevel().getGameTime() - 201;
                QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
                helper.assertValueEqual(fixture.progress.state(), VillagerQuestSavedData.QuestState.ACTIVE, "completed rescue survives actor loss");
                var result = VillagerQuestService.performAction(DialogueContext.forPlayer(fixture.player), QUEST,
                        com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
                helper.assertValueEqual(result.status(), "completed", "rescued villager death does not prevent turn-in");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void missingCaptiveStillFailsBeforeRescue(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "escort_home")) {
            var captive = fixture.captive();
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
            captive.discard();
            helper.assertTrue(helper.getLevel().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(fixture.site.actorPosition).toLong()),
                    "last known actor chunk is loaded");
            fixture.site.activatedAt = helper.getLevel().getGameTime() - 201;
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertValueEqual(fixture.progress.state(), VillagerQuestSavedData.QuestState.FAILED, "missing captive fails an unfinished rescue");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_site_queue", timeoutTicks = 100)
    public static void readyOfflineSiteDoesNotStarveLaterRequests(GameTestHelper helper) {
        var fixture = new Fixture(helper, "escort_home");
        fixture.data.retire(fixture.site);
        var waiting = new Site(UUID.randomUUID(), UUID.randomUUID(), QUEST, null,
                fixture.site.dimension, fixture.site.home);
        waiting.config = fixture.site.config;
        waiting.phase = Phase.READY;
        waiting.accepted = true;
        waiting.position = fixture.site.position;
        var later = new Site(UUID.randomUUID(), UUID.randomUUID(), QUEST, null,
                fixture.site.dimension, fixture.site.home.offset(5000, 0, 0));
        later.config = fixture.site.config;
        later.accepted = true;
        fixture.data.add(waiting);
        fixture.data.add(later);
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            try {
                helper.assertValueEqual(waiting.phase, Phase.READY, "offline site remains ready for its owner");
                helper.assertTrue(later.attempt > 0, "later site receives search attempts while the ready site waits");
            } finally {
                try { fixture.close(); } catch (Exception exception) { throw new IllegalStateException(exception); }
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void anotherPlayerCannotReleaseOrClaimTheCaptive(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "escort_home")) {
            var captive = fixture.captive();
            var outsider = helper.makeMockServerPlayerInLevel();
            try {
                var event = new PlayerInteractEvent.EntityInteract(outsider, InteractionHand.MAIN_HAND, captive);
                QuestSiteService.interact(event);
                helper.assertTrue(event.isCanceled() && !fixture.site.released, "unrelated players cannot take the captive");
                var shared = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(outsider.getUUID(), QUEST);
                shared.start(null, helper.getLevel().dimension(), fixture.site.position, 0);
                shared.beginRun(outsider.getUUID(), QUEST, fixture.site.run);
                QuestSiteService.interact(event);
                helper.assertTrue(fixture.site.released, "members of the same saved run can release the captive");
                shared.fail(helper.getLevel().getGameTime(), "left_party");
                QuestFollowerService.tickMob(captive);
                helper.assertFalse(QuestFollowerService.retained(captive), "departing leader releases its follower binding");
                fixture.interact(captive);
                helper.assertTrue(QuestFollowerService.retained(captive), "remaining quest members can resume the same escort");
            } finally { outsider.discard(); }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void knownTerrainAndObstructionsNeverTriggerPlacement(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "escort_home")) {
            fixture.site.template = TEMPLATE;
            fixture.site.phase = Phase.CANDIDATE;
            helper.assertFalse(QuestSitePlanner.candidate(helper.getLevel(), fixture.site, fixture.quest.target().site()),
                    "new-terrain-only refuses already loaded terrain");
            helper.assertValueEqual(fixture.site.phase, Phase.SEARCHING, "unsafe candidate is rejected");
            var pos = helper.absolutePos(new BlockPos(48, 3, 48));
            fixture.site.position = pos;
            helper.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            helper.assertFalse(QuestSitePlanner.clear(helper.getLevel(), pos, 0, 3, true), "storage cannot be overwritten");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CHEST), "inspection leaves player blocks unchanged");
            fixture.site.attempt = fixture.quest.target().site().attempts();
            helper.assertFalse(QuestSitePlanner.next(helper.getLevel(), fixture.site, fixture.quest.target().site()),
                    "search stops at the authored attempt limit");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void holdingCampTemplateAndBuiltInQuestAreAvailable(GameTestHelper helper) {
        var quest = VillagerQuestResources.quest(helper.getLevel().getServer(),
                ResourceLocation.parse("villagerretaliation:missing_neighbor")).orElseThrow(() -> new IllegalStateException("Built-in rescue quest is missing: " + com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.loadReport(helper.getLevel().getServer())));
        var fingerprint = com.jvn.villagerretaliation.campaign.CampaignRewardRecovery.fingerprint(helper.getLevel().getServer(), quest.id());
        helper.assertTrue(com.jvn.villagerretaliation.quest.content.bundle.QuestBundleMigrations.accepts(helper.getLevel().getServer(), quest.id(),
                "3fb79eb68ab3f0b37a2be37bc1d035ff0e8f5627331d6a9a3bacbb5270781bd8", fingerprint), "pending rewards from the previous definition remain recoverable");
        helper.assertFalse(com.jvn.villagerretaliation.quest.content.bundle.QuestBundleMigrations.accepts(helper.getLevel().getServer(), quest.id(),
                "0".repeat(64), fingerprint), "the migration does not authorize unrelated definitions");
        helper.assertValueEqual(quest.target().site().selection(), QuestSiteDefinition.Selection.PREFER_EXISTING,
                "shipped rescue prefers existing outposts");
        helper.assertValueEqual(quest.target().site().rescue(), QuestSiteDefinition.Rescue.ESCORT_HOME, "shipped rescue requires escorting home");
        var template = helper.getLevel().getStructureManager().get(TEMPLATE).orElseThrow(() -> new IllegalStateException("Holding camp template is missing"));
        helper.assertValueEqual(template.getSize().getX(), 9, "holding camp is bounded");
        helper.assertValueEqual(template.getSize().getZ(), 9, "holding camp is bounded");
        helper.assertValueEqual(quest.target().site().access().approaches().size(), 2, "the shipped quest defines two approaches");
        helper.assertTrue(quest.target().site().access().guardPositions().stream().allMatch(position -> position.getZ() < 0), "guards start on the front side");
        var origin = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(template.placeInWorld(helper.getLevel(), origin, origin,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setIgnoreEntities(true),
                helper.getLevel().getRandom(), 2), "the authored camp can be placed");
        var center = origin.offset(4, 1, 4);
        var guard = EntityType.PILLAGER.create(helper.getLevel());
        for (var offset : quest.target().site().access().guardPositions()) {
            guard.moveTo(center.offset(offset).getBottomCenter());
            helper.assertTrue(helper.getLevel().noCollision(guard), "authored guard spawns must clear the camp geometry: " + offset);
        }
        var captive = EntityType.VILLAGER.create(helper.getLevel());
        for (int z = 0; z <= 3; z++) {
            captive.moveTo(center.offset(0, 0, z).getBottomCenter());
            helper.assertTrue(helper.getLevel().noCollision(captive), "the captive has a clear passage through the rear entrance");
        }
        var structure = template.save(new CompoundTag());
        var palette = structure.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var blocks = structure.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int y = 1; y <= 2; y++) {
            boolean open = false;
            for (var raw : blocks) {
                var block = (CompoundTag) raw;
                var position = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
                if (position.getInt(0) == 4 && position.getInt(1) == y && position.getInt(2) == 5)
                    open = palette.getCompound(block.getInt("state")).getString("Name").equals("minecraft:air");
            }
            helper.assertTrue(open, "the rear entrance has two blocks of clearance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void unloadedActorsPauseAndOfflineSharedRunsRetainTheirSite(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "escort_home")) {
            fixture.site.captive = UUID.randomUUID();
            fixture.site.actorPosition = fixture.site.position.offset(200000, 0, 0);
            fixture.site.actorDimension = helper.getLevel().dimension().location();
            fixture.site.activatedAt = helper.getLevel().getGameTime() - 1000;
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertValueEqual(fixture.progress.state(), VillagerQuestSavedData.QuestState.ACTIVE, "unloaded last-known actor chunks do not fail quests");
            var shared = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(UUID.randomUUID(), QUEST);
            shared.start(null, helper.getLevel().dimension(), fixture.site.position, 0);
            shared.beginRun(UUID.randomUUID(), QUEST, fixture.site.run);
            fixture.progress.fail(helper.getLevel().getGameTime(), "left_party");
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of());
            helper.assertValueEqual(fixture.site.phase, Phase.ACTIVE, "offline shared members retain the encounter after its original owner leaves");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_site_placement", timeoutTicks = 400)
    public static void safePlacementCreatesOnePersistentEncounter(GameTestHelper helper) {
        var fixture = new Fixture(helper, "escort_home");
        var level = helper.getLevel();
        var center = fixture.site.position;
        for (int x = -32; x <= 32; x++) for (int z = -32; z <= 32; z++) {
            level.setBlockAndUpdate(center.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            for (int y = 0; y < 16; y++) level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        fixture.player.moveTo(fixture.site.position.offset(-512, 0, 0).getCenter());
        fixture.site.template = TEMPLATE;
        fixture.site.phase = Phase.PREPARING;
        fixture.site.validated = true;
        fixture.site.accepted = false;
        helper.startSequence().thenWaitUntil(() -> {
            QuestSiteService.prepare(level, fixture.data, fixture.site, fixture.quest.target().site());
            helper.assertValueEqual(fixture.site.phase, Phase.READY, "site preparation finishes: " + fixture.site.diagnostic);
        }).thenExecute(() -> {
            try {
                fixture.captive = (Villager) level.getEntity(fixture.site.captive);
                helper.assertTrue(fixture.captive != null && fixture.captive.isNoAi(), "prepared captive is persistent and held at the camp");
                helper.assertTrue(level.getBlockState(center.below()).is(Blocks.COARSE_DIRT), "holding camp was placed");
                helper.assertTrue(QuestSitePlanner.usable(level, fixture.site, fixture.quest.target().site()), "new holding camp supports its encounter");
                var reloaded = QuestSiteSavedData.load(fixture.data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
                helper.assertValueEqual(reloaded.sites.get(fixture.site.id).phase, Phase.READY, "ready sites survive reload without another placement");
                helper.assertValueEqual(reloaded.sites.get(fixture.site.id).captive, fixture.captive.getUUID(), "reload keeps the same captive");
                VillagerQuestSavedData.get(level).remove(fixture.player.getUUID(), QUEST);
                VillagerQuestService.startDiscoveredQuest(DialogueContext.forPlayer(fixture.player), QUEST);
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        }).thenWaitUntil(() -> helper.assertValueEqual(fixture.site.phase, Phase.ACTIVE, "acceptance resumes after terrain verification"))
                .thenExecute(() -> {
            try {
                var started = VillagerQuestSavedData.get(level).get(fixture.player.getUUID(), QUEST);
                helper.assertTrue(started != null && started.state() == VillagerQuestSavedData.QuestState.ACTIVE, "quest starts only after preparation");
                helper.assertValueEqual(started.questRunId(), fixture.site.run, "prepared actors belong to the accepted run");
                helper.assertValueEqual(fixture.player.getInventory().items.stream().filter(stack -> stack.is(Items.FILLED_MAP)).count(), 1L,
                        "acceptance issues exactly one map");
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                    for (int y = 0; y < 4; y++) level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                helper.assertFalse(QuestSitePlanner.usable(level, fixture.site, fixture.quest.target().site()), "demolished camps fail activation checks");
                fixture.site.discovered = false;
                fixture.player.moveTo(center.getCenter());
                QuestSiteService.maintain(level, fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
                helper.assertTrue(started.state() != VillagerQuestSavedData.QuestState.ACTIVE, "destroyed site gives a terminal lost-lead outcome");
                fixture.close();
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void realPartyEnrollmentReusesItsExistingEncounter(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "escort_home")) {
            var member = helper.makeMockServerPlayerInLevel();
            VillagerQuestService.setClientEffectsSuppressedForTests(member, true);
            try {
                helper.assertTrue(PartyQuestService.isShareable(fixture.quest), "site criteria support shared quest enrollment");
                helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.createParty(fixture.player).success(), "leader creates a party");
                var invitation = com.jvn.villagerretaliation.party.PartyService.sendInvitation(fixture.player, member);
                helper.assertTrue(invitation.success() && com.jvn.villagerretaliation.party.PartyService.acceptInvitation(member, invitation.invitationId()).success(),
                        "member joins the party");
                var party = com.jvn.villagerretaliation.party.PartyService.getPartyForPlayer(helper.getLevel(), member.getUUID()).orElseThrow();
                var shared = new com.jvn.villagerretaliation.party.PartySharedQuestRecord(QUEST, null, helper.getLevel().getGameTime());
                party.addSharedQuest(shared);
                fixture.site.run = shared.instanceId();
                var quests = VillagerQuestSavedData.get(helper.getLevel());
                quests.remove(fixture.player.getUUID(), QUEST);
                var leader = quests.getOrCreate(fixture.player.getUUID(), QUEST);
                leader.start(null, helper.getLevel().dimension(), fixture.site.position, 0);
                leader.beginRun(fixture.player.getUUID(), QUEST, shared.instanceId());
                leader.linkPartyQuest(shared.instanceId());
                leader.setCurrentStage("rescue");
                shared.enroll(fixture.player.getUUID(), false);
                var actor = fixture.captive();
                helper.assertTrue(QuestSiteService.offerAvailable(DialogueContext.forPlayer(member), fixture.quest),
                        "party eligibility resolves the existing site without preparing another");
                var outcome = VillagerQuestService.startDiscoveredQuest(DialogueContext.forPlayer(member), QUEST);
                helper.assertValueEqual(outcome.status(), "started", "member can enroll in the prepared encounter");
                helper.assertValueEqual(quests.get(member.getUUID(), QUEST).questRunId(), shared.instanceId(), "members share the definitive run");
                helper.assertValueEqual(fixture.data.sites.size(), 1, "party enrollment creates no duplicate site");
                QuestSiteService.interact(new PlayerInteractEvent.EntityInteract(member, InteractionHand.MAIN_HAND, actor));
                helper.assertTrue(fixture.site.released, "enrolled member can free the same captive");
            } finally {
                com.jvn.villagerretaliation.party.PartyVillagerContractService.disband(fixture.player);
                VillagerQuestService.clearRuntimeState(member);
                member.discard();
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_sites_rewards")
    public static void builtInRewardsIncludeRescue(GameTestHelper helper) {
        com.jvn.villagerretaliation.quest.content.QuestRewardBundleGameTests.allMigratedGenericRewardsCompileAndResolveFromBundles(helper);
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void missingNeighborDialogueRevealsTheSameExplorableEntrance(GameTestHelper helper) throws Exception {
        var id = ResourceLocation.parse("villagerretaliation:missing_neighbor");
        var compiled = VillagerQuestResources.compiledQuest(helper.getLevel().getServer(), id).orElseThrow();
        try (var fixture = new Fixture(helper, "escort_home", null, compiled)) {
            var captive = fixture.captive();
            fixture.guard();
            fixture.guard();
            fixture.player.moveTo(fixture.provider.position());
            var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player, fixture.provider);
            var binding = VillagerQuestResources.questDialogueCatalog(helper.getLevel().getServer()).bindings(id).stream()
                    .filter(value -> value.stageId().equals("rescue") && value.slot().equals("reminder")).findFirst().orElseThrow();
            helper.assertTrue(com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntry(context, binding.treeId(), binding.entryId()).isPresent(), "authored rescue reminder opens");
            var option = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions(context).orElseThrow().stream()
                    .filter(value -> value.label().equals("Is there another way inside?")).findFirst().orElseThrow();
            helper.assertTrue(com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.handleDialogueOption(context, option).isPresent(), "authored route explanation executes");
            var topic = ResourceLocation.parse("villagerretaliation:missing_neighbor_rear_entrance");
            helper.assertTrue(new com.jvn.villagerretaliation.dialogue.Knowledge.Condition(topic, "player", "heard", "speaker").matches(context), "giver shares the entrance with a remembered source");
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.leaveActiveSession(context);
            QuestSiteAccessService.observe(helper.getLevel(), fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertTrue(fixture.site.knownRoutes.get(fixture.player.getUUID()).contains("rear_entrance"), "dialogue reveals the route location");
            helper.assertTrue(fixture.site.reachedRoutes.isEmpty(), "learning the location does not complete the approach");
            fixture.player.moveTo(fixture.site.position.offset(0, 0, 3).getBottomCenter());
            fixture.interact(captive);
            helper.assertTrue(fixture.site.released, "visiting the rear entrance permits rescue with guards alive");
            helper.assertValueEqual(fixture.site.approach, "rear_entrance", "the authored route is recorded");
            helper.assertTrue(QuestFollowerService.retained(captive), "the rescued neighbor follows the player");
            captive.moveTo(fixture.site.home.getBottomCenter());
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertValueEqual(fixture.progress.currentStage(), "return", "the alternate approach reaches the normal turn-in");
            var knowledge = com.jvn.villagerretaliation.dialogue.KnowledgeSavedData.get(helper.getLevel());
            knowledge.forget(fixture.player.getUUID(), topic);
            knowledge.forget(fixture.provider.getUUID(), topic);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void approachConditionsStillApplyAfterVisitingARoute(GameTestHelper helper) throws Exception {
        var access = access();
        access.getAsJsonArray("approaches").get(1).getAsJsonObject().add("conditions",
                access.getAsJsonArray("routes").get(0).getAsJsonObject().get("reveal_when").deepCopy());
        try (var fixture = new Fixture(helper, "free_at_site", access)) {
            var captive = fixture.captive();
            fixture.guard();
            fixture.player.moveTo(fixture.site.position.offset(0, 0, 6).getBottomCenter());
            QuestSiteAccessService.observe(helper.getLevel(), fixture.site, fixture.quest, List.of(fixture.player));
            fixture.player.moveTo(fixture.site.position.getBottomCenter());
            fixture.interact(captive);
            helper.assertFalse(fixture.site.released, "a visited route cannot bypass extra approach conditions");
            var topic = ResourceLocation.parse("test:route");
            var knowledge = com.jvn.villagerretaliation.dialogue.KnowledgeSavedData.get(helper.getLevel());
            knowledge.witness(fixture.player.getUUID(), topic, helper.getLevel().getGameTime());
            fixture.interact(captive);
            helper.assertTrue(fixture.site.released, "the same approach succeeds once all conditions match");
            knowledge.forget(fixture.player.getUUID(), topic);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void accessSnapshotsAllowTextEditsButRejectChangedRules(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "free_at_site", access())) {
            var captive = fixture.captive();
            var saved = JsonParser.parseString(fixture.site.config).getAsJsonObject();
            var route = saved.getAsJsonObject("access").getAsJsonObject("definition").getAsJsonArray("routes").get(0).getAsJsonObject();
            route.addProperty("label", "An earlier translation");
            fixture.site.config = saved.toString();
            helper.assertTrue(QuestSiteAccessService.compatible(fixture.site, fixture.quest.target().site()), "display text changes preserve active sites");
            route.getAsJsonObject("offset").addProperty("x", 4);
            fixture.site.config = saved.toString();
            helper.assertFalse(QuestSiteAccessService.compatible(fixture.site, fixture.quest.target().site()), "changed route geometry is detected");
            for (String malformed : List.of("broken", "[]", "{", "{\"access\":null}", "{\"access\":{\"definition\":{\"routes\":[3]}}}")) {
                fixture.site.config = malformed;
                helper.assertFalse(QuestSiteAccessService.compatible(fixture.site, fixture.quest.target().site()), "malformed snapshots cannot silently change access rules");
                fixture.interact(captive);
                helper.assertFalse(fixture.site.released, "malformed snapshots preserve the encounter without releasing it");
            }
        }
        helper.succeed();
    }

    private static JsonObject access() {
        return JsonParser.parseString("""
                {"routes":[{"id":"rear","label":"Rear entrance","offset":{"x":0,"y":0,"z":6},"radius":1,
                  "reveal_when":[{"type":"knowledge","topic":"test:route","subject":"player","state":"complete"}]}],
                 "approaches":[{"id":"direct","guards_defeated":true},{"id":"around","route":"rear"}]}
                """).getAsJsonObject();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void authoredApproachesRejectAmbiguousOrInvalidDefinitions(GameTestHelper helper) {
        var config = config("escort_home");
        config.add("access", access());
        helper.assertValueEqual(QuestSiteDefinition.read(config).access().approaches().size(), 2, "named alternatives compile");
        for (String invalid : List.of(
                "{\"approaches\":[{\"id\":\"empty\"}]}",
                "{\"approaches\":[{\"id\":\"bad\",\"route\":\"missing\"}]}",
                "{\"approaches\":[{\"id\":\"same\",\"guards_defeated\":true},{\"id\":\"same\",\"guards_defeated\":true}]}",
                "{\"approaches\":[{\"id\":\"bad\",\"conditions\":[{\"type\":\"knowledge\"}]}]}",
                "{\"approaches\":[{\"id\":\"direct\",\"guards_defeated\":true}],\"guard_positions\":[{\"x\":0,\"y\":0,\"z\":0}]}")) {
            config.add("access", JsonParser.parseString(invalid));
            expectInvalid(helper, config);
        }
        var duplicate = access();
        duplicate.getAsJsonArray("routes").add(duplicate.getAsJsonArray("routes").get(0).deepCopy());
        config.add("access", duplicate);
        expectInvalid(helper, config);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void explorationCanFreeTheCaptiveWithGuardsAlive(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "free_at_site", access())) {
            var captive = fixture.captive();
            fixture.guard();
            fixture.interact(captive);
            helper.assertFalse(fixture.site.released, "the main approach is guarded");
            fixture.player.moveTo(fixture.site.position.offset(0, 0, 6).getBottomCenter());
            QuestSiteAccessService.observe(helper.getLevel(), fixture.site, fixture.quest, List.of(fixture.player));
            fixture.player.moveTo(fixture.site.position.getBottomCenter());
            fixture.interact(captive);
            helper.assertTrue(fixture.site.released, "walking the alternative route permits release");
            helper.assertValueEqual(fixture.site.approach, "around", "release records the actual approach");
            helper.assertTrue(fixture.guards.getFirst().isAlive(), "bypassing guards does not kill or remove them");
            QuestSiteService.maintain(helper.getLevel(), fixture.data, fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertValueEqual(fixture.progress.currentStage(), "return", "either approach reaches the shared objective");
            var restored = QuestSiteSavedData.load(fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess()).sites.get(fixture.site.id);
            helper.assertValueEqual(restored.approach, "around", "chosen approach survives reload");
            helper.assertTrue(restored.reachedRoutes.get(fixture.player.getUUID()).contains("rear"), "visited routes survive reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void informationRevealsButDoesNotTraverseARoute(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "escort_home", access())) {
            var captive = fixture.captive();
            fixture.guard();
            var knowledge = com.jvn.villagerretaliation.dialogue.KnowledgeSavedData.get(helper.getLevel());
            var topic = ResourceLocation.parse("test:route");
            knowledge.witness(fixture.player.getUUID(), topic, helper.getLevel().getGameTime());
            QuestSiteAccessService.observe(helper.getLevel(), fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertTrue(fixture.site.knownRoutes.get(fixture.player.getUUID()).contains("rear"), "information reveals the entrance");
            helper.assertTrue(fixture.site.reachedRoutes.isEmpty(), "information alone does not bypass guards");
            fixture.interact(captive);
            helper.assertFalse(fixture.site.released, "the route still needs to be reached");
            var restored = QuestSiteSavedData.load(fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_quest_sites", restored);
            helper.assertValueEqual(new com.jvn.villagerretaliation.dialogue.QuestSiteCondition(QUEST, "route_known", "rear", "")
                    .evaluate(helper.getLevel(), fixture.player), com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED, "route knowledge is queryable after reload");
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_quest_sites", fixture.data);
            fixture.site.defeated.add(fixture.guards.getFirst().getUUID());
            fixture.interact(captive);
            helper.assertTrue(fixture.site.released, "player may switch to combat after learning a route");
            helper.assertValueEqual(fixture.site.approach, "direct", "combat is recorded without a route lock");
            helper.assertTrue(QuestFollowerService.retained(captive), "both approaches start the same escort");
            fixture.player.moveTo(fixture.site.position.offset(0, 0, 6).getBottomCenter());
            QuestSiteAccessService.observe(helper.getLevel(), fixture.site, fixture.quest, List.of(fixture.player));
            fixture.interact(captive);
            helper.assertValueEqual(fixture.site.approach, "direct", "repeated interactions do not rewrite the outcome");
            knowledge.forget(fixture.player.getUUID(), topic);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void routeVisitsAreBoundToEachPlayerAndQuestRun(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "free_at_site", access())) {
            fixture.captive();
            fixture.guard();
            var other = helper.makeMockServerPlayerInLevel();
            try {
                other.moveTo(fixture.site.position.offset(0, 0, 6).getBottomCenter());
                QuestSiteAccessService.observe(helper.getLevel(), fixture.site, fixture.quest, List.of(other));
                helper.assertFalse(fixture.site.reachedRoutes.containsKey(other.getUUID()), "unrelated players cannot acquire site progress");
                fixture.player.moveTo(other.position());
                QuestSiteAccessService.observe(helper.getLevel(), fixture.site, fixture.quest, List.of(fixture.player));
                var progress = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(other.getUUID(), QUEST);
                progress.start(null, helper.getLevel().dimension(), fixture.site.position, 0);
                progress.beginRun(other.getUUID(), QUEST, fixture.site.run);
                progress.setCurrentStage("rescue");
                other.moveTo(fixture.site.position.getBottomCenter());
                helper.assertValueEqual(QuestSiteAccessService.approach(helper.getLevel(), fixture.site, fixture.quest, other), "", "party members cannot use another player's visited route remotely");
                other.moveTo(fixture.site.position.offset(0, 0, 6).getBottomCenter());
                helper.assertValueEqual(QuestSiteAccessService.approach(helper.getLevel(), fixture.site, fixture.quest, other), "around", "party member can use the same physical route");
                progress.complete(1, true);
                progress.start(null, helper.getLevel().dimension(), fixture.site.position, 2);
                progress.beginRun(other.getUUID(), QUEST, UUID.randomUUID());
                helper.assertValueEqual(new com.jvn.villagerretaliation.dialogue.QuestSiteCondition(QUEST, "route_reached", "rear", "")
                        .evaluate(helper.getLevel(), other), com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.UNMET, "new runs do not inherit an earlier site visit");
            } finally { other.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_sites")
    public static void existingRescuesKeepTheirOriginalAccessRules(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, "free_at_site", access())) {
            var captive = fixture.captive();
            fixture.guard();
            var saved = JsonParser.parseString(fixture.site.config).getAsJsonObject();
            saved.remove("access");
            fixture.site.config = saved.toString();
            helper.assertTrue(QuestSiteAccessService.compatible(fixture.site, fixture.quest.target().site()), "adding approaches preserves existing sites");
            fixture.player.moveTo(fixture.site.position.offset(0, 0, 6).getBottomCenter());
            QuestSiteAccessService.observe(helper.getLevel(), fixture.site, fixture.quest, List.of(fixture.player));
            helper.assertTrue(fixture.site.reachedRoutes.isEmpty(), "old sites retain their original geometry and rules");
            fixture.player.moveTo(fixture.site.position.getBottomCenter());
            fixture.interact(captive);
            helper.assertFalse(fixture.site.released, "legacy rescue still requires guards to be defeated");
            fixture.site.defeated.add(fixture.guards.getFirst().getUUID());
            fixture.interact(captive);
            helper.assertTrue(fixture.site.released, "existing saves remain completable");
        }
        helper.succeed();
    }

    private static JsonObject config(String rescue) {
        var root = JsonParser.parseString("""
                {"selection":"prefer_existing","structures":["minecraft:pillager_outpost"],
                 "templates":["villagerretaliation:quest/holding_camp"],"map_style":"search_circle",
                 "rescue":"escort_home","guards":0}
                """).getAsJsonObject();
        root.addProperty("rescue", rescue);
        return root;
    }

    private static void expectInvalid(GameTestHelper helper, JsonObject root) {
        try {
            QuestSiteDefinition.read(root);
            helper.fail("invalid site definition was accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final AutoCloseable catalog;
        final QuestSiteSavedData previousSites;
        final VillagerQuestSavedData previousQuests;
        final QuestFollowerSavedData previousFollowers;
        final QuestSiteSavedData data = new QuestSiteSavedData();
        final ServerPlayer player;
        final QuestDefinition quest;
        final Site site;
        final VillagerQuestSavedData.QuestProgress progress;
        final List<net.minecraft.world.entity.monster.Pillager> guards = new java.util.ArrayList<>();
        Villager captive, provider;

        Fixture(GameTestHelper helper, String rescue) {
            this(helper, rescue, null);
        }

        Fixture(GameTestHelper helper, String rescue, JsonObject access) {
            this(helper, rescue, access, null);
        }

        Fixture(GameTestHelper helper, String rescue, JsonObject access, com.jvn.villagerretaliation.quest.compiled.CompiledQuest supplied) {
            this.helper = helper;
            var level = helper.getLevel();
            var server = level.getServer();
            catalog = VillagerQuestResources.preserveTestOverride();
            previousSites = QuestSiteSavedData.get(level);
            previousQuests = VillagerQuestSavedData.get(level);
            previousFollowers = QuestFollowerSavedData.get(level);
            server.overworld().getDataStorage().set("villagerretaliation_quest_sites", data);
            server.overworld().getDataStorage().set("villagerretaliation_quests", new VillagerQuestSavedData());
            server.overworld().getDataStorage().set("villagerretaliation_followers", new QuestFollowerSavedData());
            var root = JsonParser.parseString("""
                    {"schema":"villagerretaliation:quest/v2","id":"test:rescue","localization_prefix":"test.rescue",
                     "provider":{"type":"villagerretaliation:discovery"},"entry_stage":"rescue",
                     "stages":[{"id":"rescue","objectives":[{"id":"saved","type":"criterion",
                       "criterion":"villagerretaliation:captive_rescued","match":{"quest":"test:rescue"}}],
                       "next":{"stage":"return"}},{"id":"return","objectives":[]}]}
                    """).getAsJsonObject();
            var target = new JsonObject();
            var config = config(rescue);
            if (access != null) config.add("access", access);
            target.add("site", config);
            root.add("target", target);
            var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/rescues/rescue/quest.json"), root).orElseThrow();
            var compiled = supplied == null ? QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope, level.registryAccess()).orElseThrow() : supplied;
            if (supplied == null) VillagerQuestResources.installCompiledTestCatalog(server, List.of(compiled));
            quest = compiled.asQuestDefinition();
            player = helper.makeMockServerPlayerInLevel();
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            var position = helper.absolutePos(new BlockPos(48, 3, 48));
            site = new Site(UUID.randomUUID(), player.getUUID(), quest.id(), null, level.dimension().location(), position.offset(-32, 0, 0));
            site.position = position;
            site.run = UUID.randomUUID();
            site.phase = Phase.ACTIVE;
            site.discovered = true;
            site.config = new Gson().toJson(quest.target().site());
            data.add(site);
            if (supplied != null) {
                provider = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
                provider.setNoAi(true);
                provider.setVillagerData(provider.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.CARTOGRAPHER));
                provider.moveTo(site.home.getBottomCenter());
            }
            progress = VillagerQuestSavedData.get(level).getOrCreate(player.getUUID(), quest.id());
            progress.start(provider == null ? null : provider.getUUID(), level.dimension(), position, 0);
            progress.beginRun(player.getUUID(), quest.id(), site.run);
            progress.setCurrentStage("rescue");
            player.moveTo(position.getCenter());
        }

        Villager captive() {
            captive = EntityType.VILLAGER.create(helper.getLevel());
            captive.setNoAi(true);
            captive.moveTo(site.position.getCenter());
            helper.getLevel().addFreshEntity(captive);
            site.captive = captive.getUUID();
            return captive;
        }

        net.minecraft.world.entity.monster.Pillager guard() {
            var guard = EntityType.PILLAGER.create(helper.getLevel());
            guard.setPersistenceRequired();
            guard.moveTo(site.position.offset(3, 0, 0).getCenter());
            helper.getLevel().addFreshEntity(guard);
            guards.add(guard);
            site.guards.add(guard.getUUID());
            return guard;
        }

        void interact(Villager actor) {
            helper.assertValueEqual(QuestSiteSavedData.get(helper.getLevel()), data, "site storage is current");
            helper.assertValueEqual(progress.questRunId(), site.run, "owner run is unchanged");
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.ACTIVE, "owner quest is active");
            boolean held = !site.released && !site.rescued;
            var event = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, actor);
            QuestSiteService.interact(event);
            if (held) helper.assertTrue(event.isCanceled(), "captive interaction is intercepted");

        }

        @Override public void close() throws Exception {
            if (captive != null) captive.discard();
            if (provider != null) provider.discard();
            guards.forEach(net.minecraft.world.entity.Entity::discard);
            VillagerQuestService.clearRuntimeState(player);
            player.discard();
            var storage = helper.getLevel().getServer().overworld().getDataStorage();
            storage.set("villagerretaliation_quest_sites", previousSites);
            storage.set("villagerretaliation_quests", previousQuests);
            storage.set("villagerretaliation_followers", previousFollowers);
            catalog.close();
        }
    }
}
