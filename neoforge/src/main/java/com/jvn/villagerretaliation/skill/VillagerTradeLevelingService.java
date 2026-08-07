package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
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
        TradeXpResult result = calculateTradeLevelXp(
                offer.getXp(),
                tradeLevelXpMultiplier(skillValue),
                profile.tradeLevelSkillAdjustedXpProgress());

        boolean progressChanged = profile.setTradeLevelSkillAdjustedXpProgress(result.remainingProgress(), level.getGameTime());
        if (progressChanged) {
            VillagerProfileSavedData.get(level).setDirty();
        }

        return result.awardedXp();
    }

    public static boolean onTradeLevelChanged(
            ServerLevel level, Villager villager, int previousLevel, int currentLevel) {
        if (level == null || villager == null) return false;
        int gainedLevels = Math.clamp(currentLevel, 1, 5) - Math.clamp(previousLevel, 1, 5);
        if (gainedLevels <= 0) return false;
        return VillagerProfileManager.adjustAttribute(
                level, villager, VillagerSocialAttribute.KNOWLEDGE, gainedLevels);
    }

    /**
     * Calculates one trade independently of world state so the server award and client preview use identical math.
     * The one-XP guarantee applies to this trade's contribution before carried progress is added. This prevents a
     * low-XP trade from spending fractional progress banked by an earlier trade without actually awarding it.
     */
    public static TradeXpResult calculateTradeLevelXp(int vanillaXp, double multiplier, double carriedProgress) {
        if (vanillaXp <= 0) {
            return new TradeXpResult(Math.max(0, vanillaXp), clampProgress(carriedProgress));
        }

        double safeMultiplier = clamp(multiplier, 0.0D, 1.0D);
        double tradeContribution = Math.max(1.0D, vanillaXp * safeMultiplier);
        double totalProgress = clampProgress(carriedProgress) + tradeContribution;
        int awardedXp = Math.max(1, (int) Math.floor(totalProgress + PROGRESS_EPSILON));
        return new TradeXpResult(awardedXp, clampProgress(totalProgress - awardedXp));
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
        return minMultiplier + (maxMultiplier - minMultiplier) * normalizedSkill;
    }

    private static double clampProgress(double progress) {
        return clamp(progress, 0.0D, 0.999_999D);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public record TradeXpResult(int awardedXp, double remainingProgress) {
    }
}
