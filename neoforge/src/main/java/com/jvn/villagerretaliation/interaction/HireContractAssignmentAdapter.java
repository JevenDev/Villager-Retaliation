package com.jvn.villagerretaliation.interaction;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Mirrors the authoritative contract owner and role into command assignment state. */
public final class HireContractAssignmentAdapter {
    private HireContractAssignmentAdapter() { }

    public static VillagerAssignmentSnapshot synchronize(ServerLevel level, Villager villager) {
        if (level == null || villager == null) return VillagerAssignmentSnapshot.unassigned(0L);
        VillagerAssignmentSnapshot assignment = VillagerAssignmentStore.snapshot(villager);
        HireContract contract = HireContractStore.load(villager)
                .filter(HireContract::isActiveOrAwaitingAutoPayment)
                .orElse(null);
        if (contract == null || contract.owner().isEmpty()) {
            return assignment.state() == VillagerAssignmentState.HIRED
                    ? VillagerAssignmentStore.unassign(villager)
                    : assignment;
        }
        HiredVillagerRole role = contract.role();
        if (role == null) role = HiredVillagerRoles.defaultRole(level, villager);
        if (!assignment.ownedBy(contract.owner().get())) {
            if (assignment.state() == VillagerAssignmentState.HIRED) VillagerAssignmentStore.unassign(villager);
            return VillagerAssignmentStore.hire(
                    villager, contract.owner().get(), role, contract.startGameTime(), villager.blockPosition());
        }
        if (assignment.role() != role) return VillagerAssignmentStore.setRole(villager, role);
        return assignment;
    }

    public static void roleChanged(Villager villager, HiredVillagerRole role) {
        VillagerAssignmentStore.setRole(villager, role);
    }

    public static void contractEnded(Villager villager) {
        VillagerAssignmentStore.unassign(villager);
    }
}
