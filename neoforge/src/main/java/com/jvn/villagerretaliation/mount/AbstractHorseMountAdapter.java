package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.compat.rideon.VillagerRideOnCompat;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

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
    public boolean hasGroundPath(Entity entity, BlockPos target) {
        if (!(entity instanceof AbstractHorse horse) || target == null) {
            return false;
        }
        return createTravelPath(horse, target) != null;
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
    public boolean isNavigationDone(Entity entity) {
        return !(entity instanceof AbstractHorse horse) || horse.getNavigation().isDone();
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
        if (direct != null && direct.canReach()) {
            return direct;
        }

        Vec3 destination = Vec3.atBottomCenterOf(target);
        Vec3 from = horse.position();
        Vec3 horizontal = new Vec3(destination.x - from.x, 0.0D, destination.z - from.z);
        if (horizontal.lengthSqr() > 1.0D) {
            Vec3 segment = from.add(horizontal.normalize().scale(Math.min(12.0D, horizontal.length())));
            Path segmentPath = horse.getNavigation().createPath(
                    BlockPos.containing(segment.x, destination.y, segment.z), 0);
            if (segmentPath != null && segmentPath.canReach()) {
                return segmentPath;
            }
        }

        Vec3 waypoint = DefaultRandomPos.getPosTowards(horse, 16, 7, destination, (float) (Math.PI / 10.0D));
        if (waypoint == null) {
            waypoint = DefaultRandomPos.getPosTowards(horse, 8, 7, destination, (float) (Math.PI / 2.0D));
        }
        Path waypointPath = waypoint == null
                ? null
                : horse.getNavigation().createPath(BlockPos.containing(waypoint), 0);
        if (waypointPath != null && waypointPath.canReach()) {
            return waypointPath;
        }
        return direct != null && direct.getNodeCount() > 1 ? direct : null;
    }
}
