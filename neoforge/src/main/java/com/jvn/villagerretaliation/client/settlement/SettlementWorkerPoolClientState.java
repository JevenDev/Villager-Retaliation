package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementWorkerPoolRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWorkerPoolSnapshotPayload;
import java.util.UUID;

public final class SettlementWorkerPoolClientState {
    private final UUID town;
    private SettlementWorkerPoolSnapshotPayload snapshot;
    private UUID pending;
    private boolean action;
    private long sentAt;
    private String feedback = "";

    public SettlementWorkerPoolClientState(UUID town) { this.town = town; }
    public UUID town() { return this.town; }
    public SettlementWorkerPoolSnapshotPayload snapshot() { return this.snapshot; }
    public boolean busy() { return this.pending != null && this.action; }
    public boolean waiting() { return this.pending != null; }
    public boolean editable() { return !busy() && this.snapshot != null && this.snapshot.editable(); }
    public String feedback() { return this.feedback; }

    public SettlementWorkerPoolRequestPayload request(UUID pool, long revision, String query, int page, SettlementWorkerPoolRequestPayload.Command command, long now) {
        boolean mutation = command instanceof SettlementWorkerPoolRequestPayload.Save || command instanceof SettlementWorkerPoolRequestPayload.Change
                || command instanceof SettlementWorkerPoolRequestPayload.ReturnControl;
        if (busy() || mutation && !editable()) return null;
        this.pending = UUID.randomUUID();
        this.action = mutation;
        this.sentAt = now;
        this.feedback = mutation ? "saving" : "checking";
        return new SettlementWorkerPoolRequestPayload(this.town, this.pending, pool, revision, query, page, command);
    }

    public boolean accept(SettlementWorkerPoolSnapshotPayload payload) {
        if (!this.town.equals(payload.town()) || !payload.request().equals(this.pending)) return false;
        this.pending = null;
        if (!payload.feedback().equals("busy")) this.snapshot = payload;
        this.feedback = payload.feedback().isEmpty() ? payload.blocker() : payload.feedback();
        return true;
    }

    public boolean expire(long now) {
        if (this.pending == null || now - this.sentAt < 10_000L) return false;
        this.pending = null;
        this.feedback = "request_timeout";
        return true;
    }
}
