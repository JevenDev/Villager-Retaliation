package com.jvn.villagerretaliation.mount;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** Internal mount behavior boundary; v1 deliberately has no public adapter registry. */
interface VillagerMountAdapter {
    boolean supports(Entity entity);

    boolean structurallyEligible(ServerLevel level, Entity entity);

    int seatCapacity(Entity entity);

    boolean hasUnrelatedPassengers(Entity entity, Set<UUID> assignedVillagers);

    boolean tryMountAvailableSeat(Entity entity, Villager villager);

    boolean tryDismount(Entity entity, Villager villager);

    boolean isDriver(Entity entity, Villager villager);

    boolean hasActiveRider(Entity entity);

    boolean isLeashed(Entity entity);

    boolean isPanicking(Entity entity);

    boolean moveTo(Entity entity, BlockPos target, double speed);

    void stopNavigation(Entity entity);

    void restrictTo(Entity entity, BlockPos anchor, int radius);

    void clearRestriction(Entity entity);
}
