package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.mining.MiningExcavationShaft;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementVerticalExcavationGameTests {
    private SettlementVerticalExcavationGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void verticalMinersExcavateEveryLayerWithFiniteToolsLaddersAndTorches(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            run(helper, fixture, 200, 20_000);
            assertCleared(helper, fixture);
            for (var item : List.of(Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG)) {
                helper.assertValueEqual(count(helper, fixture, item), 42, "every excavated material produces only its native finite drops");
            }
            for (var item : List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_AXE)) {
                helper.assertValueEqual(tool(helper, fixture, item).getDamageValue(), 42, "each cut wears its actual tool");
            }
            helper.assertValueEqual(blocks(fixture, Blocks.LADDER), 7, "a real ladder route connects every cleared layer");
            helper.assertValueEqual(blocks(fixture, Blocks.WALL_TORCH), 4, "native torch geometry lights all four sides of the deep excavation");
            helper.assertValueEqual(count(helper, fixture, Items.LADDER), 57, "seven placed ladders consume seven carried items");
            helper.assertValueEqual(count(helper, fixture, Items.TORCH), 12, "four placed torches consume four carried items");
            double earned = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(earned > 0.0D, "successful excavation awards mining practice");
            run(helper, fixture, 20_200, 22_000);
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 42, "finished terrain cannot generate more output");
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), earned, "finished terrain cannot award more practice");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "background excavation never loads the mine");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void verticalDescentWaitsForRealLaddersAndRespectsSupplyAccess(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            put(helper, fixture, 35, ItemStack.EMPTY);
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            fixture.supply().setItem(0, new ItemStack(Items.LADDER, 7));
            run(helper, fixture, 200, 2_000);
            helper.assertValueEqual(fixture.worker().status(), "missing_ladders", "lower excavation waits for a real escape route");
            for (BlockPos position : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max())) {
                helper.assertValueEqual(fixture.mine().getBlockState(position).isAir(), position.getY() == fixture.area().max().getY(),
                        "only the surface layer is cleared before ladders arrive");
            }
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 7, "disabled storage access preserves the ladder stock");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            run(helper, fixture, 2_200, 24_000);
            assertCleared(helper, fixture);
            helper.assertTrue(fixture.supply().isEmpty(), "descent consumes the actual seven stored ladders");
            helper.assertValueEqual(blocks(fixture, Blocks.LADDER), 7, "each stored ladder becomes one real shaft block");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void verticalSupportsConsumeMinedBackingWhereTheOutsideWallIsMissing(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            var holes = new java.util.ArrayList<BlockPos>();
            var min = fixture.area().min();
            var max = fixture.area().max();
            for (BlockPos position : BlockPos.betweenClosed(min.offset(-1, 0, -1), max.offset(1, 0, 1))) {
                if (fixture.mine().contains(position) || position.equals(fixture.worker().position().below())) continue;
                if (position.getX() < min.getX() || position.getX() > max.getX()
                        || position.getZ() < min.getZ() || position.getZ() > max.getZ()) {
                    set(fixture.chunk(), position, Blocks.AIR.defaultBlockState());
                    holes.add(position.immutable());
                }
            }
            observe(helper, fixture);
            run(helper, fixture, 200, 24_000);
            assertCleared(helper, fixture);
            int backing = (int) holes.stream().filter(position -> !fixture.mine().getBlockState(position).isAir()).count();
            helper.assertTrue(backing >= 6, "the open shaft wall receives physical backing");
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE) + count(helper, fixture, Items.DIRT)
                    + count(helper, fixture, Items.OAK_LOG), 126 - backing, "every support backing block consumes an actual mined material");
            helper.assertValueEqual(blocks(fixture, Blocks.LADDER), 7, "backed ladders maintain the full descent route");
            helper.assertValueEqual(blocks(fixture, Blocks.WALL_TORCH), 3, "wall torches receive real backing without occupying the selected shaft");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void verticalTerrainAndShaftStateSurviveSavingAndReturnExactlyOnce(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            run(helper, fixture, 200, 20_000);
            assertCleared(helper, fixture);
            var shaft = MiningExcavationShaft.stored(context(fixture));
            helper.assertTrue(shaft != null, "vertical work persists its native shaft selection");
            var savedMine = SettlementMine.load(fixture.mine().save());
            for (int y = fixture.area().min().getY(); y <= fixture.area().max().getY(); y++) {
                helper.assertTrue(savedMine.getBlockState(shaft.at(y)).is(Blocks.LADDER), "saved terrain retains every finite ladder placement");
                helper.assertFalse(fixture.chunk().getBlockState(shaft.at(y)).is(Blocks.LADDER), "physical terrain waits for chunk handoff");
            }
            SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            for (int y = fixture.area().min().getY(); y <= fixture.area().max().getY(); y++) {
                helper.assertValueEqual(fixture.chunk().getBlockState(shaft.at(y)), shaft.ladderState(), "the returning chunk receives the correctly oriented ladder");
            }
            var restored = SettlementWorkerState.load(fixture.worker().save());
            helper.assertTrue(restored.restore(fixture.villager()), "the newer worker revision returns its real inventory");
            var nativeState = HiredWorkStateStore.state(fixture.villager());
            helper.assertValueEqual(nativeState.getInt("ExcavationLadderX"), shaft.x(), "the native worker receives its shared shaft location");
            helper.assertValueEqual(nativeState.getString("ExcavationLadderFacing"), shaft.facing().getName(), "the native worker receives its shared shaft orientation");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager()).countItem(Items.LADDER), 57, "the native inventory receives only unplaced ladders");
            helper.assertFalse(restored.restore(fixture.villager()), "a repeated handoff cannot duplicate supplies");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void oneLadderCannotCreateAnUnlimitedDescentRoute(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            put(helper, fixture, 35, new ItemStack(Items.LADDER));
            run(helper, fixture, 200, 3_000);
            helper.assertValueEqual(blocks(fixture, Blocks.LADDER), 1, "one supply item places only one ladder");
            helper.assertValueEqual(count(helper, fixture, Items.LADDER), 0, "the placed ladder leaves the carried stock");
            helper.assertValueEqual(fixture.worker().status(), "missing_ladders", "the next descent waits for a replacement supply");
            helper.assertTrue(fixture.mine().getBlockState(fixture.area().min()).is(Blocks.STONE), "exhausted ladders cannot expose the bottom layer");
            fixture.supply().setItem(0, new ItemStack(Items.LADDER, 6));
            run(helper, fixture, 3_200, 24_000);
            assertCleared(helper, fixture);
            helper.assertTrue(fixture.supply().isEmpty(), "the remaining route consumes six additional ladders");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void verticalExcavationPreservesTheUnloadedWorkersLandingUntilNewMovementIsObserved(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            var position = fixture.area().min().offset(2, 7, 1);
            fixture.villager().setPos(position.getBottomCenter());
            fixture.worker().observe(helper.getLevel(), fixture.villager(),
                    new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 0L);
            run(helper, fixture, 200, 8_000);
            helper.assertTrue(fixture.mine().getBlockState(position.below()).isAir(), "the worker can open the surface beneath its last observation");
            helper.assertFalse(fixture.mine().getBlockState(position.below(2)).isAir(), "virtual movement cannot turn the saved native position into a deep fall");
            helper.assertValueEqual(fixture.worker().status(), "awaiting_worker_movement", "the miner identifies the remaining native landing constraint");
            helper.assertTrue(fixture.worker().restore(fixture.villager()), "completed work returns before the next native observation");
            var border = new BlockPos(fixture.area().min().getX() - 1, fixture.area().max().getY() + 1, fixture.area().min().getZ() + 1);
            fixture.villager().setPos(border.getBottomCenter());
            fixture.worker().observe(helper.getLevel(), fixture.villager(),
                    new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 8_000L);
            run(helper, fixture, 8_200, 24_000);
            assertCleared(helper, fixture);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void supportedLaddersHoldWorkersAboveAnObservedEmptyShaft(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            var position = fixture.area().min().above(3);
            set(fixture.chunk(), position, Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
            set(fixture.chunk(), position.above(), Blocks.AIR.defaultBlockState());
            set(fixture.chunk(), position.below(), Blocks.AIR.defaultBlockState());
            observe(helper, fixture);
            helper.assertTrue(SettlementMiningRoutes.safe(fixture.mine(), position),
                    "a properly backed ladder supports a worker without an invented floor block");
            set(fixture.chunk(), position.west(), Blocks.AIR.defaultBlockState());
            observe(helper, fixture);
            helper.assertFalse(SettlementMiningRoutes.safe(fixture.mine(), position), "an unsupported ladder cannot hold a background worker");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void verticalExcavationPreservesUncontainableWaterloggedBlocksAndItsTools(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            var water = fixture.area().min().offset(2, 4, 1);
            var waterlogged = Blocks.OAK_FENCE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
            set(fixture.chunk(), water, waterlogged);
            observe(helper, fixture);
            run(helper, fixture, 200, 12_000);
            helper.assertValueEqual(fixture.mine().getBlockState(water), waterlogged, "excavation cannot overwrite an uncontainable waterlogged block");
            helper.assertFalse(fixture.mine().getBlockState(water.above()).isAir(), "the block above an uncontained pocket remains intact");
            helper.assertValueEqual(fixture.worker().status(), "excavation_blocked", "unsafe remaining terrain cannot be reported complete");
            int damage = tool(helper, fixture, Items.DIAMOND_PICKAXE).getDamageValue();
            run(helper, fixture, 12_200, 14_000);
            helper.assertValueEqual(tool(helper, fixture, Items.DIAMOND_PICKAXE).getDamageValue(), damage, "waiting at a hazard cannot wear more durability");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void aSingleColumnShaftCanBeExtendedFromItsSupportedLadder(GameTestHelper helper) {
        fixture(helper, 7, 1, 1, fixture -> {
            run(helper, fixture, 200, 12_000);
            assertCleared(helper, fixture);
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 7, "each shaft block produces one actual drop");
            helper.assertValueEqual(blocks(fixture, Blocks.LADDER), 7, "each shaft level receives one real ladder");
            helper.assertValueEqual(count(helper, fixture, Items.LADDER), 57, "working from the ladder still consumes finite supplies");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void completedVerticalMinesUseOnlyAuthorizedFiniteTorchSupplies(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            put(helper, fixture, 36, ItemStack.EMPTY);
            fixture.supply().setItem(0, new ItemStack(Items.TORCH, 4));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            run(helper, fixture, 200, 16_000);
            assertCleared(helper, fixture);
            helper.assertValueEqual(blocks(fixture, Blocks.WALL_TORCH), 0, "disabled storage access cannot invent lighting supplies");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 4, "unauthorized torch stock remains stored");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            run(helper, fixture, 16_200, 24_000);
            assertCleared(helper, fixture);
            helper.assertValueEqual(blocks(fixture, Blocks.WALL_TORCH), 4, "newly authorized supplies can finish the mine lighting");
            helper.assertTrue(fixture.supply().isEmpty(), "four placed lights consume four actual depot torches");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_vertical_excavation")
    public static void miningSupportsStayWithinTheirAuthorizedTerrainAndBackingFaces(GameTestHelper helper) {
        fixture(helper, 7, fixture -> {
            var position = fixture.area().min();
            var backing = position.west();
            set(fixture.chunk(), position, Blocks.AIR.defaultBlockState());
            set(fixture.chunk(), backing, Blocks.AIR.defaultBlockState());
            set(fixture.chunk(), backing.west(), Blocks.AIR.defaultBlockState());
            set(fixture.chunk(), position.below(), Blocks.AIR.defaultBlockState());
            set(fixture.chunk(), backing.above(), Blocks.CHEST.defaultBlockState());
            observe(helper, fixture);
            var stone = Blocks.COBBLESTONE.defaultBlockState();
            var ladder = Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST);
            helper.assertFalse(fixture.mine().changeSupport(position, stone), "support placement cannot fill arbitrary cells inside the excavation");
            helper.assertFalse(fixture.mine().changeSupport(backing.west(), stone), "backing cannot extend beyond the adjacent outside face");
            helper.assertFalse(fixture.mine().changeSupport(position.below(), stone), "support backing cannot expand below the selected excavation");
            helper.assertFalse(fixture.mine().changeSupport(backing.above(), stone), "support backing cannot overwrite a container");
            helper.assertFalse(fixture.mine().changeSupport(backing, Blocks.BEDROCK.defaultBlockState()), "support backing cannot create unbreakable blocks");
            helper.assertFalse(fixture.mine().changeSupport(position.east(), ladder), "ladder placement cannot overwrite existing rock");
            helper.assertTrue(fixture.mine().changeSupport(backing, stone), "a real adjacent backing block is within the infrastructure boundary");
            helper.assertTrue(fixture.mine().changeSupport(position, ladder), "the ladder itself remains inside the selected excavation");
            var restored = SettlementMine.load(fixture.mine().save());
            helper.assertValueEqual(restored.getBlockState(backing), stone, "backing journals survive saving");
            helper.assertValueEqual(restored.getBlockState(position), ladder, "oriented ladder journals survive saving");
            SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            helper.assertValueEqual(fixture.chunk().getBlockState(backing), stone, "the physical chunk receives the bounded backing change");
            helper.assertValueEqual(fixture.chunk().getBlockState(position), ladder, "the physical chunk receives its supported ladder");
        });
    }

    private static int blocks(Fixture fixture, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (BlockPos position : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max())) {
            if (fixture.mine().getBlockState(position).is(block)) count++;
        }
        return count;
    }

    static HiredWorkContext context(Fixture fixture) {
        return new HiredWorkContext(HiredJobInventory.getJobInventory(fixture.villager()), fixture.worker().settings(),
                fixture.area().center(), fixture.area().min(), fixture.area().max(), 64, 32, true, 1, false, true);
    }

    static void assertCleared(GameTestHelper helper, Fixture fixture) {
        for (BlockPos position : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max())) {
            helper.assertTrue(fixture.mine().getBlockState(position).isAir() || fixture.mine().getBlockState(position).is(Blocks.LADDER)
                            || fixture.mine().getBlockState(position).is(Blocks.WALL_TORCH),
                    "finite excavation clears " + position.subtract(fixture.area().min()) + " with status " + fixture.worker().status()
                            + ", position " + BlockPos.of(fixture.worker().settings().getLong("SettlementMiningPosition")).subtract(fixture.area().min())
                            + ", layer " + com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules.currentExcavationLayer(
                                    fixture.mine(), fixture.mine()::observed, context(fixture))
                            + ", shaft " + MiningExcavationShaft.stored(context(fixture)) + ", ladders " + blocks(fixture, Blocks.LADDER));
        }
        helper.assertValueEqual(fixture.worker().status(), "excavation_complete", "exhausted excavation is reported complete");
    }

    static int count(GameTestHelper helper, Fixture fixture, Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    static ItemStack tool(GameTestHelper helper, Fixture fixture, Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item)).findFirst().orElse(ItemStack.EMPTY);
    }

    static void put(GameTestHelper helper, Fixture fixture, int slot, ItemStack stack) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        items.set(slot, stack);
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    static void run(GameTestHelper helper, Fixture fixture, int from, int until) {
        for (int now = from; now <= until; now += 200) SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
    }

    static void observe(GameTestHelper helper, Fixture fixture) {
        SettlementMineService.observeArea(helper.getLevel(), fixture.town(), fixture.area(), fixture.chunk());
    }

    static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    static void fixture(GameTestHelper helper, int height, Consumer<Fixture> check) {
        fixture(helper, height, 6, 3, check);
    }

    private static void fixture(GameTestHelper helper, int height, int width, int depth, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = new LevelChunk(level, new ChunkPos(840_000 + origin.getX() * 4, 840_000 + origin.getZ() * 4));
        var min = new BlockPos(chunk.getPos().getMinBlockX() + 4, 65, chunk.getPos().getMinBlockZ() + 5);
        var max = min.offset(width - 1, height - 1, depth - 1);
        for (BlockPos position : BlockPos.betweenClosed(new BlockPos(chunk.getPos().getMinBlockX(), 63, chunk.getPos().getMinBlockZ()),
                new BlockPos(chunk.getPos().getMaxBlockX(), max.getY(), chunk.getPos().getMaxBlockZ()))) set(chunk, position, Blocks.STONE.defaultBlockState());
        for (BlockPos position : BlockPos.betweenClosed(min, max)) {
            var block = switch ((position.getX() - min.getX()) % 3) {
                case 0 -> Blocks.STONE;
                case 1 -> Blocks.DIRT;
                default -> Blocks.OAK_LOG;
            };
            set(chunk, position, block.defaultBlockState());
        }
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), min, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), min, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Excavation", min, max, 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the excavation area is registered");
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage", min.east(9), min.offset(10, 2, 2), 5, true);
        town.putArea(town.leader(), storage);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", min.west(8), min.west(7), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
        var supply = town.registerDepot(town.leader(), storage.id(), min.east(9), SettlementDepotPurpose.MATERIALS);
        var output = town.registerDepot(town.leader(), storage.id(), min.east(10), SettlementDepotPurpose.OUTPUT);
        supply.observeRules(List.of());
        output.observeRules(List.of());
        SettlementMineService.observeArea(level, town, area, chunk);
        var mine = town.mines().get(area.id());
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(new BlockPos(min.getX() - 1, max.getY() + 1, min.getZ() + 1).getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        HiredWorkStateStore.state(villager).putString("MiningMode", "excavate_area");
        var inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        inventory.setItem(33, new ItemStack(Items.DIAMOND_SHOVEL));
        inventory.setItem(34, new ItemStack(Items.DIAMOND_AXE));
        inventory.setItem(35, new ItemStack(Items.LADDER, 64));
        inventory.setItem(36, new ItemStack(Items.TORCH, 16));
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        helper.assertFalse(level.hasChunkAt(min), "the excavation fixture is detached from the live world");
        helper.assertTrue(mine.fullyObserved(level.getMinBuildHeight(), level.getMaxBuildHeight()), "the whole excavation and safety margin are observed");
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, area, chunk, mine, supply, output, worker, villager));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk, SettlementMine mine,
                           SettlementDepot supply, SettlementDepot output, SettlementWorkerState worker, Villager villager) {
    }
}
