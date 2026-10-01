package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementWorkerPoolSchedulingGameTests {
    private SettlementWorkerPoolSchedulingGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void staffingRequiresEnrollmentAndMovesOnlyOncePerBudgetedCycle(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 3);
        var town = fixture.town();
        cycle(helper, town, 100);
        helper.assertTrue(town.duties().isEmpty(), "town autonomy alone never enrolls workers");
        town.setManualManagement(town.leader(), true);
        var pool = SettlementWorkerPoolAssignmentGameTests.pool(helper, fixture, 0);
        var work = SettlementWorkerPoolService.cycle(helper.getLevel(), town, 100);
        work.run();
        helper.assertTrue(town.duties().isEmpty(), "the first budget unit cannot scan and move the entire pool");
        drain(work);
        helper.assertValueEqual(town.duties().size(), 1, "only one enrolled resident moves during this cycle");
        helper.assertValueEqual(town.workerPools().get(pool.id()).history().size(), 1, "one completed staffing decision is recorded");
        cycle(helper, town, 300);
        helper.assertValueEqual(town.duties().size(), 2, "a later cycle can assign another eligible member");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "budgeted planning never loads remote terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void equalUrgencyKeepsWorkersWhileHigherPriorityRespectsCooldown(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var forest = new SettlementArea(fixture.farm().id(), SettlementAreaType.FORESTRY, "Forest",
                fixture.farm().min(), fixture.farm().max(), 5, true);
        helper.assertTrue(town.putArea(town.leader(), forest), "the source has no food shortage priority");
        helper.assertTrue(town.setWorksitePlan(town.leader(), forest.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.LOGGING)), "both jobs request one worker");
        var resident = fixture.residents().getFirst();
        SettlementWorkerPoolAssignmentGameTests.staff(helper, town, resident, forest);
        var pool = SettlementWorkerPoolAssignmentGameTests.pool(helper, fixture, 0);
        for (long now : List.of(100L, 1_300L, 2_500L)) cycle(helper, town, now);
        helper.assertValueEqual(town.duties().get(resident.getUUID()), forest.id(), "equal urgency cannot steal the only worker and oscillate");
        helper.assertTrue(town.workerPools().get(pool.id()).history().isEmpty(), "tie-breaking does not create needless moves");
        priority(town, fixture.stores(), 8);
        cycle(helper, town, 3_000);
        helper.assertValueEqual(town.duties().get(resident.getUUID()), fixture.stores().id(), "a higher-priority shortage of staff justifies moving");
        helper.assertValueEqual(town.workerPools().get(pool.id()).history().getLast().reason(), SettlementWorkerPool.Reason.PRIORITY, "the decision explains the priority change");
        priority(town, forest, 9);
        cycle(helper, town, 3_200);
        helper.assertValueEqual(town.duties().get(resident.getUUID()), fixture.stores().id(), "changed priorities cannot bypass a recent move");
        cycle(helper, town, 4_200);
        helper.assertValueEqual(town.duties().get(resident.getUUID()), forest.id(), "the higher priority takes effect after the full cooldown");
        priority(town, forest, 8);
        priority(town, fixture.stores(), 9);
        cycle(helper, town, 200);
        cycle(helper, town, 1_399);
        helper.assertValueEqual(town.duties().get(resident.getUUID()), forest.id(), "clock rollback still requires the full cooldown");
        cycle(helper, town, 1_400);
        helper.assertValueEqual(town.duties().get(resident.getUUID()), fixture.stores().id(), "old history cannot block staffing indefinitely after a clock rollback");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void protectedFoodShortagesOutrankOptionalStaffing(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        priority(town, fixture.stores(), 9);
        var pool = SettlementWorkerPoolAssignmentGameTests.pool(helper, fixture, 0);
        cycle(helper, town, 100);
        helper.assertValueEqual(town.duties().get(fixture.residents().getFirst().getUUID()), fixture.farm().id(),
                "a known protected food shortage outranks optional priority-nine staffing");
        var move = town.workerPools().get(pool.id()).history().getLast();
        helper.assertValueEqual(move.reason(), SettlementWorkerPool.Reason.PROTECTED_SHORTAGE, "the protected shortage explains the move");
        helper.assertTrue(move.demand() != null, "the shared food demand has a stable identity in staffing history");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void skillSuitabilitySelectsTheWorkerWithinTheChosenDestination(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 2);
        var town = fixture.town();
        var first = fixture.residents().getFirst();
        var second = fixture.residents().getLast();
        VillagerProfileManager.getOrCreateProfile(helper.getLevel(), first).replaceSkills(VillagerSkillSet.filled(35),
                VillagerSkillGenerator.CURRENT_GENERATION_VERSION, helper.getLevel().getGameTime());
        VillagerProfileManager.getOrCreateProfile(helper.getLevel(), second).replaceSkills(VillagerSkillSet.filled(90),
                VillagerSkillGenerator.CURRENT_GENERATION_VERSION, helper.getLevel().getGameTime());
        var id = UUID.randomUUID();
        helper.assertTrue(town.configureWorkerPool(town.leader(), id, -1, new SettlementWorkerPool.Configuration("Farm specialists",
                List.of(first.getUUID(), second.getUUID()), List.of(new SettlementWorkerPool.Staffing(fixture.farm().id(), 0, 2))), true),
                "both residents are explicitly eligible for the same destination");
        cycle(helper, town, 100);
        helper.assertFalse(town.duties().containsKey(first.getUUID()), "the first listed resident receives no arbitrary preference");
        helper.assertValueEqual(town.duties().get(second.getUUID()), fixture.farm().id(), "the stronger relevant skills win before distance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void completedOrdersReleaseWorkersAndRetainTheirCause(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var site = new SettlementArea(fixture.farm().id(), SettlementAreaType.SMELTERY, "Smeltery",
                fixture.farm().min(), fixture.farm().max(), 9, true);
        helper.assertTrue(town.putArea(town.leader(), site), "the source is a high-priority smeltery");
        helper.assertTrue(town.setWorksitePlan(town.leader(), site.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.SMELTER)), "smeltery settings are native");
        var worker = fixture.residents().getFirst();
        SettlementWorkerPoolAssignmentGameTests.staff(helper, town, worker, site);
        var queue = town.beginProduction(town.leader(), site.id());
        var order = HiredProductionOrder.create(helper.getLevel(),
                HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"), Map.of()),
                HiredProductionOrder.Mode.BATCH, 1, null);
        helper.assertTrue(queue.append(order), "the worksite has a finite native order");
        var allocation = queue.reserve(worker.getUUID(), order.id(), 1, 1, 0, null);
        helper.assertTrue(queue.start(allocation.id()) && queue.complete(allocation.id(), new ItemStack(Items.IRON_INGOT)), "the order is actually complete");
        var pool = SettlementWorkerPoolAssignmentGameTests.pool(helper, fixture, 0);
        cycle(helper, town, 100);
        helper.assertValueEqual(town.duties().get(worker.getUUID()), fixture.stores().id(), "completed demand yields staff even to a lower-priority worksite");
        var move = town.workerPools().get(pool.id()).history().getLast();
        helper.assertValueEqual(move.reason(), SettlementWorkerPool.Reason.COMPLETED_DEMAND, "completion is the recorded reason");
        helper.assertValueEqual(move.demand(), order.id(), "history identifies the exact completed consumer");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void anEmptyConstructionWorksiteDoesNotCompeteWithExistingWork(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        town.advance();
        var site = new SettlementArea(fixture.farm().id(), SettlementAreaType.CONSTRUCTION, "Construction",
                fixture.farm().min(), fixture.farm().max(), 9, true);
        helper.assertTrue(town.putArea(town.leader(), site), "the approved construction worksite has no active project");
        helper.assertTrue(town.setWorksitePlan(town.leader(), site.id(), new SettlementWorksitePlan(
                SettlementWorksitePlan.defaults(HiredVillagerRole.BUILDER).settings(), 3)), "the site allows several builders when a project exists");
        var pool = SettlementWorkerPoolAssignmentGameTests.pool(helper, fixture, 0);
        cycle(helper, town, 100);
        helper.assertValueEqual(town.duties().get(fixture.residents().getFirst().getUUID()), fixture.stores().id(),
                "a nominal priority-nine worksite cannot reserve staff for nonexistent construction");
        helper.assertValueEqual(town.workerPools().get(pool.id()).history().getLast().reason(), SettlementWorkerPool.Reason.STAFFING,
                "the decision records an ordinary staffing need without inventing a completed project");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void pausedConfigurationCancelsIncrementalPlanning(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var pool = SettlementWorkerPoolAssignmentGameTests.pool(helper, fixture, 0);
        var work = SettlementWorkerPoolService.cycle(helper.getLevel(), town, 100);
        for (int step = 0; step < 5; step++) work.run();
        helper.assertTrue(town.changeWorkerPool(town.leader(), pool.id(), pool.revision(), false), "the player pauses while the budgeted plan is in flight");
        drain(work);
        helper.assertTrue(town.duties().isEmpty(), "an old planning snapshot cannot move a worker after a pause");
        helper.assertTrue(town.workerPools().get(pool.id()).history().isEmpty(), "cancelled planning records no fictional move");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void distanceBreaksEqualSkillTiesBeforeResidentIdentity(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 2);
        var town = fixture.town();
        var ordered = fixture.residents().stream().sorted(java.util.Comparator.comparing(net.minecraft.world.entity.npc.Villager::getUUID)).toList();
        var farther = ordered.getFirst();
        var nearer = ordered.getLast();
        farther.setPos(fixture.farm().center().west(20).getBottomCenter());
        nearer.setPos(fixture.farm().center().getBottomCenter());
        for (var resident : ordered) town.recordResident(SettlementResidentState.capture(resident, town, helper.getLevel().getGameTime()));
        var pool = UUID.randomUUID();
        helper.assertTrue(town.configureWorkerPool(town.leader(), pool, -1, new SettlementWorkerPool.Configuration("Nearby farmers",
                ordered.stream().map(net.minecraft.world.entity.npc.Villager::getUUID).toList(),
                List.of(new SettlementWorkerPool.Staffing(fixture.farm().id(), 0, 2))), true), "both equally skilled residents are enrolled");
        cycle(helper, town, 100);
        helper.assertValueEqual(town.duties().get(nearer.getUUID()), fixture.farm().id(), "the nearby resident wins despite the other resident's lower identity");
        helper.assertFalse(town.duties().containsKey(farther.getUUID()), "list order and UUID cannot override distance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void stableResidentIdentityBreaksEqualDistanceTies(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 3);
        var town = fixture.town();
        var ordered = fixture.residents().stream().map(net.minecraft.world.entity.npc.Villager::getUUID).sorted().toList();
        for (var resident : fixture.residents()) {
            resident.setPos(fixture.farm().center().getBottomCenter());
            town.recordResident(SettlementResidentState.capture(resident, town, helper.getLevel().getGameTime()));
        }
        var pool = UUID.randomUUID();
        helper.assertTrue(town.configureWorkerPool(town.leader(), pool, -1, new SettlementWorkerPool.Configuration("Equal candidates",
                ordered.reversed(), List.of(new SettlementWorkerPool.Staffing(fixture.farm().id(), 0, 2))), true), "membership deliberately reverses stable identity order");
        cycle(helper, town, 100);
        helper.assertValueEqual(town.workerPools().get(pool).history().getLast().resident(), ordered.getFirst(), "stable identity resolves the exact skill and distance tie");
        helper.assertValueEqual(town.duties().size(), 1, "tie resolution still permits only one move");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_scheduling")
    public static void satisfiedPoolSkipsDemandPlanningUntilStaffingNeedsChange(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 35);
        var town = fixture.town();
        for (var resident : fixture.residents().subList(0, 2))
            SettlementWorkerPoolAssignmentGameTests.staff(helper, town, resident, fixture.farm());
        var pool = UUID.randomUUID();
        helper.assertTrue(town.configureWorkerPool(town.leader(), pool, -1, new SettlementWorkerPool.Configuration("Satisfied farm",
                fixture.residents().stream().map(net.minecraft.world.entity.npc.Villager::getUUID).toList(),
                List.of(new SettlementWorkerPool.Staffing(fixture.farm().id(), 0, 32))), true), "the pool has capacity beyond its desired staffing");
        var work = SettlementWorkerPoolService.cycle(helper.getLevel(), town, 100);
        for (int steps = 0; steps < 40 && !SettlementWork.complete(work); steps++) work.run();
        helper.assertTrue(SettlementWork.complete(work), "a satisfied pool completes without scanning demands and all 35 candidates");
        helper.assertTrue(town.workerPools().get(pool).history().isEmpty(), "a satisfied pool does not create a move");
        var plan = town.worksitePlans().get(fixture.farm().id());
        helper.assertTrue(town.setWorksitePlan(town.leader(), fixture.farm().id(), new SettlementWorksitePlan(plan.settings(), 3)),
                "the player increases desired staffing");
        cycle(helper, town, 200);
        helper.assertValueEqual(town.duties().size(), 3, "the next cycle fills the new staffing need");
        helper.assertValueEqual(town.workerPools().get(pool).history().size(), 1, "the changed target records exactly one move");
        helper.succeed();
    }

    static void cycle(GameTestHelper helper, SettlementRecord town, long now) {
        drain(SettlementWorkerPoolService.cycle(helper.getLevel(), town, now));
    }

    private static void drain(Runnable work) {
        int steps = 0;
        while (!SettlementWork.complete(work) && steps++ < 10_000) work.run();
        if (!SettlementWork.complete(work)) throw new AssertionError("Worker-pool planning exceeded the bounded fixture budget");
    }

    private static void priority(SettlementRecord town, SettlementArea area, int priority) {
        if (!town.putArea(town.leader(), new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(), priority, true)))
            throw new AssertionError("Unable to update fixture worksite priority");
    }
}
