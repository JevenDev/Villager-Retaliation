package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementPolicyGameTests {
    private SettlementPolicyGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void configuredGraphHasStableDependencyOrderAndRejectsCycles(GameTestHelper helper) {
        var gather = gather();
        var bake = bake();
        var chain = configuration(List.of(bake, gather), List.of(
                new SettlementSupplyPolicy.Connection(gather.id(), bake.id(), new UUID(0, 10)),
                new SettlementSupplyPolicy.Connection(bake.id(), null, new UUID(0, 11))));
        var graph = SettlementPolicyGraph.inspect(chain);
        helper.assertTrue(graph.valid(), "an explicitly connected production chain is valid");
        helper.assertValueEqual(graph.producersFirst(), List.of(gather, bake), "dependency order is independent of editor list order");
        var cyclic = configuration(chain.steps(), List.of(
                new SettlementSupplyPolicy.Connection(gather.id(), bake.id(), new UUID(0, 10)),
                new SettlementSupplyPolicy.Connection(bake.id(), gather.id(), new UUID(0, 12)),
                new SettlementSupplyPolicy.Connection(bake.id(), null, new UUID(0, 11))));
        helper.assertValueEqual(SettlementPolicyGraph.inspect(cyclic).blocker(), "policy_cycle",
                "a cycle is rejected before any managed orders can be created");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void graphRejectsMissingConflictingAndUnusedSteps(GameTestHelper helper) {
        var gather = gather();
        var bake = bake();
        var output = new SettlementSupplyPolicy.Connection(bake.id(), null, new UUID(0, 11));
        helper.assertValueEqual(SettlementPolicyGraph.inspect(configuration(List.of(bake), List.of(
                output, new SettlementSupplyPolicy.Connection(gather.id(), bake.id(), new UUID(0, 10))))).blocker(),
                "policy_connection_missing", "a removed producer cannot be replaced implicitly");
        helper.assertValueEqual(SettlementPolicyGraph.inspect(configuration(List.of(gather, bake), List.of(output))).blocker(),
                "policy_step_unused", "unconnected gathering cannot run without a target");
        helper.assertValueEqual(SettlementPolicyGraph.inspect(configuration(List.of(gather, bake), List.of(output,
                new SettlementSupplyPolicy.Connection(gather.id(), null, output.requirement())))).blocker(),
                "policy_connection_conflict", "two steps cannot independently satisfy the same target requirement");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void savedPolicyPreservesAuthorizationAndRetiredExecutionBindings(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var original = bake();
        var chain = configuration(List.of(original), List.of(new SettlementSupplyPolicy.Connection(original.id(), null, new UUID(0, 11))));
        var actor = UUID.randomUUID();
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), chain, actor, () -> {});
        policy.state(SettlementSupplyPolicy.State.ACTIVE, actor, "", 10);
        var binding = new SettlementSupplyPolicy.Binding(UUID.randomUUID(), original, UUID.randomUUID(), SettlementSupplyPolicy.BindingState.DRAINING, 12);
        helper.assertTrue(policy.bind(binding), "the original execution retains its stable identity");
        var replacement = new SettlementSupplyPolicy.Step(original.id(), original.kind(), original.area(), original.recipe(),
                ItemStack.EMPTY, null, original.destination().east());
        policy.configure(configuration(List.of(replacement), chain.connections()), actor, 20);
        var restored = SettlementSupplyPolicy.load(policy.save(provider), provider, () -> {});
        helper.assertValueEqual(restored.authority(), actor, "the recorded authority survives an offline reload");
        helper.assertValueEqual(restored.revision(), policy.revision(), "configuration revisions survive reload");
        helper.assertValueEqual(restored.bindings().getFirst(), binding, "started work keeps its original destination after a configuration edit");
        helper.assertValueEqual(restored.configuration().steps().getFirst().destination(), replacement.destination(),
                "the replacement applies only to future execution");
        helper.assertValueEqual(restored.save(provider), policy.save(provider), "policy persistence does not lose runtime history or binding state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void malformedSavedPoliciesCannotAuthorizeDuplicateExecution(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var original = bake();
        var chain = configuration(List.of(original), List.of(new SettlementSupplyPolicy.Connection(original.id(), null, new UUID(0, 11))));
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), chain, UUID.randomUUID(), () -> {});
        policy.bind(new SettlementSupplyPolicy.Binding(UUID.randomUUID(), original, UUID.randomUUID(), SettlementSupplyPolicy.BindingState.ACTIVE, 12));
        var duplicate = policy.save(provider);
        var bindings = duplicate.getList("Bindings", Tag.TAG_COMPOUND);
        bindings.add(bindings.getCompound(0).copy());
        expectInvalid(helper, () -> SettlementSupplyPolicy.load(duplicate, provider, () -> {}));
        var future = policy.save(provider);
        future.putInt("Version", 3);
        expectInvalid(helper, () -> SettlementSupplyPolicy.load(future, provider, () -> {}));
        var invalid = policy.save(provider);
        invalid.getCompound("Configuration").putInt("Priority", 10);
        expectInvalid(helper, () -> SettlementSupplyPolicy.load(invalid, provider, () -> {}));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void settlementPolicyPersistenceLeavesOlderSavesUnmanaged(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var actor = UUID.randomUUID();
        var town = new SettlementRecord(new com.jvn.villagerretaliation.allegiance.VillageAllegianceId(UUID.randomUUID()),
                helper.getLevel().dimension().location(), BlockPos.ZERO, actor, 0, () -> {});
        var step = bake();
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), configuration(List.of(step), List.of(
                new SettlementSupplyPolicy.Connection(step.id(), null, new UUID(0, 11)))), actor, town::supplyChanged);
        helper.assertTrue(town.addSupplyPolicy(policy), "a new policy is persisted by its settlement");
        var saved = town.save(provider);
        var restored = SettlementRecord.load(saved, provider, () -> {});
        helper.assertValueEqual(restored.supplyPolicies().get(policy.id()).save(provider), policy.save(provider),
                "settlement reload preserves policy configuration and authority");
        var older = saved.copy();
        older.remove("SupplyPolicies");
        helper.assertTrue(SettlementRecord.load(older, provider, () -> {}).supplyPolicies().isEmpty(),
                "older towns do not acquire policies or spending permission");
        saved.putString("SupplyPolicies", "invalid");
        expectInvalid(helper, () -> SettlementRecord.load(saved, provider, () -> {}));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void managedExecutionRechecksAuthorityWithoutInterruptingStartedReceipts(GameTestHelper helper) {
        var leader = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var town = new SettlementRecord(new com.jvn.villagerretaliation.allegiance.VillageAllegianceId(UUID.randomUUID()),
                helper.getLevel().dimension().location(), BlockPos.ZERO, leader, 0, () -> {});
        var step = bake();
        var area = new SettlementArea(step.area(), SettlementAreaType.WORKSHOP, "Bakery", BlockPos.ZERO, new BlockPos(3, 2, 3), 5, true);
        helper.assertTrue(town.putArea(leader, area), "the producer has a registered worksite");
        town.setWorksitePlan(leader, area.id(), SettlementWorksitePlan.defaults(com.jvn.villagerretaliation.interaction.HiredVillagerRole.CRAFTSMAN));
        town.setPermissions(leader, actor, java.util.Set.of(SettlementPermission.STAFF, SettlementPermission.LOGISTICS));
        var queue = town.beginProduction(actor, area.id());
        var manual = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(helper.getLevel(), step.recipe(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 16, step.destination());
        var managed = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(helper.getLevel(), step.recipe(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.MAINTAIN_STOCK, 12, step.destination());
        queue.append(manual);
        queue.append(managed);
        var original = configuration(List.of(step), List.of(new SettlementSupplyPolicy.Connection(step.id(), null, new UUID(0, 11))));
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration(original.name(), original.target(), 9,
                original.steps(), original.connections()), actor, town::supplyChanged);
        town.addSupplyPolicy(policy);
        policy.bind(new SettlementSupplyPolicy.Binding(UUID.randomUUID(), step, managed.id(), SettlementSupplyPolicy.BindingState.ACTIVE, 12));
        policy.state(SettlementSupplyPolicy.State.ACTIVE, actor, "", 1);
        helper.assertValueEqual(queue.executionOrders().getFirst().id(), managed.id(), "policy urgency controls native producer selection");
        helper.assertValueEqual(queue.orders().getFirst().id(), manual.id(), "the manual queue order is not rewritten");
        var worker = UUID.randomUUID();
        town.assignDuty(worker, area.id());
        var started = queue.reserve(worker, managed.id(), 1, 1, 0, null);
        helper.assertTrue(queue.start(started.id()), "authorized work can start through the native queue");
        town.setPermissions(leader, actor, java.util.Set.of());
        helper.assertValueEqual(queue.available(managed.id(), 0), 0L, "revocation blocks new reservations before the next policy cycle");
        helper.assertTrue(queue.reserve(UUID.randomUUID(), managed.id(), 1, 1, 0, null) == null, "revoked authority cannot start more work");
        helper.assertTrue(SettlementPolicyExecution.acquisitionAllowed(town, worker, managed.id()), "started work can finish its existing receipt");
        helper.assertFalse(SettlementPolicyExecution.acquisitionAllowed(town, UUID.randomUUID(), managed.id()),
                "another worker cannot use the started receipt's exception");
        helper.assertTrue(queue.complete(started.id(), new ItemStack(Items.BREAD)), "completion retains the original quantity accounting");
        helper.assertTrue(queue.available(manual.id(), 0) > 0, "manual production keeps its independent authority");
        var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.productionQueues().get(area.id()).available(managed.id(), 0), 0L,
                "reload reinstalls authorization checks around native queues");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void retargetingMaintainOrdersPreservesStartedProductionAndCargo(GameTestHelper helper) {
        var queue = new com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue(UUID.randomUUID(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue.Scope.WORKSITE,
                com.jvn.villagerretaliation.interaction.HiredVillagerRole.CRAFTSMAN, ignored -> {});
        var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(helper.getLevel(),
                HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("oak_planks"), Map.of()),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.MAINTAIN_STOCK, 32, BlockPos.ZERO);
        queue.append(order);
        var started = queue.reserve(UUID.randomUUID(), order.id(), 4, 1, 0, null);
        queue.start(started.id());
        var planned = queue.reserve(UUID.randomUUID(), order.id(), 4, 1, 0, null);
        helper.assertTrue(queue.maintainTarget(order.id(), 1), "a reduced deficit can resize its existing production order");
        started = queue.allocation(started.worker());
        helper.assertValueEqual(queue.allocations(), List.of(started), "retargeting releases only unstarted batches");
        helper.assertTrue(queue.allocation(planned.worker()) == null, "unstarted input demand is released");
        helper.assertTrue(queue.complete(started.id(), new ItemStack(Items.OAK_PLANKS, 4)), "an already started native batch finishes at its real yield");
        helper.assertValueEqual(queue.order(order.id()).produced(), 4L, "retargeting preserves completed production totals");
        var transfers = new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue(UUID.randomUUID(),
                com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Scope.WORKSITE, ignored -> {});
        var delivery = com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Order.create(
                new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Endpoint(BlockPos.ZERO, null),
                new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Endpoint(BlockPos.ZERO.east(), null),
                new ItemStack(Items.OAK_PLANKS), 64, com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Mode.MAINTAIN_STOCK);
        transfers.append(delivery);
        var cargo = transfers.reserve(UUID.randomUUID(), delivery.id(), 12, 0);
        transfers.picked(cargo.id(), 12);
        var pickup = transfers.reserve(UUID.randomUUID(), delivery.id(), 8, 12);
        helper.assertTrue(transfers.maintainTarget(delivery.id(), 4), "a delivery retains its stable order when the target shrinks");
        helper.assertTrue(transfers.allocation(pickup.worker()) == null, "planned pickup is released");
        helper.assertValueEqual(transfers.allocation(cargo.worker()).amount(), 12, "carried cargo is not confiscated");
        helper.assertTrue(transfers.delivered(cargo.id(), 12), "the original cargo can finish delivery");
        var restored = com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.load(
                transfers.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), ignored -> {});
        helper.assertValueEqual(restored.order(delivery.id()).delivered(), 12L, "retargeted receipts remain balanced after reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void overlappingStockTargetsRequestOnlyTheirCombinedDeficit(GameTestHelper helper) {
        var sample = new ItemStack(Items.WHEAT);
        var floors = new java.util.ArrayList<com.jvn.villagerretaliation.interaction.work.production.WorkSupplyAllocation.Request>();
        for (int amount : List.of(16, 32, 8)) {
            var consumer = UUID.randomUUID();
            var requirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("stock_" + amount, sample, amount);
            var demand = new com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand(
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(helper.getLevel().registryAccess(), consumer,
                            com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE, requirement),
                    consumer, com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE, requirement,
                    amount, amount, 0, amount == 16 ? 9 : amount == 32 ? 5 : 1, 0, 0, 0, 0, true, List.of(BlockPos.ZERO), BlockPos.ZERO,
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Blocker.of(""));
            floors.add(new com.jvn.villagerretaliation.interaction.work.production.WorkSupplyAllocation.Request(demand, 0));
        }
        for (int count : List.of(0, 10, 32, 40)) {
            var allocated = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyAllocation.allocate(floors, List.of(
                    new com.jvn.villagerretaliation.interaction.work.production.WorkSupplyAllocation.Stock(BlockPos.ZERO, sample, count, 0, 0, true)));
            helper.assertValueEqual(allocated.demands().stream().mapToLong(
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand::missing).sum(), (long) Math.max(0, 32 - count),
                    "overlapping reserve policies propose only the gap to the largest target");
            helper.assertValueEqual(allocated.allowance(java.util.Set.of(), BlockPos.ZERO, sample), (long) Math.max(0, count - 32),
                    "merged targets preserve the same physical surplus");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void managedOrdersReuseBindingsAndRespectManualStops(GameTestHelper helper) {
        var level = helper.getLevel();
        var actor = UUID.randomUUID();
        var town = new SettlementRecord(new com.jvn.villagerretaliation.allegiance.VillageAllegianceId(UUID.randomUUID()),
                level.dimension().location(), BlockPos.ZERO, actor, 0, () -> {});
        var step = bake();
        town.putArea(actor, new SettlementArea(step.area(), SettlementAreaType.WORKSHOP, "Bakery",
                BlockPos.ZERO, new BlockPos(3, 2, 3), 5, true));
        town.setWorksitePlan(actor, step.area(), SettlementWorksitePlan.defaults(com.jvn.villagerretaliation.interaction.HiredVillagerRole.CRAFTSMAN));
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), configuration(List.of(step), List.of(
                new SettlementSupplyPolicy.Connection(step.id(), null, new UUID(0, 11)))), actor, town::supplyChanged);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, actor, "", level.getGameTime());
        for (int index = 0; index < 20; index++)
            helper.assertValueEqual(SettlementPolicyOrders.reconcile(level, town, policy, step, 12), "", "repeated evaluation is idempotent");
        var binding = policy.bindings().getFirst();
        var queue = town.productionQueues().get(step.area());
        helper.assertValueEqual(queue.orders().size(), 1, "reevaluation must not append duplicate orders");
        helper.assertValueEqual(SettlementPolicyOrders.reconcile(level, town, policy, step, 0), "", "a satisfied target parks its order");
        helper.assertTrue(queue.order(binding.order()).paused(), "parked execution does not continue producing");
        helper.assertValueEqual(SettlementPolicyOrders.reconcile(level, town, policy, step, 16), "", "new consumption reuses the parked order");
        helper.assertValueEqual(policy.bindings().getFirst().order(), binding.order(), "the managed identity is retained across replenishment");
        queue.pause(binding.order(), true);
        helper.assertValueEqual(SettlementPolicyOrders.reconcile(level, town, policy, step, 16), "policy_order_suspended",
                "a manual pause is not silently undone");
        queue.remove(binding.order());
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        var restoredPolicy = restored.supplyPolicies().get(policy.id());
        helper.assertValueEqual(SettlementPolicyOrders.reconcile(level, restored, restoredPolicy, step, 16), "policy_order_suspended",
                "a removed managed order remains suspended after reload");
        helper.assertTrue(restored.productionQueues().get(step.area()).orders().isEmpty(), "reconciliation does not recreate a removed order");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void destinationEditsDrainOriginalReceiptsBeforeRetiringBindings(GameTestHelper helper) {
        var level = helper.getLevel();
        var actor = UUID.randomUUID();
        var town = new SettlementRecord(new com.jvn.villagerretaliation.allegiance.VillageAllegianceId(UUID.randomUUID()),
                level.dimension().location(), BlockPos.ZERO, actor, 0, () -> {});
        var step = bake();
        town.putArea(actor, new SettlementArea(step.area(), SettlementAreaType.WORKSHOP, "Bakery",
                BlockPos.ZERO, new BlockPos(3, 2, 3), 5, true));
        town.setWorksitePlan(actor, step.area(), SettlementWorksitePlan.defaults(com.jvn.villagerretaliation.interaction.HiredVillagerRole.CRAFTSMAN));
        var connections = List.of(new SettlementSupplyPolicy.Connection(step.id(), null, new UUID(0, 11)));
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), configuration(List.of(step), connections), actor, town::supplyChanged);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, actor, "", level.getGameTime());
        SettlementPolicyOrders.reconcile(level, town, policy, step, 12);
        var original = policy.bindings().getFirst();
        var queue = town.productionQueues().get(step.area());
        var allocation = queue.reserve(UUID.randomUUID(), original.order(), 1, 1, 0, null);
        queue.start(allocation.id());
        var waiting = queue.reserve(UUID.randomUUID(), original.order(), 1, 1, 0, null);
        var replacement = new SettlementSupplyPolicy.Step(step.id(), step.kind(), step.area(), step.recipe(),
                ItemStack.EMPTY, null, step.destination().east());
        policy.configure(configuration(List.of(replacement), connections), actor, level.getGameTime());
        SettlementPolicyOrders.retire(level, town, policy);
        helper.assertTrue(queue.allocations().stream().noneMatch(value -> value.id().equals(waiting.id())), "unstarted work is released");
        helper.assertValueEqual(queue.order(original.order()).storage(), step.destination(), "started receipts retain their original destination");
        helper.assertFalse(queue.runnable(original.order()), "the retired definition cannot start new batches");
        helper.assertValueEqual(SettlementPolicyOrders.reconcile(level, town, policy, replacement, 12), "", "the replacement has a new execution binding");
        helper.assertValueEqual(policy.bindings().size(), 2, "the old receipt remains separate while it finishes");
        helper.assertTrue(queue.complete(allocation.id(), new ItemStack(Items.BREAD)), "the original receipt can finish");
        SettlementPolicyOrders.retire(level, town, policy);
        helper.assertValueEqual(policy.bindings().size(), 1, "completed retired bindings are removed");
        helper.assertTrue(queue.order(original.order()) == null, "only the retired native order is removed");
        helper.assertValueEqual(queue.orders().getFirst().storage(), replacement.destination(), "the replacement retains the new destination");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void configuredChainOnlyReleasesUpstreamWorkForRealNativeDemand(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var farm = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.FARM).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var gather = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER, farm.id(),
                null, new ItemStack(Items.WHEAT), null, depot.position());
        var policy = stockPolicy(helper, town, producer, 12, 5);
        var requirement = SettlementPolicyPlanner.inputs(level, town, producer, 1).getFirst();
        policy.configure(new SettlementSupplyPolicy.Configuration("Food chain", policy.configuration().target(), 5, List.of(gather, producer),
                List.of(new SettlementSupplyPolicy.Connection(gather.id(), producer.id(), requirement.id()),
                        policy.configuration().connections().getFirst())), town.leader(), level.getGameTime());
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        var preview = new java.util.ArrayList<SettlementPolicyPlanner.Plan>();
        finish(SettlementPolicyPlanner.plan(level, town, policy, SettlementDemandService.inspect(level, town), new java.util.HashSet<>(), preview::add));
        var gathering = preview.getFirst().steps().stream().filter(step -> step.definition().equals(gather)).findFirst().orElseThrow();
        helper.assertValueEqual(gathering.proposed(), 36L, "the explicit bread recipe proposes three wheat for every loaf");
        helper.assertValueEqual(gathering.requested(), 0L, "proposed production cannot create a real upstream acquisition");
        helper.assertTrue(town.productionQueues().isEmpty(), "previewing a chain cannot create native work");
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.ACTIVE, "a valid observed chain stays active");
        helper.assertValueEqual(town.productionQueues().get(bakery.id()).orders().size(), 1, "the root creates one native order");
        helper.assertValueEqual(SettlementPolicyOrders.current(policy, gather).state(), SettlementSupplyPolicy.BindingState.PARKED,
                "upstream work waits for the new native demand");
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(SettlementPolicyOrders.current(policy, gather).target(), 36, "the next cycle sees the real native wheat requirement");
        for (int index = 0; index < 8; index++) finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(town.productionQueues().get(bakery.id()).orders().size(), 1, "repeated shortages retain the same native order");
        depot.setItem(0, new ItemStack(Items.WHEAT, 36));
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(SettlementPolicyOrders.current(policy, gather).state(), SettlementSupplyPolicy.BindingState.PARKED,
                "real input stock satisfies the gathering quota");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void repeatedConsumptionAndTargetReductionKeepOneBoundedNativeOrder(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var policy = stockPolicy(helper, town, producer, 12, 5);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        finish(SettlementPolicyService.cycle(level, town));
        var binding = policy.bindings().getFirst();
        var queue = town.productionQueues().get(bakery.id());
        var waiting = queue.reserve(UUID.randomUUID(), binding.order(), 1, 12, 0, null);
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null,
                depot.position(), new ItemStack(Items.BREAD), 4);
        policy.configure(new SettlementSupplyPolicy.Configuration("Smaller reserve", target, 5,
                policy.configuration().steps(), policy.configuration().connections()), town.leader(), level.getGameTime());
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(queue.order(binding.order()).amount(), 4, "a reduced target releases excessive unstarted work");
        helper.assertTrue(queue.allocations().stream().noneMatch(value -> value.id().equals(waiting.id())), "the old planned batch is released");
        for (int day = 0; day < 4; day++) {
            depot.setItem(0, new ItemStack(Items.BREAD, 4));
            finish(SettlementPolicyService.cycle(level, town));
            helper.assertTrue(queue.order(binding.order()).paused(), "a stocked reserve rests");
            depot.removeItem(0, 3);
            finish(SettlementPolicyService.cycle(level, town));
            helper.assertValueEqual(queue.available(binding.order(), 1), 3L, "consumption requests only the missing stock");
        }
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        finish(SettlementPolicyService.cycle(level, restored));
        helper.assertValueEqual(restored.productionQueues().get(bakery.id()).orders().size(), 1, "reload does not duplicate the replenishment order");
        helper.assertValueEqual(restored.supplyPolicies().get(policy.id()).bindings().getFirst().order(), binding.order(),
                "reload retains the managed execution identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void invalidStationSuspendsNewWorkAndReleasesOnlyUnstartedReceipts(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var policy = stockPolicy(helper, town, producer, 12, 5);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        finish(SettlementPolicyService.cycle(level, town));
        var order = policy.bindings().getFirst().order();
        var queue = town.productionQueues().get(bakery.id());
        var started = queue.reserve(UUID.randomUUID(), order, 1, 1, 0, null);
        queue.start(started.id());
        queue.reserve(UUID.randomUUID(), order, 1, 1, 0, null);
        town.recordSurvey(bakery.id(), new SettlementBuildingSurvey.Survey(0, List.of(), List.of(), true, java.util.Map.of()));
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.SUSPENDED, "missing approved stations suspend the chain");
        helper.assertValueEqual(policy.blocker(), "no_crafting_table", "the blocker identifies the missing station");
        helper.assertValueEqual(queue.allocations().size(), 1, "only the started receipt remains");
        helper.assertTrue(queue.complete(started.id(), new ItemStack(Items.BREAD)), "started work keeps its safe completion path");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void gatheringQuotasRecheckCargoAndPreserveActiveActions(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var farm = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.FARM).findFirst().orElseThrow();
        var depot = town.depots().values().iterator().next();
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        villager.setPos(farm.center().getBottomCenter());
        villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, villager, town.id(),
                com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), farm.id());
        SettlementWorkforceService.reconcile(level, villager);
        var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(villager);
        var worker = SettlementWorkerState.capture(level, villager, new SettlementWorkforceService.Duty(town, farm), 0);
        town.recordWorker(worker);
        helper.assertFalse(SettlementGatheringPolicy.inspect(level, villager).managed(), "unmanaged gathering is unchanged");
        var step = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER, farm.id(),
                null, new ItemStack(Items.WHEAT), null, depot.position());
        var id = UUID.randomUUID();
        var requirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("policy_stock", step.sample(), 4);
        var policy = new SettlementSupplyPolicy(id, new SettlementSupplyPolicy.Configuration("Gather wheat",
                new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, depot.position(), step.sample(), 4), 5,
                List.of(step), List.of(new SettlementSupplyPolicy.Connection(step.id(), null,
                        com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(level.registryAccess(), id,
                                com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE, requirement)))),
                town.leader(), town::supplyChanged);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        helper.assertFalse(SettlementGatheringPolicy.inspect(level, villager).allowed(), "activation waits for an evaluated quota");
        SettlementPolicyOrders.reconcile(level, town, policy, step, 4);
        helper.assertTrue(SettlementGatheringPolicy.inspect(level, villager).allowed(), "an evaluated shortage permits native gathering");
        var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager);
        helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock.destination(state),
                depot.position(), "the approved destination is pinned before output is gathered");
        villager.setPos(helper.absolutePos(BlockPos.ZERO).getBottomCenter());
        level.addFreshEntity(villager);
        helper.assertTrue(inventory.insertOutput(new ItemStack(Items.WHEAT, 4)).isEmpty(), "native output insertion stores the completed harvest");
        helper.assertValueEqual(policy.bindings().getFirst().gathered(), 4L, "real loaded harvesting consumes the evaluation budget");
        var gathered = inventory.removeItemNoUpdate(inventory.outputSlots().getFirst());
        helper.assertFalse(SettlementGatheringPolicy.inspect(level, villager).allowed(), "consuming the harvest cannot reopen the old quota");
        inventory.setItem(inventory.outputSlots().getFirst(), gathered);
        helper.assertFalse(SettlementGatheringPolicy.inspect(level, villager).allowed(), "carried output satisfies the quota before deposit");
        helper.assertFalse(com.jvn.villagerretaliation.interaction.work.HiredFarmingInventoryBridge.permitsVanillaHarvest(level, villager),
                "the vanilla farmer brain cannot continue harvesting around a closed quota");
        state.putInt("ProgressTicks", 2);
        helper.assertTrue(SettlementGatheringPolicy.inspect(level, villager).allowed(), "an action already in progress may finish");
        policy.state(SettlementSupplyPolicy.State.PAUSED, town.leader(), "", level.getGameTime());
        helper.assertTrue(SettlementGatheringPolicy.inspect(level, villager).allowed(), "pausing preserves the same active action");
        state.putInt("ProgressTicks", 0);
        helper.assertFalse(SettlementGatheringPolicy.inspect(level, villager).allowed(), "a paused quota stops the following action");
        com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock.prepareDestination(worker.settings(), false, depot.position());
        worker.inventorySnapshot(inventory.captureSnapshot());
        villager.setPos(farm.center().getBottomCenter());
        villager.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        SettlementBackgroundFarming.tick(level, town, worker, 2_000, 1);
        helper.assertValueEqual(depot.count(stack -> stack.is(Items.WHEAT)), 4, "background handoff delivers completed output before resting");
        helper.assertTrue(worker.outputSlots().stream().allMatch(slot -> worker.items(level.registryAccess()).get(slot).isEmpty()),
                "delivery transfers ownership out of worker inventory");
        helper.assertTrue(com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock.destination(worker.settings()) == null,
                "completed delivery clears the cargo destination receipt");
        helper.assertValueEqual(policy.bindings().getFirst().step().destination(), depot.position(),
                "the paused binding retains its approved destination");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        var savedWorker = restored.workers().get(worker.id());
        helper.assertFalse(SettlementGatheringPolicy.inspect(level, restored, farm.id(), worker.id(), savedWorker.settings(), List.of()).allowed(),
                "a paused gathering binding remains controlled after reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void managedProductionAndDeliveryNeedAnotherEvaluationAfterUsingTheirBudget(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var policy = stockPolicy(helper, town, producer, 3, 5);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        finish(SettlementPolicyService.cycle(level, town));
        var order = policy.bindings().getFirst().order();
        var queue = town.productionQueues().get(bakery.id());
        var allocation = queue.reserve(UUID.randomUUID(), order, 1, 3, 0, null);
        helper.assertTrue(queue.start(allocation.id()), "the evaluated quantity can start");
        helper.assertTrue(queue.complete(allocation.id(), new ItemStack(Items.BREAD, 3)), "the quantity is recorded by native production");
        helper.assertValueEqual(queue.available(order, 0), 0L, "consuming all output does not renew an old evaluation");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.productionQueues().get(bakery.id()).available(order, 0), 0L, "the production limit survives reload");
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(queue.available(order, 0), 3L, "a fresh evaluation renews only the current deficit");
        var delivery = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.DELIVERY, depot.area(),
                null, new ItemStack(Items.BREAD), depot.position().east(), depot.position());
        var deliveries = stockPolicy(helper, town, delivery, 3, 5);
        town.addSupplyPolicy(deliveries);
        deliveries.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        SettlementPolicyOrders.reconcile(level, town, deliveries, delivery, 3);
        var transferQueue = town.transferQueues().get(depot.area());
        var transfer = deliveries.bindings().getFirst().order();
        var cargo = transferQueue.reserve(UUID.randomUUID(), transfer, 3, 0);
        helper.assertTrue(transferQueue.picked(cargo.id(), 3), "the evaluated delivery can pick up its goods");
        helper.assertTrue(transferQueue.delivered(cargo.id(), 3), "physical delivery completes the receipt");
        helper.assertValueEqual(transferQueue.available(transfer, 0), 0, "consumption cannot reuse a completed delivery budget");
        restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.transferQueues().get(depot.area()).available(transfer, 0), 0, "the delivery limit survives reload");
        SettlementPolicyOrders.reconcile(level, town, deliveries, delivery, 3);
        helper.assertValueEqual(transferQueue.available(transfer, 0), 3, "an evaluated shortage renews delivery");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void stockArrivingDuringAnyPlanningStageReducesTheRenewedQuantity(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var policy = stockPolicy(helper, town, producer, 12, 5);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        finish(SettlementPolicyService.cycle(level, town));
        var queue = town.productionQueues().get(bakery.id());
        var order = policy.bindings().getFirst().order();
        for (int stage = 0; stage < 64; stage++) {
            depot.setItem(0, ItemStack.EMPTY);
            finish(SettlementPolicyService.cycle(level, town));
            var work = SettlementPolicyService.cycle(level, town);
            for (int job = 0; job < stage && !SettlementWork.complete(work); job++) work.run();
            depot.setItem(0, new ItemStack(Items.BREAD, 9));
            finish(work);
            helper.assertTrue(queue.available(order, 9) <= 3, "arriving stock cannot authorize excess production at scheduler stage " + stage);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void planningDetectsProductionProgressAndReservationChanges(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var queue = town.beginProduction(town.leader(), bakery.id());
        var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level, bake().recipe(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 3, null);
        queue.append(order);
        var snapshot = SettlementDemandService.inspect(level, town);
        helper.assertTrue(snapshot.current(order.id()), "unchanged demand remains usable");
        var allocation = queue.reserve(UUID.randomUUID(), order.id(), 1, 3, 0, null);
        helper.assertFalse(snapshot.current(order.id()), "new commitments invalidate a cached consumer plan");
        snapshot = SettlementDemandService.inspect(level, town);
        queue.start(allocation.id());
        queue.complete(allocation.id(), new ItemStack(Items.BREAD, 3));
        helper.assertFalse(snapshot.current(order.id()), "completed production invalidates the old ingredient shortage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void backgroundHarvestCountsOnceAndRestsAfterDeliveryUntilReevaluation(GameTestHelper helper) {
        SettlementFarmingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var output = fixture.output();
            SettlementTestPlots.claim(level, town, fixture.area().min().west(3), output.position().offset(2, 3, 2));
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
            var step = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER, fixture.area().id(),
                    null, new ItemStack(Items.WHEAT), null, output.position());
            var policy = stockPolicy(helper, town, step, step.sample(), 1, 5);
            town.addSupplyPolicy(policy);
            policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
            finish(SettlementPolicyService.cycle(level, town));
            helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.ACTIVE, "the observed farm has a valid quota");
            SettlementBackgroundFarming.tick(level, town, fixture.worker(), 2_000, 1);
            helper.assertValueEqual(policy.bindings().getFirst().gathered(), 1L, "one native crop harvest consumes one output unit");
            SettlementBackgroundFarming.tick(level, town, fixture.worker(), 2_000, 1);
            helper.assertValueEqual(output.count(stack -> stack.is(Items.WHEAT)), 1, "the closed quota finishes physical delivery");
            for (int slot = 0; slot < output.getContainerSize(); slot++) if (output.getItem(slot).is(Items.WHEAT)) output.removeItem(slot, 1);
            fixture.plot().change(fixture.plot().soil(), ((net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT).getStateForAge(7));
            SettlementBackgroundFarming.tick(level, town, fixture.worker(), 2_000, 1);
            helper.assertTrue(fixture.plot().harvestable(), "stock consumption cannot reopen the previous gathering budget");
            helper.assertValueEqual(policy.bindings().getFirst().gathered(), 1L, "delivery never counts as another harvest");
            finish(SettlementPolicyService.cycle(level, town));
            SettlementBackgroundFarming.tick(level, town, fixture.worker(), 2_000, 1);
            helper.assertValueEqual(policy.bindings().getFirst().gathered(), 2L, "a reevaluated shortage permits the next real harvest");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(restored.supplyPolicies().get(policy.id()).bindings().getFirst().gathered(), 2L, "gathering progress survives handoff and reload");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void nativeFarmAndBakeryReplenishConsumedBreadWithoutDuplicateWork(GameTestHelper helper) {
        SettlementFarmingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var output = fixture.output();
            output.configure(SettlementDepotPurpose.GENERAL, 5);
            var kitchen = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Bakery",
                    fixture.area().min().east(4), fixture.area().min().offset(6, 2, 2), 5, true);
            SettlementTestPlots.claim(level, town, fixture.area().min().west(3), output.position().offset(2, 3, 2));
            town.putArea(town.leader(), kitchen);
            town.setWorksitePlan(town.leader(), kitchen.id(), SettlementWorksitePlan.defaults(kitchen.type().role()));
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
            town.recordSurvey(kitchen.id(), new SettlementBuildingSurvey.Survey(0, List.of(), List.of(kitchen.min()), true,
                    Map.of(kitchen.min(), ResourceLocation.withDefaultNamespace("crafting_table"))));
            var cook = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            cook.setPos(kitchen.min().getCenter());
            cook.setNoAi(true);
            com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, cook, town.id(),
                    com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
            town.assignDuty(cook.getUUID(), kitchen.id());
            SettlementWorkforceService.reconcile(level, cook);
            var worker = SettlementWorkerState.capture(level, cook, SettlementWorkforceService.duty(cook).orElseThrow(), 0L);
            town.recordWorker(worker);
            town.recordResident(SettlementResidentState.capture(cook, town, 0L));
            var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION,
                    kitchen.id(), bake().recipe(), ItemStack.EMPTY, null, output.position());
            var gather = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER,
                    fixture.area().id(), null, new ItemStack(Items.WHEAT), null, output.position());
            var policy = stockPolicy(helper, town, producer, 2, 5);
            var wheat = SettlementPolicyPlanner.inputs(level, town, producer, 1).getFirst();
            policy.configure(new SettlementSupplyPolicy.Configuration("Farm and bakery", policy.configuration().target(), 5,
                    List.of(gather, producer), List.of(new SettlementSupplyPolicy.Connection(gather.id(), producer.id(), wheat.id()),
                    policy.configuration().connections().getFirst())), town.leader(), level.getGameTime());
            town.addSupplyPolicy(policy);
            policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
            UUID order = null;
            for (int meal = 0; meal < 4; meal++) {
                for (int cycle = 0; cycle < 64 && output.count(stack -> stack.is(Items.BREAD)) < 2; cycle++) {
                    fixture.plot().change(fixture.plot().soil(), ((net.minecraft.world.level.block.CropBlock)
                            net.minecraft.world.level.block.Blocks.WHEAT).getStateForAge(7));
                    finish(SettlementPolicyService.cycle(level, town));
                    SettlementTestPlots.planSupplies(level, town);
                    SettlementBackgroundFarming.tick(level, town, fixture.worker(), 2_000, 1);
                    SettlementTestPlots.planSupplies(level, town);
                    SettlementBackgroundCrafting.tick(level, town, worker, 2_000, 1);
                }
                helper.assertValueEqual(output.count(stack -> stack.is(Items.BREAD)), 2,
                        "native harvests, hauling and recipe execution restore the same two loaves after meal " + meal
                                + " with farm " + fixture.worker().status() + " and bakery " + worker.status() + " and policy " + policy.blocker());
                var binding = SettlementPolicyOrders.current(policy, producer);
                if (order == null) order = binding.order();
                helper.assertValueEqual(binding.order(), order, "replenishment retains the managed order identity");
                helper.assertValueEqual(town.productionQueues().get(kitchen.id()).orders().size(), 1, "the bakery never accumulates duplicate orders");
                finish(SettlementPolicyService.cycle(level, town));
                var harvested = SettlementPolicyOrders.current(policy, gather).gathered();
                SettlementBackgroundFarming.tick(level, town, fixture.worker(), 2_000, 1);
                helper.assertValueEqual(SettlementPolicyOrders.current(policy, gather).gathered(), harvested,
                        "a filled bread target rests its upstream gathering");
                for (int slot = 0; slot < output.getContainerSize(); slot++)
                    if (output.getItem(slot).is(Items.BREAD)) output.removeItem(slot, 2);
            }
            helper.assertValueEqual(town.productionQueues().get(kitchen.id()).order(order).produced(), 8L,
                    "four replenishments produce exactly the eight consumed loaves");
            helper.assertFalse(level.hasChunkAt(town.center()), "the full chain operates without loading its village");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void policyPreviewAndActivationRespectPermissionsAndConfigurationRevisions(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var player = helper.makeMockServerPlayerInLevel();
        town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var draft = stockPolicy(helper, town, producer, 12, 5);
        var preview = request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Preview(draft.configuration()));
        helper.assertValueEqual(preview.blocker(), "", "a viewer can inspect a valid chain");
        helper.assertFalse(preview.editable(), "inspection permission does not authorize activation");
        helper.assertValueEqual(preview.production().getFirst().proposed(), 12L, "preview shows the proposed output deficit");
        helper.assertTrue(town.supplyPolicies().isEmpty() && town.productionQueues().isEmpty(), "preview creates no policy, stock, or native orders");
        for (int slot = 0; slot < depot.getContainerSize(); slot++) depot.setItem(slot, new ItemStack(Items.STONE, 64));
        var full = request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Preview(draft.configuration()));
        helper.assertValueEqual(full.blocker(), "output_full", "preview identifies blocked destination capacity before activation");
        helper.assertValueEqual(full.production().getFirst().blocker(), "output_full", "the blocked producer carries the same explanation");
        for (int slot = 0; slot < depot.getContainerSize(); slot++) depot.setItem(slot, ItemStack.EMPTY);
        var rejected = request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(draft.configuration(), true));
        helper.assertFalse(rejected.applied(), "a viewer cannot activate production");
        town.setPermissions(town.leader(), player.getUUID(),
                java.util.Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF, SettlementPermission.LOGISTICS));
        var saved = request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(draft.configuration(), false));
        helper.assertTrue(saved.applied(), "an authorized manager can save a paused draft");
        var policy = town.supplyPolicies().get(draft.id());
        helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.PAUSED, "saving does not activate implicitly");
        helper.assertTrue(town.productionQueues().isEmpty(), "a saved draft performs no work");
        helper.assertFalse(request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(draft.configuration(), true)).applied(),
                "an old editor cannot overwrite the saved revision");
        helper.assertTrue(request(player, town, draft.id(), policy.revision(), bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Change(SettlementSupplyPolicy.State.ACTIVE)).applied(),
                "activation is explicit and records current authority");
        helper.assertValueEqual(policy.authority(), player.getUUID(), "execution remembers who authorized the chain");
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(town.productionQueues().get(bakery.id()).orders().size(), 1, "activation reconciles through the native queue");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.STREAM_CODEC.encode(buffer, preview);
            var decoded = com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.needs().getFirst().id(), preview.needs().getFirst().id(), "wire snapshots preserve semantic requirement identities");
            helper.assertValueEqual(decoded.production().getFirst().proposed(), 12L, "proposed quantity stays separate on the wire");
            helper.assertValueEqual(buffer.readableBytes(), 0, "the snapshot codec consumes its exact payload");
            var payload = new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload(town.id().value(), UUID.randomUUID(),
                    draft.id(), policy.revision(), bakery.id(), new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(draft.configuration(), false));
            com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.STREAM_CODEC.encode(buffer, payload);
            var decodedRequest = com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decodedRequest, payload, "policy commands preserve exact configuration and revision");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void removedRecipePreviewPreservesItsBlockerAcrossTheNetwork(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var player = helper.makeMockServerPlayerInLevel();
        town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                HiredProductionRecipe.exact(net.minecraft.resources.ResourceLocation.withDefaultNamespace("removed_policy_recipe"), java.util.Map.of()),
                ItemStack.EMPTY, null, depot.position());
        var draft = stockPolicy(helper, town, producer, 12, 5);
        var preview = request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Preview(draft.configuration()));
        helper.assertValueEqual(preview.blocker(), "recipe_unavailable", "removed recipes produce an actionable preview");
        helper.assertTrue(preview.production().getFirst().output().isEmpty(), "unavailable output has no fabricated item");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.STREAM_CODEC.encode(buffer, preview);
            var decoded = com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.blocker(), "recipe_unavailable", "the client receives the recipe blocker");
            helper.assertTrue(decoded.production().getFirst().output().isEmpty(), "missing output survives the wire round trip");
            helper.assertValueEqual(buffer.readableBytes(), 0, "the error snapshot consumes its exact payload");
        } finally { buffer.release(); }
        helper.assertTrue(town.supplyPolicies().isEmpty() && town.productionQueues().isEmpty(), "failed preview creates no execution");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void manualManagedOrderStopsRequireExplicitPolicyReactivationAndKeepStartedReceipts(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var player = helper.makeMockServerPlayerInLevel();
        town.setPermissions(town.leader(), player.getUUID(),
                java.util.Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF, SettlementPermission.LOGISTICS));
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var draft = stockPolicy(helper, town, producer, 12, 5);
        helper.assertTrue(request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(draft.configuration(), true)).applied(), "the chain activates");
        finish(SettlementPolicyService.cycle(level, town));
        var policy = town.supplyPolicies().get(draft.id());
        var binding = policy.bindings().getFirst();
        var queue = town.productionQueues().get(bakery.id());
        var started = queue.reserve(UUID.randomUUID(), binding.order(), 1, 1, 0, null);
        queue.start(started.id());
        queue.reserve(UUID.randomUUID(), binding.order(), 1, 1, 0, null);
        helper.assertValueEqual(SettlementProductionService.edit(player, town, bakery.id(), queue.configurationRevision(), binding.order(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.Edit.PAUSE, 0), "", "native order controls remain usable");
        helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.SUSPENDED, "manual pause immediately stops the managed chain");
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(queue.orders().size(), 1, "evaluation does not replace a manually paused order");
        helper.assertValueEqual(queue.allocations().size(), 1, "started production survives the pause");
        helper.assertTrue(request(player, town, policy.id(), policy.revision(), bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Change(SettlementSupplyPolicy.State.ACTIVE)).applied(),
                "explicit policy activation permits a replacement binding");
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(policy.bindings().size(), 2, "the original started receipt drains separately from the renewed chain");
        helper.assertTrue(queue.complete(started.id(), new ItemStack(Items.BREAD)), "the original paused order can finish its started receipt");
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(policy.bindings().size(), 1, "finished retired bindings leave one current execution");
        helper.assertFalse(policy.bindings().getFirst().order().equals(binding.order()), "reactivation never silently adopts a manual edit");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void policyPreviewRejectsUnlinkedStorageWithoutReturningItsContents(GameTestHelper helper) {
        var town = policyTown(helper);
        var player = helper.makeMockServerPlayerInLevel();
        town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var draft = stockPolicy(helper, town, producer, 12, 5);
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null,
                helper.absolutePos(BlockPos.ZERO), new ItemStack(Items.BREAD), 12);
        var config = new SettlementSupplyPolicy.Configuration("Unlinked target", target, 5, draft.configuration().steps(), draft.configuration().connections());
        var result = request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Preview(config));
        helper.assertValueEqual(result.blocker(), "policy_destination_unlinked", "policy inspection stays within the town's storage links");
        helper.assertTrue(result.needs().isEmpty() && result.production().isEmpty(), "rejected storage discloses no inventory counts");
        helper.assertTrue(town.supplyPolicies().isEmpty(), "a rejected preview remains read only");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void purchasePermissionsGateActivationAndExpansionWithoutBlockingShutdown(GameTestHelper helper) {
        var town = policyTown(helper);
        var player = helper.makeMockServerPlayerInLevel();
        var management = java.util.Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF, SettlementPermission.LOGISTICS);
        town.setPermissions(town.leader(), player.getUUID(), management);
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var draft = stockPolicy(helper, town, producer, 12, 5);
        var original = draft.configuration();
        var approval = new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, original.connections().getFirst().requirement(),
                ResourceLocation.withDefaultNamespace("bread"), depot.position(), com.jvn.villagerretaliation.sell.CurrencyAmount.of(2, 1));
        var purchases = new SettlementPurchasePlan(true, com.jvn.villagerretaliation.sell.CurrencyAmount.of(20, 1), List.of(approval));
        var enabled = new SettlementSupplyPolicy.Configuration(original.name(), original.target(), original.priority(),
                original.steps(), original.connections(), purchases);
        var rejected = request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(enabled, false));
        helper.assertFalse(rejected.applied(), "a production manager cannot save new spending authority");
        helper.assertValueEqual(rejected.feedback(), "policy_permission_changed", "purchase permission has an actionable rejection");
        helper.assertTrue(town.supplyPolicies().isEmpty(), "rejected spending leaves no policy behind");
        var disabled = new SettlementSupplyPolicy.Configuration(original.name(), original.target(), original.priority(),
                original.steps(), original.connections(), new SettlementPurchasePlan(false, purchases.dailyBudget(), purchases.approvals()));
        helper.assertTrue(request(player, town, draft.id(), 0, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(disabled, true)).applied(),
                "disabled purchase settings do not require trade permission for local production");
        var policy = town.supplyPolicies().get(draft.id());
        town.setPermissions(town.leader(), player.getUUID(),
                java.util.Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF, SettlementPermission.LOGISTICS, SettlementPermission.TRADE));
        helper.assertTrue(request(player, town, policy.id(), policy.revision(), bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(enabled, true)).applied(),
                "an authorized trader can explicitly enable purchases");
        town.setPermissions(town.leader(), player.getUUID(), management);
        helper.assertTrue(request(player, town, policy.id(), policy.revision(), bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Change(SettlementSupplyPolicy.State.PAUSED)).applied(),
                "revoked trade permission does not prevent pausing spending");
        long pausedRevision = policy.revision();
        helper.assertFalse(request(player, town, policy.id(), pausedRevision, bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Change(SettlementSupplyPolicy.State.ACTIVE)).applied(),
                "existing purchase approvals cannot be reactivated without trade permission");
        helper.assertValueEqual(policy.revision(), pausedRevision, "rejected activation does not rewrite the saved policy");
        var expanded = new SettlementSupplyPolicy.Configuration(enabled.name(), enabled.target(), enabled.priority(), enabled.steps(),
                enabled.connections(), new SettlementPurchasePlan(true, com.jvn.villagerretaliation.sell.CurrencyAmount.of(40, 1), purchases.approvals()));
        helper.assertFalse(request(player, town, policy.id(), policy.revision(), bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(expanded, false)).applied(),
                "a paused policy cannot gain a larger budget without trade permission");
        var renamed = new SettlementSupplyPolicy.Configuration("Renamed reserve", enabled.target(), enabled.priority(),
                enabled.steps(), enabled.connections(), purchases);
        helper.assertTrue(request(player, town, policy.id(), policy.revision(), bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(renamed, false)).applied(),
                "editing a paused label does not authorize new spending");
        helper.assertTrue(request(player, town, policy.id(), policy.revision(), bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(disabled, true)).applied(),
                "a manager can disable purchasing and resume local production");
        helper.assertTrue(SettlementPolicyExecution.authorized(town, policy), "local execution remains authorized without trade");
        town.setPermissions(town.leader(), player.getUUID(),
                java.util.Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF, SettlementPermission.LOGISTICS, SettlementPermission.TRADE));
        helper.assertTrue(request(player, town, policy.id(), policy.revision(), bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(enabled, true)).applied(), "purchasing reactivates explicitly");
        town.setPermissions(town.leader(), player.getUUID(), management);
        helper.assertTrue(request(player, town, policy.id(), policy.revision(), bakery.id(),
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Change(SettlementSupplyPolicy.State.REMOVING)).applied(),
                "removing an active purchase policy never needs new spending authority");
        helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.REMOVING, "removal stops future execution");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void tradeRevocationStopsManagedStartsAndPreservesExistingReceipts(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var actor = UUID.randomUUID();
        town.setPermissions(town.leader(), actor,
                java.util.Set.of(SettlementPermission.STAFF, SettlementPermission.LOGISTICS, SettlementPermission.TRADE));
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var draft = stockPolicy(helper, town, producer, 12, 5);
        var original = draft.configuration();
        var approval = new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, original.connections().getFirst().requirement(),
                ResourceLocation.withDefaultNamespace("bread"), depot.position(), com.jvn.villagerretaliation.sell.CurrencyAmount.of(2, 1));
        var configuration = new SettlementSupplyPolicy.Configuration(original.name(), original.target(), original.priority(), original.steps(),
                original.connections(), new SettlementPurchasePlan(true, com.jvn.villagerretaliation.sell.CurrencyAmount.of(20, 1), List.of(approval)));
        var policy = new SettlementSupplyPolicy(draft.id(), configuration, actor, town::supplyChanged, town::changed);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, actor, "", level.getGameTime());
        finish(SettlementPolicyService.cycle(level, town));
        var binding = policy.bindings().getFirst();
        var queue = town.productionQueues().get(bakery.id());
        var worker = UUID.randomUUID();
        town.assignDuty(worker, bakery.id());
        var started = queue.reserve(worker, binding.order(), 1, 1, 0, null);
        helper.assertTrue(started != null && queue.start(started.id()), "authorized work starts through the existing native queue");
        var unstarted = queue.reserve(UUID.randomUUID(), binding.order(), 1, 1, 0, null);
        helper.assertTrue(unstarted != null, "the fixture includes an unstarted reservation");
        policy.purchaseShortage(approval.id(), new SettlementSupplyPolicy.PurchaseShortage(approval.requirement(), 0, 12));
        town.setPermissions(town.leader(), actor, java.util.Set.of(SettlementPermission.STAFF, SettlementPermission.LOGISTICS));
        helper.assertFalse(SettlementPolicyExecution.authorized(town, policy), "trade revocation immediately invalidates purchase authority");
        helper.assertValueEqual(queue.available(binding.order(), 0), 0L, "revocation prevents new work before the scheduler catches up");
        helper.assertTrue(SettlementPolicyExecution.acquisitionAllowed(town, worker, binding.order()),
                "the worker holding a started receipt may finish it safely");
        helper.assertFalse(SettlementPolicyExecution.acquisitionAllowed(town, UUID.randomUUID(), binding.order()),
                "a different worker cannot borrow the started receipt exception");
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.SUSPENDED, "the policy visibly suspends after revocation");
        helper.assertValueEqual(policy.blocker(), "policy_permission_changed", "the suspension explains its missing authorization");
        helper.assertTrue(policy.purchaseShortages().isEmpty(), "suspended purchasing loses accumulated eligibility");
        helper.assertValueEqual(queue.allocations().size(), 1, "only started work retains its reservation");
        helper.assertTrue(queue.complete(started.id(), new ItemStack(Items.BREAD)), "the existing receipt completes without losing its output");
        town.setPermissions(town.leader(), actor,
                java.util.Set.of(SettlementPermission.STAFF, SettlementPermission.LOGISTICS, SettlementPermission.TRADE));
        finish(SettlementPolicyService.cycle(level, town));
        helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.SUSPENDED, "restoring trade permission does not silently reactivate spending");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.supplyPolicies().get(policy.id()).state(), SettlementSupplyPolicy.State.SUSPENDED,
                "reload preserves the explicit reactivation requirement");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void purchaseMarketPagesExposeEveryCompatibleCommodityWithoutSpendingAuthority(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var player = helper.makeMockServerPlayerInLevel();
        town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var id = UUID.randomUUID();
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.FOOD, town.id().value(), null, null, ItemStack.EMPTY, 0);
        var configuration = new SettlementSupplyPolicy.Configuration("Food imports", target, 5, List.of(), List.of());
        var requirement = SettlementFoodService.requirement(0);
        var requirementId = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(level.registryAccess(), town.id().value(),
                com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD, requirement);
        var expected = com.jvn.villagerretaliation.sell.SellPriceResources.definitions(level.getServer()).keySet().stream()
                .filter(item -> requirement.value(item.getDefaultInstance()) > 0)
                .filter(item -> com.jvn.villagerretaliation.sell.SellPriceResources.definition(level.getServer(), item.getDefaultInstance()).isPresent())
                .filter(item -> com.jvn.villagerretaliation.sell.VillageSellMarket.quote(level, town.center(), item.getDefaultInstance())
                        .filter(quote -> !quote.baseUnitPrice().isZero()).isPresent())
                .map(net.minecraft.core.registries.BuiltInRegistries.ITEM::getKey).sorted().toList();
        helper.assertTrue(expected.size() > 16, "the real food market exceeds the ordinary preview sample limit");
        var found = new java.util.ArrayList<ResourceLocation>();
        var before = town.economy().save();
        com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload last = null;
        for (int page = 0; page <= expected.size() / com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.MARKET_PAGE_SIZE; page++) {
            last = request(player, town, id, 0, null,
                    new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Market(configuration, null, requirementId, "", page));
            helper.assertTrue(last.market() != null && last.feedback().isEmpty(), "authorized inspection returns a market page");
            helper.assertFalse(last.trade() || last.editable() || last.applied(), "market inspection grants no spending or production authority");
            helper.assertTrue(last.market().commodities().size() <= com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.MARKET_PAGE_SIZE,
                    "market responses stay bounded");
            helper.assertValueEqual(last.market().depots().size(), 1, "food purchases identify the approved supply depot");
            found.addAll(last.market().commodities().stream().map(com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.Commodity::item).toList());
            helper.assertValueEqual(last.market().more(), found.size() < expected.size(), "pagination reports whether another compatible commodity exists");
            if (!last.market().more()) break;
        }
        helper.assertValueEqual(found, expected, "all eligible market alternatives remain selectable in stable order without duplicate pages");
        var command = new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Market(configuration, null, requirementId, "minecraft:bread", 0);
        var bread = request(player, town, id, 0, null, command);
        helper.assertValueEqual(bread.market().commodities().stream().map(com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.Commodity::item).toList(),
                List.of(ResourceLocation.withDefaultNamespace("bread")), "server-side search keeps the exact compatible commodity");
        helper.assertValueEqual(town.economy().save(), before, "market queries do not change treasury or market accounting");
        helper.assertTrue(town.supplyPolicies().isEmpty() && town.productionQueues().isEmpty(), "catalogue inspection creates no policy or managed orders");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            var payload = new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload(town.id().value(), UUID.randomUUID(), id, 0, null, command);
            com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.STREAM_CODEC.decode(buffer), payload,
                    "market requests preserve draft configuration, requirement, search and page");
            com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.STREAM_CODEC.encode(buffer, bread);
            var decoded = com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.market(), bread.market(), "market pages preserve exact quotes and approved depot choices");
            helper.assertValueEqual(decoded.purchases(), bread.purchases(), "purchase allowances survive the snapshot codec");
            helper.assertValueEqual(decoded.trade(), bread.trade(), "trade permission remains separate on the wire");
            helper.assertValueEqual(buffer.readableBytes(), 0, "extended policy codecs consume their exact payload");
        } finally {
            buffer.release();
        }
        expectInvalid(helper, () -> new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Market(configuration, null, requirementId, "", -1));
        expectInvalid(helper, () -> new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Market(configuration, null, requirementId, "", 4097));
        expectInvalid(helper, () -> new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Market(configuration, null, requirementId, "x".repeat(97), 0));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void purchasePreviewReportsExactPricesAllowancesAndWaitingWithoutMutation(GameTestHelper helper) {
        var town = policyTown(helper);
        var player = helper.makeMockServerPlayerInLevel();
        town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        town.economy().credit(com.jvn.villagerretaliation.sell.CurrencyAmount.of(100, 1));
        town.economy().configureBudget(com.jvn.villagerretaliation.sell.CurrencyAmount.of(10, 1), com.jvn.villagerretaliation.sell.CurrencyAmount.of(30, 1));
        var depot = town.depots().values().iterator().next();
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var step = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var policy = stockPolicy(helper, town, step, 12, 5);
        var original = policy.configuration();
        var approval = new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, original.connections().getFirst().requirement(),
                ResourceLocation.withDefaultNamespace("bread"), depot.position(), com.jvn.villagerretaliation.sell.CurrencyAmount.of(2, 1));
        var configuration = new SettlementSupplyPolicy.Configuration(original.name(), original.target(), original.priority(), original.steps(), original.connections(),
                new SettlementPurchasePlan(true, com.jvn.villagerretaliation.sell.CurrencyAmount.of(20, 1), List.of(approval)));
        var saved = town.economy().save();
        var response = request(player, town, policy.id(), 0, null, new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Preview(configuration));
        var purchase = response.purchases().approvals().getFirst();
        helper.assertValueEqual(purchase.approval(), approval.id(), "purchase previews retain stable approval identities");
        helper.assertTrue(purchase.known(), "the empty observed depot has a known shortage");
        helper.assertValueEqual(purchase.missing(), 12L, "purchase previews use native allocated target deficits");
        helper.assertValueEqual(purchase.waitTicks(), SettlementPurchasePlan.SHORTAGE_TICKS, "a draft starts with the full persistent-shortage delay");
        helper.assertValueEqual(purchase.unitPrice(), com.jvn.villagerretaliation.sell.VillageSellMarket.quote(helper.getLevel(), town.center(), approval.sample())
                .orElseThrow().baseUnitPrice().multiply(2), "preview uses the exact native import price");
        helper.assertValueEqual(purchase.blocker(), "trade_status.stage_locked", "preview explains the native Village-stage restriction");
        helper.assertValueEqual(response.purchases().available(), com.jvn.villagerretaliation.sell.CurrencyAmount.of(20, 1),
                "the displayed allowance is bounded by the policy-wide cap");
        helper.assertTrue(response.purchases().spent().isZero(), "unactivated drafts have spent nothing");
        helper.assertValueEqual(town.economy().save(), saved, "preview never reserves or spends money");
        helper.assertTrue(town.supplyPolicies().isEmpty(), "preview does not create purchase authority");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.STREAM_CODEC.encode(buffer, response);
            var decoded = com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.purchases(), response.purchases(), "prices, shortage timers and budget limits remain exact on the wire");
            helper.assertValueEqual(decoded.gameTime(), response.gameTime(), "clients receive the authoritative observation time");
            helper.assertValueEqual(buffer.readableBytes(), 0, "purchase preview decoding leaves no unread fields");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void marketInspectionRechecksViewAuthorityAndRestrictsReceivingDepots(GameTestHelper helper) {
        var town = policyTown(helper);
        var player = helper.makeMockServerPlayerInLevel();
        town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var depot = town.depots().values().iterator().next();
        town.registerDepot(town.leader(), depot.area(), depot.position().east(), SettlementDepotPurpose.GENERAL).observeRules(List.of());
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var step = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var policy = stockPolicy(helper, town, step, 12, 5);
        var requirement = policy.configuration().connections().getFirst().requirement();
        var command = new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Market(policy.configuration(), null, requirement, "", 0);
        var response = request(player, town, policy.id(), 0, null, command);
        helper.assertValueEqual(response.market().depots().stream().map(com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload.Storage::position).toList(),
                List.of(depot.position()), "the market cannot substitute another depot for an explicitly selected stock source");
        var invalid = request(player, town, policy.id(), 0, null,
                new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Market(policy.configuration(), null, UUID.randomUUID(), "", 0));
        helper.assertValueEqual(invalid.feedback(), "policy_purchase_requirement", "unknown requirement identities are rejected");
        helper.assertTrue(invalid.market() == null, "invalid requirements disclose no market or depot data");
        var results = new java.util.ArrayList<com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload>();
        var work = SettlementPolicyManagementService.prepare(player, new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload(
                town.id().value(), UUID.randomUUID(), policy.id(), 0, null, command), results::add);
        work.run();
        town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of());
        finish(work);
        helper.assertValueEqual(results.size(), 1, "revocation still completes the original request once");
        helper.assertValueEqual(results.getFirst().feedback(), "policy_permission_changed", "permissions are checked again after incremental market scanning");
        helper.assertTrue(results.getFirst().market() == null && results.getFirst().needs().isEmpty() && results.getFirst().policies().isEmpty(),
                "revoked inspection returns no private settlement state");
        helper.succeed();
    }

    static com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload request(net.minecraft.server.level.ServerPlayer player,
            SettlementRecord town, UUID policy, long revision, UUID area, com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Command command) {
        var results = new java.util.ArrayList<com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload>();
        finish(SettlementPolicyManagementService.prepare(player, new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload(
                town.id().value(), UUID.randomUUID(), policy, revision, area, command), results::add));
        if (results.size() != 1) throw new AssertionError("A policy request must produce exactly one response");
        return results.getFirst();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_policy")
    public static void managedOrderLinksFollowNativeStateAndRequireCurrentViewingPermission(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = policyTown(helper);
        var viewer = helper.makeMockServerPlayerInLevel();
        town.setPermissions(town.leader(), viewer.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var depot = town.depots().values().iterator().next();
        var source = town.registerDepot(town.leader(), depot.area(), depot.position().east(), SettlementDepotPurpose.GENERAL);
        source.observeRules(List.of());
        var bakery = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var producer = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                bake().recipe(), ItemStack.EMPTY, null, depot.position());
        var productionPolicy = stockPolicy(helper, town, producer, 12, 5);
        town.addSupplyPolicy(productionPolicy);
        productionPolicy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        SettlementPolicyOrders.reconcile(level, town, productionPolicy, producer, 12);
        var production = town.productionQueues().get(bakery.id());
        var manualProduction = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                producer.recipe(), com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 3, depot.position());
        helper.assertTrue(production.append(manualProduction), "manual production remains independently queued");
        var delivery = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.DELIVERY, depot.area(),
                null, new ItemStack(Items.BREAD), source.position(), depot.position());
        var deliveryPolicy = stockPolicy(helper, town, delivery, 12, 5);
        town.addSupplyPolicy(deliveryPolicy);
        deliveryPolicy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
        SettlementPolicyOrders.reconcile(level, town, deliveryPolicy, delivery, 12);
        var transfers = town.transferQueues().get(depot.area());
        var manualDelivery = com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Order.create(
                new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Endpoint(source.position(), source.id()),
                new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Endpoint(depot.position(), depot.id()),
                new ItemStack(Items.BREAD), 3);
        helper.assertTrue(transfers.append(manualDelivery), "manual deliveries remain independently queued");
        var demandControls = SettlementDemandViewService.demands(viewer, town);
        helper.assertTrue(demandControls.stream().anyMatch(row -> row.consumer().equals(deliveryPolicy.bindings().getFirst().order())
                        && row.control() == com.jvn.villagerretaliation.network.SettlementManagementPayload.DemandControl.TRANSFER),
                "managed delivery demand opens its native transfer controls");
        helper.assertFalse(demandControls.stream().anyMatch(row -> row.consumer().equals(manualDelivery.id())),
                "manual deliveries remain accounted through receipts without policy input demand");
        var productionView = new com.jvn.villagerretaliation.network.ProductionRequestPayload(
                new com.jvn.villagerretaliation.network.ProductionTarget(town.id().value(), bakery.id()), UUID.randomUUID(), 0,
                new com.jvn.villagerretaliation.network.ProductionRequestPayload.View());
        var transferView = new com.jvn.villagerretaliation.network.TransferRequestPayload(
                new com.jvn.villagerretaliation.network.ProductionTarget(town.id().value(), depot.area()), UUID.randomUUID(), 0,
                new com.jvn.villagerretaliation.network.TransferRequestPayload.View());
        var productionSnapshot = SettlementProductionService.snapshot(viewer, productionView, false, "");
        var transferSnapshot = com.jvn.villagerretaliation.interaction.work.courier.TransferManagementService.snapshot(viewer, transferView, false, "");
        helper.assertFalse(productionSnapshot.editable() || transferSnapshot.editable(), "viewers inspect policy links without gaining editing permission");
        helper.assertValueEqual(productionSnapshot.managed().size(), 1, "manual production has no policy link");
        helper.assertValueEqual(transferSnapshot.managed().size(), 1, "manual deliveries have no policy link");
        helper.assertValueEqual(productionSnapshot.managed().getFirst().policy(), productionPolicy.id(), "production links to the exact policy");
        helper.assertValueEqual(transferSnapshot.managed().getFirst().binding(), deliveryPolicy.bindings().getFirst().id(), "delivery links to the exact execution binding");
        helper.assertValueEqual(productionSnapshot.managed().getFirst().order(), productionPolicy.bindings().getFirst().order(), "the link identifies the native order");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            com.jvn.villagerretaliation.network.ProductionSnapshotPayload.STREAM_CODEC.encode(buffer, productionSnapshot);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.ProductionSnapshotPayload.STREAM_CODEC.decode(buffer).managed(),
                    productionSnapshot.managed(), "production links retain names, identities and states over the wire");
            com.jvn.villagerretaliation.network.TransferSnapshotPayload.STREAM_CODEC.encode(buffer, transferSnapshot);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.TransferSnapshotPayload.STREAM_CODEC.decode(buffer).managed(),
                    transferSnapshot.managed(), "delivery links retain names, identities and states over the wire");
            helper.assertValueEqual(buffer.readableBytes(), 0, "both snapshots consume their exact payload");
        } finally { buffer.release(); }
        town.setPermissions(town.leader(), viewer.getUUID(),
                java.util.Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF, SettlementPermission.LOGISTICS));
        helper.assertValueEqual(SettlementProductionService.edit(viewer, town, bakery.id(), production.configurationRevision(),
                productionPolicy.bindings().getFirst().order(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.Edit.PAUSE, 0), "", "staff can pause managed production");
        helper.assertValueEqual(SettlementTransferService.edit(viewer, town, depot.area(), transfers.revision(),
                deliveryPolicy.bindings().getFirst().order(),
                com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Edit.CANCEL, 0), "", "logistics staff can cancel managed delivery");
        finish(SettlementPolicyService.cycle(level, town));
        productionSnapshot = SettlementProductionService.snapshot(viewer, productionView, false, "");
        transferSnapshot = com.jvn.villagerretaliation.interaction.work.courier.TransferManagementService.snapshot(viewer, transferView, false, "");
        for (var link : List.of(productionSnapshot.managed().getFirst(), transferSnapshot.managed().getFirst())) {
            helper.assertValueEqual(link.state(), SettlementSupplyPolicy.State.SUSPENDED, "native controls visibly suspend the policy");
            helper.assertValueEqual(link.execution(), SettlementSupplyPolicy.BindingState.SUSPENDED, "the affected step stays suspended");
            helper.assertValueEqual(link.blocker(), "policy_order_suspended", "the link explains the required explicit reactivation");
        }
        helper.assertTrue(production.orders().size() == 2 && transfers.orders().size() == 2, "evaluation does not recreate manually stopped orders");
        helper.assertTrue(production.order(manualProduction.id()).runnable() && transfers.order(manualDelivery.id()).runnable(),
                "manual orders remain independent of managed suspension");
        town.setPermissions(town.leader(), viewer.getUUID(), java.util.Set.of());
        helper.assertTrue(SettlementProductionService.snapshot(viewer, productionView, false, "").managed().isEmpty()
                && com.jvn.villagerretaliation.interaction.work.courier.TransferManagementService.snapshot(viewer, transferView, false, "").managed().isEmpty(),
                "revoking viewing permission hides all policy links immediately");
        helper.succeed();
    }

    private static SettlementSupplyPolicy stockPolicy(GameTestHelper helper, SettlementRecord town,
                                                      SettlementSupplyPolicy.Step step, int amount, int priority) {
        return stockPolicy(helper, town, step, new ItemStack(Items.BREAD), amount, priority);
    }

    private static SettlementSupplyPolicy stockPolicy(GameTestHelper helper, SettlementRecord town,
                                                      SettlementSupplyPolicy.Step step, ItemStack sample, int amount, int priority) {
        var id = UUID.randomUUID();
        var requirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("policy_stock", sample, amount);
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, step.destination(), sample, amount);
        return new SettlementSupplyPolicy(id, new SettlementSupplyPolicy.Configuration("Bread reserve", target, priority, List.of(step),
                List.of(new SettlementSupplyPolicy.Connection(step.id(), null,
                        com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(helper.getLevel().registryAccess(), id,
                                com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE, requirement)))),
                town.leader(), town::supplyChanged);
    }

    private static SettlementRecord policyTown(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(22_000_000 + origin.getX() * 8, 64, 22_000_000 + origin.getZ() * 8);
        var registry = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Policy test");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, center, center.offset(15, 3, 12));
        var stores = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center, center.offset(3, 2, 3), 5, true);
        town.putArea(town.leader(), stores);
        var depot = town.registerDepot(town.leader(), stores.id(), center, SettlementDepotPurpose.GENERAL);
        depot.observeRules(List.of());
        town.setWorksitePlan(town.leader(), stores.id(), SettlementWorksitePlan.defaults(stores.type().role()));
        for (var type : List.of(SettlementAreaType.FARM, SettlementAreaType.KITCHEN)) {
            var position = center.east(type == SettlementAreaType.FARM ? 5 : 10);
            var area = new SettlementArea(UUID.randomUUID(), type, type.name(), position, position.offset(3, 2, 3), 5, true);
            town.putArea(town.leader(), area);
            town.setWorksitePlan(town.leader(), area.id(), SettlementWorksitePlan.defaults(type.role()));
            if (type == SettlementAreaType.KITCHEN) town.recordSurvey(area.id(), new SettlementBuildingSurvey.Survey(0, List.of(), List.of(position), true,
                    java.util.Map.of(position, net.minecraft.resources.ResourceLocation.withDefaultNamespace("crafting_table"))));
        }
        return town;
    }

    private static void finish(Runnable work) {
        int count = 0;
        do { work.run(); } while (++count < 4096 && !SettlementWork.complete(work));
        if (!SettlementWork.complete(work)) throw new AssertionError("Policy work did not finish within its bounded job count");
    }

    private static void expectInvalid(GameTestHelper helper, Runnable read) {
        try {
            read.run();
            helper.fail("invalid policy data must enter recovery");
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(!exception.getMessage().isBlank(), "recovery retains a concrete reason");
        }
    }

    private static SettlementSupplyPolicy.Step gather() {
        return new SettlementSupplyPolicy.Step(new UUID(0, 1), SettlementSupplyPolicy.StepKind.GATHER, new UUID(0, 3), null,
                new ItemStack(Items.WHEAT), null, BlockPos.ZERO);
    }

    private static SettlementSupplyPolicy.Step bake() {
        return new SettlementSupplyPolicy.Step(new UUID(0, 2), SettlementSupplyPolicy.StepKind.PRODUCTION, new UUID(0, 4),
                HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), Map.of()), ItemStack.EMPTY, null, BlockPos.ZERO.east());
    }

    private static SettlementSupplyPolicy.Configuration configuration(List<SettlementSupplyPolicy.Step> steps,
                                                                     List<SettlementSupplyPolicy.Connection> connections) {
        return new SettlementSupplyPolicy.Configuration("Bread reserve", new SettlementSupplyPolicy.Target(
                SettlementSupplyPolicy.TargetKind.STOCK, null, null, BlockPos.ZERO.east(), new ItemStack(Items.BREAD), 12), 5, steps, connections);
    }
}
