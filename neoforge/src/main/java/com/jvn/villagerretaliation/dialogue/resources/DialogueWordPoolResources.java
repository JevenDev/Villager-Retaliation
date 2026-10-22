package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueWordPools;
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
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private DialogueWordPoolResources() { }

    public static void warm(MinecraftServer server) {
        CACHE.get(server);
    }

    public static void clearCache() {
        CACHE.clear();
        REPORTED.clear();
    }

    public static Set<String> locales(MinecraftServer server) {
        return server == null ? Set.of() : CACHE.get(server).keySet();
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
        return DialogueWordPools.selectingResolver(id -> server == null ? null
                : pool(CACHE.get(server), locale, id).select(context, random), issue -> {
            if (REPORTED.add(VillagerLocale.normalize(locale) + ":" + issue)) {
                DatapackDiagnostics.warnSkippedEntry(ResourceLocation.fromNamespaceAndPath("villagerretaliation", "dialogue_words"),
                        "dialogue words", locale, issue);
            }
        });
    }

    public static List<String> readValues(JsonObject root) {
        return DialogueWordPool.read(root).values();
    }

    private static Map<String, Map<ResourceLocation, DialogueWordPool>> read(MinecraftServer server) {
        return read(DatapackResourceLoader.jsonResources(server, "dialogue_words"));
    }

    static Map<String, Map<ResourceLocation, DialogueWordPool>> read(List<DatapackResourceLoader.JsonResource> resources) {
        Map<String, Map<ResourceLocation, DialogueWordPool>> pools = new LinkedHashMap<>();
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
                DatapackDiagnostics.warnUnknownRootKeys(location, "dialogue words", root, Set.of("values"));
                try {
                    pools.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).put(id, DialogueWordPool.read(root));
                } catch (IllegalArgumentException exception) {
                    DatapackDiagnostics.warnSkippedEntry(location, "dialogue words", "values", exception.getMessage());
                }
            });
        });
        pools.replaceAll((locale, values) -> Map.copyOf(values));
        return Map.copyOf(pools);
    }
}
