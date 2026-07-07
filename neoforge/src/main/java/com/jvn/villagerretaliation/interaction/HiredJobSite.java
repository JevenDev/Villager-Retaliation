package com.jvn.villagerretaliation.interaction;

import net.minecraft.core.BlockPos;

public record HiredJobSite(
        HiredWorkArea workArea,
        BlockPos anchor,
        AnchorSource anchorSource,
        int anchorHorizontalRadius,
        int anchorVerticalRadius) {
    public HiredJobSite {
        workArea = workArea == null
                ? HiredWorkArea.fromCenter(BlockPos.ZERO, 1, 1, false).asUsable(false)
                : workArea;
        anchor = anchor == null ? null : anchor.immutable();
        anchorSource = anchorSource == null ? AnchorSource.NONE : anchorSource;
        anchorHorizontalRadius = Math.max(0, anchorHorizontalRadius);
        anchorVerticalRadius = Math.max(0, anchorVerticalRadius);
    }

    public static HiredJobSite fromWorkArea(HiredWorkArea area) {
        return new HiredJobSite(area, null, AnchorSource.NONE, 0, 0);
    }

    public static HiredJobSite withAnchor(
            HiredWorkArea area,
            BlockPos anchor,
            AnchorSource source,
            int anchorHorizontalRadius,
            int anchorVerticalRadius) {
        return new HiredJobSite(area, anchor, source, anchorHorizontalRadius, anchorVerticalRadius);
    }

    public static HiredJobSite fromAnchor(
            BlockPos anchor,
            int horizontalRadius,
            int verticalRadius,
            AnchorSource source) {
        HiredWorkArea area = HiredWorkArea.fromCenter(anchor, horizontalRadius, verticalRadius, false);
        return withAnchor(area, anchor, source, horizontalRadius, verticalRadius);
    }

    public boolean hasWorkBounds() {
        return this.workArea.usable();
    }

    public boolean hasAnchor() {
        return this.anchor != null && this.anchorSource != AnchorSource.NONE;
    }

    public boolean hasNavigationTether() {
        return hasWorkBounds() || hasAnchor();
    }

    public boolean isInsideWorkBounds(BlockPos pos) {
        return hasWorkBounds() && this.workArea.contains(pos);
    }

    public boolean isInsideNavigationTether(BlockPos pos, int horizontalPadding, int verticalPadding) {
        return isInsidePaddedWorkBounds(pos, horizontalPadding, verticalPadding) || isNearAnchor(pos);
    }

    public boolean isInsidePaddedWorkBounds(BlockPos pos, int horizontalPadding, int verticalPadding) {
        return hasWorkBounds()
                && pos != null
                && pos.getX() >= this.workArea.min().getX() - Math.max(0, horizontalPadding)
                && pos.getX() <= this.workArea.max().getX() + Math.max(0, horizontalPadding)
                && pos.getY() >= this.workArea.min().getY() - Math.max(0, verticalPadding)
                && pos.getY() <= this.workArea.max().getY() + Math.max(0, verticalPadding)
                && pos.getZ() >= this.workArea.min().getZ() - Math.max(0, horizontalPadding)
                && pos.getZ() <= this.workArea.max().getZ() + Math.max(0, horizontalPadding);
    }

    public boolean isNearAnchor(BlockPos pos) {
        if (!hasAnchor() || pos == null) {
            return false;
        }
        int dx = pos.getX() - this.anchor.getX();
        int dz = pos.getZ() - this.anchor.getZ();
        return dx * dx + dz * dz <= this.anchorHorizontalRadius * this.anchorHorizontalRadius
                && Math.abs(pos.getY() - this.anchor.getY()) <= this.anchorVerticalRadius;
    }

    public String centerDescription() {
        return this.workArea.centerDescription();
    }

    public String boundsDescription() {
        return this.workArea.boundsDescription();
    }

    public String sourceLabel() {
        return this.anchorSource.label();
    }

    public enum AnchorSource {
        NONE("assigned area"),
        VANILLA_JOB_SITE("job block"),
        BUILDER_TASK("build site");

        private final String label;

        AnchorSource(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }
}
