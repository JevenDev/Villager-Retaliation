package com.jvn.villagerretaliation.interaction;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

public enum HiredMiningMode {
    EXPOSED_ORES("exposed_ores", "Exposed Ores"),
    HORIZONTAL_EXCAVATION("horizontal_excavation", "Horizontal Excavation"),
    EXCAVATE_AREA("excavate_area", "Vertical Excavation");

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
        return this == HORIZONTAL_EXCAVATION || this == EXCAVATE_AREA;
    }

    public boolean excavatesHorizontally() {
        return this == HORIZONTAL_EXCAVATION;
    }

    public boolean usesExcavationShaft() {
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
        var queue = HiredTemporaryWorkService.queue(state);
        if (queue != null && queue.role() == HiredVillagerRole.MINING) {
            switch (queue.current().kind()) {
                case ORE, ORE_AREA -> { return EXPOSED_ORES; }
                case TUNNEL, STAIRCASE, STAIRCASE_PROSPECT -> { return HORIZONTAL_EXCAVATION; }
                case QUARRY -> { return EXCAVATE_AREA; }
                default -> { }
            }
        }
        HiredMiningMode mode = bySerializedName(state.getString(STATE_TAG));
        return mode == null ? EXPOSED_ORES : mode;
    }
}
