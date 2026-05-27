package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

public final class VillagerSpecialOrderService {
    public static final String SELECT_OPTION_PREFIX = "trade_refresh.special_order.select.";
    public static final String STATUS_OPTION_PREFIX = "trade_refresh.special_order.status.";

    private static final String ORDERS_KEY = "VillagerRetaliationSpecialOrders";
    private static final String COOLDOWNS_KEY = "VillagerRetaliationSpecialOrderCooldowns";
    private static final String PLAYER_KEY = "Player";
    private static final String OFFER_INDEX_KEY = "OfferIndex";
    private static final String TRADE_DEFINITION_KEY = "TradeDefinition";
    private static final String REQUESTED_DAY_KEY = "RequestedDay";
    private static final String READY_DAY_KEY = "ReadyDay";
    private static final String COOLDOWN_END_DAY_KEY = "CooldownEndDay";
    private static final String STATUS_KEY = "Status";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_READY = "ready";
    private static final String STATUS_APPLIED = "applied";
    private static final String STATUS_CANCELLED = "cancelled";

    private VillagerSpecialOrderService() {
    }

    public static boolean canUseSpecialOrders(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()
                || !VillagerRetaliationConfig.ENABLE_SPECIAL_ORDERS.get()) {
            return false;
        }
        VillagerReputationLevel playerLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        return meetsReputation(playerLevel, VillagerRetaliationConfig.SPECIAL_ORDER_MIN_REPUTATION.get());
    }

    public static List<SpecialOrderOption> availableOptions(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            int offerIndex) {
        if (!canUseSpecialOrders(level, villager, player)) {
            return List.of();
        }

        ResourceLocation professionId = VillagerProfessionUtil.id(villager.getVillagerData().getProfession());
        int villagerLevel = villager.getVillagerData().getLevel();
        VillagerReputationLevel playerLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        List<SkillTradeDefinition> eligibleDefinitions = new ArrayList<>();
        for (SkillTradeDefinition definition : SkillTradeResources.definitions(level.getServer())) {
            if (!isEligibleDefinition(level, villager, definition, professionId, villagerLevel, playerLevel)) {
                continue;
            }
            eligibleDefinitions.add(definition);
        }

        List<SkillTradeDefinition> uniqueDefinitions = new ArrayList<>();
        Map<Item, SkillTradeDefinition> bestDefinitionsByItem = new LinkedHashMap<>();
        Map<Item, Integer> bestCountsByItem = new HashMap<>();
        for (SkillTradeDefinition definition : eligibleDefinitions) {
            if (definition.result().items().isEmpty()) {
                uniqueDefinitions.add(definition);
                continue;
            }

            Item item = definition.result().items().getFirst();
            int count = tradeResultCount(level, villager, definition);
            int previousCount = bestCountsByItem.getOrDefault(item, -1);
            if (count > previousCount) {
                bestDefinitionsByItem.put(item, definition);
                bestCountsByItem.put(item, count);
            }
        }
        uniqueDefinitions.addAll(bestDefinitionsByItem.values());

        List<SpecialOrderOption> options = new ArrayList<>();
        for (SkillTradeDefinition definition : uniqueDefinitions) {
            options.add(SpecialOrderOption.create(
                    level,
                    villager,
                    definition,
                    effectiveWaitDays(definition),
                    effectiveCooldownDays(definition)));
        }
        options.sort(Comparator
                .comparingInt((SpecialOrderOption option) -> option.definition().request().displayPriority())
                .reversed()
                .thenComparing(SpecialOrderOption::tradeItem, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(option -> option.definition().id().toString()));
        return List.copyOf(options);
    }

    public static Optional<ResourceLocation> selectedDefinitionId(String optionId) {
        if (optionId == null || !optionId.startsWith(SELECT_OPTION_PREFIX)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(optionId.substring(SELECT_OPTION_PREFIX.length())));
    }

    public static String selectionOptionId(ResourceLocation definitionId) {
        return SELECT_OPTION_PREFIX + definitionId;
    }

    public static Optional<Integer> selectedStatusOfferIndex(String optionId) {
        if (optionId == null || !optionId.startsWith(STATUS_OPTION_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(optionId.substring(STATUS_OPTION_PREFIX.length())));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static String statusOptionId(int offerIndex) {
        return STATUS_OPTION_PREFIX + offerIndex;
    }

    public static QueueResult queue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            int offerIndex,
            ResourceLocation definitionId) {
        if (!canUseSpecialOrders(level, villager, player)) {
            return QueueResult.failed("trade_refresh.special_order_unavailable", Map.of());
        }
        int activeOrders = activeOrderCount(villager, player.getUUID());
        int maxActiveOrders = maxActiveOrders();
        if (activeOrders >= maxActiveOrders) {
            return activeOrderLimitReached(activeOrders, maxActiveOrders);
        }

        long currentDay = currentDay(level);
        pruneCooldowns(villager, currentDay);
        QueueResult cooldownResult = activeCooldown(villager, player.getUUID(), currentDay);
        if (cooldownResult != null) {
            return cooldownResult;
        }

        SpecialOrderOption option = availableOptions(level, villager, player, offerIndex)
                .stream()
                .filter(candidate -> candidate.definition().id().equals(definitionId))
                .findFirst()
                .orElse(null);
        if (option == null) {
            return QueueResult.failed("trade_refresh.special_order_unavailable", Map.of());
        }

        SpecialOrderCost cost = effectiveCost(option.definition());
        if (!cost.isEmpty() && !canPay(player, cost)) {
            return QueueResult.failed("trade_refresh.special_order_payment_missing", replacements(option, cost));
        }
        if (!cost.isEmpty()) {
            removePayment(player, cost);
        }

        long readyDay = currentDay + option.waitDays();
        long nextCooldownEndDay = currentDay + option.cooldownDays();
        CompoundTag persistentData = villager.getPersistentData();
        ListTag orders = ordersTag(persistentData);
        CompoundTag order = new CompoundTag();
        order.putUUID(PLAYER_KEY, player.getUUID());
        order.putInt(OFFER_INDEX_KEY, offerIndex);
        order.putString(TRADE_DEFINITION_KEY, definitionId.toString());
        order.putLong(REQUESTED_DAY_KEY, currentDay);
        order.putLong(READY_DAY_KEY, readyDay);
        order.putLong(COOLDOWN_END_DAY_KEY, nextCooldownEndDay);
        order.putString(STATUS_KEY, STATUS_PENDING);
        orders.add(order);
        persistentData.put(ORDERS_KEY, orders);
        setCooldown(villager, player.getUUID(), nextCooldownEndDay);
        VillagerTradeMemory.rememberDefinition(level, villager, VillagerProfessionUtil.id(villager.getVillagerData().getProfession()), definitionId);
        int queuedOrderCount = activeOrders + 1;
        Map<String, String> replacements = new HashMap<>(replacements(option, cost));
        replacements.put("active_orders", Integer.toString(queuedOrderCount));
        replacements.put("max_orders", Integer.toString(maxActiveOrders));
        return QueueResult.queued(queuedMessageKey(queuedOrderCount), replacements);
    }

    public static boolean applyReadyOrders(ServerLevel level, Villager villager, @Nullable ServerPlayer player) {
        return applyReadyOrdersDetailed(level, villager, player).changed();
    }

    public static ApplyReadyOrdersResult applyReadyOrdersDetailed(
            ServerLevel level,
            Villager villager,
            @Nullable ServerPlayer player) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(ORDERS_KEY, Tag.TAG_LIST)) {
            return new ApplyReadyOrdersResult(false, List.of());
        }

        MerchantOffers offers = villager.getOffers();
        ListTag orders = persistentData.getList(ORDERS_KEY, Tag.TAG_COMPOUND);
        ListTag remaining = new ListTag();
        Set<Integer> appliedIndexes = new HashSet<>();
        long currentDay = currentDay(level);
        boolean changed = false;
        List<String> playerReadyTradeItems = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            CompoundTag order = orders.getCompound(i);
            String status = order.getString(STATUS_KEY);
            if (!isActiveStatus(status)) {
                changed = true;
                continue;
            }
            long readyDay = order.getLong(READY_DAY_KEY);
            if (readyDay > currentDay) {
                remaining.add(order.copy());
                continue;
            }

            int offerIndex = order.getInt(OFFER_INDEX_KEY);
            ResourceLocation definitionId = ResourceLocation.tryParse(order.getString(TRADE_DEFINITION_KEY));
            if (offerIndex < 0 || offerIndex >= offers.size() || definitionId == null || !appliedIndexes.add(offerIndex)) {
                changed = true;
                continue;
            }

            Optional<SkillTradeDefinition> definition = SkillTradeResources.definition(level.getServer(), definitionId);
            if (definition.isEmpty()) {
                changed = true;
                continue;
            }

            MerchantOffer replacement = SkillTradeOfferFactory.createVillagerSpecialOrderOfferFromDefinition(
                    level,
                    villager,
                    definition.get(),
                    villager.getRandom());
            if (replacement == null) {
                changed = true;
                continue;
            }

            offers.set(offerIndex, replacement);
            if (player != null
                    && order.hasUUID(PLAYER_KEY)
                    && order.getUUID(PLAYER_KEY).equals(player.getUUID())) {
                playerReadyTradeItems.add(tradeItemName(level, villager, definition.get()));
            }
            if (order.hasUUID(PLAYER_KEY)) {
                setCooldown(villager, order.getUUID(PLAYER_KEY), order.getLong(COOLDOWN_END_DAY_KEY));
            }
            order.putString(STATUS_KEY, STATUS_APPLIED);
            VillagerTradeMemory.rememberDefinition(
                    level,
                    villager,
                    VillagerProfessionUtil.id(villager.getVillagerData().getProfession()),
                    definitionId);
            changed = true;
        }

        if (remaining.isEmpty()) {
            persistentData.remove(ORDERS_KEY);
        } else {
            persistentData.put(ORDERS_KEY, remaining);
        }
        return new ApplyReadyOrdersResult(changed, List.copyOf(playerReadyTradeItems));
    }

    public static List<Integer> pendingOfferIndexes(Villager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(ORDERS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag orders = persistentData.getList(ORDERS_KEY, Tag.TAG_COMPOUND);
        List<Integer> indexes = new ArrayList<>(orders.size());
        for (int i = 0; i < orders.size(); i++) {
            CompoundTag order = orders.getCompound(i);
            if (!isActiveStatus(order.getString(STATUS_KEY))) {
                continue;
            }
            int offerIndex = order.getInt(OFFER_INDEX_KEY);
            if (!indexes.contains(offerIndex)) {
                indexes.add(offerIndex);
            }
        }
        return List.copyOf(indexes);
    }

    public static int activeOrderCount(Villager villager, UUID playerId) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(ORDERS_KEY, Tag.TAG_LIST)) {
            return 0;
        }

        ListTag orders = persistentData.getList(ORDERS_KEY, Tag.TAG_COMPOUND);
        int count = 0;
        for (int i = 0; i < orders.size(); i++) {
            CompoundTag order = orders.getCompound(i);
            if (order.hasUUID(PLAYER_KEY)
                    && order.getUUID(PLAYER_KEY).equals(playerId)
                    && isActiveStatus(order.getString(STATUS_KEY))) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasReachedActiveOrderLimit(Villager villager, UUID playerId) {
        return activeOrderCount(villager, playerId) >= maxActiveOrders();
    }

    public static boolean hasReadyOrderForPlayer(ServerLevel level, Villager villager, UUID playerId) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(ORDERS_KEY, Tag.TAG_LIST)) {
            return false;
        }

        long currentDay = currentDay(level);
        ListTag orders = persistentData.getList(ORDERS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < orders.size(); i++) {
            CompoundTag order = orders.getCompound(i);
            if (order.hasUUID(PLAYER_KEY)
                    && order.getUUID(PLAYER_KEY).equals(playerId)
                    && isActiveStatus(order.getString(STATUS_KEY))
                    && order.getLong(READY_DAY_KEY) <= currentDay) {
                return true;
            }
        }
        return false;
    }

    public static QueueResult activeOrderLimitReached(Villager villager, UUID playerId) {
        return activeOrderLimitReached(activeOrderCount(villager, playerId), maxActiveOrders());
    }

    public static List<ActiveOrderStatus> activeOrderStatuses(ServerLevel level, Villager villager, UUID playerId) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(ORDERS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        long currentDay = currentDay(level);
        ListTag orders = persistentData.getList(ORDERS_KEY, Tag.TAG_COMPOUND);
        List<ActiveOrderStatus> statuses = new ArrayList<>(orders.size());
        for (int i = 0; i < orders.size(); i++) {
            CompoundTag order = orders.getCompound(i);
            if (!order.hasUUID(PLAYER_KEY)
                    || !order.getUUID(PLAYER_KEY).equals(playerId)
                    || !isActiveStatus(order.getString(STATUS_KEY))) {
                continue;
            }
            ResourceLocation definitionId = ResourceLocation.tryParse(order.getString(TRADE_DEFINITION_KEY));
            String tradeItem = definitionId == null
                    ? order.getString(TRADE_DEFINITION_KEY)
                    : SkillTradeResources.definition(level.getServer(), definitionId)
                            .map(definition -> tradeItemName(level, villager, definition))
                            .orElse(definitionId.toString());
            long daysRemaining = Math.max(0L, order.getLong(READY_DAY_KEY) - currentDay);
            int offerIndex = order.getInt(OFFER_INDEX_KEY);
            statuses.add(new ActiveOrderStatus(
                    offerIndex,
                    definitionId,
                    tradeItem,
                    daysRemaining,
                    timeRemainingDescription(daysRemaining),
                    orderLabel(tradeItem)));
        }
        statuses.sort(Comparator
                .comparingLong(ActiveOrderStatus::daysRemaining)
                .thenComparingInt(ActiveOrderStatus::offerIndex)
                .thenComparing(ActiveOrderStatus::tradeItem, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(statuses);
    }

    public static Optional<ActiveOrderStatus> activeOrderStatus(ServerLevel level, Villager villager, UUID playerId, int offerIndex) {
        return activeOrderStatuses(level, villager, playerId).stream()
                .filter(status -> status.offerIndex() == offerIndex)
                .findFirst();
    }

    public static boolean isOnCooldown(ServerLevel level, Villager villager, UUID playerId) {
        pruneCooldowns(villager, currentDay(level));
        return cooldownEndDay(villager, playerId) > currentDay(level);
    }

    public static Optional<QueueResult> activeCooldown(ServerLevel level, Villager villager, UUID playerId) {
        long currentDay = currentDay(level);
        pruneCooldowns(villager, currentDay);
        return Optional.ofNullable(activeCooldown(villager, playerId, currentDay));
    }

    private static boolean isEligibleDefinition(
            ServerLevel level,
            Villager villager,
            SkillTradeDefinition definition,
            ResourceLocation professionId,
            int villagerLevel,
            VillagerReputationLevel playerLevel) {
        SkillTradeRequestMetadata request = definition.request();
        if (!request.targetable()
                || !definition.matchesVillagerAtOrBelow(professionId, villagerLevel)
                || !definition.conditions().matches()) {
            return false;
        }
        if (!meetsReputation(playerLevel, effectiveMinReputation(definition))) {
            return false;
        }
        return SkillTradeOfferFactory.isSkillUnlockedForSpecialOrder(level, villager, definition);
    }

    private static VillagerReputationLevel effectiveMinReputation(SkillTradeDefinition definition) {
        VillagerReputationLevel configMin = VillagerRetaliationConfig.SPECIAL_ORDER_MIN_REPUTATION.get();
        VillagerReputationLevel definitionMin = definition.request().minReputation();
        return definitionMin.trustRank() > configMin.trustRank() ? definitionMin : configMin;
    }

    public static int maxActiveOrders() {
        return Math.clamp(VillagerRetaliationConfig.SPECIAL_ORDER_MAX_ACTIVE_PER_PLAYER.get(), 1, 3);
    }

    private static QueueResult activeOrderLimitReached(int activeOrders, int maxActiveOrders) {
        return QueueResult.failed("trade_refresh.special_order_limit_reached", Map.of(
                "active_orders", Integer.toString(activeOrders),
                "active_order_word", pluralWord(activeOrders, "request", "requests"),
                "max_order_count_word", pluralWord(maxActiveOrders, "is", "are"),
                "max_order_word", pluralWord(maxActiveOrders, "order", "orders"),
                "max_orders", Integer.toString(maxActiveOrders)));
    }

    private static QueueResult activeCooldown(Villager villager, UUID playerId, long currentDay) {
        long cooldownEndDay = cooldownEndDay(villager, playerId);
        if (cooldownEndDay <= currentDay) {
            return null;
        }
        long cooldownDays = cooldownEndDay - currentDay;
        return QueueResult.failed("trade_refresh.special_order_cooldown", Map.of(
                "cooldown_days", Long.toString(cooldownDays),
                "cooldown_day_word", pluralWord(cooldownDays, "day", "days")));
    }

    private static boolean meetsReputation(VillagerReputationLevel playerLevel, VillagerReputationLevel requiredLevel) {
        return playerLevel.trustRank() >= requiredLevel.trustRank();
    }

    private static int effectiveWaitDays(SkillTradeDefinition definition) {
        int configured = VillagerRetaliationConfig.SPECIAL_ORDER_WAIT_DAYS.get();
        int override = definition.request().waitDays();
        return Math.max(1, override > 0 ? override : configured);
    }

    private static int effectiveCooldownDays(SkillTradeDefinition definition) {
        int configured = VillagerRetaliationConfig.SPECIAL_ORDER_COOLDOWN_DAYS.get();
        int override = definition.request().cooldownDays();
        return Math.max(0, override > 0 ? override : configured);
    }

    private static SpecialOrderCost effectiveCost(SkillTradeDefinition definition) {
        return VillagerRetaliationConfig.SPECIAL_ORDER_EXTRA_COST_ENABLED.get()
                ? definition.request().extraCost()
                : SpecialOrderCost.EMPTY;
    }

    private static Map<String, String> replacements(SpecialOrderOption option, SpecialOrderCost cost) {
        return Map.of(
                "trade_item", option.tradeItem(),
                "trade_definition", option.definition().id().toString(),
                "wait_days", Integer.toString(option.waitDays()),
                "wait_day_word", pluralWord(option.waitDays(), "day", "days"),
                "cooldown_days", Integer.toString(option.cooldownDays()),
                "cooldown_day_word", pluralWord(option.cooldownDays(), "day", "days"),
                "extra_cost", costDescription(cost));
    }

    public static String pluralWord(long count, String singular, String plural) {
        return count == 1L ? singular : plural;
    }

    private static String queuedMessageKey(int activeOrders) {
        return switch (activeOrders) {
            case 1 -> "trade_refresh.special_order_queued_first";
            case 2 -> "trade_refresh.special_order_queued_second";
            case 3 -> "trade_refresh.special_order_queued_third";
            default -> "trade_refresh.special_order_queued";
        };
    }

    private static String optionLabel(SkillTradeDefinition definition, String tradeItem) {
        SpecialOrderCost cost = effectiveCost(definition);
        StringBuilder label = new StringBuilder(tradeItem);
        if (!cost.isEmpty()) {
            label.append(" - ").append(costDescription(cost));
        }
        return label.toString();
    }

    private static String orderLabel(String tradeItem) {
        return tradeItem;
    }

    private static String timeRemainingDescription(long daysRemaining) {
        if (daysRemaining <= 0L) {
            return "ready now";
        }
        return daysRemaining == 1L ? "1 more day" : daysRemaining + " more days";
    }

    private static String tradeItemName(ServerLevel level, Villager villager, SkillTradeDefinition definition) {
        if (definition.result().items().isEmpty()) {
            return definition.id().toString();
        }
        int count = tradeResultCount(level, villager, definition);
        ItemStack stack = new ItemStack(definition.result().items().getFirst(), count);
        String name = stack.getHoverName().getString();
        return count > 1 ? count + "x " + name : name;
    }

    private static int tradeResultCount(ServerLevel level, Villager villager, SkillTradeDefinition definition) {
        int skillValue = SkillTradeOfferFactory.bestSkillValue(level, villager, definition);
        SkillTradeScalingContext context = new SkillTradeScalingContext(definition, skillValue);
        return SkillTradeQualityScaler.resultCount(context, definition.result().count());
    }

    private static String costDescription(SpecialOrderCost cost) {
        if (cost == null || cost.isEmpty()) {
            return "";
        }
        ItemStack stack = new ItemStack(cost.item(), cost.count());
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }

    private static boolean canPay(ServerPlayer player, SpecialOrderCost cost) {
        if (cost.isEmpty()) {
            return true;
        }
        int count = 0;
        for (ItemStack stack : paymentStacks(player)) {
            if (stack.is(cost.item())) {
                count += stack.getCount();
            }
        }
        return count >= cost.count();
    }

    private static void removePayment(ServerPlayer player, SpecialOrderCost cost) {
        int remaining = cost.count();
        for (ItemStack stack : paymentStacks(player)) {
            if (remaining <= 0) {
                break;
            }
            if (!stack.is(cost.item())) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.getInventory().setChanged();
    }

    private static List<ItemStack> paymentStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    private static ListTag ordersTag(CompoundTag persistentData) {
        return persistentData.contains(ORDERS_KEY, Tag.TAG_LIST)
                ? persistentData.getList(ORDERS_KEY, Tag.TAG_COMPOUND)
                : new ListTag();
    }

    private static ListTag cooldownsTag(CompoundTag persistentData) {
        return persistentData.contains(COOLDOWNS_KEY, Tag.TAG_LIST)
                ? persistentData.getList(COOLDOWNS_KEY, Tag.TAG_COMPOUND)
                : new ListTag();
    }

    private static long cooldownEndDay(Villager villager, UUID playerId) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(COOLDOWNS_KEY, Tag.TAG_LIST)) {
            return 0L;
        }

        long endDay = 0L;
        ListTag cooldowns = persistentData.getList(COOLDOWNS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < cooldowns.size(); i++) {
            CompoundTag cooldown = cooldowns.getCompound(i);
            if (cooldown.hasUUID(PLAYER_KEY) && cooldown.getUUID(PLAYER_KEY).equals(playerId)) {
                endDay = Math.max(endDay, cooldown.getLong(COOLDOWN_END_DAY_KEY));
            }
        }
        return endDay;
    }

    private static void setCooldown(Villager villager, UUID playerId, long cooldownEndDay) {
        if (cooldownEndDay <= 0L) {
            return;
        }
        CompoundTag persistentData = villager.getPersistentData();
        ListTag cooldowns = cooldownsTag(persistentData);
        boolean updated = false;
        for (int i = 0; i < cooldowns.size(); i++) {
            CompoundTag cooldown = cooldowns.getCompound(i);
            if (cooldown.hasUUID(PLAYER_KEY) && cooldown.getUUID(PLAYER_KEY).equals(playerId)) {
                cooldown.putLong(COOLDOWN_END_DAY_KEY, cooldownEndDay);
                updated = true;
                break;
            }
        }
        if (!updated) {
            CompoundTag cooldown = new CompoundTag();
            cooldown.putUUID(PLAYER_KEY, playerId);
            cooldown.putLong(COOLDOWN_END_DAY_KEY, cooldownEndDay);
            cooldowns.add(cooldown);
        }
        persistentData.put(COOLDOWNS_KEY, cooldowns);
    }

    private static void pruneCooldowns(Villager villager, long currentDay) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(COOLDOWNS_KEY, Tag.TAG_LIST)) {
            return;
        }
        ListTag cooldowns = persistentData.getList(COOLDOWNS_KEY, Tag.TAG_COMPOUND);
        ListTag remaining = new ListTag();
        for (int i = 0; i < cooldowns.size(); i++) {
            CompoundTag cooldown = cooldowns.getCompound(i);
            if (cooldown.getLong(COOLDOWN_END_DAY_KEY) > currentDay) {
                remaining.add(cooldown.copy());
            }
        }
        if (remaining.isEmpty()) {
            persistentData.remove(COOLDOWNS_KEY);
        } else {
            persistentData.put(COOLDOWNS_KEY, remaining);
        }
    }

    private static boolean isActiveStatus(String status) {
        return status == null
                || status.isBlank()
                || STATUS_PENDING.equals(status)
                || STATUS_READY.equals(status);
    }

    private static long currentDay(ServerLevel level) {
        return Math.floorDiv(level.getDayTime(), 24000L);
    }

    public record SpecialOrderOption(
            SkillTradeDefinition definition,
            String label,
            String tradeItem,
            int waitDays,
            int cooldownDays) {
        private static SpecialOrderOption create(
                ServerLevel level,
                Villager villager,
                SkillTradeDefinition definition,
                int waitDays,
                int cooldownDays) {
            String tradeItem = tradeItemName(level, villager, definition);
            return new SpecialOrderOption(
                    definition,
                    optionLabel(definition, tradeItem),
                    tradeItem,
                    waitDays,
                    cooldownDays);
        }
    }

    public record ActiveOrderStatus(
            int offerIndex,
            @Nullable ResourceLocation definitionId,
            String tradeItem,
            long daysRemaining,
            String timeRemaining,
            String label) {
        public Map<String, String> replacements() {
            return Map.of(
                    "trade_item", this.tradeItem,
                    "trade_definition", this.definitionId == null ? "" : this.definitionId.toString(),
                    "offer_slot", Integer.toString(this.offerIndex + 1),
                    "days_remaining", Long.toString(this.daysRemaining),
                    "time_remaining", this.timeRemaining);
        }
    }

    public record ApplyReadyOrdersResult(boolean changed, List<String> playerReadyTradeItems) {
    }

    public record QueueResult(boolean queued, String messageKey, Map<String, String> replacements) {
        private static QueueResult queued(String messageKey, Map<String, String> replacements) {
            return new QueueResult(true, messageKey, Map.copyOf(replacements));
        }

        private static QueueResult failed(String messageKey, Map<String, String> replacements) {
            return new QueueResult(false, messageKey, Map.copyOf(replacements));
        }
    }
}
