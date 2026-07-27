package com.jvn.villagerretaliation.interaction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record HiredRoute(List<BlockPos> nodes, boolean loop, List<Branch> branches) {
    public static final int MAX_NODES = 16;
    public static final int MAX_BRANCHES = 16;
    public static final int MAX_NODE_DISTANCE = 16;
    public static final int MAX_NODE_DISTANCE_SQR = MAX_NODE_DISTANCE * MAX_NODE_DISTANCE;
    private static final String ROUTE_TAG = "Route";
    private static final String NODES_TAG = "Nodes";
    private static final String LOOP_TAG = "Loop";
    private static final String BRANCHES_TAG = "Branches";
    private static final String ANCHOR_TAG = "Anchor";
    private static final String END_TAG = "End";

    public HiredRoute(List<BlockPos> nodes, boolean loop) {
        this(nodes, loop, List.of());
    }

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
        List<Branch> safeBranches = new ArrayList<>();
        if (branches != null) {
            for (Branch branch : branches) {
                if (branch == null || safeBranches.size() >= MAX_BRANCHES) {
                    continue;
                }
                Branch safeBranch = branch;
                if (canConnect(safeBranch.anchor(), safeBranch.end())
                        && isNearBaseRoute(nodes, loop, safeBranch.anchor(), 2, 2)
                        && safeBranches.stream().noneMatch(existing -> existing.anchor().equals(safeBranch.anchor())
                                && existing.end().equals(safeBranch.end()))) {
                    safeBranches.add(safeBranch);
                }
            }
        }
        branches = List.copyOf(safeBranches);
    }

    public static HiredRoute empty() {
        return new HiredRoute(List.of(), false, List.of());
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
        List<Branch> branches = new ArrayList<>();
        ListTag branchTags = routeTag.getList(BRANCHES_TAG, Tag.TAG_COMPOUND);
        for (Tag rawBranch : branchTags) {
            if (rawBranch instanceof CompoundTag branchTag
                    && branchTag.contains(ANCHOR_TAG, Tag.TAG_LONG)
                    && branchTag.contains(END_TAG, Tag.TAG_LONG)
                    && branches.size() < MAX_BRANCHES) {
                branches.add(new Branch(
                        BlockPos.of(branchTag.getLong(ANCHOR_TAG)),
                        BlockPos.of(branchTag.getLong(END_TAG))));
            }
        }
        return new HiredRoute(nodes, routeTag.getBoolean(LOOP_TAG), branches).validatedChain();
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
        ListTag branchTags = new ListTag();
        for (Branch branch : this.branches) {
            CompoundTag branchTag = new CompoundTag();
            branchTag.putLong(ANCHOR_TAG, branch.anchor().asLong());
            branchTag.putLong(END_TAG, branch.end().asLong());
            branchTags.add(branchTag);
        }
        routeTag.put(BRANCHES_TAG, branchTags);
        state.put(ROUTE_TAG, routeTag);
    }

    public static void clear(CompoundTag state) {
        if (state != null) {
            state.remove(ROUTE_TAG);
        }
    }

    public HiredRoute validatedChain() {
        if (this.nodes.size() < 2) {
            return new HiredRoute(this.nodes, false, this.branches);
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
        return new HiredRoute(validNodes, validLoop, this.branches);
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

    /** Expands branches into ordinary out-and-back route nodes for courier traversal. */
    public List<BlockPos> traversalNodes() {
        if (this.nodes.isEmpty() || this.branches.isEmpty()) {
            return this.nodes;
        }
        record Attachment(Branch branch, int segmentIndex, double progress, int branchIndex) {
        }
        List<Attachment> attachments = new ArrayList<>();
        int segmentCount = Math.max(1, this.nodes.size() - 1);
        for (int branchIndex = 0; branchIndex < this.branches.size(); branchIndex++) {
            Branch branch = this.branches.get(branchIndex);
            int bestSegment = 0;
            double bestProgress = 0.0D;
            double bestDistance = Double.MAX_VALUE;
            for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
                BlockPos first = this.nodes.get(segmentIndex);
                BlockPos second = this.nodes.size() == 1 ? first : this.nodes.get(segmentIndex + 1);
                double progress = segmentProgress(branch.anchor(), first, second);
                double projectedX = first.getX() + (second.getX() - first.getX()) * progress;
                double projectedY = first.getY() + (second.getY() - first.getY()) * progress;
                double projectedZ = first.getZ() + (second.getZ() - first.getZ()) * progress;
                double dx = branch.anchor().getX() - projectedX;
                double dy = branch.anchor().getY() - projectedY;
                double dz = branch.anchor().getZ() - projectedZ;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSegment = segmentIndex;
                    bestProgress = progress;
                }
            }
            attachments.add(new Attachment(branch, bestSegment, bestProgress, branchIndex));
        }
        attachments.sort(java.util.Comparator
                .comparingInt(Attachment::segmentIndex)
                .thenComparingDouble(Attachment::progress)
                .thenComparingInt(Attachment::branchIndex));

        List<BlockPos> traversal = new ArrayList<>();
        addTraversalNode(traversal, this.nodes.getFirst());
        for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
            for (Attachment attachment : attachments) {
                if (attachment.segmentIndex() != segmentIndex) {
                    continue;
                }
                addTraversalNode(traversal, attachment.branch().anchor());
                addTraversalNode(traversal, attachment.branch().end());
                addTraversalNode(traversal, attachment.branch().anchor());
            }
            if (this.nodes.size() > 1) {
                addTraversalNode(traversal, this.nodes.get(segmentIndex + 1));
            }
        }
        if (!this.loop
                && traversal.size() >= 2
                && traversal.getLast().equals(this.nodes.getLast())
                && attachments.getLast().branch().anchor().equals(this.nodes.getLast())
                && !attachments.getLast().branch().end().equals(this.nodes.getLast())) {
            traversal.removeLast();
        }
        return List.copyOf(traversal);
    }

    private static double segmentProgress(BlockPos pos, BlockPos first, BlockPos second) {
        double vx = second.getX() - first.getX();
        double vy = second.getY() - first.getY();
        double vz = second.getZ() - first.getZ();
        double lengthSqr = vx * vx + vy * vy + vz * vz;
        if (lengthSqr <= 0.0001D) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D,
                ((pos.getX() - first.getX()) * vx
                        + (pos.getY() - first.getY()) * vy
                        + (pos.getZ() - first.getZ()) * vz) / lengthSqr));
    }

    private static void addTraversalNode(List<BlockPos> traversal, BlockPos node) {
        if (traversal.isEmpty() || !traversal.getLast().equals(node)) {
            traversal.add(node.immutable());
        }
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
        if (this.loop && this.nodes.size() > 1
                && isNearSegment(pos, this.nodes.getLast(), this.nodes.getFirst(), safeHorizontal, safeVertical)) {
            return true;
        }
        for (Branch branch : this.branches) {
            if (isNearSegment(pos, branch.anchor(), branch.end(), safeHorizontal, safeVertical)) {
                return true;
            }
        }
        return false;
    }

    public BlockPos nearestBaseAttachment(BlockPos pos, int horizontalRadius, int verticalRadius) {
        if (pos == null || this.nodes.isEmpty()) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos node : this.nodes) {
            double distance = pos.distSqr(node);
            if (isNearNode(pos, node, horizontalRadius, verticalRadius) && distance < bestDistance) {
                best = node;
                bestDistance = distance;
            }
        }
        for (int index = 1; index < this.nodes.size(); index++) {
            BlockPos candidate = nearestPointOnSegment(pos, this.nodes.get(index - 1), this.nodes.get(index));
            double distance = pos.distSqr(candidate);
            if (isNearSegment(pos, this.nodes.get(index - 1), this.nodes.get(index), horizontalRadius, verticalRadius)
                    && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (this.loop && this.nodes.size() > 1) {
            BlockPos candidate = nearestPointOnSegment(pos, this.nodes.getLast(), this.nodes.getFirst());
            double distance = pos.distSqr(candidate);
            if (isNearSegment(pos, this.nodes.getLast(), this.nodes.getFirst(), horizontalRadius, verticalRadius)
                    && distance < bestDistance) {
                best = candidate;
            }
        }
        return best == null ? null : best.immutable();
    }

    private static boolean isNearBaseRoute(List<BlockPos> nodes, boolean loop, BlockPos pos, int horizontalRadius, int verticalRadius) {
        HiredRoute base = new HiredRoute(nodes, loop, List.of());
        return base.isNearRoute(pos, horizontalRadius, verticalRadius);
    }

    private static BlockPos nearestPointOnSegment(BlockPos pos, BlockPos first, BlockPos second) {
        double vx = second.getX() - first.getX();
        double vy = second.getY() - first.getY();
        double vz = second.getZ() - first.getZ();
        double lengthSqr = vx * vx + vy * vy + vz * vz;
        if (lengthSqr <= 0.0001D) {
            return first;
        }
        double progress = Math.max(0.0D, Math.min(1.0D,
                ((pos.getX() - first.getX()) * vx
                        + (pos.getY() - first.getY()) * vy
                        + (pos.getZ() - first.getZ()) * vz) / lengthSqr));
        return new BlockPos(
                (int) Math.round(first.getX() + vx * progress),
                (int) Math.round(first.getY() + vy * progress),
                (int) Math.round(first.getZ() + vz * progress));
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

    public record Branch(BlockPos anchor, BlockPos end) {
        public Branch {
            anchor = anchor == null ? BlockPos.ZERO : anchor.immutable();
            end = end == null ? BlockPos.ZERO : end.immutable();
        }
    }
}
