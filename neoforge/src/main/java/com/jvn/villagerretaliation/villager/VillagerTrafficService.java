package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Provides short-lived, cooperative right-of-way decisions without replacing a villager's path.
 */
public final class VillagerTrafficService {
    private static final double DETECTION_RADIUS = 1.7D;
    private static final double MAX_VERTICAL_SEPARATION = 1.0D;
    private static final double MAX_LATERAL_SEPARATION = 0.85D;
    private static final double MIN_FORWARD_SEPARATION = 0.05D;
    private static final double MAX_FORWARD_SEPARATION = 1.65D;
    private static final double SAME_DIRECTION_DOT = 0.65D;
    private static final double MIN_HEADING_LENGTH_SQR = 0.01D;
    private static final double SIDESTEP_DISTANCE = 0.9D;
    private static final double SIDESTEP_REACHED_SQR = 0.08D;
    private static final double MANEUVER_RELEASE_DISTANCE_SQR = 5.0625D;
    private static final int HOLD_TICKS = 5;
    private static final int SIDESTEP_TICKS = 10;
    private static final Map<Villager, Maneuver> MANEUVERS = new WeakHashMap<>();

    private VillagerTrafficService() {
    }

    /**
     * Applies a local traffic maneuver and returns true when vanilla navigation should wait this tick.
     */
    public static boolean controlNavigation(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !canCoordinate(villager)) {
            MANEUVERS.remove(villager);
            return false;
        }

        long gameTime = level.getGameTime();
        Maneuver active = MANEUVERS.get(villager);
        if (active != null) {
            Villager conflict = level.getEntity(active.conflictId()) instanceof Villager other ? other : null;
            if (gameTime < active.expiresAt()
                    && conflict != null
                    && conflict.isAlive()
                    && villager.distanceToSqr(conflict) <= MANEUVER_RELEASE_DISTANCE_SQR) {
                return applyManeuver(level, villager, active);
            }
            MANEUVERS.remove(villager);
        }

        Vec3 heading = navigationHeading(villager);
        if (heading.lengthSqr() < MIN_HEADING_LENGTH_SQR) {
            return false;
        }

        Villager conflict = level.getEntitiesOfClass(
                        Villager.class,
                        villager.getBoundingBox().inflate(DETECTION_RADIUS, MAX_VERTICAL_SEPARATION, DETECTION_RADIUS),
                        other -> other != villager && canCoordinate(other))
                .stream()
                .filter(other -> shouldYieldTo(villager, other, heading, navigationHeading(other)))
                .min(Comparator.comparingDouble(villager::distanceToSqr))
                .orElse(null);
        if (conflict == null) {
            return false;
        }

