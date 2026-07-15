package com.jvn.villagerretaliation.interaction.work.logging;

import java.util.Locale;
import java.util.Map;
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

    private static final Option STRIP_LOGS_OPTION = new Option(STRIP_LOGS, STRIP_LOGS_TAG, "Strip logs", false);
    private static final Option HARVEST_LEAVES_OPTION = new Option(HARVEST_LEAVES, HARVEST_LEAVES_TAG, "Harvest leaves", false);
    private static final Option BONEMEAL_SAPLINGS_OPTION = new Option(BONEMEAL_SAPLINGS, BONEMEAL_SAPLINGS_TAG, "Bonemeal saplings", false);
    private static final Option PLANT_SAPLINGS_OPTION = new Option(PLANT_SAPLINGS, PLANT_SAPLINGS_TAG, "Plant saplings", false);
    private static final Option PICK_UP_DECAY_DROPS_OPTION = new Option(PICK_UP_DECAY_DROPS, PICK_UP_DECAY_DROPS_TAG, "Pick up decay drops", true);
    private static final Map<String, Option> OPTIONS = Map.of(
            STRIP_LOGS, STRIP_LOGS_OPTION,
            HARVEST_LEAVES, HARVEST_LEAVES_OPTION,
            BONEMEAL_SAPLINGS, BONEMEAL_SAPLINGS_OPTION,
            PLANT_SAPLINGS, PLANT_SAPLINGS_OPTION,
            PICK_UP_DECAY_DROPS, PICK_UP_DECAY_DROPS_OPTION);

    private HiredLoggingOptions() {
    }

    public static void initializeDefaults(CompoundTag state) {
        for (Option option : OPTIONS.values()) {
            putDefault(state, option.tag(), option.defaultEnabled());
        }
    }

    public static boolean stripLogs(CompoundTag state) {
        return enabled(state, STRIP_LOGS_OPTION);
    }

    public static boolean harvestLeaves(CompoundTag state) {
        return enabled(state, HARVEST_LEAVES_OPTION);
    }

    public static boolean bonemealSaplings(CompoundTag state) {
        return enabled(state, BONEMEAL_SAPLINGS_OPTION);
    }

    public static boolean plantSaplings(CompoundTag state) {
        return enabled(state, PLANT_SAPLINGS_OPTION);
    }

    public static boolean pickUpDecayDrops(CompoundTag state) {
        return enabled(state, PICK_UP_DECAY_DROPS_OPTION);
    }

    public static boolean enabled(CompoundTag state, String optionId) {
        return enabled(state, OPTIONS.get(normalize(optionId)));
    }

    public static ToggleResult toggle(CompoundTag state, String optionId) {
        Option option = OPTIONS.get(normalize(optionId));
        if (option == null) {
            return ToggleResult.invalidResult();
        }
        boolean enabled = !enabled(state, option);
        state.putBoolean(option.tag(), enabled);
        return new ToggleResult(option.id(), enabled, false);
    }

    public static String label(String optionId) {
        Option option = OPTIONS.get(normalize(optionId));
        return option == null ? "Logging option" : option.label();
    }

    private static boolean enabled(CompoundTag state, Option option) {
        return option != null && state.getBoolean(option.tag());
    }

    private static void putDefault(CompoundTag state, String tag, boolean value) {
        if (!state.contains(tag, Tag.TAG_BYTE)) {
            state.putBoolean(tag, value);
        }
    }

    private static String normalize(String optionId) {
        return optionId == null ? "" : optionId.trim().toLowerCase(Locale.ROOT);
    }

    public record ToggleResult(String optionId, boolean enabled, boolean invalid) {
        private static ToggleResult invalidResult() {
            return new ToggleResult("", false, true);
        }
    }

    private record Option(String id, String tag, String label, boolean defaultEnabled) {
    }
}
