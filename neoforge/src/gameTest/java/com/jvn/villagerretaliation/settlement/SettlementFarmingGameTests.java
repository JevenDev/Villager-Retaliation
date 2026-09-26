package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementFarmingGameTests {
    private SettlementFarmingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void unloadedFarmerHarvestsActualWheatAndKeepsAPlantingReserve(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(((CropBlock) fixture.plot().crop().getBlock()).getAge(fixture.plot().crop()), 0,
                    "one harvested mature crop becomes a newly planted crop");
            helper.assertValueEqual(carried(helper, fixture, Items.WHEAT), 1, "the real crop loot produces one wheat");
            helper.assertTrue(fixture.plot().pending(), "the harvested block awaits its unloaded chunk");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.WHEAT)), 1, "the worker deposits real harvested wheat");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.plot().position()), "crop loot and depot transfers do not load the field");
            helper.assertTrue(((CropBlock) fixture.chunk().getBlockState(fixture.plot().position().above()).getBlock())
                    .isMaxAge(fixture.chunk().getBlockState(fixture.plot().position().above())), "the absent native chunk remains unchanged until handoff");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void fieldAndChunkReceiptsSurviveSavingAndDoNotReplayAPlanting(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            var saved = fixture.plot().save();
            var restored = SettlementFieldPlot.load(saved);
            var loaded = new LevelChunk(level, fixture.chunk().getPos());
            loaded.setBlockState(restored.position(), fixture.chunk().getBlockState(restored.position()), false);
            loaded.setBlockState(restored.position().above(), fixture.chunk().getBlockState(restored.position().above()), false);
            loaded.readAttachmentsFromNBT(level.registryAccess(), fixture.chunk().writeAttachmentsToNBT(level.registryAccess()));
            helper.assertTrue(restored.reconcile(loaded), "saved field work matches its chunk receipt and baseline");
            helper.assertValueEqual(((CropBlock) loaded.getBlockState(restored.position().above()).getBlock())
                    .getAge(loaded.getBlockState(restored.position().above())), 0, "the physical chunk receives the new planting");
            var newer = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
            loaded.setBlockState(restored.position().above(), newer, false);
            var oldLedger = SettlementFieldPlot.load(saved);
            helper.assertTrue(oldLedger.reconcile(loaded), "a previously applied revision is recognized after saving");
            helper.assertTrue(loaded.getBlockState(restored.position().above()) == newer,
                    "a later crop with the original appearance is not replaced by a stale planting");
            var town = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(town.fields().size(), 1, "field identity and pending work survive the town save");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void conflictingFieldChangesArePreservedAndPauseFurtherHarvests(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            fixture.chunk().setBlockState(fixture.plot().position().above(), Blocks.DIAMOND_BLOCK.defaultBlockState(), false);
            helper.assertFalse(fixture.plot().reconcile(fixture.chunk()), "an unexpected field edit blocks replacement");
            helper.assertTrue(fixture.chunk().getBlockState(fixture.plot().position().above()).is(Blocks.DIAMOND_BLOCK), "the changed world block remains intact");
            var restored = SettlementFieldPlot.load(fixture.plot().save());
            helper.assertTrue(restored.pending() && restored.conflict(), "the pending field and conflict remain recoverable");
            fixture.town().recordField(restored);
            run(helper, fixture, 400, 4_000);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.WHEAT)), 1, "the conflicted plot cannot produce another harvest");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void fullInventoryLeavesMatureCropsAndSeedsUntouched(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            var items = fixture.worker().items(level.registryAccess());
            for (int slot : fixture.worker().outputSlots()) items.set(slot, new ItemStack(Items.STONE, 64));
            for (int slot : fixture.worker().supplySlots()) items.set(slot, new ItemStack(Items.STONE, 64));
            fixture.worker().updateInventory(level.registryAccess(), items);
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertTrue(((CropBlock) fixture.plot().crop().getBlock()).isMaxAge(fixture.plot().crop()), "a full worker leaves the real mature crop unharvested");
            helper.assertValueEqual(fixture.worker().status(), "output_full", "the worker reports finite inventory capacity");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 1, "failed harvesting does not spend a depot seed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void realFieldsRegrowRepeatedHarvestsWhileTheTownStaysUnloaded(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            int previous = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            try {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(64, level.getServer());
                fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 8));
                run(helper, fixture, 200, 12_000);
                helper.assertTrue(fixture.output().count(stack -> stack.is(Items.WHEAT)) >= 3, "observed soil, water and light support repeated real crop cycles");
                helper.assertFalse(level.hasChunkAt(fixture.plot().position()), "growing and harvesting do not load the field");
                helper.assertTrue(fixture.plot().soil().is(Blocks.FARMLAND), "the cultivated field retains its real soil");
            } finally {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(previous, level.getServer());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void cropGrowthRespectsRandomTickRulesDarknessAndBoundedTime(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.worker().settings().putBoolean("Enabled", false);
            fixture.plot().change(fixture.plot().soil(), Blocks.WHEAT.defaultBlockState());
            int previous = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            try {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
                fixture.plot().advance(level, fixture.town(), 1_000_000L);
                helper.assertValueEqual(((CropBlock) fixture.plot().crop().getBlock()).getAge(fixture.plot().crop()), 0, "disabled random ticks stop background growth");
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(64, level.getServer());
                fixture.plot().advance(level, fixture.town(), 2_000_000L);
                helper.assertValueEqual(((CropBlock) fixture.plot().crop().getBlock()).getAge(fixture.plot().crop()), 0, "a long gap advances only one bounded running interval");
                double growth = fixture.plot().save().getDouble("Growth");
                fixture.plot().advance(level, fixture.town(), 2_000_000L);
                helper.assertValueEqual(fixture.plot().save().getDouble("Growth"), growth, "repeated time cannot grant more growth");
                var dark = fixture.plot().save();
                dark.putInt("SkyLight", 0);
                dark.putInt("BlockLight", 0);
                var darkPlot = SettlementFieldPlot.load(dark);
                for (long now = 2_000_200L; now <= 2_012_000L; now += 200L) darkPlot.advance(level, fixture.town(), now);
                helper.assertValueEqual(((CropBlock) darkPlot.crop().getBlock()).getAge(darkPlot.crop()), 0, "dark fields cannot grow crops in the background");
            } finally {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(previous, level.getServer());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void farmerFetchesARealHoeAndTillingConsumesItsLastDurability(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.plot().change(Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState());
            fixture.worker().settings().putBoolean(HiredFarmingOptions.TILL_SOIL_TAG, false);
            var items = fixture.worker().items(level.registryAccess());
            items.set(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
            fixture.worker().updateInventory(level.registryAccess(), items);
            ItemStack hoe = new ItemStack(Items.WOODEN_HOE);
            hoe.setDamageValue(hoe.getMaxDamage() - 1);
            fixture.supply().setItem(0, hoe);
            fixture.supply().observeRules(List.of(new ItemStack(Items.BREAD)));
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertFalse(fixture.supply().isEmpty(), "a denied depot cannot provide a hoe");
            fixture.supply().observeRules(List.of());
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            helper.assertTrue(fixture.supply().isEmpty(), "the equipped hoe is removed from actual depot stock");
            helper.assertValueEqual(carried(helper, fixture, Items.IRON_AXE), 1, "the previous hand tool is preserved in carried supplies");
            SettlementBackgroundService.update(level, fixture.town(), 600L);
            helper.assertTrue(fixture.plot().soil().is(Blocks.DIRT), "the disabled tilling option leaves soil unchanged");
            fixture.worker().settings().putBoolean(HiredFarmingOptions.TILL_SOIL_TAG, true);
            SettlementBackgroundService.update(level, fixture.town(), 800L);
            helper.assertTrue(fixture.plot().soil().is(Blocks.FARMLAND), "the real dirt becomes cultivated soil");
            helper.assertTrue(fixture.worker().items(level.registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).isEmpty(), "the worn hoe breaks after its last till");
            helper.assertValueEqual(carried(helper, fixture, Items.IRON_AXE), 1, "breaking the hoe does not lose the previous tool");
            fixture.worker().restore(fixture.villager());
            helper.assertTrue(HiredJobInventory.getJobInventory(fixture.villager()).getItem(HiredJobInventory.MAINHAND_SLOT).isEmpty(),
                    "the broken hoe stays absent when the native worker returns");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager()).countItem(Items.IRON_AXE), 1,
                    "the native worker receives the preserved former hand tool");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void emptyFieldsRequireActualPlantingSeeds(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.plot().change(fixture.plot().soil(), Blocks.AIR.defaultBlockState());
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertTrue(fixture.plot().crop().isAir(), "an empty field cannot invent a seed");
            fixture.supply().setItem(0, new ItemStack(Items.CARROT));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            helper.assertTrue(fixture.plot().crop().isAir(), "disabled depot collection preserves seeds");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            SettlementBackgroundService.update(level, fixture.town(), 600L);
            helper.assertTrue(fixture.plot().crop().is(Blocks.CARROTS), "a real carrot plants the matching crop");
            helper.assertTrue(fixture.supply().isEmpty(), "planting consumes exactly one carrot");
            helper.assertValueEqual(carried(helper, fixture, Items.CARROT), 0, "the planted carrot cannot remain in the worker inventory");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void finalChunkHandoffCapturesFieldEditsAndAppliesPendingCrops(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.chunk().setBlockState(fixture.plot().position().above(), ((CropBlock) Blocks.CARROTS).getStateForAge(7), false);
            SettlementFieldService.onChunkUnload(new ChunkEvent.Unload(fixture.chunk()));
            helper.assertTrue(fixture.plot().crop().is(Blocks.CARROTS), "the final unload snapshot includes the newest crop");
            var lit = fixture.plot().save();
            lit.putInt("BlockLight", 15);
            var plot = SettlementFieldPlot.load(lit);
            fixture.town().recordField(plot);
            plot.change(plot.soil(), Blocks.CARROTS.defaultBlockState());
            SettlementFieldService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            helper.assertValueEqual(((CropBlock) fixture.chunk().getBlockState(plot.position().above()).getBlock())
                    .getAge(fixture.chunk().getBlockState(plot.position().above())), 0, "loading applies the pending crop before native farming");
            helper.assertFalse(plot.pending(), "the crop handoff clears its pending revision");
            helper.assertFalse(level.hasChunkAt(plot.position()), "handoff uses the supplied chunk without forcing it to load");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void fieldDiscoveryFindsRealSoilAndRejectsUnrelatedBlocks(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            BlockPos center = fixture.plot().position();
            fixture.town().removeField(center);
            SettlementFieldService.observeArea(level, fixture.town(), fixture.area(), fixture.chunk());
            helper.assertValueEqual(fixture.town().fields().size(), 1, "the supplied chunk discovers its actual cultivated field");
            fixture.chunk().setBlockState(center.above(), Blocks.CHEST.defaultBlockState(), false);
            var plot = fixture.town().fields().get(center);
            helper.assertTrue(plot.reconcile(fixture.chunk()), "an observed edit remains a native change before any pending work");
            plot.refresh(fixture.chunk());
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertTrue(fixture.town().fields().isEmpty(), "a chest is not treated as a crop or empty planting site");
            helper.assertTrue(fixture.chunk().getBlockState(center.above()).is(Blocks.CHEST), "unrelated world blocks remain intact");
        });
    }


    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void unloadedFarmerHarvestsAndReplantsCarrots(GameTestHelper helper) {
        vegetableHarvest(helper, Blocks.CARROTS, Items.CARROT);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void unloadedFarmerHarvestsAndReplantsPotatoes(GameTestHelper helper) {
        vegetableHarvest(helper, Blocks.POTATOES, Items.POTATO);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void unloadedFarmerHarvestsAndReplantsBeetroots(GameTestHelper helper) {
        vegetableHarvest(helper, Blocks.BEETROOTS, Items.BEETROOT);
    }

    private static void vegetableHarvest(GameTestHelper helper, net.minecraft.world.level.block.Block block,
                                         net.minecraft.world.item.Item produce) {
        fixture(helper, fixture -> {
            var plant = (CropBlock) block;
            setBlock(fixture.chunk(), fixture.plot().position().above(), plant.getStateForAge(plant.getMaxAge()));
            fixture.plot().refresh(fixture.chunk());
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.plot().crop().is(block), "harvested seeds replant the same staple crop");
            helper.assertValueEqual(plant.getAge(fixture.plot().crop()), 0, "the replanted crop starts at age zero");
            helper.assertTrue(carried(helper, fixture, produce) > 0, "the crop's actual loot is stored in the worker inventory");
            helper.assertTrue(fixture.supply().isEmpty(), "replanting uses harvested seeds without inventing depot stock");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.plot().position()), "crop loot does not load terrain");
            fixture.worker().restore(fixture.villager());
            int restored = HiredJobInventory.getJobInventory(fixture.villager()).countItem(produce);
            helper.assertValueEqual(restored, carried(helper, fixture, produce), "native worker return restores the real harvested items");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void drySoilRequiresASeparateRandomTickAfterLosingItsLastMoisture(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.plot().change(fixture.plot().soil(), Blocks.AIR.defaultBlockState());
            var dry = fixture.plot().save();
            dry.putLongArray("WaterSources", new long[0]);
            dry.putBoolean("RainExposed", false);
            var plot = SettlementFieldPlot.load(dry);
            int previous = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            try {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(146, level.getServer());
                plot.advance(level, fixture.town(), 200L);
                helper.assertTrue(plot.soil().is(Blocks.FARMLAND), "seven completed moisture ticks leave dry farmland");
                helper.assertValueEqual(plot.soil().getValue(FarmBlock.MOISTURE), 0, "all seven moisture levels were consumed");
                plot.advance(level, fixture.town(), 400L);
                helper.assertTrue(plot.soil().is(Blocks.DIRT), "a later random tick converts empty dry farmland to dirt");
            } finally {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(previous, level.getServer());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void movingAFarmStopsOldPlotsAndPreservesAlreadyCompletedHarvests(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            var moved = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Moved field",
                    fixture.area().min().east(2), fixture.area().max().east(2), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), moved), "the farm can move within the settlement");
            run(helper, fixture, 400, 8_000);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.WHEAT)), 1, "the completed harvest remains available");
            helper.assertTrue(fixture.plot().pending(), "the old field retains its unapplied world changes");
            helper.assertTrue(fixture.plot().reconcile(fixture.chunk()), "completed field work still reaches its original chunk");
            SettlementBackgroundService.update(level, fixture.town(), 8_200L);
            helper.assertFalse(fixture.town().fields().containsKey(fixture.plot().position()), "the old plot retires after its pending work is applied");
            helper.assertFalse(level.hasChunkAt(fixture.plot().position()), "retiring a field does not load it");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void fieldClockExcludesTheLastNativeTickBeforeBackgroundGrowth(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            setBlock(fixture.chunk(), fixture.plot().position().above(), Blocks.WHEAT.defaultBlockState());
            SettlementChunkReceipts.onVanillaTick(fixture.chunk(), 190L);
            fixture.plot().refresh(fixture.chunk());
            int previous = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            try {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(64, level.getServer());
                fixture.plot().advance(level, fixture.town(), 200L);
                helper.assertTrue(fixture.plot().save().getDouble("Growth") < 0.03D,
                        "only the ten ticks after the last native tick contribute background growth");
                double growth = fixture.plot().save().getDouble("Growth");
                fixture.plot().advance(level, fixture.town(), 200L);
                helper.assertValueEqual(fixture.plot().save().getDouble("Growth"), growth, "the same handoff interval cannot replay");
            } finally {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(previous, level.getServer());
            }
        });
    }

    private static void setBlock(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private static int carried(GameTestHelper helper, Fixture fixture, net.minecraft.world.item.Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void run(GameTestHelper helper, Fixture fixture, int from, int until) {
        for (int now = from; now <= until; now += 200) SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = new LevelChunk(level, new ChunkPos(640_000 + origin.getX() * 4, 640_000 + origin.getZ() * 4));
        BlockPos center = new BlockPos(chunk.getPos().getMinBlockX() + 8, 64, chunk.getPos().getMinBlockZ() + 8);
        setBlock(chunk, center, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        setBlock(chunk, center.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        setBlock(chunk, center.east(3), Blocks.WATER.defaultBlockState());
        helper.assertFalse(level.hasChunkAt(center), "fixture terrain remains detached from the live level");
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Field", center, center.above(), 5, true);
        town.putArea(town.leader(), area);
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage", center.east(8), center.offset(10, 2, 2), 5, true);
        town.putArea(town.leader(), storage);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", center.west(3), center.west(), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
        var supply = town.registerDepot(town.leader(), storage.id(), center.east(8), SettlementDepotPurpose.MATERIALS);
        var output = town.registerDepot(town.leader(), storage.id(), center.east(9), SettlementDepotPurpose.OUTPUT);
        supply.observeRules(List.of());
        output.observeRules(List.of());
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.above().getCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_HOE));
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        var plot = SettlementFieldPlot.capture(level, area.id(), center, chunk, 0L);
        var lit = plot.save();
        lit.putInt("BlockLight", 15);
        plot = SettlementFieldPlot.load(lit);
        town.recordField(plot);
        helper.assertFalse(level.hasChunkAt(center), "observing the fixture does not load its chunk");
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, area, chunk, plot, supply, output, worker, villager));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk, SettlementFieldPlot plot,
                           SettlementDepot supply, SettlementDepot output, SettlementWorkerState worker, Villager villager) {
    }
}
