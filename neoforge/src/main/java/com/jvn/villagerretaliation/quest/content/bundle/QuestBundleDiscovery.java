package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Reads bundle and unsupported legacy resource stacks with pack provenance. */
public final class QuestBundleDiscovery {
    private static final List<String> RESOURCE_ROOTS = List.of(
            "quests",
            "quest_messages",
            "quest_scenes",
            "quest_encounters",
            "quest_pools",
            "loot_table/quest");
    private QuestBundleDiscovery() {
    }

    public static List<QuestBundleTransactions.RawResource> discover(MinecraftServer server) {
        Map<String, Integer> packOrder = new LinkedHashMap<>();
        final int[] index = {0};
        server.getResourceManager().listPacks()
                .forEach(pack -> packOrder.put(pack.packId(), index[0]++));

        List<QuestBundleTransactions.RawResource> result = new ArrayList<>();
        Set<ResourceLocation> discovered = new LinkedHashSet<>();
        for (String root : RESOURCE_ROOTS) {
            for (DatapackResourceLoader.JsonResourceStack stack
                    : DatapackResourceLoader.jsonResourceStacks(server, root)) {
                if (!discovered.add(stack.location())) {
                    continue;
                }
                for (DatapackResourceLoader.JsonResource layer : stack.layersLowToHigh()) {
                    int priority = packOrder.getOrDefault(layer.resource().sourcePackId(), Integer.MAX_VALUE);
                    try (Reader reader = layer.resource().openAsReader()) {
                        result.add(QuestBundleTransactions.RawResource.valid(
                                priority,
                                layer.resource().sourcePackId(),
                                layer.location(),
                                JsonParser.parseReader(reader).getAsJsonObject()));
                    } catch (Exception exception) {
                        result.add(QuestBundleTransactions.RawResource.malformed(
                                priority,
                                layer.resource().sourcePackId(),
                                layer.location(),
                                "malformed quest content JSON: " + exception.getMessage()));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

}
