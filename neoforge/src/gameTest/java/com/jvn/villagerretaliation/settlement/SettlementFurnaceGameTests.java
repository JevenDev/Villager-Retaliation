package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementFurnaceGameTests {
    private SettlementFurnaceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void backgroundFurnaceMatchesVanillaFuelAndCooking(GameTestHelper helper) {
        compare(helper, Blocks.FURNACE, new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.COAL), ItemStack.EMPTY, 400);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void backgroundFastFurnacesMatchVanillaAndKeepFuelRemainders(GameTestHelper helper) {
        compare(helper, Blocks.BLAST_FURNACE, new ItemStack(Items.RAW_IRON, 4), new ItemStack(Items.COAL), ItemStack.EMPTY, 200);
        compare(helper, Blocks.SMOKER, new ItemStack(Items.POTATO, 4), new ItemStack(Items.LAVA_BUCKET), ItemStack.EMPTY, 200);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void blockedFurnaceDoesNotConsumeNewFuel(GameTestHelper helper) {
        compare(helper, Blocks.FURNACE, new ItemStack(Items.RAW_IRON), new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_INGOT, 64), 200);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void backgroundFurnacePreservesWetSpongeBucketConversion(GameTestHelper helper) {
        compare(helper, Blocks.FURNACE, new ItemStack(Items.WET_SPONGE), new ItemStack(Items.LAVA_BUCKET), ItemStack.EMPTY, 200);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void savedFurnaceWorkReconcilesOnceAndPreservesContainerName(GameTestHelper helper) {
        var level = helper.getLevel();
        var furnace = place(helper, Blocks.FURNACE, new ItemStack(Items.RAW_IRON, 2), new ItemStack(Items.COAL), ItemStack.EMPTY);
        CompoundTag named = furnace.saveWithoutMetadata(level.registryAccess());
        named.putString("CustomName", Component.Serializer.toJson(Component.literal("Town forge"), level.registryAccess()));
        furnace.loadWithComponents(named, level.registryAccess());
        var state = SettlementFurnaceState.capture(level, UUID.randomUUID(), furnace, 0L);
        state.advance(level, 200L);
        var restored = SettlementFurnaceState.load(state.save());
        helper.assertTrue(restored.reconcile(level.registryAccess(), furnace), "saved work can replace its unchanged baseline");
        helper.assertValueEqual(furnace.getItem(2).getCount(), 1, "one completed ingot returns to the real furnace");
        helper.assertValueEqual(furnace.getName().getString(), "Town forge", "container metadata survives the handoff");
        furnace.removeItem(2, 1);
        helper.assertTrue(restored.reconcile(level.registryAccess(), furnace), "repeated reconciliation is harmless");
        helper.assertTrue(furnace.getItem(2).isEmpty(), "taking the result cannot cause it to be recreated");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void conflictingFurnaceChangesRemainUntouchedAndPendingWorkIsRetained(GameTestHelper helper) {
        var level = helper.getLevel();
        var furnace = place(helper, Blocks.FURNACE, new ItemStack(Items.RAW_IRON), new ItemStack(Items.COAL), ItemStack.EMPTY);
        var state = SettlementFurnaceState.capture(level, UUID.randomUUID(), furnace, 0L);
        state.advance(level, 200L);
        furnace.setItem(0, new ItemStack(Items.DIAMOND));
        helper.assertFalse(state.reconcile(level.registryAccess(), furnace), "unexpected live contents prevent replacement");
        helper.assertTrue(furnace.getItem(0).is(Items.DIAMOND), "the changed inventory stays intact");
        var restored = SettlementFurnaceState.load(state.save());
        helper.assertTrue(restored.pending() && restored.conflict(), "unapplied work and its conflict persist");
        helper.assertTrue(restored.items(level.registryAccess()).get(2).is(Items.IRON_INGOT), "pending output remains recoverable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void furnaceClockIsBoundedAndRepeatedTimeCannotProduceTwice(GameTestHelper helper) {
        var level = helper.getLevel();
        var furnace = place(helper, Blocks.FURNACE, new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.COAL), ItemStack.EMPTY);
        var state = SettlementFurnaceState.capture(level, UUID.randomUUID(), furnace, 0L);
        state.advance(level, 1_000_000L);
        helper.assertValueEqual(state.items(level.registryAccess()).get(2).getCount(), 1, "a long gap only processes one bounded interval");
        state.advance(level, 1_000_000L);
        helper.assertValueEqual(state.items(level.registryAccess()).get(2).getCount(), 1, "the same server time cannot be counted twice");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void unloadedSmithSuppliesSmeltsAndDeliversRealOre(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            fixture.supply().setItem(0, new ItemStack(Items.RAW_IRON, 2));
            fixture.supply().setItem(1, new ItemStack(Items.COAL));
            for (int now = 200; now <= 1_200; now += 200) {
                SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
            }
            helper.assertTrue(fixture.supply().isEmpty(), "real ore and fuel leave the depot");
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.IRON_INGOT)), 2, "only two real ingots are delivered");
            helper.assertTrue(fixture.machine().items(helper.getLevel().registryAccess()).get(0).isEmpty(), "the furnace consumes its actual inputs");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "production does not load the distant chunk");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess(), () -> { });
            helper.assertValueEqual(restored.furnaces().size(), 1, "the furnace survives a settlement save");
            helper.assertTrue(restored.furnaces().values().iterator().next().pending(), "unloaded changes await their physical block");
            helper.assertTrue(SettlementPracticeGameTests.xp(profile) > 0.0D, "collected smelting output earns smithing practice");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void unloadedCookUsesSmokerAndReturnsTheFuelBucket(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            fixture.supply().setItem(0, new ItemStack(Items.POTATO, 2));
            fixture.supply().setItem(1, new ItemStack(Items.LAVA_BUCKET));
            for (int now = 200; now <= 1_200; now += 200) {
                SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
            }
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.BAKED_POTATO)), 2, "the kitchen delivers cooked food");
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.BUCKET)), 1, "the fuel bucket is returned once");
            helper.assertTrue(fixture.supply().isEmpty(), "food and fuel are not invented");
            helper.assertTrue(SettlementPracticeGameTests.xp(profile) > 0.0D, "collected cooked food earns practice");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void furnaceWorkersRespectSourceRulesAndFullCarriedOutput(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            ItemStack rule = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
            VillagerItemFilterData.setEntry(rule, 0, new ItemStack(Items.RAW_IRON));
            VillagerFilterPolicy.setDirection(rule, VillagerFilterPolicy.TransferDirection.PROVIDE);
            VillagerFilterPolicy.setListMode(rule, VillagerFilterPolicy.ListMode.DENY_MATCHING);
            fixture.supply().observeRules(List.of(rule));
            fixture.supply().setItem(0, new ItemStack(Items.RAW_IRON));
            fixture.supply().setItem(1, new ItemStack(Items.COAL));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.supply().count(stack -> !stack.isEmpty()), 2, "source rules block supply collection");
            fixture.supply().observeRules(List.of());
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            var carried = fixture.worker().items(helper.getLevel().registryAccess());
            for (int slot : fixture.worker().outputSlots()) {
                carried.set(slot, new ItemStack(Items.DIRT, 64));
            }
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), carried);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 600L);
            helper.assertTrue(fixture.machine().items(helper.getLevel().registryAccess()).get(2).is(Items.IRON_INGOT),
                    "a full worker leaves the finished item in its finite furnace slot");
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), 0.0D, "supply collection and blocked output grant no practice");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void recentVanillaTicksAreExcludedFromBackgroundElapsedTime(GameTestHelper helper) {
        var level = helper.getLevel();
        var furnace = place(helper, Blocks.FURNACE, new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.COAL), ItemStack.EMPTY);
        var state = SettlementFurnaceState.capture(level, UUID.randomUUID(), furnace, level.getGameTime() - 200L);
        AbstractFurnaceBlockEntity.serverTick(level, furnace.getBlockPos(), level.getBlockState(furnace.getBlockPos()), furnace);
        state.refresh(level.registryAccess(), furnace);
        state.advance(level, level.getGameTime() + 50L);
        helper.assertValueEqual(state.save().getCompound("Contents").getInt("CookTime"), 51,
                "background elapsed time begins after the most recent actual block tick");
        helper.assertTrue(state.items(level.registryAccess()).get(2).isEmpty(), "active time cannot produce a second background result");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void chunkLifecycleCapturesFinalFurnaceInventoryAndAppliesPendingWork(GameTestHelper helper) {
        var level = helper.getLevel();
        var furnace = place(helper, Blocks.FURNACE, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
        BlockPos position = furnace.getBlockPos();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), position, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), position, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.SMELTERY, "Forge", position, position, 5, true);
        town.putArea(town.leader(), area);
        var chunk = level.getChunkAt(position);
        SettlementFurnaceService.onChunkUnload(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));
        AbstractFurnaceBlockEntity.serverTick(level, position, level.getBlockState(position), furnace);
        SettlementFurnaceService.onChunkUnload(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));
        var state = town.furnaces().get(position);
        helper.assertTrue(state != null && state.items(level.registryAccess()).get(0).is(Items.RAW_IRON),
                "the final chunk snapshot discovers the furnace and its latest contents");
        helper.assertTrue(level.getBlockState(position).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),
                "a final snapshot cannot replace a newly lit furnace with its older block state");
        state.advance(level, level.getGameTime() + 200L);
        SettlementFurnaceService.onChunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, false));
        helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT), "chunk loading applies the pending result");
        helper.assertTrue(furnace.getItem(0).isEmpty(), "the stale input is removed before active processing resumes");
        helper.assertTrue(level.getBlockState(position).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),
                "the returned block reflects its remaining fuel");
        furnace.removeItem(2, 1);
        SettlementFurnaceService.onChunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, false));
        helper.assertTrue(furnace.getItem(2).isEmpty(), "repeated chunk loading cannot refill a collected result");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void sharedKitchenAndSmelteryConsumeOneSavedFurnaceInventory(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var level = helper.getLevel();
            var original = fixture.town().areas().get(fixture.worker().area());
            var kitchen = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Shared kitchen",
                    original.min(), original.max(), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), kitchen), "the kitchen shares the existing smeltery");
            Villager cook = EntityType.VILLAGER.create(level);
            cook.setPos(fixture.town().center().getCenter());
            cook.setNoAi(true);
            cook.setVillagerData(cook.getVillagerData().setProfession(VillagerProfession.FARMER));
            VillageAllegianceApi.assignKnown(level, cook, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
            fixture.town().assignDuty(cook.getUUID(), kitchen.id());
            SettlementWorkforceService.reconcile(level, cook);
            ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
            VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.BAKED_POTATO));
            HiredJobInventory.getJobInventory(cook).setItem(HiredJobInventory.FILTER_SLOT, filter);
            fixture.town().recordWorker(SettlementWorkerState.capture(level, cook, SettlementWorkforceService.duty(cook).orElseThrow(), 0L));
            fixture.town().recordResident(SettlementResidentState.capture(cook, fixture.town(), 0L));
            fixture.supply().setItem(0, new ItemStack(Items.RAW_IRON, 2));
            fixture.supply().setItem(1, new ItemStack(Items.POTATO, 2));
            fixture.supply().setItem(2, new ItemStack(Items.COAL));
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            var town = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            var machine = town.furnaces().get(fixture.machine().position());
            var saved = machine.save();
            SettlementBackgroundService.update(level, town, 400L);
            helper.assertValueEqual(machine.save(), saved, "replaying a saved interval cannot advance shared machinery twice");
            for (int now = 600; now <= 4_000; now += 200) SettlementBackgroundService.update(level, town, now);
            var output = town.depots().get(fixture.output().id());
            helper.assertValueEqual(output.count(stack -> stack.is(Items.IRON_INGOT)), 2, "the smelter delivers only the two funded ingots");
            helper.assertValueEqual(output.count(stack -> stack.is(Items.BAKED_POTATO)), 2, "the cook uses the same furnace for two real potatoes");
            helper.assertTrue(town.depots().get(fixture.supply().id()).isEmpty(), "both workers consume finite shared stock");
            helper.assertValueEqual(town.furnaces().size(), 1, "different workplace duties never create a second processing inventory");
            helper.assertValueEqual(machine.id(), fixture.machine().id(), "saving and shared access preserve the physical machine identity");
            helper.assertFalse(level.hasChunkAt(machine.position()), "sharing machinery does not force its chunk to load");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void sharedFurnaceKeepsProcessingWhenItsOriginalDesignationIsDisabledOrRemoved(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var original = town.areas().get(fixture.worker().area());
            var kitchen = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Shared kitchen",
                    original.min(), original.max(), 5, true);
            helper.assertTrue(town.putArea(town.leader(), kitchen), "the second workplace is registered");
            var items = fixture.machine().items(level.registryAccess());
            items.set(0, new ItemStack(Items.POTATO, 4));
            items.set(1, new ItemStack(Items.COAL));
            fixture.machine().updateItems(level, items);
            helper.assertTrue(town.putArea(town.leader(), new SettlementArea(original.id(), original.type(), original.name(),
                    original.min(), original.max(), original.priority(), false)), "the original workplace is paused");
            SettlementFurnaceService.update(level, town, 200L);
            helper.assertValueEqual(fixture.machine().items(level.registryAccess()).get(2).getCount(), 1,
                    "the enabled kitchen keeps the shared furnace operating");
            helper.assertTrue(town.removeArea(town.leader(), original.id()), "the original workplace can be removed");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            SettlementFurnaceService.update(level, restored, 400L);
            var machine = restored.furnaces().get(fixture.machine().position());
            helper.assertValueEqual(machine.items(level.registryAccess()).get(2).getCount(), 2,
                    "the remaining designation still operates the saved machine");
            restored.putArea(restored.leader(), new SettlementArea(kitchen.id(), kitchen.type(), kitchen.name(),
                    kitchen.min(), kitchen.max(), kitchen.priority(), false));
            SettlementFurnaceService.update(level, restored, 600L);
            helper.assertValueEqual(machine.items(level.registryAccess()).get(2).getCount(), 2,
                    "pausing every compatible workplace stops background processing");
            restored.putArea(restored.leader(), kitchen);
            SettlementFurnaceService.update(level, restored, 800L);
            helper.assertValueEqual(machine.items(level.registryAccess()).get(2).getCount(), 3,
                    "reenabling a shared workplace resumes one processing interval");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void repeatedSharedSurveysKeepThePhysicalFurnaceIdentityAndNativeHandoff(GameTestHelper helper) {
        var level = helper.getLevel();
        var furnace = place(helper, Blocks.FURNACE, new ItemStack(Items.RAW_IRON), new ItemStack(Items.COAL), ItemStack.EMPTY);
        var position = furnace.getBlockPos();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), position, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), position, 0L).orElseThrow();
        var smeltery = new SettlementArea(UUID.randomUUID(), SettlementAreaType.SMELTERY, "Smeltery", position, position, 5, true);
        var kitchen = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Kitchen", position, position, 5, true);
        town.putArea(town.leader(), smeltery);
        var state = SettlementFurnaceState.capture(level, smeltery.id(), furnace, level.getGameTime());
        town.recordFurnace(state);
        helper.assertTrue(town.putArea(town.leader(), kitchen), "the kitchen can share the physical furnace");
        SettlementBuildingService.refresh(level, town, kitchen);
        SettlementBuildingService.refresh(level, town, smeltery);
        SettlementFurnaceService.update(level, town, level.getGameTime());
        helper.assertTrue(town.furnaces().get(position) == state, "surveying another use does not replace the shared ledger");
        town.removeArea(town.leader(), smeltery.id());
        var chunk = level.getChunkAt(position);
        SettlementFurnaceService.onChunkUnload(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));
        helper.assertTrue(town.furnaces().get(position) == state, "the remaining area does not recapture the machine on unload");
        state.advance(level, level.getGameTime() + 200L);
        var saved = SettlementFurnaceState.load(state.save());
        SettlementFurnaceService.onChunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, false));
        helper.assertValueEqual(furnace.getItem(2).getCount(), 1, "pending shared output reaches the physical furnace once");
        furnace.removeItem(2, 1);
        town.recordFurnace(saved);
        SettlementFurnaceService.onChunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, false));
        helper.assertTrue(furnace.getItem(2).isEmpty() && furnace.getItem(0).isEmpty(),
                "an older shared ledger cannot restore collected output or consumed input");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void sharingAWorkplaceDoesNotMakeSpecializedFurnacesInterchangeable(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            var level = helper.getLevel();
            var kitchen = fixture.town().areas().get(fixture.worker().area());
            var smeltery = new SettlementArea(UUID.randomUUID(), SettlementAreaType.SMELTERY, "Shared smeltery",
                    kitchen.min(), kitchen.max(), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), smeltery), "both workplaces are registered");
            helper.assertFalse(SettlementFurnaceService.serves(smeltery, fixture.machine()), "a smoker cannot serve smelting work");
            var blast = SettlementFurnaceState.capture(level, smeltery.id(),
                    new net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity(kitchen.min(), Blocks.BLAST_FURNACE.defaultBlockState()), 0L);
            helper.assertFalse(SettlementFurnaceService.serves(kitchen, blast), "a blast furnace cannot serve kitchen work");
            helper.assertTrue(SettlementFurnaceService.serves(smeltery, blast), "the blast furnace still serves its matching workplace");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void unloadedProductionFinishesCancelledFurnaceInputsWithoutAddingMore(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var level = helper.getLevel();
            var area = fixture.town().areas().get(fixture.worker().area());
            fixture.town().setWorksitePlan(fixture.town().leader(), area.id(), SettlementWorksitePlan.defaults(area.type().role()));
            var queue = fixture.town().beginProduction(fixture.town().leader(), area.id());
            var order = HiredProductionOrder.create(level, HiredProductionRecipe.exact(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"), java.util.Map.of()),
                    HiredProductionOrder.Mode.BATCH, 3, null);
            queue.append(order);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            fixture.supply().setItem(0, new ItemStack(Items.RAW_IRON, 8));
            fixture.supply().setItem(1, new ItemStack(Items.COAL, 8));
            SettlementBackgroundProductionFurnace.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(fixture.machine().items(level.registryAccess()).getFirst().getCount(), 3, "only the finite shared batch is loaded");
            helper.assertTrue(queue.cancel(order.id()), "future production is cancelled");
            fixture.machine().advance(level, 200L);
            SettlementBackgroundProductionFurnace.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(queue.order(order.id()).produced(), 1L, "one native output is collected after its cooking interval");
            helper.assertValueEqual(queue.pending(order.id()), 2, "remaining native input stays reserved");
            fixture.machine().advance(level, 400L);
            fixture.machine().advance(level, 600L);
            SettlementBackgroundProductionFurnace.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            SettlementBackgroundProductionFurnace.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(queue.order(order.id()).produced(), 3L, "the cancelled in-progress batch finishes once");
            helper.assertValueEqual(queue.pending(order.id()), 0, "completion releases the shared quantity");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 5, "unneeded raw materials stay in storage");
            helper.assertValueEqual(fixture.supply().getItem(1).getCount(), 7, "one real coal supplies this batch");
            helper.assertFalse(level.hasChunkAt(fixture.town().center()), "production does not load the furnace chunk");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void anotherWorkerCanCollectTheSameCivicFurnaceBatchAfterDeath(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
            var level = helper.getLevel();
            var area = fixture.town().areas().get(fixture.worker().area());
            fixture.town().setWorksitePlan(fixture.town().leader(), area.id(), SettlementWorksitePlan.defaults(area.type().role()));
            var queue = fixture.town().beginProduction(fixture.town().leader(), area.id());
            var order = HiredProductionOrder.create(level, HiredProductionRecipe.exact(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"), java.util.Map.of()),
                    HiredProductionOrder.Mode.BATCH, 1, null);
            queue.append(order);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            fixture.supply().setItem(0, new ItemStack(Items.RAW_IRON, 2));
            fixture.supply().setItem(1, new ItemStack(Items.COAL));
            SettlementBackgroundProductionFurnace.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            var saved = fixture.worker().save();
            fixture.town().removeWorker(fixture.worker().id());
            var nextId = UUID.randomUUID();
            saved.putUUID("Id", nextId);
            var replacement = SettlementWorkerState.load(saved);
            fixture.town().assignDuty(nextId, area.id());
            fixture.town().recordWorker(replacement);
            fixture.machine().advance(level, 200L);
            SettlementBackgroundProductionFurnace.tick(level, fixture.town(), replacement, 200, 1.0D);
            helper.assertValueEqual(queue.order(order.id()).produced(), 1L, "replacement staff complete the existing receipt");
            helper.assertValueEqual(queue.pending(order.id()), 0, "handoff releases the original allocation");
            helper.assertTrue(HiredProductionStations.get(level).available(level, fixture.machine().position()), "the shared station is released once");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 1, "handoff does not load another input");
        });
    }

    private static void compare(GameTestHelper helper, Block block, ItemStack input, ItemStack fuel, ItemStack output, int ticks) {
        var level = helper.getLevel();
        var furnace = place(helper, block, input, fuel, output);
        var state = SettlementFurnaceState.capture(level, UUID.randomUUID(), furnace, 0L);
        for (int elapsed = 0; elapsed < ticks; elapsed += 200) {
            state.advance(level, Math.min(ticks, elapsed + 200));
        }
        for (int tick = 0; tick < ticks; tick++) {
            AbstractFurnaceBlockEntity.serverTick(level, furnace.getBlockPos(), level.getBlockState(furnace.getBlockPos()), furnace);
        }
        var actual = furnace.saveWithoutMetadata(level.registryAccess());
        var background = state.save().getCompound("Contents");
        for (String key : List.of("Items", "BurnTime", "CookTime", "CookTimeTotal", "RecipesUsed")) {
            helper.assertValueEqual(background.get(key), actual.get(key), "background and vanilla agree on " + key);
        }
    }

    private static AbstractFurnaceBlockEntity place(GameTestHelper helper, Block block, ItemStack input, ItemStack fuel, ItemStack output) {
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(position, block.defaultBlockState());
        var furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(position);
        furnace.setItem(0, input.copy());
        furnace.setItem(1, fuel.copy());
        furnace.setItem(2, output.copy());
        return furnace;
    }

    private static void fixture(GameTestHelper helper, boolean cook, Consumer<Fixture> check) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos center = new BlockPos(10_000_000 + origin.getX() * 8, 64, 10_000_000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), cook ? SettlementAreaType.KITCHEN : SettlementAreaType.SMELTERY,
                "Furnaces", center, center.offset(3, 2, 3), 5, true);
        town.putArea(town.leader(), area);
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage",
                center.east(8), center.offset(10, 2, 2), 5, true);
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
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(cook ? Items.BAKED_POTATO : Items.IRON_INGOT));
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.FILTER_SLOT, filter);
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        AbstractFurnaceBlockEntity furnace = cook ? new SmokerBlockEntity(center, Blocks.SMOKER.defaultBlockState())
                : new FurnaceBlockEntity(center, Blocks.FURNACE.defaultBlockState());
        var machine = SettlementFurnaceState.capture(level, area.id(), furnace, 0L);
        town.recordFurnace(machine);
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, supply, output, worker, machine));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }


    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void collectingAFurnaceFuelRemainderDoesNotGrantProductionPractice(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            var items = fixture.machine().items(helper.getLevel().registryAccess());
            items.set(1, new ItemStack(Items.BUCKET));
            fixture.machine().updateItems(helper.getLevel(), items);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.machine().items(helper.getLevel().registryAccess()).get(1).isEmpty(), "the real fuel bucket was collected");
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), 0.0D, "collecting a container grants no production practice");
        });
    }

    private record Fixture(SettlementRecord town, SettlementDepot supply, SettlementDepot output,
                           SettlementWorkerState worker, SettlementFurnaceState machine) {
    }
}
