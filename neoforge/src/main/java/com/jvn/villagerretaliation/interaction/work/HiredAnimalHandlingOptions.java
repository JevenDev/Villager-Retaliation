package com.jvn.villagerretaliation.interaction.work;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class HiredAnimalHandlingOptions {
    public static final String SHEAR_SHEEP_TAG = "AnimalHandlingShearSheep";
    public static final String SHEAR_SHEEP = "shear_sheep";
    public static final String BREED = "breed";
    public static final String MILK = "milk";
    public static final String COLLECT = "collect";
    public static final String CULL = "cull";
    public static final String BREED_TAG = "AnimalHandlingBreed";
    public static final String MILK_TAG = "AnimalHandlingMilk";
    public static final String COLLECT_TAG = "AnimalHandlingCollect";
    public static final String CULL_TAG = "AnimalHandlingCull";

    private HiredAnimalHandlingOptions() {
    }

    public static void initializeDefaults(CompoundTag state) {
        for (String tag : java.util.List.of(SHEAR_SHEEP_TAG, BREED_TAG, MILK_TAG, COLLECT_TAG, CULL_TAG)) {
            if (!state.contains(tag, Tag.TAG_BYTE)) state.putBoolean(tag, true);
        }
    }

    public static boolean shearSheep(CompoundTag state) {
        return !state.contains(SHEAR_SHEEP_TAG, Tag.TAG_BYTE) || state.getBoolean(SHEAR_SHEEP_TAG);
    }

    public static boolean breed(CompoundTag state) { return enabled(state, BREED); }
    public static boolean milk(CompoundTag state) { return enabled(state, MILK); }
    public static boolean collect(CompoundTag state) { return enabled(state, COLLECT); }
    public static boolean cull(CompoundTag state) { return enabled(state, CULL); }

    public static boolean enabled(CompoundTag state, String optionId) {
        String tag = tagFor(normalize(optionId));
        return !tag.isEmpty() && (!state.contains(tag, Tag.TAG_BYTE) || state.getBoolean(tag));
    }

    private static String tagFor(String optionId) {
        return switch (optionId) {
            case SHEAR_SHEEP -> SHEAR_SHEEP_TAG;
            case BREED -> BREED_TAG;
            case MILK -> MILK_TAG;
            case COLLECT -> COLLECT_TAG;
            case CULL -> CULL_TAG;
            default -> "";
        };
    }

    public static ToggleResult toggle(CompoundTag state, String optionId) {
        String normalized = normalize(optionId);
        String tag = tagFor(normalized);
        if (tag.isEmpty()) {
            return ToggleResult.invalidResult();
        }
        boolean enabled = !enabled(state, normalized);
        state.putBoolean(tag, enabled);
        return new ToggleResult(normalized, enabled, false);
    }

    public static String label(String optionId) {
        return switch (normalize(optionId)) {
            case SHEAR_SHEEP -> "Shearing";
            case BREED -> "Breeding";
            case MILK -> "Milking";
            case COLLECT -> "Collection";
            case CULL -> "Culling";
            default -> "Animal handling";
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
