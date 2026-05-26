package com.jvn.villagerretaliation.client.profile;

import com.jvn.villagerretaliation.network.VillagerProfileSyncPayload;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeRank;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class VillagerProfileClientCache {
    private static final long PRUNE_INTERVAL_TICKS = 40L;
    private static final Map<UUID, DisplayEntry> BY_VILLAGER_UUID = new HashMap<>();
    private static final Map<Integer, UUID> ENTITY_ID_TO_UUID = new HashMap<>();
    private static long nextPruneGameTime;

    private VillagerProfileClientCache() {
    }

    public static void accept(VillagerProfileSyncPayload payload) {
        long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        DisplayEntry entry = new DisplayEntry(
                payload.entityId(),
                payload.villagerId(),
                payload.professionKey(),
                payload.generatedVersion(),
                payload.attributes(),
                gameTime
        );
        BY_VILLAGER_UUID.put(payload.villagerId(), entry);
        ENTITY_ID_TO_UUID.put(payload.entityId(), payload.villagerId());
    }

    public static Optional<DisplayEntry> get(UUID villagerId, int entityId) {
        DisplayEntry entry = villagerId == null ? null : BY_VILLAGER_UUID.get(villagerId);
        if (entry != null) {
            return Optional.of(entry);
        }
        UUID mappedId = ENTITY_ID_TO_UUID.get(entityId);
        return mappedId == null ? Optional.empty() : Optional.ofNullable(BY_VILLAGER_UUID.get(mappedId));
    }

    public static Optional<DisplayEntry> get(int entityId) {
        UUID mappedId = ENTITY_ID_TO_UUID.get(entityId);
        return mappedId == null ? Optional.empty() : Optional.ofNullable(BY_VILLAGER_UUID.get(mappedId));
    }

    public static void clear() {
        BY_VILLAGER_UUID.clear();
        ENTITY_ID_TO_UUID.clear();
        nextPruneGameTime = 0L;
    }

    public static void pruneMissing() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        if (gameTime < nextPruneGameTime) {
            return;
        }
        nextPruneGameTime = gameTime + PRUNE_INTERVAL_TICKS;

        Iterator<Map.Entry<Integer, UUID>> iterator = ENTITY_ID_TO_UUID.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, UUID> entry = iterator.next();
            if (minecraft.level.getEntity(entry.getKey()) == null) {
                BY_VILLAGER_UUID.remove(entry.getValue());
                iterator.remove();
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        pruneMissing();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public record DisplayEntry(
            int entityId,
            UUID villagerId,
            String professionKey,
            int generatedVersion,
            VillagerSocialAttributes attributes,
            long lastUpdateGameTime) {
        public int value(VillagerSocialAttribute attribute) {
            return this.attributes.get(attribute);
        }

        public VillagerSocialAttributeRank rank(VillagerSocialAttribute attribute) {
            return this.attributes.rank(attribute);
        }
    }
}
