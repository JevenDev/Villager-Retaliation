package com.jvn.villagerretaliation.party;

public enum PartyAttackMode {
    ANIMALS,
    HOSTILES,
    PLAYERS,
    PARTIES,
    ALL;

    public PartyAttackMode next() {
        PartyAttackMode[] modes = values();
        return modes[(this.ordinal() + 1) % modes.length];
    }

    public boolean allows(boolean animal, boolean hostile, boolean player, boolean party) {
        return switch (this) {
            case ANIMALS -> animal;
            case HOSTILES -> hostile;
            case PLAYERS -> player;
            case PARTIES -> party;
            case ALL -> true;
        };
    }

    public static PartyAttackMode byName(String name) {
        if (name == null || name.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }
}
