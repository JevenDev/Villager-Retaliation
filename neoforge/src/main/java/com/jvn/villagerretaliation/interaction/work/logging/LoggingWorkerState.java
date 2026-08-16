package com.jvn.villagerretaliation.interaction.work.logging;

import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import java.util.function.IntSupplier;
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
    private static final String BREAK_GOAL_TARGET_TAG = "LoggingBreakGoalTarget";
    private static final String BREAK_GOAL_TOOL_TAG = "LoggingBreakGoalTool";
    private static final String BREAK_GOAL_TOOL_EFFICIENCY_TAG = "LoggingBreakGoalToolEfficiency";
    private static final String BREAK_GOAL_WORKER_EFFICIENCY_TAG = "LoggingBreakGoalWorkerEfficiency";
    private static final String BREAK_GOAL_TICKS_TAG = "LoggingBreakGoalTicks";

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

    static int breakGoal(
            HiredWorkContext context,
            BlockPos target,
            String toolId,
            int toolEfficiency,
            int workerEfficiency,
            IntSupplier calculator) {
        CompoundTag state = context.state();
        String safeToolId = toolId == null ? "" : toolId;
        if (context.progressTicks() > 0
                && state.contains(BREAK_GOAL_TARGET_TAG, Tag.TAG_LONG)
                && state.getLong(BREAK_GOAL_TARGET_TAG) == target.asLong()
                && state.contains(BREAK_GOAL_TOOL_TAG, Tag.TAG_STRING)
                && safeToolId.equals(state.getString(BREAK_GOAL_TOOL_TAG))
                && state.contains(BREAK_GOAL_TOOL_EFFICIENCY_TAG, Tag.TAG_INT)
                && state.getInt(BREAK_GOAL_TOOL_EFFICIENCY_TAG) == toolEfficiency
                && state.contains(BREAK_GOAL_WORKER_EFFICIENCY_TAG, Tag.TAG_INT)
                && state.getInt(BREAK_GOAL_WORKER_EFFICIENCY_TAG) == workerEfficiency
                && state.contains(BREAK_GOAL_TICKS_TAG, Tag.TAG_INT)
                && state.getInt(BREAK_GOAL_TICKS_TAG) > 0) {
            return state.getInt(BREAK_GOAL_TICKS_TAG);
        }

        int ticks = Math.max(1, calculator.getAsInt());
        state.putLong(BREAK_GOAL_TARGET_TAG, target.asLong());
        state.putString(BREAK_GOAL_TOOL_TAG, safeToolId);
        state.putInt(BREAK_GOAL_TOOL_EFFICIENCY_TAG, toolEfficiency);
        state.putInt(BREAK_GOAL_WORKER_EFFICIENCY_TAG, workerEfficiency);
        state.putInt(BREAK_GOAL_TICKS_TAG, ticks);
        return ticks;
    }

    static void clearBreakGoal(HiredWorkContext context) {
        CompoundTag state = context.state();
        state.remove(BREAK_GOAL_TARGET_TAG);
        state.remove(BREAK_GOAL_TOOL_TAG);
        state.remove(BREAK_GOAL_TOOL_EFFICIENCY_TAG);
        state.remove(BREAK_GOAL_WORKER_EFFICIENCY_TAG);
        state.remove(BREAK_GOAL_TICKS_TAG);
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
        clearBreakGoal(context);
        clearTargetSearch(context);
    }
}
