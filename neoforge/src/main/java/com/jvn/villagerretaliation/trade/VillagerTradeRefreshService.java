package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.VillagerTradeRefreshStatePayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationTradePricing;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class VillagerTradeRefreshService {
    private static final String PENDING_REFRESHES_KEY = "VillagerRetaliationPendingTradeRefreshes";
    private static final String PLAYER_KEY = "Player";
    private static final String OFFER_INDEX_KEY = "OfferIndex";
    private static final String READY_DAY_KEY = "ReadyDay";
    private static final String TRADE_ITEM_KEY = "TradeItem";
    private static final String TRADE_DEFINITION_KEY = "TradeDefinition";
    private static final String OFFER_SEED_KEY = "OfferSeed";

    private VillagerTradeRefreshService() {
    }

    public static void handleRequest(ServerPlayer player, int entityId, int offerIndex) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof Villager villager)
                || !villager.isAlive()
                || VillagerDownedService.isDowned(villager)
                || villager.getTradingPlayer() != player
                || !(player.containerMenu instanceof MerchantMenu)) {
            VillagerInteractionService.sendNotice(player, entityId, "trade_refresh.unavailable");
            return;
        }

        reconcile(level, villager);
        VillagerSpecialOrderService.deliverRefunds(player, villager);
        applyReadyRefreshes(level, villager, player);
        MerchantOffers offers = villager.getOffers();
        if (!VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()
                || offerIndex < 0
                || offerIndex >= offers.size()) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.unavailable", Map.of());
            return;
        }
        if (hasPendingRefresh(villager, offerIndex)
                || VillagerSpecialOrderService.pendingOfferIndexes(villager).contains(offerIndex)) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.already_pending", Map.of());
            return;
        }

        MerchantOffer currentOffer = offers.get(offerIndex);
        ItemStack result = currentOffer.getResult();
        if (result.isEmpty()) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.not_ready", Map.of());
            return;
        }

        String tradeItem = result.getHoverName().getString();
        if (VillagerSpecialOrderService.canUseSpecialOrders(level, villager, player)) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshChoiceDialogue(level, villager, player, offerIndex, tradeItem);
            return;
        }

        scheduleRandomRefresh(level, villager, player, offerIndex, tradeItem);
    }

    public static void handleSurpriseRequest(ServerPlayer player, Villager villager, int offerIndex) {
        if (!(player.level() instanceof ServerLevel level) || !villager.isAlive()) {
            return;
        }
        reconcile(level, villager);
        VillagerSpecialOrderService.deliverRefunds(player, villager);
        MerchantOffers offers = villager.getOffers();
        if (!VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()
                || offerIndex < 0
                || offerIndex >= offers.size()) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.unavailable", Map.of());
            return;
        }

        if (hasPendingRefresh(villager, offerIndex)
                || VillagerSpecialOrderService.pendingOfferIndexes(villager).contains(offerIndex)) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.already_pending", Map.of());
            return;
        }

        MerchantOffer currentOffer = offers.get(offerIndex);
        ItemStack result = currentOffer.getResult();
        if (result.isEmpty()) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.not_ready", Map.of());
            return;
        }

        scheduleRandomRefresh(level, villager, player, offerIndex, result.getHoverName().getString());
    }

    public static VillagerSpecialOrderService.QueueResult queueSpecialOrder(
            ServerPlayer player,
            Villager villager,
            int offerIndex,
            ResourceLocation definitionId) {
        if (!(player.level() instanceof ServerLevel level)
                || !villager.isAlive()
                || definitionId == null) {
            return new VillagerSpecialOrderService.QueueResult(false, "trade_refresh.special_order_unavailable", Map.of());
        }

        reconcile(level, villager);
        VillagerSpecialOrderService.deliverRefunds(player, villager);

        MerchantOffers offers = villager.getOffers();
        if (!VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()
                || offerIndex < 0
                || offerIndex >= offers.size()
                || offers.get(offerIndex).getResult().isEmpty()) {
            sendState(player, villager);
            return new VillagerSpecialOrderService.QueueResult(false, "trade_refresh.special_order_unavailable", Map.of());
        }
        if (hasPendingRefresh(villager, offerIndex)
                || VillagerSpecialOrderService.pendingOfferIndexes(villager).contains(offerIndex)) {
            sendState(player, villager);
            return new VillagerSpecialOrderService.QueueResult(false, "trade_refresh.special_order_pending", Map.of());
        }

        VillagerSpecialOrderService.QueueResult result = VillagerSpecialOrderService.queue(level, villager, player, offerIndex, definitionId);
        sendState(player, villager);
        return result;
    }

    private static void scheduleRandomRefresh(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            int offerIndex,
            String tradeItem) {
        Optional<VillagerSpecialOrderService.QueueResult> limitResult =
                activeRequestLimitReached(villager, player.getUUID());
        if (limitResult.isPresent()) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(
                    level,
                    villager,
                    player,
                    limitResult.get().messageKey(),
                    limitResult.get().replacements());
            return;
        }
        SkillTradeOfferFactory.RequestSelection selection = selectReplacement(level, villager, offerIndex);
        if (selection == null) {
            sendState(player, villager);
            ForcedDialogueService.openTradeRefreshDialogue(level, villager, player, "trade_refresh.not_ready", Map.of());
            return;
        }

        ResourceLocation professionId = VillagerProfessionUtil.id(villager.getVillagerData().getProfession());
        SkillTradeOfferFactory.consumeRequestSelection(
                level, villager, professionId, selection.definition().id());
        scheduleRefresh(
                villager,
                player.getUUID(),
                offerIndex,
                currentDay(level) + 1L,
                tradeItem,
                selection.definition().id(),
                selection.offerSeed());
        sendState(player, villager);
        ForcedDialogueService.openTradeRefreshDialogue(
                level,
                villager,
                player,
                "trade_refresh.accept",
                Map.of("trade_item", tradeItem));
    }

    private static int activeRequestCount(Villager villager, UUID playerId) {
        return activeRandomRefreshCount(villager, playerId) + VillagerSpecialOrderService.activeOrderCount(villager, playerId);
    }

    public static Optional<VillagerSpecialOrderService.QueueResult> activeRequestLimitReached(
            Villager villager,
            UUID playerId) {
        int activeRequests = activeRequestCount(villager, playerId);
        int maxActiveRequests = VillagerSpecialOrderService.maxActiveOrders();
        if (activeRequests < maxActiveRequests) {
            return Optional.empty();
        }
        return Optional.of(new VillagerSpecialOrderService.QueueResult(
                false,
                "trade_refresh.request_limit_reached",
                activeRequestLimitReplacements(activeRequests, maxActiveRequests)));
    }

    private static Map<String, String> activeRequestLimitReplacements(int activeRequests, int maxActiveRequests) {
        return Map.of(
                "active_orders", Integer.toString(activeRequests),
                "active_order_word", VillagerSpecialOrderService.pluralWord(activeRequests, "request", "requests"),
                "max_order_count_word", VillagerSpecialOrderService.pluralWord(maxActiveRequests, "is", "are"),
                "max_order_word", VillagerSpecialOrderService.pluralWord(maxActiveRequests, "request", "requests"),
                "max_orders", Integer.toString(maxActiveRequests));
    }

    public static boolean applyReadyRefreshes(ServerLevel level, Villager villager, @Nullable ServerPlayer player) {
        return applyReadyRefreshesDetailed(level, villager, player).changed();
    }

    public static ReadyRefreshResult applyReadyRefreshesDetailed(
            ServerLevel level,
            Villager villager,
            @Nullable ServerPlayer player) {
        boolean reconciled = reconcile(level, villager);
        VillagerSpecialOrderService.ApplyReadyOrdersResult specialOrderResult =
                VillagerSpecialOrderService.applyReadyOrdersDetailed(level, villager, player);
        boolean changed = reconciled || specialOrderResult.changed();
        List<String> playerReadyTradeItems = new ArrayList<>(specialOrderResult.playerReadyTradeItems());
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)) {
            if (changed && player != null) {
                VillagerReputationTradePricing.refreshPricesForPlayer(level, villager, player);
            }
            return new ReadyRefreshResult(changed, List.copyOf(playerReadyTradeItems));
        }

        MerchantOffers offers = villager.getOffers();
        ListTag pending = persistentData.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND);
        ListTag remaining = new ListTag();
        Set<Integer> refreshedIndexes = new HashSet<>();
        long currentDay = currentDay(level);

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

            MerchantOffer replacement = createPersistedReplacement(level, villager, entry);
            if (replacement == null) {
                // Invalid or removed definitions cancel independently instead of pinning the slot forever.
                changed = true;
                continue;
            }

            VillagerTradeWalletService.restoreWalletStock(villager, offerIndex);
            offers.set(offerIndex, replacement);
            if (player != null
                    && entry.hasUUID(PLAYER_KEY)
                    && entry.getUUID(PLAYER_KEY).equals(player.getUUID())) {
                String tradeItem = entry.getString(TRADE_ITEM_KEY);
                playerReadyTradeItems.add(tradeItem.isBlank() ? replacement.getResult().getHoverName().getString() : tradeItem);
            }
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
        return new ReadyRefreshResult(changed, List.copyOf(playerReadyTradeItems));
    }

    public static boolean hasReadyRefreshesForPlayer(ServerLevel level, Villager villager, ServerPlayer player) {
        reconcile(level, villager);
        if (VillagerSpecialOrderService.hasReadyOrderForPlayer(level, villager, player.getUUID())) {
            return true;
        }

        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)) {
            return false;
        }

        long currentDay = currentDay(level);
        ListTag pending = persistentData.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (entry.hasUUID(PLAYER_KEY)
                    && entry.getUUID(PLAYER_KEY).equals(player.getUUID())
                    && entry.getLong(READY_DAY_KEY) <= currentDay) {
                return true;
            }
        }
        return false;
    }

    public static void sendState(ServerPlayer player, Villager villager) {
        reconcile(player.serverLevel(), villager);
        PacketDistributor.sendToPlayer(player, new VillagerTradeRefreshStatePayload(
                villager.getId(),
                pendingOfferIndexes(villager)));
    }

    public static List<Integer> pendingOfferIndexes(Villager villager) {
        List<Integer> specialOrderIndexes = VillagerSpecialOrderService.pendingOfferIndexes(villager);
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)) {
            return specialOrderIndexes;
        }

        ListTag pending = persistentData.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND);
        List<Integer> indexes = new ArrayList<>(pending.size() + specialOrderIndexes.size());
        for (int i = 0; i < pending.size(); i++) {
            int offerIndex = pending.getCompound(i).getInt(OFFER_INDEX_KEY);
            if (!indexes.contains(offerIndex)) {
                indexes.add(offerIndex);
            }
        }
        for (int offerIndex : specialOrderIndexes) {
            if (!indexes.contains(offerIndex)) {
                indexes.add(offerIndex);
            }
        }
        return List.copyOf(indexes);
    }

    private static void scheduleRefresh(
            Villager villager,
            UUID playerId,
            int offerIndex,
            long readyDay,
            String tradeItem,
            ResourceLocation definitionId,
            long offerSeed) {
        CompoundTag persistentData = villager.getPersistentData();
        ListTag pending = persistentData.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)
                ? persistentData.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND)
                : new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID(PLAYER_KEY, playerId);
        entry.putInt(OFFER_INDEX_KEY, offerIndex);
        entry.putLong(READY_DAY_KEY, readyDay);
        entry.putString(TRADE_ITEM_KEY, tradeItem);
        entry.putString(TRADE_DEFINITION_KEY, definitionId.toString());
        entry.putLong(OFFER_SEED_KEY, offerSeed);
        pending.add(entry);
        persistentData.put(PENDING_REFRESHES_KEY, pending);
    }

    private static int activeRandomRefreshCount(Villager villager, UUID playerId) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)) {
            return 0;
        }

        ListTag pending = persistentData.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND);
        int count = 0;
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (entry.hasUUID(PLAYER_KEY) && entry.getUUID(PLAYER_KEY).equals(playerId)) {
                count++;
            }
        }
        return count;
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

    public static Set<ResourceLocation> pendingDefinitionIds(Villager villager) {
        Set<ResourceLocation> ids = new HashSet<>();
        CompoundTag data = villager.getPersistentData();
        if (data.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)) {
            ListTag pending = data.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < pending.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(pending.getCompound(i).getString(TRADE_DEFINITION_KEY));
                if (id != null) ids.add(id);
            }
        }
        ids.addAll(VillagerSpecialOrderService.pendingDefinitionIds(villager));
        return Set.copyOf(ids);
    }

    public static boolean reconcile(ServerLevel level, Villager villager) {
        boolean changed = VillagerSpecialOrderService.reconcile(level, villager);
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(PENDING_REFRESHES_KEY, Tag.TAG_LIST)) return changed;
        ListTag pending = data.getList(PENDING_REFRESHES_KEY, Tag.TAG_COMPOUND);
        ListTag remaining = new ListTag();
        int offerCount = villager.getOffers().size();
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            int offerIndex = entry.getInt(OFFER_INDEX_KEY);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(TRADE_DEFINITION_KEY));
            boolean legacy = id == null && !entry.contains(OFFER_SEED_KEY, Tag.TAG_LONG);
            if (offerIndex < 0 || offerIndex >= offerCount
                    || (!legacy && (id == null || SkillTradeResources.definition(level.getServer(), id).isEmpty()))) {
                changed = true;
                continue;
            }
            remaining.add(entry.copy());
        }
        if (remaining.isEmpty()) data.remove(PENDING_REFRESHES_KEY); else data.put(PENDING_REFRESHES_KEY, remaining);
        return changed;
    }

    @Nullable
    private static SkillTradeOfferFactory.RequestSelection selectReplacement(
            ServerLevel level, Villager villager, int offerIndex) {
        ResourceLocation professionId = VillagerProfessionUtil.id(villager.getVillagerData().getProfession());
        int villagerLevel = villager.getVillagerData().getLevel();
        Set<ResourceLocation> reserved = pendingDefinitionIds(villager);
        SkillTradeOfferFactory.RequestSelection selection = SkillTradeOfferFactory.selectVillagerRefreshRequest(
                level,
                villager,
                professionId,
                villagerLevel,
                villager.getRandom(),
                currentResultStacks(villager.getOffers()),
                reserved);
        return selection;
    }

    @Nullable
    private static MerchantOffer createPersistedReplacement(
            ServerLevel level, Villager villager, CompoundTag entry) {
        ResourceLocation definitionId = ResourceLocation.tryParse(entry.getString(TRADE_DEFINITION_KEY));
        if (definitionId == null || !entry.contains(OFFER_SEED_KEY, Tag.TAG_LONG)) {
            // Legacy pending requests are resolved once on their first due reconciliation.
            SkillTradeOfferFactory.RequestSelection migrated = selectReplacement(
                    level, villager, entry.getInt(OFFER_INDEX_KEY));
            if (migrated == null) return null;
            ResourceLocation professionId = VillagerProfessionUtil.id(villager.getVillagerData().getProfession());
            SkillTradeOfferFactory.consumeRequestSelection(level, villager, professionId, migrated.definition().id());
            return migrated.offer();
        }
        SkillTradeDefinition definition = SkillTradeResources.definition(level.getServer(), definitionId).orElse(null);
        if (definition == null || !definition.conditions().matches()) return null;
        ResourceLocation professionId = VillagerProfessionUtil.id(villager.getVillagerData().getProfession());
        if (!definition.matchesVillager(professionId, villager.getVillagerData().getLevel())) return null;
        return SkillTradeOfferFactory.createVillagerOfferFromDefinition(
                level,
                villager,
                definition,
                net.minecraft.util.RandomSource.create(entry.getLong(OFFER_SEED_KEY)),
                List.of());
    }

    private static List<ItemStack> currentResultStacks(MerchantOffers offers) {
        List<ItemStack> stacks = new ArrayList<>(offers.size());
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            ItemStack result = offer.getResult();
            if (!result.isEmpty()) {
                stacks.add(result.copy());
            }
        }
        return List.copyOf(stacks);
    }

    private static long currentDay(ServerLevel level) {
        return Math.floorDiv(level.getDayTime(), 24000L);
    }

    public record ReadyRefreshResult(boolean changed, List<String> playerReadyTradeItems) {
        public boolean hasPlayerReadyTrades() {
            return !this.playerReadyTradeItems.isEmpty();
        }

        public Map<String, String> replacements() {
            int tradeCount = this.playerReadyTradeItems.size();
            String tradeItems = naturalJoin(this.playerReadyTradeItems);
            String restockedSummary = tradeCount == 1
                    ? tradeItems
                    : "the " + tradeCount + " trades you wanted";
            return Map.of(
                    "trade_count", Integer.toString(tradeCount),
                    "trade_word", tradeCount == 1 ? "trade" : "trades",
                    "trade_items", tradeItems,
                    "restocked_summary", restockedSummary);
        }

        private static String naturalJoin(List<String> values) {
            if (values.isEmpty()) {
                return "";
            }
            if (values.size() == 1) {
                return values.getFirst();
            }
            if (values.size() == 2) {
                return values.get(0) + " and " + values.get(1);
            }
            return values.subList(0, values.size() - 1).stream().collect(Collectors.joining(", "))
                    + ", and "
                    + values.getLast();
        }
    }
}
