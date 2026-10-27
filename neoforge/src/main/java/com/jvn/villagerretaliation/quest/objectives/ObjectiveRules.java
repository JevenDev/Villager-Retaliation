package com.jvn.villagerretaliation.quest.objectives;

import com.google.gson.JsonObject;
import java.util.List;
import com.jvn.villagerretaliation.quest.QuestDefinition;

public record ObjectiveRules(boolean distinctVillagers, int maintainTicks, List<String> requirements, int requiredCount) {
    public static final ObjectiveRules EMPTY = new ObjectiveRules(false, 0, List.of(), 0);
    public ObjectiveRules {
        requirements = List.copyOf(requirements);
        if (maintainTicks < 0 || maintainTicks > 12096000) throw new IllegalArgumentException("Maintain duration must be between 0 and 12096000 ticks");
        if (requiredCount < 0 || requiredCount > requirements.size()) throw new IllegalArgumentException("Required task count exceeds the selected requirements");
        if (requirements.size() > 64 || requirements.stream().distinct().count() != requirements.size())
            throw new IllegalArgumentException("Choose up to 64 different requirements");
    }
    public int threshold() { return requiredCount == 0 ? requirements.size() : requiredCount; }
    public boolean live() { return maintainTicks > 0 || !requirements.isEmpty(); }
    public static ObjectiveRules read(JsonObject json, QuestDefinition.ObjectiveType type) {
        var rules = new ObjectiveRules(json.has("distinct_villagers") && json.get("distinct_villagers").getAsBoolean(),
                json.has("maintain_ticks") ? json.get("maintain_ticks").getAsInt() : 0,
                json.has("requirements") ? json.getAsJsonArray("requirements").asList().stream().map(value -> value.getAsString()).toList() : List.of(),
                json.has("required_count") ? json.get("required_count").getAsInt() : 0);
        if (rules.distinctVillagers && type != QuestDefinition.ObjectiveType.TRADE && type != QuestDefinition.ObjectiveType.GIFT)
            throw new IllegalArgumentException("Distinct villagers requires a trade or gift objective");
        if (rules.live() && type != QuestDefinition.ObjectiveType.CONDITION)
            throw new IllegalArgumentException("Task groups and maintained conditions require a condition objective");
        if (rules.requiredCount > 0 && rules.requirements.isEmpty()) throw new IllegalArgumentException("Select task requirements first");
        return rules;
    }
}
