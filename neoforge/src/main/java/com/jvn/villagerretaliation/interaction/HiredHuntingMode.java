package com.jvn.villagerretaliation.interaction;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public enum HiredHuntingMode {
    ANIMALS("animals", "Kill Animals"),
    HOSTILES("hostiles", "Kill Hostiles"),
    ALL("all", "Kill All");

    public static final String STATE_TAG = "HuntingMode";

    private final String serializedName;
    private final String label;

    HiredHuntingMode(String serializedName, String label) {
        this.serializedName = serializedName;
        this.label = label;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String label() {
        return this.label;
    }

    public HiredHuntingMode next() {
        HiredHuntingMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static HiredHuntingMode bySerializedName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (HiredHuntingMode mode : values()) {
            if (mode.serializedName.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return null;
    }

    public static HiredHuntingMode fromState(CompoundTag state) {
        if (state == null) {
            return ANIMALS;
        }
        if (state.contains(STATE_TAG, Tag.TAG_STRING)) {
            HiredHuntingMode mode = bySerializedName(state.getString(STATE_TAG));
            if (mode != null) {
                return mode;
            }
        }
        if (!state.contains(HiredCombatMode.STATE_TAG, Tag.TAG_STRING)) {
            return ANIMALS;
        }
        HiredCombatMode legacyMode = HiredCombatMode.bySerializedName(state.getString(HiredCombatMode.STATE_TAG));
        if (legacyMode == null) {
            return ANIMALS;
        }
        return switch (legacyMode) {
            case ATTACK_ALL -> ALL;
            case ROAMING -> HOSTILES;
            case HUNTING -> ANIMALS;
            default -> ANIMALS;
        };
    }
}
