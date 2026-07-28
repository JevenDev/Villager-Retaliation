package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.compat.rideon.VillagerRideOnCompat;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.party.PartyCommandMode;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.party.PartyVillagerRecord;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

/** Coordinates travel without owning any villager work, party, or combat navigation. */
public final class VillagerMountTravelService {
    static final double BOARD_DISTANCE_SQR = 3.0D * 3.0D;
    static final double ROLE_MOUNT_DISTANCE_SQR = 16.0D * 16.0D;
    static final double ROLE_DISMOUNT_DISTANCE_SQR = 8.0D * 8.0D;
    static final double PARK_RETURN_DISTANCE_SQR = 10.0D * 10.0D;
    static final double PARK_SETTLE_DISTANCE_SQR = 3.0D * 3.0D;
    static final int PARK_RESTRICTION_RADIUS = 8;
    private static final double APPROACH_SPEED = 0.9D;
    private static final double PARK_RETURN_SPEED = 0.8D;
    private static final long RETRY_INTERVAL_TICKS = 20L;
    private static final long PARKING_INTERVAL_TICKS = 10L;

    private VillagerMountTravelService() {
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!VillagerMountAssignmentService.featureAvailable()
                || villager == null
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        VillagerMountAssignmentSavedData data = VillagerMountAssignmentSavedData.get(level);
        VillagerMountAssignment assignment = data.forVillager(villager.getUUID()).orElse(null);
        if (assignment == null) {
            return;
        }
        Entity mount = VillagerMountEntities.loaded(level.getServer(), assignment.mountId());
        if (mount == null) {
            return;
        }
        data.updateMountLocation(mount.getUUID(), mount.level().dimension().location(), mount.blockPosition());
        if (mount.level() != level) {
            return;
        }
        VillagerMountAdapter adapter = VillagerMountAdapters.find(mount);
        if (adapter == null) {
            return;
        }
        if (VillagerDownedService.isDowned(villager)) {
            park(level, villager, mount, adapter, data);
            return;
        }

        TravelDecision decision = travelDecision(level, villager);
        if (!decision.activeContract()) {
            VillagerMountAssignmentService.clearAssignment(level, villager.getUUID());
            return;
        }
        if (!decision.wantsMount()) {
            park(level, villager, mount, adapter, data);
            return;
        }
        if (mount instanceof AbstractHorse horse
                && VillagerRideOnCompat.isRearPassenger(horse, villager)) {
            // A rear villager keeps look/combat AI, but its private on-foot navigator must not
            // compete with the controlling rider or request movement it cannot perform.
            villager.getNavigation().stop();
            alignMountedCombatLook(villager);
            return;
        }
        if (adapter.isDriver(mount, villager)) {
            data.setParkingAnchor(villager.getUUID(), null, null);
            adapter.clearRestriction(mount);
            if (decision.staying()
                    && !com.jvn.villagerretaliation.party.PartyQuickCommandService
                            .isMoveToTraveling(villager)) {
                adapter.stopNavigation(mount);
            } else {
                maintainMountedNavigationSpeed(villager);
            }
            alignMountedCombatLook(villager);
            // NeoForge delegates a controlling mob rider's navigation and move control to its
            // mob vehicle. Let the villager's normal brain drive the horse exactly as a raider
            // drives a ravager instead of maintaining a competing route here.
            return;
        }
        if (villager.getVehicle() != null) {
            return;
        }
        // Parking must not constrain the navigator once a rider wants this mount.
        adapter.clearRestriction(mount);

        if (villager.distanceToSqr(mount) <= BOARD_DISTANCE_SQR) {
            if (adapter.tryMountAvailableSeat(mount, villager)) {
                data.setParkingAnchor(villager.getUUID(), null, null);
                adapter.clearRestriction(mount);
            }
            return;
        }
        if (!isRetryTick(villager)) {
            return;
        }
        Path approach = villager.getNavigation().createPath(mount, 0);
        if (approach != null && approach.canReach()) {
            villager.getNavigation().moveTo(approach, APPROACH_SPEED);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        if (!VillagerMountAssignmentService.featureAvailable()
                || server == null
                || Math.floorMod(server.overworld().getGameTime(), PARKING_INTERVAL_TICKS) != 0L) {
            return;
        }
        maintainParking(server);
    }

    static void maintainParking(MinecraftServer server) {
        if (!VillagerMountAssignmentService.featureAvailable() || server == null) {
            return;
        }
        VillagerMountAssignmentSavedData data = VillagerMountAssignmentSavedData.get(server.overworld());
        for (UUID mountId : data.mountIds()) {
            Entity mount = VillagerMountEntities.loaded(server, mountId);
            if (mount == null) {
                continue;
            }
            data.updateMountLocation(mount.getUUID(), mount.level().dimension().location(), mount.blockPosition());
            VillagerMountAdapter adapter = VillagerMountAdapters.find(mount);
            if (adapter == null) {
                continue;
            }
            Collection<VillagerMountAssignment> mountAssignments = data.assignmentsForMountView(mountId);
            boolean wanted = false;
            for (VillagerMountAssignment mountAssignment : mountAssignments) {
                Entity assigned = VillagerMountEntities.loaded(server, mountAssignment.villagerId());
                if (assigned instanceof Villager villager
                        && villager.level() == mount.level()
                        && travelDecision((ServerLevel) villager.level(), villager).wantsMount()) {
                    wanted = true;
                    break;
                }
            }
            if (wanted) {
                adapter.clearRestriction(mount);
                continue;
            }
            if (adapter.hasActiveRider(mount) || adapter.isLeashed(mount) || adapter.isPanicking(mount)) {
                adapter.clearRestriction(mount);
                continue;
            }
            VillagerMountAssignment parked = null;
            for (VillagerMountAssignment candidate : mountAssignments) {
                if (candidate.parkingPosition() != null
                        && candidate.parkingDimension() != null
                        && candidate.parkingDimension().equals(mount.level().dimension().location())) {
                    parked = candidate;
                    break;
                }
            }
            if (parked == null) {
                continue;
            }
            BlockPos anchor = parked.parkingPosition();
            adapter.restrictTo(mount, anchor, PARK_RESTRICTION_RADIUS);
            double distance = mount.blockPosition().distSqr(anchor);
            if (distance > PARK_RETURN_DISTANCE_SQR) {
                adapter.moveTo(mount, anchor, PARK_RETURN_SPEED);
            } else if (distance <= PARK_SETTLE_DISTANCE_SQR) {
                adapter.stopNavigation(mount);
            }
        }
    }

    public static void onVillagerDowned(Villager villager) {
        if (villager != null && villager.level() instanceof ServerLevel) {
            onVillagerTickPost(villager);
        }
    }

    public static void stopAssignedMountNavigation(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        VillagerMountAssignment assignment = VillagerMountAssignmentSavedData.get(level)
                .forVillager(villager.getUUID())
                .orElse(null);
        Entity mount = assignment == null
                ? null
                : VillagerMountEntities.loaded(level.getServer(), assignment.mountId());
        VillagerMountAdapter adapter = VillagerMountAdapters.find(mount);
        if (adapter != null && adapter.isDriver(mount, villager)) {
            adapter.stopNavigation(mount);
        }
    }

    /** Lets the horse own body steering while the rider smoothly tracks its combat target. */
    public static void alignMountedCombatLook(Villager villager) {
        if (villager == null
                || !(villager.getControlledVehicle() instanceof AbstractHorse horse)
                || !(villager.getTarget() instanceof LivingEntity target)
                || !target.isAlive()) {
            return;
        }
        double deltaX = target.getX() - villager.getX();
        double deltaZ = target.getZ() - villager.getZ();
        if (deltaX * deltaX + deltaZ * deltaZ < 1.0E-6D) {
            return;
        }
        float targetYaw = (float) (Mth.atan2(deltaZ, deltaX) * 180.0D / Math.PI) - 90.0F;
        float bodyYaw = horse.yBodyRot;
        float boundedTargetYaw = bodyYaw + Mth.clamp(Mth.wrapDegrees(targetYaw - bodyYaw), -70.0F, 70.0F);
        villager.yBodyRot = bodyYaw;
        villager.yHeadRot = Mth.approachDegrees(villager.yHeadRot, boundedTargetYaw, 15.0F);
    }

    private static void maintainMountedNavigationSpeed(Villager villager) {
        if (villager.getNavigation().isDone()) {
            return;
        }
        BlockPos target = villager.getNavigation().getTargetPos();
        if (target != null) {
            villager.getNavigation().setSpeedModifier(
                    VillagerMountSpeedPolicy.toward(villager, target, VillagerMountSpeedPolicy.WALK_SPEED));
        }
    }

    static void releaseRestriction(MinecraftServer server, VillagerMountAssignment assignment) {
        Entity mount = assignment == null ? null : VillagerMountEntities.loaded(server, assignment.mountId());
        VillagerMountAdapter adapter = VillagerMountAdapters.find(mount);
        if (adapter != null) {
            adapter.clearRestriction(mount);
            adapter.stopNavigation(mount);
        }
    }

    private static TravelDecision travelDecision(ServerLevel level, Villager villager) {
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord partyVillager = party == null ? null : party.villager(villager.getUUID());
        if (partyVillager != null && PartyVillagerContractService.isActivePartyVillager(level, villager)) {
            return new TravelDecision(
                    true,
                    party.mountMode() && partyVillager.quickCommandsEnabled(),
                    partyVillager.commandMode() == PartyCommandMode.STAY);
        }
        if (!HiredVillagerContractService.isHired(level, villager)) {
            return TravelDecision.INACTIVE;
        }
        if (!HiredVillagerContractService.isMountedTravelEnabled(level, villager)) {
            return TravelDecision.ACTIVE_ON_FOOT;
        }
        BlockPos target = currentTravelTarget(villager);
        if (target == null) {
            return TravelDecision.ACTIVE_ON_FOOT;
        }
        double distance = villager.blockPosition().distSqr(target);
        boolean mounted = villager.getVehicle() != null;
        boolean wantsMount = mounted
                ? distance > ROLE_DISMOUNT_DISTANCE_SQR
                : distance >= ROLE_MOUNT_DISTANCE_SQR;
        return new TravelDecision(true, wantsMount, false);
    }

    private static BlockPos currentTravelTarget(Villager villager) {
        BlockPos walkTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget().currentBlockPosition())
                .orElse(null);
        if (walkTarget != null) {
            return walkTarget;
        }
        // For a controlling passenger, NeoForge returns the horse navigator here.
        return villager.getNavigation().isDone() ? null : villager.getNavigation().getTargetPos();
    }

