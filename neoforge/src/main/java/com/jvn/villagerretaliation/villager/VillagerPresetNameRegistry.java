package com.jvn.villagerretaliation.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class VillagerPresetNameRegistry {
    public static final String PERSISTENT_NAME_KEY = "VillagerRetaliationName";
    public static final String PERSISTENT_GENDER_KEY = "VillagerRetaliationGender";

    private static final String LEGACY_NAME_KEY_PREFIX = "villagerretaliation.villager_name.";
    private static final String VILLAGER_NAMES_ROOT = "villager_names";

    private static volatile CachedNamePool cachedNamePool = CachedNamePool.empty();

    private VillagerPresetNameRegistry() {
    }

    public static void warm(MinecraftServer server) {
        loadNamePool(server);
    }

    public static void clearCache() {
        cachedNamePool = CachedNamePool.empty();
    }

    public static void ensurePresetNameAssigned(AbstractVillager villager) {
        if (villager.hasCustomName() || !(villager.level() instanceof ServerLevel level)) {
            if (villager.hasCustomName() && villager.level() instanceof ServerLevel serverLevel) {
                resolveStoredGender(villager, serverLevel.getServer());
            }
            return;
        }
        resolveStoredName(villager, level.getServer());
        resolveStoredGender(villager, level.getServer());
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

    public static VillagerGender resolveGender(AbstractVillager villager) {
        if (villager.level() instanceof ServerLevel level) {
            return resolveStoredGender(villager, level.getServer());
        }
        return resolveClientStoredGender(villager);
    }

    private static String resolveStoredName(AbstractVillager villager, MinecraftServer server) {
        String storedName = villager.getPersistentData().getString(PERSISTENT_NAME_KEY).trim();
        if (!storedName.isBlank()) {
            if (!isLegacyNameKey(storedName)) {
                resolveStoredGender(villager, server);
                return storedName;
            }

            String migratedName = migrateLegacyNameKey(storedName, loadNamePool(server).allNames());
            if (!migratedName.isBlank()) {
                villager.getPersistentData().putString(PERSISTENT_NAME_KEY, migratedName);
            }
            resolveStoredGender(villager, server);
            return migratedName;
        }

        VillagerGender gender = resolveStoredGender(villager, server);
        NamePool namePool = loadNamePool(server);
        List<String> names = namePool.namesFor(gender);
        if (names.isEmpty()) {
            names = namePool.allNames();
            if (names.isEmpty()) {
                return "";
            }
        }

        String selectedName = names.get(Math.floorMod(villager.getUUID().hashCode(), names.size()));
        villager.getPersistentData().putString(PERSISTENT_NAME_KEY, selectedName);
        return selectedName;
    }

    private static String resolveClientStoredName(AbstractVillager villager) {
        String storedName = villager.getPersistentData().getString(PERSISTENT_NAME_KEY).trim();
        return isLegacyNameKey(storedName) ? "" : storedName;
    }

    private static VillagerGender resolveStoredGender(AbstractVillager villager, MinecraftServer server) {
        String storedGender = villager.getPersistentData().getString(PERSISTENT_GENDER_KEY).trim();
        VillagerGender gender = VillagerGender.bySerializedName(storedGender);
        if (gender != null) {
            return gender;
        }

        String storedName = villager.hasCustomName() && villager.getCustomName() != null
                ? villager.getCustomName().getString().trim()
                : villager.getPersistentData().getString(PERSISTENT_NAME_KEY).trim();
        gender = loadNamePool(server)
                .genderForName(storedName)
                .orElseGet(() -> deterministicGender(villager));
        villager.getPersistentData().putString(PERSISTENT_GENDER_KEY, gender.serializedName());
        return gender;
    }

    private static VillagerGender resolveClientStoredGender(AbstractVillager villager) {
        VillagerGender storedGender = VillagerGender.bySerializedName(villager.getPersistentData().getString(PERSISTENT_GENDER_KEY));
        return storedGender == null ? deterministicGender(villager) : storedGender;
    }

    private static VillagerGender deterministicGender(AbstractVillager villager) {
        return Math.floorMod(villager.getUUID().hashCode(), 2) == 0 ? VillagerGender.MALE : VillagerGender.FEMALE;
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

    private static NamePool loadNamePool(MinecraftServer server) {
        CachedNamePool current = cachedNamePool;
        if (current.server() == server && !current.namePool().isEmpty()) {
            return current.namePool();
        }

        synchronized (VillagerPresetNameRegistry.class) {
            current = cachedNamePool;
            if (current.server() == server && !current.namePool().isEmpty()) {
                return current.namePool();
            }

            NamePool loadedNamePool = readNamePool(server);
            cachedNamePool = new CachedNamePool(server, loadedNamePool);
            return loadedNamePool;
        }
    }

    private static NamePool readNamePool(MinecraftServer server) {
        List<String> maleNames = new ArrayList<>();
        List<String> femaleNames = new ArrayList<>();
        List<String> fallbackNames = new ArrayList<>();
        server.getResourceManager()
                .listResources(VILLAGER_NAMES_ROOT, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID)
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readNameFile(entry.getValue(), maleNames, femaleNames, fallbackNames));
        return new NamePool(List.copyOf(maleNames), List.copyOf(femaleNames), List.copyOf(fallbackNames));
    }

    private static void readNameFile(
            Resource resource,
            List<String> maleNames,
            List<String> femaleNames,
            List<String> fallbackNames) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (readBoolean(root, "replace", false)) {
                maleNames.clear();
                femaleNames.clear();
                fallbackNames.clear();
            }
            maleNames.addAll(readNames(root.getAsJsonArray("male_names")));
            femaleNames.addAll(readNames(root.getAsJsonArray("female_names")));
            fallbackNames.addAll(readNames(root.getAsJsonArray("names")));
        } catch (IOException | IllegalStateException exception) {
            // Invalid name resources are ignored so one custom list cannot break every preset name.
        }
    }

    private static List<String> readNames(JsonArray names) {
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
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsBoolean();
    }

    private record NamePool(List<String> maleNames, List<String> femaleNames, List<String> fallbackNames) {
        private static NamePool empty() {
            return new NamePool(List.of(), List.of(), List.of());
        }

        private boolean isEmpty() {
            return this.maleNames.isEmpty() && this.femaleNames.isEmpty() && this.fallbackNames.isEmpty();
        }

        private List<String> namesFor(VillagerGender gender) {
            return switch (gender) {
                case MALE -> this.maleNames;
                case FEMALE -> this.femaleNames;
            };
        }

        private List<String> allNames() {
            if (this.maleNames.isEmpty() && this.femaleNames.isEmpty()) {
                return this.fallbackNames;
            }
            List<String> names = new ArrayList<>(this.maleNames.size() + this.femaleNames.size() + this.fallbackNames.size());
            names.addAll(this.maleNames);
            names.addAll(this.femaleNames);
            names.addAll(this.fallbackNames);
            return List.copyOf(names);
        }

        private Optional<VillagerGender> genderForName(String name) {
            if (name == null || name.isBlank()) {
                return Optional.empty();
            }
            boolean male = containsIgnoreCase(this.maleNames, name);
            boolean female = containsIgnoreCase(this.femaleNames, name);
            if (male == female) {
                return Optional.empty();
            }
            return Optional.of(male ? VillagerGender.MALE : VillagerGender.FEMALE);
        }

        private static boolean containsIgnoreCase(List<String> names, String name) {
            for (String candidate : names) {
                if (candidate.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record CachedNamePool(MinecraftServer server, NamePool namePool) {
        private static CachedNamePool empty() {
            return new CachedNamePool(null, NamePool.empty());
        }
    }
}
