package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredCombatMode;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredRouteNavigator;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.RouteTraversalCursor;
import com.jvn.villagerretaliation.villager.VillagerMovementSpeedPolicy;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public final class SettlementGuardService {
    private static final String ORDER_TAG = "SettlementGuardOrder";
    private static final String RETRY_TAG = "SettlementGuardRetryAt";
    private static final RouteTraversalCursor.Tags CURSOR = new RouteTraversalCursor.Tags(
            "SettlementGuardPoint", "SettlementGuardDirection", "SettlementGuardReachedAt");

    private SettlementGuardService() {
    }

    public static boolean command(ServerPlayer player, SettlementRecord town, UUID resident, SettlementGuardOrder order) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !town.dimension().equals(player.level().dimension().location())
                || order != null && order.points().stream().anyMatch(player.serverLevel()::isOutsideBuildHeight)
                || !town.setGuardOrder(player.getUUID(), resident, order)) {
            return false;
        }
        if (player.serverLevel().getEntity(resident) instanceof Villager villager) {
            reset(villager);
        }
        return true;
    }

    public static Optional<SettlementGuardOrder> order(Villager villager) {
        return SettlementWorkforceService.duty(villager)
                .filter(duty -> duty.area().type() == SettlementAreaType.GUARD_POST)
                .flatMap(duty -> Optional.ofNullable(effectiveOrder(duty.town(), duty.area(), villager.getUUID())));
    }

    public static SettlementGuardOrder effectiveOrder(SettlementRecord town, SettlementArea area, UUID resident) {
        if (area == null || area.type() != SettlementAreaType.GUARD_POST && area.type() != SettlementAreaType.HUNTING_GROUNDS) return null;
        var plan = town.worksitePlans().get(area.id());
        var order = area.type() == SettlementAreaType.GUARD_POST ? town.guardOrders().get(resident) : null;
        if (order == null && plan != null) order = plan.route();
        return order != null && order.within(town) ? order : null;
    }

    private static Optional<SettlementGuardOrder> routeOrder(Villager villager) {
        return SettlementWorkforceService.duty(villager)
                .flatMap(duty -> Optional.ofNullable(effectiveOrder(duty.town(), duty.area(), villager.getUUID())));
    }

    public static HiredRoute route(Villager villager, HiredRoute fallback) {
        return SettlementWorkforceService.duty(villager).isEmpty() ? fallback
                : routeOrder(villager).map(order -> new HiredRoute(order.points(), false)).orElseGet(HiredRoute::empty);
    }

    public static HiredCombatMode mode(Villager villager, HiredCombatMode fallback) {
        return order(villager).map(order -> order.mode() == SettlementGuardOrder.Mode.MOVE
                ? HiredCombatMode.GUARD : HiredCombatMode.ROAMING).orElse(fallback);
    }

    public static boolean allowsTarget(Villager villager, BlockPos position) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        return duty == null || duty.area().type() != SettlementAreaType.GUARD_POST && duty.area().type() != SettlementAreaType.HUNTING_GROUNDS
                || SettlementGuardOrder.within(duty.town(), position);
    }

    public static boolean maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        var order = routeOrder(villager).orElse(null);
        if (order == null || com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.queue(context.state()) != null) {
            return false;
        }
        var state = context.state();
        var saved = order.save();
        if (!saved.equals(state.getCompound(ORDER_TAG))) {
            reset(villager);
            state.put(ORDER_TAG, saved);
        }
        var cursor = new RouteTraversalCursor(state, CURSOR);
        int index = cursor.index(order.points(), 0);
        BlockPos destination = order.points().get(index);
        if (villager.blockPosition().distSqr(destination) <= 4.0D) {
            if (order.mode() == SettlementGuardOrder.Mode.PATROL) {
                cursor.markNodeReached(level.getGameTime());
                destination = order.points().get(cursor.advancePatrol(index, order.points().size(), false));
            } else {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredWorkerBrain.clearFailure(context);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, destination);
                return true;
            }
        }
        if (level.getGameTime() < state.getLong(RETRY_TAG)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, destination);
            return true;
        }
        BlockPos target = nextStep(level, villager, destination);
        var movement = target == null ? HiredRouteNavigator.NodeMovement.FAILED
                : HiredRouteNavigator.moveToRouteNode(level, villager, target, VillagerMovementSpeedPolicy.WALK_SPEED_MODIFIER);
        if (movement == HiredRouteNavigator.NodeMovement.FAILED) {
            state.putLong(RETRY_TAG, level.getGameTime() + 60L);
            HiredWorkerBrain.setFailure(context, "route_node_unreachable", level.getGameTime() + 60L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, destination);
        } else {
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, destination);
        }
        return true;
    }

    static BlockPos patrolDestination(net.minecraft.nbt.CompoundTag state, SettlementGuardOrder order, BlockPos position, long now) {
        if (!order.save().equals(state.getCompound(ORDER_TAG))) {
            resetProgress(state);
            state.put(ORDER_TAG, order.save());
        }
        var cursor = new RouteTraversalCursor(state, CURSOR);
        int index = cursor.index(order.points(), 0);
        if (position.distSqr(order.points().get(index)) <= 4.0D) {
            cursor.markNodeReached(now);
            index = cursor.advancePatrol(index, order.points().size(), false);
        }
        return order.points().get(index);
    }

    static void copyProgress(net.minecraft.nbt.CompoundTag source, net.minecraft.nbt.CompoundTag destination) {
        for (String key : java.util.List.of(ORDER_TAG, RETRY_TAG, CURSOR.index(), CURSOR.direction(), CURSOR.lastNodeReachedGameTime())) {
            destination.remove(key);
            if (source.contains(key)) destination.put(key, source.get(key).copy());
        }
    }

    private static void resetProgress(net.minecraft.nbt.CompoundTag state) {
        state.remove(ORDER_TAG);
        state.remove(RETRY_TAG);
        new RouteTraversalCursor(state, CURSOR).clear();
        HiredRouteNavigator.clearProgress(state);
    }

    static BlockPos nextStep(ServerLevel level, Villager villager, BlockPos destination) {
        if (villager.blockPosition().distSqr(destination) <= 256.0D) {
            return level.hasChunkAt(destination) ? destination : null;
        }
        BlockPos navigation = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && navigation != null) {
            return navigation;
        }
        Vec3 delta = Vec3.atBottomCenterOf(destination).subtract(villager.position()).normalize().scale(12.0D);
        BlockPos next = BlockPos.containing(villager.position().add(delta));
        var chunk = level.getChunkSource().getChunkNow(next.getX() >> 4, next.getZ() >> 4);
        if (chunk == null) {
            return null;
        }
        BlockPos nearby = new BlockPos(next.getX(), villager.blockPosition().getY(), next.getZ());
        for (int offset = 0; offset <= 4; offset++) {
            for (int direction : new int[] {1, -1}) {
                BlockPos candidate = nearby.offset(0, offset * direction, 0);
                if (HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)) {
                    return candidate;
                }
            }
        }
        return nearby;
    }

    private static void reset(Villager villager) {
        var state = HiredWorkStateStore.state(villager);
        resetProgress(state);
        HiredWorkSession.invalidate(villager);
        if (villager.getTarget() == null && villager.getLastHurtByMob() == null) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        }
    }
}
