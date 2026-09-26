package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.Optional;

public enum SettlementStage {
    CAMP(64, 0),
    HAMLET(96, 1),
    VILLAGE(128, 2),
    TOWN(160, 4),
    CITY(192, 8);

    private final int radius;
    private final int projects;

    SettlementStage(int radius, int projects) {
        this.radius = radius;
        this.projects = projects;
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
        return this.radius;
    }

    public int projects() {
        return this.projects;
    }

    public int depots() {
        return 8 << ordinal();
    }

    public Optional<SettlementStage> next() {
        int next = ordinal() + 1;
        return next < values().length ? Optional.of(values()[next]) : Optional.empty();
    }
}
