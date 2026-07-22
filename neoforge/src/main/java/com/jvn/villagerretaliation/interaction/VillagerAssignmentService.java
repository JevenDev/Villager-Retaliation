package com.jvn.villagerretaliation.interaction;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;

/** Compatibility facade. New persistence belongs in {@link VillagerAssignmentStore}. */
@Deprecated(forRemoval = false)
public final class VillagerAssignmentService {
    private VillagerAssignmentService() {
    }

    public static VillagerAssignmentSnapshot snapshot(Villager villager) {
        return VillagerAssignmentStore.snapshot(villager);
    }

    public static VillagerAssignmentSnapshot hire(
            Villager villager, UUID owner, HiredVillagerRole role, long hiredAt, BlockPos homeAnchor) {
        return VillagerAssignmentStore.hire(villager, owner, role, hiredAt, homeAnchor);
    }

    public static VillagerAssignmentSnapshot setCommand(
            Villager villager, UUID owner, VillagerAssignmentCommand command, BlockPos anchor) {
        return VillagerAssignmentStore.setCommand(villager, owner, command, anchor);
    }

    public static VillagerAssignmentSnapshot setRole(Villager villager, HiredVillagerRole role) {
        return VillagerAssignmentStore.setRole(villager, role);
    }

    public static VillagerAssignmentSnapshot unassign(Villager villager) {
        return VillagerAssignmentStore.unassign(villager);
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        VillagerAssignmentStore.clearInheritedStateForNewborn(child);
    }
}
