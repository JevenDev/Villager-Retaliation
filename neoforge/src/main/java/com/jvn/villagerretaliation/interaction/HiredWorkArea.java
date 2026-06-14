package com.jvn.villagerretaliation.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

public record HiredWorkArea(
        BlockPos center,
        BlockPos min,
        BlockPos max,
        int horizontalRadius,
        int verticalRadius,
        boolean explicitlyAssigned,
        boolean usable) {
    public static final String WORK_CENTER_POS_TAG = "WorkCenterPos";
    public static final String WORK_MIN_POS_TAG = "WorkMinPos";
    public static final String WORK_MAX_POS_TAG = "WorkMaxPos";
    public static final String RADIUS_TAG = "Radius";
    public static final String WORK_AREA_ASSIGNED_TAG = "WorkAreaAssigned";

    public HiredWorkArea {
        if (min == null) {
            min = center == null ? BlockPos.ZERO : center;
        }
        if (max == null) {
            max = min;
        }
        BlockPos originalMin = min;
        BlockPos originalMax = max;
        min = minPos(originalMin, originalMax);
        max = maxPos(originalMin, originalMax);
        center = center == null ? centerPos(min, max) : center.immutable();
        horizontalRadius = Math.max(1, horizontalRadius(center, min, max));
        verticalRadius = Math.max(1, verticalRadius(center, min, max));
        usable = usable && min != null && max != null && center != null;
    }

    public static HiredWorkArea fromBounds(BlockPos first, BlockPos second, boolean explicitlyAssigned) {
        BlockPos min = minPos(first, second);
        BlockPos max = maxPos(first, second);
        BlockPos center = centerPos(min, max);
        return new HiredWorkArea(center, min, max, horizontalRadius(center, min, max), verticalRadius(center, min, max), explicitlyAssigned, true);
    }

    public static HiredWorkArea fromCenter(BlockPos center, int horizontalRadius, int verticalRadius, boolean explicitlyAssigned) {
        int safeHorizontal = Math.max(1, horizontalRadius);
        int safeVertical = Math.max(1, verticalRadius);
        BlockPos min = center.offset(-safeHorizontal, -safeVertical, -safeHorizontal);
        BlockPos max = center.offset(safeHorizontal, safeVertical, safeHorizontal);
        return new HiredWorkArea(center, min, max, safeHorizontal, safeVertical, explicitlyAssigned, true);
    }

    public static HiredWorkArea fromState(CompoundTag state, BlockPos fallbackCenter) {
        if (!state.contains(WORK_CENTER_POS_TAG, Tag.TAG_LONG)
                || !state.contains(WORK_MIN_POS_TAG, Tag.TAG_LONG)
                || !state.contains(WORK_MAX_POS_TAG, Tag.TAG_LONG)) {
            return fromCenter(fallbackCenter, Math.max(1, state.getInt(RADIUS_TAG)), 1, false).asUsable(false);
        }
        BlockPos center = BlockPos.of(state.getLong(WORK_CENTER_POS_TAG));
        BlockPos min = BlockPos.of(state.getLong(WORK_MIN_POS_TAG));
        BlockPos max = BlockPos.of(state.getLong(WORK_MAX_POS_TAG));
        boolean assigned = state.getBoolean(WORK_AREA_ASSIGNED_TAG);
        return new HiredWorkArea(center, min, max, horizontalRadius(center, min, max), verticalRadius(center, min, max), assigned, assigned);
    }

    public HiredWorkArea clampedTo(int maxRadius) {
        int safeRadius = Math.max(1, maxRadius);
        BlockPos clampedMin = new BlockPos(
                Math.max(this.min.getX(), this.center.getX() - safeRadius),
                Math.max(this.min.getY(), this.center.getY() - safeRadius),
                Math.max(this.min.getZ(), this.center.getZ() - safeRadius));
        BlockPos clampedMax = new BlockPos(
                Math.min(this.max.getX(), this.center.getX() + safeRadius),
                Math.min(this.max.getY(), this.center.getY() + safeRadius),
                Math.min(this.max.getZ(), this.center.getZ() + safeRadius));
        return new HiredWorkArea(this.center, clampedMin, clampedMax, horizontalRadius(this.center, clampedMin, clampedMax), verticalRadius(this.center, clampedMin, clampedMax), this.explicitlyAssigned, this.usable);
    }

    public HiredWorkArea asAssigned(boolean explicitlyAssigned) {
        return new HiredWorkArea(this.center, this.min, this.max, this.horizontalRadius, this.verticalRadius, explicitlyAssigned, explicitlyAssigned);
    }

    public HiredWorkArea asUsable(boolean usable) {
        return new HiredWorkArea(this.center, this.min, this.max, this.horizontalRadius, this.verticalRadius, this.explicitlyAssigned, usable);
    }

    public boolean contains(BlockPos pos) {
        return pos != null
                && pos.getX() >= this.min.getX()
                && pos.getX() <= this.max.getX()
                && pos.getY() >= this.min.getY()
                && pos.getY() <= this.max.getY()
                && pos.getZ() >= this.min.getZ()
                && pos.getZ() <= this.max.getZ();
    }

    public String boundsDescription() {
        return this.min.getX() + " " + this.min.getY() + " " + this.min.getZ()
                + " to " + this.max.getX() + " " + this.max.getY() + " " + this.max.getZ();
    }

    public String centerDescription() {
        return this.center.getX() + " " + this.center.getY() + " " + this.center.getZ();
    }

    public String rangeDescription() {
        return "center " + centerDescription() + ", H " + this.horizontalRadius + ", V " + this.verticalRadius;
    }

    public void save(CompoundTag state) {
        state.putLong(WORK_CENTER_POS_TAG, this.center.asLong());
        state.putLong(WORK_MIN_POS_TAG, this.min.asLong());
        state.putLong(WORK_MAX_POS_TAG, this.max.asLong());
        state.putInt(RADIUS_TAG, this.horizontalRadius);
        state.putBoolean(WORK_AREA_ASSIGNED_TAG, this.explicitlyAssigned);
    }

    public static BlockPos minPos(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    public static BlockPos maxPos(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    public static BlockPos centerPos(BlockPos min, BlockPos max) {
        return new BlockPos(
                Math.floorDiv(min.getX() + max.getX(), 2),
                Math.floorDiv(min.getY() + max.getY(), 2),
                Math.floorDiv(min.getZ() + max.getZ(), 2));
    }

    public static int horizontalRadius(BlockPos center, BlockPos min, BlockPos max) {
        return Math.max(
                Math.max(Math.abs(center.getX() - min.getX()), Math.abs(max.getX() - center.getX())),
                Math.max(Math.abs(center.getZ() - min.getZ()), Math.abs(max.getZ() - center.getZ())));
    }

    public static int verticalRadius(BlockPos center, BlockPos min, BlockPos max) {
        return Math.max(Math.abs(center.getY() - min.getY()), Math.abs(max.getY() - center.getY()));
    }

    public static int clampRadius(int radius, int minRadius, int maxRadius) {
        return Mth.clamp(radius, minRadius, Math.max(minRadius, maxRadius));
    }
}
