package com.jvn.villagerretaliation.sell;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record VillageMarketState(Map<ResourceLocation, CommodityMarketState> commodities, long revision) {
    public VillageMarketState {
        commodities = commodities == null ? Map.of() : Map.copyOf(commodities);
        revision = Math.max(0L, revision);
    }

    public static VillageMarketState empty() {
        return new VillageMarketState(Map.of(), 0L);
    }

    public CommodityMarketState commodity(ResourceLocation group, long currentDay) {
        CommodityMarketState state = this.commodities.get(group);
        return state == null
                ? new CommodityMarketState(CurrencyAmount.ZERO, currentDay)
                : state.recover(currentDay);
    }

    public VillageMarketState withCommodity(ResourceLocation group, CommodityMarketState state) {
        LinkedHashMap<ResourceLocation, CommodityMarketState> updated = new LinkedHashMap<>(this.commodities);
        if (state.pressure().isZero()) {
            updated.remove(group);
        } else {
            updated.put(group, state);
        }
        return new VillageMarketState(Map.copyOf(updated), nextRevision(this.revision));
    }

    public VillageMarketState merge(VillageMarketState other, long currentDay) {
        if (other == null) {
            return this;
        }
        LinkedHashMap<ResourceLocation, CommodityMarketState> merged = new LinkedHashMap<>();
        this.commodities.forEach((group, state) -> {
            CommodityMarketState recovered = state.recover(currentDay);
            if (!recovered.pressure().isZero()) {
                merged.put(group, recovered);
            }
        });
        other.commodities.forEach((group, state) -> {
            CommodityMarketState combined = merged.get(group);
            CommodityMarketState recovered = combined == null
                    ? state.recover(currentDay)
                    : combined.merge(state, currentDay);
            if (!recovered.pressure().isZero()) {
                merged.put(group, recovered);
            }
        });
        return new VillageMarketState(
                Map.copyOf(merged),
                nextRevision(Math.max(this.revision, other.revision)));
    }

    private static long nextRevision(long current) {
        return current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L;
    }
}
