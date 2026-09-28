package com.jvn.villagerretaliation.settlement;

import net.minecraft.nbt.CompoundTag;

public final class SettlementSupportState {
    private long checkedAt = -1L;
    private long sustainedTicks;
    private long shortageTicks;

    public long checkedAt() {
        return this.checkedAt;
    }

    public long sustainedTicks() {
        return this.sustainedTicks;
    }

    public long shortageTicks() {
        return this.shortageTicks;
    }

    void observe(long gameTime, boolean supported, boolean shortage) {
        long elapsed = this.checkedAt < 0L ? 0L : SettlementSimulationClock.elapsed(this.checkedAt, gameTime);
        this.checkedAt = gameTime;
        this.sustainedTicks = supported ? Math.min(24_000L * 30L, this.sustainedTicks + elapsed) : 0L;
        this.shortageTicks = shortage ? Math.min(24_000L * 30L, this.shortageTicks + elapsed) : 0L;
    }

    void resetAdvancement() {
        this.sustainedTicks = 0L;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("SustainedTicks", this.sustainedTicks);
        tag.putLong("ShortageTicks", this.shortageTicks);
        return tag;
    }

    static SettlementSupportState load(CompoundTag tag) {
        var state = new SettlementSupportState();
        state.checkedAt = tag.contains("CheckedAt") ? tag.getLong("CheckedAt") : -1L;
        state.sustainedTicks = Math.clamp(tag.getLong("SustainedTicks"), 0L, 24_000L * 30L);
        state.shortageTicks = Math.clamp(tag.getLong("ShortageTicks"), 0L, 24_000L * 30L);
        return state;
    }
}
