package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorker;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public record HiredWorkSession(
        CompoundTag state,
        HiredVillagerRole role,
        HiredRoleWorker worker,
        HiredJobInventory inventory,
        int maxRadius,
        HiredWorkArea area,
        HiredJobSite jobSite,
        int efficiency,
        HiredWorkContext context) {
    public static HiredWorkSession active(ServerLevel level, Villager villager) {
        return create(level, villager, HiredVillagerContractService.activeRole(level, villager));
    }

    public static HiredWorkSession create(ServerLevel level, Villager villager, HiredVillagerRole role) {
        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        HiredVillagerRole safeRole = role == null ? HiredVillagerRoles.defaultRole(level, villager) : role;
        HiredRoleWorker worker = HiredRoleWorkerRegistry.get(safeRole);
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        int roleScore = HiredVillagerRoles.roleScore(level, villager, safeRole);
        int maxRadius = HiredVillagerWorkService.maxWorkRadius(level, villager, safeRole, roleScore);
        HiredJobSite jobSite = HiredVillagerWorkService.jobSite(level, villager, safeRole, state, maxRadius);
        HiredWorkArea area = jobSite.workArea();
        int efficiency = HiredVillagerWorkService.efficiencyPercent(level, villager, safeRole, state, inventory, roleScore);
        HiredWorkContext context = new HiredWorkContext(
                inventory,
                state,
                area.center(),
                area.min(),
                area.max(),
                area.horizontalRadius(),
                area.verticalRadius(),
                area.usable(),
                efficiency,
                state.getBoolean("AutoDepositOutputs"),
                state.getBoolean("UseAssignedStorageForSupplies"),
                jobSite);
        return new HiredWorkSession(state, safeRole, worker, inventory, maxRadius, area, jobSite, efficiency, context);
    }
}
