package com.jvn.villagerretaliation.villager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class VillagerLadderTraversal {
    private static final double LADDER_ENTRY_DISTANCE_SQR = 2.25D;
    // A villager approaching a ladder from the adjacent block can stop with its
    // bounding box against the rung at about one block from its center. Leave a
    // small floating-point margin around that boundary while remaining well
    // inside the adjacent-block entry gate.
    private static final double LADDER_FORCED_ENTRY_HORIZONTAL_SQR = 1.21D;
    private static final int LADDER_VERTICAL_TARGET_DEADZONE = 0;
    private static final double LADDER_HORIZONTAL_SPEED_LIMIT = 0.15D;
    private static final double LADDER_CLIMB_SPEED = 0.20D;
    private static final double LADDER_DESCEND_SPEED = -0.15D;
    private static final double LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT = 0.18D;
    private static final double LADDER_DISMOUNT_UPWARD_SPEED = 0.10D;
    private static final double LADDER_TOP_DISMOUNT_Y_OFFSET = 1.0D;
    private static final double LADDER_TOP_HEAD_DISMOUNT_OFFSET = 0.75D;
    private static final double LADDER_BOTTOM_DISMOUNT_Y_OFFSET = 0.15D;
    private static final double LADDER_DISMOUNT_REACHED_DISTANCE_SQR = 1.0D;
    private static final long ACTIVE_LADDER_CLIMB_TICKS = 80L;
    private static final long RECENT_LADDER_DISMOUNT_TICKS = 60L;
    private static final int RECENT_LADDER_SETTLE_CHECKS = 20;
    private static final double RECENT_LADDER_DISMOUNT_DISTANCE_SQR = 16.0D;
    private static final double ACTIVE_LADDER_COLUMN_DISTANCE_SQR = 2.25D;
    private static final int LADDER_ESCAPE_SEARCH_RADIUS = 12;
    private static final int LADDER_ESCAPE_VERTICAL_RADIUS = 24;
    private static final int LADDER_SEARCH_CACHE_TICKS = 20;
    private static final double LADDER_SEARCH_ORIGIN_REUSE_DISTANCE_SQR = 16.0D;
    private static final double LADDER_ROUTE_PROGRESS_EPSILON_SQR = 0.10D;
    private static final int LADDER_ROUTE_STALLED_CHECKS = 20;
    private static final double LADDER_DIRECT_APPROACH_SPEED_LIMIT = 0.16D;
    private static final Map<UUID, ActiveLadderClimb> ACTIVE_LADDER_CLIMBS = new HashMap<>();
    private static final Map<UUID, RecentLadderDismount> RECENT_LADDER_DISMOUNTS = new HashMap<>();
    private static final Map<UUID, LadderSearch> LADDER_SEARCHES = new HashMap<>();
    private VillagerLadderTraversal() {
    }

    public static void tickPathLadders(ServerLevel level, Villager villager) {
        if (settleRecentLadderDismount(level, villager)) {
            return;
        }
        BlockPos target = villager.getNavigation().isDone() ? null : villager.getNavigation().getTargetPos();
        if (continueActiveLadderClimb(level, villager, target, 0.45D)) {
            return;
        }
        if (target == null || !needsLadderRoute(villager, target)) {
            return;
        }
        if (shouldAvoidRecentLadderDismount(level, villager, target)) {
            return;
        }
        Path path = villager.getNavigation().getPath();
        Node previousNode = path != null && !path.isDone() ? path.getPreviousNode() : null;
        Node nextNode = path != null && !path.isDone() ? path.getNextNode() : null;
        if (isStandingInLadder(level, villager.blockPosition())
                || isLadderPathNode(level, previousNode)
                || isLadderPathNode(level, nextNode)) {
            BlockPos ladder = ladderTouching(level, villager.blockPosition(), target);
            if (ladder == null) {
                ladder = isLadderPathNode(level, nextNode) ? nextNode.asBlockPos() : previousNode.asBlockPos();
            }
            beginActiveLadderClimb(level, villager, ladder, target);
            continueActiveLadderClimb(level, villager, target, 0.45D);
        }
    }
    private static double horizontalDistanceSqr(Villager villager, BlockPos target) {
        double dx = villager.getX() - (target.getX() + 0.5D);
        double dz = villager.getZ() - (target.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }
    public static boolean moveOnLadderToward(ServerLevel level, Villager villager, BlockPos target, double speed) {
        if (target == null) {
            return false;
        }
        if (shouldAvoidRecentLadderDismount(level, villager, target)) {
            return false;
        }
        BlockPos ladder = ladderTouching(level, villager.blockPosition(), target);
        if (ladder == null) {
            return false;
        }
        if (!needsLadderRoute(villager, target)) {
            return moveOffTouchedLadderToward(level, villager, ladder, target, speed);
        }
        beginActiveLadderClimb(level, villager, ladder, target);
        boolean climbingUp = target.getY() >= villager.blockPosition().getY();
        return moveOnSpecificLadderToward(level, villager, ladder, target, climbingUp, speed);
    }
    private static boolean moveOnSpecificLadderToward(
            ServerLevel level,
            Villager villager,
            BlockPos ladder,
            BlockPos target,
            boolean climbingUp,
            double speed) {
        double centerX = ladder.getX() + 0.5D;
        double centerZ = ladder.getZ() + 0.5D;
        double dx = centerX - villager.getX();
        double dz = centerZ - villager.getZ();
        if (!isStandingInLadder(level, villager.blockPosition())) {
            double horizontalDistanceSqr = dx * dx + dz * dz;
            if (horizontalDistanceSqr <= LADDER_FORCED_ENTRY_HORIZONTAL_SQR
                    && needsLadderRoute(villager, target)) {
                forceEnterAndClimbLadder(villager, ladder, target, speed);
                return true;
            }
            villager.getMoveControl().setWantedPosition(centerX, villager.getY(), centerZ, speed);
            return true;
        }

        int verticalDelta = target.getY() - villager.blockPosition().getY();
        BlockPos columnEnd = Math.abs(verticalDelta) <= LADDER_VERTICAL_TARGET_DEADZONE
                ? ladder
                : (climbingUp ? topOfLadderColumn(level, ladder) : bottomOfLadderColumn(level, ladder));
        BlockPos dismount = safeLadderDismount(level, villager, columnEnd, target, climbingUp);
        double ladderExitY = ladderExitY(columnEnd, dismount, climbingUp);
        if (dismount != null && reachedLadderBlockEnd(villager, columnEnd, climbingUp)
                && snapToLadderDismount(level, villager, ladder, dismount)) {
            return true;
        }
        if (dismount != null && reachedLadderExitHeight(villager, ladderExitY, climbingUp)) {
            moveOffLadderToDismount(level, villager, columnEnd, dismount, climbingUp, speed);
            return true;
        }
        if (dismount == null && reachedLadderColumnEnd(villager, columnEnd, climbingUp)) {
            clearActiveLadderClimb(villager);
            villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
            villager.getMoveControl().setWantedPosition(columnEnd.getX() + 0.5D, villager.getY(), columnEnd.getZ() + 0.5D, speed);
            return false;
        }

        double yDelta = ladderExitY - villager.getY();
        double climb = yDelta >= 0.0D ? LADDER_CLIMB_SPEED : LADDER_DESCEND_SPEED;
        villager.setNoGravity(true);
        villager.move(MoverType.SELF, new Vec3(0.0D, climb, 0.0D));
        villager.setDeltaMovement(Vec3.ZERO);
        villager.setOnGround(false);
        return true;
    }
    private static double ladderExitY(BlockPos columnEnd, BlockPos dismount, boolean climbingUp) {
        if (dismount != null) {
            return dismount.getY();
        }
        return climbingUp
                ? columnEnd.getY() + LADDER_TOP_DISMOUNT_Y_OFFSET
                : columnEnd.getY() + LADDER_BOTTOM_DISMOUNT_Y_OFFSET;
    }
    private static boolean reachedLadderExitHeight(Villager villager, double ladderExitY, boolean climbingUp) {
        return climbingUp
                ? villager.getY() >= ladderExitY - 0.05D
                : villager.getY() <= ladderExitY + 0.15D;
    }
    private static void moveOffLadderToDismount(
            ServerLevel level,
            Villager villager,
            BlockPos ladder,
            BlockPos dismount,
            boolean climbingUp,
            double speed) {
        double targetX = dismount.getX() + 0.5D;
        double targetZ = dismount.getZ() + 0.5D;
        double dx = targetX - villager.getX();
        double dz = targetZ - villager.getZ();
        boolean stillOnLadder = isStandingInLadder(level, villager.blockPosition());
        villager.getMoveControl().setWantedPosition(targetX, dismount.getY(), targetZ, speed);
        if (!stillOnLadder && villager.distanceToSqr(dismount.getCenter()) <= LADDER_DISMOUNT_REACHED_DISTANCE_SQR) {
            snapToLadderDismount(level, villager, ladder, dismount);
            return;
        }

        Vec3 motion = villager.getDeltaMovement();
        if (!climbingUp && stillOnLadder) {
            if (villager.getY() <= ladder.getY() + 0.35D
                    && snapToLadderDismount(level, villager, ladder, dismount)) {
                return;
            }
            double ladderCenterX = ladder.getX() + 0.5D;
            double ladderCenterZ = ladder.getZ() + 0.5D;
            double ladderDx = ladderCenterX - villager.getX();
            double ladderDz = ladderCenterZ - villager.getZ();
            villager.getMoveControl().setWantedPosition(ladderCenterX, ladder.getY(), ladderCenterZ, speed);
            villager.setNoGravity(true);
            villager.setDeltaMovement(
                    Math.clamp(ladderDx * 0.24D + motion.x * 0.15D, -LADDER_HORIZONTAL_SPEED_LIMIT, LADDER_HORIZONTAL_SPEED_LIMIT),
                    Math.clamp((ladder.getY() - villager.getY()) * 0.2D, -0.08D, -0.02D),
                    Math.clamp(ladderDz * 0.24D + motion.z * 0.15D, -LADDER_HORIZONTAL_SPEED_LIMIT, LADDER_HORIZONTAL_SPEED_LIMIT));
            villager.setOnGround(false);
            return;
        }

        double vertical = motion.y;
        if (stillOnLadder) {
            vertical = climbingUp
                    ? Math.max(motion.y, LADDER_DISMOUNT_UPWARD_SPEED)
                    : Math.clamp((dismount.getY() - villager.getY()) * 0.2D, -0.02D, 0.04D);
        }
        villager.setNoGravity(stillOnLadder);
        villager.setDeltaMovement(
                Math.clamp(dx * 0.28D + motion.x * 0.2D, -LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT, LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT),
                vertical,
                Math.clamp(dz * 0.28D + motion.z * 0.2D, -LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT, LADDER_DISMOUNT_HORIZONTAL_SPEED_LIMIT));
        villager.setOnGround(false);
    }
    private static boolean snapToLadderDismount(ServerLevel level, Villager villager, BlockPos ladder, BlockPos dismount) {
        double targetX = dismount.getX() + 0.5D;
        double targetY = dismount.getY();
        double targetZ = dismount.getZ() + 0.5D;
        if (!isWalkable(level, dismount)) {
            return false;
        }
        rememberRecentLadderDismount(level, villager, ladder, dismount);
        clearActiveLadderClimb(villager);
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
        villager.moveTo(targetX, targetY, targetZ, villager.getYRot(), villager.getXRot());
        villager.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 0.0D);
        villager.getBrain().setMemoryWithExpiry(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(dismount), 0.0F, 0),
                RECENT_LADDER_DISMOUNT_TICKS);
        villager.setOnGround(true);
        return true;
    }
    private static void rememberRecentLadderDismount(ServerLevel level, Villager villager, BlockPos ladder, BlockPos dismount) {
        RECENT_LADDER_DISMOUNTS.put(villager.getUUID(), new RecentLadderDismount(
                bottomOfLadderColumn(level, ladder).immutable(),
                topOfLadderColumn(level, ladder).immutable(),
                dismount.immutable(),
                level.getGameTime() + RECENT_LADDER_DISMOUNT_TICKS,
                0));
    }
    private static boolean settleRecentLadderDismount(ServerLevel level, Villager villager) {
        UUID villagerId = villager.getUUID();
        RecentLadderDismount recent = RECENT_LADDER_DISMOUNTS.get(villagerId);
        if (recent == null) {
            return false;
        }
        if (recent.expiresGameTime() <= level.getGameTime()
                || recent.settleChecks() >= RECENT_LADDER_SETTLE_CHECKS
                || villager.distanceToSqr(recent.dismount().getCenter()) > LADDER_DISMOUNT_REACHED_DISTANCE_SQR) {
            RECENT_LADDER_DISMOUNTS.remove(villagerId);
            return false;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (VillagerTaskNavigationUtil.isHiredWalkTarget(villager)
                && navigationTarget != null
                && !navigationTarget.equals(recent.dismount())) {
            RECENT_LADDER_DISMOUNTS.remove(villagerId);
            return false;
        }

        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        villager.setNoGravity(false);
        villager.setDeltaMovement(Vec3.ZERO);
        villager.moveTo(
                recent.dismount().getX() + 0.5D,
                recent.dismount().getY(),
                recent.dismount().getZ() + 0.5D,
                villager.getYRot(),
                villager.getXRot());
        villager.getMoveControl().setWantedPosition(
                recent.dismount().getX() + 0.5D,
                recent.dismount().getY(),
                recent.dismount().getZ() + 0.5D,
                0.0D);
        villager.getBrain().setMemoryWithExpiry(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(recent.dismount()), 0.0F, 0),
                RECENT_LADDER_DISMOUNT_TICKS);
        RECENT_LADDER_DISMOUNTS.put(villagerId, new RecentLadderDismount(
                recent.bottom(),
                recent.top(),
                recent.dismount(),
                recent.expiresGameTime(),
                recent.settleChecks() + 1));
        return true;
    }
    static boolean shouldAvoidRecentLadderDismount(ServerLevel level, Villager villager) {
        return shouldAvoidRecentLadderDismount(level, villager, null);
    }
    private static boolean shouldReenterRecentLadderColumn(RecentLadderDismount recent, BlockPos target) {
        if (target == null) {
            return false;
        }
        int verticalDelta = target.getY() - recent.dismount().getY();
        if (Math.abs(verticalDelta) <= LADDER_VERTICAL_TARGET_DEADZONE) {
            return false;
        }
        return verticalDelta < 0
                ? recent.bottom().getY() < recent.dismount().getY()
                : recent.top().getY() > recent.dismount().getY();
    }
    public static boolean moveTowardNearbyLadderThenClimb(ServerLevel level, Villager villager, BlockPos target, double speed) {
        if (target == null) {
            return false;
        }
        if (continueActiveLadderClimb(level, villager, target, speed)) {
            return true;
        }
        if (shouldAvoidRecentLadderDismount(level, villager, target)) {
            return false;
        }
        BlockPos touchingLadder = ladderTouching(level, villager.blockPosition(), target);
        if (touchingLadder != null) {
            if (!needsLadderRoute(villager, target)) {
                return moveOffTouchedLadderToward(level, villager, touchingLadder, target, speed);
            }
            return forceMoveOnLadderToward(level, villager, target, speed);
        }
        if (!needsLadderRoute(villager, target)) {
            return moveDirectlyToNearbyTarget(villager, target, speed);
        }
        VillagerLadderRoutePlanner.Route route = nearestLadderRoute(level, villager, target);
        if (route == null) {
            return false;
        }
        BlockPos ladder = route.entryRung();
        BlockPos approach = route.approach();
        if (tryEnterLadderBlock(level, villager, ladder, target, speed)) {
            return true;
        }
        if (route.directApproach() && villager.distanceToSqr(approach.getCenter()) <= 16.0D) {
            steerDirectlyToLadderApproach(villager, approach, speed);
            return true;
        }
        if (!villager.getNavigation().isDone()
                && approach.equals(villager.getNavigation().getTargetPos())) {
            return true;
        }
        Path path = villager.getNavigation().createPath(approach, 0);
        if (path != null && path.canReach()) {
            return VillagerTaskNavigationUtil.moveToHiredPath(villager, path, approach, speed, 0);
        }
        return false;
    }
    public static boolean continueLadderRoute(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            double speed) {
        LadderSearch search = activeLadderSearch(level, villager, target);
        if (search == null) {
            return false;
        }
        double currentDistanceSqr = villager.distanceToSqr(search.route().approach().getCenter());
        boolean madeProgress = currentDistanceSqr
                < search.approachDistanceSqr() - LADDER_ROUTE_PROGRESS_EPSILON_SQR;
        int progressChecks = madeProgress ? 0 : search.progressChecks() + 1;
        VillagerLadderRoutePlanner.Route route = search.route();
        if (!route.directApproach()
                && progressChecks >= LADDER_ROUTE_STALLED_CHECKS
                && currentDistanceSqr <= 16.0D
                && VillagerLadderRoutePlanner.hasClearDirectApproach(level, villager, route.approach())) {
            route = route.withDirectApproach();
            progressChecks = 0;
        }
        LadderSearch updated = new LadderSearch(
                search.origin(),
                route,
                search.targetPos(),
                search.expiresGameTime(),
                madeProgress ? currentDistanceSqr : search.approachDistanceSqr(),
                search.stalledAttempts(),
                progressChecks);
        LADDER_SEARCHES.put(villager.getUUID(), updated);
        if (tryEnterLadderBlock(level, villager, route.entryRung(), target, speed)) {
            return true;
        }
        if (route.directApproach()) {
            if (villager.distanceToSqr(route.approach().getCenter()) > 16.0D) {
                return false;
            }
            steerDirectlyToLadderApproach(villager, route.approach(), speed);
            return true;
        }
        return !villager.getNavigation().isDone()
                && route.approach().equals(villager.getNavigation().getTargetPos());
    }
    private static LadderSearch activeLadderSearch(ServerLevel level, Villager villager, BlockPos target) {
        if (level == null || villager == null || target == null) {
            return null;
        }
        LadderSearch search = LADDER_SEARCHES.get(villager.getUUID());
        if (search == null
                || search.expiresGameTime() <= level.getGameTime()
                || search.targetPos() != target.asLong()
                || search.route() == null
                || !VillagerLadderRoutePlanner.remainsUsable(level, villager, target, search.route())) {
            return null;
        }
        return search;
    }
    private static void steerDirectlyToLadderApproach(Villager villager, BlockPos approach, double speed) {
        villager.getNavigation().stop();
        villager.getMoveControl().setWantedPosition(
                approach.getX() + 0.5D,
                approach.getY(),
                approach.getZ() + 0.5D,
                speed);
        VillagerTaskNavigationUtil.setHiredWalkTarget(villager, approach, speed, 0);
        double dx = approach.getX() + 0.5D - villager.getX();
        double dz = approach.getZ() + 0.5D - villager.getZ();
        Vec3 motion = villager.getDeltaMovement();
        villager.setDeltaMovement(
                Math.clamp(dx * 0.16D + motion.x * 0.15D,
                        -LADDER_DIRECT_APPROACH_SPEED_LIMIT,
                        LADDER_DIRECT_APPROACH_SPEED_LIMIT),
                motion.y,
                Math.clamp(dz * 0.16D + motion.z * 0.15D,
                        -LADDER_DIRECT_APPROACH_SPEED_LIMIT,
                        LADDER_DIRECT_APPROACH_SPEED_LIMIT));
    }
    public static String ladderRouteDebug(ServerLevel level, Villager villager, BlockPos target) {
        VillagerLadderRoutePlanner.Route route = VillagerLadderRoutePlanner.findBest(
                level,
                villager,
                target,
                LADDER_ESCAPE_SEARCH_RADIUS,
                LADDER_ESCAPE_VERTICAL_RADIUS);
        if (route == null) {
            return "none";
        }
        return "rung=" + route.entryRung()
                + ", approach=" + route.approach()
                + ", bottom=" + route.bottom()
                + ", top=" + route.top()
                + ", dismount=" + route.dismount()
                + ", up=" + route.climbingUp()
                + ", direct=" + route.directApproach()
                + ", score=" + route.score();
    }
    private static boolean moveDirectlyToNearbyTarget(Villager villager, BlockPos target, double speed) {
        Path path = villager.getNavigation().createPath(target, 0);
        if (path != null && path.canReach() && VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target, speed, 0)) {
            return true;
        }
        if (villager.distanceToSqr(target.getCenter()) > 16.0D) {
            return false;
        }
        villager.getMoveControl().setWantedPosition(
                target.getX() + 0.5D,
                target.getY(),
                target.getZ() + 0.5D,
                speed);
        return true;
    }
    private static boolean forceMoveOnLadderToward(ServerLevel level, Villager villager, BlockPos target, double speed) {
        BlockPos ladder = ladderTouching(level, villager.blockPosition(), target);
        if (ladder == null) {
            return false;
        }
        if (!needsLadderRoute(villager, target)) {
            return moveOffTouchedLadderToward(level, villager, ladder, target, speed);
        }
        stopNavigationPath(villager);
        beginActiveLadderClimb(level, villager, ladder, target);
        ActiveLadderClimb climb = ACTIVE_LADDER_CLIMBS.get(villager.getUUID());
        boolean climbingUp = climb == null ? target.getY() >= villager.blockPosition().getY() : climb.climbingUp();
        return moveOnSpecificLadderToward(level, villager, ladder, target, climbingUp, speed);
    }
    private static boolean moveOffTouchedLadderToward(
            ServerLevel level,
            Villager villager,
            BlockPos ladder,
            BlockPos target,
            double speed) {
        boolean climbingUp = target.getY() >= villager.blockPosition().getY();
        BlockPos dismount = safeLadderDismount(level, villager, ladder, target, climbingUp);
        if (dismount == null) {
            return false;
        }
        if (villager.distanceToSqr(dismount.getCenter()) <= LADDER_DISMOUNT_REACHED_DISTANCE_SQR
                && snapToLadderDismount(level, villager, ladder, dismount)) {
            return true;
        }
        moveOffLadderToDismount(level, villager, ladder, dismount, climbingUp, speed);
        return true;
    }
    private static boolean tryEnterLadderBlock(ServerLevel level, Villager villager, BlockPos ladder, BlockPos target, double speed) {
        if (!needsLadderRoute(villager, target)
                || isStandingInLadder(level, villager.blockPosition())
                || Math.abs(villager.getY() - ladder.getY()) > 2.0D
                || horizontalDistanceToBlockCenterSqr(villager, ladder) > LADDER_ENTRY_DISTANCE_SQR
                || !isWalkable(level, ladder)
                || !isUsefulLadderRoute(level, villager, ladder, target)) {
            return false;
        }
        stopNavigationPath(villager);
        beginActiveLadderClimb(level, villager, ladder, target);
        forceEnterAndClimbLadder(villager, ladder, target, speed);
        return true;
    }
    private static void forceEnterAndClimbLadder(Villager villager, BlockPos ladder, BlockPos target, double speed) {
        double centerX = ladder.getX() + 0.5D;
        double centerZ = ladder.getZ() + 0.5D;
        double dx = centerX - villager.getX();
        double dz = centerZ - villager.getZ();
        int verticalDelta = target.getY() - villager.blockPosition().getY();
        double climb = verticalDelta >= 0 ? LADDER_CLIMB_SPEED : LADDER_DESCEND_SPEED;
        double targetY = verticalDelta >= 0 ? Math.max(villager.getY() + 0.25D, ladder.getY() + 0.05D) : ladder.getY();
        villager.getMoveControl().setWantedPosition(centerX, targetY, centerZ, speed);

        if (dx * dx + dz * dz <= LADDER_FORCED_ENTRY_HORIZONTAL_SQR) {
            // Snap into the validated ladder block before the vanilla move
            // controller can keep treating its thin collision face as a path
            // endpoint. This transition is limited to the adjacent block.
            villager.moveTo(centerX, villager.getY(), centerZ, villager.getYRot(), villager.getXRot());
            villager.setDeltaMovement(0.0D, climb, 0.0D);
            villager.setNoGravity(true);
            villager.setOnGround(false);
        }
    }
    static boolean continueActiveLadderClimb(ServerLevel level, Villager villager, BlockPos target, double speed) {
        ActiveLadderClimb climb = ACTIVE_LADDER_CLIMBS.get(villager.getUUID());
        if (climb == null) {
            return false;
        }
        if (climb.expiresGameTime() <= level.getGameTime()
                || (target != null && climb.targetPos() != target.asLong())) {
            clearActiveLadderClimb(villager);
            return false;
        }
        BlockPos routeTarget = target == null ? BlockPos.of(climb.targetPos()) : target;
        if (!needsLadderRoute(villager, routeTarget)) {
            BlockPos touchingLadder = ladderTouching(level, villager.blockPosition(), routeTarget);
            if (touchingLadder == null) {
                clearActiveLadderClimb(villager);
                return false;
            }
            ACTIVE_LADDER_CLIMBS.put(villager.getUUID(), climb.refreshed(level.getGameTime() + ACTIVE_LADDER_CLIMB_TICKS));
            return moveOnSpecificLadderToward(level, villager, touchingLadder, routeTarget, climb.climbingUp(), speed);
        }
        BlockPos ladder = activeLadderBlock(level, villager, climb);
        if (ladder == null) {
            clearActiveLadderClimb(villager);
            return false;
        }
        stopNavigationPath(villager);
        ACTIVE_LADDER_CLIMBS.put(villager.getUUID(), climb.refreshed(level.getGameTime() + ACTIVE_LADDER_CLIMB_TICKS));
        return moveOnSpecificLadderToward(level, villager, ladder, routeTarget, climb.climbingUp(), speed);
    }
    private static void beginActiveLadderClimb(ServerLevel level, Villager villager, BlockPos ladder, BlockPos target) {
        if (ladder == null || target == null || !isLoadedLadder(level, ladder) || !needsLadderRoute(villager, target)) {
            return;
        }
        BlockPos bottom = bottomOfLadderColumn(level, ladder);
        BlockPos top = topOfLadderColumn(level, ladder);
        ActiveLadderClimb existing = ACTIVE_LADDER_CLIMBS.get(villager.getUUID());
        if (existing != null
                && existing.targetPos() == target.asLong()
                && existing.bottom().equals(bottom)
                && existing.top().equals(top)) {
            ACTIVE_LADDER_CLIMBS.put(villager.getUUID(), existing.refreshed(level.getGameTime() + ACTIVE_LADDER_CLIMB_TICKS));
            return;
        }
        boolean climbingUp = target.getY() >= villager.blockPosition().getY();
        ACTIVE_LADDER_CLIMBS.put(villager.getUUID(), new ActiveLadderClimb(
                bottom.immutable(),
                top.immutable(),
                target.asLong(),
                climbingUp,
                level.getGameTime() + ACTIVE_LADDER_CLIMB_TICKS));
    }
    private static boolean needsLadderRoute(Villager villager, BlockPos target) {
        return target != null && Math.abs(target.getY() - villager.blockPosition().getY()) > LADDER_VERTICAL_TARGET_DEADZONE;
    }
    private static BlockPos activeLadderBlock(ServerLevel level, Villager villager, ActiveLadderClimb climb) {
        BlockPos origin = villager.blockPosition();
        double centerX = climb.bottom().getX() + 0.5D;
        double centerZ = climb.bottom().getZ() + 0.5D;
        double dx = centerX - villager.getX();
        double dz = centerZ - villager.getZ();
        if (dx * dx + dz * dz > ACTIVE_LADDER_COLUMN_DISTANCE_SQR) {
            return null;
        }
        int minY = Math.min(climb.bottom().getY(), climb.top().getY());
        int maxY = Math.max(climb.bottom().getY(), climb.top().getY());
        int y = Math.clamp(origin.getY(), minY, maxY);
        for (int offset = 0; offset <= 2; offset++) {
            BlockPos above = new BlockPos(climb.bottom().getX(), Math.min(maxY, y + offset), climb.bottom().getZ());
            if (isLoadedLadder(level, above)) {
                return above;
            }
            BlockPos below = new BlockPos(climb.bottom().getX(), Math.max(minY, y - offset), climb.bottom().getZ());
            if (isLoadedLadder(level, below)) {
                return below;
            }
        }
        return null;
    }
    private static void clearActiveLadderClimb(Villager villager) {
        ACTIVE_LADDER_CLIMBS.remove(villager.getUUID());
        if (villager.isNoGravity()) {
            villager.setNoGravity(false);
        }
    }
    private static void stopNavigationPath(Villager villager) {
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
    }
    private static BlockPos ladderTouching(ServerLevel level, BlockPos pos) {
        return ladderTouching(level, pos, null);
    }
    private static boolean isStandingInLadder(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER);
    }
    private static boolean isLadderPathNode(ServerLevel level, Node node) {
        if (node == null) {
            return false;
        }
        BlockPos pos = node.asBlockPos();
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER);
    }
    private static VillagerLadderRoutePlanner.Route nearestLadderRoute(
            ServerLevel level,
            Villager villager,
            BlockPos target) {
        UUID villagerId = villager.getUUID();
        BlockPos origin = villager.blockPosition();
        LadderSearch cached = LADDER_SEARCHES.get(villagerId);
        if (cached != null
                && cached.expiresGameTime() > level.getGameTime()
                && cached.targetPos() == target.asLong()
                && cached.origin().distSqr(origin) <= LADDER_SEARCH_ORIGIN_REUSE_DISTANCE_SQR
                && (cached.route() == null
                || VillagerLadderRoutePlanner.remainsUsable(level, villager, target, cached.route()))) {
            return cached.route();
        }
        boolean failedToApproach = cached != null
                && cached.targetPos() == target.asLong()
                && cached.route() != null
                && VillagerLadderRoutePlanner.remainsUsable(level, villager, target, cached.route())
                && villager.distanceToSqr(cached.route().approach().getCenter())
                >= cached.approachDistanceSqr() - LADDER_ROUTE_PROGRESS_EPSILON_SQR;
        int stalledAttempts = failedToApproach ? cached.stalledAttempts() + 1 : 0;
        VillagerLadderRoutePlanner.Route best = VillagerLadderRoutePlanner.findBest(
                level,
                villager,
                target,
                LADDER_ESCAPE_SEARCH_RADIUS,
                LADDER_ESCAPE_VERTICAL_RADIUS);
        if (best != null
                && stalledAttempts > 0
                && horizontalDistanceSqr(best.approach(), origin) <= 16.0D
                && VillagerLadderRoutePlanner.hasClearDirectApproach(level, villager, best.approach())) {
            best = best.withDirectApproach();
        }
        LADDER_SEARCHES.put(villagerId, new LadderSearch(
                origin.immutable(),
                best,
                target.asLong(),
                level.getGameTime() + LADDER_SEARCH_CACHE_TICKS,
                best == null ? Double.POSITIVE_INFINITY : villager.distanceToSqr(best.approach().getCenter()),
                stalledAttempts,
                0));
        return best;
    }
    private static boolean isLoadedLadder(ServerLevel level, BlockPos pos) {
        return pos != null && level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER);
    }
    private static boolean reachedLadderColumnEnd(Villager villager, BlockPos columnEnd, boolean climbingUp) {
        return climbingUp
                ? villager.getY() >= columnEnd.getY() + LADDER_TOP_DISMOUNT_Y_OFFSET
                : villager.getY() <= columnEnd.getY() + LADDER_BOTTOM_DISMOUNT_Y_OFFSET;
    }
    private static boolean reachedLadderBlockEnd(Villager villager, BlockPos columnEnd, boolean climbingUp) {
        return climbingUp
                ? villager.getY() + villager.getBbHeight() >= columnEnd.getY() + LADDER_TOP_HEAD_DISMOUNT_OFFSET
                : villager.getY() <= columnEnd.getY() + 0.15D;
    }
    private static boolean isUsefulLadderRoute(ServerLevel level, Villager villager, BlockPos ladder, BlockPos target) {
        return ladderRouteScore(level, villager, ladder, target) < Double.MAX_VALUE;
    }
    private static double ladderRouteScore(ServerLevel level, Villager villager, BlockPos ladder, BlockPos target) {
        if (target == null || !isLoadedLadder(level, ladder)) {
            return Double.MAX_VALUE;
        }
        BlockPos origin = villager.blockPosition();
        int verticalDelta = target.getY() - origin.getY();
        if (Math.abs(verticalDelta) <= LADDER_VERTICAL_TARGET_DEADZONE) {
            return Double.MAX_VALUE;
        }
        boolean climbingUp = verticalDelta > 0;
        if (climbingUp && ladder.getY() < origin.getY()) {
            return Double.MAX_VALUE;
        }
        if (!climbingUp && ladder.getY() > origin.getY()) {
            return Double.MAX_VALUE;
        }
        BlockPos columnEnd = climbingUp ? topOfLadderColumn(level, ladder) : bottomOfLadderColumn(level, ladder);
        if (climbingUp ? columnEnd.getY() <= origin.getY() : columnEnd.getY() >= origin.getY()) {
            return Double.MAX_VALUE;
        }
        int currentVerticalMiss = Math.abs(target.getY() - origin.getY());
        int ladderVerticalMiss = Math.abs(target.getY() - columnEnd.getY());
        if (ladderVerticalMiss >= currentVerticalMiss) {
            return Double.MAX_VALUE;
        }
        BlockPos dismount = safeLadderDismount(level, villager, columnEnd, target, climbingUp);
        if (dismount == null) {
            return Double.MAX_VALUE;
        }
        return villager.distanceToSqr(ladder.getCenter())
                + ladderVerticalMiss * ladderVerticalMiss * 4.0D
                + dismount.distSqr(target) * 0.5D;
    }
    private static BlockPos safeLadderDismount(
            ServerLevel level,
            Villager villager,
            BlockPos columnEnd,
            BlockPos target,
            boolean climbingUp) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = columnEnd.relative(direction);
            BlockPos[] candidates = climbingUp
                    ? new BlockPos[] { side, side.above() }
                    : new BlockPos[] { side, side.below() };
            for (BlockPos candidate : candidates) {
                if (!isWalkable(level, candidate)) {
                    continue;
                }
                double score = candidate.distSqr(target)
                        + villager.distanceToSqr(candidate.getCenter()) * 0.2D
                        + Math.abs(candidate.getY() - columnEnd.getY()) * 4.0D;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate.immutable();
                }
            }
        }
        return best;
    }
    private static BlockPos topOfLadderColumn(ServerLevel level, BlockPos ladder) {
        BlockPos top = ladder;
        while (level.hasChunkAt(top.above()) && level.getBlockState(top.above()).is(Blocks.LADDER)) {
            top = top.above();
        }
        return top;
    }
    private static BlockPos bottomOfLadderColumn(ServerLevel level, BlockPos ladder) {
        BlockPos bottom = ladder;
        while (level.hasChunkAt(bottom.below()) && level.getBlockState(bottom.below()).is(Blocks.LADDER)) {
            bottom = bottom.below();
        }
        return bottom;
    }
    private static double horizontalDistanceToBlockCenterSqr(Entity entity, BlockPos pos) {
        double dx = entity.getX() - (pos.getX() + 0.5D);
        double dz = entity.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }
    private static boolean isWalkable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return (feet.isAir() || feet.liquid() || feet.is(Blocks.LADDER))
                && (head.isAir() || head.liquid() || head.is(Blocks.LADDER))
                && (floor.isSolid() || feet.is(Blocks.LADDER));
    }
    private record ActiveLadderClimb(BlockPos bottom, BlockPos top, long targetPos, boolean climbingUp, long expiresGameTime) {
        private ActiveLadderClimb refreshed(long expiresGameTime) {
            return new ActiveLadderClimb(this.bottom, this.top, this.targetPos, this.climbingUp, expiresGameTime);
        }
    }
    private record RecentLadderDismount(
            BlockPos bottom,
            BlockPos top,
            BlockPos dismount,
            long expiresGameTime,
            int settleChecks) {
    }
    private record LadderSearch(
            BlockPos origin,
            VillagerLadderRoutePlanner.Route route,
            long targetPos,
            long expiresGameTime,
            double approachDistanceSqr,
            int stalledAttempts,
            int progressChecks) {
    }

    static boolean shouldAvoidRecentLadderDismount(ServerLevel level, Villager villager, BlockPos target) {
        RecentLadderDismount recent = RECENT_LADDER_DISMOUNTS.get(villager.getUUID());
        if (recent == null) {
            return false;
        }
        if (recent.expiresGameTime() <= level.getGameTime()) {
            RECENT_LADDER_DISMOUNTS.remove(villager.getUUID());
            return false;
        }
        if (isStandingInLadder(level, villager.blockPosition())) {
            return false;
        }
        if (villager.distanceToSqr(recent.dismount().getCenter()) > RECENT_LADDER_DISMOUNT_DISTANCE_SQR) {
            RECENT_LADDER_DISMOUNTS.remove(villager.getUUID());
            return false;
        }
        if (shouldReenterRecentLadderColumn(recent, target)) {
            RECENT_LADDER_DISMOUNTS.remove(villager.getUUID());
            return false;
        }
        return true;
    }
    private static BlockPos ladderTouching(ServerLevel level, BlockPos pos, BlockPos target) {
        if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER)) {
            return pos;
        }
        BlockPos below = pos.below();
        if (target != null
                && target.getY() < pos.getY()
                && level.hasChunkAt(below)
                && level.getBlockState(below).is(Blocks.LADDER)) {
            return below;
        }
        return null;
    }
    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    public static void clearRuntimeState() {
        ACTIVE_LADDER_CLIMBS.clear();
        RECENT_LADDER_DISMOUNTS.clear();
        LADDER_SEARCHES.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        clearActiveLadderClimb(villager);
        RECENT_LADDER_DISMOUNTS.remove(villager.getUUID());
        LADDER_SEARCHES.remove(villager.getUUID());
    }
}
