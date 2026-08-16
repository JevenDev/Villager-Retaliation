package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Loads the packaged projection of the exact beta.13 compatibility manifest. */
public final class BuiltInQuestBundleCompatibility {
    private static final String RESOURCE = "/quest-bundle-compatibility.json";
    private static final QuestBundleTransactions.CompatibilityRules RULES = load();

    private BuiltInQuestBundleCompatibility() {
    }

    public static QuestBundleTransactions.CompatibilityRules rules() {
        return RULES;
    }

    private static QuestBundleTransactions.CompatibilityRules load() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        Map<ResourceLocation, String> slugs = new LinkedHashMap<>();
        Map<ResourceLocation, String> prefixes = new LinkedHashMap<>();
        try (var stream = BuiltInQuestBundleCompatibility.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing required packaged resource " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject quests = root.getAsJsonObject("quests");
            for (Map.Entry<String, JsonElement> entry : quests.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject value = entry.getValue().getAsJsonObject();
                ids.add(id);
                slugs.put(id, value.get("slug").getAsString());
                prefixes.put(id, value.get("localization_prefix").getAsString());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load " + RESOURCE, exception);
        }
        return new QuestBundleTransactions.CompatibilityRules(ids, slugs, prefixes);
    }
}
