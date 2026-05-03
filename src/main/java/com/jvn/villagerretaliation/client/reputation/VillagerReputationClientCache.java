package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.network.VillagerReputationSyncPayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class VillagerReputationClientCache {
    private static final Map<UUID, DisplayEntry> BY_VILLAGER_UUID = new HashMap<>();
    private static final Map<Integer, UUID> ENTITY_ID_TO_UUID = new HashMap<>();

    private VillagerReputationClientCache() {
    }

    public static void accept(VillagerReputationSyncPayload payload) {
        long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        BY_VILLAGER_UUID.put(payload.villagerId(), new DisplayEntry(payload.entityId(), payload.villagerId(), payload.reputation(), payload.level(), gameTime));
        ENTITY_ID_TO_UUID.put(payload.entityId(), payload.villagerId());
    }

    public static Optional<DisplayEntry> get(UUID villagerId, int entityId) {
        DisplayEntry entry = BY_VILLAGER_UUID.get(villagerId);
        if (entry != null) {
            return Optional.of(entry);
        }
        UUID mappedId = ENTITY_ID_TO_UUID.get(entityId);
        return mappedId == null ? Optional.empty() : Optional.ofNullable(BY_VILLAGER_UUID.get(mappedId));
    }

    public static void clear() {
        BY_VILLAGER_UUID.clear();
        ENTITY_ID_TO_UUID.clear();
    }

    public static void pruneMissing() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }

        Iterator<Map.Entry<Integer, UUID>> iterator = ENTITY_ID_TO_UUID.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, UUID> entry = iterator.next();
            if (minecraft.level.getEntity(entry.getKey()) == null) {
                BY_VILLAGER_UUID.remove(entry.getValue());
                iterator.remove();
            }
        }
    }

    public record DisplayEntry(int entityId, UUID villagerId, int reputation, VillagerReputationLevel level, long lastUpdateGameTime) {
    }
}
