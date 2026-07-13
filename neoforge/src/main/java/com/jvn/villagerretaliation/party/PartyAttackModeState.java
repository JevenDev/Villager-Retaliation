package com.jvn.villagerretaliation.party;

public enum PartyAttackModeState {
    ANIMALS,
    HOSTILES,
    PLAYERS,
    VILLAGERS,
    PARTIES,
    ALL,
    CUSTOM;

    public PartyAttackMode nextMode() {
        if (this == CUSTOM) {
            return PartyAttackMode.ALL;
        }
        return PartyAttackMode.valueOf(this.name()).next();
    }

    public static PartyAttackModeState of(PartyAttackMode mode) {
        return valueOf((mode == null ? PartyAttackMode.ALL : mode).name());
    }
}
