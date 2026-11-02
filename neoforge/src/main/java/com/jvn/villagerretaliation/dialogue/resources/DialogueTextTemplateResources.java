package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialoguePresentationTemplates;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class DialogueTextTemplateResources {
    private static final String ROOT = "dialogue_templates/";
    private static final ServerResourceCache<Map<String, Map<ResourceLocation, JsonObject>>> CACHE =
            ServerResourceCache.create(Map::of, DialogueTextTemplateResources::read);

    private DialogueTextTemplateResources() { }

    public static void clearCache() { CACHE.clear(); }

    public static DialoguePresentationTemplates.Library library(MinecraftServer server, String locale) {
        return new DialoguePresentationTemplates.Library(id -> definition(server, locale, id));
    }

    public static JsonObject definition(MinecraftServer server, String locale, ResourceLocation id) {
        var value = localizedDefinition(server, VillagerLocale.normalize(locale), id);
        return value != null || VillagerLocale.DEFAULT_LOCALE.equals(locale) ? value : localizedDefinition(server, VillagerLocale.DEFAULT_LOCALE, id);
    }

    public static JsonObject localizedDefinition(MinecraftServer server, String locale, ResourceLocation id) {
        if (server != null) {
            var definitions = CACHE.get(server);
            var value = definitions.getOrDefault(VillagerLocale.normalize(locale), Map.of()).get(id);
            return value == null ? null : value.deepCopy();
        }
        return bundled(id, VillagerLocale.normalize(locale));
    }

    public static JsonObject bundled(ResourceLocation id, String locale) {
        String path = "/data/" + id.getNamespace() + "/" + ROOT + locale + "/" + id.getPath() + ".json";
        var stream = DialogueTextTemplateResources.class.getResourceAsStream(path);
        if (stream == null) return null;
        try (var reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException | com.google.gson.JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Cannot read presentation template " + id, exception);
        }
    }

    private static Map<String, Map<ResourceLocation, JsonObject>> read(MinecraftServer server) {
        var definitions = new LinkedHashMap<String, Map<ResourceLocation, JsonObject>>();
        for (var resource : DatapackResourceLoader.jsonResources(server, "dialogue_templates")) {
            String path = resource.location().getPath().substring(ROOT.length());
            int separator = path.indexOf('/');
            if (separator < 1 || path.length() <= separator + 6) {
                DatapackDiagnostics.warnSkippedEntry(resource.location(), "dialogue templates", "root", "expected <locale>/<template>.json");
                continue;
            }
            String locale = VillagerLocale.normalize(path.substring(0, separator));
            var id = ResourceLocation.fromNamespaceAndPath(resource.location().getNamespace(), path.substring(separator + 1, path.length() - 5));
            DatapackResourceLoader.readObject(resource.location(), "dialogue templates", resource.resource())
                    .ifPresent(json -> definitions.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).put(id, json));
        }
        var result = new LinkedHashMap<String, Map<ResourceLocation, JsonObject>>();
        definitions.forEach((locale, values) -> {
            var library = new DialoguePresentationTemplates.Library(id -> values.getOrDefault(id,
                    definitions.getOrDefault(VillagerLocale.DEFAULT_LOCALE, Map.of()).get(id)));
            var valid = new LinkedHashMap<ResourceLocation, JsonObject>();
            values.forEach((id, json) -> {
                try { library.apply(id); valid.put(id, json); }
                catch (IllegalArgumentException exception) { DatapackDiagnostics.warnSkippedEntry(id, "dialogue templates", locale, exception.getMessage()); }
            });
            result.put(locale, Map.copyOf(valid));
        });
        return Map.copyOf(result);
    }
}
