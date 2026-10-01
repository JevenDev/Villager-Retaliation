package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAppearance;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementWorkerPoolAssignmentGameTests {
    private SettlementWorkerPoolAssignmentGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_assignment")
    public static void poolMovesUseNativeDutiesAndAppearanceWithoutManualPins(GameTestHelper helper) {
        var fixture = ready(helper, 2);
        var town = fixture.town();
        town.setManualManagement(town.leader(), true);
        var pool = pool(helper, fixture, 0);
        var resident = fixture.residents().getFirst().getUUID();
        var demand = UUID.randomUUID();
        var move = new SettlementWorkerPool.Move(100, resident, null, fixture.farm().id(),
                SettlementWorkerPool.Reason.PROTECTED_SHORTAGE, demand, 9);
        helper.assertTrue(SettlementWorkforceService.assignFromPool(helper.getLevel(), town, pool, move), "an explicitly authorized pool operates in a manual town");
        helper.assertValueEqual(town.duties().get(resident), fixture.farm().id(), "native civic duty owns the new job");
        helper.assertFalse(town.manuallyStaffed(resident), "automatic movement does not create a manual pin");
        var context = town.residents().get(resident).context(helper.getLevel());
        SettlementWorkforceService.reconcile(helper.getLevel(), context);
        helper.assertValueEqual(VillagerWorkAppearance.profession(context), VillagerProfession.FARMER, "the native assignment publishes farmer clothing");
        helper.assertValueEqual(context.getVillagerData().getProfession(), VillagerProfession.TOOLSMITH, "the real profession remains unchanged");
        helper.assertValueEqual(town.workerPools().get(pool.id()).history().getLast(), move, "the same demand and priority explain the move");
        helper.assertValueEqual(town.workerPoolCooldown(resident, 100), 1_200L, "successful assignment starts the durable cooldown");
        helper.assertFalse(SettlementWorkforceService.assignFromPool(helper.getLevel(), town, pool, move), "an old pool object cannot perform another move");
        var second = fixture.residents().getLast().getUUID();
        helper.assertFalse(move(helper, town, pool.id(), second, fixture.stores().id(), 100), "one pool cannot move twice at the same cycle timestamp");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "background reassignment never loads the remote worksite");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_assignment")
    public static void currentLimitsPinsAndMembershipAreRecheckedBeforeEveryMove(GameTestHelper helper) {
        var fixture = ready(helper, 3);
        var town = fixture.town();
        for (var resident : fixture.residents()) staff(helper, town, resident, fixture.farm());
        var pool = pool(helper, fixture, 1);
        var first = fixture.residents().getFirst().getUUID();
        var second = fixture.residents().get(1).getUUID();
        var third = fixture.residents().getLast().getUUID();
        town.rememberManualDuty(first);
        helper.assertFalse(move(helper, town, pool.id(), first, fixture.stores().id(), 100), "enrolled manual assignments remain pinned");
        helper.assertTrue(town.returnToAutomaticStaffing(town.leader(), first), "only an explicit return releases the pin");
        helper.assertTrue(move(helper, town, pool.id(), first, fixture.stores().id(), 100), "the released resident may move");
        helper.assertFalse(move(helper, town, pool.id(), second, fixture.stores().id(), 1_300), "the cap counts another worker already at the destination");
        town.removeDuty(first);
        town.removeDuty(third);
        helper.assertFalse(move(helper, town, pool.id(), second, fixture.stores().id(), 1_300), "the source minimum protects its last worker");
        var current = town.workerPools().get(pool.id());
        helper.assertTrue(town.configureWorkerPool(town.leader(), pool.id(), current.revision(),
                new SettlementWorkerPool.Configuration("Changed pool", List.of(first),
                        List.of(new SettlementWorkerPool.Staffing(fixture.stores().id(), 0, 1))), true), "membership can be explicitly edited");
        helper.assertFalse(SettlementWorkforceService.assignFromPool(helper.getLevel(), town, current,
                new SettlementWorkerPool.Move(1_300, second, fixture.farm().id(), fixture.stores().id(),
                        SettlementWorkerPool.Reason.STAFFING, null, 5)), "a stale candidate cannot bypass changed membership");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_assignment")
    public static void physicalActionsTemporaryTasksAndCargoFinishBeforeReassignment(GameTestHelper helper) {
        var fixture = ready(helper, 1);
        var town = fixture.town();
        var villager = fixture.residents().getFirst();
        staff(helper, town, villager, fixture.farm());
        var worker = town.workers().get(villager.getUUID());
        var pool = pool(helper, fixture, 0);
        worker.settings().putInt("ProgressTicks", 12);
        helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_physical_action", "an incomplete physical action keeps its worker");
        worker.settings().remove("ProgressTicks");
        HiredTemporaryQueue.storePending(worker.settings(), null);
        helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_temporary_task", "a pending temporary action retains player control");
        worker.settings().remove(HiredTemporaryQueue.PENDING_TAG);
        var inventory = worker.items(helper.getLevel().registryAccess());
        inventory.set(worker.outputSlots().getFirst(), new ItemStack(Items.WHEAT, 3));
        worker.updateInventory(helper.getLevel().registryAccess(), inventory);
        helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_delivery", "carried output must be delivered first");
        helper.assertFalse(move(helper, town, pool.id(), villager.getUUID(), fixture.stores().id(), 100), "cargo cannot change its destination through staffing");
        helper.assertValueEqual(worker.items(helper.getLevel().registryAccess()).get(worker.outputSlots().getFirst()).getCount(), 3, "blocked movement preserves physical cargo");
        inventory.set(worker.outputSlots().getFirst(), ItemStack.EMPTY);
        worker.updateInventory(helper.getLevel().registryAccess(), inventory);
        var context = new HiredWorkContext(HiredJobInventory.getJobInventory(villager), worker.settings(), fixture.farm().center(),
                fixture.farm().min(), fixture.farm().max(), 4, 2, true, 0, true, true);
        LoggingHarvestPlan.begin(context, fixture.farm().min(), fixture.farm().min(), List.of(fixture.farm().min()),
                List.of(), List.of(), ItemStack.EMPTY, false, "oak");
        helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_physical_action", "a started tree cannot be abandoned between blocks");
        LoggingHarvestPlan.clear(context);
        helper.assertTrue(move(helper, town, pool.id(), villager.getUUID(), fixture.stores().id(), 100), "the worker can move after the native action and delivery finish");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_assignment")
    public static void loadedHealthCombatTradingAndArrivalRemainAuthoritative(GameTestHelper helper) {
        var fixture = ready(helper, 1);
        var town = fixture.town();
        var villager = fixture.residents().getFirst();
        villager.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        villager.setNoAi(true);
        villager.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(villager), "the candidate is present in the live world");
        var pool = pool(helper, fixture, 0);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PoolTestTrader"));
        try {
            villager.setHealth(4);
            helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_recovery", "live injury overrides an older healthy observation");
            villager.setHealth(villager.getMaxHealth());
            villager.setTarget(player);
            helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_combat", "combat blocks moves immediately");
            villager.setTarget(null);
            villager.setTradingPlayer(player);
            helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_trading", "an open trade retains the resident");
            villager.setTradingPlayer(null);
            player.setPos(villager.position());
            helper.assertTrue(com.jvn.villagerretaliation.interaction.VillagerConversationService.startForcedIgnoringDisposition(player, villager),
                    "the native conversation session is open");
            helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_conversation", "conversation retains control of the live resident");
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForVillager(villager, false);
            town.recordArrival(new SettlementArrival(villager.getUUID(), villager.saveWithoutId(new CompoundTag())));
            helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_arrival", "arrival completion precedes staffing");
            town.removeArrival(villager.getUUID());
            helper.assertTrue(move(helper, town, pool.id(), villager.getUUID(), fixture.farm().id(), 100), "the healthy idle live resident can be assigned");
            helper.assertValueEqual(VillagerWorkAppearance.profession(villager), VillagerProfession.FARMER, "loaded tracking state receives the native outfit immediately");
        } finally {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForVillager(villager, false);
            villager.setTradingPlayer(null);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_assignment")
    public static void newlyLoadedWorkersCannotLeaveBackgroundCargoBeforeHandoff(GameTestHelper helper) {
        var fixture = ready(helper, 1);
        var town = fixture.town();
        var villager = fixture.residents().getFirst();
        villager.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        villager.setNoAi(true);
        villager.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(villager), "the resident is loaded before the saved worker handoff");
        try {
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(villager.blockPosition()), "the live entity is already eligible for active ticking");
            staff(helper, town, villager, fixture.farm());
            var pool = pool(helper, fixture, 0);
            var worker = town.workers().get(villager.getUUID());
            var carried = worker.items(helper.getLevel().registryAccess());
            carried.set(worker.outputSlots().getFirst(), new ItemStack(Items.WHEAT, 3));
            worker.updateInventory(helper.getLevel().registryAccess(), carried);
            worker.changed();
            helper.assertFalse(HiredJobInventory.getJobInventory(villager).hasOutputItems(), "the live inventory has not applied the newer background version yet");
            helper.assertTrue(worker.needsRestore(villager), "the saved worker owns the newer inventory");
            helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_delivery", "pool safety considers the pending background cargo");
            helper.assertFalse(move(helper, town, pool.id(), villager.getUUID(), fixture.stores().id(), 100), "handoff timing cannot bypass cargo ownership");
            helper.assertTrue(worker.restore(villager), "the native handoff applies its saved inventory");
            var inventory = HiredJobInventory.getJobInventory(villager);
            helper.assertValueEqual(inventory.getItem(inventory.outputSlots().getFirst()).getCount(), 3, "all carried wheat survives the rejected move");
            inventory.setItem(inventory.outputSlots().getFirst(), ItemStack.EMPTY);
            worker.observe(helper.getLevel(), villager, new SettlementWorkforceService.Duty(town, fixture.farm()), helper.getLevel().getGameTime());
            helper.assertTrue(move(helper, town, pool.id(), villager.getUUID(), fixture.stores().id(), 100), "finishing the delivery makes the live worker eligible");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_assignment")
    public static void eligibilityHoursAndPersonalCommitmentsAreNeverOverridden(GameTestHelper helper) {
        var fixture = ready(helper, 1);
        var town = fixture.town();
        var villager = fixture.residents().getFirst();
        var pool = pool(helper, fixture, 0);
        var care = town.residents().get(villager.getUUID()).save();
        care.putBoolean("PersonalCommitment", true);
        town.recordResident(SettlementResidentState.load(care));
        helper.assertFalse(move(helper, town, pool.id(), villager.getUUID(), fixture.farm().id(), 100), "saved personal commitments exclude background residents");
        care.putBoolean("PersonalCommitment", false);
        town.recordResident(SettlementResidentState.load(care));
        town.advance();
        town.advance();
        var tradingPost = new SettlementArea(fixture.farm().id(), SettlementAreaType.TRADING_POST, "Trading post",
                fixture.farm().min(), fixture.farm().max(), 5, true);
        helper.assertTrue(town.putArea(town.leader(), tradingPost), "the destination temporarily requires the restricted trading role");
        helper.assertTrue(town.setWorksitePlan(town.leader(), tradingPost.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.NITWIT)), "the trading post uses its native settings");
        helper.assertFalse(com.jvn.villagerretaliation.interaction.HiredVillagerRoles.availableRoles(helper.getLevel(), villager).contains(HiredVillagerRole.NITWIT),
                "the toolsmith fixture is not eligible for nitwit trading work");
        helper.assertFalse(move(helper, town, pool.id(), villager.getUUID(), tradingPost.id(), 100), "a pool cannot grant an unavailable role");
        helper.assertTrue(town.putArea(town.leader(), fixture.farm()), "the approved farming destination is restored");
        helper.assertTrue(town.setWorksitePlan(town.leader(), fixture.farm().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.FARMING)), "farming uses its native settings");
        long dayTime = helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(12_000);
        try {
            helper.assertFalse(move(helper, town, pool.id(), villager.getUUID(), fixture.farm().id(), 100), "a destination outside working hours is ineligible");
        } finally {
            helper.getLevel().setDayTime(dayTime);
        }
        helper.assertTrue(move(helper, town, pool.id(), villager.getUUID(), fixture.farm().id(), 100), "eligible residents become assignable during their shift");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_assignment")
    public static void independentMachineReceiptsRemainRecoverableAfterPoolMoves(GameTestHelper helper) {
        var fixture = ready(helper, 1);
        var town = fixture.town();
        var villager = fixture.residents().getFirst();
        var site = new SettlementArea(fixture.farm().id(), SettlementAreaType.SMELTERY, "Smeltery",
                fixture.farm().min(), fixture.farm().max(), 5, true);
        helper.assertTrue(town.putArea(town.leader(), site), "the permitted worksite is a smeltery");
        helper.assertTrue(town.setWorksitePlan(town.leader(), site.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.SMELTER)), "the smeltery has its native settings");
        staff(helper, town, villager, site);
        var queue = town.beginProduction(town.leader(), site.id());
        var order = HiredProductionOrder.create(helper.getLevel(),
                HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"), Map.of()),
                HiredProductionOrder.Mode.BATCH, 2, null);
        helper.assertTrue(queue.append(order), "the shared smelting order is configured independently");
        var allocation = queue.reserve(villager.getUUID(), order.id(), 1, 1, 0, site.min());
        var machine = UUID.randomUUID();
        var stations = com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations.get(helper.getLevel());
        helper.assertTrue(stations.reserve(helper.getLevel(), queue, allocation, machine) && queue.start(allocation.id()), "the started batch has a durable machine receipt");
        helper.assertTrue(stations.claim(helper.getLevel(), site.min()).started(), "the native station claim records started ownership");
        var pool = pool(helper, fixture, 0);
        helper.assertTrue(move(helper, town, pool.id(), villager.getUUID(), fixture.stores().id(), 100), "a waiting machine does not monopolize a worker");
        var detached = queue.allocations().getFirst();
        helper.assertTrue(detached.worker() == null && detached.started(), "the native reassignment detaches the started batch");
        helper.assertValueEqual(detached.machine(), machine, "the machine identity is unchanged");
        helper.assertValueEqual(detached.id(), allocation.id(), "the original production receipt survives");
        var claim = stations.claim(helper.getLevel(), site.min());
        helper.assertTrue(claim != null && claim.started(), "native assignment cleanup preserves the occupied station");
        helper.assertValueEqual(claim.allocation(), allocation.id(), "the station still refers to the original batch");
        helper.assertValueEqual(claim.machine(), machine, "station ownership retains the exact machine identity");
        var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        var resumed = restored.productionQueues().get(site.id());
        helper.assertTrue(resumed.resume(allocation.id(), UUID.randomUUID()), "replacement staff can recover the same batch after reload");
        helper.assertTrue(resumed.complete(allocation.id(), new ItemStack(Items.IRON_INGOT)), "recovery settles exactly the original batch");
        helper.assertValueEqual(resumed.order(order.id()).produced(), 1L, "pool movement never duplicates completed output");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_assignment")
    public static void workerOwnedBrewingOrdersMustFinishBeforeLeaving(GameTestHelper helper) {
        var fixture = ready(helper, 1);
        var town = fixture.town();
        var villager = fixture.residents().getFirst();
        var site = new SettlementArea(fixture.farm().id(), SettlementAreaType.BREWERY, "Brewery",
                fixture.farm().min(), fixture.farm().max(), 5, true);
        helper.assertTrue(town.putArea(town.leader(), site), "the selected worksite is a brewery");
        helper.assertTrue(town.setWorksitePlan(town.leader(), site.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.BREWING)), "the brewery uses native settings");
        staff(helper, town, villager, site);
        var worker = town.workers().get(villager.getUUID());
        var order = new SettlementBrewingOrder(ResourceLocation.withDefaultNamespace("potion"),
                ResourceLocation.withDefaultNamespace("healing"), 3, false);
        worker.brewingOrder(order);
        var pool = pool(helper, fixture, 0);
        helper.assertValueEqual(inspect(helper, town, pool, villager).blocker(), "pool_brewing_order", "a worker-owned order has no independent receipt to detach");
        helper.assertFalse(move(helper, town, pool.id(), villager.getUUID(), fixture.stores().id(), 100), "pool assignment cannot cancel the legacy brewing order");
        helper.assertValueEqual(SettlementBrewingService.snapshot(helper.getLevel(), town, villager.getUUID()), order, "blocked reassignment preserves the exact remaining order");
        worker.brewingOrder(null);
        helper.assertTrue(move(helper, town, pool.id(), villager.getUUID(), fixture.stores().id(), 100), "completion releases the brewer for other work");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_assignment")
    public static void constructionLeadAndClaimedAssistantStayUntilSafeBoundaries(GameTestHelper helper) {
        helper.getLevel().setDayTime(4_000);
        var fixture = SettlementConstructionGameTests.fixture(helper);
        var town = fixture.town();
        var lead = fixture.builder();
        var assistant = SettlementConstructionCrewGameTests.assistant(helper, fixture);
        try {
            town.advance();
            SettlementConstructionCrewGameTests.adopt(helper, fixture);
            var project = SettlementConstructionCrewGameTests.begin(helper, fixture);
            helper.assertTrue(project.configureCrew(0, 5, List.of(assistant.getUUID())), "the project explicitly includes its assistant");
            for (var worker : List.of(lead, assistant)) {
                town.recordWorker(SettlementWorkerState.capture(helper.getLevel(), worker,
                        new SettlementWorkforceService.Duty(town, fixture.area()), helper.getLevel().getGameTime()));
            }
            var id = UUID.randomUUID();
            helper.assertTrue(town.configureWorkerPool(town.leader(), id, -1,
                    new SettlementWorkerPool.Configuration("Construction crew", List.of(lead.getUUID(), assistant.getUUID()),
                            List.of(new SettlementWorkerPool.Staffing(fixture.area().id(), 0, 2))), true), "both builders are explicitly enrolled");
            var pool = town.workerPools().get(id);
            helper.assertValueEqual(inspect(helper, town, pool, lead).blocker(), "pool_construction", "moving the lead must not cancel an active project");
            helper.assertTrue(project.crew().acquire(assistant.getUUID(), 0, List.of(fixture.origin()), helper.getLevel().getGameTime()),
                    "the assistant has an actual native placement claim");
            helper.assertValueEqual(inspect(helper, town, pool, assistant).blocker(), "pool_construction", "a claimed placement retains the assistant");
            project.crew().release(assistant.getUUID());
            helper.assertTrue(inspect(helper, town, pool, assistant).eligible(), "an assistant without cargo or a placement claim reaches a safe boundary");
            helper.assertTrue(project.active(), "staffing checks leave the project active");
            helper.assertValueEqual(project.placed(), 0, "staffing checks never acknowledge a placement");
        } finally {
            assistant.discard();
            lead.discard();
        }
        helper.succeed();
    }

    static SettlementWorkerPoolGameTests.Fixture ready(GameTestHelper helper, int population) {
        helper.getLevel().setDayTime(4_000);
        var fixture = SettlementWorkerPoolGameTests.fixture(helper, population);
        for (var villager : fixture.residents()) {
            VillagerProfileManager.getOrCreateProfile(helper.getLevel(), villager).replaceSkills(VillagerSkillSet.filled(100),
                    VillagerSkillGenerator.CURRENT_GENERATION_VERSION, helper.getLevel().getGameTime());
        }
        return fixture;
    }

    static SettlementWorkerPool pool(GameTestHelper helper, SettlementWorkerPoolGameTests.Fixture fixture, int minimum) {
        var id = UUID.randomUUID();
        var configuration = new SettlementWorkerPool.Configuration("Pool under test",
                fixture.residents().stream().map(Villager::getUUID).toList(),
                List.of(new SettlementWorkerPool.Staffing(fixture.farm().id(), minimum, 3),
                        new SettlementWorkerPool.Staffing(fixture.stores().id(), 0, 1)));
        helper.assertTrue(fixture.town().configureWorkerPool(fixture.town().leader(), id, -1, configuration, true), "the player explicitly activates a pool");
        return fixture.town().workerPools().get(id);
    }

    static void staff(GameTestHelper helper, SettlementRecord town, Villager villager, SettlementArea area) {
        helper.assertTrue(town.assignDuty(villager.getUUID(), area.id()), "the fixture has an existing native duty");
        SettlementWorkforceService.reconcile(helper.getLevel(), villager);
        town.recordWorker(SettlementWorkerState.capture(helper.getLevel(), villager, new SettlementWorkforceService.Duty(town, area), helper.getLevel().getGameTime()));
        town.recordResident(SettlementResidentState.capture(villager, town, helper.getLevel().getGameTime()));
    }

    private static SettlementWorkerPoolAssignment.Candidate inspect(GameTestHelper helper, SettlementRecord town, SettlementWorkerPool pool, Villager villager) {
        return SettlementWorkerPoolAssignment.inspect(helper.getLevel(), town, pool, villager.getUUID(), 100);
    }

    private static boolean move(GameTestHelper helper, SettlementRecord town, UUID pool, UUID resident, UUID destination, long now) {
        return SettlementWorkforceService.assignFromPool(helper.getLevel(), town, town.workerPools().get(pool),
                new SettlementWorkerPool.Move(now, resident, town.duties().get(resident), destination,
                        SettlementWorkerPool.Reason.STAFFING, null, 5));
    }
}
