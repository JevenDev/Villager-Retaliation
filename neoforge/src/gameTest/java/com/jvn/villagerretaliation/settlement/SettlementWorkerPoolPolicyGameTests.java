package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
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
public final class SettlementWorkerPoolPolicyGameTests {
    private SettlementWorkerPoolPolicyGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_policy")
    public static void configuredChainInheritsUrgencyAndCompletedGatheringReleasesStaff(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var level = helper.getLevel();
        var town = fixture.town();
        var forest = new SettlementArea(fixture.farm().id(), SettlementAreaType.FORESTRY, "Forest",
                fixture.farm().min(), fixture.farm().max(), 1, true);
        helper.assertTrue(town.putArea(town.leader(), forest), "the gathering site has low local priority");
        town.setWorksitePlan(town.leader(), forest.id(), new SettlementWorksitePlan(
                SettlementWorksitePlan.defaults(HiredVillagerRole.LOGGING).settings(), 2));
        var position = town.center().east(5);
        var workshop = new SettlementArea(UUID.randomUUID(), SettlementAreaType.WORKSHOP, "Workshop",
                position, position.offset(2, 2, 3), 1, true);
        helper.assertTrue(town.putArea(town.leader(), workshop), "the intermediate workshop is inside the existing claim");
        town.setWorksitePlan(town.leader(), workshop.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.CRAFTSMAN));
        town.recordSurvey(workshop.id(), new SettlementBuildingSurvey.Survey(0, List.of(), List.of(position), true,
                Map.of(position, ResourceLocation.withDefaultNamespace("crafting_table"))));
        var source = town.registerDepot(town.leader(), fixture.stores().id(), fixture.stores().min(), SettlementDepotPurpose.GENERAL);
        var destination = town.registerDepot(town.leader(), fixture.stores().id(), fixture.stores().min().east(), SettlementDepotPurpose.GENERAL);
        source.observeRules(List.of());
        destination.observeRules(List.of());
        var gather = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER, forest.id(),
                null, new ItemStack(Items.OAK_LOG), null, source.position());
        var production = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, workshop.id(),
                HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("oak_planks"), Map.of()), ItemStack.EMPTY, null, source.position());
        var delivery = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.DELIVERY, fixture.stores().id(),
                null, new ItemStack(Items.OAK_PLANKS), source.position(), destination.position());
        var id = UUID.randomUUID();
        var sample = new ItemStack(Items.OAK_PLANKS);
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, destination.position(), sample, 32);
        var requirement = WorkSupplyDemand.identity(level.registryAccess(), id, WorkSupplyDemand.Kind.STOCK_RESERVE,
                WorkSupplyRequirement.exact("policy_stock", sample, 32));
        var policy = new SettlementSupplyPolicy(id, new SettlementSupplyPolicy.Configuration("Timber supply", target, 9,
                List.of(gather, production, delivery), List.of(
                        new SettlementSupplyPolicy.Connection(gather.id(), production.id(), SettlementPolicyPlanner.inputs(level, town, production, 1).getFirst().id()),
                        new SettlementSupplyPolicy.Connection(production.id(), delivery.id(), SettlementPolicyPlanner.inputs(level, town, delivery, 1).getFirst().id()),
                        new SettlementSupplyPolicy.Connection(delivery.id(), null, requirement))), town.leader(), town::supplyChanged);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        for (int index = 0; index < 4; index++) drain(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.ACTIVE, "the explicitly configured native chain is valid");
        var binding = SettlementPolicyOrders.current(policy, gather);
        helper.assertTrue(binding != null && binding.target() > 0, "native downstream orders create a real gathering quota");
        var needs = new SettlementWorkerPoolNeeds(level, town, SettlementDemandService.inspect(level, town));
        for (var area : List.of(forest, workshop, fixture.stores())) {
            helper.assertTrue(needs.area(area.id()).active(), "each configured stage has real outstanding work");
            helper.assertValueEqual(needs.area(area.id()).priority(), 9, "downstream policy urgency reaches every stage");
        }
        var pool = UUID.randomUUID();
        helper.assertTrue(town.configureWorkerPool(town.leader(), pool, -1, new SettlementWorkerPool.Configuration("Timber crew",
                List.of(fixture.residents().getFirst().getUUID()), List.of(
                        SettlementWorkerPool.Staffing.defaults(town, forest.id()),
                        SettlementWorkerPool.Staffing.defaults(town, workshop.id()),
                        SettlementWorkerPool.Staffing.defaults(town, fixture.stores().id()))), true), "all three worksites are explicitly approved");
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 100);
        var resident = fixture.residents().getFirst().getUUID();
        helper.assertValueEqual(town.duties().get(resident), forest.id(), "the greatest inherited staffing deficit receives the worker");
        helper.assertValueEqual(town.workerPools().get(pool).history().getLast().priority(), 9, "move history retains downstream urgency");
        source.setItem(0, new ItemStack(Items.OAK_LOG, binding.target()));
        drain(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(SettlementPolicyOrders.current(policy, gather).state(), SettlementSupplyPolicy.BindingState.PARKED,
                "real input stock parks the gathering quota");
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 1_300);
        helper.assertValueEqual(town.duties().get(resident), workshop.id(), "the finished gatherer can staff the nearby intermediate producer");
        var move = town.workerPools().get(pool).history().getLast();
        helper.assertValueEqual(move.reason(), SettlementWorkerPool.Reason.COMPLETED_DEMAND, "quota completion explains the reassignment");
        helper.assertValueEqual(move.demand(), binding.id(), "history identifies the exact completed gathering binding");
        helper.assertValueEqual(town.productionQueues().get(workshop.id()).orders().size(), 1, "staffing creates no duplicate production");
        helper.assertValueEqual(town.transferQueues().get(fixture.stores().id()).orders().size(), 1, "staffing creates no duplicate delivery");
        helper.assertFalse(level.hasChunkAt(town.center()), "policy staffing never loads background terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_policy")
    public static void startedCourierReceiptsRetainTheWorkerThroughPartialDeliveryAndReload(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var resident = fixture.residents().getFirst();
        SettlementWorkerPoolAssignmentGameTests.staff(helper, town, resident, fixture.stores());
        var pool = SettlementWorkerPoolAssignmentGameTests.pool(helper, fixture, 0);
        var queue = town.beginTransfers(town.leader(), fixture.stores().id());
        var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(fixture.stores().min(), null),
                new HiredTransferQueue.Endpoint(fixture.stores().min().east(), null), new ItemStack(Items.WHEAT), 6);
        helper.assertTrue(queue.append(order), "the courier has a finite native delivery");
        var allocation = queue.reserve(resident.getUUID(), order.id(), 6);
        helper.assertTrue(queue.picked(allocation.id(), 6), "the native receipt owns six collected items");
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 100);
        helper.assertValueEqual(town.duties().get(resident.getUUID()), fixture.stores().id(), "even an essential food shortage cannot interrupt cargo");
        helper.assertTrue(queue.delivered(allocation.id(), 2), "the native path acknowledges only a partial delivery");
        var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.transferQueues().get(fixture.stores().id()).allocation(resident.getUUID()).amount(), 4,
                "reload retains the undelivered quantity and worker ownership");
        helper.assertValueEqual(SettlementWorkerPoolAssignment.inspect(helper.getLevel(), restored, restored.workerPools().get(pool.id()),
                resident.getUUID(), 1_300).blocker(), "pool_delivery", "reloaded receipts still prevent reassignment");
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 1_300);
        helper.assertTrue(town.workerPools().get(pool.id()).history().isEmpty(), "partial delivery is not a safe movement boundary");
        helper.assertTrue(queue.delivered(allocation.id(), 4), "the original courier finishes the remainder");
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 2_500);
        helper.assertValueEqual(town.duties().get(resident.getUUID()), fixture.farm().id(), "completed delivery releases the worker for the protected shortage");
        helper.assertValueEqual(queue.order(order.id()).delivered(), 6L, "staffing neither loses nor duplicates the delivery receipt");
        helper.succeed();
    }

    private static void drain(Runnable work) {
        int steps = 0;
        while (!SettlementWork.complete(work) && steps++ < 10_000) work.run();
        if (!SettlementWork.complete(work)) throw new AssertionError("Policy staffing fixture exceeded its bounded job count");
    }
}
