package com.jvn.villagerretaliation.trade;

public record SkillTradeQualityScaling(
        boolean enabled,
        boolean countBySkill,
        boolean costBySkill,
        boolean maxUsesBySkill,
        boolean xpBySkill,
        boolean rareChanceBySkill,
        boolean enchantmentsBySkill) {
    public static final SkillTradeQualityScaling DISABLED = new SkillTradeQualityScaling(
            false,
            false,
            false,
            false,
            false,
            false,
            false);
    public static final SkillTradeQualityScaling ENABLED_DEFAULTS = new SkillTradeQualityScaling(
            true,
            true,
            true,
            true,
            false,
            true,
            true);

    public boolean scalesCount() {
        return this.enabled && this.countBySkill;
    }

    public boolean scalesCost() {
        return this.enabled && this.costBySkill;
    }

    public boolean scalesMaxUses() {
        return this.enabled && this.maxUsesBySkill;
    }

    public boolean scalesXp() {
        return this.enabled && this.xpBySkill;
    }

    public boolean scalesRareChance() {
        return this.enabled && this.rareChanceBySkill;
    }

    public boolean scalesEnchantments() {
        return this.enabled && this.enchantmentsBySkill;
    }
}