        Vec3 sidestep = safeSidestep(level, villager, heading);
        Maneuver maneuver = new Maneuver(
                conflict.getUUID(),
                gameTime + (sidestep == null ? HOLD_TICKS : SIDESTEP_TICKS),
                sidestep);
        MANEUVERS.put(villager, maneuver);
        return applyManeuver(level, villager, maneuver);
    }

    static boolean shouldYieldTo(Villager villager, Villager other, Vec3 heading, Vec3 otherHeading) {
        if (heading.lengthSqr() < MIN_HEADING_LENGTH_SQR
                || otherHeading.lengthSqr() < MIN_HEADING_LENGTH_SQR
                || Math.abs(other.getY() - villager.getY()) > MAX_VERTICAL_SEPARATION) {
            return false;
        }

        Maneuver otherManeuver = MANEUVERS.get(other);
        if (otherManeuver != null
                && other.level() instanceof ServerLevel otherLevel
                && otherLevel.getGameTime() < otherManeuver.expiresAt()
                && otherManeuver.conflictId().equals(villager.getUUID())) {
            return false;
        }

        Vec3 offset = other.position().subtract(villager.position());
        double forward = horizontalDot(offset, heading);
        double lateral = Math.abs(horizontalCross(offset, heading));
        if (forward <= MIN_FORWARD_SEPARATION
                || forward > MAX_FORWARD_SEPARATION
                || lateral > MAX_LATERAL_SEPARATION) {
            return false;
        }

        double alignment = horizontalDot(heading, otherHeading);
        if (alignment >= SAME_DIRECTION_DOT) {
            // On a shared route the trailing villager always yields to the one already ahead.
            return true;
        }

        // At head-on and crossing conflicts, UUID order is a stable, symmetric stop-sign tiebreaker.
        return villager.getUUID().compareTo(other.getUUID()) > 0;
    }

    private static boolean applyManeuver(ServerLevel level, Villager villager, Maneuver maneuver) {
        Vec3 sidestep = maneuver.sidestep();
        if (sidestep != null
                && horizontalDistanceSqr(villager.position(), sidestep) > SIDESTEP_REACHED_SQR
                && canOccupy(level, villager, sidestep)) {
            villager.getMoveControl().setWantedPosition(
                    sidestep.x,
                    villager.getY(),
                    sidestep.z,
                    Math.max(0.35D, villager.getMoveControl().getSpeedModifier()));
            return true;
        }

        villager.getMoveControl().setWantedPosition(
                villager.getX(),
                villager.getY(),
                villager.getZ(),
                0.0D);
        villager.setSpeed(0.0F);
        villager.setXxa(0.0F);
        villager.setZza(0.0F);
        Vec3 motion = villager.getDeltaMovement();
        villager.setDeltaMovement(motion.x * 0.25D, motion.y, motion.z * 0.25D);
        return true;
    }

    static Vec3 safeSidestep(ServerLevel level, Villager villager, Vec3 heading) {
        Vec3 right = new Vec3(-heading.z, 0.0D, heading.x);
        Vec3 origin = villager.position();
        Vec3 first = origin.add(right.scale(SIDESTEP_DISTANCE));
        if (canOccupy(level, villager, first)) {
            return first;
        }
        Vec3 second = origin.subtract(right.scale(SIDESTEP_DISTANCE));
        return canOccupy(level, villager, second) ? second : null;
    }

    private static boolean canOccupy(ServerLevel level, Villager villager, Vec3 target) {
        double dx = target.x - villager.getX();
        double dz = target.z - villager.getZ();
        AABB moved = villager.getBoundingBox().move(dx, 0.0D, dz).deflate(0.03D);
        if (!level.noCollision(villager, moved)) {
            return false;
        }

        BlockPos feet = BlockPos.containing(target.x, villager.getY() + 0.05D, target.z);
        BlockPos floor = feet.below();
        return level.getFluidState(feet).isEmpty()
                && level.getFluidState(floor).isEmpty()
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    private static boolean canCoordinate(Villager villager) {
        if (!villager.isAlive()
                || VillagerDownedService.isDowned(villager)
                || VillagerRecoveryService.isForcingRecovery(villager)
                || DuelService.isParticipant(villager)
                || villager.isTrading()
                || VillagerConversationService.isConversing(villager)
                || villager.isSleeping()
                || villager.isPassenger()
                || villager.isVehicle()
                || villager.isInWaterOrBubble()
                || villager.isInLava()
                || villager.isOnFire()
                || !villager.onGround()
                || villager.fallDistance > 0.5F
                || villager.getTarget() != null) {
            return false;
        }
        if (villager.getBrain().isActive(Activity.PANIC)
                || villager.getBrain().isActive(Activity.HIDE)
                || villager.getBrain().isActive(Activity.PRE_RAID)
                || villager.getBrain().isActive(Activity.RAID)) {
            return false;
        }
        PathNavigation navigation = villager.getNavigation();
        return navigation != null && !navigation.isDone() && navigation.getPath() != null;
    }

    private static Vec3 navigationHeading(Villager villager) {
        Path path = villager.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return Vec3.ZERO;
        }
        Vec3 target = path.getNextEntityPos(villager);
        Vec3 heading = new Vec3(target.x - villager.getX(), 0.0D, target.z - villager.getZ());
        if (heading.lengthSqr() < MIN_HEADING_LENGTH_SQR
                && path.getNextNodeIndex() + 1 < path.getNodeCount()) {
            target = path.getEntityPosAtNode(villager, path.getNextNodeIndex() + 1);
            heading = new Vec3(target.x - villager.getX(), 0.0D, target.z - villager.getZ());
        }
        return heading.lengthSqr() < MIN_HEADING_LENGTH_SQR ? Vec3.ZERO : heading.normalize();
    }

    private static double horizontalDot(Vec3 first, Vec3 second) {
        return first.x * second.x + first.z * second.z;
    }

    private static double horizontalCross(Vec3 first, Vec3 second) {
        return first.x * second.z - first.z * second.x;
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }

    private record Maneuver(UUID conflictId, long expiresAt, Vec3 sidestep) {
    }
}
