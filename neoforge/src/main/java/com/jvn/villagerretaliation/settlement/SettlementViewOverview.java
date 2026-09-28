package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.SettlementViewOverviewPayload;
import com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class SettlementViewOverview {
    private long generation;
    private long refreshed = Long.MIN_VALUE;
    private int requestId = -1;
    private SettlementManagementPayload.Header header;
    private Map<Page, List<SettlementManagementPayload.Entry>> entries = Map.of();

    SettlementViewOverviewPayload page(SettlementViewService.Session session, SettlementViewOverviewRequestPayload request) {
        if (request.requestId() <= this.requestId) return null;
        if (request.generation() == 0) {
            if (this.generation > 0 && session.level.getGameTime() - this.refreshed < 100) return null;
            var assessment = SettlementManagementService.overviewHeader(session.player, session.town);
            this.header = assessment.header();
            var snapshot = SettlementManagementService.overview(session.player, session.town, this.header);
            snapshot.put(Page.ISSUES, List.copyOf(assessment.issues()));
            snapshot.replaceAll((category, rows) -> rows.stream().sorted(Comparator
                    .comparing(SettlementManagementPayload.Entry::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(SettlementManagementPayload.Entry::id)).toList());
            this.entries = Map.copyOf(snapshot);
            this.generation++;
            this.refreshed = session.level.getGameTime();
        } else if (request.generation() != this.generation) return null;
        var rows = this.entries.get(request.category());
        long start = (long) request.index() * SettlementViewOverviewPayload.PAGE_SIZE;
        if (rows == null || start > rows.size()) return null;
        this.requestId = request.requestId();
        return new SettlementViewOverviewPayload(session.id, request.requestId(), this.generation, this.header,
                request.category(), request.index(), rows.size(),
                rows.subList((int) start, (int) Math.min(rows.size(), start + SettlementViewOverviewPayload.PAGE_SIZE)));
    }
}
