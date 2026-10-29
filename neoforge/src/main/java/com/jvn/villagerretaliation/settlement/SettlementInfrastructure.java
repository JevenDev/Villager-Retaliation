package com.jvn.villagerretaliation.settlement;

import net.minecraft.core.BlockPos;

public record SettlementInfrastructure(Type type, BlockPos first, BlockPos second) {
    public static final int MAX_CELLS = 4096;
    public enum Type {
        BRIDGE("Bridge"), STAIRS("Stairs"), PEN("Animal pen"), CLEAR("Clear land"), LEVEL("Level land"), FILL("Fill land");
        private final String label;
        Type(String label) { this.label = label; }
        public String label() { return this.label; }
    }

    public SettlementInfrastructure {
        if (type == null || first == null || second == null
                || Math.abs((long) first.getX() - second.getX()) > 63 || Math.abs((long) first.getZ() - second.getZ()) > 63
                || Math.abs((long) first.getY() - second.getY()) > 32)
            throw new IllegalArgumentException("Keep the selection within 64 blocks and 32 blocks of height");
        first = first.immutable();
        second = second.immutable();
    }
}
