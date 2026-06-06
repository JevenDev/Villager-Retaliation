package com.jvn.villagerretaliation.client.trade;

import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerTradingTargetFinder;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerTradeLevelingService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.trading.MerchantOffer;

public final class VillagerTradeLevelingClientDisplay {
    private static final double PROGRESS_EPSILON = 0.000_001D;

    private VillagerTradeLevelingClientDisplay() {
    }

    public static int adjustedTradeXp(MerchantOffer offer, boolean updateProgress) {
        int vanillaXp = offer.getXp();
        if (!VillagerRetaliationConfig.ENABLE_SKILL_BASED_TRADE_LEVELING.get() || vanillaXp <= 0) {
            return vanillaXp;
        }

        AbstractVillager trader = VillagerTradingTargetFinder.findTradingVillagerOrSingleNearby(Minecraft.getInstance())
                .orElse(null);
        if (!(trader instanceof Villager villager)
                || villager.isBaby()
                || !VillagerData.canLevelUp(villager.getVillagerData().getLevel())) {
            return vanillaXp;
        }

        VillagerProfileClientCache.DisplayEntry profile =
                VillagerProfileClientCache.get(villager.getUUID(), villager.getId()).orElse(null);
        if (profile == null) {
            return vanillaXp;
        }

        VillagerSkill primarySkill = VillagerProfessionSkills.primarySkill(villager);
        double totalScaledProgress = profile.tradeLevelSkillAdjustedXpProgress()
                + vanillaXp * VillagerTradeLevelingService.tradeLevelXpMultiplier(profile.skillValue(primarySkill));
        int effectiveXp = Math.max(1, (int) Math.floor(totalScaledProgress + PROGRESS_EPSILON));
        if (updateProgress) {
            VillagerProfileClientCache.updateTradeLevelSkillAdjustedXpProgress(
                    profile.entityId(),
                    Math.max(0.0D, totalScaledProgress - effectiveXp)
            );
        }
        return Math.max(0, effectiveXp);
    }
}
