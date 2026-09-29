package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.HiredJobSetting;
import net.minecraft.nbt.CompoundTag;

public final class MiningHorizontalOptions {
    public static final String PATCH_FLOOR_TAG = "HorizontalMiningPatchFloor";

    private MiningHorizontalOptions() {
    }

    public static void initializeDefaults(CompoundTag state) {
        HiredJobSetting.PATCH_FLOOR.initializeDefault(state);
    }

    public static boolean patchFloor(CompoundTag state) {
        initializeDefaults(state);
        return HiredJobSetting.PATCH_FLOOR.read(state) != 0;
    }

    public static boolean togglePatchFloor(CompoundTag state) {
        boolean enabled = !patchFloor(state);
        HiredJobSetting.PATCH_FLOOR.apply(state, enabled ? 1 : 0);
        return enabled;
    }
}
