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
        String stateId = safeState.id();
        boolean keepBlockTarget = targetPos != null && safeState.keepsBlockTarget();
        long packedTarget = keepBlockTarget ? targetPos.asLong() : 0L;
        boolean stateChanged = !state.contains(WORKER_TASK_STATE_TAG, Tag.TAG_STRING)
                || !stateId.equals(state.getString(WORKER_TASK_STATE_TAG));
        boolean targetChanged = keepBlockTarget
                ? !state.contains(WORKER_TASK_TARGET_POS_TAG, Tag.TAG_LONG)
                || state.getLong(WORKER_TASK_TARGET_POS_TAG) != packedTarget
                : state.contains(WORKER_TASK_TARGET_POS_TAG);
        boolean storageChanged = !safeState.keepsStorageTarget() && state.contains(WORKER_STORAGE_TARGET_POS_TAG);
        if (!stateChanged && !targetChanged && !storageChanged) {
            return;
        }

        if (stateChanged) {
            state.putString(WORKER_TASK_STATE_TAG, stateId);
        }
        if (keepBlockTarget && targetChanged) {
            state.putLong(WORKER_TASK_TARGET_POS_TAG, packedTarget);
        } else if (!keepBlockTarget && targetChanged) {
            state.remove(WORKER_TASK_TARGET_POS_TAG);
        }
        if (storageChanged) {
            state.remove(WORKER_STORAGE_TARGET_POS_TAG);
        }
    }

    public static void setStorageTarget(HiredWorkContext context, BlockPos storagePos) {
        if (storagePos == null) {
            clearStorageTarget(context);
            return;
        }
        CompoundTag state = context.state();
        long packed = storagePos.asLong();
        if (!state.contains(WORKER_STORAGE_TARGET_POS_TAG, Tag.TAG_LONG)
                || state.getLong(WORKER_STORAGE_TARGET_POS_TAG) != packed) {
            state.putLong(WORKER_STORAGE_TARGET_POS_TAG, packed);
        }
    }

    public static void clearTarget(HiredWorkContext context) {
        if (context.state().contains(WORKER_TASK_TARGET_POS_TAG)) {
            context.state().remove(WORKER_TASK_TARGET_POS_TAG);
        }
    }

    static void clearStorageTarget(HiredWorkContext context) {
        if (context.state().contains(WORKER_STORAGE_TARGET_POS_TAG)) {
            context.state().remove(WORKER_STORAGE_TARGET_POS_TAG);
        }
    }

    public static void setFailure(HiredWorkContext context, String reason, long retryAfterGameTime) {
        CompoundTag state = context.state();
        String safeReason = reason == null ? "" : reason;
        if (!state.contains(WORKER_FAILURE_REASON_TAG, Tag.TAG_STRING)
                || !safeReason.equals(state.getString(WORKER_FAILURE_REASON_TAG))) {
            state.putString(WORKER_FAILURE_REASON_TAG, safeReason);
        }
        if (retryAfterGameTime > 0L) {
            if (!state.contains(WORKER_RETRY_AFTER_GAME_TIME_TAG, Tag.TAG_LONG)
                    || state.getLong(WORKER_RETRY_AFTER_GAME_TIME_TAG) != retryAfterGameTime) {
                state.putLong(WORKER_RETRY_AFTER_GAME_TIME_TAG, retryAfterGameTime);
            }
        } else if (state.contains(WORKER_RETRY_AFTER_GAME_TIME_TAG)) {
            state.remove(WORKER_RETRY_AFTER_GAME_TIME_TAG);
        }
    }

    public static void clearFailure(HiredWorkContext context) {
        if (context.state().contains(WORKER_FAILURE_REASON_TAG)) {
            context.state().remove(WORKER_FAILURE_REASON_TAG);
        }
        if (context.state().contains(WORKER_RETRY_AFTER_GAME_TIME_TAG)) {
            context.state().remove(WORKER_RETRY_AFTER_GAME_TIME_TAG);
        }
    }

    public static void setLastTargetScanResult(HiredWorkContext context, String result) {
        String safeResult = result == null ? "" : result;
        if (!context.state().contains(WORKER_LAST_TARGET_SCAN_RESULT_TAG, Tag.TAG_STRING)
                || !safeResult.equals(context.state().getString(WORKER_LAST_TARGET_SCAN_RESULT_TAG))) {
            context.state().putString(WORKER_LAST_TARGET_SCAN_RESULT_TAG, safeResult);
        }
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
