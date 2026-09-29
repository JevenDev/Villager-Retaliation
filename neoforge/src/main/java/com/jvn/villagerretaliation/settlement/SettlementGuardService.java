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
                || SettlementSavedData.get(player.serverLevel()).readOnly()
                || order != null && order.positions().anyMatch(position -> player.serverLevel().isOutsideBuildHeight(position)
                        || !player.serverLevel().getWorldBorder().isWithinBounds(position))
                || player.serverLevel().getEntity(resident) instanceof Villager worker && SettlementWorkforceService.hasPersonalCommitment(worker)
                || !town.setGuardOrder(player.getUUID(), resident, order)) {
            return false;
        }
        if (player.serverLevel().getEntity(resident) instanceof Villager villager) {
            reset(villager);
        }
        return true;
    }

    public static String configure(ServerPlayer player, SettlementRecord town,
                                   com.jvn.villagerretaliation.network.SettlementManagementActionPayload.SaveRoute request) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !town.dimension().equals(player.level().dimension().location())
                || SettlementSavedData.get(player.serverLevel()).readOnly()
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)) return "permission_staff";
        var area = town.areas().get(request.area());
        var plan = town.worksitePlans().get(request.area());
        if (area == null || !SettlementGuardOrder.supports(area.type().role())) return "area_missing";
        if (request.resident() == null && plan == null) return "state_changed";
        if (request.resident() != null && !request.area().equals(town.duties().get(request.resident()))) return "duty_changed";
        if (request.resident() != null && player.serverLevel().getEntity(request.resident()) instanceof Villager villager
                && (SettlementWorkforceService.hasPersonalCommitment(villager) || !villager.isAlive() || villager.isBaby())) return "personal_commitment";
        var previous = request.resident() == null ? plan.route() : configuredRoute(town, area, request.resident());
        if (!java.util.Objects.equals(previous, request.expected())) return "state_changed";
        var route = request.route();
        if (route != null && (!route.validFor(area.type().role()) || !route.within(town)
                || route.positions().anyMatch(position -> player.serverLevel().isOutsideBuildHeight(position)
                        || !player.serverLevel().getWorldBorder().isWithinBounds(position)))) return "guard_order";
        boolean changed = request.resident() == null ? town.setWorksitePlan(player.getUUID(), area.id(), plan.withRoute(route))
                : town.setGuardOrder(player.getUUID(), request.resident(), route);
        if (!changed) return "state_changed";
        for (var duty : town.duties().entrySet()) {
            if (!duty.getValue().equals(area.id()) || request.resident() != null && !request.resident().equals(duty.getKey())) continue;
            if (player.serverLevel().getEntity(duty.getKey()) instanceof Villager villager
                    && !SettlementWorkforceService.hasPersonalCommitment(villager)) {
                SettlementBackgroundService.onActive(villager);
                HiredWorkStateStore.pauseWork(player.serverLevel(), villager, area.type().role(),
                        "interaction.work.status.waiting_tick", java.util.Map.of());
                reset(villager);
                SettlementDepotService.synchronizeStorage(player.serverLevel(), villager, town);
                SettlementBackgroundService.onUnloaded(villager);
            }
        }
        return "";
    }

    public static boolean temporaryCommand(ServerPlayer player, SettlementRecord town, UUID resident, SettlementGuardOrder order) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !town.dimension().equals(player.level().dimension().location())
                || SettlementSavedData.get(player.serverLevel()).readOnly()
                || order != null && order.positions().anyMatch(position -> player.serverLevel().isOutsideBuildHeight(position)
                        || !player.serverLevel().getWorldBorder().isWithinBounds(position))) return false;
        var entity = player.serverLevel().getEntity(resident);
        if (entity instanceof Villager villager && SettlementWorkforceService.hasPersonalCommitment(villager)) return false;
        if (!town.setTemporaryGuardOrder(player.getUUID(), resident, order)) return false;
        if (entity instanceof Villager villager) reset(villager);
        return true;
    }

    public static Optional<SettlementGuardOrder> order(Villager villager) {
        return SettlementWorkforceService.duty(villager)
                .filter(duty -> duty.area().type() == SettlementAreaType.GUARD_POST)
                .flatMap(duty -> Optional.ofNullable(effectiveOrder(duty.town(), duty.area(), villager.getUUID())));
    }

    public static SettlementGuardOrder effectiveOrder(SettlementRecord town, SettlementArea area, UUID resident) {
        if (area == null || !SettlementGuardOrder.supports(area.type().role())) return null;
        var temporary = town.temporaryGuardOrders().get(resident);
        if (temporary != null && temporary.within(town)) return temporary;
        var order = configuredRoute(town, area, resident);
        return order != null && !order.points().isEmpty() && order.within(town) ? order : null;
    }

    public static SettlementGuardOrder configuredRoute(SettlementRecord town, SettlementArea area, UUID resident) {
        if (area == null || !SettlementGuardOrder.supports(area.type().role())) return null;
        var plan = town.worksitePlans().get(area.id());
        var order = town.guardOrders().get(resident);
        if (order == null && plan != null) order = plan.route();
        return order;
    }

    private static Optional<SettlementGuardOrder> routeOrder(Villager villager) {
        return SettlementWorkforceService.duty(villager)
                .flatMap(duty -> Optional.ofNullable(effectiveOrder(duty.town(), duty.area(), villager.getUUID())));
    }

    public static HiredRoute route(Villager villager, HiredRoute fallback) {
        return SettlementWorkforceService.duty(villager).isEmpty() ? fallback
                : routeOrder(villager).map(SettlementGuardOrder::sharedRoute).orElseGet(HiredRoute::empty);
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
        var points = order.traversal();
        int index = cursor.index(points, 0);
        BlockPos destination = points.get(index);
        if (villager.blockPosition().distSqr(destination) <= 4.0D) {
            if (order.mode() == SettlementGuardOrder.Mode.PATROL) {
                cursor.markNodeReached(level.getGameTime());
                destination = points.get(cursor.advancePatrol(index, points.size(), order.loop()));
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
        var points = order.traversal();
        int index = cursor.index(points, 0);
        if (position.distSqr(points.get(index)) <= 4.0D) {
            cursor.markNodeReached(now);
            index = cursor.advancePatrol(index, points.size(), order.loop());
        }
        return points.get(index);
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
