package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.List;

public record QuestTimer(long durationTicks, Mode mode, boolean pauseWhenInactive,
        boolean pauseWhenOffline, long warningTicks, String timeoutStage) {
    public enum Mode { DURATION, DEADLINE }

    public QuestTimer {
        if (durationTicks < 1 || durationTicks > Integer.MAX_VALUE || warningTicks < 0 || warningTicks >= durationTicks)
            throw new IllegalArgumentException("Invalid quest timer duration or warning.");
        timeoutStage = timeoutStage == null ? "" : timeoutStage;
        if (!timeoutStage.isEmpty() && (mode != Mode.DEADLINE || !timeoutStage.matches("[a-z0-9_./-]+")))
            throw new IllegalArgumentException("Only deadline timers can select a timeout stage.");
        java.util.Objects.requireNonNull(mode);
    }

    public static QuestTimer read(JsonElement raw) {
        if (raw == null || raw.isJsonNull()) return null;
        var issues = QuestJsonSchema.validate(raw, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid quest timer: " + issues);
        JsonObject value = raw.getAsJsonObject();
        return new QuestTimer(value.get("duration_ticks").getAsLong(),
                value.has("mode") && value.get("mode").getAsString().equals("duration") ? Mode.DURATION : Mode.DEADLINE,
                !value.has("pause_when_inactive") || value.get("pause_when_inactive").getAsBoolean(),
                !value.has("pause_when_offline") || value.get("pause_when_offline").getAsBoolean(),
                value.has("warning_ticks") ? value.get("warning_ticks").getAsLong() : 0,
                value.has("timeout_stage") ? value.get("timeout_stage").getAsString() : "");
    }

    public static JsonObject schema() {
        var result = new JsonObject();
        result.addProperty("type", "object");
        result.addProperty("additionalProperties", false);
        var required = new com.google.gson.JsonArray();
        required.add("duration_ticks");
        result.add("required", required);
        var fields = new JsonObject();
        fields.add("duration_ticks", integer(1, Integer.MAX_VALUE));
        fields.add("warning_ticks", integer(0, Integer.MAX_VALUE - 1));
        var mode = new JsonObject();
        mode.addProperty("type", "string");
        var values = new com.google.gson.JsonArray();
        List.of("duration", "deadline").forEach(values::add);
        mode.add("enum", values);
        fields.add("mode", mode);
        for (String name : List.of("pause_when_inactive", "pause_when_offline")) {
            var flag = new JsonObject();
            flag.addProperty("type", "boolean");
            fields.add(name, flag);
        }
        var stage = new JsonObject();
        stage.addProperty("type", "string");
        stage.addProperty("minLength", 1);
        stage.addProperty("pattern", "^[a-z0-9_./-]+$");
        fields.add("timeout_stage", stage);
        result.add("properties", fields);
        return result;
    }

    private static JsonObject integer(long min, long max) {
        var field = new JsonObject();
        field.addProperty("type", "integer");
        field.addProperty("minimum", min);
        field.addProperty("maximum", max);
        return field;
    }
}
