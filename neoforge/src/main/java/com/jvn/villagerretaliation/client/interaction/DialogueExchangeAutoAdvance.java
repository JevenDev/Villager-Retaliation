package com.jvn.villagerretaliation.client.interaction;

/** Pure progression clock for an explicitly automatic, two-line greeting. */
public final class DialogueExchangeAutoAdvance {
    private boolean active;
    private long readyAt = -1L;
    private long holdMillis;

    public enum Step { NONE, ADVANCE, FINISH }

    public boolean active() { return this.active; }

    public void start() {
        this.active = true;
        this.readyAt = -1L;
    }

    public void lineStarted(int codePoints) {
        this.readyAt = -1L;
        this.holdMillis = holdMillis(codePoints);
    }

    public static long holdMillis(int codePoints) {
        return Math.clamp(250L + Math.max(0L, codePoints) * 40L, 1000L, 4000L);
    }

    public Step poll(long nowMillis, boolean textFullyRevealed, boolean hasNext) {
        if (!this.active) return Step.NONE;
        if (!textFullyRevealed) {
            this.readyAt = -1L;
            return Step.NONE;
        }
        if (this.readyAt < 0L) {
            this.readyAt = nowMillis + this.holdMillis;
            return Step.NONE;
        }
        if (nowMillis < this.readyAt) return Step.NONE;
        this.readyAt = -1L;
        if (hasNext) return Step.ADVANCE;
        cancel();
        return Step.FINISH;
    }

    public void cancel() {
        this.active = false;
        this.readyAt = -1L;
    }

    public void copyFrom(DialogueExchangeAutoAdvance other) {
        this.active = other.active;
        this.readyAt = other.readyAt;
        this.holdMillis = other.holdMillis;
    }
}
