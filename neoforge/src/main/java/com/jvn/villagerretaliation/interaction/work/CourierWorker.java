package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class CourierWorker implements HiredRoleWorker {
    private static final String PHASE_PICKUP = "pickup";
    private static final String PHASE_OUTBOUND = "outbound";
    private static final String PHASE_DELIVER = "deliver";
    private static final String PHASE_RETURN = "return";
    private static final double MOVE_SPEED = 0.5D;
    private static final double ROUTE_ARRIVAL_DISTANCE_SQR = 4.0D;
    private static final double MAX_STORAGE_TETHER_DISTANCE_SQR = 16.0D * 16.0D;
    private static final long ROUTE_RECOVERY_TICKS = 20L * 30L;

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
        if (context.inventory().hasOutputItems()
                && !PHASE_RETURN.equals(phase)
                && compatibleOutputState(villager, context)
                        == AssignedStorageService.AssignedOutputState.BACKPRESSURED) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE);
            return WorkResult.idle("interaction.work.status.paused");
        }
        if (HiredWorkerBrain.snapshot(context.state(), level.getGameTime()).taskState()
                == HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE) {
            HiredWorkerBrain.clearFailure(context);
        }
        if (context.inventory().hasOutputItems() && PHASE_PICKUP.equals(phase)) {
            beginOutbound(context);
            phase = PHASE_OUTBOUND;
        }
        return switch (phase) {
            case PHASE_OUTBOUND, PHASE_DELIVER -> traverseRoute(level, villager, context, route, true);
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
        if (purposePositions(level, villager, AssignedStorageService.OUTPUT_PURPOSE).isEmpty()) {
            HiredWorkerBrain.setFailure(context, "courier_missing_output_storage", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, null);
            return WorkResult.idle("interaction.work.courier.missing_output_storage");
        }
        if (AssignedStorageService.courierTransferState(level, villager)
                == AssignedStorageService.CourierTransferState.OUTPUT_BACKPRESSURED) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE);
            return WorkResult.idle("interaction.work.status.paused");
        }
        beginOutbound(context);
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
        List<BlockPos> traversalNodes = route.traversalNodes();
        int lastIndex = traversalNodes.size() - 1;
        CourierWorkState workState = courierState(context);
        RouteTraversalCursor cursor = workState.routeCursor();
        int index = cursor.index(traversalNodes, outbound ? 0 : lastIndex);
        boolean deliverySweep = PHASE_DELIVER.equals(phase(context));
        boolean servicingInputs = outbound && !deliverySweep;
        BlockPos node = traversalNodes.get(index);

        WorkResult detour = continueStorageDetour(level, villager, context, route, node, index, outbound);
        if (detour != null) {
            return detour;
        }

        while (true) {
            RouteTraversalCursor.Traversal traversal = cursor.moveToCurrentNode(
                    level, villager, traversalNodes, index, MOVE_SPEED,
                    ROUTE_ARRIVAL_DISTANCE_SQR, ROUTE_RECOVERY_TICKS);
            index = traversal.index();
            node = traversal.target();
            if (traversal.movement() == HiredRouteNavigator.NodeMovement.FAILED) {
                HiredWorkerBrain.setFailure(context, "courier_route_unreachable", level.getGameTime() + 100L);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, node);
                return WorkResult.idle("interaction.work.courier.route_unreachable");
            }
            if (traversal.movement() == HiredRouteNavigator.NodeMovement.MOVING) {
                HiredWorkerBrain.clearFailure(context);
                HiredWorkerBrain.setState(
                        context,
                        outbound ? HiredWorkerTaskState.MOVING_TO_TARGET : HiredWorkerTaskState.RETURNING_TO_WORK_AREA,
                        node);
                return WorkResult.progressed(outbound
                        ? "interaction.work.courier.following_route_outbound"
                        : "interaction.work.courier.following_route_return");
            }

            String storagePurpose = servicingInputs
                    ? AssignedStorageService.INPUT_PURPOSE
                    : AssignedStorageService.OUTPUT_PURPOSE;
            List<BlockPos> storageBatch = servicingInputs
                    ? selectInputsAtNode(level, villager, context, route, traversalNodes, index)
                    : selectOutputsAtNode(level, villager, context, route, traversalNodes, index);
            if (!storageBatch.isEmpty()) {
                beginStorageBatch(context, storageBatch, storagePurpose);
                return continueStorageDetour(level, villager, context, route, node, index, outbound);
            }

            boolean finished = outbound ? index >= lastIndex : index <= 0;
            if (finished) {
                break;
            }
            index = cursor.advanceLinear(index, outbound ? 1 : -1, traversalNodes.size());
        }

        cursor.clear();
        if (outbound) {
            if (deliverySweep && context.inventory().hasOutputItems()) {
                boolean hasCompatibleOutputRoute = compatibleOutputState(villager, context)
                        != AssignedStorageService.AssignedOutputState.NO_ROUTE;
                beginReturn(context, traversalNodes);
                if (!hasCompatibleOutputRoute) {
                    workState.setReturnToInputSweep(true);
                    HiredWorkerBrain.setFailure(context, "courier_output_unavailable", level.getGameTime() + 100L);
                    HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, node);
                } else {
                    HiredWorkerBrain.clearFailure(context);
                    HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE, node);
                }
                return WorkResult.idle("interaction.work.courier.output_unavailable");
            }
            beginReturn(context, traversalNodes);
        } else {
            if (workState.returnToInputSweep()) {
                beginOutbound(context);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, route.first());
                return WorkResult.progressed("interaction.work.courier.following_route_outbound");
            }
            if (context.inventory().hasOutputItems()) {
                beginDeliverySweep(context);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, route.first());
                return WorkResult.progressed("interaction.work.courier.following_route_return");
            }
            setPhase(context, PHASE_PICKUP);
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, route.first());
        }
        return WorkResult.progressed(outbound
                ? "interaction.work.courier.route_complete_outbound"
                : "interaction.work.courier.route_complete_return");
    }

    private WorkResult depositAtOutput(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route,
            BlockPos output) {
        ItemStack deliveredStack = context.inventory().collectOutputItems().stream()
                .map(outputItem -> outputItem.stack())
                .filter(stack -> !stack.isEmpty())
                .findFirst()
                .map(stack -> stack.copyWithCount(1))
                .orElse(ItemStack.EMPTY);
        int cargoBefore = cargoItemCount(context);
        BlockPos selectedOutput = output;
        while (context.inventory().hasOutputItems()
                && context.inventory().depositOutputToAssignedStorageAt(
                        selectedOutput,
                        stack -> AssignedStorageService.courierOutputStorageAccepts(level, villager, selectedOutput, stack))) {
        }
        int deliveredItems = cargoBefore - cargoItemCount(context);
        if (context.inventory().hasOutputItems()) {
            boolean acceptsRemainingCargo = context.inventory().collectOutputItems().stream()
                    .map(outputItem -> outputItem.stack())
                    .anyMatch(stack -> AssignedStorageService.courierOutputStorageAccepts(level, villager, selectedOutput, stack));
            if (acceptsRemainingCargo) {
                AssignedStorageService.closeStorageFeedback(level, output);
                AssignedStorageService.rememberOutputStorageFull(level, villager, output);
            }
        }
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, output);
        HiredWorkAnimation.useItem(level, villager, deliveredStack);
        if (deliveredItems <= 0) {
            return WorkResult.progressed("interaction.work.courier.delivered_items");
        }
        return WorkResult.progressedWithPractice(
                "interaction.work.courier.delivered_items",
                HiredWorkPractice.courier(deliveredItems, routeDistance(route)));
    }

    private static BlockPos selectOutput(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Set<BlockPos> outputs) {
        List<ItemStack> cargo = context.inventory().collectOutputItems().stream()
                .map(output -> output.stack())
                .toList();
        return AssignedStorageService.nearestAssignedCourierOutputStoragePos(
                level,
                villager,
                cargo,
                outputs::contains);
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

    private static void animateCargo(ServerLevel level, Villager villager, HiredWorkContext context) {
        ItemStack cargo = context.inventory().collectOutputItems().stream()
                .map(outputItem -> outputItem.stack())
                .filter(stack -> !stack.isEmpty())
                .findFirst()
                .map(stack -> stack.copyWithCount(1))
                .orElse(ItemStack.EMPTY);
        HiredWorkAnimation.useItem(level, villager, cargo);
    }

    private WorkResult continueStorageDetour(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route,
            BlockPos node,
            int routeIndex,
            boolean outbound) {
        CourierWorkState workState = courierState(context);
        BlockPos storage = workState.storageTarget();
        if (storage == null || !workState.hasStoragePurpose()) {
            return null;
        }
        if (workState.returningToRouteNode()) {
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
            clearStorageDetour(context);
            workState.routeCursor().markNodeReached(level.getGameTime());
            return null;
        }
        String purpose = workState.storagePurpose();
        HiredStorageNavigationGoal.Result movement = HiredStorageNavigationGoal.moveToStorageTarget(
                level, context, villager, storage, MOVE_SPEED);
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.setStorageTarget(context, storage);
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, storage);
            return WorkResult.progressed(AssignedStorageService.INPUT_PURPOSE.equals(purpose)
                    ? "interaction.work.courier.moving_to_input"
                    : "interaction.work.courier.moving_to_output");
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (movement == HiredStorageNavigationGoal.Result.FAILED) {
            workState.setReturningToRouteNode(true);
            if (AssignedStorageService.OUTPUT_PURPOSE.equals(purpose)) {
                AssignedStorageService.rememberOutputStorageFailure(
                        level, villager, storage, "courier_output_unreachable");
            }
            String failure = AssignedStorageService.INPUT_PURPOSE.equals(purpose)
                    ? "courier_input_unreachable"
                    : "courier_output_unreachable";
            HiredWorkerBrain.setFailure(context, failure, level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return WorkResult.idle(AssignedStorageService.INPUT_PURPOSE.equals(purpose)
                    ? "interaction.work.courier.input_unreachable"
                    : "interaction.work.courier.output_unreachable");
        }
        rememberVisitedStorage(level, villager, context, storage);
        WorkResult result;
        if (AssignedStorageService.OUTPUT_PURPOSE.equals(purpose)) {
            result = depositAtOutput(level, villager, context, route, storage);
        } else {
            int moved = collectInput(villager, context, storage);
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, storage);
            if (moved > 0) {
                animateCargo(level, villager, context);
            }
            result = WorkResult.progressed(
                    "interaction.work.courier.collected_items",
                    Map.of("count", Integer.toString(moved)));
        }

        BlockPos nextStorage = takeNextStorageInBatch(level, villager, context, purpose);
        if (nextStorage != null) {
            beginStorageDetour(context, nextStorage, purpose);
        } else {
            clearStorageBatch(context);
            workState.setReturningToRouteNode(true);
        }
        return result;
    }

    private static List<BlockPos> selectInputsAtNode(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route,
            List<BlockPos> routeNodes,
            int routeIndex) {
        if (!context.inventory().hasOutputSpace()) {
            return List.of();
        }
        return AssignedStorageService.assignedStoragePositionsContaining(
                        level,
                        villager,
                        stack -> !stack.isEmpty())
                .stream()
                .filter(pos -> !hasVisitedStorage(level, villager, context, pos))
                .filter(pos -> isStorageTetheredToNode(level, villager, route, routeNodes, pos, routeIndex))
                .toList();
    }

    private static List<BlockPos> selectOutputsAtNode(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route,
            List<BlockPos> routeNodes,
            int routeIndex) {
        if (!context.inventory().hasOutputItems()) {
            return List.of();
        }
        Set<BlockPos> outputs = purposePositions(level, villager, AssignedStorageService.OUTPUT_PURPOSE).stream()
                .filter(pos -> !hasVisitedStorage(level, villager, context, pos))
                .filter(pos -> isStorageTetheredToNode(level, villager, route, routeNodes, pos, routeIndex))
                .collect(Collectors.toSet());
        List<BlockPos> ordered = new ArrayList<>();
        while (!outputs.isEmpty()) {
            BlockPos next = selectOutput(level, villager, context, outputs);
            if (next == null) {
                break;
            }
            ordered.add(next);
            outputs.remove(next);
        }
        return List.copyOf(ordered);
    }

    private static void beginStorageBatch(
            HiredWorkContext context,
            List<BlockPos> storages,
            String purpose) {
        BlockPos first = storages.getFirst();
        courierState(context).setStorageBatch(
                storages.stream().skip(1L).mapToLong(BlockPos::asLong).toArray());
        beginStorageDetour(context, first, purpose);
    }

    private static BlockPos takeNextStorageInBatch(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            String purpose) {
        CourierWorkState workState = courierState(context);
        long[] pending = workState.storageBatch();
        for (int index = 0; index < pending.length; index++) {
            BlockPos candidate = BlockPos.of(pending[index]);
            workState.setStorageBatch(
                    java.util.Arrays.copyOfRange(pending, index + 1, pending.length));
            if (!AssignedStorageService.isValidContainerForPurpose(level, candidate, purpose)
                    || hasVisitedStorage(level, villager, context, candidate)) {
                continue;
            }
            if (AssignedStorageService.INPUT_PURPOSE.equals(purpose)) {
                return context.inventory().hasOutputSpace() ? candidate : null;
            }
            if (!context.inventory().hasOutputItems()) {
                return null;
            }
            boolean acceptsCargo = context.inventory().collectOutputItems().stream()
                    .map(output -> output.stack())
                    .anyMatch(stack -> AssignedStorageService.courierOutputStorageAccepts(
                            level,
                            villager,
                            candidate,
                            stack));
            if (acceptsCargo) {
                return candidate;
            }
        }
        return null;
    }

    private static void clearStorageBatch(HiredWorkContext context) {
        courierState(context).clearStorageBatch();
    }

    private static boolean isStorageTetheredToNode(
            ServerLevel level,
            Villager villager,
            HiredRoute route,
            List<BlockPos> routeNodes,
            BlockPos storage,
            int routeIndex) {
        return AssignedStorageService.assignedStorageInteractionPositions(level, villager, storage).stream()
                .anyMatch(position -> isTetheredToNode(route, routeNodes, position, routeIndex));
    }

    private static boolean isTetheredToNode(
            HiredRoute route,
            List<BlockPos> routeNodes,
            BlockPos storage,
            int routeIndex) {
        if (routeIndex < 0 || routeIndex >= routeNodes.size()) {
            return false;
        }
        int branchEndIndex = nearestBranchEndIndex(route, routeNodes, storage);
        if (branchEndIndex >= 0) {
            return routeIndex == branchEndIndex;
        }
        double selectedDistance = storage.distSqr(routeNodes.get(routeIndex));
        if (selectedDistance > MAX_STORAGE_TETHER_DISTANCE_SQR) {
            return false;
        }
        for (int index = 0; index < routeNodes.size(); index++) {
            double distance = storage.distSqr(routeNodes.get(index));
            if (distance < selectedDistance || distance == selectedDistance && index < routeIndex) {
                return false;
            }
        }
        return true;
    }

    private static int nearestBranchEndIndex(
            HiredRoute route,
            List<BlockPos> routeNodes,
            BlockPos storage) {
        int selectedIndex = -1;
        double selectedDistance = Double.MAX_VALUE;
        for (HiredRoute.Branch branch : route.branches()) {
            double distance = storage.distSqr(branch.end());
            if (distance > MAX_STORAGE_TETHER_DISTANCE_SQR || distance >= selectedDistance) {
                continue;
            }
            int candidateIndex = routeNodes.indexOf(branch.end());
            if (candidateIndex >= 0) {
                selectedIndex = candidateIndex;
                selectedDistance = distance;
            }
        }
        return selectedIndex;
    }

    private static void beginStorageDetour(HiredWorkContext context, BlockPos storage, String purpose) {
        CourierWorkState workState = courierState(context);
        workState.setStorageTarget(storage);
        workState.setStoragePurpose(purpose);
        workState.setReturningToRouteNode(false);
    }

    private static boolean hasVisitedStorage(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos storage) {
        long[] visitedPositions = courierState(context).visitedStorage();
        return AssignedStorageService.assignedStorageInteractionPositions(level, villager, storage).stream()
                .mapToLong(BlockPos::asLong)
                .anyMatch(target -> java.util.Arrays.stream(visitedPositions)
                        .anyMatch(visited -> visited == target));
    }

    private static void rememberVisitedStorage(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos storage) {
        CourierWorkState workState = courierState(context);
        long[] existing = workState.visitedStorage();
        long[] additions = AssignedStorageService.assignedStorageInteractionPositions(level, villager, storage).stream()
                .mapToLong(BlockPos::asLong)
                .filter(target -> java.util.Arrays.stream(existing).noneMatch(visited -> visited == target))
                .toArray();
        if (additions.length == 0) {
            return;
        }
        long[] visited = java.util.Arrays.copyOf(existing, existing.length + additions.length);
        System.arraycopy(additions, 0, visited, existing.length, additions.length);
        workState.setVisitedStorage(visited);
    }


    private static void clearStorageDetour(HiredWorkContext context) {
        CourierWorkState workState = courierState(context);
        workState.clearStorageTarget();
        workState.clearStoragePurpose();
        workState.setReturningToRouteNode(false);
        clearStorageBatch(context);
        HiredStorageNavigationGoal.clearStorageTarget(context);
    }

    private static int collectInput(Villager villager, HiredWorkContext context, BlockPos input) {
        int containerCapacity = HiredVillagerRoles.courierTransferLimit(context.aptitude());
        if (!context.inventory().hasOutputSpace()) {
            return 0;
        }
        return AssignedStorageService.transferCourierItemsAtAssignedStorage(
                villager,
                input,
                containerCapacity,
                context.inventory()::insertOutput);
    }

    private static int cargoItemCount(HiredWorkContext context) {
        return context.inventory().collectOutputItems().stream()
                .mapToInt(output -> output.stack().getCount())
                .sum();
    }

    private static AssignedStorageService.AssignedOutputState compatibleOutputState(
            Villager villager,
            HiredWorkContext context) {
        List<ItemStack> cargo = context.inventory().collectOutputItems().stream()
                .map(output -> output.stack())
                .filter(stack -> !stack.isEmpty())
                .toList();
        return AssignedStorageService.assignedOutputStateFor(villager, cargo);
    }

    private static Set<BlockPos> purposePositions(ServerLevel level, Villager villager, String purpose) {
        return AssignedStorageService.assignedStorage(level, villager).stream()
                .filter(record -> record.dimension().equals(level.dimension()))
                .filter(record -> purpose.equals(AssignedStorageService.normalizePurpose(record.purpose())))
                .filter(record -> AssignedStorageService.isValidContainerForPurpose(level, record.pos(), purpose))
                .map(AssignedContainerRecord::pos)
                .collect(Collectors.toSet());
    }

    private static void beginOutbound(HiredWorkContext context) {
        setPhase(context, PHASE_OUTBOUND);
        CourierWorkState workState = courierState(context);
        workState.setReturnToInputSweep(false);
        workState.clearVisitedStorage();
        workState.routeCursor().reset(0);
    }

    private static void beginDeliverySweep(HiredWorkContext context) {
        setPhase(context, PHASE_DELIVER);
        CourierWorkState workState = courierState(context);
        workState.setReturnToInputSweep(false);
        workState.clearVisitedStorage();
        workState.routeCursor().reset(0);
    }

    private static void beginReturn(HiredWorkContext context, List<BlockPos> routeNodes) {
        setPhase(context, PHASE_RETURN);
        CourierWorkState workState = courierState(context);
        workState.setReturnToInputSweep(false);
        workState.clearVisitedStorage();
        workState.routeCursor().reset(routeNodes.size() - 1);
    }

    private static String phase(HiredWorkContext context) {
        return courierState(context).phase(PHASE_PICKUP);
    }

    private static void setPhase(HiredWorkContext context, String phase) {
        courierState(context).setPhase(phase);
        clearStorageDetour(context);
    }

    private static CourierWorkState courierState(HiredWorkContext context) {
        return new CourierWorkState(context.state());
    }

    private static void clearTrip(HiredWorkContext context) {
        courierState(context).clearTrip();
        HiredStorageNavigationGoal.clearStorageTarget(context);
    }

}
