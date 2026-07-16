package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.compat.rideon.VillagerRideOnCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;

final class AbstractHorseMountAdapter implements VillagerMountAdapter {
    @Override
    public boolean supports(Entity entity) {
        return entity instanceof AbstractHorse;
    }

    @Override
    public boolean structurallyEligible(ServerLevel level, Entity entity) {
        if (!(entity instanceof AbstractHorse horse)
                || level == null
                || horse.level() != level
                || !horse.getType().is(VillagerMountTags.ASSIGNABLE_MOUNTS)
                || !horse.isAlive()
                || horse.isBaby()) {
            return false;
        }
        return horse.getType() == EntityType.CAMEL || horse.isTamed();
    }

    @Override
    public boolean hasUnrelatedPassengers(Entity entity, Villager assignedVillager) {
        if (!(entity instanceof AbstractHorse horse)) {
            return true;
        }
        return horse.getPassengers().stream().anyMatch(passenger -> passenger != assignedVillager);
    }

    @Override
    public boolean tryMountDriver(Entity entity, Villager villager) {
        return entity instanceof AbstractHorse horse
                && VillagerRideOnCompat.tryMountDriver(horse, villager);
    }

    @Override
    public boolean tryDismount(Entity entity, Villager villager) {
        return entity instanceof AbstractHorse horse
                && VillagerRideOnCompat.tryDismount(horse, villager);
    }

    @Override
    public boolean isDriver(Entity entity, Villager villager) {
        return entity instanceof AbstractHorse horse
                && VillagerRideOnCompat.occupant(horse, false) == villager;
    }
}
