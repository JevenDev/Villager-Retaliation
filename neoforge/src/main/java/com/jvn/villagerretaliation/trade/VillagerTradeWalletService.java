package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class VillagerTradeWalletService {
    private static final String WALLET_STOCK_TAG = "VillagerRetaliationWalletTradeStock";
    private static final String INDEX_TAG = "OfferIndex";
    private static final String USES_TAG = "Uses";

    private VillagerTradeWalletService() {
    }

    public static void refreshWalletStock(ServerLevel level, Villager villager) {
        if (level == null || villager == null || !villager.isAlive()) {
            return;
        }
        VillagerWalletService.initializeWalletIfNeeded(villager);
        MerchantOffers offers = villager.getOffers();
        if (VillagerWalletService.hasUnlimitedCurrency()) {
            restoreAllWalletStock(villager, offers);
            return;
        }
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            int payout = emeraldCount(offer.getResult());
            if (payout <= 0) {
                restoreWalletStock(villager, offer, index);
                continue;
            }
            if (VillagerWalletService.canSpendEmeralds(villager, payout)) {
                restoreWalletStock(villager, offer, index);
            } else {
                markWalletOutOfStock(villager, offer, index);
            }
        }
    }

    public static void syncOffers(ServerPlayer player, Villager villager) {
        if (player == null || villager == null || !(player.containerMenu instanceof MerchantMenu menu)) {
            return;
        }
        menu.setOffers(villager.getOffers());
        player.sendMerchantOffers(
                menu.containerId,
                villager.getOffers(),
                villager.getVillagerData().getLevel(),
                villager.getVillagerXp(),
                villager.showProgressBar(),
                villager.canRestock()
        );
    }

    public static void onTradeCompleted(ServerLevel level, Villager villager, MerchantOffer offer) {
        if (level == null || villager == null || offer == null) {
            return;
        }
        restoreWalletStock(villager, offer, indexOf(villager.getOffers(), offer));

        int paidToVillager = emeraldCount(offer.getCostA()) + emeraldCount(offer.getCostB());
        if (paidToVillager > 0) {
            VillagerWalletService.addEmeralds(villager, paidToVillager, VillagerWalletService.WalletSource.TRADE_PAYMENT);
        }

        int paidByVillager = emeraldCount(offer.getResult());
        if (paidByVillager > 0) {
            VillagerWalletService.spendEmeralds(villager, paidByVillager, VillagerWalletService.WalletSource.TRADE_PAYOUT);
        }
        refreshWalletStock(level, villager);
    }

    public static void restoreWalletStock(Villager villager, int index) {
        if (villager == null || index < 0 || index >= villager.getOffers().size()) {
            return;
        }
        restoreWalletStock(villager, villager.getOffers().get(index), index);
    }

    private static void restoreAllWalletStock(Villager villager, MerchantOffers offers) {
        for (int index = 0; index < offers.size(); index++) {
            restoreWalletStock(villager, offers.get(index), index);
        }
    }

    private static void markWalletOutOfStock(Villager villager, MerchantOffer offer, int index) {
        if (index < 0 || isWalletStockOverlayed(villager, index)) {
            return;
        }
        rememberUses(villager, index, offer.getUses());
        offer.setToOutOfStock();
    }

    private static void restoreWalletStock(Villager villager, MerchantOffer offer, int index) {
        if (index < 0) {
            return;
        }
        Integer rememberedUses = forgetUses(villager, index);
        if (rememberedUses == null) {
            return;
        }
        offer.resetUses();
        for (int i = 0; i < rememberedUses; i++) {
            offer.increaseUses();
        }
    }

    private static int emeraldCount(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.EMERALD) ? stack.getCount() : 0;
    }

    private static int indexOf(MerchantOffers offers, MerchantOffer target) {
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isWalletStockOverlayed(Villager villager, int index) {
        ListTag entries = walletStockEntries(villager);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.getCompound(i).getInt(INDEX_TAG) == index) {
                return true;
            }
        }
        return false;
    }

    private static void rememberUses(Villager villager, int index, int uses) {
        ListTag entries = walletStockEntries(villager);
        CompoundTag entry = new CompoundTag();
        entry.putInt(INDEX_TAG, index);
        entry.putInt(USES_TAG, Math.max(0, uses));
        entries.add(entry);
        villager.getPersistentData().put(WALLET_STOCK_TAG, entries);
    }

    private static Integer forgetUses(Villager villager, int index) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(WALLET_STOCK_TAG, Tag.TAG_LIST)) {
            return null;
        }
        ListTag entries = data.getList(WALLET_STOCK_TAG, Tag.TAG_COMPOUND);
        ListTag remaining = new ListTag();
        Integer rememberedUses = null;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.getInt(INDEX_TAG) == index && rememberedUses == null) {
                rememberedUses = Math.max(0, entry.getInt(USES_TAG));
            } else {
                remaining.add(entry.copy());
            }
        }
        if (remaining.isEmpty()) {
            data.remove(WALLET_STOCK_TAG);
        } else {
            data.put(WALLET_STOCK_TAG, remaining);
        }
        return rememberedUses;
    }

    private static ListTag walletStockEntries(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        return data.contains(WALLET_STOCK_TAG, Tag.TAG_LIST)
                ? data.getList(WALLET_STOCK_TAG, Tag.TAG_COMPOUND)
                : new ListTag();
    }
}
