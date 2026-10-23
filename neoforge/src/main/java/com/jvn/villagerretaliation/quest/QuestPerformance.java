package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record QuestPerformance(Map<String, Measurement> measurements, List<Tier> tiers) {
    public static final QuestPerformance EMPTY = new QuestPerformance(Map.of(), List.of());

    public QuestPerformance {
        measurements = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(measurements));
        tiers = List.copyOf(tiers);
    }

    public enum Metric {
        ELAPSED_TICKS, FINAL_HEALTH_PERCENT, LOWEST_HEALTH_PERCENT,
        DEATHS, DAMAGE_TAKEN, KILLS, OPTIONAL_OBJECTIVES
    }

    public record Measurement(Metric metric, String stage, String objective, String actor,
            Set<ResourceLocation> entityTypes, Set<ResourceLocation> entityTags,
            boolean includeInactive, boolean includeOffline) {
        public boolean health() {
            return metric == Metric.FINAL_HEALTH_PERCENT || metric == Metric.LOWEST_HEALTH_PERCENT;
        }
    }

    public record Bounds(Double min, Double max) {
        public boolean matches(Double value) {
            return value != null && Double.isFinite(value)
                    && (min == null || value >= min) && (max == null || value <= max);
        }
    }

    public record Tier(String id, String title, String titleKey, Map<String, Bounds> requirements,
            RewardOverrides rewards) {
        public Tier { requirements = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(requirements)); }
    }

    public record RewardOverrides(Integer experience, Integer reputation, Integer gossip,
            ResourceLocation loot, ResourceLocation memory, VillageEventMemory.MemoryScope memoryScope) {
        public QuestDefinition.Rewards apply(QuestDefinition.Rewards base) {
            return new QuestDefinition.Rewards(experience == null ? base.experience() : experience,
                    reputation == null ? base.reputation() : reputation,
                    gossip == null ? base.gossipReputation() : gossip,
                    loot == null ? base.lootTable() : loot, memory == null ? base.memoryEvent() : memory,
                    memoryScope == null ? base.memoryScope() : memoryScope);
        }
    }

    public String trackingSignature() {
        var signature = new StringBuilder();
        measurements.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var value = entry.getValue();
            signature.append(entry.getKey()).append(':').append(value.metric()).append(':')
                    .append(value.stage()).append(':').append(value.objective()).append(':').append(value.actor()).append(':')
                    .append(value.includeInactive()).append(':').append(value.includeOffline()).append(':')
                    .append(value.entityTypes().stream().map(Object::toString).sorted().toList()).append(':')
                    .append(value.entityTags().stream().map(Object::toString).sorted().toList()).append('|');
        });
        return signature.toString();
    }

    public Tier select(Map<String, Double> values) {
        return tiers.stream().filter(tier -> tier.requirements().entrySet().stream()
                .allMatch(entry -> entry.getValue().matches(values.get(entry.getKey())))).findFirst().orElse(null);
    }

    public static QuestPerformance read(JsonElement raw) {
        if (raw == null) return EMPTY;
        var issues = QuestJsonSchema.validate(raw, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid quest performance: " + issues);
        var object = raw.getAsJsonObject();
        Map<String, Measurement> measurements = new LinkedHashMap<>();
        object.getAsJsonObject("measurements").entrySet().forEach(entry -> {
            var value = entry.getValue().getAsJsonObject();
            var metric = Metric.valueOf(text(value, "metric").toUpperCase(java.util.Locale.ROOT));
            var measurement = new Measurement(metric, text(value, "stage"), text(value, "objective"),
                    text(value, "actor"), ids(value, "entity_types"), ids(value, "entity_tags"),
                    flag(value, "include_inactive"), flag(value, "include_offline"));
            if (!measurement.stage().isEmpty() && !measurement.objective().isEmpty())
                throw new IllegalArgumentException("Performance measurements select one scope: " + entry.getKey());
            if (measurement.health() != !measurement.actor().isEmpty())
                throw new IllegalArgumentException("Health measurements require an actor, other metrics cannot select one: " + entry.getKey());
            if (metric != Metric.KILLS && (!measurement.entityTypes().isEmpty() || !measurement.entityTags().isEmpty()))
                throw new IllegalArgumentException("Only kill measurements accept entity filters: " + entry.getKey());
            if (metric != Metric.ELAPSED_TICKS && (measurement.includeInactive() || measurement.includeOffline()))
                throw new IllegalArgumentException("Only elapsed time accepts clock options: " + entry.getKey());
            measurements.put(entry.getKey(), measurement);
        });
        List<Tier> tiers = new ArrayList<>();
        var seen = new java.util.HashSet<String>();
        for (var element : object.getAsJsonArray("tiers")) {
            var value = element.getAsJsonObject();
            String id = text(value, "id");
            if (!seen.add(id)) throw new IllegalArgumentException("Duplicate performance tier: " + id);
            Map<String, Bounds> requirements = new LinkedHashMap<>();
            value.getAsJsonObject("requirements").entrySet().forEach(entry -> {
                var bounds = entry.getValue().getAsJsonObject();
                Double min = number(bounds, "min"), max = number(bounds, "max");
                if (!measurements.containsKey(entry.getKey()))
                    throw new IllegalArgumentException("Unknown performance measurement: " + entry.getKey());
                if (min != null && max != null && min > max)
                    throw new IllegalArgumentException("Performance minimum exceeds maximum: " + entry.getKey());
                if (measurements.get(entry.getKey()).health() && (min != null && min > 100 || max != null && max > 100))
                    throw new IllegalArgumentException("Health percentages must be between 0 and 100");
                requirements.put(entry.getKey(), new Bounds(min, max));
            });
            var reward = value.getAsJsonObject("rewards");
            tiers.add(new Tier(id, value.has("title") ? text(value, "title") : id, text(value, "title_key"),
                    requirements, new RewardOverrides(integer(reward, "experience"), integer(reward, "reputation"),
                    integer(reward, "gossip_reputation"), resource(reward, "loot_table"),
                    resource(reward, "memory_event"), reward.has("memory_scope")
                    ? VillageEventMemory.MemoryScope.parse(text(reward, "memory_scope")).orElseThrow() : null)));
        }
        return new QuestPerformance(measurements, tiers);
    }

    public List<String> referenceErrors(JsonObject root) {
        List<String> errors = new ArrayList<>();
        var stages = new java.util.HashSet<String>();
        var objectives = new java.util.HashSet<String>();
        var optional = new java.util.HashSet<String>();
        var actors = new java.util.HashSet<String>();
        if (root.has("stages") && root.get("stages").isJsonArray()) for (var raw : root.getAsJsonArray("stages")) {
            if (!raw.isJsonObject()) continue;
            var stage = raw.getAsJsonObject();
            stages.add(text(stage, "id"));
            if (stage.has("objectives") && stage.get("objectives").isJsonArray()) for (var item : stage.getAsJsonArray("objectives")) {
                if (!item.isJsonObject()) continue;
                var objective = item.getAsJsonObject();
                String objectiveId = text(stage, "id") + "." + text(objective, "id");
                objectives.add(objectiveId);
                if (flag(objective, "optional")) optional.add(objectiveId);
            }
            if (stage.has("followers") && stage.get("followers").isJsonArray()) for (var item : stage.getAsJsonArray("followers")) {
                if (!item.isJsonObject()) continue;
                var follower = item.getAsJsonObject();
                if (follower.has("actor") && follower.get("actor").isJsonObject()) actors.add(text(follower.getAsJsonObject("actor"), "alias"));
            }
        }
        collectFollowers(root, actors);
        measurements.forEach((id, measurement) -> {
            if (!measurement.stage().isEmpty() && !stages.contains(measurement.stage()))
                errors.add("Unknown performance stage for " + id + ": " + measurement.stage());
            if (!measurement.objective().isEmpty() && !objectives.contains(measurement.objective()))
                errors.add("Unknown performance objective for " + id + ": " + measurement.objective());
            if (measurement.metric() == Metric.OPTIONAL_OBJECTIVES && !measurement.objective().isEmpty()
                    && !optional.contains(measurement.objective()))
                errors.add("Performance objective must be optional: " + measurement.objective());
            if (measurement.health() && !actors.contains(measurement.actor()))
                errors.add("Unknown performance follower for " + id + ": " + measurement.actor());
        });
        return errors;
    }

    private static void collectFollowers(JsonElement value, Set<String> actors) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> collectFollowers(child, actors));
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("command") && object.has("actor") && object.get("actor").isJsonObject()) {
                var actor = object.getAsJsonObject("actor");
                if (actor.has("alias") && actor.get("alias").isJsonPrimitive()) actors.add(text(actor, "alias"));
            }
            object.entrySet().forEach(entry -> collectFollowers(entry.getValue(), actors));
        }
    }

    private static String text(JsonObject value, String key) {
        if (!value.has(key)) return "";
        var raw = value.get(key);
        if (raw.isJsonPrimitive()) return raw.getAsString();
        if (raw.isJsonObject() && raw.getAsJsonObject().has("key")
                && raw.getAsJsonObject().get("key").isJsonPrimitive()) return raw.getAsJsonObject().get("key").getAsString();
        return "";
    }
    private static boolean flag(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isBoolean() && value.get(key).getAsBoolean();
    }
    private static Double number(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsDouble() : null; }
    private static Integer integer(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsInt() : null; }
    private static ResourceLocation resource(JsonObject value, String key) {
        return value.has(key) ? ResourceLocation.parse(text(value, key)) : null;
    }
    private static Set<ResourceLocation> ids(JsonObject value, String key) {
        var result = new java.util.LinkedHashSet<ResourceLocation>();
        if (value.has(key)) for (var item : value.getAsJsonArray(key)) result.add(ResourceLocation.parse(item.getAsString()));
        return Set.copyOf(result);
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["measurements","tiers"],"properties":{
                  "measurements":{"type":"object","minProperties":1,"propertyNames":{"pattern":"^[a-z][a-z0-9_.-]{0,63}$"},
                    "additionalProperties":{"type":"object","additionalProperties":false,"required":["metric"],"properties":{
                      "metric":{"enum":["elapsed_ticks","final_health_percent","lowest_health_percent","deaths","damage_taken","kills","optional_objectives"]},
                      "stage":{"type":"string","minLength":1},"objective":{"type":"string","minLength":1},
                      "actor":{"type":"string","minLength":1},"include_inactive":{"type":"boolean"},"include_offline":{"type":"boolean"},
                      "entity_types":{"type":"array","items":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$"}},
                      "entity_tags":{"type":"array","items":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$"}}}}},
                  "tiers":{"type":"array","minItems":1,"items":{"type":"object","additionalProperties":false,
                    "required":["id","requirements","rewards"],"properties":{
                      "id":{"type":"string","pattern":"^[a-z][a-z0-9_.-]{0,63}$"},
                      "title":{"oneOf":[{"type":"string"},{"type":"object","additionalProperties":false,"required":["key"],
                        "properties":{"key":{"type":"string","minLength":1}}}]},"title_key":{"type":"string"},
                      "requirements":{"type":"object","minProperties":1,"additionalProperties":{
                        "type":"object","additionalProperties":false,"anyOf":[{"required":["min"]},{"required":["max"]}],
                        "properties":{"min":{"type":"number","minimum":0},"max":{"type":"number","minimum":0}}}},
                      "rewards":{"type":"object","additionalProperties":false,"properties":{
                        "experience":{"type":"integer","minimum":0,"maximum":2147483647},
                        "reputation":{"type":"integer","minimum":-2147483648,"maximum":2147483647},
                        "gossip_reputation":{"type":"integer","minimum":-2147483648,"maximum":2147483647},
                        "loot_table":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$"},
                        "memory_event":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$"},
                        "memory_scope":{"enum":["villager","village","both"]}}}}}}}}
                """).getAsJsonObject();
    }
}
