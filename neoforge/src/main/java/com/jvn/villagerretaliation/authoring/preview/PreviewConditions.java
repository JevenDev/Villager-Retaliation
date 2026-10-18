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
    private final Map<String, Boolean> checks = new LinkedHashMap<>();

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
    public boolean checkSuccess(String id) { return checks.getOrDefault(id, checkSuccess); }
    public void checkSuccess(String id, Boolean value) { if (value == null) checks.remove(id); else checks.put(id, value); }

    private ConditionContext context() {
        return new ConditionContext(null, null, null, null, null, Set.of(), (condition, trigger) -> {
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
    }

    public ConditionOutcome evaluate(java.util.List<DialogueCondition> values) {
        return ConditionEvaluator.all(context(), null, values);
    }

    public ConditionOutcome evaluate(ResourceLocation id, JsonObject object) {
        ConditionOutcome result = ConditionEvaluator.all(context(), null,
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

    public JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("reputation", reputation);
        root.addProperty("tier", reputationLevel.name());
        root.addProperty("profession", profession);
        root.addProperty("variant", variant);
        root.addProperty("check_success", checkSuccess);
        if (dayTime != null) root.addProperty("day_time", dayTime);
        JsonObject outcomes = new JsonObject();
        overrides.forEach((key, value) -> outcomes.addProperty(key, value.name()));
        root.add("overrides", outcomes);
        JsonObject results = new JsonObject();
        checks.forEach(results::addProperty);
        root.add("checks", results);
        JsonObject questStates = new JsonObject();
        quests.forEach(questStates::addProperty);
        root.add("quests", questStates);
        JsonObject placeholders = new JsonObject();
        replacements.forEach(placeholders::addProperty);
        root.add("placeholders", placeholders);
        JsonObject bindings = new JsonObject();
        speakers.forEach((key, value) -> bindings.addProperty(key, value.toString()));
        root.add("speakers", bindings);
        return root;
    }

    public void restore(JsonObject root) {
        var parsed = new PreviewConditions();
        parsed.reputation(root.get("reputation").getAsInt(), VillagerReputationLevel.valueOf(root.get("tier").getAsString()));
        parsed.profession(root.get("profession").getAsString());
        parsed.variant(root.get("variant").getAsInt());
        parsed.checkSuccess(root.get("check_success").getAsBoolean());
        parsed.dayTime(root.has("day_time") ? root.get("day_time").getAsLong() : null);
        root.getAsJsonObject("overrides").entrySet().forEach(entry -> parsed.override(entry.getKey(), ConditionOutcome.valueOf(entry.getValue().getAsString())));
        root.getAsJsonObject("checks").entrySet().forEach(entry -> parsed.checkSuccess(entry.getKey(), entry.getValue().getAsBoolean()));
        root.getAsJsonObject("quests").entrySet().forEach(entry -> parsed.quest(entry.getKey(), entry.getValue().getAsString()));
        root.getAsJsonObject("placeholders").entrySet().forEach(entry -> parsed.placeholder(entry.getKey(), entry.getValue().getAsString()));
        if (root.has("speakers")) root.getAsJsonObject("speakers").entrySet().forEach(entry -> parsed.bindSpeaker(entry.getKey(), java.util.UUID.fromString(entry.getValue().getAsString())));
        speakers.clear(); speakers.putAll(parsed.speakers);
        reputation = parsed.reputation;
        reputationLevel = parsed.reputationLevel;
        profession = parsed.profession;
        variant = parsed.variant;
        dayTime = parsed.dayTime;
        checkSuccess = parsed.checkSuccess;
        overrides.clear(); overrides.putAll(parsed.overrides);
        checks.clear(); checks.putAll(parsed.checks);
        quests.clear(); quests.putAll(parsed.quests);
        replacements.clear(); replacements.putAll(parsed.replacements);
    }

    private static ConditionOutcome outcome(boolean value) {
        return value ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
    }
}
