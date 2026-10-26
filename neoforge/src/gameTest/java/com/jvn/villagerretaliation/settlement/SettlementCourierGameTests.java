package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_couriers")
@PrefixGameTestTemplate(false)
public final class SettlementCourierGameTests {
    @GameTest(template = "empty")
    public static void courierProgressIsExclusiveAndDoesNotCatchUp(GameTestHelper helper) {
        var fixture = fixture(helper, false); var journey = fixture.request.journey;
        journey.presence = SettlementCourierJourney.Presence.PHYSICAL; journey.advance(1000000);
        helper.assertValueEqual(journey.step, 0, "physical couriers never advance abstractly");
        journey.presence = SettlementCourierJourney.Presence.STORED; journey.advance(1000000);
        helper.assertValueEqual(journey.step, 1, "large time gap advances only one step");
        journey.obstructed = true; journey.advance(2000000);
        helper.assertValueEqual(journey.step, 1, "observed obstruction cannot be escaped in the background");
        var saved = fixture.request.save(helper.getLevel().registryAccess());
        var loaded = SettlementFoodRequest.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.save(helper.getLevel().registryAccess()), saved, "journey, resident and cargo survive a save round trip");
        var malformed = saved.copy(); malformed.getCompound("Journey").putInt("Step", 100000);
        boolean rejected = false;
        try { SettlementFoodRequest.load(malformed, helper.getLevel().registryAccess()); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "invalid progress is rejected without resetting custody");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void donorDeliveryAndCollectionConserveFoodAndResident(GameTestHelper helper) {
        for (boolean collection : new boolean[]{false, true}) {
            var fixture = fixture(helper, collection); var request = fixture.request; var journey = request.journey;
            var data = SettlementSavedData.get(helper.getLevel()); var runtime = new SettlementCourierService.Runtime();
            for (long time = journey.lastTick + 8, end = journey.lastTick + 4000; time < end && !journey.finished(); time += 8) {
                data.aid().durableRevision = data.aid().revision();
                SettlementCourierService.step(helper.getLevel(), request, time, runtime);
                if (time > 100000) throw new IllegalStateException("Courier did not finish");
            }
            data.aid().durableRevision = data.aid().revision();
            SettlementCourierService.step(helper.getLevel(), request, journey.lastTick + 8, runtime);
            helper.assertTrue(journey.finished(), "round trip finishes for both arrangements");
            helper.assertValueEqual(request.state(), SettlementFoodRequest.State.COMPLETED, "delivery and return complete together");
            helper.assertValueEqual(SettlementAidStock.nutrition(fixture.recipient.town()), 100L, "exact offered food reaches its destination");
            helper.assertTrue(request.offer.empty(), "delivered food leaves courier custody");
            helper.assertValueEqual(request.deliveredNutrition(), 100L, "only delivered food is credited");
            helper.assertFalse(fixture.home.residents().get(journey.resident).courierAway(), "same resident returns to its home commitments");
            helper.assertTrue(SettlementCourierService.existing(helper.getLevel().getServer(), journey.resident) == null, "background journey creates no entity or chunk ticket");
            var replay = SettlementFoodRequest.load(request.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertValueEqual(replay.journey.position(), journey.approach.steps().getFirst().position(), "returned resident position persists");
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void fullDestinationRetainsCourierCargoAtTheReachedDepot(GameTestHelper helper) {
        var fixture = fixture(helper, false); var request = fixture.request; var journey = request.journey;
        for (int slot = 0; slot < fixture.recipient.depot().getContainerSize(); slot++) fixture.recipient.depot().setItem(slot, new ItemStack(Items.STONE, 64));
        journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.step = journey.route().steps().size() - 1;
        journey.position = journey.route().steps().getLast().position(); request.offer.custody = SettlementFoodOffer.Custody.COURIER;
        var data = SettlementSavedData.get(helper.getLevel()); data.aid().durableRevision = data.aid().revision();
        SettlementCourierTransfers.arrived(helper.getLevel(), request, null, journey.lastTick + 10);
        helper.assertValueEqual(request.blocker(), "destination_storage_full", "full depot exposes a recoverable blocker");
        helper.assertValueEqual(request.deliveredNutrition(), 0L, "full destination awards no goodwill");
        helper.assertValueEqual(request.offer.remaining().getFirst().item().getCount(), 20, "all food remains with the courier");
        fixture.recipient.depot().setItem(0, new ItemStack(Items.BREAD, 60)); data.aid().durableRevision = data.aid().revision();
        SettlementCourierTransfers.arrived(helper.getLevel(), request, null, journey.lastTick + 20);
        helper.assertValueEqual(request.deliveredNutrition(), 20L, "only the four accepted meals are credited");
        helper.assertValueEqual(request.offer.remaining().getFirst().item().getCount(), 16, "remaining meals are preserved");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void residentOwnershipRejectsStaleCopiesAndWorkAssignments(GameTestHelper helper) {
        var fixture = fixture(helper, false); var journey = fixture.request.journey; var resident = fixture.home.residents().get(journey.resident);
        helper.assertFalse(resident.availableForWork(), "borrowed resident cannot work simultaneously");
        helper.assertFalse(resident.canDepart(), "borrowed resident cannot migrate simultaneously");
        var copy = EntityType.VILLAGER.create(helper.getLevel()); copy.load(journey.residentContext.copy());
        copy.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id);
        copy.getPersistentData().putLong(SettlementCourierService.INCARNATION, journey.incarnation - 1);
        journey.presence = SettlementCourierJourney.Presence.PHYSICAL;
        var stale = new EntityJoinLevelEvent(copy, helper.getLevel()); SettlementCourierService.join(stale);
        helper.assertTrue(stale.isCanceled(), "old native incarnation cannot rejoin");
        var current = EntityType.VILLAGER.create(helper.getLevel()); current.load(journey.residentContext.copy());
        current.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id);
        current.getPersistentData().putLong(SettlementCourierService.INCARNATION, journey.incarnation);
        var accepted = new EntityJoinLevelEvent(current, helper.getLevel()); SettlementCourierService.join(accepted);
        helper.assertFalse(accepted.isCanceled(), "current physical incarnation is admitted");
        journey.presence = SettlementCourierJourney.Presence.STORED;
        var abstractCopy = new EntityJoinLevelEvent(current, helper.getLevel()); SettlementCourierService.join(abstractCopy);
        helper.assertTrue(abstractCopy.isCanceled(), "stored ownership rejects even the previous physical incarnation");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void physicalCourierWalksAndNativeCheckpointCompletes(GameTestHelper helper) {
        var fixture = fixture(helper, false); var request = fixture.request; var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int x = 0; x < 7; x++) for (int z = 0; z < 4; z++) helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.STONE);
        var player = helper.makeMockServerPlayerInLevel(); player.moveTo(origin.getX(), origin.getY(), origin.getZ() + 3);
        var courier = EntityType.VILLAGER.create(level); courier.load(request.journey.residentContext.copy()); courier.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        request.journey = new SettlementCourierJourney(request.id(), courier.getUUID(), fixture.home.id(), level.dimension().location(), route(origin, origin.offset(3, 0, 0)),
                route(origin.offset(3, 0, 0), origin.offset(4, 0, 0)), courier.saveWithoutId(new CompoundTag()), 2, ItemStack.EMPTY, level.getGameTime());
        var journey = request.journey; journey.presence = SettlementCourierJourney.Presence.PHYSICAL;
        courier.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id); courier.getPersistentData().putLong(SettlementCourierService.INCARNATION, 2);
        helper.assertTrue(level.addFreshEntity(courier), "current courier is added to ticking terrain");
        var runtime = new SettlementCourierService.Runtime();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(journey.step >= 2 || journey.leg != SettlementCourierJourney.Leg.APPROACH, "physical navigation reaches consecutive route steps: " + journey.presence + "/" + journey.blocker + "/" + courier.position() + "/" + courier.getNavigation().isDone() + "/" + level.isPositionEntityTicking(courier.blockPosition()) + "/" + courier.getPersistentData().getLong(SettlementCourierService.INCARNATION)))
                .thenWaitUntil(() -> helper.assertTrue(SettlementCourierService.nativeDurable(level, journey, courier, runtime, level.getGameTime()), "native entity checkpoint finishes without blocking"))
                .thenExecute(() -> {
                    helper.assertValueEqual(level.getEntity(courier.getUUID()), courier, "resident identity remains unchanged");
                    journey.obstructed = true; int before = journey.step;
                    journey.advance(level.getGameTime() + 10000);
                    helper.assertValueEqual(journey.step, before, "physical blockage cannot advance abstractly");
                    courier.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                }).thenSucceed();
    }
    @GameTest(template = "empty")
    public static void boatsKeepIdentityAcrossBackgroundLaunchLandingAndRestart(GameTestHelper helper) {
        var fixture = fixture(helper, false); var old = fixture.request.journey; var origin = old.crossing.steps().getFirst().position();
        var crossing = new DiplomaticRoute(List.of(new DiplomaticRoute.Step(origin, DiplomaticRoute.Mode.WALK),
                new DiplomaticRoute.Step(origin.offset(2, 0, 0), DiplomaticRoute.Mode.LAUNCH),
                new DiplomaticRoute.Step(origin.offset(3, 0, 0), DiplomaticRoute.Mode.SAIL),
                new DiplomaticRoute.Step(origin.offset(5, 0, 0), DiplomaticRoute.Mode.LAND)));
        var boat = new ItemStack(Items.BIRCH_BOAT); boat.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Village ferry"));
        var journey = new SettlementCourierJourney(old.id, old.resident, old.home, old.dimension, old.approach, crossing, old.residentContext, 1, boat, 0);
        journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.presence = SettlementCourierJourney.Presence.STORED;
        journey.advance(40);
        helper.assertValueEqual(journey.boatCustody, SettlementCourierJourney.BoatCustody.LAUNCHED, "real reserved boat launches on a water segment");
        var saved = journey.save(helper.getLevel().registryAccess()); var restored = SettlementCourierJourney.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.boatId, journey.boatId, "same boat identity survives restart");
        helper.assertTrue(ItemStack.matches(restored.boat, boat), "boat components are preserved");
        restored.advance(48); restored.advance(88);
        helper.assertValueEqual(restored.boatCustody, SettlementCourierJourney.BoatCustody.PACKED, "landing returns the same boat to custody");
        restored.boatCustody = SettlementCourierJourney.BoatCustody.LOST; restored.nextLeg(89);
        fixture.request.journey = restored;
        SettlementCourierService.step(helper.getLevel(), fixture.request, 100, new SettlementCourierService.Runtime());
        helper.assertValueEqual(restored.step, 0, "lost boats cannot be replaced by background travel");
        helper.assertValueEqual(fixture.request.blocker(), "boat_lost", "boat loss remains visible");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void courierDeathReleasesCargoOnlyAfterBothCheckpoints(GameTestHelper helper) {
        var fixture = fixture(helper, false); var request = fixture.request; var journey = request.journey; var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(1, 2, 1)); helper.setBlock(new BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.STONE);
        var courier = EntityType.VILLAGER.create(level); courier.load(journey.residentContext.copy());
        courier.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        request.offer.custody = SettlementFoodOffer.Custody.COURIER; journey.presence = SettlementCourierJourney.Presence.PHYSICAL;
        SettlementCourierRecovery.died(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(courier, level.damageSources().genericKill()));
        helper.assertValueEqual(journey.presence, SettlementCourierJourney.Presence.DEAD, "real death ends resident travel");
        helper.assertValueEqual(request.deliveredNutrition(), 0L, "dropped food earns no delivery credit");
        helper.assertValueEqual(request.incomingNutrition(), 0L, "lost cargo does not suppress future shortage requests");
        helper.assertValueEqual(journey.recovery.size(), 1, "one recoverable item identity per parcel");
        var drop = journey.recovery.getFirst();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(drop.state == SettlementCourierRecovery.State.RELEASED, "cargo reaches native storage and root receipt"))
                .thenWaitUntil(() -> {
                    var entity = level.getEntity(drop.id);
                    helper.assertTrue(entity instanceof net.minecraft.world.entity.item.ItemEntity item && !SettlementCourierRecovery.locked(item), "durable recovery becomes available for pickup");
                }).thenExecute(() -> {
                    var item = (net.minecraft.world.entity.item.ItemEntity) level.getEntity(drop.id);
                    helper.assertTrue(request.offer.empty(), "released cargo is removed from root custody");
                    helper.assertValueEqual(item.getItem().getCount(), 20, "all food survives the death handoff");
                    item.discard();
                    helper.assertValueEqual(drop.state, SettlementCourierRecovery.State.REMOVED, "consumed recovery identity is retained");
                    var stale = new net.minecraft.world.entity.item.ItemEntity(level, position.getX(), position.getY(), position.getZ(), drop.item.copy());
                    stale.setUUID(drop.id); stale.getPersistentData().putUUID(SettlementCourierRecovery.DROP, journey.id);
                    var event = new EntityJoinLevelEvent(stale, level); SettlementCourierRecovery.join(event);
                    helper.assertTrue(event.isCanceled(), "stale consumed cargo cannot reappear");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void representedInterferenceIsIdempotentAndNeverDeclaresWar(GameTestHelper helper) {
        var fixture = fixture(helper, false); var level = helper.getLevel(); var request = fixture.request;
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CourierGuard"));
        fixture.recipient.town().setPermissions(fixture.recipient.town().leader(), player.getUUID(), java.util.Set.of(SettlementPermission.DIPLOMACY));
        SettlementCourierInterference.record(player, request); SettlementCourierInterference.record(player, request);
        helper.assertValueEqual(request.journey.interference.size(), 1, "repeat interference records one incident per represented settlement");
        helper.startSequence().thenWaitUntil(() -> {
            SettlementCourierInterference.update(level, request);
            helper.assertTrue(request.journey.interference.values().stream().allMatch(incident -> incident.applied), "standing effect is durably acknowledged");
        }).thenExecute(() -> {
            var diplomacy = com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData.get(level);
            helper.assertValueEqual(diplomacy.standing(fixture.home.id(), fixture.recipient.town().id()), -3, "interference changes shared standing once");
            helper.assertTrue(com.jvn.villagerretaliation.diplomacy.VillageDiplomacyApi.query(level, fixture.home.id(), fixture.recipient.town().id()).relation()
                    != com.jvn.villagerretaliation.diplomacy.VillageRelation.HOSTILE, "standing does not activate combat");
            var second = SettlementAidGameTests.fixture(helper, 256).town();
            second.setPermissions(second.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.DIPLOMACY));
            request.journey.interference.clear(); SettlementCourierInterference.record(player, request);
            helper.assertTrue(request.journey.interference.isEmpty(), "ambiguous representation keeps consequences personal");
        }).thenSucceed();
    }
    @GameTest(template = "empty")
    public static void mapJourneysHideUnknownContactsAndRejectUnrelatedRoutes(GameTestHelper helper) {
        var fixture = fixture(helper, false); var level = helper.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CourierMap"));
        fixture.home.setPermissions(fixture.home.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var query = new com.jvn.villagerretaliation.network.SettlementCourierMapRequest(fixture.home.id().value(), fixture.home.id().value(), fixture.request.id(), 0, 0, 0, 0, 1);
        var concealed = SettlementCourierMapNetworking.snapshot(player, query);
        helper.assertTrue(concealed.couriers().isEmpty() && concealed.route().isEmpty(), "unknown counterpart exposes no shipment markers, count or route");
        com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService.receiveCorrespondence(level, fixture.recipient.town().id(), fixture.home.id());
        var shown = SettlementCourierMapNetworking.snapshot(player, query);
        helper.assertValueEqual(shown.couriers().size(), 1, "authorized contacts reveal the selected settlement journey");
        helper.assertFalse(shown.couriers().getFirst().observed(), "background position is explicitly estimated");
        helper.assertTrue(!shown.route().isEmpty() && shown.route().size() <= 256, "selected journey route is bounded");
        fixture.home.setPermissions(fixture.home.leader(), player.getUUID(), java.util.Set.of());
        var revoked = SettlementCourierMapNetworking.snapshot(player, query);
        helper.assertTrue(revoked.couriers().isEmpty() && revoked.route().isEmpty() && revoked.cells().isEmpty() && !revoked.terrainAllowed(), "revocation removes private overlays");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        com.jvn.villagerretaliation.network.SettlementCourierMapPayload.STREAM_CODEC.encode(buffer, shown);
        helper.assertValueEqual(com.jvn.villagerretaliation.network.SettlementCourierMapPayload.STREAM_CODEC.decode(buffer), shown, "bounded courier map packet round trips");
        buffer.release(); helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void acceptedOfferDispatchesItsExistingResidentAndDebitsARealBoat(GameTestHelper helper) {
        var fixture = fixture(helper, false); var request = fixture.request; var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        var resident = fixture.home.residents().get(request.offer.courier());
        data.aid().couriers.remove(resident.id()); request.journey = null;
        resident.courier(request.id(), 1, false); var context = resident.context(level);
        context.setHealth(14); context.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        resident.observe(context, fixture.home, level.getGameTime());
        for (int slot = 0; slot < fixture.donor.depot().getContainerSize(); slot++) fixture.donor.depot().setItem(slot, new ItemStack(Items.BREAD, 64));
        var boat = new ItemStack(Items.SPRUCE_BOAT); boat.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Aid ferry"));
        fixture.donor.depot().setItem(0, boat.copy());
        request.preparation = new SettlementAidPreparation(fixture.donor.depot().id(), fixture.recipient.depot().id(), fixture.donor.town().center(), fixture.recipient.town().center(), true,
                new SettlementAidPreparation.Candidate(resident.id(), resident.position()), null);
        var plan = request.preparation; var candidate = plan.delivery;
        candidate.approach = UUID.randomUUID(); candidate.access = fixture.home.center();
        com.jvn.villagerretaliation.diplomacy.route.AidRouteFixture.waterCrossing(level, plan.from, plan.to);
        com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService.prepareAccess(level, plan.crossing, plan.from, plan.to, true, 2, 2);
        request.state = SettlementFoodRequest.State.PREPARING; data.aid().changed(request); data.setDirty();
        helper.startSequence().thenWaitUntil(() -> {
            var crossing = com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService.ready(level, plan.crossing);
            helper.assertTrue(crossing != null && crossing.needsBoat(), "cached terrain produces a real boat crossing");
            candidate.access = crossing.steps().getFirst().position();
            com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService.prepare(level, candidate.approach, candidate.origin, candidate.access, false);
            helper.assertTrue(com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService.ready(level, candidate.approach) != null, "resident approach is prepared");
        }).thenExecute(() -> {
            SettlementCourierService.begin(level, request, level.getGameTime());
            helper.assertTrue(request.journey != null, "accepted offer borrows its selected resident");
            helper.assertValueEqual(request.journey.resident, resident.id(), "no transport resident is fabricated");
            helper.assertTrue(resident.courierAway(), "dispatch excludes ordinary work");
            helper.assertValueEqual(request.journey.residentContext.getFloat("Health"), 14.0F, "health is preserved at departure");
            helper.assertTrue(ItemStack.matches(request.journey.boat, boat), "real boat components enter shipment custody");
            helper.assertValueEqual(fixture.donor.depot().count(item -> item.getItem() == Items.SPRUCE_BOAT), 0, "departure debits exactly one real boat");
            SettlementCourierService.begin(level, request, level.getGameTime());
            helper.assertValueEqual(fixture.donor.depot().count(item -> item.getItem() == Items.SPRUCE_BOAT), 0, "repeated dispatch cannot debit another boat");
        }).thenWaitUntil(() -> helper.assertTrue(request.journey.presence != SettlementCourierJourney.Presence.CLAIMING, "durable resident claim advances into travel"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void physicalBoatLaunchSailingAndLandingKeepTheReservedBoat(GameTestHelper helper) {
        var fixture = fixture(helper, false); var request = fixture.request; var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 2));
        for (int dx = -1; dx <= 8; dx++) for (int dz = -2; dz <= 2; dz++) {
            level.setBlockAndUpdate(origin.offset(dx, -2, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(dx, -1, dz), (dx >= 1 && dx <= 5 ? net.minecraft.world.level.block.Blocks.WATER : net.minecraft.world.level.block.Blocks.STONE).defaultBlockState());
            level.setBlockAndUpdate(origin.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(dx, 1, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        var courier = EntityType.VILLAGER.create(level); courier.load(request.journey.residentContext.copy());
        courier.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        var crossing = new DiplomaticRoute(List.of(new DiplomaticRoute.Step(origin, DiplomaticRoute.Mode.WALK),
                new DiplomaticRoute.Step(origin.offset(2, 0, 0), DiplomaticRoute.Mode.LAUNCH),
                new DiplomaticRoute.Step(origin.offset(3, 0, 0), DiplomaticRoute.Mode.SAIL),
                new DiplomaticRoute.Step(origin.offset(4, 0, 0), DiplomaticRoute.Mode.SAIL),
                new DiplomaticRoute.Step(origin.offset(6, 0, 0), DiplomaticRoute.Mode.LAND)));
        var boatItem = new ItemStack(Items.SPRUCE_BOAT); boatItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("River courier"));
        var journey = new SettlementCourierJourney(request.id(), courier.getUUID(), fixture.home.id(), level.dimension().location(), route(origin, origin), crossing,
                courier.saveWithoutId(new CompoundTag()), 2, boatItem, level.getGameTime());
        request.journey = journey; journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.presence = SettlementCourierJourney.Presence.PHYSICAL;
        courier.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id); courier.getPersistentData().putLong(SettlementCourierService.INCARNATION, 2);
        var player = helper.makeMockServerPlayerInLevel(); player.moveTo(origin.getX(), origin.getY(), origin.getZ() + 4);
        helper.assertTrue(level.addFreshEntity(courier), "courier materializes at the prepared shore"); SettlementCourierService.changed(level, request);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED && courier.isPassenger(),
                "physical courier launches its reserved boat: " + journey.blocker))
                .thenExecute(() -> {
                    var boat = (net.minecraft.world.entity.vehicle.Boat) courier.getVehicle();
                    helper.assertValueEqual(boat.getUUID(), journey.boatId, "launched boat keeps its stable identity");
                    helper.assertValueEqual(boat.getVariant(), net.minecraft.world.entity.vehicle.Boat.Type.SPRUCE, "boat variant comes from the real item");
                    helper.assertValueEqual(boat.getCustomName().getString(), "River courier", "boat item name survives launch");
                }).thenWaitUntil(() -> helper.assertTrue(journey.step == 4 && journey.boatCustody == SettlementCourierJourney.BoatCustody.PACKED,
                        "physical boat reaches land and is recovered: " + journey.step + "/" + journey.blocker + "/" + courier.position()))
                .thenExecute(() -> {
                    helper.assertFalse(courier.isPassenger(), "courier walks ashore");
                    helper.assertTrue(level.getEntity(journey.boatId) == null, "recovered boat has one owner in shipment custody");
                    helper.assertTrue(ItemStack.matches(journey.boat, boatItem), "recovery preserves the original boat item");
                    courier.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void repeatedUnloadingRematerializesOneResidentWithHealthAndEquipment(GameTestHelper helper) {
        var fixture = fixture(helper, false); var request = fixture.request; var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int dx = -1; dx < 7; dx++) for (int dz = -1; dz < 4; dz++) level.setBlockAndUpdate(origin.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var courier = EntityType.VILLAGER.create(level); courier.load(request.journey.residentContext.copy());
        courier.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5); courier.setHealth(14);
        courier.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        var journey = new SettlementCourierJourney(request.id(), courier.getUUID(), fixture.home.id(), level.dimension().location(), route(origin, origin),
                route(origin, origin.offset(80, 0, 0)), courier.saveWithoutId(new CompoundTag()), 2, ItemStack.EMPTY, level.getGameTime());
        request.journey = journey; journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.presence = SettlementCourierJourney.Presence.PHYSICAL;
        courier.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id); courier.getPersistentData().putLong(SettlementCourierService.INCARNATION, 2);
        var player = helper.makeMockServerPlayerInLevel(); player.moveTo(origin.getX(), origin.getY(), origin.getZ() + 4);
        helper.assertTrue(level.addFreshEntity(courier), "original resident enters physical travel"); SettlementCourierService.changed(level, request);
        var sequence = helper.startSequence(); long[] incarnation = {journey.incarnation};
        for (int cycle = 0; cycle < 3; cycle++) {
            sequence.thenExecute(() -> {
                var physical = (net.minecraft.world.entity.npc.Villager) level.getEntity(journey.resident);
                helper.assertTrue(physical != null, "resident is present before unloading");
                physical.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                helper.assertTrue(journey.incarnation > incarnation[0], "unloading retires the native incarnation"); incarnation[0] = journey.incarnation;
            }).thenWaitUntil(() -> {
                var current = level.getEntity(journey.resident);
                helper.assertTrue(current instanceof net.minecraft.world.entity.npc.Villager && journey.presence == SettlementCourierJourney.Presence.PHYSICAL,
                        "same resident rematerializes after durable ownership: " + journey.presence + "/" + journey.blocker);
            }).thenExecute(() -> {
                var current = (net.minecraft.world.entity.npc.Villager) level.getEntity(journey.resident);
                helper.assertValueEqual(current.getHealth(), 14.0F, "transition preserves health");
                helper.assertTrue(current.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET), "transition preserves equipment");
                helper.assertValueEqual(current.getPersistentData().getLong(SettlementCourierService.INCARNATION), journey.incarnation, "physical incarnation matches durable ownership");
                var stale = EntityType.VILLAGER.create(level); stale.load(journey.residentContext.copy());
                stale.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id);
                stale.getPersistentData().putLong(SettlementCourierService.INCARNATION, journey.incarnation - 1);
                var event = new EntityJoinLevelEvent(stale, level); SettlementCourierService.join(event);
                helper.assertTrue(event.isCanceled(), "stale entity is rejected after each transition");
            });
        }
        sequence.thenSucceed();
    }

    static DiplomaticRoute route(BlockPos from, BlockPos to) {
        if (from.getY() != to.getY()) throw new IllegalArgumentException("Courier fixture requires level endpoints");
        var steps = new ArrayList<DiplomaticRoute.Step>(); var position = from;
        steps.add(new DiplomaticRoute.Step(position, DiplomaticRoute.Mode.WALK));
        while (!position.equals(to)) {
            position = position.getX() != to.getX() ? position.offset(Integer.signum(to.getX() - position.getX()), 0, 0)
                    : position.offset(0, 0, Integer.signum(to.getZ() - position.getZ()));
            steps.add(new DiplomaticRoute.Step(position, DiplomaticRoute.Mode.WALK));
        }
        return new DiplomaticRoute(steps);
    }
    record Fixture(SettlementAidGameTests.Fixture donor, SettlementAidGameTests.Fixture recipient, SettlementRecord home, SettlementFoodRequest request) { }
    static Fixture fixture(GameTestHelper helper, boolean collection) {
        var donor = SettlementAidGameTests.fixture(helper, collection ? 512 : 0);
        var recipient = SettlementAidGameTests.fixture(helper, collection ? 640 : 128);
        var home = collection ? recipient.town() : donor.town(); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        var villager = EntityType.VILLAGER.create(level); villager.setUUID(UUID.randomUUID());
        var origin = home.center().offset(-2, 0, 0); villager.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        VillageAllegianceApi.assignKnown(level, villager, home.id(), AllegianceAssignmentSource.EXPLICIT_API);
        home.recordResident(SettlementResidentState.capture(villager, home, level.getGameTime()));
        long now = level.getServer().overworld().getGameTime();
        var request = new SettlementFoodRequest(UUID.randomUUID(), recipient.town().id(), donor.town().id(), 100, now, now + 20, now + 24000, recipient.town().leader());
        request.donorAuthority = donor.town().leader(); request.maximumItems = 20;
        request.offer = new SettlementFoodOffer(collection ? SettlementFoodOffer.Arrangement.REQUESTER_COLLECTION : SettlementFoodOffer.Arrangement.DONOR_DELIVERY,
                villager.getUUID(), List.of(new SettlementFoodOffer.Parcel(donor.depot().id(), new ItemStack(Items.BREAD, 20))));
        request.preparation = new SettlementAidPreparation(donor.depot().id(), recipient.depot().id(), donor.town().center(), recipient.town().center(), false,
                collection ? null : new SettlementAidPreparation.Candidate(villager.getUUID(), origin), collection ? new SettlementAidPreparation.Candidate(villager.getUUID(), origin) : null);
        request.state = SettlementFoodRequest.State.TRAVELLING; data.aid().requests.put(request.id(), request);
        var crossing = route(home.center(), collection ? donor.town().center() : recipient.town().center());
        com.jvn.villagerretaliation.diplomacy.route.AidRouteFixture.flat(level, origin, crossing.steps().getLast().position());
        var journey = new SettlementCourierJourney(request.id(), villager.getUUID(), home.id(), level.dimension().location(), route(origin, home.center()), crossing, villager.saveWithoutId(new CompoundTag()), 1, ItemStack.EMPTY, now);
        journey.presence = SettlementCourierJourney.Presence.STORED; data.aid().journey(request, journey);
        home.residents().get(villager.getUUID()).courier(request.id(), 1, true); data.setDirty();
        return new Fixture(donor, recipient, home, request);
    }
}
