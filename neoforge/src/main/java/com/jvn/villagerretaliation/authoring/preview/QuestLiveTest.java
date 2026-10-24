package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public record QuestLiveTest(String baseline, boolean manual, List<Step> steps, List<Expectation> expectations) {
    public static final List<String> ACTIONS = List.of("start", "prepare", "travel", "choice", "next", "turn_in", "fail", "abandon",
            "wait", "return_provider", "discovery_item", "discovery_travel", "accept_discovery", "criterion");
    public static final List<String> CHECKS = List.of("state", "stage", "experience_delta", "item_count", "performance_tier", "completion_delta", "start_delta", "objective_complete");
    public record Step(String action, String target, String value) { }
    public record Expectation(String kind, String target, String expected) { }

    public QuestLiveTest {
        if (baseline == null || baseline.isBlank() || baseline.length() > 128) throw new IllegalArgumentException("Choose a saved playtest baseline");
        steps = List.copyOf(steps); expectations = List.copyOf(expectations);
        if (steps.size() > 256 || expectations.isEmpty() || expectations.size() > 64)
            throw new IllegalArgumentException("Use at most 256 steps and 1 to 64 expectations");
        int waiting = 0;
        for (var step : steps) {
            if (!ACTIONS.contains(step.action())) throw new IllegalArgumentException("Unknown live test action: " + step.action());
            if (List.of("prepare", "travel", "choice", "criterion", "discovery_item", "discovery_travel").contains(step.action()) && step.target().isBlank())
                throw new IllegalArgumentException("Choose a target for " + step.action());
            if (step.action().equals("wait")) {
                int ticks = integer(step.value());
                if (ticks < 1 || ticks > 12000) throw new IllegalArgumentException("Wait steps require 1 to 12000 ticks");
                waiting += ticks;
            }
        }
        if (waiting > 24000) throw new IllegalArgumentException("Use at most 24000 waiting ticks per live test");
        for (var check : expectations) {
            if (!CHECKS.contains(check.kind())) throw new IllegalArgumentException("Unknown live test expectation: " + check.kind());
            if (List.of("item_count", "objective_complete").contains(check.kind()) && check.target().isBlank())
                throw new IllegalArgumentException("Choose an expectation target");
            if (List.of("experience_delta", "completion_delta", "start_delta", "item_count").contains(check.kind())) {
                int count = integer(check.expected());
                if (!check.kind().equals("experience_delta") && count < 0) throw new IllegalArgumentException("Expected counts must be nonnegative");
            }
            if (check.kind().equals("state") && !List.of("not_started", "active", "completed", "failed", "abandoned", "expired", "consumed").contains(check.expected()))
                throw new IllegalArgumentException("Choose a quest state");
            if (check.kind().equals("objective_complete") && !List.of("true", "false").contains(check.expected()))
                throw new IllegalArgumentException("Objective completion expects true or false");
        }
    }

    public int waitingTicks() { return steps.stream().filter(step -> step.action().equals("wait")).mapToInt(step -> integer(step.value())).sum(); }

    public JsonObject json() {
        var result = new JsonObject(); result.addProperty("baseline", baseline); result.addProperty("manual", manual);
        var actions = new JsonArray();
        for (var step : steps) {
            var value = new JsonObject(); value.addProperty("action", step.action()); value.addProperty("target", step.target()); value.addProperty("value", step.value()); actions.add(value);
        }
        var checks = new JsonArray();
        for (var check : expectations) {
            var value = new JsonObject(); value.addProperty("kind", check.kind()); value.addProperty("target", check.target()); value.addProperty("expected", check.expected()); checks.add(value);
        }
        result.add("steps", actions); result.add("expectations", checks); return result;
    }

    public static QuestLiveTest read(JsonObject value) {
        if (!value.has("steps") || !value.get("steps").isJsonArray() || !value.has("expectations") || !value.get("expectations").isJsonArray()
                || !value.has("manual") || !value.get("manual").isJsonPrimitive() || !value.getAsJsonPrimitive("manual").isBoolean())
            throw new IllegalArgumentException("Saved live test is missing steps, expectations or progression mode");
        if (value.getAsJsonArray("steps").size() > 256 || value.getAsJsonArray("expectations").size() > 64)
            throw new IllegalArgumentException("Saved live test has too many steps or expectations");
        var steps = new ArrayList<Step>();
        for (var entry : value.getAsJsonArray("steps")) {
            var step = object(entry); steps.add(new Step(text(step, "action"), text(step, "target"), text(step, "value")));
        }
        var checks = new ArrayList<Expectation>();
        for (var entry : value.getAsJsonArray("expectations")) {
            var check = object(entry); checks.add(new Expectation(text(check, "kind"), text(check, "target"), text(check, "expected")));
        }
        return new QuestLiveTest(text(value, "baseline"), value.get("manual").getAsBoolean(), steps, checks);
    }

    private static JsonObject object(JsonElement value) {
        if (!value.isJsonObject()) throw new IllegalArgumentException("Saved live test entries must be objects");
        return value.getAsJsonObject();
    }

    private static String text(JsonObject value, String field) {
        var entry = value.get(field);
        if (entry == null || !entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Saved live test is missing text field " + field);
        return entry.getAsString();
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Enter a whole number"); }
    }
}
