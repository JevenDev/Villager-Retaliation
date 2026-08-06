package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;

/**
 * Converts learned combat skills into concrete combat performance.
 * Personality attributes deliberately do not participate in these calculations.
 */
public final class VillagerCombatSkillBehavior {
    public static final int AXE_BREAKER_GUARDING_REQUIRED = HiredVillagerRoles.STANDARD_APTITUDE;

    private VillagerCombatSkillBehavior() {
    }

    public static int guarding(ServerLevel level, AbstractVillager villager) {
        return VillagerProfileManager.getSkill(level, villager, VillagerSkill.GUARDING);
    }

    public static int archery(ServerLevel level, AbstractVillager villager) {
        return VillagerProfileManager.getSkill(level, villager, VillagerSkill.ARCHERY);
    }

    public static int meleeAttackSpeedPercent(int guarding) {
        return piecewisePercent(guarding, 91, 100, 109);
    }

    public static int rangedAttackSpeedPercent(int archery) {
        return piecewisePercent(archery, 90, 100, 110);
    }

    public static int meleeDamagePercent(int guarding) {
        return piecewisePercent(guarding, 95, 100, 108);
    }

    /** Projectile spread multiplier. Lower values are more accurate. */
    public static int rangedSpreadPercent(int archery) {
        return piecewisePercent(archery, 135, 100, 75);
    }

    public static int adjustMeleeRecoveryTicks(
            ServerLevel level,
            AbstractVillager villager,
            int normalTicks) {
        return scaledRecoveryTicks(normalTicks, meleeAttackSpeedPercent(guarding(level, villager)));
    }

    public static int adjustRangedRecoveryTicks(
            ServerLevel level,
            AbstractVillager villager,
            int normalTicks) {
        return scaledRecoveryTicks(normalTicks, rangedAttackSpeedPercent(archery(level, villager)));
    }

    public static float adjustRangedInaccuracy(
            ServerLevel level,
            AbstractVillager villager,
            float baseInaccuracy) {
        return Math.max(0.0F, baseInaccuracy * rangedSpreadPercent(archery(level, villager)) / 100.0F);
    }

    public static boolean canUseAxeBreaker(ServerLevel level, AbstractVillager villager) {
        return canUseAxeBreaker(guarding(level, villager));
    }

    public static boolean canUseAxeBreaker(int guarding) {
        return guarding >= AXE_BREAKER_GUARDING_REQUIRED;
    }

    private static int scaledRecoveryTicks(int normalTicks, int speedPercent) {
        if (normalTicks <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(normalTicks * 100.0F / speedPercent));
    }

    private static int piecewisePercent(int skill, int minimum, int standard, int maximum) {
        int score = Math.clamp(skill, 0, 100);
        if (score <= HiredVillagerRoles.STANDARD_APTITUDE) {
            return minimum + Math.round(
                    score * (standard - minimum) / (float) HiredVillagerRoles.STANDARD_APTITUDE);
        }
        return standard + Math.round(
                (score - HiredVillagerRoles.STANDARD_APTITUDE) * (maximum - standard)
                        / (float) (100 - HiredVillagerRoles.STANDARD_APTITUDE));
    }
}
