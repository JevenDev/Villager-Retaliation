package com.jvn.villagerretaliation.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.Comparator;
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
        server.getResourceManager()
                .listResources(root, location -> location.getPath().endsWith(".json") && locationFilter.test(location))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
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
}
