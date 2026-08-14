package com.jvn.villagerretaliation.quest.pool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class QuestPoolResources {
    private static final String RESOURCE_ROOT = "quest_pools";
    private static final long DEFAULT_REFRESH_TICKS = 24_000L;
    private static volatile Cache cache = new Cache(null, List.of());
    private static final Map<SelectionKey, Set<ResourceLocation>> SELECTION_CACHE = new ConcurrentHashMap<>();

    private QuestPoolResources() {
    }

    public static void warm(MinecraftServer server) {
        pools(server);
    }

    public static void clearCache() {
        cache = new Cache(null, List.of());
        SELECTION_CACHE.clear();
    }

    public static List<QuestPoolDefinition> pools(MinecraftServer server) {
        Cache current = cache;
        if (current.server() == server) {
            return current.pools();
        }
        synchronized (QuestPoolResources.class) {
            current = cache;
            if (current.server() != server) {
                current = new Cache(server, read(server));
                cache = current;
            }
            return current.pools();
        }
    }

    public static boolean allows(DialogueContext context, QuestDefinition quest) {
        if (context == null || quest == null) {
            return true;
        }
        List<QuestPoolDefinition> claiming = pools(context.level().getServer()).stream()
                .filter(pool -> pool.matchesContext(context))
                .filter(pool -> pool.claims(quest))
                .sorted(java.util.Comparator.comparingInt(QuestPoolDefinition::priority).reversed())
                .toList();
        if (claiming.isEmpty()) {
            return true;
        }
        List<QuestDefinition> catalog = VillagerQuestResources.quests(context.level().getServer()).stream()
                .filter(candidate -> VillagerQuestService.canStartIgnoringPools(context, candidate))
                .toList();
        int highestExclusivePriority = claiming.stream().filter(QuestPoolDefinition::exclusive)
                .mapToInt(QuestPoolDefinition::priority).max().orElse(Integer.MIN_VALUE);
        for (QuestPoolDefinition pool : claiming) {
            if (highestExclusivePriority != Integer.MIN_VALUE
                    && (!pool.exclusive() || pool.priority() < highestExclusivePriority)) continue;
            String scopeKey = scopeKey(pool, context);
            long epoch = context.level().getGameTime() / pool.refreshTicks();
            List<WeightedQuestId> effectiveWeights = catalog.stream()
                    .filter(pool::claims)
                    .map(candidate -> new WeightedQuestId(candidate.id(), pool.weight(candidate, context)))
                    .filter(candidate -> candidate.weight() > 0)
                    .sorted(java.util.Comparator.comparing(candidate -> candidate.id().toString()))
                    .toList();
            SelectionKey key = new SelectionKey(pool.id(), scopeKey, epoch, effectiveWeights);
            if (SELECTION_CACHE.size() > 4_096) {
                SELECTION_CACHE.clear();
            }
            Set<ResourceLocation> selected = SELECTION_CACHE.computeIfAbsent(
                    key,
                    ignored -> QuestPoolSelector.select(pool, catalog, context, scopeKey, epoch));
            if (selected.contains(quest.id())) {
                return true;
            }
        }
        return false;
    }

    private static String scopeKey(QuestPoolDefinition pool, DialogueContext context) {
        return switch (pool.scope()) {
            case PLAYER -> context.player().getUUID().toString();
            case PROVIDER -> context.villager().getUUID().toString();
            case VILLAGE -> context.villageKey() == null || context.villageKey().isBlank()
                    ? context.villager().getUUID().toString()
                    : context.villageKey();
            case WORLD -> "world";
            case DIMENSION -> context.level().dimension().location().toString();
        };
    }

    private static List<QuestPoolDefinition> read(MinecraftServer server) {
        List<QuestPoolDefinition> pools = new ArrayList<>();
        Map<ResourceLocation, ResourceLocation> sources = new LinkedHashMap<>();
        DatapackResourceLoader.forEachJsonResource(server, RESOURCE_ROOT, (location, resource) ->
                DatapackResourceLoader.readObject(location, "quest pool", resource).ifPresent(root -> {
                    ResourceLocation requestedId = poolId(location, root);
                    if (DatapackJsonReader.readBoolean(root, "remove", false)) {
                        pools.removeIf(existing -> existing.id().equals(requestedId));
                        sources.remove(requestedId);
                        return;
                    }
                    QuestPoolDefinition pool = parse(location, root);
                    if (pool == null) {
                        return;
                    }
                    ResourceLocation previous = sources.put(pool.id(), location);
                    if (previous != null) {
                        DatapackDiagnostics.warnDuplicateId(location, "quest pool", pool.id().toString(), previous);
                        pools.removeIf(existing -> existing.id().equals(pool.id()));
                    }
                    pools.add(pool);
                }));
        return List.copyOf(pools);
    }

    static QuestPoolDefinition parse(ResourceLocation location, JsonObject root) {
        String schema = DatapackJsonReader.readString(root, "schema");
        if (!schema.isBlank() && !"villagerretaliation:quest_pool/v1".equals(schema)) {
            DatapackDiagnostics.warnSkippedEntry(location, "quest pool", "root", "unsupported schema " + schema);
            return null;
        }
        ResourceLocation id = poolId(location, root);
        Set<ResourceLocation> quests = resourceSet(root.get("quests"));
        Set<ResourceLocation> excludedQuests = resourceSet(root.get("exclude_quests"));
        Map<ResourceLocation, Integer> weights = new LinkedHashMap<>();
        JsonObject weightObject = root.has("weights") && root.get("weights").isJsonObject()
                ? root.getAsJsonObject("weights") : null;
        if (weightObject != null) {
            for (Map.Entry<String, JsonElement> entry : weightObject.entrySet()) {
                ResourceLocation questId = ResourceLocation.tryParse(entry.getKey());
                if (questId != null && entry.getValue().isJsonPrimitive()) {
                    weights.put(questId, Math.max(0, entry.getValue().getAsInt()));
                }
            }
        }
        List<QuestPoolDefinition.WeightRule> weightRules = new ArrayList<>();
        if (root.has("weight_rules") && root.get("weight_rules").isJsonArray()) {
            root.getAsJsonArray("weight_rules").forEach(raw -> {
                if (raw.isJsonObject()) {
                    JsonObject rule = raw.getAsJsonObject();
                    weightRules.add(new QuestPoolDefinition.WeightRule(
                            stringSet(rule.get("any_tags")), stringSet(rule.get("all_tags")),
                            stringSet(rule.get("exclude_tags")),
                            DatapackJsonReader.readDouble(rule, "multiplier", 1.0D),
                            DialogueCondition.readList(location, "quest pool weight rule", rule)));
                }
            });
        }
        Map<String, Integer> tagQuotas = new LinkedHashMap<>();
        if (root.has("tag_quotas") && root.get("tag_quotas").isJsonObject()) {
            root.getAsJsonObject("tag_quotas").entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) tagQuotas.put(entry.getKey(), Math.max(0, entry.getValue().getAsInt()));
            });
        }
        return new QuestPoolDefinition(
                id,
                !root.has("enabled") || root.get("enabled").getAsBoolean(),
                QuestPoolDefinition.Scope.parse(DatapackJsonReader.readString(root, "scope")),
                DatapackJsonReader.readDurationTicks(root, "refresh", DEFAULT_REFRESH_TICKS),
                DatapackJsonReader.readInt(root, "max_offers", 3),
                DatapackJsonReader.readInt(root, "anti_repeat_rotations", 1),
                DatapackJsonReader.readInt(root, "default_weight", 1),
                root.has("seed_salt") ? root.get("seed_salt").getAsLong() : 0L,
                quests,
                stringSet(root.get("any_tags")),
                stringSet(root.get("all_tags")),
                excludedQuests,
                stringSet(root.get("exclude_tags")),
                weights,
                QuestPoolDefinition.MatchMode.parse(DatapackJsonReader.readString(root, "match")),
                DatapackJsonReader.readInt(root, "priority", 0),
                root.has("exclusive") && root.get("exclusive").getAsBoolean(),
                weightRules,
                tagQuotas,
                DialogueCondition.readList(location, "quest pool", root));
    }

    private static ResourceLocation poolId(ResourceLocation location, JsonObject root) {
        ResourceLocation id = ResourceLocation.tryParse(DatapackJsonReader.readString(root, "id"));
        if (id != null) {
            return id;
        }
        String path = location.getPath().substring((RESOURCE_ROOT + "/").length());
        return ResourceLocation.fromNamespaceAndPath(
                location.getNamespace(), path.substring(0, path.length() - 5));
    }

    private static Set<ResourceLocation> resourceSet(JsonElement element) {
        Set<ResourceLocation> values = new LinkedHashSet<>();
        for (String value : strings(element)) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                values.add(id);
            }
        }
        return Set.copyOf(values);
    }

    private static Set<String> stringSet(JsonElement element) {
        return Set.copyOf(strings(element));
    }

    private static List<String> strings(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> {
                if (value.isJsonPrimitive()) {
                    values.add(value.getAsString());
                }
            });
        } else if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    private record Cache(MinecraftServer server, List<QuestPoolDefinition> pools) {
    }

    private record WeightedQuestId(ResourceLocation id, int weight) {
    }

    private record SelectionKey(ResourceLocation pool, String scope, long epoch, List<WeightedQuestId> effectiveWeights) {
    }
}
