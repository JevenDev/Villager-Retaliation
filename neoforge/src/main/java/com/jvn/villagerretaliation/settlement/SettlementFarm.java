package com.jvn.villagerretaliation.settlement;

import net.minecraft.core.BlockPos;

public record SettlementFarm(BlockPos first, BlockPos second) {
    public static final int MAX_SPAN = 64;
    public static final int MAX_CELLS = MAX_SPAN * MAX_SPAN;

    public SettlementFarm {
        if (first == null || second == null
                || Math.abs((long) first.getX() - second.getX()) >= MAX_SPAN
                || Math.abs((long) first.getZ() - second.getZ()) >= MAX_SPAN)
            throw new IllegalArgumentException("Farmland must fit within 64 x 64 blocks");
        first = first.immutable();
        second = second.immutable();
    }

    public int minX() { return Math.min(first.getX(), second.getX()); }
    public int maxX() { return Math.max(first.getX(), second.getX()); }
    public int minZ() { return Math.min(first.getZ(), second.getZ()); }
    public int maxZ() { return Math.max(first.getZ(), second.getZ()); }
}
