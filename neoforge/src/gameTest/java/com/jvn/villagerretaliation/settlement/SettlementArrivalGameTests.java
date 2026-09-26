package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementArrivalGameTests {
    private SettlementArrivalGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unloadedAdmissionsConsumeCapacityAndReceiveRealMeals(GameTestHelper helper) {
        var town = remoteTown(helper, 2);
        var level = helper.getLevel();
        var first = approved(helper, town);
        var second = approved(helper, town);
        helper.assertTrue(SettlementPopulationService.arrive(level, town, first), "the first remote applicant joins");
        helper.assertTrue(SettlementPopulationService.arrive(level, town, second), "the second remote applicant joins");
        helper.assertTrue(SettlementPopulationService.propose(level, town).isEmpty(), "both beds are occupied by saved residents");
        helper.assertValueEqual(town.arrivals().size(), 2, "both world entities remain pending");
        long now = level.getGameTime();
        for (int step = 1; step <= 240; step++) SettlementCareService.update(level, town, now + step * 200L);
        var depot = town.depots().values().iterator().next();
        helper.assertTrue(depot.count(stack -> stack.is(Items.BREAD)) < 64, "background residents eat real depot meals");
        helper.assertTrue(town.residents().values().stream().allMatch(state -> state.food() > 0), "the stock supports the new residents");
        helper.assertFalse(level.hasChunkAt(town.center()), "admission and care never load the remote town");
        helper.assertTrue(level.getEntity(first.id()) == null && level.getEntity(second.id()) == null, "care does not spawn proxies");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void queuedApprovalsRecheckFoodAndRevokedAuthority(GameTestHelper helper) {
        var town = remoteTown(helper, 2);
        var level = helper.getLevel();
        var applicant = approved(helper, town);
        var depot = town.depots().values().iterator().next();
        depot.clearContent();
        helper.assertFalse(SettlementPopulationService.arrive(level, town, applicant), "queued approval still requires actual food");
        helper.assertTrue(town.arrivals().isEmpty() && town.residents().isEmpty(), "a shortage cannot admit an unsupported resident");
        town.transferLeadership(town.leader(), UUID.randomUUID());
        depot.setItem(0, new ItemStack(Items.BREAD, 64));
        helper.assertFalse(SettlementPopulationService.arrive(level, town, applicant), "revoked approval cannot admit the applicant later");
        helper.assertTrue(town.applicants().get(applicant.id()).approvedBy().isEmpty(), "new leadership can reconsider the application");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingArrivalAndCareSurviveReloadWithoutTimeCatchup(GameTestHelper helper) {
        var town = remoteTown(helper, 2);
        var level = helper.getLevel();
        var applicant = approved(helper, town);
        SettlementPopulationService.arrive(level, town, applicant);
        long now = level.getGameTime();
        SettlementCareService.update(level, town, now + 200L);
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.arrivals().get(applicant.id()).entity(), town.arrivals().get(applicant.id()).entity(),
                "the exact admitted entity survives reload");
        var care = restored.residents().get(applicant.id());
        helper.assertValueEqual(care.save(), town.residents().get(applicant.id()).save(), "resident care has one saved owner");
        helper.assertValueEqual(care.advance(level, restored, now + 200L), 0, "repeated server time is not simulated twice");
        helper.assertValueEqual(care.advance(level, restored, now + 200_000L), 200, "a restart gap cannot accumulate offline work");
        SettlementArrivalService.update(level, restored);
        helper.assertTrue(restored.arrivals().containsKey(applicant.id()), "world creation still waits for loaded terrain");
        helper.assertFalse(level.hasChunkAt(town.center()), "reload reconciliation does not force chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void returningArrivalRestoresCareAndAppearsExactlyOnce(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        UUID id = pending(helper, fixture.town());
        var careTag = fixture.town().residents().get(id).save();
        careTag.putLong("Version", 2L);
        careTag.putInt("Food", 7);
        careTag.putFloat("Health", 9.0F);
        careTag.putInt("Age", 400);
        fixture.town().recordResident(SettlementResidentState.load(careTag));
        helper.assertTrue(SettlementArrivalService.materialize(level, fixture.town(), id), "the saved resident can enter loaded terrain");
        Villager resident = (Villager) level.getEntity(id);
        helper.assertTrue(resident != null, "the pending identity becomes one actual villager");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(resident), 7, "background meals and hunger survive return");
        helper.assertValueEqual(resident.getHealth(), 9.0F, "background health survives return");
        helper.assertValueEqual(resident.getAge(), 400, "background age survives return");
        helper.assertTrue(fixture.town().arrivals().isEmpty(), "successful world creation consumes the pending entry");
        helper.assertFalse(SettlementArrivalService.materialize(level, fixture.town(), id), "replaying the request cannot create a second entity");
        helper.assertValueEqual(VillageAllegianceApi.canonicalPrimary(level, resident).orElseThrow(), fixture.town().id(),
                "world creation preserves the admitted home");
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void appliedArrivalReceiptPreventsOldJournalResurrection(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        UUID id = pending(helper, fixture.town());
        CompoundTag older = fixture.town().save(level.registryAccess());
        helper.assertTrue(SettlementArrivalService.materialize(level, fixture.town(), id), "the first arrival succeeds");
        level.getEntity(id).discard();
        var restored = SettlementRecord.load(older, level.registryAccess(), () -> {});
        helper.assertTrue(SettlementArrivalService.materialize(level, restored, id), "the chunk acknowledges the older arrival record");
        helper.assertTrue(restored.arrivals().isEmpty(), "the stale pending entry is retired");
        helper.assertTrue(level.getEntity(id) == null, "an already removed villager cannot be resurrected by an old journal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingArrivalEntityWinsOverItsPendingSnapshot(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        UUID id = pending(helper, fixture.town());
        Villager existing = EntityType.VILLAGER.create(level);
        existing.load(fixture.town().arrivals().get(id).entity());
        existing.setPos(fixture.town().center().getCenter());
        existing.setHealth(5.0F);
        level.addFreshEntity(existing);
        helper.assertTrue(SettlementArrivalService.materialize(level, fixture.town(), id), "an existing entity retires the pending entry");
        helper.assertTrue(level.getEntity(id) == existing, "the live identity is preserved");
        helper.assertValueEqual(existing.getHealth(), 5.0F, "reconciliation cannot replace current health with the arrival snapshot");
        existing.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void blockedArrivalWaitsAndUsesANewSafePosition(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        var center = fixture.town().center();
        UUID id = pending(helper, fixture.town());
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -3, -4), center.offset(4, 5, 4))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        helper.assertFalse(SettlementArrivalService.materialize(level, fixture.town(), id), "solid terrain blocks world creation");
        helper.assertTrue(fixture.town().arrivals().containsKey(id), "blocked terrain retains the pending identity");
        helper.assertTrue(fixture.town().residents().containsKey(id), "the admitted resident still belongs to the town");
        level.setBlock(center, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(center.above(), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(SettlementArrivalService.materialize(level, fixture.town(), id), "a newly cleared safe position resumes the arrival");
        helper.assertValueEqual(level.getEntity(id).blockPosition(), center, "arrival uses the checked open space");
        level.getEntity(id).discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void shortageDepartureBeforeArrivalRemainsUnaffiliatedOnReturn(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var town = fixture.town();
        var level = helper.getLevel();
        UUID id = pending(helper, town);
        var care = town.residents().get(id).save();
        care.putLong("Version", 1L);
        care.putLong("ShortageTicks", SettlementDepartureService.graceTicks());
        care.putInt("Food", 0);
        care.putFloat("Saturation", 0.0F);
        town.recordResident(SettlementResidentState.load(care));
        fixture.depot().clearContent();
        helper.assertValueEqual(SettlementDepartureService.process(level, town, level.getGameTime()), List.of(id),
                "unmaterialized residents follow the same shortage departures");
        helper.assertTrue(town.arrivals().containsKey(id), "departure preserves the pending villager instead of deleting them");
        helper.assertTrue(SettlementArrivalService.materialize(level, town, id), "the departing resident can appear when terrain returns");
        Villager resident = (Villager) level.getEntity(id);
        helper.assertTrue(VillageAllegianceApi.canonicalPrimary(level, resident).isEmpty(), "world creation does not undo departure");
        helper.assertTrue(town.departures().containsKey(id), "departure history survives world creation");
        helper.assertTrue(VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime()).isEmpty(),
                "the departed resident is not counted twice");
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void removedResidentCannotBeRecreatedByPendingArrival(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        UUID id = pending(helper, fixture.town());
        VillageAllegianceRegistrySavedData.get(level).removeResident(fixture.town().id(), id);
        fixture.town().removeResident(id);
        helper.assertFalse(SettlementArrivalService.materialize(level, fixture.town(), id), "removed membership prevents world creation");
        helper.assertTrue(fixture.town().arrivals().isEmpty(), "the invalid pending identity is retired");
        helper.assertTrue(level.getEntity(id) == null, "removal cannot be reversed by reconciliation");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void arrivalPersistenceRejectsDuplicateAndMismatchedIdentities(GameTestHelper helper) {
        var town = remoteTown(helper, 2);
        var level = helper.getLevel();
        var applicant = approved(helper, town);
        SettlementPopulationService.arrive(level, town, applicant);
        CompoundTag saved = town.save(level.registryAccess());
        ListTag arrivals = saved.getList("Arrivals", net.minecraft.nbt.Tag.TAG_COMPOUND);
        arrivals.add(arrivals.getCompound(0).copy());
        boolean duplicateRejected = false;
        try {
            SettlementRecord.load(saved, level.registryAccess(), () -> {});
        } catch (IllegalArgumentException expected) {
            duplicateRejected = true;
        }
        helper.assertTrue(duplicateRejected, "duplicate pending identities cannot be loaded");
        var invalid = town.arrivals().get(applicant.id()).save();
        invalid.putUUID("Id", UUID.randomUUID());
        boolean mismatchRejected = false;
        try {
            SettlementArrival.load(invalid);
        } catch (IllegalArgumentException expected) {
            mismatchRejected = true;
        }
        helper.assertTrue(mismatchRejected, "the entity UUID must match its arrival identity");
        saved = town.save(level.registryAccess());
        saved.remove("Arrivals");
        helper.assertTrue(SettlementRecord.load(saved, level.registryAccess(), () -> {}).arrivals().isEmpty(),
                "older saves need no pending-arrival field");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 260, batch = "isolated_settlement_arrival")
    public static void serverPopulationTickCompletesPendingWorldArrivals(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        UUID id = pending(helper, fixture.town());
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getEntity(id) instanceof Villager, "the normal population tick creates the pending resident");
            helper.assertTrue(fixture.town().arrivals().isEmpty(), "automatic reconciliation consumes the pending entity");
            helper.assertValueEqual(VillageAllegianceRegistrySavedData.get(level).residentRoster(
                    fixture.town().id(), level.getGameTime()).size(), 1, "automatic return keeps one census entry");
            level.getEntity(id).discard();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void managementShowsPendingArrivalsAndTheirCare(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        UUID id = pending(helper, fixture.town());
        var page = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                fixture.town().id().value(), SettlementManagementRequestPayload.Page.RESIDENTS, 0, "", 1, false));
        var resident = (SettlementManagementPayload.Resident) page.entries().getFirst();
        helper.assertValueEqual(page.header().population(), 1, "the admitted resident appears in town population");
        helper.assertValueEqual(resident.id(), id, "management preserves the pending resident identity");
        helper.assertValueEqual(resident.status(), "awaiting_arrival", "the panel distinguishes pending arrival from idle work");
        helper.assertValueEqual(resident.food(), fixture.town().residents().get(id).food(), "management shows actual background care");
        helper.succeed();
    }

    private static UUID pending(GameTestHelper helper, SettlementRecord town) {
        var applicant = SettlementPopulationService.propose(helper.getLevel(), town).orElseThrow();
        Villager resident = EntityType.VILLAGER.create(helper.getLevel());
        resident.load(applicant.entity());
        resident.setNoAi(true);
        resident.setPos(town.center().getCenter());
        SettlementArrivalService.admit(helper.getLevel(), town, resident);
        town.removeApplicant(applicant.id());
        return applicant.id();
    }

    private static SettlementApplicant approved(GameTestHelper helper, SettlementRecord town) {
        var applicant = SettlementPopulationService.propose(helper.getLevel(), town).orElseThrow().approve(town.leader());
        town.putApplicant(applicant);
        return applicant;
    }

    private static SettlementRecord remoteTown(GameTestHelper helper, int beds) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(BlockPos.ZERO).offset(6_000_000, 0, 6_000_000).atY(80);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", center, center.offset(5, 3, 5), 5, true);
        var depotArea = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Depot", center.east(6), center.offset(8, 3, 5), 5, true);
        town.putArea(town.leader(), home);
        town.putArea(town.leader(), depotArea);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(level.getGameTime(),
                java.util.stream.IntStream.range(0, beds).mapToObj(index -> center.offset(index, 1, 1)).toList(), List.of(), true));
        var depot = town.registerDepot(town.leader(), depotArea.id(), depotArea.min(), SettlementDepotPurpose.GENERAL);
        depot.setItem(0, new ItemStack(Items.BREAD, 64));
        helper.assertFalse(level.hasChunkAt(center), "the remote fixture is unloaded");
        return town;
    }
}
