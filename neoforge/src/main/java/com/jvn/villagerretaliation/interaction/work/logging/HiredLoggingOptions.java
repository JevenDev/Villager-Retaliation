package com.jvn.villagerretaliation.interaction.work.logging;

import com.jvn.villagerretaliation.interaction.HiredJobSetting;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

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

    private static final Option STRIP_LOGS_OPTION = new Option(STRIP_LOGS, HiredJobSetting.STRIP_LOGS, "Strip logs");
    private static final Option HARVEST_LEAVES_OPTION = new Option(HARVEST_LEAVES, HiredJobSetting.HARVEST_LEAVES, "Harvest leaves");
    private static final Option BONEMEAL_SAPLINGS_OPTION = new Option(BONEMEAL_SAPLINGS, HiredJobSetting.BONEMEAL_SAPLINGS, "Bonemeal saplings");
    private static final Option PLANT_SAPLINGS_OPTION = new Option(PLANT_SAPLINGS, HiredJobSetting.PLANT_SAPLINGS, "Plant saplings");
    private static final Option PICK_UP_DECAY_DROPS_OPTION = new Option(PICK_UP_DECAY_DROPS, HiredJobSetting.COLLECT_DECAY, "Pick up decay drops");
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
            option.setting().initializeDefault(state);
        }
    }

    public static boolean stripLogs(CompoundTag state) {
        return enabled(state, STRIP_LOGS_OPTION);
    }

    public static boolean harvestLeaves(CompoundTag state) {
        return enabled(state, HARVEST_LEAVES_OPTION);
    }

    public static boolean bonemealSaplings(CompoundTag state) {
        var queue = com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.queue(state);
        if (queue != null && queue.current().kind().forest()) return false;
        return enabled(state, BONEMEAL_SAPLINGS_OPTION);
    }

    public static boolean plantSaplings(CompoundTag state) {
        var queue = com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.queue(state);
        if (queue != null && queue.current().kind().forest()) return queue.current().kind() == com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.FOREST_REPLANT;
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
        option.setting().apply(state, enabled ? 1 : 0);
        return new ToggleResult(option.id(), enabled, false);
    }

    public static String label(String optionId) {
        Option option = OPTIONS.get(normalize(optionId));
        return option == null ? "Logging option" : option.label();
    }

    private static boolean enabled(CompoundTag state, Option option) {
        return option != null && option.setting().read(state) != 0;
    }

    private static String normalize(String optionId) {
        return optionId == null ? "" : optionId.trim().toLowerCase(Locale.ROOT);
    }

    public record ToggleResult(String optionId, boolean enabled, boolean invalid) {
        private static ToggleResult invalidResult() {
            return new ToggleResult("", false, true);
        }
    }

    private record Option(String id, HiredJobSetting setting, String label) {
    }
}
