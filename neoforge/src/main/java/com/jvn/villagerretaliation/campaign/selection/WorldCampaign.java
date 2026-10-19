package com.jvn.villagerretaliation.campaign.selection;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record WorldCampaign(ResourceLocation id, String name, String description, List<String> authors,
                            ResourceLocation image, boolean locked, boolean includeDefaultContent) {
    public static final ResourceLocation DEFAULT = ResourceLocation.parse("villagerretaliation:default");
    public static final ResourceLocation SHARED = ResourceLocation.parse("villagerretaliation:shared");
    public static final ResourceLocation CARD_FALLBACK = ResourceLocation.parse("villagerretaliation:textures/gui/campaign_select_screen/campaign_select_screen_card_fallback.png");
    public static final ResourceLocation MOD_ICON = ResourceLocation.parse("villagerretaliation:mod_icon");

    public WorldCampaign {
        authors = List.copyOf(authors);
    }

    public static WorldCampaign read(JsonObject root) {
        var issues = QuestJsonSchema.validate(root, WorldCampaignSchema.export());
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.toString());
        ResourceLocation id = ResourceLocation.parse(root.get("id").getAsString());
        if (SHARED.equals(id)) throw new IllegalArgumentException("The shared ownership ID is reserved");
        List<String> authors = root.has("authors") ? java.util.stream.StreamSupport.stream(root.getAsJsonArray("authors").spliterator(), false)
                .map(value -> value.getAsString()).toList() : List.of();
        return new WorldCampaign(id, root.get("name").getAsString(), root.has("description") ? root.get("description").getAsString() : "", authors,
                root.has("image") ? ResourceLocation.parse(root.get("image").getAsString()) : CARD_FALLBACK,
                root.has("locked") && root.get("locked").getAsBoolean(),
                root.has("include_default_content") && root.get("include_default_content").getAsBoolean());
    }
}
