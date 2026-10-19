package com.jvn.villagerretaliation.progress;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

public final class ProgressHistoryService {
    private ProgressHistoryService() { }

    public static ResourceLocation event(String path) { return ResourceLocation.fromNamespaceAndPath("villagerretaliation", path); }

    public static void playerEvent(ServerPlayer player, String event, ResourceLocation target) {
        var level = player.serverLevel();
        var settlement = VillageAllegianceRegistrySavedData.get(level).peekAt(level, player.blockPosition()).orElse(null);
        EventHistorySavedData.get(level).record(level, player.getUUID(), null, settlement,
                event(event), target == null ? "" : target.toString(), 1, 1);
    }

    public static void trade(ServerLevel level, ServerPlayer player, AbstractVillager villager, MerchantOffer offer, int count) {
        if (offer == null || count <= 0) return;
        var town = VillageAllegianceApi.canonicalPrimary(level, villager).orElse(null);
        var data = EventHistorySavedData.get(level);
        data.record(level, player.getUUID(), villager.getUUID(), town, event("trade_completed"), "", count, count);
        for (var payment : List.of(offer.getCostA(), offer.getCostB())) {
            if (payment.isEmpty()) continue;
            long quantity = (long) count * payment.getCount();
            data.record(level, player.getUUID(), villager.getUUID(), town, event("trade_item_paid"),
                    BuiltInRegistries.ITEM.getKey(payment.getItem()).toString(), count, quantity);
            if (VillagerCurrencyResources.isCurrency(level.getServer(), payment))
                data.record(level, player.getUUID(), villager.getUUID(), town, event("trade_currency_paid"),
                        BuiltInRegistries.ITEM.getKey(payment.getItem()).toString(), count, quantity);
        }
        var result = offer.getResult();
        ExplorationHistoryService.observeItem(player, result);
        long quantity = (long) count * result.getCount();
        data.record(level, player.getUUID(), villager.getUUID(), town, event("trade_item_received"),
                BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), count, quantity);
        if (VillagerCurrencyResources.isCurrency(level.getServer(), result))
            data.record(level, player.getUUID(), villager.getUUID(), town, event("trade_currency_received"),
                    BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), count, quantity);
    }

    public static void contribution(ServerLevel level, UUID player, VillageAllegianceId town, String kind,
                                    String target, long quantity) {
        if (player != null && town != null && quantity > 0)
            EventHistorySavedData.get(level).record(level, player, null, town, event("contribution_" + kind), target, 1, quantity);
    }

    public static void itemsContributed(ServerLevel level, ServerPlayer player, VillageAllegianceId town, ItemStack stack, int count) {
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        contribution(level, player.getUUID(), town, "items", item, count);
        if (com.jvn.villagerretaliation.settlement.SettlementFoodService.isMeal(stack)) contribution(level, player.getUUID(), town, "food", item, count);
        if (stack.isDamageableItem() || stack.getItem() instanceof net.minecraft.world.item.ArmorItem)
            contribution(level, player.getUUID(), town, "equipment", item, count);
    }
}
