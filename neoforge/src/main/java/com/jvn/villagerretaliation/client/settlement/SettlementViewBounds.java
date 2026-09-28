package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public record SettlementViewBounds(BlockPos min, BlockPos max) {
    public SettlementViewBounds {
        min = min.immutable();
        max = max.immutable();
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            throw new IllegalArgumentException("Inverted area bounds");
        }
    }

    public int size(Direction.Axis axis) {
        return this.max.get(axis) - this.min.get(axis) + 1;
    }

    public long volume() {
        return (long) size(Direction.Axis.X) * size(Direction.Axis.Y) * size(Direction.Axis.Z);
    }

    public AABB box() {
        return new AABB(this.min.getX(), this.min.getY(), this.min.getZ(),
                this.max.getX() + 1.0D, this.max.getY() + 1.0D, this.max.getZ() + 1.0D);
    }

    public boolean valid(Limits limits) {
        return size(Direction.Axis.X) <= 128 && size(Direction.Axis.Y) <= 64 && size(Direction.Axis.Z) <= 128
                && volume() <= SettlementArea.MAX_VOLUME && limits.contains(this.min) && limits.contains(this.max);
    }

    public SettlementViewBounds translate(Direction.Axis axis, int blocks, Limits limits) {
        int delta = Math.clamp(blocks, limits.min(axis) - this.min.get(axis), limits.max(axis) - this.max.get(axis));
        return new SettlementViewBounds(with(this.min, axis, this.min.get(axis) + delta),
                with(this.max, axis, this.max.get(axis) + delta));
    }

    public SettlementViewBounds resize(Direction face, int blocks, Limits limits) {
        Direction.Axis axis = face.getAxis();
        long crossSection = volume() / size(axis);
        int maximum = (int) Math.min(axis == Direction.Axis.Y ? 64 : 128, SettlementArea.MAX_VOLUME / crossSection);
        if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            int next = (int) Math.clamp((long) this.max.get(axis) + blocks, this.min.get(axis),
                    Math.min(limits.max(axis), (long) this.min.get(axis) + maximum - 1L));
            return new SettlementViewBounds(this.min, with(this.max, axis, next));
        }
        int next = (int) Math.clamp((long) this.min.get(axis) + blocks,
                Math.max(limits.min(axis), (long) this.max.get(axis) - maximum + 1L), this.max.get(axis));
        return new SettlementViewBounds(with(this.min, axis, next), this.max);
    }

    public static SettlementViewBounds drawing(BlockPos first, BlockPos second, Limits limits) {
        BlockPos start = limits.clamp(first);
        var bounds = new SettlementViewBounds(start, start);
        for (Direction.Axis axis : Direction.Axis.values()) {
            int delta = second.get(axis) - start.get(axis);
            Direction face = Direction.fromAxisAndDirection(axis, delta < 0
                    ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
            bounds = bounds.resize(face, delta, limits);
        }
        return bounds;
    }

    private static BlockPos with(BlockPos position, Direction.Axis axis, int value) {
        return new BlockPos(axis == Direction.Axis.X ? value : position.getX(),
                axis == Direction.Axis.Y ? value : position.getY(), axis == Direction.Axis.Z ? value : position.getZ());
    }

    public record Limits(BlockPos center, int radius, int minHeight, int maxHeight) {
        public int min(Direction.Axis axis) {
            return axis == Direction.Axis.Y ? this.minHeight : this.center.get(axis) - this.radius;
        }

        public int max(Direction.Axis axis) {
            return axis == Direction.Axis.Y ? this.maxHeight : this.center.get(axis) + this.radius;
        }

        public boolean contains(BlockPos position) {
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (position.get(axis) < min(axis) || position.get(axis) > max(axis)) {
                    return false;
                }
            }
            return true;
        }

        public BlockPos clamp(BlockPos position) {
            return new BlockPos(Math.clamp(position.getX(), min(Direction.Axis.X), max(Direction.Axis.X)),
                    Math.clamp(position.getY(), minHeight, maxHeight),
                    Math.clamp(position.getZ(), min(Direction.Axis.Z), max(Direction.Axis.Z)));
        }
    }
}
