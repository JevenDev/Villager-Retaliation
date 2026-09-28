package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementBackgroundHaulingGameTests {
    private SettlementBackgroundHaulingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void unloadedCouriersMoveRealStockWithoutLoadingTheTown(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.town().leader(), fixture.source().id(), new ItemStack(Items.IRON_INGOT), 13);
            fixture.town().setDepotStockTarget(fixture.town().leader(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "the town is unloaded before simulation");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.destination().getItem(0).getCount(), 7, "the courier fills the actual depot request");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 13, "the source reserve is protected");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "background hauling never loads world chunks");
            helper.assertValueEqual(fixture.worker().status(), "delivered", "the saved worker reports the completed delivery");
            double practice = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(practice > 0.0D, "completed courier delivery earns practice");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), practice, "a satisfied request cannot grant another award");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void backgroundHaulingHonorsTravelTimeAndDoesNotCatchUpOffline(GameTestHelper helper) {
        fixture(helper, fixture -> {
            SettlementDepot distant = depot(fixture, fixture.town().center().east(60), SettlementDepotPurpose.MATERIALS, 9);
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.town().leader(), distant.id(), new ItemStack(Items.IRON_INGOT), 7);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 10_000_000L);
            helper.assertTrue(distant.isEmpty(), "a large time gap grants only one running interval of travel");
            double progress = fixture.worker().progress();
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 10_000_000L);
            helper.assertValueEqual(fixture.worker().progress(), progress, "replaying a simulation time grants no extra work");
            for (long now = 10_000_200L; now <= 10_001_400L; now += 200L) {
                SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
            }
            helper.assertValueEqual(distant.getItem(0).getCount(), 7, "the shipment arrives after its travel time");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 13, "one delivered batch is removed exactly once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void observedDepotRulesPersistAndBlockUnloadedTransfers(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.source().observeRules(List.of(new ItemStack(Items.BREAD)));
            fixture.town().setDepotStockTarget(fixture.town().leader(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertTrue(saved.depots().get(fixture.source().id()).rulesObserved(), "the rule observation survives saving");
            helper.assertTrue(saved.depots().get(fixture.source().id()).observedRules().getFirst().is(Items.BREAD), "the actual framed item persists");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.destination().isEmpty(), "a denied source cannot provide background cargo");
            fixture.source().observeRules(List.of());
            fixture.destination().observeRules(List.of(new ItemStack(Items.BREAD)));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertTrue(fixture.destination().isEmpty(), "a denied destination cannot receive background cargo");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 20, "blocked transfers consume no items");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void competingBackgroundCouriersFillDifferentUnmetRequests(GameTestHelper helper) {
        fixture(helper, fixture -> {
            SettlementDepot second = depot(fixture, fixture.town().center().offset(4, 0, 2), SettlementDepotPurpose.MATERIALS, 4);
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.town().leader(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            fixture.town().setDepotStockTarget(fixture.town().leader(), second.id(), new ItemStack(Items.IRON_INGOT), 7);
            courier(helper, fixture.town(), fixture.area());
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.destination().getItem(0).getCount(), 7, "the first request is filled once");
            helper.assertValueEqual(second.getItem(0).getCount(), 7, "the other courier serves the remaining request");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 6, "concurrent simulation conserves the shared source");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void carriedCargoAndEquipmentReturnFromBackgroundExactlyOnce(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            fixture.town().configureDepot(fixture.town().leader(), fixture.destination().id(), SettlementDepotPurpose.GENERAL, 9);
            var inventory = HiredJobInventory.getJobInventory(fixture.courier());
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            helmet.set(DataComponents.CUSTOM_NAME, Component.literal("Mira's helmet"));
            inventory.setItem(0, helmet);
            inventory.insertOutput(new ItemStack(Items.IRON_INGOT, 7));
            var duty = SettlementWorkforceService.duty(fixture.courier()).orElseThrow();
            fixture.worker().observe(helper.getLevel(), fixture.courier(), duty, 0L);
            CompoundTag staleEntity = new CompoundTag();
            fixture.courier().saveWithoutId(staleEntity);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.destination().count(stack -> stack.is(Items.IRON_INGOT)), 7, "real carried cargo reaches the depot");
            double practice = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(practice > 0.0D, "delivered carried cargo earns courier practice");
            Villager returning = EntityType.VILLAGER.create(helper.getLevel());
            returning.load(staleEntity);
            SettlementBackgroundService.onActive(returning);
            var restoredInventory = HiredJobInventory.getJobInventory(returning);
            helper.assertFalse(restoredInventory.hasOutputItems(), "the stale carried shipment is cleared on return");
            helper.assertValueEqual(restoredInventory.getItem(0).getHoverName().getString(), "Mira's helmet", "unrelated equipment and item components are preserved");
            restoredInventory.insertOutput(new ItemStack(Items.GOLD_INGOT, 3));
            SettlementBackgroundService.onActive(returning);
            helper.assertTrue(restoredInventory.hasOutput(stack -> stack.is(Items.GOLD_INGOT)), "an already applied revision cannot overwrite later inventory changes");
            helper.assertValueEqual(fixture.destination().count(stack -> stack.is(Items.IRON_INGOT)), 7, "returning never repeats the completed delivery");
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), practice, "restoring inventory cannot replay skill growth");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void unobservedDepotsAndDisabledWorkplacesPauseBackgroundHauling(GameTestHelper helper) {
        fixture(helper, fixture -> {
            SettlementDepot unobserved = fixture.town().registerDepot(fixture.town().leader(), fixture.area().id(),
                    fixture.town().center().east(5), SettlementDepotPurpose.MATERIALS);
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.town().leader(), unobserved.id(), new ItemStack(Items.IRON_INGOT), 7);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(unobserved.isEmpty(), "unknown container rules are never treated as unrestricted");
            unobserved.observeRules(List.of());
            var area = fixture.area();
            fixture.town().putArea(fixture.town().leader(), new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(), area.priority(), false));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertTrue(unobserved.isEmpty(), "disabled areas stop their absent workers");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 20, "paused work cannot spend stock");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void savedWorkerProgressAndInventoryKeepTheirIdentity(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.worker().work(60, 1.0D);
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var worker = restored.workers().get(fixture.courier().getUUID());
            helper.assertValueEqual(worker.id(), fixture.courier().getUUID(), "the existing worker identity is persisted");
            helper.assertValueEqual(worker.area(), fixture.area().id(), "the assigned workplace is persisted");
            helper.assertValueEqual(worker.progress(), 60.0D, "unfinished work time survives saving");
            helper.assertValueEqual(worker.inventory(), fixture.worker().inventory(), "the precise inventory snapshot survives saving");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void combatAtUnloadPausesSavedWorkUntilTheWorkerIsObservedSafe(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.town().leader(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            var attacker = EntityType.ZOMBIE.create(level);
            fixture.courier().setTarget(attacker);
            SettlementBackgroundService.onUnloaded(fixture.courier());
            var saved = SettlementWorkerState.load(fixture.worker().save());
            fixture.town().recordWorker(saved);
            long now = level.getGameTime();
            SettlementBackgroundService.update(level, fixture.town(), now + 200L);
            helper.assertTrue(fixture.destination().isEmpty(), "a fighting courier cannot deliver after unloading");
            helper.assertValueEqual(saved.status(), "unsafe", "the saved worker reports its last observed danger");
            helper.assertValueEqual(saved.progress(), 0.0D, "danger cannot accumulate background work credit");
            fixture.courier().setTarget(null);
            SettlementBackgroundService.update(level, fixture.town(), now + 1_000_000L);
            helper.assertTrue(fixture.destination().isEmpty(), "elapsed time alone cannot declare an unobserved worker safe");
            saved.observe(level, fixture.courier(), SettlementWorkforceService.duty(fixture.courier()).orElseThrow(), now + 1_000_000L);
            SettlementBackgroundService.update(level, fixture.town(), now + 1_000_200L);
            int delivered = Math.min(7, com.jvn.villagerretaliation.interaction.HiredVillagerRoles.courierTransferLimit(saved.aptitude()));
            helper.assertValueEqual(fixture.destination().getItem(0).getCount(), delivered, "a new safe observation permits real work again");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 20 - delivered, "resuming transfers exactly one finite batch");
            helper.assertFalse(level.hasChunkAt(fixture.town().center()), "safety checks do not load the absent town");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void fleeingHurtAndBurningWorkersCannotAdvanceBackgroundHauling(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.town().leader(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            var hostile = EntityType.ZOMBIE.create(level);
            var duty = SettlementWorkforceService.duty(fixture.courier()).orElseThrow();
            fixture.courier().getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE, hostile);
            fixture.worker().observe(level, fixture.courier(), duty, 0L);
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertTrue(fixture.destination().isEmpty(), "fleeing without an attack target still pauses work");
            fixture.courier().getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE);
            fixture.courier().setLastHurtByMob(hostile);
            fixture.worker().observe(level, fixture.courier(), duty, 200L);
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            helper.assertTrue(fixture.destination().isEmpty(), "recent attackers remain unsafe even without a threat memory");
            fixture.courier().setLastHurtByMob(null);
            fixture.courier().setRemainingFireTicks(200);
            fixture.worker().observe(level, fixture.courier(), duty, 400L);
            SettlementBackgroundService.update(level, fixture.town(), 600L);
            helper.assertTrue(fixture.destination().isEmpty(), "a burning worker cannot become productive by unloading");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 20, "all unsafe observations preserve shared supplies");
            fixture.courier().clearFire();
            fixture.worker().observe(level, fixture.courier(), duty, 600L);
            SettlementBackgroundService.update(level, fixture.town(), 800L);
            int delivered = Math.min(7, com.jvn.villagerretaliation.interaction.HiredVillagerRoles.courierTransferLimit(fixture.worker().aptitude()));
            helper.assertValueEqual(fixture.destination().getItem(0).getCount(), delivered, "cleared hazards allow the worker to resume");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_hauling")
    public static void olderWorkerSnapshotsPreserveTheirExistingWorkEligibility(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.town().leader(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            var old = fixture.worker().save();
            old.remove("Unsafe");
            fixture.town().recordWorker(SettlementWorkerState.load(old));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.destination().getItem(0).getCount(), 7, "an older snapshot retains its prior eligibility until a new observation");
        });
    }

    private static SettlementDepot depot(Fixture fixture, BlockPos pos, SettlementDepotPurpose purpose, int priority) {
        var depot = fixture.town().registerDepot(fixture.town().leader(), fixture.area().id(), pos, purpose);
        fixture.town().configureDepot(fixture.town().leader(), depot.id(), purpose, priority);
        depot.observeRules(List.of());
        return depot;
    }

    private static Villager courier(GameTestHelper helper, SettlementRecord town, SettlementArea area) {
        Villager courier = EntityType.VILLAGER.create(helper.getLevel());
        courier.setPos(town.center().getCenter());
        courier.setNoAi(true);
        courier.setVillagerData(courier.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(helper.getLevel(), courier, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(courier.getUUID(), area.id());
        SettlementWorkforceService.reconcile(helper.getLevel(), courier);
        town.recordResident(SettlementResidentState.capture(courier, town, 0L));
        var worker = SettlementWorkerState.capture(helper.getLevel(), courier, SettlementWorkforceService.duty(courier).orElseThrow(), 0L);
        CompoundTag skilled = worker.save();
        skilled.putInt("Aptitude", 60);
        town.recordWorker(SettlementWorkerState.load(skilled));
        return courier;
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos center = new BlockPos(8_000_000 + origin.getX() * 8, 64, 8_000_000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Distribution", center, center.offset(63, 2, 3), 5, true);
        SettlementTestPlots.claim(level, town, area.min(), area.max());
        town.putArea(town.leader(), area);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Homes", center.west(3), center.west(), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min(), home.max()), List.of(), true));
        Villager courier = courier(helper, town, area);
        Fixture seed = new Fixture(town, area, null, null, courier, town.workers().get(courier.getUUID()));
        var source = depot(seed, center.east(), SettlementDepotPurpose.OUTPUT, 5);
        var destination = depot(seed, center.east(4), SettlementDepotPurpose.MATERIALS, 9);
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, area, source, destination, courier, seed.worker()));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementDepot source, SettlementDepot destination,
                           Villager courier, SettlementWorkerState worker) {
    }
}
