package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementWorkerPoolGameTests {
    private SettlementWorkerPoolGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool")
    public static void poolEnrollmentIsExplicitAndOlderSavesKeepExistingDuties(GameTestHelper helper) {
        var fixture = fixture(helper, 2);
        var town = fixture.town();
        var resident = fixture.residents().getFirst();
        town.assignDuty(resident.getUUID(), fixture.farm().id());
        town.rememberManualDuty(resident.getUUID());
        helper.assertTrue(town.workerPools().isEmpty(), "new towns have no enrolled workers");
        var saved = town.save(helper.getLevel().registryAccess());
        saved.remove("WorkerPoolVersion");
        saved.remove("WorkerPools");
        saved.remove("WorkerPoolCooldowns");
        var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(restored.workerPools().isEmpty(), "older saves never create a pool");
        helper.assertValueEqual(restored.duties().get(resident.getUUID()), fixture.farm().id(), "existing work is preserved");
        helper.assertTrue(restored.manuallyStaffed(resident.getUUID()), "legacy manual pins survive");
        var defaults = SettlementWorkerPool.Staffing.defaults(town, fixture.farm().id());
        helper.assertValueEqual(defaults.minimum(), 0, "pool minimums start at zero");
        helper.assertValueEqual(defaults.maximum(), 2, "pool caps use the existing desired staffing");
        helper.assertValueEqual(resident.getVillagerData().getProfession(), VillagerProfession.FARMER, "pool data never changes real professions");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "pool persistence does not load terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool")
    public static void enrollmentIsExclusiveAndNeverClearsManualAssignments(GameTestHelper helper) {
        var fixture = fixture(helper, 2);
        var town = fixture.town();
        var resident = fixture.residents().getFirst().getUUID();
        town.assignDuty(resident, fixture.farm().id());
        town.rememberManualDuty(resident);
        town.setManualManagement(town.leader(), true);
        var id = UUID.randomUUID();
        var configuration = configuration(fixture, List.of(resident));
        helper.assertTrue(town.configureWorkerPool(town.leader(), id, -1, configuration, true), "a pool has separate authority in a manual town");
        helper.assertTrue(town.manuallyStaffed(resident), "enrollment preserves the manual pin");
        helper.assertValueEqual(town.duties().get(resident), fixture.farm().id(), "enrollment does not move a worker");
        helper.assertValueEqual(town.workerPoolFor(resident), id, "membership is visible independently of the pin");
        var other = UUID.randomUUID();
        helper.assertFalse(town.configureWorkerPool(town.leader(), other, -1, configuration, false), "paused pools cannot duplicate enrollment");
        var replacement = configuration(fixture, List.of(fixture.residents().getLast().getUUID()));
        helper.assertTrue(town.configureWorkerPool(town.leader(), id, town.workerPools().get(id).revision(), replacement, true),
                "editing membership releases only the selected resident");
        helper.assertTrue(town.configureWorkerPool(town.leader(), other, -1, configuration, false), "the released resident can explicitly join another pool");
        helper.assertValueEqual(town.workerPoolFor(resident), other, "membership lookup reflects the new owner");
        helper.assertTrue(town.manuallyStaffed(resident), "moving between pools still preserves manual ownership");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool")
    public static void poolAuthorizationAndRevisionsSurvivePermissionChanges(GameTestHelper helper) {
        var fixture = fixture(helper, 1);
        var town = fixture.town();
        var delegate = UUID.randomUUID();
        var id = UUID.randomUUID();
        var configuration = configuration(fixture, List.of(fixture.residents().getFirst().getUUID()));
        town.setPermissions(town.leader(), delegate, Set.of(SettlementPermission.VIEW));
        helper.assertFalse(town.configureWorkerPool(delegate, id, -1, configuration, true), "view permission cannot enroll workers");
        town.setPermissions(town.leader(), delegate, Set.of(SettlementPermission.STAFF));
        helper.assertTrue(town.configureWorkerPool(delegate, id, -1, configuration, true), "a staffing delegate can authorize a pool");
        var original = town.workerPools().get(id);
        helper.assertTrue(original.authorized(town), "the recorded staffing authority is current");
        helper.assertFalse(town.configureWorkerPool(delegate, id, original.revision() + 1, configuration, false), "stale edits do not change a pool");
        town.setPermissions(town.leader(), delegate, Set.of(SettlementPermission.VIEW));
        helper.assertValueEqual(town.workerPools().get(id).state(), SettlementWorkerPool.State.SUSPENDED, "revocation suspends immediately");
        helper.assertFalse(original.authorized(town), "an old execution snapshot loses authority immediately");
        town.setPermissions(town.leader(), delegate, Set.of(SettlementPermission.STAFF));
        helper.assertValueEqual(town.workerPools().get(id).state(), SettlementWorkerPool.State.SUSPENDED, "restoring permission does not reactivate");
        helper.assertFalse(town.changeWorkerPool(delegate, id, original.revision(), true), "the pre-revocation revision cannot reactivate");
        helper.assertTrue(town.changeWorkerPool(delegate, id, town.workerPools().get(id).revision(), true), "explicit current reactivation succeeds");
        var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.workerPools().get(id), town.workerPools().get(id), "authority and revision survive reload");
        var successor = UUID.randomUUID();
        var offer = town.offerLeadership(town.leader(), successor, helper.getLevel().getGameTime());
        helper.assertTrue(town.acceptLeadership(successor, offer.id(), helper.getLevel().getGameTime()), "native leadership handoff succeeds");
        helper.assertValueEqual(town.workerPools().get(id).state(), SettlementWorkerPool.State.SUSPENDED, "cleared delegates suspend their pools");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool")
    public static void cooldownSurvivesReloadReconfigurationAndPoolRecreation(GameTestHelper helper) {
        var fixture = fixture(helper, 1);
        var town = fixture.town();
        var resident = fixture.residents().getFirst().getUUID();
        var id = UUID.randomUUID();
        var configuration = configuration(fixture, List.of(resident));
        helper.assertTrue(town.configureWorkerPool(town.leader(), id, -1, configuration, true), "the pool is explicitly active");
        town.assignDuty(resident, fixture.stores().id());
        town.recordWorkerPoolMove(town.workerPools().get(id), new SettlementWorkerPool.Move(10_000, resident, null,
                fixture.stores().id(), SettlementWorkerPool.Reason.MINIMUM, null, 5));
        var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.workerPoolCooldown(resident, 10_000), 1_200L, "reload retains the full remaining cooldown");
        helper.assertValueEqual(restored.workerPoolCooldown(resident, 10_500), 700L, "cooldown uses simulated elapsed ticks");
        helper.assertTrue(restored.configureWorkerPool(restored.leader(), id, restored.workerPools().get(id).revision(), configuration, false),
                "a player can pause and edit the pool");
        helper.assertValueEqual(restored.workerPoolCooldown(resident, 10_500), 700L, "configuration changes do not renew eligibility");
        helper.assertTrue(restored.removeWorkerPool(restored.leader(), id, restored.workerPools().get(id).revision()), "the pool can be removed");
        helper.assertTrue(restored.configureWorkerPool(restored.leader(), UUID.randomUUID(), -1, configuration, true), "enrollment can be explicitly recreated");
        helper.assertValueEqual(restored.workerPoolCooldown(resident, 10_500), 700L, "recreation cannot bypass the town-level cooldown");
        restored.reconcileWorkerPoolCooldowns(500);
        helper.assertValueEqual(restored.workerPoolCooldown(resident, 500), 1_200L, "clock rollback restarts a bounded wait");
        helper.assertValueEqual(restored.workerPoolCooldown(resident, 1_699), 1L, "rollback cannot grant an early move");
        restored.reconcileWorkerPoolCooldowns(1_700);
        helper.assertValueEqual(restored.workerPoolCooldown(resident, 1_700), 0L, "the rollback wait expires normally");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool")
    public static void removalPrunesOnlyTheDepartingMemberAndTheirCooldown(GameTestHelper helper) {
        var fixture = fixture(helper, 2);
        var town = fixture.town();
        var resident = fixture.residents().getFirst().getUUID();
        var remaining = fixture.residents().getLast().getUUID();
        var id = UUID.randomUUID();
        helper.assertTrue(town.configureWorkerPool(town.leader(), id, -1, configuration(fixture, List.of(resident, remaining)), true), "both residents enroll");
        town.assignDuty(resident, fixture.farm().id());
        town.recordWorkerPoolMove(town.workerPools().get(id), new SettlementWorkerPool.Move(100, resident, null,
                fixture.farm().id(), SettlementWorkerPool.Reason.STAFFING, null, 5));
        town.rememberManualDuty(remaining);
        helper.assertValueEqual(town.workerPoolFor(resident), id, "membership lookup is populated before removal");
        town.removeResident(resident);
        helper.assertTrue(town.workerPoolFor(resident) == null, "departing residents leave the pool");
        helper.assertValueEqual(town.workerPoolCooldown(resident, 100), 0L, "obsolete cooldowns are removed");
        helper.assertValueEqual(town.workerPools().get(id).configuration().residents(), List.of(remaining), "other members remain enrolled");
        helper.assertValueEqual(town.workerPools().get(id).state(), SettlementWorkerPool.State.ACTIVE, "remaining members keep authorized pool control");
        helper.assertTrue(town.manuallyStaffed(remaining), "another resident's manual pin remains intact");
        town.removeResident(remaining);
        helper.assertValueEqual(town.workerPools().get(id).state(), SettlementWorkerPool.State.SUSPENDED, "an empty active pool suspends visibly");
        helper.assertValueEqual(town.workerPools().get(id).blocker(), "pool_no_residents", "the suspension identifies the missing membership");
        SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool")
    public static void moveHistoryIsBoundedAndPreservesTheCauseOfReassignment(GameTestHelper helper) {
        var fixture = fixture(helper, 1);
        var town = fixture.town();
        var resident = fixture.residents().getFirst().getUUID();
        var id = UUID.randomUUID();
        var demand = UUID.randomUUID();
        helper.assertTrue(town.configureWorkerPool(town.leader(), id, -1, configuration(fixture, List.of(resident)), true), "the pool is active");
        for (int index = 0; index < 40; index++) {
            long now = 1_200L * index;
            var from = town.duties().get(resident);
            var to = index % 2 == 0 ? fixture.farm().id() : fixture.stores().id();
            town.reconcileWorkerPoolCooldowns(now);
            town.assignDuty(resident, to);
            town.recordWorkerPoolMove(town.workerPools().get(id), new SettlementWorkerPool.Move(now, resident, from, to,
                    SettlementWorkerPool.Reason.PROTECTED_SHORTAGE, demand, 9));
        }
        var pool = town.workerPools().get(id);
        helper.assertValueEqual(pool.history().size(), SettlementWorkerPool.MAX_HISTORY, "history stays bounded across repeated moves");
        helper.assertValueEqual(pool.history().getFirst().at(), 9_600L, "only the oldest history is discarded");
        helper.assertValueEqual(pool.history().getLast().demand(), demand, "the stable demand identity explains the latest move");
        helper.assertValueEqual(pool.history().getLast().reason(), SettlementWorkerPool.Reason.PROTECTED_SHORTAGE, "the protected cause is retained");
        helper.assertValueEqual(SettlementWorkerPool.load(pool.save()), pool, "move reasons and worksite identities survive serialization");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool")
    public static void malformedPoolsCannotDuplicateMembershipOrEraseCooldowns(GameTestHelper helper) {
        var fixture = fixture(helper, 1);
        var town = fixture.town();
        var resident = fixture.residents().getFirst().getUUID();
        var id = UUID.randomUUID();
        helper.assertTrue(town.configureWorkerPool(town.leader(), id, -1, configuration(fixture, List.of(resident)), true), "the fixture has one active pool");
        var saved = town.save(helper.getLevel().registryAccess());
        var duplicate = saved.copy();
        var other = duplicate.getList("WorkerPools", Tag.TAG_COMPOUND).getCompound(0).copy();
        other.putUUID("Id", UUID.randomUUID());
        duplicate.getList("WorkerPools", Tag.TAG_COMPOUND).add(other);
        invalid(() -> SettlementRecord.load(duplicate, helper.getLevel().registryAccess(), () -> {}));
        var version = saved.copy();
        version.putInt("WorkerPoolVersion", 2);
        invalid(() -> SettlementRecord.load(version, helper.getLevel().registryAccess(), () -> {}));
        var missingVersion = saved.copy();
        missingVersion.remove("WorkerPoolVersion");
        invalid(() -> SettlementRecord.load(missingVersion, helper.getLevel().registryAccess(), () -> {}));
        var missingLedger = saved.copy();
        missingLedger.remove("WorkerPoolCooldowns");
        invalid(() -> SettlementRecord.load(missingLedger, helper.getLevel().registryAccess(), () -> {}));
        var limits = town.workerPools().get(id).save();
        limits.getCompound("Configuration").getList("Worksites", Tag.TAG_COMPOUND).getCompound(0).putInt("Minimum", 33);
        invalid(() -> SettlementWorkerPool.load(limits));
        invalid(() -> configuration(fixture, List.of(resident, resident)));
        helper.succeed();
    }

    private static void invalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid worker pool data was accepted");
    }

    private static SettlementWorkerPool.Configuration configuration(Fixture fixture, List<UUID> residents) {
        return new SettlementWorkerPool.Configuration("Food and deliveries", residents,
                List.of(SettlementWorkerPool.Staffing.defaults(fixture.town(), fixture.farm().id()),
                        SettlementWorkerPool.Staffing.defaults(fixture.town(), fixture.stores().id())));
    }

    static Fixture fixture(GameTestHelper helper, int population) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(24_000_000 + origin.getX() * 8, 64, 24_000_000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Worker pool settlement");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, center, center.offset(11, 2, 3));
        var farm = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm", center, center.offset(3, 2, 3), 5, true);
        var stores = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center.east(8), center.offset(11, 2, 3), 5, true);
        helper.assertTrue(town.putArea(town.leader(), farm) && town.putArea(town.leader(), stores), "the fixture owns both worksites");
        town.setWorksitePlan(town.leader(), farm.id(), new SettlementWorksitePlan(SettlementWorkerSettings.capture(HiredVillagerRole.FARMING,
                new net.minecraft.nbt.CompoundTag()), 2));
        town.setWorksitePlan(town.leader(), stores.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER));
        var residents = new ArrayList<Villager>();
        for (int index = 0; index < population; index++) {
            var villager = EntityType.VILLAGER.create(level);
            villager.setPos(center.east(index).getBottomCenter());
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
            VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
            town.recordResident(SettlementResidentState.capture(villager, town, level.getGameTime()));
            residents.add(villager);
        }
        return new Fixture(town, farm, stores, residents);
    }

    record Fixture(SettlementRecord town, SettlementArea farm, SettlementArea stores, List<Villager> residents) { }
}
