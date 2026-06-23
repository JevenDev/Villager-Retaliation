package com.jvn.villagerretaliation.interaction.work.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

public final class BuilderStructureCatalog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BUILDER_STRUCTURE_ROOT = "builder_structures";
    private static final Set<String> ROOT_KEYS = Set.of("replace", "entries");
    private static final Set<String> ENTRY_KEYS = Set.of(
            "id",
            "structure",
            "structure_id",
            "structureId",
            "structures",
            "category",
            "label",
            "base_cost",
            "baseCost",
            "cost",
            "enabled",
            "remove"
    );
    private static final List<Entry> DEFAULT_ENTRIES = createFallbackEntries();

    private static volatile CachedEntries cachedEntries = CachedEntries.empty();
    private static volatile CatalogData clientEntries = CatalogData.of(DEFAULT_ENTRIES);

    private BuilderStructureCatalog() {
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cachedEntries = CachedEntries.empty();
    }

    public static List<Entry> entries() {
        return clientEntries.entries();
    }

    public static List<Entry> entries(MinecraftServer server) {
        return load(server).entries();
    }

    public static Optional<Entry> byId(ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(clientEntries.byId().get(id));
    }

    public static Optional<Entry> byId(MinecraftServer server, ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(load(server).byId().get(id));
    }

    public static void replaceClientEntries(List<Entry> entries) {
        clientEntries = CatalogData.of(entries == null ? DEFAULT_ENTRIES : entries);
    }

    public static void resetClientEntries() {
        clientEntries = CatalogData.of(DEFAULT_ENTRIES);
    }

    private static CatalogData load(MinecraftServer server) {
        if (server == null) {
            return CatalogData.of(DEFAULT_ENTRIES);
        }

        CachedEntries current = cachedEntries;
        if (current.server() == server) {
            return current.catalog();
        }

        synchronized (BuilderStructureCatalog.class) {
            current = cachedEntries;
            if (current.server() == server) {
                return current.catalog();
            }

            CatalogData loaded = read(server);
            cachedEntries = new CachedEntries(server, loaded);
            return loaded;
        }
    }

    private static CatalogData read(MinecraftServer server) {
        Map<ResourceLocation, LoadedEntry> loaded = new LinkedHashMap<>();
        boolean[] sawFile = new boolean[]{false};
        DatapackResourceLoader.forEachJsonResource(
                server,
                BUILDER_STRUCTURE_ROOT,
                (location, resource) -> {
                    sawFile[0] = true;
                    readFile(location, resource, loaded);
                });

        List<Entry> entries = loaded.values()
                .stream()
                .map(LoadedEntry::entry)
                .toList();
        if (entries.isEmpty() && !sawFile[0]) {
            LOGGER.warn("No Villager Retaliation builder structure datapack files were found; using fallback vanilla village houses.");
            entries = DEFAULT_ENTRIES;
        }
        entries = sorted(entries);
        LOGGER.info("Loaded {} Villager Retaliation builder structure definitions.", entries.size());
        return CatalogData.of(entries);
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            Map<ResourceLocation, LoadedEntry> loaded) {
        DatapackResourceLoader.readObject(location, "builder structure", resource).ifPresent(root -> {
            DatapackDiagnostics.warnUnknownRootKeys(location, "builder structures", root, ROOT_KEYS);
            if (DatapackJsonReader.readBoolean(root, "replace", false)) {
                loaded.clear();
                LOGGER.info("Villager Retaliation builder structure file {} requested global replace.", location);
            }
            readEntries(location, root, loaded);
        });
    }

    private static void readEntries(
            ResourceLocation location,
            JsonObject root,
            Map<ResourceLocation, LoadedEntry> loaded) {
        JsonArray entries = root.getAsJsonArray("entries");
        if (entries == null) {
            if (looksLikeEntry(root)) {
                readEntry(location, root, 0, loaded);
            }
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                DatapackDiagnostics.warnSkippedEntry(location, "builder structures", "entry " + index, "entry is not an object");
                index++;
                continue;
            }
            readEntry(location, element.getAsJsonObject(), index, loaded);
            index++;
        }
    }

    private static void readEntry(
            ResourceLocation location,
            JsonObject entry,
            int index,
            Map<ResourceLocation, LoadedEntry> loaded) {
        DatapackDiagnostics.warnUnknownKeys(location, "builder structures", "entry " + index, entry, ENTRY_KEYS);
        List<ResourceLocation> structures = readStructureIds(location, entry, index);
        if (structures.isEmpty()) {
            DatapackDiagnostics.warnSkippedEntry(location, "builder structures", "entry " + index, "no valid structure id");
            return;
        }

        boolean remove = DatapackJsonReader.readBoolean(entry, "remove", false)
                || !DatapackJsonReader.readBoolean(entry, "enabled", true);
        if (remove) {
            structures.forEach(loaded::remove);
            return;
        }

        String category = fallback(DatapackJsonReader.readString(entry, "category"), "Structures");
        int baseCost = Math.max(0, DatapackJsonReader.readInt(entry, "base_cost", "baseCost", readCostAlias(entry)));
        for (ResourceLocation structure : structures) {
            String label = structures.size() == 1
                    ? fallback(DatapackJsonReader.readString(entry, "label"), labelFromId(structure, category))
                    : labelFromId(structure, category);
            LoadedEntry previous = loaded.put(structure, new LoadedEntry(
                    new Entry(structure, category, label, baseCost),
                    location));
            if (previous != null) {
                DatapackDiagnostics.warnDuplicateId(location, "builder structure", structure.toString(), previous.source());
            }
        }
    }

    private static List<ResourceLocation> readStructureIds(ResourceLocation location, JsonObject entry, int index) {
        List<ResourceLocation> structures = new ArrayList<>();
        for (String value : DatapackJsonReader.readStringList(entry, "id", "structure", "structure_id", "structureId", "structures")) {
            Optional<ResourceLocation> parsed = parseStructureLocation(value);
            if (parsed.isEmpty()) {
                DatapackDiagnostics.warnInvalidResourceLocation(
                        location,
                        "builder structure",
                        "entry " + index,
                        value,
                        "Use a full id such as examplemod:village/houses/carpenter_house.");
                continue;
            }
            if (!structures.contains(parsed.get())) {
                structures.add(parsed.get());
            }
        }
        return List.copyOf(structures);
    }

    private static Optional<ResourceLocation> parseStructureLocation(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim();
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    private static int readCostAlias(JsonObject entry) {
        return DatapackJsonReader.readInt(entry, "cost", 0);
    }

    private static boolean looksLikeEntry(JsonObject root) {
        return root.has("id")
                || root.has("structure")
                || root.has("structure_id")
                || root.has("structureId")
                || root.has("structures");
    }

    private static CatalogData defaultCatalogData() {
        return CatalogData.of(DEFAULT_ENTRIES);
    }

    private static List<Entry> createFallbackEntries() {
        List<Entry> entries = new ArrayList<>();
        addFallback(entries, "Plains", 10,
                "houses/plains_small_house_1",
                "houses/plains_small_house_2",
                "houses/plains_small_house_3",
                "houses/plains_small_house_4",
                "houses/plains_small_house_5",
                "houses/plains_small_house_6",
                "houses/plains_small_house_7",
                "houses/plains_medium_house_1",
                "houses/plains_big_house_1",
                "houses/plains_armorer_house_1",
                "houses/plains_butcher_shop_1",
                "houses/plains_cartographer_1",
                "houses/plains_fisher_cottage_1",
                "houses/plains_fletcher_house_1",
                "houses/plains_library_1",
                "houses/plains_masons_house_1",
                "houses/plains_shepherds_house_1",
                "houses/plains_stable_1",
                "houses/plains_tannery_1",
                "houses/plains_tool_smith_1",
                "houses/plains_weaponsmith_1");
        addFallback(entries, "Desert", 11,
                "houses/desert_small_house_1",
                "houses/desert_small_house_2",
                "houses/desert_small_house_3",
                "houses/desert_small_house_4",
                "houses/desert_small_house_5",
                "houses/desert_small_house_6",
                "houses/desert_small_house_7",
                "houses/desert_small_house_8",
                "houses/desert_medium_house_1",
                "houses/desert_medium_house_2",
                "houses/desert_armorer_1",
                "houses/desert_butcher_shop_1",
                "houses/desert_cartographer_1",
                "houses/desert_farm_1",
                "houses/desert_farm_2",
                "houses/desert_fisher_1",
                "houses/desert_fletcher_house_1",
                "houses/desert_library_1",
                "houses/desert_mason_1",
                "houses/desert_shepherd_house_1",
                "houses/desert_tannery_1",
                "houses/desert_temple_1",
                "houses/desert_tool_smith_1",
                "houses/desert_weaponsmith_1");
        addFallback(entries, "Savanna", 11,
                "houses/savanna_small_house_1",
                "houses/savanna_small_house_2",
                "houses/savanna_small_house_3",
                "houses/savanna_small_house_4",
                "houses/savanna_small_house_5",
                "houses/savanna_small_house_6",
                "houses/savanna_small_house_7",
                "houses/savanna_small_house_8",
                "houses/savanna_medium_house_1",
                "houses/savanna_medium_house_2",
                "houses/savanna_big_house_1",
                "houses/savanna_armorer_1",
                "houses/savanna_butchers_shop_1",
                "houses/savanna_cartographer_1",
                "houses/savanna_fisher_cottage_1",
                "houses/savanna_fletcher_house_1",
                "houses/savanna_library_1",
                "houses/savanna_mason_1",
                "houses/savanna_shepherd_1",
                "houses/savanna_tannery_1",
                "houses/savanna_temple_1",
                "houses/savanna_tool_smith_1",
                "houses/savanna_weaponsmith_1");
        addFallback(entries, "Snowy", 12,
                "houses/snowy_small_house_1",
                "houses/snowy_small_house_2",
                "houses/snowy_small_house_3",
                "houses/snowy_small_house_4",
                "houses/snowy_small_house_5",
                "houses/snowy_small_house_6",
                "houses/snowy_small_house_7",
                "houses/snowy_small_house_8",
                "houses/snowy_medium_house_1",
                "houses/snowy_medium_house_2",
                "houses/snowy_medium_house_3",
                "houses/snowy_big_house_1",
                "houses/snowy_armorer_house_1",
                "houses/snowy_butchers_shop_1",
                "houses/snowy_cartographer_house_1",
                "houses/snowy_farm_1",
                "houses/snowy_farm_2",
                "houses/snowy_fisher_cottage_1",
                "houses/snowy_fletcher_house_1",
                "houses/snowy_library_1",
                "houses/snowy_masons_house_1",
                "houses/snowy_shepherds_house_1",
                "houses/snowy_tannery_1",
                "houses/snowy_temple_1",
                "houses/snowy_tool_smith_1",
                "houses/snowy_weaponsmith_1");
        addFallback(entries, "Taiga", 12,
                "houses/taiga_small_house_1",
                "houses/taiga_small_house_2",
                "houses/taiga_small_house_3",
                "houses/taiga_small_house_4",
                "houses/taiga_small_house_5",
                "houses/taiga_medium_house_1",
                "houses/taiga_medium_house_2",
                "houses/taiga_medium_house_3",
                "houses/taiga_medium_house_4",
                "houses/taiga_large_farm_1",
                "houses/taiga_armorer_house_1",
                "houses/taiga_butcher_shop_1",
                "houses/taiga_cartographer_house_1",
                "houses/taiga_fisher_cottage_1",
                "houses/taiga_fletcher_house_1",
                "houses/taiga_library_1",
                "houses/taiga_masons_house_1",
                "houses/taiga_shepherds_house_1",
                "houses/taiga_tannery_1",
                "houses/taiga_temple_1",
                "houses/taiga_tool_smith_1",
                "houses/taiga_weaponsmith_1");
        return sorted(entries);
    }

    private static void addFallback(List<Entry> entries, String category, int baseCost, String... paths) {
        String prefix = "village/" + category.toLowerCase(Locale.ROOT) + "/";
        for (String path : paths) {
            ResourceLocation id = ResourceLocation.withDefaultNamespace(prefix + path);
            entries.add(new Entry(id, category, labelFromPath(path, category), baseCost));
        }
    }

    private static List<Entry> sorted(List<Entry> entries) {
        return List.copyOf(entries.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Entry::category)
                        .thenComparing(Entry::label)
                        .thenComparing(entry -> entry.id().toString()))
                .toList());
    }

    private static Map<ResourceLocation, Entry> byIdMap(List<Entry> entries) {
        return entries.stream()
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.id(), entry), LinkedHashMap::putAll);
    }

    private static String labelFromId(ResourceLocation id, String category) {
        return labelFromPath(id.getPath(), category);
    }

    private static String labelFromPath(String path, String category) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String categoryPrefix = category.toLowerCase(Locale.ROOT);
        String[] words = name.split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank() || word.equals(categoryPrefix)) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            if (word.chars().allMatch(Character::isDigit)) {
                label.append(word);
            } else {
                label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return label.isEmpty() ? name : label.toString();
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Entry(ResourceLocation id, String category, String label, int baseCost) {
        public Entry {
            id = id == null ? ResourceLocation.withDefaultNamespace("missing") : id;
            category = category == null || category.isBlank() ? "Structures" : category.trim();
            label = label == null || label.isBlank() ? id.toString() : label.trim();
            baseCost = Math.max(0, baseCost);
        }

        public String menuLabel() {
            return this.category + ": " + this.label;
        }
    }

    private record CatalogData(List<Entry> entries, Map<ResourceLocation, Entry> byId) {
        private static CatalogData of(List<Entry> entries) {
            List<Entry> safeEntries = sorted(entries == null ? List.of() : entries);
            return new CatalogData(safeEntries, byIdMap(safeEntries));
        }
    }

    private record LoadedEntry(Entry entry, ResourceLocation source) {
    }

    private record CachedEntries(MinecraftServer server, CatalogData catalog) {
        private static CachedEntries empty() {
            return new CachedEntries(null, defaultCatalogData());
        }
    }
}
