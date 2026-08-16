package com.jvn.villagerretaliation.interaction.work;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class HiredAnimalCullSettings {
    public static final int DISABLED_CAP = 0;
    private static final String CULL_CAP_TAG = "AnimalCullCap";
    private static final int[] CAP_OPTIONS = {2, 4, 6, 8, 10, 20, 32};

    private HiredAnimalCullSettings() {
    }

    public static int[] capOptions() {
        return CAP_OPTIONS.clone();
    }

    public static int cap(CompoundTag state) {
        if (state == null || !state.contains(CULL_CAP_TAG, Tag.TAG_INT)) {
            return DISABLED_CAP;
        }
        int stored = state.getInt(CULL_CAP_TAG);
        return isValidCap(stored) ? stored : DISABLED_CAP;
    }

    public static void setCap(CompoundTag state, int cap) {
        if (state == null) {
            return;
        }
        if (cap <= DISABLED_CAP) {
            state.remove(CULL_CAP_TAG);
            return;
        }
        if (isValidCap(cap)) {
            state.putInt(CULL_CAP_TAG, cap);
        }
    }

    public static boolean isValidCap(int cap) {
        return Arrays.stream(CAP_OPTIONS).anyMatch(option -> option == cap);
    }

    public static String selectionLabel(CompoundTag state) {
        int cap = cap(state);
        return cap > DISABLED_CAP ? Integer.toString(cap) : "off";
    }
}
