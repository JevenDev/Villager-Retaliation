package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

record SettlementEncounterDefinition(ResourceLocation id, JsonObject source, String type, double weight,
        double chance, int interval, int distance, int cooldown, int duration, int roundTicks,
        double roadRisk, double guardRisk, double recovery, double cargoLoss, int delay,
        int minimumParticipants, int maximumGuards, String route, String status,
        List<DialogueCondition> conditions, List<Modifier> modifiers, List<Attacker> attackers, List<JsonObject> outcomes,
        String leg, double minimumDevelopment, double maximumDevelopment) {
    record Attacker(ResourceLocation entity, int count, double health, double damage, double armor, JsonObject equipment) { }
    record Modifier(double factor, List<DialogueCondition> conditions) { }

    static SettlementEncounterDefinition parse(ResourceLocation id, JsonObject json) {
        keys(json, "type", "weight", "chance", "interval_ticks", "distance_blocks", "cooldown_ticks", "duration_ticks",
                "round_ticks", "developed_road_risk", "guard_risk", "recovery_chance", "cargo_loss", "delay_ticks",
                "minimum_participants", "maximum_guards", "route", "status", "conditions", "modifiers", "attackers", "outcomes",
                "leg", "minimum_development", "maximum_development");
        String type = string(json, "type", "");
        var behavior = SettlementEncounterBehaviors.get(type);
        var attackers = new java.util.ArrayList<Attacker>();
        if (json.has("attackers")) for (var raw : json.get("attackers").getAsJsonArray()) {
            var entry = raw.getAsJsonObject();
            keys(entry, "entity", "count", "health", "damage", "armor", "equipment");
            var entity = ResourceLocation.parse(string(entry, "entity", ""));
            if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(entity))
                throw new IllegalArgumentException("unknown attacker entity " + entity);
            var equipment = entry.has("equipment") ? entry.get("equipment").getAsJsonObject().deepCopy() : new JsonObject();
            for (String slot : equipment.keySet()) net.minecraft.world.entity.EquipmentSlot.byName(slot);
            attackers.add(new Attacker(entity, integer(entry, "count", 1, 1, 16),
                    number(entry, "health", 20, 1, 2048), number(entry, "damage", 3, 0, 128),
                    number(entry, "armor", 0, 0, 30), equipment));
        }
        if (behavior.hostile() == attackers.isEmpty() || attackers.stream().mapToInt(Attacker::count).sum() > 32)
            throw new IllegalArgumentException("attack requires 1..32 attackers and rest must have none");
        var modifiers = new java.util.ArrayList<Modifier>();
        if (json.has("modifiers")) for (var raw : json.get("modifiers").getAsJsonArray()) {
            var entry = raw.getAsJsonObject(); keys(entry, "factor", "conditions");
            modifiers.add(new Modifier(number(entry, "factor", 1, 0, 10), conditions(id, entry)));
        }
        if (modifiers.size() > 32) throw new IllegalArgumentException("at most 32 risk modifiers");
        String route = string(json, "route", "any");
        if (!Set.of("any", "default", "custom").contains(route)) throw new IllegalArgumentException("unknown route selector");
        var outcomes = new java.util.ArrayList<JsonObject>();
        if (json.has("outcomes")) for (var raw : json.get("outcomes").getAsJsonArray()) {
            var action = raw.getAsJsonObject(); SettlementEncounterActions.get(action).validate(action); outcomes.add(action.deepCopy());
        }
        if (outcomes.size() > 32) throw new IllegalArgumentException("at most 32 outcome actions");
        String leg = string(json, "leg", "any");
        if (!Set.of("any", "outbound", "homebound").contains(leg)) throw new IllegalArgumentException("unknown journey leg");
        double minimum = number(json, "minimum_development", 0, 0, 1), maximum = number(json, "maximum_development", 1, 0, 1);
        if (minimum > maximum) throw new IllegalArgumentException("conflicting development range");
        return new SettlementEncounterDefinition(id, json.deepCopy(), type, number(json, "weight", 1, .001, 1000),
                number(json, "chance", .1, 0, 1), integer(json, "interval_ticks", 200, 20, 24000),
                integer(json, "distance_blocks", 16, 1, 4096), integer(json, "cooldown_ticks", 2400, 20, 2400000),
                integer(json, "duration_ticks", 600, 20, 24000), integer(json, "round_ticks", 40, 20, 1200),
                number(json, "developed_road_risk", .4, .01, 1), number(json, "guard_risk", .85, .01, 1),
                number(json, "recovery_chance", .25, 0, 1), number(json, "cargo_loss", .1, 0, 1),
                integer(json, "delay_ticks", 100, 0, 24000), integer(json, "minimum_participants", 1, 1, 8),
                integer(json, "maximum_guards", 8, 0, 8), route, string(json, "status", "villagerretaliation.encounter." + type),
                conditions(id, json), List.copyOf(modifiers), List.copyOf(attackers), List.copyOf(outcomes), leg, minimum, maximum);
    }
    private static List<DialogueCondition> conditions(ResourceLocation id, JsonObject json) {
        var result = DialogueCondition.readList(id, "journey encounter", json);
        result.forEach(SettlementEncounterDefinition::validate);
        return result;
    }
    private static void validate(DialogueCondition condition) {
        if (condition instanceof DialogueCondition.AllOf all) all.conditions().forEach(SettlementEncounterDefinition::validate);
        else if (condition instanceof DialogueCondition.AnyOf any) any.conditions().forEach(SettlementEncounterDefinition::validate);
        else if (condition instanceof DialogueCondition.Not not) validate(not.condition());
        else if (!(condition instanceof DialogueCondition.Dimension || condition instanceof DialogueCondition.Biome
                || condition instanceof DialogueCondition.Time || condition instanceof DialogueCondition.Weather
                || condition instanceof DialogueCondition.VillagerEquipment))
            throw new IllegalArgumentException("unsupported journey condition: " + DialogueCondition.canonicalTypeId(condition));
    }
    static void keys(JsonObject json, String... allowed) {
        var names = Set.of(allowed);
        for (String key : json.keySet()) if (!names.contains(key)) throw new IllegalArgumentException("unknown field " + key);
    }
    static String string(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        var raw = json.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString() || raw.getAsString().isBlank()
                || raw.getAsString().length() > 256) throw new IllegalArgumentException("invalid " + key);
        return raw.getAsString();
    }
    static double number(JsonObject json, String key, double fallback, double minimum, double maximum) {
        if (!json.has(key)) return fallback;
        var raw = json.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("invalid " + key);
        double value = raw.getAsDouble();
        if (!Double.isFinite(value) || value < minimum || value > maximum) throw new IllegalArgumentException("out of range " + key);
        return value;
    }
    static int integer(JsonObject json, String key, int fallback, int minimum, int maximum) {
        double value = number(json, key, fallback, minimum, maximum);
        if (value != Math.rint(value)) throw new IllegalArgumentException("expected integer " + key);
        return (int) value;
    }
    SettlementEncounterBehaviors.Behavior behavior() { return SettlementEncounterBehaviors.get(type); }
    double risk(double travelFactor, int guards) {
        return (1 - (1 - roadRisk) * Math.clamp((1 - travelFactor) * 2, 0, 1)) * Math.pow(guardRisk, guards);
    }
}
