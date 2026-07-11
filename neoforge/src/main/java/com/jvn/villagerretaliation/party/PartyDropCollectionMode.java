package com.jvn.villagerretaliation.party;

public enum PartyDropCollectionMode {
    OFF,
    SLAIN_ENTITIES,
    ALL_DROPS;

    public PartyDropCollectionMode next() {
        PartyDropCollectionMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    static PartyDropCollectionMode byName(String name) {
        if (name != null && !name.isBlank()) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return OFF;
    }
}
