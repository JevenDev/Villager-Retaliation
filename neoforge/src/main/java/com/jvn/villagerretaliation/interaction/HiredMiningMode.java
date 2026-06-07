package com.jvn.villagerretaliation.interaction;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

public enum HiredMiningMode {
    EXPOSED_ORES("exposed_ores", "Exposed Ores"),
    EXCAVATE_AREA("excavate_area", "Excavate Area");

    public static final String STATE_TAG = "MiningMode";

    private final String serializedName;
    private final String label;

    HiredMiningMode(String serializedName, String label) {
        this.serializedName = serializedName;
        this.label = label;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String label() {
        return this.label;
    }

    public HiredMiningMode next() {
        HiredMiningMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public boolean excavatesArea() {
        return this == EXCAVATE_AREA;
    }

    public static HiredMiningMode bySerializedName(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (HiredMiningMode mode : values()) {
            if (mode.serializedName.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return null;
    }

    public static HiredMiningMode fromState(CompoundTag state) {
        HiredMiningMode mode = bySerializedName(state.getString(STATE_TAG));
        return mode == null ? EXPOSED_ORES : mode;
    }
}
