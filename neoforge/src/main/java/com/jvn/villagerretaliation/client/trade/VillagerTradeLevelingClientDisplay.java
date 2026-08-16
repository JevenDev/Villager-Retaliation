package com.jvn.villagerretaliation.client.trade;

import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerTradingTargetFinder;
import com.jvn.villagerretaliation.skill.VillagerTradeLevelingService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.trading.MerchantOffer;

public final class VillagerTradeLevelingClientDisplay {
    private VillagerTradeLevelingClientDisplay() {
    }

    public static int adjustedTradeXp(MerchantOffer offer, boolean updateProgress) {
        int vanillaXp = offer.getXp();
        if (vanillaXp <= 0) {
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
        if (!profile.skillBasedTradeLevelingEnabled()) {
            return vanillaXp;
        }

        VillagerTradeLevelingService.TradeXpResult result = VillagerTradeLevelingService.calculateTradeLevelXp(
                vanillaXp,
                profile.tradeLevelXpMultiplier(),
                profile.tradeLevelSkillAdjustedXpProgress());
        if (updateProgress) {
            VillagerProfileClientCache.updateTradeLevelSkillAdjustedXpProgress(
                    profile.entityId(),
                    result.remainingProgress()
            );
        }
        return result.awardedXp();
    }
}
