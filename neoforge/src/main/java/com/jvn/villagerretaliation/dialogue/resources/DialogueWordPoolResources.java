package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueWordPool;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

public final class DialogueWordPoolResources {
    private static final String ROOT = "dialogue_words/";
    private static final ServerResourceCache<Map<String, Map<ResourceLocation, DialogueWordPool>>> CACHE =
            ServerResourceCache.create(Map::of, DialogueWordPoolResources::read);
    private static final Map<MinecraftServer, com.jvn.villagerretaliation.dialogue.DialogueWordHistory> HISTORIES = new java.util.WeakHashMap<>();
    private static final Map<net.minecraft.server.packs.resources.ResourceManager, Map<String, Set<ResourceLocation>>> IDS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private DialogueWordPoolResources() { }

    public static void warm(MinecraftServer server) {
        CACHE.get(server);
    }

    public static void clearCache() {
        CACHE.clear();
        REPORTED.clear();
        HISTORIES.clear();
        IDS.clear();
    }

    public static Set<String> locales(MinecraftServer server) {
        return server == null ? Set.of() : CACHE.get(server).keySet();
    }

    public static Set<ResourceLocation> ids(MinecraftServer server, String locale) {
        if (server == null) return Set.of();
        var definitions = IDS.computeIfAbsent(server.getResourceManager(), DialogueWordPoolResources::readIds);
        var ids = new java.util.LinkedHashSet<>(definitions.getOrDefault(VillagerLocale.DEFAULT_LOCALE, Set.of()));
        ids.addAll(definitions.getOrDefault(VillagerLocale.normalize(locale), Set.of()));
        return Set.copyOf(ids);
    }

    private static Map<String, Set<ResourceLocation>> readIds(net.minecraft.server.packs.resources.ResourceManager resources) {
        Map<String, Set<ResourceLocation>> ids = new LinkedHashMap<>();
        for (var location : resources.listResources("dialogue_words", id -> id.getPath().endsWith(".json")).keySet()) {
            String path = location.getPath().substring(ROOT.length());
            int separator = path.indexOf('/');
            if (separator < 1 || path.length() <= separator + 6) continue;
            String locale = VillagerLocale.normalize(path.substring(0, separator));
            ids.computeIfAbsent(locale, ignored -> new java.util.LinkedHashSet<>()).add(ResourceLocation.fromNamespaceAndPath(
                    location.getNamespace(), path.substring(separator + 1, path.length() - 5)));
        }
        ids.replaceAll((locale, values) -> Set.copyOf(values));
        return Map.copyOf(ids);
    }

    public static List<String> values(MinecraftServer server, String locale, ResourceLocation id) {
        return server == null ? List.of() : values(CACHE.get(server), locale, id);
    }

    public static DialogueWordPool localizedPool(MinecraftServer server, String locale, ResourceLocation id) {
        return server == null ? DialogueWordPool.EMPTY : CACHE.get(server).getOrDefault(VillagerLocale.normalize(locale), Map.of()).getOrDefault(id, DialogueWordPool.EMPTY);
    }

    public static List<String> localizedValues(MinecraftServer server, String locale, ResourceLocation id) {
        return server == null ? List.of() : CACHE.get(server).getOrDefault(VillagerLocale.normalize(locale), Map.of()).getOrDefault(id, DialogueWordPool.EMPTY).values();
    }

    public static List<String> values(Map<String, Map<ResourceLocation, DialogueWordPool>> pools, String locale, ResourceLocation id) {
        return pool(pools, locale, id).values();
    }

    private static DialogueWordPool pool(Map<String, Map<ResourceLocation, DialogueWordPool>> pools, String locale, ResourceLocation id) {
        return pools.getOrDefault(VillagerLocale.normalize(locale), Map.of()).getOrDefault(id,
                pools.getOrDefault(VillagerLocale.DEFAULT_LOCALE, Map.of()).getOrDefault(id, DialogueWordPool.EMPTY));
    }

