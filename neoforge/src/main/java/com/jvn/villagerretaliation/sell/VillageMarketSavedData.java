package com.jvn.villagerretaliation.sell;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillageMarketSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_village_markets";
    private static final int FORMAT_VERSION = 1;

    private final Map<VillageAllegianceId, VillageMarketState> markets = new LinkedHashMap<>();

    public static VillageMarketSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageMarketSavedData::new, VillageMarketSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static VillageMarketSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageMarketSavedData data = new VillageMarketSavedData();
        if (tag == null || tag.getInt("FormatVersion") > FORMAT_VERSION) {
            return data;
        }
        for (Tag rawMarket : tag.getList("Markets", Tag.TAG_COMPOUND)) {
            if (!(rawMarket instanceof CompoundTag marketTag) || !marketTag.hasUUID("Village")) {
                continue;
            }
            VillageAllegianceId village = new VillageAllegianceId(marketTag.getUUID("Village"));
            LinkedHashMap<ResourceLocation, CommodityMarketState> commodities = new LinkedHashMap<>();
            for (Tag rawCommodity : marketTag.getList("Commodities", Tag.TAG_COMPOUND)) {
                if (!(rawCommodity instanceof CompoundTag commodityTag)) {
                    continue;
                }
                ResourceLocation group = ResourceLocation.tryParse(commodityTag.getString("Group"));
                if (group == null) {
                    continue;
                }
                CurrencyAmount pressure = VillageMarketPolicy.sanitizePressure(
                        loadPressure(commodityTag.getCompound("Pressure")));
                if (!pressure.isZero()) {
                    commodities.put(group, new CommodityMarketState(
                            pressure,
                            commodityTag.getLong("LastUpdatedDay")));
                }
            }
            if (!commodities.isEmpty()) {
                data.markets.put(village, new VillageMarketState(
                        Map.copyOf(commodities),
                        Math.max(0L, marketTag.getLong("Revision"))));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
        ListTag marketTags = new ListTag();
        this.markets.forEach((village, state) -> {
            CompoundTag marketTag = new CompoundTag();
            marketTag.putUUID("Village", village.value());
            marketTag.putLong("Revision", state.revision());
            ListTag commodityTags = new ListTag();
            state.commodities().forEach((group, commodity) -> {
                if (commodity.pressure().isZero()) {
                    return;
                }
                CompoundTag commodityTag = new CompoundTag();
                commodityTag.putString("Group", group.toString());
                commodityTag.put("Pressure", commodity.pressure().save());
                commodityTag.putLong("LastUpdatedDay", commodity.lastUpdatedDay());
                commodityTags.add(commodityTag);
            });
            marketTag.put("Commodities", commodityTags);
            marketTags.add(marketTag);
        });
        tag.put("Markets", marketTags);
        return tag;
    }

    public static Optional<VillageAllegianceId> peekVillage(ServerLevel level, BlockPos position) {
        if (level == null || position == null) {
            return Optional.empty();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        return registry.peekAt(level, position).flatMap(registry::canonical);
    }

    public static Optional<VillageAllegianceId> discoverVillage(ServerLevel level, BlockPos position) {
        if (level == null || position == null) {
            return Optional.empty();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        return registry.discoverAt(level, position).flatMap(registry::canonical);
    }

    public CurrencyAmount pressure(
            VillageAllegianceRegistrySavedData registry,
            VillageAllegianceId village,
            ResourceLocation group,
            long currentDay) {
        if (registry == null || village == null || group == null) {
            return CurrencyAmount.ZERO;
        }
        Optional<VillageAllegianceId> canonical = registry.canonical(village);
        if (canonical.isEmpty()) {
            return CurrencyAmount.ZERO;
        }
        CurrencyAmount total = CurrencyAmount.ZERO;
        for (Map.Entry<VillageAllegianceId, VillageMarketState> entry : this.markets.entrySet()) {
            if (registry.canonical(entry.getKey()).filter(canonical.get()::equals).isPresent()) {
                total = VillageMarketPolicy.sanitizePressure(
                        total.add(entry.getValue().commodity(group, currentDay).pressure()));
            }
        }
        return total;
    }

    public long revision(VillageAllegianceRegistrySavedData registry, VillageAllegianceId village) {
        if (registry == null || village == null) {
            return 0L;
        }
        Optional<VillageAllegianceId> canonical = registry.canonical(village);
        if (canonical.isEmpty()) {
            return 0L;
        }
        return this.markets.entrySet().stream()
                .filter(entry -> registry.canonical(entry.getKey()).filter(canonical.get()::equals).isPresent())
                .mapToLong(entry -> entry.getValue().revision())
                .max()
                .orElse(0L);
    }

    public CommodityMarketState recordPressure(
            VillageAllegianceRegistrySavedData registry,
            VillageAllegianceId village,
            ResourceLocation group,
            CurrencyAmount pressureAdded,
            long currentDay) {
        if (registry == null || village == null || group == null || pressureAdded == null || pressureAdded.isZero()) {
            return new CommodityMarketState(CurrencyAmount.ZERO, currentDay);
        }
        Optional<VillageAllegianceId> canonical = registry.canonical(village);
        if (canonical.isEmpty()) {
            return new CommodityMarketState(CurrencyAmount.ZERO, currentDay);
        }
        canonicalize(registry, currentDay);
        VillageMarketState market = this.markets.getOrDefault(canonical.get(), VillageMarketState.empty());
        CommodityMarketState updated = market.commodity(group, currentDay).add(pressureAdded, currentDay);
        this.markets.put(canonical.get(), market.withCommodity(group, updated));
        setDirty();
        return updated;
    }

    public void canonicalize(VillageAllegianceRegistrySavedData registry, long currentDay) {
        if (registry == null || this.markets.isEmpty()) {
            return;
        }
        LinkedHashMap<VillageAllegianceId, VillageMarketState> canonicalMarkets = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<VillageAllegianceId, VillageMarketState> entry : this.markets.entrySet()) {
            Optional<VillageAllegianceId> canonical = registry.canonical(entry.getKey());
            if (canonical.isEmpty()) {
                canonicalMarkets.put(entry.getKey(), entry.getValue());
                continue;
            }
            VillageMarketState previous = canonicalMarkets.putIfAbsent(canonical.get(), entry.getValue());
            if (previous != null) {
                canonicalMarkets.put(canonical.get(), previous.merge(entry.getValue(), currentDay));
            }
            changed |= !canonical.get().equals(entry.getKey()) || previous != null;
        }
        if (changed) {
            this.markets.clear();
            this.markets.putAll(canonicalMarkets);
            setDirty();
        }
    }

    private static CurrencyAmount loadPressure(CompoundTag tag) {
        if (tag == null) {
            return CurrencyAmount.ZERO;
        }
        String numerator = tag.getString("Numerator");
        String denominator = tag.getString("Denominator");
        if (numerator.length() > 128 || denominator.length() > 128) {
            return CurrencyAmount.ZERO;
        }
        return CurrencyAmount.load(tag);
    }

    public int marketCount() {
        return this.markets.size();
    }

    public List<VillageAllegianceId> villageIds() {
        return List.copyOf(new ArrayList<>(this.markets.keySet()));
    }
}
