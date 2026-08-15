package com.jvn.villagerretaliation.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

public final class DatapackResourceLoader {
    private DatapackResourceLoader() {
    }

    public static void forEachJsonResource(
            MinecraftServer server,
            String root,
            BiConsumer<ResourceLocation, Resource> consumer) {
        forEachJsonResource(server, root, ignored -> true, consumer);
    }

    public static void forEachJsonResource(
            MinecraftServer server,
            String root,
            Predicate<ResourceLocation> locationFilter,
            BiConsumer<ResourceLocation, Resource> consumer) {
        jsonResources(server, root, locationFilter)
                .forEach(entry -> consumer.accept(entry.location(), entry.resource()));
    }

    public static List<JsonResource> jsonResources(MinecraftServer server, String root) {
        return jsonResources(server, root, ignored -> true);
    }

    public static List<JsonResource> jsonResources(
            MinecraftServer server,
            String root,
            Predicate<ResourceLocation> locationFilter) {
        return server.getResourceManager()
                .listResources(root, location -> location.getPath().endsWith(".json") && locationFilter.test(location))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> new JsonResource(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Discovers every resource layer for each JSON location. Minecraft exposes stacks in
     * low-to-high priority order; the returned layer index preserves that order.
     */
    public static List<JsonResourceStack> jsonResourceStacks(MinecraftServer server, String root) {
        Map<ResourceLocation, List<Resource>> stacks = server.getResourceManager()
                .listResourceStacks(root, location -> location.getPath().endsWith(".json"));
        List<JsonResourceStack> result = new ArrayList<>();
        stacks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<JsonResource> layers = new ArrayList<>();
                    for (Resource resource : entry.getValue()) {
                        layers.add(new JsonResource(entry.getKey(), resource));
                    }
                    result.add(new JsonResourceStack(entry.getKey(), List.copyOf(layers)));
                });
        return List.copyOf(result);
    }

    public record JsonResourceStack(ResourceLocation location, List<JsonResource> layersLowToHigh) {
        public JsonResourceStack { layersLowToHigh = layersLowToHigh == null ? List.of() : List.copyOf(layersLowToHigh); }
    }
    public static Optional<JsonObject> readObject(
            ResourceLocation location,
            String systemName,
            Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | IllegalStateException | JsonParseException exception) {
            DatapackDiagnostics.warnSkippedFile(location, systemName, exception);
            return Optional.empty();
        }
    }

    public record JsonResource(ResourceLocation location, Resource resource) {
        public boolean isFromPack(String packId) {
            String expected = normalizePackId(packId);
            String actual = normalizePackId(this.resource.sourcePackId());
            return !expected.isBlank()
                    && (actual.equals(expected) || actual.endsWith(expected));
        }
    }

    private static String normalizePackId(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
