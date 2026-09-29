package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;

final class SettlementManagementTracker {
    private SettlementManagementActionPayload pending;
    private long sentAt;
    private boolean unconfirmed;

    void sent(SettlementManagementActionPayload payload, long now) {
        this.pending = payload;
        this.sentAt = now;
        this.unconfirmed = false;
    }

    boolean accept(SettlementManagementActionResultPayload result) {
        if (this.pending == null || !this.pending.town().equals(result.town())
                || !this.pending.request().equals(result.request())) return false;
        clear();
        return true;
    }

    boolean timeout(long now) {
        if (this.pending == null || this.unconfirmed || now - this.sentAt < 10_000L) return false;
        this.unconfirmed = true;
        return true;
    }

    boolean busy() { return this.pending != null && !this.unconfirmed; }
    boolean unconfirmed() { return this.unconfirmed; }

    void clear() {
        this.pending = null;
        this.unconfirmed = false;
    }
}
