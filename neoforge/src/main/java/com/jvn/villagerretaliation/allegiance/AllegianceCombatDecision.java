package com.jvn.villagerretaliation.allegiance;

public record AllegianceCombatDecision(Action action, Reason reason) {
    public static AllegianceCombatDecision allow(Reason reason) {
        return new AllegianceCombatDecision(Action.ALLOW, reason);
    }

    public static AllegianceCombatDecision deny(Reason reason) {
        return new AllegianceCombatDecision(Action.DENY, reason);
    }

    public static AllegianceCombatDecision pass(Reason reason) {
        return new AllegianceCombatDecision(Action.PASS, reason);
    }

    public boolean denied() {
        return this.action == Action.DENY;
    }

    public enum Action {
        ALLOW,
        DENY,
        PASS
    }

    public enum Reason {
        SAME_ENTITY,
        SAME_PARTY,
        SAME_CANONICAL_ALLEGIANCE,
        PARENT_PROTECTION,
        UNKNOWN_ACTOR,
        UNKNOWN_TARGET,
        NEUTRAL_TRADER,
        GOLEM_RESTRICTED,
        NO_OPPOSING_PARTY_AUTHORIZATION,
        AUTHORIZED_OPPOSING_ALLEGIANCE,
        NON_CIVILIAN_TARGET,
        ORDINARY_BEHAVIOR,
        DIRECT_PLAYER_DAMAGE,
        DISCIPLINARY_RESPONSE
    }
}
