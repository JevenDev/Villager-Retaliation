package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalOptions;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalStairPlan;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementHorizontalExcavationGameTests {
    private SettlementHorizontalExcavationGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void horizontalExcavationUsesRealToolsForEveryMaterial(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            run(helper, fixture, 200, 10_000);
            assertCleared(helper, fixture);
            for (var item : List.of(Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG)) {
                helper.assertValueEqual(count(helper, fixture, item), 18, "each material produces its finite native drops");
            }
            for (var item : List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_AXE)) {
                helper.assertValueEqual(tool(helper, fixture, item).getDamageValue(), 18, "each suitable tool wears for its own material");
            }
            double earned = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(earned > 0.0D, "excavation awards real mining practice");
            run(helper, fixture, 10_200, 12_000);
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), earned, "completed terrain cannot award further practice");
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 18, "completed terrain cannot create additional stock");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "excavation never loads its detached chunks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void tallExcavationsKeepTheirStairsUntilUpperWorkFinishes(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            var context = context(fixture);
            boolean sawReservedSupport = false;
            for (int now = 200; now <= 20_000; now += 200) {
                SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
                if (!MiningHorizontalStairPlan.cleanup(context)) {
                    for (BlockPos position : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max())) {
                        if (MiningHorizontalStairPlan.isReservedSupport(context, fixture.worker().position(), position)) {
                            helper.assertFalse(fixture.mine().getBlockState(position).isAir(), "upper work preserves its reserved staircase");
                            sawReservedSupport = true;
                        }
                    }
                }
            }
            helper.assertTrue(sawReservedSupport, "tall work reserves a real staircase before cleanup");
            helper.assertTrue(MiningHorizontalStairPlan.cleanup(context), "stair cleanup starts after the upper excavation");
            assertCleared(helper, fixture);
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE)
                    + count(helper, fixture, Items.DIRT) + count(helper, fixture, Items.OAK_LOG), 126,
                    "cleanup yields each actual staircase block exactly once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void floorPatchingConsumesFiniteOutputBeforeClearingTheRockAbove(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var floor = fixture.area().min().below();
            set(fixture.chunk(), floor, Blocks.AIR.defaultBlockState());
            observe(helper, fixture);
            put(helper, fixture, fixture.worker().outputSlots().getFirst(), new ItemStack(Items.COBBLESTONE));
            run(helper, fixture, 200, 10_000);
            assertCleared(helper, fixture);
            helper.assertTrue(fixture.mine().getBlockState(floor).is(Blocks.COBBLESTONE), "floor repair works below initially solid excavation terrain");
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 18, "one carried cobblestone is spent and only real mined drops remain");
            helper.assertTrue(java.util.Arrays.stream(fixture.worker().settings().getLongArray("MiningPermanentHazardBarriers"))
                    .anyMatch(position -> position == floor.asLong()), "the repaired floor remains a protected native barrier");
            helper.assertTrue(fixture.chunk().getBlockState(floor).isAir(), "the unloaded physical floor waits for handoff");
            SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            helper.assertTrue(fixture.chunk().getBlockState(floor).is(Blocks.COBBLESTONE), "the returning chunk receives the actual floor repair");
            helper.assertTrue(fixture.chunk().getBlockState(fixture.area().min()).isAir(), "the returning chunk receives excavation too");
            var restored = SettlementWorkerState.load(fixture.worker().save());
            var nativePosition = fixture.villager().position();
            helper.assertTrue(restored.restore(fixture.villager()), "a newer worker revision restores its inventory and mining state");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager()).countItem(Items.COBBLESTONE), 18,
                    "native inventory receives the remaining real materials");
            helper.assertValueEqual(fixture.villager().position(), nativePosition, "background travel never teleports the native worker");
            helper.assertFalse(restored.restore(fixture.villager()), "repeating the handoff cannot restore the same inventory twice");
            helper.assertTrue(java.util.Arrays.stream(HiredWorkStateStore.state(fixture.villager()).getLongArray("MiningPermanentHazardBarriers"))
                    .anyMatch(position -> position == floor.asLong()), "native work retains the repaired floor barrier");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void missingFloorSuppliesRespectDedicatedDepotAccess(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var floor = fixture.area().min().below();
            set(fixture.chunk(), floor, Blocks.AIR.defaultBlockState());
            observe(helper, fixture);
            fixture.supply().setItem(0, new ItemStack(Items.COBBLESTONE));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(fixture.worker().status(), "missing_safe_fill", "disabled supply access pauses required floor repairs");
            helper.assertTrue(fixture.mine().getBlockState(fixture.area().min()).is(Blocks.STONE), "missing floor materials cannot be bypassed by mining");
            helper.assertTrue(fixture.mine().getBlockState(floor).isAir(), "missing materials cannot invent a floor");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 1, "disabled supply access preserves depot stock");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            run(helper, fixture, 1_200, 10_000);
            assertCleared(helper, fixture);
            helper.assertTrue(fixture.supply().getItem(0).isEmpty(), "repair consumes the actual dedicated depot supply");
            helper.assertTrue(fixture.mine().getBlockState(floor).is(Blocks.COBBLESTONE), "the supplied material repairs the actual hole");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void unsafeFillMaterialsCannotBecomeExcavationFloors(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var floor = fixture.area().min().below();
            set(fixture.chunk(), floor, Blocks.LAVA.defaultBlockState());
            observe(helper, fixture);
            var slots = fixture.worker().outputSlots();
            List<Item> unsafe = List.of(Items.SAND, Items.IRON_ORE, Items.CHEST, Items.BEDROCK, Items.MAGMA_BLOCK);
            for (int index = 0; index < unsafe.size(); index++) put(helper, fixture, slots.get(index), new ItemStack(unsafe.get(index)));
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(fixture.worker().status(), "missing_safe_fill", "falling, valuable, container, unbreakable and damaging blocks are not safe fill");
            helper.assertTrue(fixture.mine().getBlockState(floor).is(Blocks.LAVA), "unsafe supplies leave the hazard intact");
            put(helper, fixture, slots.get(unsafe.size()), new ItemStack(Items.COBBLESTONE));
            run(helper, fixture, 1_200, 10_000);
            assertCleared(helper, fixture);
            for (var item : unsafe) helper.assertValueEqual(count(helper, fixture, item), 1, "unsafe fill stock remains untouched");
            helper.assertTrue(fixture.mine().getBlockState(floor).is(Blocks.COBBLESTONE), "a safe finite material contains the floor hazard");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void disabledFloorPatchingPreservesHolesAndCarriedFill(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var floor = fixture.area().min().below();
            set(fixture.chunk(), floor, Blocks.AIR.defaultBlockState());
            observe(helper, fixture);
            fixture.worker().settings().putBoolean(MiningHorizontalOptions.PATCH_FLOOR_TAG, false);
            put(helper, fixture, fixture.worker().outputSlots().getFirst(), new ItemStack(Items.COBBLESTONE));
            run(helper, fixture, 200, 10_000);
            assertCleared(helper, fixture);
            helper.assertTrue(fixture.mine().getBlockState(floor).isAir(), "the disabled option preserves the original floor hole");
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 19, "disabled floor repairs do not consume carried materials");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void excavationWaitsForCompleteObservationsAndResumesAfterSurvey(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var missing = fixture.mine().sections().keySet().stream().filter(position -> position.getY() == 48).findFirst().orElseThrow();
            fixture.mine().remove(missing);
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(fixture.worker().status(), "excavation_needs_observation", "an unobserved floor margin blocks excavation");
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 0, "missing observations cannot produce drops");
            helper.assertValueEqual(tool(helper, fixture, Items.DIAMOND_PICKAXE).getDamageValue(), 0, "missing observations preserve tools");
            observe(helper, fixture);
            run(helper, fixture, 1_200, 10_000);
            assertCleared(helper, fixture);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void fullExcavationOutputsPreserveTerrainAndTools(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            for (int slot : fixture.worker().outputSlots()) put(helper, fixture, slot, new ItemStack(Items.BEDROCK, 64));
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(fixture.worker().status(), "output_full", "excavation waits for real output space");
            for (BlockPos position : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max())) {
                helper.assertFalse(fixture.mine().getBlockState(position).isAir(), "full outputs preserve every selected block");
            }
            helper.assertValueEqual(tool(helper, fixture, Items.DIAMOND_PICKAXE).getDamageValue(), 0, "failed output insertion cannot wear tools");
            helper.assertValueEqual(fixture.worker().progress(), 0.0D, "full outputs cannot accumulate instant work credit");
            for (int slot : fixture.worker().outputSlots()) put(helper, fixture, slot, ItemStack.EMPTY);
            run(helper, fixture, 1_200, 10_000);
            assertCleared(helper, fixture);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void excavationFetchesMissingShovelsAndAxesFromFiniteSupplies(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            put(helper, fixture, 33, ItemStack.EMPTY);
            put(helper, fixture, 34, ItemStack.EMPTY);
            fixture.supply().setItem(0, new ItemStack(Items.IRON_SHOVEL));
            fixture.supply().setItem(1, new ItemStack(Items.IRON_AXE));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            run(helper, fixture, 200, 2_000);
            helper.assertTrue(fixture.worker().status().startsWith("missing_"), "mixed terrain pauses for its actual required tool");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 1, "disabled supply access preserves the shovel");
            helper.assertValueEqual(fixture.supply().getItem(1).getCount(), 1, "disabled supply access preserves the axe");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            run(helper, fixture, 2_200, 12_000);
            assertCleared(helper, fixture);
            helper.assertTrue(fixture.supply().isEmpty(), "each replacement is removed from actual depot stock");
            helper.assertValueEqual(tool(helper, fixture, Items.IRON_SHOVEL).getDamageValue(), 18, "the fetched shovel performs the dirt cuts");
            helper.assertValueEqual(tool(helper, fixture, Items.IRON_AXE).getDamageValue(), 18, "the fetched axe performs the log cuts");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void excavationKeepsContainersAndPermanentBarriersAndReportsUnsafeRock(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var chest = fixture.area().min();
            var barrier = chest.south();
            var unsafeRock = chest.east(3);
            set(fixture.chunk(), chest, Blocks.CHEST.defaultBlockState());
            set(fixture.chunk(), unsafeRock.north(), Blocks.WATER.defaultBlockState());
            observe(helper, fixture);
            fixture.worker().settings().putLongArray("MiningPermanentHazardBarriers", new long[] {barrier.asLong()});
            run(helper, fixture, 200, 10_000);
            helper.assertTrue(fixture.mine().getBlockState(chest).is(Blocks.CHEST), "excavation preserves block entities");
            helper.assertTrue(fixture.mine().getBlockState(barrier).is(Blocks.STONE), "excavation preserves permanent barriers");
            helper.assertTrue(fixture.mine().getBlockState(unsafeRock).is(Blocks.STONE), "an uncontained fluid face prevents the unsafe cut");
            helper.assertValueEqual(fixture.worker().status(), "excavation_blocked", "unmined unsafe rock cannot be reported complete");
            helper.assertTrue(count(helper, fixture, Items.DIRT) > 0, "safe reachable work continues around protected terrain");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void floorRepairsCannotAlterAnythingOutsideTheExcavationFloor(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var floor = fixture.area().min().below();
            set(fixture.chunk(), floor, Blocks.AIR.defaultBlockState());
            set(fixture.chunk(), floor.west(), Blocks.AIR.defaultBlockState());
            set(fixture.chunk(), floor.below(), Blocks.AIR.defaultBlockState());
            set(fixture.chunk(), floor.south(), Blocks.CHEST.defaultBlockState());
            observe(helper, fixture);
            var fill = Blocks.COBBLESTONE.defaultBlockState();
            helper.assertFalse(fixture.mine().changeFloor(floor.west(), fill), "repair cannot extend beyond the selected horizontal bounds");
            helper.assertFalse(fixture.mine().changeFloor(floor.below(), fill), "repair cannot extend below the selected floor");
            helper.assertFalse(fixture.mine().changeFloor(floor.east(), fill), "repair cannot overwrite an existing solid floor");
            helper.assertFalse(fixture.mine().changeFloor(floor.south(), fill), "repair cannot overwrite a container");
            helper.assertFalse(fixture.mine().changeFloor(floor, Blocks.WATER.defaultBlockState()), "repair cannot replace a hole with fluid");
            helper.assertTrue(fixture.mine().changeFloor(floor, fill), "repair can change the observed hole immediately beneath the work area");
            var saved = SettlementMine.load(fixture.mine().save());
            helper.assertTrue(saved.getBlockState(floor).is(Blocks.COBBLESTONE), "pending floor repairs survive a save and reload");
            helper.assertTrue(saved.pending(), "the reloaded floor repair still awaits physical handoff");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void horizontalMinersRememberTheirBorderEntranceAfterUnloadingAtHome(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var entry = fixture.worker().position();
            helper.assertFalse(fixture.mine().contains(entry), "the native entrance is on the observed border of the selected terrain");
            var home = entry.west(40);
            fixture.villager().setPos(home.getBottomCenter());
            fixture.worker().observe(helper.getLevel(), fixture.villager(),
                    new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 0L);
            var restored = SettlementWorkerState.load(fixture.worker().save());
            fixture.town().recordWorker(restored);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 100L);
            helper.assertValueEqual(restored.status(), "returning_to_mine", "a saved border entrance permits a paid return from home");
            helper.assertValueEqual(restored.items(helper.getLevel().registryAccess()).stream()
                    .filter(stack -> stack.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum(), 0, "the return trip cannot mine instantly");
            var resumed = new Fixture(fixture.town(), fixture.area(), fixture.chunk(), fixture.mine(),
                    fixture.supply(), fixture.output(), restored, fixture.villager());
            run(helper, resumed, 200, 12_000);
            assertCleared(helper, resumed);
            helper.assertValueEqual(fixture.villager().blockPosition(), home, "virtual travel preserves the loaded worker position");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_horizontal_excavation")
    public static void minersOpenASafeApproachBeforeRepairingADistantFloorHole(GameTestHelper helper) {
        fixture(helper, 3, fixture -> {
            var floor = fixture.area().min().east(5).below();
            set(fixture.chunk(), floor, Blocks.AIR.defaultBlockState());
            observe(helper, fixture);
            put(helper, fixture, fixture.worker().outputSlots().getFirst(), new ItemStack(Items.COBBLESTONE));
            run(helper, fixture, 200, 10_000);
            assertCleared(helper, fixture);
            helper.assertTrue(fixture.mine().getBlockState(floor).is(Blocks.COBBLESTONE),
                    "safe excavation opens access to a distant floor repair");
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 18, "the distant repair consumes exactly one real block");
        });
    }

    private static HiredWorkContext context(Fixture fixture) {
        return new HiredWorkContext(HiredJobInventory.getJobInventory(fixture.villager()), fixture.worker().settings(),
                fixture.area().center(), fixture.area().min(), fixture.area().max(), 64, 32, true, 1, false, true);
    }

    private static void assertCleared(GameTestHelper helper, Fixture fixture) {
        for (BlockPos position : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max())) {
            helper.assertTrue(fixture.mine().getBlockState(position).isAir(),
                    "finite excavation clears " + position.subtract(fixture.area().min()) + " with status " + fixture.worker().status());
        }
        helper.assertValueEqual(fixture.worker().status(), "excavation_complete", "exhausted excavation is reported complete");
    }

    private static int count(GameTestHelper helper, Fixture fixture, Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static ItemStack tool(GameTestHelper helper, Fixture fixture, Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item)).findFirst().orElse(ItemStack.EMPTY);
    }

    private static void put(GameTestHelper helper, Fixture fixture, int slot, ItemStack stack) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        items.set(slot, stack);
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private static void run(GameTestHelper helper, Fixture fixture, int from, int until) {
        for (int now = from; now <= until; now += 200) SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
    }

    private static void observe(GameTestHelper helper, Fixture fixture) {
        SettlementMineService.observeArea(helper.getLevel(), fixture.town(), fixture.area(), fixture.chunk());
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private static void fixture(GameTestHelper helper, int height, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = new LevelChunk(level, new ChunkPos(820_000 + origin.getX() * 4, 820_000 + origin.getZ() * 4));
        var min = new BlockPos(chunk.getPos().getMinBlockX() + 4, 65, chunk.getPos().getMinBlockZ() + 5);
        var max = min.offset(5, height - 1, 2);
        for (BlockPos position : BlockPos.betweenClosed(new BlockPos(chunk.getPos().getMinBlockX(), 63, chunk.getPos().getMinBlockZ()),
                new BlockPos(chunk.getPos().getMaxBlockX(), 64, chunk.getPos().getMaxBlockZ()))) set(chunk, position, Blocks.STONE.defaultBlockState());
        for (BlockPos position : BlockPos.betweenClosed(min, max)) {
            var block = switch ((position.getX() - min.getX()) % 3) {
                case 0 -> Blocks.STONE;
                case 1 -> Blocks.DIRT;
                default -> Blocks.OAK_LOG;
            };
            set(chunk, position, block.defaultBlockState());
        }
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), min, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), min, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Excavation", min, max, 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the excavation area is registered");
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage", min.east(9), min.offset(10, 2, 2), 5, true);
        town.putArea(town.leader(), storage);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", min.west(8), min.west(7), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
        var supply = town.registerDepot(town.leader(), storage.id(), min.east(9), SettlementDepotPurpose.MATERIALS);
        var output = town.registerDepot(town.leader(), storage.id(), min.east(10), SettlementDepotPurpose.OUTPUT);
        supply.observeRules(List.of());
        output.observeRules(List.of());
        SettlementMineService.observeArea(level, town, area, chunk);
        var mine = town.mines().get(area.id());
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(min.west().south().getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        HiredWorkStateStore.state(villager).putString("MiningMode", "horizontal_excavation");
        var inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        inventory.setItem(33, new ItemStack(Items.DIAMOND_SHOVEL));
        inventory.setItem(34, new ItemStack(Items.DIAMOND_AXE));
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        helper.assertFalse(level.hasChunkAt(min), "the excavation fixture is detached from the live world");
        helper.assertTrue(mine.fullyObserved(level.getMinBuildHeight(), level.getMaxBuildHeight()), "the whole excavation and safety margin are observed");
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, area, chunk, mine, supply, output, worker, villager));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk, SettlementMine mine,
                           SettlementDepot supply, SettlementDepot output, SettlementWorkerState worker, Villager villager) {
    }
}
