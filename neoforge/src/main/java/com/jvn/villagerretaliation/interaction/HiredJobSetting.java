package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.work.CraftsmanWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public enum HiredJobSetting {
    ENABLED("Enabled", 1, null),
    USE_STORAGE("UseAssignedStorageForSupplies", 1, null),
    AUTO_DEPOSIT("AutoDepositOutputs", 1, null),
    TILL_SOIL(HiredFarmingOptions.TILL_SOIL_TAG, 1, HiredVillagerRole.FARMING),
    STRIP_LOGS(HiredLoggingOptions.STRIP_LOGS_TAG, 0, HiredVillagerRole.LOGGING),
    HARVEST_LEAVES(HiredLoggingOptions.HARVEST_LEAVES_TAG, 0, HiredVillagerRole.LOGGING),
    BONEMEAL_SAPLINGS(HiredLoggingOptions.BONEMEAL_SAPLINGS_TAG, 0, HiredVillagerRole.LOGGING),
    PLANT_SAPLINGS(HiredLoggingOptions.PLANT_SAPLINGS_TAG, 1, HiredVillagerRole.LOGGING),
    COLLECT_DECAY(HiredLoggingOptions.PICK_UP_DECAY_DROPS_TAG, 1, HiredVillagerRole.LOGGING),
    HUNT_ANIMALS(HiredHuntingTargets.HUNT_ANIMALS_TAG, 1, HiredVillagerRole.HUNTING),
    HUNT_HOSTILES(HiredHuntingTargets.HUNT_HOSTILES_TAG, 0, HiredVillagerRole.HUNTING),
    SHEAR_SHEEP(HiredAnimalHandlingOptions.SHEAR_SHEEP_TAG, 1, HiredVillagerRole.ANIMAL_HANDLING),
    CULL_CAP("AnimalCullCap", 0, HiredVillagerRole.ANIMAL_HANDLING),
    MINING_MODE(HiredMiningMode.STATE_TAG, 0, HiredVillagerRole.MINING),
    PATCH_FLOOR(MiningHorizontalOptions.PATCH_FLOOR_TAG, 1, HiredVillagerRole.MINING),
    CRAFTING_MODE("CraftsmanMode", 0, HiredVillagerRole.CRAFTSMAN),
    GUARD_MODE(HiredCombatMode.STATE_TAG, 0, HiredVillagerRole.COMBAT),
    PLANT_CROPS(HiredFarmingOptions.PLANT_CROPS_TAG, 1, HiredVillagerRole.FARMING),
    HARVEST_CROPS(HiredFarmingOptions.HARVEST_CROPS_TAG, 1, HiredVillagerRole.FARMING),
    CROP_SELECTION(HiredFarmingOptions.CROP_TAG, 0, HiredVillagerRole.FARMING),
    BREED_ANIMALS(HiredAnimalHandlingOptions.BREED_TAG, 1, HiredVillagerRole.ANIMAL_HANDLING),
    MILK_ANIMALS(HiredAnimalHandlingOptions.MILK_TAG, 1, HiredVillagerRole.ANIMAL_HANDLING),
    COLLECT_ANIMAL_DROPS(HiredAnimalHandlingOptions.COLLECT_TAG, 1, HiredVillagerRole.ANIMAL_HANDLING),
    CULL_ANIMALS(HiredAnimalHandlingOptions.CULL_TAG, 1, HiredVillagerRole.ANIMAL_HANDLING),
    FARM_BUCKETS("FarmingBuckets", 0, HiredVillagerRole.FARMING);

    private final String tag;
    private final int defaultValue;
    private final HiredVillagerRole role;

    HiredJobSetting(String tag, int defaultValue, HiredVillagerRole role) {
        this.tag = tag;
        this.defaultValue = defaultValue;
        this.role = role;
    }

    public boolean supports(HiredVillagerRole role) {
        if ((this == USE_STORAGE || this == AUTO_DEPOSIT) && (role == HiredVillagerRole.COURIER || role == HiredVillagerRole.NITWIT)) {
            return false;
        }
        return this.role == null || this.role == role;
    }

    public int choices() {
        return switch (this) {
            case FARM_BUCKETS -> 4;
            case CULL_CAP -> HiredAnimalCullSettings.capOptions().length + 1;
            case MINING_MODE -> HiredMiningMode.values().length;
            case CRAFTING_MODE -> CraftsmanWorker.Mode.values().length;
            case GUARD_MODE -> 2;
            case CROP_SELECTION -> HiredFarmingOptions.Crop.values().length;
            default -> 2;
        };
    }

    public int read(CompoundTag state) {
        return switch (this) {
            case FARM_BUCKETS -> Math.clamp(state.getInt(this.tag), 0, 3);
            case CULL_CAP -> {
                int cap = HiredAnimalCullSettings.cap(state);
                int[] options = HiredAnimalCullSettings.capOptions();
                int value = 0;
                for (int index = 0; index < options.length; index++) {
                    if (options[index] == cap) {
                        value = index + 1;
                        break;
                    }
                }
                yield value;
            }
            case CROP_SELECTION -> HiredFarmingOptions.crop(state).ordinal();
            case MINING_MODE -> HiredMiningMode.fromState(state).ordinal();
            case CRAFTING_MODE -> CraftsmanWorker.mode(state).ordinal();
            case GUARD_MODE -> HiredCombatMode.fromState(state) == HiredCombatMode.GUARD ? 0 : 1;
            case HUNT_ANIMALS -> HiredHuntingTargets.fromState(state).animals() ? 1 : 0;
            case HUNT_HOSTILES -> HiredHuntingTargets.fromState(state).hostiles() ? 1 : 0;
            default -> state.contains(this.tag, Tag.TAG_BYTE) ? state.getBoolean(this.tag) ? 1 : 0 : this.defaultValue;
        };
    }

    public boolean valid(HiredVillagerRole role, int value) {
        return supports(role) && value >= 0 && value < choices();
    }

    public void initializeDefault(CompoundTag state) {
        int type = switch (this) {
            case CULL_CAP, FARM_BUCKETS -> Tag.TAG_INT;
            case CROP_SELECTION, MINING_MODE, CRAFTING_MODE, GUARD_MODE -> Tag.TAG_STRING;
            default -> Tag.TAG_BYTE;
        };
        if (!state.contains(this.tag, type)) apply(state, read(state));
    }

    public void apply(CompoundTag state, HiredVillagerRole role, int value) {
        if (!valid(role, value)) throw new IllegalArgumentException("Worker option does not match its role or range");
        apply(state, value);
    }

    public void apply(CompoundTag state, int value) {
        if (value < 0 || value >= choices()) {
            throw new IllegalArgumentException("Invalid worker option");
        }
        switch (this) {
            case FARM_BUCKETS -> state.putInt(this.tag, value);
            case CULL_CAP -> HiredAnimalCullSettings.setCap(state, value == 0 ? 0 : HiredAnimalCullSettings.capOptions()[value - 1]);
            case CROP_SELECTION -> state.putString(this.tag, HiredFarmingOptions.Crop.values()[value].name());
            case MINING_MODE -> state.putString(this.tag, HiredMiningMode.values()[value].serializedName());
            case CRAFTING_MODE -> {
                if (read(state) != value) {
                    state.putInt("CraftsmanCursor", 0);
                }
                state.putString(this.tag, CraftsmanWorker.Mode.values()[value].id());
            }
            case GUARD_MODE -> state.putString(this.tag, HiredCombatMode.values()[value].serializedName());
            default -> state.putBoolean(this.tag, value != 0);
        }
    }
}
