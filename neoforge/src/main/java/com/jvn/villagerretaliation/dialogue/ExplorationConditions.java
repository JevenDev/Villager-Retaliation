package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.progress.EventHistorySavedData;
import com.jvn.villagerretaliation.progress.ExplorationHistorySavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ExplorationConditions {
    private ExplorationConditions() { }

    public enum Kind {
        STRUCTURE("player_structure_visited"), SETTLEMENT("player_settlement_visited"),
        DESTINATIONS("player_destination_count"), POSSESSION("player_item_possessed");
        private final String id;
        Kind(String id) { this.id = id; }
        public String id() { return id; }
    }

    public sealed interface Check extends DialogueCondition permits StructureVisited, SettlementVisited, DestinationCount, ItemPossessed {
        Predicate reference();
        @Override default boolean matches(DialogueContext context) {
            return reference().evaluate(context.level(), context.player(), context, null) == ConditionOutcome.MATCHED;
        }
        @Override default int specificityScore() { return 6; }
        @Override default String unavailableReason(DialogueContext context) {
            return context == null || reference().evaluate(context.level(), context.player(), context, null) == ConditionOutcome.UNKNOWN
                    ? "The player, settlement identity or selected registry resource is unavailable." : "";
        }
    }
    public record StructureVisited(Predicate reference) implements Check { }
    public record SettlementVisited(Predicate reference) implements Check { }
    public record DestinationCount(Predicate reference) implements Check { }
    public record ItemPossessed(Predicate reference) implements Check { }

    public record Predicate(Kind kind, Set<ResourceLocation> ids, boolean all, ResourceLocation dimension,
                            String settlement, UUID settlementId, String destination, long min, Long max,
                            String since, Long minAge, Long maxAge) {
        public Predicate { ids = Set.copyOf(ids); }

        public ConditionOutcome evaluate(ServerLevel level, ServerPlayer player, DialogueContext live,
                                         VillagerQuestSavedData.QuestProgress progress) {
            if (level == null || player == null) return ConditionOutcome.UNKNOWN;
            var data = ExplorationHistorySavedData.get(level);
            if (kind == Kind.SETTLEMENT) {
                UUID selected = settlementId;
                if (settlement.equals("current")) {
                    var town = ProgressConditions.settlement(level, player, live, progress);
                    if (town == null) return ConditionOutcome.UNKNOWN;
                    selected = town.value();
                }
                var summary = EventHistorySavedData.Summary.EMPTY;
                for (var entry : data.settlements(player.getUUID()).entrySet()) {
                    var key = entry.getKey();
                    if (dimension != null && !dimension.equals(key.dimension()) || selected != null
                            && !ExplorationHistorySavedData.canonical(level, selected).equals(ExplorationHistorySavedData.canonical(level, key.settlement()))) continue;
                    summary = summary.merge(entry.getValue());
                }
                return outcome(matches(summary, level));
            }
            if (kind == Kind.DESTINATIONS) {
                if (!destination.equals("settlements")) for (var id : ids)
                    if (!level.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(id)) return ConditionOutcome.UNKNOWN;
                if (destination.equals("settlements")) {
                    long count = data.settlements(player.getUUID()).keySet().stream()
                            .filter(key -> dimension == null || dimension.equals(key.dimension()))
                            .map(key -> ExplorationHistorySavedData.canonical(level, key.settlement())).distinct().count();
                    return outcome(inRange(count));
                }
                var selected = data.structures(player.getUUID()).keySet().stream()
                        .filter(key -> (dimension == null || dimension.equals(key.dimension())) && (ids.isEmpty() || ids.contains(key.structure())));
                long count = destination.equals("structure_types") ? selected.map(ExplorationHistorySavedData.StructureDestination::structure).distinct().count()
                        : selected.count();
                return outcome(inRange(count));
            }
            boolean unknown = false;
            for (var id : ids) {
                ConditionOutcome result;
                if (kind == Kind.POSSESSION) {
                    result = BuiltInRegistries.ITEM.containsKey(id) ? outcome(data.possessed(player.getUUID()).contains(id)) : ConditionOutcome.UNKNOWN;
                } else if (!level.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(id)) {
                    result = ConditionOutcome.UNKNOWN;
                } else {
                    var summary = EventHistorySavedData.Summary.EMPTY;
                    for (var entry : data.structures(player.getUUID()).entrySet()) {
                        var key = entry.getKey();
                        if (key.structure().equals(id) && (dimension == null || dimension.equals(key.dimension()))) summary = summary.merge(entry.getValue());
                    }
                    result = outcome(matches(summary, level));
                }
                if (all && result == ConditionOutcome.UNMET || !all && result == ConditionOutcome.MATCHED) return result;
                unknown |= result == ConditionOutcome.UNKNOWN;
            }
            return unknown ? ConditionOutcome.UNKNOWN : outcome(all);
        }

        private boolean inRange(long value) { return value >= min && (max == null || value <= max); }
        private boolean matches(EventHistorySavedData.Summary summary, ServerLevel level) {
            if (!inRange(summary.count())) return false;
            if (minAge == null && maxAge == null) return true;
            long occurred = since.equals("first") ? summary.first() : summary.last();
            if (occurred < 0) return false;
            long age = Math.max(0, level.getServer().overworld().getGameTime() - occurred);
            return (minAge == null || age >= minAge) && (maxAge == null || age <= maxAge);
        }
    }

    private static ConditionOutcome outcome(boolean matched) { return matched ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET; }

    public static Optional<DialogueCondition> read(ResourceLocation source, String pointer, JsonObject value, Kind kind) {
        var issues = QuestJsonSchema.validate(value, schema(kind));
        if (!issues.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, issues.toString());
            return Optional.empty();
        }
        long min = value.has("min") ? value.get("min").getAsLong() : 1;
        Long max = number(value, "max"), minAge = number(value, "min_age_ticks"), maxAge = number(value, "max_age_ticks");
        String settlement = text(value, "settlement", "current"), destination = text(value, "destination", "structures");
        if (max != null && min > max || minAge != null && maxAge != null && minAge > maxAge
                || value.has("since") && minAge == null && maxAge == null
                || kind == Kind.SETTLEMENT && (settlement.equals("id") != value.has("id"))
                || kind == Kind.DESTINATIONS && destination.equals("settlements") && value.has("structures")) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, "invalid visit bounds, timing or destination selector");
            return Optional.empty();
        }
        var ids = new HashSet<ResourceLocation>();
        String field = kind == Kind.POSSESSION ? "items" : "structures";
        if (value.has(field)) for (var id : value.getAsJsonArray(field)) ids.add(ResourceLocation.parse(id.getAsString()));
        var reference = new Predicate(kind, ids, !value.has("all") || value.get("all").getAsBoolean(),
                value.has("dimension") ? ResourceLocation.parse(value.get("dimension").getAsString()) : null,
                settlement, value.has("id") ? UUID.fromString(value.get("id").getAsString()) : null, destination,
                min, max, text(value, "since", "last"), minAge, maxAge);
        return Optional.of(switch (kind) {
            case STRUCTURE -> new StructureVisited(reference);
            case SETTLEMENT -> new SettlementVisited(reference);
            case DESTINATIONS -> new DestinationCount(reference);
            case POSSESSION -> new ItemPossessed(reference);
        });
    }

    private static Long number(JsonObject value, String field) { return value.has(field) ? value.get(field).getAsLong() : null; }
    private static String text(JsonObject value, String field, String fallback) { return value.has(field) ? value.get(field).getAsString() : fallback; }

    public static JsonObject schema(Kind kind) {
        var schema = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["type"],"properties":{}}
                """).getAsJsonObject();
        var fields = schema.getAsJsonObject("properties");
        var type = new JsonObject(); type.addProperty("const", kind.id()); fields.add("type", type);
        var required = schema.getAsJsonArray("required");
        if (kind == Kind.STRUCTURE || kind == Kind.POSSESSION || kind == Kind.DESTINATIONS) {
            var list = new JsonObject(); list.addProperty("type", "array"); list.addProperty("minItems", 1); list.add("items", resource());
            String name = kind == Kind.POSSESSION ? "items" : "structures";
            list.getAsJsonObject("items").addProperty("default", kind == Kind.POSSESSION ? "minecraft:diamond" : "minecraft:monument");
            fields.add(name, list);
            if (kind != Kind.DESTINATIONS) {
                required.add(name);
                var all = new JsonObject(); all.addProperty("type", "boolean"); all.addProperty("default", true);
                fields.add("all", all);
            }
        }
        if (kind == Kind.SETTLEMENT) {
            fields.add("settlement", choices("current", "any", "id"));
            fields.getAsJsonObject("settlement").addProperty("description", "Current means the speaker's settlement, the saved quest issuer's settlement, or your current settlement for player-only checks. Any matches all recorded settlements. Use id to select a durable settlement UUID.");
            var id = new JsonObject(); id.addProperty("type", "string");
            id.addProperty("pattern", "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
            fields.add("id", id);
        }
        if (kind == Kind.DESTINATIONS) {
            fields.add("destination", choices("structures", "structure_types", "settlements")); required.add("destination");
            fields.getAsJsonObject("destination").addProperty("description", "Count distinct structure instances, registered structure types, or canonical settlements. Instances use dimension and start chunk. Settlement merges combine identities. Optional structures filters structure counts.");
        }
        if (kind != Kind.POSSESSION) {
            fields.add("dimension", resource());
            var count = JsonParser.parseString("""
                    {"type":"integer","minimum":0,"maximum":9007199254740991}
                    """).getAsJsonObject();
            fields.add("min", count.deepCopy()); fields.getAsJsonObject("min").addProperty("default", 1);
            fields.add("max", count.deepCopy());
            if (kind != Kind.DESTINATIONS) {
                fields.add("since", choices("last", "first"));
                fields.add("min_age_ticks", count.deepCopy()); fields.add("max_age_ticks", count.deepCopy());
                fields.getAsJsonObject("min").addProperty("description", "Completed entries into the selected destination. Staying inside or reconnecting inside does not add a visit. Age bounds measure server ticks, 20 per second.");
            }
        } else fields.getAsJsonObject("items").addProperty("description", "Item types observed in your inventory, equipment or cursor remain known after loss. Unopened containers and recipe previews do not establish possession.");
        return schema;
    }

    private static JsonObject choices(String... values) {
        var field = new JsonObject(); var options = new JsonArray();
        for (String value : values) options.add(value);
        field.add("enum", options); return field;
    }
    private static JsonObject resource() {
        var field = new JsonObject(); field.addProperty("type", "string");
        field.addProperty("pattern", "^[a-z0-9_.-]+:[a-z0-9_./-]+$"); return field;
    }
}
