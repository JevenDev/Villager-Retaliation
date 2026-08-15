package com.jvn.villagerretaliation.quest.tracking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

/** Reads the static item possibilities from quest reward loot tables for journal presentation. */
public final class QuestRewardPreviewResources {
    private static final int MAX_PREVIEWS_PER_TABLE = 16;
    private QuestRewardPreviewResources() {
    }

    public static List<ItemPreview> itemPreviews(MinecraftServer server, ResourceLocation lootTableId) {
        return server == null || lootTableId == null ? List.of() : read(server, lootTableId);
    }

    /** Retained for callers; preview data now belongs to the immutable catalog snapshot. */
    public static void clearCache() {
    }

    private static List<ItemPreview> read(MinecraftServer server, ResourceLocation lootTableId) {
        QuestRewardResolver.Resolution resolution = QuestRewardResolver.resolve(server, lootTableId);
        JsonObject root;
        if (resolution.bundled() != null) {
            root = resolution.bundled().tableJson();
        } else if (resolution.source() == QuestRewardResolver.Source.EXTERNAL) {
            ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(
                    lootTableId.getNamespace(),
                    "loot_table/" + lootTableId.getPath() + ".json");
            Resource resource = server.getResourceManager().getResource(resourceLocation).orElse(null);
            if (resource == null) {
                return List.of();
            }
            root = DatapackResourceLoader
                    .readObject(resourceLocation, "quest reward preview", resource)
                    .orElse(null);
        } else {
            return List.of();
        }
        if (root == null) {
            return List.of();
        }

        Map<ResourceLocation, ItemPreview> previews = new LinkedHashMap<>();
        JsonElement poolsElement = root.get("pools");
        if (poolsElement == null || !poolsElement.isJsonArray()) {
            return List.of();
        }
        for (JsonElement poolElement : poolsElement.getAsJsonArray()) {
            if (!poolElement.isJsonObject()) {
                continue;
            }
            JsonElement entriesElement = poolElement.getAsJsonObject().get("entries");
            if (entriesElement == null || !entriesElement.isJsonArray()) {
                continue;
            }
            for (JsonElement entry : entriesElement.getAsJsonArray()) {
                collectEntry(entry, previews);
                if (previews.size() >= MAX_PREVIEWS_PER_TABLE) {
                    return List.copyOf(previews.values());
                }
            }
        }
        return List.copyOf(previews.values());
    }

    private static void collectEntry(JsonElement element, Map<ResourceLocation, ItemPreview> previews) {
        if (element == null || !element.isJsonObject() || previews.size() >= MAX_PREVIEWS_PER_TABLE) {
            return;
        }
        JsonObject entry = element.getAsJsonObject();
        String type = DatapackJsonReader.readString(entry, "type");
        if ("minecraft:item".equals(type)) {
            ResourceLocation itemId = ResourceLocation.tryParse(DatapackJsonReader.readString(entry, "name", "item"));
            if (itemId != null) {
                CountRange count = countRange(entry);
                previews.putIfAbsent(itemId, new ItemPreview(itemId.toString(), count.text(), count.minimum()));
            }
            return;
        }
        JsonElement children = entry.get("children");
        if (children != null && children.isJsonArray()) {
            for (JsonElement child : children.getAsJsonArray()) {
                collectEntry(child, previews);
            }
        }
    }

    private static CountRange countRange(JsonObject entry) {
        JsonElement functionsElement = entry.get("functions");
        if (functionsElement == null || !functionsElement.isJsonArray()) {
            return new CountRange(1, 1);
        }
        JsonArray functions = functionsElement.getAsJsonArray();
        for (JsonElement functionElement : functions) {
            if (!functionElement.isJsonObject()) {
                continue;
            }
            JsonObject function = functionElement.getAsJsonObject();
            if (!"minecraft:set_count".equals(DatapackJsonReader.readString(function, "function"))) {
                continue;
            }
            JsonElement count = function.get("count");
            if (count == null) {
                return new CountRange(1, 1);
            }
            if (count.isJsonPrimitive()) {
                int value = Math.max(1, DatapackJsonReader.readInt(count, 1));
                return new CountRange(value, value);
            }
            if (count.isJsonObject()) {
                JsonObject range = count.getAsJsonObject();
                int minimum = Math.max(1, DatapackJsonReader.readInt(range, "min", 1));
                int maximum = Math.max(minimum, DatapackJsonReader.readInt(range, "max", minimum));
                return new CountRange(minimum, maximum);
            }
        }
        return new CountRange(1, 1);
    }

    public record ItemPreview(String itemId, String countText, int minimumCount) {
    }

    private record CountRange(int minimum, int maximum) {
        private String text() {
            return this.minimum == this.maximum ? Integer.toString(this.minimum) : this.minimum + "-" + this.maximum;
        }
    }
}
