package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class SettlementEconomy {
    public static final int MAX_ORDERS = 64;
    private final Runnable dirty;
    private final Map<UUID, SettlementTradeOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, CurrencyAmount> orderSpending = new LinkedHashMap<>();
    private final Map<ResourceLocation, MarketVolume> markets = new LinkedHashMap<>();
    private Map<UUID, SettlementTradeService.Result> lastResults = Map.of();
    private CurrencyAmount balance = CurrencyAmount.ZERO;
    private CurrencyAmount reserve = CurrencyAmount.ZERO;
    private CurrencyAmount dailyBudget = CurrencyAmount.ZERO;
    private CurrencyAmount spent = CurrencyAmount.ZERO;
    private long period = -1L;
    private long lastTradeAt = -1L;

    SettlementEconomy(Runnable dirty) {
        this.dirty = dirty;
    }

    public Map<UUID, SettlementTradeService.Result> lastResults() {
        return this.lastResults;
    }

    void recordResults(Map<UUID, SettlementTradeService.Result> results) {
        this.lastResults = Map.copyOf(results);
    }

    public CurrencyAmount balance() {
        return this.balance;
    }

    public CurrencyAmount reserve() {
        return this.reserve;
    }

    public CurrencyAmount dailyBudget() {
        return this.dailyBudget;
    }

    public CurrencyAmount spent() {
        return this.spent;
    }

    public long period() {
        return this.period;
    }

    public Map<UUID, SettlementTradeOrder> orders() {
        return Collections.unmodifiableMap(this.orders);
    }

    public CurrencyAmount orderSpending(UUID id) {
        return this.orderSpending.getOrDefault(id, CurrencyAmount.ZERO);
    }

    public MarketVolume market(ResourceLocation group) {
        return this.markets.getOrDefault(group, new MarketVolume(0, 0));
    }

    void credit(CurrencyAmount amount) {
        this.balance = this.balance.add(amount);
        this.dirty.run();
    }

    boolean withdraw(CurrencyAmount amount) {
        if (this.balance.compareTo(amount) < 0) {
            return false;
        }
        this.balance = this.balance.subtract(amount);
        this.dirty.run();
        return true;
    }

    boolean configureBudget(CurrencyAmount reserve, CurrencyAmount dailyBudget) {
        if (reserve == null || dailyBudget == null
                || reserve.compareTo(CurrencyAmount.of(1_000_000_000L, 1L)) > 0
                || dailyBudget.compareTo(CurrencyAmount.of(1_000_000L, 1L)) > 0) {
            return false;
        }
        this.reserve = reserve;
        this.dailyBudget = dailyBudget;
        this.dirty.run();
        return true;
    }

    boolean putOrder(SettlementTradeOrder order) {
        if (order == null || !this.orders.containsKey(order.id()) && this.orders.size() >= MAX_ORDERS
                || this.orders.values().stream().anyMatch(existing ->
                        !existing.id().equals(order.id()) && existing.item().equals(order.item()))) {
            return false;
        }
        this.orders.put(order.id(), order);
        this.dirty.run();
        return true;
    }

    boolean removeOrder(UUID id) {
        if (this.orders.remove(id) == null) {
            return false;
        }
        this.orderSpending.remove(id);
        this.dirty.run();
        return true;
    }

    boolean beginTradeCycle(long gameTime) {
        if (this.lastTradeAt >= 0L && gameTime - this.lastTradeAt < 200L) {
            return false;
        }
        long currentPeriod = Math.floorDiv(Math.max(0L, gameTime), 24_000L);
        if (currentPeriod > this.period) {
            this.period = currentPeriod;
            this.spent = CurrencyAmount.ZERO;
            this.orderSpending.clear();
            this.markets.clear();
        }
        this.lastTradeAt = gameTime;
        this.dirty.run();
        return true;
    }

    public CurrencyAmount availableBudget(SettlementTradeOrder order) {
        return this.balance.subtractClamped(this.reserve)
                .min(this.dailyBudget.subtractClamped(this.spent))
                .min(order.dailyBudget().subtractClamped(orderSpending(order.id())));
    }

    void recordPurchase(SettlementTradeOrder order, ResourceLocation group, int count, CurrencyAmount cost) {
        if (count <= 0 || cost.compareTo(availableBudget(order)) > 0) {
            throw new IllegalStateException("Validated settlement purchase exceeded its budget");
        }
        this.balance = this.balance.subtract(cost);
        this.spent = this.spent.add(cost);
        this.orderSpending.put(order.id(), orderSpending(order.id()).add(cost));
        MarketVolume volume = market(group);
        this.markets.put(group, new MarketVolume(Math.addExact(volume.bought(), count), volume.sold()));
        this.dirty.run();
    }

    void recordSale(ResourceLocation group, int count, CurrencyAmount income) {
        if (count <= 0) {
            throw new IllegalArgumentException("Settlement sale quantity must be positive");
        }
        this.balance = this.balance.add(income);
        MarketVolume volume = market(group);
        this.markets.put(group, new MarketVolume(volume.bought(), Math.addExact(volume.sold(), count)));
        this.dirty.run();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Balance", this.balance.save());
        tag.put("Reserve", this.reserve.save());
        tag.put("DailyBudget", this.dailyBudget.save());
        tag.put("Spent", this.spent.save());
        tag.putLong("Period", this.period);
        tag.putLong("LastTradeAt", this.lastTradeAt);
        ListTag orders = new ListTag();
        this.orders.values().forEach(order -> {
            CompoundTag entry = order.save();
            entry.put("Spent", orderSpending(order.id()).save());
            orders.add(entry);
        });
        tag.put("Orders", orders);
        ListTag markets = new ListTag();
        this.markets.forEach((group, volume) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Group", group.toString());
            entry.putInt("Bought", volume.bought());
            entry.putInt("Sold", volume.sold());
            markets.add(entry);
        });
        tag.put("Markets", markets);
        return tag;
    }

    static SettlementEconomy load(CompoundTag tag, Runnable dirty) {
        SettlementEconomy economy = new SettlementEconomy(dirty);
        economy.balance = CurrencyAmount.load(tag.getCompound("Balance"));
        economy.reserve = CurrencyAmount.load(tag.getCompound("Reserve"));
        economy.dailyBudget = CurrencyAmount.load(tag.getCompound("DailyBudget"));
        economy.spent = CurrencyAmount.load(tag.getCompound("Spent"));
        economy.period = tag.contains("Period") ? Math.max(-1L, tag.getLong("Period")) : -1L;
        economy.lastTradeAt = tag.contains("LastTradeAt") ? Math.max(-1L, tag.getLong("LastTradeAt")) : -1L;
        for (Tag raw : tag.getList("Orders", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            SettlementTradeOrder order = SettlementTradeOrder.load(entry);
            if (economy.orders.size() >= MAX_ORDERS || economy.orders.containsKey(order.id())
                    || economy.orders.values().stream().anyMatch(existing -> existing.item().equals(order.item()))) {
                throw new IllegalArgumentException("Settlement trade orders are duplicated or exceed their limit");
            }
            economy.orders.put(order.id(), order);
            economy.orderSpending.put(order.id(), CurrencyAmount.load(entry.getCompound("Spent")));
        }
        for (Tag raw : tag.getList("Markets", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ResourceLocation group = ResourceLocation.parse(entry.getString("Group"));
            if (economy.markets.putIfAbsent(group, new MarketVolume(entry.getInt("Bought"), entry.getInt("Sold"))) != null) {
                throw new IllegalArgumentException("Settlement market group is duplicated");
            }
        }
        return economy;
    }

    public record MarketVolume(int bought, int sold) {
        public MarketVolume {
            if (bought < 0 || sold < 0) {
                throw new IllegalArgumentException("Settlement market volumes cannot be negative");
            }
        }
    }
}
