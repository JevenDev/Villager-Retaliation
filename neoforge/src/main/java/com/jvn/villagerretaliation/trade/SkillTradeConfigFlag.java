package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.Locale;
import java.util.Optional;

public enum SkillTradeConfigFlag {
    ENABLE_SKILL_TRADE_OVERHAUL("enableSkillTradeOverhaul"),
    SKILL_TRADE_ALLOW_HIGH_TIER_EQUIPMENT("skillTradeAllowHighTierEquipment"),
    SKILL_TRADE_ALLOW_SPECIAL_ARROWS("skillTradeAllowSpecialArrows"),
    SKILL_TRADE_ALLOW_RARE_SPECIALTY_TRADES("skillTradeAllowRareSpecialtyTrades");

    private final String serializedName;

    SkillTradeConfigFlag(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public boolean enabled() {
        return switch (this) {
            case ENABLE_SKILL_TRADE_OVERHAUL -> VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get();
            case SKILL_TRADE_ALLOW_HIGH_TIER_EQUIPMENT -> VillagerRetaliationConfig.SKILL_TRADE_ALLOW_HIGH_TIER_EQUIPMENT.get();
            case SKILL_TRADE_ALLOW_SPECIAL_ARROWS -> VillagerRetaliationConfig.SKILL_TRADE_ALLOW_SPECIAL_ARROWS.get();
            case SKILL_TRADE_ALLOW_RARE_SPECIALTY_TRADES -> VillagerRetaliationConfig.SKILL_TRADE_ALLOW_RARE_SPECIALTY_TRADES.get();
        };
    }

    public static Optional<SkillTradeConfigFlag> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(value);
        for (SkillTradeConfigFlag flag : values()) {
            if (normalize(flag.name()).equals(normalized)
                    || normalize(flag.serializedName).equals(normalized)) {
                return Optional.of(flag);
            }
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
    }
}
