package com.jvn.villagerretaliation.interaction;

import java.util.Optional;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.Villager;

/** Persistence boundary for the authoritative hire contract representation. */
public final class HireContractStore {
    private static final String CONTRACT_TAG = "VillagerRetaliationHireContract";
    private HireContractStore() { }

    public static Optional<HireContract> load(Villager villager) {
        if (!exists(villager)) return Optional.empty();
        return Optional.of(new HireContract(villager.getPersistentData().getCompound(CONTRACT_TAG)));
    }

    public static boolean exists(Villager villager) {
        return villager != null && villager.getPersistentData().contains(CONTRACT_TAG, Tag.TAG_COMPOUND);
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
