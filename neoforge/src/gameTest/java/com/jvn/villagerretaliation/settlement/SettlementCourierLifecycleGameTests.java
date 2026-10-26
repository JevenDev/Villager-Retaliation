package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.diplomacy.route.AidRouteFixture;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_couriers")
@PrefixGameTestTemplate(false)
public final class SettlementCourierLifecycleGameTests {
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void cachedRouteRepairKeepsCustodyAndRejectsInvalidDetours(GameTestHelper helper) {
        var fixture = SettlementCourierGameTests.fixture(helper, false); var request = fixture.request();
        var level = helper.getLevel(); var journey = request.journey;
        journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.step = 10;
        journey.position = journey.route().steps().get(10).position(); journey.obstructed = true;
        request.offer.custody = SettlementFoodOffer.Custody.COURIER;
        var endpoint = journey.route().steps().getLast().position();
        AidRouteFixture.flat(level, journey.position, endpoint);
        helper.assertTrue(SettlementCourierService.retry(level, request), "blocked courier can prepare a bounded replacement route");
        var pending = SettlementFoodRequest.load(request.save(level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(pending.journey.repair, journey.repair, "preparation identity survives save loading");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(journey.repair == null, "cached repair completes through the shared queue"))
                .thenExecute(() -> {
                    helper.assertTrue(journey.detour != null && !journey.obstructed, "repair installs a validated detour");
                    helper.assertValueEqual(journey.detour.steps().getLast().position(), endpoint, "repair preserves the original destination");
                    helper.assertValueEqual(request.offer.remaining().getFirst().item().getCount(), 20, "repair never releases food custody");
                    var saved = request.save(level.registryAccess());
                    var restored = SettlementFoodRequest.load(saved, level.registryAccess());
                    helper.assertValueEqual(restored.journey.route(), journey.route(), "detour survives restart");
                    saved.getCompound("Journey").put("Detour", SettlementCourierGameTests.route(journey.position, journey.position.offset(2, 0, 0)).save());
                    boolean rejected = false;
                    try { SettlementFoodRequest.load(saved, level.registryAccess()); } catch (IllegalArgumentException expected) { rejected = true; }
                    helper.assertTrue(rejected, "malformed detour cannot silently change the cargo destination");
                    helper.assertValueEqual(DiplomaticRouteService.metrics(level.getServer()).tickets(), 0, "cached repair holds no generation tickets");
                    journey.obstructed = true;
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void saveEveryHandoffAndLeadershipChangesConserveFood(GameTestHelper helper) {
        for (boolean collection : new boolean[]{false, true}) {
            var fixture = SettlementCourierGameTests.fixture(helper, collection); var request = fixture.request();
            var level = helper.getLevel(); var data = SettlementSavedData.get(level); var runtime = new SettlementCourierService.Runtime();
            UUID resident = request.journey.resident; var previous = request.journey.leg;
            int handoffs = 0;
            for (long now = request.journey.lastTick + 8, end = now + 4000; now < end && request.state() != SettlementFoodRequest.State.COMPLETED; now += 8) {
                data.aid().durableRevision = data.aid().revision();
                SettlementCourierService.step(level, request, now, runtime);
                if (request.journey.leg != previous) {
                    previous = request.journey.leg; handoffs++;
                    for (var town : List.of(fixture.donor().town(), fixture.recipient().town())) {
                        var successor = UUID.randomUUID(); var offer = town.offerLeadership(town.leader(), successor, now);
                        helper.assertTrue(town.acceptLeadership(successor, offer.id(), now), "leadership changes while the resident is away");
                    }
                }
                request = SettlementFoodRequest.load(request.save(level.registryAccess()), level.registryAccess());
                data.aid().requests.put(request.id(), request);
            }
            helper.assertValueEqual(handoffs, 4, "every route handoff completes after reload");
            helper.assertValueEqual(request.state(), SettlementFoodRequest.State.COMPLETED, "authorized shipment survives later leadership changes");
            helper.assertValueEqual(SettlementAidStock.nutrition(fixture.recipient().town()), 100L, "handoff replay delivers food exactly once");
            helper.assertValueEqual(request.deliveredNutrition(), 100L, "credit matches the single delivery");
            helper.assertValueEqual(fixture.home().residents().size(), 1, "same home resident survives every checkpoint");
            helper.assertFalse(fixture.home().residents().get(resident).courierAway(), "returned resident is released from travel");
            helper.assertTrue(data.aid().courierAvailable(resident), "unloaded resident can carry another shipment after returning");
            var replacement = new SettlementFoodRequest(UUID.randomUUID(), request.requester(), request.donor(), 100,
                    level.getGameTime(), level.getGameTime() + 20, level.getGameTime() + 24000, fixture.recipient().town().leader());
            var nextJourney = new SettlementCourierJourney(replacement.id(), resident, request.journey.home, request.journey.dimension,
                    request.journey.approach, request.journey.crossing, request.journey.residentContext, request.journey.incarnation + 1, ItemStack.EMPTY, level.getGameTime());
            data.aid().requests.put(replacement.id(), replacement); data.aid().journey(replacement, nextJourney);
            helper.assertTrue(request.journey.released(), "reassignment retires the previous completed identity");
            helper.assertValueEqual(data.aid().courier(resident), replacement, "one active journey owns the reused resident");
            data.aid().couriers.remove(resident); data.aid().requests.remove(replacement.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectorDeathBeforePickupRefundsWithoutCreatingCargo(GameTestHelper helper) {
        var fixture = SettlementCourierGameTests.fixture(helper, true); var request = fixture.request(); var level = helper.getLevel();
        var courier = EntityType.VILLAGER.create(level); courier.load(request.journey.residentContext.copy());
        SettlementCourierRecovery.died(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(courier, level.damageSources().genericKill()));
        helper.assertTrue(request.journey.recovery.isEmpty(), "uncollected food never drops at the collecting resident");
        SettlementCourierRecovery.update(level, request, level.getGameTime(), new SettlementCourierService.Runtime());
        helper.assertValueEqual(SettlementAidStock.nutrition(fixture.donor().town()), 100L, "reservation returns to the original donor depot");
        helper.assertTrue(request.offer.empty(), "refund removes reserved custody");
        helper.assertValueEqual(request.deliveredNutrition(), 0L, "refund grants no delivery credit");
        var restored = SettlementFoodRequest.load(request.save(level.registryAccess()), level.registryAccess());
        SettlementCourierRecovery.update(level, restored, level.getGameTime(), new SettlementCourierService.Runtime());
        helper.assertValueEqual(SettlementAidStock.nutrition(fixture.donor().town()), 100L, "refund replay cannot duplicate food");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownedCourierCannotBeHiredOrOrderedToFollow(GameTestHelper helper) {
        var fixture = SettlementCourierGameTests.fixture(helper, false); var level = helper.getLevel();
        var courier = EntityType.VILLAGER.create(level); courier.load(fixture.request().journey.residentContext.copy());
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CourierHire"));
        var hire = com.jvn.villagerretaliation.interaction.RecruitmentPolicy.mayAcceptPaidContract(level, courier, player);
        helper.assertValueEqual(hire.reason(), com.jvn.villagerretaliation.interaction.RecruitmentPolicy.DenialReason.SETTLEMENT_DUTY,
                "paid hiring cannot steal an active courier");
        var command = com.jvn.villagerretaliation.interaction.RecruitmentPolicy.mayCommand(level, courier, player,
                com.jvn.villagerretaliation.interaction.VillagerAssignmentCommand.FOLLOW);
        helper.assertValueEqual(command.reason(), com.jvn.villagerretaliation.interaction.RecruitmentPolicy.DenialReason.SETTLEMENT_DUTY,
                "personal commands cannot override the journey");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stolenAndDestroyedBoatsRemainLostWithoutReplacement(GameTestHelper helper) {
        var fixture = SettlementCourierGameTests.fixture(helper, false); var level = helper.getLevel(); var request = fixture.request();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1)); var old = request.journey;
        var route = new DiplomaticRoute(List.of(new DiplomaticRoute.Step(origin, DiplomaticRoute.Mode.WALK),
                new DiplomaticRoute.Step(origin.offset(2, 0, 0), DiplomaticRoute.Mode.LAUNCH),
                new DiplomaticRoute.Step(origin.offset(3, 0, 0), DiplomaticRoute.Mode.SAIL),
                new DiplomaticRoute.Step(origin.offset(5, 0, 0), DiplomaticRoute.Mode.LAND)));
        var journey = new SettlementCourierJourney(old.id, old.resident, old.home, old.dimension, SettlementCourierGameTests.route(origin, origin),
                route, old.residentContext, 2, new ItemStack(Items.OAK_BOAT), level.getGameTime());
        request.journey = journey; journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.step = 1; journey.position = origin.offset(2, 0, 0);
        journey.presence = SettlementCourierJourney.Presence.PHYSICAL; journey.boatCustody = SettlementCourierJourney.BoatCustody.LAUNCHED; journey.boatIncarnation = 1;
        var courier = EntityType.VILLAGER.create(level); courier.load(journey.residentContext.copy());
        courier.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id); courier.getPersistentData().putLong(SettlementCourierService.INCARNATION, 2);
        courier.moveTo(origin.getX(), origin.getY(), origin.getZ()); helper.assertTrue(level.addFreshEntity(courier), "resident enters the encounter");
        var boat = SettlementCourierBoat.create(level, journey, net.minecraft.world.phys.Vec3.atBottomCenterOf(journey.position));
        helper.assertTrue(level.addFreshEntity(boat), "reserved boat enters the encounter");
        helper.assertTrue(courier.startRiding(boat, true), "courier boards its reserved boat");
        var player = helper.makeMockServerPlayerInLevel(); player.moveTo(origin.getX(), origin.getY(), origin.getZ());
        fixture.recipient().town().setPermissions(fixture.recipient().town().leader(), player.getUUID(), java.util.Set.of(SettlementPermission.DIPLOMACY));
        helper.assertTrue(player.startRiding(boat, true), "represented player takes the courier boat");
        SettlementCourierBoat.navigate(level, request, courier, route.steps().get(2), level.getGameTime());
        helper.assertValueEqual(journey.boatCustody, SettlementCourierJourney.BoatCustody.LOST, "taken boat leaves shipment custody");
        helper.assertValueEqual(journey.interference.size(), 1, "identified representative creates a diplomatic incident");
        player.stopRiding();
        SettlementCourierBoat.navigate(level, request, courier, route.steps().get(2), level.getGameTime());
        helper.assertValueEqual(journey.boatCustody, SettlementCourierJourney.BoatCustody.LAUNCHED, "recovering the same nearby boat resumes travel");
        helper.assertValueEqual(courier.getVehicle().getUUID(), journey.boatId, "recovery keeps the original boat identity");
        var outsider = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "BoatAttacker"));
        int reputation = com.jvn.villagerretaliation.reputation.VillagerReputationManager.getReputation(level, courier, outsider.getUUID());
        boat.hurt(level.damageSources().playerAttack(outsider), 5);
        helper.assertTrue(com.jvn.villagerretaliation.reputation.VillagerReputationManager.getReputation(level, courier, outsider.getUUID()) < reputation,
                "unrepresented boat damage has personal consequences");
        helper.assertValueEqual(journey.interference.size(), 1, "unrepresented damage does not invent a settlement incident");
        helper.assertTrue(boat.isRemoved(), "actual boat destruction follows normal world behavior");
        helper.assertTrue(fixture.home().depots().values().stream().allMatch(depot -> depot.count(item -> item.is(Items.OAK_BOAT)) == 0),
                "destruction never replaces a boat in village stock");
        courier.stopRiding(); player.stopRiding(); courier.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.succeed();
    }
}
