package com.jvn.villagerretaliation.reputation.runtime;

import net.minecraft.core.BlockPos;

public class ReputationState {
    private int reputation;
    private long lastInteractionGameTime;
    private BlockPos position;

    public int reputation() { return this.reputation; }
    public long lastInteractionGameTime() { return this.lastInteractionGameTime; }
    public BlockPos position() { return this.position; }
    public void setReputation(int value) { this.reputation = value; }
    public void setLastInteractionGameTime(long value) { this.lastInteractionGameTime = value; }
    public void setPosition(BlockPos value) { this.position = value == null ? null : value.immutable(); }
}
