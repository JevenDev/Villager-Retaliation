package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

public record PlayerStatCondition(String category, ResourceLocation target, int min, Integer max) implements DialogueCondition {
    @Override public boolean matches(DialogueContext context) { return evaluate(context.player()) == ConditionOutcome.MATCHED; }
    @Override public int specificityScore() { return 6; }
    @Override public String unavailableReason(DialogueContext context) { return reason(context == null ? null : context.player()); }

    public ConditionOutcome evaluate(ServerPlayer player) {
        if (player == null) return ConditionOutcome.UNKNOWN;
        Integer value = switch (category) {
            case "mined" -> BuiltInRegistries.BLOCK.getOptional(target)
                    .map(block -> player.getStats().getValue(Stats.BLOCK_MINED.get(block))).orElse(null);
            case "killed", "killed_by" -> BuiltInRegistries.ENTITY_TYPE.getOptional(target)
                    .map(entity -> player.getStats().getValue((category.equals("killed") ? Stats.ENTITY_KILLED : Stats.ENTITY_KILLED_BY).get(entity))).orElse(null);
            case "custom" -> BuiltInRegistries.CUSTOM_STAT.getOptional(target)
                    .map(stat -> player.getStats().getValue(Stats.CUSTOM.get(stat))).orElse(null);
            default -> BuiltInRegistries.ITEM.getOptional(target).map(item -> player.getStats().getValue(switch (category) {
                case "crafted" -> Stats.ITEM_CRAFTED.get(item);
                case "used" -> Stats.ITEM_USED.get(item);
                case "broken" -> Stats.ITEM_BROKEN.get(item);
                case "picked_up" -> Stats.ITEM_PICKED_UP.get(item);
                case "dropped" -> Stats.ITEM_DROPPED.get(item);
                default -> throw new IllegalStateException("Unknown statistic category: " + category);
            })).orElse(null);
        };
        return value == null ? ConditionOutcome.UNKNOWN
                : value >= min && (max == null || value <= max) ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
    }

    public String reason(ServerPlayer player) {
        return player == null ? "The player is unavailable."
                : evaluate(player) == ConditionOutcome.UNKNOWN ? "The statistic target is unavailable in this world." : "";
    }

    public static Optional<DialogueCondition> read(ResourceLocation source, String pointer, JsonObject value) {
        var issues = QuestJsonSchema.validate(value, schema());
        if (!issues.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, issues.toString());
            return Optional.empty();
        }
        int min = value.has("min") ? value.get("min").getAsInt() : 1;
        Integer max = value.has("max") ? value.get("max").getAsInt() : null;
        if (max != null && min > max) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, "minimum statistic exceeds maximum statistic");
            return Optional.empty();
        }
        return Optional.of(new PlayerStatCondition(value.get("category").getAsString(),
                ResourceLocation.parse(value.get("target").getAsString()), min, max));
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["type","category","target"],
                 "properties":{"type":{"const":"player_stat"},
                   "category":{"enum":["mined","crafted","used","broken","picked_up","dropped","killed","killed_by","custom"]},
                   "target":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$",
                     "description":"Registered block, item, entity or custom statistic. Values use vanilla units: distance in centimetres, time in ticks, damage in tenths of a health point. Workstation statistics count interactions."},
                   "min":{"type":"integer","minimum":0,"maximum":2147483647,"default":1},
                   "max":{"type":"integer","minimum":0,"maximum":2147483647}}}
                """).getAsJsonObject();
    }
}
