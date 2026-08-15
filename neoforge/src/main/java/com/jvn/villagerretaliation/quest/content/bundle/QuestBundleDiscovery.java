package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Reads every bundle resource stack with pack provenance while leaving loose legacy resources untouched. */
public final class QuestBundleDiscovery {
    private QuestBundleDiscovery() {
    }

    public static List<QuestBundleTransactions.RawResource> discover(MinecraftServer server) {
        Map<String, Integer> packOrder = new LinkedHashMap<>();
        final int[] index = {0};
        server.getResourceManager().listPacks()
                .forEach(pack -> packOrder.put(pack.packId(), index[0]++));

        List<QuestBundleTransactions.RawResource> result = new ArrayList<>();
        for (DatapackResourceLoader.JsonResourceStack stack
                : DatapackResourceLoader.jsonResourceStacks(server, "quests")) {
            if (!looksLikeBundlePath(stack.location())) {
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
                            "malformed bundle JSON: " + exception.getMessage()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean looksLikeBundlePath(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith("quests/")) {
            return false;
        }
        String[] parts = path.substring("quests/".length()).split("/");
        return parts.length > 0 && ("_shared".equals(parts[0]) || parts.length >= 3);
    }
    }
