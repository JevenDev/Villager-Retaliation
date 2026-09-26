package com.jvn.villagerretaliation.settlement;

import java.util.Optional;

public enum SettlementStage {
    CAMP(0, 64, 0),
    HAMLET(5, 96, 1),
    VILLAGE(15, 128, 2),
    TOWN(35, 160, 4),
    CITY(75, 192, 8);

    private final int population;
    private final int radius;
    private final int projects;

    SettlementStage(int population, int radius, int projects) {
        this.population = population;
        this.radius = radius;
        this.projects = projects;
    }

    public int population() {
        return this.population;
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
