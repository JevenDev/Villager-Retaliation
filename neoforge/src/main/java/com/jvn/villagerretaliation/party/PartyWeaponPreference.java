package com.jvn.villagerretaliation.party;

public enum PartyWeaponPreference {
    AUTO,
    RANGED,
    MELEE;

    public static PartyWeaponPreference byName(String name) {
        if (name == null || name.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }
}
