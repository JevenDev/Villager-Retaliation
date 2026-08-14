package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.util.ContentTags;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

public final class DialogueTuningResources {
    private static final String RESOURCE_ROOT = "dialogue_tuning";
    private static final String SCHEMA = "villagerretaliation:dialogue_tuning/v1";
    private static final ServerResourceCache<Map<String, Double>> CACHE =
            ServerResourceCache.create(Map::of, DialogueTuningResources::read);

    private DialogueTuningResources() {
    }

    public static void warm(MinecraftServer server) {
        CACHE.get(server);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static double value(MinecraftServer server, String key, double fallback) {
        if (server == null) {
            return fallback;
        }
        return CACHE.get(server).getOrDefault(ContentTags.normalize(key), fallback);
    }

    public static double value(DialogueContext context, String key, double fallback) {
        return context == null ? fallback : value(context.level().getServer(), key, fallback);
    }

    public static boolean passes(DialogueContext context, String key, double fallbackChance) {
        return passes(context, value(context, key, fallbackChance));
    }

    public static boolean passes(DialogueContext context, double chance) {
        double bounded = Math.clamp(chance, 0.0D, 1.0D);
        return context != null && (bounded >= 1.0D || bounded > 0.0D && context.random().nextDouble() < bounded);
    }

    private static Map<String, Double> read(MinecraftServer server) {
        Map<String, Double> values = new LinkedHashMap<>();
        DatapackResourceLoader.forEachJsonResource(server, RESOURCE_ROOT, (location, resource) ->
                DatapackResourceLoader.readObject(location, "dialogue tuning", resource).ifPresent(root -> {
                    String schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                            ? root.get("schema").getAsString()
                            : "";
                    if (!schema.isBlank() && !SCHEMA.equals(schema)) {
                        DatapackDiagnostics.warnSkippedEntry(location, "dialogue tuning", "root", "unsupported schema " + schema);
                        return;
                    }
                    JsonObject entries = root.has("values") && root.get("values").isJsonObject()
                            ? root.getAsJsonObject("values")
                            : null;
                    if (entries == null) {
                        DatapackDiagnostics.warnSkippedEntry(location, "dialogue tuning", "root", "values must be an object");
                        return;
                    }
                    for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                        if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                            DatapackDiagnostics.warnSkippedEntry(
                                    location, "dialogue tuning", entry.getKey(), "value must be numeric");
                            continue;
                        }
                        String key = ContentTags.normalize(entry.getKey());
                        double value = entry.getValue().getAsDouble();
                        if (!key.isBlank() && Double.isFinite(value)) {
                            values.put(key, value);
                        }
                    }
                }));
        return Map.copyOf(values);
    }
}
