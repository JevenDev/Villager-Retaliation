package com.jvn.villagerretaliation.villager;

import java.util.List;
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
        return isOwned(orb)
                && orb.getPersistentData().getUUID(OWNER_TAG).equals(villager.getUUID());
    }

    public static boolean isOwned(ExperienceOrb orb) {
        return orb != null && orb.getPersistentData().hasUUID(OWNER_TAG);
    }

    public static boolean mayMerge(ExperienceOrb first, ExperienceOrb second) {
        return !isOwned(first) && !isOwned(second);
    }

    public static Villager mendingTarget(ExperienceOrb orb, net.minecraft.world.entity.player.Player followingPlayer, List<Villager> villagers) {
        Villager nearest = null;
        double distance = Double.MAX_VALUE;
        for (var villager : villagers) {
            if (!villager.isAlive() || !VillagerEquipmentMending.canRepair(villager)
                    && !(belongsTo(orb, villager) && VillagerEquipmentMending.hasMendingEquipment(villager))) continue;
            double candidate = villager.distanceToSqr(orb);
            if (candidate < 64.0D && candidate < distance) {
                nearest = villager;
                distance = candidate;
            }
        }
        return nearest != null && (belongsTo(orb, nearest) || followingPlayer == null || distance < followingPlayer.distanceToSqr(orb))
                ? nearest : null;
    }

    public static boolean mendOnContact(ExperienceOrb orb, Villager villager) {
        if (villager == null || !villager.isAlive() || villager.level() != orb.level() || villager.distanceToSqr(orb) >= 64.0D
                || !orb.getBoundingBox().intersects(villager.getBoundingBox())) return false;
        boolean owned = belongsTo(orb, villager) && VillagerEquipmentMending.hasMendingEquipment(villager);
        return VillagerEquipmentMending.repairWithXp(villager, orb.getValue()) || owned;
    }

    public static boolean hasNearbyOwnedExperience(Villager villager) {
        return !villager.level().getEntitiesOfClass(
                ExperienceOrb.class,
                villager.getBoundingBox().inflate(PICKUP_RANGE),
                orb -> orb.isAlive() && belongsTo(orb, villager)).isEmpty();
    }
}
