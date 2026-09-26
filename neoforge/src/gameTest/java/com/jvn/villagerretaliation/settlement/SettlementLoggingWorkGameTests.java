package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
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
public final class SettlementLoggingWorkGameTests {
    private SettlementLoggingWorkGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void backgroundLoggerCutsRealTreesReplantsAndEarnsPracticeOnce(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            run(helper, fixture, 200, 4_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.OAK_LOG), 4, "the observed tree produces its four real log drops");
            helper.assertValueEqual(count(helper, fixture.worker(), Items.OAK_LEAVES), 4, "real shears collect the four observed leaves");
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(Blocks.OAK_SAPLING), "one supplied sapling replaces the harvested tree");
            helper.assertValueEqual(fixture.supply().count(stack -> stack.is(Items.OAK_SAPLING)), 0, "planting consumes the actual depot sapling");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.DIAMOND_AXE).getDamageValue(), 4, "each log wears the real equipped axe");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.SHEARS).getDamageValue(), 4, "leaf harvesting wears the real shears");
            double practice = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(practice > 0.0D, "finishing the tree grants logging practice");
            run(helper, fixture, 4_200, 8_000);
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), practice, "waiting after completion does not award the same tree again");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.root()), "background harvesting and loot do not load the forest");
            helper.assertTrue(fixture.chunk().getBlockState(fixture.root()).is(Blocks.OAK_LOG), "unloaded physical terrain awaits its handoff");
            SettlementForestService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            helper.assertTrue(fixture.chunk().getBlockState(fixture.root()).is(Blocks.OAK_SAPLING), "the returning world receives the completed replanting");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void loggerDepositsOnlyRealCompletedTreeOutputs(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.worker().settings().putBoolean("AutoDepositOutputs", true);
            run(helper, fixture, 200, 8_000);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.OAK_LOG)), 4, "the dedicated output depot receives the finite logs");
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.OAK_LEAVES)), 4, "the depot receives the real collected leaves");
            helper.assertValueEqual(count(helper, fixture.worker(), Items.OAK_LOG), 0, "deposited logs leave the worker inventory");
            run(helper, fixture, 8_200, 12_000);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.OAK_LOG)), 4, "an exhausted tree cannot generate extra timber");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void loggerFullInventoryPreservesBlocksToolsAndBlockedWorkTime(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            for (int slot : fixture.worker().outputSlots()) items.set(slot, new ItemStack(Items.STONE, 64));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.worker().status(), "output_full", "a full job inventory pauses cutting");
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(Blocks.OAK_LOG), "capacity failure leaves the actual tree untouched");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.DIAMOND_AXE).getDamageValue(), 0, "failed cutting does not wear the axe");
            double credit = fixture.worker().progress();
            run(helper, fixture, 400, 6_000);
            helper.assertValueEqual(fixture.worker().progress(), credit, "blocked capacity cannot accumulate hours of instant work credit");
            helper.assertValueEqual(fixture.supply().count(stack -> stack.is(Items.OAK_SAPLING)), 1, "blocked cutting does not consume planting supplies");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void brokenAxesPauseARecordedTreeUntilARealReplacementArrives(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            items.get(HiredJobInventory.MAINHAND_SLOT).setDamageValue(Items.DIAMOND_AXE.getDefaultInstance().getMaxDamage() - 1);
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.OAK_LOG), 1, "the last durability point permits only one real cut");
            helper.assertValueEqual(fixture.worker().status(), "missing_axe", "the remaining recorded tree waits for a replacement axe");
            helper.assertTrue(fixture.forest().getBlockState(fixture.root().above()).is(Blocks.OAK_LOG), "the unfinished tree remains available");
            fixture.supply().setItem(1, new ItemStack(Items.IRON_AXE));
            run(helper, fixture, 2_200, 6_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.OAK_LOG), 4, "a finite replacement resumes the same tree");
            helper.assertTrue(fixture.supply().getItem(1).isEmpty(), "the replacement tool leaves the depot");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.IRON_AXE).getDamageValue(), 3, "the replacement only wears for the remaining logs");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void strippedLoggingUsesStrippedLootAndAdditionalToolWear(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.worker().settings().putBoolean(HiredLoggingOptions.STRIP_LOGS_TAG, true);
            run(helper, fixture, 200, 4_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.STRIPPED_OAK_LOG), 4, "stripping changes the actual loot state");
            helper.assertValueEqual(count(helper, fixture.worker(), Items.OAK_LOG), 0, "stripped logs do not also produce ordinary logs");
            helper.assertValueEqual(tool(helper, fixture.worker(), Items.DIAMOND_AXE).getDamageValue(), 8, "stripping and cutting each spend durability");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void loggerResumesNativePartialPlansDespiteLaterFilterChanges(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var state = HiredWorkStateStore.state(fixture.villager());
            var inventory = HiredJobInventory.getJobInventory(fixture.villager());
            var context = nativeContext(fixture, state, inventory);
            var logs = List.of(fixture.root(), fixture.root().above(), fixture.root().above(2), fixture.root().above(3));
            var leaves = net.minecraft.core.Direction.Plane.HORIZONTAL.stream().map(direction -> fixture.root().above(3).relative(direction)).toList();
            LoggingHarvestPlan.begin(context, fixture.root(), null, logs, leaves, List.of(fixture.root()), new ItemStack(Items.OAK_SAPLING),
                    false, "minecraft:oak");
            LoggingHarvestPlan.incrementLogsCut(context);
            LoggingHarvestPlan.incrementLogsCut(context);
            set(fixture.chunk(), fixture.root(), Blocks.AIR.defaultBlockState());
            set(fixture.chunk(), fixture.root().above(), Blocks.AIR.defaultBlockState());
            inventory.setItem(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.OAK_LOG, 2));
            HiredLoggingFilters.toggleFilter(state, "minecraft:birch_log");
            state.putBoolean(HiredLoggingOptions.STRIP_LOGS_TAG, true);
            var worker = SettlementWorkerState.capture(level, fixture.villager(), SettlementWorkforceService.duty(fixture.villager()).orElseThrow(), 0L);
            fixture.town().recordWorker(worker);
            SettlementForestService.observeArea(level, fixture.town(), fixture.area(), fixture.chunk());
            run(helper, fixture, 200, 4_000);
            helper.assertValueEqual(count(helper, worker, Items.OAK_LOG), 4, "the recorded oak finishes after current filters switch to birch");
            helper.assertValueEqual(count(helper, worker, Items.STRIPPED_OAK_LOG), 0, "the selected tree keeps its original stripping choice");
            helper.assertFalse(worker.settings().contains("PendingLoggingTreeOrigin"), "completed native work is consumed once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void partialBackgroundLoggingReturnsInventoryAndNativePlanExactlyOnce(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var items = fixture.worker().items(level.registryAccess());
            items.set(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.WOODEN_AXE));
            fixture.worker().updateInventory(level.registryAccess(), items);
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            int cut = count(helper, fixture.worker(), Items.OAK_LOG);
            helper.assertTrue(cut > 0 && cut < 4, "a slow tool leaves a genuinely partial harvest");
            var restored = SettlementWorkerState.load(fixture.worker().save());
            SettlementForestService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            helper.assertTrue(restored.restore(fixture.villager()), "the returning native worker restores the newer background inventory");
            helper.assertFalse(restored.restore(fixture.villager()), "the same background revision cannot restore twice");
            var inventory = HiredJobInventory.getJobInventory(fixture.villager());
            var nativePlan = LoggingHarvestPlan.read(nativeContext(fixture, HiredWorkStateStore.state(fixture.villager()), inventory));
            helper.assertTrue(nativePlan != null, "the native worker receives the pending tree plan");
            helper.assertValueEqual(nativePlan.logsCut(), cut, "native practice bookkeeping includes only actual cuts");
            helper.assertValueEqual(nativePlan.logs().length, 4 - cut, "only the remaining real logs are pending");
            var returned = SettlementWorkerState.capture(level, fixture.villager(),
                    SettlementWorkforceService.duty(fixture.villager()).orElseThrow(), 200L);
            fixture.town().recordWorker(returned);
            run(helper, fixture, 400, 6_000);
            helper.assertValueEqual(count(helper, returned, Items.OAK_LOG), 4, "another unload finishes without duplicating earlier inventory");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void loggerRespectsSaplingOptionsAndObservedGroundSupport(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.worker().settings().putBoolean(HiredLoggingOptions.PLANT_SAPLINGS_TAG, false);
            run(helper, fixture, 200, 4_000);
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).isAir(), "disabled replanting leaves the cleared tree position empty");
            helper.assertValueEqual(fixture.supply().count(stack -> stack.is(Items.OAK_SAPLING)), 1, "disabled planting preserves depot stock");
            helper.assertTrue(SettlementBackgroundLogging.canPlant(fixture.forest(), fixture.root(), Blocks.OAK_SAPLING.defaultBlockState()),
                    "real dirt supports the native sapling");
            fixture.forest().change(java.util.Map.of(fixture.root().below(), Blocks.STONE.defaultBlockState()));
            helper.assertFalse(SettlementBackgroundLogging.canPlant(fixture.forest(), fixture.root(), Blocks.OAK_SAPLING.defaultBlockState()),
                    "stone cannot stand in for a valid planting bed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void loggerUsesSilkTouchHoesForLeavesAndKeepsEnchantedToolComponents(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var items = fixture.worker().items(level.registryAccess());
            var hoe = new ItemStack(Items.DIAMOND_HOE);
            hoe.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
            items.set(fixture.worker().supplySlots().getFirst(), hoe);
            fixture.worker().updateInventory(level.registryAccess(), items);
            run(helper, fixture, 200, 4_000);
            helper.assertValueEqual(count(helper, fixture.worker(), Items.OAK_LEAVES), 4, "Silk Touch hoe loot preserves actual leaves");
            helper.assertTrue(tool(helper, fixture.worker(), Items.DIAMOND_HOE).isEnchanted(), "tool handoffs preserve enchantment components");
            helper.assertTrue(tool(helper, fixture.worker(), Items.DIAMOND_HOE).getDamageValue() > 0, "the real enchanted leaf tool is worn");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void legacyLoggerNeedsARealWorkerObservationBeforeProducingLoot(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var saved = fixture.worker().save();
            saved.remove("EntityContext");
            var old = SettlementWorkerState.load(saved);
            fixture.town().recordWorker(old);
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(old.status(), "awaiting_worker_observation", "legacy saves wait for real loot and equipment context");
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(Blocks.OAK_LOG), "missing worker context cannot invent tree outputs");
            helper.assertValueEqual(count(helper, old, Items.OAK_LOG), 0, "no timber is created without the actual worker");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void loggerCannotUseSuppliesWhenDepotAccessIsDisabled(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            items.set(HiredJobInventory.MAINHAND_SLOT, ItemStack.EMPTY);
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            fixture.supply().setItem(1, new ItemStack(Items.DIAMOND_AXE));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(fixture.worker().status(), "missing_axe", "disabled depot access prevents fetching a tool");
            helper.assertValueEqual(fixture.supply().getItem(1).getCount(), 1, "the protected depot axe remains in place");
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(Blocks.OAK_LOG), "the forest remains uncut");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void conflictingForestChangesStopFurtherBackgroundHarvesting(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            items.set(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.WOODEN_AXE));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            int cut = count(helper, fixture.worker(), Items.OAK_LOG);
            helper.assertTrue(cut > 0 && cut < 4, "the worker completed part of the tree before the conflict");
            set(fixture.chunk(), fixture.root(), Blocks.DIAMOND_BLOCK.defaultBlockState());
            SettlementForestService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            run(helper, fixture, 400, 4_000);
            helper.assertValueEqual(fixture.worker().status(), "forest_conflict", "a conflicting forest pauses further cutting");
            helper.assertValueEqual(count(helper, fixture.worker(), Items.OAK_LOG), cut, "pending conflict cannot produce more timber");
            helper.assertTrue(fixture.chunk().getBlockState(fixture.root()).is(Blocks.DIAMOND_BLOCK), "the changed native block remains intact");
            helper.assertTrue(fixture.forest().pending(), "the unapplied changes remain available for recovery");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_logging_work")
    public static void legacyCompletedTreePlansDoNotAwardPracticeAgain(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var worker = fixture.worker();
            var context = SettlementBackgroundLogging.context(HiredJobInventory.getJobInventory(fixture.villager()), worker, fixture.area());
            var logs = List.of(fixture.root(), fixture.root().above(), fixture.root().above(2), fixture.root().above(3));
            LoggingHarvestPlan.begin(context, fixture.root(), null, logs, List.of(), List.of(), ItemStack.EMPTY, false, "minecraft:oak");
            for (int index = 0; index < 4; index++) LoggingHarvestPlan.incrementLogsCut(context);
            LoggingHarvestPlan.replaceLogs(context, new long[0]);
            worker.settings().putBoolean("LoggingTreeDepositPending", true);
            for (BlockPos log : logs) set(fixture.chunk(), log, Blocks.AIR.defaultBlockState());
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                set(fixture.chunk(), fixture.root().above(3).relative(direction), Blocks.AIR.defaultBlockState());
            }
            SettlementForestService.observeArea(helper.getLevel(), fixture.town(), fixture.area(), fixture.chunk());
            var profile = SettlementPracticeGameTests.prepare(helper, worker);
            worker.practice(helper.getLevel(), com.jvn.villagerretaliation.skill.HiredWorkPractice.logging(4));
            double earned = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(earned > 0.0D, "the legacy completed harvest has already earned its practice");
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), earned, "an old completed deposit plan cannot grant the practice twice");
            helper.assertFalse(worker.settings().contains("PendingLoggingTreeOrigin"), "the obsolete completed plan is removed");
        });
    }

    private static HiredWorkContext nativeContext(Fixture fixture, net.minecraft.nbt.CompoundTag state, HiredJobInventory inventory) {
        return new HiredWorkContext(inventory, state, fixture.area().center(), fixture.area().min(), fixture.area().max(),
                64, 32, true, 1, false, true);
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

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = new LevelChunk(level, new ChunkPos(670_000 + origin.getX() * 4, 670_000 + origin.getZ() * 4));
        var root = new BlockPos(chunk.getPos().getMinBlockX() + 7, 65, chunk.getPos().getMinBlockZ() + 8);
        set(chunk, root.below(), Blocks.DIRT.defaultBlockState());
        for (int y = 0; y < 4; y++) set(chunk, root.above(y), Blocks.OAK_LOG.defaultBlockState());
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) set(chunk, root.above(3).relative(direction), Blocks.OAK_LEAVES.defaultBlockState());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), root, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), root, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FORESTRY, "Forest",
                new BlockPos(chunk.getPos().getMinBlockX(), 64, chunk.getPos().getMinBlockZ()),
                new BlockPos(chunk.getPos().getMaxBlockX(), 79, chunk.getPos().getMaxBlockZ()), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the forestry fixture is registered");
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage", root.east(12), root.offset(14, 2, 2), 5, true);
        town.putArea(town.leader(), storage);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", root.west(14), root.west(12), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
        var supply = town.registerDepot(town.leader(), storage.id(), root.east(12), SettlementDepotPurpose.MATERIALS);
        var output = town.registerDepot(town.leader(), storage.id(), root.east(13), SettlementDepotPurpose.OUTPUT);
        supply.observeRules(List.of());
        output.observeRules(List.of());
        supply.setItem(0, new ItemStack(Items.OAK_SAPLING));
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(root.west(2).getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        var state = HiredWorkStateStore.state(villager);
        HiredLoggingOptions.initializeDefaults(state);
        state.putBoolean(HiredLoggingOptions.HARVEST_LEAVES_TAG, true);
        state.putBoolean("AutoDepositOutputs", false);
        var inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_AXE));
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.SHEARS));
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        SettlementForestService.observeArea(level, town, area, chunk);
        var forest = town.forests().get(area.id());
        helper.assertFalse(level.hasChunkAt(root), "logging fixture terrain is detached from the live level");
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, area, chunk, forest, root, supply, output, worker, villager));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk, SettlementForest forest, BlockPos root,
                           SettlementDepot supply, SettlementDepot output, SettlementWorkerState worker, Villager villager) {
    }
}
