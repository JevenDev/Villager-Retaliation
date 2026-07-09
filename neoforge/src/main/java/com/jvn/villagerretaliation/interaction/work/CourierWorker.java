package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;

public final class CourierWorker implements HiredRoleWorker {
    private static final String PHASE_TAG = "CourierPhase";
    private static final String STORAGE_TARGET_TAG = "CourierStorageTarget";
    private static final String ROUTE_INDEX_TAG = "CourierRouteIndex";
    private static final String PHASE_PICKUP = "pickup";
    private static final String PHASE_OUTBOUND = "outbound";
    private static final String PHASE_DELIVER = "deliver";
    private static final String PHASE_RETURN = "return";
    private static final int MAX_ITEMS_PER_TRIP = 64;
    private static final double MOVE_SPEED = 0.5D;
    private static final double ROUTE_ARRIVAL_DISTANCE_SQR = 4.0D;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.COURIER;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        HiredRoute route = context.route();
        if (route == null || !route.usableForNavigation()) {
            clearTrip(context);
            HiredWorkerBrain.setFailure(context, "courier_missing_route", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            return WorkResult.idle("interaction.work.courier.missing_route");
        }

        String phase = phase(context);
        if (context.inventory().hasOutputItems() && PHASE_PICKUP.equals(phase)) {
            setPhase(context, PHASE_OUTBOUND);
            phase = PHASE_OUTBOUND;
        } else if (!context.inventory().hasOutputItems()
                && (PHASE_OUTBOUND.equals(phase) || PHASE_DELIVER.equals(phase))) {
            setPhase(context, PHASE_RETURN);
            phase = PHASE_RETURN;
        }

        return switch (phase) {
            case PHASE_OUTBOUND -> traverseRoute(level, villager, context, route, true);
            case PHASE_DELIVER -> deliver(level, villager, context);
            case PHASE_RETURN -> traverseRoute(level, villager, context, route, false);
            default -> pickup(level, villager, context);
        };
    }

