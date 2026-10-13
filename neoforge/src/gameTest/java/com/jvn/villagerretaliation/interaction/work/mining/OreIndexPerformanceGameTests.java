package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("resource_performance")
@PrefixGameTestTemplate(false)
public final class OreIndexPerformanceGameTests {
    private OreIndexPerformanceGameTests() { }

    @GameTest(template = "empty", batch = "ore_snapshot", timeoutTicks = 200)
    public static void copiedOreSnapshotRunsOffThreadAndIgnoresLaterEdits(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var source = new ProtoChunk(new ChunkPos(pos), UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        source.setBlockState(pos, Blocks.DIAMOND_ORE.defaultBlockState(), false);
        source.setBlockState(pos.above(32), Blocks.IRON_ORE.defaultBlockState(), false);
        var chunk = new LevelChunk(level, source, null);
        var snapshot = OreIndexSnapshot.capture(chunk, Set.of(Blocks.DIAMOND_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState()));
        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
        chunk.setBlockState(pos.east(), Blocks.DIAMOND_ORE.defaultBlockState(), false);
        Thread caller = Thread.currentThread();
        var offThread = new AtomicBoolean();
        var result = OreIndexTasks.submit(() -> {
            offThread.set(Thread.currentThread() != caller);
            return snapshot.scan();
        });
        helper.assertTrue(result != null, "snapshot task accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(result.isDone(), "snapshot finished without blocking server");
            var ores = result.resultNow();
            helper.assertTrue(offThread.get(), "index classification ran on a worker thread");
            helper.assertTrue(ores.contains(pos.asLong()) && ores.contains(pos.above(32).asLong()), "copied ore positions retained");
            helper.assertFalse(ores.contains(pos.east().asLong()), "later edits cannot mutate the copied palette");
        });
    }

