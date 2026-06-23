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
        values.put("player", context.player().getName().getString());
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
