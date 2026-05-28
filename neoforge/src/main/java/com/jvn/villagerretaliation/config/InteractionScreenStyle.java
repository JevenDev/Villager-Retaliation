package com.jvn.villagerretaliation.config;

public enum InteractionScreenStyle {
    DEFAULT("Default"),
    EXPERIMENTAL("Experimental");

    private final String label;

    InteractionScreenStyle(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
