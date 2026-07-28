package com.jvn.villagerretaliation.sell;

import com.jvn.villagerretaliation.block.SellBoxMenu;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class SellBoxMarketSyncService {
    private static MinecraftServer trackedServer;
    private static long syncedDay = Long.MIN_VALUE;
    private static long syncedGeneration = Long.MIN_VALUE;

    private SellBoxMarketSyncService() {
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long day = DailySellMarket.currentDay(server);
        long generation = SellPriceResources.generation();
        if (trackedServer == server && syncedDay == day && syncedGeneration == generation) {
            return;
        }
        trackedServer = server;
        syncedDay = day;
        syncedGeneration = generation;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof SellBoxMenu menu) {
                menu.sync(player);
            }
        }
    }

    public static void clear(MinecraftServer server) {
        if (trackedServer == server) {
            trackedServer = null;
            syncedDay = Long.MIN_VALUE;
            syncedGeneration = Long.MIN_VALUE;
        }
    }
}
