package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementBackgroundCraftingGameTests {
    private SettlementBackgroundCraftingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void unloadedCraftsmanMakesAndDeliversChestFromRealLogs(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            fixture.supply().setItem(0, new ItemStack(Items.OAK_LOG, 2));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.supply().isEmpty(), "the two real logs are consumed by intermediate plank recipes");
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 1, "one chest is held by the absent worker");
            helper.assertValueEqual(carried(helper, fixture, Items.OAK_PLANKS), 0, "exact intermediate batches leave no extra planks");
            double practice = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(practice > 0.0D, "successful crafting earns practice");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.CHEST)), 1, "the actual output depot receives the chest");
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 0, "delivered output is removed from carried stock");
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), practice, "depositing crafted output grants no second practice award");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "crafting and delivery do not load the town");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void backgroundCakeCraftReturnsEveryMilkBucket(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.KITCHEN, Items.CAKE, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            fixture.supply().setItem(0, new ItemStack(Items.MILK_BUCKET));
            fixture.supply().setItem(1, new ItemStack(Items.MILK_BUCKET));
            fixture.supply().setItem(2, new ItemStack(Items.MILK_BUCKET));
            fixture.supply().setItem(3, new ItemStack(Items.SUGAR, 2));
            fixture.supply().setItem(4, new ItemStack(Items.EGG));
            fixture.supply().setItem(5, new ItemStack(Items.WHEAT, 3));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.supply().isEmpty(), "the cake consumes exactly the actual ingredients");
            helper.assertValueEqual(carried(helper, fixture, Items.CAKE), 1, "the kitchen produces a cake");
            helper.assertValueEqual(carried(helper, fixture, Items.BUCKET), 3, "all three recipe container remainders survive");
            helper.assertTrue(SettlementPracticeGameTests.xp(profile) > 0.0D, "completed kitchen crafting earns cooking practice");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void backgroundCraftingRespectsSourceRulesAndFiniteOutputSpace(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            fixture.supply().setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            fixture.supply().observeRules(List.of(new ItemStack(Items.BREAD)));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 8, "a denied source is untouched");
            fixture.supply().observeRules(List.of());
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            var inventory = fixture.worker().items(helper.getLevel().registryAccess());
            for (int slot : fixture.worker().outputSlots()) {
                inventory.set(slot, new ItemStack(Items.STONE, 64));
            }
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), inventory);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 8, "a full output inventory consumes no ingredients");
            helper.assertValueEqual(fixture.worker().status(), "output_full", "the worker explains the capacity limit");
            inventory.set(HiredJobInventory.MAIN_GRID_START, ItemStack.EMPTY);
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), inventory);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 600L);
            helper.assertTrue(fixture.supply().isEmpty(), "freeing output room allows the recipe to complete");
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 1, "only one chest is produced");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void exactRecipeIngredientChoicesSurviveUnloadedCrafting(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.STICK, fixture -> {
            ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
            helper.assertTrue(VillagerRecipeFilterData.setRecipe(filter, helper.getLevel(), id("stick")), "the stick recipe is configured");
            helper.assertTrue(VillagerRecipeFilterData.setIngredient(filter, helper.getLevel(), 0, id("oak_planks")),
                    "the first ingredient is restricted to oak");
            helper.assertTrue(VillagerRecipeFilterData.setIngredient(filter, helper.getLevel(), 1, id("oak_planks")),
                    "the second ingredient is restricted to oak");
            var inventory = fixture.worker().items(helper.getLevel().registryAccess());
            inventory.set(HiredJobInventory.FILTER_SLOT, filter);
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), inventory);
            fixture.supply().setItem(0, new ItemStack(Items.BIRCH_PLANKS, 2));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 2, "birch cannot replace explicitly selected oak");
            helper.assertValueEqual(carried(helper, fixture, Items.STICK), 0, "a different stick recipe cannot bypass the filter");
            fixture.supply().setItem(1, new ItemStack(Items.OAK_PLANKS, 2));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(carried(helper, fixture, Items.STICK), 4, "the exact selected recipe produces its complete batch");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 2, "unselected ingredients remain unchanged");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void kitchenRequiresObservedCraftingTableAndKeepsItWhileUnloaded(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.KITCHEN, Items.BREAD, fixture -> {
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT, 3));
            fixture.town().recordSurvey(fixture.area().id(), new SettlementBuildingSurvey.Survey(0L, List.of(),
                    List.of(fixture.town().center()), true, Map.of(fixture.town().center(), id("smoker"))));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 3, "a smoker cannot stand in for a crafting table");
            observeTable(fixture);
            SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), fixture.area());
            var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(saved.surveys().get(fixture.area().id()).fixtures().get(fixture.town().center()),
                    id("crafting_table"), "the verified fixture remains known after unloading and saving");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(carried(helper, fixture, Items.BREAD), 1, "the verified table supports the bread recipe");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void backgroundCraftingRestoresOutputAndRecipeCursorOnce(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.OAK_PLANKS, fixture -> {
            ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
            VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.OAK_PLANKS));
            VillagerItemFilterData.setEntry(filter, 1, new ItemStack(Items.STICK));
            var inventory = HiredJobInventory.getJobInventory(fixture.villager());
            inventory.setItem(HiredJobInventory.FILTER_SLOT, filter);
            HiredWorkStateStore.state(fixture.villager()).putString("CraftsmanMode", "round_robin");
            fixture.worker().observe(helper.getLevel(), fixture.villager(),
                    SettlementWorkforceService.duty(fixture.villager()).orElseThrow(), 0L);
            fixture.supply().setItem(0, new ItemStack(Items.OAK_LOG));
            CompoundTag oldEntity = new CompoundTag();
            fixture.villager().saveWithoutId(oldEntity);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            Villager returning = EntityType.VILLAGER.create(helper.getLevel());
            returning.load(oldEntity);
            SettlementBackgroundService.onActive(returning);
            helper.assertValueEqual(HiredJobInventory.getJobInventory(returning).collectOutputItems().stream()
                            .filter(entry -> entry.stack().is(Items.OAK_PLANKS)).mapToInt(entry -> entry.stack().getCount()).sum(),
                    4, "the completed batch replaces the stale carried inventory");
            helper.assertValueEqual(HiredWorkStateStore.state(returning).getInt("CraftsmanCursor"), 1,
                    "the next recipe cursor follows the background batch");
            HiredWorkStateStore.state(returning).putInt("CraftsmanCursor", 0);
            SettlementBackgroundService.onActive(returning);
            helper.assertValueEqual(HiredWorkStateStore.state(returning).getInt("CraftsmanCursor"), 0,
                    "a repeated handoff cannot overwrite newer active settings");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void stoppedWorkersAndDisabledSupplyCollectionDoNotSpendDepotStock(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.OAK_PLANKS, fixture -> {
            fixture.supply().setItem(0, new ItemStack(Items.OAK_LOG));
            fixture.worker().settings().putBoolean("Enabled", false);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertFalse(fixture.supply().isEmpty(), "stopped workers cannot craft in the background");
            fixture.worker().settings().putBoolean("Enabled", true);
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertFalse(fixture.supply().isEmpty(), "disabled depot collection preserves shared stock");
            var inventory = fixture.worker().items(helper.getLevel().registryAccess());
            inventory.set(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_LOG));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), inventory);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 600L);
            helper.assertValueEqual(carried(helper, fixture, Items.OAK_PLANKS), 4, "carried supplies can still be crafted");
            helper.assertFalse(fixture.supply().isEmpty(), "carried crafting does not touch the depot");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void buildingSurveyRecordsActualFixtureBlockTypes(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(table.east(), Blocks.SMOKER.defaultBlockState());
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Kitchen", table, table.east(), 5, true);
        var survey = SettlementBuildingSurvey.inspect(helper.getLevel(), area);
        helper.assertValueEqual(survey.fixtures().get(table), id("crafting_table"), "the table is observed from the real world");
        helper.assertValueEqual(survey.fixtures().get(table.east()), id("smoker"), "the smoker keeps its distinct function");
        helper.getLevel().setBlockAndUpdate(table, Blocks.AIR.defaultBlockState());
        helper.assertFalse(SettlementBuildingSurvey.inspect(helper.getLevel(), area).fixtures().containsKey(table),
                "removed fixtures disappear from a fresh loaded survey");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void craftingKeepsCombinedFramedReserveAcrossIngredientAlternatives(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            ItemStack rule = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
            VillagerItemFilterData.setEntry(rule, 0, new ItemStack(Items.OAK_PLANKS));
            VillagerItemFilterData.setEntry(rule, 1, new ItemStack(Items.BIRCH_PLANKS));
            VillagerFilterPolicy.setDirection(rule, VillagerFilterPolicy.TransferDirection.PROVIDE);
            VillagerFilterPolicy.setStockTarget(rule, 2);
            fixture.supply().observeRules(List.of(rule));
            fixture.supply().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            fixture.supply().setItem(1, new ItemStack(Items.BIRCH_PLANKS, 4));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.supply().count(stack -> !stack.isEmpty()), 8,
                    "a combined reserve blocks a recipe that would consume every matching item");
            fixture.supply().setItem(2, new ItemStack(Items.OAK_PLANKS, 2));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 1, "stock above the combined reserve can be crafted");
            helper.assertValueEqual(fixture.supply().count(stack -> !stack.isEmpty()), 2,
                    "the combined reserve remains after consuming mixed ingredients");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void backgroundCraftingChecksLoadedTablesBeforeConsumingIngredients(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            var level = helper.getLevel();
            BlockPos table = fixture.town().center();
            var chunk = level.getChunkAt(table);
            chunk.setBlockState(table, Blocks.AIR.defaultBlockState(), false);
            observeTable(fixture);
            fixture.supply().setItem(0, new ItemStack(Items.OAK_LOG, 2));
            SettlementBackgroundCrafting.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(fixture.worker().status(), "no_crafting_table", "the worker notices a destroyed loaded table immediately");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 2, "stale survey data cannot consume real ingredients");
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 0, "a missing table produces no goods");
            chunk.setBlockState(table, Blocks.CRAFTING_TABLE.defaultBlockState(), false);
            SettlementBackgroundCrafting.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.supply().isEmpty(), "restoring the real table permits production");
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 1, "production resumes from the same real stock");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void stoppingEntityTicksCapturesFinalInventoryAndHealth(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            var villager = fixture.villager();
            HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_LOG, 2));
            villager.setHealth(7.0F);
            VillagerRecoveryService.restoreRecoveryState(villager,
                    new VillagerRecoveryService.RecoverySnapshot(0, 0.0F, 0.0F, 0));
            SettlementSimulationHandoff.stopTicking(villager);
            var care = fixture.town().residents().get(villager.getUUID());
            helper.assertValueEqual(carried(helper, fixture, Items.OAK_LOG), 2, "the final active supplies reach the worker ledger");
            helper.assertValueEqual(care.health(), 7.0F, "recent damage reaches the care ledger");
            helper.assertValueEqual(care.food(), 0, "recent hunger reaches the care ledger");
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
            SettlementCareService.update(helper.getLevel(), fixture.town(), care.checkedAt() + 200L);
            SettlementSimulationHandoff.stopTicking(villager);
            helper.assertValueEqual(carried(helper, fixture, Items.OAK_LOG), 0, "a repeated transition preserves background consumption");
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 1, "a repeated transition preserves background output");
            SettlementBackgroundService.onActive(villager);
            SettlementCareService.onActive(villager);
            helper.assertValueEqual(HiredJobInventory.getJobInventory(villager).collectOutputItems().stream()
                    .filter(entry -> entry.stack().is(Items.CHEST)).mapToInt(entry -> entry.stack().getCount()).sum(), 1,
                    "returning to active simulation restores one crafted chest");
            helper.assertTrue(villager.getHealth() <= 7.0F, "returning cannot restore health from the older observation");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void stoppingEntityTicksRefundsAnInterruptedMealBeforeSnapshotting(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            var villager = fixture.villager();
            VillagerRecoveryService.restoreRecoveryState(villager,
                    new VillagerRecoveryService.RecoverySnapshot(9, 0.0F, 0.0F, 0));
            VillagerInventoryAccess.addItem(villager, new ItemStack(Items.BREAD));
            helper.assertTrue(VillagerRecoveryService.onVillagerTickPost(villager), "the resident starts using the real meal");
            SettlementSimulationHandoff.stopTicking(villager);
            SettlementSimulationHandoff.stopTicking(villager);
            helper.assertValueEqual(VillagerInventoryAccess.captureFullInventory(villager).stream()
                    .filter(stack -> stack.is(Items.BREAD)).mapToInt(ItemStack::getCount).sum(), 1,
                    "the interrupted meal returns to personal inventory exactly once");
            helper.assertValueEqual(carried(helper, fixture, Items.BREAD), 0, "the visual meal cannot become job equipment");
            CompoundTag saved = new CompoundTag();
            villager.saveWithoutId(saved);
            Villager returning = EntityType.VILLAGER.create(helper.getLevel());
            returning.load(saved);
            VillagerRecoveryService.onVillagerLoaded(returning);
            SettlementBackgroundService.onActive(returning);
            helper.assertValueEqual(VillagerInventoryAccess.captureFullInventory(returning).stream()
                    .filter(stack -> stack.is(Items.BREAD)).mapToInt(ItemStack::getCount).sum(), 1,
                    "saving after the transition leaves no second refundable meal");
            helper.assertValueEqual(VillagerRecoveryService.foodLevel(returning), 9, "interrupted eating grants no nutrition");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void remoteWorkerSettingsPauseProductionAndControlDepotUse(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            var leader = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(fixture.town().leader(), "RemoteStaff"));
            var settings = SettlementWorkerConfigurationService.snapshot(helper.getLevel(), fixture.town(), fixture.worker().id());
            fixture.supply().setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            helper.assertTrue(SettlementWorkerConfigurationService.configure(leader, fixture.town(), fixture.worker().id(), fixture.area().id(),
                    settings.with(SettlementWorkerSetting.ENABLED, 0)), "staff can pause the absent craftsman");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 8, "paused production consumes no stock");
            settings = settings.with(SettlementWorkerSetting.USE_DEPOTS, 0);
            SettlementWorkerConfigurationService.configure(leader, fixture.town(), fixture.worker().id(), fixture.area().id(), settings);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 8, "disabled depot supplies are not consumed");
            settings = settings.with(SettlementWorkerSetting.USE_DEPOTS, 1).with(SettlementWorkerSetting.AUTO_DEPOSIT, 0);
            SettlementWorkerConfigurationService.configure(leader, fixture.town(), fixture.worker().id(), fixture.area().id(), settings);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 600L);
            helper.assertTrue(fixture.supply().isEmpty(), "resuming production consumes the real planks");
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 1, "the craftsman retains the finished chest");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 800L);
            helper.assertTrue(fixture.output().isEmpty(), "disabled auto-deposit keeps finished stock with the resident");
            SettlementWorkerConfigurationService.configure(leader, fixture.town(), fixture.worker().id(), fixture.area().id(),
                    settings.with(SettlementWorkerSetting.AUTO_DEPOSIT, 1));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 1_000L);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.CHEST)), 1, "enabling auto-deposit delivers the same chest once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void partialCraftSurvivesNativeReturnWithoutSpendingIngredientsTwice(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            fixture.supply().setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 50, 1.0D);
            helper.assertValueEqual(fixture.worker().status(), "crafting", "travel and crafting need more than the first interval");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 8, "unfinished work reserves no ingredients");
            SettlementBackgroundService.onActive(fixture.villager());
            SettlementSimulationHandoff.stopTicking(fixture.villager());
            helper.assertValueEqual(fixture.worker().progress(), 50.0D, "native return retains the paid part of the job");
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 100, 1.0D);
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 1, "the remaining interval completes the original recipe");
            helper.assertTrue(fixture.supply().isEmpty(), "only one recipe consumes ingredients");
            SettlementBackgroundService.onActive(fixture.villager());
            var inventory = HiredJobInventory.getJobInventory(fixture.villager());
            helper.assertValueEqual(inventory.countItem(Items.CHEST), 1, "the actual result reaches the native worker");
            inventory.removeItem(HiredJobInventory.MAIN_GRID_START, 1);
            SettlementBackgroundService.onActive(fixture.villager());
            helper.assertValueEqual(inventory.countItem(Items.CHEST), 0, "the same recipe result cannot be returned twice");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void sharedCraftQueueSurvivesReloadWithoutDuplicatingReservedQuantity(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            var queue = production(fixture);
            var blocked = HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(id("diamond_block"), Map.of()),
                    HiredProductionOrder.Mode.BATCH, 1, null);
            var order = HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(id("stick"), Map.of()),
                    HiredProductionOrder.Mode.BATCH, 4, null);
            queue.append(blocked);
            queue.append(order);
            fixture.supply().setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            var copy = fixture.worker().save();
            var secondId = UUID.randomUUID();
            copy.putUUID("Id", secondId);
            var second = SettlementWorkerState.load(copy);
            fixture.town().assignDuty(secondId, fixture.area().id());
            fixture.town().recordWorker(second);
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 1, 1.0D);
            helper.assertValueEqual(queue.pending(order.id()), 4, "the first worker reserves the shared batch while approaching supplies");
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), second, 200, 1.0D);
            helper.assertValueEqual(queue.pending(order.id()), 4, "another worker cannot duplicate the worksite goal");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 8, "uncommitted work consumes nothing");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var returned = restored.workers().get(fixture.worker().id());
            SettlementBackgroundCrafting.tick(helper.getLevel(), restored, returned, 200, 1.0D);
            var restoredQueue = restored.productionQueues().get(fixture.area().id());
            helper.assertValueEqual(restoredQueue.order(order.id()).produced(), 4L, "reloaded production completes the original reserved quantity");
            helper.assertValueEqual(restoredQueue.pending(order.id()), 0, "completed work releases its reservation");
            helper.assertValueEqual(restored.depots().get(fixture.supply().id()).getItem(0).getCount(), 6, "one actual recipe batch is consumed");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "saved production never loads the worksite chunk");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void maintainedStockIncludesCarriedOutputsAndKeepsTheirDestinationOnReturn(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            var queue = production(fixture);
            var order = HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(id("stick"), Map.of()),
                    HiredProductionOrder.Mode.MAINTAIN_STOCK, 5, fixture.output().position());
            queue.append(order);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            fixture.supply().setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            fixture.output().setItem(0, new ItemStack(Items.STICK, 1));
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(carried(helper, fixture, Items.STICK), 4, "the missing stock is crafted once");
            helper.assertValueEqual(SettlementProductionStock.count(helper.getLevel(), fixture.town(), queue, order), 5L,
                    "stored and carried outputs count toward the same stock target");
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(carried(helper, fixture, Items.STICK), 4, "undelivered goods prevent redundant production");
            helper.assertTrue(fixture.worker().restore(fixture.villager()), "the saved inventory returns to its villager");
            helper.assertValueEqual(HiredProductionStock.destination(HiredWorkStateStore.state(fixture.villager())),
                    fixture.output().position(), "the chosen destination returns with the goods");
            helper.assertFalse(fixture.worker().restore(fixture.villager()), "the same handoff cannot restore goods twice");
            fixture.worker().settings().putBoolean("AutoDepositOutputs", true);
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 300, 1.0D);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.STICK)), 5, "the batch reaches only its selected output depot");
            helper.assertTrue(HiredProductionStock.destination(fixture.worker().settings()) == null, "completed delivery clears the temporary destination");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void departingCrafterReleasesAnUnconsumedSharedBatch(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            var queue = production(fixture);
            var order = HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(id("stick"), Map.of()),
                    HiredProductionOrder.Mode.BATCH, 4, null);
            queue.append(order);
            fixture.supply().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 1, 1.0D);
            helper.assertValueEqual(queue.pending(order.id()), 4, "travel has reserved the next batch");
            fixture.town().removeWorker(fixture.worker().id());
            helper.assertValueEqual(queue.pending(order.id()), 0, "a dead or removed worker cannot permanently hold the shared goal");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 2, "the abandoned craft leaves its materials intact");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void sharedKitchenQueueKeepsFurnacePriorityAcrossBlockedCrafts(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.KITCHEN, Items.BREAD, fixture -> {
            var level = helper.getLevel();
            var queue = production(fixture);
            var cake = HiredProductionOrder.create(level, HiredProductionRecipe.exact(id("cake"), Map.of()), HiredProductionOrder.Mode.BATCH, 1, null);
            var potato = HiredProductionOrder.create(level, HiredProductionRecipe.exact(id("baked_potato_from_smoking"), Map.of()), HiredProductionOrder.Mode.BATCH, 2, null);
            var bread = HiredProductionOrder.create(level, HiredProductionRecipe.exact(id("bread"), Map.of()), HiredProductionOrder.Mode.BATCH, 1, null);
            queue.append(cake);
            queue.append(potato);
            queue.append(bread);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            fixture.supply().setItem(0, new ItemStack(Items.POTATO, 5));
            fixture.supply().setItem(1, new ItemStack(Items.COAL));
            fixture.supply().setItem(2, new ItemStack(Items.WHEAT, 6));
            var entity = new net.minecraft.world.level.block.entity.SmokerBlockEntity(fixture.area().min().east(), Blocks.SMOKER.defaultBlockState());
            var machine = SettlementFurnaceState.capture(level, fixture.area().id(), entity, 0L);
            fixture.town().recordFurnace(machine);
            for (int tick = 0; tick < 60; tick++) SettlementBackgroundSmelting.tick(level, fixture.town(), fixture.worker(), 3, 1.0D);
            helper.assertValueEqual(machine.items(level.registryAccess()).getFirst().getCount(), 2, "blocked earlier crafts do not repeatedly reset the runnable furnace trip");
            helper.assertValueEqual(carried(helper, fixture, Items.BREAD), 0, "a later craft cannot bypass the first runnable furnace order");
            helper.assertFalse(queue.order(cake.id()).blocker().isEmpty(), "the blocked crafting order remains visible");
            machine.advance(level, 99L);
            helper.assertTrue(machine.items(level.registryAccess()).get(2).isEmpty(), "the smoker keeps its native duration");
            machine.advance(level, 100L);
            SettlementBackgroundSmelting.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(queue.order(potato.id()).produced(), 1L, "the first actual meal settles once");
            machine.advance(level, 200L);
            SettlementBackgroundSmelting.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            SettlementBackgroundSmelting.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(queue.order(potato.id()).produced(), 2L, "the finite smoking entry completes");
            helper.assertValueEqual(queue.order(bread.id()).produced(), 1L, "the later craft runs after native processing finishes");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 3, "only the two requested potatoes are consumed");
            helper.assertFalse(level.hasChunkAt(fixture.town().center()), "mixed production leaves the worksite unloaded");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void sharedMealStockIncludesWholeBatchesCarriedByOtherCooks(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.KITCHEN, Items.COOKIE, fixture -> {
            var level = helper.getLevel();
            var queue = production(fixture);
            var order = HiredProductionOrder.create(level, HiredProductionRecipe.exact(id("cookie"), Map.of()),
                    HiredProductionOrder.Mode.MAINTAIN_STOCK, 5, fixture.output().position());
            queue.append(order);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT, 8));
            fixture.supply().setItem(1, new ItemStack(Items.COCOA_BEANS, 4));
            var saved = fixture.worker().save();
            var secondId = UUID.randomUUID();
            saved.putUUID("Id", secondId);
            var second = SettlementWorkerState.load(saved);
            fixture.town().assignDuty(secondId, fixture.area().id());
            fixture.town().recordWorker(second);
            SettlementBackgroundSmelting.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            SettlementBackgroundSmelting.tick(level, fixture.town(), second, 200, 1.0D);
            helper.assertValueEqual(queue.order(order.id()).produced(), 8L, "the kitchen completes one indivisible batch for the five-meal target");
            helper.assertValueEqual(SettlementProductionStock.count(level, fixture.town(), queue, order), 8L, "carried meals count toward shared stock before delivery");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 6, "another cook cannot duplicate meals already on their way to storage");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void queuedSmallFoodRecipeKeepsItsNativeStationRequirements(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.KITCHEN, Items.PUMPKIN_PIE, fixture -> {
            var level = helper.getLevel();
            var queue = production(fixture);
            var order = HiredProductionOrder.create(level, HiredProductionRecipe.exact(id("pumpkin_pie"), Map.of()), HiredProductionOrder.Mode.BATCH, 1, null);
            queue.append(order);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            fixture.town().recordSurvey(fixture.area().id(), new SettlementBuildingSurvey.Survey(0L, List.of(), List.of(), true));
            fixture.supply().setItem(0, new ItemStack(Items.PUMPKIN));
            fixture.supply().setItem(1, new ItemStack(Items.EGG));
            fixture.supply().setItem(2, new ItemStack(Items.SUGAR));
            var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(fixture.area().min(), Blocks.FURNACE.defaultBlockState());
            furnace.setLevel(level);
            furnace.setItem(0, new ItemStack(Items.RAW_IRON));
            furnace.setItem(1, new ItemStack(Items.COAL));
            var machine = SettlementFurnaceState.capture(level, fixture.area().id(), furnace, 0L);
            fixture.town().recordFurnace(machine);
            SettlementBackgroundSmelting.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(machine.items(level.registryAccess()).getFirst().is(Items.RAW_IRON), "unrelated legacy furnace contents remain untouched");
            helper.assertValueEqual(queue.order(order.id()).produced(), 1L, "a small native recipe does not gain a new crafting-table requirement");
            helper.assertValueEqual(carried(helper, fixture, Items.PUMPKIN_PIE), 1, "the actual ingredients become one carried meal");
            helper.assertTrue(fixture.supply().isEmpty(), "only real supplies are consumed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void personalAndCivicAdaptersProduceEquivalentRecipeRemainders(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.KITCHEN, Items.CAKE, fixture -> {
            var level = helper.getLevel();
            List<ItemStack> ingredients = List.of(new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                    new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.SUGAR, 2), new ItemStack(Items.EGG), new ItemStack(Items.WHEAT, 3));
            var position = helper.absolutePos(new BlockPos(2, 2, 2));
            var previous = level.getBlockState(position);
            var personal = EntityType.VILLAGER.create(level);
            personal.setPos(position.getCenter());
            try {
                level.setBlockAndUpdate(position, Blocks.CRAFTING_TABLE.defaultBlockState());
                com.jvn.villagerretaliation.interaction.HiredWorkArea.fromBounds(position, position, true)
                        .save(HiredWorkStateStore.state(personal));
                var session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(level, personal,
                        com.jvn.villagerretaliation.interaction.HiredVillagerRole.COOK);
                for (int index = 0; index < ingredients.size(); index++) {
                    fixture.supply().setItem(index, ingredients.get(index).copy());
                    helper.assertTrue(session.inventory().insertPlainSupply(ingredients.get(index).copy()).isEmpty(), "personal ingredients fit");
                }
                var recipe = (net.minecraft.world.item.crafting.CraftingRecipe) level.getRecipeManager().byKey(id("cake")).orElseThrow().value();
                helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HiredSupplyCrafting
                        .craftCarriedRecipeToOutputsWithStations(level, session.context(), recipe), "the personal recipe commits");
                SettlementBackgroundService.update(level, fixture.town(), 200L);
                for (Item item : List.of(Items.CAKE, Items.BUCKET, Items.MILK_BUCKET, Items.SUGAR, Items.EGG, Items.WHEAT)) {
                    int personalCount = 0;
                    for (int slot = 0; slot < session.inventory().getContainerSize(); slot++) {
                        var stack = session.inventory().getItem(slot);
                        if (stack.is(item)) personalCount += stack.getCount();
                    }
                    helper.assertValueEqual(carried(helper, fixture, item), personalCount, "both adapters preserve the same actual outputs and remainders: " + item);
                }
                helper.assertTrue(fixture.supply().isEmpty(), "the civic adapter consumes exactly the same recipe inputs");
                helper.assertFalse(com.jvn.villagerretaliation.interaction.VillagerWorkAuthority.hasWork(personal),
                        "personal inventory crafting does not acquire civic authority");
            } finally {
                level.setBlockAndUpdate(position, previous);
                personal.discard();
            }
        });
    }

    private static HiredProductionQueue production(Fixture fixture) {
        fixture.town().setWorksitePlan(fixture.town().leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
        return fixture.town().beginProduction(fixture.town().leader(), fixture.area().id());
    }

    private static int carried(GameTestHelper helper, Fixture fixture, Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream()
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static void observeTable(Fixture fixture) {
        fixture.town().recordSurvey(fixture.area().id(), new SettlementBuildingSurvey.Survey(0L, List.of(),
                List.of(fixture.town().center()), true, Map.of(fixture.town().center(), id("crafting_table"))));
    }

    private static void fixture(GameTestHelper helper, SettlementAreaType type, Item output, Consumer<Fixture> check) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos center = new BlockPos(9_000_000 + origin.getX() * 8, 64, 9_000_000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), type, "Production", center, center.offset(3, 2, 3), 5, true);
        town.putArea(town.leader(), area);
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage", center.east(8), center.offset(10, 2, 2), 5, true);
        town.putArea(town.leader(), storage);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", center.west(3), center.west(), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
        var supply = town.registerDepot(town.leader(), storage.id(), center.east(8), SettlementDepotPurpose.MATERIALS);
        var destination = town.registerDepot(town.leader(), storage.id(), center.east(9), SettlementDepotPurpose.OUTPUT);
        supply.observeRules(List.of());
        destination.observeRules(List.of());
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(output));
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.FILTER_SLOT, filter);
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        var fixture = new Fixture(town, area, supply, destination, villager, worker);
        observeTable(fixture);
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(fixture);
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementDepot supply, SettlementDepot output,
                           Villager villager, SettlementWorkerState worker) {
    }
}
