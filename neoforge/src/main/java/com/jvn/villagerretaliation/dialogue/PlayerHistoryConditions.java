package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.progress.PlayerHistorySavedData;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

public final class PlayerHistoryConditions {
    private PlayerHistoryConditions() { }

    public enum Kind {
        PLAYER_DIMENSION_VISITED("player_dimension_visited", "dimensions"),
        WORLD_DIMENSION_UNLOCKED("world_dimension_unlocked", "dimensions"),
        WORLD_ADVANCEMENT("world_advancement", "advancements"),
        PLAYER_KILLED_ENTITY("player_killed_entity", "entities"),
        PLAYER_KILLED_BY_ENTITY("player_killed_by_entity", "entities"),
        PLAYER_DIED_FROM("player_died_from", "damage_types"),
        PLAYER_LAST_DEATH("player_last_death", "damage_types"),
        PLAYER_BIOME_VISITED("player_biome_visited", "biomes"),
        PLAYER_CRAFTED_ITEM("player_crafted_item", "items"),
        PLAYER_USED_ITEM("player_used_item", "items"),
        PLAYER_DEATHS("player_deaths", ""),
        PLAYER_KILLS("player_kills", "");

        private final String id;
        private final String field;
        Kind(String id, String field) { this.id = id; this.field = field; }
        public String id() { return id; }
        public String field() { return field; }
        public boolean world() { return this == WORLD_DIMENSION_UNLOCKED || this == WORLD_ADVANCEMENT; }
        public boolean count() { return this == PLAYER_DEATHS || this == PLAYER_KILLS; }
    }

    public sealed interface Check extends DialogueCondition permits
            PlayerDimensionVisited,
            WorldDimensionUnlocked,
            WorldAdvancement,
            PlayerKilledEntity,
            PlayerKilledByEntity,
            PlayerDiedFrom,
            PlayerLastDeath,
            PlayerBiomeVisited,
            PlayerCraftedItem,
            PlayerUsedItem,
            PlayerDeaths,
            PlayerKills {
        Predicate reference();
        @Override default boolean matches(DialogueContext context) {
            return reference().evaluate(context.level(), context.player()) == ConditionOutcome.MATCHED;
        }
        @Override default int specificityScore() { return 6; }
        @Override default String unavailableReason(DialogueContext context) {
            return reference().reason(context == null ? null : context.level(), context == null ? null : context.player());
        }
    }

    public record PlayerDimensionVisited(Predicate reference) implements Check { }
    public record WorldDimensionUnlocked(Predicate reference) implements Check { }
    public record WorldAdvancement(Predicate reference) implements Check { }
    public record PlayerKilledEntity(Predicate reference) implements Check { }
    public record PlayerKilledByEntity(Predicate reference) implements Check { }
    public record PlayerDiedFrom(Predicate reference) implements Check { }
    public record PlayerLastDeath(Predicate reference) implements Check { }
    public record PlayerBiomeVisited(Predicate reference) implements Check { }
    public record PlayerCraftedItem(Predicate reference) implements Check { }
    public record PlayerUsedItem(Predicate reference) implements Check { }
    public record PlayerDeaths(Predicate reference) implements Check { }
    public record PlayerKills(Predicate reference) implements Check { }

    public record Predicate(Kind kind, Set<ResourceLocation> ids, Set<ResourceLocation> killers,
            boolean all, int min, Integer max) {
        public Predicate {
            ids = Set.copyOf(ids);
            killers = Set.copyOf(killers);
        }

        public ConditionOutcome evaluate(ServerLevel level, ServerPlayer player) {
            if (level == null || !kind.world() && player == null) return ConditionOutcome.UNKNOWN;
            if (kind.count()) {
                int value = player.getStats().getValue(Stats.CUSTOM.get(kind == Kind.PLAYER_DEATHS ? Stats.DEATHS : Stats.PLAYER_KILLS));
                return outcome(value >= min && (max == null || value <= max));
            }
            var data = PlayerHistorySavedData.get(level);
            if (kind == Kind.PLAYER_LAST_DEATH) {
                var death = data.latestDeath(player.getUUID());
                return outcome(death != null && (ids.isEmpty() || ids.contains(death.damageType()))
                        && (killers.isEmpty() || death.killerType() != null && killers.contains(death.killerType())));
            }
            Set<ResourceLocation> recorded = switch (kind) {
                case PLAYER_DIMENSION_VISITED -> data.dimensions(player.getUUID());
                case WORLD_DIMENSION_UNLOCKED -> data.worldDimensions();
                case WORLD_ADVANCEMENT -> data.worldAdvancements();
                case PLAYER_DIED_FROM -> data.deathCauses(player.getUUID());
                case PLAYER_BIOME_VISITED -> data.biomes(player.getUUID());
                default -> null;
            };
            boolean unknown = false;
            for (var id : ids) {
                ConditionOutcome result = recorded == null ? statistic(player, id) : outcome(recorded.contains(id));
                if (all && result == ConditionOutcome.UNMET || !all && result == ConditionOutcome.MATCHED) return result;
                unknown |= result == ConditionOutcome.UNKNOWN;
            }
            return unknown ? ConditionOutcome.UNKNOWN : outcome(all);
        }

        private ConditionOutcome statistic(ServerPlayer player, ResourceLocation id) {
            if (kind == Kind.PLAYER_KILLED_ENTITY || kind == Kind.PLAYER_KILLED_BY_ENTITY) {
                var entity = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                if (entity == null) return ConditionOutcome.UNKNOWN;
                return outcome(player.getStats().getValue((kind == Kind.PLAYER_KILLED_ENTITY ? Stats.ENTITY_KILLED : Stats.ENTITY_KILLED_BY).get(entity)) > 0);
            }
            var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) return ConditionOutcome.UNKNOWN;
            return outcome(player.getStats().getValue((kind == Kind.PLAYER_CRAFTED_ITEM ? Stats.ITEM_CRAFTED : Stats.ITEM_USED).get(item)) > 0);
        }

