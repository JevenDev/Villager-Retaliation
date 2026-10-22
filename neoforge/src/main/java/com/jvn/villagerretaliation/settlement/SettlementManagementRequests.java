package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

final class SettlementManagementRequests {
    private final SettlementSimulationService simulation;
    private final Map<UUID, Pending> pending = new HashMap<>();

    SettlementManagementRequests(SettlementSimulationService simulation) {
        this.simulation = simulation;
    }

    void clear() {
        this.pending.clear();
    }

    void submit(ServerPlayer player, SettlementManagementRequestPayload request, Consumer<SettlementManagementPayload> ready) {
        var level = player.serverLevel();
        var town = request.town() == null || request.page() == Page.TOWNS ? null
                : SettlementSavedData.get(level).get(new VillageAllegianceId(request.town())).orElse(null);
        if (SettlementSavedData.get(level).readOnly()) {
            this.pending.remove(player.getUUID());
            var snapshot = SettlementManagementService.snapshot(player, request);
            if (snapshot != null) ready.accept(snapshot);
            else deliver(new Pending(player, town, request, ready), null);
            return;
        }
        var previous = this.pending.get(player.getUUID());
        if (previous != null && previous.player == player && previous.town == town && town != null) {
            previous.request = request;
            previous.ready = ready;
            return;
        }
        var next = new Pending(player, town, request, ready);
        this.pending.put(player.getUUID(), next);
        this.simulation.enqueue(next, SettlementWork.repeat(() -> {
            if (this.pending.get(player.getUUID()) != next) return true;
            if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.serverLevel() != level) {
                this.pending.remove(player.getUUID(), next);
                return true;
            }
            boolean valid = town != null && town.dimension().equals(level.dimension().location())
                    && SettlementSavedData.get(level).get(town.id()).orElse(null) == town
                    && town.permits(player.getUUID(), SettlementPermission.VIEW);
            if (!valid) {
                deliver(next, null);
                return true;
            }
            var permissions = EnumSet.noneOf(SettlementPermission.class);
            for (var permission : SettlementPermission.values()) {
                if (town.permits(player.getUUID(), permission)) permissions.add(permission);
            }
            if (!permissions.equals(next.permissions)) {
                next.permissions = permissions;
                next.work = null;
            }
            SettlementSimulationService.managed(player, town);
            if (next.work == null) {
                next.work = SettlementDemandService.plan(level, town, player, supply -> deliver(next, supply));
            } else next.work.run();
            return next.work != null && SettlementWork.complete(next.work);
        }));
    }

    private void deliver(Pending next, SettlementDemandService.Snapshot supply) {
        var request = next.request;
        var snapshot = request.page() == Page.TOWNS ? SettlementManagementService.snapshot(next.player, request)
                : supply == null ? null : SettlementManagementService.snapshot(next.player, request, supply);
        if (snapshot == null) snapshot = SettlementManagementService.snapshot(next.player,
                new SettlementManagementRequestPayload(null, Page.TOWNS, 0, "", request.requestId(), request.openScreen()));
        this.pending.remove(next.player.getUUID(), next);
        if (snapshot != null) next.ready.accept(snapshot);
    }

    private static final class Pending {
        private final ServerPlayer player;
        private final SettlementRecord town;
        private SettlementManagementRequestPayload request;
        private Consumer<SettlementManagementPayload> ready;
        private EnumSet<SettlementPermission> permissions;
        private Runnable work;

        private Pending(ServerPlayer player, SettlementRecord town, SettlementManagementRequestPayload request,
                        Consumer<SettlementManagementPayload> ready) {
            this.player = player;
            this.town = town;
            this.request = request;
            this.ready = ready;
        }
    }
}
