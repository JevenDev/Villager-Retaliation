package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.mount.VillagerMountSpeedPolicy;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerMovementSpeedPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.pathfinder.Path;

/** Executes recruitment movement intents without taking ownership of the villager's Brain. */
public final class VillagerCommandController {
    private static final double FOLLOW_START_DISTANCE_SQR = 1.5D * 1.5D;
    private static final double FOLLOW_STOP_DISTANCE_SQR = 0.75D * 0.75D;
    private static final double MOUNTED_FOLLOW_START_DISTANCE_SQR = 4.0D * 4.0D;
    private static final double MOUNTED_FOLLOW_STOP_DISTANCE_SQR = 3.0D * 3.0D;
    private static final double STAY_RETURN_START_DISTANCE_SQR = 2.25D * 2.25D;
    private static final double STAY_RETURN_STOP_DISTANCE_SQR = 1.25D * 1.25D;
    private static final double STAY_SPEED = VillagerMovementSpeedPolicy.WALK_SPEED_MODIFIER;
    private static final double VEHICLE_BOARD_DISTANCE_SQR = 4.0D * 4.0D;
    private static final long JOURNEY_UPDATE_INTERVAL_TICKS = 20L;
    private static final int PATH_RECALCULATION_MIN_TICKS = 4;
    private static final int PATH_RECALCULATION_RANDOM_TICKS = 7;
    private static final Map<UUID, Long> NEXT_JOURNEY_UPDATE_TICKS = new HashMap<>();
    private static final Map<UUID, PathState> PATH_STATES = new HashMap<>();
    private static final Map<UUID, BlockPos> OWNED_NAVIGATION_TARGETS = new HashMap<>();

    private VillagerCommandController() {
    }

    public static void beginFollow(ServerLevel level, Villager villager, UUID owner) {
        if (level == null || villager == null || owner == null) return;
        if (VillagerAssignmentStore.isFollowing(villager)
                && VillagerAssignmentStore.commandOwner(villager).filter(owner::equals).isPresent()
                && VillagerAssignmentStore.hasFollowJourney(villager)) {
            return;
        }
        HiredVillagerWorkService.pauseForRecruitmentCommand(level, villager);
        BlockPos start = villager.blockPosition();
        String biome = level.getBiome(start).unwrapKey()
                .map(key -> VillagerInteractionTextUtil.resourcePathName(key.location()))
                .orElse("the wilds");
        VillagerAssignmentStore.beginFollowing(villager, owner, start, biome, level.getBiome(start).is(BiomeTags.IS_OCEAN));
        com.jvn.villagerretaliation.social.VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
    }

    public static void beginStay(ServerLevel level, Villager villager, UUID owner, BlockPos anchor) {
        if (level == null || villager == null || owner == null || anchor == null) return;
        HiredVillagerWorkService.pauseForRecruitmentCommand(level, villager);
        VillagerAssignmentStore.beginStaying(villager, owner, anchor);
        stopNavigation(villager);
        com.jvn.villagerretaliation.social.VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
    }

    public static void clear(Villager villager) {
        if (villager == null) return;
        UUID villagerId = villager.getUUID();
        NEXT_JOURNEY_UPDATE_TICKS.remove(villagerId);
        PATH_STATES.remove(villagerId);
        dismount(villager);
        VillagerAssignmentStore.clearRecruitmentCommand(villager);
        stopNavigation(villager);
    }

    public static void onVillagerTickPre(Villager villager) {
        // Arbitration happens after the Brain tick so higher-priority activities can declare intent.
    }

