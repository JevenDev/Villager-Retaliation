package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload;
import java.util.UUID;

public final class SettlementPolicyClientState {
    private final UUID town;
    private SettlementPolicySnapshotPayload snapshot;
    private UUID pending;
    private boolean action;
    private long sentAt;
    private String feedback = "";

    public SettlementPolicyClientState(UUID town) { this.town = town; }
    public UUID town() { return this.town; }
    public SettlementPolicySnapshotPayload snapshot() { return this.snapshot; }
    public boolean busy() { return this.pending != null && this.action; }
    public boolean waiting() { return this.pending != null; }
    public boolean editable() { return !busy() && this.snapshot != null && this.snapshot.editable(); }
    public String feedback() { return this.feedback; }

    public SettlementPolicyRequestPayload request(UUID policy, long revision, UUID area, SettlementPolicyRequestPayload.Command command, long now) {
        boolean mutation = command instanceof SettlementPolicyRequestPayload.Save || command instanceof SettlementPolicyRequestPayload.Change;
        if (busy() || mutation && !editable()) return null;
        this.pending = UUID.randomUUID();
        this.action = mutation;
        this.sentAt = now;
        this.feedback = mutation ? "saving" : "checking";
        return new SettlementPolicyRequestPayload(this.town, this.pending, policy, revision, area, command);
    }

    public boolean accept(SettlementPolicySnapshotPayload payload) {
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
