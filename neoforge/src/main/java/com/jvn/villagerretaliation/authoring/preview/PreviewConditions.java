package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class PreviewConditions {
    private final Map<String, ConditionOutcome> overrides = new LinkedHashMap<>();
    private final Map<String, String> quests = new LinkedHashMap<>();
    private final Map<String, java.util.UUID> speakers = new LinkedHashMap<>();
    private final Map<String, String> replacements = new LinkedHashMap<>();
    private ResourceLocation defaultQuest;
    private int reputation;
    private VillagerReputationLevel reputationLevel = VillagerReputationLevel.NEUTRAL;
    private String profession = "minecraft:none";
    private int variant;
    private Long dayTime, worldTime;
    private boolean checkSuccess = true;

    public void override(String type, ConditionOutcome outcome) {
        if (outcome == null) overrides.remove(type);
        else overrides.put(type, outcome);
    }

    public Map<String, ConditionOutcome> overrides() { return Map.copyOf(overrides); }
    public void bindSpeaker(String role, java.util.UUID entity) {
        if (entity == null) speakers.remove(role);
        else speakers.put(role, entity);
    }
    public Map<String, java.util.UUID> speakers() { return Map.copyOf(speakers); }
    public void placeholder(String key, String value) { replacements.put(key, value); }
    public Map<String, String> placeholders() {
        var values = new LinkedHashMap<>(replacements);
        values.putIfAbsent("profession", profession.substring(profession.indexOf(':') + 1));
        values.putIfAbsent("reputation", Integer.toString(reputation));
        values.putIfAbsent("reputation_level", reputationLevel.name().toLowerCase(java.util.Locale.ROOT));
        return Map.copyOf(values);
    }
    public void worldTime(long value) { worldTime = value; }
    public void dayTime(Long value) { dayTime = value; }
    public Long dayTime() { return dayTime; }
    public long resolveDayTime(long worldTime) { return dayTime == null ? worldTime : dayTime; }

    public void defaultQuest(ResourceLocation value) { defaultQuest = value; }
    public void reputation(int value) {
        reputation(value, VillagerReputationLevel.fromReputation(value));
    }
    public void reputation(int value, VillagerReputationLevel level) {
        reputation = value;
        reputationLevel = java.util.Objects.requireNonNull(level);
    }
    public void reputationLevel(VillagerReputationLevel value) { reputationLevel = value; }
    public void profession(String value) { profession = value.contains(":") ? value : "minecraft:" + value; }
    public void quest(String id, String state) { quests.put(id, state); }
    public void variant(int value) { variant = Math.max(0, value); }
    public int variant() { return variant; }
    public void checkSuccess(boolean value) { checkSuccess = value; }
    public boolean checkSuccess() { return checkSuccess; }

    public ConditionOutcome evaluate(ResourceLocation id, JsonObject object) {
        var context = new ConditionContext(null, null, null, null, null, Set.of(), (condition, trigger) -> {
            String type = DialogueCondition.canonicalTypeId(condition);
            if (overrides.containsKey(type)) return overrides.get(type);
            if (condition instanceof DialogueCondition.Reputation test)
                return outcome((test.levels().isEmpty() || test.levels().contains(reputationLevel))
                        && (test.minReputation() == null || reputation >= test.minReputation())
                        && (test.maxReputation() == null || reputation <= test.maxReputation()));
            if (condition instanceof DialogueCondition.Time test && (dayTime != null || worldTime != null)) {
                String phase = com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(
                        dayTime == null ? worldTime : dayTime).get("time_of_day");
                return outcome(test.times().stream().anyMatch(time -> time.name().equalsIgnoreCase(phase)));
            }
            if (condition instanceof DialogueCondition.Quest test) {
                String state = quests.get(test.questId().toString());
                return state == null ? ConditionOutcome.UNKNOWN : outcome(test.states().contains(state));
            }
            return ConditionOutcome.UNKNOWN;
        });
        ConditionOutcome result = ConditionEvaluator.all(context, null,
                DialogueCondition.readList(id, "authoring preview", object, defaultQuest));
        if (!com.jvn.villagerretaliation.util.VillagerReputationCondition.read(object).matches(reputation, reputationLevel))
            return ConditionOutcome.UNMET;
        for (String field : new String[] {"professions", "witness_professions"}) {
            if (object.has(field) && !object.getAsJsonArray(field).isEmpty()) {
                boolean matches = object.getAsJsonArray(field).asList().stream().anyMatch(value -> {
                    String candidate = value.getAsString();
                    return profession.equals(candidate.contains(":") ? candidate : "minecraft:" + candidate);
                });
                if (!matches) return ConditionOutcome.UNMET;
            }
        }
        if (object.has("reputation_levels") && !object.getAsJsonArray("reputation_levels").isEmpty()
                && object.getAsJsonArray("reputation_levels").asList().stream().noneMatch(value ->
                    value.getAsString().equalsIgnoreCase(reputationLevel.name()))) return ConditionOutcome.UNMET;
        for (String field : object.keySet()) {
            if (field.startsWith("requires_") && object.get(field).isJsonPrimitive() && object.get(field).getAsBoolean()) {
                ConditionOutcome simulated = overrides.getOrDefault(field, ConditionOutcome.UNKNOWN);
                if (simulated == ConditionOutcome.UNMET) return simulated;
                if (simulated == ConditionOutcome.UNKNOWN && result == ConditionOutcome.MATCHED) result = simulated;
            }
        }
        return result;
    }

    private static ConditionOutcome outcome(boolean value) {
        return value ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
    }
}
