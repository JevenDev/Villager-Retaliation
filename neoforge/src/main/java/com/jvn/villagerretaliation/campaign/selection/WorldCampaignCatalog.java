package com.jvn.villagerretaliation.campaign.selection;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public record WorldCampaignCatalog(Map<ResourceLocation, WorldCampaign> campaigns) {
    public WorldCampaignCatalog {
        campaigns = Map.copyOf(campaigns);
    }

    public static WorldCampaignCatalog load(ResourceManager resources) {
        Map<ResourceLocation, WorldCampaign> result = new LinkedHashMap<>();
        resources.listResources("world_campaigns", id -> id.getPath().endsWith("/campaign.json") && id.getPath().split("/").length == 3)
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    try (var reader = entry.getValue().openAsReader()) {
                        var root = JsonParser.parseReader(reader).getAsJsonObject();
                        WorldCampaign campaign = WorldCampaign.read(root);
                        String expected = "world_campaigns/" + campaign.id().getPath() + "/campaign.json";
                        if (!entry.getKey().getNamespace().equals(campaign.id().getNamespace()) || !expected.equals(entry.getKey().getPath())) {
                            throw new IllegalArgumentException("Campaign ID must match its namespace and folder");
                        }
                        if (root.has("description_file")) {
                            ResourceLocation description = ResourceLocation.parse(root.get("description_file").getAsString());
                            campaign = new WorldCampaign(campaign.id(), campaign.name(), readDescription(resources, description),
                                    campaign.authors(), campaign.image(), campaign.locked(), campaign.includeDefaultContent());
                        }
                        if (result.putIfAbsent(campaign.id(), campaign) != null) throw new IllegalArgumentException("Duplicate campaign ID " + campaign.id());
                    } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                        throw new IllegalStateException("Invalid world campaign " + entry.getKey() + " from "
                                + entry.getValue().sourcePackId() + ": " + exception.getMessage(), exception);
                    }
                });
        return new WorldCampaignCatalog(result);
    }

    private static String readDescription(ResourceManager resources, ResourceLocation id) throws IOException {
        try (var reader = resources.getResourceOrThrow(id).openAsReader()) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (text.length() + count > 4096) throw new IOException("Campaign description " + id + " exceeds 4096 characters");
                text.append(buffer, 0, count);
            }
            return text.toString();
        }
    }

    public WorldCampaign require(ResourceLocation id, boolean creating) {
        WorldCampaign campaign = campaigns.get(id);
        if (campaign == null) throw new IllegalStateException("World campaign " + id + " is missing. Restore its datapack before opening this world.");
        if (creating && campaign.locked()) throw new IllegalStateException("World campaign " + id + " is locked and cannot be selected yet.");
        return campaign;
    }

    public List<WorldCampaign> choices() {
        return campaigns.values().stream().sorted(java.util.Comparator.comparing((WorldCampaign c) -> !c.id().equals(WorldCampaign.DEFAULT))
                .thenComparing(WorldCampaign::name).thenComparing(c -> c.id().toString())).toList();
    }
}
