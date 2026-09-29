package com.jvn.villagerretaliation.settlement;

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
        return SettlementPopulationRequirements.effective().get(ordinal());
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
