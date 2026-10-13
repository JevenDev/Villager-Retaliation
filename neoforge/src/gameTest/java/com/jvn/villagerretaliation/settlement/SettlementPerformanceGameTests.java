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

    @GameTest(template = "empty", batch = "territory_lookup")
    public static void territoryLookupPreservesNegativeBoundsAndSaveOrder(GameTestHelper helper) {
        var center = new BlockPos(-1, 64, -1);
        var territory = new SettlementTerritory(center);
        territory.start();
        helper.assertTrue(territory.contains(new BlockPos(-32, 64, -32)), "negative boundary belongs to chunk minus two");
        helper.assertFalse(territory.contains(new BlockPos(-33, 64, -32)), "the block beyond the negative boundary is outside");
        helper.assertTrue(territory.contains(new BlockPos(-32, 0, -32), new BlockPos(15, 100, 15)), "all original chunks cover the full bounds");
        helper.assertFalse(territory.contains(new BlockPos(-32, 0, -32), new BlockPos(16, 100, 15)), "bounds outside the last chunk are rejected");
        var saved = territory.save();
        var restored = SettlementTerritory.load(center, saved);
        helper.assertValueEqual(List.copyOf(restored.plots()), List.copyOf(territory.plots()), "save round trips retain plot iteration order");
        helper.assertValueEqual(restored.save(), saved, "the serialized territory format stays unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "construction_supply_range")
    public static void constructionOnlyReservesMaterialsWithinBuilderReach(GameTestHelper helper) {
        var town = town(helper);
        while (town.stage() != SettlementStage.CITY) town.advance();
        var level = helper.getLevel();
        var center = town.center();
        var entry = com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog.byId(level.getServer(),
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")).orElseThrow();
        var plan = com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.scan(level, entry,
                net.minecraft.world.level.block.Rotation.NONE).orElseThrow();
        var origin = center.east(8);
        var far = center.east(96);
        SettlementTestPlots.claim(level, town, center.offset(-8, -2, -8), far.offset(8, 16, 16));
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "House", plan.worldMin(origin), plan.worldMax(origin), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "construction area registers");
        for (var position : List.of(center, far)) {
            var store = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Materials", position, position.offset(2, 2, 2), 5, true);
            helper.assertTrue(town.putArea(town.leader(), store), "material storage registers");
            var depot = town.registerDepot(town.leader(), store.id(), position, SettlementDepotPurpose.MATERIALS);
            depot.observeRules(List.of());
            for (var material : plan.materials()) depot.addItem(material.item().copyWithCount(material.count()));
        }
        var builder = UUID.randomUUID();
        helper.assertTrue(town.assignDuty(builder, area.id()), "builder receives the site");
        var project = SettlementProject.create(town, area.id(), builder, plan, origin, UUID.randomUUID(), 0);
        helper.assertTrue(town.startProject(town.leader(), project), "construction starts");
        var radius = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.HIRED_BUILDER_MATERIAL_STORAGE_RADIUS;
        int previous = radius.get();
        try {
            radius.set(32);
            var demands = SettlementDemandService.inspect(level, town).demands().stream()
                    .filter(demand -> demand.consumer().equals(project.id())).toList();
            helper.assertFalse(demands.isEmpty(), "the unfinished house requests actual construction materials");
            for (var demand : demands) helper.assertValueEqual(demand.sources(), List.of(center),
                    "a project must not reserve supplies in a depot its builder cannot use");
        } finally {
            radius.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inheritedSettingsReuseImmutableInputsAndObserveEdits(GameTestHelper helper) {
        var oak = net.minecraft.resources.ResourceLocation.withDefaultNamespace("oak_log");
        var birch = net.minecraft.resources.ResourceLocation.withDefaultNamespace("birch_log");
        var worksite = SettlementWorkerSettings.capture(com.jvn.villagerretaliation.interaction.HiredVillagerRole.LOGGING,
                new net.minecraft.nbt.CompoundTag()).withFilters(java.util.Set.of(oak), java.util.Set.of())
                .with(SettlementWorkerSetting.ENABLED, 1);
        var saved = worksite.save();
        var resident = worksite.withOverrides(0);
        helper.assertTrue(resident == worksite.withOverrides(0), "repeated inheritance reuses immutable settings");
        var initial = resident.resolve(worksite);
        helper.assertTrue(initial == resident.resolve(worksite), "unchanged worksite settings reuse their resolved value");
        var changed = worksite.withFilters(java.util.Set.of(birch), java.util.Set.of()).with(SettlementWorkerSetting.ENABLED, 0);
        helper.assertFalse(resident.resolve(changed).enabled(), "a changed worksite immediately disables inherited work");
        helper.assertValueEqual(resident.resolve(changed).logs(), java.util.Set.of(birch), "changed filters immediately reach the worker");
        helper.assertTrue(resident.with(SettlementWorkerSetting.ENABLED, 1).resolve(changed).enabled(), "personal overrides retain priority");
        helper.assertValueEqual(worksite.save(), saved, "memoization never changes serialized configuration");
        helper.assertValueEqual(resident.resolve(worksite).logs(), java.util.Set.of(oak), "returning to a previous worksite restores its settings");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "field_chunk_neighbors")
    public static void fieldObservationCoversNegativeChunkCornersAndWaterEdits(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = new BlockPos(-513, -58, -513);
        for (var sample : BlockPos.betweenClosed(position.offset(-4, 0, -4), position.offset(4, 2, 4))) {
            level.setBlock(sample, sample.getY() == position.getY() ? Blocks.FARMLAND.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        var first = position.offset(-4, 0, -4);
        var second = position.offset(4, 1, 4);
        level.setBlock(first, Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(second, Blocks.WATER.defaultBlockState(), 2);
        var chunk = level.getChunkAt(position);
        var plot = SettlementFieldPlot.capture(level, UUID.randomUUID(), position, chunk, level.getGameTime());
        var saved = plot.save();
        helper.assertTrue(saved.getBoolean("EnvironmentKnown"), "the full neighborhood spans four available chunks");
        helper.assertValueEqual(java.util.Arrays.stream(saved.getLongArray("WaterSources")).boxed().collect(java.util.stream.Collectors.toSet()),
                java.util.Set.of(first.asLong(), second.asLong()), "both water levels and opposite corners are observed");
        level.setBlock(second, Blocks.AIR.defaultBlockState(), 2);
        plot.observeEnvironment(level, chunk);
        helper.assertValueEqual(java.util.Arrays.stream(plot.save().getLongArray("WaterSources")).boxed().toList(),
                List.of(first.asLong()), "water removal in a neighboring chunk is observed immediately");
        helper.assertValueEqual(plot.save().getList("Environment", net.minecraft.nbt.Tag.TAG_COMPOUND).size(), 9,
                "the crop and soil neighborhood retains all nine cells");
        helper.succeed();
    }

    private static SettlementRecord town(GameTestHelper helper) {
        return new SettlementRecord(VillageAllegianceId.random(), helper.getLevel().dimension().location(),
                helper.absolutePos(new BlockPos(2, 2, 2)), UUID.randomUUID(), helper.getLevel().getGameTime(), () -> {});
    }
}
