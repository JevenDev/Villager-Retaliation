package com.jvn.villagerretaliation.interaction.work;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class HiredWorkerBrain {
    private static final String WORKER_TASK_STATE_TAG = "WorkerTaskState";
    private static final String WORKER_TASK_TARGET_POS_TAG = "WorkerTaskTargetPos";
    private static final String WORKER_STORAGE_TARGET_POS_TAG = "WorkerStorageTargetPos";
    private static final String WORKER_FAILURE_REASON_TAG = "WorkerFailureReason";
    private static final String WORKER_RETRY_AFTER_GAME_TIME_TAG = "WorkerRetryAfterGameTime";
    private static final String WORKER_LAST_TARGET_SCAN_RESULT_TAG = "WorkerLastTargetScanResult";

    private HiredWorkerBrain() {
    }

    public static void initialize(CompoundTag state) {
        if (!state.contains(WORKER_TASK_STATE_TAG, Tag.TAG_STRING)) {
            state.putString(WORKER_TASK_STATE_TAG, HiredWorkerTaskState.IDLE.id());
        }
    }

    public static void setState(HiredWorkContext context, HiredWorkerTaskState taskState) {
        setState(context, taskState, null);
    }

    public static void setState(HiredWorkContext context, HiredWorkerTaskState taskState, BlockPos targetPos) {
        setState(context.state(), taskState, targetPos);
    }

    public static void setState(CompoundTag state, HiredWorkerTaskState taskState, BlockPos targetPos) {
        HiredWorkerTaskState safeState = taskState == null ? HiredWorkerTaskState.IDLE : taskState;
        state.putString(WORKER_TASK_STATE_TAG, safeState.id());
        if (targetPos != null && safeState.keepsBlockTarget()) {
            state.putLong(WORKER_TASK_TARGET_POS_TAG, targetPos.asLong());
        } else if (!safeState.keepsBlockTarget()) {
            state.remove(WORKER_TASK_TARGET_POS_TAG);
        }
        if (!safeState.keepsStorageTarget()) {
            state.remove(WORKER_STORAGE_TARGET_POS_TAG);
        }
    }

    public static void setStorageTarget(HiredWorkContext context, BlockPos storagePos) {
        if (storagePos == null) {
            clearStorageTarget(context);
            return;
        }
        context.state().putLong(WORKER_STORAGE_TARGET_POS_TAG, storagePos.asLong());
    }

    public static void clearTarget(HiredWorkContext context) {
        context.state().remove(WORKER_TASK_TARGET_POS_TAG);
    }

    public static void clearStorageTarget(HiredWorkContext context) {
        context.state().remove(WORKER_STORAGE_TARGET_POS_TAG);
    }

    public static void setFailure(HiredWorkContext context, String reason, long retryAfterGameTime) {
        CompoundTag state = context.state();
        state.putString(WORKER_FAILURE_REASON_TAG, reason == null ? "" : reason);
        if (retryAfterGameTime > 0L) {
            state.putLong(WORKER_RETRY_AFTER_GAME_TIME_TAG, retryAfterGameTime);
        } else {
            state.remove(WORKER_RETRY_AFTER_GAME_TIME_TAG);
        }
    }

    public static void clearFailure(HiredWorkContext context) {
        context.state().remove(WORKER_FAILURE_REASON_TAG);
        context.state().remove(WORKER_RETRY_AFTER_GAME_TIME_TAG);
    }

    public static void setLastTargetScanResult(HiredWorkContext context, String result) {
        context.state().putString(WORKER_LAST_TARGET_SCAN_RESULT_TAG, result == null ? "" : result);
    }

    public static Snapshot snapshot(CompoundTag state, long nowGameTime) {
        initialize(state);
        long retryAfterGameTime = state.getLong(WORKER_RETRY_AFTER_GAME_TIME_TAG);
        long retryCooldownTicks = retryAfterGameTime > nowGameTime ? retryAfterGameTime - nowGameTime : 0L;
        return new Snapshot(
                HiredWorkerTaskState.byId(state.getString(WORKER_TASK_STATE_TAG)),
                readPos(state, WORKER_TASK_TARGET_POS_TAG),
                readPos(state, WORKER_STORAGE_TARGET_POS_TAG),
                state.getString(WORKER_FAILURE_REASON_TAG),
                retryCooldownTicks,
                state.getString(WORKER_LAST_TARGET_SCAN_RESULT_TAG),
                state.getInt("ProgressTicks"),
                state.getString("Status"));
    }

    public static String formatPos(BlockPos pos) {
        return pos == null ? "none" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static BlockPos readPos(CompoundTag state, String tagName) {
        return state.contains(tagName, Tag.TAG_LONG) ? BlockPos.of(state.getLong(tagName)) : null;
    }

    public record Snapshot(
            HiredWorkerTaskState taskState,
            BlockPos targetPos,
            BlockPos storageTargetPos,
            String failureReason,
            long retryCooldownTicks,
            String lastTargetScanResult,
            int progressTicks,
            String status) {
    }
}
