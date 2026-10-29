package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;

public record SettlementWall(Type type, List<BlockPos> nodes, boolean closed, int towerSpacing, List<Tower> nodeTowers, Part part) {
    public static final int MAX_NODES = 32;
    public static final int MAX_BLOCKS = 16_384;
    public static final int MAX_SPAN = 128;

    public enum Type {
        LOW_STONE("Low stone", "stone_tower_low", "stone_span"),
        TALL_STONE("Tall stone", "stone_tower_tall", "stone_span"),
        TIMBER("Timber reinforced", "stone_tower_tall", "timber_span"),
        PALISADE("Oak palisade", "palisade_post", "palisade_span"),
        PLANK("Plank fence", "plank_post", "plank_span");

        private final String label;
        private final String tower;
        private final String span;
        Type(String label, String tower, String span) { this.label = label; this.tower = tower; this.span = span; }
        public String label() { return this.label; }
        public String tower() { return this.tower; }
        public String span() { return this.span; }
    }

    public enum Part { COMPLETE, SPAN, TOWER }

    public SettlementWall(Type type, List<BlockPos> nodes, boolean closed, int towerSpacing, List<Tower> nodeTowers) {
        this(type, nodes, closed, towerSpacing, nodeTowers, Part.COMPLETE);
    }

    public enum Tower {
        DEFAULT("Default"), LOW_STONE("Low stone"), TALL_STONE("Tall stone"), NONE("None"),
        PALISADE("Palisade post"), PLANK("Plank post");
        private final String label;
        Tower(String label) { this.label = label; }
        public String label() { return this.label; }
        public Tower resolved(Type type) {
            return this != DEFAULT ? this : switch (type) {
                case LOW_STONE -> LOW_STONE;
                case TALL_STONE, TIMBER -> TALL_STONE;
                case PALISADE -> PALISADE;
                case PLANK -> PLANK;
            };
        }
    }

    public SettlementWall(Type type, List<BlockPos> nodes, boolean closed, int towerSpacing) {
        this(type, nodes, closed, towerSpacing, java.util.Collections.nCopies(nodes == null ? 0 : nodes.size(), Tower.DEFAULT));
    }

    public String towerAt(BlockPos position) {
        int index = this.nodes.indexOf(position);
        var choice = index < 0 ? Tower.DEFAULT : this.nodeTowers.get(index);
        return switch (choice) {
            case DEFAULT -> this.type.tower();
            case LOW_STONE -> "stone_tower_low";
            case TALL_STONE -> "stone_tower_tall";
            case NONE -> "";
            case PALISADE -> "palisade_post";
            case PLANK -> "plank_post";
        };
    }

    public int towerRadius(BlockPos position) {
        var name = towerAt(position);
        return name.isEmpty() ? -1 : SettlementWallTemplates.get(name).size().getX() / 2;
    }

    public SettlementWall {
        if (type == null || nodes == null || nodes.size() > MAX_NODES || closed && nodes.size() < 3
                || towerSpacing != 0 && (towerSpacing < 6 || towerSpacing > 64))
            throw new IllegalArgumentException("Invalid wall draft");
        if (part == null || part != Part.COMPLETE && (nodes.size() != 2 || closed || towerSpacing != 0))
            throw new IllegalArgumentException("Invalid wall piece");
        if (nodeTowers == null || nodeTowers.size() != nodes.size()) throw new IllegalArgumentException("Invalid tower choices");
        nodeTowers = List.copyOf(nodeTowers);
        nodes = nodes.stream().map(BlockPos::immutable).toList();
        for (int i = 0; i < nodes.size(); i++) {
            int first = nodes.indexOf(nodes.get(i));
            if (nodeTowers.get(first) != nodeTowers.get(i)) throw new IllegalArgumentException("Conflicting junction towers");
        }
        for (var node : nodes) if (Math.abs((long) node.getX()) > 30_000_000 || Math.abs((long) node.getZ()) > 30_000_000
                || node.getY() < -2048 || node.getY() > 2048) throw new IllegalArgumentException("Invalid wall position");
    }
}
