package com.jvn.villagerretaliation.interaction.work.logging;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class HiredLoggingOptions {
    public static final String STRIP_LOGS_TAG = "LoggingStripLogs";
    public static final String HARVEST_LEAVES_TAG = "LoggingHarvestLeaves";
    public static final String BONEMEAL_SAPLINGS_TAG = "LoggingBonemealSaplings";
    public static final String PLANT_SAPLINGS_TAG = "LoggingPlantSaplings";
    public static final String PICK_UP_DECAY_DROPS_TAG = "LoggingPickUpDecayDrops";

    public static final String STRIP_LOGS = "strip_logs";
    public static final String HARVEST_LEAVES = "harvest_leaves";
    public static final String BONEMEAL_SAPLINGS = "bonemeal_saplings";
    public static final String PLANT_SAPLINGS = "plant_saplings";
    public static final String PICK_UP_DECAY_DROPS = "pick_up_decay_drops";

    private HiredLoggingOptions() {
    }

    public static void initializeDefaults(CompoundTag state) {
        putDefault(state, STRIP_LOGS_TAG, false);
        putDefault(state, HARVEST_LEAVES_TAG, false);
        putDefault(state, BONEMEAL_SAPLINGS_TAG, false);
        putDefault(state, PLANT_SAPLINGS_TAG, false);
        putDefault(state, PICK_UP_DECAY_DROPS_TAG, true);
    }

    public static boolean stripLogs(CompoundTag state) {
        return state.getBoolean(STRIP_LOGS_TAG);
    }

    public static boolean harvestLeaves(CompoundTag state) {
        return state.getBoolean(HARVEST_LEAVES_TAG);
    }

    public static boolean bonemealSaplings(CompoundTag state) {
        return state.getBoolean(BONEMEAL_SAPLINGS_TAG);
    }

    public static boolean plantSaplings(CompoundTag state) {
        return state.getBoolean(PLANT_SAPLINGS_TAG);
    }

    public static boolean pickUpDecayDrops(CompoundTag state) {
        return state.getBoolean(PICK_UP_DECAY_DROPS_TAG);
    }

    public static boolean enabled(CompoundTag state, String optionId) {
        return switch (normalize(optionId)) {
            case STRIP_LOGS -> stripLogs(state);
            case HARVEST_LEAVES -> harvestLeaves(state);
            case BONEMEAL_SAPLINGS -> bonemealSaplings(state);
            case PLANT_SAPLINGS -> plantSaplings(state);
            case PICK_UP_DECAY_DROPS -> pickUpDecayDrops(state);
            default -> false;
        };
    }

    public static ToggleResult toggle(CompoundTag state, String optionId) {
        String normalized = normalize(optionId);
        String tag = tagFor(normalized);
        if (tag.isBlank()) {
            return ToggleResult.invalidResult();
        }
        boolean enabled = !state.getBoolean(tag);
        state.putBoolean(tag, enabled);
        return new ToggleResult(normalized, enabled, false);
    }

    public static String label(String optionId) {
        return switch (normalize(optionId)) {
            case STRIP_LOGS -> "Strip logs";
            case HARVEST_LEAVES -> "Harvest leaves";
            case BONEMEAL_SAPLINGS -> "Bonemeal saplings";
            case PLANT_SAPLINGS -> "Plant saplings";
            case PICK_UP_DECAY_DROPS -> "Pick up decay drops";
            default -> "Logging option";
        };
    }

    private static void putDefault(CompoundTag state, String tag, boolean value) {
        if (!state.contains(tag, Tag.TAG_BYTE)) {
            state.putBoolean(tag, value);
        }
    }

    private static String tagFor(String optionId) {
        return switch (optionId) {
            case STRIP_LOGS -> STRIP_LOGS_TAG;
            case HARVEST_LEAVES -> HARVEST_LEAVES_TAG;
            case BONEMEAL_SAPLINGS -> BONEMEAL_SAPLINGS_TAG;
            case PLANT_SAPLINGS -> PLANT_SAPLINGS_TAG;
            case PICK_UP_DECAY_DROPS -> PICK_UP_DECAY_DROPS_TAG;
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
