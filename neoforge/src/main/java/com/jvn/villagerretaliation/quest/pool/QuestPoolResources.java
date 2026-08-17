package com.jvn.villagerretaliation.quest.pool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
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
    private static final long DEFAULT_REFRESH_TICKS = 24_000L;
    private static final Map<SelectionKey, Set<ResourceLocation>> SELECTION_CACHE = new ConcurrentHashMap<>();

    private QuestPoolResources() {
    }

    public static void warm(MinecraftServer server) {
        QuestContentCatalogs.warm(server);
    }

    public static void clearCache() {
        SELECTION_CACHE.clear();
        QuestContentCatalogs.invalidate();
    }

    public static List<QuestPoolDefinition> pools(MinecraftServer server) {
        return QuestContentCatalogs.current(server).pools();
    }

    public static ContentSnapshot snapshotForCatalog(MinecraftServer server) {
        return snapshotForCatalog(
                server, new QuestBundleTransactions.Result(Map.of(), List.of()));
    }

    public static ContentSnapshot snapshotForCatalog(
            MinecraftServer server,
            QuestBundleTransactions.Result bundles) {
        return new ContentSnapshot(load(server, bundles));
    }

    private static List<QuestPoolDefinition> load(
            MinecraftServer server,
            QuestBundleTransactions.Result bundles) {
        return read(server, bundles);
    }

    public static boolean allows(DialogueContext context, QuestDefinition quest) {
        return evaluate(context).allows(quest);
    }

    /**
     * Resolves the eligible quest catalog and pool selections once for a dialogue
     * context. Nearby quest tracking checks every quest for every villager, so
     * rebuilding these selections in each individual availability check turns
     * that scan into multiplicative work.
     */
    public static Evaluation evaluate(DialogueContext context) {
        if (context == null) {
            return Evaluation.ALLOW_ALL;
        }
        List<QuestPoolDefinition> matchingPools = pools(context.level().getServer()).stream()
                .filter(pool -> pool.matchesContext(context))
                .sorted(java.util.Comparator.comparingInt(QuestPoolDefinition::priority).reversed())
                .toList();
        if (matchingPools.isEmpty()) {
            return Evaluation.ALLOW_ALL;
        }
        List<QuestDefinition> catalog = VillagerQuestResources.quests(context.level().getServer()).stream()
                .filter(candidate -> VillagerQuestService.canStartIgnoringPools(context, candidate))
                .toList();
        List<PoolSelection> selections = matchingPools.stream()
                .map(pool -> new PoolSelection(pool, selectedFor(pool, catalog, context)))
                .toList();
        return new Evaluation(selections);
    }

    private static Set<ResourceLocation> selectedFor(
            QuestPoolDefinition pool,
            List<QuestDefinition> catalog,
            DialogueContext context) {
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
        return SELECTION_CACHE.computeIfAbsent(
                key,
                ignored -> QuestPoolSelector.select(pool, catalog, context, scopeKey, epoch));
    }

    public static final class Evaluation {
        private static final Evaluation ALLOW_ALL = new Evaluation(List.of());
        private final List<PoolSelection> selections;

        private Evaluation(List<PoolSelection> selections) {
            this.selections = List.copyOf(selections);
        }

        public boolean allows(QuestDefinition quest) {
            if (quest == null) {
                return true;
            }
            List<PoolSelection> claiming = this.selections.stream()
                    .filter(selection -> selection.pool().claims(quest))
                    .toList();
            if (claiming.isEmpty()) {
                return true;
            }
            int highestExclusivePriority = claiming.stream()
                    .map(PoolSelection::pool)
                    .filter(QuestPoolDefinition::exclusive)
                    .mapToInt(QuestPoolDefinition::priority)
                    .max()
                    .orElse(Integer.MIN_VALUE);
            return claiming.stream().anyMatch(selection -> {
                QuestPoolDefinition pool = selection.pool();
                return (highestExclusivePriority == Integer.MIN_VALUE
                        || (pool.exclusive() && pool.priority() >= highestExclusivePriority))
                        && selection.selected().contains(quest.id());
            });
        }
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

    private static List<QuestPoolDefinition> read(
            MinecraftServer server,
            QuestBundleTransactions.Result bundles) {
        List<QuestPoolDefinition> pools = new ArrayList<>();
        Map<ResourceLocation, ResourceLocation> sources = new LinkedHashMap<>();
        if (bundles != null) {
            bundles.bundles().values().stream()
                    .sorted(java.util.Comparator.comparing(bundle -> bundle.owner().key()))
                    .forEach(bundle -> bundle.definitions()
                            .getOrDefault(QuestBundlePath.Kind.POOL, Map.of())
                            .entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> readDefinition(
                                    bundleSource(bundle.owner(), entry.getKey()),
                                    entry.getValue(),
                                    pools,
                                    sources)));
        }
        return List.copyOf(pools);
    }

    private static void readDefinition(
            ResourceLocation location,
            JsonObject root,
            List<QuestPoolDefinition> pools,
            Map<ResourceLocation, ResourceLocation> sources) {
        ResourceLocation requestedId = poolId(location, root);
        if (requestedId == null) {
            DatapackDiagnostics.warnSkippedEntry(
                    location, "quest pool", "id", "bundle definitions require an explicit stable id");
            return;
        }
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
            DatapackDiagnostics.warnDuplicateId(
                    location, "quest pool", pool.id().toString(), previous);
            pools.removeIf(existing -> existing.id().equals(pool.id()));
        }
        pools.add(pool);
    }

    private static ResourceLocation bundleSource(
            QuestBundlePath.Owner owner, ResourceLocation id) {
        String idPath = id.getPath();
        int separator = idPath.lastIndexOf('/');
        String file = (separator < 0 ? idPath : idPath.substring(separator + 1)) + ".json";
        String path = owner.shared()
                ? "quests/_shared/pools/" + file
                : "quests/" + owner.questline() + "/" + owner.slug() + "/pools/" + file;
        return ResourceLocation.fromNamespaceAndPath(owner.namespace(), path);
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
        return ResourceLocation.tryParse(DatapackJsonReader.readString(root, "id"));
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

    public record ContentSnapshot(List<QuestPoolDefinition> pools) {
        public ContentSnapshot {
            pools = pools == null ? List.of() : List.copyOf(pools);
        }
    }


    private record WeightedQuestId(ResourceLocation id, int weight) {
    }

    private record PoolSelection(QuestPoolDefinition pool, Set<ResourceLocation> selected) {
    }

    private record SelectionKey(ResourceLocation pool, String scope, long epoch, List<WeightedQuestId> effectiveWeights) {
    }
}
