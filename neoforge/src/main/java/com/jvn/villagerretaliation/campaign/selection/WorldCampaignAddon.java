package com.jvn.villagerretaliation.campaign.selection;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record WorldCampaignAddon(ResourceLocation id, String name, String description, List<String> authors,
                                 List<ResourceLocation> campaigns, List<ResourceLocation> incompatibleCampaigns,
                                 List<ResourceLocation> requires, List<ResourceLocation> incompatibleAddons,
                                 ResourceLocation image, String sourcePack, boolean allowExistingWorlds) {
    public static final String SCHEMA = "villagerretaliation:campaign_addon/v1";
    public static final int MAX_SELECTED = 64;

    public WorldCampaignAddon {
        authors = List.copyOf(authors);
        campaigns = List.copyOf(campaigns);
        incompatibleCampaigns = List.copyOf(incompatibleCampaigns);
        requires = List.copyOf(requires);
        incompatibleAddons = List.copyOf(incompatibleAddons);
        if (campaigns.stream().anyMatch(incompatibleCampaigns::contains)) {
            throw new IllegalArgumentException("A campaign cannot be both supported and incompatible");
        }
        if (requires.contains(id) || incompatibleAddons.contains(id) || requires.stream().anyMatch(incompatibleAddons::contains)) {
            throw new IllegalArgumentException("Addon dependencies conflict with its identity or incompatibilities");
        }
    }

    public static WorldCampaignAddon read(JsonObject root) {
        var issues = QuestJsonSchema.validate(root, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.toString());
        List<String> authors = root.has("authors") ? java.util.stream.StreamSupport.stream(root.getAsJsonArray("authors").spliterator(), false)
                .map(value -> value.getAsString()).toList() : List.of();
        return new WorldCampaignAddon(ResourceLocation.parse(root.get("id").getAsString()), root.get("name").getAsString(),
                root.has("description") ? root.get("description").getAsString() : "", authors,
                ids(root, "campaigns"), ids(root, "incompatible_campaigns"), ids(root, "requires"), ids(root, "incompatible_addons"),
                root.has("image") ? ResourceLocation.parse(root.get("image").getAsString()) : null, "",
                root.has("allow_existing_worlds") && root.get("allow_existing_worlds").getAsBoolean());
    }

    private static List<ResourceLocation> ids(JsonObject root, String field) {
        return root.has(field) ? java.util.stream.StreamSupport.stream(root.getAsJsonArray(field).spliterator(), false)
                .map(value -> ResourceLocation.parse(value.getAsString())).toList() : List.of();
    }

    public boolean supports(ResourceLocation campaign) {
        return campaign != null && (campaigns.isEmpty() || campaigns.contains(campaign)) && !incompatibleCampaigns.contains(campaign);
    }

    public static JsonObject schema() {
        JsonObject schema = WorldCampaignSchema.export();
        JsonObject properties = schema.getAsJsonObject("properties");
        properties.getAsJsonObject("schema").addProperty("const", SCHEMA);
        for (String field : List.of("locked", "include_default_content")) properties.remove(field);
        JsonObject existing = new JsonObject();
        existing.addProperty("type", "boolean");
        existing.addProperty("default", false);
        properties.add("allow_existing_worlds", existing);
        JsonObject ids = new JsonObject();
        ids.addProperty("type", "array");
        ids.addProperty("maxItems", MAX_SELECTED);
        ids.addProperty("uniqueItems", true);
        ids.add("items", properties.get("id").deepCopy());
        for (String field : List.of("campaigns", "incompatible_campaigns", "requires", "incompatible_addons")) properties.add(field, ids.deepCopy());
        return schema;
    }
}
