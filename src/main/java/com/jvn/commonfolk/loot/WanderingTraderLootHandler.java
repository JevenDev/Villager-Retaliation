package com.jvn.commonfolk.loot;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.util.CommonfolkLootUtil;
import com.jvn.commonfolk.util.CommonfolkRandomUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class WanderingTraderLootHandler {
    private WanderingTraderLootHandler() {
    }

    public static void addDrops(WanderingTrader trader, LivingDropsEvent event) {
        if (!CommonfolkConfig.ENABLE_WANDERING_TRADER_DROPS.get()) {
            return;
        }

        RandomSource random = trader.getRandom();
        if (CommonfolkConfig.WANDERER_DROP_EMERALDS.get()) {
            CommonfolkLootUtil.addDrop(event, new ItemStack(Items.EMERALD, CommonfolkRandomUtil.between(random, 1, 5)));
        }

        if (CommonfolkConfig.WANDERER_DROP_INVISIBILITY_POTION.get()) {
            CommonfolkLootUtil.addDrop(event, PotionContents.createItemStack(Items.POTION, Potions.INVISIBILITY));
        }

        if (CommonfolkConfig.WANDERER_DROP_RANDOM_CURRENT_TRADE.get()
                && CommonfolkRandomUtil.chance(random, CommonfolkConfig.WANDERER_RANDOM_TRADE_DROP_CHANCE.get())) {
            rollTradeResult(trader, random).ifPresent(stack -> CommonfolkLootUtil.addDropIfNoMatchingItem(event, stack));
        }
    }

    private static java.util.Optional<ItemStack> rollTradeResult(WanderingTrader trader, RandomSource random) {
        MerchantOffers offers = trader.getOffers();
        if (offers.isEmpty()) {
            return java.util.Optional.empty();
        }

        MerchantOffer offer = offers.get(random.nextInt(offers.size()));
        ItemStack result = offer.getResult();
        if (result.isEmpty()) {
            return java.util.Optional.empty();
        }

        ItemStack drop = result.copy();
        drop.setCount(CommonfolkRandomUtil.between(random, 1, Math.max(1, result.getCount())));
        return java.util.Optional.of(drop);
    }
}
