package com.jvn.villagerretaliation.interaction.work;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class HiredFarmingOptions {
    public static final String TILL_SOIL_TAG = "FarmingTillSoil";

    public static final String TILL_SOIL = "till_soil";

    private HiredFarmingOptions() {
    }

    public static void initializeDefaults(CompoundTag state) {
        putDefault(state, TILL_SOIL_TAG, true);
    }

    public static boolean tillSoil(CompoundTag state) {
        return !state.contains(TILL_SOIL_TAG, Tag.TAG_BYTE) || state.getBoolean(TILL_SOIL_TAG);
    }

    public static boolean enabled(CompoundTag state, String optionId) {
        return switch (normalize(optionId)) {
            case TILL_SOIL -> tillSoil(state);
            default -> false;
        };
    }

    public static ToggleResult toggle(CompoundTag state, String optionId) {
        String normalized = normalize(optionId);
        String tag = tagFor(normalized);
        if (tag.isBlank()) {
            return ToggleResult.invalidResult();
        }
        boolean enabled = !enabled(state, normalized);
        state.putBoolean(tag, enabled);
        return new ToggleResult(normalized, enabled, false);
    }

    public static String label(String optionId) {
        return switch (normalize(optionId)) {
            case TILL_SOIL -> "Till dirt and grass";
            default -> "Farming option";
        };
    }

    private static void putDefault(CompoundTag state, String tag, boolean value) {
        if (!state.contains(tag, Tag.TAG_BYTE)) {
            state.putBoolean(tag, value);
        }
    }

    private static String tagFor(String optionId) {
        return switch (optionId) {
            case TILL_SOIL -> TILL_SOIL_TAG;
            default -> "";
        };
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
