package com.jvn.villagerretaliation.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Bounds repeatable client requests before they can create main-thread world work. */
public final class ServerboundRequestLimiter {
    private static final Map<RequestKey, Long> NEXT_ALLOWED_TICKS = new HashMap<>();

    private ServerboundRequestLimiter() {
    }

    public static boolean tryAcquire(
            ServerPlayer player,
            ResourceLocation requestId,
            long intervalTicks) {
        if (player == null || requestId == null) {
            return false;
        }
        return tryAcquire(player.getUUID(), requestId,
                player.serverLevel().getServer().overworld().getGameTime(), intervalTicks);
    }

    public static boolean tryAcquire(
            UUID playerId,
            ResourceLocation requestId,
            long gameTime,
            long intervalTicks) {
        if (playerId == null || requestId == null) {
            return false;
        }
        RequestKey key = new RequestKey(playerId, requestId);
        Long nextAllowed = NEXT_ALLOWED_TICKS.get(key);
        if (nextAllowed != null && gameTime < nextAllowed) {
            return false;
        }
        NEXT_ALLOWED_TICKS.put(key, gameTime + Math.max(1L, intervalTicks));
        return true;
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            NEXT_ALLOWED_TICKS.keySet().removeIf(key -> key.playerId().equals(playerId));
        }
    }

    public static void clearRuntimeState() {
        NEXT_ALLOWED_TICKS.clear();
    }


    private record RequestKey(UUID playerId, ResourceLocation requestId) {
    }
}
