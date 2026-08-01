package com.jvn.villagerretaliation.sell;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class VillageSellMarket {
    private static final Map<MinecraftServer, Map<DemandCacheKey, Map<ResourceLocation, DailyDemandBand>>>
            DEMAND_CACHE = new WeakHashMap<>();
    private static final int MAX_CACHE_ENTRIES_PER_SERVER = 512;

    private VillageSellMarket() {
    }

    public static Optional<MarketQuote> quote(ServerLevel level, BlockPos position, ItemStack stack) {
        if (level == null || position == null || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> village = resolveVillage(registry, level, position);
        if (village.isEmpty()) {
            return Optional.empty();
        }
        return quote(level, registry, VillageMarketSavedData.get(level), village.get(), stack);
    }

    public static Optional<MarketQuote> quoteDiscovering(ServerLevel level, BlockPos position, ItemStack stack) {
        if (level == null || position == null || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> village = registry.discoverAt(level, position)
                .flatMap(registry::canonical)
                .or(() -> resolveVillage(registry, level, position));
        return village.flatMap(id -> quote(level, registry, VillageMarketSavedData.get(level), id, stack));
    }

    public static Optional<SaleResult> sell(ServerLevel level, BlockPos position, ItemStack stack) {
        if (level == null || position == null || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> village = registry.discoverAt(level, position)
                .flatMap(registry::canonical)
                .or(() -> resolveVillage(registry, level, position));
        if (village.isEmpty()) {
            return Optional.empty();
        }
        VillageMarketSavedData markets = VillageMarketSavedData.get(level);
        Optional<MarketQuote> quote = quote(level, registry, markets, village.get(), stack);
        if (quote.isEmpty()) {
            return Optional.empty();
        }
        MarketQuote calculated = quote.get();
        CommodityMarketState updated = markets.recordPressure(
                registry,
                calculated.villageId(),
                calculated.marketGroup(),
                calculated.pressureAdded(),
                currentDay(level.getServer()));
        MarketQuote committed = new MarketQuote(
                calculated.villageId(),
                calculated.villageDisplayName(),
                calculated.marketGroup(),
                calculated.baseUnitPrice(),
                calculated.dailyDemandBand(),
                calculated.dailyDemandMultiplier(),
                calculated.recoveredPressure(),
                calculated.supplySegments(),
                calculated.effectiveUnitPrice(),
                calculated.stackPayout(),
                calculated.pressureAdded(),
                updated.pressure());
        return Optional.of(new SaleResult(committed, stack.getCount()));
    }

    static Optional<MarketQuote> quote(
            ServerLevel level,
            VillageAllegianceRegistrySavedData registry,
            VillageMarketSavedData markets,
            VillageAllegianceId village,
            ItemStack stack) {
        MinecraftServer server = level.getServer();
        Optional<SellPriceDefinition> definition = SellPriceResources.definition(server, stack);
        Optional<VillageAllegianceId> canonical = registry.canonical(village);
        if (definition.isEmpty() || canonical.isEmpty()) {
            return Optional.empty();
        }
        long day = currentDay(server);
        SellPriceDefinition priceDefinition = definition.get();
        DailyDemandBand demand = demandBands(
                        server,
                        canonical.get(),
                        day,
                        SellPriceResources.definitions(server).values().stream()
                                .map(SellPriceDefinition::marketGroup)
                                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))
                .getOrDefault(priceDefinition.marketGroup(), DailyDemandBand.NORMAL);
        CurrencyAmount baseUnitPrice = selectBasePrice(
                level.getSeed(), day, canonical.get(), priceDefinition);
        CurrencyAmount pressure = markets.pressure(
                registry, canonical.get(), priceDefinition.marketGroup(), day);
        String villageName = registry.canonicalRecord(canonical.get())
                .map(VillageAllegianceRegistrySavedData.AllegianceRecord::displayName)
                .orElse("Village");
        return Optional.of(calculateQuote(
                canonical.get(),
                villageName,
                priceDefinition.marketGroup(),
                baseUnitPrice,
                demand,
                pressure,
                stack.getCount()));
    }

    public static Optional<VillageAllegianceId> resolveVillage(
            VillageAllegianceRegistrySavedData registry,
            ServerLevel level,
            BlockPos position) {
        if (registry == null || level == null || position == null) {
            return Optional.empty();
        }
        Optional<VillageAllegianceId> resolved = registry.peekAt(level, position).flatMap(registry::canonical);
        if (resolved.isPresent()) {
            return resolved;
        }
        long section = SectionPos.asLong(position);
        return registry.activeRecords(level.dimension().location()).stream()
                .filter(record -> record.footprintSections().isEmpty())
                .filter(record -> SectionPos.asLong(record.originPosition()) == section)
                .min(Comparator.comparingDouble(record -> record.originPosition().distSqr(position)))
                .map(VillageAllegianceRegistrySavedData.AllegianceRecord::id)
                .flatMap(registry::canonical);
    }

    public static long currentDay(MinecraftServer server) {
        return Math.floorDiv(server.overworld().getDayTime(), VillageMarketPolicy.DAY_TICKS);
    }

    static CurrencyAmount selectBasePrice(
            long worldSeed,
            long day,
            VillageAllegianceId village,
            SellPriceDefinition definition) {
        List<CurrencyAmount> candidates = definition.candidatePrices();
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        long villageHash = village == null
                ? 0L
                : mix64(village.value().getMostSignificantBits() ^ village.value().getLeastSignificantBits());
        long identity = mix64(worldSeed ^ villageHash ^ hash(definition.id()) ^ mix64(day));
        return candidates.get(Math.floorMod(identity, candidates.size()));
    }

    static Map<ResourceLocation, DailyDemandBand> demandBands(
            long worldSeed,
            VillageAllegianceId village,
            long day,
            long generation,
            Set<ResourceLocation> groups) {
        if (village == null || groups == null || groups.isEmpty()) {
            return Map.of();
        }
        ArrayList<ResourceLocation> ranked = new ArrayList<>(groups);
        ranked.sort(Comparator
                .comparingLong((ResourceLocation group) -> demandScore(
                        worldSeed, village, day, group))
                .thenComparing(ResourceLocation::toString));
        LinkedHashMap<ResourceLocation, DailyDemandBand> result = new LinkedHashMap<>();
        for (int rank = 0; rank < ranked.size(); rank++) {
            result.put(ranked.get(rank), bandForRank(rank, ranked.size()));
        }
        return Map.copyOf(result);
    }

    static MarketQuote calculateQuote(
            VillageAllegianceId village,
            String villageName,
            ResourceLocation group,
            CurrencyAmount baseUnitPrice,
            DailyDemandBand demand,
            CurrencyAmount recoveredPressure,
            int itemCount) {
        if (itemCount <= 0) {
            throw new IllegalArgumentException("Market quote item count must be positive");
        }
        CurrencyAmount safePressure = VillageMarketPolicy.sanitizePressure(recoveredPressure);
        CurrencyAmount pressureAdded = baseUnitPrice.multiply(itemCount);
        CurrencyAmount remaining = pressureAdded;
        CurrencyAmount cursor = safePressure;
        CurrencyAmount payout = CurrencyAmount.ZERO;
        ArrayList<SupplySegment> segments = new ArrayList<>();
        while (!remaining.isZero()) {
            SupplyBand supply = SupplyBand.forPressure(cursor);
            Optional<CurrencyAmount> upperBound = supply.upperBound();
            CurrencyAmount segmentBase = upperBound.isPresent()
                    ? remaining.min(upperBound.get().subtractClamped(cursor))
                    : remaining;
            if (segmentBase.isZero()) {
                throw new IllegalStateException("Supply tier calculation did not advance");
            }
            CurrencyAmount multiplier = VillageMarketPolicy.effectiveMultiplier(demand, supply);
            CurrencyAmount segmentPayout = segmentBase.multiply(multiplier);
            segments.add(new SupplySegment(supply, segmentBase, multiplier, segmentPayout));
            payout = payout.add(segmentPayout);
            cursor = cursor.add(segmentBase);
            remaining = remaining.subtract(segmentBase);
        }
        CurrencyAmount resultingPressure =
                VillageMarketPolicy.sanitizePressure(safePressure.add(pressureAdded));
        return new MarketQuote(
                village,
                villageName,
                group,
                baseUnitPrice,
                demand,
                demand.multiplier(),
                safePressure,
                List.copyOf(segments),
                payout.divide(itemCount),
                payout,
                pressureAdded,
                resultingPressure);
    }

    private static Map<ResourceLocation, DailyDemandBand> demandBands(
            MinecraftServer server,
            VillageAllegianceId village,
            long day,
            Set<ResourceLocation> groups) {
        long generation = SellPriceResources.generation();
        DemandCacheKey key = new DemandCacheKey(
                server.overworld().getSeed(), village, day, generation, Set.copyOf(groups));
        synchronized (DEMAND_CACHE) {
            Map<DemandCacheKey, Map<ResourceLocation, DailyDemandBand>> cache =
                    DEMAND_CACHE.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
            Map<ResourceLocation, DailyDemandBand> cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            if (cache.size() >= MAX_CACHE_ENTRIES_PER_SERVER) {
                cache.clear();
            }
            Map<ResourceLocation, DailyDemandBand> created =
                    demandBands(key.worldSeed, village, day, generation, groups);
            cache.put(key, created);
            return created;
        }
    }

    private static DailyDemandBand bandForRank(int rank, int size) {
        if (size == 1) {
            return DailyDemandBand.NORMAL;
        }
        if (size == 2) {
            return rank == 0 ? DailyDemandBand.VERY_HIGH : DailyDemandBand.VERY_LOW;
        }
        int percentile = (int) ((long) rank * 100L / size);
        if (percentile < 10) {
            return DailyDemandBand.VERY_HIGH;
        }
        if (percentile < 30) {
            return DailyDemandBand.HIGH;
        }
        if (percentile < 70) {
            return DailyDemandBand.NORMAL;
        }
        if (percentile < 90) {
            return DailyDemandBand.LOW;
        }
        return DailyDemandBand.VERY_LOW;
    }

    private static long demandScore(
            long worldSeed,
            VillageAllegianceId village,
            long day,
            ResourceLocation group) {
        return mix64(worldSeed
                ^ village.value().getMostSignificantBits()
                ^ Long.rotateLeft(village.value().getLeastSignificantBits(), 23)
                ^ mix64(day)
                ^ hash(group));
    }

    private static long hash(ResourceLocation id) {
        long hash = 0xcbf29ce484222325L;
        String value = id.toString();
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private record DemandCacheKey(
            long worldSeed,
            VillageAllegianceId village,
            long day,
            long generation,
            Set<ResourceLocation> groups) {
    }
}
