package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.VillagerTradeRefreshStatePayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationTradePricing;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class VillagerTradeRefreshService {
    private static final String PENDING_REFRESHES_KEY = "VillagerRetaliationPendingTradeRefreshes";
    private static final String OFFER_INDEX_KEY = "OfferIndex";
    private static final String READY_DAY_KEY = "ReadyDay";
    private static final String TRADE_ITEM_KEY = "TradeItem";

    private VillagerTradeRefreshService() {
    }

    public static void handleRequest(ServerPlayer player, int entityId, int offerIndex) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof Villager villager)
                || !villager.isAlive()
                || villager.getTradingPlayer() != player
                || !(player.containerMenu instanceof MerchantMenu)) {
            VillagerInteractionService.sendNotice(player, entityId, "trade_refresh.unavailable");
            return;
        }

        applyReadyRefreshes(level, villager, player);
        MerchantOffers offers = villager.getOffers();
        if (!VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()
                || offerIndex < 0
                || offerIndex >= offers.size()) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.unavailable", Map.of());
            return;
        }
        if (hasPendingRefresh(villager, offerIndex)) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.already_pending", Map.of());
            return;
        }

        MerchantOffer currentOffer = offers.get(offerIndex);
        ItemStack result = currentOffer.getResult();
        if (result.isEmpty() || createReplacement(level, villager, offerIndex) == null) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.not_ready", Map.of());
            return;
        }

        String tradeItem = result.getHoverName().getString();
        scheduleRefresh(villager, offerIndex, currentDay(level) + 1L, tradeItem);
        sendState(player, villager);
        ForcedDialogueService.openTradeRefreshDialogue(
                level,
                villager,
                player,
                "trade_refresh.accept",
                Map.of("trade_item", tradeItem));
    }

    public static boolean applyReadyRefreshes(ServerLevel level, Villager villager, @Nullable ServerPlayer player) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)) {
            return false;
        }

        MerchantOffers offers = villager.getOffers();
        ListTag pending = persistentData.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND);
        ListTag remaining = new ListTag();
        Set<Integer> refreshedIndexes = new HashSet<>();
        long currentDay = currentDay(level);
        boolean changed = false;

        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            int offerIndex = entry.getInt(OFFER_INDEX_KEY);
            long readyDay = entry.getLong(READY_DAY_KEY);
            if (readyDay > currentDay) {
                remaining.add(entry.copy());
                continue;
            }
            if (offerIndex < 0 || offerIndex >= offers.size() || !refreshedIndexes.add(offerIndex)) {
                changed = true;
                continue;
            }

            MerchantOffer replacement = createReplacement(level, villager, offerIndex);
            if (replacement == null) {
                remaining.add(entry.copy());
                continue;
            }

            offers.set(offerIndex, replacement);
            changed = true;
        }

        if (remaining.isEmpty()) {
            persistentData.remove(PENDING_REFRESHES_KEY);
        } else {
            persistentData.put(PENDING_REFRESHES_KEY, remaining);
        }
        if (changed && player != null) {
            VillagerReputationTradePricing.refreshPricesForPlayer(level, villager, player);
        }
        return changed;
    }

    public static void sendState(ServerPlayer player, Villager villager) {
        PacketDistributor.sendToPlayer(player, new VillagerTradeRefreshStatePayload(
                villager.getId(),
                pendingOfferIndexes(villager)));
    }

    public static List<Integer> pendingOfferIndexes(Villager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag pending = persistentData.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND);
        List<Integer> indexes = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            int offerIndex = pending.getCompound(i).getInt(OFFER_INDEX_KEY);
            if (!indexes.contains(offerIndex)) {
                indexes.add(offerIndex);
            }
        }
        return List.copyOf(indexes);
    }

    private static void scheduleRefresh(Villager villager, int offerIndex, long readyDay, String tradeItem) {
        CompoundTag persistentData = villager.getPersistentData();
        ListTag pending = persistentData.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)
                ? persistentData.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND)
                : new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putInt(OFFER_INDEX_KEY, offerIndex);
        entry.putLong(READY_DAY_KEY, readyDay);
        entry.putString(TRADE_ITEM_KEY, tradeItem);
        pending.add(entry);
        persistentData.put(PENDING_REFRESHES_KEY, pending);
    }

    private static boolean hasPendingRefresh(Villager villager, int offerIndex) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)) {
            return false;
        }

        ListTag pending = persistentData.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            if (pending.getCompound(i).getInt(OFFER_INDEX_KEY) == offerIndex) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static MerchantOffer createReplacement(ServerLevel level, Villager villager, int offerIndex) {
        ResourceLocation professionId = VillagerProfessionUtil.id(villager.getVillagerData().getProfession());
        int villagerLevel = villager.getVillagerData().getLevel();
        return SkillTradeOfferFactory.createVillagerRefreshOffer(
                level,
                villager,
                professionId,
                villagerLevel,
                villager.getRandom(),
                currentResultItems(villager.getOffers()));
    }

    private static Set<Item> currentResultItems(MerchantOffers offers) {
        Set<Item> items = new HashSet<>();
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            ItemStack result = offer.getResult();
            if (!result.isEmpty()) {
                items.add(result.getItem());
            }
        }
        return Set.copyOf(items);
    }

    private static long currentDay(ServerLevel level) {
        return Math.floorDiv(level.getDayTime(), 24000L);
    }
}
