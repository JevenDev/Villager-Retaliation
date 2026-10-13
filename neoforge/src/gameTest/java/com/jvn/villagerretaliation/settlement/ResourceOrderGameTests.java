package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.interaction.work.ResourceWorkOrders;
import com.jvn.villagerretaliation.interaction.work.mining.OreNodes;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("resource_orders")
@PrefixGameTestTemplate(false)
public final class ResourceOrderGameTests {
    private ResourceOrderGameTests() { }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void stairsPreserveStepsInEveryDirectionAndQueueProspecting(GameTestHelper helper) {
        for (var direction : Direction.Plane.HORIZONTAL) {
            var start = new BlockPos(0, 64, 0);
            var task = ResourceWorkOrders.create(HiredTemporaryTask.Kind.STAIRCASE_PROSPECT, start, start, direction,
                    12, 3, 3, "#c:ores/iron", -64, 320);
            helper.assertValueEqual(HiredTemporaryTask.load(task.save()), task, "stair plan survives save");
            for (int step = 0; step < 12; step++) {
                var feet = start.relative(direction, step).below(step);
                helper.assertTrue(task.includes(feet) && task.includes(feet.above(2)), "three blocks of corridor are removed");
                helper.assertFalse(task.includes(feet.below()), "the stair tread is preserved");
                helper.assertFalse(task.includes(feet.above(3)), "the ceiling outside the plan is preserved");
            }
            var next = task.consume();
            helper.assertValueEqual(next.kind(), HiredTemporaryTask.Kind.ORE_AREA, "stairs transition to exposed ore search");
            helper.assertValueEqual(next.material(), "#c:ores/iron", "prospecting retains material choice");
            helper.assertTrue(next.area().contains(start.relative(direction, 11).below(11)), "search surrounds staircase exit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void ordersOverrideMiningModeWithoutChangingClipboardSettings(GameTestHelper helper) {
        var state = new CompoundTag();
        state.putString("MiningMode", "exposed_ores");
        var start = BlockPos.ZERO;
        var task = ResourceWorkOrders.create(HiredTemporaryTask.Kind.TUNNEL, start, start, Direction.EAST, 8, 3, 3, "", -64, 320);
        new HiredTemporaryQueue(UUID.randomUUID(), UUID.randomUUID(), null, HiredVillagerRole.MINING,
                task.min(), task.max(), List.of(task)).store(state);
        helper.assertValueEqual(HiredMiningMode.fromState(state), HiredMiningMode.HORIZONTAL_EXCAVATION, "tunnel chooses horizontal mining");
        HiredTemporaryWorkService.clearOrders(state);
        helper.assertValueEqual(HiredMiningMode.fromState(state), HiredMiningMode.EXPOSED_ORES, "clipboard mode restored");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void selectedDepositUsesRealMinerAndLeavesOtherOre(GameTestHelper helper) {
        var fixture = HiredTemporaryWorkGameTests.personal(helper, HiredVillagerRole.MINING);
        var miner = fixture.villager();
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(miner);
        var position = miner.blockPosition().east();
        try {
            for (var floor : BlockPos.betweenClosed(miner.blockPosition().offset(-2, -1, -2), miner.blockPosition().offset(3, -1, 3)))
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(position, Blocks.DIAMOND_ORE.defaultBlockState());
            level.setBlockAndUpdate(position.south(), Blocks.COAL_ORE.defaultBlockState());
            var natural = new CompoundTag();
            natural.putInt(Long.toString(position.asLong()), 0);
            OreNodes.apply(level.getChunkAt(position), new CompoundTag(), natural);
            var inventory = HiredJobInventory.getJobInventory(miner);
            inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
            state.putBoolean("AutoDepositOutputs", false);
            state.putBoolean("UseAssignedStorageForSupplies", false);
            var task = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.ORE, position, position, 1, position);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), miner, task, false).isEmpty(), "deposit order accepted");
            for (int i = 0; i < 1500 && HiredTemporaryWorkService.queue(state) != null; i++) {
                var session = HiredWorkSession.active(level, miner);
                var result = session.worker().tick(level, miner, fixture.owner(), session.context());
                HiredTemporaryWorkService.completed(level, miner, session, result);
            }
            helper.assertTrue(level.getBlockState(position).isAir(), "real miner depletes all ten extractions: " + state);
            helper.assertTrue(level.getBlockState(position.south()).is(Blocks.COAL_ORE), "unselected deposit remains");
            helper.assertValueEqual(inventory.collectOutputItems().stream().filter(entry -> entry.stack().is(Items.DIAMOND))
                    .mapToInt(entry -> entry.stack().getCount()).sum(), 10, "normal loot delivered for each extraction");
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "depleted order finishes");
        } finally {
            HiredPathMemory.clear(miner);
            miner.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void forestOrderRecognizesTreesAndRestoresReplantPreference(GameTestHelper helper) {
        var fixture = HiredTemporaryWorkGameTests.logger(helper);
        var miner = fixture.villager();
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(miner);
        var root = miner.blockPosition().east(2);
        try {
            state.putBoolean(com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions.PLANT_SAPLINGS_TAG, true);
            var home = HiredWorkArea.fromState(state, miner.blockPosition());
            var task = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.FOREST, root.offset(-2, -1, -2), root.offset(2, 4, 2), 1);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), miner, task, false).isEmpty(), "forest order accepted");
            for (int i = 0; i < 1000 && HiredTemporaryWorkService.queue(state) != null; i++) {
                var session = HiredWorkSession.active(level, miner);
                var result = session.worker().tick(level, miner, fixture.owner(), session.context());
                HiredTemporaryWorkService.completed(level, miner, session, result);
            }
            helper.assertTrue(level.getBlockState(root).isAir(), "forest selection finds and harvests the tree: " + state);
            helper.assertTrue(level.getBlockState(miner.blockPosition().west(4)).is(Blocks.OAK_LOG), "tree outside selection remains");
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "empty forest finishes");
            helper.assertValueEqual(HiredWorkSession.active(level, miner).area(), home, "permanent work area restored");
            helper.assertTrue(com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions.plantSaplings(state), "permanent replant preference restored");
        } finally {
            HiredPathMemory.clear(miner);
            miner.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void tunnelUsesNativeMinerAndPreservesFloor(GameTestHelper helper) {
        var fixture = HiredTemporaryWorkGameTests.personal(helper, HiredVillagerRole.MINING);
        var miner = fixture.villager();
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(miner);
        var start = miner.blockPosition();
        try {
            for (var floor : BlockPos.betweenClosed(start.offset(-2, -1, -2), start.offset(4, -1, 2)))
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(start.east(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(start.east().above(), Blocks.STONE.defaultBlockState());
            HiredWorkArea.fromBounds(start.offset(-2, -1, -2), start.offset(4, 3, 2), true).save(state);
            HiredJobInventory.getJobInventory(miner).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
            state.putBoolean("AutoDepositOutputs", false);
            state.putBoolean("UseAssignedStorageForSupplies", false);
            var task = ResourceWorkOrders.create(HiredTemporaryTask.Kind.TUNNEL, start, start, Direction.EAST, 3, 1, 2, "", -64, 320);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), miner, task, false).isEmpty(), "tunnel order accepted");
            for (int i = 0; i < 400 && HiredTemporaryWorkService.queue(state) != null; i++) {
                var session = HiredWorkSession.active(level, miner);
                var result = session.worker().tick(level, miner, fixture.owner(), session.context());
                HiredTemporaryWorkService.completed(level, miner, session, result);
            }
            helper.assertTrue(level.getBlockState(start.east()).isAir() && level.getBlockState(start.east().above()).isAir(), "tunnel cleared: " + state);
            helper.assertTrue(level.getBlockState(start.east().below()).is(Blocks.STONE), "tunnel floor preserved");
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "tunnel order finishes");
        } finally {
            HiredPathMemory.clear(miner);
            miner.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void expeditionsAllowNearbyUnclaimedLandAndRejectForeignClaims(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(0, 2, 0)).offset(8_000_000, 0, 8_000_000);
        var registry = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        var leader = UUID.randomUUID();
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader, center, level.getGameTime()).orElseThrow();
        var outside = center.east(80);
        var task = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.ORE, outside, outside, 1, outside);
        helper.assertFalse(town.contains(outside), "deposit is outside village territory");
        helper.assertTrue(SettlementExpeditions.permits(level, town, task), "nearby unclaimed resource can be worked");
        helper.assertFalse(level.hasChunkAt(outside), "permission checks do not load expedition chunks");
        var distant = outside.east(512);
        helper.assertFalse(SettlementExpeditions.permits(level, town,
                new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.ORE, distant, distant, 1, distant)), "distant expedition rejected");
        var rival = registry.create(level.getGameTime(), level.dimension().location(), outside, "");
        helper.assertTrue(SettlementSavedData.get(level).establish(registry, rival, UUID.randomUUID(), outside, level.getGameTime()).isPresent(), "rival claim exists");
        helper.assertFalse(SettlementExpeditions.permits(level, town, task), "foreign settlement deposit rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void staircaseMinerCutsCorridorAndKeepsEveryTread(GameTestHelper helper) {
        var fixture = HiredTemporaryWorkGameTests.personal(helper, HiredVillagerRole.MINING);
        var miner = fixture.villager();
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(miner);
        var start = miner.blockPosition().east();
        try {
            level.setBlockAndUpdate(miner.blockPosition().below(), Blocks.STONE.defaultBlockState());
            miner.setPos(miner.blockPosition().getBottomCenter());
            var task = ResourceWorkOrders.create(HiredTemporaryTask.Kind.STAIRCASE, start, start, Direction.EAST, 4, 1, 3, "", -64, 320);
            for (var position : BlockPos.betweenClosed(task.min().below(), task.max())) level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
            HiredWorkArea.fromBounds(task.min().offset(-2, -1, -2), task.max().offset(2, 1, 2), true).save(state);
            HiredJobInventory.getJobInventory(miner).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
            state.putBoolean("AutoDepositOutputs", false);
            state.putBoolean("UseAssignedStorageForSupplies", false);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), miner, task, false).isEmpty(), "staircase accepted");
            int step = 0;
            for (int i = 0; i < 1500 && HiredTemporaryWorkService.queue(state) != null; i++) {
                var next = start.east(step).below(step);
                if (step < 4 && com.jvn.villagerretaliation.interaction.work.mining.MiningSafety.isSafePathPosition(level, next)) {
                    miner.setPos(next.getBottomCenter());
                    step++;
                }
                var session = HiredWorkSession.active(level, miner);
                var result = session.worker().tick(level, miner, fixture.owner(), session.context());
                HiredTemporaryWorkService.completed(level, miner, session, result);
            }
            for (int i = 0; i < 4; i++) {
                var feet = start.east(i).below(i);
                helper.assertTrue(level.getBlockState(feet).isAir() && level.getBlockState(feet.above(2)).isAir(), "stair corridor cut: " + state);
                helper.assertTrue(level.getBlockState(feet.below()).is(Blocks.STONE), "stair tread preserved");
            }
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "staircase finishes");
        } finally {
            HiredPathMemory.clear(miner);
            miner.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void expandedViewKeepsIrregularClaimAndForeignTownBoundaries(GameTestHelper helper) {
        SettlementViewGameTests.irregularClaimsDoNotExposeTheirEnvelopeOrAnotherTown(helper);
    }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void civicMinerHarvestsAnUnclaimedNodeAndKeepsItsDuty(GameTestHelper helper) {
        SettlementMiningWorkGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var miner = fixture.villager();
            var position = fixture.ore().east(80);
            var leader = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(fixture.town().leader(), "NodeLeader"));
            helper.assertFalse(fixture.town().contains(position), "target is outside settlement territory");
            for (var floor : BlockPos.betweenClosed(position.offset(-2, -1, -2), position.offset(2, -1, 2)))
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(position, Blocks.DIAMOND_ORE.defaultBlockState());
            var nodes = new CompoundTag();
            nodes.putInt(Long.toString(position.asLong()), 0);
            OreNodes.apply(level.getChunkAt(position), new CompoundTag(), nodes);
            miner.setPos(position.west().getBottomCenter());
            var state = HiredWorkStateStore.state(miner);
            state.putBoolean("UseAssignedStorageForSupplies", false);
            var task = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.ORE, position, position, 1, position);
            helper.assertTrue(HiredTemporaryWorkService.submit(leader, miner, task, false).isEmpty(), "civic expedition accepted");
            try {
                for (int i = 0; i < 1500 && HiredTemporaryWorkService.queue(state) != null; i++) {
                    var session = HiredWorkSession.active(level, miner);
                    helper.assertTrue(HiredTemporaryWorkService.ready(level, miner, session), "expedition ready in observed chunks");
                    var result = session.worker().tick(level, miner, leader, session.context());
                    HiredTemporaryWorkService.completed(level, miner, session, result);
                }
                helper.assertTrue(level.getBlockState(position).isAir(), "civic miner depletes node");
                helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "civic expedition completes");
                helper.assertValueEqual(fixture.town().duties().get(miner.getUUID()), fixture.area().id(), "home duty retained");
                helper.assertValueEqual(HiredJobInventory.getJobInventory(miner).collectOutputItems().stream()
                        .filter(entry -> entry.stack().is(Items.DIAMOND)).mapToInt(entry -> entry.stack().getCount()).sum(), 10, "civic miner gets ten real drops");
            } finally {
                HiredPathMemory.clear(miner);
                miner.discard();
            }
        });
    }

    @GameTest(template = "empty", batch = "resource_orders")
    public static void oreSelectorsAndOrderPacketsRetainModdedIdentifiers(GameTestHelper helper) {
        var task = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.ORE_AREA, BlockPos.ZERO,
                new BlockPos(3, 3, 3), 1, null, null, "#c:ores/iron");
        helper.assertTrue(task.matches(Blocks.IRON_ORE.defaultBlockState()), "ore tag matches normal iron");
        helper.assertTrue(task.matches(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()), "ore tag matches variants");
        helper.assertFalse(task.matches(Blocks.COAL_ORE.defaultBlockState()), "ore selector excludes other materials");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            task.write(buffer);
            helper.assertValueEqual(HiredTemporaryTask.read(buffer), task, "material and geometry survive network serialization");
        } finally { buffer.release(); }
        var custom = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.ORE_AREA, BlockPos.ZERO,
                new BlockPos(3, 3, 3), 1, null, null, "examplemod:tin_ore");
        helper.assertValueEqual(HiredTemporaryTask.load(custom.save()), custom, "modded block selector survives save");
        helper.succeed();
    }
}
