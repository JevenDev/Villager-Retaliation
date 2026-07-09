package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

final class HiredWorkCollectionBounds {
    private HiredWorkCollectionBounds() {
    }

    static AABB around(HiredWorkContext context) {
        if (context.hasRoute()) {
            return routeBounds(context);
        }
        return new AABB(
                context.workMin().getX(),
                context.workMin().getY(),
                context.workMin().getZ(),
                context.workMax().getX() + 1.0D,
                context.workMax().getY() + 1.0D,
                context.workMax().getZ() + 1.0D);
    }

    private static AABB routeBounds(HiredWorkContext context) {
        HiredRoute route = context.route();
        int horizontalPadding = HiredRoute.MAX_NODE_DISTANCE;
        int verticalPadding = Math.max(2, context.verticalRadius());
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos node : route.nodes()) {
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
}
