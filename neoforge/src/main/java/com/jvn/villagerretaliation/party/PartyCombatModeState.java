package com.jvn.villagerretaliation.party;

public enum PartyCombatModeState {
    KILL_ON_SIGHT,
    ATTACK_WITH_PARTY,
    SELF_DEFENSE,
    CUSTOM;

    public PartyCombatMode nextMode() {
        if (this == CUSTOM) {
            return PartyCombatMode.ATTACK_WITH_PARTY;
        }
        return PartyCombatMode.valueOf(this.name()).next();
    }

    public static PartyCombatModeState of(PartyCombatMode mode) {
        return valueOf((mode == null ? PartyCombatMode.ATTACK_WITH_PARTY : mode).name());
    }
}
