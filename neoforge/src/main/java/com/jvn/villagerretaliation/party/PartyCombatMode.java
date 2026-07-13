package com.jvn.villagerretaliation.party;

public enum PartyCombatMode {
    KILL_ON_SIGHT,
    ATTACK_WITH_PARTY,
    SELF_DEFENSE;

    public PartyCombatMode next() {
        PartyCombatMode[] modes = values();
        return modes[(this.ordinal() + 1) % modes.length];
    }

    public static PartyCombatMode byName(String name) {
        if (name == null || name.isBlank()) {
            return ATTACK_WITH_PARTY;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ATTACK_WITH_PARTY;
        }
    }
}
