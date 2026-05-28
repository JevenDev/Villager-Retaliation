package com.jvn.villagerretaliation.config;

public enum ReputationChangeNotificationStyle {
    DEFAULT("Default"),
    EXPERIMENTAL("Experimental");

    private final String label;

    ReputationChangeNotificationStyle(String label) {
        this.label = label;
    }

    public boolean experimental() {
        return this == EXPERIMENTAL;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
