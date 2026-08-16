package com.jvn.villagerretaliation.loot;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops;
import com.jvn.toucanlib.util.ToucanRandom;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
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
        VillagerRetaliationVillagerWeapons.ensurePickedMainHandDrop(trader, event);

        if (!VillagerRetaliationConfig.ENABLE_WANDERING_TRADER_DROPS.get()) {
            return;
        }

        RandomSource random = trader.getRandom();
        if (VillagerRetaliationConfig.WANDERER_DROP_EMERALDS.get()) {
            ToucanLivingDrops.addDrop(event, VillagerCurrencyResources.createStack(trader.level().getServer(), ToucanRandom.betweenInclusive(random, 1, 5)));
        }

        if (VillagerRetaliationConfig.WANDERER_DROP_INVISIBILITY_POTION.get()) {
            ToucanLivingDrops.addDrop(event, PotionContents.createItemStack(Items.POTION, Potions.INVISIBILITY));
        }

        if (VillagerRetaliationConfig.WANDERER_DROP_RANDOM_CURRENT_TRADE.get()
                && ToucanRandom.chance(random, VillagerRetaliationConfig.WANDERER_RANDOM_TRADE_DROP_CHANCE.get())) {
            rollTradeResult(trader, random).ifPresent(stack -> ToucanLivingDrops.addDropIfNoMatchingItem(event, stack));
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
        drop.setCount(ToucanRandom.betweenInclusive(random, 1, Math.max(1, result.getCount())));
        return java.util.Optional.of(drop);
    }
}
