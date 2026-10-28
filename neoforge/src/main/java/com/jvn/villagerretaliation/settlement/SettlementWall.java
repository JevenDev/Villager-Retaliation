package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;

public record SettlementWall(Type type, List<BlockPos> nodes, boolean closed, int towerSpacing) {
    public static final int MAX_NODES = 32;
    public static final int MAX_BLOCKS = 16_384;
    public static final int MAX_SPAN = 128;

    public enum Type {
        LOW_STONE("Low stone", "stone_tower_low", "stone_span"),
        TALL_STONE("Tall stone", "stone_tower_tall", "stone_span"),
        TIMBER("Timber reinforced", "stone_tower_tall", "timber_span");

        private final String label;
        private final String tower;
        private final String span;
        Type(String label, String tower, String span) { this.label = label; this.tower = tower; this.span = span; }
        public String label() { return this.label; }
        public String tower() { return this.tower; }
        public String span() { return this.span; }
    }

    public SettlementWall {
        if (type == null || nodes == null || nodes.size() > MAX_NODES || closed && nodes.size() < 3
                || towerSpacing != 0 && (towerSpacing < 6 || towerSpacing > 64))
            throw new IllegalArgumentException("Invalid wall draft");
        nodes = nodes.stream().map(BlockPos::immutable).toList();
        for (var node : nodes) if (Math.abs((long) node.getX()) > 30_000_000 || Math.abs((long) node.getZ()) > 30_000_000
                || node.getY() < -2048 || node.getY() > 2048) throw new IllegalArgumentException("Invalid wall position");
    }
}
