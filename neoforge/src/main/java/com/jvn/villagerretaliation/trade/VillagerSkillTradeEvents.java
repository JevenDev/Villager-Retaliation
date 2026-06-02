package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import org.jetbrains.annotations.Nullable;

public final class VillagerSkillTradeEvents {
    private VillagerSkillTradeEvents() {
    }

    public static void onVillagerTrades(VillagerTradesEvent event) {
        ResourceLocation professionId = VillagerProfessionUtil.id(event.getType());
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        for (int level = 1; level <= 5; level++) {
            add(trades, level, new SkillTradeListing(professionId, level));
        }
    }

    public static void onWandererTrades(WandererTradesEvent event) {
        addIfAbsent(event.getGenericTrades(), new WanderingTraderSkillTradeListing(SkillTradePool.WANDERING_TRADER_GENERIC));
        addIfAbsent(event.getRareTrades(), new WanderingTraderSkillTradeListing(SkillTradePool.WANDERING_TRADER_RARE));
    }

    private static void add(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades, int level, VillagerTrades.ItemListing listing) {
        List<VillagerTrades.ItemListing> mutable = new ArrayList<>();
        List<VillagerTrades.ItemListing> existing = trades.get(level);
        if (existing != null) {
            if (existing.contains(listing)) {
                return;
            }
            mutable.addAll(existing);
        }
        mutable.add(listing);
        trades.put(level, mutable);
    }

    private static void addIfAbsent(List<VillagerTrades.ItemListing> listings, VillagerTrades.ItemListing listing) {
        if (!listings.contains(listing)) {
            listings.add(listing);
        }
    }

    private record SkillTradeListing(ResourceLocation professionId, int villagerLevel) implements VillagerTrades.ItemListing {
        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            if (!(trader instanceof AbstractVillager villager) || !(villager.level() instanceof ServerLevel level)) {
                return null;
            }
            return SkillTradeOfferFactory.createVillagerOffer(level, villager, this.professionId, this.villagerLevel, random);
        }
    }

    private record WanderingTraderSkillTradeListing(SkillTradePool pool) implements VillagerTrades.ItemListing {
        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            if (!(trader instanceof AbstractVillager villager) || !(villager.level() instanceof ServerLevel level)) {
                return null;
            }
            return SkillTradeOfferFactory.createWanderingTraderOffer(level, villager, this.pool, random);
        }
    }
}
