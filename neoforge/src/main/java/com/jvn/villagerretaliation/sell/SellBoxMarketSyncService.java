package com.jvn.villagerretaliation.sell;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.jvn.villagerretaliation.block.SellBoxMenu;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class SellBoxMarketSyncService {
    private static final Map<UUID, SyncKey> SYNCED = new LinkedHashMap<>();

    private SellBoxMarketSyncService() {
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long day = VillageSellMarket.currentDay(server);
        long generation = SellPriceResources.generation();
        Set<UUID> openPlayers = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof SellBoxMenu menu)) {
                continue;
            }
            SellBoxBlockEntity sellBox = menu.sellBox();
            if (sellBox == null) {
                continue;
            }
            openPlayers.add(player.getUUID());
            SyncKey key = syncKey(player, sellBox, day, generation);
            if (!key.equals(SYNCED.get(player.getUUID()))) {
                menu.sync(player);
            }
        }
        SYNCED.keySet().removeIf(playerId -> !openPlayers.contains(playerId));
    }

    public static void markSynced(ServerPlayer player, SellBoxBlockEntity sellBox) {
        if (player != null && sellBox != null) {
            SYNCED.put(player.getUUID(), syncKey(
                    player,
                    sellBox,
                    VillageSellMarket.currentDay(player.getServer()),
                    SellPriceResources.generation()));
        }
    }

    static boolean isSynced(ServerPlayer player, SellBoxBlockEntity sellBox) {
        if (player == null || sellBox == null) {
            return false;
        }
        return syncKey(
                        player,
                        sellBox,
                        VillageSellMarket.currentDay(player.getServer()),
                        SellPriceResources.generation())
                .equals(SYNCED.get(player.getUUID()));
    }

    private static SyncKey syncKey(
            ServerPlayer player,
            SellBoxBlockEntity sellBox,
            long day,
            long generation) {
        ServerLevel level = player.serverLevel();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> village =
                VillageSellMarket.resolveVillage(registry, level, sellBox.getBlockPos());
        long revision = village
                .map(id -> VillageMarketSavedData.get(level).revision(registry, id))
                .orElse(0L);
        return new SyncKey(
                level.dimension(),
                sellBox.getBlockPos(),
                village.orElse(null),
                revision,
                day,
                generation);
    }

    public static void clear(MinecraftServer server) {
        SYNCED.clear();
    }

    private record SyncKey(
            ResourceKey<Level> dimension,
            BlockPos position,
            VillageAllegianceId village,
            long revision,
            long day,
            long generation) {
        private SyncKey {
            position = position.immutable();
        }
    }
}
