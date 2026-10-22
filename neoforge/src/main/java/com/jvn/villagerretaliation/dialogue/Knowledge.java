package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class Knowledge {
    private Knowledge() { }

    public static JsonObject actionSchema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["type","topic","recipient","operation"],"properties":{
                  "type":{"const":"knowledge"},"topic":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$","maxLength":256},
                  "recipient":{"enum":["speaker","player"]},"operation":{"enum":["witness","share","hint","forget"]},
                  "required":{"type":"boolean"}}}
                """).getAsJsonObject();
    }

    public static JsonObject conditionSchema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["type","topic","subject"],"properties":{
                  "type":{"const":"knowledge"},"topic":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$","maxLength":256},
                  "subject":{"enum":["speaker","player"]},"state":{"enum":["known","complete","witnessed","heard","partial"]},
                  "source":{"enum":["speaker","player"]}}}
                """).getAsJsonObject();
    }

    public static Optional<DialogueCondition> read(ResourceLocation source, String pointer, JsonObject value) {
        if (!QuestJsonSchema.validate(value, conditionSchema()).isEmpty()) {
            com.jvn.villagerretaliation.util.DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, "invalid knowledge condition");
            return Optional.empty();
        }
        return Optional.of(new Condition(ResourceLocation.parse(value.get("topic").getAsString()), value.get("subject").getAsString(),
                value.has("state") ? value.get("state").getAsString() : "known", value.has("source") ? value.get("source").getAsString() : ""));
    }

    public record Condition(ResourceLocation topic, String subject, String state, String source) implements DialogueCondition {
        @Override public int specificityScore() { return 8; }
        @Override public boolean matches(DialogueContext context) {
            return context != null && evaluate(context.level(), context.player(), context.providerId()) == ConditionOutcome.MATCHED;
        }
        public ConditionOutcome evaluate(ServerLevel level, ServerPlayer player, UUID speaker) {
            UUID owner = actor(subject, player, speaker);
            UUID from = source.isBlank() ? null : actor(source, player, speaker);
            if (level == null || owner == null || !source.isBlank() && from == null) return ConditionOutcome.UNKNOWN;
            var data = KnowledgeSavedData.get(level);
            if (!data.available()) return ConditionOutcome.UNKNOWN;
            var known = data.knowledge(owner, topic);
            boolean matches = known.filter(entry -> from == null || from.equals(entry.source()))
                    .filter(entry -> state.equals("known") || state.equals("complete") && !entry.kind().equals("partial")
                            || state.equals(entry.kind())).isPresent();
            return matches ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
        }
    }

    private static UUID actor(String subject, ServerPlayer player, UUID speaker) {
        return subject.equals("speaker") ? speaker : player == null ? null : player.getUUID();
    }

    public static ActionResult execute(DialogueRuntimeContext context, VillagerActionDefinition action) {
        if (context == null || context.level() == null || context.player() == null) return unavailable("Knowledge context is unavailable.");
        UUID recipient = actor(action.factKey(), context.player(), context.providerId());
        if (recipient == null) return unavailable("The knowledge recipient is unavailable.");
        var data = KnowledgeSavedData.get(context.level());
        if (!data.available()) return unavailable("Knowledge save data requires repair.");
        var topic = action.factTag();
        long time = context.level().getGameTime();
        boolean changed;
        switch (action.factValue()) {
            case "witness" -> changed = data.witness(recipient, topic, time);
            case "forget" -> changed = data.forget(recipient, topic);
            case "share", "hint" -> {
                UUID source = action.factKey().equals("player") ? context.providerId() : context.player().getUUID();
                if (source == null || data.knowledge(source, topic).isEmpty()) return unavailable("The source does not know this topic.");
                changed = data.share(source, recipient, topic, action.factValue().equals("hint"), time);
            }
            default -> { return unavailable("Unknown knowledge operation."); }
        }
        return changed ? ActionResult.success(VillagerActionResult.success(), Set.of())
                : ActionResult.alreadySatisfied("knowledge already applied", Set.of(), VillagerActionResult.EMPTY);
    }

    private static ActionResult unavailable(String reason) {
        return ActionResult.unavailable(reason, Set.of(), VillagerActionResult.EMPTY);
    }
}
