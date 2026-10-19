package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.progress.EventHistorySavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.settlement.SettlementNeeds;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ProgressConditions {
    private ProgressConditions() { }

    public enum Kind {
        HISTORY("event_history"), TRADE("personal_trade"), NEED("settlement_need"), CONTRIBUTION("settlement_contribution");
        private final String id;
        Kind(String id) { this.id = id; }
        public String id() { return id; }
    }

    public sealed interface Check extends DialogueCondition permits History, Trade, Need, Contribution {
        Predicate reference();
        @Override default boolean matches(DialogueContext context) {
            return reference().evaluate(context.level(), context.player(), context, null) == ConditionOutcome.MATCHED;
        }
        @Override default int specificityScore() { return 6; }
        @Override default String unavailableReason(DialogueContext context) {
            return context == null || reference().evaluate(context.level(), context.player(), context, null) == ConditionOutcome.UNKNOWN
                    ? "The player, provider, settlement or required settlement observations are unavailable." : "";
        }
    }
    public record History(Predicate reference) implements Check { }
    public record Trade(Predicate reference) implements Check { }
    public record Need(Predicate reference) implements Check { }
    public record Contribution(Predicate reference) implements Check { }

    public record Predicate(Kind kind, ResourceLocation event, String scope, String target, boolean playerOnly,
                            String metric, long min, Long max, String since, Long minAge, Long maxAge) {
        public ConditionOutcome evaluate(ServerLevel level, ServerPlayer player, DialogueContext live,
                                         VillagerQuestSavedData.QuestProgress progress) {
            if (level == null || playerOnly && player == null) return ConditionOutcome.UNKNOWN;
            UUID provider = live != null ? live.providerId() : progress == null ? null : progress.startedVillagerId();
            boolean settlementScope = kind == Kind.NEED || kind == Kind.CONTRIBUTION || scope.equals("settlement");
            VillageAllegianceId settlement = settlementScope ? settlement(level, player, live, progress) : null;
            if (settlementScope && settlement == null || scope.equals("villager") && provider == null) return ConditionOutcome.UNKNOWN;
            if (kind == Kind.NEED) {
                var town = SettlementSavedData.get(level).get(settlement).orElse(null);
                if (town == null) return ConditionOutcome.UNKNOWN;
                var townLevel = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, town.dimension()));
                if (townLevel == null) return ConditionOutcome.UNKNOWN;
                Long value = SettlementNeeds.value(townLevel, town, metric);
                return value == null ? ConditionOutcome.UNKNOWN : outcome(inRange(value));
            }
            UUID subject = scope.equals("villager") ? provider : settlementScope ? settlement.value() : null;
            var summary = EventHistorySavedData.get(level).query(level, scope, subject,
                    playerOnly ? player.getUUID() : null, event, target);
            long value = metric.equals("count") || metric.equals("trades") ? summary.count() : summary.amount();
            if (!inRange(value)) return ConditionOutcome.UNMET;
            if (minAge != null || maxAge != null) {
                long occurred = since.equals("first") ? summary.first() : summary.last();
                if (occurred < 0) return ConditionOutcome.UNMET;
                long age = Math.max(0, level.getServer().overworld().getGameTime() - occurred);
                if (minAge != null && age < minAge || maxAge != null && age > maxAge) return ConditionOutcome.UNMET;
            }
            return ConditionOutcome.MATCHED;
        }

        private boolean inRange(long value) { return value >= min && (max == null || value <= max); }
    }

    private static VillageAllegianceId settlement(ServerLevel level, ServerPlayer player, DialogueContext live,
                                                  VillagerQuestSavedData.QuestProgress progress) {
        var registry = VillageAllegianceRegistrySavedData.get(level);
        if (live != null && live.villager() != null) {
            var allegiance = VillageAllegianceApi.canonicalPrimary(level, live.villager());
            if (allegiance.isPresent()) return allegiance.get();
        }
        String key = live != null ? live.villageKey() : progress == null ? "" : progress.issuerVillageKey();
        var dimension = VillageScopeKeys.dimension(key);
        var position = VillageScopeKeys.pos(key);
        if (dimension.isPresent() && position.isPresent()) {
            var origin = level.getServer().getLevel(dimension.get());
            return origin == null ? null : registry.peekAt(origin, position.get()).flatMap(registry::canonical).orElse(null);
        }
        if (progress != null && progress.startedVillagerId() != null) {
            var origin = progress.issuerDimension() == null ? null : level.getServer().getLevel(progress.issuerDimension());
            return origin == null || progress.issuerPos() == null ? null
                    : registry.peekAt(origin, progress.issuerPos()).flatMap(registry::canonical).orElse(null);
        }
        var origin = live != null && live.villager() != null ? live.villager() : player;
        return origin == null ? null : registry.peekAt((ServerLevel) origin.level(), origin.blockPosition()).flatMap(registry::canonical).orElse(null);
    }

    private static ConditionOutcome outcome(boolean matched) { return matched ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET; }

    public static Optional<DialogueCondition> read(ResourceLocation source, String pointer, JsonObject value, Kind kind) {
        var issues = QuestJsonSchema.validate(value, schema(kind));
        if (!issues.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, issues.toString());
            return Optional.empty();
        }
        long min = number(value, "min", 1);
        Long max = nullable(value, "max"), minAge = nullable(value, "min_age_ticks"), maxAge = nullable(value, "max_age_ticks");
        if (max != null && min > max || minAge != null && maxAge != null && minAge > maxAge
                || value.has("since") && minAge == null && maxAge == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, "invalid history range or timing without age bounds");
            return Optional.empty();
        }
        String metric = text(value, kind == Kind.NEED ? "need" : "metric", kind == Kind.TRADE ? "trades" : kind == Kind.NEED ? "food" : kind == Kind.CONTRIBUTION ? "items" : "count");
        String scope = kind == Kind.TRADE ? "villager" : kind == Kind.CONTRIBUTION || kind == Kind.NEED ? "settlement" : text(value, "scope", "player");
        String target = value.has("target") ? value.get("target").getAsString() : value.has("item") ? value.get("item").getAsString() : null;
        if (kind == Kind.TRADE && metric.equals("trades") && target != null
                || kind == Kind.CONTRIBUTION && target != null && !java.util.Set.of("items", "food", "equipment").contains(metric)) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, "item filters require an item quantity metric");
            return Optional.empty();
        }
        ResourceLocation event = kind == Kind.HISTORY ? ResourceLocation.parse(value.get("event").getAsString())
                : ResourceLocation.fromNamespaceAndPath("villagerretaliation", kind == Kind.TRADE ? switch (metric) {
                    case "trades" -> "trade_completed";
                    case "items_paid" -> "trade_item_paid";
                    case "items_received" -> "trade_item_received";
                    case "currency_paid" -> "trade_currency_paid";
                    case "currency_received" -> "trade_currency_received";
                    default -> throw new IllegalStateException("Unknown trade metric");
                } : "contribution_" + metric);
        boolean playerOnly = kind != Kind.HISTORY || scope.equals("player") || (value.has("player_only") ? value.get("player_only").getAsBoolean() : !scope.equals("world"));
        var predicate = new Predicate(kind, event, scope, target, playerOnly, metric, min, max,
                text(value, "since", "last"), minAge, maxAge);
        return Optional.of(switch (kind) {
            case HISTORY -> new History(predicate);
            case TRADE -> new Trade(predicate);
            case NEED -> new Need(predicate);
            case CONTRIBUTION -> new Contribution(predicate);
        });
    }

    private static String text(JsonObject value, String field, String fallback) { return value.has(field) ? value.get(field).getAsString() : fallback; }
    private static long number(JsonObject value, String field, long fallback) { return value.has(field) ? value.get(field).getAsLong() : fallback; }
    private static Long nullable(JsonObject value, String field) { return value.has(field) ? value.get(field).getAsLong() : null; }

    public static JsonObject schema(Kind kind) {
        var schema = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["type"],"properties":{
                  "min":{"type":"integer","minimum":0,"maximum":9007199254740991,"default":1},
                  "max":{"type":"integer","minimum":0,"maximum":9007199254740991}}}
                """).getAsJsonObject();
        var fields = schema.getAsJsonObject("properties");
        var type = new JsonObject(); type.addProperty("const", kind.id()); fields.add("type", type);
        if (kind == Kind.NEED) {
            fields.add("need", choices("food", "equipment", "materials", "production", "stock", "planting", "housing", "workers", "population", "housing_capacity"));
            schema.getAsJsonArray("required").add("need");
            fields.getAsJsonObject("need").addProperty("description", "Supply needs measure missing units after committed and incoming supplies. Food uses nutrition points, other supplies use their demand quantities. Housing counts residents without a usable assigned bed. Workers counts unfilled desired worksite positions. Unavailable observations remain unknown.");
        } else {
            fields.add("since", choices("last", "first"));
            var age = JsonParser.parseString("""
                    {"type":"integer","minimum":0,"maximum":9007199254740991,
                     "description":"Elapsed server game ticks, 20 per second. Bounds apply to the first or most recent occurrence, not a rolling event count."}
                    """).getAsJsonObject();
            fields.add("min_age_ticks", age); fields.add("max_age_ticks", age.deepCopy());
            if (kind == Kind.HISTORY) {
                fields.add("event", resource()); fields.add("target", resource());
                fields.add("scope", choices("player", "world", "villager", "settlement"));
                fields.add("metric", choices("count", "amount"));
                var playerOnly = new JsonObject();
                playerOnly.addProperty("type", "boolean");
                playerOnly.addProperty("description", "Only this player's events. Defaults to false for world scope, true otherwise.");
                fields.add("player_only", playerOnly);
                schema.getAsJsonArray("required").add("event");
            } else if (kind == Kind.TRADE) {
                fields.add("metric", choices("trades", "items_paid", "items_received", "currency_paid", "currency_received"));
                fields.add("item", resource());
            } else {
                fields.add("metric", choices("items", "food", "equipment", "currency", "defense", "construction", "storage"));
                fields.add("item", resource());
            }
        }
        return schema;
    }

    private static JsonObject choices(String... values) {
        var field = new JsonObject(); var options = new JsonArray();
        for (String value : values) options.add(value);
        field.add("enum", options); return field;
    }
    private static JsonObject resource() {
        var field = new JsonObject();
        field.addProperty("type", "string");
        field.addProperty("pattern", "^[a-z0-9_.-]+:[a-z0-9_./-]+$");
        return field;
    }
}
