package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class VillagerTradeLevelingService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double PROGRESS_EPSILON = 0.000_001D;
    private static final Field UPDATE_MERCHANT_TIMER_FIELD = villagerField("updateMerchantTimer");
    private static final Field INCREASE_PROFESSION_LEVEL_ON_UPDATE_FIELD = villagerField("increaseProfessionLevelOnUpdate");
    private static boolean warnedPendingLevelClearFailure;

    private VillagerTradeLevelingService() {
    }

    public static void onTradeCompleted(ServerLevel level, Villager villager, @Nullable MerchantOffer offer) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_BASED_TRADE_LEVELING.get()
                || villager == null
                || villager.isBaby()
                || offer == null
                || offer.getXp() <= 0
                || !VillagerData.canLevelUp(villager.getVillagerData().getLevel())) {
            return;
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        VillagerSkill primarySkill = VillagerProfessionSkills.primarySkill(villager);
        int skillValue = profile.skills().get(primarySkill);
        double multiplier = tradeLevelXpMultiplier(skillValue);
        int vanillaXp = offer.getXp();
        int xpBeforeTrade = Math.max(0, villager.getVillagerXp() - vanillaXp);

        double totalScaledProgress = profile.tradeLevelSkillAdjustedXpProgress() + vanillaXp * multiplier;
        int effectiveXp = (int) Math.floor(totalScaledProgress + PROGRESS_EPSILON);
        double remainingProgress = totalScaledProgress - effectiveXp;
        int adjustedXp = Math.max(0, xpBeforeTrade + effectiveXp);

        villager.setVillagerXp(adjustedXp);
        boolean progressChanged = profile.setTradeLevelSkillAdjustedXpProgress(remainingProgress, level.getGameTime());
        if (progressChanged) {
            VillagerProfileSavedData.get(level).setDirty();
        }

        clearPendingLevelUpIfBlocked(villager);
    }

    private static double tradeLevelXpMultiplier(int skillValue) {
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

    private static void clearPendingLevelUpIfBlocked(Villager villager) {
        int tradeLevel = villager.getVillagerData().getLevel();
        if (!VillagerData.canLevelUp(tradeLevel) || villager.getVillagerXp() >= VillagerData.getMaxXpPerLevel(tradeLevel)) {
            return;
        }

        if (UPDATE_MERCHANT_TIMER_FIELD == null || INCREASE_PROFESSION_LEVEL_ON_UPDATE_FIELD == null) {
            warnPendingLevelClearFailure(null);
            return;
        }

        try {
            UPDATE_MERCHANT_TIMER_FIELD.setInt(villager, 0);
            INCREASE_PROFESSION_LEVEL_ON_UPDATE_FIELD.setBoolean(villager, false);
        } catch (IllegalAccessException exception) {
            warnPendingLevelClearFailure(exception);
        }
    }

    private static Field villagerField(String name) {
        try {
            Field field = Villager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }

    private static void warnPendingLevelClearFailure(@Nullable Exception exception) {
        if (warnedPendingLevelClearFailure) {
            return;
        }

        warnedPendingLevelClearFailure = true;
        if (exception == null) {
            LOGGER.warn("Could not access vanilla villager trade-level timer fields; skill-based trade leveling may not block a trade level that vanilla already queued.");
        } else {
            LOGGER.warn("Could not clear vanilla villager trade-level timer after skill-based XP scaling.", exception);
        }
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
