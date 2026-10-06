package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredJobSetting;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

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
        HiredJobSetting.TILL_SOIL.initializeDefault(state);
        HiredJobSetting.PLANT_CROPS.initializeDefault(state);
        HiredJobSetting.HARVEST_CROPS.initializeDefault(state);
    }

    public static boolean tillSoil(CompoundTag state) {
        return HiredJobSetting.TILL_SOIL.read(state) != 0;
    }

    public static boolean plantCrops(CompoundTag state) {
        return HiredJobSetting.PLANT_CROPS.read(state) != 0;
    }

    public static boolean harvestCrops(CompoundTag state) {
        return HiredJobSetting.HARVEST_CROPS.read(state) != 0;
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
        public net.minecraft.world.item.Item seed() { return this.seed; }
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
            HiredJobSetting.CROP_SELECTION.apply(state, (crop(state).ordinal() + 1) % HiredJobSetting.CROP_SELECTION.choices());
            return new ToggleResult(normalized, true, false);
        }
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
            case TILL_SOIL -> "Till dirt and grass";
            case PLANT_CROPS -> "Plant crops";
            case HARVEST_CROPS -> "Harvest crops";
            case CROP -> "Crop selection";
            default -> "Farming option";
        };
    }

    private static HiredJobSetting settingFor(String optionId) {
        return switch (optionId) {
            case TILL_SOIL -> HiredJobSetting.TILL_SOIL;
            case PLANT_CROPS -> HiredJobSetting.PLANT_CROPS;
            case HARVEST_CROPS -> HiredJobSetting.HARVEST_CROPS;
            default -> null;
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
