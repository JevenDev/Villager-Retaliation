package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.network.VillagerNameSyncPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public final class VillagerNameClientCache {
    private static final long PRUNE_INTERVAL_TICKS = 40L;
    private static final Map<Integer, DisplayEntry> BY_ENTITY_ID = new HashMap<>();
    private static long nextPruneGameTime;

    private VillagerNameClientCache() {
    }

    public static void accept(VillagerNameSyncPayload payload) {
        if (payload.nameKey().isBlank() && payload.fallbackName().isBlank()) {
            BY_ENTITY_ID.remove(payload.entityId());
            return;
        }
        BY_ENTITY_ID.put(payload.entityId(), new DisplayEntry(
                payload.villagerId(), payload.nameKey(), payload.fallbackName(), payload.hired()));
    }

    public static Optional<Component> displayName(int entityId) {
        DisplayEntry entry = BY_ENTITY_ID.get(entityId);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.nameKey().isBlank() && I18n.exists(entry.nameKey())) {
            return Optional.of(Component.translatable(entry.nameKey()));
        }
        return entry.fallbackName().isBlank()
                ? Optional.empty()
                : Optional.of(Component.literal(entry.fallbackName()));
    }

    public static boolean isHired(int entityId) {
        DisplayEntry entry = BY_ENTITY_ID.get(entityId);
        return entry != null && entry.hired();
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

        Iterator<Integer> iterator = BY_ENTITY_ID.keySet().iterator();
        while (iterator.hasNext()) {
            if (minecraft.level.getEntity(iterator.next()) == null) {
                iterator.remove();
            }
        }
    }

    public static void clear() {
        BY_ENTITY_ID.clear();
        nextPruneGameTime = 0L;
    }

    private record DisplayEntry(UUID villagerId, String nameKey, String fallbackName, boolean hired) {
    }
}