    private WorkResult pickup(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!hasPurpose(level, villager, AssignedStorageService.INPUT_PURPOSE)) {
            HiredWorkerBrain.setFailure(context, "courier_missing_input_storage", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, null);
            return WorkResult.idle("interaction.work.courier.missing_input_storage");
        }
        BlockPos input = storedTarget(context);
        if (input == null) {
            Set<BlockPos> inputPositions = purposePositions(level, villager, AssignedStorageService.INPUT_PURPOSE);
            input = AssignedStorageService.nearestAssignedInputStoragePosContaining(
                    level,
                    villager,
                    stack -> !stack.isEmpty(),
                    inputPositions::contains);
            if (input == null) {
                HiredWorkerBrain.setFailure(context, "courier_input_empty", level.getGameTime() + 100L);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, null);
                return WorkResult.idle("interaction.work.courier.input_empty");
            }
            storeTarget(context, input);
        }

        HiredStorageNavigationGoal.Result movement = HiredStorageNavigationGoal.moveToStorageTarget(
                level, context, villager, input, MOVE_SPEED);
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, input);
            return WorkResult.progressed("interaction.work.courier.moving_to_input");
        }
        if (movement == HiredStorageNavigationGoal.Result.FAILED) {
            clearStoredTarget(context);
            HiredWorkerBrain.setFailure(context, "courier_input_unreachable", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, input);
            return WorkResult.idle("interaction.work.courier.input_unreachable");
        }

        int moved = AssignedStorageService.transferItemsAtAssignedStorage(
                villager,
                input,
                stack -> !stack.isEmpty(),
                MAX_ITEMS_PER_TRIP,
                context.inventory()::insertOutput);
        clearStoredTarget(context);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (moved <= 0) {
            HiredWorkerBrain.setFailure(context, "courier_input_empty", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, input);
            return WorkResult.idle("interaction.work.courier.input_empty");
        }
        setPhase(context, PHASE_OUTBOUND);
        context.state().putInt(ROUTE_INDEX_TAG, 0);
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, input);
        return WorkResult.progressed(
                "interaction.work.courier.collected_items",
                Map.of("count", Integer.toString(moved)));
    }

    private WorkResult traverseRoute(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route,
            boolean outbound) {
        int lastIndex = route.nodes().size() - 1;
        int fallback = outbound ? 0 : lastIndex;
        int index = context.state().contains(ROUTE_INDEX_TAG, Tag.TAG_INT)
                ? Math.clamp(context.state().getInt(ROUTE_INDEX_TAG), 0, lastIndex)
                : fallback;
        BlockPos node = route.nodes().get(index);
        RouteMovement movement = moveToRouteNode(level, villager, context, node);
        if (movement == RouteMovement.FAILED) {
            HiredWorkerBrain.setFailure(context, "courier_route_unreachable", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, node);
            return WorkResult.idle("interaction.work.courier.route_unreachable");
        }
        if (movement == RouteMovement.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, node);
            return WorkResult.progressed(outbound
                    ? "interaction.work.courier.following_route_outbound"
                    : "interaction.work.courier.following_route_return");
        }

        boolean finished = outbound ? index >= lastIndex : index <= 0;
        if (!finished) {
            context.state().putInt(ROUTE_INDEX_TAG, outbound ? index + 1 : index - 1);
            return WorkResult.progressed(outbound
                    ? "interaction.work.courier.following_route_outbound"
                    : "interaction.work.courier.following_route_return");
        }
        context.state().remove(ROUTE_INDEX_TAG);
        setPhase(context, outbound ? PHASE_DELIVER : PHASE_PICKUP);
        return WorkResult.progressed(outbound
                ? "interaction.work.courier.route_complete_outbound"
                : "interaction.work.courier.route_complete_return");
    }

    private WorkResult deliver(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!hasPurpose(level, villager, AssignedStorageService.OUTPUT_PURPOSE)) {
            HiredWorkerBrain.setFailure(context, "courier_missing_output_storage", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, null);
            return WorkResult.idle("interaction.work.courier.missing_output_storage");
        }
        BlockPos output = storedTarget(context);
        if (output == null) {
            Set<BlockPos> outputPositions = purposePositions(level, villager, AssignedStorageService.OUTPUT_PURPOSE);
            output = AssignedStorageService.nearestAssignedOutputStoragePos(level, villager, outputPositions::contains);
            if (output == null) {
                HiredWorkerBrain.setFailure(context, "courier_output_unavailable", level.getGameTime() + 100L);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, null);
                return WorkResult.idle("interaction.work.courier.output_unavailable");
            }
            storeTarget(context, output);
        }

        HiredStorageNavigationGoal.Result movement = HiredStorageNavigationGoal.moveToStorageTarget(
                level, context, villager, output, MOVE_SPEED);
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, output);
            return WorkResult.progressed("interaction.work.courier.moving_to_output");
        }
        if (movement == HiredStorageNavigationGoal.Result.FAILED) {
            clearStoredTarget(context);
            HiredWorkerBrain.setFailure(context, "courier_output_unreachable", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, output);
            return WorkResult.idle("interaction.work.courier.output_unreachable");
        }

        boolean deposited = context.inventory().depositOutputToAssignedStorageAt(output);
        if (!deposited) {
            HiredWorkerBrain.setFailure(context, "courier_output_full", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_STORAGE_FULL, output);
            return WorkResult.idle("interaction.work.courier.output_full");
        }
        if (context.inventory().hasOutputItems()) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.DEPOSITING, output);
            return WorkResult.progressed("interaction.work.courier.depositing_items");
        }

        clearStoredTarget(context);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        setPhase(context, PHASE_RETURN);
        context.state().putInt(ROUTE_INDEX_TAG, Math.max(0, context.route().nodes().size() - 1));
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, output);
        return WorkResult.completed("interaction.work.courier.delivered_items");
    }

    private static RouteMovement moveToRouteNode(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target) {
        double distanceSqr = villager.blockPosition().distSqr(target);
        if (distanceSqr <= ROUTE_ARRIVAL_DISTANCE_SQR) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredPathMemory.clearNavigationProgress(villager);
            return RouteMovement.ARRIVED;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.equals(navigationTarget)) {
            if (HiredPathMemory.observeNavigationProgress(level, villager, target, distanceSqr)) {
                return RouteMovement.MOVING;
            }
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredPathMemory.clearNavigationProgress(villager);
        }
        Path path = HiredPathMemory.createPath(level, villager, target, 1);
        if (path == null || !path.canReach()
                || !VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target, MOVE_SPEED, 1)) {
            return RouteMovement.FAILED;
        }
        HiredPathMemory.rememberNavigationProgress(level, villager, target, distanceSqr);
        return RouteMovement.MOVING;
    }

    private static boolean hasPurpose(ServerLevel level, Villager villager, String purpose) {
        for (AssignedContainerRecord record : AssignedStorageService.assignedStorage(level, villager)) {
            String normalized = AssignedStorageService.normalizePurpose(record.purpose());
            if (purpose.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static Set<BlockPos> purposePositions(ServerLevel level, Villager villager, String purpose) {
        return AssignedStorageService.assignedStorage(level, villager).stream()
                .filter(record -> purpose.equals(AssignedStorageService.normalizePurpose(record.purpose())))
                .map(AssignedContainerRecord::pos)
                .collect(Collectors.toSet());
    }

    private static String phase(HiredWorkContext context) {
        String phase = context.state().getString(PHASE_TAG);
        return phase.isBlank() ? PHASE_PICKUP : phase;
    }

    private static void setPhase(HiredWorkContext context, String phase) {
        context.state().putString(PHASE_TAG, phase);
        clearStoredTarget(context);
    }

    private static BlockPos storedTarget(HiredWorkContext context) {
        return context.state().contains(STORAGE_TARGET_TAG, Tag.TAG_LONG)
                ? BlockPos.of(context.state().getLong(STORAGE_TARGET_TAG))
                : null;
    }

    private static void storeTarget(HiredWorkContext context, BlockPos target) {
        context.state().putLong(STORAGE_TARGET_TAG, target.asLong());
    }

    private static void clearStoredTarget(HiredWorkContext context) {
        context.state().remove(STORAGE_TARGET_TAG);
    }

    private static void clearTrip(HiredWorkContext context) {
        CompoundTag state = context.state();
        state.remove(PHASE_TAG);
        state.remove(STORAGE_TARGET_TAG);
        state.remove(ROUTE_INDEX_TAG);
    }

    private enum RouteMovement {
        MOVING,
        ARRIVED,
        FAILED
    }
}
