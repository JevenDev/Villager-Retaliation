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
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(((CropBlock) fixture.plot().crop().getBlock()).getAge(fixture.plot().crop()), 0,
                    "one harvested mature crop becomes a newly planted crop");
            helper.assertValueEqual(carried(helper, fixture, Items.WHEAT), 1, "the real crop loot produces one wheat");
            helper.assertTrue(fixture.plot().pending(), "the harvested block awaits its unloaded chunk");
            double practice = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(practice > 0.0D, "a completed harvest and replant earns farming practice");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), practice, "depositing a farmer\'s output cannot grant extra practice");
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.WHEAT)), 1, "the worker deposits real harvested wheat");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.plot().position()), "crop loot and depot transfers do not load the field");
            helper.assertTrue(((CropBlock) fixture.chunk().getBlockState(fixture.plot().position().above()).getBlock())
                    .isMaxAge(fixture.chunk().getBlockState(fixture.plot().position().above())), "the absent native chunk remains unchanged until handoff");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void backgroundFarmersBatchHarvestsAndResumeDeliveryAfterSaving(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var worker = fixture.worker();
            var items = worker.items(level.registryAccess());
            items.set(worker.outputSlots().getFirst(), new ItemStack(Items.WHEAT, 8));
            worker.updateInventory(level.registryAccess(), items);
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
            SettlementBackgroundFarming.tick(level, fixture.town(), worker, 200, 1.0D);
            helper.assertValueEqual(carried(helper, fixture, Items.WHEAT), 9, "a partial batch keeps harvesting instead of taking another round trip");
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.WHEAT)), 0, "ongoing field work does not deliver every crop separately");
            items = worker.items(level.registryAccess());
            for (int slot : worker.outputSlots()) items.set(slot, ItemStack.EMPTY);
            items.set(worker.outputSlots().getFirst(), new ItemStack(Items.WHEAT, 64));
            worker.updateInventory(level.registryAccess(), items);
            worker.spend(worker.progress());
            SettlementBackgroundFarming.tick(level, fixture.town(), worker, 10, 1.0D);
            helper.assertValueEqual(worker.status(), "hauling", "a full stack starts a real paid delivery");
            var restored = SettlementWorkerState.load(worker.save());
            SettlementBackgroundFarming.tick(level, fixture.town(), restored, 200, 1.0D);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.WHEAT)), 64, "the saved delivery transfers the original batch");
            helper.assertValueEqual(restored.items(level.registryAccess()).stream().filter(stack -> stack.is(Items.WHEAT))
                    .mapToInt(ItemStack::getCount).sum(), 0, "delivery preserves the item count across saving");
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
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            var items = fixture.worker().items(level.registryAccess());
            for (int slot : fixture.worker().outputSlots()) items.set(slot, new ItemStack(Items.STONE, 64));
            for (int slot : fixture.worker().supplySlots()) items.set(slot, new ItemStack(Items.STONE, 64));
            fixture.worker().updateInventory(level.registryAccess(), items);
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertTrue(((CropBlock) fixture.plot().crop().getBlock()).isMaxAge(fixture.plot().crop()), "a full worker leaves the real mature crop unharvested");
            helper.assertValueEqual(fixture.worker().status(), "output_full", "the worker reports finite inventory capacity");
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), 0.0D, "an uncompleted harvest grants no practice");
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
            helper.assertTrue(fixture.villager().getMainHandItem().isEmpty(),
                    "the broken background tool cannot remain in the native hand");
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
            fixture.output().configure(SettlementDepotPurpose.GENERAL, 9);
            fixture.output().setItem(0, new ItemStack(Items.BREAD, 64));
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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void cropPlansRetainStableRowsAndSurviveStorageAndPackets(GameTestHelper helper) {
        var mix = new SettlementCropPlan(List.of(50, 25, 25, 0, 0, 0));
        var min = new BlockPos(-12, 64, -8);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Mixed farm", min, min.offset(3, 1, 3), 5, true, mix);
        var counts = new java.util.EnumMap<HiredFarmingOptions.Crop, Integer>(HiredFarmingOptions.Crop.class);
        for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) {
            var crop = mix.crop(area, min.offset(x, 0, z));
            counts.merge(crop, 1, Integer::sum);
            helper.assertValueEqual(crop, mix.crop(area, min.offset(x, 1, z)), "height does not scramble crop rows");
        }
        helper.assertValueEqual(counts.get(HiredFarmingOptions.Crop.WHEAT), 8, "half of the plots grow wheat");
        helper.assertValueEqual(counts.get(HiredFarmingOptions.Crop.CARROT), 4, "one quarter grows carrots");
        helper.assertValueEqual(counts.get(HiredFarmingOptions.Crop.POTATO), 4, "one quarter grows potatoes");
        helper.assertValueEqual(SettlementArea.load(area.save()), area, "area saves retain crop percentages");
        var legacy = area.save();
        legacy.remove("Crops");
        helper.assertFalse(SettlementArea.load(legacy).crops().enabled(), "old farms do not acquire a plan");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            mix.write(buffer);
            helper.assertValueEqual(SettlementCropPlan.read(buffer), mix, "network fields retain the exact crop plan");
        } finally {
            buffer.release();
        }
        boolean rejected = false;
        try {
            new SettlementCropPlan(List.of(50, 25, 24, 0, 0, 0));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "incomplete mixes are rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void cropPlanChangesOnlyMatureOrEmptyPlotsAndHonorsRestrictions(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var area = fixture.area();
            var plan = new SettlementCropPlan(List.of(0, 0, 100, 0, 0, 0));
            var configured = new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(), area.priority(), true, plan);
            helper.assertFalse(fixture.town().putArea(UUID.randomUUID(), configured), "unauthorized players cannot change planting");
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), configured), "the owner can set a crop plan");
            helper.assertValueEqual(SettlementPlanting.crop(fixture.villager(), fixture.plot().position()), HiredFarmingOptions.Crop.POTATO,
                    "loaded workers resolve the shared area plan");
            helper.assertFalse(com.jvn.villagerretaliation.interaction.work.HiredFarmingInventoryBridge.permitsVanillaHarvest(helper.getLevel(), fixture.villager()),
                    "vanilla planting cannot bypass the area plan");
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            items.set(fixture.worker().supplySlots().getFirst(), new ItemStack(Items.POTATO, 2));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            fixture.plot().change(fixture.plot().soil(), Blocks.WHEAT.defaultBlockState());
            SettlementBackgroundFarming.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.plot().crop().is(Blocks.WHEAT), "growing crops remain unchanged");
            fixture.plot().change(fixture.plot().soil(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
            SettlementBackgroundFarming.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.plot().crop().is(Blocks.POTATOES), "harvesting plants the selected crop instead of the first loot seed");
            helper.assertTrue(fixture.worker().diagnostic().contains("potatoes") && fixture.worker().diagnostic().contains("crop mix"),
                    "settlement status explains the chosen crop and mix");
            fixture.plot().change(fixture.plot().soil(), Blocks.AIR.defaultBlockState());
            fixture.worker().settings().putString(HiredFarmingOptions.CROP_TAG, "WHEAT");
            SettlementBackgroundFarming.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.plot().crop().isAir(), "worker restrictions remain authoritative");
            helper.assertValueEqual(carried(helper, fixture, Items.POTATO), 1, "blocked planting consumes no seed");
            helper.assertValueEqual(fixture.worker().status(), "planting_restricted", "conflicting settings have a distinct recovery status");
            helper.assertTrue(fixture.worker().diagnostic().contains("worker") && fixture.worker().diagnostic().contains("potatoes"),
                    "the conflict identifies the crop and setting to change");
            helper.assertValueEqual(SettlementWorkerState.load(fixture.worker().save()).diagnostic(), fixture.worker().diagnostic(),
                    "planting details survive background handoff saves");
            fixture.worker().settings().putString(HiredFarmingOptions.CROP_TAG, "ANY");
            SettlementBackgroundFarming.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.plot().crop().is(Blocks.POTATOES), "resolving the restriction resumes the same planned crop");
            fixture.plot().change(fixture.plot().soil(), Blocks.AIR.defaultBlockState());
            SettlementBackgroundFarming.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(fixture.worker().status(), "missing_planting_item", "exhausted planting stock is reported separately");
            helper.assertTrue(fixture.worker().diagnostic().contains("potatoes") && fixture.worker().diagnostic().contains("filters"),
                    "missing stock points to matching supplies and storage rules");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void plantingReasonsReachLoadedWorkerAndSettlementViews(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(fixture.villager());
            var context = com.jvn.villagerretaliation.interaction.HiredWorkSession.active(helper.getLevel(), fixture.villager());
            for (var reason : List.of("mix", "cooking", "feed", "food")) {
                SettlementPlanting.report(state, new SettlementPlanting.Decision(HiredFarmingOptions.Crop.CARROT, reason), "");
                state.putString("Status", SettlementPlanting.message(state));
                state.putString("WorkerTaskState", "idle");
                var work = SettlementWorkStatus.observe(helper.getLevel(), fixture.villager());
                helper.assertValueEqual(work.status(), "planting", "planting reasons remain ordinary work");
                helper.assertTrue(work.diagnostic().contains("carrots"), "the settlement view names the selected crop");
                var brain = com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(state, helper.getLevel().getGameTime());
                var facts = com.jvn.villagerretaliation.interaction.HiredWorkerFacts.observe(context, brain);
                helper.assertValueEqual(com.jvn.villagerretaliation.interaction.PersonalWorkerDiagnostics.describe(context, brain, facts),
                        work.diagnostic(), "worker and settlement views share the planting explanation");
                helper.assertValueEqual(SettlementPlanting.replacements(state).get("crop"), "carrots", "dialogue receives the selected crop");
            }
            SettlementPlanting.report(state, new SettlementPlanting.Decision(HiredFarmingOptions.Crop.CARROT, "food"), "missing");
            state.putString("Status", SettlementPlanting.message(state));
            state.putString("WorkerTaskState", "waiting_for_materials");
            helper.assertValueEqual(SettlementWorkStatus.observe(helper.getLevel(), fixture.villager()).status(), "missing_planting_item",
                    "missing planting supplies become an actionable settlement issue");
            state.putString("Status", "interaction.work.status.sleeping");
            helper.assertValueEqual(SettlementWorkStatus.observe(helper.getLevel(), fixture.villager()).diagnostic(), "",
                    "resting does not retain an obsolete planting complaint");
        });
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


    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void successfulBackgroundPracticeUpdatesWorkerAptitude(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            var primary = com.jvn.villagerretaliation.interaction.HiredVillagerRoles.primarySkill(fixture.worker().role());
            profile.setSkillPracticeXp(primary,
                    com.jvn.villagerretaliation.skill.VillagerSkillProgressionService.requiredXp(40) - 0.000_001D,
                    helper.getLevel().getGameTime());
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(profile.skills().get(primary) > 40, "completed farming can cross a real skill threshold");
            helper.assertValueEqual(fixture.worker().aptitude(),
                    com.jvn.villagerretaliation.interaction.HiredVillagerRoles.roleScore(profile.skills(), fixture.worker().role()),
                    "the background worker uses its newly earned aptitude");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void legacyWorkPracticeMigratesBeforeWorkerSnapshotAndCannotReplayOnReturn(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(fixture.villager());
            var legacy = new net.minecraft.nbt.CompoundTag();
            legacy.putDouble("farming", 0.5D);
            state.put("HiredWorkSkillGrowthProgress", legacy);
            var duty = SettlementWorkforceService.duty(fixture.villager()).orElseThrow();
            fixture.worker().observe(level, fixture.villager(), duty, 0L);
            helper.assertFalse(state.contains("HiredWorkSkillGrowthProgress"), "capture removes migrated legacy state from the real worker");
            helper.assertFalse(fixture.worker().settings().contains("HiredWorkSkillGrowthProgress"), "the snapshot cannot repeat legacy migration");
            double practice = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(practice > 0.0D, "capture preserves legacy fractional practice");
            fixture.worker().restore(fixture.villager());
            com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService.onPractice(
                    level, fixture.villager(), null, fixture.worker().role(), state, List.of());
            fixture.worker().observe(level, fixture.villager(), duty, 200L);
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), practice, "return and recapture cannot migrate the same practice twice");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void fieldSurveyFindsFruitCocoaAndOnlyTheLowerPitcherHalf(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            BlockPos center = fixture.plot().position();
            var area = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Mixed field",
                    center, center.offset(2, 16, 0), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), area), "the mixed field fits its existing assignment");
            extendedPlot(helper, fixture, center, Blocks.FARMLAND.defaultBlockState(), Blocks.TORCHFLOWER.defaultBlockState(), Blocks.AIR.defaultBlockState());
            var pitcher = pitcher(4);
            extendedPlot(helper, fixture, center.east(), Blocks.FARMLAND.defaultBlockState(), pitcher,
                    pitcher.setValue(net.minecraft.world.level.block.PitcherCropBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            extendedPlot(helper, fixture, center.east(2), Blocks.STONE.defaultBlockState(), Blocks.MELON.defaultBlockState(), Blocks.AIR.defaultBlockState());
            BlockPos cocoa = center.atY(79);
            setBlock(fixture.chunk(), cocoa.above(), Blocks.COCOA.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CocoaBlock.FACING, net.minecraft.core.Direction.EAST));
            setBlock(fixture.chunk(), cocoa.above().east(), Blocks.JUNGLE_LOG.defaultBlockState());
            SettlementFieldService.observeArea(level, fixture.town(), area, fixture.chunk());
            helper.assertTrue(fixture.town().fields().get(center).harvestable(), "mature torchflowers remain harvestable");
            helper.assertTrue(fixture.town().fields().get(center.east()).harvestable(), "a complete pitcher plant is one harvestable plot");
            helper.assertFalse(fixture.town().fields().containsKey(center.east().above()), "the pitcher upper half is not a second crop");
            helper.assertTrue(fixture.town().fields().get(center.east(2)).harvestable(), "placed fruit can be harvested from non-farm soil");
            helper.assertTrue(fixture.town().fields().containsKey(cocoa), "cocoa above an air block is discovered across a section boundary");
            helper.assertFalse(level.hasChunkAt(center), "mixed field observation never loads the detached chunk");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void backgroundFarmerPlantsGrowsAndHarvestsTorchflowers(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var plot = extendedPlot(helper, fixture, fixture.plot().position(), fixture.plot().soil(),
                    Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
            fixture.supply().setItem(0, new ItemStack(Items.TORCHFLOWER_SEEDS));
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertTrue(plot.crop().is(Blocks.TORCHFLOWER_CROP), "a real depot seed plants a torchflower crop");
            helper.assertTrue(fixture.supply().getItem(0).isEmpty(), "planting consumes the finite seed");
            fastGrowth(helper, () -> SettlementFieldService.update(level, fixture.town(), 400L));
            helper.assertTrue(plot.crop().is(Blocks.TORCHFLOWER), "growth transforms the crop into its mature flower block");
            SettlementBackgroundService.update(level, fixture.town(), 600L);
            helper.assertValueEqual(carried(helper, fixture, Items.TORCHFLOWER), 1, "the mature flower enters job output");
            helper.assertTrue(plot.crop().isAir(), "harvesting does not invent replacement seeds");
            helper.assertFalse(level.hasChunkAt(plot.position()), "torchflower production keeps terrain unloaded");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void backgroundPitcherPlantsGrowAndClearBothHalvesThroughSavedReceipts(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var plot = extendedPlot(helper, fixture, fixture.plot().position(), fixture.plot().soil(),
                    Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
            fixture.supply().setItem(0, new ItemStack(Items.PITCHER_POD));
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertTrue(plot.crop().is(Blocks.PITCHER_CROP), "a supplied pitcher pod creates the lower crop");
            fastGrowth(helper, () -> SettlementFieldService.update(level, fixture.town(), 400L));
            helper.assertTrue(plot.harvestable(), "growth produces matching mature lower and upper halves");
            helper.assertTrue(plot.reconcile(fixture.chunk()), "both grown halves reach the native chunk together");
            helper.assertTrue(fixture.chunk().getBlockState(plot.position().above(2)).is(Blocks.PITCHER_CROP),
                    "the native chunk receives the upper half");
            SettlementBackgroundService.update(level, fixture.town(), 600L);
            helper.assertValueEqual(carried(helper, fixture, Items.PITCHER_PLANT), 1, "one complete crop yields one pitcher plant");
            helper.assertTrue(plot.crop().isAir() && plot.top().isAir(), "harvesting clears both saved halves");
            var saved = plot.save();
            helper.assertTrue(SettlementFieldPlot.load(saved).reconcile(fixture.chunk()), "saved harvesting clears both native halves");
            helper.assertTrue(fixture.chunk().getBlockState(plot.position().above()).isAir()
                            && fixture.chunk().getBlockState(plot.position().above(2)).isAir(), "no orphaned upper half remains");
            setBlock(fixture.chunk(), plot.position().above(), pitcher(4));
            setBlock(fixture.chunk(), plot.position().above(2), pitcher(4).setValue(
                    net.minecraft.world.level.block.PitcherCropBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            helper.assertTrue(SettlementFieldPlot.load(saved).reconcile(fixture.chunk()), "an applied harvest receipt is recognized after reload");
            helper.assertTrue(fixture.chunk().getBlockState(plot.position().above(2)).is(Blocks.PITCHER_CROP),
                    "an old harvest cannot erase a later identical plant");
            helper.assertFalse(level.hasChunkAt(plot.position()), "growing and reconciling pitcher halves never load the field");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void pitcherGrowthRespectsBlockedAndUnobservedUpperSpace(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var plot = extendedPlot(helper, fixture, fixture.plot().position(), fixture.plot().soil(),
                    pitcher(0), Blocks.STONE.defaultBlockState());
            fastGrowth(helper, () -> plot.advance(helper.getLevel(), fixture.town(), 200L));
            helper.assertValueEqual(SettlementFarmPlants.age(plot.crop()), 2, "a low ceiling stops growth before the double-height stage");
            helper.assertTrue(plot.top().is(Blocks.STONE), "growth preserves the blocking upper block");
            helper.assertTrue(plot.reconcile(fixture.chunk()), "the lower growth can still be applied safely");
            helper.assertTrue(fixture.chunk().getBlockState(plot.position().above(2)).is(Blocks.STONE), "reconciliation preserves the ceiling");
            var legacy = plot.save();
            legacy.remove("Top");
            legacy.remove("BaselineTop");
            var unknown = SettlementFieldPlot.load(legacy);
            fastGrowth(helper, () -> unknown.advance(helper.getLevel(), fixture.town(), 400L));
            helper.assertValueEqual(SettlementFarmPlants.age(unknown.crop()), 2, "older snapshots cannot invent clear upper space");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void changedPitcherUpperSpaceRejectsTheWholePendingGrowth(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var plot = extendedPlot(helper, fixture, fixture.plot().position(), fixture.plot().soil(), pitcher(2), Blocks.AIR.defaultBlockState());
            fastGrowth(helper, () -> plot.advance(helper.getLevel(), fixture.town(), 200L));
            var saved = SettlementFieldPlot.load(plot.save());
            setBlock(fixture.chunk(), plot.position().above(2), Blocks.DIAMOND_BLOCK.defaultBlockState());
            helper.assertFalse(saved.reconcile(fixture.chunk()), "a changed upper block rejects pending double-height growth");
            helper.assertTrue(saved.conflict(), "the rejected growth remains recoverable");
            helper.assertValueEqual(SettlementFarmPlants.age(fixture.chunk().getBlockState(plot.position().above())), 2,
                    "a failed upper check cannot partially apply the lower half");
            helper.assertTrue(fixture.chunk().getBlockState(plot.position().above(2)).is(Blocks.DIAMOND_BLOCK),
                    "the player's upper block remains intact");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void fullFarmerInventoryPreservesBothMaturePitcherHalves(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var lower = pitcher(4);
            var plot = extendedPlot(helper, fixture, fixture.plot().position(), fixture.plot().soil(), lower,
                    lower.setValue(net.minecraft.world.level.block.PitcherCropBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            for (int slot : fixture.worker().outputSlots()) items.set(slot, new ItemStack(Items.STONE, 64));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.worker().status(), "output_full", "finite job storage blocks harvesting");
            helper.assertTrue(plot.harvestable() && !plot.pending(), "a blocked harvest leaves both halves untouched");
            helper.assertValueEqual(carried(helper, fixture, Items.PITCHER_PLANT), 0, "a blocked harvest yields no duplicate item");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void unloadedFruitFarmsKeepTheirStemsAndProduceRepeatedHarvests(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            BlockPos center = fixture.plot().position();
            var area = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Fruit field", center, center.east().above(), 5, true);
            fixture.town().putArea(fixture.town().leader(), area);
            var stem = extendedPlot(helper, fixture, center, fixture.plot().soil(),
                    Blocks.PUMPKIN_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.StemBlock.AGE, 7), Blocks.AIR.defaultBlockState());
            var fruit = extendedPlot(helper, fixture, center.east(), Blocks.DIRT.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
            fixture.worker().settings().putBoolean(HiredFarmingOptions.TILL_SOIL_TAG, false);
            fastGrowth(helper, () -> run(helper, fixture, 200, 2_000));
            int harvested = fixture.output().count(stack -> stack.is(Items.PUMPKIN)) + carried(helper, fixture, Items.PUMPKIN);
            helper.assertTrue(harvested >= 2, "the same living stem supplies repeated finite harvests");
            for (int tick = 0; tick < 10; tick++) SettlementBackgroundFarming.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.output().count(stack -> stack.is(Items.PUMPKIN)) >= harvested,
                    "the farmer delivers the partial batch when no further fruit has grown");
            helper.assertValueEqual(carried(helper, fixture, Items.PUMPKIN), 0, "delivery clears the harvested batch without losing fruit");
            helper.assertTrue(SettlementFarmPlants.stem(stem.crop()) || SettlementFarmPlants.attachedStem(stem.crop()),
                    "fruit harvesting never removes its source stem");
            helper.assertTrue(stem.reconcile(fixture.chunk()) && fruit.reconcile(fixture.chunk()), "stem and fruit changes both reach their original chunk");
            helper.assertValueEqual(fixture.chunk().getBlockState(center.above()), stem.crop(), "the physical stem matches the ledger");
            helper.assertValueEqual(fixture.chunk().getBlockState(center.east().above()), fruit.crop(), "the physical fruit matches the ledger");
            helper.assertFalse(level.hasChunkAt(center), "repeated fruit cycles do not load the farm");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void melonHarvestUsesTheRealHoeAndDetachesItsStem(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            BlockPos center = fixture.plot().position();
            fixture.town().putArea(fixture.town().leader(), new SettlementArea(fixture.area().id(), SettlementAreaType.FARM,
                    "Melon field", center, center.east().above(), 5, true));
            var stem = extendedPlot(helper, fixture, center, fixture.plot().soil(),
                    Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.AttachedStemBlock.FACING,
                            net.minecraft.core.Direction.EAST), Blocks.AIR.defaultBlockState());
            var fruit = extendedPlot(helper, fixture, center.east(), Blocks.DIRT.defaultBlockState(),
                    Blocks.MELON.defaultBlockState(), Blocks.AIR.defaultBlockState());
            var items = fixture.worker().items(level.registryAccess());
            items.get(HiredJobInventory.MAINHAND_SLOT).enchant(level.registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
            fixture.worker().updateInventory(level.registryAccess(), items);
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertValueEqual(carried(helper, fixture, Items.MELON), 1, "Silk Touch uses the native melon loot table");
            helper.assertValueEqual(carried(helper, fixture, Items.MELON_SLICE), 0, "the enchanted harvest does not also produce slices");
            helper.assertTrue(stem.crop().is(Blocks.MELON_STEM) && SettlementFarmPlants.age(stem.crop()) == 7,
                    "removing the fruit returns its attached stem to the mature growing state");
            helper.assertTrue(fruit.crop().isAir(), "one harvested fruit becomes an empty site");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void stemsCannotCreateFruitInUnobservedOrOccupiedSites(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            BlockPos center = fixture.plot().position();
            var stem = extendedPlot(helper, fixture, center, fixture.plot().soil(),
                    Blocks.MELON_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.StemBlock.AGE, 7), Blocks.AIR.defaultBlockState());
            fastGrowth(helper, () -> stem.advance(level, fixture.town(), 200L));
            helper.assertTrue(stem.crop().is(Blocks.MELON_STEM), "unrecorded adjacent space cannot produce fruit");
            fixture.town().putArea(fixture.town().leader(), new SettlementArea(fixture.area().id(), SettlementAreaType.FARM,
                    "Blocked field", center, center.east().above(), 5, true));
            var occupied = extendedPlot(helper, fixture, center.east(), Blocks.DIRT.defaultBlockState(),
                    Blocks.MELON.defaultBlockState(), Blocks.AIR.defaultBlockState());
            fastGrowth(helper, () -> stem.advance(level, fixture.town(), 400L));
            helper.assertTrue(stem.crop().is(Blocks.MELON_STEM), "an occupied target cannot accept another fruit");
            helper.assertTrue(occupied.crop().is(Blocks.MELON) && !occupied.pending(), "the existing fruit remains unchanged");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void backgroundCocoaNeedsItsObservedJungleSupportAndYieldsRealBeans(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            BlockPos center = fixture.plot().position();
            setBlock(fixture.chunk(), center.above().east(), Blocks.JUNGLE_LOG.defaultBlockState());
            var cocoa = Blocks.COCOA.defaultBlockState().setValue(net.minecraft.world.level.block.CocoaBlock.FACING, net.minecraft.core.Direction.EAST);
            var plot = extendedPlot(helper, fixture, center, Blocks.AIR.defaultBlockState(), cocoa, Blocks.AIR.defaultBlockState());
            fastGrowth(helper, () -> plot.advance(level, fixture.town(), 200L));
            helper.assertTrue(plot.harvestable(), "supported cocoa matures without farmland or light requirements");
            setBlock(fixture.chunk(), center.above().east(), Blocks.AIR.defaultBlockState());
            plot.observeEnvironment(level, fixture.chunk());
            helper.assertFalse(plot.harvestable(), "removing the observed jungle support blocks the stale crop");
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertValueEqual(carried(helper, fixture, Items.COCOA_BEANS), 0, "unsupported cocoa cannot generate beans");
            setBlock(fixture.chunk(), center.above().east(), Blocks.JUNGLE_LOG.defaultBlockState());
            plot.observeEnvironment(level, fixture.chunk());
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            helper.assertValueEqual(carried(helper, fixture, Items.COCOA_BEANS), 3, "mature cocoa uses its real block loot");
            helper.assertTrue(plot.crop().isAir(), "harvesting does not invent a replacement cocoa pod");
            helper.assertFalse(level.hasChunkAt(center), "cocoa work does not load its supporting terrain");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void cocoaSurveyIncludesAnOtherwiseEmptySectionBelowItsPod(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var original = fixture.plot().position();
            setBlock(fixture.chunk(), original, Blocks.STONE.defaultBlockState());
            setBlock(fixture.chunk(), original.above(), Blocks.AIR.defaultBlockState());
            var soil = original.atY(79);
            var area = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Cocoa",
                    soil, soil.above(), 5, true);
            fixture.town().putArea(fixture.town().leader(), area);
            setBlock(fixture.chunk(), soil.above(), Blocks.COCOA.defaultBlockState());
            SettlementFieldService.observeArea(level, fixture.town(), area, fixture.chunk());
            helper.assertTrue(fixture.town().fields().containsKey(soil),
                    "the section above is checked even when the soil section contains no farm blocks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void fruitAndStemReceiptsSurviveDifferentChunkReturnOrders(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var first = fixture.chunk();
            var second = new LevelChunk(level, new ChunkPos(first.getPos().x + 1, first.getPos().z));
            BlockPos stemPos = new BlockPos(first.getPos().getMaxBlockX(), 64, first.getPos().getMinBlockZ() + 11);
            BlockPos fruitPos = stemPos.east();
            var area = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Boundary fruit",
                    stemPos, fruitPos.above(), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), area), "the cross-chunk field fits its assignment");
            fixture.town().removeField(fixture.plot().position());
            setBlock(first, stemPos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            setBlock(first, stemPos.above(), Blocks.PUMPKIN_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.StemBlock.AGE, 7));
            setBlock(second, fruitPos, Blocks.DIRT.defaultBlockState());
            var stem = boundaryPlot(helper, fixture, stemPos, first, second);
            var fruit = boundaryPlot(helper, fixture, fruitPos, first, second);
            fastGrowth(helper, () -> SettlementFieldService.update(level, fixture.town(), 200L));
            helper.assertTrue(stem.crop().is(Blocks.ATTACHED_PUMPKIN_STEM) && fruit.crop().is(Blocks.PUMPKIN),
                    "one observed stem creates one adjacent fruit across the boundary");
            var grownStem = stem.save();
            var grownFruit = fruit.save();
            helper.assertTrue(fruit.reconcile(second), "the fruit chunk can return first");
            helper.assertTrue(stem.reconcile(first), "the stem chunk can return later");
            SettlementBackgroundFarming.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(carried(helper, fixture, Items.PUMPKIN), 1, "the cross-chunk fruit produces one real harvest");
            helper.assertTrue(fruit.crop().isAir() && stem.crop().is(Blocks.PUMPKIN_STEM), "harvesting retires the fruit and detaches the stem");
            helper.assertTrue(stem.reconcile(first), "the stem can apply its next revision first");
            helper.assertTrue(fruit.reconcile(second), "the fruit can apply its next revision later");
            helper.assertTrue(SettlementFieldPlot.load(grownStem).reconcile(first)
                    && SettlementFieldPlot.load(grownFruit).reconcile(second), "older growth receipts recognize the later applied harvest");
            helper.assertTrue(first.getBlockState(stemPos.above()).is(Blocks.PUMPKIN_STEM)
                    && second.getBlockState(fruitPos.above()).isAir(), "stale growth cannot resurrect the harvested fruit or attachment");
            helper.assertFalse(level.hasChunkAt(stemPos) || level.hasChunkAt(fruitPos), "neither transaction direction loads a chunk");
        });
    }

    private static SettlementFieldPlot boundaryPlot(GameTestHelper helper, Fixture fixture, BlockPos position,
                                                       LevelChunk first, LevelChunk second) {
        var own = new ChunkPos(position).equals(first.getPos()) ? first : second;
        var plot = SettlementFieldPlot.capture(helper.getLevel(), fixture.area().id(), position, own, 0L);
        var saved = plot.save();
        var environment = new net.minecraft.nbt.ListTag();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                var sample = position.offset(x, 0, z);
                var chunk = new ChunkPos(sample).equals(first.getPos()) ? first : second;
                var entry = new net.minecraft.nbt.CompoundTag();
                entry.put("Soil", net.minecraft.nbt.NbtUtils.writeBlockState(chunk.getBlockState(sample)));
                entry.put("Crop", net.minecraft.nbt.NbtUtils.writeBlockState(chunk.getBlockState(sample.above())));
                environment.add(entry);
            }
        }
        saved.put("Environment", environment);
        saved.putBoolean("EnvironmentKnown", true);
        saved.putInt("BlockLight", 15);
        plot = SettlementFieldPlot.load(saved);
        fixture.town().recordField(plot);
        return plot;
    }


    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void farmerHarvestsAPenObservedPlotAfterItsOriginalDesignationIsRemoved(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var pen = sharedPen(helper, fixture);
            var saved = fixture.plot().save();
            saved.putUUID("Area", pen.id());
            var plot = SettlementFieldPlot.load(saved);
            fixture.town().recordField(plot);
            helper.assertTrue(fixture.town().removeArea(fixture.town().leader(), pen.id()), "the original pen can be removed");
            var town = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            var worker = town.workers().get(fixture.worker().id());
            worker.settings().putBoolean("AutoDepositOutputs", false);
            SettlementFieldService.update(level, town, 200L);
            SettlementBackgroundFarming.tick(level, town, worker, 200, 1.0D);
            helper.assertValueEqual(worker.items(level.registryAccess()).stream().filter(stack -> stack.is(Items.WHEAT))
                    .mapToInt(ItemStack::getCount).sum(), 1, "the current farm receives the single real harvest");
            var remaining = town.fields().get(plot.position());
            helper.assertTrue(remaining != null && !remaining.harvestable(), "one physical plot keeps the replanted crop");
            helper.assertValueEqual(remaining.save().getUUID("Id"), saved.getUUID("Id"), "reassignment preserves the plot receipt identity");
            SettlementBackgroundFarming.tick(level, town, worker, 200, 1.0D);
            helper.assertValueEqual(worker.items(level.registryAccess()).stream().filter(stack -> stack.is(Items.WHEAT))
                    .mapToInt(ItemStack::getCount).sum(), 1, "the old pen observation cannot supply a second harvest");
            helper.assertFalse(level.hasChunkAt(plot.position()), "the shared field remains unloaded");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void sharedFieldGrowthUsesOneClockThroughSaveAndRepeatedUpdates(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.plot().change(fixture.plot().soil(), Blocks.WHEAT.defaultBlockState());
            var pen = sharedPen(helper, fixture);
            var saved = fixture.town().save(level.registryAccess());
            var shared = SettlementRecord.load(saved, level.registryAccess(), () -> {});
            var single = SettlementRecord.load(saved, level.registryAccess(), () -> {});
            single.removeArea(single.leader(), pen.id());
            SettlementFieldService.update(level, shared, 200L);
            SettlementFieldService.update(level, single, 200L);
            var plot = shared.fields().get(fixture.plot().position());
            helper.assertValueEqual(plot.save(), single.fields().get(plot.position()).save(),
                    "overlapping designations do not multiply crop growth or soil drying");
            var checkpoint = plot.save();
            SettlementFieldService.update(level, shared, 200L);
            helper.assertValueEqual(plot.save(), checkpoint, "a repeated checkpoint cannot replay shared growth");
            var restored = SettlementRecord.load(shared.save(level.registryAccess()), level.registryAccess(), () -> {});
            SettlementFieldService.update(level, restored, 400L);
            SettlementFieldService.update(level, single, 400L);
            helper.assertValueEqual(restored.fields().get(plot.position()).save(), single.fields().get(plot.position()).save(),
                    "saving retains the single fractional growth clock");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void sharedPenCannotAuthorizeADisabledOrMovedFarm(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            sharedPen(helper, fixture);
            var area = fixture.area();
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            fixture.town().putArea(fixture.town().leader(), new SettlementArea(area.id(), area.type(), area.name(),
                    area.min(), area.max(), area.priority(), false));
            SettlementBackgroundFarming.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.plot().harvestable(), "an enabled pen cannot enable a disabled farmer");
            fixture.town().putArea(fixture.town().leader(), new SettlementArea(area.id(), area.type(), area.name(),
                    area.min().south(3), area.max().south(3), area.priority(), true));
            SettlementFieldService.update(level, fixture.town(), 200L);
            SettlementBackgroundFarming.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.town().fields().get(fixture.plot().position()) == fixture.plot(),
                    "the current pen retains the observed ground outside the moved farm");
            helper.assertTrue(fixture.plot().harvestable() && carried(helper, fixture, Items.WHEAT) == 0,
                    "old farm provenance cannot authorize harvesting beyond its current bounds");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void fruitGrowsAndIsHarvestedAcrossDifferentPlotOrigins(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var center = fixture.plot().position();
            fixture.town().putArea(fixture.town().leader(), new SettlementArea(fixture.area().id(), SettlementAreaType.FARM,
                    "Shared fruit", center, center.east().above(), 5, true));
            var pen = sharedPen(helper, fixture);
            var stem = extendedPlot(helper, fixture, center, fixture.plot().soil(),
                    Blocks.PUMPKIN_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.StemBlock.AGE, 7),
                    Blocks.AIR.defaultBlockState());
            var fruit = extendedPlot(helper, fixture, center.east(), Blocks.DIRT.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
            var saved = fruit.save();
            saved.putUUID("Area", pen.id());
            fruit = SettlementFieldPlot.load(saved);
            fixture.town().recordField(fruit);
            fastGrowth(helper, () -> stem.advance(level, fixture.town(), 200L));
            helper.assertTrue(fruit.crop().is(Blocks.PUMPKIN) && stem.crop().is(Blocks.ATTACHED_PUMPKIN_STEM),
                    "the current farm joins the stem and its pen-observed fruit site");
            var oldStem = stem.save();
            var oldFruit = fruit.save();
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            SettlementBackgroundFarming.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(carried(helper, fixture, Items.PUMPKIN), 1, "the shared fruit yields one real pumpkin");
            helper.assertTrue(stem.crop().is(Blocks.PUMPKIN_STEM) && fruit.crop().isAir(), "harvesting detaches the same shared stem");
            helper.assertTrue(stem.reconcile(fixture.chunk()) && fruit.reconcile(fixture.chunk()), "both physical changes reconcile");
            helper.assertTrue(SettlementFieldPlot.load(oldStem).reconcile(fixture.chunk())
                    && SettlementFieldPlot.load(oldFruit).reconcile(fixture.chunk()), "old growth receipts are retired");
            helper.assertTrue(fixture.chunk().getBlockState(center.east().above()).isAir(),
                    "a stale pen observation cannot resurrect the harvested fruit");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void grazingAndFarmingConsumeTheSameGroundAcrossNativeReturn(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var pen = sharedPen(helper, fixture);
            var plot = extendedPlot(helper, fixture, fixture.plot().position(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
            var sheep = EntityType.SHEEP.create(level);
            sheep.setPos(plot.position().above().getBottomCenter());
            sheep.setSheared(true);
            var animal = SettlementLivestock.capture(sheep, pen.id(), 0L);
            fixture.town().recordLivestock(animal);
            for (int now = 200; now <= 2_200; now += 200) SettlementLivestockService.update(level, fixture.town(), now);
            helper.assertTrue(plot.soil().is(Blocks.DIRT), "grazing consumes the farm-observed grass");
            helper.assertFalse(((net.minecraft.world.entity.animal.Sheep) animal.context(level)).isSheared(),
                    "the finite grass meal restores one fleece");
            var grazed = plot.save();
            fixture.worker().settings().putBoolean(HiredFarmingOptions.TILL_SOIL_TAG, true);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            var items = fixture.worker().items(level.registryAccess());
            items.set(fixture.worker().supplySlots().getFirst(), new ItemStack(Items.WHEAT_SEEDS));
            fixture.worker().updateInventory(level.registryAccess(), items);
            SettlementBackgroundFarming.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(plot.soil().is(Blocks.FARMLAND), "the farmer tills the dirt left by that sheep");
            SettlementBackgroundFarming.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(plot.crop().is(Blocks.WHEAT), "one real seed is planted on the shared ground");
            helper.assertValueEqual(carried(helper, fixture, Items.WHEAT_SEEDS), 0, "the planted seed leaves the supply inventory");
            animal.shear();
            for (int now = 2_400; now <= 4_800; now += 200) SettlementLivestockService.update(level, fixture.town(), now);
            helper.assertTrue(((net.minecraft.world.entity.animal.Sheep) animal.context(level)).isSheared(),
                    "the tilled crop cannot also provide pasture output");
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertTrue(restored.fields().get(plot.position()).reconcile(fixture.chunk()), "the combined terrain history reaches the native chunk");
            helper.assertTrue(SettlementFieldPlot.load(grazed).reconcile(fixture.chunk()), "the older grazing receipt recognizes the later planting");
            helper.assertTrue(fixture.chunk().getBlockState(plot.position()).is(Blocks.FARMLAND)
                    && fixture.chunk().getBlockState(plot.position().above()).is(Blocks.WHEAT),
                    "replaying grazing cannot erase the paid farming work");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_farming")
    public static void sharedGrassGroundPreservesSupportedCocoaGrowth(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var center = fixture.plot().position();
            sharedPen(helper, fixture);
            setBlock(fixture.chunk(), center.above().east(), Blocks.JUNGLE_LOG.defaultBlockState());
            var cocoa = Blocks.COCOA.defaultBlockState().setValue(net.minecraft.world.level.block.CocoaBlock.FACING,
                    net.minecraft.core.Direction.EAST);
            var plot = extendedPlot(helper, fixture, center, Blocks.GRASS_BLOCK.defaultBlockState(), cocoa,
                    Blocks.AIR.defaultBlockState());
            fastGrowth(helper, () -> plot.advance(helper.getLevel(), fixture.town(), 200L));
            helper.assertTrue(plot.harvestable(), "grass below supported cocoa cannot divert its crop growth into pasture growth");
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            SettlementBackgroundFarming.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(carried(helper, fixture, Items.COCOA_BEANS), 3, "the shared crop keeps its native mature yield");
        });
    }

    private static SettlementArea sharedPen(GameTestHelper helper, Fixture fixture) {
        var farm = fixture.town().areas().get(fixture.area().id());
        var pen = new SettlementArea(UUID.randomUUID(), SettlementAreaType.ANIMAL_PEN, "Shared pen",
                farm.min(), farm.max(), 5, true);
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), pen), "farm and pen can share physical ground");
        return pen;
    }

    private static BlockState pitcher(int age) {
        return Blocks.PITCHER_CROP.defaultBlockState().setValue(net.minecraft.world.level.block.PitcherCropBlock.AGE, age);
    }

    private static SettlementFieldPlot extendedPlot(GameTestHelper helper, Fixture fixture, BlockPos position,
                                                     BlockState soil, BlockState crop, BlockState top) {
        setBlock(fixture.chunk(), position, soil);
        setBlock(fixture.chunk(), position.above(), crop);
        setBlock(fixture.chunk(), position.above(2), top);
        var plot = SettlementFieldPlot.capture(helper.getLevel(), fixture.area().id(), position, fixture.chunk(), 0L);
        var saved = plot.save();
        saved.putInt("BlockLight", 15);
        plot = SettlementFieldPlot.load(saved);
        fixture.town().recordField(plot);
        return plot;
    }

    private static void fastGrowth(GameTestHelper helper, Runnable work) {
        var level = helper.getLevel();
        int previous = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        try {
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(4096, level.getServer());
            work.run();
        } finally {
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(previous, level.getServer());
        }
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

    static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
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

    record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk, SettlementFieldPlot plot,
                           SettlementDepot supply, SettlementDepot output, SettlementWorkerState worker, Villager villager) {
    }
}
