package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

public final class SkillTradeQualityScaler {
    private SkillTradeQualityScaler() {
    }

    public static double rareChance(SkillTradeScalingContext context) {
        double scaledChance = context.definition().chance();
        if (scaledChance >= 1.0D) {
            return 1.0D;
        }

        scaledChance *= VillagerRetaliationConfig.SKILL_TRADE_RARE_CHANCE_MULTIPLIER.get();
        if (!scalesQuality(context) || !context.scaling().scalesRareChance()) {
            scaledChance += Math.max(0, context.skillValue() - context.definition().minRank().minInclusive()) / 250.0D;
            return Mth.clamp(scaledChance, 0.0D, 1.0D);
        }

        SkillTradeQuality quality = quality(context);
        scaledChance *= quality.rareChanceMultiplier();
        scaledChance += 0.05D * context.eligibleProgress();
        return Mth.clamp(scaledChance, 0.0D, 1.0D);
    }

    public static int resultCount(SkillTradeScalingContext context, int baseCount) {
        if (!scalesQuality(context) || !context.scaling().scalesCount()) {
            return Math.clamp(baseCount, 1, 64);
        }
        return scaleStackCount(baseCount, quality(context).countMultiplier());
    }

    public static int currencyCost(MinecraftServer server, SkillTradeScalingContext context, Item costItem, int baseCount) {
        if (!scalesQuality(context) || !context.scaling().scalesCost() || costItem != VillagerCurrencyResources.primaryItem(server)) {
            return Math.clamp(baseCount, 1, 64);
        }
        return scaleStackCount(baseCount, quality(context).emeraldCostMultiplier());
    }

    public static int maxUses(SkillTradeScalingContext context, int baseMaxUses) {
        if (!scalesQuality(context) || !context.scaling().scalesMaxUses()) {
            return Math.clamp(baseMaxUses, 1, 128);
        }
        int scaled = Mth.floor(baseMaxUses * quality(context).maxUsesMultiplier());
        return Math.clamp(scaled, 1, 128);
    }

    public static int xp(SkillTradeScalingContext context, int baseXp) {
        if (!scalesQuality(context) || !context.scaling().scalesXp()) {
            return Math.clamp(baseXp, 0, 10_000);
        }
        int scaled = Mth.floor(baseXp * quality(context).xpMultiplier());
        return Math.clamp(scaled, 0, 10_000);
    }

    public static int requestedEnchantmentLevel(
            SkillTradeScalingContext context,
            SkillTradeEnchantments enchantments) {
        if (!enchantments.levelBySkill()) {
            return enchantments.minLevel();
        }
        if (!scalesQuality(context) || !context.scaling().scalesEnchantments()) {
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

    private static boolean scalesQuality(SkillTradeScalingContext context) {
        return VillagerRetaliationConfig.SKILL_TRADE_QUALITY_SCALING.get() && context.scaling().enabled();
    }

    private static SkillTradeQuality quality(SkillTradeScalingContext context) {
        SkillTradeQuality quality = SkillTradeQuality.forRank(context.rank());
        if (VillagerRetaliationConfig.SKILL_TRADE_LOW_SKILL_PENALTIES.get()) {
            return quality;
        }
        return new SkillTradeQuality(
                Math.max(1.0D, quality.countMultiplier()),
                Math.min(1.0D, quality.emeraldCostMultiplier()),
                Math.max(1.0D, quality.maxUsesMultiplier()),
                Math.max(1.0D, quality.xpMultiplier()),
                Math.max(1.0D, quality.rareChanceMultiplier())
        );
    }

    private static int scaleStackCount(int baseCount, double multiplier) {
        return Math.clamp(Mth.floor(baseCount * multiplier), 1, 64);
    }
}
