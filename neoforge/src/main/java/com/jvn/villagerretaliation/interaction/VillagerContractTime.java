package com.jvn.villagerretaliation.interaction;

import net.minecraft.util.Mth;

/** Shared absolute-game-time rules for prepaid villager contracts. */
public final class VillagerContractTime {
    public static final long DAY_TICKS = 24000L;
    public static final int MAX_PREPAID_DAYS = 30;

    private VillagerContractTime() {
    }

    public static int clampedPurchaseDays(int days) {
        return Mth.clamp(days, 1, MAX_PREPAID_DAYS);
    }

    public static int remainingDays(long gameTime, long endGameTime) {
        long remainingTicks = Math.max(0L, endGameTime - gameTime);
        return (int) Math.max(1L, (remainingTicks + DAY_TICKS - 1L) / DAY_TICKS);
    }

    public static int availableExtensionDays(long gameTime, long endGameTime, int requestedDays) {
        int safeRequestedDays = clampedPurchaseDays(requestedDays);
        long currentEnd = Math.max(gameTime, endGameTime);
        long remainingTicks = Math.max(0L, currentEnd - gameTime);
        long availableTicks = Math.max(0L, MAX_PREPAID_DAYS * DAY_TICKS - remainingTicks);
        int maxAdditionalDays = (int) Math.min(MAX_PREPAID_DAYS, availableTicks / DAY_TICKS);
        return Math.min(safeRequestedDays, maxAdditionalDays);
    }

    public static long endAfterDays(long gameTime, int days) {
        return gameTime + (long) clampedPurchaseDays(days) * DAY_TICKS;
    }

    public static long extendEnd(long gameTime, long endGameTime, int days) {
        int extensionDays = availableExtensionDays(gameTime, endGameTime, days);
        return Math.max(gameTime, endGameTime) + (long) extensionDays * DAY_TICKS;
    }

    public static boolean isExpired(long gameTime, long endGameTime) {
        return gameTime >= endGameTime;
    }
}
