package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonParseException;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.io.IOException;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

public final class SettlementPlotPricing {
    private static volatile Rules rules = new Rules(16, 2, 4);

    private SettlementPlotPricing() {
    }

    public static long price(SettlementTerritory territory, int x, int z) {
        long distance = Math.max(Math.abs((long) x - territory.foundingChunk().x),
                Math.abs((long) z - territory.foundingChunk().z));
        var current = rules;
        return current.base() + (long) current.purchaseIncrement() * territory.paidPurchases()
                + current.distanceIncrement() * Math.max(0L, distance - 1L);
    }

    public static void reload(ResourceManager resources) {
        var location = ResourceLocation.parse("villagerretaliation:settlement_plots/pricing.json");
        try (var reader = resources.getResourceOrThrow(location).openAsReader()) {
            var root = GsonHelper.parse(reader);
            DatapackDiagnostics.warnUnknownRootKeys(location, "settlement plot pricing", root,
                    Set.of("base", "purchase_increment", "distance_increment"));
            apply(new Rules(coefficient(root, "base"), coefficient(root, "purchase_increment"), coefficient(root, "distance_increment")));
        } catch (IOException | JsonParseException | IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalStateException("Invalid settlement plot pricing at " + location, exception);
        }
    }

    private static int coefficient(com.google.gson.JsonObject root, String key) {
        var value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Plot pricing " + key + " must be an integer");
        }
        return value.getAsBigDecimal().intValueExact();
    }

    static Rules rules() { return rules; }

    static void apply(Rules value) {
        rules = value;
    }

    record Rules(int base, int purchaseIncrement, int distanceIncrement) {
        Rules {
            if (base < 0 || purchaseIncrement < 0 || distanceIncrement < 0
                    || base > 1_000_000 || purchaseIncrement > 1_000_000 || distanceIncrement > 1_000_000) {
                throw new IllegalArgumentException("Plot pricing coefficients must be between zero and one million");
            }
        }
    }
}
