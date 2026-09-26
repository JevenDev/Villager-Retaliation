package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredCombatMode;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.CraftsmanWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalOptions;
import net.minecraft.nbt.CompoundTag;

public enum SettlementWorkerSetting {
    ENABLED("Enabled", 1, null),
    USE_DEPOTS("UseAssignedStorageForSupplies", 1, null),
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
    GUARD_MODE(HiredCombatMode.STATE_TAG, 0, HiredVillagerRole.COMBAT);

    private final String tag;
    private final int defaultValue;
    private final HiredVillagerRole role;

    SettlementWorkerSetting(String tag, int defaultValue, HiredVillagerRole role) {
        this.tag = tag;
        this.defaultValue = defaultValue;
        this.role = role;
    }

    public boolean supports(HiredVillagerRole role) {
        if ((this == USE_DEPOTS || this == AUTO_DEPOSIT) && (role == HiredVillagerRole.COURIER || role == HiredVillagerRole.NITWIT)) {
            return false;
        }
        return this.role == null || this.role == role;
    }

    public int choices() {
        return switch (this) {
            case CULL_CAP -> HiredAnimalCullSettings.capOptions().length + 1;
            case MINING_MODE -> HiredMiningMode.values().length;
            case CRAFTING_MODE -> CraftsmanWorker.Mode.values().length;
            case GUARD_MODE -> 2;
            default -> 2;
        };
    }

    public int read(CompoundTag state) {
        return switch (this) {
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
            case MINING_MODE -> HiredMiningMode.fromState(state).ordinal();
            case CRAFTING_MODE -> CraftsmanWorker.mode(state).ordinal();
            case GUARD_MODE -> HiredCombatMode.fromState(state) == HiredCombatMode.GUARD ? 0 : 1;
            case HUNT_ANIMALS -> HiredHuntingTargets.fromState(state).animals() ? 1 : 0;
            case HUNT_HOSTILES -> HiredHuntingTargets.fromState(state).hostiles() ? 1 : 0;
            default -> state.contains(this.tag) ? state.getBoolean(this.tag) ? 1 : 0 : this.defaultValue;
        };
    }

    void apply(CompoundTag state, int value) {
        if (value < 0 || value >= choices()) {
            throw new IllegalArgumentException("Invalid settlement worker option");
        }
        switch (this) {
            case CULL_CAP -> HiredAnimalCullSettings.setCap(state, value == 0 ? 0 : HiredAnimalCullSettings.capOptions()[value - 1]);
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
