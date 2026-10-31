package com.jvn.villagerretaliation.util;

import com.google.gson.JsonObject;
import java.util.List;
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
        if (com.jvn.villagerretaliation.campaign.selection.WorldCampaignResources.supports(root)) {
            return jsonResourceStacks(server, root).stream().filter(stack -> locationFilter.test(stack.location()))
                    .filter(stack -> !stack.layersLowToHigh().isEmpty())
                    .map(stack -> stack.layersLowToHigh().getLast()).toList();
        }
        return com.jvn.toucanlib.util.ToucanJsonResources.resources(server.getResourceManager(), root, locationFilter)
                .stream().map(entry -> new JsonResource(entry.location(), entry.resource())).toList();
    }

    /**
     * Discovers every resource layer for each JSON location. Minecraft exposes stacks in
     * low-to-high priority order; the returned layer index preserves that order.
     */
    public static List<JsonResourceStack> jsonResourceStacks(MinecraftServer server, String root) {
        if (com.jvn.villagerretaliation.campaign.selection.WorldCampaignResources.supports(root)) {
            return com.jvn.villagerretaliation.campaign.selection.WorldCampaignResources.stacks(server, root);
        }
        return com.jvn.toucanlib.util.ToucanJsonResources.stacks(server.getResourceManager(), root).stream()
                .map(stack -> new JsonResourceStack(stack.location(), stack.layersLowToHigh().stream()
                        .map(entry -> new JsonResource(entry.location(), entry.resource())).toList())).toList();
    }

    public record JsonResourceStack(ResourceLocation location, List<JsonResource> layersLowToHigh) {
        public JsonResourceStack { layersLowToHigh = layersLowToHigh == null ? List.of() : List.copyOf(layersLowToHigh); }
    }
    public static Optional<JsonObject> readObject(
            ResourceLocation location,
            String systemName,
            Resource resource) {
        return com.jvn.toucanlib.util.ToucanJsonResources.readObject(location, resource,
                (id, exception) -> DatapackDiagnostics.warnSkippedFile(id, systemName, exception));
    }

    public record JsonResource(ResourceLocation location, Resource resource, int priority) {
        public JsonResource(ResourceLocation location, Resource resource) {
            this(location, resource, -1);
        }

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
