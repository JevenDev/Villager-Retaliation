package com.jvn.villagerretaliation.interaction.work;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class HiredAnimalHandlingOptions {
    public static final String SHEAR_SHEEP_TAG = "AnimalHandlingShearSheep";
    public static final String SHEAR_SHEEP = "shear_sheep";

    private HiredAnimalHandlingOptions() {
    }

    public static void initializeDefaults(CompoundTag state) {
        if (!state.contains(SHEAR_SHEEP_TAG, Tag.TAG_BYTE)) {
            state.putBoolean(SHEAR_SHEEP_TAG, true);
        }
    }

    public static boolean shearSheep(CompoundTag state) {
        return !state.contains(SHEAR_SHEEP_TAG, Tag.TAG_BYTE) || state.getBoolean(SHEAR_SHEEP_TAG);
    }

    public static ToggleResult toggle(CompoundTag state, String optionId) {
        String normalized = normalize(optionId);
        if (!SHEAR_SHEEP.equals(normalized)) {
            return ToggleResult.invalidResult();
        }
        boolean enabled = !shearSheep(state);
        state.putBoolean(SHEAR_SHEEP_TAG, enabled);
        return new ToggleResult(normalized, enabled, false);
    }

    private static String normalize(String optionId) {
        return optionId == null ? "" : optionId.trim().toLowerCase(Locale.ROOT);
    }

    public record ToggleResult(String optionId, boolean enabled, boolean invalid) {
        private static ToggleResult invalidResult() {
            return new ToggleResult("", false, true);
        }
    }
}
