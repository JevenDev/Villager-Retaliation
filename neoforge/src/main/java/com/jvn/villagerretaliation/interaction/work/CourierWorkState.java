package com.jvn.villagerretaliation.interaction.work;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** The single persistence boundary for courier-specific work state. */
public final class CourierWorkState {
    private static final String PHASE_TAG = "CourierPhase";
    private static final String STORAGE_TARGET_TAG = "CourierStorageTarget";
    private static final String STORAGE_PURPOSE_TAG = "CourierStoragePurpose";
    private static final String STORAGE_RETURN_TO_NODE_TAG = "CourierStorageReturnToNode";
    private static final String VISITED_STORAGE_TAG = "CourierVisitedStorage";
    private static final String STORAGE_BATCH_TAG = "CourierStorageBatch";
    private static final String ROUTE_INDEX_TAG = "CourierRouteIndex";
    private static final String ROUTE_DIRECTION_TAG = "CourierRouteDirection";
    private static final String RETURN_TO_INPUT_SWEEP_TAG = "CourierReturnToInputSweep";
    private static final String ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG =
            "CourierRouteLastNodeReachedGameTime";
    private static final RouteTraversalCursor.Tags ROUTE_CURSOR_TAGS = new RouteTraversalCursor.Tags(
            ROUTE_INDEX_TAG, ROUTE_DIRECTION_TAG, ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG);

    private final CompoundTag state;

    public CourierWorkState(CompoundTag state) {
        this.state = state;
    }

    public RouteTraversalCursor routeCursor() {
        return new RouteTraversalCursor(state, ROUTE_CURSOR_TAGS);
    }

    public String phase(String defaultPhase) {
        String phase = state.getString(PHASE_TAG);
        return phase.isBlank() ? defaultPhase : phase;
    }

    public void setPhase(String phase) {
        state.putString(PHASE_TAG, phase);
        routeCursor().clearWatchdog();
    }

    public BlockPos storageTarget() {
        return state.contains(STORAGE_TARGET_TAG, Tag.TAG_LONG)
                ? BlockPos.of(state.getLong(STORAGE_TARGET_TAG))
                : null;
    }

    public void setStorageTarget(BlockPos target) {
        state.putLong(STORAGE_TARGET_TAG, target.asLong());
    }

    public void clearStorageTarget() {
        state.remove(STORAGE_TARGET_TAG);
    }

    public boolean hasStoragePurpose() {
        return state.contains(STORAGE_PURPOSE_TAG, Tag.TAG_STRING);
    }

    public String storagePurpose() {
        return state.getString(STORAGE_PURPOSE_TAG);
    }

    public void setStoragePurpose(String purpose) {
        state.putString(STORAGE_PURPOSE_TAG, purpose);
    }

    public void clearStoragePurpose() {
        state.remove(STORAGE_PURPOSE_TAG);
    }

    public boolean returningToRouteNode() {
        return state.getBoolean(STORAGE_RETURN_TO_NODE_TAG);
    }

    public void setReturningToRouteNode(boolean returning) {
        if (returning) {
            state.putBoolean(STORAGE_RETURN_TO_NODE_TAG, true);
        } else {
            state.remove(STORAGE_RETURN_TO_NODE_TAG);
        }
    }

    public long[] visitedStorage() {
        return state.getLongArray(VISITED_STORAGE_TAG);
    }

    public boolean hasVisited(BlockPos pos) {
        long packed = pos.asLong();
        return Arrays.stream(visitedStorage()).anyMatch(visited -> visited == packed);
    }

    public void setVisitedStorage(long[] positions) {
        state.putLongArray(VISITED_STORAGE_TAG, positions);
    }

    public void clearVisitedStorage() {
        state.remove(VISITED_STORAGE_TAG);
    }

    public long[] storageBatch() {
        return state.getLongArray(STORAGE_BATCH_TAG);
    }

    public void setStorageBatch(long[] positions) {
        state.putLongArray(STORAGE_BATCH_TAG, positions);
    }

    public void clearStorageBatch() {
        state.remove(STORAGE_BATCH_TAG);
    }

    public boolean returnToInputSweep() {
        return state.getBoolean(RETURN_TO_INPUT_SWEEP_TAG);
    }

    public void setReturnToInputSweep(boolean returnToInputSweep) {
        if (returnToInputSweep) {
            state.putBoolean(RETURN_TO_INPUT_SWEEP_TAG, true);
        } else {
            state.remove(RETURN_TO_INPUT_SWEEP_TAG);
        }
    }

    public void clearTrip() {
        state.remove(PHASE_TAG);
        clearStorageTarget();
        clearStoragePurpose();
        setReturningToRouteNode(false);
        clearVisitedStorage();
        clearStorageBatch();
        routeCursor().clear();
        setReturnToInputSweep(false);
    }
}
