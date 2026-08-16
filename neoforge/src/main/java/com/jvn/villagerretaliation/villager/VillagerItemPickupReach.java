package com.jvn.villagerretaliation.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class VillagerItemPickupReach {
    private static final double PARTIAL_BLOCK_REACH_SQR = 4.0D;
    private static final int OBSTRUCTION_SAMPLES = 8;

    private VillagerItemPickupReach() {
    }

    public static boolean isWithinReach(LivingEntity villager, Entity item, double horizontalReachSqr) {
        if (villager == null || item == null || horizontalReachSqr < 0.0D) {
            return false;
        }
        if (villager.distanceToSqr(item) <= horizontalReachSqr) {
            return true;
        }

        double dx = villager.getX() - item.getX();
        double dz = villager.getZ() - item.getZ();
        double horizontalDistanceSqr = dx * dx + dz * dz;
        AABB villagerBounds = villager.getBoundingBox();
        AABB itemBounds = item.getBoundingBox();
        boolean overlapsVertically = itemBounds.maxY >= villagerBounds.minY
                && itemBounds.minY <= villagerBounds.maxY;
        if (!overlapsVertically) {
            return false;
        }
        if (horizontalDistanceSqr <= horizontalReachSqr) {
            return true;
        }
        return horizontalDistanceSqr <= PARTIAL_BLOCK_REACH_SQR
                && hasOnlyPartialCollisionBetween(villager, item);
    }

    private static boolean hasOnlyPartialCollisionBetween(LivingEntity villager, Entity item) {
        boolean foundPartialCollision = false;
        for (int sample = 1; sample <= OBSTRUCTION_SAMPLES; sample++) {
            double progress = (double) sample / OBSTRUCTION_SAMPLES;
            BlockPos pos = BlockPos.containing(
                    villager.getX() + (item.getX() - villager.getX()) * progress,
                    item.getY(),
                    villager.getZ() + (item.getZ() - villager.getZ()) * progress);
            BlockState state = villager.level().getBlockState(pos);
            if (state.getCollisionShape(villager.level(), pos).isEmpty()) {
                continue;
            }
            if (state.isCollisionShapeFullBlock(villager.level(), pos)) {
                return false;
            }
            foundPartialCollision = true;
        }
        return foundPartialCollision;
    }
}
