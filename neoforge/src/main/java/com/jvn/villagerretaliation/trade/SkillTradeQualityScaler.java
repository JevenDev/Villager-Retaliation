package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class SkillTradeQualityScaler {
    private SkillTradeQualityScaler() {
    }

    public static double rareChance(SkillTradeScalingContext context) {
        double scaledChance = context.definition().chance();
        if (scaledChance >= 1.0D) {
            return 1.0D;
        }

        scaledChance *= VillagerRetaliationConfig.SKILL_TRADE_RARE_CHANCE_MULTIPLIER.get();
        if (!context.scaling().scalesRareChance()) {
            scaledChance += Math.max(0, context.skillValue() - context.definition().minRank().minInclusive()) / 250.0D;
            return Mth.clamp(scaledChance, 0.0D, 1.0D);
        }

        SkillTradeQuality quality = SkillTradeQuality.forRank(context.rank());
        scaledChance *= quality.rareChanceMultiplier();
        scaledChance += 0.05D * context.eligibleProgress();
        return Mth.clamp(scaledChance, 0.0D, 1.0D);
    }

    public static int resultCount(SkillTradeScalingContext context, int baseCount) {
        if (!context.scaling().scalesCount()) {
            return Math.clamp(baseCount, 1, 64);
        }
        return scaleStackCount(baseCount, SkillTradeQuality.forRank(context.rank()).countMultiplier());
    }

    public static int emeraldCost(SkillTradeScalingContext context, Item costItem, int baseCount) {
        if (!context.scaling().scalesCost() || costItem != Items.EMERALD) {
            return Math.clamp(baseCount, 1, 64);
        }
        return scaleStackCount(baseCount, SkillTradeQuality.forRank(context.rank()).emeraldCostMultiplier());
    }

    public static int maxUses(SkillTradeScalingContext context, int baseMaxUses) {
        if (!context.scaling().scalesMaxUses()) {
            return Math.clamp(baseMaxUses, 1, 128);
        }
        int scaled = Mth.floor(baseMaxUses * SkillTradeQuality.forRank(context.rank()).maxUsesMultiplier());
        return Math.clamp(scaled, 1, 128);
    }

    public static int xp(SkillTradeScalingContext context, int baseXp) {
        if (!context.scaling().scalesXp()) {
            return Math.clamp(baseXp, 0, 10_000);
        }
        int scaled = Mth.floor(baseXp * SkillTradeQuality.forRank(context.rank()).xpMultiplier());
        return Math.clamp(scaled, 0, 10_000);
    }

    public static int requestedEnchantmentLevel(
            SkillTradeScalingContext context,
            SkillTradeEnchantments enchantments) {
        if (!enchantments.levelBySkill()) {
            return enchantments.minLevel();
        }
        if (!context.scaling().scalesEnchantments()) {
            return legacyRequestedEnchantmentLevel(context.skillValue(), enchantments);
        }

        VillagerSkillRank rank = context.rank();
        return switch (rank) {
            case NOVICE, APPRENTICE, SKILLED -> enchantments.minLevel();
            case EXPERT -> Math.min(enchantments.maxLevel(), enchantments.minLevel() + 1);
            case MASTER -> enchantments.maxLevel();
        };
    }

    private static int legacyRequestedEnchantmentLevel(int skillValue, SkillTradeEnchantments enchantments) {
        if (skillValue >= 90) {
            return enchantments.maxLevel();
        }
        if (skillValue >= 72) {
            return Math.max(enchantments.minLevel(), Math.min(enchantments.maxLevel(), 2));
        }
        return enchantments.minLevel();
    }

    private static int scaleStackCount(int baseCount, double multiplier) {
        return Math.clamp(Mth.floor(baseCount * multiplier), 1, 64);
    }
}
