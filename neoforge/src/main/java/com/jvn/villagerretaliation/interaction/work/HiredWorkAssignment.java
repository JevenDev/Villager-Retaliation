package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredJobSite;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Shared geometry and assignment policy for a hired worker's job site or route.
 */
public final class HiredWorkAssignment {
    private final HiredJobSite jobSite;
    private final HiredRoute route;
    private AABB collectionBounds;
    private List<AABB> entitySearchBounds;

    private HiredWorkAssignment(HiredJobSite jobSite, HiredRoute route) {
        this.jobSite = jobSite;
        this.route = route;
    }

    public static HiredWorkAssignment of(HiredJobSite jobSite, HiredRoute route) {
        HiredJobSite safeJobSite = jobSite == null
                ? HiredJobSite.fromWorkArea(HiredWorkArea.fromCenter(BlockPos.ZERO, 1, 1, false).asUsable(false))
                : jobSite;
        return new HiredWorkAssignment(safeJobSite, route == null ? HiredRoute.empty() : route);
    }

    public HiredJobSite jobSite() {
        return this.jobSite;
    }

    public HiredRoute route() {
        return this.route;
    }

    public HiredWorkArea workArea() {
        return this.jobSite.workArea();
    }

    public boolean hasRoute() {
        return this.route.usableForNavigation();
    }

    public boolean isInsideWorkArea(BlockPos pos) {
        return this.jobSite.isInsideWorkBounds(pos);
    }

    public boolean isInsideRouteArea(BlockPos pos) {
        return hasRoute()
                && isInsideCollectionBounds(pos)
                && this.route.isNearRoute(
                        pos,
                        HiredRoute.MAX_NODE_DISTANCE,
                        Math.max(2, workArea().verticalRadius()));
    }

    public boolean isInsideWorkAreaOrRoute(BlockPos pos) {
        return hasRoute() ? isInsideRouteArea(pos) : isInsideWorkArea(pos);
    }

    public boolean hasNavigationTether() {
        return this.jobSite.hasNavigationTether();
    }

    public boolean isInsideNavigationTether(BlockPos pos, int horizontalPadding, int verticalPadding) {
        return this.jobSite.isInsideNavigationTether(pos, horizontalPadding, verticalPadding);
    }

    public AABB collectionBounds() {
        if (this.collectionBounds == null) {
            this.collectionBounds = hasRoute() ? routeBounds() : workAreaBounds();
        }
        return this.collectionBounds;
    }

    public List<AABB> entitySearchBounds() {
        if (this.entitySearchBounds == null) {
            this.entitySearchBounds = hasRoute()
                    ? List.copyOf(routeSegmentBounds())
                    : List.of(collectionBounds());
        }
        return this.entitySearchBounds;
    }

    private boolean isInsideCollectionBounds(BlockPos pos) {
        return pos != null && collectionBounds().contains(pos.getCenter());
    }

    private AABB workAreaBounds() {
        HiredWorkArea area = workArea();
        return new AABB(
                area.min().getX(),
                area.min().getY(),
                area.min().getZ(),
                area.max().getX() + 1.0D,
                area.max().getY() + 1.0D,
                area.max().getZ() + 1.0D);
    }

    private AABB routeBounds() {
        int horizontalPadding = HiredRoute.MAX_NODE_DISTANCE;
        int verticalPadding = Math.max(2, workArea().verticalRadius());
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos node : this.route.nodes()) {
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            minZ = Math.min(minZ, node.getZ());
            maxX = Math.max(maxX, node.getX());
            maxY = Math.max(maxY, node.getY());
            maxZ = Math.max(maxZ, node.getZ());
        }
        return new AABB(
                minX - horizontalPadding,
                minY - verticalPadding,
                minZ - horizontalPadding,
                maxX + horizontalPadding + 1.0D,
                maxY + verticalPadding + 1.0D,
                maxZ + horizontalPadding + 1.0D);
    }

    private List<AABB> routeSegmentBounds() {
        List<BlockPos> nodes = this.route.nodes();
        int horizontalPadding = HiredRoute.MAX_NODE_DISTANCE;
        int verticalPadding = Math.max(2, workArea().verticalRadius());
        List<AABB> bounds = new ArrayList<>(Math.max(1, nodes.size()));
        if (nodes.size() == 1) {
            bounds.add(segmentBounds(nodes.getFirst(), nodes.getFirst(), horizontalPadding, verticalPadding));
            return bounds;
        }
        for (int index = 1; index < nodes.size(); index++) {
            bounds.add(segmentBounds(nodes.get(index - 1), nodes.get(index), horizontalPadding, verticalPadding));
        }
        if (this.route.loop()) {
            bounds.add(segmentBounds(nodes.getLast(), nodes.getFirst(), horizontalPadding, verticalPadding));
        }
        return bounds;
    }

    private static AABB segmentBounds(BlockPos first, BlockPos second, int horizontalPadding, int verticalPadding) {
        return new AABB(
                Math.min(first.getX(), second.getX()) - horizontalPadding,
                Math.min(first.getY(), second.getY()) - verticalPadding,
                Math.min(first.getZ(), second.getZ()) - horizontalPadding,
                Math.max(first.getX(), second.getX()) + horizontalPadding + 1.0D,
                Math.max(first.getY(), second.getY()) + verticalPadding + 1.0D,
                Math.max(first.getZ(), second.getZ()) + horizontalPadding + 1.0D);
    }
}
