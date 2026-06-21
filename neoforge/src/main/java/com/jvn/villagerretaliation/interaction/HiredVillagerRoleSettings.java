package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;

public final class HiredVillagerRoleSettings {
    private HiredVillagerRoleSettings() {
    }

    public static int defaultHorizontalRadius(HiredVillagerRole role, int minRadius, int maxRadius) {
        int preferred = switch (role) {
            case MINING -> 24;
            case LOGGING -> 32;
            case FARMING -> 24;
            case FISHING -> 24;
            case BUILDER -> 32;
            default -> 24;
        };
        return HiredWorkArea.clampRadius(preferred, minRadius, maxRadius);
    }

    public static int defaultVerticalRadius(HiredVillagerRole role, int maxRadius) {
        int preferred = switch (role) {
            case LOGGING -> 16;
            case FARMING -> 6;
            case FISHING -> 8;
            case BUILDER -> 12;
            default -> 8;
        };
        return HiredWorkArea.clampRadius(preferred, 1, maxRadius);
    }

    public static String workReportMessageKey(HiredVillagerRole role) {
        return switch (role) {
            case COMBAT -> "interaction.work_report.combat";
            case MINING -> "interaction.work_report.mining";
            case LOGGING -> "interaction.work_report.logging";
            case FARMING -> "interaction.work_report.farming";
            case FISHING -> "interaction.work_report.fishing";
            case BREWING -> "interaction.work_report.brewing";
            case BUILDER -> "interaction.work_report.builder";
            case ANIMAL_HANDLING -> "interaction.work_report.animal_handling";
            case NITWIT -> "interaction.work_report.nitwit";
        };
    }

    public static String workFinalReportMessageKey(HiredVillagerRole role) {
        return switch (role) {
            case COMBAT -> "interaction.work_final_report.combat";
            case MINING -> "interaction.work_final_report.mining";
            case LOGGING -> "interaction.work_final_report.logging";
            case FARMING -> "interaction.work_final_report.farming";
            case FISHING -> "interaction.work_final_report.fishing";
            case BREWING -> "interaction.work_final_report.brewing";
            case BUILDER -> "interaction.work_final_report.builder";
            case ANIMAL_HANDLING -> "interaction.work_final_report.animal_handling";
            case NITWIT -> "interaction.work_final_report.nitwit";
        };
    }

    public static int foodCost(HiredVillagerRole role) {
        int base = Math.max(0, VillagerRetaliationConfig.HIRED_WORK_BASE_FOOD_PER_DAY.get());
        int roleCost = switch (role) {
            case COMBAT -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_COMBAT.get();
            case MINING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_MINING.get();
            case LOGGING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_LOGGING.get();
            case FARMING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_FARMING.get();
            case FISHING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_NAVIGATION.get();
            case BREWING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_BREWING.get();
            case BUILDER -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_BUILDER.get();
            case ANIMAL_HANDLING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_ANIMAL_HANDLING.get();
            case NITWIT -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_NITWIT.get();
        };
        return Math.max(0, Math.max(base, roleCost));
    }

    public static double skillGrowthAmount(HiredVillagerRole role) {
        double amount = switch (role) {
            case COMBAT -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_COMBAT.get();
            case MINING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_MINING.get();
            case LOGGING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_LOGGING.get();
            case FARMING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_FARMING.get();
            case FISHING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_NAVIGATION.get();
            case BREWING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_BREWING.get();
            case BUILDER -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_BUILDER.get();
            case ANIMAL_HANDLING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_ANIMAL_HANDLING.get();
            case NITWIT -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_NITWIT.get();
        };
        return Math.max(0.0D, amount);
    }
}
