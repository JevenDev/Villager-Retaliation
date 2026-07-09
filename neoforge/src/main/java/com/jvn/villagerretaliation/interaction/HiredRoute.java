package com.jvn.villagerretaliation.interaction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record HiredRoute(List<BlockPos> nodes, boolean loop) {
    public static final int MAX_NODES = 16;
    public static final int MAX_NODE_DISTANCE = 16;
    public static final int MAX_NODE_DISTANCE_SQR = MAX_NODE_DISTANCE * MAX_NODE_DISTANCE;
    private static final String ROUTE_TAG = "Route";
    private static final String NODES_TAG = "Nodes";
    private static final String LOOP_TAG = "Loop";

    public HiredRoute {
        List<BlockPos> safeNodes = new ArrayList<>();
        if (nodes != null) {
            for (BlockPos node : nodes) {
                if (node == null || safeNodes.size() >= MAX_NODES) {
                    continue;
                }
                safeNodes.add(node.immutable());
            }
        }
        nodes = List.copyOf(safeNodes);
        loop = loop && nodes.size() > 1 && canConnect(nodes.getLast(), nodes.getFirst());
    }

    public static HiredRoute empty() {
        return new HiredRoute(List.of(), false);
    }

    public static HiredRoute fromState(CompoundTag state) {
        if (state == null || !state.contains(ROUTE_TAG, Tag.TAG_COMPOUND)) {
            return empty();
        }
        CompoundTag routeTag = state.getCompound(ROUTE_TAG);
        List<BlockPos> nodes = new ArrayList<>();
        ListTag nodeTags = routeTag.getList(NODES_TAG, Tag.TAG_LONG);
        for (Tag rawNode : nodeTags) {
            if (rawNode instanceof net.minecraft.nbt.LongTag nodeTag && nodes.size() < MAX_NODES) {
                nodes.add(BlockPos.of(nodeTag.getAsLong()));
            }
        }
        return new HiredRoute(nodes, routeTag.getBoolean(LOOP_TAG)).validatedChain();
    }

    public void save(CompoundTag state) {
        if (state == null) {
            return;
        }
        if (this.nodes.isEmpty()) {
            clear(state);
            return;
        }
        CompoundTag routeTag = new CompoundTag();
        ListTag nodeTags = new ListTag();
        for (BlockPos node : this.nodes) {
            nodeTags.add(net.minecraft.nbt.LongTag.valueOf(node.asLong()));
        }
        routeTag.put(NODES_TAG, nodeTags);
        routeTag.putBoolean(LOOP_TAG, this.loop);
        state.put(ROUTE_TAG, routeTag);
    }

    public static void clear(CompoundTag state) {
        if (state != null) {
            state.remove(ROUTE_TAG);
        }
    }

    public HiredRoute validatedChain() {
        if (this.nodes.size() < 2) {
            return new HiredRoute(this.nodes, false);
        }
        int validSize = this.nodes.size();
        for (int index = 1; index < this.nodes.size(); index++) {
            if (!canConnect(this.nodes.get(index - 1), this.nodes.get(index))) {
                validSize = index;
                break;
            }
        }
        List<BlockPos> validNodes = validSize == this.nodes.size()
                ? this.nodes
                : this.nodes.subList(0, validSize);
        boolean validLoop = this.loop && validNodes.size() > 1 && canConnect(validNodes.getLast(), validNodes.getFirst());
        return new HiredRoute(validNodes, validLoop);
    }

    public boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    public boolean usableForNavigation() {
        return !this.nodes.isEmpty();
    }

    public BlockPos first() {
        return this.nodes.isEmpty() ? null : this.nodes.getFirst();
    }

    public BlockPos last() {
        return this.nodes.isEmpty() ? null : this.nodes.getLast();
    }

    public boolean contains(BlockPos pos) {
        return pos != null && this.nodes.contains(pos);
    }

    public int indexOf(BlockPos pos) {
        return pos == null ? -1 : this.nodes.indexOf(pos);
    }

    public boolean isNearRoute(BlockPos pos, int horizontalRadius, int verticalRadius) {
        if (pos == null || this.nodes.isEmpty()) {
            return false;
        }
        int safeHorizontal = Math.max(1, horizontalRadius);
        int safeVertical = Math.max(1, verticalRadius);
        for (BlockPos node : this.nodes) {
            if (isNearNode(pos, node, safeHorizontal, safeVertical)) {
                return true;
            }
        }
        for (int index = 1; index < this.nodes.size(); index++) {
            if (isNearSegment(pos, this.nodes.get(index - 1), this.nodes.get(index), safeHorizontal, safeVertical)) {
                return true;
            }
        }
        return this.loop && this.nodes.size() > 1
                && isNearSegment(pos, this.nodes.getLast(), this.nodes.getFirst(), safeHorizontal, safeVertical);
    }

    private static boolean isNearNode(BlockPos pos, BlockPos node, int horizontalRadius, int verticalRadius) {
        int dx = pos.getX() - node.getX();
        int dz = pos.getZ() - node.getZ();
        return Math.abs(pos.getY() - node.getY()) <= verticalRadius
                && dx * dx + dz * dz <= horizontalRadius * horizontalRadius;
    }

    private static boolean isNearSegment(BlockPos pos, BlockPos first, BlockPos second, int horizontalRadius, int verticalRadius) {
        int minY = Math.min(first.getY(), second.getY()) - verticalRadius;
        int maxY = Math.max(first.getY(), second.getY()) + verticalRadius;
        if (pos.getY() < minY || pos.getY() > maxY) {
            return false;
        }
        double ax = first.getX() + 0.5D;
        double az = first.getZ() + 0.5D;
        double bx = second.getX() + 0.5D;
        double bz = second.getZ() + 0.5D;
        double px = pos.getX() + 0.5D;
        double pz = pos.getZ() + 0.5D;
        double vx = bx - ax;
        double vz = bz - az;
        double lengthSqr = vx * vx + vz * vz;
        if (lengthSqr <= 0.0001D) {
            return isNearNode(pos, first, horizontalRadius, verticalRadius);
        }
        double progress = Math.max(0.0D, Math.min(1.0D, ((px - ax) * vx + (pz - az) * vz) / lengthSqr));
        double nearestX = ax + vx * progress;
        double nearestZ = az + vz * progress;
        double dx = px - nearestX;
        double dz = pz - nearestZ;
        return dx * dx + dz * dz <= horizontalRadius * horizontalRadius;
    }

    public static boolean canConnect(BlockPos first, BlockPos second) {
        return first != null && second != null && first.distSqr(second) <= MAX_NODE_DISTANCE_SQR;
    }
}
