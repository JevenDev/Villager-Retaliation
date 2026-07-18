package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
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

public final class CourierWorker implements HiredRoleWorker {
    private static final String PHASE_TAG = "CourierPhase";
    private static final String STORAGE_TARGET_TAG = "CourierStorageTarget";
    private static final String ROUTE_INDEX_TAG = "CourierRouteIndex";
    private static final String PHASE_PICKUP = "pickup";
    private static final String PHASE_OUTBOUND = "outbound";
    private static final String PHASE_DELIVER = "deliver";
    private static final String PHASE_RETURN = "return";
    private static final int MAX_CARGO_ITEMS = 64;
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
            context.state().putInt(ROUTE_INDEX_TAG, 0);
            phase = PHASE_OUTBOUND;
        } else if (!context.inventory().hasOutputItems() && PHASE_DELIVER.equals(phase)) {
            beginReturn(context, route);
            phase = PHASE_RETURN;
        }

        return switch (phase) {
            case PHASE_OUTBOUND -> traverseRoute(level, villager, context, route, true);
            case PHASE_DELIVER -> deliver(level, villager, context, route);
            case PHASE_RETURN -> traverseRoute(level, villager, context, route, false);
            default -> pickup(level, villager, context, route);
        };
    }

    private WorkResult pickup(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route) {
        Set<BlockPos> inputs = purposePositions(level, villager, AssignedStorageService.INPUT_PURPOSE);
        if (inputs.isEmpty()) {
            HiredWorkerBrain.setFailure(context, "courier_missing_input_storage", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, null);
            return WorkResult.idle("interaction.work.courier.missing_input_storage");
        }

        BlockPos input = storedTarget(context);
        if (input == null || !inputs.contains(input)) {
            input = AssignedStorageService.nearestAssignedInputStoragePosContaining(
                    level, villager, stack -> !stack.isEmpty(), inputs::contains);
            if (input == null) {
                return beginEmptyOutboundPass(context, route);
            }
            storeTarget(context, input);
        }

        HiredStorageNavigationGoal.Result movement = HiredStorageNavigationGoal.moveToStorageTarget(
                level, context, villager, input, MOVE_SPEED);
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.setStorageTarget(context, input);
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

        int moved = collectInput(villager, context, input);
        clearStoredTarget(context);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (moved <= 0) {
            return beginEmptyOutboundPass(context, route);
        }

        setPhase(context, PHASE_OUTBOUND);
        context.state().putInt(ROUTE_INDEX_TAG, 0);
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, input);
        return WorkResult.progressed(
                "interaction.work.courier.collected_items",
                Map.of("count", Integer.toString(moved)));
    }

    private static WorkResult beginEmptyOutboundPass(HiredWorkContext context, HiredRoute route) {
        setPhase(context, PHASE_OUTBOUND);
        context.state().putInt(ROUTE_INDEX_TAG, 0);
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, route.first());
        return WorkResult.progressed("interaction.work.courier.following_route_outbound");
    }

    private WorkResult traverseRoute(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route,
            boolean outbound) {
        int lastIndex = route.nodes().size() - 1;
        int index = context.state().contains(ROUTE_INDEX_TAG, Tag.TAG_INT)
                ? Math.clamp(context.state().getInt(ROUTE_INDEX_TAG), 0, lastIndex)
                : outbound ? 0 : lastIndex;
        BlockPos node = route.nodes().get(index);
        HiredRouteNavigator.NodeMovement movement = HiredRouteNavigator.moveToRouteNode(
                level, villager, node, MOVE_SPEED);
        if (movement == HiredRouteNavigator.NodeMovement.FAILED) {
            HiredWorkerBrain.setFailure(context, "courier_route_unreachable", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, node);
            return WorkResult.idle("interaction.work.courier.route_unreachable");
        }
        if (movement == HiredRouteNavigator.NodeMovement.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(
                    context,
                    outbound ? HiredWorkerTaskState.MOVING_TO_TARGET : HiredWorkerTaskState.RETURNING_TO_WORK_AREA,
                    node);
            return WorkResult.progressed(outbound
                    ? "interaction.work.courier.following_route_outbound"
                    : "interaction.work.courier.following_route_return");
        }

        if (outbound) {
            int moved = collectInputsAtNode(level, villager, context, node);
            if (moved > 0) {
                HiredWorkerBrain.clearFailure(context);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, node);
                return WorkResult.progressed(
                        "interaction.work.courier.collected_items",
                        Map.of("count", Integer.toString(moved)));
            }
        }

        boolean finished = outbound ? index >= lastIndex : index <= 0;
        if (!finished) {
            context.state().putInt(ROUTE_INDEX_TAG, outbound ? index + 1 : index - 1);
            return WorkResult.progressed(outbound
                    ? "interaction.work.courier.following_route_outbound"
                    : "interaction.work.courier.following_route_return");
        }

        context.state().remove(ROUTE_INDEX_TAG);
        if (outbound) {
            if (context.inventory().hasOutputItems()) {
                setPhase(context, PHASE_DELIVER);
            } else {
                beginReturn(context, route);
            }
        } else {
            setPhase(context, PHASE_PICKUP);
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, route.first());
        }
        return WorkResult.progressed(outbound
                ? "interaction.work.courier.route_complete_outbound"
                : "interaction.work.courier.route_complete_return");
    }

    private WorkResult deliver(ServerLevel level, Villager villager, HiredWorkContext context, HiredRoute route) {
        Set<BlockPos> outputs = purposePositions(level, villager, AssignedStorageService.OUTPUT_PURPOSE);
        if (outputs.isEmpty()) {
            HiredWorkerBrain.setFailure(context, "courier_missing_output_storage", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, null);
            return WorkResult.idle("interaction.work.courier.missing_output_storage");
        }

        BlockPos output = storedTarget(context);
        if (output == null || !outputs.contains(output)) {
            output = AssignedStorageService.nearestAssignedOutputStoragePos(level, villager, outputs::contains);
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
            HiredWorkerBrain.setStorageTarget(context, output);
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

        int deliveredItems = context.inventory().collectOutputItems().stream()
                .mapToInt(outputItem -> outputItem.stack().getCount())
                .sum();
        while (context.inventory().hasOutputItems()
                && context.inventory().depositOutputToAssignedStorageAt(output)) {
        }
        if (context.inventory().hasOutputItems()) {
            HiredWorkerBrain.setFailure(context, "courier_output_full", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_STORAGE_FULL, output);
            return WorkResult.idle("interaction.work.courier.output_full");
        }

        clearStoredTarget(context);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        beginReturn(context, route);
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, route.last());
        if (deliveredItems <= 0) {
            return WorkResult.progressed("interaction.work.courier.delivered_items");
        }
        return WorkResult.progressedWithPractice(
                "interaction.work.courier.delivered_items",
                HiredWorkPractice.courier(deliveredItems, routeDistance(route)));
    }

    static double routeDistance(HiredRoute route) {
        double distance = 0.0D;
        for (int index = 1; index < route.nodes().size(); index++) {
            distance += Math.sqrt(route.nodes().get(index - 1).distSqr(route.nodes().get(index)));
        }
        if (route.loop() && route.nodes().size() > 1) {
            distance += Math.sqrt(route.nodes().getLast().distSqr(route.nodes().getFirst()));
        }
        return distance;
    }

    private static int collectInputsAtNode(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos node) {
        int moved = 0;
        for (BlockPos input : purposePositions(level, villager, AssignedStorageService.INPUT_PURPOSE)) {
            if (input.distSqr(node) > ROUTE_ARRIVAL_DISTANCE_SQR) {
                continue;
            }
            moved += collectInput(villager, context, input);
            if (!context.inventory().hasOutputSpace()) {
                break;
            }
        }
        return moved;
    }

    private static int collectInput(Villager villager, HiredWorkContext context, BlockPos input) {
        int remainingCapacity = MAX_CARGO_ITEMS - cargoItemCount(context);
        if (remainingCapacity <= 0 || !context.inventory().hasOutputSpace()) {
            return 0;
        }
        return AssignedStorageService.transferItemsAtAssignedStorage(
                villager,
                input,
                stack -> !stack.isEmpty(),
                remainingCapacity,
                context.inventory()::insertOutput);
    }

    private static int cargoItemCount(HiredWorkContext context) {
        return context.inventory().collectOutputItems().stream()
                .mapToInt(output -> output.stack().getCount())
                .sum();
    }

    private static Set<BlockPos> purposePositions(ServerLevel level, Villager villager, String purpose) {
        return AssignedStorageService.assignedStorage(level, villager).stream()
                .filter(record -> record.dimension().equals(level.dimension()))
                .filter(record -> purpose.equals(AssignedStorageService.normalizePurpose(record.purpose())))
                .filter(record -> AssignedStorageService.isValidContainerForPurpose(level, record.pos(), purpose))
                .map(AssignedContainerRecord::pos)
                .collect(Collectors.toSet());
    }

    private static void beginReturn(HiredWorkContext context, HiredRoute route) {
        setPhase(context, PHASE_RETURN);
        context.state().putInt(ROUTE_INDEX_TAG, route.loop() ? 0 : route.nodes().size() - 1);
    }

    private static String phase(HiredWorkContext context) {
        String phase = context.state().getString(PHASE_TAG);
        return phase.isBlank() ? PHASE_PICKUP : phase;
    }

    private static void setPhase(HiredWorkContext context, String phase) {
        context.state().putString(PHASE_TAG, phase);
        clearStoredTarget(context);
        HiredStorageNavigationGoal.clearStorageTarget(context);
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
        HiredStorageNavigationGoal.clearStorageTarget(context);
    }

}
