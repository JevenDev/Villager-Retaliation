package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.SettlementViewOverviewPayload;
import com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SettlementViewSnapshots {
    private final UUID session;
    private final UUID town;
    private final Map<Page, List<SettlementManagementPayload.Entry>> incoming = new EnumMap<>(Page.class);
    private Map<Page, List<SettlementManagementPayload.Entry>> complete = Map.of();
    private SettlementManagementPayload.Header header;
    private SettlementManagementPayload.Header incomingHeader;
    private long generation;
    private int category;
    private int index;
    private int requestId;
    private int waiting = -1;
    private int total = -1;
    private long sentAt;
    private long refreshAt;
    private long completedAt;

    public SettlementViewSnapshots(UUID session, UUID town) {
        this.session = session;
        this.town = town;
    }

    public SettlementViewOverviewRequestPayload request(long now) {
        if (this.waiting >= 0 && now - this.sentAt < 5_000) return null;
        if (this.waiting >= 0) refresh();
        if (now < this.refreshAt) return null;
        this.waiting = ++this.requestId;
        this.sentAt = now;
        return new SettlementViewOverviewRequestPayload(this.session, this.waiting, this.generation,
                SettlementViewOverviewPayload.CATEGORIES.get(this.category), this.index);
    }

    public boolean accept(SettlementViewOverviewPayload payload, long now) {
        if (!this.session.equals(payload.session()) || !this.town.equals(payload.header().id())
                || this.waiting != payload.requestId() || payload.index() != this.index
                || payload.category() != SettlementViewOverviewPayload.CATEGORIES.get(this.category)
                || this.generation != 0 && this.generation != payload.generation()
                || this.total >= 0 && this.total != payload.total()) return false;
        this.waiting = -1;
        this.generation = payload.generation();
        this.incomingHeader = payload.header();
        this.total = payload.total();
        this.incoming.computeIfAbsent(payload.category(), ignored -> new ArrayList<>()).addAll(payload.entries());
        if ((long) (this.index + 1) * SettlementViewOverviewPayload.PAGE_SIZE < payload.total()) {
            this.index++;
        } else {
            this.index = 0;
            this.total = -1;
            this.category++;
            if (this.category == SettlementViewOverviewPayload.CATEGORIES.size()) {
                var snapshot = new EnumMap<Page, List<SettlementManagementPayload.Entry>>(Page.class);
                this.incoming.forEach((page, rows) -> snapshot.put(page, List.copyOf(rows)));
                this.complete = Map.copyOf(snapshot);
                this.header = this.incomingHeader;
                refresh();
                this.completedAt = now;
                this.refreshAt = now + 5_000;
                return true;
            }
        }
        return false;
    }

    public void refresh() {
        this.waiting = -1;
        this.incoming.clear();
        this.generation = 0;
        this.category = 0;
        this.index = 0;
        this.total = -1;
        this.refreshAt = 0;
    }

    public List<SettlementManagementPayload.Entry> entries(Page page) { return this.complete.getOrDefault(page, List.of()); }
    public SettlementManagementPayload.Header header() { return this.header; }
    public boolean loading() { return this.waiting >= 0 || this.generation != 0 || this.complete.isEmpty(); }
    public boolean stale(long now) { return this.completedAt == 0 || now - this.completedAt > 10_000; }
}
