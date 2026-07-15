package com.jvn.villagerretaliation.interaction.work.logging;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class LoggingJobGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private LoggingJobGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void loggingFiltersResolvePersistedIdsWithoutAcceptingNonLogs(GameTestHelper helper) {
        CompoundTag state = new CompoundTag();
        HiredLoggingFilters.toggleFilter(state, "minecraft:stone");
        helper.assertTrue(HiredLoggingFilters.selectedFilterIds(state).isEmpty(), "non-log ids must not become logging filters");

        ResourceLocation oak = ResourceLocation.withDefaultNamespace("oak_log");
        HiredLoggingFilters.toggleFilter(state, oak.toString());
        helper.assertValueEqual(HiredLoggingFilters.selectedFilterIds(state), java.util.Set.of(oak), "namespaced log filter");

        CompoundTag legacy = new CompoundTag();
        legacy.putString(HiredLoggingFilters.LEGACY_FILTER_TAG, "oak_log");
        helper.assertValueEqual(HiredLoggingFilters.selectedFilterIds(legacy), java.util.Set.of(oak), "legacy path-only filter");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void loggingHarvestPlanBoundsAndNormalizesPersistedWork(GameTestHelper helper) {
        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(state, new BlockPos(-20, 0, -20), new BlockPos(20, 4, 20));
        List<BlockPos> logs = positions(320, 1);
        logs.add(logs.getFirst());
        List<BlockPos> leaves = positions(440, 2);
        List<BlockPos> saplings = positions(320, 1);
        BlockPos origin = logs.getFirst();

        LoggingHarvestPlan.begin(
                context,
                origin,
                logs,
                leaves,
                saplings,
                new ItemStack(Items.OAK_SAPLING),
                true,
                "minecraft:oak");
        LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);

        helper.assertTrue(plan != null, "bounded logging plan should survive persistence validation");
        helper.assertValueEqual(plan.logs().length, LoggingHarvestPlan.MAX_LOGS, "bounded pending log count");
        helper.assertValueEqual(plan.leaves().length, LoggingHarvestPlan.MAX_LEAVES, "bounded pending leaf count");
        helper.assertValueEqual(plan.saplings().length, LoggingHarvestPlan.MAX_SAPLINGS, "bounded pending sapling count");
        helper.assertValueEqual(new HashSet<>(boxed(plan.logs())).size(), plan.logs().length, "pending logs should be deduplicated");
        helper.assertValueEqual(plan.logFamily(), "minecraft:oak", "selected tree family should persist with the plan");
        helper.assertTrue(plan.stripLogs(), "strip option should be snapshotted with the plan");
        helper.assertTrue(plan.sapling().is(Items.OAK_SAPLING), "replanting item should survive plan reload");

        HiredLoggingFilters.toggleFilter(state, "minecraft:birch_log");
        HiredWorkPlan.clear(context);
        context.setProgressTicks(0);
        helper.assertValueEqual(
                LoggingHarvestPlan.read(context).logFamily(),
                "minecraft:oak",
                "changing future target filters must not rewrite an active harvest");

        HiredWorkContext movedArea = context(state, new BlockPos(100, 0, 100), new BlockPos(110, 4, 110));
        helper.assertTrue(LoggingHarvestPlan.read(movedArea) == null, "plan outside a reassigned work area should be discarded");
        helper.assertFalse(LoggingHarvestPlan.has(movedArea), "discarded plan must not leave partial state behind");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void loggingAccessLeafMarkerIsScopedAndClearedWithSearchState(GameTestHelper helper) {
        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(state, BlockPos.ZERO, new BlockPos(8, 8, 8));
        BlockPos leaf = new BlockPos(4, 4, 4);
        LoggingWorkerState.markAccessLeaf(context, leaf);
        helper.assertTrue(LoggingWorkerState.isAccessLeaf(context, leaf), "active access leaf marker");

        state.putLong(LoggingWorkerState.TREE_SCAN_CURSOR_TAG, 4L);
        state.putLong(LoggingWorkerState.SAPLING_SCAN_CURSOR_TAG, 8L);
        state.putLong(LoggingWorkerState.NEXT_TREE_SCAN_GAME_TIME_TAG, 12L);
        state.putLong(LoggingWorkerState.NEXT_SAPLING_SCAN_GAME_TIME_TAG, 16L);
        AtomicInteger breakGoalCalculations = new AtomicInteger();
        helper.assertValueEqual(
                LoggingWorkerState.breakGoal(context, leaf, "minecraft:iron_axe", 0, 100, () -> {
                    breakGoalCalculations.incrementAndGet();
                    return 37;
                }),
                37,
                "initial tree break goal");
        context.setProgressTicks(1);
        helper.assertValueEqual(
                LoggingWorkerState.breakGoal(context, leaf, "minecraft:iron_axe", 0, 100, () -> {
                    breakGoalCalculations.incrementAndGet();
                    return 99;
                }),
                37,
                "active target should reuse its tree break goal");
        helper.assertValueEqual(breakGoalCalculations.get(), 1, "tree geometry should not be recalculated every break tick");
        LoggingWorkerState.clear(context);
        helper.assertFalse(LoggingWorkerState.isAccessLeaf(context, leaf), "worker clear should remove access leaf marker");
        helper.assertFalse(state.contains(LoggingWorkerState.TREE_SCAN_CURSOR_TAG), "worker clear should remove tree scan cursor");
        helper.assertFalse(state.contains(LoggingWorkerState.SAPLING_SCAN_CURSOR_TAG), "worker clear should remove sapling scan cursor");
        helper.assertFalse(state.contains(LoggingWorkerState.NEXT_TREE_SCAN_GAME_TIME_TAG), "worker clear should wake tree scans");
        helper.assertFalse(state.contains(LoggingWorkerState.NEXT_SAPLING_SCAN_GAME_TIME_TAG), "worker clear should wake sapling scans");
        helper.assertValueEqual(
                LoggingWorkerState.breakGoal(context, leaf, "minecraft:iron_axe", 0, 100, () -> {
                    breakGoalCalculations.incrementAndGet();
                    return 41;
                }),
                41,
                "worker clear should invalidate the tree break goal");
        helper.assertValueEqual(breakGoalCalculations.get(), 2, "cleared tree break goal should be recalculated");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void loggingTreeDiscoveryAnalyzesConnectedLogsOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos root = helper.absolutePos(new BlockPos(4, 2, 4));
        setBlock(level, root.below(), Blocks.DIRT.defaultBlockState());
        List<BlockPos> logs = new ArrayList<>();
        for (int y = 0; y < 4; y++) {
            BlockPos log = root.above(y);
            logs.add(log);
            setBlock(level, log, Blocks.OAK_LOG.defaultBlockState());
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        BlockPos crown = root.above(4);
        for (BlockPos leaf : List.of(crown, crown.north(), crown.south(), crown.east(), crown.west())) {
            setBlock(level, leaf, leaves);
        }

        LoggingTreeGeometry.DiscoveryCache discovery = LoggingTreeGeometry.discovery(level, Set.of());
        for (BlockPos log : logs) {
            helper.assertTrue(discovery.isNaturalTree(log), "connected log should share its tree classification");
        }
        helper.assertValueEqual(discovery.analysisCount(), 1, "one geometry analysis per connected tree");
        helper.assertValueEqual(discovery.distinctRoots(logs), List.of(root), "one route objective per connected tree");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 400)
    public static void activeLoggingHarvestFinishesItsTreeAfterFilterChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 6; z++) {
                setBlock(level, helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
            }
        }
        Villager villager = EntityType.VILLAGER.create(level);
        helper.assertTrue(villager != null, "logging fixture villager");
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 3));
        villager.moveTo(villagerPos.getX() + 0.5D, villagerPos.getY(), villagerPos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(level.addFreshEntity(villager), "logging fixture villager should enter the level");

        List<BlockPos> treeLogs = new ArrayList<>();
        for (int x = 3; x <= 6; x++) {
            for (int z = 2; z <= 4; z++) {
                BlockPos log = helper.absolutePos(new BlockPos(x, 2, z));
                treeLogs.add(log);
                setBlock(level, log.below(), Blocks.DIRT.defaultBlockState());
                setBlock(level, log, Blocks.OAK_LOG.defaultBlockState());
            }
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        BlockPos crown = helper.absolutePos(new BlockPos(4, 4, 3));
        for (BlockPos leaf : List.of(crown, crown.north(), crown.south(), crown.east(), crown.west())) {
            setBlock(level, leaf, leaves);
        }

        CompoundTag state = new CompoundTag();
        HiredLoggingOptions.initializeDefaults(state);
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_AXE));
        HiredWorkContext context = new HiredWorkContext(
                inventory,
                state,
                helper.absolutePos(new BlockPos(4, 3, 3)),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                helper.absolutePos(new BlockPos(7, 6, 5)),
                16,
                16,
                true,
                100,
                false,
                false);
        LoggingWorker worker = new LoggingWorker();

        runUntil(helper, level, villager, hirer, context, worker, 240, () -> {
            LoggingHarvestPlan.Snapshot plan = LoggingHarvestPlan.read(context);
            return plan != null && plan.logsCut() > 0 && plan.logs().length > 0;
        });
        LoggingHarvestPlan.Snapshot partial = LoggingHarvestPlan.read(context);
        helper.assertTrue(partial != null && partial.logsCut() > 0, "fixture should pause between bounded harvest batches");
        helper.assertValueEqual(partial.logFamily(), "minecraft:oak", "active tree family before filter change");

        HiredLoggingFilters.toggleFilter(state, "minecraft:birch_log");
        HiredWorkPlan.clear(context);
        context.setProgressTicks(0);
        runUntil(helper, level, villager, hirer, context, worker, 160, () ->
                treeLogs.stream().noneMatch(pos -> level.getBlockState(pos).is(BlockTags.OAK_LOGS)));

        helper.assertTrue(
                treeLogs.stream().noneMatch(pos -> level.getBlockState(pos).is(BlockTags.OAK_LOGS)),
                "remaining original logs should not be abandoned");
        helper.assertTrue(LoggingHarvestPlan.read(context) == null, "completed tree should clear its persisted harvest plan");
        helper.assertTrue(inventory.hasOutput(stack -> stack.is(Items.OAK_LOG)), "completed original tree should keep its oak output");
        villager.discard();
        helper.succeed();
    }

    private static HiredWorkContext context(CompoundTag state, BlockPos min, BlockPos max) {
        BlockPos center = new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2);
        return new HiredWorkContext(null, state, center, min, max, 32, 16, true, 100, false, false);
    }

    private static List<BlockPos> positions(int count, int y) {
        List<BlockPos> positions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int x = index % 31 - 15;
            int z = index / 31 - 10;
            positions.add(new BlockPos(x, y, z));
        }
        return positions;
    }

    private static List<Long> boxed(long[] values) {
        List<Long> boxed = new ArrayList<>(values.length);
        for (long value : values) {
            boxed.add(value);
        }
        return boxed;
    }

    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void runUntil(
            GameTestHelper helper,
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredWorkContext context,
            LoggingWorker worker,
            int maxTicks,
            java.util.function.BooleanSupplier done) {
        for (int tick = 0; tick < maxTicks && !done.getAsBoolean(); tick++) {
            worker.maintain(level, villager, context);
            worker.tick(level, villager, hirer, context);
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertTrue(done.getAsBoolean(), "logging worker did not reach the expected state in " + maxTicks + " ticks");
    }
}
