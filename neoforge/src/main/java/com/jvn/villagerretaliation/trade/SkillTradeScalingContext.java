package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import net.minecraft.util.Mth;

public record SkillTradeScalingContext(SkillTradeDefinition definition, int skillValue) {
    public SkillTradeScalingContext {
        skillValue = VillagerSkillSet.clamp(skillValue);
    }

    public SkillTradeQualityScaling scaling() {
        return this.definition.qualityScaling();
    }

    public VillagerSkillRank rank() {
        return VillagerSkillRank.fromValue(this.skillValue);
    }

    public double eligibleProgress() {
        int min = this.definition.minRank().minInclusive();
        int max = this.definition.maxRank() == null ? VillagerSkillSet.MAX_VALUE : this.definition.maxRank().maxInclusive();
        if (max <= min) {
            return 1.0D;
        }
        return Mth.clamp((this.skillValue - min) / (double) (max - min), 0.0D, 1.0D);
    }
}
