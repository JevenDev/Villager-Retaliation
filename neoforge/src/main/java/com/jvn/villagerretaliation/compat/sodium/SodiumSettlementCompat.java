package com.jvn.villagerretaliation.compat.sodium;

import java.lang.reflect.Method;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

public final class SodiumSettlementCompat {
    private static final TrackerAccess TRACKER = findTracker();

    private SodiumSettlementCompat() {
    }

    public static void onChunkLoaded(ClientLevel level, ChunkPos position) {
        if (TRACKER == null) {
            return;
        }
        try {
            Object tracker = TRACKER.get().invoke(null, level);
            TRACKER.add().invoke(tracker, position.x, position.z, TRACKER.blockDataFlag());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register a settlement chunk with Sodium", exception);
        }
    }

    private static TrackerAccess findTracker() {
        if (!ModList.get().isLoaded("sodium")) {
            return null;
        }
        try {
            Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
            Class<?> status = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus");
            Method get = holder.getMethod("get", ClientLevel.class);
            Method add = get.getReturnType().getMethod("onChunkStatusAdded", int.class, int.class, int.class);
            return new TrackerAccess(get, add, status.getField("FLAG_HAS_BLOCK_DATA").getInt(null));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access Sodium's chunk tracker for settlement rendering", exception);
        }
    }

    private record TrackerAccess(Method get, Method add, int blockDataFlag) {
    }
}
