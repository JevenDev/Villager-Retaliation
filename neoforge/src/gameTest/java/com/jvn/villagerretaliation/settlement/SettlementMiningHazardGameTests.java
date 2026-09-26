package com.jvn.villagerretaliation.settlement;

import static com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.assertCleared;
import static com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.context;
import static com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.count;
import static com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.fixture;
import static com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.observe;
import static com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.put;
import static com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.run;
import static com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.set;
import static com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.tool;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHazardManager;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.settlement.SettlementVerticalExcavationGameTests.Fixture;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementMiningHazardGameTests {
    private SettlementMiningHazardGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void boundedWaterPocketsConsumeRealFillAndThenExcavateIt(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var water = fixture.area().min().offset(2, 1, 1);
            set(fixture.chunk(), water, Blocks.WATER.defaultBlockState());
            set(fixture.chunk(), water.east(), Blocks.WATER.defaultBlockState());
            observe(helper, fixture);
            put(helper, fixture, 37, new ItemStack(Items.COBBLESTONE, 2));
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            run(helper, fixture, 200, 12_000);
            assertCleared(helper, fixture);
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 19, "temporary fill is recovered with the seventeen original stone blocks");
            helper.assertValueEqual(count(helper, fixture, Items.DIRT), 18, "draining preserves unrelated native drops");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_LOG), 17, "the former water cell cannot invent its missing log");
            helper.assertValueEqual(tool(helper, fixture, Items.DIAMOND_PICKAXE).getDamageValue(), 19, "recovering temporary fill wears the actual tool");
            helper.assertValueEqual(fixture.worker().settings().getLongArray("MiningPermanentHazardBarriers").length, 0, "a bounded drained pocket needs no permanent seal");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "containment does not load the mine");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void boundedLavaPocketsAreFilledBeforeTheirCoverIsMined(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var lava = fixture.area().min().offset(3, 1, 1);
            set(fixture.chunk(), lava, Blocks.LAVA.defaultBlockState());
            observe(helper, fixture);
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(fixture.worker().status(), "missing_safe_fill", "lava containment waits for an actual material");
            helper.assertFalse(fixture.mine().getBlockState(lava.above()).isAir(), "the intact cover protects the uncontained lava");
            helper.assertValueEqual(tool(helper, fixture, Items.DIAMOND_PICKAXE).getDamageValue(), 0, "waiting for containment does not wear the pickaxe");
            put(helper, fixture, 37, new ItemStack(Items.COBBLESTONE));
            run(helper, fixture, 1_200, 12_000);
            assertCleared(helper, fixture);
            helper.assertValueEqual(materials(helper, fixture), 54, "one supplied fill and fifty three native blocks are recovered exactly once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void externallyFedWaterKeepsItsPhysicalFaceSeal(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var face = fixture.area().min().offset(0, 1, 1);
            for (int offset = 0; offset <= 2; offset++) set(fixture.chunk(), face.west(offset), Blocks.WATER.defaultBlockState());
            observe(helper, fixture);
            put(helper, fixture, 37, new ItemStack(Items.COBBLESTONE, 8));
            run(helper, fixture, 200, 12_000);
            helper.assertTrue(fixture.mine().getBlockState(face).is(Blocks.COBBLESTONE), "the excavated face retains its real seal");
            helper.assertTrue(permanent(fixture, face), "the face seal remains protected from normal excavation");
            helper.assertTrue(fixture.mine().getBlockState(face.west(2)).is(Blocks.WATER), "water outside the containment scope is preserved");
            int remaining = materials(helper, fixture);
            run(helper, fixture, 12_200, 14_000);
            helper.assertValueEqual(materials(helper, fixture), remaining, "a finished seal cannot consume or generate additional material");
            var saved = SettlementMine.load(fixture.mine().save());
            helper.assertTrue(saved.getBlockState(face).is(Blocks.COBBLESTONE), "permanent fill survives saving");
            SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            helper.assertTrue(fixture.chunk().getBlockState(face).is(Blocks.COBBLESTONE), "chunk handoff applies the actual retained seal");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void largeWaterPocketsUseBoundedPermanentFacePlans(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            for (BlockPos position : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max().below())) {
                set(fixture.chunk(), position, Blocks.WATER.defaultBlockState());
            }
            observe(helper, fixture);
            var plan = MiningHazardManager.assess(fixture.mine(), fixture.mine()::observed, context(fixture), fixture.worker().position().getBottomCenter());
            helper.assertTrue(plan != null && plan.permanent() && plan.kind() == MiningHazardManager.HazardKind.WATER, "a pocket over ninety six cells is sealed instead of fully drained");
            helper.assertTrue(plan.positions().size() <= 8, "one assessment contains at most eight exposed faces");
            put(helper, fixture, 37, new ItemStack(Items.COBBLESTONE, 32));
            run(helper, fixture, 200, 8_000);
            helper.assertTrue(fixture.mine().getBlockState(fixture.area().min()).is(Blocks.WATER), "large pockets keep their unexcavated interior");
            helper.assertTrue(Arrays.stream(fixture.worker().settings().getLongArray("MiningPermanentHazardBarriers"))
                    .mapToObj(BlockPos::of).anyMatch(position -> fixture.mine().getBlockState(position).is(Blocks.COBBLESTONE)), "real fill remains in the permanent face barrier");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void finalExcavationFloorsReceivePermanentFallGuards(GameTestHelper helper) {
        fixture(helper, 1, fixture -> {
            var floor = fixture.area().min().offset(3, -1, 1);
            set(fixture.chunk(), floor, Blocks.AIR.defaultBlockState());
            observe(helper, fixture);
            put(helper, fixture, fixture.worker().outputSlots().getFirst(), new ItemStack(Items.COBBLESTONE));
            run(helper, fixture, 200, 8_000);
            assertCleared(helper, fixture);
            helper.assertTrue(fixture.mine().getBlockState(floor).is(Blocks.COBBLESTONE), "the bottom hole receives one actual fall guard");
            helper.assertTrue(permanent(fixture, floor), "the guard below the work bounds is permanent");
            helper.assertValueEqual(materials(helper, fixture), 18, "the supplied block stays in the floor and only native drops remain carried");
            SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            helper.assertTrue(fixture.chunk().getBlockState(floor).is(Blocks.COBBLESTONE), "native terrain receives the protective floor");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void temporaryFallGuardsInsideTheMineAreRecoveredDuringDescent(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var hole = fixture.area().min().offset(3, 1, 1);
            set(fixture.chunk(), hole, Blocks.AIR.defaultBlockState());
            observe(helper, fixture);
            put(helper, fixture, 37, new ItemStack(Items.COBBLESTONE));
            run(helper, fixture, 200, 12_000);
            assertCleared(helper, fixture);
            helper.assertFalse(permanent(fixture, hole), "a temporary internal landing does not block deeper excavation");
            helper.assertValueEqual(materials(helper, fixture), 54, "temporary fill is recovered along with the original fifty three blocks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void hazardFillRespectsDepotAccessAndFetchesOnlyRemainingPlanMaterials(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var water = fixture.area().min().offset(2, 1, 1);
            set(fixture.chunk(), water, Blocks.WATER.defaultBlockState());
            set(fixture.chunk(), water.east(), Blocks.WATER.defaultBlockState());
            observe(helper, fixture);
            fixture.supply().setItem(0, new ItemStack(Items.COBBLESTONE, 8));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(fixture.worker().status(), "missing_safe_fill", "disabled depot access cannot supply a hazard plan");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 8, "unauthorized stock is untouched");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            run(helper, fixture, 1_200, 12_000);
            assertCleared(helper, fixture);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 6, "the two cell plan fetches only two stored blocks");
            helper.assertValueEqual(materials(helper, fixture), 54, "the finite supplied blocks become recovered terrain");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void unsafeFillItemsCannotContainMineHazards(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var water = fixture.area().min().offset(2, 1, 1);
            set(fixture.chunk(), water, Blocks.WATER.defaultBlockState());
            observe(helper, fixture);
            int now = 200;
            for (var item : List.of(Items.SAND, Items.DIAMOND_ORE, Items.CHEST, Items.BEDROCK, Items.MAGMA_BLOCK, Items.OAK_SLAB, Items.OAK_FENCE)) {
                put(helper, fixture, 37, new ItemStack(item, 64));
                run(helper, fixture, now, now);
                now += 200;
                helper.assertValueEqual(fixture.worker().status(), "missing_safe_fill", "unsafe fill remains unusable: " + item);
                helper.assertValueEqual(count(helper, fixture, item), 64, "rejected fill is not consumed");
                helper.assertTrue(fixture.mine().getBlockState(water).is(Blocks.WATER), "rejected fill cannot erase the pocket");
            }
            helper.assertValueEqual(tool(helper, fixture, Items.DIAMOND_PICKAXE).getDamageValue(), 0, "failed containment cannot wear mining tools");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void partialHazardPlansResumeAfterSavingWithoutDuplicateConsumption(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            for (BlockPos position : BlockPos.betweenClosed(fixture.area().min().above(), fixture.area().max().below())) {
                set(fixture.chunk(), position, Blocks.WATER.defaultBlockState());
            }
            observe(helper, fixture);
            put(helper, fixture, 37, new ItemStack(Items.COBBLESTONE, 18));
            run(helper, fixture, 200, 200);
            int index = fixture.worker().settings().getInt("MiningHazardPlanIndex");
            helper.assertTrue(index > 0 && index < 18, "the first update partially fills a saved multi cell plan");
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 18 - index, "every completed plan step consumes one real block");
            var mine = SettlementMine.load(fixture.mine().save());
            var worker = SettlementWorkerState.load(fixture.worker().save());
            fixture.town().recordMine(mine);
            fixture.town().recordWorker(worker);
            var resumed = new Fixture(fixture.town(), fixture.area(), fixture.chunk(), mine, fixture.supply(), fixture.output(), worker, fixture.villager());
            run(helper, resumed, 400, 16_000);
            assertCleared(helper, resumed);
            helper.assertValueEqual(materials(helper, resumed), 54, "resumption spends each fill once and recovers only the finite terrain");
            helper.assertFalse(worker.settings().contains("MiningHazardPlanKind"), "finished plans are cleared");
            helper.assertTrue(worker.restore(fixture.villager()), "the newer worker returns its inventory and native mining state");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager()).countItem(Items.COBBLESTONE), 30, "the native worker receives twelve native stone drops and eighteen recovered fill blocks");
            helper.assertFalse(HiredWorkStateStore.state(fixture.villager()).contains("MiningHazardPlanKind"), "the native worker cannot repeat a completed plan");
            helper.assertFalse(worker.restore(fixture.villager()), "a repeated worker handoff does not duplicate materials");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void hazardJournalsCannotOverwriteContainersOrEscapeTheirNativeScope(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var face = fixture.area().min().west();
            var outside = face.west();
            set(fixture.chunk(), face, Blocks.WATER.defaultBlockState());
            set(fixture.chunk(), outside, Blocks.WATER.defaultBlockState());
            set(fixture.chunk(), face.above(), Blocks.CHEST.defaultBlockState());
            observe(helper, fixture);
            var fill = Blocks.COBBLESTONE.defaultBlockState();
            helper.assertFalse(fixture.mine().changeHazard(outside, fill), "observed terrain outside the hazard scope cannot be filled");
            helper.assertFalse(fixture.mine().changeHazard(face.above(), fill), "hazard work cannot overwrite a block entity");
            helper.assertFalse(fixture.mine().changeHazard(fixture.area().min(), fill), "hazard work cannot overwrite existing rock");
            for (var block : List.of(Blocks.BEDROCK, Blocks.DIAMOND_ORE, Blocks.SAND, Blocks.MAGMA_BLOCK, Blocks.OAK_SLAB, Blocks.CHEST)) {
                helper.assertFalse(fixture.mine().changeHazard(face, block.defaultBlockState()), "unsafe fill is rejected by the terrain journal");
            }
            helper.assertFalse(fixture.mine().changeHazard(face, Blocks.MANGROVE_ROOTS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true)), "fill cannot introduce a new waterlogged hazard");
            helper.assertTrue(fixture.mine().changeHazard(face, fill), "the immediate outside face can receive an actual safe seal");
            helper.assertTrue(SettlementMine.load(fixture.mine().save()).getBlockState(face).is(Blocks.COBBLESTONE), "bounded hazard terrain survives saving");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void hazardPickupPreservesCombinedFramedStockReserves(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var water = fixture.area().min().offset(2, 1, 1);
            set(fixture.chunk(), water, Blocks.WATER.defaultBlockState());
            set(fixture.chunk(), water.east(), Blocks.WATER.defaultBlockState());
            observe(helper, fixture);
            var rule = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.ITEM_FILTER.get());
            com.jvn.villagerretaliation.item.VillagerItemFilterData.setEntry(rule, 0, new ItemStack(Items.COBBLESTONE));
            com.jvn.villagerretaliation.item.VillagerFilterPolicy.setDirection(rule,
                    com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferDirection.PROVIDE);
            com.jvn.villagerretaliation.item.VillagerFilterPolicy.setStockTarget(rule, 2);
            fixture.supply().observeRules(List.of(rule));
            fixture.supply().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 2, "the shared stock reserve remains after one permitted pickup");
            helper.assertValueEqual(fixture.worker().settings().getInt("MiningHazardPlanIndex"), 1, "only the funded plan step is completed");
            helper.assertValueEqual(fixture.worker().status(), "missing_safe_fill", "the next step waits instead of taking reserved stock");
            fixture.supply().observeRules(List.of());
            run(helper, fixture, 2_200, 12_000);
            assertCleared(helper, fixture);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 1, "relaxing the reserve supplies exactly the remaining plan step");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void backgroundContainmentResumesAnExistingNativePlanAndSkipsFilledSteps(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var filled = fixture.area().min().offset(2, 1, 1);
            var water = filled.east();
            set(fixture.chunk(), filled, Blocks.COBBLESTONE.defaultBlockState());
            set(fixture.chunk(), water, Blocks.WATER.defaultBlockState());
            observe(helper, fixture);
            put(helper, fixture, 37, new ItemStack(Items.COBBLESTONE));
            var state = fixture.worker().settings();
            state.putString("MiningHazardPlanKind", "water");
            state.putLongArray("MiningHazardPlanPositions", new long[] {filled.asLong(), water.asLong()});
            state.putInt("MiningHazardPlanIndex", 0);
            state.putBoolean("MiningHazardPlanPermanent", false);
            run(helper, fixture, 200, 12_000);
            assertCleared(helper, fixture);
            helper.assertValueEqual(materials(helper, fixture), 54, "the already filled native step consumes no replacement block");
            helper.assertFalse(state.contains("MiningHazardPlanKind"), "the shared native plan finishes once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void largeLavaPocketsUseTheirSmallerNativeContainmentLimit(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            int remaining = 33;
            for (BlockPos position : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max().below())) {
                if (remaining-- > 0) set(fixture.chunk(), position, Blocks.LAVA.defaultBlockState());
            }
            observe(helper, fixture);
            var plan = MiningHazardManager.assess(fixture.mine(), fixture.mine()::observed, context(fixture), fixture.worker().position().getBottomCenter());
            helper.assertTrue(plan != null && plan.kind() == MiningHazardManager.HazardKind.LAVA && plan.permanent(),
                    "thirty three connected lava cells exceed the native full drain limit");
            helper.assertTrue(plan.positions().size() <= 8, "large lava pockets retain bounded face plans");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void unknownFluidBoundariesCannotBeAssumedToBeClosed(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var water = fixture.area().min().offset(2, 1, 1);
            set(fixture.chunk(), water, Blocks.WATER.defaultBlockState());
            observe(helper, fixture);
            var unknown = water.east();
            var plan = MiningHazardManager.assess(fixture.mine(), position -> fixture.mine().observed(position) && !position.equals(unknown),
                    context(fixture), fixture.worker().position().getBottomCenter());
            helper.assertTrue(plan != null && plan.permanent(), "an unobserved pocket boundary requires a retained face seal");
            helper.assertFalse(plan.positions().contains(unknown), "the plan never fills an unobserved cell");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void hazardPlansOutsideTheCurrentMineAreDiscardedBeforeWork(GameTestHelper helper) {
        fixture(helper, 1, fixture -> {
            var outside = fixture.area().min().west(2);
            set(fixture.chunk(), outside, Blocks.WATER.defaultBlockState());
            observe(helper, fixture);
            var state = fixture.worker().settings();
            state.putString("MiningHazardPlanKind", "water");
            state.putLongArray("MiningHazardPlanPositions", new long[] {outside.asLong()});
            state.putBoolean("MiningHazardPlanPermanent", true);
            run(helper, fixture, 200, 8_000);
            assertCleared(helper, fixture);
            helper.assertTrue(fixture.mine().getBlockState(outside).is(Blocks.WATER), "stale plans cannot act beyond their current mine scope");
            helper.assertFalse(state.contains("MiningHazardPlanPositions"), "invalid persisted plans are removed");
            helper.assertValueEqual(materials(helper, fixture), 18, "discarding a stale plan consumes no fill");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_hazards")
    public static void supportChecksCannotRestartABoundedHazardRouteSearch(GameTestHelper helper) {
        fixture(helper, 1, fixture -> {
            var origin = fixture.worker().position();
            var position = fixture.area().max().above();
            SettlementMiningRoutes.Target reached = null;
            for (int step = 0; step < 64 && reached == null; step++) {
                reached = SettlementMiningRoutes.forWorker(fixture.worker(), fixture.mine(), origin, true)
                        .target(fixture.villager(), position, new SettlementMiningRoutes.Budget(1));
                SettlementMiningRoutes.forSupport(fixture.worker(), fixture.mine(), origin)
                        .target(fixture.villager(), fixture.area().min().above(), new SettlementMiningRoutes.Budget(1));
            }
            helper.assertTrue(reached != null && reached.steps() > 0, "interleaved support checks preserve progress toward a distant hazard placement");
        });
    }

    private static int materials(GameTestHelper helper, Fixture fixture) {
        return count(helper, fixture, Items.COBBLESTONE) + count(helper, fixture, Items.DIRT) + count(helper, fixture, Items.OAK_LOG);
    }

    private static boolean permanent(Fixture fixture, BlockPos position) {
        return Arrays.stream(fixture.worker().settings().getLongArray("MiningPermanentHazardBarriers")).anyMatch(value -> value == position.asLong());
    }
}
