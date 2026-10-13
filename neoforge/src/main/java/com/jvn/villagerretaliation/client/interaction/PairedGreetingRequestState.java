package com.jvn.villagerretaliation.client.interaction;

import java.util.UUID;

/** Correlates a single pending Greet without accepting an older screen's response. */
public final class PairedGreetingRequestState {
    public static final long TIMEOUT_MILLIS = 10_000L;
    private UUID token;
    private long startedAt;

    public boolean pending() { return this.token != null; }

    public UUID begin(long nowMillis) {
        if (pending()) return null;
        this.token = UUID.randomUUID();
        this.startedAt = nowMillis;
        return this.token;
    }

    public boolean accept(UUID responseToken, long nowMillis) {
        if (responseToken == null || !responseToken.equals(this.token)
                || nowMillis - this.startedAt >= TIMEOUT_MILLIS) return false;
        cancel();
        return true;
    }

    public boolean expire(long nowMillis) {
        if (!pending() || nowMillis - this.startedAt < TIMEOUT_MILLIS) return false;
        cancel();
        return true;
    }

    public void cancel() { this.token = null; }

    public void copyFrom(PairedGreetingRequestState other) {
        this.token = other.token;
        this.startedAt = other.startedAt;
    }
}
