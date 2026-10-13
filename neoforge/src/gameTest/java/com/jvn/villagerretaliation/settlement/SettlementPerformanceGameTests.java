package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyAllocation;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("worker_performance")
@PrefixGameTestTemplate(false)
public final class SettlementPerformanceGameTests {
    @GameTest(template = "empty", batch = "supply_snapshots", timeoutTicks = 200)
    public static void allocationSnapshotNeverCallsIngredientsOnWorker(GameTestHelper helper) {
        var serverThread = Thread.currentThread();
        var captured = new AtomicBoolean();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var source = BlockPos.ZERO;
        var requests = new ArrayList<WorkSupplyAllocation.Request>();
        for (int index = 0; index < 32; index++) {
            var requirement = new WorkSupplyRequirement("iron", List.of(new ItemStack(Items.IRON_INGOT)), 4,
                    WorkSupplyRequirement.Unit.ITEMS, stack -> {
                        if (Thread.currentThread() != serverThread || captured.get()) throw new IllegalStateException("Live ingredient callback after capture");
                        calls.incrementAndGet();
                        return stack.is(Items.IRON_INGOT);
                    }, null);
            var demand = new WorkSupplyDemand(new UUID(0, index + 1), new UUID(1, index + 1), WorkSupplyDemand.Kind.PRODUCTION,
                    requirement, 4, 0, index == 0 ? 4 : 0, index % 10, 0, 0, 0, 0, true,
                    List.of(source), null, WorkSupplyDemand.Blocker.of(""));
            requests.add(new WorkSupplyAllocation.Request(demand, index == 1 ? 4 : 0));
        }
        var stocks = new ArrayList<>(List.of(new WorkSupplyAllocation.Stock(source, new ItemStack(Items.IRON_INGOT), 35, 0, 5, true)));
        var expected = WorkSupplyAllocation.allocate(requests, stocks);
        calls.set(0);
        var preparation = WorkSupplyAllocation.begin(requests, stocks);
        helper.assertFalse(preparation.advance(7), "capture yields when its matching budget is exhausted");
        helper.assertValueEqual(calls.get(), 7, "capture evaluates only the requested number of pairs");
        helper.assertTrue(preparation.advance(25), "the remaining matches finish capture");
        var snapshot = preparation.finish();
        captured.set(true);
        requests.clear();
        stocks.clear();
        var future = SettlementSupplyTasks.submit(snapshot);
        helper.assertTrue(future != null, "the snapshot is admitted to the bounded worker queue");
        helper.succeedWhen(() -> {
            helper.assertTrue(future.isDone(), "background allocation has completed");
            WorkSupplyAllocation.Result actual;
            try { actual = snapshot.finish(future.get()); }
            catch (InterruptedException | java.util.concurrent.ExecutionException exception) { throw new IllegalStateException(exception); }
            helper.assertValueEqual(actual.grants().stream().map(WorkSupplyAllocation.Grant::consumer).toList(),
                    expected.grants().stream().map(WorkSupplyAllocation.Grant::consumer).toList(), "worker allocation preserves priority and started work");
            helper.assertValueEqual(actual.grants().stream().map(WorkSupplyAllocation.Grant::count).toList(),
                    expected.grants().stream().map(WorkSupplyAllocation.Grant::count).toList(), "worker allocation preserves exact quantities");
            helper.assertValueEqual(actual.demands().stream().map(WorkSupplyDemand::blocker).toList(),
                    expected.demands().stream().map(WorkSupplyDemand::blocker).toList(), "competition diagnostics survive snapshot allocation");
        });
    }

    @GameTest(template = "empty", batch = "stock_catalog")
    public static void catalogTracksPriorityPurposeAndAreaChanges(GameTestHelper helper) {
        var town = town(helper);
        var center = town.center();
        SettlementTestPlots.claim(helper.getLevel(), town, center, center.offset(8, 3, 8));
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center, center.offset(3, 3, 3), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "depot area is registered");
        var first = town.registerDepot(town.leader(), area.id(), center, SettlementDepotPurpose.GENERAL);
        var second = town.registerDepot(town.leader(), area.id(), center.east(), SettlementDepotPurpose.GENERAL);
        helper.assertTrue(first != null && second != null, "both depots are registered");
        first.configure(SettlementDepotPurpose.GENERAL, 9);
        var initial = SettlementStock.depots(town);
        helper.assertTrue(initial == SettlementStock.depots(town), "unchanged configuration reuses the ordered catalog");
        helper.assertValueEqual(initial.getFirst(), first, "highest priority comes first");
        second.configure(SettlementDepotPurpose.OUTPUT, 9);
        first.configure(SettlementDepotPurpose.MATERIALS, 0);
        helper.assertValueEqual(SettlementStock.depots(town).getFirst(), second, "priority edits reorder the catalog");
        helper.assertValueEqual(SettlementStock.supplies(town), List.of(first), "purpose edits update supplies");
        helper.assertValueEqual(SettlementStock.outputs(town), List.of(second), "purpose edits update outputs");
        helper.assertTrue(town.putArea(town.leader(), new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(), 5, false)), "area disabled");
        helper.assertTrue(SettlementStock.depots(town).isEmpty(), "disabled areas invalidate the catalog");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "shared_family_routes")
    public static void sharedRouteRechecksTerrainAndAccess(GameTestHelper helper) {
        var town = town(helper);
        var level = helper.getLevel();
        var start = town.center().above();
        var target = start.east(5);
        for (var pos : BlockPos.betweenClosed(start.offset(-1, -1, -2), target.offset(1, 2, 2))) {
            level.setBlockAndUpdate(pos, pos.getY() == start.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        var first = new SettlementFamilyRoutes(level, town).route(start, target);
        helper.assertTrue(!first.isEmpty(), "a route is found across observed ground");
        var second = new SettlementFamilyRoutes(level, town).route(start, target);
        helper.assertTrue(first == second, "separate residents reuse the validated route");
        var blocked = first.get(1);
        var restricted = new SettlementFamilyRoutes(level, town).route(start, target, pos -> !pos.equals(blocked));
        helper.assertTrue(!restricted.contains(blocked), "access restrictions are applied before reusing a route");
        level.setBlockAndUpdate(start.east(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(start.east().above(), Blocks.STONE.defaultBlockState());
        var changed = new SettlementFamilyRoutes(level, town).route(start, target);
        helper.assertTrue(!changed.contains(start.east()), "a terrain edit invalidates obstructed steps");
        helper.succeed();
    }

    private static SettlementRecord town(GameTestHelper helper) {
        return new SettlementRecord(VillageAllegianceId.random(), helper.getLevel().dimension().location(),
                helper.absolutePos(new BlockPos(2, 2, 2)), UUID.randomUUID(), helper.getLevel().getGameTime(), () -> {});
    }
}
