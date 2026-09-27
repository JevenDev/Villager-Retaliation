package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.Optional;

public enum SettlementStage {
    CAMP(64),
    HAMLET(96),
    VILLAGE(128),
    TOWN(160),
    CITY(192);

    private final int legacyRadius;

    SettlementStage(int legacyRadius) {
        this.legacyRadius = legacyRadius;
    }

    public int population() {
        int configured = switch (this) {
            case CAMP -> 0;
            case HAMLET -> VillagerRetaliationConfig.SETTLEMENT_HAMLET_POPULATION.get();
            case VILLAGE -> VillagerRetaliationConfig.SETTLEMENT_VILLAGE_POPULATION.get();
            case TOWN -> VillagerRetaliationConfig.SETTLEMENT_TOWN_POPULATION.get();
            case CITY -> VillagerRetaliationConfig.SETTLEMENT_CITY_POPULATION.get();
        };
        return this == CAMP ? 0 : Math.max(values()[ordinal() - 1].population() + 1, configured);
    }

    public int radius() {
        return SettlementStageRules.forStage(this).radius();
    }

    int legacyRadius() {
        return this.legacyRadius;
    }

    public int projects() {
        return SettlementStageRules.forStage(this).projects();
    }

    public int depots() {
        return SettlementStageRules.forStage(this).depots();
    }

    public Optional<SettlementStage> next() {
        int next = ordinal() + 1;
        return next < values().length ? Optional.of(values()[next]) : Optional.empty();
    }
}
