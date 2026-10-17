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
                        WorldCampaign campaign = WorldCampaign.read(JsonParser.parseReader(reader).getAsJsonObject());
                        String expected = "world_campaigns/" + campaign.id().getPath() + "/campaign.json";
                        if (!entry.getKey().getNamespace().equals(campaign.id().getNamespace()) || !expected.equals(entry.getKey().getPath())) {
                            throw new IllegalArgumentException("Campaign ID must match its namespace and folder");
                        }
                        if (result.putIfAbsent(campaign.id(), campaign) != null) throw new IllegalArgumentException("Duplicate campaign ID " + campaign.id());
                    } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                        throw new IllegalStateException("Invalid world campaign " + entry.getKey() + " from "
                                + entry.getValue().sourcePackId() + ": " + exception.getMessage(), exception);
                    }
                });
        return new WorldCampaignCatalog(result);
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
