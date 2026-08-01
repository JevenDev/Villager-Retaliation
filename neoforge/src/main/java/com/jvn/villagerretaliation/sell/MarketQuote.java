package com.jvn.villagerretaliation.sell;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record MarketQuote(
        VillageAllegianceId villageId,
        String villageDisplayName,
        ResourceLocation marketGroup,
        CurrencyAmount baseUnitPrice,
        DailyDemandBand dailyDemandBand,
        CurrencyAmount dailyDemandMultiplier,
        CurrencyAmount recoveredPressure,
        List<SupplySegment> supplySegments,
        CurrencyAmount effectiveUnitPrice,
        CurrencyAmount stackPayout,
        CurrencyAmount pressureAdded,
        CurrencyAmount resultingPressure) {

    public MarketQuote {
        if (villageId == null
                || villageDisplayName == null
                || marketGroup == null
                || baseUnitPrice == null
                || dailyDemandBand == null
                || dailyDemandMultiplier == null
                || recoveredPressure == null
                || supplySegments == null
                || effectiveUnitPrice == null
                || stackPayout == null
                || pressureAdded == null
                || resultingPressure == null) {
            throw new IllegalArgumentException("Market quotes require complete village and exact price data");
        }
        supplySegments = List.copyOf(supplySegments);
    }
}
