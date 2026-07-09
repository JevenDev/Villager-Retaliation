package com.jvn.villagerretaliation.interaction;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public enum HiredCombatMode {
    GUARD("guard", false),
    ROAMING("roaming", true),
    ATTACK_ALL("attack_all", true),
    HUNTING("hunting", true);

    public static final String STATE_TAG = "CombatMode";

    private final String serializedName;
    private final boolean roaming;

    HiredCombatMode(String serializedName, boolean roaming) {
        this.serializedName = serializedName;
        this.roaming = roaming;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public boolean roams() {
        return this.roaming;
    }

    public String label() {
        String[] words = this.serializedName.split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    public HiredCombatMode next() {
        return this == GUARD ? ROAMING : GUARD;
    }

    public static HiredCombatMode bySerializedName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (HiredCombatMode mode : values()) {
            if (mode.serializedName.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return null;
    }

    public static HiredCombatMode fromState(CompoundTag state) {
        if (state == null || !state.contains(STATE_TAG, Tag.TAG_STRING)) {
            return GUARD;
        }
        HiredCombatMode mode = bySerializedName(state.getString(STATE_TAG));
        return mode == null ? GUARD : mode == ATTACK_ALL || mode == HUNTING ? ROAMING : mode;
    }
}