    private static boolean isRetryTick(Villager villager) {
        long spread = villager.level().getGameTime() + villager.getUUID().getLeastSignificantBits();
        return Math.floorMod(spread, RETRY_INTERVAL_TICKS) == 0L;
    }

    private static void park(
            ServerLevel level,
            Villager villager,
            Entity mount,
            VillagerMountAdapter adapter,
            VillagerMountAssignmentSavedData data) {
        if (villager.getVehicle() == mount) {
            adapter.tryDismount(mount, villager);
        }
        VillagerMountAssignment current = data.forVillager(villager.getUUID()).orElse(null);
        if (current != null && (current.parkingDimension() == null || current.parkingPosition() == null)) {
            data.setParkingAnchor(villager.getUUID(), level.dimension().location(), mount.blockPosition());
        }
        if (adapter.hasActiveRider(mount) || adapter.isLeashed(mount) || adapter.isPanicking(mount)) {
            adapter.clearRestriction(mount);
            return;
        }
        VillagerMountAssignment parked = data.forVillager(villager.getUUID()).orElse(null);
        if (parked != null
                && level.dimension().location().equals(parked.parkingDimension())
                && parked.parkingPosition() != null) {
            adapter.restrictTo(mount, parked.parkingPosition(), PARK_RESTRICTION_RADIUS);
            adapter.stopNavigation(mount);
        }
    }

    private record TravelDecision(
            boolean activeContract,
            boolean wantsMount,
            boolean staying) {
        private static final TravelDecision INACTIVE = new TravelDecision(false, false, false);
        private static final TravelDecision ACTIVE_ON_FOOT = new TravelDecision(true, false, false);
    }

}
