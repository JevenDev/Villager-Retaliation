package com.jvn.villagerretaliation.interaction.work;

public enum BuilderBuildPhase {
    IDLE("idle"),
    WAITING_FOR_SELECTION("waiting_for_selection"),
    VALIDATING_SITE("validating_site"),
    COLLECTING_MATERIALS("collecting_materials"),
    WAITING_FOR_MATERIALS("waiting_for_materials"),
    TRAVELING_TO_SITE("traveling_to_site"),
    BUILDING("building"),
    DEPOSITING_LEFTOVERS("depositing_leftovers"),
    COMPLETE("complete"),
    BLOCKED("blocked"),
    CANCELLED("cancelled");

    private final String id;

    BuilderBuildPhase(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
