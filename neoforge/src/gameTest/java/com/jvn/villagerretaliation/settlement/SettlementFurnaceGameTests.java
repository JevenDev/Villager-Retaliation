package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
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

@GameTestHolder
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
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void unloadedCookUsesSmokerAndReturnsTheFuelBucket(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            fixture.supply().setItem(0, new ItemStack(Items.POTATO, 2));
            fixture.supply().setItem(1, new ItemStack(Items.LAVA_BUCKET));
            for (int now = 200; now <= 1_200; now += 200) {
                SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
            }
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.BAKED_POTATO)), 2, "the kitchen delivers cooked food");
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.BUCKET)), 1, "the fuel bucket is returned once");
            helper.assertTrue(fixture.supply().isEmpty(), "food and fuel are not invented");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_furnaces")
    public static void furnaceWorkersRespectSourceRulesAndFullCarriedOutput(GameTestHelper helper) {
        fixture(helper, false, fixture -> {
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

    private record Fixture(SettlementRecord town, SettlementDepot supply, SettlementDepot output,
                           SettlementWorkerState worker, SettlementFurnaceState machine) {
    }
}
