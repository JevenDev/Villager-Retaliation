package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyAllocation;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementAllocationGameTests {
    private SettlementAllocationGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void allocationUsesPriorityAndStableIdentityInsteadOfRequestOrder(GameTestHelper helper) {
        var sample = new ItemStack(Items.WHEAT);
        var first = request(helper, new UUID(0, 1), sample, 3, 5, 0, 0);
        var second = request(helper, new UUID(0, 2), sample, 3, 5, 0, 0);
        var stock = List.of(new WorkSupplyAllocation.Stock(BlockPos.ZERO, sample, 3, 0, 0, true));
        for (var order : List.of(List.of(first, second), List.of(second, first))) {
            var allocation = WorkSupplyAllocation.allocate(order, stock);
            helper.assertValueEqual(allocation.allowance(Set.of(first.demand().consumer()), BlockPos.ZERO, sample), 3L,
                    "equal priorities use the stable consumer identity");
            helper.assertValueEqual(allocation.allowance(Set.of(second.demand().consumer()), BlockPos.ZERO, sample), 0L,
                    "a worker running first cannot take another consumer's stock");
            helper.assertValueEqual(row(allocation, second).blocker().competingConsumer(), first.demand().consumer(),
                    "the blocked request names the competing consumer");
        }
        var urgent = request(helper, second.demand().consumer(), sample, 3, 9, 0, 0);
        var changed = WorkSupplyAllocation.allocate(List.of(first, urgent), stock);
        helper.assertValueEqual(changed.allowance(Set.of(urgent.demand().consumer()), BlockPos.ZERO, sample), 3L,
                "a changed priority reallocates unstarted material");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void startedWorkAndEssentialNutritionPrecedeOptionalDemand(GameTestHelper helper) {
        var bread = new ItemStack(Items.BREAD);
        var started = request(helper, new UUID(0, 1), bread, 2, 0, 0, 2);
        var foodConsumer = new UUID(0, 2);
        var foodNeed = SettlementFoodService.requirement(15);
        var food = new WorkSupplyAllocation.Request(new WorkSupplyDemand(
                WorkSupplyDemand.identity(helper.getLevel().registryAccess(), foodConsumer, WorkSupplyDemand.Kind.FOOD, foodNeed),
                foodConsumer, WorkSupplyDemand.Kind.FOOD, foodNeed, 15, 15, 15, 1,
                0, 0, 0, 0, true, List.of(BlockPos.ZERO), null, WorkSupplyDemand.Blocker.of("")), 0);
        var optional = request(helper, new UUID(0, 3), bread, 4, 9, 0, 0);
        var allocation = WorkSupplyAllocation.allocate(List.of(optional, food, started),
                List.of(new WorkSupplyAllocation.Stock(BlockPos.ZERO, bread, 5, 0, 0, true)));
        helper.assertValueEqual(row(allocation, started).availableNow(), 2L, "started work retains the inputs needed to finish");
        helper.assertValueEqual(row(allocation, food).availableNow(), 15L, "essential nutrition receives the remaining bread before priority nine work");
        helper.assertValueEqual(allocation.allowance(Set.of(foodConsumer), BlockPos.ZERO, bread), 3L,
                "protected stock remains legal for its intended consumer");
        helper.assertValueEqual(allocation.allowance(Set.of(optional.demand().consumer()), BlockPos.ZERO, bread), 0L,
                "optional work cannot appropriate protected meals");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void committedItemsKeepTheirOwnerWhenPrioritiesChange(GameTestHelper helper) {
        var sample = new ItemStack(Items.IRON_INGOT);
        var existing = request(helper, new UUID(0, 1), sample, 3, 0, 3, 0);
        var urgent = request(helper, new UUID(0, 2), sample, 3, 9, 0, 0);
        var allocation = WorkSupplyAllocation.allocate(List.of(urgent, existing),
                List.of(new WorkSupplyAllocation.Stock(BlockPos.ZERO, sample, 3, 3, 0, true)));
        helper.assertValueEqual(row(allocation, existing).committed(), 3L, "carried or durable commitments are never confiscated");
        helper.assertValueEqual(row(allocation, urgent).availableNow(), 3L, "the urgent consumer receives only uncommitted stock");
        helper.assertValueEqual(allocation.grants().stream().mapToLong(WorkSupplyAllocation.Grant::count).sum(), 3L,
                "reserved and unreserved quantities do not share ownership");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void incomingSupplyIsAssignedOnceAndCannotAuthorizeAWithdrawal(GameTestHelper helper) {
        var sample = new ItemStack(Items.WHEAT);
        var first = request(helper, new UUID(0, 1), sample, 3, 9, 0, 0);
        var second = request(helper, new UUID(0, 2), sample, 3, 0, 0, 0);
        var allocation = WorkSupplyAllocation.allocate(List.of(second, first),
                List.of(new WorkSupplyAllocation.Stock(BlockPos.ZERO, sample, 0, 0, 3, true)));
        helper.assertValueEqual(row(allocation, first).incoming(), 3L, "real incoming supply follows the same priority decision");
        helper.assertValueEqual(row(allocation, second).missing(), 3L, "one delivery cannot cover independent consumers twice");
        helper.assertValueEqual(allocation.allowance(Set.of(first.demand().consumer()), BlockPos.ZERO, sample), 0L,
                "future delivery is not available for physical acquisition");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void allocationRetainsComponentsAndCountsOverlappingFloorsOnce(GameTestHelper helper) {
        var plain = new ItemStack(Items.WHEAT);
        var named = plain.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Seed prize"));
        var production = request(helper, new UUID(0, 1), plain, 3, 5, 0, 0);
        var floorConsumer = new UUID(0, 2);
        var requirement = WorkSupplyRequirement.exact("stock_reserve", plain, 5);
        var floor = new WorkSupplyAllocation.Request(new WorkSupplyDemand(
                WorkSupplyDemand.identity(helper.getLevel().registryAccess(), floorConsumer, WorkSupplyDemand.Kind.STOCK_RESERVE, requirement),
                floorConsumer, WorkSupplyDemand.Kind.STOCK_RESERVE, requirement, 5, 5, 0, 5,
                0, 0, 0, 0, true, List.of(BlockPos.ZERO), BlockPos.ZERO, WorkSupplyDemand.Blocker.of("")), 0);
        var allocation = WorkSupplyAllocation.allocate(List.of(floor, production), List.of(
                new WorkSupplyAllocation.Stock(BlockPos.ZERO, named, 64, 0, 0, true),
                new WorkSupplyAllocation.Stock(BlockPos.ZERO, plain, 10, 0, 0, true)));
        helper.assertValueEqual(row(allocation, floor).requirement().quantity(), 2L, "active work already covers three of the five stock-floor items");
        helper.assertValueEqual(allocation.allowance(Set.of(), BlockPos.ZERO, plain), 5L, "exports receive only genuine surplus");
        helper.assertValueEqual(allocation.allowance(Set.of(), BlockPos.ZERO, named), 64L, "component-distinct items are not silently assigned");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void protectedPassRetainsTheFullValueOfIndivisibleItems(GameTestHelper helper) {
        var consumer = new UUID(0, 1);
        var need = SettlementFoodService.requirement(9);
        var request = new WorkSupplyAllocation.Request(new WorkSupplyDemand(
                WorkSupplyDemand.identity(helper.getLevel().registryAccess(), consumer, WorkSupplyDemand.Kind.FOOD, need),
                consumer, WorkSupplyDemand.Kind.FOOD, need, 6, 9, 6, 5,
                0, 0, 0, 0, true, List.of(BlockPos.ZERO), null, WorkSupplyDemand.Blocker.of("")), 0);
        var bread = new ItemStack(Items.BREAD);
        var allocation = WorkSupplyAllocation.allocate(List.of(request),
                List.of(new WorkSupplyAllocation.Stock(BlockPos.ZERO, bread, 3, 0, 0, true)));
        helper.assertValueEqual(row(allocation, request).availableNow(), 9L, "two five-nutrition meals cover the nine-nutrition target");
        helper.assertValueEqual(allocation.grants().stream().mapToLong(WorkSupplyAllocation.Grant::count).sum(), 2L,
                "the ordinary pass reuses surplus value already assigned by the protected pass");
        helper.assertValueEqual(allocation.allowance(Set.of(), BlockPos.ZERO, bread), 1L, "the third meal stays unallocated");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void unavailableRequirementsStayUnknownEvenWithoutAQuantity(GameTestHelper helper) {
        var consumer = new UUID(0, 1);
        var need = new WorkSupplyRequirement("unknown_recipe", List.of(), 0, WorkSupplyRequirement.Unit.ITEMS, stack -> false, null);
        var request = new WorkSupplyAllocation.Request(new WorkSupplyDemand(
                WorkSupplyDemand.identity(helper.getLevel().registryAccess(), consumer, WorkSupplyDemand.Kind.PRODUCTION, need),
                consumer, WorkSupplyDemand.Kind.PRODUCTION, need, 0, 0, 0, 5,
                0, 0, 0, 0, false, List.of(BlockPos.ZERO), null, WorkSupplyDemand.Blocker.of("recipe_unavailable")), 0);
        var result = WorkSupplyAllocation.allocate(List.of(request), List.of());
        helper.assertValueEqual(row(result, request).missing(), -1L, "unknown recipe inputs cannot be treated as satisfied");
        helper.assertValueEqual(row(result, request).blocker().code(), "recipe_unavailable", "the underlying observation blocker remains actionable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void acquisitionRefreshReallocatesCurrentPhysicalStock(GameTestHelper helper) {
        var town = town(helper);
        var depot = town.depots().values().iterator().next();
        depot.setItem(0, new ItemStack(Items.WHEAT, 3));
        var planned = SettlementDemandService.inspect(helper.getLevel(), town);
        var winner = planned.demands().stream().filter(demand -> demand.supply().priority() == 9).findFirst().orElseThrow();
        helper.assertValueEqual(winner.supply().availableNow(), 3L, "the urgent bakery receives the observed wheat");
        depot.setItem(0, ItemStack.EMPTY);
        var empty = planned.refresh();
        helper.assertValueEqual(empty.demands().stream().mapToLong(demand -> demand.supply().availableNow()).sum(), 0L,
                "a cached recipe plan cannot authorize stock that disappeared");
        depot.setItem(0, new ItemStack(Items.WHEAT, 6));
        var replenished = planned.refresh();
        helper.assertValueEqual(replenished.demands().stream().mapToLong(SettlementDemandService.Demand::missing).sum(), 0L,
                "fresh acquisitions allocate replenished stock to both independent consumers");
        helper.assertTrue(replenished.demands().stream().anyMatch(demand -> demand.supply().id().equals(winner.supply().id())),
                "stock refresh preserves the selected requirement identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void scheduledPlanningPublishesOnlyACompleteAllocationDecision(GameTestHelper helper) {
        var town = town(helper);
        var depot = town.depots().values().iterator().next();
        depot.setItem(0, new ItemStack(Items.WHEAT, 3));
        var ready = new java.util.concurrent.atomic.AtomicReference<SettlementDemandService.Snapshot>();
        var planning = SettlementDemandService.plan(helper.getLevel(), town, ready::set);
        planning.run();
        helper.assertTrue(ready.get() == null, "an unfinished consumer scan cannot publish partial permissions");
        for (int step = 0; step < 16 && !SettlementWork.complete(planning); step++) planning.run();
        helper.assertTrue(SettlementWork.complete(planning) && ready.get() != null, "the bounded work units finish through the existing scheduler");
        var demands = ready.get().demands();
        helper.assertValueEqual(demands.stream().filter(demand -> demand.supply().priority() == 9).findFirst().orElseThrow().supplied(), 3L,
                "the complete plan contains the higher-priority grant");
        helper.assertValueEqual(demands.stream().filter(demand -> demand.supply().priority() == 1).findFirst().orElseThrow()
                .supply().blocker().action(), WorkSupplyDemand.Action.INSPECT_COMPETITOR, "the losing consumer receives an actionable explanation");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void competingDemandNavigationSurvivesTheManagementWire(GameTestHelper helper) {
        var town = town(helper);
        town.depots().values().iterator().next().setItem(0, new ItemStack(Items.WHEAT, 3));
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(town.leader(), "AllocationViewer"));
        var page = SettlementManagementService.snapshot(player, new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(
                town.id().value(), com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.DEMAND, 0, "", 1, false));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.encode(buffer, page);
            var decoded = com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            var rows = decoded.entries().stream().map(com.jvn.villagerretaliation.network.SettlementManagementPayload.Demand.class::cast).toList();
            var winner = rows.stream().filter(demand -> demand.priority() == 9).findFirst().orElseThrow();
            var blocked = rows.stream().filter(demand -> demand.priority() == 1).findFirst().orElseThrow();
            helper.assertValueEqual(blocked.cause(), SettlementSupplyRecovery.Cause.ALLOCATED, "physical competition differs from missing stock");
            helper.assertValueEqual(blocked.competingConsumer(), winner.consumer(), "both clients can navigate to the same competing consumer");
            helper.assertTrue(blocked.advice().contains("priority 9"), "the explanation identifies the decisive priority");
            var target = SettlementManagementService.snapshot(player, new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(
                    town.id().value(), com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.DEMAND,
                    0, blocked.competingConsumer().toString(), 2, false));
            helper.assertValueEqual(target.total(), 1, "the competing-demand action resolves to its authoritative consumer");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void acquisitionWaitsForPlanningAndRejectsAnOutdatedPriority(GameTestHelper helper) {
        withIsolatedSettlementState(helper, () -> assertScheduledAcquisition(helper));
    }

    private static void withIsolatedSettlementState(GameTestHelper helper, Runnable test) {
        var level = helper.getLevel();
        var storage = level.getServer().overworld().getDataStorage();
        var priorSettlements = SettlementSavedData.get(level);
        var priorVillages = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        try {
            storage.set("villagerretaliation_settlements", new SettlementSavedData());
            storage.set("villagerretaliation_village_allegiances", new com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData());
            test.run();
        } finally {
            storage.set("villagerretaliation_settlements", priorSettlements);
            storage.set("villagerretaliation_village_allegiances", priorVillages);
        }
    }

    private static void assertScheduledAcquisition(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = town(helper);
        var depot = town.depots().values().iterator().next();
        depot.setItem(0, new ItemStack(Items.WHEAT, 3));
        var area = town.areas().get(new UUID(0, 1));
        var first = town.productionQueues().get(area.id()).orders().getFirst().id();
        var second = town.productionQueues().get(new UUID(0, 2)).orders().getFirst().id();
        var worker = UUID.randomUUID();
        var sample = new ItemStack(Items.WHEAT);
        var initial = SettlementAllocationService.inspect(level, town, worker, first, depot.position(), sample);
        helper.assertValueEqual(initial.blocker(), "allocation_pending", "acquisition cannot use a partially built plan");
        for (int step = 0; step < 512 && SettlementAllocationService.inspect(level, town, worker, first, depot.position(), sample)
                .blocker().equals("allocation_pending"); step++) SettlementSavedData.get(level).simulation.runPending();
        helper.assertValueEqual(SettlementAllocationService.inspect(level, town, worker, first, depot.position(), sample).allowance(), 3,
                "the complete scheduled plan permits the winning acquisition");
        helper.assertTrue(town.putArea(town.leader(), new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(), 0, true)),
                "the player can change the worksite priority");
        helper.assertValueEqual(SettlementAllocationService.inspect(level, town, worker, first, depot.position(), sample).allowance(), 0,
                "a cached grant is invalid immediately after a priority change");
        for (int step = 0; step < 512 && SettlementAllocationService.inspect(level, town, worker, second, depot.position(), sample)
                .blocker().equals("allocation_pending"); step++) SettlementSavedData.get(level).simulation.runPending();
        helper.assertValueEqual(SettlementAllocationService.inspect(level, town, worker, first, depot.position(), sample).allowance(), 0,
                "the former winner cannot retain unstarted stock");
        helper.assertValueEqual(SettlementAllocationService.inspect(level, town, worker, second, depot.position(), sample).allowance(), 3,
                "the new winner receives the current physical stock");
        depot.setItem(0, ItemStack.EMPTY);
        helper.assertValueEqual(SettlementAllocationService.inspect(level, town, worker, second, depot.position(), sample).allowance(), 0,
                "removing stock invalidates its allowance without waiting for another planning cycle");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void backgroundAcquisitionKeepsPriorityWhenTheLosingWorkerRunsFirst(GameTestHelper helper) {
        contestedCrafting(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void backgroundAcquisitionKeepsPriorityWhenTheWinningWorkerRunsFirst(GameTestHelper helper) {
        contestedCrafting(helper, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void linkedPersonalWorkSharesLoadedAcquisitionsWithoutBecomingCivic(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = town(helper, helper.absolutePos(new BlockPos(1, 2, 1)));
        var source = town.center().above();
        level.setBlockAndUpdate(source, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
        var container = (net.minecraft.world.Container) level.getBlockEntity(source);
        container.setItem(0, new ItemStack(Items.WHEAT, 6));
        for (var area : town.areas().values()) if (area.type() == SettlementAreaType.KITCHEN) {
            var plan = town.worksitePlans().get(area.id());
            var links = new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(source, SettlementStorageLinks.Purpose.SUPPLY, 0)));
            town.setWorksitePlan(town.leader(), area.id(), new SettlementWorksitePlan(plan.settings().withStorage(links), plan.desiredStaff(), plan.links()));
        }
        var owner = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PersonalSupplyOwner"));
        var worker = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        worker.setPos(source.north().getCenter());
        worker.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(worker), "the linked personal worker is loaded");
        var role = com.jvn.villagerretaliation.interaction.HiredVillagerRole.COOK;
        com.jvn.villagerretaliation.interaction.HireContractStore.save(worker,
                com.jvn.villagerretaliation.interaction.HireContract.regular(owner.getUUID(), level.getGameTime(), 1, 1, 1, role));
        try {
            com.jvn.villagerretaliation.inventory.AssignedStorageService.assign(owner, worker,
                    List.of(new com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition(level.dimension(), source)), "supply");
            var recipe = com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"), java.util.Map.of());
            var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level, recipe,
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 1, null);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.appendPersonal(owner, worker, 0, order), "",
                    "personal production stays under its hirer's authority");
            var competingOrder = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level, recipe,
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 1, null);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.appendPersonal(owner, worker, 1, competingOrder), "",
                    "the same personal queue can contain competing requirements");
            var pendingView = personalSnapshot(owner, worker);
            helper.assertTrue(pendingView.requirements().stream().allMatch(need ->
                            need.missing() == -1 && need.allocation().blocker().equals("allocation_pending")),
                    "unplanned shared allocation is checking rather than a known stock shortage");
            SettlementTestPlots.planSupplies(level, town);
            var snapshot = SettlementDemandService.inspect(level, town);
            var personalDemands = snapshot.demands().stream().filter(demand -> demand.consumer().equals(order.id())
                    || demand.consumer().equals(competingOrder.id())).toList();
            helper.assertValueEqual(personalDemands.stream().mapToLong(demand -> demand.supply().availableNow()).sum(),
                    3L, "priority five personal demand shares stock after urgent civic work");
            var winningOrder = personalDemands.stream().filter(demand -> demand.supply().availableNow() == 3).findFirst().orElseThrow().consumer();
            var losingOrder = winningOrder.equals(order.id()) ? competingOrder.id() : order.id();
            var personalRows = SettlementDemandViewService.demands(level, town, snapshot).stream()
                    .filter(row -> row.consumer().equals(order.id()) || row.consumer().equals(competingOrder.id())).toList();
            helper.assertValueEqual(personalRows.size(), 2, "both linked personal orders have visible demand controls");
            helper.assertTrue(personalRows.stream().allMatch(row ->
                    row.control() == com.jvn.villagerretaliation.network.SettlementManagementPayload.DemandControl.PERSONAL_PRODUCTION
                            && row.control().personal() && !row.control().worksite() && row.area().equals(worker.getUUID())),
                    "personal controls retain the worker target and cannot open civic configuration");
            helper.assertValueEqual(personalRows.stream().filter(row -> row.consumer().equals(winningOrder)).findFirst().orElseThrow().cause(),
                    SettlementSupplyRecovery.Cause.READY, "a supplied personal job does not ask for a civic assignment");
            var personalView = personalSnapshot(owner, worker);
            var winner = personalView.requirements().stream().filter(need -> need.order().equals(winningOrder)).findFirst().orElseThrow();
            var loser = personalView.requirements().stream().filter(need -> need.order().equals(losingOrder)).findFirst().orElseThrow();
            helper.assertValueEqual(winner.availableNow(), 3L, "the personal queue shows the exact winning order's allowance");
            helper.assertValueEqual(loser.availableNow(), 0L, "queue position cannot display another personal order's grant");
            helper.assertValueEqual(loser.allocation().blocker(), "allocated_elsewhere", "contested supply is distinct from absent stock");
            helper.assertTrue(loser.allocation().competitor() == null, "a personal contract does not grant access to settlement consumer details");
            town.setPermissions(town.leader(), owner.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
            SettlementTestPlots.planSupplies(level, town);
            personalView = personalSnapshot(owner, worker);
            loser = personalView.requirements().stream().filter(need -> need.order().equals(losingOrder)).findFirst().orElseThrow();
            var competitor = loser.allocation().competitor();
            helper.assertTrue(competitor != null && competitor.town().equals(town.id().value()) && competitor.priority() == 9
                            && competitor.name().equals("Bakery") && town.productionQueues().values().stream()
                            .anyMatch(queue -> queue.order(competitor.consumer()) != null),
                    "VIEW identifies the actual competing consumer and its effective priority");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            try {
                com.jvn.villagerretaliation.network.ProductionSnapshotPayload.STREAM_CODEC.encode(buffer, personalView);
                var decoded = com.jvn.villagerretaliation.network.ProductionSnapshotPayload.STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(decoded.requirements(), personalView.requirements(), "quantities and bounded allocation explanations survive the wire");
                helper.assertTrue(decoded.managed().isEmpty() && decoded.target().town() == null, "inspection never adopts personal work into a policy");
            } finally { buffer.release(); }
            town.setPermissions(town.leader(), owner.getUUID(), java.util.Set.of());
            SettlementTestPlots.planSupplies(level, town);
            helper.assertTrue(personalSnapshot(owner, worker).requirements().stream().allMatch(need -> need.allocation().competitor() == null),
                    "revoking VIEW hides competing details in the next personal snapshot");
            helper.assertFalse(com.jvn.villagerretaliation.inventory.AssignedStorageService.reserveProductionInputs(
                    worker, java.util.Map.of(Items.WHEAT, 3), losingOrder), "one order cannot reserve another order's share at the same workplace");
            helper.assertTrue(com.jvn.villagerretaliation.inventory.AssignedStorageService.reserveProductionInputs(
                    worker, java.util.Map.of(Items.WHEAT, 3), winningOrder), "the winning order reserves its exact allocated inputs");
            var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(worker);
            helper.assertValueEqual(com.jvn.villagerretaliation.inventory.AssignedStorageService.transferItemsAtAssignedStorage(
                    worker, source, stack -> stack.is(Items.WHEAT), 6, inventory::insertSupplyFromStorage), 3,
                    "loaded pickup cannot appropriate the urgent civic allocation");
            helper.assertValueEqual(container.getItem(0).getCount(), 3, "the urgent consumer's real stock remains in the barrel");
            var second = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level, recipe,
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 1, null);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.appendPersonal(owner, worker, 2, second), "",
                    "the hirer can expand personal work independently");
            helper.assertValueEqual(SettlementAllocationService.inspect(level, town, worker.getUUID(), null, source, new ItemStack(Items.WHEAT)).blocker(),
                    "allocation_pending", "a changed personal queue invalidates the cached shared decision");
            SettlementTestPlots.planSupplies(level, town);
            helper.assertValueEqual(SettlementAllocationService.inspect(level, town, worker.getUUID(), null, source, new ItemStack(Items.WHEAT)).allowance(),
                    0, "carried personal inputs are credited once while remaining civic stock retains its priority");
            helper.assertFalse(town.duties().containsKey(worker.getUUID()), "linked supplies do not grant settlement staffing control");
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.access(level, worker, role)
                    .queue().orders().size(), 3, "allocation never rewrites or adopts personal orders");
            container.setItem(1, new ItemStack(Items.CARROT, 2));
            helper.assertValueEqual(com.jvn.villagerretaliation.inventory.AssignedStorageService.transferFirstMatchingStackAtAssignedStorage(
                    worker, source, stack -> stack.is(Items.WHEAT) || stack.is(Items.CARROT), inventory::insertSupplyFromStorage), 2,
                    "an allocated first match does not hide a later permitted supply stack");
            helper.assertValueEqual(container.getItem(0).getCount(), 3, "trying another eligible stack preserves the competitor's wheat");
        } finally {
            com.jvn.villagerretaliation.inventory.AssignedStorageService.releaseProductionInputs(worker);
            worker.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void personalIncomingCreditsOnlyActualSourcesAcrossSettlementsAndAssignments(GameTestHelper helper) {
        var level = helper.getLevel();
        var first = town(helper, helper.absolutePos(new BlockPos(1, 2, 1)));
        var second = town(helper);
        var towns = List.of(first, second);
        var sources = towns.stream().map(town -> town.center().above()).toList();
        var receipts = new java.util.ArrayList<com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Allocation>();
        for (int index = 0; index < towns.size(); index++) {
            var town = towns.get(index);
            var source = sources.get(index);
            level.setBlockAndUpdate(source, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            for (var area : town.areas().values()) if (area.type() == SettlementAreaType.KITCHEN) {
                var plan = town.worksitePlans().get(area.id());
                var links = new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(source, SettlementStorageLinks.Purpose.SUPPLY, 0)));
                town.setWorksitePlan(town.leader(), area.id(), new SettlementWorksitePlan(plan.settings().withStorage(links), plan.desiredStaff(), plan.links()));
            }
            var depot = town.depots().values().iterator().next();
            depot.setItem(0, new ItemStack(Items.WHEAT, 6));
            town.setWorksitePlan(town.leader(), depot.area(), SettlementWorksitePlan.defaults(com.jvn.villagerretaliation.interaction.HiredVillagerRole.COURIER));
            var queue = town.beginTransfers(town.leader(), depot.area());
            var delivery = com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Order.create(
                    new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Endpoint(depot.position(), depot.id()),
                    new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Endpoint(source, null), new ItemStack(Items.WHEAT), 6);
            helper.assertTrue(queue.append(delivery), "the incoming fixture uses a real native delivery");
            var allocation = queue.reserve(UUID.randomUUID(), delivery.id(), 6);
            helper.assertTrue(allocation != null, "each source has a durable incoming receipt");
            receipts.add(allocation);
        }
        var owner = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "IncomingSupplyOwner"));
        var worker = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        worker.setPos(first.center().north().getCenter());
        worker.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(worker), "the personal consumer is loaded");
        var role = com.jvn.villagerretaliation.interaction.HiredVillagerRole.COOK;
        com.jvn.villagerretaliation.interaction.HireContractStore.save(worker,
                com.jvn.villagerretaliation.interaction.HireContract.regular(owner.getUUID(), level.getGameTime(), 1, 1, 1, role));
        try {
            var links = sources.stream().map(source ->
                    new com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition(level.dimension(), source)).toList();
            com.jvn.villagerretaliation.inventory.AssignedStorageService.assign(owner, worker, links, "supply");
            com.jvn.villagerretaliation.inventory.AssignedStorageService.assign(owner, worker, links, "general");
            var recipe = com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"), java.util.Map.of());
            var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level, recipe,
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 4, null);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.appendPersonal(owner, worker, 0, order), "",
                    "the independent personal queue requests twelve wheat");
            towns.forEach(town -> SettlementTestPlots.planSupplies(level, town));
            var need = personalSnapshot(owner, worker).requirements().getFirst();
            helper.assertValueEqual(need.required(), 12L, "linked towns do not duplicate the personal requirement");
            helper.assertValueEqual(need.availableNow(), 0L, "incoming cargo is not available for pickup");
            helper.assertValueEqual(need.committed(), 0L, "incoming cargo is not already in the worker's inventory");
            helper.assertValueEqual(need.incoming(), 6L, "each town contributes only its own source grant after urgent civic demand");
            helper.assertValueEqual(need.missing(), 6L, "duplicate source purposes and overlapping town snapshots do not erase the shortage");
            var queue = first.transferQueues().values().iterator().next();
            queue.releasePlanned(receipts.getFirst().worker());
            helper.assertTrue(queue.allocation(receipts.getFirst().worker()) == null, "cancelling pickup releases the native incoming receipt");
            towns.forEach(town -> SettlementTestPlots.planSupplies(level, town));
            need = personalSnapshot(owner, worker).requirements().getFirst();
            helper.assertValueEqual(need.incoming(), 3L, "released incoming cargo disappears from the personal explanation");
            helper.assertValueEqual(need.missing(), 9L, "the released receipt restores the real shortage");
        } finally {
            worker.discard();
        }
        helper.succeed();
    }

    private static com.jvn.villagerretaliation.network.ProductionSnapshotPayload personalSnapshot(
            net.minecraft.server.level.ServerPlayer owner, net.minecraft.world.entity.npc.Villager worker) {
        return com.jvn.villagerretaliation.interaction.work.production.ProductionManagementService.snapshot(owner,
                new com.jvn.villagerretaliation.network.ProductionRequestPayload(
                        new com.jvn.villagerretaliation.network.ProductionTarget(null, worker.getUUID()), UUID.randomUUID(), 0,
                        new com.jvn.villagerretaliation.network.ProductionRequestPayload.View()), false, "");
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void loadedWorkersChooseAvailableStorageBeyondAContestedSource(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = town(helper, helper.absolutePos(new BlockPos(1, 2, 1)));
        var sources = List.of(town.center().above(), town.center().above().east(4));
        for (var source : sources) {
            level.setBlockAndUpdate(source, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            ((net.minecraft.world.Container) level.getBlockEntity(source)).setItem(0, new ItemStack(Items.WHEAT, 3));
        }
        var links = new SettlementStorageLinks(false, sources.stream().map(source ->
                new SettlementStorageLinks.Link(source, SettlementStorageLinks.Purpose.SUPPLY, 0)).toList());
        for (var area : town.areas().values()) if (area.type() == SettlementAreaType.KITCHEN) {
            var plan = town.worksitePlans().get(area.id());
            town.setWorksitePlan(town.leader(), area.id(), new SettlementWorksitePlan(plan.settings().withStorage(links), plan.desiredStaff(), plan.links()));
        }
        var owner = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SupplySelectionOwner"));
        var worker = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        worker.setPos(sources.getFirst().north().getCenter());
        worker.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(worker), "the personal worker is loaded");
        com.jvn.villagerretaliation.interaction.HireContractStore.save(worker,
                com.jvn.villagerretaliation.interaction.HireContract.regular(owner.getUUID(), level.getGameTime(), 1, 1, 1,
                        com.jvn.villagerretaliation.interaction.HiredVillagerRole.COOK));
        try {
            com.jvn.villagerretaliation.inventory.AssignedStorageService.assign(owner, worker, sources.stream().map(source ->
                    new com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition(level.dimension(), source)).toList(), "supply");
            var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(
                            net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"), java.util.Map.of()),
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 1, null);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.appendPersonal(owner, worker, 0, order),
                    "", "the personal consumer requests its own batch");
            SettlementTestPlots.planSupplies(level, town);
            var blocked = sources.stream().filter(source -> SettlementAllocationService.inspect(level, town, worker.getUUID(),
                    null, source, new ItemStack(Items.WHEAT)).allowance() == 0).findFirst().orElseThrow();
            var available = sources.stream().filter(source -> !source.equals(blocked)).findFirst().orElseThrow();
            worker.setPos(blocked.north().getCenter());
            helper.assertValueEqual(com.jvn.villagerretaliation.inventory.AssignedStorageService.nearestAssignedInputStoragePosContaining(
                    level, worker, stack -> stack.is(Items.WHEAT), ignored -> true), available,
                    "source selection skips nearer stock allocated to urgent civic work");
            helper.assertValueEqual(com.jvn.villagerretaliation.inventory.AssignedStorageService.assignedStoragePositionsContaining(
                    level, worker, stack -> stack.is(Items.WHEAT)), List.of(available),
                    "the source list uses the same permitted acquisition decision");
            worker.setPos(available.north().getCenter());
            var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(worker);
            helper.assertValueEqual(com.jvn.villagerretaliation.inventory.AssignedStorageService.transferItemsAtAssignedStorage(
                    worker, available, stack -> stack.is(Items.WHEAT), 3, inventory::insertSupplyFromStorage), 3,
                    "the selected source supplies the native loaded pickup");
            helper.assertValueEqual(((net.minecraft.world.Container) level.getBlockEntity(blocked)).getItem(0).getCount(), 3,
                    "the competing consumer retains its stock");
        } finally {
            worker.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void doubleChestAliasesShareOneScarcityDecision(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = town(helper, helper.absolutePos(new BlockPos(1, 2, 1)));
        var left = town.center().above();
        var right = left.east();
        var chest = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH);
        level.setBlock(left, chest.setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.LEFT), 2);
        level.setBlock(right, chest.setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.RIGHT), 2);
        var container = (net.minecraft.world.Container) level.getBlockEntity(left);
        container.setItem(0, new ItemStack(Items.WHEAT, 3));
        var areas = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN)
                .sorted(java.util.Comparator.comparingInt(SettlementArea::priority).reversed()).toList();
        for (int index = 0; index < areas.size(); index++) {
            var area = areas.get(index);
            var plan = town.worksitePlans().get(area.id());
            var links = new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(
                    index == 0 ? left : right, SettlementStorageLinks.Purpose.SUPPLY, 0)));
            town.setWorksitePlan(town.leader(), area.id(), new SettlementWorksitePlan(plan.settings().withStorage(links), plan.desiredStaff(), plan.links()));
        }
        SettlementTestPlots.planSupplies(level, town);
        var winner = town.productionQueues().get(areas.getFirst().id()).orders().getFirst().id();
        var loser = town.productionQueues().get(areas.getLast().id()).orders().getFirst().id();
        var sample = new ItemStack(Items.WHEAT);
        for (var alias : List.of(left, right)) {
            helper.assertValueEqual(SettlementAllocationService.inspect(level, town, null, winner, alias, sample).allowance(),
                    3, "either chest half exposes the same winning allocation");
            helper.assertValueEqual(SettlementAllocationService.inspect(level, town, null, loser, alias, sample).allowance(),
                    0, "a second chest alias cannot create another supply pool");
        }
        container.removeItem(0, 2);
        helper.assertValueEqual(SettlementAllocationService.inspect(level, town, null, winner, right, sample).allowance(),
                1, "acquisition through the other half rechecks current physical stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_allocation")
    public static void storageConsumerIndexTracksMultiplePurposesTransfersAndReload(GameTestHelper helper) {
        var data = new com.jvn.villagerretaliation.inventory.AssignedStorageSavedData();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var source = new com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord(
                helper.getLevel().dimension(), BlockPos.ZERO, first, owner, "supply", 0, "valid");
        var output = new com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord(
                helper.getLevel().dimension(), BlockPos.ZERO, first, owner, "output", 0, "valid");
        data.assign(source);
        data.assign(output);
        data.removeAssignment(source);
        helper.assertValueEqual(data.assignedAt(helper.getLevel().dimension(), BlockPos.ZERO).size(), 1,
                "removing one purpose retains other users of the same storage");
        data.transferVillagerAssignments(first, second);
        var restored = com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.load(
                data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.assignedAt(helper.getLevel().dimension(), BlockPos.ZERO).getFirst().villagerId(), second,
                "the storage index follows ownership transfer and rebuilds from saved assignments");
        restored.removeAssignedTo(second);
        helper.assertTrue(restored.assignedAt(helper.getLevel().dimension(), BlockPos.ZERO).isEmpty(),
                "removed assignments cannot leave phantom demand consumers");
        helper.succeed();
    }

    private static void contestedCrafting(GameTestHelper helper, boolean urgentFirst) {
        withIsolatedSettlementState(helper, () -> assertContestedCrafting(helper, urgentFirst));
    }

    private static void assertContestedCrafting(GameTestHelper helper, boolean urgentFirst) {
        var level = helper.getLevel();
        var town = town(helper);
        var depot = town.depots().values().iterator().next();
        depot.setItem(0, new ItemStack(Items.WHEAT, 3));
        var urgent = worker(helper, town, new UUID(0, 1));
        var ordinary = worker(helper, town, new UUID(0, 2));
        ready(helper, town, urgent.id(), town.productionQueues().get(urgent.area()).orders().getFirst().id(), depot);
        for (var worker : urgentFirst ? List.of(urgent, ordinary) : List.of(ordinary, urgent))
            SettlementBackgroundCrafting.tick(level, town, worker, 1_000, 1.0D);
        helper.assertValueEqual(urgent.items(level.registryAccess()).stream().filter(stack -> stack.is(Items.BREAD))
                .mapToInt(ItemStack::getCount).sum(), 1, "the urgent worker crafts the only funded batch");
        helper.assertValueEqual(ordinary.items(level.registryAccess()).stream().filter(stack -> stack.is(Items.BREAD))
                .mapToInt(ItemStack::getCount).sum(), 0, "worker tick order cannot change scarce material ownership");
        helper.assertTrue(depot.isEmpty(), "exactly three real wheat items became one loaf");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        var next = restored.workers().get(ordinary.id());
        var stores = restored.depots().get(depot.id());
        stores.setItem(0, new ItemStack(Items.WHEAT, 3));
        var order = restored.productionQueues().get(next.area()).orders().getFirst();
        ready(helper, restored, next.id(), order.id(), stores);
        SettlementBackgroundCrafting.tick(level, restored, next, 1_000, 1.0D);
        helper.assertValueEqual(restored.productionQueues().get(next.area()).order(order.id()).produced(), 1L,
                "completed work releases its demand and replenished stock serves the remaining consumer after reload");
        helper.assertTrue(stores.isEmpty(), "reloaded allocation accounting neither duplicates nor loses supply");
        helper.succeed();
    }

    private static SettlementWorkerState worker(GameTestHelper helper, SettlementRecord town, UUID areaId) {
        var level = helper.getLevel();
        var area = town.areas().get(areaId);
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        villager.setPos(area.min().getCenter());
        town.assignDuty(villager.getUUID(), areaId);
        var worker = SettlementWorkerState.capture(level, villager, new SettlementWorkforceService.Duty(town, area), 0);
        worker.settings().putBoolean("AutoDepositOutputs", false);
        town.recordWorker(worker);
        town.recordSurvey(areaId, new SettlementBuildingSurvey.Survey(0L, List.of(), List.of(area.min()), true,
                java.util.Map.of(area.min(), net.minecraft.resources.ResourceLocation.withDefaultNamespace("crafting_table"))));
        return worker;
    }

    private static void ready(GameTestHelper helper, SettlementRecord town, UUID worker, UUID consumer, SettlementDepot depot) {
        for (int step = 0; step < 512; step++) {
            var decision = SettlementAllocationService.inspect(helper.getLevel(), town, worker, consumer, depot.position(), new ItemStack(Items.WHEAT));
            if (!decision.blocker().equals("allocation_pending")) return;
            SettlementSavedData.get(helper.getLevel()).simulation.runPending();
        }
        helper.fail("the allocation plan did not finish through the budgeted scheduler");
    }

    private static SettlementRecord town(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(23_000_000 + origin.getX() * 8, 64, 23_000_000 + origin.getZ() * 8);
        return town(helper, center);
    }

    private static SettlementRecord town(GameTestHelper helper, BlockPos center) {
        var level = helper.getLevel();
        var registry = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Allocation test");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, center, center.offset(15, 3, 12));
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center, center.offset(3, 2, 3), 5, true);
        helper.assertTrue(town.putArea(town.leader(), storage), "allocation storage is registered");
        var depot = town.registerDepot(town.leader(), storage.id(), center, SettlementDepotPurpose.GENERAL);
        depot.observeRules(List.of());
        for (int index = 0; index < 2; index++) {
            var position = center.east(5 + index * 5);
            var bakery = new SettlementArea(new UUID(0, index + 1), SettlementAreaType.KITCHEN, "Bakery", position, position.offset(3, 2, 3), index == 0 ? 9 : 1, true);
            helper.assertTrue(town.putArea(town.leader(), bakery), "allocation consumers are registered");
            town.setWorksitePlan(town.leader(), bakery.id(), SettlementWorksitePlan.defaults(bakery.type().role()));
            var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(
                            net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"), java.util.Map.of()),
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 1, center);
            town.beginProduction(town.leader(), bakery.id()).append(order);
        }
        return town;
    }

    private static WorkSupplyAllocation.Request request(GameTestHelper helper, UUID consumer, ItemStack sample,
                                                         long required, int priority, long committed, long started) {
        var requirement = WorkSupplyRequirement.exact("material", sample, required);
        return new WorkSupplyAllocation.Request(new WorkSupplyDemand(
                WorkSupplyDemand.identity(helper.getLevel().registryAccess(), consumer, WorkSupplyDemand.Kind.PRODUCTION, requirement),
                consumer, WorkSupplyDemand.Kind.PRODUCTION, requirement, required, 0, 0, priority,
                0, committed, 0, 0, true, List.of(BlockPos.ZERO), null, WorkSupplyDemand.Blocker.of("")), started);
    }

    private static WorkSupplyDemand row(WorkSupplyAllocation.Result result, WorkSupplyAllocation.Request request) {
        return result.demands().stream().filter(demand -> demand.id().equals(request.demand().id())).findFirst().orElseThrow();
    }
}