        public String reason(ServerLevel level, ServerPlayer player) {
            if (level == null) return "The world is unavailable.";
            if (!kind.world() && player == null) return "The player is unavailable.";
            return evaluate(level, player) == ConditionOutcome.UNKNOWN ? "A statistic target is unavailable in this world." : "";
        }
    }

    private static ConditionOutcome outcome(boolean value) { return value ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET; }

    public static Optional<DialogueCondition> read(ResourceLocation source, String pointer, JsonObject value, Kind kind) {
        var issues = QuestJsonSchema.validate(value, schema(kind));
        if (!issues.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, issues.toString());
            return Optional.empty();
        }
        int min = value.has("min") ? value.get("min").getAsInt() : 1;
        Integer max = value.has("max") ? value.get("max").getAsInt() : null;
        if (kind.count() && max != null && min > max) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, "minimum count exceeds maximum count");
            return Optional.empty();
        }
        var predicate = new Predicate(kind, ids(value, kind.field()),
                kind == Kind.PLAYER_LAST_DEATH ? ids(value, "entities") : Set.of(),
                !value.has("all") || value.get("all").getAsBoolean(), min, max);
        return Optional.of(switch (kind) {
            case PLAYER_DIMENSION_VISITED -> new PlayerDimensionVisited(predicate);
            case WORLD_DIMENSION_UNLOCKED -> new WorldDimensionUnlocked(predicate);
            case WORLD_ADVANCEMENT -> new WorldAdvancement(predicate);
            case PLAYER_KILLED_ENTITY -> new PlayerKilledEntity(predicate);
            case PLAYER_KILLED_BY_ENTITY -> new PlayerKilledByEntity(predicate);
            case PLAYER_DIED_FROM -> new PlayerDiedFrom(predicate);
            case PLAYER_LAST_DEATH -> new PlayerLastDeath(predicate);
            case PLAYER_BIOME_VISITED -> new PlayerBiomeVisited(predicate);
            case PLAYER_CRAFTED_ITEM -> new PlayerCraftedItem(predicate);
            case PLAYER_USED_ITEM -> new PlayerUsedItem(predicate);
            case PLAYER_DEATHS -> new PlayerDeaths(predicate);
            case PLAYER_KILLS -> new PlayerKills(predicate);
        });
    }

    private static Set<ResourceLocation> ids(JsonObject value, String field) {
        var result = new LinkedHashSet<ResourceLocation>();
        if (value.has(field)) for (var id : value.getAsJsonArray(field)) result.add(ResourceLocation.parse(id.getAsString()));
        return Set.copyOf(result);
    }

    public static JsonObject schema(Kind kind) {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        var properties = new JsonObject();
        var type = new JsonObject();
        type.addProperty("const", kind.id());
        properties.add("type", type);
        var required = new JsonArray();
        required.add("type");
        if (kind.count()) {
            var min = countField();
            min.addProperty("default", 1);
            properties.add("min", min);
            properties.add("max", countField());
        } else {
            properties.add(kind.field(), resourceList(kind.field()));
            if (kind == Kind.PLAYER_LAST_DEATH) {
                properties.add("entities", resourceList("entities"));
                var alternatives = new JsonArray();
                for (String field : new String[] {"damage_types", "entities"}) {
                    var branch = new JsonObject();
                    var names = new JsonArray();
                    names.add(field);
                    branch.add("required", names);
                    alternatives.add(branch);
                }
                schema.add("anyOf", alternatives);
            } else {
                required.add(kind.field());
                var all = new JsonObject();
                all.addProperty("type", "boolean");
                all.addProperty("default", true);
                all.addProperty("description", "Match all selected entries. Disable to match any selected entry.");
                properties.add("all", all);
            }
        }
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    public static JsonObject advancementSchema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", true);
        var properties = new JsonObject();
        var ids = resourceList("advancements");
        ids.getAsJsonObject("items").remove("pattern");
        properties.add("advancements", ids);
        properties.add("ids", ids.deepCopy());
        var single = new JsonObject();
        single.addProperty("type", "string");
        single.addProperty("default", "minecraft:story/mine_stone");
        properties.add("advancement", single);
        properties.add("id", single.deepCopy());
        var all = new JsonObject();
        all.addProperty("type", "boolean");
        all.addProperty("default", true);
        properties.add("all", all);
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject countField() {
        var field = new JsonObject();
        field.addProperty("type", "integer");
        field.addProperty("minimum", 0);
        field.addProperty("maximum", Integer.MAX_VALUE);
        return field;
    }

    private static JsonObject resourceList(String field) {
        var list = new JsonObject();
        list.addProperty("type", "array");
        list.addProperty("minItems", 1);
        var item = new JsonObject();
        item.addProperty("type", "string");
        item.addProperty("pattern", "^[a-z0-9_.-]+:[a-z0-9_./-]+$");
        item.addProperty("default", switch (field) {
            case "dimensions" -> "minecraft:the_nether";
            case "advancements" -> "minecraft:story/mine_stone";
            case "biomes" -> "minecraft:plains";
            case "damage_types" -> "minecraft:fall";
            case "items" -> "minecraft:crafting_table";
            default -> "minecraft:zombie";
        });
        list.add("items", item);
        return list;
    }
}
