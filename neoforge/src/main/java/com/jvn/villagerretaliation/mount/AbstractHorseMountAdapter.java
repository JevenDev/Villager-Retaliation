package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.compat.rideon.VillagerRideOnCompat;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

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
    public int seatCapacity(Entity entity) {
        if (!(entity instanceof AbstractHorse horse) || !VillagerRideOnCompat.supportsDriver(horse)) {
            return 0;
        }
        return VillagerRideOnCompat.supportsPassenger(horse) ? 2 : 1;
    }

    @Override
    public boolean hasUnrelatedPassengers(Entity entity, Set<UUID> assignedVillagers) {
        if (!(entity instanceof AbstractHorse horse)) {
            return true;
        }
        return horse.getPassengers().stream()
                .anyMatch(passenger -> assignedVillagers == null || !assignedVillagers.contains(passenger.getUUID()));
    }

    @Override
    public boolean tryMountAvailableSeat(Entity entity, Villager villager) {
        return entity instanceof AbstractHorse horse
                && VillagerRideOnCompat.tryMountAvailableSeat(horse, villager);
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

    @Override
    public boolean hasActiveRider(Entity entity) {
        return entity instanceof AbstractHorse horse && horse.isVehicle();
    }

    @Override
    public boolean isLeashed(Entity entity) {
        return entity instanceof AbstractHorse horse && horse.isLeashed();
    }

    @Override
    public boolean isPanicking(Entity entity) {
        return entity instanceof AbstractHorse horse && horse.isPanicking();
    }

    @Override
    public boolean moveTo(Entity entity, BlockPos target, double speed) {
        if (!(entity instanceof AbstractHorse horse) || target == null) {
            return false;
        }
        Path path = createTravelPath(horse, target);
        return path != null && horse.getNavigation().moveTo(path, speed);
    }

    @Override
    public void stopNavigation(Entity entity) {
        if (entity instanceof AbstractHorse horse) {
            horse.getNavigation().stop();
        }
    }

    @Override
    public void restrictTo(Entity entity, BlockPos anchor, int radius) {
        if (entity instanceof AbstractHorse horse && anchor != null) {
            horse.restrictTo(anchor, radius);
        }
    }

    @Override
    public void clearRestriction(Entity entity) {
        if (entity instanceof AbstractHorse horse) {
            horse.clearRestriction();
        }
    }

    private static Path createTravelPath(AbstractHorse horse, BlockPos target) {
        Path direct = horse.getNavigation().createPath(target, 0);
        return direct != null && direct.canReach() ? direct : null;
    }
}
