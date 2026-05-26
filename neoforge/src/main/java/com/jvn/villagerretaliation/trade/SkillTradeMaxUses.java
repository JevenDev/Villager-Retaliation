package com.jvn.villagerretaliation.trade;

import net.minecraft.util.Mth;

public record SkillTradeMaxUses(int base, boolean bonusBySkill, int maxBonus) {
    public static final SkillTradeMaxUses DEFAULT = new SkillTradeMaxUses(6, false, 0);

    public SkillTradeMaxUses {
        base = Math.clamp(base, 1, 64);
        maxBonus = Math.clamp(maxBonus, 0, 64);
    }

    public int valueForSkill(int skillValue, int minimumSkillValue) {
        if (!this.bonusBySkill || this.maxBonus <= 0) {
            return this.base;
        }

        int skillRange = Math.max(1, 100 - minimumSkillValue);
        double skillProgress = Mth.clamp((skillValue - minimumSkillValue) / (double) skillRange, 0.0D, 1.0D);
        return Math.clamp(this.base + Mth.floor(this.maxBonus * skillProgress), 1, 128);
    }
}
