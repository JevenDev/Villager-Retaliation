package com.jvn.villagerretaliation.interaction.work;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class HiredFarmingOptions {
    public static final String TILL_SOIL_TAG = "FarmingTillSoil";

    public static final String TILL_SOIL = "till_soil";
    public static final String PLANT_CROPS = "plant_crops";
    public static final String HARVEST_CROPS = "harvest_crops";
    public static final String CROP = "crop";
    public static final String PLANT_CROPS_TAG = "FarmingPlantCrops";
    public static final String HARVEST_CROPS_TAG = "FarmingHarvestCrops";
    public static final String CROP_TAG = "FarmingCrop";

    private HiredFarmingOptions() {
    }

    public static void initializeDefaults(CompoundTag state) {
        putDefault(state, TILL_SOIL_TAG, true);
        putDefault(state, PLANT_CROPS_TAG, true);
        putDefault(state, HARVEST_CROPS_TAG, true);
    }

    public static boolean tillSoil(CompoundTag state) {
        return !state.contains(TILL_SOIL_TAG, Tag.TAG_BYTE) || state.getBoolean(TILL_SOIL_TAG);
    }

    public static boolean plantCrops(CompoundTag state) {
        return !state.contains(PLANT_CROPS_TAG, Tag.TAG_BYTE) || state.getBoolean(PLANT_CROPS_TAG);
    }

    public static boolean harvestCrops(CompoundTag state) {
        return !state.contains(HARVEST_CROPS_TAG, Tag.TAG_BYTE) || state.getBoolean(HARVEST_CROPS_TAG);
    }

    public static Crop crop(CompoundTag state) {
        for (var crop : Crop.values()) {
            if (crop.name().equals(state.getString(CROP_TAG))) return crop;
        }
        return Crop.ANY;
    }

    public static boolean permitsSeed(CompoundTag state, net.minecraft.world.item.ItemStack stack) {
        return plantCrops(state) && (crop(state).seed == null || stack.is(crop(state).seed));
    }

    public enum Crop {
        ANY(null, "Any available crop"),
        WHEAT(net.minecraft.world.item.Items.WHEAT_SEEDS, "Wheat"),
        CARROT(net.minecraft.world.item.Items.CARROT, "Carrot"),
        POTATO(net.minecraft.world.item.Items.POTATO, "Potato"),
        BEETROOT(net.minecraft.world.item.Items.BEETROOT_SEEDS, "Beetroot"),
        TORCHFLOWER(net.minecraft.world.item.Items.TORCHFLOWER_SEEDS, "Torchflower"),
        PITCHER(net.minecraft.world.item.Items.PITCHER_POD, "Pitcher plant");

        private final net.minecraft.world.item.Item seed;
        private final String label;

        Crop(net.minecraft.world.item.Item seed, String label) {
            this.seed = seed;
            this.label = label;
        }

        public String label() { return this.label; }
    }

    public static boolean enabled(CompoundTag state, String optionId) {
        return switch (normalize(optionId)) {
            case TILL_SOIL -> tillSoil(state);
            case PLANT_CROPS -> plantCrops(state);
            case HARVEST_CROPS -> harvestCrops(state);
            default -> false;
        };
    }

    public static ToggleResult toggle(CompoundTag state, String optionId) {
        String normalized = normalize(optionId);
        if (CROP.equals(normalized)) {
            state.putString(CROP_TAG, Crop.values()[(crop(state).ordinal() + 1) % Crop.values().length].name());
            return new ToggleResult(normalized, true, false);
        }
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
            case PLANT_CROPS -> "Plant crops";
            case HARVEST_CROPS -> "Harvest crops";
            case CROP -> "Crop selection";
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
            case PLANT_CROPS -> PLANT_CROPS_TAG;
            case HARVEST_CROPS -> HARVEST_CROPS_TAG;
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
