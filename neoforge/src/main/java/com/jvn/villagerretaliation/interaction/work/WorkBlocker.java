package com.jvn.villagerretaliation.interaction.work;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public enum WorkBlocker {
    NONE,
    MISSING_TOOL,
    NO_WORK_AREA,
    MISSING_FILTER,
    MISSING_CRAFTING_TABLE,
    MISSING_FURNACE,
    MISSING_BREWING_STAND,
    NO_OBSERVED_WORK,
    PROFESSION_REQUIRED,
    RESTING,
    UNSAFE;

    private static final String STATE_TAG = "ServiceBlocker";

    public boolean preservesService() {
        return this == RESTING || this == UNSAFE;
    }

    public boolean blocksService() {
        return this != NONE && !preservesService();
    }

    public void save(CompoundTag state) {
        state.putString(STATE_TAG, name().toLowerCase(Locale.ROOT));
    }

    public static WorkBlocker read(CompoundTag state) {
        WorkBlocker saved = state.contains(STATE_TAG, Tag.TAG_STRING)
                ? decode(state.getString(STATE_TAG)) : legacy(state);
        if (saved.preservesService()) return saved;
        return switch (HiredWorkerTaskState.byId(state.getString("WorkerTaskState"))) {
            case PAUSED_MISSING_TOOL -> MISSING_TOOL;
            case NO_WORK_AREA -> NO_WORK_AREA;
            default -> saved;
        };
    }

    private static WorkBlocker decode(String value) {
        for (var blocker : values()) {
            if (blocker.name().equalsIgnoreCase(value)) return blocker;
        }
        return NONE;
    }

    private static WorkBlocker legacy(CompoundTag state) {
        String status = state.getString("Status");
        if (status.equals("interaction.work.status.sleeping") || status.equals("interaction.work.status.tired")) return RESTING;
        if (status.endsWith(".no_filter")) return MISSING_FILTER;
        if (status.endsWith(".no_crafting_table") || status.equals("interaction.work.craftsman.no_table")) return MISSING_CRAFTING_TABLE;
        if (status.equals("interaction.work.cooking.no_station")) return MISSING_FURNACE;
        if (state.getString("WorkerFailureReason").endsWith("_profession_required")) return PROFESSION_REQUIRED;
        return NONE;
    }
}
