package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressModel;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record QuestAftermathCondition(ResourceLocation quest, int minDays, Integer maxDays) implements DialogueCondition {
    @Override public int specificityScore() { return 8; }
    @Override public boolean matches(DialogueContext context) {
        return context != null && evaluate(context.level(), context.player(), context.providerId()) == ConditionOutcome.MATCHED;
    }

    public ConditionOutcome evaluate(ServerLevel level, ServerPlayer player, UUID provider) {
        if (level == null || player == null || provider == null) return ConditionOutcome.UNKNOWN;
        var progress = VillagerQuestSavedData.get(level).get(player.getUUID(), quest);
        return remembers(progress, provider, level.getGameTime()) ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
    }

    public boolean remembers(QuestProgressModel progress, UUID provider, long time) {
        if (progress == null || provider == null) return false;
        long completed = progress.completionRecords().stream().filter(entry -> provider.equals(entry.issuerId()))
                .mapToLong(entry -> entry.completedGameTime()).max().orElse(-1);
        if (!progress.hasCompletionHistory() && provider.equals(progress.startedVillagerId())) completed = progress.completedGameTime();
        if (completed < 0 || time < completed) return false;
        long days = (time - completed) / 24000L;
        return days >= minDays && (maxDays == null || days <= maxDays);
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["type","quest"],"properties":{
                  "type":{"const":"quest_aftermath"},"quest":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$","maxLength":256},
                  "min_days":{"type":"integer","minimum":0,"maximum":2147483647},
                  "max_days":{"type":"integer","minimum":0,"maximum":2147483647}}}
                """).getAsJsonObject();
    }

    public static Optional<DialogueCondition> read(ResourceLocation source, String pointer, JsonObject value) {
        if (!QuestJsonSchema.validate(value, schema()).isEmpty()) {
            com.jvn.villagerretaliation.util.DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, "invalid quest aftermath condition");
            return Optional.empty();
        }
        int min = value.has("min_days") ? value.get("min_days").getAsInt() : 0;
        Integer max = value.has("max_days") ? value.get("max_days").getAsInt() : null;
        if (max != null && max < min) {
            com.jvn.villagerretaliation.util.DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, "quest aftermath requires min_days <= max_days");
            return Optional.empty();
        }
        return Optional.of(new QuestAftermathCondition(ResourceLocation.parse(value.get("quest").getAsString()), min, max));
    }
}
