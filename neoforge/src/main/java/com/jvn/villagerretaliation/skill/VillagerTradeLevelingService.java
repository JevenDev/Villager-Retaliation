package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

public final class VillagerTradeLevelingService {
    private static final double PROGRESS_EPSILON = 0.000_001D;

    private VillagerTradeLevelingService() {
    }

    public static int adjustedTradeLevelXp(ServerLevel level, Villager villager, @Nullable MerchantOffer offer) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_BASED_TRADE_LEVELING.get()
                || villager == null
                || villager.isBaby()
                || offer == null
                || offer.getXp() <= 0
                || !VillagerData.canLevelUp(villager.getVillagerData().getLevel())) {
            return offer == null ? 0 : offer.getXp();
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        VillagerSkill primarySkill = VillagerProfessionSkills.primarySkill(villager);
        int skillValue = profile.skills().get(primarySkill);
        double totalScaledProgress = profile.tradeLevelSkillAdjustedXpProgress()
                + offer.getXp() * tradeLevelXpMultiplier(skillValue);
        int effectiveXp = Math.max(1, (int) Math.floor(totalScaledProgress + PROGRESS_EPSILON));
        double remainingProgress = Math.max(0.0D, totalScaledProgress - effectiveXp);

        boolean progressChanged = profile.setTradeLevelSkillAdjustedXpProgress(remainingProgress, level.getGameTime());
        if (progressChanged) {
            VillagerProfileSavedData.get(level).setDirty();
        }

        return Math.max(0, effectiveXp);
    }

    public static double tradeLevelXpMultiplier(int skillValue) {
        double minMultiplier = clamp(
                VillagerRetaliationConfig.SKILL_BASED_TRADE_LEVELING_MIN_MULTIPLIER.get(),
                0.0D,
                1.0D);
        double maxMultiplier = clamp(
                VillagerRetaliationConfig.SKILL_BASED_TRADE_LEVELING_MAX_MULTIPLIER.get(),
                minMultiplier,
                1.0D);
        double normalizedSkill = (VillagerSkillSet.clamp(skillValue) - VillagerSkillSet.MIN_VALUE)
                / (double) (VillagerSkillSet.MAX_VALUE - VillagerSkillSet.MIN_VALUE);
        double curvedSkill = normalizedSkill * normalizedSkill;
        return minMultiplier + (maxMultiplier - minMultiplier) * curvedSkill;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
