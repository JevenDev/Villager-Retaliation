package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.diplomacy.route.AidRouteFixture;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import com.jvn.villagerretaliation.diplomacy.route.RouteGroundService;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_routes")
@PrefixGameTestTemplate(false)
public final class SettlementRouteGameTests {
    private static DiplomaticRoute path(BlockPos start, int length) {
        var steps = new java.util.ArrayList<DiplomaticRoute.Step>();
        for (int i = 0; i <= length; i++) steps.add(new DiplomaticRoute.Step(start.east(i), DiplomaticRoute.Mode.WALK));
        return new DiplomaticRoute(steps);
    }
    private static SettlementRoute route(GameTestHelper helper, int length) {
        return new SettlementRoute(UUID.randomUUID(), new VillageAllegianceId(UUID.randomUUID()), new VillageAllegianceId(UUID.randomUUID()),
                helper.getLevel().dimension().location(), "Test route", path(helper.absolutePos(new BlockPos(1, 24, 1)), length), 1);
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void mixedSectionsProduceTwelveAndHalfMinutesAndNeverStack(GameTestHelper helper) {
        var route = route(helper, 32);
        java.util.Arrays.fill(route.wear, 0, 16, (byte) 8); java.util.Arrays.fill(route.built, 16, 32, (byte) 1);
        helper.assertValueEqual(10 * SettlementRoute.timeFactor(route.trail(0), route.construction(0))
                + 10 * SettlementRoute.timeFactor(route.trail(1), route.construction(1)), 12.5, "20 minute mixed route takes 12.5 minutes");
        helper.assertValueEqual(route.estimatedTicks(), 160.0, "time is accumulated from the actual edges");
        java.util.Arrays.fill(route.wear, (byte) 8); java.util.Arrays.fill(route.built, (byte) 1);
        helper.assertValueEqual(route.estimatedTicks(), 128.0, "trail and road never exceed the 50 percent cap");
        route.built[0] = 0; route.wear[0] = 4;
        helper.assertValueEqual(route.estimatedTicks(), 131.0, "partial trail earns proportional benefit");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void routeAndPendingDevelopmentRoundTripAndRejectInvalidProgress(GameTestHelper helper) {
        var route = route(helper, 33); route.wear[2] = 3; route.built[17] = 1; route.pendingWear[32] = 4;
        route.projects[1] = UUID.randomUUID(); route.blockers[1] = "Missing cobblestone"; route.points = List.of(route.path.steps().get(8).position());
        helper.assertValueEqual(SettlementRoute.load(route.save()).save(), route.save(), "path, points, sections, jobs and pending wear persist");
        var malformed = route.save(); var wear = malformed.getByteArray("Wear"); wear[0] = 9; malformed.putByteArray("Wear", wear);
        boolean rejected = false; try { SettlementRoute.load(malformed); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "invalid progress cannot create excess benefit"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void removedOrEditedRouteKeepsJourneySnapshotAndDefaultHasNoWear(GameTestHelper helper) {
        var f = SettlementCourierGameTests.fixture(helper, false); var request = f.request(); var journey = request.journey;
        var route = new SettlementRoute(UUID.randomUUID(), request.donor(), request.requester(), journey.dimension, "Supply trail", journey.crossing, 1);
        var data = SettlementSavedData.get(helper.getLevel()); data.aid().routes.put(route.id, route);
        journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.step = 1; journey.position = journey.crossing.steps().get(1).position();
        SettlementRouteService.travelled(helper.getLevel(), journey);
        helper.assertValueEqual(route.pendingWear[0], (byte) 0, "default supply journeys never develop custom routes");
        java.util.Arrays.fill(route.wear, (byte) 8); journey.select(route, false); var original = journey.save(helper.getLevel().registryAccess());
        java.util.Arrays.fill(route.wear, (byte) 0); route.revision++;
        SettlementRouteService.travelled(helper.getLevel(), journey);
        helper.assertValueEqual(route.pendingWear[0], (byte) 0, "old journeys cannot wear an edited route");
        data.aid().routes.remove(route.id);
        helper.assertValueEqual(journey.timeFactor(), .75, "removal keeps the departure benefit");
        helper.assertValueEqual(SettlementCourierJourney.load(original, helper.getLevel().registryAccess()).save(helper.getLevel().registryAccess()), original, "snapshot survives reload independently");
        helper.assertValueEqual(request.offer.remaining().getFirst().item().getCount(), 20, "route operations never move cargo"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void simulatedAndPhysicalProgressHaveExclusiveClocks(GameTestHelper helper) {
        var route = route(helper, 8); java.util.Arrays.fill(route.built, (byte) 1);
        var villager = EntityType.VILLAGER.create(helper.getLevel()); var from = route.path.steps().getFirst().position();
        var journey = new SettlementCourierJourney(UUID.randomUUID(), villager.getUUID(), route.origin, route.dimension,
                path(from, 0), route.path, villager.saveWithoutId(new CompoundTag()), 1, ItemStack.EMPTY, 0);
        journey.select(route, false); journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.presence = SettlementCourierJourney.Presence.STORED;
        journey.advance(3); helper.assertValueEqual(journey.step, 0, "built road still requires travel time");
        journey.advance(4); helper.assertValueEqual(journey.step, 1, "built edge takes half normal time");
        journey.presence = SettlementCourierJourney.Presence.PHYSICAL; journey.advance(10000);
        helper.assertValueEqual(journey.step, 1, "physical navigation cannot receive background progress");
        helper.assertValueEqual(.5 / journey.timeFactor(), 1.0, "physical walking uses the same road multiplier");
        journey.presence = SettlementCourierJourney.Presence.STORED; journey.lastTick = 10000; journey.advance(10000);
        helper.assertValueEqual(journey.step, 1, "handoff does not count elapsed physical travel again");
        var loaded = SettlementCourierJourney.load(journey.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        loaded.advance(10004); helper.assertValueEqual(loaded.step, 2, "reload advances only the next edge"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void naturalWearIsGradualAndProtectsModifiedGround(GameTestHelper helper) {
        var level = helper.getLevel(); var route = route(helper, 2); var ground = route.path.steps().get(1).position().below();
        AidRouteFixture.naturalGround(level, ground);
        helper.assertValueEqual(RouteGroundService.customGroundProblem(level, ground), "", "untouched open soil is eligible");
        route.pendingWear[0] = 2; SettlementRouteService.develop(level, route, 0);
        helper.assertTrue(level.getBlockState(ground).is(Blocks.COARSE_DIRT), "early travel leaves worn soil");
        route.pendingWear[0] = 2; SettlementRouteService.develop(level, route, 0);
        helper.assertTrue(level.getBlockState(ground).is(Blocks.ROOTED_DIRT), "intermediate wear is gradual");
        route.pendingWear[0] = 4; SettlementRouteService.develop(level, route, 0);
        helper.assertTrue(level.getBlockState(ground).is(Blocks.DIRT_PATH), "repeated travel completes a trail");
        helper.assertValueEqual(route.wear[0], (byte) 8, "completed trail records only realized development");
        level.setBlock(ground, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3); route.pendingWear[0] = 8;
        SettlementRouteService.develop(level, route, 0);
        helper.assertTrue(level.getBlockState(ground).is(Blocks.DIAMOND_BLOCK), "valuable player block is preserved");
        helper.assertValueEqual(route.wear[0], (byte) 0, "obstructed ground loses development credit");
        helper.assertFalse(route.blockers[0].isEmpty(), "obstruction is reported"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void cropsContainersAndPlayerSoilCannotBeOverwritten(GameTestHelper helper) {
        var level = helper.getLevel(); var ground = helper.absolutePos(new BlockPos(1, 24, 1));
        for (var block : List.of(Blocks.CHEST, Blocks.FARMLAND, Blocks.STONE_BRICKS, Blocks.WATER)) {
            AidRouteFixture.naturalGround(level, ground); level.setBlock(ground, block.defaultBlockState(), 3);
            helper.assertFalse(RouteGroundService.wearGround(level, ground, 8), "unsafe ground is rejected: " + block);
            helper.assertTrue(level.getBlockState(ground).is(block), "unsafe block remains intact");
        }
        AidRouteFixture.naturalGround(level, ground); level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 3);
        helper.assertFalse(RouteGroundService.wearGround(level, ground, 8), "player changed soil is protected even though it is natural material");
        AidRouteFixture.naturalGround(level, ground); level.setBlock(ground.above(), Blocks.WHEAT.defaultBlockState(), 3);
        helper.assertFalse(RouteGroundService.wearGround(level, ground, 8), "crops block development"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void queuedRoadProtectsGroundChangedAfterCommission(GameTestHelper helper) {
        var f = SettlementCourierGameTests.fixture(helper, false); var level = helper.getLevel();
        var ground = helper.absolutePos(new BlockPos(1, 24, 1)); AidRouteFixture.naturalGround(level, ground);
        var preview = new SettlementPathTerrain.Preview(List.of(new SettlementPathTerrain.Cell(ground, SettlementPathTerrain.Kind.CONVERT,
                level.getBlockState(ground), level.getBlockState(ground.above()), level.getBlockState(ground.above(2)),
                Blocks.COBBLESTONE.defaultBlockState(), level.getBlockState(ground.below()))), "", SettlementPath.Material.COBBLESTONE);
        var order = SettlementPathTerrain.order(preview); order.route(UUID.randomUUID());
        helper.assertFalse(order.protectedSite(level, f.home()), "untouched road site remains available");
        level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(order.protectedSite(level, f.home()), "player edit is protected even when restored to the quoted material");
        helper.assertTrue(level.getBlockState(ground).is(Blocks.GRASS_BLOCK), "checking the obstruction does not place a road"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void caravanMembersShareCustodyLifecycleAndKeepOneCargoOwner(GameTestHelper helper) {
        var f = SettlementCourierGameTests.fixture(helper, false); var leader = f.request(); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        var member = member(helper, f, true);
        SettlementCaravanService.claim(level, leader, f.home(), List.of(member));
        member.journey.presence = SettlementCourierJourney.Presence.STORED;
        helper.assertFalse(f.home().residents().get(member.journey.resident).availableForWork(), "guard is unavailable to other work");
        helper.assertTrue(member.offer == null, "guard never holds a duplicate cargo reservation");
        var saved = data.aid().save(level.registryAccess());
        helper.assertValueEqual(SettlementAidLedger.load(saved, level.registryAccess()).save(level.registryAccess()), saved, "group, roles and ownership survive reload");
        long now = leader.journey.lastTick;
        for (int tick = 1; tick < 5000 && (!leader.journey.finished() || !member.journey.finished()); tick++) {
            data.aid().durableRevision = data.aid().revision();
            SettlementCourierService.step(level, leader, now + tick * 8L, new SettlementCourierService.Runtime());
            data.aid().durableRevision = data.aid().revision();
            SettlementCourierService.step(level, member, now + tick * 8L, new SettlementCourierService.Runtime());
        }
        data.aid().durableRevision = data.aid().revision(); SettlementCourierService.step(level, leader, now + 50000, new SettlementCourierService.Runtime());
        data.aid().durableRevision = data.aid().revision(); SettlementCourierService.step(level, member, now + 50000, new SettlementCourierService.Runtime());
        helper.assertTrue(leader.journey.finished() && member.journey.finished(), "leader and guard complete the same round trip: " + leader.blocker() + " / " + member.blocker());
        helper.assertValueEqual(SettlementAidStock.nutrition(f.recipient().town()), 100L, "one shipment arrives once");
        helper.assertFalse(f.home().residents().get(member.journey.resident).courierAway(), "guard returns with identity intact");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void blockedMemberPausesCaravanAndClearingBlockerResumesIt(GameTestHelper helper) {
        var f = SettlementCourierGameTests.fixture(helper, false); var leader = f.request(); var level = helper.getLevel();
        var member = member(helper, f, false); SettlementCaravanService.claim(level, leader, f.home(), List.of(member));
        leader.journey.leg = member.journey.leg = SettlementCourierJourney.Leg.OUTBOUND;
        member.journey.blocker = "courier_in_danger";
        helper.assertFalse(SettlementCaravanService.ready(level, leader), "leader waits for incapacitated member");
        helper.assertValueEqual(leader.offer.remaining().getFirst().item().getCount(), 20, "blocked group preserves its cargo");
        member.journey.blocker = "caravan_waiting";
        helper.assertTrue(SettlementCaravanService.ready(level, leader), "derived wait states cannot deadlock each other");
        member.journey.presence = SettlementCourierJourney.Presence.DEAD;
        helper.assertFalse(SettlementCaravanService.ready(level, leader), "missing member is never silently recreated"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "route_editor")
    public static void createEditRemoveRoutesWithAuthorityAndStaleWriteChecks(GameTestHelper helper) {
        var f = SettlementCourierGameTests.fixture(helper, false); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        var player = helper.makeMockServerPlayerInLevel(); player.setUUID(f.home().leader()); player.moveTo(f.home().center().getCenter());
        com.jvn.villagerretaliation.diplomacy.VillageDiplomacyApi.set(level, f.home().id(), f.recipient().town().id(), com.jvn.villagerretaliation.diplomacy.VillageRelation.NEUTRAL);
        UUID id = UUID.randomUUID(); long[] revision = {0};
        helper.startSequence().thenWaitUntil(() -> {
            String result = SettlementRouteService.save(player, id, f.home().id(), f.recipient().town().id(), "Reusable route", 0, List.of());
            helper.assertTrue(result.startsWith("Saved"), result);
        }).thenExecute(() -> {
            var route = data.aid().routes.get(id); revision[0] = route.revision;
            helper.assertTrue(SettlementRoute.load(route.save()).path.equals(route.path), "created route survives save and load");
            helper.assertTrue(SettlementRouteService.save(player, id, f.home().id(), f.recipient().town().id(), "Stale edit", 0, List.of()).contains("refresh"), "stale edit is rejected");
        }).thenWaitUntil(() -> {
            var point = f.home().center().east(64).south(4);
            String result = SettlementRouteService.save(player, id, f.home().id(), f.recipient().town().id(), "Adjusted route", revision[0], List.of(point));
            helper.assertTrue(result.startsWith("Saved"), result);
        }).thenExecute(() -> {
            var route = data.aid().routes.get(id);
            helper.assertValueEqual(route.points.size(), 1, "edited waypoint persists");
            helper.assertTrue(route.revision > revision[0], "edits advance route revision");
            helper.assertTrue(SettlementRouteService.remove(player, route, route.revision).startsWith("Removed"), "authorized route removal succeeds");
            helper.assertFalse(data.aid().routes.containsKey(id), "removed route is absent");
            helper.assertValueEqual(f.request().offer.remaining().getFirst().item().getCount(), 20, "editing does not affect active shipment cargo");
        }).thenSucceed();
    }
    @GameTest(template = "empty", timeoutTicks = 800, batch = "route_construction")
    public static void sectionUsesExistingBuilderEscrowAndAccuratePartialProgress(GameTestHelper helper) {
        var f = SettlementPathConstructionGameTests.fixture(helper); var level = helper.getLevel();
        SettlementConstructionCrewGameTests.adopt(helper, f);
        var recipient = SettlementAidGameTests.fixture(helper, 128).town();
        com.jvn.villagerretaliation.diplomacy.VillageDiplomacyApi.set(level, f.town().id(), recipient.id(), com.jvn.villagerretaliation.diplomacy.VillageRelation.NEUTRAL);
        var base = f.origin().offset(2, -1, 4);
        for (int i = 0; i < 8; i++) AidRouteFixture.naturalGround(level, base.east(i));
        var route = new SettlementRoute(UUID.randomUUID(), f.town().id(), recipient.id(), level.dimension().location(), "Builder road", path(base.west().above(), 8), 1);
        var data = SettlementSavedData.get(level); data.aid().routes.put(route.id, route);
        String result = SettlementRouteService.construct(f.leader(), route, 0);
        helper.assertTrue(route.projects[0] != null, result);
        var project = f.town().projects().get(route.projects[0]); var plan = project.order().plan(project.id());
        helper.assertTrue(project.pending(), "materials are required before road work starts");
        helper.assertTrue(SettlementConstructionOrder.load(project.order().save()).routeWork(f.town()), "route construction survives reload");
        helper.assertTrue(SettlementRouteService.remove(f.leader(), route, route.revision).contains("construction"), "active builder orders prevent route removal");
        SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), plan, f.origin(), project.id());
        SettlementConstructionMaterials.collect(f.town(), project, f.builder().getUUID(), SettlementConstructionMaterials.collectionTarget(project));
        com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(f.builder()).setItem(
                com.jvn.villagerretaliation.inventory.HiredJobInventory.MAINHAND_SLOT, new ItemStack(net.minecraft.world.item.Items.IRON_SHOVEL));
        var worker = new com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker();
        helper.startSequence().thenWaitUntil(() -> {
            if (project.site() != null) SettlementConstructionSiteService.refresh(level, project.site());
            SettlementConstructionService.reconcile(f.builder());
            int index = SettlementConstructionCoordination.select(f.town(), project, f.builder().getUUID(), plan, level, level.getGameTime());
            if (index >= 0 && index < project.total()) f.builder().setPos(plan.worldPos(project.origin(), plan.blocks().get(index)).north(2).atY(f.origin().getY()).getBottomCenter());
            var session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(level, f.builder(), com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER);
            worker.tick(level, f.builder(), null, session.context());
            SettlementConstructionService.observe(f.builder(), com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(f.builder()));
            SettlementRouteService.refresh(level, route);
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "section finishes: " + project.blocker());
        }).thenExecute(() -> {
            SettlementRouteService.refresh(level, route);
            helper.assertValueEqual(route.construction(0), 1.0, "only placed cobblestone receives built credit");
            var quote = project.materialEscrow().getList("Quote", Tag.TAG_COMPOUND).getCompound(0);
            helper.assertValueEqual(quote.getInt("Spent"), 2, "eight route cells consume two cobblestone through existing escrow");
            level.setBlock(base, Blocks.DIRT.defaultBlockState(), 3); SettlementRouteService.refresh(level, route);
            helper.assertValueEqual(route.construction(0), .875, "removed road immediately reduces section credit");
            f.builder().discard();
        }).thenSucceed();
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "route_recall")
    public static void recalledShipmentReturnsCargoOnceAndKeepsResidentIdentity(GameTestHelper helper) {
        var f = SettlementCourierGameTests.fixture(helper, false); var level = helper.getLevel(); var request = f.request(); var journey = request.journey;
        journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.step = 16; journey.position = journey.crossing.steps().get(16).position();
        request.offer.custody = SettlementFoodOffer.Custody.COURIER;
        var player = helper.makeMockServerPlayerInLevel(); player.setUUID(f.home().leader()); player.moveTo(f.home().center().getCenter());
        helper.assertTrue(SettlementCaravanService.recall(player, request.id(), request.revision()).startsWith("Survivors"), "owner can recall a land shipment");
        player.moveTo(0, 100, 0);
        var data = SettlementSavedData.get(level); var runtime = new SettlementCourierService.Runtime();
        helper.startSequence().thenWaitUntil(() -> {
            data.aid().durableRevision = data.aid().revision(); SettlementCourierService.step(level, request, level.getGameTime(), runtime);
            helper.assertValueEqual(request.state(), SettlementFoodRequest.State.CANCELLED, "recall returns home: " + request.blocker());
        }).thenExecute(() -> {
            helper.assertValueEqual(SettlementAidStock.nutrition(f.home()), 100L, "undelivered cargo returns to donor storage");
            helper.assertValueEqual(SettlementAidStock.nutrition(f.recipient().town()), 0L, "recall grants no delivery or duplicate cargo");
            helper.assertFalse(f.home().residents().get(journey.resident).courierAway(), "recalled resident is available again");
            data.aid().durableRevision = data.aid().revision(); SettlementCourierService.step(level, request, level.getGameTime() + 8, runtime);
            helper.assertValueEqual(SettlementAidStock.nutrition(f.home()), 100L, "repeated completion cannot refund twice");
            helper.assertValueEqual(SettlementFoodRequest.load(request.save(level.registryAccess()), level.registryAccess()).save(level.registryAccess()), request.save(level.registryAccess()), "recalled custody survives reload");
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void recalledCollectionRefundsUncollectedFood(GameTestHelper helper) { recalledCollection(helper, false); }
    @GameTest(template = "empty", batch = "route_unit")
    public static void recalledCollectionDeliversCollectedFood(GameTestHelper helper) { recalledCollection(helper, true); }
    private static void recalledCollection(GameTestHelper helper, boolean collected) {
        var level = helper.getLevel();
        var f = SettlementCourierGameTests.fixture(helper, true); var request = f.request(); var journey = request.journey;
        var donor = SettlementSavedData.get(level).get(request.donor()).orElseThrow();
        request.recalled = true; journey.leg = SettlementCourierJourney.Leg.HOMEBOUND;
        journey.step = journey.route().steps().size() - 1; journey.position = journey.route().steps().getLast().position();
        request.offer.custody = collected ? SettlementFoodOffer.Custody.COURIER : SettlementFoodOffer.Custody.RESERVED;
        var data = SettlementSavedData.get(level); data.aid().durableRevision = data.aid().revision();
        SettlementCourierTransfers.arrived(level, request, null, journey.lastTick + 8);
        helper.assertValueEqual(SettlementAidStock.nutrition(f.home()), collected ? 100L : 0L, "only collected cargo reaches the requester");
        helper.assertValueEqual(SettlementAidStock.nutrition(donor), collected ? 0L : 100L, "uncollected reservation returns to donor storage");
        helper.assertValueEqual(request.deliveredNutrition(), collected ? 100L : 0L, "recall never credits an uncollected shipment");
        helper.assertTrue(request.offer.empty(), "cargo has exactly one final owner");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void companionTransitionsRejectDuplicateNativeEntities(GameTestHelper helper) {
        var f = SettlementCourierGameTests.fixture(helper, false); var level = helper.getLevel(); var member = member(helper, f, true);
        SettlementCaravanService.claim(level, f.request(), f.home(), List.of(member)); var journey = member.journey;
        var nativeMember = EntityType.VILLAGER.create(level); nativeMember.load(journey.residentContext.copy());
        nativeMember.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id);
        nativeMember.getPersistentData().putLong(SettlementCourierService.INCARNATION, journey.incarnation);
        journey.presence = SettlementCourierJourney.Presence.STORED;
        var duplicate = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(nativeMember, level); SettlementCourierService.join(duplicate);
        helper.assertTrue(duplicate.isCanceled(), "stored group custody rejects a native duplicate");
        journey.presence = SettlementCourierJourney.Presence.MATERIALIZING;
        var valid = EntityType.VILLAGER.create(level); valid.load(journey.residentContext.copy());
        valid.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id); valid.getPersistentData().putLong(SettlementCourierService.INCARNATION, journey.incarnation);
        var accepted = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(valid, level); SettlementCourierService.join(accepted);
        helper.assertFalse(accepted.isCanceled(), "current materialization is accepted for a caravan member");
        journey.presence = SettlementCourierJourney.Presence.PHYSICAL; journey.incarnation++;
        var stale = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(valid, level); SettlementCourierService.join(stale);
        helper.assertTrue(stale.isCanceled(), "stale companion incarnation cannot return after handoff");
        helper.assertTrue(member.offer == null, "presence transitions never clone leader cargo"); helper.succeed();
    }
    private static SettlementFoodRequest member(GameTestHelper helper, SettlementCourierGameTests.Fixture f, boolean guard) {
        var level = helper.getLevel(); var leader = f.request(); var villager = EntityType.VILLAGER.create(level);
        villager.moveTo(leader.journey.approach.steps().getFirst().position().getBottomCenter());
        com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, villager, f.home().id(), com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
        f.home().recordResident(SettlementResidentState.capture(villager, f.home(), level.getGameTime()));
        leader.participants.put(villager.getUUID(), guard);
        var member = new SettlementFoodRequest(UUID.randomUUID(), leader.requester(), leader.donor(), 1, leader.created(), leader.created(), leader.expires(), leader.authority());
        member.purpose = guard ? SettlementFoodRequest.Purpose.GUARD : SettlementFoodRequest.Purpose.COMPANION; member.caravan = leader.id(); member.state = SettlementFoodRequest.State.TRAVELLING;
        member.journey = new SettlementCourierJourney(member.id(), villager.getUUID(), f.home().id(), level.dimension().location(), leader.journey.approach,
                leader.journey.crossing, villager.saveWithoutId(new CompoundTag()), 1, ItemStack.EMPTY, leader.journey.lastTick);
        return member;
    }
    @GameTest(template = "empty", batch = "route_unit")
    public static void legacyJourneyAndLedgerRemainReadableWithoutCustomRoutes(GameTestHelper helper) {
        var f = SettlementCourierGameTests.fixture(helper, false); var data = SettlementSavedData.get(helper.getLevel());
        var saved = data.aid().save(helper.getLevel().registryAccess()); saved.putInt("Version", 3); saved.remove("Routes");
        var requests = new net.minecraft.nbt.ListTag(); var legacy = f.request().save(helper.getLevel().registryAccess());
        legacy.remove("Participants"); requests.add(legacy); saved.put("Requests", requests); saved.put("Receipts", new net.minecraft.nbt.ListTag());
        var loaded = SettlementAidLedger.load(saved, helper.getLevel().registryAccess());
        var request = loaded.request(f.request().id());
        helper.assertTrue(request.journey.customRoute == null && request.participants.isEmpty(), "old shipments remain solo default journeys");
        helper.assertValueEqual(request.offer.save(helper.getLevel().registryAccess()), f.request().offer.save(helper.getLevel().registryAccess()), "legacy cargo remains in its original custody"); helper.succeed();
    }
}
