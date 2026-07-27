package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.List;
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

public final class CourierWorker implements HiredRoleWorker {
    private static final String PHASE_TAG = "CourierPhase";
    private static final String STORAGE_TARGET_TAG = "CourierStorageTarget";
    private static final String STORAGE_PURPOSE_TAG = "CourierStoragePurpose";
    private static final String STORAGE_RETURN_TO_NODE_TAG = "CourierStorageReturnToNode";
    private static final String VISITED_STORAGE_TAG = "CourierVisitedStorage";
    private static final String ROUTE_INDEX_TAG = "CourierRouteIndex";
    private static final String ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG = "CourierRouteLastNodeReachedGameTime";
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
        int lastIndex = route.nodes().size() - 1;
        int index = context.state().contains(ROUTE_INDEX_TAG, Tag.TAG_INT)
                ? Math.clamp(context.state().getInt(ROUTE_INDEX_TAG), 0, lastIndex)
                : outbound ? 0 : lastIndex;
        BlockPos node = route.nodes().get(index);
        boolean deliverySweep = PHASE_DELIVER.equals(phase(context));
        boolean servicingInputs = outbound && !deliverySweep;

        WorkResult detour = continueStorageDetour(level, villager, context, route, node, index, outbound);
        if (detour != null) {
            return detour;
        }

        long gameTime = level.getGameTime();
        initializeRouteWatchdog(context, gameTime);
        if (villager.blockPosition().distSqr(node) > ROUTE_ARRIVAL_DISTANCE_SQR
                && routeRecoveryDue(context, gameTime)) {
            index = HiredRouteNavigator.restartAtNearestNode(villager, route);
            setRouteIndex(context, index);
            context.state().putLong(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG, gameTime);
            node = route.nodes().get(index);
        }
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

        context.state().putLong(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG, gameTime);

        BlockPos storage = servicingInputs
                ? selectInputAtNode(level, villager, context, route, index)
                : selectOutputAtNode(level, villager, context, route, index);
        if (storage != null) {
            beginStorageDetour(context, storage, servicingInputs
                    ? AssignedStorageService.INPUT_PURPOSE
                    : AssignedStorageService.OUTPUT_PURPOSE);
            return continueStorageDetour(level, villager, context, route, node, index, outbound);
        }

        boolean finished = outbound ? index >= lastIndex : index <= 0;
        if (!finished) {
            setRouteIndex(context, outbound ? index + 1 : index - 1);
            return WorkResult.progressed(outbound
                    ? "interaction.work.courier.following_route_outbound"
                    : "interaction.work.courier.following_route_return");
        }

