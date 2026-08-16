package com.jvn.villagerretaliation.dialogue.forced.container;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

public final class GeneratedContainerLootResources {
    private static final String RESOURCE_ROOT = "generated_containers";
    private static final Set<String> ROOT_KEYS = Set.of(
            "metadata",
            "id",
            "entries",
            "loot_table",
            "loot_tables",
            "LootTable",
            "LootTables");
    private static final ServerResourceCache<Set<ResourceLocation>> CACHED_CONTAINERS =
            ServerResourceCache.create(Set::of, GeneratedContainerLootResources::read);

    private GeneratedContainerLootResources() {
    }

    public static void warm(MinecraftServer server) {
        lootTables(server);
    }

    public static void clearCache() {
        CACHED_CONTAINERS.clear();
    }

    public static boolean isVillagePropertyLootTable(MinecraftServer server, ResourceLocation lootTable) {
        return lootTable != null && lootTables(server).contains(lootTable);
    }

    private static Set<ResourceLocation> lootTables(MinecraftServer server) {
        return CACHED_CONTAINERS.get(server);
    }

    private static Set<ResourceLocation> read(MinecraftServer server) {
        Set<ResourceLocation> lootTables = new LinkedHashSet<>();
        DatapackResourceLoader.forEachJsonResource(
                server,
                RESOURCE_ROOT,
                (location, resource) -> readFile(location, resource, lootTables));
        return Set.copyOf(lootTables);
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            Set<ResourceLocation> lootTables) {
        DatapackResourceLoader.readObject(location, "generated container", resource).ifPresent(root -> {
            DatapackDiagnostics.warnUnknownRootKeys(location, "generated container", root, ROOT_KEYS);
            JsonArray entries = root.getAsJsonArray("entries");
            if (entries != null) {
                for (JsonElement element : entries) {
                    if (element.isJsonObject()) {
                        readLootTables(element.getAsJsonObject(), lootTables);
                    }
                }
                return;
            }

            readLootTables(root, lootTables);
        });
    }

    private static void readLootTables(JsonObject entry, Set<ResourceLocation> lootTables) {
        for (String value : DatapackJsonReader.readStringList(entry, "loot_table", "loot_tables", "LootTable", "LootTables")) {
            parseLootTable(value).ifPresent(lootTables::add);
        }
    }

    private static Optional<ResourceLocation> parseLootTable(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (!normalized.contains(":")) {
            normalized = ResourceLocation.DEFAULT_NAMESPACE + ":" + normalized;
        }
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }
}