    /** Executes one command tick and reports lifecycle changes to the coordinator. */
    public static TickResult onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return TickResult.NONE;
        UUID ownerId = VillagerAssignmentStore.commandOwner(villager).orElse(null);
        if (ownerId == null) return TickResult.NONE;
        boolean durableCommand = PartyVillagerContractService.isActivePartyVillager(level, villager)
                || VillagerAssignmentStore.snapshot(villager).state() == VillagerAssignmentState.HIRED;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (VillagerAssignmentStore.isFollowing(villager)) {
            if (owner == null) {
                stopNavigation(villager);
                return durableCommand ? TickResult.WAITING_FOR_OWNER : TickResult.OWNER_LOST;
            }
            if (owner.level() != level) {
                stopNavigation(villager);
                return durableCommand ? TickResult.WAITING_FOR_OWNER : TickResult.OWNER_CHANGED_DIMENSION;
            }
            if (!owner.isAlive() || owner.isSpectator() || !villager.isAlive()) {
                return durableCommand ? TickResult.WAITING_FOR_OWNER : TickResult.OWNER_LOST;
            }
            updateJourney(level, villager, owner);
            syncVehicle(villager, owner);
            double maxDistance = VillagerRetaliationConfig.MAX_FOLLOW_DISTANCE.get();
            if (!durableCommand && villager.distanceToSqr(owner) > maxDistance * maxDistance) {
                return TickResult.LEFT_BEHIND;
            }
        } else if (owner != null) {
            dismount(villager);
        }

        VillagerAiArbitration.Priority priority = VillagerAiArbitration.currentPriority(level, villager);
        if (priority.yieldsCommandMovement()) {
            yieldNavigation(villager);
            return TickResult.NONE;
        }
        if (VillagerAssignmentStore.isStaying(villager)) {
            maintainStay(villager);
            return TickResult.NONE;
        }
        if (owner == null || owner.level() != level) return TickResult.NONE;

