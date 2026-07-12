package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.network.VillageBoundsSyncPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillageBoundsDebugService {
    public static final double RADIUS = 256.0D;
    private static final int REFRESH_TICKS = 80;
    private static final int VISIBLE_TICKS = REFRESH_TICKS + 40;
    private static final Map<UUID, Subscription> SUBSCRIPTIONS = new HashMap<>();

    private VillageBoundsDebugService() {
    }

    public static void setSubscribed(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return;
        }
        if (!enabled) {
            SUBSCRIPTIONS.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, VillageBoundsSyncPayload.disabled(player.level().dimension().location()));
            return;
        }
        SUBSCRIPTIONS.put(player.getUUID(), new Subscription(0L, null));
        refresh(player);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Subscription subscription = SUBSCRIPTIONS.get(player.getUUID());
        if (subscription == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ResourceLocation dimension = level.dimension().location();
        long now = level.getServer().overworld().getGameTime();
        if (!dimension.equals(subscription.dimension()) || now >= subscription.nextRefreshGameTime()) {
            refresh(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SUBSCRIPTIONS.remove(player.getUUID());
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        SUBSCRIPTIONS.clear();
    }

    public static boolean isSubscribed(UUID playerId) {
        return playerId != null && SUBSCRIPTIONS.containsKey(playerId);
    }

    private static void refresh(ServerPlayer player) {
        if (!SUBSCRIPTIONS.containsKey(player.getUUID())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ResourceLocation dimension = level.dimension().location();
        double radiusSqr = RADIUS * RADIUS;
        List<VillageAllegianceRegistrySavedData.AllegianceRecord> nearby = VillageAllegianceRegistrySavedData.get(level)
                .activeRecords(dimension).stream()
                .filter(record -> record.lifecycleState() == VillageLifecycleState.ACTIVE
                        || record.lifecycleState() == VillageLifecycleState.EMPTY_GRACE)
                .filter(record -> record.center().distSqr(player.blockPosition()) <= radiusSqr)
                .filter(record -> !record.footprintSections().isEmpty())
                .filter(record -> record.footprintSections().size() <= VillageBoundsSyncPayload.MAX_SECTIONS_PER_VILLAGE)
                .sorted(Comparator.comparingDouble(record -> record.center().distSqr(player.blockPosition())))
                .toList();
        List<VillageBoundsSyncPayload.VillageEntry> entries = new ArrayList<>();
        int totalSections = 0;
        for (VillageAllegianceRegistrySavedData.AllegianceRecord record : nearby) {
            if (entries.size() >= VillageBoundsSyncPayload.MAX_VILLAGES
                    || totalSections + record.footprintSections().size() > VillageBoundsSyncPayload.MAX_TOTAL_SECTIONS) {
                break;
            }
            List<Long> sections = record.footprintSections().stream().sorted().toList();
            entries.add(new VillageBoundsSyncPayload.VillageEntry(
                    record.id(), record.displayName(), record.center(), record.lifecycleState(), sections));
            totalSections += sections.size();
        }
        PacketDistributor.sendToPlayer(player, new VillageBoundsSyncPayload(true, dimension, entries, VISIBLE_TICKS));
        long now = level.getServer().overworld().getGameTime();
        SUBSCRIPTIONS.put(player.getUUID(), new Subscription(now + REFRESH_TICKS, dimension));
    }

    private record Subscription(long nextRefreshGameTime, ResourceLocation dimension) {
    }
}
