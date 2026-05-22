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

    private static final String LEGACY_NAME_KEY_PREFIX = "villagerretaliation.villager_name.";
    private static final ResourceLocation PRESET_NAMES_RESOURCE =
            VillagerRetaliation.id("villager_names/preset_names.json");

    private static volatile CachedNamePool cachedNamePool = CachedNamePool.empty();

    private VillagerPresetNameRegistry() {
    }

    public static void warm(MinecraftServer server) {
        loadNames(server);
    }

    public static void clearCache() {
        cachedNamePool = CachedNamePool.empty();
    }

    public static void ensurePresetNameAssigned(AbstractVillager villager) {
        if (villager.hasCustomName() || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        resolveStoredName(villager, level.getServer());
    }

    public static Component resolveDisplayName(AbstractVillager villager) {
        if (villager.hasCustomName() && villager.getCustomName() != null) {
            return villager.getCustomName();
        }

        String storedName = villager.level() instanceof ServerLevel level
                ? resolveStoredName(villager, level.getServer())
                : resolveClientStoredName(villager);
        if (!storedName.isBlank()) {
            return Component.literal(storedName);
        }
        return villager.getName();
    }

    public static String resolvePresetName(AbstractVillager villager) {
        if (villager.hasCustomName()) {
            return "";
        }
        if (villager.level() instanceof ServerLevel level) {
            return resolveStoredName(villager, level.getServer());
        }
        return resolveClientStoredName(villager);
    }

    private static String resolveStoredName(AbstractVillager villager, MinecraftServer server) {
        String storedName = villager.getPersistentData().getString(PERSISTENT_NAME_KEY).trim();
        if (!storedName.isBlank()) {
            if (!isLegacyNameKey(storedName)) {
                return storedName;
            }

            String migratedName = migrateLegacyNameKey(storedName, loadNames(server));
            if (!migratedName.isBlank()) {
                villager.getPersistentData().putString(PERSISTENT_NAME_KEY, migratedName);
            }
            return migratedName;
        }

        List<String> names = loadNames(server);
        if (names.isEmpty()) {
            return "";
        }

        String selectedName = names.get(Math.floorMod(villager.getUUID().hashCode(), names.size()));
        villager.getPersistentData().putString(PERSISTENT_NAME_KEY, selectedName);
        return selectedName;
    }

    private static String resolveClientStoredName(AbstractVillager villager) {
        String storedName = villager.getPersistentData().getString(PERSISTENT_NAME_KEY).trim();
        return isLegacyNameKey(storedName) ? "" : storedName;
    }

    private static boolean isLegacyNameKey(String value) {
        return value.startsWith(LEGACY_NAME_KEY_PREFIX);
    }

    private static String migrateLegacyNameKey(String legacyNameKey, List<String> names) {
        if (names.isEmpty()) {
            return "";
        }

        String legacyIndex = legacyNameKey.substring(LEGACY_NAME_KEY_PREFIX.length());
        try {
            int index = Integer.parseInt(legacyIndex) - 1;
            return index >= 0 && index < names.size() ? names.get(index) : "";
        } catch (NumberFormatException exception) {
            return "";
        }
    }

    private static List<String> loadNames(MinecraftServer server) {
        CachedNamePool current = cachedNamePool;
        if (current.server() == server && !current.names().isEmpty()) {
            return current.names();
        }

        synchronized (VillagerPresetNameRegistry.class) {
            current = cachedNamePool;
            if (current.server() == server && !current.names().isEmpty()) {
                return current.names();
            }

            List<String> loadedNames = readNames(server);
            cachedNamePool = new CachedNamePool(server, loadedNames);
            return loadedNames;
        }
    }

    private static List<String> readNames(MinecraftServer server) {
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

            List<String> loadedNames = new ArrayList<>(names.size());
            for (JsonElement element : names) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                String value = element.getAsString().trim();
                if (!value.isBlank()) {
                    loadedNames.add(value);
                }
            }
            return List.copyOf(loadedNames);
        } catch (IOException | IllegalStateException exception) {
            return List.of();
        }
    }

    private record CachedNamePool(MinecraftServer server, List<String> names) {
        private static CachedNamePool empty() {
            return new CachedNamePool(null, List.of());
        }
    }
}
