package com.jvn.villagerretaliation.mount;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** Internal mount behavior boundary; v1 deliberately has no public adapter registry. */
interface VillagerMountAdapter {
    boolean supports(Entity entity);

    boolean structurallyEligible(ServerLevel level, Entity entity);

    boolean hasUnrelatedPassengers(Entity entity, Villager assignedVillager);

    boolean tryMountDriver(Entity entity, Villager villager);

    boolean tryDismount(Entity entity, Villager villager);

    boolean isDriver(Entity entity, Villager villager);
}
