package com.jvn.villagerretaliation.interaction;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;

/** Persistence boundary for the authoritative hire contract representation. */
public final class HireContractStore {
    private static final String CONTRACT_TAG = "VillagerRetaliationHireContract";
    private HireContractStore() { }

    public static Optional<HireContract> load(Villager villager) {
        return villager != null && villager.getPersistentData().get(CONTRACT_TAG) instanceof CompoundTag tag
                ? Optional.of(new HireContract(tag)) : Optional.empty();
    }

    public static boolean exists(Villager villager) {
        return villager != null && villager.getPersistentData().get(CONTRACT_TAG) instanceof CompoundTag;
    }

    public static void save(Villager villager, HireContract contract) {
        if (villager == null || contract == null) return;
        villager.getPersistentData().put(CONTRACT_TAG, contract.encoded());
        villager.setPersistenceRequired();
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        if (child == null) return;
        child.getPersistentData().remove(CONTRACT_TAG);
    }
}
