package com.jvn.villagerretaliation.config;

public enum InteractionScreenBackdropStyle {
    FRAMED("Framed"),
    VEIL("Veil");

    private final String label;

    InteractionScreenBackdropStyle(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
