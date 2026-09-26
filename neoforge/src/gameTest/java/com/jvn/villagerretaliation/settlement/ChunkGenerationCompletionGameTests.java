package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class ChunkGenerationCompletionGameTests {
    private ChunkGenerationCompletionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void unloadingWaitsForEveryGenerationReferenceAndEachNewCycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var holder = new ChunkHolder(new ChunkPos(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)), 33,
                level, level.getChunkSource().getLightEngine(), (position, oldLevel, newLevel, update) -> {},
                (position, boundary) -> List.of());
        helper.assertTrue(holder.getSaveSyncFuture().isDone(), "idle terrain is ready to unload");
        holder.increaseGenerationRefCount();
        holder.increaseGenerationRefCount();
        var first = holder.getSaveSyncFuture();
        helper.assertFalse(first.isDone(), "unloading waits while terrain generation has references");
        holder.decreaseGenerationRefCount();
        helper.assertFalse(first.isDone(), "one remaining generator still prevents unloading");
        holder.decreaseGenerationRefCount();
        helper.assertTrue(first.isDone() && holder.isReadyForSaving(), "the last release allows saving and unloading");
        holder.increaseGenerationRefCount();
        var second = holder.getSaveSyncFuture();
        helper.assertTrue(first.isDone(), "a later generation cycle cannot invalidate a completed save");
        helper.assertFalse(second.isDone(), "a later generation cycle has its own completion");
        holder.decreaseGenerationRefCount();
        helper.assertTrue(second.isDone() && holder.isReadyForSaving(), "the later cycle also releases cleanly");
        helper.succeed();
    }
}
