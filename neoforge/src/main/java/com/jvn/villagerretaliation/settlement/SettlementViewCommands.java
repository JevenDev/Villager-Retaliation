package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.SettlementViewBatchPayload;
import com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload;
import com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Outcome;
import com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Status;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

final class SettlementViewCommands {
    private final Map<UUID, SettlementViewBatchResultPayload> completed = new LinkedHashMap<>();
    private SettlementViewBatchPayload pending;
    private final List<Outcome> outcomes = new ArrayList<>();

    void clear() {
        this.pending = null;
        this.outcomes.clear();
        this.completed.clear();
    }

    void submit(SettlementViewService.Session session, SettlementViewBatchPayload request) {
        var result = this.completed.get(request.operation());
        if (result != null) {
            PacketDistributor.sendToPlayer(session.player, result);
            return;
        }
        if (this.pending != null && this.pending.operation().equals(request.operation())) return;
        if (this.pending != null || this.completed.size() >= 128) {
            var denied = new SettlementViewBatchResultPayload(session.id, request.operation(),
                    request.targets().stream().map(target -> new Outcome(target.resident(), Status.FAILED,
                            this.pending != null ? "Another command is pending" : "Session command limit reached. Reopen the view")).toList());
            if (this.completed.size() < 128) this.completed.put(request.operation(), denied);
            PacketDistributor.sendToPlayer(session.player, denied);
            return;
        }
        this.pending = request;
        this.outcomes.clear();
    }

    int tick(SettlementViewService.Session session, int budget) {
        if (this.pending == null) return 0;
        int processed = 0;
        while (processed < budget && this.outcomes.size() < this.pending.targets().size()) {
            var target = this.pending.targets().get(this.outcomes.size());
            this.outcomes.add(apply(session, this.pending, target));
            processed++;
        }
        if (this.outcomes.size() == this.pending.targets().size()) {
            var result = new SettlementViewBatchResultPayload(session.id, this.pending.operation(), this.outcomes);
            this.completed.put(this.pending.operation(), result);
            this.pending = null;
            this.outcomes.clear();
            PacketDistributor.sendToPlayer(session.player, result);
        }
        return processed;
    }

    static Outcome apply(SettlementViewService.Session session, SettlementViewBatchPayload request, SettlementViewBatchPayload.Target target) {
        if (!session.id.equals(request.session())) return new Outcome(target.resident(), Status.FAILED, "Viewing session changed");
        var town = session.town;
        var player = session.player;
        UUID id = target.resident();
        if (!SettlementViewService.eligible(player, town) || !town.permits(player.getUUID(), request.action().permission())) {
            return new Outcome(id, Status.FAILED, "Permission or viewing session changed");
        }
        boolean member = VillageAllegianceRegistrySavedData.get(session.level).canonicalRecord(town.id())
                .map(record -> record.residents().containsKey(id)).orElse(false);
        if (!member || town.departures().containsKey(id)) return new Outcome(id, Status.SKIPPED, "No longer a settlement resident");
        if (session.level.getEntity(id) instanceof Villager villager && (!villager.isAlive() || villager.isBaby()
                || SettlementWorkforceService.hasPersonalCommitment(villager))) {
            return new Outcome(id, Status.SKIPPED, "Unavailable, young, or personally employed");
        }
        var workplace = town.workplace(town.duties().get(id));
        if (request.action().guard() && (workplace == null || workplace.type() != SettlementAreaType.GUARD_POST)) {
            return new Outcome(id, Status.SKIPPED, "Not assigned to a guard post");
        }
        if ((request.action() == SettlementViewAction.ENABLE || request.action() == SettlementViewAction.PAUSE)
                && (workplace == null || workplace.type().role() == null)) {
            return new Outcome(id, Status.SKIPPED, "No workplace assignment");
        }
        if (request.order() != null && (!request.order().within(town)
                || request.order().points().stream().anyMatch(session.level::isOutsideBuildHeight))) {
            return new Outcome(id, Status.FAILED, "Route leaves settlement or world bounds");
        }
        if (request.action() == SettlementViewAction.TEMPORARY_TASK || request.action() == SettlementViewAction.CANCEL_TASKS) {
            if (!java.util.Objects.equals(target.expectedArea(), town.duties().get(id))) {
                return new Outcome(id, Status.FAILED, "Assignment changed since review");
            }
            if (workplace == null || request.task() != null && request.task().kind().role() != workplace.type().role()) {
                return new Outcome(id, Status.SKIPPED, "Incompatible workplace role");
            }
            if (!(session.level.getEntity(id) instanceof Villager villager)) return new Outcome(id, Status.FAILED, "Worker is not loaded");
            String reason = com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(player, villager, request.task(), request.append());
            return new Outcome(id, reason.isEmpty() ? Status.APPLIED : Status.FAILED, reason.isEmpty() ? "Applied" : reason);
        }
        if (request.action() == SettlementViewAction.ASSIGN) {
            if (!java.util.Objects.equals(target.expectedArea(), town.duties().get(id))) {
                return new Outcome(id, Status.FAILED, "Assignment changed since review");
            }
            var destination = town.workplace(request.area());
            if (destination == null || destination.type().role() == null) return new Outcome(id, Status.FAILED, "Workplace no longer exists");
            var care = town.residents().get(id);
            var roles = session.level.getEntity(id) instanceof Villager villager
                    ? com.jvn.villagerretaliation.interaction.HiredVillagerRoles.availableRoles(session.level, villager)
                    : care == null ? List.of() : care.availableRoles(session.level);
            if (destination.type() != SettlementAreaType.CONSTRUCTION && !roles.contains(destination.type().role()))
                return new Outcome(id, Status.SKIPPED, "Incompatible workplace role");
        }
        boolean applied = request.action().execute(player, town, id, request.area(), request.order());
        return new Outcome(id, applied ? Status.APPLIED : Status.FAILED,
                applied ? "Applied" : session.level.getEntity(id) == null
                        ? "Resident unavailable or saved work state cannot accept this action"
                        : "Resident or workplace requirements changed");
    }
}