        // A horse rider faces with the mount. Asking the rider to stare back at its owner makes
        // its body controller fight the saddle-facing rotation every tick.
        if (!(villager.getVehicle() instanceof AbstractHorse)) {
            villager.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        }
        if (owner.getVehicle() != null && villager.getVehicle() == owner.getVehicle()) {
            stopNavigation(villager);
            return TickResult.NONE;
        }
        Entity anchor = owner.getVehicle() == null ? owner : owner.getVehicle();
        double distanceSqr = villager.distanceToSqr(anchor);
        boolean mountedDriver = isMountedDriver(villager);
        double startDistanceSqr = mountedDriver
                ? MOUNTED_FOLLOW_START_DISTANCE_SQR
                : FOLLOW_START_DISTANCE_SQR;
        double stopDistanceSqr = mountedDriver
                ? MOUNTED_FOLLOW_STOP_DISTANCE_SQR
                : FOLLOW_STOP_DISTANCE_SQR;
        if (distanceSqr > startDistanceSqr) {
            moveToward(villager, anchor, VillagerMovementSpeedPolicy.following(distanceSqr));
        }
        else if (distanceSqr < stopDistanceSqr) stopNavigation(villager);
        return TickResult.NONE;
    }

    private static void maintainStay(Villager villager) {
        BlockPos anchor = VillagerAssignmentStore.stayAnchor(villager);
        if (anchor == null) return;
        double x = anchor.getX() + 0.5D;
        double z = anchor.getZ() + 0.5D;
        double distanceSqr = villager.distanceToSqr(x, anchor.getY(), z);
        if (distanceSqr > STAY_RETURN_START_DISTANCE_SQR) {
            villager.getLookControl().setLookAt(x, anchor.getY(), z, 20.0F, 20.0F);
            if (villager.getNavigation().moveTo(x, anchor.getY(), z, STAY_SPEED)) {
                rememberOwnedNavigationTarget(villager);
            }
        } else if (distanceSqr < STAY_RETURN_STOP_DISTANCE_SQR) {
            stopNavigation(villager);
        }
    }

    private static void updateJourney(ServerLevel level, Villager villager, ServerPlayer owner) {
        if (!TickThrottle.consume(
                villager.getUUID(), NEXT_JOURNEY_UPDATE_TICKS, level.getGameTime(), JOURNEY_UPDATE_INTERVAL_TICKS)) return;
        VillagerAssignmentStore.updateJourney(
                villager, villager.getVehicle() instanceof Boat, level.getBiome(villager.blockPosition()).is(BiomeTags.IS_OCEAN));
        VillagerReputationAdvancements.onFollowerJourneyUpdated(
                owner, VillagerAssignmentStore.journey(villager).distanceBlocks());
    }

    private static void syncVehicle(Villager villager, ServerPlayer owner) {
        if (isMountedDriver(villager)) return;
        Entity vehicle = owner.getVehicle();
        if (vehicle == null) {
            dismount(villager);
        } else if (villager.getVehicle() != vehicle) {
            dismount(villager);
            if (villager.distanceToSqr(vehicle) <= VEHICLE_BOARD_DISTANCE_SQR) villager.startRiding(vehicle);
        }
    }

    private static boolean moveToward(Villager villager, Entity target, double speed) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        PathState state = PATH_STATES.get(villager.getUUID());
        long gameTime = level.getGameTime();
        boolean targetChanged = state == null || !state.targetId().equals(target.getUUID());
        boolean targetMoved = state == null || target.distanceToSqr(state.x(), state.y(), state.z()) >= 1.0D;
        if (!targetChanged && !targetMoved && !villager.getNavigation().isDone() && gameTime < state.nextRecalculation()) {
            return true;
        }
        boolean moved;
        if (isMountedDriver(villager)) {
            AbstractHorse horse = (AbstractHorse) villager.getControlledVehicle();
            Path path = horse.getNavigation().createPath(target, 0);
            moved = path != null && horse.getNavigation().moveTo(path, VillagerMountSpeedPolicy.toward(villager, target, speed));
        } else {
            VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);
            moved = villager.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), speed);
            if (!moved) {
                moved = villager.getNavigation().moveTo(target, speed);
            }
        }
        long delay = PATH_RECALCULATION_MIN_TICKS + villager.getRandom().nextInt(PATH_RECALCULATION_RANDOM_TICKS);
        PATH_STATES.put(villager.getUUID(), new PathState(target.getUUID(), target.getX(), target.getY(), target.getZ(), gameTime + delay));
        if (moved) rememberOwnedNavigationTarget(villager);
        return moved;
    }

    private static void rememberOwnedNavigationTarget(Villager villager) {
        BlockPos target = villager.getNavigation().getTargetPos();
        if (target != null) OWNED_NAVIGATION_TARGETS.put(villager.getUUID(), target);
    }

    private static void yieldNavigation(Villager villager) {
        PATH_STATES.remove(villager.getUUID());
        BlockPos ownedTarget = OWNED_NAVIGATION_TARGETS.remove(villager.getUUID());
        if (villager.getTarget() != null && villager.getTarget().isAlive()) {
            return;
        }
        if (ownedTarget != null && ownedTarget.equals(villager.getNavigation().getTargetPos())) {
            villager.getNavigation().stop();
        }
    }

    private static boolean isMountedDriver(Villager villager) {
        return villager.getControlledVehicle() instanceof AbstractHorse;
    }

    private static void dismount(Villager villager) {
        if (villager.isPassenger()) villager.stopRiding();
    }

    private static void stopNavigation(Villager villager) {
        PATH_STATES.remove(villager.getUUID());
        BlockPos ownedTarget = OWNED_NAVIGATION_TARGETS.remove(villager.getUUID());
        if (ownedTarget != null && ownedTarget.equals(villager.getNavigation().getTargetPos())) {
            villager.getNavigation().stop();
        }
    }

    public static void clearRuntimeState() {
        NEXT_JOURNEY_UPDATE_TICKS.clear();
        PATH_STATES.clear();
        OWNED_NAVIGATION_TARGETS.clear();
    }

    public enum TickResult {
        NONE,
        LEFT_BEHIND,
        OWNER_LOST,
        OWNER_CHANGED_DIMENSION,
        WAITING_FOR_OWNER
    }

    private record PathState(UUID targetId, double x, double y, double z, long nextRecalculation) {
    }
}
