package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.settlement.SettlementBackgroundConstructionGameTests.Fixture;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementConstructedMachineGameTests {
    private SettlementConstructedMachineGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machines")
    public static void aNewUnloadedFurnaceSmeltsRealDepotOre(GameTestHelper helper) {
        smelting(helper, 20, Blocks.FURNACE, SettlementAreaType.SMELTERY, Items.RAW_IRON, Items.IRON_INGOT);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machines")
    public static void aNewUnloadedBlastFurnaceSmeltsRealDepotOre(GameTestHelper helper) {
        smelting(helper, 21, Blocks.BLAST_FURNACE, SettlementAreaType.SMELTERY, Items.RAW_IRON, Items.IRON_INGOT);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machines")
    public static void aNewUnloadedSmokerCooksRealDepotFood(GameTestHelper helper) {
        smelting(helper, 22, Blocks.SMOKER, SettlementAreaType.KITCHEN, Items.POTATO, Items.BAKED_POTATO);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machines")
    public static void aNewUnloadedBreweryCompletesItsRealPotionOrder(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.capacityFixture(helper, 23, Map.of(BlockPos.ZERO, Blocks.BREWING_STAND.defaultBlockState()), fixture -> {
            build(helper, fixture, Items.BREWING_STAND);
            var area = designate(helper, fixture, SettlementAreaType.BREWERY);
            var worker = staff(helper, fixture, area);
            worker.brewingOrder(new SettlementBrewingOrder(ResourceLocation.withDefaultNamespace("potion"),
                    ResourceLocation.withDefaultNamespace("healing"), 1, false));
            fixture.depot().setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            fixture.depot().setItem(1, new ItemStack(Items.NETHER_WART));
            fixture.depot().setItem(2, new ItemStack(Items.GLISTERING_MELON_SLICE));
            fixture.depot().setItem(3, new ItemStack(Items.BLAZE_POWDER));
            var level = helper.getLevel();
            SettlementBrewingStandService.update(level, fixture.town(), 0L);
            helper.assertValueEqual(fixture.town().brewingStands().size(), 1, "funded construction creates a processing ledger");
            for (int now = 200; now <= 4_000; now += 200) {
                SettlementBrewingStandService.update(level, fixture.town(), now);
                SettlementBackgroundBrewing.tick(level, fixture.town(), worker, 200, 1.0D);
            }
            var expected = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            helper.assertValueEqual(worker.items(level.registryAccess()).stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                    .mapToInt(ItemStack::getCount).sum(), 1, "one real bottle becomes one ordered healing potion");
            helper.assertTrue(fixture.depot().isEmpty(), "both ingredients and the fuel come from finite depot stock");
            var machine = fixture.town().brewingStands().get(fixture.origin());
            helper.assertValueEqual(machine.fuel(), 18, "the two brewing steps spend two native fuel uses");
            helper.assertTrue(machine.pending(), "the new stand still awaits physical delivery");
            helper.assertFalse(level.hasChunkAt(fixture.origin()), "brewing never loads its construction chunk");
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(restored.brewingStands().get(fixture.origin()).id(), machine.id(), "saving preserves the funded machine identity");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machines")
    public static void unfundedAndDisabledConstructionCannotCreateMachines(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.capacityFixture(helper, 24, Map.of(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState()), fixture -> {
            SettlementBackgroundConstructionGameTests.work(helper, fixture, 20);
            SettlementFurnaceService.update(helper.getLevel(), fixture.town(), 0L);
            helper.assertTrue(fixture.town().furnaces().isEmpty(), "an unfunded plan cannot create a machine");
            build(helper, fixture, Items.FURNACE);
            var area = designate(helper, fixture, SettlementAreaType.SMELTERY);
            setEnabled(helper, fixture, area, false);
            SettlementFurnaceService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.town().furnaces().isEmpty(), "a disabled designation does not activate its new furnace");
            setEnabled(helper, fixture, area, true);
            SettlementFurnaceService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(fixture.town().furnaces().size(), 1, "enabling the funded smeltery activates its furnace");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machines")
    public static void newMachinesRespectTheirFunctionalDesignation(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.capacityFixture(helper, 25, Map.of(BlockPos.ZERO, Blocks.BLAST_FURNACE.defaultBlockState()), fixture -> {
            build(helper, fixture, Items.BLAST_FURNACE);
            var area = designate(helper, fixture, SettlementAreaType.KITCHEN);
            SettlementFurnaceService.update(helper.getLevel(), fixture.town(), 0L);
            helper.assertTrue(fixture.town().furnaces().isEmpty(), "a blast furnace cannot serve a kitchen");
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), new SettlementArea(area.id(), SettlementAreaType.SMELTERY,
                    area.name(), area.min(), area.max(), area.priority(), true)), "the completed building can become a smeltery");
            SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), fixture.town().areas().get(area.id()));
            SettlementFurnaceService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.town().furnaces().size(), 1, "the matching designation activates the furnace");
            helper.assertTrue(fixture.town().brewingStands().isEmpty(), "a furnace cannot supply a brewing ledger");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machines")
    public static void pausedConstructedFurnacesKeepInputsWithoutCatchingUp(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.capacityFixture(helper, 26, Map.of(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState()), fixture -> {
            build(helper, fixture, Items.FURNACE);
            var area = designate(helper, fixture, SettlementAreaType.SMELTERY);
            var level = helper.getLevel();
            SettlementFurnaceService.update(level, fixture.town(), 0L);
            var machine = fixture.town().furnaces().get(fixture.origin());
            fill(helper, machine, 2);
            setEnabled(helper, fixture, area, false);
            SettlementFurnaceService.update(level, fixture.town(), 20_000L);
            helper.assertValueEqual(machine.items(level.registryAccess()).get(0).getCount(), 2, "disabled processing retains the real input");
            helper.assertValueEqual(machine.burnTime(), 0, "disabled processing does not ignite fuel");
            setEnabled(helper, fixture, area, true);
            SettlementFurnaceService.update(level, fixture.town(), 20_050L);
            helper.assertTrue(machine.items(level.registryAccess()).get(2).isEmpty(), "only the fifty new ticks count after enabling");
            SettlementFurnaceService.update(level, fixture.town(), 20_200L);
            helper.assertValueEqual(machine.items(level.registryAccess()).get(2).getCount(), 1, "the next real interval completes exactly one ingot");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machine_return")
    public static void constructionConflictsFreezeMachineStockAndRecoverOnDelivery(GameTestHelper helper) {
        queued(helper, Blocks.FURNACE, SettlementAreaType.SMELTERY, null, (fixture, placement) -> {
            var level = helper.getLevel();
            SettlementFurnaceService.update(level, fixture.town(), 0L);
            var machine = fixture.town().furnaces().get(fixture.origin());
            fill(helper, machine, 2);
            var chunk = level.getChunkAt(fixture.origin());
            SettlementFurnaceService.reconcileChunk(level, fixture.town(), chunk);
            helper.assertFalse(machine.conflict(), "loading a chunk before construction delivery does not discard its machine");
            SettlementFurnaceService.update(level, fixture.town(), 100L);
            helper.assertTrue(machine.burnTime() > 0, "a compatible pending placement can process while its chunk is loaded");
            level.setBlock(fixture.origin(), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            SettlementFurnaceService.update(level, fixture.town(), 300L);
            helper.assertTrue(machine.conflict(), "changed construction terrain freezes the processing ledger");
            helper.assertValueEqual(machine.items(level.registryAccess()).get(0).getCount(), 2, "conflicting terrain cannot consume another input");
            var frozen = machine.save().getCompound("Contents").copy();
            SettlementFurnaceService.update(level, fixture.town(), 5_000L);
            helper.assertValueEqual(machine.save().getCompound("Contents"), frozen, "conflicted machines retain their timers and inventory");
            helper.assertFalse(SettlementFurnaceService.available(level, machine, new SettlementConstructionMachines(level, fixture.town(), null)),
                    "workers cannot add or collect stock through a construction conflict");
            helper.assertFalse(placement.reconcile(level, fixture.project().id()), "delivery preserves the changed terrain");
            level.setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(placement.reconcile(level, fixture.project().id()), "restoring the baseline delivers the waiting machine");
            var nativeMachine = (AbstractFurnaceBlockEntity) level.getBlockEntity(fixture.origin());
            helper.assertTrue(machine.matches(nativeMachine) && !machine.pending() && !machine.conflict(), "delivery binds and reconciles the real machine synchronously");
            helper.assertValueEqual(nativeMachine.getItem(0).getCount(), 2, "the physical machine receives its unconsumed inputs");
            for (int tick = 0; tick < 100; tick++) AbstractFurnaceBlockEntity.serverTick(level, fixture.origin(), level.getBlockState(fixture.origin()), nativeMachine);
            helper.assertValueEqual(nativeMachine.getItem(2).getCount(), 1, "native ticking resumes from the exact saved cooking progress");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machine_return")
    public static void staleConstructionCannotRefillCollectedFurnaceOutput(GameTestHelper helper) {
        queued(helper, Blocks.FURNACE, SettlementAreaType.SMELTERY, null, (fixture, placement) -> {
            var level = helper.getLevel();
            SettlementFurnaceService.update(level, fixture.town(), 0L);
            var machine = fixture.town().furnaces().get(fixture.origin());
            fill(helper, machine, 1);
            SettlementFurnaceService.update(level, fixture.town(), 200L);
            var saved = machine.save();
            var journal = placement.save();
            helper.assertTrue(placement.reconcile(level, fixture.project().id()), "the new furnace is delivered with its processing state");
            var nativeMachine = (AbstractFurnaceBlockEntity) level.getBlockEntity(fixture.origin());
            helper.assertValueEqual(nativeMachine.getItem(2).getCount(), 1, "exactly one output reaches the furnace");
            nativeMachine.removeItem(2, 1);
            fixture.town().recordFurnace(SettlementFurnaceState.load(saved));
            helper.assertTrue(SettlementConstructionPlacement.load(journal).reconcile(level, fixture.project().id()), "the placement receipt survives an old journal");
            SettlementFurnaceService.reconcileChunk(level, fixture.town(), level.getChunkAt(fixture.origin()));
            helper.assertTrue(nativeMachine.getItem(2).isEmpty(), "the machine receipt prevents replaying collected output");
            level.setBlock(fixture.origin(), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            SettlementFurnaceService.update(level, fixture.town(), 400L);
            helper.assertTrue(fixture.town().furnaces().isEmpty(), "an applied construction journal cannot recreate a later removed machine");
            helper.assertTrue(level.getBlockState(fixture.origin()).is(Blocks.GOLD_BLOCK), "the player's replacement stays intact");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machine_return")
    public static void aConstructedBrewingStandResumesItsNativeIngredientAndTimer(GameTestHelper helper) {
        queued(helper, Blocks.BREWING_STAND, SettlementAreaType.BREWERY, null, (fixture, placement) -> {
            var level = helper.getLevel();
            SettlementBrewingStandService.update(level, fixture.town(), 0L);
            var machine = fixture.town().brewingStands().get(fixture.origin());
            var items = machine.items(level.registryAccess());
            items.set(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            items.set(3, new ItemStack(Items.NETHER_WART));
            items.set(4, new ItemStack(Items.BLAZE_POWDER));
            machine.updateItems(level, items);
            SettlementBrewingStandService.update(level, fixture.town(), 200L);
            helper.assertValueEqual(machine.brewTime(), 201, "the future stand has a real partly completed batch");
            var saved = machine.save();
            var restored = SettlementBrewingStandState.load(saved);
            fixture.town().recordBrewingStand(restored);
            SettlementBrewingStandService.reconcileChunk(level, fixture.town(), level.getChunkAt(fixture.origin()));
            helper.assertFalse(restored.conflict(), "loading the empty construction position retains the saved batch");
            helper.assertTrue(placement.reconcile(level, fixture.project().id()), "the stand receives the saved batch during placement");
            var stand = (BrewingStandBlockEntity) level.getBlockEntity(fixture.origin());
            helper.assertTrue(restored.matches(stand) && !restored.pending(), "the native stand owns the exact funded identity");
            helper.assertTrue(level.getBlockState(fixture.origin()).getValue(BrewingStandBlock.HAS_BOTTLE[0]), "native bottle visuals match the delivered inventory");
            for (int tick = 0; tick < 201; tick++) BrewingStandBlockEntity.serverTick(level, fixture.origin(), level.getBlockState(fixture.origin()), stand);
            helper.assertTrue(ItemStack.isSameItemSameComponents(stand.getItem(0), PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)),
                    "the active ingredient survives delivery and vanilla finishes on time");
            stand.removeItem(0, 1);
            fixture.town().recordBrewingStand(SettlementBrewingStandState.load(saved));
            helper.assertTrue(placement.reconcile(level, fixture.project().id()), "the same construction receipt remains idempotent");
            SettlementBrewingStandService.reconcileChunk(level, fixture.town(), level.getChunkAt(fixture.origin()));
            helper.assertTrue(stand.getItem(0).isEmpty(), "an old machine save cannot refill a removed potion");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machine_return")
    public static void copiedBlueprintMachineVersionsCannotSuppressPendingDelivery(GameTestHelper helper) {
        var template = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        template.getPersistentData().putUUID("VillagerRetaliationSettlementFurnace", UUID.randomUUID());
        template.getPersistentData().putLong("VillagerRetaliationSettlementFurnaceVersion", Long.MAX_VALUE);
        template.getPersistentData().putLong("VillagerRetaliationSettlementFurnaceTick", Long.MAX_VALUE);
        queued(helper, Blocks.FURNACE, SettlementAreaType.SMELTERY, template.saveWithoutMetadata(helper.getLevel().registryAccess()), (fixture, placement) -> {
            var level = helper.getLevel();
            SettlementFurnaceService.update(level, fixture.town(), 0L);
            var machine = fixture.town().furnaces().get(fixture.origin());
            helper.assertValueEqual(machine.id(), placement.id(), "construction replaces the copied machine identity");
            fill(helper, machine, 1);
            SettlementFurnaceService.update(level, fixture.town(), 200L);
            helper.assertValueEqual(machine.items(level.registryAccess()).get(2).getCount(), 1, "a copied future timestamp cannot stop the new furnace clock");
            helper.assertTrue(placement.reconcile(level, fixture.project().id()), "the copied version cannot skip pending inventory delivery");
            var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(fixture.origin());
            helper.assertValueEqual(furnace.getItem(2).getCount(), 1, "the real completed output reaches the new block");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_constructed_machine_chunks")
    public static void realBackgroundConstructionReturnsTwoProcessingFurnacesAcrossChunks(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(2, 2, 2)).offset(16_000_000, 0, 16_000_000);
        var origin = new BlockPos((base.getX() >> 4 << 4) + 15, 80, (base.getZ() >> 4 << 4) + 14);
        SettlementBackgroundConstructionGameTests.fixture(helper, origin, Map.of(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState(),
                BlockPos.ZERO.east(), Blocks.FURNACE.defaultBlockState()), false, fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.FURNACE, 2));
            SettlementBackgroundConstructionGameTests.work(helper, fixture, 50);
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "two real machine items fund the complete remote building");
            designate(helper, fixture, SettlementAreaType.SMELTERY);
            var level = helper.getLevel();
            long now = level.getGameTime();
            SettlementFurnaceService.update(level, fixture.town(), now);
            helper.assertValueEqual(fixture.town().furnaces().size(), 2, "both constructed machines have independent processing ledgers");
            var saved = new java.util.LinkedHashMap<BlockPos, CompoundTag>();
            for (var machine : fixture.town().furnaces().values()) fill(helper, machine, 1);
            SettlementFurnaceService.update(level, fixture.town(), now + 100L);
            for (var machine : fixture.town().furnaces().values()) saved.put(machine.position(), machine.save());
            saved.values().forEach(tag -> fixture.town().recordFurnace(SettlementFurnaceState.load(tag)));
            var journal = fixture.site().save();
            helper.assertFalse(level.hasChunkAt(origin) || level.hasChunkAt(origin.east()), "construction and saved cooking remain entirely unloaded");
            var chunks = new java.util.LinkedHashMap<net.minecraft.world.level.ChunkPos, net.minecraft.world.level.chunk.LevelChunk>();
            for (int x = fixture.site().min().getX() >> 4; x <= fixture.site().max().getX() >> 4; x++) {
                for (int z = fixture.site().min().getZ() >> 4; z <= fixture.site().max().getZ() >> 4; z++) {
                    var position = new net.minecraft.world.level.ChunkPos(x, z);
                    level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, position, 2, position);
                    chunks.put(position, level.getChunk(x, z));
                }
            }
            for (var section : fixture.site().sections().values()) {
                var chunk = chunks.get(new net.minecraft.world.level.ChunkPos(section.min()));
                for (var position : BlockPos.betweenClosed(section.min(), section.max())) chunk.setBlockState(position, section.state(position), false);
            }
            helper.startSequence().thenWaitUntil(() -> {
                SettlementConstructionSiteService.refresh(level, fixture.site());
                helper.assertFalse(fixture.site().conflict(), "restored construction terrain remains compatible");
                helper.assertFalse(fixture.site().pending(), "both processing machines reach their physical chunks: " + pendingDetails(level, fixture.site()));
            }).thenExecute(() -> {
                try {
                    for (var machine : fixture.town().furnaces().values()) {
                        var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(machine.position());
                        helper.assertTrue(machine.matches(furnace) && !machine.pending(), "the newly delivered block owns its saved machine state");
                        for (int tick = 0; tick < 100; tick++) AbstractFurnaceBlockEntity.serverTick(level, machine.position(), level.getBlockState(machine.position()), furnace);
                        helper.assertValueEqual(furnace.getItem(2).getCount(), 1, "the native machine completes the remaining half of its one funded recipe");
                        furnace.removeItem(2, 1);
                    }
                    var replay = SettlementConstructionSite.load(fixture.project().id(), journal);
                    SettlementConstructionSiteService.refresh(level, replay);
                    helper.assertFalse(replay.pending(), "an older journal recognizes both delivered chunk receipts");
                    saved.values().forEach(tag -> fixture.town().recordFurnace(SettlementFurnaceState.load(tag)));
                    chunks.values().forEach(chunk -> SettlementFurnaceService.reconcileChunk(level, fixture.town(), chunk));
                    for (var position : saved.keySet()) {
                        var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(position);
                        helper.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).isEmpty(), "replaying old saves cannot regenerate consumed input or collected output");
                    }
                    helper.assertTrue(fixture.depot().isEmpty(), "physical return never refunds either construction item");
                } finally {
                    chunks.keySet().forEach(position -> level.getChunkSource().removeRegionTicket(
                            net.minecraft.server.level.TicketType.FORCED, position, 2, position));
                }
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machine_return")
    public static void newConstructionCannotReplaceAnotherPendingMachineIdentity(GameTestHelper helper) {
        queued(helper, Blocks.FURNACE, SettlementAreaType.SMELTERY, null, (fixture, placement) -> {
            var level = helper.getLevel();
            var area = fixture.town().areas().values().stream().filter(candidate -> candidate.type() == SettlementAreaType.SMELTERY).findFirst().orElseThrow();
            var old = SettlementFurnaceState.capture(level, area.id(), new FurnaceBlockEntity(fixture.origin(), Blocks.FURNACE.defaultBlockState()), 0L);
            fill(helper, old, 2);
            fixture.town().recordFurnace(old);
            SettlementFurnaceService.update(level, fixture.town(), 200L);
            helper.assertValueEqual(fixture.town().furnaces().get(fixture.origin()), old, "new construction retains an unrelated pending inventory at the same position");
            helper.assertTrue(old.conflict() && old.pending(), "the old machine stays recoverable while its identity conflicts");
            helper.assertValueEqual(old.items(level.registryAccess()).get(0).getCount(), 2, "a different placement cannot process the retained old inputs");
            helper.assertTrue(placement.reconcile(level, fixture.project().id()), "the funded replacement can still be delivered");
            var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(fixture.origin());
            helper.assertFalse(old.matches(furnace), "the replacement does not inherit the old machine identity");
            helper.assertTrue(furnace.isEmpty(), "the unrelated pending inventory is never copied into the replacement");
        });
    }

    private static void smelting(GameTestHelper helper, int region, Block block, SettlementAreaType type, Item input, Item output) {
        SettlementBackgroundConstructionGameTests.capacityFixture(helper, region, Map.of(BlockPos.ZERO, block.defaultBlockState()), fixture -> {
            build(helper, fixture, block.asItem());
            var area = designate(helper, fixture, type);
            var worker = staff(helper, fixture, area);
            fixture.depot().setItem(0, new ItemStack(input, 2));
            fixture.depot().setItem(1, new ItemStack(Items.COAL));
            var level = helper.getLevel();
            SettlementFurnaceService.update(level, fixture.town(), 0L);
            helper.assertValueEqual(fixture.town().furnaces().size(), 1, "a funded machine can process before materializing");
            for (int now = 200; now <= 2_000; now += 200) {
                SettlementFurnaceService.update(level, fixture.town(), now);
                SettlementBackgroundSmelting.tick(level, fixture.town(), worker, 200, 1.0D);
            }
            helper.assertValueEqual(worker.items(level.registryAccess()).stream().filter(stack -> stack.is(output)).mapToInt(ItemStack::getCount).sum(),
                    2, "two real inputs yield two native recipe outputs");
            helper.assertTrue(fixture.depot().isEmpty(), "processing consumes the finite supplied materials");
            var machine = fixture.town().furnaces().get(fixture.origin());
            helper.assertTrue(machine.pending(), "processing remains pending until its block is delivered");
            helper.assertFalse(level.hasChunkAt(fixture.origin()), "construction and processing leave the distant chunk unloaded");
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(restored.furnaces().get(fixture.origin()).id(), machine.id(), "the construction identity survives settlement saves");
            SettlementFurnaceService.update(level, restored, 2_200L);
            helper.assertTrue(restored.furnaces().get(fixture.origin()).pending(), "saved processing keeps waiting for its real block");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machines")
    public static void aSharedMachineCanBeDesignatedBeforeItsConstructionFinishes(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.capacityFixture(helper, 40, Map.of(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState()), fixture -> {
            var level = helper.getLevel();
            var kitchen = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Kitchen",
                    fixture.area().min(), fixture.area().max(), 5, true);
            var smeltery = new SettlementArea(UUID.randomUUID(), SettlementAreaType.SMELTERY, "Smeltery",
                    fixture.area().min(), fixture.area().max(), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), kitchen)
                    && fixture.town().putArea(fixture.town().leader(), smeltery), "the new machine has both planned uses while building");
            helper.assertTrue(fixture.project().active(), "adding the final designations does not cancel construction");
            build(helper, fixture, Items.FURNACE);
            SettlementBuildingService.refresh(level, fixture.town(), kitchen);
            SettlementBuildingService.refresh(level, fixture.town(), smeltery);
            SettlementFurnaceService.update(level, fixture.town(), 0L);
            var machine = fixture.town().furnaces().get(fixture.origin());
            helper.assertTrue(machine != null, "the completed shared machine has a processing ledger");
            SettlementFurnaceService.update(level, fixture.town(), 100L);
            helper.assertTrue(fixture.town().furnaces().get(fixture.origin()) == machine, "idle shared surveys retain one machine ledger");
            fill(helper, machine, 2);
            SettlementFurnaceService.update(level, fixture.town(), 200L);
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            SettlementFurnaceService.update(level, restored, 300L);
            var returned = restored.furnaces().get(fixture.origin());
            helper.assertValueEqual(returned.items(level.registryAccess()).get(2).getCount(), 1,
                    "two shared designations advance one saved processing clock");
            helper.assertValueEqual(returned.items(level.registryAccess()).get(0).getCount(), 1, "only one funded input is consumed");
            helper.assertValueEqual(restored.furnaces().size(), 1, "the shared building still owns one inventory");
            helper.assertFalse(level.hasChunkAt(fixture.origin()), "neither construction nor processing loads the site");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_constructed_machines")
    public static void constructedFixturesRequireCompatibleRecordedTerrain(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.capacityFixture(helper, 41, Map.of(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState()), fixture -> {
            build(helper, fixture, Items.FURNACE);
            var smeltery = new SettlementArea(UUID.randomUUID(), SettlementAreaType.SMELTERY, "Smeltery",
                    fixture.area().min(), fixture.area().max(), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), smeltery), "the completed machine has its final use");
            var retired = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FORESTRY, "Retired forest",
                    fixture.site().min(), fixture.site().max(), 5, true);
            var forest = new SettlementForest(retired);
            fixture.site().sections().values().forEach(section -> forest.record(SettlementForestSection.load(retired.id(), section.save())));
            helper.assertTrue(forest.change(Map.of(fixture.origin().west(), Blocks.OAK_LOG.defaultBlockState())), "earlier pending terrain work is retained");
            fixture.town().recordForest(forest);
            SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), smeltery);
            helper.assertValueEqual(fixture.town().surveys().get(smeltery.id()).fixtures().size(), 1,
                    "a funded placement supersedes compatible older observations of its starting block");
            SettlementFurnaceService.update(helper.getLevel(), fixture.town(), 0L);
            var machine = fixture.town().furnaces().get(fixture.origin());
            fill(helper, machine, 2);
            SettlementFurnaceService.update(helper.getLevel(), fixture.town(), 100L);
            var processing = machine.save().getCompound("Contents").copy();
            helper.assertTrue(forest.change(Map.of(fixture.origin(), Blocks.OAK_LOG.defaultBlockState())), "another pending change can contradict the placement");
            SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), smeltery);
            helper.assertTrue(fixture.town().surveys().get(smeltery.id()).fixtures().isEmpty(),
                    "conflicting recorded changes cannot keep a constructed workstation operational");
            SettlementFurnaceService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(machine.save().getCompound("Contents"), processing, "the conflict pauses processing without consuming more input or fuel");
            helper.assertTrue(fixture.project().site().pending(), "inspection preserves the funded construction journal for reconciliation");
            forest.change(Map.of(fixture.origin(), Blocks.AIR.defaultBlockState()));
            SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), smeltery);
            SettlementFurnaceService.update(helper.getLevel(), fixture.town(), 300L);
            helper.assertValueEqual(machine.items(helper.getLevel().registryAccess()).get(2).getCount(), 1,
                    "compatible recorded terrain lets the same paid recipe finish without replaying its paused interval");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "conflict checks never load the remote site");
        });
    }

    private static void build(GameTestHelper helper, Fixture fixture, Item material) {
        fixture.depot().setItem(0, new ItemStack(material));
        SettlementBackgroundConstructionGameTests.work(helper, fixture, 50);
        helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "the funded construction finishes before processing");
        helper.assertTrue(fixture.depot().isEmpty(), "the machine's building material is consumed");
    }

    private static SettlementArea designate(GameTestHelper helper, Fixture fixture, SettlementAreaType type) {
        var area = SettlementBackgroundConstructionGameTests.designate(helper, fixture, type);
        SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), area);
        return area;
    }

    private static SettlementWorkerState staff(GameTestHelper helper, Fixture fixture, SettlementArea area) {
        helper.assertTrue(fixture.town().assignDuty(fixture.builder().getUUID(), area.id()), "the finished workplace receives a worker");
        SettlementWorkforceService.reconcile(helper.getLevel(), fixture.builder());
        var worker = SettlementWorkerState.capture(helper.getLevel(), fixture.builder(), SettlementWorkforceService.duty(fixture.builder()).orElseThrow(), 0L);
        worker.settings().putBoolean("AutoDepositOutputs", false);
        fixture.town().recordWorker(worker);
        return worker;
    }

    private static void setEnabled(GameTestHelper helper, Fixture fixture, SettlementArea area, boolean enabled) {
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), new SettlementArea(area.id(), area.type(), area.name(),
                area.min(), area.max(), area.priority(), enabled)), "the workplace updates its enabled state");
    }

    private static void fill(GameTestHelper helper, SettlementFurnaceState machine, int count) {
        var items = machine.items(helper.getLevel().registryAccess());
        items.set(0, new ItemStack(Items.RAW_IRON, count));
        items.set(1, new ItemStack(Items.COAL));
        machine.updateItems(helper.getLevel(), items);
    }

    private static String pendingDetails(net.minecraft.server.level.ServerLevel level, SettlementConstructionSite site) {
        var details = new StringBuilder();
        for (var placement : site.placements().values()) {
            for (var part : placement.parts()) {
                var pos = part.position();
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                details.append("position=").append(pos).append(", chunk=").append(chunk != null)
                        .append(", entitiesLoaded=").append(level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pos)))
                        .append(", state=").append(chunk == null ? "unloaded" : chunk.getBlockState(pos))
                        .append(", occupants=").append(level.getEntities((net.minecraft.world.entity.Entity) null,
                                new net.minecraft.world.phys.AABB(pos), entity -> entity.isAlive() && !entity.isSpectator()));
                for (int x = pos.getX() - 2 >> 4; x <= pos.getX() + 2 >> 4; x++) {
                    for (int z = pos.getZ() - 2 >> 4; z <= pos.getZ() + 2 >> 4; z++) {
                        if (level.getChunkSource().getChunkNow(x, z) == null) details.append(", missingNeighbor=").append(x).append(",").append(z);
                    }
                }
                details.append(" | ");
            }
        }
        return details.toString();
    }

    private static void queued(GameTestHelper helper, Block block, SettlementAreaType type, CompoundTag data,
                               java.util.function.BiConsumer<Fixture, SettlementConstructionPlacement> check) {
        SettlementBackgroundConstructionGameTests.fixture(helper, true, Map.of(BlockPos.ZERO, block.defaultBlockState()), fixture -> {
            helper.getLevel().setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            var placement = new SettlementConstructionPlacement(List.of(new SettlementConstructionPlacement.Part(
                    fixture.origin(), Blocks.AIR.defaultBlockState(), block.defaultBlockState(), data)));
            fixture.site().queue(placement);
            fixture.town().finishProject(fixture.project().id(), SettlementProject.Status.COMPLETED);
            designate(helper, fixture, type);
            check.accept(fixture, placement);
        });
    }
}
