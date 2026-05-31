package com.jvn.villagerretaliation.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class VillagerTradeUseTracker {
    private static final Map<OfferUseKey, Integer> OFFER_USES = new HashMap<>();

    private VillagerTradeUseTracker() {
    }

    public static void snapshotOffers(AbstractVillager villager) {
        MerchantOffers offers = villager.getOffers();
        for (int index = 0; index < offers.size(); index++) {
            OFFER_USES.put(new OfferUseKey(villager.getUUID(), index), offers.get(index).getUses());
        }
    }

    public static int completedTradeCount(AbstractVillager villager, MerchantOffer offer) {
        if (offer == null) {
            return 1;
        }

        OptionalInt offerIndex = offerIndex(villager, offer);
        if (offerIndex.isEmpty()) {
            return 1;
        }

        OfferUseKey key = new OfferUseKey(villager.getUUID(), offerIndex.getAsInt());
        int currentUses = offer.getUses();
        Integer previousUses = OFFER_USES.put(key, currentUses);
        if (previousUses == null) {
            return 1;
        }
        return Math.max(1, currentUses - previousUses);
    }

    public static void forget(AbstractVillager villager) {
        OFFER_USES.keySet().removeIf(key -> key.villagerId().equals(villager.getUUID()));
    }

    private static OptionalInt offerIndex(AbstractVillager villager, MerchantOffer offer) {
        MerchantOffers offers = villager.getOffers();
        for (int index = 0; index < offers.size(); index++) {
            if (offers.get(index) == offer) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    private record OfferUseKey(UUID villagerId, int offerIndex) {
    }
}
