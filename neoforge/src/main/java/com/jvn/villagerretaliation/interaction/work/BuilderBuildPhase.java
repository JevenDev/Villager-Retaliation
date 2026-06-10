package com.jvn.villagerretaliation.interaction.work;

import java.util.Locale;

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

    public static BuilderBuildPhase byId(String value) {
        if (value == null || value.isBlank()) {
            return IDLE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (BuilderBuildPhase phase : values()) {
            if (phase.id.equals(normalized) || phase.name().equalsIgnoreCase(normalized)) {
                return phase;
            }
        }
        return IDLE;
    }
}
