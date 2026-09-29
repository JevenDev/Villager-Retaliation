package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;

public final class SettlementPopulationRequirements {
    public static final int MAX_POPULATION = 1000;
    private static List<Integer> observed = List.of();
    private static List<Integer> effective = List.of(0, 5, 15, 35, 75);
    private static String problem = "";

    private SettlementPopulationRequirements() { }

    public static synchronized List<Integer> effective() {
        var configured = configured();
        if (!configured.equals(observed)) {
            observed = configured;
            try {
                var previous = effective;
                effective = calculate(configured);
                if (!previous.equals(effective)) invalidate(previous, effective);
                problem = "";
            } catch (IllegalArgumentException invalid) {
                problem = invalid.getMessage();
                LogUtils.getLogger().error("{}. Keeping effective settlement populations {}. Correct settlements.hamletPopulation, villagePopulation, townPopulation and cityPopulation and reload the configuration.", problem, effective);
            }
        }
        return effective;
    }

    private static void invalidate(List<Integer> previous, List<Integer> accepted) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) return;
        Runnable refresh = () -> {
            for (var town : SettlementSavedData.get(server.overworld()).settlements()) {
                int next = town.stage().next().orElse(town.stage()).ordinal();
                if (!previous.get(next).equals(accepted.get(next))) {
                    town.support().resetAdvancement();
                    town.changed();
                }
            }
        };
        if (server.isSameThread()) refresh.run();
        else server.execute(refresh);
    }

    static List<Integer> calculate(List<Integer> configured) {
        if (configured.size() != 4) throw new IllegalArgumentException("Four settlement population thresholds are required");
        var result = new ArrayList<Integer>();
        result.add(0);
        for (int index = 0; index < configured.size(); index++) {
            int raw = configured.get(index);
            if (raw < 1 || raw > MAX_POPULATION) throw new IllegalArgumentException("Settlement population thresholds must be between 1 and " + MAX_POPULATION);
            int required = Math.max(result.getLast() + 1, raw);
            if (required > MAX_POPULATION) throw new IllegalArgumentException("Effective " + SettlementStage.values()[index + 1].name()
                    + " population " + required + " exceeds the maximum growth target " + MAX_POPULATION);
            result.add(required);
        }
        return List.copyOf(result);
    }

    private static List<Integer> configured() {
        return List.of(VillagerRetaliationConfig.SETTLEMENT_HAMLET_POPULATION.get(),
                VillagerRetaliationConfig.SETTLEMENT_VILLAGE_POPULATION.get(),
                VillagerRetaliationConfig.SETTLEMENT_TOWN_POPULATION.get(),
                VillagerRetaliationConfig.SETTLEMENT_CITY_POPULATION.get());
    }

    public static int growthTarget() {
        return VillagerRetaliationConfig.SETTLEMENT_POPULATION_TARGET.get();
    }

    public static boolean adjusted() {
        return !effective().subList(1, 5).equals(configured());
    }

    public static String problem() {
        effective();
        return problem;
    }

    static boolean capped(SettlementStage stage, int population) {
        return stage.next().filter(next -> population < next.population() && growthTarget() < next.population()).isPresent();
    }
}
