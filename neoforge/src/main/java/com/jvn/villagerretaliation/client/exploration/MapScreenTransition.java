package com.jvn.villagerretaliation.client.exploration;

import java.util.function.LongSupplier;

final class MapScreenTransition {
    private enum State { NEW, OPENING, OPEN, CLOSING }
    private final LongSupplier clock;
    private State state = State.NEW;
    private long startedAt;
    private float closingFrom;

    MapScreenTransition() { this(System::nanoTime); }
    MapScreenTransition(LongSupplier clock) { this.clock = clock; }

    void open() {
        if (this.state != State.NEW) return;
        this.startedAt = this.clock.getAsLong();
        this.state = State.OPENING;
    }

    boolean closing() { return this.state == State.CLOSING; }

    boolean close(boolean animate) {
        if (closing() || !animate) return true;
        this.closingFrom = progress(animate);
        this.startedAt = this.clock.getAsLong();
        this.state = State.CLOSING;
        return false;
    }

    void finishOpening() {
        if (!closing()) this.state = State.OPEN;
    }

    void suspend() {
        this.state = State.OPEN;
    }

    float progress(boolean animate) {
        if (closing()) return (float) Math.max(0, this.closingFrom - (this.clock.getAsLong() - this.startedAt) / 600_000_000.0);
        if (!animate || this.state == State.OPEN) return 1;
        return this.state == State.NEW ? 0 : (float) Math.clamp((this.clock.getAsLong() - this.startedAt) / 1_250_000_000.0, 0, 1);
    }

    float pitch(float pitch, boolean animate) {
        float progress = progress(animate);
        if (progress >= .4f) return pitch;
        float turn = ease(progress / .4f);
        return pitch + (Math.max(-90, pitch - 6) - pitch) * turn;
    }

    static float ease(float progress) {
        progress = Math.clamp(progress, 0, 1);
        return Math.clamp(progress * progress * progress * (progress * (progress * 6 - 15) + 10), 0, 1);
    }
}