        context.state().remove(ROUTE_INDEX_TAG);
        context.state().remove(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG);
        if (outbound) {
            if (deliverySweep && context.inventory().hasOutputItems()) {
                clearVisitedStorage(context);
                setRouteIndex(context, 0);
                HiredWorkerBrain.setFailure(context, "courier_output_unavailable", level.getGameTime() + 100L);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, node);
                return WorkResult.idle("interaction.work.courier.output_unavailable");
            }
            beginReturn(context, route);
        } else {
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
        BlockPos storage = storedTarget(context);
        if (storage == null || !context.state().contains(STORAGE_PURPOSE_TAG, Tag.TAG_STRING)) {
            return null;
        }
        if (context.state().getBoolean(STORAGE_RETURN_TO_NODE_TAG)) {
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
            context.state().putLong(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG, level.getGameTime());
            return null;
        }
        HiredStorageNavigationGoal.Result movement = HiredStorageNavigationGoal.moveToStorageTarget(
                level, context, villager, storage, MOVE_SPEED);
        String purpose = context.state().getString(STORAGE_PURPOSE_TAG);
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.setStorageTarget(context, storage);
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, storage);
            return WorkResult.progressed(AssignedStorageService.INPUT_PURPOSE.equals(purpose)
                    ? "interaction.work.courier.moving_to_input"
                    : "interaction.work.courier.moving_to_output");
        }
        rememberVisitedStorage(context, storage);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (movement == HiredStorageNavigationGoal.Result.FAILED) {
            context.state().putBoolean(STORAGE_RETURN_TO_NODE_TAG, true);
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

        BlockPos nextStorage = AssignedStorageService.INPUT_PURPOSE.equals(purpose)
                ? selectInputAtNode(level, villager, context, route, routeIndex)
                : selectOutputAtNode(level, villager, context, route, routeIndex);
        if (nextStorage != null) {
            beginStorageDetour(context, nextStorage, purpose);
        } else {
            context.state().putBoolean(STORAGE_RETURN_TO_NODE_TAG, true);
        }
        return result;
    }

    private static BlockPos selectInputAtNode(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route,
            int routeIndex) {
        if (!context.inventory().hasOutputSpace()) {
            return null;
        }
        Set<BlockPos> inputs = purposePositions(level, villager, AssignedStorageService.INPUT_PURPOSE);
        return AssignedStorageService.nearestAssignedInputStoragePosContaining(
                level,
                villager,
                stack -> !stack.isEmpty(),
                pos -> inputs.contains(pos)
                        && !hasVisitedStorage(context, pos)
                        && isTetheredToNode(route, pos, routeIndex));
    }

    private static BlockPos selectOutputAtNode(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredRoute route,
            int routeIndex) {
        if (!context.inventory().hasOutputItems()) {
            return null;
        }
        Set<BlockPos> outputs = purposePositions(level, villager, AssignedStorageService.OUTPUT_PURPOSE).stream()
                .filter(pos -> !hasVisitedStorage(context, pos))
                .filter(pos -> isTetheredToNode(route, pos, routeIndex))
                .collect(Collectors.toSet());
        return selectOutput(level, villager, context, outputs);
    }

    private static boolean isTetheredToNode(HiredRoute route, BlockPos storage, int routeIndex) {
        if (routeIndex < 0 || routeIndex >= route.nodes().size()) {
            return false;
        }
        double selectedDistance = storage.distSqr(route.nodes().get(routeIndex));
        if (selectedDistance > MAX_STORAGE_TETHER_DISTANCE_SQR) {
            return false;
        }
        for (int index = 0; index < route.nodes().size(); index++) {
            double distance = storage.distSqr(route.nodes().get(index));
            if (distance < selectedDistance || (distance == selectedDistance && index < routeIndex)) {
                return false;
            }
        }
        return true;
    }

    private static void beginStorageDetour(HiredWorkContext context, BlockPos storage, String purpose) {
        storeTarget(context, storage);
        context.state().putString(STORAGE_PURPOSE_TAG, purpose);
        context.state().remove(STORAGE_RETURN_TO_NODE_TAG);
    }

    private static boolean hasVisitedStorage(HiredWorkContext context, BlockPos storage) {
        long target = storage.asLong();
        for (long visited : context.state().getLongArray(VISITED_STORAGE_TAG)) {
            if (visited == target) {
                return true;
            }
        }
        return false;
    }

    private static void rememberVisitedStorage(HiredWorkContext context, BlockPos storage) {
        if (hasVisitedStorage(context, storage)) {
            return;
        }
        long[] existing = context.state().getLongArray(VISITED_STORAGE_TAG);
        long[] visited = new long[existing.length + 1];
        System.arraycopy(existing, 0, visited, 0, existing.length);
        visited[existing.length] = storage.asLong();
        context.state().putLongArray(VISITED_STORAGE_TAG, visited);
    }

    private static void clearVisitedStorage(HiredWorkContext context) {
        context.state().remove(VISITED_STORAGE_TAG);
    }

    private static void clearStorageDetour(HiredWorkContext context) {
        clearStoredTarget(context);
        context.state().remove(STORAGE_PURPOSE_TAG);
        context.state().remove(STORAGE_RETURN_TO_NODE_TAG);
        HiredStorageNavigationGoal.clearStorageTarget(context);
    }

    private static int collectInput(Villager villager, HiredWorkContext context, BlockPos input) {
        int containerCapacity = HiredVillagerRoles.courierTransferLimit(context.aptitude());
        if (!context.inventory().hasOutputSpace()) {
            return 0;
        }
        return AssignedStorageService.transferItemsAtAssignedStorage(
                villager,
                input,
                stack -> !stack.isEmpty(),
                containerCapacity,
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

    private static void beginOutbound(HiredWorkContext context) {
        setPhase(context, PHASE_OUTBOUND);
        clearVisitedStorage(context);
        setRouteIndex(context, 0);
    }

    private static void beginDeliverySweep(HiredWorkContext context) {
        setPhase(context, PHASE_DELIVER);
        clearVisitedStorage(context);
        setRouteIndex(context, 0);
    }

    private static void beginReturn(HiredWorkContext context, HiredRoute route) {
        setPhase(context, PHASE_RETURN);
        clearVisitedStorage(context);
        setRouteIndex(context, route.nodes().size() - 1);
    }

    private static String phase(HiredWorkContext context) {
        String phase = context.state().getString(PHASE_TAG);
        return phase.isBlank() ? PHASE_PICKUP : phase;
    }

    private static void setPhase(HiredWorkContext context, String phase) {
        context.state().putString(PHASE_TAG, phase);
        context.state().remove(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG);
        clearStorageDetour(context);
    }

    private static void setRouteIndex(HiredWorkContext context, int index) {
        context.state().putInt(ROUTE_INDEX_TAG, index);
        context.state().remove(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG);
    }

    private static void initializeRouteWatchdog(HiredWorkContext context, long gameTime) {
        CompoundTag state = context.state();
        if (!state.contains(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG, Tag.TAG_LONG)
                || state.getLong(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG) > gameTime) {
            state.putLong(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG, gameTime);
        }
    }

    private static boolean routeRecoveryDue(HiredWorkContext context, long gameTime) {
        return gameTime - context.state().getLong(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG) >= ROUTE_RECOVERY_TICKS;
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
        state.remove(STORAGE_PURPOSE_TAG);
        state.remove(STORAGE_RETURN_TO_NODE_TAG);
        state.remove(VISITED_STORAGE_TAG);
        state.remove(ROUTE_INDEX_TAG);
        state.remove(ROUTE_LAST_NODE_REACHED_GAME_TIME_TAG);
        HiredStorageNavigationGoal.clearStorageTarget(context);
    }

}
