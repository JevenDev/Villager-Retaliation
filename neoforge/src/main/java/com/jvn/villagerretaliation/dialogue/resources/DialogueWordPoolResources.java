package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueWordPools;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.ArrayList;
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
    private static final ServerResourceCache<Map<String, Map<ResourceLocation, List<String>>>> CACHE =
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

    public static List<String> localizedValues(MinecraftServer server, String locale, ResourceLocation id) {
        return server == null ? List.of() : CACHE.get(server).getOrDefault(VillagerLocale.normalize(locale), Map.of()).getOrDefault(id, List.of());
    }

    public static List<String> values(Map<String, Map<ResourceLocation, List<String>>> pools, String locale, ResourceLocation id) {
        return pools.getOrDefault(VillagerLocale.normalize(locale), Map.of()).getOrDefault(id,
                pools.getOrDefault(VillagerLocale.DEFAULT_LOCALE, Map.of()).getOrDefault(id, List.of()));
    }

    public static UnaryOperator<String> resolver(MinecraftServer server, String locale, RandomSource random) {
        return DialogueWordPools.resolver(id -> values(server, locale, id), random, issue -> {
            if (REPORTED.add(VillagerLocale.normalize(locale) + ":" + issue)) {
                DatapackDiagnostics.warnSkippedEntry(ResourceLocation.fromNamespaceAndPath("villagerretaliation", "dialogue_words"),
                        "dialogue words", locale, issue);
            }
        });
    }

    public static List<String> readValues(JsonObject root) {
        if (!root.has("values") || !root.get("values").isJsonArray() || root.getAsJsonArray("values").isEmpty())
            throw new IllegalArgumentException("values must be a nonempty array of literal strings");
        List<String> values = new ArrayList<>();
        for (var value : root.getAsJsonArray("values")) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("word pool entries must be strings");
            String text = value.getAsString();
            if (text.isBlank() || text.length() > 512 || text.chars().anyMatch(c -> "{}<>\u00a7".indexOf(c) >= 0 || Character.isISOControl(c)))
                throw new IllegalArgumentException("word pool entries must be nonblank literal text without placeholders, markup, or control characters, at most 512 characters");
            values.add(text);
        }
        return List.copyOf(values);
    }

    private static Map<String, Map<ResourceLocation, List<String>>> read(MinecraftServer server) {
        return read(DatapackResourceLoader.jsonResources(server, "dialogue_words"));
    }

    static Map<String, Map<ResourceLocation, List<String>>> read(List<DatapackResourceLoader.JsonResource> resources) {
        Map<String, Map<ResourceLocation, List<String>>> pools = new LinkedHashMap<>();
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
                    pools.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).put(id, readValues(root));
                } catch (IllegalArgumentException exception) {
                    DatapackDiagnostics.warnSkippedEntry(location, "dialogue words", "values", exception.getMessage());
                }
            });
        });
        pools.replaceAll((locale, values) -> Map.copyOf(values));
        return Map.copyOf(pools);
    }
}
