package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementWorkerPoolRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWorkerPoolSnapshotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementWorkerPoolManagementService {
    private SettlementWorkerPoolManagementService() { }

    public static void handle(ServerPlayer player, SettlementWorkerPoolRequestPayload request) {
        if (!ServerboundRequestLimiter.tryAcquire(player, SettlementWorkerPoolRequestPayload.TYPE.id(), 5L)) {
            PacketDistributor.sendToPlayer(player, unavailable(player, request, "busy"));
            return;
        }
        var data = SettlementSavedData.get(player.serverLevel());
        data.workerPoolRequests.put(player.getUUID(), request.request());
        var work = prepare(player, request, payload -> {
            if (data.workerPoolRequests.remove(player.getUUID(), request.request())) PacketDistributor.sendToPlayer(player, payload);
        });
        if (data.readOnly()) work.run();
        else data.simulation.enqueue(new RequestKey(player.getUUID(), request.request()), SettlementWork.guard(work,
                () -> request.request().equals(data.workerPoolRequests.get(player.getUUID()))));
    }

    static Runnable prepare(ServerPlayer player, SettlementWorkerPoolRequestPayload request, Consumer<SettlementWorkerPoolSnapshotPayload> complete) {
        var town = SettlementSavedData.get(player.serverLevel()).get(new VillageAllegianceId(request.town())).orElse(null);
        String failure = access(player, town, request);
        if (!failure.isEmpty()) return () -> complete.accept(unavailable(player, request, failure));
        if (SettlementSavedData.get(player.serverLevel()).readOnly())
            return () -> complete.accept(unavailable(player, request, "recovery_read_only"));
        return new Inspection(player, town, request, complete).work();
    }

    private static final class Inspection {
        private final ServerPlayer player;
        private final SettlementRecord town;
        private final SettlementWorkerPoolRequestPayload request;
        private final Consumer<SettlementWorkerPoolSnapshotPayload> complete;
        private final List<SettlementWorkerPoolSnapshotPayload.Resident> residents = new ArrayList<>();
        private final List<SettlementWorkerPoolSnapshotPayload.Worksite> worksites = new ArrayList<>();
        private SettlementDemandService.Snapshot snapshot;
        private SettlementWorkerPoolNeeds needs;
        private SettlementWorkerPool.Configuration configuration;
        private String feedback = "";
        private String blocker = "";
        private boolean applied;
        private long observedRevision;
        private int matched;

        private Inspection(ServerPlayer player, SettlementRecord town, SettlementWorkerPoolRequestPayload request,
                           Consumer<SettlementWorkerPoolSnapshotPayload> complete) {
            this.player = player;
            this.town = town;
            this.request = request;
            this.complete = complete;
        }

        private Runnable work() {
            return new SettlementWork(List.of(
                    this::apply,
                    SettlementWork.lazy(() -> SettlementDemandService.plan(this.player.serverLevel(), this.town, this.player, snapshot -> {
                        this.snapshot = snapshot;
                        this.needs = new SettlementWorkerPoolNeeds(this.player.serverLevel(), this.town, snapshot);
                    })),
                    SettlementWork.lazy(() -> SettlementWork.each(this.town.residents().keySet().stream().sorted().toList(), this::resident)),
                    SettlementWork.lazy(() -> SettlementWork.each(this.town.areas().values().stream()
                            .filter(area -> area.type().role() != null).sorted(Comparator.comparing(SettlementArea::id)).toList(), this::worksite)),
                    this::finish));
        }

        private void apply() {
            this.feedback = access(this.player, this.town, this.request);
            if (!this.feedback.isEmpty()) return;
            boolean mutation = !(this.request.command() instanceof SettlementWorkerPoolRequestPayload.View)
                    && !(this.request.command() instanceof SettlementWorkerPoolRequestPayload.Preview);
            if (mutation) {
                this.feedback = change(this.player, this.town, this.request);
                this.applied = this.feedback.isEmpty();
            }
            this.observedRevision = this.town.revision();
            var selected = this.town.workerPools().get(this.request.pool());
            this.configuration = this.request.command() instanceof SettlementWorkerPoolRequestPayload.Preview preview ? preview.configuration()
                    : selected == null ? null : selected.configuration();
            if (this.configuration != null) this.blocker = this.town.workerPoolConfigurationFailure(this.request.pool(), this.configuration, true);
            if (this.blocker.isEmpty() && selected != null && selected.state() == SettlementWorkerPool.State.SUSPENDED)
                this.blocker = selected.blocker();
        }

        private void resident(UUID id) {
            if (!access(this.player, this.town, this.request).isEmpty()) return;
            String name = text(SettlementEquipmentDemand.name(this.player.serverLevel(), id));
            String needle = this.request.query().strip().toLowerCase(Locale.ROOT);
            if (!name.toLowerCase(Locale.ROOT).contains(needle) && !id.toString().contains(needle)) return;
            int index = this.matched++;
            int first = this.request.page() * SettlementWorkerPoolSnapshotPayload.PAGE_SIZE;
            if (index < first || index >= first + SettlementWorkerPoolSnapshotPayload.PAGE_SIZE) return;
            var owner = this.town.workerPoolFor(id);
            String blocker = owner != null && !owner.equals(this.request.pool()) ? "pool_resident_enrolled"
                    : SettlementWorkerPoolAssignment.inspectEligibility(this.player.serverLevel(), this.town, id,
                            this.player.serverLevel().getGameTime()).blocker();
            this.residents.add(new SettlementWorkerPoolSnapshotPayload.Resident(id, name, owner, this.town.duties().get(id),
                    this.town.manuallyStaffed(id), this.town.workerPoolCooldown(id, this.player.serverLevel().getGameTime()), blocker));
        }

        private void worksite(SettlementArea area) {
            var plan = this.town.worksitePlans().get(area.id());
            var staffing = this.configuration == null ? null : this.configuration.worksites().stream()
                    .filter(site -> site.area().equals(area.id())).findFirst().orElse(null);
            if (staffing == null) staffing = SettlementWorkerPool.Staffing.defaults(this.town, area.id());
            var need = this.needs.area(area.id());
            boolean available = area.enabled() && this.town.territory().active() && this.town.owns(area)
                    && (plan == null || plan.settings().enabled())
                    && (area.type() != SettlementAreaType.CONSTRUCTION || this.town.stage().projects() > 0)
                    && (area.type() != SettlementAreaType.TRADING_POST || this.town.stage().ordinal() >= SettlementStage.VILLAGE.ordinal());
            this.worksites.add(new SettlementWorkerPoolSnapshotPayload.Worksite(area.id(), text(area.name()), area.type(), area.center(),
                    available, SettlementWorkerPoolAssignment.count(this.town, area.id()), plan == null ? 1 : plan.desiredStaff(),
                    staffing.minimum(), staffing.maximum(), need.active(), need.protectedShortage(), need.priority(), need.demand(), cause(need.demand())));
        }

        private String cause(UUID id) {
            if (id == null) return "";
            for (var demand : this.snapshot.demands()) if (demand.supply().id().equals(id)) return text(demand.name());
            var policy = this.town.supplyPolicies().get(id);
            if (policy != null) return text(policy.configuration().name());
            for (var configured : this.town.supplyPolicies().values())
                if (configured.bindings().stream().anyMatch(binding -> binding.id().equals(id))) return text(configured.configuration().name());
            var project = this.town.projects().get(id);
            if (project == null) project = this.town.projectHistory().get(id);
            if (project != null) return text(project.label());
            for (var queue : this.town.productionQueues().values())
                if (queue.order(id) != null) return text(queue.order(id).output().getHoverName().getString());
            for (var queue : this.town.transferQueues().values())
                if (queue.order(id) != null) return text(queue.order(id).sample().getHoverName().getString());
            return id.toString();
        }

        private String area(UUID id) {
            var area = id == null ? null : this.town.workplace(id);
            return area == null ? id == null ? "" : id.toString() : text(area.name());
        }

        private void finish() {
            String failure = access(this.player, this.town, this.request);
            if (!failure.isEmpty()) {
                this.complete.accept(unavailable(this.player, this.request, failure));
                return;
            }
            var pools = this.town.workerPools().values().stream().sorted(Comparator.comparing(SettlementWorkerPool::id))
                    .map(pool -> new SettlementWorkerPoolSnapshotPayload.Summary(pool.id(), pool.configuration().name(),
                            pool.revision(), pool.state(), pool.blocker())).toList();
            var selected = this.town.workerPools().get(this.request.pool());
            var history = selected == null ? List.<SettlementWorkerPoolSnapshotPayload.History>of() : selected.history().reversed().stream()
                    .map(move -> new SettlementWorkerPoolSnapshotPayload.History(move.at(), text(SettlementEquipmentDemand.name(this.player.serverLevel(), move.resident())),
                            area(move.from()), area(move.to()), move.reason(), move.demand(), cause(move.demand()), move.priority())).toList();
            this.complete.accept(new SettlementWorkerPoolSnapshotPayload(this.player.serverLevel().dimension().location(), this.request.town(),
                    this.request.request(), this.observedRevision, this.player.serverLevel().getGameTime(), this.applied, editable(this.player, this.town),
                    this.feedback, this.blocker, pools, selected, this.request.query(), this.request.page(),
                    this.matched > (this.request.page() + 1) * SettlementWorkerPoolSnapshotPayload.PAGE_SIZE, this.residents, this.worksites, history));
        }
    }

    private static String change(ServerPlayer player, SettlementRecord town, SettlementWorkerPoolRequestPayload request) {
        if (SettlementSavedData.get(player.serverLevel()).readOnly()) return "recovery_read_only";
        if (!editable(player, town)) return "pool_permission_changed";
        var original = town.workerPools().get(request.pool());
        if (request.revision() != (original == null ? -1 : original.revision())) return "pool_changed";
        boolean applied;
        switch (request.command()) {
            case SettlementWorkerPoolRequestPayload.Save save -> {
                String failure = town.workerPoolConfigurationFailure(request.pool(), save.configuration(), save.active());
                if (!failure.isEmpty()) return failure;
                if (original == null && town.workerPools().size() >= SettlementWorkerPool.MAX_POOLS) return "pool_limit";
                applied = town.configureWorkerPool(player.getUUID(), request.pool(), request.revision(), save.configuration(), save.active());
            }
            case SettlementWorkerPoolRequestPayload.Change change -> {
                if (original == null) return "pool_missing";
                if (change.action() == SettlementWorkerPoolRequestPayload.Action.REMOVE)
                    applied = town.removeWorkerPool(player.getUUID(), request.pool(), request.revision());
                else {
                    boolean active = change.action() == SettlementWorkerPoolRequestPayload.Action.ACTIVATE;
                    String failure = town.workerPoolConfigurationFailure(request.pool(), original.configuration(), active);
                    if (!failure.isEmpty()) return failure;
                    applied = town.changeWorkerPool(player.getUUID(), request.pool(), request.revision(), active);
                }
            }
            case SettlementWorkerPoolRequestPayload.ReturnControl returned -> {
                if (original == null || !request.pool().equals(town.workerPoolFor(returned.resident()))) return "pool_resident_missing";
                if (returned.townRevision() > town.revision() || town.manualDutyRevision(returned.resident()) > returned.townRevision()) return "pool_changed";
                applied = town.returnToAutomaticStaffing(player.getUUID(), returned.resident());
            }
            default -> { return ""; }
        }
        return applied ? "" : "pool_changed";
    }

    private static String access(ServerPlayer player, SettlementRecord town, SettlementWorkerPoolRequestPayload request) {
        if (!player.isAlive() || player.isSpectator()) return "player_unavailable";
        if (town == null || !town.id().value().equals(request.town())
                || SettlementSavedData.get(player.serverLevel()).get(new VillageAllegianceId(request.town())).orElse(null) != town)
            return "town_missing";
        if (!town.dimension().equals(player.serverLevel().dimension().location())) return "wrong_dimension";
        return town.permits(player.getUUID(), SettlementPermission.VIEW) ? "" : "pool_permission_changed";
    }

    private static boolean editable(ServerPlayer player, SettlementRecord town) {
        return !SettlementSavedData.get(player.serverLevel()).readOnly() && town.permits(player.getUUID(), SettlementPermission.STAFF);
    }

    private static SettlementWorkerPoolSnapshotPayload unavailable(ServerPlayer player, SettlementWorkerPoolRequestPayload request, String feedback) {
        return new SettlementWorkerPoolSnapshotPayload(player.serverLevel().dimension().location(), request.town(), request.request(), 0,
                player.serverLevel().getGameTime(), false, false, feedback, feedback, List.of(), null, request.query(), request.page(), false,
                List.of(), List.of(), List.of());
    }

    private static String text(String value) { return value.substring(0, Math.min(128, value.length())); }
    private record RequestKey(UUID player, UUID request) { }
}
