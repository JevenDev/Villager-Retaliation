package com.jvn.villagerretaliation.allegiance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

public final class VillageNameResources {
    private static final String VILLAGE_NAMES_ROOT = "village_names";

    private static volatile CachedNamePool cachedNamePool = CachedNamePool.empty();

    private VillageNameResources() {
    }

    public static void warm(MinecraftServer server) {
        loadNamePool(server);
    }

    public static void clearCache() {
        synchronized (VillageNameResources.class) {
            cachedNamePool = new CachedNamePool(cachedNamePool.server(), NamePool.empty(), false);
        }
    }

    public static void reset() {
        cachedNamePool = CachedNamePool.empty();
    }

    static NamePool currentNamePool() {
        CachedNamePool current = cachedNamePool;
        if (current.loaded()) {
            return current.namePool();
        }
        return current.server() == null ? NamePool.empty() : loadNamePool(current.server());
    }

    private static NamePool loadNamePool(MinecraftServer server) {
        CachedNamePool current = cachedNamePool;
        if (current.server() == server && current.loaded()) {
            return current.namePool();
        }

        synchronized (VillageNameResources.class) {
            current = cachedNamePool;
            if (current.server() == server && current.loaded()) {
                return current.namePool();
            }

            NamePool loadedNamePool = readNamePool(server);
            cachedNamePool = new CachedNamePool(server, loadedNamePool, true);
            return loadedNamePool;
        }
    }

    private static NamePool readNamePool(MinecraftServer server) {
        List<String> prefixes = new ArrayList<>();
        List<String> suffixes = new ArrayList<>();
        List<LoadedNameResource> resources = DatapackResourceLoader
                .jsonResources(
                        server,
                        VILLAGE_NAMES_ROOT,
                        location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID))
                .stream()
                .map(resource -> DatapackResourceLoader
                        .readObject(resource.location(), "village names", resource.resource())
                        .map(root -> new LoadedNameResource(resource, root)))
                .flatMap(Optional::stream)
                .toList();
        boolean replacementMode = resources.stream()
                .anyMatch(resource -> readBoolean(resource.root(), "replace", false));
        for (LoadedNameResource resource : resources) {
            if (replacementMode
                    && isBuiltInModResource(resource.resource())
                    && !readBoolean(resource.root(), "replace", false)) {
                continue;
            }
            readNameFile(resource.root(), prefixes, suffixes);
        }
        return new NamePool(List.copyOf(prefixes), List.copyOf(suffixes));
    }

    private static void readNameFile(
            JsonObject root,
            List<String> prefixes,
            List<String> suffixes) {
        if (readBoolean(root, "replace", false)) {
            prefixes.clear();
            suffixes.clear();
        }
        prefixes.addAll(readNames(root.get("prefixes")));
        suffixes.addAll(readNames(root.get("suffixes")));
    }

    private static List<String> readNames(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }

        JsonArray names = element.getAsJsonArray();
        List<String> loadedNames = new ArrayList<>(names.size());
        for (JsonElement nameElement : names) {
            if (!nameElement.isJsonPrimitive()) {
                continue;
            }
            String value = nameElement.getAsString().trim();
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

    private static boolean isBuiltInModResource(DatapackResourceLoader.JsonResource resource) {
        return VillagerRetaliation.MOD_ID.equals(resource.location().getNamespace())
                && resource.isFromPack(VillagerRetaliation.MOD_ID);
    }

    record NamePool(List<String> prefixes, List<String> suffixes) {
        private static NamePool empty() {
            return new NamePool(List.of(), List.of());
        }

        boolean isEmpty() {
            return this.prefixes.isEmpty() || this.suffixes.isEmpty();
        }
    }

    private record CachedNamePool(MinecraftServer server, NamePool namePool, boolean loaded) {
        private static CachedNamePool empty() {
            return new CachedNamePool(null, NamePool.empty(), false);
        }
    }

    private record LoadedNameResource(DatapackResourceLoader.JsonResource resource, JsonObject root) {
    }
}
