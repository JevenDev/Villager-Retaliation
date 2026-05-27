package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;

public record SkillTradeRequestMetadata(
        boolean targetable,
        int displayPriority,
        VillagerReputationLevel minReputation,
        int waitDays,
        int cooldownDays,
        SpecialOrderCost extraCost) {
    public static final SkillTradeRequestMetadata NOT_TARGETABLE = new SkillTradeRequestMetadata(
            false,
            0,
            VillagerReputationLevel.RESPECTED,
            0,
            0,
            SpecialOrderCost.EMPTY);

    public SkillTradeRequestMetadata {
        minReputation = minReputation == null ? VillagerReputationLevel.RESPECTED : minReputation;
        waitDays = Math.max(0, waitDays);
        cooldownDays = Math.max(0, cooldownDays);
        extraCost = extraCost == null ? SpecialOrderCost.EMPTY : extraCost;
    }
}
