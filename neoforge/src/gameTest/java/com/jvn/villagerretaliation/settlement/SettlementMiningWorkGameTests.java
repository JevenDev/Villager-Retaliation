package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementMiningWorkGameTests {
    private SettlementMiningWorkGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void backgroundMinersConsumeFiniteOreToolsAndAwardPracticeOnce(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 3, "the three observed ores produce three real drops");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.DIAMOND_PICKAXE).getDamageValue(), 3, "each block wears the real pickaxe");
            helper.assertTrue(fixture.mine().getBlockState(fixture.ore()).isAir(), "the observed vein is exhausted");
            double earned = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(earned > 0.0D, "successful mining earns skill practice");
            run(helper, fixture, 2_200, 4_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 3, "exhausted terrain cannot produce more ore");
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), earned, "the same ore cannot award practice again");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "background work never loads the mine");
            helper.assertTrue(fixture.chunk().getBlockState(fixture.ore()).is(Blocks.IRON_ORE), "physical terrain awaits its chunk handoff");
            SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            helper.assertTrue(fixture.chunk().getBlockState(fixture.ore()).isAir(), "the returning chunk receives the finite cuts");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void minersHaulOnlyTheirRealOutputsToDedicatedDepots(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.worker().settings().putBoolean("AutoDepositOutputs", true);
            run(helper, fixture, 200, 4_000);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.RAW_IRON)), 3, "the dedicated output depot receives the real ore drops");
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 0, "delivered drops leave the worker");
            helper.assertTrue(fixture.supply().isEmpty(), "ore is not deposited into a materials depot");
            run(helper, fixture, 4_200, 6_000);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.RAW_IRON)), 3, "waiting cannot create additional stock");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void fullMiningOutputsPreserveTerrainToolsAndWorkCredit(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            for (int slot : fixture.worker().outputSlots()) items.set(slot, new ItemStack(Items.STONE, 64));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(fixture.worker().status(), "output_full", "mining pauses before consuming a blocked drop");
            helper.assertTrue(fixture.mine().getBlockState(fixture.ore()).is(Blocks.IRON_ORE), "full output space leaves the block intact");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.DIAMOND_PICKAXE).getDamageValue(), 0, "failed work does not damage a tool");
            helper.assertValueEqual(fixture.worker().progress(), 0.0D, "full output space cannot accumulate instant work credit");
            items = fixture.worker().items(helper.getLevel().registryAccess());
            items.set(fixture.worker().outputSlots().getFirst(), ItemStack.EMPTY);
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            run(helper, fixture, 2_200, 4_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 3, "freeing output space resumes the finite vein");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void minersFetchSuitableFiniteToolsOnlyWhenSupplyAccessAllows(GameTestHelper helper) {
        fixture(helper, fixture -> {
            equip(helper, fixture, new ItemStack(Items.WOODEN_PICKAXE));
            fixture.supply().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 0, "an insufficient tool tier cannot harvest iron");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 1, "disabled supply access preserves the replacement");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            run(helper, fixture, 2_200, 5_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 3, "the supplied iron pickaxe mines the finite ore");
            helper.assertTrue(fixture.supply().getItem(0).isEmpty(), "equipping consumes the actual stored replacement");
            helper.assertValueEqual(count(helper, fixture.worker(), Items.WOODEN_PICKAXE), 1, "the previous tool remains in carried supplies");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.IRON_PICKAXE).getDamageValue(), 3, "the fetched tool wears only for successful cuts");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void brokenMiningToolsStopUntilARealReplacementArrives(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
            equip(helper, fixture, pickaxe);
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 1, "the last durability point permits only one block");
            helper.assertValueEqual(fixture.worker().status(), "missing_pickaxe", "the rest of the vein waits for a real tool");
            fixture.supply().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            run(helper, fixture, 1_200, 4_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 3, "the replacement resumes the remaining blocks");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.IRON_PICKAXE).getDamageValue(), 2, "the replacement spends only the remaining durability");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void backgroundMiningUsesSilkTouchLootAndRetainsEnchantments(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
            equip(helper, fixture, pickaxe);
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.IRON_ORE), 3, "native Silk Touch loot produces ore blocks");
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 0, "Silk Touch does not also generate ordinary drops");
            helper.assertFalse(tool(helper, fixture.worker(), Items.DIAMOND_PICKAXE).getEnchantments().isEmpty(), "the actual enchanted tool survives work");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void miningRoutesCannotReachOreThroughAnObservedSolidWall(GameTestHelper helper) {
        fixture(helper, fixture -> {
            for (BlockPos position : BlockPos.betweenClosed(
                    new BlockPos(fixture.ore().getX() - 2, 65, fixture.chunk().getPos().getMinBlockZ()),
                    new BlockPos(fixture.ore().getX() - 2, 70, fixture.chunk().getPos().getMaxBlockZ()))) {
                set(fixture.chunk(), position, Blocks.STONE.defaultBlockState());
            }
            observe(helper, fixture);
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 0, "a closed wall blocks every observed approach");
            helper.assertValueEqual(fixture.worker().status(), "no_safe_mining_route", "the worker reports its blocked route");
            fixture.mine().change(Map.of(fixture.ore().west(2), Blocks.AIR.defaultBlockState(),
                    fixture.ore().west(2).above(), Blocks.AIR.defaultBlockState()));
            run(helper, fixture, 1_200, 3_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 3, "opening a real two block passage makes the vein reachable");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void minersWaitForContainmentAndPreserveTheirActualUnderfootLanding(GameTestHelper helper) {
        fixture(helper, fixture -> {
            for (int x = 0; x < 3; x++) set(fixture.chunk(), fixture.ore().east(x).above(), Blocks.LAVA.defaultBlockState());
            var underfoot = fixture.worker().position().below();
            set(fixture.chunk(), underfoot, Blocks.IRON_ORE.defaultBlockState());
            set(fixture.chunk(), underfoot.below(), Blocks.AIR.defaultBlockState());
            observe(helper, fixture);
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 0, "fluid faces and an unsafe underfoot drop both block harvesting");
            for (int x = 0; x < 3; x++) fixture.mine().change(Map.of(fixture.ore().east(x).above(), Blocks.AIR.defaultBlockState()));
            run(helper, fixture, 1_200, 3_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 3, "contained ore can be mined from reachable safe stances");
            helper.assertTrue(fixture.mine().getBlockState(underfoot).is(Blocks.IRON_ORE),
                    "virtual movement never permits a cut that would drop the saved native worker");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void minerInventoryAndVeinPlansReturnToNativeWorkersOnce(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var context = context(fixture, fixture.worker().settings());
            HiredWorkPlan.replaceWithObjective(context, "vein", fixture.ore(),
                    List.of(fixture.ore(), fixture.ore().east(), fixture.ore().east(2)), 20);
            var nativeState = HiredWorkStateStore.state(fixture.villager());
            nativeState.putLong("ActiveWorkBlockPos", fixture.ore().asLong());
            nativeState.putInt("ProgressTicks", 500);
            var pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
            equip(helper, fixture, pickaxe);
            run(helper, fixture, 200, 1_000);
            var restored = SettlementWorkerState.load(fixture.worker().save());
            helper.assertTrue(restored.restore(fixture.villager()), "a newer worker revision restores its real output inventory");
            var inventory = HiredJobInventory.getJobInventory(fixture.villager());
            helper.assertValueEqual(inventory.countItem(Items.RAW_IRON), 1, "the native worker receives one real mined block");
            helper.assertFalse(HiredWorkPlan.targets(context(fixture, nativeState)).contains(fixture.ore()), "the completed target is removed from the native vein plan");
            helper.assertValueEqual(HiredWorkPlan.targets(context(fixture, nativeState)).size(), 2, "unmined targets remain in the shared plan");
            helper.assertFalse(nativeState.contains("ActiveWorkBlockPos"), "stale native breaking targets are cleared on handoff");
            helper.assertValueEqual(nativeState.getInt("ProgressTicks"), 0, "old native break progress cannot apply to the next block");
            inventory.setItem(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.RAW_IRON, 9));
            helper.assertFalse(restored.restore(fixture.villager()), "the same saved worker revision cannot restore twice");
            helper.assertValueEqual(inventory.countItem(Items.RAW_IRON), 9, "native inventory changes survive a repeated handoff");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void miningTravelAndRepeatedClockUpdatesCannotCreateFreeWork(GameTestHelper helper) {
        fixture(helper, fixture -> {
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 1L);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 0, "one tick cannot complete approach and breaking time");
            double progress = fixture.worker().progress();
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 1L);
            helper.assertValueEqual(fixture.worker().progress(), progress, "the same clock observation cannot advance the worker twice");
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 3, "real elapsed work eventually completes the finite vein");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void conflictingMineTerrainStopsInventoryAndToolChanges(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var position = fixture.ore().south(2);
            fixture.mine().change(Map.of(position, Blocks.STONE.defaultBlockState()));
            set(fixture.chunk(), position, Blocks.DIAMOND_BLOCK.defaultBlockState());
            SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(fixture.worker().status(), "mine_conflict", "a terrain conflict pauses background mining");
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 0, "conflicting terrain cannot generate loot");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.DIAMOND_PICKAXE).getDamageValue(), 0, "a paused miner preserves its tool");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void miningModeChangesClearStaleHazardPlansOnNativeHandoff(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var state = fixture.worker().settings();
            state.putString("MiningMode", "horizontal_excavation");
            com.jvn.villagerretaliation.interaction.work.mining.MiningWorkerState.prepareBackground(context(fixture, state));
            state.putString("MiningHazardPlanKind", "water");
            state.putLongArray("MiningHazardPlanPositions", new long[] {fixture.ore().above().asLong()});
            state.putInt("MiningHazardPlanIndex", 0);
            state.putBoolean("MiningHazardPlanPermanent", false);
            state.putLongArray("MiningPermanentHazardBarriers", new long[] {fixture.ore().below().asLong()});
            state.putString("HorizontalExcavationStairAxis", "x");
            state.putInt("HorizontalExcavationStairLane", fixture.ore().getZ());
            state.putInt("ExcavationLadderX", fixture.ore().getX());
            state.putInt("ExcavationLadderZ", fixture.ore().getZ());
            state.putString("ExcavationLadderFacing", "north");
            var nativeState = HiredWorkStateStore.state(fixture.villager());
            for (String key : state.getAllKeys()) nativeState.put(key, state.get(key).copy());
            state.putString("MiningMode", "exposed_ores");
            run(helper, fixture, 200, 1_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 3, "the changed mode performs exposed ore work");
            helper.assertTrue(fixture.worker().restore(fixture.villager()), "the changed worker revision returns to native work");
            helper.assertFalse(nativeState.contains("MiningHazardPlanPositions") || nativeState.contains("HorizontalExcavationStairAxis"),
                    "obsolete fluid and staircase plans are cleared in the native state too");
            helper.assertValueEqual(nativeState.getLongArray("MiningPermanentHazardBarriers").length, 1,
                    "changing mode preserves an already placed permanent barrier");
            helper.assertValueEqual(nativeState.getInt("ExcavationLadderX"), fixture.ore().getX(),
                    "changing mode keeps the persisted physical shaft");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void efficiencyChangesActualBackgroundBreakingTime(GameTestHelper helper) {
        fixture(helper, fixture -> {
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 35L);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.RAW_IRON), 0, "the ordinary tool still needs breaking time after its approach");
            var pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), 4);
            equip(helper, fixture, pickaxe);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 40L);
            helper.assertTrue(count(helper, fixture.worker(), Items.RAW_IRON) > 0, "the real Efficiency enchantment reduces the remaining breaking time");
            helper.assertTrue(tool(helper, fixture.worker(), Items.DIAMOND_PICKAXE).getDamageValue() > 0,
                    "faster work still consumes tool durability");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_work")
    public static void observedMiningRoutesDoNotUseContainerTopsAsWalkingFloors(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var barrel = fixture.worker().position().east().below();
            set(fixture.chunk(), barrel, Blocks.BARREL.defaultBlockState());
            observe(helper, fixture);
            var routes = SettlementMiningRoutes.reachable(fixture.mine(), fixture.worker().position());
            helper.assertFalse(routes.containsKey(barrel.above()), "background routes honor the native container climbing restriction");
            helper.assertTrue(routes.containsKey(fixture.worker().position().south()), "ordinary observed floor remains available");
        });
    }

    private static HiredWorkContext context(Fixture fixture, net.minecraft.nbt.CompoundTag state) {
        return new HiredWorkContext(HiredJobInventory.getJobInventory(fixture.villager()), state, fixture.area().center(),
                fixture.area().min(), fixture.area().max(), 64, 32, true, 1, false, true);
    }

    private static void equip(GameTestHelper helper, Fixture fixture, ItemStack tool) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        items.set(HiredJobInventory.MAINHAND_SLOT, tool);
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private static int count(GameTestHelper helper, SettlementWorkerState worker, Item item) {
        return worker.items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static ItemStack tool(GameTestHelper helper, SettlementWorkerState worker, Item item) {
        return worker.items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item)).findFirst().orElse(ItemStack.EMPTY);
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

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = new LevelChunk(level, new ChunkPos(770_000 + origin.getX() * 4, 770_000 + origin.getZ() * 4));
        var ore = new BlockPos(chunk.getPos().getMinBlockX() + 7, 65, chunk.getPos().getMinBlockZ() + 8);
        for (BlockPos position : BlockPos.betweenClosed(new BlockPos(chunk.getPos().getMinBlockX(), 63, chunk.getPos().getMinBlockZ()),
                new BlockPos(chunk.getPos().getMaxBlockX(), 64, chunk.getPos().getMaxBlockZ()))) set(chunk, position, Blocks.STONE.defaultBlockState());
        for (int x = 0; x < 3; x++) set(chunk, ore.east(x), Blocks.IRON_ORE.defaultBlockState());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), ore, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), ore, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Mine",
                new BlockPos(chunk.getPos().getMinBlockX() + 2, 64, chunk.getPos().getMinBlockZ() + 2),
                new BlockPos(chunk.getPos().getMaxBlockX() - 2, 74, chunk.getPos().getMaxBlockZ() - 2), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the mine fixture is registered");
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage", ore.east(12), ore.offset(14, 2, 2), 5, true);
        town.putArea(town.leader(), storage);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", ore.west(14), ore.west(12), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
        var supply = town.registerDepot(town.leader(), storage.id(), ore.east(12), SettlementDepotPurpose.MATERIALS);
        var output = town.registerDepot(town.leader(), storage.id(), ore.east(13), SettlementDepotPurpose.OUTPUT);
        supply.observeRules(List.of());
        output.observeRules(List.of());
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(ore.west(4).getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        SettlementMineService.observeArea(level, town, area, chunk);
        var mine = town.mines().get(area.id());
        helper.assertFalse(level.hasChunkAt(ore), "mining fixture terrain is detached from the live level");
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, area, chunk, mine, ore, supply, output, worker, villager));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk, SettlementMine mine, BlockPos ore,
                           SettlementDepot supply, SettlementDepot output, SettlementWorkerState worker, Villager villager) {
    }
}
