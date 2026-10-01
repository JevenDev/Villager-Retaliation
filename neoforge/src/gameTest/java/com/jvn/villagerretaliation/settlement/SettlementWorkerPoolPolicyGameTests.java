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
        helper.assertValueEqual(policy.history().stream().filter(event -> event.kind() == SettlementSupplyPolicy.EventKind.STAFFED).count(), 1L,
                "the policy records its confirmed pool assignment once");
        helper.assertTrue(policy.history().getLast().detail().contains("Forest"), "staffing history names the destination");
        helper.assertValueEqual(policy.history().getLast().at(), level.getGameTime(),
                "catch-up staffing uses confirmation time alongside other policy events");
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
        helper.assertValueEqual(policy.history().stream().filter(event -> event.kind() == SettlementSupplyPolicy.EventKind.STAFFED).count(), 2L,
                "the completed-demand move adds one staffing event");
        helper.assertTrue(policy.history().getLast().detail().contains("Forest -> Workshop"), "the policy explains the reassignment");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.supplyPolicies().get(policy.id()).history(), policy.history(), "staffing history survives reload");
        var history = policy.history();
        helper.assertFalse(SettlementWorkforceService.assignFromPool(level, town, town.workerPools().get(pool), move),
                "replaying a completed move is rejected");
        helper.assertValueEqual(policy.history(), history, "rejected movement does not append history");
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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_policy")
    public static void policyAndPoolIssuesLinkExactControlsAndReusePersistentNotificationThrottling(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var level = helper.getLevel();
        var town = fixture.town();
        var depot = town.registerDepot(town.leader(), fixture.stores().id(), fixture.stores().min(), SettlementDepotPurpose.GENERAL);
        depot.observeRules(List.of());
        var step = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER, fixture.farm().id(),
                null, new ItemStack(Items.WHEAT), null, depot.position());
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration("Food chain",
                new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, depot.position(), new ItemStack(Items.WHEAT), 8),
                5, List.of(step), List.of()), town.leader(), town::supplyChanged);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        UUID delegate = UUID.randomUUID(), poolId = UUID.randomUUID();
        town.setPermissions(town.leader(), delegate, java.util.Set.of(SettlementPermission.STAFF));
        helper.assertTrue(town.configureWorkerPool(delegate, poolId, -1, new SettlementWorkerPool.Configuration("Food crew",
                List.of(fixture.residents().getFirst().getUUID()),
                List.of(SettlementWorkerPool.Staffing.defaults(town, fixture.farm().id()))), true), "the delegate explicitly authorizes the pool");
        helper.assertTrue(coordinationIssues(level, town).isEmpty(), "healthy coordination creates no issue");
        policy.blocker("policy_rechecking");
        helper.assertTrue(coordinationIssues(level, town).isEmpty(), "a transient allocation refresh creates no issue");
        policy.state(SettlementSupplyPolicy.State.SUSPENDED, town.leader(), "policy_recipe_unavailable", level.getGameTime());
        town.setPermissions(town.leader(), delegate, java.util.Set.of());
        var issues = coordinationIssues(level, town);
        helper.assertValueEqual(issues.size(), 2, "configuration and authorization blockers are visible together");
        var policyIssue = issues.stream().filter(issue -> issue.kind()
                == com.jvn.villagerretaliation.network.SettlementManagementPayload.IssueKind.POLICY).findFirst().orElseThrow();
        var poolIssue = issues.stream().filter(issue -> issue.kind()
                == com.jvn.villagerretaliation.network.SettlementManagementPayload.IssueKind.WORKER_POOL).findFirst().orElseThrow();
        helper.assertValueEqual(policyIssue.targetId(), policy.id(), "the issue opens the exact policy");
        helper.assertValueEqual(poolIssue.targetId(), poolId, "the issue opens the exact pool");
        helper.assertValueEqual(poolIssue.cause(), "pool_permission_changed", "the lost staffing authority is explained");
        helper.assertTrue(issues.stream().allMatch(com.jvn.villagerretaliation.network.SettlementManagementPayload.Issue::needsAttention),
                "unexpected coordination blockers need attention");
        var keys = issues.stream().map(issue -> issue.id() + ":" + issue.cause()).collect(java.util.stream.Collectors.toSet());
        var notifications = new SettlementNotifications.Problems();
        helper.assertValueEqual(notifications.update(keys, 0), 0, "one observation remains quiet");
        helper.assertValueEqual(notifications.update(keys, 200), 2, "persistent new coordination blockers notify once");
        helper.assertValueEqual(notifications.update(keys, 1_600), 0, "unchanged coordination blockers never repeat");
        var before = town.save(level.registryAccess());
        helper.assertValueEqual(coordinationIssues(level, town), issues, "repeated inspection retains stable issue identities");
        helper.assertValueEqual(town.save(level.registryAccess()), before, "issue inspection changes no saved coordination state");
        var viewer = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CoordinationViewer"));
        var request = new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(town.id().value(),
                com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES, 0, "", 1, false);
        helper.assertTrue(SettlementManagementService.snapshot(viewer, request) == null, "outsiders cannot inspect coordination issues");
        town.setPermissions(town.leader(), viewer.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var snapshot = SettlementManagementService.snapshot(viewer, request);
        helper.assertTrue(snapshot.entries().contains(policyIssue) && snapshot.entries().contains(poolIssue),
                "view delegates receive the same authoritative issue targets");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.encode(buffer, snapshot);
            var restored = com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(restored.entries(), snapshot.entries(), "typed coordination issues survive the wire format");
            helper.assertValueEqual(buffer.readableBytes(), 0, "the snapshot consumes its exact payload");
        } finally { buffer.release(); }
        policy.state(SettlementSupplyPolicy.State.SUSPENDED, town.leader(), "policy_order_suspended", level.getGameTime());
        var stopped = coordinationIssues(level, town).stream().filter(issue -> issue.targetId().equals(policy.id())).findFirst().orElseThrow();
        helper.assertValueEqual(stopped.id(), policyIssue.id(), "a changed blocker preserves the selected policy issue");
        helper.assertFalse(stopped.needsAttention(), "a deliberate native-order pause stays visible without warning notifications");
        policy.state(SettlementSupplyPolicy.State.PAUSED, town.leader(), "", level.getGameTime());
        helper.assertTrue(town.changeWorkerPool(town.leader(), poolId, town.workerPools().get(poolId).revision(), false),
                "the owner acknowledges the suspended pool by pausing it");
        helper.assertTrue(coordinationIssues(level, town).isEmpty(), "paused coordination does not retain old warning issues");
        town.setPermissions(town.leader(), viewer.getUUID(), java.util.Set.of());
        helper.assertTrue(SettlementManagementService.snapshot(viewer, request) == null, "revocation hides coordination immediately");
        helper.succeed();
    }

    private static List<com.jvn.villagerretaliation.network.SettlementManagementPayload.Issue> coordinationIssues(
            net.minecraft.server.level.ServerLevel level, SettlementRecord town) {
        return SettlementIssueService.issues(level, town, SettlementProgressionService.assess(level, town), List.of()).stream()
                .filter(issue -> issue.kind() == com.jvn.villagerretaliation.network.SettlementManagementPayload.IssueKind.POLICY
                        || issue.kind() == com.jvn.villagerretaliation.network.SettlementManagementPayload.IssueKind.WORKER_POOL).toList();
    }

    private static void drain(Runnable work) {
        int steps = 0;
        while (!SettlementWork.complete(work) && steps++ < 10_000) work.run();
        if (!SettlementWork.complete(work)) throw new AssertionError("Policy staffing fixture exceeded its bounded job count");
    }
}
