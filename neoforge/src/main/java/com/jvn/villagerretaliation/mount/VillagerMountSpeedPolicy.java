package com.jvn.villagerretaliation.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;

/** Selects walking or catch-up speed for a villager controlling an assigned horse mount. */
public final class VillagerMountSpeedPolicy {
    public static final double WALK_DISTANCE = 8.0D;
    public static final double WALK_SPEED = 1.0D;
    public static final double SPRINT_SPEED = 1.45D;

    private static final double WALK_DISTANCE_SQR = WALK_DISTANCE * WALK_DISTANCE;
    private static final double REFERENCE_HORSE_SPEED = 0.30D;
    private static final double MAX_NAVIGATION_SPEED = 4.0D;

    private VillagerMountSpeedPolicy() {
    }

    public static double toward(Villager villager, Entity target, double onFootSpeed) {
        if (target == null) {
            return onFootSpeed;
        }
        return toward(villager, target.getX(), target.getY(), target.getZ(), onFootSpeed);
    }

    public static double toward(Villager villager, BlockPos target, double onFootSpeed) {
        if (target == null) {
            return onFootSpeed;
        }
        return toward(
                villager,
                target.getX() + 0.5D,
                target.getY(),
                target.getZ() + 0.5D,
                onFootSpeed);
    }

    private static double toward(
            Villager villager,
            double targetX,
            double targetY,
            double targetZ,
            double onFootSpeed) {
        if (villager == null || !(villager.getControlledVehicle() instanceof AbstractHorse horse)) {
            return onFootSpeed;
        }
        double selectedSpeed = horse.distanceToSqr(targetX, targetY, targetZ) > WALK_DISTANCE_SQR
                ? SPRINT_SPEED
                : WALK_SPEED;
        double horseSpeed = horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (horseSpeed <= 0.0D) {
            return selectedSpeed;
        }

        // Millager clamps rider horses to a 0.30 movement attribute. Assigned mounts can have
        // any vanilla roll, so normalize only the navigation modifier rather than permanently
        // rewriting a player's horse stats. Naturally fast horses retain their advantage.
        double referenceModifier = REFERENCE_HORSE_SPEED * selectedSpeed / horseSpeed;
        return Math.min(MAX_NAVIGATION_SPEED, Math.max(selectedSpeed, referenceModifier));
    }
}
