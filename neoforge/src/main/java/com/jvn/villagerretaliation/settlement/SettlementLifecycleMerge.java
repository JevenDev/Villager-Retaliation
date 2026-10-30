package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class SettlementLifecycleMerge {
    private SettlementLifecycleMerge() { }

    static CompoundTag merge(SettlementRecord source, SettlementRecord target, HolderLookup.Provider provider, Set<Long> owned) {
        var from = source.save(provider);
        var into = target.save(provider);
        into.putLong("Revision", Math.max(source.revision(), target.revision()));
        collections(source, from, into);
        SettlementConstructionState.merge(from, into, target.id().value());
        workerPools(target, into);
        var stage = source.stage().ordinal() > target.stage().ordinal() ? source.stage() : target.stage();
        into.putString("Stage", stage.name());
        into.putInt("ExpansionRadius", Math.max(from.getInt("ExpansionRadius"), into.getInt("ExpansionRadius")));
        into.put("Support", new SettlementSupportState().save());
        into.remove("LeadershipOffer");
        into.putLong("NextApplicantAt", Math.max(from.getLong("NextApplicantAt"), into.getLong("NextApplicantAt")));
        into.putLong("NextDepartureAt", Math.max(from.getLong("NextDepartureAt"), into.getLong("NextDepartureAt")));
        territory(source, target, owned, from, into);
        economy(from.getCompound("Economy"), into.getCompound("Economy"));
        return into;
    }

    private static void collections(SettlementRecord source, CompoundTag from, CompoundTag into) {
        for (String key : from.getAllKeys()) {
            if (!(from.get(key) instanceof ListTag list)) continue;
            switch (key) {
                case "Delegates", "SupportServices", "Projects", "ProjectHistory" -> { }
                case "Navigation" -> navigation(list, into);
                case "AnimalBirths" -> append(list, into, key, entry -> {
                    if (!entry.hasUUID("ReceiptTown")) entry.putUUID("ReceiptTown", source.id().value());
                });
                case "Arrivals" -> append(list, into, key, entry -> {
                    if (!entry.contains("Origin")) entry.putLong("Origin", source.center().asLong());
                });
                case "History", "Areas", "FarmJobs", "Duties", "GuardOrders", "TemporaryGuardOrders", "WorkerSettings",
                     "WorksitePlans", "ProductionQueues", "TransferQueues", "SupplyPolicies", "WorkerPools",
                     "WorkerPoolCooldowns", "EquipmentPlans", "Depots", "Surveys", "ManualDuties", "ManualDutyRevisions",
                     "ResidentCare", "Homes", "Courtships", "HomeReleases", "Workers", "Livestock", "HuntingTargets",
                     "Projectiles", "ItemDrops", "GroundItems", "GroundExperience", "EntityDeaths", "Furnaces",
                     "BrewingStands", "Fields", "Forests", "Mines", "FishingSpots", "Departures", "Applicants" -> {
                    var combined = ((ListTag) into.get(key)).copy();
                    list.forEach(entry -> combined.add(entry.copy()));
                    into.put(key, combined);
                }
                default -> throw new IllegalArgumentException("Missing settlement merge rule for " + key);
            }
        }
    }

    private static void append(ListTag from, CompoundTag into, String key, java.util.function.Consumer<CompoundTag> transform) {
        var combined = into.getList(key, Tag.TAG_COMPOUND).copy();
        for (Tag raw : from) {
            var entry = ((CompoundTag) raw).copy();
            transform.accept(entry);
            combined.add(entry);
        }
        into.put(key, combined);
    }

    private static void navigation(ListTag from, CompoundTag into) {
        var combined = into.getList("Navigation", Tag.TAG_COMPOUND).copy();
        var origins = new HashSet<Long>();
        combined.forEach(entry -> origins.add(((CompoundTag) entry).getLong("Origin")));
        for (Tag entry : from) if (origins.add(((CompoundTag) entry).getLong("Origin"))) combined.add(entry.copy());
        into.put("Navigation", combined);
    }

    private static void workerPools(SettlementRecord target, CompoundTag into) {
        for (Tag raw : into.getList("WorkerPools", Tag.TAG_COMPOUND)) {
            var pool = (CompoundTag) raw;
            if (pool.getString("State").equals(SettlementWorkerPool.State.ACTIVE.name())
                    && !target.permits(pool.getUUID("Authority"), SettlementPermission.STAFF)) {
                pool.putString("State", SettlementWorkerPool.State.SUSPENDED.name());
                pool.putString("Blocker", "pool_permission_changed");
                pool.putLong("Revision", Math.addExact(into.getLong("Revision"), 1L));
            }
        }
    }

    private static void territory(SettlementRecord source, SettlementRecord target, Set<Long> owned, CompoundTag from, CompoundTag into) {
        var territory = into.getCompound("Territory");
        var oldTerritory = from.getCompound("Territory");
        int capacity = Math.max(territory.getInt("EarnedCapacity"), oldTerritory.getInt("EarnedCapacity"));
        territory.putInt("EarnedCapacity", capacity);
        territory.putInt("GrandfatheredCapacity", Math.max(0, owned.size() - capacity));
        territory.putInt("FreeClaims", Math.addExact(territory.getInt("FreeClaims"), oldTerritory.getInt("FreeClaims")));
        territory.putInt("PaidPurchases", Math.addExact(territory.getInt("PaidPurchases"), oldTerritory.getInt("PaidPurchases")));
        territory.putInt("Rewards", territory.getInt("Rewards") | oldTerritory.getInt("Rewards"));
        territory.putLong("Revision", Math.addExact(Math.max(source.territory().revision(), target.territory().revision()), 1L));
        territory.putLongArray("Plots", owned.stream().sorted().mapToLong(Long::longValue).toArray());
    }

    private static void economy(CompoundTag oldEconomy, CompoundTag economy) {
        for (String key : Set.of("Balance", "PendingDeposit", "Reserve", "DailyBudget", "Spent")) {
            economy.put(key, CurrencyAmount.load(economy.getCompound(key)).add(CurrencyAmount.load(oldEconomy.getCompound(key))).save());
        }
        var bankStocks = economy.getCompound("BankStorage").getList("Stocks", Tag.TAG_COMPOUND);
        oldEconomy.getCompound("BankStorage").getList("Stocks", Tag.TAG_COMPOUND).forEach(stock -> bankStocks.add(stock.copy()));
        var bankTag = new CompoundTag(); bankTag.put("Stocks", bankStocks); economy.put("BankStorage", bankTag);
        var orders = economy.getList("Orders", Tag.TAG_COMPOUND);
        oldEconomy.getList("Orders", Tag.TAG_COMPOUND).forEach(order -> orders.add(order.copy()));
        var policySpending = economy.getList("PolicySpending", Tag.TAG_COMPOUND);
        oldEconomy.getList("PolicySpending", Tag.TAG_COMPOUND).forEach(account -> policySpending.add(account.copy()));
        var markets = economy.getList("Markets", Tag.TAG_COMPOUND);
        for (Tag raw : oldEconomy.getList("Markets", Tag.TAG_COMPOUND)) {
            var previous = (CompoundTag) raw;
            var existing = markets.stream().map(value -> (CompoundTag) value)
                    .filter(value -> value.getString("Group").equals(previous.getString("Group"))).findFirst().orElse(null);
            if (existing == null) markets.add(previous.copy());
            else for (String key : Set.of("Bought", "Sold")) existing.putInt(key, Math.addExact(existing.getInt(key), previous.getInt(key)));
        }
        economy.putLong("Period", Math.max(economy.getLong("Period"), oldEconomy.getLong("Period")));
        economy.putLong("LastTradeAt", Math.max(economy.getLong("LastTradeAt"), oldEconomy.getLong("LastTradeAt")));
    }
}
