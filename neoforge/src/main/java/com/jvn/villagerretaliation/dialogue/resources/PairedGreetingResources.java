package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.PairedGreetingService.Pair;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Optional additive bindings; they never replace normal dialogue pools or menus. */
public final class PairedGreetingResources {
    private static final Set<String> KEYS = Set.of("id", "owner_id", "npc_variant_id", "npc_text",
            "reputation_level", "familiarity", "player", "weight", "option_id");
    private static volatile Cache cache = new Cache(null, Map.of());

    private PairedGreetingResources() { }

    public static List<Pair> pairs(DialogueContext context) {
        MinecraftServer server = context.level().getServer();
        String locale = VillagerLocale.normalize(context.locale());
        Cache current = cache;
        if (current.server() == server && current.locales().containsKey(locale)) return current.locales().get(locale);
        synchronized (PairedGreetingResources.class) {
            current = cache;
            Map<String, List<Pair>> locales = current.server() == server ? new HashMap<>(current.locales()) : new HashMap<>();
            if (locales.containsKey(locale)) return locales.get(locale);
            Map<String, Pair> pairs = new LinkedHashMap<>();
            readLocale(server, VillagerLocale.DEFAULT_LOCALE, pairs);
            if (!locale.equals(VillagerLocale.DEFAULT_LOCALE)) readLocale(server, locale, pairs);
            List<Pair> loaded = List.copyOf(pairs.values());
            locales.put(locale, loaded);
            cache = new Cache(server, Map.copyOf(locales));
            return loaded;
        }
    }

    public static void clearCache() { cache = new Cache(null, Map.of()); }

    private static void readLocale(MinecraftServer server, String locale, Map<String, Pair> pairs) {
        DatapackResourceLoader.forEachJsonResource(server, "paired_greetings/" + locale, (location, resource) ->
                DatapackResourceLoader.readObject(location, "paired greetings", resource).ifPresent(root ->
                        readPairs(location, root).forEach(pair -> pairs.put(pair.id(), pair))));
    }

    public static List<Pair> readPairs(ResourceLocation location, JsonObject root) {
        DatapackDiagnostics.warnUnknownRootKeys(location, "paired greetings", root, Set.of("pairs", "metadata"));
        if (!root.has("pairs") || !root.get("pairs").isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "pairs", "Expected a pairs array.");
            return List.of();
        }
        List<Pair> pairs = new ArrayList<>();
        int index = 0;
        for (JsonElement raw : root.getAsJsonArray("pairs")) {
            String context = "pairs[" + index++ + "]";
            try {
                JsonObject entry = raw.getAsJsonObject();
                DatapackDiagnostics.warnUnknownKeys(location, "paired greetings", context, entry, KEYS);
                String familiarity = DatapackJsonReader.readString(entry, "familiarity");
                if (!familiarity.equals("first") && !familiarity.equals("familiar")) {
                    throw new IllegalArgumentException("familiarity must be first or familiar");
                }
                pairs.add(new Pair(DatapackJsonReader.readString(entry, "id"),
                        DatapackJsonReader.readString(entry, "owner_id"),
                        DatapackJsonReader.readString(entry, "npc_variant_id"),
                        DatapackJsonReader.readString(entry, "npc_text"),
                        VillagerReputationLevel.valueOf(DatapackJsonReader.readString(entry, "reputation_level").toUpperCase(Locale.ROOT)),
                        familiarity.equals("first"), DatapackJsonReader.readString(entry, "player"),
                        DatapackJsonReader.readInt(entry, "weight", 1),
                        entry.has("option_id") ? DatapackJsonReader.readString(entry, "option_id") : "adult_greeting"));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, exception.getMessage());
            }
        }
        return List.copyOf(pairs);
    }

    private record Cache(MinecraftServer server, Map<String, List<Pair>> locales) { }
}
