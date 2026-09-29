package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredJobSetting;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

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
        for (HiredJobSetting setting : java.util.List.of(HiredJobSetting.SHEAR_SHEEP, HiredJobSetting.BREED_ANIMALS,
                HiredJobSetting.MILK_ANIMALS, HiredJobSetting.COLLECT_ANIMAL_DROPS, HiredJobSetting.CULL_ANIMALS)) {
            setting.initializeDefault(state);
        }
    }

    public static boolean shearSheep(CompoundTag state) {
        return HiredJobSetting.SHEAR_SHEEP.read(state) != 0;
    }

    public static boolean breed(CompoundTag state) { return enabled(state, BREED); }
    public static boolean milk(CompoundTag state) { return enabled(state, MILK); }
    public static boolean collect(CompoundTag state) { return enabled(state, COLLECT); }
    public static boolean cull(CompoundTag state) { return enabled(state, CULL); }

    public static boolean enabled(CompoundTag state, String optionId) {
        HiredJobSetting setting = settingFor(normalize(optionId));
        return setting != null && setting.read(state) != 0;
    }

    private static HiredJobSetting settingFor(String optionId) {
        return switch (optionId) {
            case SHEAR_SHEEP -> HiredJobSetting.SHEAR_SHEEP;
            case BREED -> HiredJobSetting.BREED_ANIMALS;
            case MILK -> HiredJobSetting.MILK_ANIMALS;
            case COLLECT -> HiredJobSetting.COLLECT_ANIMAL_DROPS;
            case CULL -> HiredJobSetting.CULL_ANIMALS;
            default -> null;
        };
    }

    public static ToggleResult toggle(CompoundTag state, String optionId) {
        String normalized = normalize(optionId);
        HiredJobSetting setting = settingFor(normalized);
        if (setting == null) {
            return ToggleResult.invalidResult();
        }
        boolean enabled = !enabled(state, normalized);
        setting.apply(state, enabled ? 1 : 0);
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
