package com.jvn.villagerretaliation.settlement;

final class SettlementWorkBudget {
    private final SettlementWorkerState worker;
    private final double multiplier;
    private int elapsed;

    SettlementWorkBudget(SettlementWorkerState worker, int elapsed, double multiplier) {
        this.worker = worker;
        this.elapsed = elapsed;
        this.multiplier = multiplier;
    }

    double available() {
        return this.worker.progress() + this.elapsed * this.multiplier;
    }

    boolean spend(double cost) {
        this.worker.work(this.elapsed, this.multiplier);
        this.elapsed = 0;
        return this.worker.spend(cost);
    }
}
