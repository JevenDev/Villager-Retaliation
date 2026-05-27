package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.skill.VillagerSkillRank;

public record SkillTradeQuality(
        double countMultiplier,
        double emeraldCostMultiplier,
        double maxUsesMultiplier,
        double xpMultiplier,
        double rareChanceMultiplier) {
    public static SkillTradeQuality forRank(VillagerSkillRank rank) {
        return switch (rank) {
            case NOVICE -> new SkillTradeQuality(0.75D, 1.20D, 0.75D, 0.90D, 0.55D);
            case APPRENTICE -> new SkillTradeQuality(0.90D, 1.10D, 0.85D, 0.95D, 0.75D);
            case SKILLED -> new SkillTradeQuality(1.00D, 1.00D, 1.00D, 1.00D, 1.00D);
            case EXPERT -> new SkillTradeQuality(1.12D, 0.95D, 1.15D, 1.10D, 1.15D);
            case MASTER -> new SkillTradeQuality(1.20D, 0.90D, 1.25D, 1.20D, 1.30D);
        };
    }
}
