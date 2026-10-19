package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DialoguePlaceholders {
    private DialoguePlaceholders() {
    }

    public static Map<String, String> base(DialogueContext context) {
        Map<String, String> values = new LinkedHashMap<>();
        if (context.level() != null) values.putAll(time(context.level().getDayTime()));
        values.put("player", context.player().getName().getString());
        values.putAll(com.jvn.villagerretaliation.progress.PlayerHistoryService.replacements(context.player()));
        if (context.villager() == null) {
            values.put("villager", "");
            return Map.copyOf(values);
        }
        String villagerName = context.villager().getName().getString();
        values.put("villager", villagerName);
        values.put("villager_name", villagerName);
        values.put("villager_possessive", toPossessive(villagerName));
        values.put("profession", VillagerInteractionTextUtil.professionName(context.profession(), "villager"));
        values.put("reputation", Integer.toString(context.reputation()));
        values.put("reputation_level", context.reputationLevel().name().toLowerCase(java.util.Locale.ROOT));
        values.put("x", Integer.toString(context.villager().blockPosition().getX()));
        values.put("y", Integer.toString(context.villager().blockPosition().getY()));
        values.put("z", Integer.toString(context.villager().blockPosition().getZ()));
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
