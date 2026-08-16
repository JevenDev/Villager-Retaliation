package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.Locale;

public record DialogueUsagePolicy(
        long cooldownTicks,
        int maxUses,
        boolean antiRepeat,
        Scope scope
) {
    public static final DialogueUsagePolicy DEFAULT = new DialogueUsagePolicy(0L, 0, true, Scope.PLAYER_VILLAGER);

    public DialogueUsagePolicy {
        cooldownTicks = Math.max(0L, cooldownTicks);
        maxUses = Math.max(0, maxUses);
        scope = scope == null ? Scope.PLAYER_VILLAGER : scope;
    }

    public static DialogueUsagePolicy read(JsonObject object, DialogueUsagePolicy fallback) {
        DialogueUsagePolicy base = fallback == null ? DEFAULT : fallback;
        if (object == null) {
            return base;
        }
        JsonObject usage = DatapackJsonReader.readObject(object, "usage");
        JsonObject source = usage == null ? object : usage;
        long cooldown = hasAny(source, "cooldown", "cooldown_ticks", "cooldown_seconds", "cooldown_days")
                ? DatapackJsonReader.readDurationTicks(source, "cooldown", base.cooldownTicks())
                : base.cooldownTicks();
        int maxUses = source.has("once") && source.get("once").getAsBoolean()
                ? 1
                : DatapackJsonReader.readInt(source, "max_uses", base.maxUses());
        boolean antiRepeat = DatapackJsonReader.readBoolean(source, "anti_repeat", base.antiRepeat());
        Scope scope = Scope.parse(DatapackJsonReader.readString(source, "scope"), base.scope());
        return new DialogueUsagePolicy(cooldown, maxUses, antiRepeat, scope);
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                return true;
            }
        }
        return false;
    }

    public enum Scope {
        PLAYER_VILLAGER,
        PLAYER,
        VILLAGER,
        VILLAGE,
        DIMENSION,
        WORLD;

        public static Scope parse(String value, Scope fallback) {
            if (value == null || value.isBlank()) {
                return fallback == null ? PLAYER_VILLAGER : fallback;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback == null ? PLAYER_VILLAGER : fallback;
            }
        }
    }
}
