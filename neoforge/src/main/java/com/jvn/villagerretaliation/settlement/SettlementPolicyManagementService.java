package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementPolicyManagementService {
    private SettlementPolicyManagementService() { }

    public static void handle(ServerPlayer player, SettlementPolicyRequestPayload request) {
        if (!ServerboundRequestLimiter.tryAcquire(player, SettlementPolicyRequestPayload.TYPE.id(), 5L)) {
            PacketDistributor.sendToPlayer(player, unavailable(player, request, "busy"));
            return;
        }
        var data = SettlementSavedData.get(player.serverLevel());
        data.policyRequests.put(player.getUUID(), request.request());
        var work = prepare(player, request, payload -> {
            if (data.policyRequests.remove(player.getUUID(), request.request())) PacketDistributor.sendToPlayer(player, payload);
        });
        if (request.command() instanceof SettlementPolicyRequestPayload.View || data.readOnly()) work.run();
        else data.simulation.enqueue(new RequestKey(player.getUUID(), request.request()), SettlementWork.guard(work,
                () -> request.request().equals(data.policyRequests.get(player.getUUID()))));
    }

    static Runnable prepare(ServerPlayer player, SettlementPolicyRequestPayload request,
                            java.util.function.Consumer<SettlementPolicySnapshotPayload> complete) {
        var level = player.serverLevel();
        var town = SettlementSavedData.get(level).get(new VillageAllegianceId(request.town())).orElse(null);
        String access = access(player, town, request);
        if (!access.isEmpty()) return () -> complete.accept(unavailable(player, request, access));
        var original = request.policy() == null ? null : town.supplyPolicies().get(request.policy());
        if (request.command() instanceof SettlementPolicyRequestPayload.View)
            return () -> complete.accept(snapshot(player, town, request, false, "", "", List.of(), List.of()));
        if (!(request.command() instanceof SettlementPolicyRequestPayload.Preview) && !editable(player, town))
            return () -> complete.accept(unavailable(player, request, "policy_permission_changed"));
        if (SettlementSavedData.get(level).readOnly())
            return () -> complete.accept(unavailable(player, request, "recovery_read_only"));
        if (request.command() instanceof SettlementPolicyRequestPayload.Change change && change.state() != SettlementSupplyPolicy.State.ACTIVE)
            return () -> {
                String failure = apply(player, town, request, original == null ? null : original.configuration());
                complete.accept(snapshot(player, town, request, failure.isEmpty(), failure, "", List.of(), List.of()));
            };
        var configuration = switch (request.command()) {
            case SettlementPolicyRequestPayload.Preview preview -> preview.configuration();
            case SettlementPolicyRequestPayload.Save save -> save.configuration();
            case SettlementPolicyRequestPayload.Change ignored -> original == null ? null : original.configuration();
            case SettlementPolicyRequestPayload.View ignored -> null;
        };
        if (configuration == null) return () -> complete.accept(unavailable(player, request, "policy_missing"));
        var draft = new SettlementSupplyPolicy(request.policy(), configuration, player.getUUID(), () -> {});
        if (original != null) for (var binding : original.bindings()) draft.bind(binding);
        String targetFailure = SettlementPolicyValidation.target(level, town, draft);
        if (!targetFailure.isEmpty()) return () -> {
            String failure = request.command() instanceof SettlementPolicyRequestPayload.Save save && !save.active()
                    ? apply(player, town, request, configuration) : targetFailure;
            complete.accept(snapshot(player, town, request, failure.isEmpty(), failure, targetFailure, List.of(), List.of()));
        };
        var work = new ArrayList<Runnable>();
        var snapshots = new ArrayList<SettlementDemandService.Snapshot>(1);
        var plans = new ArrayList<SettlementPolicyPlanner.Plan>(1);
        var errors = new ArrayList<String>();
        work.add(() -> {
            String failure = SettlementPolicyValidation.configuration(level, town, draft);
            if (!failure.isEmpty()) errors.add(failure);
        });
        work.add(SettlementWork.each(configuration.steps(), step -> {
            String failure = SettlementPolicyValidation.step(level, town, draft, step);
            if (!failure.isEmpty()) errors.add(failure);
        }));
        work.add(SettlementWork.lazy(() -> SettlementDemandService.plan(level, town, player, snapshots::add)));
        work.add(SettlementWork.lazy(() -> SettlementPolicyPlanner.plan(level, town, draft, snapshots.getFirst(), new HashSet<>(), plans::add)));
        work.add(() -> {
            String failure = access(player, town, request);
            if (!failure.isEmpty()) {
                complete.accept(unavailable(player, request, failure));
                return;
            }
            var plan = plans.getFirst();
            String blocker = errors.isEmpty() ? plan.blocker() : errors.getFirst();
            if (blocker.isEmpty()) blocker = plan.steps().stream().map(step -> previewBlocker(level, town, step))
                    .filter(value -> !value.isEmpty()).findFirst().orElse("");
            boolean preview = request.command() instanceof SettlementPolicyRequestPayload.Preview;
            boolean activating = request.command() instanceof SettlementPolicyRequestPayload.Change
                    || request.command() instanceof SettlementPolicyRequestPayload.Save save && save.active();
            if (!preview) {
                if (activating && !blocker.isEmpty()) failure = blocker;
                else failure = apply(player, town, request, configuration);
            }
            var inputs = new ArrayList<SettlementPolicySnapshotPayload.Need>();
            var target = SettlementPolicyPlanner.targets(level, town, draft, snapshots.getFirst().policyDraft(draft.id(), configuration));
            for (var need : target) inputs.add(need(null, need));
            for (var step : configuration.steps()) {
                var planned = plan.steps().stream().filter(value -> value.definition().id().equals(step.id())).findFirst().orElse(null);
                var templates = SettlementPolicyPlanner.inputs(level, town, step, planned == null ? 1 : Math.max(1, planned.proposed()));
                for (var template : templates) {
                    var current = planned == null ? null : planned.inputs().stream().filter(value -> value.id().equals(template.id())).findFirst().orElse(null);
                    inputs.add(need(step.id(), current == null ? template : current));
                }
            }
            var production = plan.steps().stream().map(step -> new SettlementPolicySnapshotPayload.Production(step.definition().id(),
                    step.output(), step.proposed(), step.requested(), step.retained(), previewBlocker(level, town, step))).toList();
            complete.accept(snapshot(player, town, request, !preview && failure.isEmpty(), failure, blocker, inputs, production));
        });
        return new SettlementWork(work);
    }

    private static String previewBlocker(net.minecraft.server.level.ServerLevel level, SettlementRecord town, SettlementPolicyPlanner.Step step) {
        if (!step.blocker().isEmpty() || step.requested() == 0) return step.blocker();
        int capacity = SettlementPolicyValidation.capacity(level, town, step.definition().destination(), step.output());
        if (capacity < 0) return "output_storage_unavailable";
        int batch = step.definition().kind() == SettlementSupplyPolicy.StepKind.PRODUCTION ? step.output().getCount() : 1;
        return capacity < batch ? "output_full" : "";
    }

    private static String apply(ServerPlayer player, SettlementRecord town, SettlementPolicyRequestPayload request,
                        SettlementSupplyPolicy.Configuration configuration) {
        String failure = access(player, town, request);
        if (!failure.isEmpty()) return failure;
        if (!editable(player, town)) return "policy_permission_changed";
        if (request.revision() == Long.MAX_VALUE) return "policy_revision_changed";
        var existing = town.supplyPolicies().get(request.policy());
        if (existing == null ? request.revision() != 0 || !(request.command() instanceof SettlementPolicyRequestPayload.Save)
                : existing.revision() != request.revision() || existing.state() == SettlementSupplyPolicy.State.REMOVING)
            return "policy_revision_changed";
        if (existing == null && town.supplyPolicies().size() >= SettlementSupplyPolicy.MAX_POLICIES) return "policy_limit";
        boolean activating = request.command() instanceof SettlementPolicyRequestPayload.Change change && change.state() == SettlementSupplyPolicy.State.ACTIVE
                || request.command() instanceof SettlementPolicyRequestPayload.Save save && save.active();
        if (activating) {
            var check = new SettlementSupplyPolicy(request.policy(), configuration, player.getUUID(), () -> {});
            if (existing != null) for (var binding : existing.bindings()) check.bind(binding);
            failure = SettlementPolicyValidation.configuration(player.serverLevel(), town, check);
            if (!failure.isEmpty()) return failure;
            for (var step : configuration.steps()) {
                failure = SettlementPolicyValidation.step(player.serverLevel(), town, check, step);
                if (!failure.isEmpty()) return failure;
            }
        }
        long now = player.serverLevel().getGameTime();
        if (request.command() instanceof SettlementPolicyRequestPayload.Save) {
            if (existing == null) {
                existing = new SettlementSupplyPolicy(request.policy(), configuration, player.getUUID(), town::supplyChanged);
                existing.configure(configuration, player.getUUID(), now);
                if (!town.addSupplyPolicy(existing)) return "policy_limit";
            } else existing.configure(configuration, player.getUUID(), now);
        }
        if (existing == null) return "policy_missing";
        var state = request.command() instanceof SettlementPolicyRequestPayload.Change change ? change.state()
                : activating ? SettlementSupplyPolicy.State.ACTIVE : SettlementSupplyPolicy.State.PAUSED;
        if (activating) SettlementPolicyOrders.rearm(player.serverLevel(), town, existing);
        existing.state(state, player.getUUID(), "", now);
        SettlementPolicyOrders.stop(player.serverLevel(), town, existing);
        SettlementSimulationService.managed(player, town);
        SettlementSavedData.get(player.serverLevel()).simulation.enqueue(new PolicyKey(town.id()),
                SettlementWork.lazy(() -> SettlementPolicyService.cycle(player.serverLevel(), town)));
        return "";
    }

    static void manuallyStopped(net.minecraft.server.level.ServerLevel level, SettlementRecord town, UUID order) {
        var managed = town.managedSupplyOrder(order);
        if (managed == null) return;
        var policy = managed.policy();
        var binding = policy.bindings().stream().filter(value -> value.id().equals(managed.binding())).findFirst().orElseThrow();
        policy.binding(binding.execution(SettlementSupplyPolicy.BindingState.SUSPENDED, binding.target(), binding.ceiling()));
        policy.state(SettlementSupplyPolicy.State.SUSPENDED, policy.authority(), "policy_order_suspended", level.getGameTime());
        SettlementPolicyOrders.stop(level, town, policy);
    }

    private static String access(ServerPlayer player, SettlementRecord town, SettlementPolicyRequestPayload request) {
        if (player == null || !player.isAlive() || player.isSpectator()) return "player_unavailable";
        if (town == null || !town.id().value().equals(request.town())
                || SettlementSavedData.get(player.serverLevel()).get(town.id()).orElse(null) != town) return "town_missing";
        if (!town.dimension().equals(player.serverLevel().dimension().location())) return "wrong_dimension";
        if (!town.permits(player.getUUID(), SettlementPermission.VIEW)) return "policy_permission_changed";
        return request.area() == null || town.workplace(request.area()) != null ? "" : "policy_worksite_unavailable";
    }

    private static boolean editable(ServerPlayer player, SettlementRecord town) {
        return !SettlementSavedData.get(player.serverLevel()).readOnly() && town.permits(player.getUUID(), SettlementPermission.STAFF)
                && town.permits(player.getUUID(), SettlementPermission.LOGISTICS);
    }

    private static SettlementPolicySnapshotPayload.Need need(UUID step, SettlementPolicyPlanner.Input input) {
        var requirement = input.requirement();
        String name = requirement.alternatives().isEmpty() ? requirement.key() : requirement.alternatives().getFirst().getHoverName().getString();
        return new SettlementPolicySnapshotPayload.Need(step, input.id(), text(name), requirement.unit(), requirement.quantity(),
                input.available(), input.committed(), input.incoming(), input.missing(), input.known(),
                requirement.alternatives().stream().limit(16).toList(), input.sources().stream().limit(512).toList());
    }

    private static SettlementPolicySnapshotPayload snapshot(ServerPlayer player, SettlementRecord town, SettlementPolicyRequestPayload request,
            boolean applied, String feedback, String blocker, List<SettlementPolicySnapshotPayload.Need> needs,
            List<SettlementPolicySnapshotPayload.Production> production) {
        String failure = access(player, town, request);
        if (!failure.isEmpty()) return unavailable(player, request, failure);
        var level = player.serverLevel();
        var policies = town.supplyPolicies().values().stream().sorted(Comparator.comparing(SettlementSupplyPolicy::id))
                .map(policy -> new SettlementPolicySnapshotPayload.Summary(policy.id(), policy.configuration().name(), policy.revision(), policy.state(), policy.blocker())).toList();
        var selected = request.policy() == null ? null : town.supplyPolicies().get(request.policy());
        var worksites = town.areas().values().stream().filter(area -> area.type().role() != null).sorted(Comparator.comparing(SettlementArea::id))
                .map(area -> new SettlementPolicySnapshotPayload.Worksite(area.id(), text(area.name()), area.type(), area.center(),
                        area.enabled() && town.owns(area) && town.worksitePlans().containsKey(area.id()))).toList();
        var sources = request.area() == null ? List.<BlockPos>of() : SettlementSupplyStock.storages(level, town, request.area(), false);
        var destinations = request.area() == null ? List.<BlockPos>of() : SettlementSupplyStock.storages(level, town, request.area(), true);
        var positions = new LinkedHashSet<>(sources);
        positions.addAll(destinations);
        if (request.area() == null) town.depots().values().forEach(depot -> positions.add(depot.position()));
        var storages = positions.stream().sorted().limit(512).map(position -> {
            var depot = town.depots().values().stream().filter(value -> value.position().equals(position)).findFirst().orElse(null);
            var area = depot == null ? null : town.areas().get(depot.area());
            String name = area == null ? position.toShortString() : area.name() + " / " + position.toShortString();
            boolean visible = !com.jvn.villagerretaliation.inventory.AssignedStorageService.containerLoaded(level, position)
                    || com.jvn.villagerretaliation.inventory.PartyContainerLootService.isAvailable(level, position, player);
            return new SettlementPolicySnapshotPayload.Storage(position, text(name), sources.contains(position), destinations.contains(position),
                    visible && SettlementSupplyStock.contents(level, town, position) != null);
        }).toList();
        var consumers = new ArrayList<SettlementPolicySnapshotPayload.Consumer>();
        consumers.add(new SettlementPolicySnapshotPayload.Consumer(town.id().value(),
                net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.settlement.food_reserve").getString(),
                SettlementSupplyPolicy.TargetKind.FOOD));
        for (var area : town.equipmentPlans().keySet()) if (request.area() == null || request.area().equals(area)) {
            var workplace = town.workplace(area);
            if (workplace != null) consumers.add(new SettlementPolicySnapshotPayload.Consumer(area, text(workplace.name()),
                    SettlementSupplyPolicy.TargetKind.EQUIPMENT));
        }
        if (request.area() != null) for (var entry : town.duties().entrySet()) {
            var area = town.workplace(entry.getValue());
            if (area != null && area.id().equals(request.area())
                    && com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed.supports(area.type().role()))
                consumers.add(new SettlementPolicySnapshotPayload.Consumer(entry.getKey(), text(SettlementEquipmentDemand.name(level, entry.getKey())),
                        SettlementSupplyPolicy.TargetKind.EQUIPMENT));
        }
        for (var project : town.projects().values()) if (project.active() && (request.area() == null || request.area().equals(project.area())))
            consumers.add(new SettlementPolicySnapshotPayload.Consumer(project.id(), text(project.label()), SettlementSupplyPolicy.TargetKind.CONSUMER));
        if (request.area() != null) {
            var queue = town.productionQueues().get(request.area());
            if (queue != null) for (var order : queue.orders()) if (!order.cancelled() && town.managedSupplyOrder(order.id()) == null)
                consumers.add(new SettlementPolicySnapshotPayload.Consumer(order.id(), text(order.output().getHoverName().getString()),
                        SettlementSupplyPolicy.TargetKind.CONSUMER));
        }
        return new SettlementPolicySnapshotPayload(level.dimension().location(), request.town(), request.request(), applied, editable(player, town),
                feedback, blocker, policies, selected == null ? null : selected.save(level.registryAccess()), worksites, storages,
                consumers.stream().limit(512).toList(), needs.stream().limit(1024).toList(), production);
    }

    private static SettlementPolicySnapshotPayload unavailable(ServerPlayer player, SettlementPolicyRequestPayload request, String feedback) {
        return new SettlementPolicySnapshotPayload(player.serverLevel().dimension().location(), request.town(), request.request(), false, false,
                feedback, feedback, List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static String text(String value) { return value.length() > 128 ? value.substring(0, 128) : value; }
    private record RequestKey(UUID player, UUID request) { }
    private record PolicyKey(VillageAllegianceId town) { }
}
