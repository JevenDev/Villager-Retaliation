package com.jvn.villagerretaliation.party;

public enum PartyCommandMode {
    FOLLOW,
    STAY;

    static PartyCommandMode byName(String value) {
        if (value != null) {
            for (PartyCommandMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
        }
        return FOLLOW;
    }
}
