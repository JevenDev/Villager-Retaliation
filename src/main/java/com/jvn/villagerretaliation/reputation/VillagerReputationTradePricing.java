package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;

public final class VillagerReputationTradePricing {
    private VillagerReputationTradePricing() {
    }

    public static void refreshPricesForPlayer(ServerLevel level, Villager villager, Player player) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !VillagerRetaliationConfig.ENABLE_REPUTATION_TRADE_PRICING.get()
                || villager.getOffers().isEmpty()) {
            return;
        }

        int modReputation = VillagerReputationManager.getReputation(level, villager, player.getUUID());
        int scaledModReputation = Mth.floor((float) (modReputation * VillagerRetaliationConfig.REPUTATION_TRADE_PRICE_SCALE.get()));
        VillagerReputationLevel modReputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        int tierReputationEquivalent = resolveTierReputationEquivalent(modReputationLevel, scaledModReputation);

        for (MerchantOffer offer : villager.getOffers()) {
            offer.resetSpecialPriceDiff();
            if (tierReputationEquivalent != 0) {
                offer.addToSpecialPriceDiff(-Mth.floor(tierReputationEquivalent * offer.getPriceMultiplier()));
            }
            applyHeroDiscount(player, offer);
        }

        if (player instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof MerchantMenu menu) {
            menu.setOffers(villager.getOffers());
            serverPlayer.sendMerchantOffers(
                    menu.containerId,
                    villager.getOffers(),
                    villager.getVillagerData().getLevel(),
                    villager.getVillagerXp(),
                    villager.showProgressBar(),
                    villager.canRestock()
            );
        }
    }

    private static int resolveTierReputationEquivalent(VillagerReputationLevel level, int scaledReputation) {
        int tierReputation = level.tradeReputationEquivalent();
        if (tierReputation > 0) {
            return Math.max(tierReputation, scaledReputation);
        }
        if (tierReputation < 0) {
            return Math.min(tierReputation, scaledReputation);
        }
        return scaledReputation;
    }

    private static void applyHeroDiscount(Player player, MerchantOffer offer) {
        if (!player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            return;
        }

        MobEffectInstance effect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (effect == null) {
            return;
        }

        double discountRatio = 0.3D + 0.0625D * effect.getAmplifier();
        int discount = (int) Math.floor(discountRatio * offer.getBaseCostA().getCount());
        offer.addToSpecialPriceDiff(-Math.max(discount, 1));
    }
}
