package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredJobSetting;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.nbt.CompoundTag;

public enum SettlementWorkerSetting {
    ENABLED(HiredJobSetting.ENABLED),
    USE_DEPOTS(HiredJobSetting.USE_STORAGE),
    AUTO_DEPOSIT(HiredJobSetting.AUTO_DEPOSIT),
    TILL_SOIL(HiredJobSetting.TILL_SOIL),
    STRIP_LOGS(HiredJobSetting.STRIP_LOGS),
    HARVEST_LEAVES(HiredJobSetting.HARVEST_LEAVES),
    BONEMEAL_SAPLINGS(HiredJobSetting.BONEMEAL_SAPLINGS),
    PLANT_SAPLINGS(HiredJobSetting.PLANT_SAPLINGS),
    COLLECT_DECAY(HiredJobSetting.COLLECT_DECAY),
    HUNT_ANIMALS(HiredJobSetting.HUNT_ANIMALS),
    HUNT_HOSTILES(HiredJobSetting.HUNT_HOSTILES),
    SHEAR_SHEEP(HiredJobSetting.SHEAR_SHEEP),
    CULL_CAP(HiredJobSetting.CULL_CAP),
    MINING_MODE(HiredJobSetting.MINING_MODE),
    PATCH_FLOOR(HiredJobSetting.PATCH_FLOOR),
    CRAFTING_MODE(HiredJobSetting.CRAFTING_MODE),
    GUARD_MODE(HiredJobSetting.GUARD_MODE),
    PLANT_CROPS(HiredJobSetting.PLANT_CROPS),
    HARVEST_CROPS(HiredJobSetting.HARVEST_CROPS),
    CROP_SELECTION(HiredJobSetting.CROP_SELECTION),
    BREED_ANIMALS(HiredJobSetting.BREED_ANIMALS),
    MILK_ANIMALS(HiredJobSetting.MILK_ANIMALS),
    COLLECT_ANIMAL_DROPS(HiredJobSetting.COLLECT_ANIMAL_DROPS),
    CULL_ANIMALS(HiredJobSetting.CULL_ANIMALS),
    FARM_BUCKETS(HiredJobSetting.FARM_BUCKETS);

    private final HiredJobSetting definition;

    SettlementWorkerSetting(HiredJobSetting definition) {
        this.definition = definition;
    }

    public HiredJobSetting definition() { return this.definition; }

    public boolean supports(HiredVillagerRole role) { return this.definition.supports(role); }

    public int choices() { return this.definition.choices(); }

    public int read(CompoundTag state) { return this.definition.read(state); }

    void apply(CompoundTag state, int value) { this.definition.apply(state, value); }
}
