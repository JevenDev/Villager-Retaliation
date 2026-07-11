package com.jvn.villagerretaliation.interaction.work.mining;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class MiningHorizontalOptions {
    public static final String PATCH_FLOOR_TAG = "HorizontalMiningPatchFloor";

    private MiningHorizontalOptions() {
    }

    public static void initializeDefaults(CompoundTag state) {
        if (!state.contains(PATCH_FLOOR_TAG, Tag.TAG_BYTE)) {
            state.putBoolean(PATCH_FLOOR_TAG, true);
        }
    }

    public static boolean patchFloor(CompoundTag state) {
        initializeDefaults(state);
        return state.getBoolean(PATCH_FLOOR_TAG);
    }

    public static boolean togglePatchFloor(CompoundTag state) {
        boolean enabled = !patchFloor(state);
        state.putBoolean(PATCH_FLOOR_TAG, enabled);
        return enabled;
    }
}
