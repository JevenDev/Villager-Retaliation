package com.jvn.villagerretaliation.trade;

import java.util.Locale;

public enum SkillTradePool {
    VILLAGER,
    WANDERING_TRADER_GENERIC,
    WANDERING_TRADER_RARE;

    public static SkillTradePool wanderingTraderPool(String value) {
        if (value == null || value.isBlank()) {
            return WANDERING_TRADER_GENERIC;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "rare", "wanderer_rare", "wandering_trader_rare" -> WANDERING_TRADER_RARE;
            default -> WANDERING_TRADER_GENERIC;
        };
    }
}
