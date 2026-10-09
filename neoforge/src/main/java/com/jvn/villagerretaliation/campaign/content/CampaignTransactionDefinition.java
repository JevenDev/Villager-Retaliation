package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignTransactionDefinition(String id, String titleKey, ResourceLocation quest, String character,
        ResourceLocation item, int count, Integer bargainedCount, String pointer) {
    public static final int MAX_ITEM_COUNT = 256;

    public CampaignTransactionDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_./-]*") || titleKey == null || titleKey.isBlank()
                || quest == null || character == null || !character.matches("[a-z0-9][a-z0-9_./-]*")
                || item == null || item.equals(ResourceLocation.parse("minecraft:air"))
                || count < 1 || count > MAX_ITEM_COUNT || bargainedCount != null && (bargainedCount < 0 || bargainedCount >= count))
            throw new IllegalArgumentException("Invalid campaign transaction terms");
    }

    static JsonObject schema() {
        var count = CampaignSchema.type("integer");
        count.addProperty("minimum", 1);
        count.addProperty("maximum", MAX_ITEM_COUNT);
        var bargained = count.deepCopy();
        bargained.addProperty("minimum", 0);
        return CampaignSchema.object(List.of("id", "title_key", "quest", "character", "payment"),
                "id", CampaignSchema.text("^[a-z0-9][a-z0-9_./-]*$"), "title_key", CampaignSchema.text(""),
                "quest", CampaignSchema.text("^[a-z0-9_.-]+:[a-z0-9_./-]+$"),
                "character", CampaignSchema.text("^[a-z0-9][a-z0-9_./-]*$"),
                "payment", CampaignSchema.object(List.of("item", "count"),
                        "item", CampaignSchema.text("^[a-z0-9_.-]+:[a-z0-9_./-]+$"), "count", count),
                "bargained_count", bargained);
    }

    static Map<String, CampaignTransactionDefinition> read(ResourceLocation source, JsonArray entries, List<QuestDiagnostic> issues) {
        var values = new LinkedHashMap<String, CampaignTransactionDefinition>();
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index).getAsJsonObject();
            var payment = entry.getAsJsonObject("payment");
            String pointer = "/transactions/" + index;
            String id = entry.get("id").getAsString();
            int count = payment.get("count").getAsInt();
            Integer bargained = entry.has("bargained_count") ? entry.get("bargained_count").getAsInt() : null;
            var item = ResourceLocation.parse(payment.get("item").getAsString());
            if (bargained != null && bargained >= count) {
                issues.add(CampaignSchema.error(source, pointer + "/bargained_count", "bargained payment must be lower than the original count"));
                continue;
            }
            if (item.equals(ResourceLocation.parse("minecraft:air"))) {
                issues.add(CampaignSchema.error(source, pointer + "/payment/item", "campaign payment cannot use minecraft:air"));
                continue;
            }
            var value = new CampaignTransactionDefinition(id, entry.get("title_key").getAsString(),
                    ResourceLocation.parse(entry.get("quest").getAsString()), entry.get("character").getAsString(), item, count, bargained, pointer);
            if (values.putIfAbsent(id, value) != null) issues.add(CampaignSchema.error(source, pointer + "/id", "duplicate transaction id " + id));
        }
        return values;
    }

    static void validate(CampaignDefinition campaign, List<QuestDiagnostic> issues) {
        for (var value : campaign.transactions().values()) {
            if (!campaign.questBundles().contains(value.quest()))
                issues.add(CampaignSchema.error(campaign.source(), value.pointer() + "/quest", "quest is not declared in quest_bundles: " + value.quest()));
            if (!campaign.characters().containsKey(value.character()))
                issues.add(CampaignSchema.error(campaign.source(), value.pointer() + "/character", "unknown transaction character " + value.character()));
            if (!value.titleKey().startsWith(campaign.localizationPrefix()))
                issues.add(CampaignSchema.error(campaign.source(), value.pointer() + "/title_key", "localization key must use the campaign prefix " + campaign.localizationPrefix()));
        }
    }
}
