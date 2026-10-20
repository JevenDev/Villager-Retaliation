package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DialoguePlaceholders {
    private DialoguePlaceholders() {
    }

    public static Map<String, String> base(DialogueRuntimeContext context) {
        Map<String, String> values = new LinkedHashMap<>();
        if (context.level() != null) values.putAll(time(context.level().getDayTime()));
        values.put("player", context.player().getName().getString());
        String speaker = context.speaker() == null ? "" : context.displayName(context.speaker());
        values.put("speaker", speaker);
        values.put("speaker_name", speaker);
        values.put("speaker_possessive", toPossessive(speaker));
        if (context.speaker() != null) {
            values.put("x", Integer.toString(context.origin().getX()));
            values.put("y", Integer.toString(context.origin().getY()));
            values.put("z", Integer.toString(context.origin().getZ()));
        }
        values.putAll(context.replacements());
        return Map.copyOf(values);
    }

    public static Map<String, String> time(long dayTime) {
        long ticks = Math.floorMod(dayTime, 24000L);
        long minutes = (ticks + 6000) % 24000 * 1440 / 24000;
        String phase = ticks < 6000 ? "morning" : ticks < 12000 ? "afternoon" : ticks < 14000 ? "evening" : "night";
        return Map.of("time", String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60),
                "time_of_day", phase, "day", Long.toString(Math.floorDiv(dayTime, 24000L) + 1));
    }

    public static Map<String, String> merge(Map<String, String> first, Map<String, String> second) {
        Map<String, String> values = new LinkedHashMap<>();
        if (first != null) {
            values.putAll(first);
        }
        if (second != null) {
            values.putAll(second);
        }
        return Map.copyOf(values);
    }

    private static String toPossessive(String name) {
        if (name == null || name.isBlank()) {
            return "someone here's";
        }
        return name.endsWith("s") || name.endsWith("S") ? name + "'" : name + "'s";
    }
}
