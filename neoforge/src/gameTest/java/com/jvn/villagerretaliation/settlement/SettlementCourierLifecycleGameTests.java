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
    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void unloadingAfloatPreservesBoatAndResidentIncarnations(GameTestHelper helper) {
        var fixture = SettlementCourierGameTests.fixture(helper, false); var request = fixture.request(); var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        for (int x = -2; x <= 8; x++) for (int z = -2; z <= 3; z++) {
            level.setBlockAndUpdate(origin.offset(x, -2, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(x, -1, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(x, 0, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(x, 1, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        var courier = EntityType.VILLAGER.create(level); courier.load(request.journey.residentContext.copy());
        courier.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        var crossing = new DiplomaticRoute(List.of(new DiplomaticRoute.Step(origin.west(2), DiplomaticRoute.Mode.WALK),
                new DiplomaticRoute.Step(origin, DiplomaticRoute.Mode.LAUNCH), new DiplomaticRoute.Step(origin.east(), DiplomaticRoute.Mode.SAIL),
                new DiplomaticRoute.Step(origin.east(2), DiplomaticRoute.Mode.SAIL), new DiplomaticRoute.Step(origin.east(3), DiplomaticRoute.Mode.SAIL),
                new DiplomaticRoute.Step(origin.east(4), DiplomaticRoute.Mode.SAIL), new DiplomaticRoute.Step(origin.east(5), DiplomaticRoute.Mode.SAIL),
                new DiplomaticRoute.Step(origin.east(6), DiplomaticRoute.Mode.SAIL),
                new DiplomaticRoute.Step(origin.east(8), DiplomaticRoute.Mode.LAND)));
        var journey = new SettlementCourierJourney(request.id(), courier.getUUID(), fixture.home().id(), level.dimension().location(),
                SettlementCourierGameTests.route(origin.west(2), origin.west(2)), crossing, courier.saveWithoutId(new CompoundTag()), 2, new ItemStack(Items.BIRCH_BOAT), level.getGameTime());
        request.journey = journey; journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.step = 1; journey.position = origin;
        journey.presence = SettlementCourierJourney.Presence.PHYSICAL; journey.boatCustody = SettlementCourierJourney.BoatCustody.LAUNCHED; journey.boatIncarnation = 1;
        courier.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id); courier.getPersistentData().putLong(SettlementCourierService.INCARNATION, 2);
        helper.assertTrue(level.addFreshEntity(courier), "existing resident enters its real boat");
        var boat = SettlementCourierBoat.create(level, journey, net.minecraft.world.phys.Vec3.atBottomCenterOf(origin));
        helper.assertTrue(level.addFreshEntity(boat) && courier.startRiding(boat, true), "reserved boat holds the courier");
        var player = helper.makeMockServerPlayerInLevel(); player.moveTo(origin.getX(), origin.getY() + 2, origin.getZ() + 4);
        SettlementCourierService.capture(journey, courier); SettlementCourierService.changed(level, request);
        var sequence = helper.startSequence();
        for (int cycle = 1; cycle <= 3; cycle++) {
            final int seal = cycle;
            sequence.thenExecute(() -> {
                var resident = (net.minecraft.world.entity.npc.Villager) level.getEntity(journey.resident);
                var vessel = (net.minecraft.world.entity.vehicle.Boat) resident.getVehicle();
                vessel.getPersistentData().putInt("TransitSeal", seal);
                vessel.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                resident.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            }).thenWaitUntil(() -> helper.assertTrue(level.getEntity(journey.resident) instanceof net.minecraft.world.entity.npc.Villager resident
                    && resident.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat && journey.presence == SettlementCourierJourney.Presence.PHYSICAL,
                    "resident and boat rematerialize together: " + journey.presence + "/" + journey.blocker))
                    .thenExecute(() -> {
                        var resident = (net.minecraft.world.entity.npc.Villager) level.getEntity(journey.resident);
                        var vessel = (net.minecraft.world.entity.vehicle.Boat) resident.getVehicle();
                        helper.assertValueEqual(vessel.getUUID(), journey.boatId, "boat identity is conserved across unloading");
                        helper.assertValueEqual(vessel.getPersistentData().getInt("TransitSeal"), seal, "boat state at unloading survives rematerialization");
                        helper.assertValueEqual(vessel.getPersistentData().getLong(SettlementCourierService.INCARNATION), journey.boatIncarnation, "native boat matches current incarnation");
                        helper.assertValueEqual(resident.getPersistentData().getLong(SettlementCourierService.INCARNATION), journey.incarnation, "native resident matches current incarnation");
                    });
        }
        sequence.thenSucceed();
    }

    @GameTest(template = "empty")
    public static void checkpointSnapshotsKeepAssetsAndCustodyConsistentAfterMutation(GameTestHelper helper) {
        var fixture = SettlementCourierGameTests.fixture(helper, false); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        var snapshots = new SettlementSnapshotCache();
        var origin = SettlementTerrainSection.sectionOrigin(helper.absolutePos(new BlockPos(1, 2, 1)));
        fixture.home().recordNavigation(SettlementNavigationSection.capture(origin, level.getChunkAt(origin)));
        fixture.home().changed();
        fixture.donor().depot().setItem(0, new ItemStack(Items.BREAD, 20));
        var component = new CompoundTag(); component.putInt("Seal", 1);
        fixture.donor().depot().getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(component));
        var workerTag = new CompoundTag(); workerTag.putUUID("Id", fixture.request().journey.resident); workerTag.putUUID("Area", UUID.randomUUID());
        workerTag.putString("Role", com.jvn.villagerretaliation.interaction.HiredVillagerRole.FARMING.name()); workerTag.put("Settings", component.copy()); workerTag.put("Inventory", component.copy());
        workerTag.put("EntityContext", fixture.home().residents().get(fixture.request().journey.resident).save().getCompound("EntityContext").copy());
        var worker = SettlementWorkerState.load(workerTag); fixture.home().recordWorker(worker);
        var first = snapshots.capture(data, level.registryAccess()); var frozen = first.copy();
        level.setBlockAndUpdate(origin, net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState());
        fixture.home().recordNavigation(SettlementNavigationSection.capture(origin, level.getChunkAt(origin)));
        fixture.donor().depot().removeItem(0, 5); fixture.recipient().depot().setItem(0, new ItemStack(Items.BREAD, 5));
        fixture.home().residents().get(fixture.request().journey.resident).courier(fixture.request().id(), 2, true); fixture.home().changed();
        fixture.request().journey.incarnation = 2; SettlementCourierService.changed(level, fixture.request());
        worker.settings().putInt("Seal", 2); worker.inventorySnapshot(worker.settings());
        fixture.donor().depot().getItem(0).get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).getUnsafe().putInt("Seal", 2);
        var second = snapshots.capture(data, level.registryAccess());
        helper.assertValueEqual(first, frozen, "queued checkpoint remains immutable after live asset and resident changes");
        helper.assertValueEqual(second, data.save(new CompoundTag(), level.registryAccess()), "checkpoint includes current depots, resident ownership and aid custody together");
        var secondFrozen = second.copy();
        long revision = fixture.home().revision();
        fixture.home().residents().get(fixture.request().journey.resident).observeShortage(1200, true);
        helper.assertValueEqual(fixture.home().revision(), revision, "care fields can change between town revision updates");
        var third = snapshots.capture(data, level.registryAccess());
        helper.assertValueEqual(third, data.save(new CompoundTag(), level.registryAccess()), "every mutable care field is refreshed without a town revision change");
        fixture.donor().depot().removeItem(0, 5);
        snapshots.capture(data, level.registryAccess());
        helper.assertValueEqual(second, secondFrozen, "a later checkpoint cannot mutate an earlier queued save");
        var resident = fixture.home().residents().get(fixture.request().journey.resident);
        var residentBefore = resident.save(); var workerBefore = worker.save();
        for (var raw : third.getList("Settlements", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var town = (CompoundTag) raw;
            if (!town.getUUID("Id").equals(fixture.home().id().value())) continue;
            var savedWorker = town.getList("Workers", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            for (var key : List.of("Settings", "Inventory")) savedWorker.getCompound(key).putInt("Seal", 3);
        }
        resident.save().getCompound("EntityContext").putInt("Seal", 3);
        worker.save().getCompound("EntityContext").putInt("Seal", 3);
        helper.assertValueEqual(resident.save(), residentBefore, "public resident saves cannot mutate live or queued contexts");
        helper.assertValueEqual(worker.save(), workerBefore, "worker checkpoint tags have no mutable reference into live state");
        var context = resident.context(level); context.getPersistentData().putInt("Seal", 4);
        var helmet = new ItemStack(Items.IRON_HELMET);
        helmet.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(component));
        context.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
        resident.rememberFamily(context); worker.rememberPayments(context);
        var fourth = snapshots.capture(data, level.registryAccess()); var fourthFrozen = fourth.copy();
        helper.assertValueEqual(fourth, data.save(new CompoundTag(), level.registryAccess()), "replaced contexts are refreshed together with current care state");
        helper.assertValueEqual(second, secondFrozen, "replacing an entity context preserves earlier queued snapshots");
        helmet.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).getUnsafe().putInt("Seal", 5);
        context.getPersistentData().putInt("Seal", 5);
        helper.assertValueEqual(snapshots.capture(data, level.registryAccess()), fourthFrozen, "later native component mutations cannot alter captured contexts");
        resident.rememberFamily(context); worker.rememberPayments(context);
        helper.assertFalse(snapshots.capture(data, level.registryAccess()).equals(fourthFrozen), "a new native observation replaces the frozen context");
        helper.assertValueEqual(fourth, fourthFrozen, "new observations never edit an earlier I/O snapshot");
        fixture.request().journey.obstructed = true; helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void periodicSaveFreezesAssetsAndKeepsLaterChangesDirty(GameTestHelper helper) {
        var fixture = SettlementCourierGameTests.fixture(helper, false); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        fixture.request().journey.obstructed = true;
        var origin = SettlementTerrainSection.sectionOrigin(helper.absolutePos(new BlockPos(1, 2, 1)));
        fixture.home().recordNavigation(SettlementNavigationSection.capture(origin, level.getChunkAt(origin)));
        fixture.donor().depot().setItem(0, new ItemStack(Items.BREAD, 20)); data.setDirty();
        var expected = data.save(new CompoundTag(), level.registryAccess()).copy();
        var path = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("settlement_snapshot_" + UUID.randomUUID() + ".dat");
        data.save(path.toFile(), level.registryAccess());
        helper.assertFalse(data.isDirty(), "queued periodic save acknowledges only the captured state");
        fixture.donor().depot().removeItem(0, 5); fixture.home().residents().get(fixture.request().journey.resident).observeShortage(1200, true);
        fixture.home().changed();
        var saved = new java.util.concurrent.CompletableFuture<CompoundTag>();
        net.neoforged.neoforge.common.IOUtilities.withIOWorker(() -> {
            try { saved.complete(net.minecraft.nbt.NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap())); }
            catch (java.io.IOException failure) { saved.completeExceptionally(failure); }
        });
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(saved.isDone(), "periodic save finishes on the ordered I/O queue"))
                .thenExecute(() -> {
                    helper.assertValueEqual(saved.join().getCompound("data"), expected, "queued periodic save preserves its complete settlement and custody snapshot");
                    helper.assertTrue(data.isDirty(), "changes after capture still need a later save");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void navigationBudgetServesEveryCourierWhenThePhysicalCapIsRaised(GameTestHelper helper) {
        var fixture = SettlementCourierGameTests.fixture(helper, false); var level = helper.getLevel();
        var saved = fixture.request().journey.save(level.registryAccess());
        var journeys = new java.util.ArrayList<SettlementCourierJourney>();
        for (int index = 0; index < 32; index++) journeys.add(SettlementCourierJourney.load(saved, level.registryAccess()));
        var runtime = SettlementCourierService.runtime(level.getServer()); runtime.navigationTick = Long.MIN_VALUE;
        int[] served = new int[journeys.size()];
        for (long tick = 0; tick < 64; tick++) {
            int start = runtime.nextCourierStart(journeys.size()), submissions = 0;
            for (int offset = 0; offset < journeys.size(); offset++) {
                int index = (start + offset) % journeys.size();
                if (SettlementCourierService.navigationDue(level, journeys.get(index), tick)) { served[index]++; submissions++; }
            }
            helper.assertValueEqual(submissions, 1, "global navigation budget permits one submission each tick");
        }
        for (int count : served) helper.assertValueEqual(count, 2, "every courier receives a fair navigation turn");
        runtime.navigationTick = Long.MIN_VALUE;
        fixture.request().journey.obstructed = true; helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void dissolutionKeepsAidAssetsAndMergedReceiptsGrantNoSelfGoodwill(GameTestHelper helper) {
        var fixture = SettlementCourierGameTests.fixture(helper, false); var request = fixture.request(); var level = helper.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(fixture.home().leader(), "AidLifecycle"));
        var preview = SettlementLifecycleService.preview(player.createCommandSourceStack().withPermission(0), fixture.home().id(),
                SettlementLifecycleService.Operation.DISSOLVE, null, null);
        helper.assertTrue(preview.token() == null && preview.blockers().stream().anyMatch(message -> message.contains("food aid")),
                "outstanding shipment custody explicitly blocks dissolution");
        var data = SettlementSavedData.get(level); request.journey.obstructed = true;
        request.offer.replaceRemaining(List.of()); request.offer.custody = SettlementFoodOffer.Custody.DELIVERED;
        request.deliveredNutrition = 100; data.aid().receipt(request, 3, level.getGameTime()); data.setDirty();
        helper.assertTrue(com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level)
                .mergeSettlements(level, request.donor(), request.requester()), "registry reconciles the merged pair before goodwill acknowledgement");
        helper.startSequence().thenWaitUntil(() -> {
            SettlementAidService.receipt(level);
            helper.assertTrue(data.aid().receipts.values().stream().noneMatch(receipt -> receipt.request().equals(request.id())), "merged self receipt is durably acknowledged");
        }).thenExecute(() -> helper.assertValueEqual(com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData.get(level)
                .standing(request.requester(), request.requester()), 0, "internal delivery cannot earn diplomatic standing with itself")).thenSucceed();
    }

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
