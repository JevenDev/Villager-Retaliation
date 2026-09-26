package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
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

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementBackgroundCraftingGameTests {
    private SettlementBackgroundCraftingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void unloadedCraftsmanMakesAndDeliversChestFromRealLogs(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.WORKSHOP, Items.CHEST, fixture -> {
            fixture.supply().setItem(0, new ItemStack(Items.OAK_LOG, 2));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.supply().isEmpty(), "the two real logs are consumed by intermediate plank recipes");
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 1, "one chest is held by the absent worker");
            helper.assertValueEqual(carried(helper, fixture, Items.OAK_PLANKS), 0, "exact intermediate batches leave no extra planks");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.CHEST)), 1, "the actual output depot receives the chest");
            helper.assertValueEqual(carried(helper, fixture, Items.CHEST), 0, "delivered output is removed from carried stock");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "crafting and delivery do not load the town");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_crafting")
    public static void backgroundCakeCraftReturnsEveryMilkBucket(GameTestHelper helper) {
        fixture(helper, SettlementAreaType.KITCHEN, Items.CAKE, fixture -> {
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
