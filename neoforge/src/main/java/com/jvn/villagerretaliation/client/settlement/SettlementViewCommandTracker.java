package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementViewBatchPayload;
import com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload;
import java.util.List;
import java.util.UUID;

final class SettlementViewCommandTracker {
    private SettlementViewBatchPayload request;
    private long sentAt;
    private boolean unconfirmed;
    private List<SettlementViewBatchResultPayload.Outcome> outcomes = List.of();

    void clear() {
        this.request = null;
        this.outcomes = List.of();
        this.unconfirmed = false;
    }

    void sent(SettlementViewBatchPayload request, long now) {
        this.request = request;
        this.sentAt = now;
        this.unconfirmed = false;
        this.outcomes = List.of();
    }

    boolean accept(SettlementViewBatchResultPayload payload) {
        if (this.request == null || !this.request.session().equals(payload.session())
                || !this.request.operation().equals(payload.operation())
                || !this.request.targets().stream().map(SettlementViewBatchPayload.Target::resident).toList()
                    .equals(payload.outcomes().stream().map(SettlementViewBatchResultPayload.Outcome::resident).toList())) return false;
        this.outcomes = payload.outcomes();
        this.request = null;
        this.unconfirmed = false;
        return true;
    }

    boolean timeout(long now) {
        if (this.request == null || this.unconfirmed || now - this.sentAt < 10_000) return false;
        this.unconfirmed = true;
        return true;
    }

    boolean busy() { return this.request != null && !this.unconfirmed; }
    List<SettlementViewBatchResultPayload.Outcome> outcomes() { return this.outcomes; }

    String summary() {
        if (this.unconfirmed) return "Outcome unconfirmed. State refreshed. Command was not resent";
        if (busy()) return "Command pending";
        if (this.outcomes.isEmpty()) return "";
        long applied = this.outcomes.stream().filter(r -> r.status() == SettlementViewBatchResultPayload.Status.APPLIED).count();
        long skipped = this.outcomes.stream().filter(r -> r.status() == SettlementViewBatchResultPayload.Status.SKIPPED).count();
        return applied + " applied, " + skipped + " skipped, " + (this.outcomes.size() - applied - skipped) + " failed";
    }
}
