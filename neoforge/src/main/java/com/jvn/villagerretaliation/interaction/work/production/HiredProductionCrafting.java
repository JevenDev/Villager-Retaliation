package com.jvn.villagerretaliation.interaction.work.production;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public final class HiredProductionCrafting {
    private static final String ALLOCATION_TAG = "CraftsmanProductionAllocation";

    private HiredProductionCrafting() {
    }

    public static HiredProductionQueue.Allocation reserve(ServerLevel level, HiredProductionQueue queue,
            UUID worker, HiredProductionOrder order, int batchSize, long stock, Runnable resetProgress) {
        var allocation = queue.allocation(worker);
        if (allocation != null && !allocation.order().equals(order.id())) {
            if (allocation.started()) return null;
            releasePlanned(level, queue, allocation);
            resetProgress.run();
            allocation = null;
        }
        return allocation != null ? allocation : queue.reserve(worker, order.id(), batchSize, 1, stock, null);
    }

    public static void trackProgress(CompoundTag state, HiredProductionQueue.Allocation allocation, Runnable resetProgress) {
        if (!state.hasUUID(ALLOCATION_TAG) || !state.getUUID(ALLOCATION_TAG).equals(allocation.id())) {
            resetProgress.run();
            state.putUUID(ALLOCATION_TAG, allocation.id());
        }
    }

    public static void clearProgress(CompoundTag state, Runnable resetProgress) {
        state.remove(ALLOCATION_TAG);
        resetProgress.run();
    }

    public static void releasePlanned(ServerLevel level, HiredProductionQueue queue, HiredProductionQueue.Allocation allocation) {
        if (allocation != null && !allocation.started()) {
            HiredProductionStations.get(level).release(allocation.id());
            queue.release(allocation.id());
        }
    }
}
