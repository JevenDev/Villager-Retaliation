package com.jvn.villagerretaliation.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class VillagerPresetNameRegistry {
    public static final String PERSISTENT_NAME_KEY = "VillagerRetaliationName";

    private static final ResourceLocation PRESET_NAMES_RESOURCE =
            VillagerRetaliation.id("villager_names/preset_names.json");

    private static volatile CachedNamePool cachedNamePool = CachedNamePool.empty();

    private VillagerPresetNameRegistry() {
    }

    public static void ensurePresetNameAssigned(AbstractVillager villager) {
        if (villager.hasCustomName() || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        resolveStoredNameKey(villager, level.getServer());
    }

    public static Component resolveDisplayName(AbstractVillager villager) {
        if (villager.hasCustomName() && villager.getCustomName() != null) {
            return villager.getCustomName();
        }

        String storedNameKey = villager.level() instanceof ServerLevel level
                ? resolveStoredNameKey(villager, level.getServer())
                : villager.getPersistentData().getString(PERSISTENT_NAME_KEY);
        if (!storedNameKey.isBlank()) {
            return Component.translatable(storedNameKey);
        }
        return villager.getName();
    }

    public static String resolveNameTranslationKey(AbstractVillager villager) {
        if (villager.hasCustomName()) {
            return "";
        }
        if (villager.level() instanceof ServerLevel level) {
            return resolveStoredNameKey(villager, level.getServer());
        }
        return villager.getPersistentData().getString(PERSISTENT_NAME_KEY);
    }

    private static String resolveStoredNameKey(AbstractVillager villager, MinecraftServer server) {
        String storedNameKey = villager.getPersistentData().getString(PERSISTENT_NAME_KEY);
        if (!storedNameKey.isBlank()) {
            return storedNameKey;
        }

        List<String> nameKeys = loadNameKeys(server);
        if (nameKeys.isEmpty()) {
            return "";
        }

        String selectedNameKey = nameKeys.get(Math.floorMod(villager.getUUID().hashCode(), nameKeys.size()));
        villager.getPersistentData().putString(PERSISTENT_NAME_KEY, selectedNameKey);
        return selectedNameKey;
    }

    private static List<String> loadNameKeys(MinecraftServer server) {
        CachedNamePool current = cachedNamePool;
        if (current.server() == server && !current.keys().isEmpty()) {
            return current.keys();
        }

        synchronized (VillagerPresetNameRegistry.class) {
            current = cachedNamePool;
            if (current.server() == server && !current.keys().isEmpty()) {
                return current.keys();
            }

            List<String> loadedKeys = readNameKeys(server);
            cachedNamePool = new CachedNamePool(server, loadedKeys);
            return loadedKeys;
        }
    }

    private static List<String> readNameKeys(MinecraftServer server) {
        Optional<Resource> resource = server.getResourceManager().getResource(PRESET_NAMES_RESOURCE);
        if (resource.isEmpty()) {
            return List.of();
        }

        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray names = root.getAsJsonArray("names");
            if (names == null || names.isEmpty()) {
                return List.of();
            }

            List<String> keys = new ArrayList<>(names.size());
            for (JsonElement element : names) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                String value = element.getAsString().trim();
                if (!value.isBlank()) {
                    keys.add(value);
                }
            }
            return List.copyOf(keys);
        } catch (IOException | IllegalStateException exception) {
            return List.of();
        }
    }

    private record CachedNamePool(MinecraftServer server, List<String> keys) {
        private static CachedNamePool empty() {
            return new CachedNamePool(null, List.of());
        }
    }
}
