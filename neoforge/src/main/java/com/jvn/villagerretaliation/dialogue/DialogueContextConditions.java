package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.*;
import com.jvn.villagerretaliation.party.PartyCommandMode;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class DialogueContextConditions {
    private DialogueContextConditions() { }

    public static JsonObject schema(String type) {
        String fields = switch (type) {
            case "context" -> """
                    {"id":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$","maxLength":256}}
                    """;
            case "health" -> """
                    {"subject":{"enum":["player","speaker"]},
                     "min":{"type":"number","minimum":0,"maximum":1},
                     "max":{"type":"number","minimum":0,"maximum":1}}
                    """;
            case "physical_state" -> """
                    {"subject":{"enum":["player","speaker"]},"state":{"enum":["burning","submerged","riding"]}}
                    """;
            case "companion_state" -> """
                    {"state":{"enum":["active","following","waiting"]}}
                    """;
            default -> throw new IllegalArgumentException("Unknown context condition: " + type);
        };
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        var properties = JsonParser.parseString(fields).getAsJsonObject();
        var discriminator = new JsonObject();
        discriminator.addProperty("const", type);
        properties.add("type", discriminator);
        schema.add("properties", properties);
        var required = new com.google.gson.JsonArray();
        required.add("type");
        if (type.equals("context")) required.add("id");
        if (type.equals("health") || type.equals("physical_state")) required.add("subject");
        if (type.equals("physical_state") || type.equals("companion_state")) required.add("state");
        schema.add("required", required);
        return schema;
    }

    public static Optional<DialogueCondition> read(ResourceLocation source, String pointer, JsonObject value, String type) {
        var issues = QuestJsonSchema.validate(value, schema(type));
        if (!issues.isEmpty()) {
            com.jvn.villagerretaliation.util.DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer, issues.toString());
            return Optional.empty();
        }
        return switch (type) {
            case "context" -> Optional.of(new ContextRule(ResourceLocation.parse(value.get("id").getAsString())));
            case "health" -> {
                double min = value.has("min") ? value.get("min").getAsDouble() : 0;
                double max = value.has("max") ? value.get("max").getAsDouble() : 1;
                if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
                    com.jvn.villagerretaliation.util.DatapackDiagnostics.warnInvalidDialogueCondition(source, pointer,
                            "health requires finite bounds with min <= max");
                    yield Optional.empty();
                }
                yield Optional.of(new Health(value.get("subject").getAsString(), min, max));
            }
            case "physical_state" -> Optional.of(new PhysicalState(value.get("subject").getAsString(), value.get("state").getAsString()));
            case "companion_state" -> Optional.of(new CompanionState(value.get("state").getAsString()));
            default -> Optional.empty();
        };
    }

    public static boolean health(LivingEntity entity, Health condition) {
        double fraction = entity.getHealth() / entity.getMaxHealth();
        return fraction >= condition.min() && fraction <= condition.max();
    }

    public static boolean physicalState(LivingEntity entity, PhysicalState condition) {
        return switch (condition.state()) {
            case "burning" -> entity.isOnFire();
            case "submerged" -> entity.isUnderWater();
            case "riding" -> entity.isPassenger();
            default -> false;
        };
    }

    public static boolean companion(DialogueContext context, CompanionState condition) {
        var villager = context.villager();
        if (!PartyService.arePlayerAndVillagerInSameParty(context.level(), context.player().getUUID(), villager.getUUID())
                || !PartyVillagerContractService.isActivePartyVillager(context.level(), villager)) return false;
        var party = PartyService.getPartyForVillager(context.level(), villager.getUUID()).orElse(null);
        var record = party == null ? null : party.villager(villager.getUUID());
        return record != null && switch (condition.state()) {
            case "active" -> true;
            case "following" -> record.commandMode() == PartyCommandMode.FOLLOW;
            case "waiting" -> record.commandMode() == PartyCommandMode.STAY;
            default -> false;
        };
    }
}
