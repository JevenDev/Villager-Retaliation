package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.SettlementViewOverviewPayload;
import com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class SettlementViewOverview {
    private long generation;
    private long refreshed = Long.MIN_VALUE;
    private int requestId = -1;
    private SettlementManagementPayload.Header header;
    private Map<Page, List<SettlementManagementPayload.Entry>> entries = Map.of();
    private Pending pending;

    boolean planning() {
        return this.pending != null;
    }

    void clear() {
        this.pending = null;
        this.entries = Map.of();
        this.header = null;
    }

    void submit(SettlementViewService.Session session, SettlementViewOverviewRequestPayload request,
                Consumer<SettlementViewOverviewPayload> ready) {
        if (!session.id.equals(request.session()) || request.requestId() <= this.requestId) return;
        if (request.generation() != 0) {
            if (this.pending != null || request.generation() != this.generation) return;
            var page = page(session, request);
            if (page != null) {
                this.requestId = request.requestId();
                ready.accept(page);
            }
            return;
        }
        if (this.pending == null && this.generation > 0 && session.level.getGameTime() - this.refreshed < 100) return;
        this.requestId = request.requestId();
        if (this.pending != null) {
            this.pending.request = request;
            this.pending.ready = ready;
            schedule(session, this.pending);
            return;
        }
        var next = new Pending(request, ready);
        this.pending = next;
        schedule(session, next);
    }

    private void schedule(SettlementViewService.Session session, Pending next) {
        SettlementSavedData.get(session.level).simulation.enqueue(next, SettlementWork.repeat(() -> {
            if (this.pending != next) return true;
            if (SettlementViewService.session(session.player.getUUID()) != session
                    || session.player.serverLevel() != session.level || !SettlementViewService.eligible(session.player, session.town)) {
                clear();
                return true;
            }
            var permissions = EnumSet.noneOf(SettlementPermission.class);
            for (var permission : SettlementPermission.values()) {
                if (session.town.permits(session.player.getUUID(), permission)) permissions.add(permission);
            }
            if (!permissions.equals(next.permissions)) {
                next.permissions = permissions;
                next.work = null;
            }
            if (next.work == null) {
                next.work = SettlementDemandService.plan(session.level, session.town, session.player,
                        supply -> complete(session, next, supply));
            } else next.work.run();
            return SettlementWork.complete(next.work);
        }));
    }

    private void complete(SettlementViewService.Session session, Pending next, SettlementDemandService.Snapshot supply) {
        var assessment = SettlementManagementService.overviewHeader(session.player, session.town, supply);
        this.header = assessment.header();
        var snapshot = SettlementManagementService.overview(session.player, session.town);
        snapshot.put(Page.ISSUES, List.copyOf(assessment.issues()));
        snapshot.replaceAll((category, rows) -> rows.stream().sorted(Comparator
                .comparing(SettlementManagementPayload.Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SettlementManagementPayload.Entry::id)).toList());
        this.entries = Map.copyOf(snapshot);
        this.generation++;
        this.refreshed = session.level.getGameTime();
        this.pending = null;
        var page = page(session, next.request);
        if (page != null) next.ready.accept(page);
    }

    private SettlementViewOverviewPayload page(SettlementViewService.Session session, SettlementViewOverviewRequestPayload request) {
        var rows = this.entries.get(request.category());
        long start = (long) request.index() * SettlementViewOverviewPayload.PAGE_SIZE;
        if (rows == null || start > rows.size()) return null;
        return new SettlementViewOverviewPayload(session.id, request.requestId(), this.generation, this.header,
                request.category(), request.index(), rows.size(),
                rows.subList((int) start, (int) Math.min(rows.size(), start + SettlementViewOverviewPayload.PAGE_SIZE)));
    }

    private static final class Pending {
        private SettlementViewOverviewRequestPayload request;
        private Consumer<SettlementViewOverviewPayload> ready;
        private EnumSet<SettlementPermission> permissions;
        private Runnable work;

        private Pending(SettlementViewOverviewRequestPayload request, Consumer<SettlementViewOverviewPayload> ready) {
            this.request = request;
            this.ready = ready;
        }
    }
}
