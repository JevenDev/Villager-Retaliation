package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_aid")
@PrefixGameTestTemplate(false)
public final class SettlementAidGameTests {
    @GameTest(template = "empty")
    public static void aidSaveMigrationAndMalformedCargoArePreserved(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var old = new CompoundTag(); old.putInt("FormatVersion", 74); old.put("Settlements", new ListTag());
        var migrated = SettlementSavedData.load(old, provider);
        helper.assertFalse(migrated.readOnly(), "old settlement save migrates without resetting settlements");
        var request = new SettlementFoodRequest(UUID.randomUUID(), VillageAllegianceId.random(), VillageAllegianceId.random(), 100, 0, 10, 1000, UUID.randomUUID());
        request.offer = new SettlementFoodOffer(SettlementFoodOffer.Arrangement.REQUESTER_COLLECTION, UUID.randomUUID(),
                List.of(new SettlementFoodOffer.Parcel(UUID.randomUUID(), new ItemStack(Items.BREAD, 20))));
        request.state = SettlementFoodRequest.State.OFFER_SENT;
        migrated.aid().requests.put(request.id(), request); migrated.aid().changed(request);
        var saved = migrated.save(new CompoundTag(), provider);
        helper.assertValueEqual(SettlementSavedData.load(saved, provider).save(new CompoundTag(), provider), saved, "requests and reserved food survive restart");
        var policy = new SettlementAidPolicy(true, UUID.randomUUID(), 5, 64, 20, 96000);
        helper.assertValueEqual(SettlementAidPolicy.load(policy.save()), policy, "automatic aid limits round trip");
        var missing = saved.copy(); missing.remove("FoodAid");
        helper.assertTrue(SettlementSavedData.load(missing, provider).readOnly(), "new format cannot silently drop food custody");
        var corrupted = saved.copy();
        corrupted.getCompound("FoodAid").getList("Requests", 10).getCompound(0).getCompound("Offer").putLong("Nutrition", 101);
        var recovered = SettlementSavedData.load(corrupted, provider);
        helper.assertTrue(recovered.readOnly(), "mismatched food quantities suspend settlement writes");
        helper.assertValueEqual(recovered.save(new CompoundTag(), provider), corrupted, "malformed root remains intact");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void reservationProtectsReservesAndRefundConservesFood(GameTestHelper helper) {
        var donor = fixture(helper, 0); var recipient = fixture(helper, 128);
        var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        VillageAllegianceRegistrySavedData.get(level).addOrUpdateResident(donor.town.id(), UUID.randomUUID(), true, level.getGameTime());
        fillBread(donor.depot);
        var request = request(helper, donor.town, recipient.town);
        long initial = SettlementAidStock.nutrition(donor.town);
        long captured = donor.town.revision();
        SettlementDemandService.Snapshot[] snapshot = {null};
        var work = SettlementDemandService.planAid(level, donor.town, value -> snapshot[0] = value);
        helper.startSequence().thenWaitUntil(() -> { work.run(); helper.assertTrue(snapshot[0] != null, "food allocation snapshot finishes"); })
                .thenExecute(() -> {
                    var policy = data.aid().policy(donor.town);
                    helper.assertFalse(policy.automatic(), "player-led settlements require manual outgoing approval by default");
                    var quote = SettlementAidStock.quote(level, donor.town, policy, snapshot[0], captured, request.nutrition(), 128);
                    helper.assertTrue(!quote.parcels().isEmpty(), "surplus food can be reserved");
                    helper.assertFalse(SettlementAidStock.reserve(level, recipient.town, request, quote, SettlementFoodOffer.Arrangement.DONOR_DELIVERY, UUID.randomUUID()), "different donor identity rejected");
                    helper.assertTrue(SettlementAidStock.reserve(level, donor.town, request, quote, SettlementFoodOffer.Arrangement.DONOR_DELIVERY, UUID.randomUUID()), "surplus moves into reservation custody");
                    helper.assertValueEqual(SettlementAidStock.nutrition(donor.town) + request.offer.nutrition(), initial, "reservation conserves food");
                    helper.assertTrue(SettlementAidStock.nutrition(donor.town) >= SettlementAidStock.reserve(level, donor.town, policy), "protected nutrition remains in depots");
                    helper.assertFalse(SettlementAidStock.reserve(level, donor.town, request, quote, SettlementFoodOffer.Arrangement.DONOR_DELIVERY, UUID.randomUUID()), "duplicate reservation cannot debit twice");
                    helper.assertTrue(SettlementAidStock.refund(level, donor.town, request), "cancelled aid can return to permitted depot");
                    helper.assertValueEqual(SettlementAidStock.nutrition(donor.town), initial, "refund conserves all reserved food");
                    helper.assertFalse(SettlementAidStock.refund(level, donor.town, request), "refund cannot credit twice");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullAndPartialDeliveryKeepCargoAndCreditOnlyReceivedFood(GameTestHelper helper) {
        var donor = fixture(helper, 0); var recipient = fixture(helper, 128); var level = helper.getLevel();
        fillBread(donor.depot);
        for (int slot = 0; slot < recipient.depot.getContainerSize(); slot++) recipient.depot.setItem(slot, new ItemStack(Items.STONE, 64));
        var request = request(helper, donor.town, recipient.town);
        long captured = donor.town.revision();
        SettlementDemandService.Snapshot[] snapshot = {null};
        var work = SettlementDemandService.planAid(level, donor.town, value -> snapshot[0] = value);
        helper.startSequence().thenWaitUntil(() -> { work.run(); helper.assertTrue(snapshot[0] != null, "food allocation snapshot finishes"); })
                .thenExecute(() -> {
                    var data = SettlementSavedData.get(level);
                    var quote = SettlementAidStock.quote(level, donor.town, data.aid().policy(donor.town), snapshot[0], captured, request.nutrition(), 128);
                    helper.assertTrue(SettlementAidStock.reserve(level, donor.town, request, quote, SettlementFoodOffer.Arrangement.DONOR_DELIVERY, UUID.randomUUID()), "food reserved");
                    request.offer.custody = SettlementFoodOffer.Custody.COURIER; request.state = SettlementFoodRequest.State.TRAVELLING;
                    helper.assertValueEqual(SettlementAidStock.deliver(level, donor.town, request), 0, "wrong recipient cannot accept or generate goodwill");
                    helper.assertValueEqual(SettlementAidStock.deliver(level, recipient.town, request), 0, "full depot retains cargo");
                    helper.assertValueEqual(request.deliveredNutrition(), 0L, "full depot grants no delivery credit");
                    recipient.depot.setItem(0, new ItemStack(Items.BREAD, 63));
                    helper.assertValueEqual(SettlementAidStock.deliver(level, recipient.town, request), 1, "partial capacity accepts one meal");
                    helper.assertValueEqual(request.deliveredNutrition(), 5L, "only one meal's actual nutrition is credited");
                    helper.assertValueEqual(request.offer.remaining().stream().mapToLong(parcel -> SettlementAidStock.nutrition(parcel.item())).sum(), 95L, "undelivered food remains in custody");
                    helper.assertValueEqual(SettlementAidStock.deliver(level, recipient.town, request), 0, "repeated full delivery grants nothing");
                    recipient.depot.setItem(1, ItemStack.EMPTY);
                    helper.assertValueEqual(SettlementAidStock.deliver(level, recipient.town, request), 19, "remaining food can be recovered into freed space");
                    helper.assertValueEqual(request.deliveredNutrition(), 100L, "complete receipt totals actual delivery");
                    helper.assertValueEqual(request.offer.custody(), SettlementFoodOffer.Custody.DELIVERED, "empty shipment closes custody");
                    helper.assertValueEqual(SettlementAidStock.deliver(level, recipient.town, request), 0, "completed delivery is idempotent");
                    helper.assertValueEqual(data.aid().receipts.values().stream().filter(receipt -> receipt.request().equals(request.id())).mapToInt(SettlementAidLedger.Receipt::standing).sum(), 3, "partial receipts cannot exceed shipment goodwill");
                    var saved = data.save(new CompoundTag(), level.registryAccess());
                    helper.assertFalse(SettlementSavedData.load(saved, level.registryAccess()).readOnly(), "partial handoff receipts remain valid after restart");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void unloadedAidInspectionDoesNotEnableProduction(GameTestHelper helper) {
        var donor = fixture(helper, 0); var level = helper.getLevel();
        fillBread(donor.depot);
        var autonomous = new AutonomousVillageState();
        autonomous.sections.add(net.minecraft.core.SectionPos.asLong(donor.depot.position()));
        donor.town.autonomous(autonomous);
        helper.assertFalse(level.hasChunkAt(donor.depot.position()), "fixture is unloaded");
        helper.assertTrue(SettlementStock.supplies(donor.town).isEmpty(), "normal autonomous production still excludes unloaded depots");
        long before = SettlementAidStock.nutrition(donor.town), captured = donor.town.revision();
        SettlementDemandService.Snapshot[] snapshot = {null};
        var work = SettlementDemandService.planAid(level, donor.town, value -> snapshot[0] = value);
        helper.startSequence().thenWaitUntil(() -> { work.run(); helper.assertTrue(snapshot[0] != null, "read-only aid planning finishes"); })
                .thenExecute(() -> {
                    var quote = SettlementAidStock.quote(level, donor.town, SettlementAidPolicy.defaults(donor.town), snapshot[0], captured, 100, 20);
                    helper.assertValueEqual(quote.nutrition(), 100L, "registered depot can offer existing food while unloaded: " + quote.blocker());
                    helper.assertValueEqual(SettlementAidStock.nutrition(donor.town), before, "inspection produces and consumes nothing");
                    helper.assertTrue(SettlementStock.supplies(donor.town).isEmpty(), "aid inspection leaves production restrictions unchanged");
                    helper.assertFalse(level.hasChunkAt(donor.depot.position()), "aid inspection does not load the depot");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void custodyCheckpointUsesTheOrderedSaveQueue(GameTestHelper helper) {
        var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        data.aid().changed(); data.setDirty();
        long revision = data.aid().revision();
        helper.assertFalse(SettlementAidCheckpoint.durable(level, revision), "new custody waits for its queued disk checkpoint");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(SettlementAidCheckpoint.durable(level, revision), "checkpoint completes without waiting on the server thread"))
                .thenExecute(() -> {
                    var path = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("data").resolve("villagerretaliation_settlements.dat");
                    try {
                        var saved = net.minecraft.nbt.NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                        helper.assertTrue(saved.getCompound("data").getCompound("FoodAid").getLong("Revision") >= revision,
                                "the confirmed custody revision is present on disk");
                    } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                    helper.assertTrue(SettlementAidCheckpoint.durable(level, revision), "acknowledged revision needs no repeat write");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void delayedRequestsRequireKnowledgeAuthorityAndManualApproval(GameTestHelper helper) {
        var donor = fixture(helper, 0); var recipient = fixture(helper, 128); var level = helper.getLevel();
        var data = SettlementSavedData.get(level); var diplomacy = com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData.get(level);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AidDiplomat"));
        recipient.town.setPermissions(recipient.town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        helper.assertValueEqual(SettlementAidService.request(player, recipient.town.id(), donor.town.id(), 100, 0), SettlementAidService.Result.FORBIDDEN,
                "view authority cannot request aid");
        recipient.town.setPermissions(recipient.town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.DIPLOMACY));
        helper.assertValueEqual(SettlementAidService.request(player, recipient.town.id(), donor.town.id(), 100, 0), SettlementAidService.Result.UNKNOWN_VILLAGE,
                "unknown villages cannot be addressed");
        com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService.receiveCorrespondence(level, donor.town.id(), recipient.town.id());
        helper.assertTrue(diplomacy.contact(donor.town.id(), recipient.town.id()) == null, "receiving knowledge stays directional");
        VillageAllegianceRegistrySavedData.get(level).addOrUpdateResident(recipient.town.id(), UUID.randomUUID(), true, level.getGameTime());
        helper.assertValueEqual(SettlementAidService.request(player, recipient.town.id(), donor.town.id(), 100, 0), SettlementAidService.Result.APPLIED, "authorized known request is sent");
        var request = data.aid().requests.values().stream().filter(value -> value.requester().equals(recipient.town.id())).findFirst().orElseThrow();
        helper.assertTrue(request.arrives() - request.created() >= 256, "correspondence delay accounts for distance");
        helper.assertValueEqual(SettlementAidService.request(player, recipient.town.id(), donor.town.id(), 100, 0), SettlementAidService.Result.OUTSTANDING,
                "an equivalent request cannot duplicate incoming food");
        var schedule = new SettlementAidService.Schedule(); schedule.step(level, request, request.arrives() - 1);
        helper.assertValueEqual(request.state(), SettlementFoodRequest.State.REQUEST_SENT, "request does not arrive early");
        schedule.step(level, request, request.arrives());
        helper.assertValueEqual(request.state(), SettlementFoodRequest.State.AWAITING_OFFER, "arrival opens the donor decision");
        helper.assertTrue(diplomacy.contact(donor.town.id(), recipient.town.id()).established(), "recipient learns its correspondent");
        schedule.step(level, request, request.arrives() + 1);
        helper.assertValueEqual(request.state(), SettlementFoodRequest.State.AWAITING_OFFER, "player settlement stays manual by default");
        donor.town.setPermissions(donor.town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.DIPLOMACY));
        helper.assertValueEqual(SettlementAidService.approve(player, request.id(), request.revision() + 1, 64), SettlementAidService.Result.STALE, "stale approval rejected");
        helper.assertValueEqual(SettlementAidService.decline(player, request.id(), request.revision()), SettlementAidService.Result.APPLIED, "donor may decline without punishment");
        schedule.step(level, request, request.arrives());
        helper.assertValueEqual(request.state(), SettlementFoodRequest.State.DECLINED, "decline arrives later");
        helper.assertValueEqual(diplomacy.standing(donor.town.id(), recipient.town.id()), 0, "decline does not lower standing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sustainedShortageAndIncomingFoodLimitAutonomousRequests(GameTestHelper helper) {
        var donor = fixture(helper, 0); var recipient = fixture(helper, 128); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        var autonomous = new AutonomousVillageState(); autonomous.sections.add(net.minecraft.core.SectionPos.asLong(recipient.depot.position())); recipient.town.autonomous(autonomous);
        VillageAllegianceRegistrySavedData.get(level).addOrUpdateResident(recipient.town.id(), UUID.randomUUID(), true, level.getGameTime());
        com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService.receiveCorrespondence(level, donor.town.id(), recipient.town.id());
        var schedule = new SettlementAidService.Schedule();
        var rules = com.jvn.villagerretaliation.diplomacy.VillageDiplomacyRules.get(level.getServer());
        long start = level.getGameTime() + rules.cadence(); schedule.observe(level, recipient.town, start);
        helper.assertTrue(data.aid().requests.values().stream().noneMatch(value -> value.requester().equals(recipient.town.id())), "one short observation causes no request");
        schedule.observe(level, recipient.town, start + rules.shortageDuration());
        var request = data.aid().requests.values().stream().filter(value -> value.requester().equals(recipient.town.id())).findFirst().orElseThrow();
        helper.assertValueEqual(SettlementAidService.need(level, recipient.town), 0L, "incoming request covers the measured shortage");
        schedule.observe(level, recipient.town, start + rules.shortageDuration() + rules.cadence());
        helper.assertValueEqual(data.aid().requests.values().stream().filter(value -> value.requester().equals(recipient.town.id())).count(), 1L, "shortage does not create repeated requests");
        helper.assertTrue(data.aid().town(recipient.town).nextRequest > request.created(), "request cooldown survives in the ledger");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void courierSelectionPreservesResidentIdentityAndAvoidsCommitments(GameTestHelper helper) {
        var town = fixture(helper, 0); var level = helper.getLevel();
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        villager.setPos(town.depot.position().getX() + 0.5, town.depot.position().getY(), town.depot.position().getZ() + 0.5);
        com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, villager, town.town.id(), com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
        var resident = SettlementResidentState.capture(villager, town.town, level.getGameTime()); town.town.recordResident(resident);
        var request = UUID.randomUUID();
        helper.assertTrue(SettlementAidService.eligible(level, town.town, resident, request), "existing available adult can carry diplomacy");
        helper.assertValueEqual(resident.context(level).getUUID(), villager.getUUID(), "restoring context preserves the resident UUID");
        villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        resident.observe(villager, town.town, level.getGameTime());
        helper.assertFalse(SettlementAidService.eligible(level, town.town, resident, request), "food producer is not borrowed");
        villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE)); villager.setAge(-100);
        resident.observe(villager, town.town, level.getGameTime());
        helper.assertFalse(SettlementAidService.eligible(level, town.town, resident, request), "children cannot become couriers");
        helper.assertTrue(SettlementAidService.boat(level, town.town, town.depot).isEmpty(), "no equipment is invented");
        town.depot.setItem(0, new ItemStack(Items.OAK_BOAT));
        helper.assertTrue(SettlementAidService.boat(level, town.town, town.depot).is(Items.OAK_BOAT), "real permitted boat can support a sailing offer");
        helper.assertValueEqual(town.depot.getItem(0).getCount(), 1, "feasibility check does not consume equipment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void donorDeliveryWinsAnEqualTravelCost(GameTestHelper helper) {
        preparedOffer(helper, 0, SettlementFoodOffer.Arrangement.DONOR_DELIVERY);
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void requesterCollectionWinsALowerTravelCost(GameTestHelper helper) {
        preparedOffer(helper, 8, SettlementFoodOffer.Arrangement.REQUESTER_COLLECTION);
    }

    private static void preparedOffer(GameTestHelper helper, int donorDistance, SettlementFoodOffer.Arrangement arrangement) {
        var donor = fixture(helper, 0); var recipient = fixture(helper, 128); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        fillBread(donor.depot);
        com.jvn.villagerretaliation.diplomacy.route.AidRouteFixture.flat(level, donor.depot.position(), recipient.depot.position());
        UUID[] residents = new UUID[2]; var fixtures = List.of(donor, recipient);
        for (int index = 0; index < fixtures.size(); index++) {
            var fixture = fixtures.get(index); var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            villager.setPos(fixture.depot.position().getX() + (index == 0 ? donorDistance : 0) + 0.5, 64, fixture.depot.position().getZ() + 0.5);
            com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, villager, fixture.town.id(), com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
            fixture.town.recordResident(SettlementResidentState.capture(villager, fixture.town, level.getGameTime())); residents[index] = villager.getUUID();
        }
        var request = request(helper, donor.town, recipient.town);
        request.donorAuthority = donor.town.leader(); request.maximumItems = 20;
        SettlementAidService.transition(data, request, SettlementFoodRequest.State.OFFER_PREPARING, "");
        long before = SettlementAidStock.nutrition(donor.town);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(request.state() == SettlementFoodRequest.State.OFFER_SENT,
                "feasible offer finishes preparation: " + request.state() + " " + request.blocker()))
                .thenExecute(() -> {
                    helper.assertValueEqual(request.offer.arrangement(), arrangement, "lower-cost feasible arrangement is selected, donor wins ties");
                    helper.assertValueEqual(request.offer.courier(), residents[arrangement == SettlementFoodOffer.Arrangement.DONOR_DELIVERY ? 0 : 1], "offer borrows the selected real resident");
                    helper.assertValueEqual(request.offer.nutrition(), 100L, "offer respects the approved item quantity");
                    helper.assertValueEqual(SettlementAidStock.nutrition(donor.town) + request.offer.nutrition(), before, "prepared offer conserves stock");
                    helper.assertFalse(level.hasChunkAt(donor.depot.position()), "cached preparation does not load a depot or tick a village");
                    helper.assertFalse(SettlementSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess()).readOnly(), "prepared offer is restartable");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void aidPagesFilterContactsAndConcealPendingOffers(GameTestHelper helper) {
        var donor = fixture(helper, 0); var recipient = fixture(helper, 128); var level = helper.getLevel();
        var viewer = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AidViewer"));
        recipient.town.setPermissions(recipient.town.leader(), viewer.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var policy = new com.jvn.villagerretaliation.network.SettlementAidRequestPayload.Policy(false, 3, 128, 0, 72000);
        var query = new com.jvn.villagerretaliation.network.SettlementAidRequestPayload(recipient.town.id().value(), null, null,
                com.jvn.villagerretaliation.network.SettlementAidRequestPayload.Action.VIEW, 0, 0, 0, policy, 1);
        var request = request(helper, donor.town, recipient.town);
        helper.assertValueEqual(SettlementAidNetworking.snapshot(viewer, query, SettlementAidService.Result.APPLIED).total(), 0, "unknown counterpart has no name or count");
        com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService.receiveCorrespondence(level, donor.town.id(), recipient.town.id());
        request.offer = new SettlementFoodOffer(SettlementFoodOffer.Arrangement.DONOR_DELIVERY, UUID.randomUUID(), List.of(new SettlementFoodOffer.Parcel(donor.depot.id(), new ItemStack(Items.BREAD, 20))));
        request.state = SettlementFoodRequest.State.OFFER_SENT; request.messageSender = donor.town.id();
        var hidden = SettlementAidNetworking.snapshot(viewer, query, SettlementAidService.Result.APPLIED);
        helper.assertValueEqual(hidden.total(), 1, "known requested contact is listed");
        helper.assertValueEqual(hidden.entries().getFirst().offered(), 0L, "pending offer amount is not disclosed early");
        helper.assertTrue(hidden.entries().getFirst().arrangement() == null, "pending courier choice is not disclosed early");
        request.state = SettlementFoodRequest.State.AWAITING_ACCEPTANCE;
        var arrived = SettlementAidNetworking.snapshot(viewer, query, SettlementAidService.Result.APPLIED);
        helper.assertValueEqual(arrived.entries().getFirst().offered(), 100L, "received offer can be inspected");
        helper.assertFalse(arrived.authorized(), "view access cannot approve aid");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementAidPayload.STREAM_CODEC.encode(buffer, arrived);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.SettlementAidPayload.STREAM_CODEC.decode(buffer), arrived, "bounded aid payload round trips");
        } finally { buffer.release(); }
        recipient.town.setPermissions(recipient.town.leader(), viewer.getUUID(), java.util.Set.of());
        var refused = SettlementAidNetworking.snapshot(viewer, query, SettlementAidService.Result.APPLIED);
        helper.assertValueEqual(refused.total(), 0, "revoked authority removes request counts"); helper.assertValueEqual(refused.name(), "", "revoked authority removes settlement details");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void goodwillAcknowledgementWaitsForBothSavesAndRejectsReplay(GameTestHelper helper) {
        var donor = fixture(helper, 0); var recipient = fixture(helper, 128); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        var request = request(helper, donor.town, recipient.town);
        request.offer = new SettlementFoodOffer(SettlementFoodOffer.Arrangement.DONOR_DELIVERY, UUID.randomUUID(),
                List.of(new SettlementFoodOffer.Parcel(donor.depot.id(), new ItemStack(Items.BREAD, 20))));
        request.offer.replaceRemaining(List.of()); request.offer.custody = SettlementFoodOffer.Custody.DELIVERED;
        request.deliveredNutrition = 100; request.state = SettlementFoodRequest.State.RETURNING;
        data.aid().receipt(request, 3, level.getGameTime()); data.setDirty();
        var receipt = data.aid().receipts.values().stream().filter(value -> value.request().equals(request.id())).findFirst().orElseThrow();
        var diplomacy = com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData.get(level);
        helper.startSequence().thenWaitUntil(() -> {
            SettlementAidService.receipt(level);
            helper.assertFalse(data.aid().receipts.containsKey(receipt.sequence()), "receipt acknowledgement waits for durable food and standing");
        }).thenExecute(() -> {
            helper.assertValueEqual(diplomacy.standing(donor.town.id(), recipient.town.id()), 3, "actual delivery earns bounded goodwill");
            helper.assertTrue(diplomacy.received(donor.town.id(), recipient.town.id(), data.aid().stream(), receipt.sequence()), "stable receipt stream is acknowledged");
            data.aid().receipts.put(receipt.sequence(), receipt); data.aid().changed(); data.setDirty();
        }).thenWaitUntil(() -> {
            SettlementAidService.receipt(level);
            helper.assertFalse(data.aid().receipts.containsKey(receipt.sequence()), "replayed delivery receipt is safely acknowledged again");
        }).thenExecute(() -> helper.assertValueEqual(diplomacy.standing(donor.town.id(), recipient.town.id()), 3, "replay cannot duplicate goodwill")).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void cancellingAReservationRetainsFoodUntilRefundStorageIsAvailable(GameTestHelper helper) {
        var donor = fixture(helper, 0); var recipient = fixture(helper, 128); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        fillBread(donor.depot); var request = request(helper, donor.town, recipient.town);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AidCancel"));
        donor.town.setPermissions(donor.town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.DIPLOMACY));
        long revision = donor.town.revision(); SettlementDemandService.Snapshot[] ready = {null};
        var work = SettlementDemandService.planAid(level, donor.town, value -> ready[0] = value);
        helper.startSequence().thenWaitUntil(() -> { work.run(); helper.assertTrue(ready[0] != null, "allocation planning completes"); })
                .thenExecute(() -> {
                    var quote = SettlementAidStock.quote(level, donor.town, data.aid().policy(donor.town), ready[0], revision, 100, 20);
                    helper.assertTrue(SettlementAidStock.reserve(level, donor.town, request, quote, SettlementFoodOffer.Arrangement.DONOR_DELIVERY, UUID.randomUUID()), "food reserved before cancellation");
                    fillBread(donor.depot);
                    helper.assertValueEqual(SettlementAidService.cancel(player, request.id(), request.revision()), SettlementAidService.Result.APPLIED, "authorized cancellation is sent");
                    var schedule = new SettlementAidService.Schedule(); schedule.step(level, request, request.arrives());
                    helper.assertValueEqual(request.state(), SettlementFoodRequest.State.CANCELLING, "full donor storage keeps cancellation recoverable");
                    helper.assertValueEqual(request.offer.remaining().stream().mapToLong(parcel -> SettlementAidStock.nutrition(parcel.item())).sum(), 100L, "reserved food is not discarded");
                    donor.depot.setItem(0, ItemStack.EMPTY); schedule.step(level, request, request.arrives());
                    helper.assertValueEqual(request.state(), SettlementFoodRequest.State.CANCELLED, "free space completes refund");
                    helper.assertValueEqual(donor.depot.getItem(0).getCount(), 20, "all cancelled food is returned once");
                    helper.assertValueEqual(SettlementAidService.cancel(player, request.id(), request.revision()), SettlementAidService.Result.UNAVAILABLE, "completed refund cannot repeat");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void aidAllocationGuardIgnoresCareRevisionsButRejectsStockChanges(GameTestHelper helper) {
        var fixture = fixture(helper, 0); fillBread(fixture.depot);
        var guard = SettlementAllocationService.aidGuard(helper.getLevel(), fixture.town);
        fixture.town.changed();
        helper.assertTrue(guard.getAsBoolean(), "unrelated care revisions do not starve aid planning");
        fixture.depot.removeItem(0, 1);
        helper.assertFalse(guard.getAsBoolean(), "stock changes invalidate a pending offer snapshot");
        helper.succeed();
    }

    record Fixture(SettlementRecord town, SettlementDepot depot) { }
    static Fixture fixture(GameTestHelper helper, int offset) {
        var level = helper.getLevel(); var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(22000000 + origin.getX() * 8 + offset, 64, 22000000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Aid fixture");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Food store", center, center.offset(2, 2, 2), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "depot area registered");
        var depot = town.registerDepot(town.leader(), area.id(), center, SettlementDepotPurpose.GENERAL);
        depot.observeRules(List.of());
        return new Fixture(town, depot);
    }
    private static void fillBread(SettlementDepot depot) {
        for (int slot = 0; slot < depot.getContainerSize(); slot++) depot.setItem(slot, new ItemStack(Items.BREAD, 64));
    }
    private static SettlementFoodRequest request(GameTestHelper helper, SettlementRecord donor, SettlementRecord recipient) {
        var data = SettlementSavedData.get(helper.getLevel());
        long now = helper.getLevel().getServer().overworld().getGameTime();
        var request = new SettlementFoodRequest(UUID.randomUUID(), recipient.id(), donor.id(), 100, now, now + 20, now + 24000, recipient.leader());
        request.state = SettlementFoodRequest.State.AWAITING_OFFER;
        data.aid().requests.put(request.id(), request); data.aid().changed(request); data.setDirty();
        return request;
    }
}
