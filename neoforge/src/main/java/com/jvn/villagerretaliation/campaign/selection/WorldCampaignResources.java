package com.jvn.villagerretaliation.campaign.selection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

public final class WorldCampaignResources {
    private static final Set<String> ROOTS = Set.of("quests", "dialogue", "dialogue_trees", "forced_dialogue", "paired_greetings", "campaigns", "campaign_scenarios");

    private WorldCampaignResources() { }

    public static boolean supports(String root) {
        return ROOTS.contains(root.split("/", 2)[0]);
    }

    public static List<DatapackResourceLoader.JsonResourceStack> stacks(MinecraftServer server, String root) {
        return stacks(server.getResourceManager(), WorldCampaignSelection.get(server), root);
    }

    public static List<DatapackResourceLoader.JsonResourceStack> stacks(net.minecraft.server.packs.resources.ResourceManager manager,
            WorldCampaignSelection.Selection selection, String root) {
        WorldCampaignCatalog catalog = WorldCampaignCatalog.load(manager);
        if (selection.id() != null) catalog.require(selection.id(), false);
        Map<ResourceLocation, List<Resource>> mapped = new LinkedHashMap<>();
        manager.listResourceStacks(root, id -> id.getPath().endsWith(".json")).forEach((id, resources) -> {
            for (Resource resource : resources) append(manager, mapped, id, resource, null, selection, catalog);
        });
        for (WorldCampaign campaign : catalog.choices()) {
            if (!selection.allows(campaign.id())) continue;
            String prefix = "world_campaigns/" + campaign.id().getPath() + "/";
            manager.listResourceStacks(prefix + root, id -> id.getNamespace().equals(campaign.id().getNamespace()) && id.getPath().endsWith(".json"))
                    .forEach((physical, resources) -> {
                        ResourceLocation logical = ResourceLocation.fromNamespaceAndPath(physical.getNamespace(), physical.getPath().substring(prefix.length()));
                        for (Resource resource : resources) append(manager, mapped, logical, resource, campaign.id(), selection, catalog);
                    });
        }
        Map<String, Integer> order = new LinkedHashMap<>();
        manager.listPacks().forEach(pack -> order.put(pack.packId(), order.size()));
        return mapped.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            List<DatapackResourceLoader.JsonResource> layers = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(resource -> order.getOrDefault(resource.sourcePackId(), Integer.MAX_VALUE)))
                    .map(resource -> new DatapackResourceLoader.JsonResource(entry.getKey(), resource)).toList();
            return new DatapackResourceLoader.JsonResourceStack(entry.getKey(), layers);
        }).toList();
    }

    private static void append(net.minecraft.server.packs.resources.ResourceManager manager, Map<ResourceLocation, List<Resource>> mapped,
            ResourceLocation location, Resource resource, ResourceLocation folderOwner,
            WorldCampaignSelection.Selection selection, WorldCampaignCatalog catalog) {
        JsonObject root;
        ResourceLocation inherited;
        try (var reader = resource.openAsReader()) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
            inherited = folderOwner == null ? bundleOwner(manager, location, resource) : folderOwner;
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            appendLayer(mapped, location, new Resource(resource.source(), () -> {
                throw new IOException("Cannot read campaign content " + location + " from " + resource.sourcePackId(), exception);
            }));
            return;
        }
        try {
            ResourceLocation owner = owner(root, inherited, folderOwner != null, catalog);
            JsonObject filtered = root.deepCopy();
            if (root.has("entries") && root.get("entries").isJsonArray()) {
                JsonArray entries = new JsonArray();
                for (var element : root.getAsJsonArray("entries")) {
                    if (!element.isJsonObject()) throw new IllegalArgumentException("Dialogue entries must be objects");
                    JsonObject entry = element.getAsJsonObject();
                    ResourceLocation entryOwner = owner(entry, owner, folderOwner != null, catalog);
                    if (selection.allows(entryOwner)) {
                        JsonObject accepted = entry.deepCopy();
                        accepted.remove("world_campaign");
                        entries.add(accepted);
                    }
                }
                if (entries.isEmpty() && (!root.getAsJsonArray("entries").isEmpty() || !selection.allows(owner))) return;
                filtered.add("entries", entries);
            } else if (!selection.allows(owner)) return;
            if (!com.jvn.villagerretaliation.quest.schema.v3.QuestV3Format.isV3(filtered)) filtered.remove("world_campaign");
            byte[] bytes = filtered.toString().getBytes(StandardCharsets.UTF_8);
            appendLayer(mapped, location, new Resource(resource.source(), () -> new ByteArrayInputStream(bytes)));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IllegalStateException("Invalid campaign content " + location + " from " + resource.sourcePackId() + ": " + exception.getMessage(), exception);
        }
    }

    private static void appendLayer(Map<ResourceLocation, List<Resource>> mapped, ResourceLocation location, Resource resource) {
        List<Resource> layers = mapped.computeIfAbsent(location, ignored -> new ArrayList<>());
        if (layers.stream().anyMatch(layer -> layer.sourcePackId().equals(resource.sourcePackId()))) {
            throw new IllegalArgumentException("Multiple campaign files map to " + location + " in the same pack");
        }
        layers.add(resource);
    }

    private static ResourceLocation owner(JsonObject root, ResourceLocation inherited, boolean folder, WorldCampaignCatalog catalog) {
        if (!root.has("world_campaign")) return inherited;
        var value = root.get("world_campaign");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !value.getAsString().contains(":")) {
            throw new IllegalArgumentException("world_campaign must be a namespaced campaign ID");
        }
        ResourceLocation explicit = ResourceLocation.parse(value.getAsString());
        if (folder && !explicit.equals(inherited)) throw new IllegalArgumentException("world_campaign conflicts with the campaign folder");
        if (!WorldCampaign.SHARED.equals(explicit)) catalog.require(explicit, false);
        return explicit;
    }

    private static ResourceLocation bundleOwner(net.minecraft.server.packs.resources.ResourceManager manager, ResourceLocation location, Resource companion) throws IOException {
        String[] parts = location.getPath().split("/", 4);
        String manifest;
        if (parts.length == 4 && parts[0].equals("quests") && !parts[1].equals("_shared") && !parts[3].equals("quest.json")) {
            manifest = "quests/" + parts[1] + "/" + parts[2] + "/quest.json";
        } else if (parts.length >= 3 && parts[0].equals("campaigns") && !parts[2].equals("campaign.json")) {
            manifest = "campaigns/" + parts[1] + "/campaign.json";
        } else return null;
        ResourceLocation quest = ResourceLocation.fromNamespaceAndPath(location.getNamespace(), manifest);
        List<String> packs = manager.listPacks().map(net.minecraft.server.packs.PackResources::packId).toList();
        int layer = packs.indexOf(companion.sourcePackId());
        var resource = manager.getResourceStack(quest).stream()
                .filter(candidate -> packs.indexOf(candidate.sourcePackId()) <= layer)
                .max(Comparator.comparingInt(candidate -> packs.indexOf(candidate.sourcePackId())));
        if (resource.isEmpty()) return null;
        try (var reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("world_campaign")) return null;
            return ResourceLocation.parse(root.get("world_campaign").getAsString());
        }
    }
}
