package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementBackgroundBrewingGameTests {
    private SettlementBackgroundBrewingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void backgroundBrewingMatchesVanillaProgressAndFuel(GameTestHelper helper) {
        for (int ticks : new int[] {1, 200, 400, 401, 800}) {
            compare(helper, PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.NETHER_WART),
                    new ItemStack(Items.BLAZE_POWDER, 2), ticks);
        }
        compare(helper, PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.DIRT),
                new ItemStack(Items.BLAZE_POWDER), 401);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void brewingKeepsActualPotionComponentsAndIngredientRemainders(GameTestHelper helper) {
        compare(helper, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING), new ItemStack(Items.DRAGON_BREATH),
                new ItemStack(Items.BLAZE_POWDER), 401);
        ItemStack named = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Stored batch"));
        compare(helper, named, new ItemStack(Items.GLISTERING_MELON_SLICE), new ItemStack(Items.BLAZE_POWDER), 401);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void brewingSaveReceiptPreventsDuplicateOutputsAndKeepsMetadata(GameTestHelper helper) {
        var level = helper.getLevel();
        var stand = place(helper, PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.NETHER_WART),
                new ItemStack(Items.BLAZE_POWDER));
        var named = stand.saveWithoutMetadata(level.registryAccess());
        named.putString("CustomName", Component.Serializer.toJson(Component.literal("Town brewery"), level.registryAccess()));
        stand.loadWithComponents(named, level.registryAccess());
        var state = SettlementBrewingStandState.capture(level, UUID.randomUUID(), stand, 0L);
        advance(helper, state, 401);
        var saved = state.save();
        helper.assertTrue(state.reconcile(level.registryAccess(), stand), "pending brewing replaces its unchanged native inventory");
        helper.assertTrue(ItemStack.isSameItemSameComponents(stand.getItem(0), PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)),
                "the completed potion reaches the physical stand");
        helper.assertValueEqual(stand.getName().getString(), "Town brewery", "container metadata survives reconciliation");
        stand.removeItem(0, 1);
        var stale = SettlementBrewingStandState.load(saved);
        helper.assertTrue(stale.reconcile(level.registryAccess(), stand), "a previously applied receipt remains recognizable after saving");
        helper.assertTrue(stand.getItem(0).isEmpty(), "a stale settlement save cannot replay a collected potion");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void conflictingOrReplacedStandPreservesBothVersions(GameTestHelper helper) {
        var level = helper.getLevel();
        var stand = place(helper, PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.NETHER_WART),
                new ItemStack(Items.BLAZE_POWDER));
        var state = SettlementBrewingStandState.capture(level, UUID.randomUUID(), stand, 0L);
        advance(helper, state, 401);
        stand.setItem(0, new ItemStack(Items.DIAMOND));
        helper.assertFalse(state.reconcile(level.registryAccess(), stand), "changed native contents block replacement");
        helper.assertTrue(stand.getItem(0).is(Items.DIAMOND), "the native inventory is retained");
        var restored = SettlementBrewingStandState.load(state.save());
        restored.advance(level, 1_000L);
        helper.assertTrue(restored.pending() && restored.conflict(), "unapplied work stays paused and recoverable");
        helper.assertTrue(ItemStack.isSameItemSameComponents(restored.items(level.registryAccess()).get(0),
                PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)), "the background potion is retained separately");
        var replacement = new BrewingStandBlockEntity(stand.getBlockPos(), Blocks.BREWING_STAND.defaultBlockState());
        helper.assertFalse(restored.reconcile(level.registryAccess(), replacement), "a new stand at the same position has a different identity");
        helper.assertTrue(replacement.isEmpty(), "pending work cannot populate a replacement stand");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void brewingClockCapsGapsAndExcludesRecentVanillaTicks(GameTestHelper helper) {
        var level = helper.getLevel();
        var stand = place(helper, PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.NETHER_WART),
                new ItemStack(Items.BLAZE_POWDER));
        var bounded = SettlementBrewingStandState.capture(level, UUID.randomUUID(), stand, 0L);
        bounded.advance(level, 1_000_000L);
        helper.assertValueEqual(bounded.brewTime(), 201, "a large gap advances only the bounded server interval");
        bounded.advance(level, 1_000_000L);
        helper.assertValueEqual(bounded.brewTime(), 201, "the same timestamp cannot advance brewing twice");
        var state = SettlementBrewingStandState.capture(level, UUID.randomUUID(), stand, level.getGameTime() - 200L);
        BrewingStandBlockEntity.serverTick(level, stand.getBlockPos(), level.getBlockState(stand.getBlockPos()), stand);
        state.refresh(level.registryAccess(), stand);
        state.advance(level, level.getGameTime() + 50L);
        helper.assertValueEqual(state.brewTime(), 350, "background work begins after the most recent native block tick");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void stackedRemainderIngredientWaitsUntilItsExtraItemsAreRecovered(GameTestHelper helper) {
        var level = helper.getLevel();
        var stand = place(helper, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING),
                new ItemStack(Items.DRAGON_BREATH, 2), new ItemStack(Items.BLAZE_POWDER));
        var state = SettlementBrewingStandState.capture(level, UUID.randomUUID(), stand, 0L);
        advance(helper, state, 600);
        helper.assertValueEqual(state.brewTime(), 1, "completion waits when its remainder would require an unloaded item entity");
        var items = state.items(level.registryAccess());
        helper.assertValueEqual(items.get(3).getCount(), 2, "neither ingredient is lost while waiting");
        ItemStack recovered = items.get(3).split(1);
        state.updateItems(level, items);
        state.advance(level, 601L);
        helper.assertTrue(recovered.is(Items.DRAGON_BREATH), "the extra ingredient can be recovered before completion");
        helper.assertTrue(state.items(level.registryAccess()).get(3).is(Items.GLASS_BOTTLE), "the used ingredient leaves its actual bottle");
        helper.assertTrue(state.items(level.registryAccess()).get(0).is(Items.LINGERING_POTION), "brewing resumes after the extra stack is removed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void chunkHandoffCapturesFinalBrewingStateAndAppliesBottlesOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var stand = place(helper, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
        BlockPos position = stand.getBlockPos();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), position, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), position, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.BREWERY, "Brewery", position, position, 5, true);
        town.putArea(town.leader(), area);
        var chunk = level.getChunkAt(position);
        SettlementBrewingStandService.onChunkUnload(new ChunkEvent.Unload(chunk));
        stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        stand.setItem(3, new ItemStack(Items.NETHER_WART));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        BrewingStandBlockEntity.serverTick(level, position, level.getBlockState(position), stand);
        SettlementBrewingStandService.onChunkUnload(new ChunkEvent.Unload(chunk));
        var state = town.brewingStands().get(position);
        helper.assertValueEqual(state.brewTime(), 400, "the final snapshot includes the newest active brew");
        helper.assertTrue(level.getBlockState(position).getValue(BrewingStandBlock.HAS_BOTTLE[0]), "observation preserves the real bottle state");
        state.advance(level, level.getGameTime() + 200L);
        SettlementBrewingStandService.onChunkLoad(new ChunkEvent.Load(chunk, false));
        helper.assertFalse(state.pending(), "the chunk receives its pending progress before active processing");
        for (int tick = 0; tick < 200; tick++) {
            BrewingStandBlockEntity.serverTick(level, position, level.getBlockState(position), stand);
        }
        helper.assertTrue(ItemStack.isSameItemSameComponents(stand.getItem(0), PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)),
                "the restored active ingredient allows native brewing to finish on time");
        stand.removeItem(0, 1);
        SettlementBrewingStandService.onChunkLoad(new ChunkEvent.Load(chunk, false));
        helper.assertTrue(stand.getItem(0).isEmpty(), "repeated loading cannot refill a collected potion");
        helper.assertFalse(level.getBlockState(position).getValue(BrewingStandBlock.HAS_BOTTLE[0]), "returned bottle state matches the actual slots");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void unloadedBrewerFillsBottlesCompletesExactOrderAndDelivers(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            fixture.worker().brewingOrder(order("potion", 4, false));
            supply(fixture, 4, 2);
            run(helper, fixture, 200, 8_000);
            var expected = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            helper.assertValueEqual(fixture.output().count(stack -> ItemStack.isSameItemSameComponents(stack, expected)), 4,
                    "two real batches produce exactly the requested four potions");
            helper.assertTrue(fixture.supply().isEmpty(), "bottles and both ingredient batches come from finite shared stock");
            helper.assertValueEqual(fixture.machine().fuel(), 16, "four brewing steps consume four of the twenty fuel uses");
            helper.assertTrue(SettlementBrewingOrder.read(fixture.worker().settings()) == null, "the completed quantity clears its order");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "production and delivery do not load the distant town");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.brewingStands().size(), 1, "machine state survives the town save");
            helper.assertTrue(restored.brewingStands().values().iterator().next().pending(), "unloaded work awaits its physical stand");
            helper.assertValueEqual(restored.surveys().get(fixture.area().id()).waterSources(), List.of(fixture.town().center().east()),
                    "the observed source survives saving without loading water");
            double practice = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(practice > 0.0D, "completed potion batches earn brewing practice");
            run(helper, fixture, 8_200, 9_000);
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), practice, "completed orders cannot grant repeated practice");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void unloadedLingeringOrderReturnsItsGlassBottle(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.worker().brewingOrder(order("lingering_potion", 1, false));
            supply(fixture, 1, 1);
            fixture.supply().setItem(4, new ItemStack(Items.GUNPOWDER));
            fixture.supply().setItem(5, new ItemStack(Items.DRAGON_BREATH));
            run(helper, fixture, 200, 8_000);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.LINGERING_POTION)), 1, "all container and potion steps complete");
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.GLASS_BOTTLE)), 1, "the consumed dragon breath returns one glass bottle");
            helper.assertTrue(fixture.supply().isEmpty(), "each ingredient is consumed once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void brewingHonorsSourceRulesAndSharedBottleReserve(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.worker().brewingOrder(order("potion", 3, false));
            supply(fixture, 3, 1);
            fixture.supply().observeRules(List.of(new ItemStack(Items.BREAD)));
            run(helper, fixture, 200, 600);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 3, "denied depot bottles remain untouched");
            ItemStack rule = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
            VillagerItemFilterData.setEntry(rule, 0, new ItemStack(Items.GLASS_BOTTLE));
            VillagerFilterPolicy.setDirection(rule, VillagerFilterPolicy.TransferDirection.PROVIDE);
            VillagerFilterPolicy.setStockTarget(rule, 2);
            fixture.supply().observeRules(List.of(rule));
            run(helper, fixture, 800, 1_200);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 2, "a multi-bottle transfer preserves the combined framed reserve");
            helper.assertFalse(fixture.machine().items(helper.getLevel().registryAccess()).get(0).isEmpty(),
                    "the bottle above the reserve can be filled");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void fullWorkerKeepsPotionsInStandAndRestoresCompletedOrderOnlyOnce(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            var level = helper.getLevel();
            fixture.worker().brewingOrder(order("potion", 3, false));
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            supply(fixture, 3, 1);
            var inventory = fixture.worker().items(level.registryAccess());
            for (int slot : fixture.worker().outputSlots()) {
                inventory.set(slot, new ItemStack(Items.DIRT, 64));
            }
            fixture.worker().updateInventory(level.registryAccess(), inventory);
            run(helper, fixture, 200, 4_000);
            helper.assertValueEqual(fixture.worker().status(), "output_full", "finished potions wait for actual carried space");
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), 0.0D, "supply trips and blocked output cannot earn brewing practice");
            helper.assertValueEqual(SettlementBrewingOrder.read(fixture.worker().settings()).amount(), 3, "uncollected potions do not complete the order");
            var expected = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            helper.assertTrue(ItemStack.isSameItemSameComponents(fixture.machine().items(level.registryAccess()).get(0), expected),
                    "the finished potion remains in its finite stand slot");
            for (int slot : fixture.worker().outputSlots()) {
                inventory.set(slot, ItemStack.EMPTY);
            }
            fixture.worker().updateInventory(level.registryAccess(), inventory);
            run(helper, fixture, 4_200, 4_400);
            helper.assertTrue(SettlementBrewingOrder.read(fixture.worker().settings()) == null, "collecting the exact batch completes the order");
            helper.assertTrue(fixture.worker().inventory().contains("LayoutVersion"), "an initially empty worker retains its current inventory layout");
            var legacy = fixture.worker().save();
            legacy.getCompound("Inventory").remove("LayoutVersion");
            var returning = SettlementWorkerState.load(legacy);
            helper.assertTrue(returning.restore(fixture.villager()), "the returning worker receives its real carried potions from an older empty snapshot");
            var live = HiredJobInventory.getJobInventory(fixture.villager());
            helper.assertValueEqual(live.collectOutputItems().stream().mapToInt(entry -> entry.stack().getCount()).sum(), 3,
                    "all three carried potions survive the handoff");
            helper.assertFalse(com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker.hasOrder(HiredWorkStateStore.state(fixture.villager())),
                    "a completed background order cannot resume on return");
            live.removeItem(HiredJobInventory.MAIN_GRID_START, 1);
            helper.assertFalse(fixture.worker().restore(fixture.villager()), "repeated worker reconciliation does not replay the inventory");
            helper.assertTrue(live.getItem(HiredJobInventory.MAIN_GRID_START).isEmpty(), "the collected potion is not recreated");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void continuousBrewingCanUseCarriedWaterWithoutDepotCollection(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.worker().brewingOrder(order("potion", 1, true));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            fixture.worker().settings().putBoolean("Enabled", false);
            fixture.town().recordSurvey(fixture.area().id(), new SettlementBuildingSurvey.Survey(0L, List.of(), List.of(), true));
            var items = fixture.worker().items(level.registryAccess());
            int slot = HiredJobInventory.HOTBAR_START;
            items.set(slot, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            items.set(slot + 1, new ItemStack(Items.NETHER_WART));
            items.set(slot + 2, new ItemStack(Items.GLISTERING_MELON_SLICE));
            items.set(slot + 3, new ItemStack(Items.BLAZE_POWDER));
            fixture.worker().updateInventory(level.registryAccess(), items);
            fixture.supply().setItem(0, new ItemStack(Items.GLASS_BOTTLE, 8));
            run(helper, fixture, 200, 600);
            helper.assertTrue(fixture.machine().items(level.registryAccess()).stream().allMatch(ItemStack::isEmpty), "stopped workers leave stands untouched");
            fixture.worker().settings().putBoolean("Enabled", true);
            run(helper, fixture, 800, 4_000);
            var expected = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            helper.assertValueEqual(fixture.worker().items(level.registryAccess()).stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                    .mapToInt(ItemStack::getCount).sum(), 1, "carried water and ingredients produce one actual potion");
            helper.assertTrue(SettlementBrewingOrder.read(fixture.worker().settings()).continuous(), "a continuous order remains active after collecting");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 8, "disabled depot collection preserves depot stock");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void foreignPotionsAndUnobservedWaterCannotSpendSupplies(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.worker().brewingOrder(order("potion", 3, false));
            supply(fixture, 3, 1);
            fixture.town().recordSurvey(fixture.area().id(), new SettlementBuildingSurvey.Survey(0L, List.of(), List.of(), true));
            run(helper, fixture, 200, 600);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 3, "glass bottles cannot be filled without a verified source");
            var contents = fixture.machine().items(level.registryAccess());
            contents.set(0, PotionContents.createItemStack(Items.POTION, Potions.POISON));
            fixture.machine().updateItems(level, contents);
            run(helper, fixture, 800, 1_200);
            helper.assertValueEqual(fixture.supply().getItem(1).getCount(), 1, "an unrelated potion cannot consume this order's ingredients");
            helper.assertTrue(ItemStack.isSameItemSameComponents(fixture.machine().items(level.registryAccess()).get(0),
                    PotionContents.createItemStack(Items.POTION, Potions.POISON)), "the unrelated potion is preserved");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void brewerySurveySeparatesWaterFromFunctionalEquipment(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(position, Blocks.BREWING_STAND.defaultBlockState(), 2);
        level.setBlock(position.east(), Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(position.east(2), Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 1), 2);
        level.setBlock(position.east(3), Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true), 2);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.BREWERY, "Brewery", position, position.east(3), 5, true);
        var survey = SettlementBuildingSurvey.inspect(level, area);
        helper.assertValueEqual(survey.workstations(), List.of(position), "water does not count as brewery equipment");
        helper.assertValueEqual(survey.waterSources().size(), 2, "only source water and waterlogged sources qualify");
        var restored = SettlementBuildingSurvey.Survey.load(survey.save());
        helper.assertValueEqual(restored.waterSources(), survey.waterSources(), "water observations survive a survey save");
        var legacy = survey.save();
        legacy.remove("WaterSources");
        helper.assertTrue(SettlementBuildingSurvey.Survey.load(legacy).waterSources().isEmpty(), "older surveys load without invented water sources");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void loadedWaterIsRecheckedBeforeFillingFromAnOlderSurvey(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            BlockPos center = fixture.town().center();
            var chunk = level.getChunkAt(center);
            chunk.setBlockState(center, Blocks.BREWING_STAND.defaultBlockState(), false);
            chunk.setBlockState(center.east(), Blocks.AIR.defaultBlockState(), false);
            var stand = (BrewingStandBlockEntity) chunk.getBlockEntity(center);
            var machine = SettlementBrewingStandState.capture(level, fixture.area().id(), stand, level.getGameTime());
            fixture.town().recordBrewingStand(machine);
            fixture.town().recordSurvey(fixture.area().id(), new SettlementBuildingSurvey.Survey(0L, List.of(), List.of(center), true,
                    Map.of(center, ResourceLocation.withDefaultNamespace("brewing_stand")), List.of(center.east())));
            fixture.worker().brewingOrder(order("potion", 3, false));
            supply(fixture, 3, 1);
            SettlementBackgroundBrewing.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 3, "removed loaded water overrides the older survey");
            chunk.setBlockState(center.east(), Blocks.WATER.defaultBlockState(), false);
            SettlementBackgroundBrewing.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            SettlementBackgroundBrewing.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.supply().getItem(0).isEmpty(), "a restored real source allows the three bottles to be filled");
            helper.assertTrue(stand.getItem(0).is(Items.POTION), "loaded stands receive supplied bottles immediately");
            helper.assertTrue(level.getBlockState(center).getValue(BrewingStandBlock.HAS_BOTTLE[0]), "the block displays its actual supplied bottle");
            helper.assertTrue(SettlementBuildingSurvey.waterSource(chunk.getBlockState(center.east())), "filling preserves the native water source");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void unsafeBrewersPauseWhileAnAlreadySuppliedStandKeepsBrewing(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.villager().setTarget(EntityType.ZOMBIE.create(level));
            fixture.worker().observe(level, fixture.villager(), SettlementWorkforceService.duty(fixture.villager()).orElseThrow(), 0L);
            fixture.worker().brewingOrder(order("potion", 3, false));
            supply(fixture, 3, 1);
            var items = fixture.machine().items(level.registryAccess());
            items.set(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            items.set(3, new ItemStack(Items.NETHER_WART));
            items.set(4, new ItemStack(Items.BLAZE_POWDER));
            fixture.machine().updateItems(level, items);
            run(helper, fixture, 200, 1_000);
            helper.assertTrue(ItemStack.isSameItemSameComponents(fixture.machine().items(level.registryAccess()).get(0),
                    PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)), "an already supplied machine continues its own brew clock");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 3, "unsafe workers cannot fetch new bottles");
            helper.assertValueEqual(fixture.supply().getItem(2).getCount(), 1, "unsafe workers cannot supply the next ingredient");
            helper.assertValueEqual(fixture.worker().status(), "unsafe", "the resident retains the safety pause");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void sharedBrewingQueueFinishesOnlyItsCancelledNativeBatch(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.town().setWorksitePlan(fixture.town().leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
            var queue = fixture.town().beginProduction(fixture.town().leader(), fixture.area().id());
            var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.brewing(
                            ResourceLocation.withDefaultNamespace("potion"), ResourceLocation.withDefaultNamespace("healing")),
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 5, null);
            queue.append(order);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            supply(fixture, 6, 3);
            SettlementBackgroundBrewing.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(queue.pending(order.id()), 3, "one real three-bottle batch is reserved before processing");
            helper.assertTrue(queue.cancel(order.id()), "future bottles are cancelled");
            for (int now = 200; now <= 2000; now += 200) {
                fixture.machine().advance(level, now);
                SettlementBackgroundBrewing.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            }
            helper.assertValueEqual(queue.order(order.id()).produced(), 3L, "the in-progress recipe chain finishes exactly three potions");
            helper.assertValueEqual(queue.pending(order.id()), 0, "the finished batch releases its reservation");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 3, "no extra bottle is filled after cancellation");
            helper.assertValueEqual(fixture.supply().getItem(1).getCount(), 2, "only one real wart is consumed");
            helper.assertValueEqual(fixture.supply().getItem(2).getCount(), 2, "only one real melon is consumed");
            helper.assertTrue(com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations.get(level).available(level, fixture.machine().position()),
                    "completion releases the shared stand");
            helper.assertFalse(level.hasChunkAt(fixture.town().center()), "brewing does not load the worksite chunk");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void replacementBrewerContinuesTheOriginalSharedPotionReceipt(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.town().setWorksitePlan(fixture.town().leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
            var queue = fixture.town().beginProduction(fixture.town().leader(), fixture.area().id());
            var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.brewing(
                            ResourceLocation.withDefaultNamespace("potion"), ResourceLocation.withDefaultNamespace("healing")),
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 1, null);
            queue.append(order);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            supply(fixture, 2, 2);
            SettlementBackgroundBrewing.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            var saved = fixture.worker().save();
            fixture.town().removeWorker(fixture.worker().id());
            var next = UUID.randomUUID();
            saved.putUUID("Id", next);
            var replacement = SettlementWorkerState.load(saved);
            fixture.town().assignDuty(next, fixture.area().id());
            fixture.town().recordWorker(replacement);
            for (int now = 200; now <= 1600; now += 200) {
                fixture.machine().advance(level, now);
                SettlementBackgroundBrewing.tick(level, fixture.town(), replacement, 200, 1.0D);
            }
            helper.assertValueEqual(queue.order(order.id()).produced(), 1L, "replacement staff finish the original potion");
            helper.assertValueEqual(queue.pending(order.id()), 0, "the original receipt is completed once");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 1, "handoff never consumes a second bottle");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void legacySharedBrewingPersistsBothActiveBatchesBeforeClearingOrders(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
            fixture.worker().brewingOrder(order("potion", 3, false));
            fixture.worker().settings().putLong("BrewingCachedStandPos", fixture.machine().position().asLong());
            var second = SettlementBrewingStandState.capture(level, fixture.area().id(),
                    new BrewingStandBlockEntity(fixture.machine().position().east(), Blocks.BREWING_STAND.defaultBlockState()), 0L);
            town.recordBrewingStand(second);
            for (var machine : List.of(fixture.machine(), second)) {
                var items = machine.items(level.registryAccess());
                for (int slot = 0; slot < 3; slot++) items.set(slot, PotionContents.createItemStack(Items.POTION, Potions.WATER));
                items.set(3, new ItemStack(Items.NETHER_WART));
                items.set(4, new ItemStack(Items.BLAZE_POWDER));
                machine.updateItems(level, items);
                machine.advance(level, 120L);
            }
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(town.leader(), "BrewMigration"));
            var requested = HiredProductionOrder.create(level, HiredProductionRecipe.brewing(
                    ResourceLocation.withDefaultNamespace("potion"), ResourceLocation.withDefaultNamespace("regeneration")),
                    HiredProductionOrder.Mode.BATCH, 1, null);
            helper.assertValueEqual(SettlementProductionService.append(player, town, fixture.area().id(), 0L, requested), "", "the legacy order and both existing batches convert");
            var queue = town.productionQueues().get(fixture.area().id());
            var legacy = queue.orders().getFirst();
            helper.assertValueEqual(queue.orders().size(), 2, "one legacy entry precedes the requested order");
            helper.assertValueEqual(legacy.amount(), 6, "both already consumed batches are retained even above the old remaining target");
            helper.assertValueEqual(queue.pending(legacy.id()), 6, "all six existing bottles count before any worker resumes");
            helper.assertValueEqual(queue.allocations().stream().filter(allocation -> allocation.worker() == null).count(), 1L, "the extra native batch waits for an available brewer");
            helper.assertFalse(BrewingWorker.hasOrder(town.workers().get(fixture.worker().id()).settings()), "the canonical legacy order clears after durable conversion");
            helper.assertFalse(SettlementBrewingService.configure(player, town, fixture.worker().id(), fixture.area().id(), order("potion", 1, false)), "legacy controls cannot recreate a second order after conversion");
            helper.assertFalse(SettlementProductionService.append(player, town, fixture.area().id(), 0L, requested).isEmpty(), "a replayed append has a stale revision");
            try {
                var file = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/villagerretaliation_settlements.dat");
                var disk = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                var restored = SettlementSavedData.load(disk.getCompound("data"), level.registryAccess()).get(town.id()).orElseThrow();
                helper.assertValueEqual(restored.productionQueues().get(fixture.area().id()).save(level.registryAccess()), queue.save(level.registryAccess()), "the complete converted queue is already on disk");
                helper.assertFalse(BrewingWorker.hasOrder(restored.workers().get(fixture.worker().id()).settings()), "disk recovery cannot import the old order again");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
            var stations = HiredProductionStations.get(level);
            var allocation = queue.allocation(fixture.worker().id());
            stations.release(allocation.id());
            helper.assertValueEqual(stations.claim(level, allocation.station()).machine(), fixture.machine().id(), "the canonical queue restores a missing station index entry");
            var staleIndex = stations.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
            for (var raw : staleIndex.getList("Claims", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var entry = (net.minecraft.nbt.CompoundTag) raw;
                entry.putBoolean("Started", false);
                entry.putLong("Expires", 0L);
            }
            var restoredIndex = HiredProductionStations.load(staleIndex, level.registryAccess());
            helper.assertTrue(restoredIndex.claim(level, allocation.station()).started(), "the saved queue repairs a stale unstarted index without expiring consumed work");
            helper.assertTrue(restoredIndex.reconcile(level, allocation.station(), UUID.randomUUID()) == null, "a verified replacement releases only the original machine's receipt");
            helper.assertValueEqual(queue.pending(legacy.id()), 3, "replaced machines do not credit foreign potions");
            helper.assertValueEqual(queue.order(legacy.id()).produced(), 0L, "lost work is never reported as output");
            var retained = queue.reserve(fixture.worker().id(), legacy.id(), 3, 1, 0L, fixture.machine().position());
            queue.bindMachine(retained.id(), fixture.machine().id());
            queue.start(retained.id());
            helper.assertFalse(level.hasChunkAt(town.center()), "conversion and station recovery do not load the worksite");
            queue.cancel(legacy.id());
            fixture.supply().setItem(2, new ItemStack(Items.GLISTERING_MELON_SLICE, 2));
            var worker = town.workers().get(fixture.worker().id());
            for (int now = 320; now <= 3520; now += 200) {
                fixture.machine().advance(level, now);
                second.advance(level, now);
                SettlementBackgroundBrewing.tick(level, town, worker, 200, 1.0D);
            }
            helper.assertValueEqual(queue.order(legacy.id()).produced(), 6L, "a cancelled converted order finishes both actual batches exactly once");
            helper.assertValueEqual(queue.pending(legacy.id()), 0, "all imported receipts settle");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void matchingLegacyBrewersKeepDistinctOrdersDuringConversion(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
            fixture.worker().brewingOrder(order("potion", 2, false));
            var saved = fixture.worker().save();
            var secondId = UUID.randomUUID();
            saved.putUUID("Id", secondId);
            var second = SettlementWorkerState.load(saved);
            second.brewingOrder(order("potion", 7, false));
            helper.assertTrue(town.assignDuty(secondId, fixture.area().id()), "the second brewer has the same home worksite");
            town.recordWorker(second);
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(town.leader(), "BrewMigration"));
            var requested = HiredProductionOrder.create(level, HiredProductionRecipe.brewing(
                    ResourceLocation.withDefaultNamespace("potion"), ResourceLocation.withDefaultNamespace("regeneration")),
                    HiredProductionOrder.Mode.BATCH, 1, null);
            helper.assertValueEqual(SettlementProductionService.append(player, town, fixture.area().id(), 0L, requested), "", "matching legacy targets convert independently");
            var queue = town.productionQueues().get(fixture.area().id());
            helper.assertValueEqual(queue.orders().size(), 3, "two matching legacy entries are not merged");
            helper.assertValueEqual(queue.orders().subList(0, 2).stream().map(HiredProductionOrder::amount).sorted().toList(), List.of(2, 7), "each original quantity survives");
            helper.assertTrue(queue.orders().subList(0, 2).stream().map(HiredProductionOrder::id).distinct().count() == 2L, "each imported order has its own identity");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_brewing")
    public static void unknownLegacyActiveBatchesBlockConversionWithoutLosingOrders(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
            fixture.worker().brewingOrder(order("potion", 2, false));
            var items = fixture.machine().items(level.registryAccess());
            items.set(0, PotionContents.createItemStack(Items.POTION, Potions.REGENERATION));
            fixture.machine().updateItems(level, items);
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(town.leader(), "BrewMigration"));
            var requested = HiredProductionOrder.create(level, HiredProductionRecipe.brewing(
                    ResourceLocation.withDefaultNamespace("potion"), ResourceLocation.withDefaultNamespace("regeneration")),
                    HiredProductionOrder.Mode.BATCH, 1, null);
            helper.assertFalse(SettlementProductionService.append(player, town, fixture.area().id(), 0L, requested).isEmpty(), "an unmatched existing potion requires recovery");
            helper.assertFalse(town.productionQueues().containsKey(fixture.area().id()), "blocked conversion creates no partial queue");
            helper.assertValueEqual(SettlementBrewingOrder.read(fixture.worker().settings()).amount(), 2, "the legacy order remains intact");
            helper.assertTrue(ItemStack.isSameItemSameComponents(fixture.machine().items(level.registryAccess()).get(0), items.get(0)), "the unmatched potion remains intact");
            helper.assertFalse(level.hasChunkAt(town.center()), "blocked conversion never forces a chunk load");
        });
    }

    private static void compare(GameTestHelper helper, ItemStack bottle, ItemStack ingredient, ItemStack fuel, int ticks) {
        var stand = place(helper, bottle, ingredient, fuel);
        var state = SettlementBrewingStandState.capture(helper.getLevel(), UUID.randomUUID(), stand, 0L);
        advance(helper, state, ticks);
        for (int tick = 0; tick < ticks; tick++) {
            BrewingStandBlockEntity.serverTick(helper.getLevel(), stand.getBlockPos(), helper.getLevel().getBlockState(stand.getBlockPos()), stand);
        }
        var actual = stand.saveWithoutMetadata(helper.getLevel().registryAccess());
        var background = state.save().getCompound("Contents");
        for (String key : List.of("Items", "BrewTime", "Fuel")) {
            helper.assertValueEqual(background.get(key), actual.get(key), "background and vanilla agree on " + key + " after " + ticks + " ticks");
        }
    }

    private static void advance(GameTestHelper helper, SettlementBrewingStandState state, int ticks) {
        for (int elapsed = 0; elapsed < ticks; elapsed += 200) {
            state.advance(helper.getLevel(), Math.min(ticks, elapsed + 200));
        }
    }

    private static BrewingStandBlockEntity place(GameTestHelper helper, ItemStack bottle, ItemStack ingredient, ItemStack fuel) {
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(position, Blocks.BREWING_STAND.defaultBlockState());
        var stand = (BrewingStandBlockEntity) helper.getLevel().getBlockEntity(position);
        stand.setItem(0, bottle.copy());
        stand.setItem(3, ingredient.copy());
        stand.setItem(4, fuel.copy());
        return stand;
    }

    private static SettlementBrewingOrder order(String item, int amount, boolean continuous) {
        return new SettlementBrewingOrder(ResourceLocation.withDefaultNamespace(item), ResourceLocation.withDefaultNamespace("healing"), amount, continuous);
    }

    private static void supply(Fixture fixture, int bottles, int batches) {
        fixture.supply().setItem(0, new ItemStack(Items.GLASS_BOTTLE, bottles));
        fixture.supply().setItem(1, new ItemStack(Items.NETHER_WART, batches));
        fixture.supply().setItem(2, new ItemStack(Items.GLISTERING_MELON_SLICE, batches));
        fixture.supply().setItem(3, new ItemStack(Items.BLAZE_POWDER));
    }

    private static void run(GameTestHelper helper, Fixture fixture, int from, int until) {
        for (int now = from; now <= until; now += 200) {
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
        }
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos center = new BlockPos(10_000_000 + origin.getX() * 8, 64, 10_000_000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.BREWERY, "Brewery", center, center.offset(3, 2, 3), 5, true);
        town.putArea(town.leader(), area);
        town.recordSurvey(area.id(), new SettlementBuildingSurvey.Survey(0L, List.of(), List.of(center), true,
                Map.of(center, ResourceLocation.withDefaultNamespace("brewing_stand")), List.of(center.east())));
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
        villager.setPos(center.getCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.CLERIC));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        var stand = new BrewingStandBlockEntity(center, Blocks.BREWING_STAND.defaultBlockState());
        var machine = SettlementBrewingStandState.capture(level, area.id(), stand, 0L);
        town.recordBrewingStand(machine);
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, area, supply, output, villager, worker, machine));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementDepot supply, SettlementDepot output,
                           Villager villager, SettlementWorkerState worker, SettlementBrewingStandState machine) {
    }
}
