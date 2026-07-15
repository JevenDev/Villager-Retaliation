package com.jvn.villagerretaliation.interaction.work.logging;

import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Small persisted state transitions that belong to the logging routine itself. */
final class LoggingWorkerState {
    static final String NEXT_TREE_SCAN_GAME_TIME_TAG = "NextLoggingTreeScanGameTime";
    static final String TREE_SCAN_CURSOR_TAG = "LoggingTreeScanCursor";
    static final String NEXT_SAPLING_SCAN_GAME_TIME_TAG = "NextLoggingSaplingScanGameTime";
    static final String SAPLING_SCAN_CURSOR_TAG = "LoggingSaplingScanCursor";

    private static final String ACTIVE_ACCESS_LEAF_TAG = "LoggingActiveAccessLeaf";

    private LoggingWorkerState() {
    }

    static void markAccessLeaf(HiredWorkContext context, BlockPos pos) {
        if (pos == null || !context.isInsideWorkArea(pos)) {
            clearAccessLeaf(context);
            return;
        }
        context.state().putLong(ACTIVE_ACCESS_LEAF_TAG, pos.asLong());
    }

    static boolean isAccessLeaf(HiredWorkContext context, BlockPos pos) {
        CompoundTag state = context.state();
        if (pos == null || !state.contains(ACTIVE_ACCESS_LEAF_TAG, Tag.TAG_LONG)) {
            return false;
        }
        BlockPos stored = BlockPos.of(state.getLong(ACTIVE_ACCESS_LEAF_TAG));
        if (!context.isInsideWorkArea(stored)) {
            clearAccessLeaf(context);
            return false;
        }
        return stored.equals(pos);
    }

    static void clearAccessLeaf(HiredWorkContext context) {
        context.state().remove(ACTIVE_ACCESS_LEAF_TAG);
    }

    static void clearTargetSearch(HiredWorkContext context) {
        HiredWorkAreaScan.clearCursor(context, TREE_SCAN_CURSOR_TAG);
        HiredWorkAreaScan.clearCursor(context, SAPLING_SCAN_CURSOR_TAG);
        wakeTreeSearch(context);
        wakeSaplingSearch(context);
    }

    static void wakeTreeSearch(HiredWorkContext context) {
        context.state().remove(NEXT_TREE_SCAN_GAME_TIME_TAG);
    }

    static void wakeSaplingSearch(HiredWorkContext context) {
        context.state().remove(NEXT_SAPLING_SCAN_GAME_TIME_TAG);
    }

    static void clear(HiredWorkContext context) {
        clearAccessLeaf(context);
        clearTargetSearch(context);
    }
}