    @GameTest(template = "empty", batch = "ore_pool_bounds", timeoutTicks = 200)
    public static void indexingPoolRejectsExcessWorkWithoutRunningItOnServer(GameTestHelper helper) {
        OreIndexTasks.clear();
        var release = new CountDownLatch(1);
        var tasks = new java.util.ArrayList<Future<it.unimi.dsi.fastutil.longs.LongSet>>();
        Thread caller = Thread.currentThread();
        var ranOnServer = new AtomicBoolean();
        int rejected = 0;
        try {
            for (int i = 0; i < 32; i++) {
                var result = OreIndexTasks.submit(() -> {
                    if (Thread.currentThread() == caller) ranOnServer.set(true);
                    release.await();
                    return new LongOpenHashSet();
                });
                if (result == null) rejected++;
                else tasks.add(result);
            }
            helper.assertTrue(rejected >= 22 && !tasks.isEmpty(), "at most two running and eight queued scans");
        } finally {
            release.countDown();
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(tasks.stream().allMatch(Future::isDone), "accepted jobs drain after backpressure");
            helper.assertFalse(ranOnServer.get(), "a full queue never runs scans on the server thread");
            tasks.forEach(Future::resultNow);
        });
    }

    @GameTest(template = "empty", batch = "ore_live_edits", timeoutTicks = 200)
    public static void pendingIndexMergesLiveEditsWithoutLoadingChunks(GameTestHelper helper) {
        HiredOreBlockTracker.clearRuntimeState();
        var level = helper.getLevel();
        var chunk = level.getChunkAt(helper.absolutePos(BlockPos.ZERO));
        var pos = new BlockPos(chunk.getPos().getMinBlockX() + 8, 64, chunk.getPos().getMinBlockZ() + 8);
        chunk.setBlockState(pos, Blocks.DIAMOND_ORE.defaultBlockState(), false);
        chunk.setBlockState(pos.above(32), Blocks.IRON_ORE.defaultBlockState(), false);
        int loaded = level.getChunkSource().getLoadedChunksCount();
        HiredOreBlockTracker.nearbyOreBlocks(level, pos, 1, 40);
        helper.assertFalse(HiredOreBlockTracker.ready(level, pos, 1), "large scan is deferred");
        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
        chunk.setBlockState(pos.east(), Blocks.COAL_ORE.defaultBlockState(), false);
        helper.succeedWhen(() -> {
            var ores = HiredOreBlockTracker.nearbyOreBlocks(level, pos, 1, 40);
            helper.assertTrue(HiredOreBlockTracker.ready(level, pos, 1), "index eventually ready");
            helper.assertFalse(ores.contains(pos), "removed ore cannot reappear from the snapshot");
            helper.assertTrue(ores.contains(pos.east()) && ores.contains(pos.above(32)), "new and unchanged ores indexed");
            for (int worker = 0; worker < 200; worker++) {
                helper.assertValueEqual(HiredOreBlockTracker.nearbyOreBlocks(level, pos, 1, 40), ores, "workers share the completed index");
            }
            var detachedSource = new ProtoChunk(chunk.getPos(), UpgradeData.EMPTY, level,
                    level.registryAccess().registryOrThrow(Registries.BIOME), null);
            detachedSource.setBlockState(pos.east(), Blocks.COAL_ORE.defaultBlockState(), false);
            var detached = new LevelChunk(level, detachedSource, null);
            detached.setBlockState(pos.east(), Blocks.AIR.defaultBlockState(), false);
            helper.assertTrue(HiredOreBlockTracker.nearbyOreBlocks(level, pos, 1, 40).contains(pos.east()),
                    "edits to a detached chunk cannot corrupt the live index at the same coordinates");
            helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), loaded, "indexing never requests chunks");
            chunk.setBlockState(pos.east(), Blocks.AIR.defaultBlockState(), false);
            chunk.setBlockState(pos.above(32), Blocks.AIR.defaultBlockState(), false);
        });
    }

    @GameTest(template = "empty", batch = "ore_unload", timeoutTicks = 200)
    public static void unloadingDiscardsPendingIndexes(GameTestHelper helper) {
        HiredOreBlockTracker.clearRuntimeState();
        var level = helper.getLevel();
        var chunk = level.getChunkAt(helper.absolutePos(BlockPos.ZERO));
        var pos = new BlockPos(chunk.getPos().getMinBlockX() + 8, 96, chunk.getPos().getMinBlockZ() + 8);
        chunk.setBlockState(pos, Blocks.DIAMOND_ORE.defaultBlockState(), false);
        chunk.setBlockState(pos.above(32), Blocks.IRON_ORE.defaultBlockState(), false);
        HiredOreBlockTracker.nearbyOreBlocks(level, pos, 1, 40);
        HiredOreBlockTracker.onChunkUnload(new ChunkEvent.Unload(chunk));
        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
        chunk.setBlockState(pos.east(), Blocks.COAL_ORE.defaultBlockState(), false);
        helper.assertFalse(HiredOreBlockTracker.ready(level, pos, 1), "unloaded index is unavailable");
        helper.succeedWhen(() -> {
            var ores = HiredOreBlockTracker.nearbyOreBlocks(level, pos, 1, 40);
            helper.assertTrue(HiredOreBlockTracker.ready(level, pos, 1), "new snapshot completes after invalidation");
            helper.assertFalse(ores.contains(pos), "old result cannot resurrect removed ore");
            helper.assertTrue(ores.contains(pos.east()), "replacement snapshot includes current terrain");
            chunk.setBlockState(pos.east(), Blocks.AIR.defaultBlockState(), false);
            chunk.setBlockState(pos.above(32), Blocks.AIR.defaultBlockState(), false);
        });
    }

    @GameTest(template = "empty", batch = "ore_reload", timeoutTicks = 200)
    public static void tagReloadDiscardsPendingClassificationAndRestartsPool(GameTestHelper helper) {
        HiredOreBlockTracker.clearRuntimeState();
        var level = helper.getLevel();
        var chunk = level.getChunkAt(helper.absolutePos(BlockPos.ZERO));
        var pos = new BlockPos(chunk.getPos().getMinBlockX() + 8, 128, chunk.getPos().getMinBlockZ() + 8);
        chunk.setBlockState(pos, Blocks.DIAMOND_ORE.defaultBlockState(), false);
        chunk.setBlockState(pos.above(32), Blocks.IRON_ORE.defaultBlockState(), false);
        HiredOreBlockTracker.nearbyOreBlocks(level, pos, 1, 40);
        OreNodeDefinitions.onTagsUpdated(new net.neoforged.neoforge.event.TagsUpdatedEvent(level.registryAccess(), false, false));
        helper.assertFalse(HiredOreBlockTracker.ready(level, pos, 1), "reload discards old classification and pending work");
        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
        chunk.setBlockState(pos.east(), Blocks.COAL_ORE.defaultBlockState(), false);
        helper.succeedWhen(() -> {
            var ores = HiredOreBlockTracker.nearbyOreBlocks(level, pos, 1, 40);
            helper.assertTrue(HiredOreBlockTracker.ready(level, pos, 1), "pool restarts after reload");
            helper.assertFalse(ores.contains(pos), "pre-reload snapshot cannot publish");
            helper.assertTrue(ores.contains(pos.east()), "new rules classify current terrain");
            helper.assertValueEqual(OreNodeDefinitions.extractions(Blocks.DIAMOND_ORE.defaultBlockState()), 10, "reloaded count remains correct");
            chunk.setBlockState(pos.east(), Blocks.AIR.defaultBlockState(), false);
            chunk.setBlockState(pos.above(32), Blocks.AIR.defaultBlockState(), false);
        });
    }

    @GameTest(template = "empty", batch = "completion_scan")
    public static void completionChecksStopAtExistingWorkAndContinueEmptyPasses(GameTestHelper helper) {
        var state = new CompoundTag();
        var context = new com.jvn.villagerretaliation.interaction.work.HiredWorkContext(null, state,
                BlockPos.ZERO, BlockPos.ZERO, new BlockPos(31, 31, 31), 32, 32, true, 1, false, false);
        var found = com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan.findAny(context, "scan", 768, pos -> true);
        helper.assertValueEqual(found.visitedPositions(), 1L, "completion stops after finding unfinished work");
        helper.assertFalse(found.completedFullPass(), "finding a target must not finish the task");
        state.remove("scan");
        long visited = 0;
        com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan.Result empty;
        do {
            empty = com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan.findAny(context, "scan", 768, pos -> false);
            visited += empty.visitedPositions();
            helper.assertTrue(empty.visitedPositions() <= 768, "each empty scan stays within its tick budget");
        } while (!empty.completedFullPass());
        helper.assertValueEqual(visited, 32768L, "empty completion still checks the entire volume");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "order_cache")
    public static void cachedOrdersStillValidateEditsAndPendingChanges(GameTestHelper helper) {
        var state = new CompoundTag();
        var task = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.ORE_AREA, BlockPos.ZERO, new BlockPos(8, 8, 8), 1, null);
        new HiredTemporaryQueue(UUID.randomUUID(), UUID.randomUUID(), null, HiredVillagerRole.MINING,
                BlockPos.ZERO, new BlockPos(8, 8, 8), List.of(task)).store(state);
        var first = HiredTemporaryWorkService.queue(state);
        helper.assertTrue(first != null && first == HiredTemporaryWorkService.queue(state), "unchanged order reuses parsed queue");
        var encoded = state.getCompound(HiredTemporaryQueue.STATE_TAG).getList("Tasks", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        encoded.putString("Material", "#c:ores/iron");
        var changed = HiredTemporaryWorkService.queue(state);
        helper.assertTrue(changed != first && changed.current().material().equals("#c:ores/iron"), "in-place edit invalidates cached parse");
        encoded.putInt("Remaining", 0);
        helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "invalid cached queue is rejected");
        encoded.putInt("Remaining", 1);
        helper.assertTrue(HiredTemporaryWorkService.queue(state) != null, "repaired queue becomes usable");
        state.putString(HiredTemporaryQueue.PENDING_TAG, "invalid");
        helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "cache cannot bypass pending validation");
        helper.succeed();
    }
}