    public static UnaryOperator<String> resolver(MinecraftServer server, String locale, RandomSource random) {
        return resolver(server, locale, random, null);
    }

    public static UnaryOperator<String> resolver(MinecraftServer server, String locale, RandomSource random, DialogueRuntimeContext context) {
        var history = server == null || context == null || context.player() == null ? null
                : HISTORIES.computeIfAbsent(server, ignored -> new com.jvn.villagerretaliation.dialogue.DialogueWordHistory());
        String key = history == null ? "" : context.player().getUUID() + "/" + context.providerId() + "/" + VillagerLocale.normalize(locale);
        return new com.jvn.villagerretaliation.dialogue.DialogueWordSelection(
                id -> server == null ? DialogueWordPool.EMPTY : pool(CACHE.get(server), locale, id),
                conditions -> context != null && com.jvn.villagerretaliation.dialogue.DialogueCondition.matchesAll(context, conditions),
                random, history, key, issue -> {
            if (REPORTED.add(VillagerLocale.normalize(locale) + ":" + issue)) {
                DatapackDiagnostics.warnSkippedEntry(ResourceLocation.fromNamespaceAndPath("villagerretaliation", "dialogue_words"),
                        "dialogue words", locale, issue);
            }
        }, selection -> { });
    }

    public static List<String> readValues(JsonObject root) {
        return DialogueWordPool.read(root).values();
    }

    private static Map<String, Map<ResourceLocation, DialogueWordPool>> read(MinecraftServer server) {
        return read(DatapackResourceLoader.jsonResources(server, "dialogue_words"));
    }

    static Map<String, Map<ResourceLocation, DialogueWordPool>> read(List<DatapackResourceLoader.JsonResource> resources) {
        Map<String, Map<ResourceLocation, JsonObject>> definitions = new LinkedHashMap<>();
        resources.forEach(entry -> {
            var location = entry.location();
            var resource = entry.resource();
            String path = location.getPath().substring(ROOT.length());
            int separator = path.indexOf('/');
            if (separator < 1 || path.length() <= separator + 6) {
                DatapackDiagnostics.warnSkippedEntry(location, "dialogue words", "root", "expected <locale>/<pool>.json");
                return;
            }
            String locale = VillagerLocale.normalize(path.substring(0, separator));
            var id = ResourceLocation.fromNamespaceAndPath(location.getNamespace(), path.substring(separator + 1, path.length() - 5));
            DatapackResourceLoader.readObject(location, "dialogue words", resource).ifPresent(root -> {
                DatapackDiagnostics.warnUnknownRootKeys(location, "dialogue words", root, DialogueWordPool.FIELDS);
                try {
                    DialogueWordPool.read(root);
                    definitions.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).put(id, root);
                } catch (IllegalArgumentException exception) {
                    DatapackDiagnostics.warnSkippedEntry(location, "dialogue words", "values", exception.getMessage());
                }
            });
        });
        Map<String, Map<ResourceLocation, DialogueWordPool>> pools = new LinkedHashMap<>();
        definitions.forEach((locale, values) -> {
            var library = new com.jvn.villagerretaliation.dialogue.DialogueWordPoolLibrary(
                    id -> values.getOrDefault(id, definitions.getOrDefault(VillagerLocale.DEFAULT_LOCALE, Map.of()).get(id)), id -> DialogueWordPool.EMPTY);
            var compiled = new LinkedHashMap<ResourceLocation, DialogueWordPool>();
            values.forEach((id, json) -> {
                try { compiled.put(id, library.apply(id)); }
                catch (IllegalArgumentException exception) { DatapackDiagnostics.warnSkippedEntry(id, "dialogue words", locale, exception.getMessage()); }
            });
            pools.put(locale, Map.copyOf(compiled));
        });
        return Map.copyOf(pools);
    }
}
