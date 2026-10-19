package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.sell.SellPriceResources;
import com.jvn.villagerretaliation.sell.VillageSellMarket;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementTradeService {
    private SettlementTradeService() {
    }

    public static boolean depositCurrency(ServerPlayer player, VillageAllegianceId id, int amount) {
        SettlementRecord town = manageable(player, id, SettlementPermission.TREASURY);
        if (town == null || amount <= 0 || amount > 4_096 || !VillagerCurrencyPayment.tryRemove(player, amount)) {
            return false;
        }
        town.economy().credit(CurrencyAmount.of(amount, 1L));
        com.jvn.villagerretaliation.progress.ProgressHistoryService.contribution(player.serverLevel(), player.getUUID(), town.id(), "currency", "", amount);
        return true;
    }

    public static boolean withdrawCurrency(ServerPlayer player, VillageAllegianceId id, int amount) {
        SettlementRecord town = manageable(player, id, SettlementPermission.TREASURY);
        if (town == null || !town.leader().equals(player.getUUID()) || amount <= 0 || amount > 4_096
                || !canReceiveWithdrawal(player, amount)
                || !town.economy().withdraw(CurrencyAmount.of(amount, 1L))) {
            return false;
        }
        VillagerCurrencyPayment.give(player, amount);
        return true;
    }

    /** Match the slots vanilla Inventory.add can use, without debiting funds or dropping overflow. */
    static boolean canReceiveWithdrawal(ServerPlayer player, int amount) {
        if (player == null || amount <= 0 || amount > 4_096) return false;
        ItemStack currency = VillagerCurrencyResources.createStack(player.serverLevel().getServer(), 1);
        int limit = Math.min(currency.getMaxStackSize(), player.getInventory().getMaxStackSize());
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) remaining -= limit;
            else if (ItemStack.isSameItemSameComponents(currency, stack)) remaining -= Math.max(0, limit - stack.getCount());
            if (remaining <= 0) return true;
        }
        // Vanilla merges into an occupied offhand slot, but never fills an empty one.
        ItemStack offhand = player.getOffhandItem();
        if (ItemStack.isSameItemSameComponents(currency, offhand)) remaining -= Math.max(0, limit - offhand.getCount());
        return remaining <= 0;
    }

    public static boolean configureBudget(ServerPlayer player, VillageAllegianceId id,
                                          CurrencyAmount reserve, CurrencyAmount dailyBudget) {
        SettlementRecord town = manageable(player, id, SettlementPermission.TREASURY);
        return town != null && town.economy().configureBudget(reserve, dailyBudget);
    }

    public static boolean putOrder(ServerPlayer player, VillageAllegianceId id, SettlementTradeOrder order) {
        SettlementRecord town = manageable(player, id, SettlementPermission.TRADE);
        return town != null && order != null
                && SellPriceResources.definition(player.serverLevel().getServer(), order.sample()).isPresent()
                && town.economy().putOrder(order);
    }

    public static boolean removeOrder(ServerPlayer player, VillageAllegianceId id, UUID order) {
        SettlementRecord town = manageable(player, id, SettlementPermission.TRADE);
        return town != null && town.economy().removeOrder(order);
    }

    private static SettlementRecord manageable(ServerPlayer player, VillageAllegianceId id,
                                                SettlementPermission permission) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        return SettlementSavedData.get(player.serverLevel()).get(id)
                .filter(town -> town.dimension().equals(player.level().dimension().location()))
                .filter(town -> town.permits(player.getUUID(), permission)).orElse(null);
    }

    static Map<UUID, Result> process(ServerLevel level, SettlementRecord town) {
        return process(level, town, level.getGameTime());
    }

    static Map<UUID, Result> process(ServerLevel level, SettlementRecord town, long gameTime) {
        if (!town.dimension().equals(level.dimension().location())
                || !town.economy().beginTradeCycle(gameTime)) {
            return Map.of();
        }
        List<UUID> workers = activeClerks(level, town, true);
        Map<UUID, Result> results = new LinkedHashMap<>();
        List<SettlementTradeOrder> orders = new ArrayList<>(town.economy().orders().values());
        int capacity = workCapacity(level, town, workers);
        var protection = SettlementDemandService.inspect(level, town);
        int start = orders.isEmpty() ? 0 : (int) Math.floorMod(gameTime / 200L, orders.size());
        for (int offset = 0; offset < orders.size(); offset++) {
            SettlementTradeOrder order = orders.get((start + offset) % orders.size());
            Result result;
            if (!order.enabled()) {
                result = Result.blocked(Status.DISABLED);
            } else if (town.stage().ordinal() < SettlementStage.VILLAGE.ordinal()) {
                result = Result.blocked(Status.STAGE_LOCKED);
            } else if (!SettlementWorkforceService.workingHours(level.getDayTime(), town.leader(), HiredVillagerRole.NITWIT)) {
                result = Result.blocked(Status.CLOSED);
            } else if (workers.isEmpty()) {
                result = Result.blocked(Status.NO_STAFF);
            } else if (capacity == 0) {
                result = Result.blocked(Status.BUSY);
            } else {
                result = order.direction() == SettlementTradeOrder.Direction.BUY
                        ? buy(level, town, order, capacity) : sell(level, town, order, capacity, protection);
                capacity -= result.count();
            }
            results.put(order.id(), result);
        }
        var purchases = SettlementPurchaseService.work(level, town, gameTime, new int[] {capacity});
        while (!SettlementWork.complete(purchases)) purchases.run();
        town.economy().recordResults(results);
        return Map.copyOf(results);
    }

    static void schedule(ServerLevel level, SettlementRecord town, long now, List<Runnable> jobs) {
        SettlementAllocationService.schedule(level, town, jobs);
        jobs.add(SettlementWork.lazy(() -> {
            var surveys = new ArrayList<Runnable>();
            if (town.stage().ordinal() >= SettlementStage.VILLAGE.ordinal()) {
                for (var area : town.areas().values()) {
                    if (area.enabled() && area.type() == SettlementAreaType.TRADING_POST) surveys.add(SettlementBuildingService.work(level, town, area));
                }
            }
            return new SettlementWork(surveys);
        }));
        jobs.add(SettlementWork.lazy(() -> {
            if (!town.dimension().equals(level.dimension().location()) || !town.economy().beginTradeCycle(now)) return () -> {};
            var orders = new ArrayList<>(town.economy().orders().keySet());
            if (!orders.isEmpty()) java.util.Collections.rotate(orders, -(int) Math.floorMod(now / 200L, orders.size()));
            int[] remaining = {workCapacity(level, town, activeClerks(level, town, false))};
            var results = new LinkedHashMap<UUID, Result>();
            var manual = SettlementWork.each(orders, id -> {
                var order = town.economy().orders().get(id);
                if (order == null) return;
                Result result;
                if (!order.enabled()) result = Result.blocked(Status.DISABLED);
                else if (town.stage().ordinal() < SettlementStage.VILLAGE.ordinal()) result = Result.blocked(Status.STAGE_LOCKED);
                else if (!SettlementWorkforceService.workingHours(level.getDayTime(), town.leader(), HiredVillagerRole.NITWIT))
                    result = Result.blocked(Status.CLOSED);
                else if (activeClerks(level, town, false).isEmpty()) result = Result.blocked(Status.NO_STAFF);
                else if (remaining[0] == 0) result = Result.blocked(Status.BUSY);
                else {
                    var protection = order.direction() == SettlementTradeOrder.Direction.SELL ? SettlementAllocationService.ready(level, town) : null;
                    result = order.direction() == SettlementTradeOrder.Direction.BUY ? buy(level, town, order, remaining[0])
                            : protection == null ? Result.blocked(Status.BUSY) : sell(level, town, order, remaining[0], protection.refresh());
                    remaining[0] -= result.count();
                }
                results.put(id, result);
                town.economy().recordResults(results);
            });
            return new SettlementWork(List.of(manual,
                    SettlementWork.lazy(() -> SettlementPurchaseService.work(level, town, now, remaining))));
        }));
    }

    private static List<UUID> activeClerks(ServerLevel level, SettlementRecord town, boolean refresh) {
        if (town.stage().ordinal() < SettlementStage.VILLAGE.ordinal()
                || !SettlementWorkforceService.workingHours(level.getDayTime(), town.leader(), HiredVillagerRole.NITWIT)) {
            return List.of();
        }
        Map<UUID, SettlementArea> equipped = new LinkedHashMap<>();
        var blocks = new SettlementBuildingSurvey.ObservedBlocks(level, null, town);
        for (SettlementArea area : town.areas().values()) {
            if (area.enabled() && area.type() == SettlementAreaType.TRADING_POST) {
                if (refresh) SettlementBuildingService.refresh(level, town, area);
                var survey = town.surveys().get(area.id());
                if (survey != null && survey.workstations().stream().anyMatch(position -> SettlementBuildingSurvey.fixtureAvailable(
                        blocks, position, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.LECTERN)))) {
                    equipped.put(area.id(), area);
                }
            }
        }
        List<UUID> clerks = new ArrayList<>();
        for (var resident : VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime())) {
            SettlementArea area = equipped.get(town.duties().get(resident.id()));
            if (!resident.adult() || area == null
                    || town.effectiveWorkerSettings(resident.id()) != null && !town.effectiveWorkerSettings(resident.id()).enabled()) {
                continue;
            }
            var entity = level.getEntity(resident.id());
            if(town.autonomous()!=null && (entity==null || !level.isPositionEntityTicking(entity.blockPosition()))) continue;
            if (entity != null && level.isPositionEntityTicking(entity.blockPosition())) {
                if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.isSleeping()
                        || villager.isTrading() || villager.getTarget() != null || villager.getLastHurtByMob() != null
                        || VillagerDownedService.isDowned(villager) || VillagerRecoveryService.isForcingRecovery(villager)
                        || VillagerConversationService.isConversing(villager)
                        || VillagerRetaliationVillagerBrainUtil.hasThreatMemories(villager.getBrain())
                        || !SettlementWorkforceService.onDuty(level, villager)
                        || town.surveys().get(area.id()).workstations().stream()
                                .noneMatch(pos -> villager.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) <= 16.0D)) {
                    continue;
                }
            } else if (town.residents().containsKey(resident.id()) && !town.residents().get(resident.id()).availableForWork()) {
                continue;
            }
            clerks.add(resident.id());
        }
        return List.copyOf(clerks);
    }

    private static int workCapacity(ServerLevel level, SettlementRecord town, List<UUID> workers) {
        int population = VillageAllegianceRegistrySavedData.get(level).residentCount(town.id(), level.getGameTime());
        double foodDays = SettlementFoodService.reserveDays(town, population);
        double backgroundMultiplier = foodDays <= 0.0D ? 0.4D : foodDays < 0.25D ? 0.7D : 1.0D;
        int housing = town.housingCapacity();
        if (population > housing) {
            backgroundMultiplier *= 0.75D;
        }
        double capacity = 0.0D;
        for (UUID worker : workers) {
            var resident = town.residents().get(worker);
            double multiplier = resident == null ? backgroundMultiplier
                    : resident.workMultiplier() * (population > housing ? 0.75D : 1.0D);
            capacity += 8.0D * (level.getEntity(worker) instanceof Villager villager && level.isPositionEntityTicking(villager.blockPosition())
                    ? SettlementFoodService.workMultiplier(level, villager) : multiplier);
        }
        return workers.isEmpty() ? 0 : Math.clamp((int) capacity, 1, 32);
    }

    public static int dailySupply(SettlementStage stage) {
        return SettlementStageRules.forStage(stage).marketSupply();
    }

    public static int dailyDemand(SettlementStage stage) {
        return SettlementStageRules.forStage(stage).marketDemand();
    }

    private static Result buy(ServerLevel level, SettlementRecord town, SettlementTradeOrder order, int maximum) {
        var sample = order.sample();
        var depots = SettlementStock.supplies(town);
        long projected = SettlementSupplyStock.projected(level, town, depots, sample);
        long needed = projected < 0 ? -1 : Math.max(0, order.stockTarget() - projected);
        return buy(level, town, sample, order.unitPriceLimit(), needed, maximum, depots, SettlementStock.capacity(depots, sample),
                town.economy().availableBudget(order), (group, count, cost) -> town.economy().recordPurchase(order, group, count, cost));
    }

    static Result buyPolicy(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy,
                            SettlementPurchasePlan.Approval approval, long needed, int maximum) {
        if (!SettlementPurchaseService.active(level, town, policy) || !policy.configuration().purchases().approvals().contains(approval))
            return Result.blocked(Status.DISABLED);
        var availability = purchaseAvailability(level, town, policy.id(), policy.configuration().purchases(), approval);
        if (availability != Status.TRADED) return Result.blocked(availability);
        var clerks = activeClerks(level, town, false);
        if (clerks.isEmpty()) return Result.blocked(Status.NO_STAFF);
        int capacity = Math.min(maximum, workCapacity(level, town, clerks));
        if (capacity <= 0) return Result.blocked(Status.BUSY);
        var depot = SettlementPurchaseService.depot(level, town, approval);
        if (depot == null) return Result.blocked(Status.UNAVAILABLE);
        var sample = approval.sample();
        int space = SettlementPolicyValidation.capacity(level, town, depot.position(), sample);
        if (space < 0) return Result.blocked(Status.UNAVAILABLE);
        return buy(level, town, sample, approval.unitPriceLimit(), needed, capacity, List.of(depot), space,
                town.economy().availablePolicyBudget(policy.id(), policy.configuration().purchases().dailyBudget()),
                (group, count, cost) -> town.economy().recordPolicyPurchase(policy.id(), policy.configuration().purchases().dailyBudget(), group, count, cost));
    }

    static Status purchaseAvailability(ServerLevel level, SettlementRecord town, UUID policy, SettlementPurchasePlan purchases,
                                       SettlementPurchasePlan.Approval approval) {
        if (!purchases.enabled()) return Status.DISABLED;
        if (town.stage().ordinal() < SettlementStage.VILLAGE.ordinal()) return Status.STAGE_LOCKED;
        if (!SettlementWorkforceService.workingHours(level.getDayTime(), town.leader(), HiredVillagerRole.NITWIT)) return Status.CLOSED;
        if (activeClerks(level, town, false).isEmpty()) return Status.NO_STAFF;
        var depot = SettlementPurchaseService.depot(level, town, approval);
        if (depot == null) return Status.UNAVAILABLE;
        var sample = approval.sample();
        var quote = VillageSellMarket.quote(level, town.center(), sample).orElse(null);
        if (quote == null || quote.baseUnitPrice().isZero()) return Status.UNAVAILABLE;
        var price = quote.baseUnitPrice().multiply(2);
        if (price.compareTo(approval.unitPriceLimit()) > 0) return Status.PRICE_LIMIT;
        if (town.economy().market(quote.marketGroup()).bought() >= dailySupply(town.stage())) return Status.MARKET_LIMIT;
        int space = SettlementPolicyValidation.capacity(level, town, depot.position(), sample);
        if (space < 0) return Status.UNAVAILABLE;
        if (space == 0) return Status.NO_SPACE;
        return affordable(town.economy().availablePolicyBudget(policy, purchases.dailyBudget()), price) == 0 ? Status.BUDGET_LIMIT : Status.TRADED;
    }

    private static Result buy(ServerLevel level, SettlementRecord town, ItemStack sample, CurrencyAmount unitPriceLimit,
                              long needed, int maximum, List<SettlementDepot> depots, int space, CurrencyAmount budget, PurchaseLedger ledger) {
        var quote = VillageSellMarket.quote(level, town.center(), sample).orElse(null);
        if (quote == null || quote.baseUnitPrice().isZero()) return Result.blocked(Status.UNAVAILABLE);
        CurrencyAmount price = quote.baseUnitPrice().multiply(2L);
        if (price.compareTo(unitPriceLimit) > 0) return Result.blocked(Status.PRICE_LIMIT);
        if (needed < 0) return Result.blocked(Status.UNAVAILABLE);
        if (needed == 0) return Result.blocked(Status.STOCK_TARGET);
        int available = Math.max(0, dailySupply(town.stage()) - town.economy().market(quote.marketGroup()).bought());
        if (available == 0) return Result.blocked(Status.MARKET_LIMIT);
        if (space == 0) return Result.blocked(Status.NO_SPACE);
        int affordable = affordable(budget, price);
        if (affordable == 0) return Result.blocked(Status.BUDGET_LIMIT);
        int count = (int) Math.min(Math.min(Math.min(needed, available), Math.min(space, affordable)),
                Math.min(maximum, sample.getMaxStackSize()));
        if (count <= 0) return Result.blocked(Status.BUSY);
        CurrencyAmount cost = price.multiply(count);
        ItemStack remainder = SettlementStock.deposit(depots, sample.copyWithCount(count));
        if (!remainder.isEmpty()) throw new IllegalStateException("Validated settlement purchase exceeded depot capacity");
        ledger.record(quote.marketGroup(), count, cost);
        return new Result(Status.TRADED, count, cost);
    }

    @FunctionalInterface
    private interface PurchaseLedger {
        void record(net.minecraft.resources.ResourceLocation group, int count, CurrencyAmount cost);
    }

    private static Result sell(ServerLevel level, SettlementRecord town, SettlementTradeOrder order, int maximum,
                               SettlementDemandService.Snapshot protection) {
        ItemStack sample = order.sample();
        List<SettlementDepot> depots = SettlementStock.depots(town).stream()
                .filter(depot -> depot.purpose() == SettlementDepotPurpose.GENERAL
                        || depot.purpose() == SettlementDepotPurpose.OUTPUT).toList();
        Predicate<ItemStack> matches = stack -> ItemStack.isSameItemSameComponents(sample, stack);
        var allowances = new LinkedHashMap<SettlementDepot, Integer>();
        for (var depot : depots) allowances.put(depot, Math.min(depot.count(matches), protection.sellable(depot, sample)));
        int surplus = Math.max(0, Math.min(allowances.values().stream().mapToInt(Integer::intValue).sum(),
                SettlementStock.count(depots, matches) - order.stockTarget()));
        if (surplus == 0) {
            return Result.blocked(Status.STOCK_TARGET);
        }
        var quote = VillageSellMarket.quote(level, town.center(), sample).orElse(null);
        if (quote == null || quote.baseUnitPrice().isZero()) {
            return Result.blocked(Status.UNAVAILABLE);
        }
        int demand = Math.max(0, dailyDemand(town.stage()) - town.economy().market(quote.marketGroup()).sold());
        if (demand == 0) {
            return Result.blocked(Status.MARKET_LIMIT);
        }
        int count = Math.min(Math.min(surplus, demand), Math.min(maximum, sample.getMaxStackSize()));
        int low = 0;
        int high = count;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            var candidate = VillageSellMarket.quote(level, town.center(), sample.copyWithCount(middle)).orElseThrow();
            if (candidate.effectiveUnitPrice().compareTo(order.unitPriceLimit()) >= 0) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        count = low;
        if (count == 0) {
            return Result.blocked(Status.PRICE_LIMIT);
        }
        var sale = VillageSellMarket.sell(level, town.center(), sample.copyWithCount(count)).orElseThrow();
        int remaining = count;
        for (var entry : allowances.entrySet()) {
            int allowance = Math.min(remaining, entry.getValue());
            while (allowance > 0) {
                ItemStack taken = entry.getKey().withdraw(matches, allowance);
                if (taken.isEmpty()) throw new IllegalStateException("Validated settlement sale lost its depot stock");
                remaining -= taken.getCount();
                allowance -= taken.getCount();
                protection.sold(entry.getKey(), taken);
            }
        }
        if (remaining != 0) throw new IllegalStateException("Validated settlement sale exceeded protected surplus");
        CurrencyAmount income = sale.quote().stackPayout();
        town.economy().recordSale(quote.marketGroup(), count, income);
        return new Result(Status.TRADED, count, income);
    }

    private static int affordable(CurrencyAmount budget, CurrencyAmount price) {
        return budget.numerator().multiply(price.denominator())
                .divide(budget.denominator().multiply(price.numerator()))
                .min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

    public enum Status {
        DISABLED,
        STAGE_LOCKED,
        CLOSED,
        NO_STAFF,
        BUSY,
        UNAVAILABLE,
        PRICE_LIMIT,
        STOCK_TARGET,
        MARKET_LIMIT,
        NO_SPACE,
        BUDGET_LIMIT,
        TRADED
    }

    public record Result(Status status, int count, CurrencyAmount currency) {
        static Result blocked(Status status) {
            return new Result(status, 0, CurrencyAmount.ZERO);
        }
    }
}
