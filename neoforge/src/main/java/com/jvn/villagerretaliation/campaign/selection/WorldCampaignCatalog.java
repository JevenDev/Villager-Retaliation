package com.jvn.villagerretaliation.campaign.selection;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public record WorldCampaignCatalog(Map<ResourceLocation, WorldCampaign> campaigns, Map<ResourceLocation, WorldCampaignAddon> addons) {
    public WorldCampaignCatalog {
        campaigns = Map.copyOf(campaigns);
        addons = Map.copyOf(addons);
    }

    public WorldCampaignCatalog(Map<ResourceLocation, WorldCampaign> campaigns) {
        this(campaigns, Map.of());
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
        Map<ResourceLocation, WorldCampaignAddon> addons = new LinkedHashMap<>();
        resources.listResources("campaign_addons", id -> id.getPath().endsWith("/addon.json") && id.getPath().split("/").length == 3)
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    try (var reader = entry.getValue().openAsReader()) {
                        var root = JsonParser.parseReader(reader).getAsJsonObject();
                        WorldCampaignAddon addon = WorldCampaignAddon.read(root);
                        String expected = "campaign_addons/" + addon.id().getPath() + "/addon.json";
                        if (!entry.getKey().getNamespace().equals(addon.id().getNamespace()) || !expected.equals(entry.getKey().getPath())) {
                            throw new IllegalArgumentException("Addon ID must match its namespace and folder");
                        }
                        addon = new WorldCampaignAddon(addon.id(), addon.name(), root.has("description_file")
                                ? readDescription(resources, ResourceLocation.parse(root.get("description_file").getAsString())) : addon.description(),
                                addon.authors(), addon.campaigns(), addon.incompatibleCampaigns(), addon.requires(), addon.incompatibleAddons(),
                                addon.image(), entry.getValue().sourcePackId(), addon.allowExistingWorlds());
                        if (addons.putIfAbsent(addon.id(), addon) != null) throw new IllegalArgumentException("Duplicate addon ID " + addon.id());
                    } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                        throw new IllegalStateException("Invalid campaign addon " + entry.getKey() + " from "
                                + entry.getValue().sourcePackId() + ": " + exception.getMessage(), exception);
                    }
                });
        return new WorldCampaignCatalog(result, addons);
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

    public String addonProblem(ResourceLocation id, ResourceLocation campaign, List<ResourceLocation> enabled) {
        WorldCampaignAddon addon = addons.get(id);
        if (addon == null) return "Missing addon " + id + ". Restore its datapack before opening this world.";
        if (!addon.supports(campaign)) return "Incompatible with campaign " + campaign;
        for (ResourceLocation dependency : addon.requires()) {
            if (!enabled.contains(dependency)) return "Requires addon " + dependency;
            if (enabled.indexOf(dependency) >= enabled.indexOf(id)) return "Must have higher priority than " + dependency;
        }
        for (ResourceLocation other : enabled) {
            WorldCampaignAddon candidate = addons.get(other);
            if (addon.incompatibleAddons().contains(other) || candidate != null && candidate.incompatibleAddons().contains(id)) {
                return "Conflicts with " + other;
            }
        }
        return null;
    }

    public void validate(WorldCampaignSelection.Selection selection, boolean creating) {
        if (selection.id() != null) require(selection.id(), creating);
        for (ResourceLocation addon : selection.addons()) {
            String problem = addonProblem(addon, selection.id(), selection.addons());
            if (problem != null) throw new IllegalStateException("Invalid campaign addon " + addon + ": " + problem);
        }
    }

    public String addonChangeProblem(WorldCampaignSelection.Selection previous, List<ResourceLocation> next) {
        if (!next.stream().filter(previous.addons()::contains).toList().equals(previous.addons())) {
            return "Addons already saved with this world must stay enabled and keep their relative priority";
        }
        for (ResourceLocation id : next) {
            WorldCampaignAddon addon = addons.get(id);
            if (!previous.addons().contains(id) && addon != null && !addon.allowExistingWorlds()) {
                return "New worlds only: " + id;
            }
        }
        return null;
    }

    public List<WorldCampaignAddon> addonChoices() {
        return addons.values().stream().sorted(java.util.Comparator.comparing(WorldCampaignAddon::name)
                .thenComparing(addon -> addon.id().toString())).toList();
    }

    public List<WorldCampaign> choices() {
        return campaigns.values().stream().sorted(java.util.Comparator.comparing((WorldCampaign c) -> !c.id().equals(WorldCampaign.DEFAULT))
                .thenComparing(WorldCampaign::name).thenComparing(c -> c.id().toString())).toList();
    }
}
