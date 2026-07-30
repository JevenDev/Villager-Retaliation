package com.jvn.villagerretaliation.villager;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class VillagerWorkExperience {
    private static final String OWNER_TAG = "VillagerRetaliationWorkExperienceOwner";
    public static final double PICKUP_RANGE = 8.0D;

    private VillagerWorkExperience() {
    }

    public static void spawn(Level level, Villager owner, Vec3 position, int value) {
        if (value <= 0) {
            return;
        }

        ExperienceOrb orb = new ExperienceOrb(level, position.x, position.y, position.z, value);
        orb.getPersistentData().putUUID(OWNER_TAG, owner.getUUID());
        level.addFreshEntity(orb);
    }

    public static boolean belongsTo(ExperienceOrb orb, Villager villager) {
        return orb.getPersistentData().hasUUID(OWNER_TAG)
                && orb.getPersistentData().getUUID(OWNER_TAG).equals(villager.getUUID());
    }

    public static boolean hasNearbyOwnedExperience(Villager villager) {
        return !villager.level().getEntitiesOfClass(
                ExperienceOrb.class,
                villager.getBoundingBox().inflate(PICKUP_RANGE),
                orb -> orb.isAlive() && belongsTo(orb, villager)).isEmpty();
    }
}
