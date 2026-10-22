package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record QuestSiteCondition(ResourceLocation quest, String state, String route, String approach) implements DialogueCondition {
    @Override public int specificityScore() { return 8; }
    @Override public boolean matches(DialogueContext context) {
        return context != null && evaluate(context.level(), context.player()) == ConditionOutcome.MATCHED;
    }

    public ConditionOutcome evaluate(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) return ConditionOutcome.UNKNOWN;
        return com.jvn.villagerretaliation.quest.site.QuestSiteService.matches(level, player, this)
                ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
    }

    public static Optional<DialogueCondition> read(ResourceLocation source, String pointer, JsonObject value) {
        if (!QuestJsonSchema.validate(value, schema()).isEmpty()) {
            com.jvn.villagerretaliation.util.DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, "invalid quest site condition");
            return Optional.empty();
        }
        return Optional.of(new QuestSiteCondition(ResourceLocation.parse(value.get("quest").getAsString()), value.get("state").getAsString(),
                value.has("route") ? value.get("route").getAsString() : "", value.has("approach") ? value.get("approach").getAsString() : ""));
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["type","quest","state"],"properties":{
                  "type":{"const":"quest_site"},"quest":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$","maxLength":256},
                  "state":{"enum":["discovered","guards_defeated","released","rescued","route_exists","route_known","route_reached","approach_used"]},
                  "route":{"type":"string","pattern":"^[a-z0-9_.-]+$","maxLength":64},
                  "approach":{"type":"string","pattern":"^[a-z0-9_.-]+$","maxLength":64}},
                 "allOf":[
                  {"if":{"properties":{"state":{"enum":["route_exists","route_known","route_reached"]}}},"then":{"required":["route"]},"else":{"not":{"required":["route"]}}},
                  {"if":{"properties":{"state":{"const":"approach_used"}}},"then":{"required":["approach"]},"else":{"not":{"required":["approach"]}}}]}
                """).getAsJsonObject();
    }
}
