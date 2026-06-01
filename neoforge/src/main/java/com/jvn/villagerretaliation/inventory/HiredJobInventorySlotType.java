package com.jvn.villagerretaliation.inventory;

import java.util.Locale;

public enum HiredJobInventorySlotType {
    NORMAL("normal"),
    GEAR("gear"),
    SUPPLY("supply"),
    OUTPUT("output"),
    PROTECTED_PROPERTY("protected_property");

    private final String id;

    HiredJobInventorySlotType(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static HiredJobInventorySlotType byId(String id) {
        if (id == null || id.isBlank()) {
            return NORMAL;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (HiredJobInventorySlotType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return NORMAL;
    }
}
